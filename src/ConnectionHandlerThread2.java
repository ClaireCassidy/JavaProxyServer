import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ConnectionHandlerThread2 extends Thread {

    private int port;
    private ServerSocket localSocket;
    private Socket incoming;
    private static int global_id;   // helps identify the threads; incremented every time a thread is created in the session
    private int id; // local id
    private InputStream inputStream;
    private OutputStream outputStream;

    final String CRLF = "\r\n"; // 13, 10 ascii
    final String FORBIDDEN = "FORBIDDEN";
    final String FORBIDDEN_CODE = "403";
    final String NOT_IMPLEMENTED = "NOT IMPLEMENTED";
    final String NOT_IMPLEMENTED_CODE = "501";

    public ConnectionHandlerThread2(Socket incoming) throws IOException {

        this.incoming = incoming;   // record the client socket
        incoming.setSoTimeout(2000);    // set timeout window

        // assign id to thread
        setId();
    }

    // prevent race condition
    private synchronized void setId() {
        global_id++;
        id = global_id;
        System.out.println("Established ConnectionHandlerThread:"+id);
        System.out.println("\tClient info: " + incoming.toString());
    }

    @Override
    public void run() {
        System.out.println(this.toString()+" RUNNING");

        // 1. accept incoming request
        // 2. check if its http or https


        try {
            inputStream = incoming.getInputStream();
            outputStream = incoming.getOutputStream();

            // mesgs to and from a node are BYTE STREAMS, Strings -> bytes -> stream -> bytes -> Strings
            // so to send a HTTP header we literally send a string/byte/etc. through the stream between the
            // two socket endpoints.

            // read in the request from the client and store in 'request'
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String request = "";
            String inputLine;
            while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
//                System.out.println(inputLine);
                request += inputLine;
            }

            String method = getMethod(request);
            String endpointUrl = getURL(request);

            System.out.println(this.toString()+" wants to "+method+" "+endpointUrl);

            // check resource not on blocklist
            ArrayList<String> blockedSites = ManagementConsole.getBlockedSites();
            boolean endpointIsBlocked = false;

//            System.out.println("printing blocked sites: ...");
            if (blockedSites != null) {
                for (String site:blockedSites) {
                    System.out.println(site);
                    // capture "www.youtube.com/..." as well as "www.youtube.com"
                    if (endpointUrl.toLowerCase().contains(site.toLowerCase())) {
//                        System.out.println(this.toString()+" refusing connection to BLOCKED site.");
                        endpointIsBlocked = true;
                        break;
                    }
                }
            }


            if (!endpointIsBlocked) {
                String justTheUrl = endpointUrl;

                if (endpointUrl.startsWith("https")) {
                    justTheUrl = endpointUrl.substring(8);
                } else if (endpointUrl.startsWith("http")) {
                    justTheUrl = endpointUrl.substring(7);
                }

                if (justTheUrl.endsWith("/")) {
                    justTheUrl = justTheUrl.substring(0,justTheUrl.length()-1); // trim trailing '/'
                }

                System.out.println(justTheUrl);

                // now check if its in cache before contacting URL
                ArrayList<File> cacheFiles = getCacheFiles();
                for (File cachedFile:cacheFiles) {

                    String filename = cachedFile.getName();
                    ManagementConsole.printMgmtStyle(filename);
                    if (filename.equals(justTheUrl)) {  // if it matches, it may not be in date
                        if (isInDate(cachedFile)) {
//                            sendCachedFile();
                        } else {
                            // Todo handle http request, cache response ADD BOOLEAN 'cache' TO handleHttpRequest() METHOD
                        }
                    }
                }   // if the http request hasn't been dealt with at this point, it hasn't been cached -> continue as normal

                if (method.toLowerCase().equals("connect")) {   // https connect request
                    handleHttpsRequest(endpointUrl);
                } else if (method.toLowerCase().equals("get")) { // http get request
                    handleHttpRequest(endpointUrl);
                } else {    // only service GET and CONNECT
                    sendNotImplementedResponse();
                }

            } else {
                // TODO send bad request response
                ManagementConsole.printMgmtStyle("Access to blocked site \""+endpointUrl+"\" denied.\n" +
                        "\tIf this is unexpected, you may have blocked a parent resource -\n"+
                        "\tEnter \"BLOCKLIST\" to view your blocked sites. \n" +
                        "\tEnter \"UNBLOCK [site]\" to access.");

                sendHttpForbidden();
            }

        } catch (IOException e) {
            System.out.println("IOException in ConnectionHandlerThread "+id);
            e.printStackTrace();
        }
    }

    // Takes a raw http request and attempts to extract the method
    private String getMethod(String request) {
        String method = null;
        try {

            String[] requestLines = request.split("\r\n");  // split into lines

            String methodLine = requestLines[0];    // get first line
            String[] methodLineTokens = methodLine.split(" ");  // get the individual tokens
            method = methodLineTokens[0];

        } catch (Exception e) {
            System.out.println(this.toString()+" Error parsing request method type - invalid format?");
        } finally {
            return method;
        }
    }

    // Takes a raw http request and attempts to extract the endpoint URL
    private String getURL(String request) {
        String endpoint = null;
        try {

            String[] requestLines = request.split("\r\n");  // split into lines

            String methodLine = requestLines[0];    // get first line
            String[] methodLineTokens = methodLine.split(" ");  // get the individual tokens
            endpoint= methodLineTokens[1];

        } catch (Exception e) {
            System.out.println(this.toString()+" Error parsing endpoint URL - invalid format?");
        } finally {
            return endpoint;
        }
    }

    private void handleHttpsRequest(String urlString) {
        // establish tunnel
        // @Todo
    }

    private void handleHttpRequest(String urlString){   // simple http get

        String expiryDate = null;   // for caching purposes

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();    // connect to endpoint
            connection.setRequestMethod("GET");         // indicate method type in header
            connection.setConnectTimeout(5000);         // cancel long connection
            connection.setReadTimeout(5000);            // cancel long-awaited reply

            // read response from requested endpoint, store in 'content'
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }



            // check everything was okay ->
