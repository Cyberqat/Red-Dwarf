/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This file implements a client for the example-chat-app application,
 * and can also be used with the "Hello Channels" tutorial application.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include <errno.h>
#include <poll.h>  /** just for POLLIN, POLLOUT, POLLERR declarations */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
/* required for STDIN_FILENO on Linux/Solaris, but not Darwin: */
#include <unistd.h>
#include <sys/select.h>
/** included for optarg (declared in unistd.h on some, but not all systems) */
#include <getopt.h>
#include <wchar.h>
#include "sgs/connection.h"
#include "sgs/context.h"
#include "sgs/session.h"
#include "sgs/hex_utils.h"
#include "sgs/map.h"

/** Timeout value for calls to select(), in milliseconds */
#define SELECT_TIMEOUT  200

/** Default connection info for server. */
#define DEFAULT_HOST  "localhost"
#define DEFAULT_PORT  2502

/** The name of the global channel */
static const wchar_t GLOBAL_CHANNEL_NAME[] = L"-GLOBAL-";

#ifndef FD_COPY
#define FD_COPY fd_copy
static fd_set* fd_copy(fd_set* from, fd_set* to) {
    return (fd_set*) memcpy(to, from, sizeof(fd_set));
}
#endif /* FD_COPY */

/*
 * Message callbacks
 */
static void channel_joined_cb(sgs_connection *conn, sgs_channel *channel);
static void channel_left_cb(sgs_connection *conn, sgs_channel *channel);
static void channel_recv_msg_cb(sgs_connection *conn, sgs_channel *channel,
    const sgs_id *sender_id, const uint8_t *msg,
    size_t msglen);
static void disconnected_cb(sgs_connection *conn);
static void logged_in_cb(sgs_connection *conn, sgs_session *session);
static void login_failed_cb(sgs_connection *conn, const uint8_t *msg,
    size_t msglen);
static void reconnected_cb(sgs_connection *conn);
static void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen);
static void register_fd_cb(sgs_connection *conn, int fd, short events);
static void unregister_fd_cb(sgs_connection *conn, int fd, short events);

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void bye(int exitval);
static int concatstr(const char *prefix, const char *suffix, char *buf,
    size_t buflen);
static void process_user_cmd(char *cmd);

/*
 * STATIC GLOBAL VARIABLES
 *
 * Some of these are declared globally so that the various callback functions
 * can access them; others are declared globally just so that they will persist
 * between function calls without having to be redeclared and initialized each
 * time.  Another way to do this would be to declare a globally-accessible
 * lookup service which, given a sgs_connection as the key, allows access to
 * independent sets of these variables for each connection.  This would support
 * multiple, concurrent connections/sessions.
 */
