/*
 * Copyright (C) 2015 HERU
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.socket;

import java.io.*;
import java.net.*;

public class SocketServer implements Runnable {

    public ServerThread clients[];
    public ServerSocket server = null;
    public Thread thread = null;
    public int clientCount = 0, port = 8099;
    public ServerFrame ui;
    public Database db;

    public SocketServer(ServerFrame frame) {

        this.clients = new ServerThread[50];
        this.ui = frame;
        this.db = new Database(ui.filePath);

        try {

            this.server = new ServerSocket(port);
            this.port = server.getLocalPort();
            this.ui.jTextArea1.append("Server started. IP : " + InetAddress.getLocalHost() + ", Port : " + server.getLocalPort());
            start();
        } catch (IOException ioe) {
            this.ui.jTextArea1.append("Can not bind to port : " + port + "\nRetrying");
            this.ui.RetryStart(0);
        }
    }

    public SocketServer(ServerFrame frame, int Port) {

        //this.clients = new ServerThread[50];
        this.ui = frame;
        this.port = Port;
        this.db = new Database(ui.filePath);

        try {
            this.server = new ServerSocket(port);
            this.port = server.getLocalPort();
            this.ui.jTextArea1.append("Server started. IP : " + InetAddress.getLocalHost() + ", Port : " + server.getLocalPort());
            start();
        } catch (IOException ioe) {
            this.ui.jTextArea1.append("\nCan not bind to port " + port + ": " + ioe.getMessage());
        }
    }

    public void run() {
        while (thread != null) {
            try {
                this.ui.jTextArea1.append("\nWaiting for a client ...");
                addThread(server.accept());
                 
            } catch (Exception ioe) {
                this.ui.jTextArea1.append("\nServer accept error: \n");
                this.ui.RetryStart(0);
            }
        }
    }

    public void start() {
        if (thread == null) {
            this.thread = new Thread(this);
            this.thread.start();
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        if (thread != null) {
            this.thread.stop();
            this.thread = null;
        }
    }

    private int findClient(int ID) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].getID() == ID) {
                return i;
            }
        }
        return -1;
    }

    public synchronized void handle(int ID, Message msg) {
        if (msg.content.equals(".bye")) {
            Announce("signout", "SERVER", msg.sender);
            remove(ID);
        } else {
            if (msg.type.equals("login")) {
                if (findUserThread(msg.sender) == null) {
                    if (db.checkLogin(msg.sender, msg.content)) {
                        this.clients[findClient(ID)].username = msg.sender;
                        this.clients[findClient(ID)].send(new Message("login", "SERVER", "TRUE", msg.sender));
                        Announce("newuser", "SERVER", msg.sender);
                        SendUserList(msg.sender);
                    } else {
                        this.clients[findClient(ID)].send(new Message("login", "SERVER", "FALSE", msg.sender));
                    }
                } else {
                    this.clients[findClient(ID)].send(new Message("login", "SERVER", "FALSE", msg.sender));
                }
            } else if (msg.type.equals("message")) {
                if (msg.recipient.equals("All")) {
                    Announce("message", msg.sender, msg.content);
                } else {
                    findUserThread(msg.recipient).send(new Message(msg.type, msg.sender, msg.content, msg.recipient));
                    this.clients[findClient(ID)].send(new Message(msg.type, msg.sender, msg.content, msg.recipient));
                }
            } else if (msg.type.equals("test")) {
                this.clients[findClient(ID)].send(new Message("test", "SERVER", "OK", msg.sender));
            } else if (msg.type.equals("signup")) {
                if (findUserThread(msg.sender) == null) {
                    if (!db.userExists(msg.sender)) {
                        this.db.addUser(msg.sender, msg.content);
                        this.clients[findClient(ID)].username = msg.sender;
                        this.clients[findClient(ID)].send(new Message("signup", "SERVER", "TRUE", msg.sender));
                        this.clients[findClient(ID)].send(new Message("login", "SERVER", "TRUE", msg.sender));
                        Announce("newuser", "SERVER", msg.sender);
                        SendUserList(msg.sender);
                    } else {
                        this.clients[findClient(ID)].send(new Message("signup", "SERVER", "FALSE", msg.sender));
                    }
                } else {
                    this.clients[findClient(ID)].send(new Message("signup", "SERVER", "FALSE", msg.sender));
                }
            } else if (msg.type.equals("upload_req")) {
                if (msg.recipient.equals("All")) {
                    this.clients[findClient(ID)].send(new Message("message", "SERVER", "Uploading to 'All' forbidden", msg.sender));
                } else {
                    findUserThread(msg.recipient).send(new Message("upload_req", msg.sender, msg.content, msg.recipient));
                }
            } else if (msg.type.equals("upload_res")) {
                if (!msg.content.equals("NO")) {
                    String IP = findUserThread(msg.sender).socket.getInetAddress().getHostAddress();
                    findUserThread(msg.recipient).send(new Message("upload_res", IP, msg.content, msg.recipient));
                } else {
                    findUserThread(msg.recipient).send(new Message("upload_res", msg.sender, msg.content, msg.recipient));
                }
            }
        }
    }

    public void Announce(String type, String sender, String content) {
        Message msg = new Message(type, sender, content, "All");
        for (int i = 0; i < clientCount; i++) {
            this.clients[i].send(msg);
        }
    }

    public void SendUserList(String toWhom) {
        for (int i = 0; i < clientCount; i++) {
            findUserThread(toWhom).send(new Message("newuser", "SERVER", clients[i].username, toWhom));
        }
    }

    public ServerThread findUserThread(String usr) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].username.equals(usr)) {
                return clients[i];
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    public synchronized void remove(int ID) {
        int pos = findClient(ID);
        if (pos >= 0) {
            ServerThread toTerminate = clients[pos];
            this.ui.jTextArea1.append("\nRemoving client thread " + ID + " at " + pos);
            if (pos < clientCount - 1) {
                for (int i = pos + 1; i < clientCount; i++) {
                    clients[i - 1] = clients[i];
                }
            }
            this.clientCount--;
            try {
                toTerminate.close();
            } catch (IOException ioe) {
                this.ui.jTextArea1.append("\nError closing thread: " + ioe);
            }
            toTerminate.stop();
        }
    }

    private synchronized void addThread(Socket socket) throws IOException {

        if (clientCount < clients.length) {
            if (socket.getPort() < 50000) {
                
                this.ui.jTextArea1.append("\nClient accepted: " + socket);
                this.clients[clientCount] = new ServerThread(this, socket);
                System.out.println(clients[clientCount].ID);

                try {
                    this.clients[clientCount].open();
                    this.clients[clientCount].start();
                    this.clientCount++;
                } catch (IOException ioe) {
                    this.ui.jTextArea1.append("\nError opening thread: " + ioe);
                }
            }else{
                this.ui.jTextArea1.append("\nFlood Client rejected: " + socket);
                socket.close();
            }
        } else {
            this.ui.jTextArea1.append("\nClient refused: maximum " + clients.length + " reached.");
        }
    }
}
