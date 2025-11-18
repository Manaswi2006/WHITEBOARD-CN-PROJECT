package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Multi-client server for chat + whiteboard + polls.
 *
 * Protocol (client → server):
 *   JOIN|username
 *   CHAT|username|message
 *   DRAW|x1|y1|x2|y2|rgb|stroke
 *   CLEAR|
 *   CURSOR|username|x|y
 *   BOARD_LOCK|true/false         (only teacher is honoured)
 *   POLL_CREATE|username|pollId|question|opt1;opt2;...
 *   POLL_VOTE|username|pollId|optionIndex
 *
 * Protocol (server → clients):
 *   CHAT|username|message
 *   DRAW|x1|y1|x2|y2|rgb|stroke
 *   CLEAR|
 *   CURSOR|username|x|y
 *   USERLIST|u1,u2,u3
 *   ROLE|TEACHER or ROLE|STUDENT
 *   BOARD_LOCK|true/false
 *   POLL_CREATE|username|pollId|question|opt1;opt2;...
 *   POLL_RESULTS|pollId|c0;c1;c2;...
 */
public class WhiteboardServer {

    private static int PORT = 5001;

    // All connected clients
    private static final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    // Usernames currently in the room
    private static final Set<String> usernames =
            Collections.synchronizedSet(new HashSet<>());

    // Teacher / board state
    private static boolean teacherAssigned = false;
    private static boolean boardLocked = false;

    // Simple single active poll
    private static class Poll {
        String id;
        String question;
        String[] options;
        int[] counts;
        Set<String> votedUsers = new HashSet<>();
    }

    public WhiteboardServer(int port){
        this.PORT = port;
    }

    private static Poll activePoll = null;


    public static void main(String[] args) {
        System.out.println("Whiteboard server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void startServer() {
        System.out.println("Whiteboard server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void startServer(int port) {
        System.out.println("Whiteboard server starting on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast a message to all clients (null exclude = send to everyone)
    public static void broadcast(String message, ClientHandler exclude) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (exclude != null && client == exclude) continue;
                client.sendMessage(message);
            }
        }
    }

    private static void broadcastUserList() {
        synchronized (usernames) {
            StringBuilder sb = new StringBuilder("USERLIST|");
            boolean first = true;
            for (String u : usernames) {
                if (!first) sb.append(",");
                sb.append(u);
                first = false;
            }
            broadcast(sb.toString(), null);
        }
    }

    private static void broadcastBoardLock() {
        broadcast("BOARD_LOCK|" + boardLocked, null);
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    // ---- Poll handling ----
    private static synchronized void handlePollCreate(String line) {
        // POLL_CREATE|username|pollId|question|opt1;opt2;...
        String[] parts = line.split("\\|", 5);
        if (parts.length < 5) return;

        String pollId = parts[2];
        String question = parts[3];
        String[] opts = parts[4].split(";");

        Poll p = new Poll();
        p.id = pollId;
        p.question = question;
        p.options = opts;
        p.counts = new int[opts.length];
        p.votedUsers = new HashSet<>();

        activePoll = p;

        // Let everyone know about the new poll
        broadcast(line, null);
    }

    private static synchronized void handlePollVote(String line) {
        // POLL_VOTE|username|pollId|optionIndex
        if (activePoll == null) return;

        String[] parts = line.split("\\|");
        if (parts.length < 4) return;

        String username = parts[1];
        String pollId = parts[2];
        if (!activePoll.id.equals(pollId)) return;

        int idx;
        try {
            idx = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }

        if (idx < 0 || idx >= activePoll.counts.length) return;
        if (activePoll.votedUsers.contains(username)) {
            return;
        }
        activePoll.votedUsers.add(username);
        activePoll.counts[idx]++;

        StringBuilder sb = new StringBuilder("POLL_RESULTS|");
        sb.append(activePoll.id).append("|");
        for (int i = 0; i < activePoll.counts.length; i++) {
            if (i > 0) sb.append(";");
            sb.append(activePoll.counts[i]);
        }
        broadcast(sb.toString(), null);
    }

    // ---- Client handler ----
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username = "Anonymous";
        private boolean isTeacher = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void sendMessage(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream()), true);

                // First line from client should be JOIN|username
                String joinLine = in.readLine();
                if (joinLine != null && joinLine.startsWith("JOIN|")) {
                    String[] parts = joinLine.split("\\|", 2);
                    if (parts.length == 2 && !parts[1].isEmpty()) {
                        username = parts[1];
                    }
                }

                synchronized (usernames) {
                    username = ensureUniqueUsername(username);
                    usernames.add(username);
                }
                sendMessage("USERNAME|" + username);

                // Assign teacher role to the first client
                synchronized (WhiteboardServer.class) {
                    if (!teacherAssigned) {
                        teacherAssigned = true;
                        isTeacher = true;
                        sendMessage("ROLE|TEACHER");
                    } else {
                        sendMessage("ROLE|STUDENT");
                    }
                    // Send current board-lock state
                    sendMessage("BOARD_LOCK|" + boardLocked);
                }

                System.out.println("User joined: " + username +
                        (isTeacher ? " (TEACHER)" : ""));
                broadcast("CHAT|SERVER|" + username + " joined the session.", null);
                broadcastUserList();

                String line;
                while ((line = in.readLine()) != null) {

                    if (line.startsWith("CHAT|")) {
                        // Chat goes to everybody including sender
                        broadcast(line, null);

                    } else if (line.startsWith("DRAW|") ||
                            line.startsWith("CURSOR|")) {
                        // echo to everyone EXCEPT sender (to avoid double-drawing
                        // and double-cursor for oneself)
                        broadcast(line, this);

                    } else if (line.startsWith("CLEAR|")) {
                        if (isTeacher) {
                            broadcast(line, this);
                        }

                    } else if (line.startsWith("BOARD_LOCK|")) {
                        // Only teacher can lock/unlock
                        if (isTeacher) {
                            String[] parts = line.split("\\|");
                            if (parts.length >= 2) {
                                boardLocked = Boolean.parseBoolean(parts[1]);
                                broadcastBoardLock();
                                broadcast("CHAT|SERVER|Board " +
                                        (boardLocked ? "locked" : "unlocked") +
                                        " by teacher.", null);
                            }
                        }

                    } else if (line.startsWith("POLL_CREATE|")) {
                        // Only teacher can create poll
                        if (isTeacher) {
                            handlePollCreate(line);
                        }

                    } else if (line.startsWith("POLL_VOTE|")) {
                        handlePollVote(line);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost with " + username);
            } finally {
                WhiteboardServer.removeClient(this);
                usernames.remove(username);
                broadcast("CHAT|SERVER|" + username + " left the session.", null);
                broadcastUserList();
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static String ensureUniqueUsername(String requested) {
        if (requested == null || requested.isBlank()) {
            requested = "Guest";
        }
        String base = requested.trim();
        if (!usernames.contains(base)) {
            return base;
        }
        int counter = 2;
        while (usernames.contains(base + "-" + counter)) {
            counter++;
        }
        return base + "-" + counter;
    }
}