static sgs_context *g_context;
static sgs_connection *g_conn;
static sgs_session *g_session;
static sgs_map *g_channel_map;
static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd;

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    char inbuf[1024] = { '\0' };   /** inbuf must always be null-terminated */
    int inbuf_alive = 0;
    int do_cmd_prompts = 1;
    char *token;
    int c, i, result, token_len, work;
    size_t len;
    char *hostname = DEFAULT_HOST;
    int port = DEFAULT_PORT;
    fd_set readset, writeset, exceptset;
    struct timeval timeout_tv;
    
    /**
     * stdout and stderr are normally line-buffered, but if they are redirected
     * to a file (instead of the console) this may not be true; this can be
     * annoying so force them both to be line-buffered no matter what.
     */
    setvbuf(stdout, (char *)NULL, _IOLBF, 0);
    setvbuf(stderr, (char *)NULL, _IOLBF, 0);
    
    FD_ZERO(&g_master_readset);
    FD_ZERO(&g_master_writeset);
    FD_ZERO(&g_master_exceptset);
    
    /** We are always interested in reading from STDIN. */
    FD_SET(STDIN_FILENO, &g_master_readset);
    g_maxfd = STDIN_FILENO;
    
    /** process command line arguments */
    while ((c = getopt(argc, argv, "h:p:su")) != -1) {
        switch (c) {
        case 'h':  /* hostname */
            hostname = optarg;
            break;
            
        case 'p':  /* port */
            port = atoi(optarg);
            break;
            
        case 's': /* silence */
            do_cmd_prompts = 0;
            break;
            
        case 'u':  /* usage */
            printf("Usage: %s [-h HOST] [-p PORT] [-s] [-u]\n  -h    Specify rem\
ote hostname (default: %s)\n  -p    Specify remote port (default: %d)\n  -s    S\
ilent Mode (no command prompts)\n  -u    Print usage\n",
                argv[0], DEFAULT_HOST, DEFAULT_PORT);
            return 0;
            
            /* 
             * No default case necessary; an error will automatically be printed
             * since opterr is 1.
             */
        }
    }
    
    printf("Starting up with host=%s and port=%d...\n", hostname, port);
    
    /**
     * Create map to hold channel pointers.  Use strcmp to compare map keys
     * since the keys are channel names.  Do not pass functions to free map
     * keys or values since we are not allocating these (just copying pointers
     * passed by callback functions).
     */
    g_channel_map = sgs_map_create((int (*)(const void*,const void*))wcscmp);
    
    /** Create sgs_context object and register event callbacks. */
    g_context = sgs_ctx_create(hostname, port,
        register_fd_cb, unregister_fd_cb);
    if (g_context == NULL) { perror("Error in sgs_ctx_create()"); bye(-1); }
    
    sgs_ctx_set_channel_joined_cb(g_context, channel_joined_cb);
    sgs_ctx_set_channel_left_cb(g_context, channel_left_cb);
    sgs_ctx_set_channel_recv_msg_cb(g_context, channel_recv_msg_cb);
    sgs_ctx_set_disconnected_cb(g_context, disconnected_cb);
    sgs_ctx_set_logged_in_cb(g_context, logged_in_cb);
    sgs_ctx_set_login_failed_cb(g_context, login_failed_cb);
    sgs_ctx_set_reconnected_cb(g_context, reconnected_cb);
    sgs_ctx_set_recv_msg_cb(g_context, recv_msg_cb);
    
    /** Create sgs_connection object. */
    g_conn = sgs_connection_create(g_context);
    if (g_conn == NULL) { perror("Error in sgs_connection_create()"); bye(-1); }
    
    if (do_cmd_prompts) {
        printf("Command: ");
        fflush(stdout);
    }
    
    while (1) {
        if (inbuf_alive && strlen(inbuf) > 0) {
            len = strlen(inbuf);
            
            /**
             * Note: If strtok is called on a string with no characters from
             * set, strtok will return the string, not NULL, which is not the
             * behavior that we want in this case (if there is no terminating
             * newline character, then we have a partial line and we want to
             * wait for more input).  So we need a special check for this case.
             */
            token = strtok(inbuf, "\n");

            if (token == NULL || strlen(token) == len) {
                inbuf_alive = 0;
            } else {
                token_len = strlen(token);
                process_user_cmd(token);
                memmove(inbuf, token + token_len + 1, len - (token_len + 1) + 1);
            }
        }
        
        /** Copy master fd_sets into temporary copies. */
        FD_COPY(&g_master_readset, &readset);
        FD_COPY(&g_master_writeset, &writeset);
        FD_COPY(&g_master_exceptset, &exceptset);
        
        /**
         * According to `man select(2)`, timeout might be modified by select(),
         * hence we re-initialize it every time.
         */
        timeout_tv.tv_sec = 0;
        timeout_tv.tv_usec = SELECT_TIMEOUT*1000;
        
        result = select(g_maxfd + 1, &readset, &writeset, &exceptset,
            &timeout_tv);

        if (result == -1) {
            perror("Error calling select()");
        }
        else if (result > 0) {
            work = 0;
            
            for (i=0; i <= g_maxfd; i++) {
                /** STDIN */
                if (FD_ISSET(i, &readset) && (i == STDIN_FILENO)) {
                    /** Data available for reading. */
                    len = strlen(inbuf);
                    result = read(STDIN_FILENO, inbuf + len,
                        sizeof(inbuf) - strlen(inbuf) - 1);
                    
                    if (result == -1) {
                        perror("Error calling read() on STDIN");
                    }
                    else if (result > 0) {
                        /**
                         * Always null-terminate the block of data in
                         * inbuf so that strtok can be called on it.
                         */
                        inbuf[len + result] = '\0';
                        inbuf_alive = 1;
                        
                        if (do_cmd_prompts) {
                            printf("Command: ");
                            fflush(stdout);
                        }
                    }
                } else if (FD_ISSET(i, &readset) || FD_ISSET(i, &writeset) ||
                    FD_ISSET(i, &exceptset)) {
                    /** Must be some fd that the sgs_connection registered */
                    work = 1;
                }
            }
            
            if (work) {
                if (sgs_connection_do_work(g_conn)) {
                    perror("Error calling sgs_connection_do_work()");
                }
            }
        }
        /** else, select() timed out. */
    }
    
    /** Just for compiler; should never reach here. */
    return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */


