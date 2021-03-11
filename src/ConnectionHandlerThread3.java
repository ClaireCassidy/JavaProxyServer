import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectionHandlerThread3 extends Thread {

    private Socket incoming;
    private static int global_id;   // helps identify the threads; incremented every time a thread is created in the session
    private int id; // local id
    private InputStream inputStream;
    private OutputStream outputStream;

    public ConnectionHandlerThread3(Socket incoming) throws IOException {

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
        try {
            inputStream = incoming.getInputStream();
            outputStream = incoming.getOutputStream();

            int count;
            byte[] buffer = new byte[8192]; // or 4096, or more
            while ((count = inputStream.read(buffer)) > 0)
            {
                outputStream.write(buffer, 0, count);
            }

        } catch (IOException e) {
            System.out.println("IOException in ConnectionHandlerThread " + id);
            e.printStackTrace();
        }
    }

}
