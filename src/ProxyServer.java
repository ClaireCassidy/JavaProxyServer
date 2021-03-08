import com.sun.security.ntlm.Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.CRL;

public class ProxyServer implements Runnable {

    private boolean stopped = false;

    public ProxyServer(int port) {

        // set up socket on specified port
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server established on port "+port);

            // wait for connection and when received, assign to incoming
            Socket incoming = serverSocket.accept();
            System.out.println("Client info: "+incoming.toString());

            //PrintWriter out = new PrintWriter(incoming.getOutputStream(), true);
            //BufferedReader in = new BufferedReader( new InputStreamReader( incoming.getInputStream() ) );

            InputStream inputStream = incoming.getInputStream();
            OutputStream outputStream = incoming.getOutputStream();

            final String CRLF = "\r\n"; // 13, 10 ascii

            String html = "<html><head><title>Test</title></head><body><h1>HI is it working</h1></body></html>";
            String response = "HTTP/1.1 200 OK" // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                    + CRLF
                    + "Content-Length: " + html.getBytes().length + CRLF // HEADER
                    + CRLF // tells client were done with header
                    + html // response body
                    + CRLF + CRLF;
            outputStream.write(response.getBytes());


            //out.println("hi");

            inputStream.close();
            outputStream.close();
            incoming.close();
            serverSocket.close();

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