/*
 * -----  Callback Functions -----
 */


/*
 * channel_joined_cb()
 */
static void channel_joined_cb(sgs_connection *conn, sgs_channel *channel) {
    int result;
    const wchar_t *name;

    name = sgs_channel_name(channel);
    printf(" - Callback -   Joined channel: %ls\n", name);

    if (sgs_map_contains(g_channel_map, name)) {
        printf("Warning: client thought it was already a member of channel"
            " %ls\n", name);
        return;
    }
    
    result = sgs_map_put(g_channel_map, name, channel);
    if (result == -1) {
        perror("Error in sgs_map_put()");
    }
}

/*
 * channel_left_cb()
 */
static void channel_left_cb(sgs_connection *conn, sgs_channel *channel) {
    const wchar_t *name;
    int result;

    name = sgs_channel_name(channel);
    printf(" - Callback -   Left channel: %ls\n", name);
    
    result = sgs_map_remove(g_channel_map, name);
    if (result == -1) {
        printf("Warning: client did not think it was a member of channel"
            "%ls\n", name);
    }
}

/*
 * channel_recv_msg_cb()
 */
static void channel_recv_msg_cb(sgs_connection *conn, sgs_channel *channel,
    const sgs_id *sender_id, const uint8_t *msg, size_t msglen)
{
    char msgstr[msglen + 1];
    const wchar_t *channel_name = sgs_channel_name(channel);
    char *sender_desc;
    
    memcpy(msgstr, msg, msglen);
    msgstr[msglen] = '\0';
    
    if (sender_id == NULL) {
        sender_desc = "Server";
    }
    else {
        sender_desc = (char *)malloc(sgs_id_get_byte_len(sender_id)*2 + 1);
        
        if (sender_desc == NULL) {
            printf("Error: malloc failed in channel_recv_msg_cb().");
            return;
        }
        
        bytestohex(sgs_id_get_bytes(sender_id), sgs_id_get_byte_len(sender_id),
            sender_desc);
    }
    
    printf(" - Callback -   Received message on channel %ls from %s: %s\n",
        channel_name, sender_desc, msgstr);
    
    if (sender_id != NULL) free(sender_desc);
}

/*
 * disconnected_cb()
 */
static void disconnected_cb(sgs_connection *conn) {
    printf(" - Callback -   Disconnected.\n");
    g_session = NULL;
}

/*
 * logged_in_cb()
 */
static void logged_in_cb(sgs_connection *conn, sgs_session *session) {
    const sgs_id *session_id = sgs_session_get_id(session);
    char *session_desc = (char *)malloc(sgs_id_get_byte_len(session_id)*2 + 1);
    
    if (session_desc == NULL) {
        printf("Error: malloc failed in logged_in_cb().");
        return;
    }
    
    bytestohex(sgs_id_get_bytes(session_id), sgs_id_get_byte_len(session_id),
        session_desc);
    
    printf(" - Callback -   Logged in with sessionId %s.\n", session_desc);
    
    g_session = session;
    free(session_desc);
}

/*
 * login_failed_cb()
 */
static void login_failed_cb(sgs_connection *conn, const uint8_t *msg,
    size_t msglen)
{
    char msgstr[msglen + 1];
    memcpy(msgstr, msg, msglen);
    msgstr[msglen] = '\0';
    
    printf(" - Callback -   Login failed (%s)\n", msgstr);
}

/*
 * reconnected_cb()
 */
static void reconnected_cb(sgs_connection *conn) {
    printf(" - Callback -   Reconnected.\n");
}

/*
 * recv_msg_cb()
 */
static void recv_msg_cb(sgs_connection *conn, const uint8_t *msg,
    size_t msglen)
{
    char msgstr[msglen + 1];
    memcpy(msgstr, msg, msglen);
    msgstr[msglen] = '\0';
    
    printf(" - Callback -   Received message: %s\n", msgstr);
}

/*
 * register_fd_cb()
 */
static void register_fd_cb(sgs_connection *conn, int fd, short events) {
    if ((events & POLLIN) == POLLIN)
        FD_SET(fd, &g_master_readset);
    
    if ((events & POLLOUT) == POLLOUT)
        FD_SET(fd, &g_master_writeset);
    
    if ((events & POLLERR) == POLLERR)
        FD_SET(fd, &g_master_exceptset);
    
    if (fd > g_maxfd) g_maxfd = fd;
}

/*
 * unregister_fd_cb()
 */
