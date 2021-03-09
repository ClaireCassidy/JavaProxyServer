import com.sun.security.ntlm.Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

// Spawned for each incoming connection to handle connections in a multi-threaded format.
public class ConnectionHandlerThread extends Thread {

    private int port;
    private ServerSocket localSocket;
    private Socket incoming;
    private static int global_id;   // helps identify the threads; incremented every time a thread is created in the session
    private int id; // local id

    final String CRLF = "\r\n"; // 13, 10 ascii

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

//            String request = "";
//            int nextByte = inputStream.read();
//            while (nextByte != -1) {
////                System.out.print((char) nextByte);
//                request = request + (char) nextByte;    // capture request in String
//
//                nextByte = inputStream.read();
//            }
//
//            System.out.println(request);
//            parseHttpRequest(request);

            // mesgs to and from a node are BYTE STREAMS, Strings -> bytes -> stream -> bytes -> Strings
            // so to send a HTTP header we literally send a string/byte/etc. through the stream between the
            // two socket endpoints.

            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String request = "";
            String inputLine;
            while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
//                System.out.println(inputLine);
                request += inputLine;
            }

            parseHttpRequest(request);

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

            System.out.println("ConnectionHandlerThread:"+id+" finished");
//            System.out.println("ConnectionHandlerThread:"+id+" going to sleep ...");
//
//            sleep(5000);

//            CLIENT REQUEST WILL HAVE FORM 'GET /www.example.com HTTP/1.1'
//            So need to extract out the bit in the middle, actually go there, get the shit,
//            GET REQUESTS DONT HAVE A BODY

        } catch (IOException e) {
            System.out.println("IOException in ConnectionHandlerThread "+id);
            e.printStackTrace();
        }
    }

    private void parseHttpRequest(String request) {

//        request = request.replace("\n", "\\n");
//        request = request.replace("\r", "\\r");
//        System.out.println("\n\n" + request + "\n\n");

        try {
            String[] requestLines = request.split("\r\n");
//            for (String line : requestLines) {
//                System.out.println("LINES:" + line);
//            }

            String methodLine = requestLines[0];

            // GET /www.example.com HTTP/1.1
            String[] methodLineTokens = methodLine.split(" ");  // get the individual tokens
            String method = methodLineTokens[0];
            String endpointUrl = methodLineTokens[1];
            System.out.println("ConnectionHandlerThread:"+id+" wants to "+method+" "+endpointUrl);


        } catch (Exception e) {
            System.out.println("Error parsing request - invalid format");
            e.printStackTrace();
        }

    }

}
