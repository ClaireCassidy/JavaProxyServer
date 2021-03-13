
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/* services a one-way HTTPS connection (tunnel) between the client and the desired endpoint;
*   a second HttpsConnectorThread services data flow in the opoosite direction */
public class HttpsConnectorThread extends Thread {

    InputStream in;
    OutputStream out;

    // helps identify the threads; incremented every time a thread is created in the session
    private static int global_id;
    private int id;                 // local id

    public HttpsConnectorThread(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;

        // assign id to thread
        setId();
    }

    // prevent race condition
    private synchronized void setId() {
        global_id++;
        id = global_id;
//        System.out.println("Established HttpsConnectorThread:" + id);
    }

    @Override
    public void run() {

        try {
            byte[] responseBuffer = new byte[8192]; // buffer received data
            // will be > 0 if last read operation got some bytes
            int amountBytesToSend;

            // keep checking we have bytes
            while ((amountBytesToSend = in.read(responseBuffer, 0 , responseBuffer.length)) != -1) {
                out.write(responseBuffer, 0, amountBytesToSend);          // send them
                out.flush();
            }
        } catch (IOException e) { // don't print these as they tend to flood console
            // System.out.println(this.toString()+" experienced IOException");
            //e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "HttpsConnectorThread:"+id;
    }
}