static void unregister_fd_cb(sgs_connection *conn, int fd, short events) {
    int i, new_max;
    
    if ((events & POLLIN) == POLLIN)
        FD_CLR(fd, &g_master_readset);
    
    if ((events & POLLOUT) == POLLOUT)
        FD_CLR(fd, &g_master_writeset);
    
    if ((events & POLLERR) == POLLERR)
        FD_CLR(fd, &g_master_exceptset);
    
    /** Check if a new max-fd needs to be calculated. */
    if (fd == g_maxfd) {
        new_max = 0;
        
        for (i=0; i <= g_maxfd; i++) {
            if (FD_ISSET(i, &g_master_readset) ||
                FD_ISSET(i, &g_master_writeset) ||
                FD_ISSET(i, &g_master_exceptset))
                new_max = i;
        }
        
        g_maxfd = new_max;
    }
}


/*
 * -----  Other Static Methods -----
 */


/*
 * function: bye()
 *
 * Performs object cleanup and then exits.
 */
static void bye(int exitval) {
    /* cleanup: */
    sgs_map_destroy(g_channel_map);
    sgs_connection_destroy(g_conn);
    sgs_ctx_destroy(g_context);
    
    exit(exitval);
}

/*
 * function: concatstr()
 *
 * Copies prefix and suffix to buf, after checking that buf has enough space.
 * Returns 0 on success or -1 on error.
 */
static int concatstr(const char *prefix, const char *suffix, char *buf,
    size_t buflen)
{
    if (strlen(prefix) + strlen(suffix) + 1 > buflen) {
        errno = ENOBUFS;
        return -1;
    }
  
    memcpy(buf, prefix, strlen(prefix));
    memcpy(buf + strlen(prefix), suffix, strlen(suffix) + 1);  /* include '\0' */
    return 0;
}

static wchar_t* to_wcs(const char* s) {
    int wlen = mbstowcs(NULL, s, 0);
    wchar_t* result = malloc(sizeof(wchar_t) * (wlen + 1));
    mbstowcs(result, s, wlen);
    return result;
}

/*
 * function: process_user_cmd()
 *
 * Process and act on a line of user input.
 *
 * args:
 *   input: string of text entered by the user
 */
