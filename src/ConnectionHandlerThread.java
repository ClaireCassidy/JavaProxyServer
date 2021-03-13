import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ConnectionHandlerThread extends Thread {

    private Socket incoming;
    private static int global_id;   // helps identify the threads; incr'd every time thread is created in the session
    private int id;                 // local id
    private InputStream inputStream;
    private OutputStream outputStream;

    // HTTP Status codes
    final String CRLF = "\r\n";
    final String FORBIDDEN = "FORBIDDEN";
    final String FORBIDDEN_CODE = "403";
    final String NOT_IMPLEMENTED = "NOT IMPLEMENTED";
    final String NOT_IMPLEMENTED_CODE = "501";
    final String OK = "OK";
    final String OK_CODE = "200";
    final String CONNECTION_ESTABLISHED = "CONNECTION ESTABLISHED";
    final String NOT_FOUND = "NOT FOUND";
    final String NOT_FOUND_CODE = "404";

    // for configuring how to send http resource
    final String JPG = "jpg";
    final String JPEG = "jpeg";
    final String GIF = "gif";
    final String PNG = "png";


    public ConnectionHandlerThread(Socket incoming) throws IOException {

        this.incoming = incoming;       // record the client socket
        incoming.setSoTimeout(3000);    // set timeout window

        // assign id to thread
        setId();

    }

    // prevent race condition among threads
    private synchronized void setId() {
        global_id++;
        id = global_id;
        System.out.println("Established ConnectionHandlerThread:" + id);
        System.out.println("\tClient info: " + incoming.toString());
    }

    @Override
    public void run() {
        System.out.println(this.toString() + " RUNNING");

        try {
            inputStream = incoming.getInputStream();
            outputStream = incoming.getOutputStream();

            // read in the request from the client and store in 'request'
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String request = "";
            String inputLine;
            while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
                request += inputLine;
            }

//            long startTime = System.nanoTime();

            // get the type of the http(s) request and the resource url
            String method = getMethod(request);
            String endpointUrl = getURL(request);

            if (method.toLowerCase().equals("get")) {
                System.out.print("(HTTP): ");
            } else if (method.toLowerCase().equals("connect")) {
                System.out.print("(HTTPS): ");
            }
            System.out.println(this.toString() + " wants to " + method + " " + endpointUrl);

            // check blocklist -> check cache -> fetch from source

            // check resource not on blocklist
            ArrayList<String> blockedSites = ManagementConsole.getBlockedSites();
            boolean endpointIsBlocked = false;

            if (blockedSites != null) {
                for (String site : blockedSites) {
                    // capture "www.youtube.com/..." as well as "www.youtube.com"
                    if (endpointUrl.toLowerCase().contains(site.toLowerCase())) {
                        endpointIsBlocked = true;
                        break;
                    }
                }
            }


            if (!endpointIsBlocked) {   // next check in cache for resource before contacting endpoint

                // compare the encoded version of the desired resource url against the filenames in the cache
                String justTheUrl = trimUrl(endpointUrl);
                String filenameForUrl = getCacheFilenameFromUrl(justTheUrl);    // get the encoded filename

                boolean sendCached = false;
                File cachedFile = null;

                ArrayList<File> cacheFiles = getCacheFiles();

                for (File curFile : cacheFiles) {   // compare against filenames in the cache

                    String filename = curFile.getName();

                    if (filename.equals(filenameForUrl)) {  // if it matches, it may not be in date
                        // if it hasn't expired, don't send request to server and just send cached file
                        if (isInDate(curFile)) {
                            sendCached = true;
                            cachedFile = curFile;
                            break;
                        }
                    }
                }

                if (sendCached) {   // if cached and in date, send the cached version
                    sendCachedFile(cachedFile);
                } else {    // otherwise need to contact endpoint

                    if (method.toLowerCase().equals("connect")) {   // https connect request
                        handleHttpsRequest(endpointUrl);
                    } else if (method.toLowerCase().equals("get")) { // http get request
                        handleHttpRequest(endpointUrl);
                    } else {    // only service GET and CONNECT
                        sendNotImplementedResponse();
                    }
                }

            } else {    // resource was on blocklist
                ManagementConsole.printMgmtStyle("Access to blocked site \"" + endpointUrl + "\" denied.\n" +
                        "\tIf this is unexpected, you may have blocked a parent resource -\n" +
                        "\tEnter \"BLOCKLIST\" to view your blocked sites. \n" +
                        "\tEnter \"UNBLOCK [site]\" to access.");

                sendForbidden();    // respond to browser with 403
            }

//            long endTime = System.nanoTime();
//            long duration = (endTime - startTime)/1000000;  //divide by 1000000 to get milliseconds.
//            ManagementConsole.printMgmtStyle(this.toString()+" Duration: "+duration+" ms");

            // close streams and sockets
            inputStream.close();
            outputStream.close();
            incoming.close();
        } catch (SocketTimeoutException e) {    // ignore; usually as result of trying to contact blocked site.
        } catch (IOException e) {
            System.out.println("IOException in ConnectionHandlerThread " + id);
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
            System.out.println(this.toString() + " Error parsing request method type - invalid format?: "+request);
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
            endpoint = methodLineTokens[1];

        } catch (Exception e) {
            System.out.println(this.toString() + " Error parsing endpoint URL - invalid format?");
        } finally {
            return endpoint;
        }
    }

    private void handleHttpsRequest(String urlString) {

        /*
        The most common form of HTTP tunneling is the standardized HTTP CONNECT method.[1][2]
        In this mechanism, the client asks an HTTP proxy server to forward the TCP connection
        to the desired destination. The server then proceeds to make the connection on behalf
        of the client. Once the connection has been established by the server, the proxy server
        continues to proxy the TCP stream to and from the client. Only the initial connection
        request is HTTP - after that, the server simply proxies the established TCP connection.
        This mechanism is how a client behind an HTTP proxy can access websites using SSL or
        TLS (i.e. HTTPS).
        */

        // extract endpoint target port from url:
        String[] urlComponents = urlString.split(":");
        String urlItself = urlComponents[0];
        int endpointPort = Integer.parseInt(urlComponents[1]);

        try {
            sendConnectionEst();    // respond to connect request

            // set up socket connection to endpoint
            Socket endpointSocket = new Socket(urlItself, endpointPort);
            endpointSocket.setSoTimeout(3000);

            InputStream endpointSocketIn = endpointSocket.getInputStream();
            OutputStream endpointSocketOut = endpointSocket.getOutputStream();

            // link from client to endpoint
            // Needs endpointSocket's inputStream, incoming's outputStream
            Thread clientToEndpointThread = new HttpsConnectorThread(endpointSocketIn, outputStream);
            clientToEndpointThread.start();

            // link from endpoint to client
            // Needs incoming's input stream, endpointSocket's outputStream
            Thread endpointToClientThread = new HttpsConnectorThread(inputStream, endpointSocketOut);
            endpointToClientThread.start();

            // wait for them to finish before continuing
            clientToEndpointThread.join();
            endpointToClientThread.join();

            // close the endpoint socket when communication has ceased
            endpointSocket.close();

        } catch (UnknownHostException e) {
            System.out.println(this.toString() + " Unable to connect to HTTPS host "+urlItself);
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println(this.toString() + " IOException when connecting to HTTPS "+urlItself);
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleHttpRequest(String urlString) {   // simple http get (non-cached)

        String expiryDate = null;   // for caching purposes
        String urlExtension = getResourceExtension(urlString);  // decide what kind of connection stream to use
        if (isImageType(urlExtension)) {    // handle image stream separately
            try {
                URL url = new URL(urlString);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                // fetch the resource and store it in 'imgResource'
                BufferedImage imgResource = ImageIO.read(connection.getInputStream());
                if (imgResource != null) {                          // found image at given url
                    String responseHeader = "HTTP/1.1 " + OK_CODE + " " + OK
                            + CRLF + CRLF;
                    outputStream.write(responseHeader.getBytes());
                    ImageIO.write(imgResource, urlExtension, outputStream);
                } else {
                    sendNotFound(); // couldn't fetch the resource, so send user a 404
                }

            } catch (MalformedURLException e) {

                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {    // send normal text type
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
                System.out.println("---------\nRepsonse from " + urlString + ":\n");

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
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    if (entry.getKey() == null)
                        continue;
                    builder.append(entry.getKey())
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

                System.out.println(builder.toString());
                System.out.println("EXPIRY DATE FROM RESPONSE: " + expiryDate);
                System.out.println(content + "\n---------");

                String fullResponse = "";

                // given good response code, contents is what we want to send back
                if (responseCode >= 200 && responseCode < 300) { // alles gut


                    fullResponse = "HTTP/1.1 " + responseCode + " " + responseMsg // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                            + CRLF
                            + "Content-Length: " + content.toString().getBytes().length + CRLF // HEADER
                            + CRLF // tells client were done with header
                            + content.toString() // response body
                            + CRLF + CRLF;
                    outputStream.write(fullResponse.getBytes());
                    outputStream.flush();
                    // cache the response

                    String justTheUrl = trimUrl(urlString);
                    String filenameFromUrl = getCacheFilenameFromUrl(justTheUrl); // name cache file according to convention

                    if (expiryDate != null) {   // don't bother caching if we can't give an expiry date - will just be re-fetched regardless
                        try {

                            File cacheFile = new File("res\\cache\\" + filenameFromUrl);
                            ManagementConsole.printMgmtStyle("Writing to " + cacheFile.getCanonicalPath());
                            if (cacheFile.createNewFile()) {                      // creates the file if it doesn't already exist
                                System.out.println(this.toString() + " Creating cache file for \"" + filenameFromUrl + "\" ("+justTheUrl+") ... ");
                            } else {
                                System.out.println(this.toString() + " Overwriting cache file for \"" + justTheUrl + "\" ("+justTheUrl+") ... ");
                            }
                            // write expiry date and response to file

                            FileWriter fw = new FileWriter(cacheFile, false);
                            fw.write(expiryDate + System.lineSeparator());
                            fw.write(content.toString() + System.lineSeparator());
                            fw.flush();
                            fw.close();
                        } catch (IOException e) {
                            System.out.println(this.toString() + " Error writing to cachefile for resource \"" + filenameFromUrl + "\" ("+justTheUrl+")");
                            e.printStackTrace();
                        }
                    }


                }

            } catch (ProtocolException e) {
                System.out.println(this.toString() + " experienced ProtocolException - check headers");
                e.printStackTrace();
            } catch (MalformedURLException e) {
                System.out.println(this.toString() + " received malformed url: " + urlString);
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // sends 200 CONNECTION ESTABLISHED
    private void sendConnectionEst() throws IOException {
        String response = "HTTP/1.1 " + OK_CODE + " " + CONNECTION_ESTABLISHED
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(response.getBytes());
        outputStream.flush();
    }

    // sent when a request is a received for an image that can't be sourced, etc.
    private void sendNotFound() throws IOException {
        System.out.println("Sending not found ...");
        String fullResponse = "HTTP/1.1 " + NOT_FOUND_CODE + " " + NOT_FOUND // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(fullResponse.getBytes());
        outputStream.flush();
    }

    // check resource type before choosing send method
    private boolean isImageType(String urlExtension) {
        String local = urlExtension.toLowerCase();
        return local.equals(JPG) ||
                local.equals(JPEG) ||
                local.equals(PNG) ||
                local.equals(GIF);
    }

    // triggered as a response to a request for a blocked resource
    private void sendForbidden() throws IOException {
        System.out.println("Sending forbidden...");
        String fullResponse = "HTTP/1.1 " + FORBIDDEN_CODE + " " + FORBIDDEN // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(fullResponse.getBytes());
        outputStream.flush();
    }   // will say "proxy is refusing connections" -> go to inspector and network and see that its just gotten the 403 we sent ^


    // triggered as a response to a non-GET or non-CONNECT method type in request
    private void sendNotImplementedResponse() throws IOException {
        System.out.println("Sending not implemented...");
        String fullResponse = "HTTP/1.1 " + NOT_IMPLEMENTED_CODE + " " + NOT_IMPLEMENTED// [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                + CRLF // HEADER
                + CRLF // tells client were done with header
                + CRLF + CRLF;
        outputStream.write(fullResponse.getBytes());
        outputStream.flush();
    }

    // for comparison against the requested resource url
    private ArrayList<File> getCacheFiles() {

        ArrayList<File> files = new ArrayList<>();

        try {

            File cacheDir = new File("res\\cache");     // file representing the directory
            File[] cacheFiles = cacheDir.listFiles();

            for (File cacheFile : cacheFiles) {     // copy to arraylist
                if (cacheFile.isFile()) {           // ignore subdirectories
                    files.add(cacheFile);
                }
            }

        } catch (Exception e) {
            System.out.println(this.toString() + " Experienced error retrieving cache files - check path");
            e.printStackTrace();
        }

        return files;
    }

    private boolean isInDate(File cachedFile) {
        // file cached with format
        // 1    EXPIRY_DATE
        // 2    HTML etc.   (one line)

        try (Scanner sc = new Scanner(cachedFile)) {    // ensure resource is closed
            String expiryDate;
            if (sc.hasNextLine()) { // just need the first line
                expiryDate = sc.nextLine();

                ZonedDateTime expiryZdt = ZonedDateTime.parse(expiryDate, DateTimeFormatter.RFC_1123_DATE_TIME);
                ZonedDateTime nowZdt = ZonedDateTime.now();

                // diff == 1 if expiryZdt in future, diff = -1 if its expired, diff == 0 if its the same
                long diff = expiryZdt.compareTo(nowZdt);

                if (diff < 1) { // i.e. expired or expires now; not suitable for sending
                    return false;
                } else return true;
            }
        } catch (FileNotFoundException e) {
            System.out.println(this.toString() + " Unexpected error encountered opening Cached File \""
                    + cachedFile.getName() + "\".");
            e.printStackTrace();
        } catch (Exception e) { // catch date parse errors
            System.out.println(this.toString() + " Invalid date format in file \"" + cachedFile.getName() + "\"");
            e.printStackTrace();
        }

        return false;   // if we can't parse an expiry date from the cache, re-fetch the resource to be safe.
    }

    private void sendCachedFile(File cachedFile) {

        // file cached with format
        // 1    EXPIRY_DATE
        // 2    HTML etc.   (one line)

        ManagementConsole.printMgmtStyle("Sending cached version of \"" + cachedFile.getName() + "\" ...");
        String resource;

        try (Scanner sc = new Scanner(cachedFile)) {    // ensure resource is closed
            if (sc.hasNextLine()) sc.nextLine();        // skip expiry date line
            if (sc.hasNextLine()) {
                resource = sc.nextLine();
                System.out.println("\tCACHED RESOURCE FOR \"" + cachedFile.getName() + "\": \n\t" + resource);

                String fullResponse = "HTTP/1.1 " + OK_CODE + " " + OK // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                        + CRLF
                        + "Content-Length: " + resource.getBytes().length + CRLF // HEADER
                        + CRLF // tells client were done with header
                        + resource // response body
                        + CRLF + CRLF;

                outputStream.write(fullResponse.getBytes());
                outputStream.flush();
            }


        } catch (FileNotFoundException e) {
            System.out.println(this.toString() + " Unexpected error encountered opening Cached File \"" + cachedFile.getName() + "\".");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println(this.toString() + " Error writing cached resource to client.");
            e.printStackTrace();
        }

    }

    // trims unecessary stuff from request url so that it can be effectively matched to cache files
    private static String trimUrl(String rawUrl) {
        String justTheUrl = rawUrl;

        if (rawUrl.startsWith("https")) {
            justTheUrl = rawUrl.substring(8);
        } else if (rawUrl.startsWith("http")) {
            justTheUrl = rawUrl.substring(7);
        }

        if (justTheUrl.endsWith("/")) {
            justTheUrl = justTheUrl.substring(0, justTheUrl.length() - 1); // trim trailing '/'
        }

        return justTheUrl;
    }

    // need to disambiguate between image-type resources and text resources and handle streams appropriately
    private String getResourceExtension(String url) {

        int extStartIndex = url.lastIndexOf('.');
        if (extStartIndex > -1 && extStartIndex != url.length()-1) {
            String extension = url.substring(extStartIndex+1);
            if (extension.contains(":")) {  // port
                extension = extension.substring(0, extension.indexOf(':'));
            }
            return extension;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ConnectionHandlerThread:" + id;
    }

    public static String getCacheFilenameFromUrl(String url) { // should be a url like www.example.com/files/resource
        // 	www.w3.org/Icons/w3c_logo.svg
        //      convert _ to ,,,
        //  www.w3.org/Icons/w3c,,,logo.svg
        //      convert / to _
        //  www.w3.org_Icons_w3c,,,logo.svg

        String filename = trimUrl(url); // trim if hasn't been trimmed already; removes 'http(s)://' and trailing '/'
        filename = filename.replaceAll("_", ",,,");
        filename = filename.replaceAll("/", "_");

        return filename;
    }

    // reverses the encoding process for cache filenames and extracts true url
    public static String getUrlFromCacheFilename(String filename) {
        String url = filename;
        url = url.replaceAll("_", "/");
        url = url.replaceAll(",,,", "_");

        return url;
    }

}

