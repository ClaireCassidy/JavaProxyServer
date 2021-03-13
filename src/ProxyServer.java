import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer implements Runnable {

    private volatile boolean stopped = false;
    private int port;
    private ServerSocket serverSocket;

    // creates a new ServerSocket given a port number that listens for connections
    public ProxyServer(int port) throws IOException {

        this.port = port;

        serverSocket = new ServerSocket(port);
        System.out.println("Server established on port " + port);
    }

    // may be called from ManagementConsole to stop the server; prevents response to new connection requests
    //      and thus the spawning of new ConnectionHandlerThreads
    public synchronized void stop() {
        ManagementConsole.printMgmtStyle("ProxyServer stopping ...");
        this.stopped = true;
    }


    // checked after each connection to see if it should still be running
    private synchronized boolean isRunning() {
        return this.stopped == false;
    }

    @Override
    public void run() {
        System.out.println("ProxyServer Running");

        try {
            // while the server socket should still be accepting incoming connections
            while (this.isRunning() && serverSocket.isBound() && !serverSocket.isClosed()) {

                // wait for connection and when received, assign to incoming
                Socket incoming = serverSocket.accept();

                // create a new thread and give it the socket to begin communications
                ConnectionHandlerThread newThread = new ConnectionHandlerThread(incoming);
                newThread.start();
            } // loop and listen for new connections
        } catch (IOException e) {
            System.out.println("Error creating server socket @PORT:" + port);
            e.printStackTrace();
        }


    }
}
