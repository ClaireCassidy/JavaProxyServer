// Creates a ProxyServer and runs it
public class Launcher {
    public static void main(String[] args) {
        ProxyServer proxy = new ProxyServer(1408);


        Thread proxyThread = new Thread(proxy);
        proxyThread.start();

        try {
            proxyThread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        proxy.stop();
    }
}
