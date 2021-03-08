import com.sun.security.ntlm.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

// Spawned for each incoming connection to handle connections in a multi-threaded format.
public class ConnectionHandlerThread extends Thread {

    private int port;
    private ServerSocket localSocket;
    private Socket incoming;
    private static int global_id;   // helps identify the threads; incremented every time a thread is created in the session
    private int id; // local id

    public ConnectionHandlerThread(Socket incoming) throws IOException {

        this.incoming = incoming;

        // assign id to thread
        setId();
    }

    // prevent race condition
    private synchronized void setId() {
        global_id++;
        id = global_id;
        System.out.println("Established ConnectionHandlerThread:"+id);
        System.out.println("Client info: " + incoming.toString());

    }

    @Override
    public void run() {

        System.out.println("RUNNING");

        try {
            InputStream inputStream = incoming.getInputStream();
            OutputStream outputStream = incoming.getOutputStream();

            final String CRLF = "\r\n"; // 13, 10 ascii

            // mesgs to and from a node are BYTE STREAMS, Strings -> bytes -> stream -> bytes -> Strings
            // so to send a HTTP header we literally send a string/byte/etc. through the stream between the
            // two socket endpoints.

            // HTTP RESPONSE
            String html = "<html><head><title>Test</title></head><body><h1>HI is it working YES</h1><p>Hello from thread "+id+"!</body></html>";
            String response = "HTTP/1.1 200 OK" // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                    + CRLF
                    + "Content-Length: " + html.getBytes().length + CRLF // HEADER
                    + CRLF // tells client were done with header
                    + html // response body
                    + CRLF + CRLF;
            outputStream.write(response.getBytes());

            inputStream.close();
            outputStream.close();
            incoming.close();

            System.out.println("ConnectionHandlerThread:"+id+" going to sleep ...");

            sleep(5000);

        } catch (IOException | InterruptedException e) {
            System.out.println("IOException in ConnectionHandlerThread "+id);
        }
    }

}
