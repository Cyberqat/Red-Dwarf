/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/
package com.sun.sgs.test.app.matchmaker.common;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;


/**
 * This class is used by both client and server to pack commands into bytes, 
 * and unpack responses into Java ojbects.
 */
public final class CommandProtocol implements Serializable {

    private static final long serialVersionUID = 1L;

    public final static String LOBBY_MANAGER_CONTROL_CHANNEL =
            "LobbyManagerControl";

    // The command codes are unsigned bytes, but since Java doesn't have
    // unsigned bytes ints are used to fit the range.

    /**
     * Sent to the client to notify that the server is ready to accept
     * commands.
     */
    public final static int SERVER_LISTENING = 0x10;
    public final static int ERROR = 0x11;

    public final static int LEAVE_LOBBY = 0x66;
    public final static int LEAVE_GAME = 0x67;
    
    public final static int LIST_FOLDER_REQUEST = 0x70;
    public final static int LIST_FOLDER_RESPONSE = 0x71;

    public final static int JOIN_LOBBY = 0x78;
    public final static int JOIN_GAME = 0x79;
    public final static int PLAYER_ENTERED_LOBBY = 0x90;

    public final static int UPDATE_PLAYER_READY_REQUEST = 0xBB;
    public final static int PLAYER_READY_UPDATE = 0xBC;
    public final static int START_GAME_REQUEST = 0xBD;

    public final static int SEND_TEXT = 0x91;
    public final static int SEND_PRIVATE_TEXT = 0x92;

    public final static int GAME_PARAMETERS_REQUEST = 0x94;
    public final static int GAME_PARAMETERS_RESPONSE = 0x95;

    public final static int CREATE_GAME = 0x96;
    public final static int CREATE_GAME_FAILED = 0x97;
    public final static int GAME_CREATED = 0x98;
    public final static int GAME_STARTED = 0x9A;	// sent to lobby

    public final static int PLAYER_JOINED_GAME = 0x9B;  // sent to lobby
    public final static int PLAYER_ENTERED_GAME = 0xB0; // sent to game room
    public final static int PLAYER_LEFT_GAME = 0x9C;    // sent to lobby

    public final static int GAME_DELETED = 0x99;        // sent to lobby
    public final static int GAME_COMPLETED = 0x9D;
    public final static int GAME_UPDATED = 0x9E;
    
    public final static int UPDATE_GAME_REQUEST = 0xB1;
    public final static int UPDATE_GAME_FAILED = 0xB2;
    public final static int BOOT_REQUEST = 0xB5;
    public final static int BOOT_FAILED = 0xB6;
    public final static int PLAYER_BOOTED_FROM_GAME = 0xB7;

    public final static int PLAYER_LEFT_LOBBY = 0xB8;

    // error codes
    public final static int NOT_CONNECTED_LOBBY = 0x12;
    public final static int NOT_CONNECTED_GAME = 0x13;
    public final static int CONNECTED_LOBBY = 0x14;
    public final static int CONNECTED_GAME = 0x15;
    public final static int PLAYER_NOT_HOST = 0x16;
    public final static int PLAYERS_NOT_READY = 0x17;
    public final static int LESS_THAN_MIN_PLAYERS = 0x18;
    public final static int GREATER_THAN_MAX_PLAYERS = 0x19;
    public final static int MAX_PLAYERS = 0x1A;
    public final static int INCORRECT_PASSWORD = 0x1B;
    public final static int INVALID_LOBBY = 0x1C;
    public final static int INVALID_GAME = 0x1D;
    public final static int INVALID_GAME_PARAMETERS = 0x1E;
    public final static int INVALID_GAME_NAME = 0x1F;
    public final static int GAME_EXISTS = 0x20;
    public final static int BOOT_NOT_SUPPORTED = 0x21;
    public final static int BAN_NOT_SUPPORTED = 0x22;
    public final static int BOOT_SELF = 0x23;
    public final static int PLAYER_BANNED = 0x24;

    
    // type codes
    public final static int TYPE_INTEGER = 0x1;
    public final static int TYPE_BOOLEAN = 0x2;
    public final static int TYPE_STRING = 0x3;
    public final static int TYPE_BYTE = 0x4;
    public final static int TYPE_UUID = 0x5;
    

    public CommandProtocol() {}


    /**
     * Converts the given int to an unsigned byte (by downcasting).
     * 
     * @param b the byte stored as an int
     * 
     * @return the same value returned as a byte unsigned.
     */
    private byte getUnsignedByte(int b) {
        return (byte) b;
    }

    public String readString(ByteBuffer data) {
        String str = null;
        byte[] stringBytes = readBytes(data);
        try {
            str = new String(stringBytes, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
        }

        return str;
    }

    /**
     * Reads the next int off the buffer and returns true if it equals
     * 1, otherwise false.
     * 
     * @param data the data buffer
     * 
     * @return true if the int read equals one.
     */
    public boolean readBoolean(ByteBuffer data) {
        return data.getInt() == 1;
    }

    /**
     * Reads the next int off the given ByteBuffer, interpreting it as a
     * size, and reads that many more bytes from the buffer, returning
     * the resulting byte array.
     * 
     * @param data the buffer to read from
     * 
     * @return a byte array matching the length of the first int read
     * from the buffer
     */
    public byte[] readBytes(ByteBuffer data) {
        int length = data.getInt();
        byte[] bytes = new byte[length];
        data.get(bytes);

        return bytes;
    }
    
    /**
     * Reads the next unsigned byte off the buffer, maps it to a type,
     * and reads the resulting object off the buffer as that type.
     * 
     * @param data 				the buffer to read from
     * 
     * @return an object matching the type specified from the initial
     * byte
     */
    public ByteWrapper readParamValue(ByteBuffer data) {
        int type = readUnsignedByte(data);
        if (type == TYPE_BOOLEAN) {
            return new BooleanByteWrapper(readBoolean(data));
        } 
        else if (type == TYPE_BYTE) {
            return new UnsignedByteWrapper(new UnsignedByte(
                                                    readUnsignedByte(data)));
        } 
        else if (type == TYPE_INTEGER) {
            return new IntegerByteWrapper(data.getInt());
        } 
        else if (type == TYPE_STRING) {
            String str = readString(data);
            str = str == null ? "" : str;
            return new StringByteWrapper(str);
        } 
        // unknown type
        return null;
    }
    
    public ByteWrapper createByteWrapper(Object obj) {
    	if (obj instanceof String) {
    	    return new StringByteWrapper((String) obj);
    	}
    	else if (obj instanceof Boolean) {
    	    return new BooleanByteWrapper((Boolean) obj);
    	}
    	else if (obj instanceof Integer) {
    	    return new IntegerByteWrapper((Integer) obj);
    	}
    	else if (obj instanceof UnsignedByte) {
    	    return new UnsignedByteWrapper((UnsignedByte) obj);
    	}
    	return null;
    }
    

    /**
     * Reads a regular old Java signed byte off the buffer and converts
     * it to an unsigned one (0-255).
     * 
     * @param data the buffer from which to read
     * 
     * @return the unsigned representation of the next byte off the
     * buffer (as an int).
     */
    public int readUnsignedByte(ByteBuffer data) {
        return data.get() & 0xff;
    }

}
