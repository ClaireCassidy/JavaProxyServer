import java.util.Scanner;

public class ManagementConsole implements Runnable {

    private boolean stopped = false;

    // may be called from Launcher to stop the mgmt console
    public synchronized void stop() {
        System.out.println("ProxyServer stopping ...");
        this.stopped = true;
    }

    private synchronized boolean isRunning() {
        return this.stopped == false;
    }


    @Override
    public void run() {
        Scanner in = new Scanner(System.in);

        while (isRunning()) {
            String nextInput = in.nextLine();
            System.out.println("ECHO: " + nextInput);
        }

        in.close();
    }
}
