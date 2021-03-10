import javax.imageio.IIOException;
import java.io.IOException;

// Creates a ProxyServer and runs it
public class Launcher {
    public static void main(String[] args) throws IOException {
        ProxyServer proxy = new ProxyServer(8080);

        Thread proxyThread = new Thread(proxy);
        proxyThread.start();

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
