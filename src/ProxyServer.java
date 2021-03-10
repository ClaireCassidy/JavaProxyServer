import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ProxyServer implements Runnable {

    private volatile boolean stopped = false;
    private ArrayList<ConnectionHandlerThread2> activeConnectionHandlers;
    private int port;
    private ServerSocket serverSocket;

    public ProxyServer(int port) throws IOException {

        activeConnectionHandlers = new ArrayList<>();
        this.port = port;

        serverSocket = new ServerSocket(port);
        System.out.println("Server established on port " + port);
    }

    // may be called from Launcher to stop the server
    public synchronized void stop() {
        System.out.println("ProxyServer stopping ...");
        this.stopped = true;
    }


    private synchronized boolean isRunning() {
        return this.stopped == false;
    }

    @Override
    public void run() {
        System.out.println("ProxyServer Running");

        // set up socket on specified port
        try {
            while (this.isRunning() && serverSocket.isBound() && !serverSocket.isClosed()) {

                // wait for connection and when received, assign to incoming
                Socket incoming = serverSocket.accept();    // won't stop until it receives one more request which is annoying; write in docs that its safe to just terminate the program after quit regardless of if it exits itself.
                System.out.println("Establishing ConnectionHandlerThread...");

                ConnectionHandlerThread2 newGuyOnTheBlock = new ConnectionHandlerThread2(incoming);
                activeConnectionHandlers.add(newGuyOnTheBlock);
                newGuyOnTheBlock.start();
            }

        } catch (IOException e) {
            System.out.println("Error creating server socket @PORT:" + port);
            e.printStackTrace();
        }


    }
}
