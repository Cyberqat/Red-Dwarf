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
package com.sun.sgs.test.client.matchmaker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.test.app.matchmaker.common.CommandList;

import static com.sun.sgs.test.app.matchmaker.common.CommandProtocol.*;

/**
 * An implementation of LobbyChannel that uses SGS ClientChannels for 
 * communication.
 * 
 */
public class LobbyChannelImpl extends AbstractChannelRoom implements 
                                        LobbyChannel, ClientChannelListener {

    private LobbyChannelListener listener;

    public LobbyChannelImpl(ClientChannel chan, MatchMakingClientImpl client) {
    	super(chan, client);
    }

    public void setListener(LobbyChannelListener listener) {
        this.listener = listener;
    }

    public void requestGameParameters() {
        CommandList list = new CommandList(GAME_PARAMETERS_REQUEST);

        try {
            sendCommand(list);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createGame(String name, String description, String password,
            HashMap<String, Object> gameParameters) {
    	
    	if (name == null || gameParameters == null) {
    	    return;
    	}
        CommandList list = new CommandList(CREATE_GAME);
        list.add(name);
        list.add(description);
        list.add(password != null);
        if (password != null) {
            list.add(password);
        }
        packGameParameters(list, gameParameters);
        try {
            sendCommand(list);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void receiveGameParameters(HashMap<String, Object> params) {
        if (listener != null) {
            listener.receivedGameParameters(params);
        }
    }

    void createGameFailed(String game, int errorCode) {
        if (listener != null) {
            listener.createGameFailed(game, errorCode);
        }
    }


    public void leftChannel(ClientChannel channel) {
        if (listener != null) {
            listener.leftLobby();
        }
    }

    public void receivedMessage(ClientChannel channel, SessionId sender, 
                                                            byte[] message) {
        if (listener == null) {
            return;
        }
        ByteBuffer data = ByteBuffer.allocate(message.length);
        data.put(message);
        data.flip();
        int command = protocol.readUnsignedByte(data);
        if (processCommand(command, listener, sender.toBytes(), data)) {
            return;
        }
        
        if (command == PLAYER_ENTERED_LOBBY) {
            byte[] id = protocol.readBytes(data);
            String name = protocol.readString(data);
            listener.playerEntered(id, name);
        } 
        else if (command == GAME_CREATED) {
            listener.gameCreated(readGameDescriptor(data));
        } 
        else if (command == PLAYER_JOINED_GAME) {
            byte[] id = protocol.readBytes(data);
            String username = protocol.readString(data);
            String gameName = protocol.readString(data);
            listener.playerJoinedGame(gameName, username);
        } 
        else if (command == GAME_DELETED) {
            listener.gameDeleted(readGameDescriptor(data));
        }
        else if (command == PLAYER_LEFT_LOBBY) {
            byte[] id = protocol.readBytes(data);
            
            listener.playerLeft(id);
        }
    }
    

}
