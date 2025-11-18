# 🎨 Real-Time Collaborative Whiteboard & Class Chat

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Swing](https://img.shields.io/badge/GUI-Swing-red?style=for-the-badge)
![Networking](https://img.shields.io/badge/Networking-TCP%20Sockets-blue?style=for-the-badge)

A lightweight, networked desktop application designed for interactive classrooms. This project replaces heavy tools like Teams or Zoom for local labs by providing a **real-time shared whiteboard**, **integrated chat**, and **live polling system** using pure Java Sockets (TCP).
Webpage: https://manaswi2006.github.io/Website-CN-project/

---

## 📖 Problem & Solution

**The Problem:**
In many educational settings, students and teachers rely on heavy platforms just to share a simple board. These solutions often require high bandwidth, complex accounts, and internet access, making them unsuitable for quick local lab interactions.

**The Solution:**
We built a lightweight application (~500 lines of code) that runs over plain TCP sockets. It works seamlessly on a local LAN lab setup, synchronizing drawing strokes, messages, and votes instantly without external dependencies.

---

## ✨ Key Features

### 🖌️ Collaborative Whiteboard
* **Real-time Sync:** Drawing strokes are broadcast to all connected clients instantly.
* **Tools:** Pen, Eraser, Color Picker, and Stroke Width adjustment.
* **Remote Cursors:** See other users' names floating next to their drawing cursor.

### 💬 Integrated Communication
* **Class Chat:** A side-panel chat room for group discussion.
* **Participant List:** Visual list of currently connected students.

### 📊 Teacher Tools & Polling
* **Teacher Role:** The first user to join becomes the "Teacher".
* **Board Control:** Only the teacher can clear the entire board.
* **Live Polls:** Teacher can create multiple-choice questions. Students vote, and results/percentages update in real-time for everyone.

---

## 🏗️ Architecture

The system follows a multi-threaded **Client-Server** architecture.

### 1. Server Side (Single Process)
* **Listener:** `ServerSocket` listens on a fixed port.
* **ClientHandler:** A separate thread for each student connection.
* **Broadcast Manager:** Relays drawings, chats, and poll data to all active streams.
* **State Management:** Tracks active users and current poll statistics.

### 2. Client Side (Multiple Instances)
* **Network Layer:**
    * *Sender:* Encodes GUI events into the custom text protocol.
    * *Receiver Thread:* Listens for server updates and modifies the Swing components asynchronously.
* **GUI Layer (Swing):**
    * `DrawPanel` (Canvas)
    * `ChatPanel` (History & Input)
    * `PollPanel` (Vote Interface & Graphs)

---

## 📡 Custom Text Protocol

The application communicates using a custom string-based protocol over TCP. This makes the network traffic easy to debug and human-readable.

| Action | Protocol Format | Description |
| :--- | :--- | :--- |
| **Join** | `JOIN|username` | Sent when a client connects. |
| **Chat** | `CHAT|username|message` | Broadcasts a text message. |
| **Draw** | `DRAW|user|x1|y1|x2|y2|rgb|str` | Coordinates, Color (int), and Stroke width. |
| **Clear** | `CLEAR|` | Teacher wipes the board. |
| **Create Poll** | `POLL_CREATE|user|id|Q|opt1;opt2` | Teacher starts a new poll. |
| **Vote** | `POLL_VOTE|user|id|optIndex` | Student submits a vote. |
| **Results** | `POLL_RESULTS|id|c0;c1;c2...` | Server updates vote counts. |

---

## 🚀 Getting Started

### Prerequisites
* **Java Development Kit (JDK)** 8 or higher.
* An IDE like **IntelliJ IDEA** or **Eclipse** (or just the command line).

### Installation & Running

1.  **Clone the Repository**
    ```bash
    git clone [https://github.com/yourusername/collaborative-whiteboard.git](https://github.com/yourusername/collaborative-whiteboard.git)
    cd collaborative-whiteboard
    ```

2.  **Compile the Code**
    ```bash
    javac -d bin src/*.java
    ```

3.  **Run the Server** (Do this first)
    ```bash
    java -cp bin src.ServerMain
    ```
    _Console should say: "Server started on port..."_

4.  **Run the Clients** (Open multiple terminal windows)
    ```bash
    java -cp bin src.ClientMain
    ```
    * Enter `localhost` for IP (or the LAN IP of the server machine).
    * Enter a unique Username.
    * *Note: The first client launched gets Teacher privileges.*

---

## 🛠️ Tech Stack

* **Language:** Java
* **GUI:** Java Swing (JPanel, JFrame, Graphics2D)
* **Networking:** `java.net` (Socket, ServerSocket)
* **Concurrency:** `Thread`, `Runnable`