static void process_user_cmd(char *cmd) {
    char *token, *token2, *tmp;
    char strbuf[1024];
    uint8_t bytebuf[1024];
    size_t nbytes;
    int result;
    sgs_id* recipient;
    sgs_channel *channel;
    
    token = strtok(cmd, " ");

    if (token == NULL) {
        /** nothing entered? */
    }
    else if (strcmp(token, "help") == 0) {
        printf("Available commands:\n");
        printf("  quit: terminates the program\n");
        printf("  login <username> <password>: log into the server\n");
        printf("  logout: log out from the server (cleanly)\n");
        printf("  logoutf: log out from the server (forcibly)\n");
        printf("  srvsend <msg>: send a message directly to the server (not \
normally necessary)\n");
        printf("  psend <user-id> <msg>: send a private message to a user (alias\
: pm)\n");
        printf("  chsend <channel-name> <msg>: broadcast a message on a channel\n");
        printf("  chjoin <channel-name>: join a channel (alias: join)\n");
        printf("  chleave <channel-name>: leave a channel (alias: leave)\n");
        printf("\n");
    }
    else if ((strcmp(token, "quit") == 0) || (strcmp(token, "exit") == 0)) {
        bye(0);
    }
    else if (strcmp(token, "login") == 0) {
        token = strtok(NULL, " ");
    
        if (token == NULL) {
            printf("Invalid command.  Syntax: login <username> <password>\n");
            return;
        }
    
        token2 = strtok(NULL, " ");
    
        if (token2 == NULL) {
            printf("Invalid command.  Syntax: login <username> <password>\n");
            return;
        }
    
        tmp = strtok(NULL, "");
    
        if (tmp == token2) {
            printf("Invalid command.  Syntax: login <username> <password>\n");
            return;
        }
    
        if (sgs_connection_login(g_conn, token, token2) == -1) {
            perror("Error in sgs_connection_login()");
            return;
        }
    }
    else if (strcmp(token, "logout") == 0) {
        if (sgs_connection_logout(g_conn, 0) == -1) {
            perror("Error in sgs_connection_logout()");
            return;
        }
    }
    else if (strcmp(token, "logoutf") == 0) {
        if (sgs_connection_logout(g_conn, 1) == -1) {
            perror("Error in sgs_connection_logout()");
            return;
        }
    }
    else if (strcmp(token, "srvsend") == 0) {
        if (g_session == NULL) {
            printf("Error: not logged in!\n");
            return;
        }
    
        token = strtok(NULL, "");
    
        if (token == NULL) {
            printf("Invalid command.  Syntax: srvsend <msg>\n");
            return;
        }
    
        if (sgs_session_direct_send(g_session, (uint8_t*)token,
                strlen(token)) == -1) {
            perror("Error in sgs_session_direct_send()");
            return;
        }
    }
    else if (strcmp(token, "psend") == 0 || strcmp(token, "pm") == 0) {
        if (g_session == NULL) {
            printf("Error: not logged in!\n");
            return;
        }
        
        /** For private messages, use the "Global" channel */
        channel = sgs_map_get(g_channel_map, GLOBAL_CHANNEL_NAME);
        if (channel == NULL) {
            printf("Error: could not find global channel in channel map.\n");
            return;
        }
        
        token = strtok(NULL, " ");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: psend <user> <msg>\n");
            return;
        }

        nbytes = strlen(token) / 2;
        
        if (nbytes > sizeof(bytebuf)) {
            printf("Error: ran out of buffer space (recipient ID too big).\n");
            return;
        }
        
        result = hextobytes(token, bytebuf);

        if (result == -1) {
            printf("Error: invalid recipient ID (%s).\n", token);
            return;
        }
        
        recipient = sgs_id_create(bytebuf, nbytes, NULL);
        if (recipient == NULL) {
            printf("Error: invalid recipient ID (%s).\n", token);
            return;
        }
        
        token = strtok(NULL, "");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: psend <user> <msg>\n");
            sgs_id_destroy(recipient);
            return;
        }
        
        if (concatstr("/pm ", token, strbuf, sizeof(strbuf)) == -1) {
            printf("Error: ran out of buffer space (user input too big?).\n");
            sgs_id_destroy(recipient);
            return;
        }
        
        if (sgs_channel_send_one(channel, (uint8_t*)strbuf, strlen(strbuf),
                recipient) == -1) {
            perror("Error in sgs_session_channel_send()");
            sgs_id_destroy(recipient);
            return;
        }

        sgs_id_destroy(recipient);
    }
    else if (strcmp(token, "chsend") == 0) {
        if (g_session == NULL) {
            printf("Error: not logged in!\n");
            fflush(stdout);
            return;
        }
        
        token = strtok(NULL, " ");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: chsend <channel> <msg>\n");
            fflush(stdout);
            return;
        }

        wchar_t* chname = to_wcs(token);
        channel = sgs_map_get(g_channel_map, chname);

        if (channel == NULL) {
            printf("Error: Channel \"%ls\" not found.\n", chname);
            fflush(stdout);
            free(chname);
            return;
        }

        free(chname);
        
        token = strtok(NULL, "");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: chsend <channel> <msg>\n");
            fflush(stdout);
            return;
        }
        
        /** note: no prefix necessary for this command */
        
        if (sgs_channel_send_all(channel, (uint8_t*)token, strlen(token)) == -1) {
            perror("Error in sgs_session_channel_send()");
            return;
        }
    }
    else if (strcmp(token, "chjoin") == 0 || strcmp(token, "join") == 0) {
        if (g_session == NULL) {
            printf("Error: not logged in!\n");
            fflush(stdout);
            return;
        }
        
        token = strtok(NULL, "");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: chjoin <channel-name>\n");
            return;
        }
        
        if (concatstr("/join ", token, strbuf, sizeof(strbuf)) == -1) {
            printf("Error: ran out of buffer space (user input too big?).\n");
            return;
        }
        
        if (sgs_session_direct_send(g_session, (uint8_t*)strbuf,
                strlen(strbuf)) == -1) {
            perror("Error in sgs_session_direct_send()");
            return;
        }
    }
    else if (strcmp(token, "chleave") == 0 || strcmp(token, "leave") == 0) {
        if (g_session == NULL) {
            printf("Error: not logged in!\n");
            return;
        }
        
        token = strtok(NULL, "");
        
        if (token == NULL) {
            printf("Invalid command.  Syntax: chleave <channel-name>\n");
            return;
        }
        
        if (concatstr("/leave ", token, strbuf, sizeof(strbuf)) == -1) {
            printf("Error: ran out of buffer space (user input too big?).\n");
            return;
        }
        
        if (sgs_session_direct_send(g_session, (uint8_t*)strbuf,
                strlen(strbuf)) == -1) {
            perror("Error in sgs_session_direct_send()");
            return;
        }
    }
    else {
        printf("Unrecognized command.  Try \"help\"\n");
    }
}
