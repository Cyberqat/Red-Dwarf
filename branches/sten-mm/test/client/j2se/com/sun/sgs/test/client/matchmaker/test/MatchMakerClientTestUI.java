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
package com.sun.sgs.test.client.matchmaker.test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.test.app.matchmaker.common.UnsignedByte;
import com.sun.sgs.test.client.matchmaker.FolderDescriptor;
import com.sun.sgs.test.client.matchmaker.GameDescriptor;
import com.sun.sgs.test.client.matchmaker.GameChannel;
import com.sun.sgs.test.client.matchmaker.GameChannelListener;
import com.sun.sgs.test.client.matchmaker.LobbyChannel;
import com.sun.sgs.test.client.matchmaker.LobbyChannelListener;
import com.sun.sgs.test.client.matchmaker.MatchMakingClientListener;
import com.sun.sgs.test.client.matchmaker.LobbyDescriptor;
import com.sun.sgs.test.client.matchmaker.MatchMakingClientImpl;

import static com.sun.sgs.test.app.matchmaker.common.CommandProtocol.*;

/**
 * This class is a Swing UI that serves as a test harness for the Match 
 * Making client.
 * 
 */
public class MatchMakerClientTestUI extends JFrame implements
		                                MatchMakingClientListener {

    private MatchMakingClientImpl mmClient;
    private SimpleClient manager;
    private DefaultMutableTreeNode root;
    private JTree tree;
    private DefaultTreeModel treeModel;
    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;
    private HashMap<String, LobbyDescriptor> lobbyMap;
    private JTextArea incomingText;
    private JScrollPane incomingScrollPane;
    
    private JMenuItem connectItem;
    private JMenuItem disconnectItem;
    private JMenuItem locateUserItem;
    
    private JMenu lobbyMenu;
    private JMenu gameMenu;
    private JMenuItem joinLobby;
    private JMenuItem leaveLobby;
    private JMenuItem createGame;
    private JMenuItem joinGame;
    
    private String username;
    private String password;

    public MatchMakerClientTestUI(String[] args) {
    	super();
                
    	if (args.length > 0) {
    	    String[] arg0 = args[0].split("=");
            if (arg0.length > 0 && arg0[0].equals("-user")) {
                String[] credentials = arg0[1].split(":");
                username = credentials[0];
                password = credentials.length > 1 ? credentials[1] : "";
            }
        }
    
        setStatus("Not Connected");
        
        lobbyMap = new HashMap<String, LobbyDescriptor>();
        
        JPanel treePanel = doTreeLayout();
        
        incomingText = new JTextArea(3, 40);
        
        JSplitPane rightPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightPane.setDividerLocation(250);
        rightPane.setTopComponent(lobbyPanel = new LobbyPanel());
        rightPane.setBottomComponent(gamePanel = new GamePanel());
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(200);
        splitPane.setLeftComponent(treePanel);
        splitPane.setRightComponent(rightPane);
        
        final JTextField chatField = new JTextField(35);
        JButton sendTextButton = new JButton("Send Text");
        sendTextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if (gamePanel.hasGame()) {
            	    gamePanel.sendText(chatField.getText());
            	} else {
            	    lobbyPanel.sendText(chatField.getText());
            	}
            }
        });
        
        JButton sendPrivateTextButton = new JButton("Send Private Text");
        sendPrivateTextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (gamePanel.hasGame()) {
                    gamePanel.sendPrivateText(chatField.getText());
                } else {
                    lobbyPanel.sendPrivateText(chatField.getText());
                }
            }
        });        
        
        JPanel chatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chatPanel.add(chatField);
        chatPanel.add(sendTextButton);
        chatPanel.add(sendPrivateTextButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        incomingScrollPane = new JScrollPane(incomingText);
        bottomPanel.add(incomingScrollPane, BorderLayout.NORTH);
        bottomPanel.add(chatPanel, BorderLayout.CENTER);
        
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        createMenu();
        
        setBounds(300, 200, 720, 630);
        
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
            	if (manager != null && manager.isConnected()) {
            	    manager.logout(true);
                }
                System.exit(0);
            }
        });
            
        setVisible(true);
    }
	
    private void createMenu() {
        JMenuBar menuBar = new JMenuBar();
        
        connectItem = new JMenuItem("Connect");
        connectItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gamePanel.setParentUI(MatchMakerClientTestUI.this);
                lobbyPanel.setParentUI(MatchMakerClientTestUI.this);
                connect();
            }
        });
        connectItem.setAccelerator(KeyStroke.getKeyStroke("control alt C"));
        
        disconnectItem = new JMenuItem("Disconnect");
        disconnectItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                manager.logout(false);
            }
        });
        
        locateUserItem = new JMenuItem("Locate User");
        
        JMenu userMenu = new JMenu("User");
        userMenu.add(connectItem);
        userMenu.add(disconnectItem);
        userMenu.addSeparator();
        userMenu.add(locateUserItem);
        
        joinLobby = new JMenuItem("Join Lobby");
        joinLobby.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                LobbyDescriptor lobby = getSelectedLobby();
                if (lobby == null) {
                	return;
                }
                try {
                    mmClient.joinLobby(lobby.getName(), null);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        
        leaveLobby = new JMenuItem("Leave Lobby");
        leaveLobby.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mmClient.leaveLobby();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        
        createGame = new JMenuItem("Create Game");
        createGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GameInputDialog dialog = new GameInputDialog();
                GameDescriptor descriptor = dialog.promptForParameters(
                	new GameDescriptor(null, null, null, 0, false, 
                			lobbyPanel.getGameParameters()));
                
                lobbyPanel.createGame(descriptor, dialog.getPassword());
            }
        });
        
        joinGame = new JMenuItem("Join Game");
        joinGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GameDescriptor game = lobbyPanel.getSelectedGame();
                if (game == null) {
                	return;
                }
                String password = game.isPasswordProtected() ? 
                		JOptionPane.showInputDialog(null, 
                                        "Enter Password") : null;
                try {
                    mmClient.joinGame(game.getName(), password);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        
        lobbyMenu = new JMenu("Lobby");
        lobbyMenu.add(joinLobby);
        lobbyMenu.add(leaveLobby);
        lobbyMenu.addSeparator();
        lobbyMenu.add(createGame);
        lobbyMenu.add(joinGame);
        
        
        JMenuItem readyItem= new JMenuItem("Ready");
        readyItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gamePanel.ready();
            }
        });
        
        JMenuItem startGameItem = new JMenuItem("Start Game");
        startGameItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gamePanel.startGame();
            }
        });
        
        JMenuItem endGameItem = new JMenuItem("End Game");
        endGameItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               try {
                mmClient.completeGame(gamePanel.getName());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            }
        });
        
        JMenuItem bootItem = new JMenuItem("Boot Player");
        bootItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gamePanel.boot(false);
            }
        });
        
        JMenuItem banItem = new JMenuItem("Ban Player");
        banItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gamePanel.boot(true);
            }
        });
        
        JMenuItem updateGameItem = new JMenuItem("Update Game");
        updateGameItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GameInputDialog dialog = new GameInputDialog();
                GameDescriptor descriptor = dialog.promptForParameters(
                        gamePanel.getGameDescriptor());				
                gamePanel.updateGame(descriptor, dialog.getPassword());
            }
        });
        
        JMenuItem leaveItem = new JMenuItem("Leave Game");
        leaveItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mmClient.leaveGame();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });		
        
        gameMenu = new JMenu("Game");
        gameMenu.add(readyItem);
        gameMenu.add(startGameItem);
        gameMenu.add(endGameItem);
        gameMenu.addSeparator();
        gameMenu.add(updateGameItem);
        gameMenu.add(bootItem);
        gameMenu.add(banItem);
        gameMenu.add(leaveItem);
        
        menuBar.add(userMenu);
        menuBar.add(lobbyMenu);
        menuBar.add(gameMenu);
        
        setJMenuBar(menuBar);
        
        enableMenus(false);
    }
	
    /**
     * Enables/disables portions of the menus based on connected state.
     * 
     * @param connected		if true, then the app is connected to the server
     */
    private void enableMenus(boolean connected) {
        connectItem.setEnabled(!connected);
        disconnectItem.setEnabled(connected);
        locateUserItem.setEnabled(connected);
        lobbyMenu.setEnabled(connected);
        gameMenu.setEnabled(connected);
    }
    
    protected void receiveServerMessage(String message) {
        incomingText.setText(incomingText.getText() + message + "\n");
        incomingText.setCaretPosition(
                            incomingText.getDocument().getLength() - 1);
    }
    
    private LobbyDescriptor getSelectedLobby() {
        Object selection = tree.getLastSelectedPathComponent();
        LobbyDescriptor lobby = null;
        if (selection != null && selection instanceof LobbyNode) {
        	lobby = ((LobbyNode) selection).getDescriptor();
        }
        return lobby;
    }
    
    private JPanel doTreeLayout() {
    	treeModel = createTreeModel();
    	tree = new JTree(treeModel);
    	tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
            	TreePath path = e.getNewLeadSelectionPath();
            	if (path != null) {
            		TreeNode node = (TreeNode) path.getLastPathComponent();
            	}
            }
    	});
    	tree.setRootVisible(true);
    	tree.setShowsRootHandles(true);
    
    	JPanel p = new JPanel(new BorderLayout());
    	p.add(new JScrollPane(tree), BorderLayout.CENTER);
    
    	return p;
    }
    
    public DefaultTreeModel createTreeModel() {
    	root = new DefaultMutableTreeNode("Folders");
    
    	return new DefaultTreeModel(root);
    }
    
    private void setStatus(String status) {
    	setTitle("Match Maker Client Test: " + status);
    }

    public void connect() {
    	try {
            mmClient = new MatchMakingClientImpl();
            manager = new SimpleClient(mmClient);
            mmClient.setListener(this);
            mmClient.setSimpleClient(manager);
            Properties props = new Properties();
            props.put("host", "127.0.0.1");
            props.put("port", "2502");
            manager.login(props);
    	} catch (Exception e) {
    	    e.printStackTrace();
    	    return;
    	}
    }

    public void listedFolder(String folderName, FolderDescriptor[] subFolders,
    		LobbyDescriptor[] lobbies) {
    
    	DefaultMutableTreeNode node = findFolderNode(folderName, root);
    	if (node == null) {
    	    node = root;
    	}
    	for (FolderDescriptor f : subFolders) {
    	    treeModel.insertNodeInto(new FolderNode(f), node, node
    				.getChildCount());
    	}
    	for (LobbyDescriptor l : lobbies) {
            treeModel.insertNodeInto(new LobbyNode(l), node, node
            		.getChildCount());
            lobbyMap.put(l.getChannelName(), l);
    	}
    	for (FolderDescriptor f : subFolders) {
            
            try {
                mmClient.listFolder(f.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    }

    private FolderNode findFolderNode(String folderName, 
                                                DefaultMutableTreeNode node) {
    	for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof FolderNode) {
            	FolderNode curNode = (FolderNode) node.getChildAt(i);
            	if (curNode.toString().equals(folderName)) {
            	    return curNode;
            	} else if (curNode.getChildCount() > 0) {
            	    FolderNode subFolder = findFolderNode(folderName, curNode);
            	    if (subFolder != null) {
            		return subFolder;
            	    }
            	}
            }
    	}
    	return null;
    }

    public void joinedLobby(LobbyChannel channel) {
    	lobbyPanel.setLobby(channel);
    }
    
    public void joinedGame(GameChannel channel) {
    	joinGame.setText("Leave Game");
    	gamePanel.setGame(channel, lobbyPanel.gameMap.get(channel.getName()));
    
    }
    
    public void connected() {
    	setStatus("Connected");
    	enableMenus(true);
    	try {
            mmClient.listFolder(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void connectionFailed(String reason) {
        JOptionPane.showMessageDialog(this, "Connection Failed: " + reason, 
                        "Connection Failed", JOptionPane.ERROR_MESSAGE);
    }
    
    public void disconnected() {
    	setStatus("Disconnected");
    	enableMenus(false);
    }
    
    /*
     * Inherited JDoc
     */
    public void error(int errorCode) {
    	String message = mapErrorCode(errorCode);
    	receiveServerMessage("<ERROR> " + message);
    }
    
    private String mapErrorCode(int errorCode) {
    	String message = null;
    
    	if (errorCode == NOT_CONNECTED_LOBBY) {
    	   message = "Not connected to a lobby";
    	} else if (errorCode == NOT_CONNECTED_GAME) {
    	    message = "Not connected to a game";
    	} else if (errorCode == CONNECTED_GAME) {
    	    message = "Already connected to a game";
    	} else if (errorCode == CONNECTED_LOBBY) {
    	    message = "Already connected to a lobby";
    	} else if (errorCode == PLAYER_NOT_HOST) {
    	    message = "The requested action can only be performed by the host";
    	} else if (errorCode == PLAYERS_NOT_READY) {
    	    message = "The game cannot start until all players are ready";
    	} else if (errorCode == LESS_THAN_MIN_PLAYERS) {
    	    message = "Too few players to start game";
    	} else if (errorCode == GREATER_THAN_MAX_PLAYERS) {
    	    message = "Too many players to start game";
    	} else if (errorCode == MAX_PLAYERS) {
    	    message = "Already at max players";
    	} else if (errorCode == INCORRECT_PASSWORD) {
    	    message = "Incorrect Password";
    	} else if (errorCode == INVALID_GAME) {
    	    message = "Invalid Game";
    	} else if (errorCode == INVALID_GAME_NAME) {
    	    message = "Invalid Game Name";
    	} else if (errorCode == INVALID_LOBBY) {
    	    message = "Invalid Lobby";
    	} else if (errorCode == BOOT_NOT_SUPPORTED) {
    	    message = "Lobby does not support booting of players";
    	} else if (errorCode == BAN_NOT_SUPPORTED) {
    	    message = "Lobby does not support banning of players";
    	} else if (errorCode == BOOT_SELF) {
    	    message = "You can't boot yourself";
    	} else if (errorCode == PLAYER_BANNED) {
    	    message = "You have been banned from this game";
    	} else {
    	    message = "Unknown Error";
    	}
    
    	return message;
    }

    
    public static void main(String[] args) {
    	if (args.length < 1) {
    	    System.out.println("Usage: java MatchMakerClientTestUI " +
                                "-user=<username>:<password>");
            System.exit(0);
        }
        new MatchMakerClientTestUI(args);
    }
    
    private class FolderNode extends DefaultMutableTreeNode {
    
    	private FolderDescriptor folder;
    
    	public FolderNode(FolderDescriptor f) {
    	    folder = f;
    	}
    
    	public boolean isLeaf() {
    	    return false;
    	}
    
    	public String toString() {
    	    return folder.getName();
    	}
    
    }

    private class LobbyNode extends DefaultMutableTreeNode {
    
    	private LobbyDescriptor lobby;
    
    	public LobbyNode(LobbyDescriptor l) {
    	    lobby = l;
    
    	}
    
    	public boolean isLeaf() {
    	    return true;
    	}
    
    	public String toString() {
    	    return lobby.getName();
    	}
    
    	public LobbyDescriptor getDescriptor() {
    	    return lobby;
    	}
    
    }

    private class LobbyPanel extends ChannelRoomPanel implements
    		LobbyChannelListener {
    
    	private LobbyChannel channel;
    
    	private DefaultListModel userListModel;
    
    	private JList userList;
    
    	private JList gameList;
    
    	private DefaultListModel gameListModel;
    
    	private GameParametersTableModel parametersModel;
    
    	private HashMap<String, Object> gameParameters;
    
    	private JCheckBox bootBox;
    
    	private JCheckBox banBox;
    
    	HashMap<String, GameDescriptor> gameMap;
    
    	LobbyPanel() {
            super("Lobby");
            gameMap = new HashMap<String, GameDescriptor>();
            
            int listHeight = 150;
            JTable gameParametersTable = new GameParametersTable(
            		parametersModel = new GameParametersTableModel(), 
                                                                        false);
            JScrollPane tablePane = new JScrollPane(gameParametersTable);
            tablePane.setPreferredSize(new Dimension(200, listHeight));
            
            JPanel gameParametersPanel = new JPanel(new BorderLayout());
            gameParametersPanel.add(new JLabel("Game Params"),
            		BorderLayout.NORTH);
            gameParametersPanel.add(tablePane, BorderLayout.CENTER);
            
            JScrollPane gameListPane = new JScrollPane(gameList = new JList(
            		gameListModel = new DefaultListModel()));
            gameListPane.setPreferredSize(new Dimension(150, listHeight));
            
            JPanel gameListPanel = new JPanel(new BorderLayout());
            gameListPanel.add(new JLabel("Game List"), BorderLayout.NORTH);
            gameListPanel.add(gameListPane, BorderLayout.CENTER);
            
            JScrollPane userListPane = new JScrollPane(userList = new JList(
            		userListModel = new DefaultListModel()));
            userListPane.setPreferredSize(new Dimension(150, listHeight));
            
            JPanel userListPanel = new JPanel(new BorderLayout());
            userListPanel.add(new JLabel("User List"), BorderLayout.NORTH);
            userListPanel.add(userListPane, BorderLayout.CENTER);
            
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(userListPanel, BorderLayout.WEST);
            bottomPanel.add(gameListPanel, BorderLayout.CENTER);
            bottomPanel.add(gameParametersPanel, BorderLayout.EAST);
            
            bootBox = new JCheckBox("Can Host Boot?");
            bootBox.setEnabled(false);
            banBox = new JCheckBox("Can Host Ban?");
            banBox.setEnabled(false);
            
            addToCenter(bootBox);
            addToCenter(banBox);
            
            add(bottomPanel, BorderLayout.SOUTH);
    	}
    
    	public GameDescriptor getSelectedGame() {
    	    return (GameDescriptor) gameList.getSelectedValue();
    	}
    
    	void setLobby(LobbyChannel channel) {
            LobbyPanel.this.channel = channel;
            channel.setListener(LobbyPanel.this);
            LobbyDescriptor lobby = lobbyMap.get(channel.getName());
            setDetails(lobby.getName(), lobby.getDescription(), lobby
            		.isPasswordProtected(), lobby.getMaxUsers());
            channel.requestGameParameters();
    	}
    
    	public void leftLobby() {
    	    receiveServerMessage("Left Lobby");
            super.reset();
            LobbyPanel.this.channel = null;
            gameListModel.removeAllElements();
            userListModel.removeAllElements();
            gameMap.clear();
            parametersModel.clear();
    
    	}
    
    	void createGame(GameDescriptor game, String password) {
            if (channel != null && game != null) {
            	channel.createGame(game.getName(), game.getDescription(),
            			password.equals("") ? null : password,
            					game.getGameParameters());
            }
    	}
    
    	void sendText(String message) {
            if (channel != null) {
            	try {
                    channel.sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    	}
        
        void sendPrivateText(String message) {
            if (channel != null) {
                byte[] userID = 
                        lookupUserID((String) userList.getSelectedValue());
                try {
                    channel.sendPrivateText(userID, message);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }        
    
    	private void removeGame(GameDescriptor game) {
            gameMap.remove(game.getChannelName());
            gameListModel.removeElement(game);
    	}
    
    	public void playerEntered(byte[] id, String name) {
            super.playerEntered(id, name);
            userListModel.addElement(name);
    	}
    
    	public void playerLeft(byte[] player) {
            String name = removePlayer(player);
            userListModel.removeElement(name);
    	}
    
    	public void receivedGameParameters(HashMap<String, Object> parameters) {
            gameParameters = parameters;
            Iterator<String> iterator = parameters.keySet().iterator();
            while (iterator.hasNext()) {
            	String curKey = iterator.next();
            	parametersModel.addParameter(curKey, parameters.get(curKey));
            }
            parametersModel.fireTableDataChanged();
    	}
    
    	public HashMap<String, Object> getGameParameters() {
    	    return parametersModel.getGameParameters();
    	}
    
    	public void playerBootedFromGame(String booter, String bootee,
    			boolean isBanned) {
    
    	    super.playerBootedFromGame(booter, bootee, isBanned);
    
    	}
    
    	public void createGameFailed(String name, int errorCode) {
            String reason = null;
            if (errorCode == NOT_CONNECTED_LOBBY) {
            	reason = "Not connected to a lobby";
            } else if (errorCode == INVALID_GAME_PARAMETERS) {
            	reason = "Invalid game parameters";
            } else if (errorCode == INVALID_GAME_NAME) {
            	reason = "Invalid game name";
            } else if (errorCode == CONNECTED_GAME) {
            	reason = "Already connected to a game";
            } else if (errorCode == GAME_EXISTS) {
            	reason = "Game already exists";
            } else {
            	reason = "Unknown Error";
            }
            receiveServerMessage("LobbyPanel createGameFailed: " + name
            		+ " reason " + reason);
    	}
    
    	public void gameCreated(GameDescriptor game) {
            gameListModel.addElement(game);
            gameMap.put(game.getChannelName(), game);
            receiveServerMessage("Game Created: " + game.getName());
    	}
    
    	public void playerJoinedGame(String gameID, String player) {
    	    receiveServerMessage("<Lobby> " + player
    				+ " Joined Game ");
    	}
    
    	public void gameStarted(GameDescriptor game) {
    	    receiveServerMessage("<Lobby> Game Started " + game.getName());
    	    removeGame(game);
    	}
    
    	public void gameDeleted(GameDescriptor game) {
    	    receiveServerMessage("<Lobby> Game Deleted " + game.getName());
    	    removeGame(game);
    	}
    
    	public void gameUpdated(GameDescriptor game) {
    	    super.gameUpdated(game);
    	}
    }
    
    private class GameParametersTable extends JTable {
    
    	private boolean canEdit;
    
    	GameParametersTable(GameParametersTableModel model, boolean canEdit) {
    		super(model);
    		GameParametersTable.this.canEdit = canEdit;
    	}
    
    	public boolean isCellEditable(int row, int column) {
    		return canEdit && column == 1;
    	}
    }

    private class GameParametersTableModel extends AbstractTableModel {

        private List<String> params;

        private List values;

        GameParametersTableModel() {
            params = new LinkedList<String>();
            values = new LinkedList();
        }

	public void setData(HashMap<String, Object> map) {
            clear();
            for (Entry<String, Object> curEntry : map.entrySet()) {
            	addParameter(curEntry.getKey(), curEntry.getValue());
            }
            fireTableDataChanged();
	}

	public void addParameter(String param, Object value) {
            params.add(param);
            values.add(value);
	}

	public int getColumnCount() {
	    return 2;
	}

	public HashMap<String, Object> getGameParameters() {
            HashMap<String, Object> map = new HashMap<String, Object>();
            for (int i = 0; i < params.size(); i++) {
            	map.put(params.get(i), values.get(i));
            }
            
            return map;
	}
	
        private String byteArrayToString(byte[] bytes) {
            StringBuffer buffer = new StringBuffer();
            for (byte b : bytes) {
            	buffer.append((int) (b & 0xFF));
            }
            return buffer.toString();
        }
    
        public void setValueAt(Object value, int row, int col) {
            String strValue = (String) value;
            if (col != 1) {
            	return;
            }
            Object oldValue = values.get(row);
            Object newValue = null;
            if (oldValue instanceof Integer) {
            	newValue = Integer.parseInt(strValue);
            } else if (oldValue instanceof Boolean) {
            	newValue = Boolean.parseBoolean(strValue);
            } else if (oldValue instanceof UnsignedByte) {
            	newValue = new UnsignedByte(Integer.parseInt(strValue));
            } else if (oldValue instanceof byte[]) { 
                return;
            } else {
            	newValue = strValue;
            }
            values.set(row, newValue);

        }

        public int getRowCount() {
            return params.size();
        }
        
        public void clear() {
            params.clear();
            values.clear();
            fireTableDataChanged();
        }
        
        public Object getValueAt(int row, int col) {
            if (col == 0) {
                return params.get(row);
            }
            Object value = values.get(row);
            if (value instanceof byte[]) {
                return byteArrayToString((byte[]) value);
            }
            return value;
        }
        
        public String getColumnName(int col) {
            return col == 0 ? "Parameter" : "Value";
        }
    }

    private class GamePanel extends ChannelRoomPanel implements
		GameChannelListener {

        private GameChannel channel;
        private GameDescriptor descriptor;
        private HashMap<String, String> userMap;
        private GameUsersTableModel userTableModel;
        private GameParametersTableModel parametersModel;
        private JTable userTable;
        
        GamePanel() {
            super("Game Room");

            int listHeight = 100;
            JTable gameParametersTable = new GameParametersTable(
            		parametersModel = new GameParametersTableModel(), 
                                                                        false);
            
            JScrollPane tablePane = new JScrollPane(gameParametersTable);
            tablePane.setPreferredSize(new Dimension(250, listHeight));
            
            JPanel gameParametersPanel = new JPanel(new BorderLayout());
            gameParametersPanel.add(new JLabel("Game Params"),
            		BorderLayout.NORTH);
            gameParametersPanel.add(tablePane, BorderLayout.CENTER);
            
            JScrollPane userListPane = new JScrollPane(userTable = new JTable(
            		userTableModel = new GameUsersTableModel()));
            userListPane.setPreferredSize(new Dimension(220, listHeight));
            
            JPanel userListPanel = new JPanel(new BorderLayout());
            userListPanel.add(new JLabel("User List"), BorderLayout.NORTH);
            userListPanel.add(userListPane, BorderLayout.CENTER);
            
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(userListPanel, BorderLayout.WEST);
            bottomPanel.add(gameParametersPanel, BorderLayout.EAST);
            
            add(bottomPanel, BorderLayout.SOUTH);
        }

        public void setGame(GameChannel channel, GameDescriptor descriptor) {
            GamePanel.this.channel = channel;
            GamePanel.this.descriptor = descriptor;
            channel.setListener(GamePanel.this);
            updateGameParameters(descriptor.getGameParameters());
            setDetails(descriptor.getName(), descriptor.getDescription(),
                    descriptor.isPasswordProtected(), descriptor.getMaxUsers());
        }

        public void boot(boolean shouldBan) {
            if (channel != null && getSelectedUserName() != null) {
            	channel.boot(getSelectedUserName(), shouldBan);
            }
        }
        
        public boolean hasGame() {
            return GamePanel.this.channel != null;
        }
        
        void sendText(String message) {
            if (channel != null) {
                try {
                    channel.sendText(message);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        
        void sendPrivateText(String message) {
            if (channel != null) {
                byte[] userID = lookupUserID(getSelectedUserName());
                try {
                    channel.sendPrivateText(userID, message);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }        
        
        public void updateGame(GameDescriptor gameDescriptor, String password) {
            if (channel != null && gameDescriptor != null) {
            	channel.updateGame(gameDescriptor.getName(), 
            				gameDescriptor.getDescription(), 
            				(password.equals("") ? null : password), 
            				gameDescriptor.getGameParameters());
            }
        }
        
        private String getSelectedUserName() {
            int row = userTable.getSelectedRow();
            if (row >= 0) {
            	return (String) userTableModel.getValueAt(row, 0);
            }
            return "";
        }
        
        public void leftGame() {
            joinGame.setText("Join Game");
            receiveServerMessage("Left Game");
            resetGame();
        }
        
        public void resetGame() {
            super.reset();
            GamePanel.this.channel = null;
            GamePanel.this.descriptor = null;
            userTableModel.clear();
            parametersModel.clear();
            joinGame.setText("Join Game");
        }
        
        public void ready() {
            if (channel != null) {
            	channel.ready(descriptor, true);
            }
        }
        
        public void startGame() {
            if (channel != null) {
            	channel.startGame();
            }
        }
        
        private void updateGameParameters(HashMap<String, Object> parameters) {
            Iterator<String> iterator = parameters.keySet().iterator();
            while (iterator.hasNext()) {
            	String curKey = iterator.next();
            	parametersModel.addParameter(curKey, parameters.get(curKey));
            }
            parametersModel.fireTableDataChanged();
        }
        
        public void playerEntered(byte[] id, String name) {
            super.playerEntered(id, name);
            userTableModel.addUser(name);
        }
        
        public void playerLeft(byte[] player) {
            String name = removePlayer(player);
            userTableModel.removeUser(name);
        }
        
        public void playerBootedFromGame(String booter, String bootee,
        		boolean isBanned) {
        
            super.playerBootedFromGame(booter, bootee, isBanned);
        
        }
        
        public void playerReady(String player, boolean ready) {
            receiveServerMessage("<Game Room> " + player + " is "
        		+ (ready ? "" : "not ") + "ready");
            userTableModel.updateReady(player, ready);
        }
        
        public void startGameFailed(String reason) {
            receiveServerMessage("<Game Room> Start Game Failed: " + reason);
        
        }
        
        public void gameStarted(GameDescriptor game) {
            receiveServerMessage("<Game Room> Game Started " + game.getName());
        }
        
        public void gameCompleted() {
            resetGame();
            receiveServerMessage("<Game Room> Game Completed");
        }
        
        public void bootFailed(String player, boolean isBanned, int errorCode) {
            receiveServerMessage("<Game Room> Boot attempt of " + player 
                    + " failed " + mapErrorCode(errorCode));
        }
        
        private void updateDescriptor(GameDescriptor game) {
            GamePanel.this.descriptor = game;
            setDetails(game.getName(), game.getDescription(), 
        			game.isPasswordProtected(), game.getMaxUsers());
            parametersModel.setData(game.getGameParameters());
        }
        
        public void gameUpdated(GameDescriptor game) {
            super.gameUpdated(game);
            updateDescriptor(game);
        }
        
        public void updateGameFailed(GameDescriptor game, int errorCode) {
            receiveServerMessage("<Game Room> Game Update Failed: " + 
                            game.getName() + ", error: " + errorCode + " " + 
                            mapErrorCode(errorCode));
        }
            
        public GameDescriptor getGameDescriptor() {
            return descriptor;
        }
    }
        
    private class GameUsersTableModel extends AbstractTableModel {
    
        private List<String> usernames;
        
        private List<Boolean> readyState;
        
        GameUsersTableModel() {
            usernames = new LinkedList<String>();
            readyState = new LinkedList<Boolean>();
        }
        
        public void addUser(String userName) {
            usernames.add(userName);
            readyState.add(false);
            fireTableDataChanged();
        }
        
        public void removeUser(String userName) {
            int index = usernames.indexOf(userName);
            if (index == -1) {
            	return;
            }
            usernames.remove(userName);
            readyState.remove(index);
            fireTableDataChanged();
        }
        
        public void clear() {
            usernames.clear();
            readyState.clear();
            fireTableDataChanged();
        }
        
        public int getColumnCount() {
            return 2;
        }
        
        public int getRowCount() {
            return usernames.size();
        }
        
        public void updateReady(String userName, boolean ready) {
            int index = usernames.indexOf(userName);
            if (index == -1) {
            	return;
            }
            readyState.set(index, ready);
            fireTableDataChanged();
        }
        
        public Object getValueAt(int row, int col) {
            if (col == 0) {
            	return usernames.get(row);
            }
            return readyState.get(row);
        }
        
        public String getColumnName(int col) {
            return col == 0 ? "User" : "Ready?";
        }
    }
        
    private class GameInputDialog {

        private String password;
        
        private GameDescriptor descriptor;
        
        public GameDescriptor promptForParameters(GameDescriptor game) {
	JPanel panel = new JPanel(new BorderLayout());
	JTextField nameField = new JTextField(game.getName(), 20);
	JTextField descriptionField = new JTextField(game.getDescription(), 20);
	JTextField passwordField = new JTextField(20);

	JPanel topPanel = new JPanel(new GridLayout(3, 2));
	topPanel.add(new JLabel("Name"));
            topPanel.add(nameField);
            topPanel.add(new JLabel("Description"));
            topPanel.add(descriptionField);
            topPanel.add(new JLabel("Password"));
            topPanel.add(passwordField);
            
            panel.add(topPanel, BorderLayout.NORTH);
            
            GameParametersTableModel gameTableModel = 
                                            new GameParametersTableModel();
            
            gameTableModel.setData(game.getGameParameters());
            JScrollPane userListPane = new JScrollPane(new GameParametersTable(
            		gameTableModel, true));
            userListPane.setPreferredSize(new Dimension(220, 150));
            
            panel.add(userListPane, BorderLayout.SOUTH);
            
            JOptionPane optionPane = new JOptionPane(panel,
            	JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            
            JDialog dialog = optionPane.createDialog(null,
            		"Specify Game Parameters");
            dialog.setVisible(true);
            Integer ret = (Integer) optionPane.getValue();
            
            if (ret == null || ret == JOptionPane.CANCEL_OPTION) {
            	return null; 
            }
            password = passwordField.getText();
            
            return new GameDescriptor(nameField.getText(), 
                                        descriptionField.getText(), 
                                        null, 0, !password.equals(""), 
                                        gameTableModel.getGameParameters());
        }

        public String getPassword() {
            return password;
        }
    }

    public PasswordAuthentication getPasswordAuthentication() {
        PasswordAuthentication auth = new PasswordAuthentication(username, 
                                    password.toCharArray());
        return auth;
    }

}
