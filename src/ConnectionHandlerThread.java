import com.sun.security.ntlm.Server;


// TODO set connection timeout for ConnectionHandlerThread so it can service more connections
// keepalive
import java.io.*;
import java.net.*;

// TODO add note to docs suggesting turning off Firefox Data Collection and Use to prevent flooding with telemetry requests

// Spawned for each incoming connection to handle connections in a multi-threaded format.
public class ConnectionHandlerThread extends Thread {

    private int port;
    private ServerSocket localSocket;
    private Socket incoming;
    private static int global_id;   // helps identify the threads; incremented every time a thread is created in the session
    private int id; // local id
    private InputStream inputStream;
    private OutputStream outputStream;

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
            inputStream = incoming.getInputStream();
            outputStream = incoming.getOutputStream();

            // mesgs to and from a node are BYTE STREAMS, Strings -> bytes -> stream -> bytes -> Strings
            // so to send a HTTP header we literally send a string/byte/etc. through the stream between the
            // two socket endpoints.

            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String request = "";
            String inputLine;
            while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
                System.out.println(inputLine);
                request += inputLine;
            }

            String resourceUrl = parseHttpRequest(request);
            if (resourceUrl != null) {    // if not invalid format
                System.out.println("\tFROM THREAD MAIN: Want URL " + resourceUrl);

                // send the request to the endpoint
                String resource = sendHttpRequestToEndpoint(resourceUrl);

                if (resource != null && resource.length() > 0) {
                    String response = "HTTP/1.1 200 OK" // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                            + CRLF
                            + "Content-Length: " + resource.getBytes().length + CRLF // HEADER
                            + CRLF // tells client were done with header
                            + resource // response body
                            + CRLF + CRLF;
                    outputStream.write(response.getBytes());
                } else {    // TODO something went wrong with fetching the resource; send appropriate response
                    // HTTP RESPONSE
                    String html = "<html><head><title>Test</title></head><body><h1>Something went wrong</h1><p>Hello from thread "+id+"!</body></html>";
                    String response = "HTTP/1.1 200 OK" // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                            + CRLF
                            + "Content-Length: " + html.getBytes().length + CRLF // HEADER
                            + CRLF // tells client were done with header
                            + html // response body
                            + CRLF + CRLF;
                    outputStream.write(response.getBytes());
                }
            } else {
                //TODO send back response saying request was invalid
                System.out.println("\t\tResponse invalid");
            }

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

    private String parseHttpRequest(String request) {

//        request = request.replace("\n", "\\n");
//        request = request.replace("\r", "\\r");
//        System.out.println("\n\n" + request + "\n\n");

        try {
            String[] requestLines = request.split("\r\n");
//            for (String line : requestLines) {
//                System.out.println("LINES:" + line);
//            }

            String methodLine = requestLines[0];

            // Method line has format 'GET /www.example.com HTTP/1.1'
            String[] methodLineTokens = methodLine.split(" ");  // get the individual tokens
            String method = methodLineTokens[0];
            String endpointUrl = methodLineTokens[1];
            System.out.println(this.toString()+" wants to "+method+" "+endpointUrl);

            if (method.toLowerCase().equals("connect")) { //https connection request
                //Todo handle https
            } else if (method.toLowerCase().equals("get")) {    // get request
//                httpGet();
            }

            if (endpointUrl.equals("/favicon.ico")) {
                System.out.println(this.toString()+" ignoring request for favicon ...");
                return null;   // sometimes browser wants root server's favicon; ignore
            }

            if (endpointUrl.startsWith("/")) endpointUrl = endpointUrl.substring(1);
            if (!endpointUrl.startsWith("http://")) {
                endpointUrl = "http://"+endpointUrl;    // prepend 'http://' to url if necessary
                // don't need 'www.'
            }

            return endpointUrl;

        } catch (Exception e) {
            System.out.println("Error parsing request - invalid format");
            e.printStackTrace();
        }

        return null;
    }

    // for retrieving the data on behalf of the client
    private String sendHttpRequestToEndpoint(String urlString) {

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            //@Todo some urls block http by default idk what im supposed to do with this info (change all outgoing requests to https?)
            // @Todo Wait - just send back the original header i guess.
            int resCode = connection.getResponseCode();
            if (resCode > 300 && resCode < 400) {
                String redirectHeader = connection.getHeaderField("Location");
                System.out.println(redirectHeader);
            }

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            System.out.println(content.toString());
            if(content.length() == 0) {
                System.out.println("WHY");
            }

            // release resources
            in.close();
            connection.disconnect();

            // Have requested resource, now pass back to run() to send back to client using socket
            return content.toString();

        } catch (MalformedURLException e) {
            System.out.println(this.toString()+" received malformed url: "+urlString);
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println(this.toString()+" experienced IOException when connecting to "+urlString);
            e.printStackTrace();
        }

        return null;    // smth went wrong
    }

    @Override
    public String toString () {
        return "ConnectionHandlerThread:"+id;
    }
}
