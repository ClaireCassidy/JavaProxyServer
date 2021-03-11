import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// TODO: www.example.com gives expiry date, www.neverssl.com doesn't -> show how we don't bother caching neverssl.
// www.httpforever.com
// TODO: had issue: https://support.mozilla.org/en-US/questions/1313356
// TODO: "proxy server is refusing connections" -> either the program isn't running or a resource is on a blocked site

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
    final String OK = "OK";
    final String OK_CODE = "200";

    final String JPG = "jpg";
    final String JPEG = "jpeg";
    final String GIF = "gif";
    final String PNG = "png";


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
        System.out.println("Established ConnectionHandlerThread:" + id);
        System.out.println("\tClient info: " + incoming.toString());
    }

    @Override
    public void run() {
        System.out.println(this.toString() + " RUNNING");

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

            System.out.println(this.toString() + " wants to " + method + " " + endpointUrl);
            ManagementConsole.printMgmtStyle(getResourceExtension(endpointUrl));

            // check resource not on blocklist
            ArrayList<String> blockedSites = ManagementConsole.getBlockedSites();
            boolean endpointIsBlocked = false;

//            System.out.println("printing blocked sites: ...");
            if (blockedSites != null) {
                for (String site : blockedSites) {
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
                String justTheUrl = trimUrl(endpointUrl);

                //System.out.println(justTheUrl);

                boolean sendCached = false;
                File cachedFile = null;

                // now check if its in cache before contacting URL
                ArrayList<File> cacheFiles = getCacheFiles();
                for (File curFile : cacheFiles) {

                    String filename = curFile.getName();
                    ManagementConsole.printMgmtStyle(filename);
                    if (filename.equals(justTheUrl)) {  // if it matches, it may not be in date
                        if (isInDate(curFile)) {     // if it hasn't expired, don't send request to server and just send cached file
                            sendCached = true;
                            cachedFile = curFile;
                            break;
                        }
                    }
                }

                if (sendCached) {
                    sendCachedFile(cachedFile);
                } else {

                    if (method.toLowerCase().equals("connect")) {   // https connect request
                        handleHttpsRequest(endpointUrl);
                    } else if (method.toLowerCase().equals("get")) { // http get request
                        handleHttpRequest(endpointUrl);
                    } else {    // only service GET and CONNECT
                        sendNotImplementedResponse();
                    }
                }

            } else {
                ManagementConsole.printMgmtStyle("Access to blocked site \"" + endpointUrl + "\" denied.\n" +
                        "\tIf this is unexpected, you may have blocked a parent resource -\n" +
                        "\tEnter \"BLOCKLIST\" to view your blocked sites. \n" +
                        "\tEnter \"UNBLOCK [site]\" to access.");

                sendHttpForbidden();
            }

            incoming.close();
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
            System.out.println(this.toString() + " Error parsing request method type - invalid format?");
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
        // establish tunnel
        // @Todo
    }

    private void handleHttpRequest(String urlString) {   // simple http get (non-cached)

        ManagementConsole.printMgmtStyle("HEREEEEEEEE:" + urlString);
        String expiryDate = null;   // for caching purposes
        String urlExtension = getResourceExtension(urlString);  // decide what kind of connection stream to use
        if (isImageType(urlExtension)) {    // handle image stream separately
            try {
                URL url = new URL(urlString);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                BufferedImage imgResource = ImageIO.read(connection.getInputStream());     // fetch the resource and store it in 'imgResource'
                if (imgResource != null) {                          // found image at given url
                    String responseHeader = "HTTP/1.1 " + OK_CODE + " " + OK
                            + CRLF + CRLF;
                    outputStream.write(responseHeader.getBytes());
                    ImageIO.write(imgResource, urlExtension, outputStream);
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

                    String justTheUrl = trimUrl(urlString);                 // name cache file according to convention
                    String cachedFilePath = "res\\cache\\" + justTheUrl;      // construct path to cache file

                    if (expiryDate != null) {   // don't bother caching if we can't give an expiry date - will just be re-fetched regardless
                        try {

                            File cacheFile = new File("res\\cache\\" + justTheUrl);
                            ManagementConsole.printMgmtStyle("Writing to " + cacheFile.getCanonicalPath());
                            if (cacheFile.createNewFile()) {                      // creates the file if it doesn't already exist
                                System.out.println(this.toString() + " Creating cache file for \"" + justTheUrl + "\" ... ");
                            } else {
                                System.out.println(this.toString() + " Overwriting cache file for \"" + justTheUrl + "\"");
                            }
                            // write expiry date and response to file
//                        ManagementConsole.printMgmtStyle("HERE: "+expiryDate);

                            FileWriter fw = new FileWriter(cacheFile, false);
                            fw.write(expiryDate + System.lineSeparator());
                            fw.write(content.toString() + System.lineSeparator());
                            fw.flush();
                            fw.close();
                        } catch (IOException e) {
                            System.out.println(this.toString() + " Error writing to cachefile for resource \"" + justTheUrl + "\"");
                            e.printStackTrace();
                        }
                    }


                } else {
                    // TODO handle other status codes
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

    private boolean isImageType(String urlExtension) {
        String local = urlExtension.toLowerCase();
        return local.equals(JPG) ||
                local.equals(JPEG) ||
                local.equals(PNG) ||
                local.equals(GIF);
    }

    // triggered as a response to a request for a blocked resource
    private void sendHttpForbidden() throws IOException {
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

    private ArrayList<File> getCacheFiles() {

        ArrayList<File> files = new ArrayList<>();

        try {
            File cacheDir = new File("res\\cache");     // file representing the directory
            File[] cacheFiles = cacheDir.listFiles();

            for (File cacheFile : cacheFiles) {   // copy to arraylist
                if (cacheFile.isFile()) {   // ignore subdirectories
                    files.add(cacheFile);
//                    System.out.println(cacheFile.getName());
                }
            }
        } catch (Exception e) {
            System.out.println(this.toString() + " Experienced error retrieving cache files - check path");
            e.printStackTrace();
        }

        return files;
    }

    private boolean isInDate(File cachedFile) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        // file cached with format
        // 1    EXPIRY_DATE
        // 2    HTML etc.   (one line)


        try (Scanner sc = new Scanner(cachedFile)) {    // ensure resource is closed
            String expiryDate = null;
            if (sc.hasNextLine()) { // just need the first line
                expiryDate = sc.nextLine();
                System.out.println("EXPIRY DATE FROM CACHE: " + expiryDate);

//                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
//                Date d = format.parse(expiryDate);  // returns null if invalid format
                ZonedDateTime expiryZdt = ZonedDateTime.parse(expiryDate, DateTimeFormatter.RFC_1123_DATE_TIME);
                ZonedDateTime nowZdt = ZonedDateTime.now();
                long diff = expiryZdt.compareTo(nowZdt);
                System.out.println("DIFF: " + diff);  // diff == 1 if expiryZdt in future, diff = -1 if its expired, diff == 0 if its the same
                if (diff < 1) { // i.e. expired or expires now
                    return false;
                } else return true;
            }
        } catch (FileNotFoundException e) {
            System.out.println(this.toString() + " Unexpected error encountered opening Cached File \"" + cachedFile.getName() + "\".");
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
        System.out.println("Sending cached version of \"" + cachedFile.getName() + "\" ...");
        String resource = null;

        try (Scanner sc = new Scanner(cachedFile)) {    // ensure resource is closed
            if (sc.hasNextLine()) sc.nextLine();    // skip expiry date line
            if (sc.hasNextLine()) {
                resource = sc.nextLine();
                System.out.println("\tCACHED RESOURCE FOR \"" + cachedFile.getName() + "\": \n\t" + resource);

                String fullResponse = "HTTP/1.1 " + OK_CODE + " " + OK // [HTTP_VERSION] [RESPONSE_CODE] [RESPONSE_MESSAGE]
                        + CRLF
                        + "Content-Length: " + resource.toString().getBytes().length + CRLF // HEADER
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

    private static String trimUrl(String rawUrl) { // trims unecessary stuff from request url so that it can be effectively matched to cache files
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

    // need to disambiguate between image-type resources and text resources and handle appropriately
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
        return "ConnectionHandlerThread2:" + id;
    }

    public static String getCacheFilenameFromUrl(String url) { // should be a url like www.example.com/files/folder
        // 	www.w3.org/Icons/w3c_logo.svg
        //      convert _ to ,,,
        //  www.w3.org/Icons/w3c,,,logo.svg
        //  www.w3.org_Icons_w3c,,,logo.svg

        String filename = trimUrl(url); // trim if hasn't been already; removes 'http(s)://' and trailing '/'
        filename = filename.replaceAll("_", ",,,");
        filename = filename.replaceAll("/", "_");

        return filename;
    }

    public static String getUrlFromCacheFilename(String filename) {
        String url = filename;
        url = url.replaceAll("_", "/");
        url = url.replaceAll(",,,", "_");

        return url;
    }
}

// http request ->
//      if url extension is .jpg, .gif, .jpeg, .png
//          handle connection for image
//      else
//          handle connection for webpage (wgat we have)

