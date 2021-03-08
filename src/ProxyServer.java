public class ProxyServer implements Runnable {

    private boolean stopped = false;

    // may be called from Launcher to stop the server
    public synchronized void stop() {
        System.out.println("ProxyServer stopping ...");
        this.stopped = true;
    }


    private synchronized boolean isRunning() {
        return this.stopped == false;
    }

    @Override
    public void run() {
        System.out.println("ProxyServer Running");

        int count = 0;
        while (!this.stopped) {
            count++;
            System.out.println(count);
        }
    }
}
