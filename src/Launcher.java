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

// 1. check the directory for a filename that matches the url
// 2. if match -> open file -> get first line -> check cur date time against expiry date
//              -> if expiry date OK -> get rest of file and serve
//              -> else -> delete file from cache -> forward request as normal -> save response to cache
// 3. if no match -> forward request as normal -> save response to cache
