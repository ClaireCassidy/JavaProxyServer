import com.sun.security.ntlm.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer implements Runnable {

    private boolean stopped = false;

    public ProxyServer(int port) {

        // set up socket on specified port
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server established on port "+port);

            // wait for connection
            Socket incoming = serverSocket.accept();
            System.out.println("Client info: "+incoming.toString());

        } catch (IOException e) {
            System.out.println("Error creating socket");
            e.printStackTrace();
        }

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
//
//        int count = 0;
//        while (!this.stopped) {
//            count++;
//            System.out.println(count);
//        }
    }
}