//            System.out.println("---------\nRepsonse from "+urlString+":\n");

            // get response code
            int responseCode = connection.getResponseCode();
            String responseMsg = connection.getResponseMessage();

            StringBuilder builder = new StringBuilder();
            builder.append(responseCode)
                    .append(" ")
                    .append(responseMsg)
                    .append("\n");

            // get headers
            Map<String, List<String>> map = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : map.entrySet())
            {
                if (entry.getKey() == null)
                    continue;
                builder.append( entry.getKey())
                        .append(": ");

                if (entry.getKey().toLowerCase().equals("expires")) {
                    expiryDate = entry.getValue().get(0);
                }

                List<String> headerValues = entry.getValue();
                Iterator<String> it = headerValues.iterator();
                if (it.hasNext()) {
                    builder.append(it.next());

                    while (it.hasNext()) {
                        builder.append(", ")
                                .append(it.next());
                    }
                }

                builder.append("\n");
            }

//            System.out.println(builder.toString());
//            String responseStatusCode = connection.get
            System.out.println("EXPIRY DATE: "+expiryDate);
            System.out.println(content+"\n---------");

            String fullResponse = "";
            // given good response code, contents is what we want to send back
            if (responseCode >= 200 && responseCode < 300) { // alles gut

                fullResponse = "HTTP/1.1 "+responseCode+" "+responseMsg // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                        + CRLF
                        + "Content-Length: " + content.toString().getBytes().length + CRLF // HEADER
                        + CRLF // tells client were done with header
                        + content.toString() // response body
                        + CRLF + CRLF;
                outputStream.write(fullResponse.getBytes());
                outputStream.flush();

            } else {
                // TODO handle other status codes
            }

        } catch (ProtocolException e) {
            System.out.println(this.toString()+" experienced ProtocolException - check headers");
            e.printStackTrace();
        } catch (MalformedURLException e) {
            System.out.println(this.toString()+" received malformed url: "+urlString);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // triggered as a response to a request for a blocked resource
    private void sendHttpForbidden() throws IOException {
        System.out.println("Sending forbidden...");
        String fullResponse = "HTTP/1.1 "+FORBIDDEN_CODE+" "+FORBIDDEN // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(fullResponse.getBytes());
        outputStream.flush();
    }   // will say "proxy is refusing connections" -> go to inspector and network and see that its just gotten the 403 we sent ^


    // triggered as a response to a non-GET or non-CONNECT method type in request
    private void sendNotImplementedResponse() throws IOException {
        System.out.println("Sending not implemented...");
        String fullResponse = "HTTP/1.1 "+NOT_IMPLEMENTED_CODE+" "+NOT_IMPLEMENTED// [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(fullResponse.getBytes());
        outputStream.flush();
    }

    private ArrayList<File> getCacheFiles() {

        ArrayList<File> filenames = new ArrayList<>();

        try {
            File cacheDir = new File("res\\cache");     // file representing the directory
            File[] cacheFiles = cacheDir.listFiles();

            for (File cacheFile:cacheFiles) {   // copy to arraylist
                if (cacheFile.isFile()) {   // ignore subdirectories
                    filenames.add(cacheFile);
//                    System.out.println(cacheFile.getName());
                }
            }
        } catch (Exception e) {
            System.out.println(this.toString()+" Experienced error retrieving cache files - check path");
            e.printStackTrace();
        }

        return filenames;
    }

    private boolean isInDate(File cachedFile) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        // file cached with format
        // 1    EXPIRY_DATE
        // 2    FILE_CONTENTS ...
        // 3    ...
        // 4    ...

        try {
            Scanner sc = new Scanner(cachedFile);
            String expiryDate = null;
            if (sc.hasNextLine()) { // just need the first line
                expiryDate = sc.nextLine();
                System.out.println("EXPIRY DATE FROM CACHE: "+expiryDate);

//                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
//                Date d = format.parse(expiryDate);  // returns null if invalid format
                ZonedDateTime expiryZdt = ZonedDateTime.parse(expiryDate, DateTimeFormatter.RFC_1123_DATE_TIME);
                ZonedDateTime nowZdt = ZonedDateTime.now();
                long diff = expiryZdt.compareTo(nowZdt);
                System.out.println("DIFF: "+diff);  // diff == 1 if expiryZdt in future, diff = -1 if its expired, diff == 0 if its the same
                if (diff < 1) { // i.e. expired or expires now
                    return false;
                } else return true;
            }
        } catch (FileNotFoundException e) {
            System.out.println(this.toString()+" Unexpected error encountered opening Cached File \""+cachedFile.getName()+"\".");
            e.printStackTrace();
        } catch (Exception e) { // catch date parse errors
            System.out.println(this.toString()+" Invalid date format in file \""+cachedFile.getName()+"\"");
            e.printStackTrace();
        }

        return false;   // if we can't parse an expiry date from the cache, re-fetch the resource.
    }


    @Override
    public String toString () {
        return "ConnectionHandlerThread2:"+id;
    }
}
