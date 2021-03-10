import javax.imageio.IIOException;
import java.io.IOException;

// TODO merge launcher and ManagementConsole
// Creates a ProxyServer and runs it
public class Launcher {
    public static void main(String[] args) throws IOException {

        Thread mgmtConsole = new Thread(new ManagementConsole());
        mgmtConsole.start();


//        try {
//            proxyThread.sleep(2000);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        //proxy.stop();
    }
}
