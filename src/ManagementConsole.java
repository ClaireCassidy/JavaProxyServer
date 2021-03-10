import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class ManagementConsole implements Runnable {

    private boolean stopped = false;
    private static ArrayList<String> blockedSitesInstance = null;


    public ManagementConsole() {
        // open blocked sites file

        File blockedSitesFile = new File("res\\blocked.txt");

        try {
            if (blockedSitesFile.exists()) {
                Scanner sc = new Scanner(blockedSitesFile);     //file to be scanned
                blockedSitesInstance = new ArrayList<>();

                System.out.println("Blocked:");

                String site;
                while (sc.hasNextLine()) {
                    site = sc.nextLine();
                    System.out.println(site);
                    blockedSitesInstance.add(site); // update dynamic blocked sites
                }
            } else {
                System.out.println(this.toString()+"Couldn't find blocked sites file - check path");
            }
        } catch (FileNotFoundException e) {
            System.out.println(this.toString()+"Failed to open blocked sites file");

            e.printStackTrace();
        }

    }

    public static ArrayList<String> getBlockedSites() {
        return blockedSitesInstance;
    }

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

    @Override
    public String toString() {
        return "\tMGMT-CONSOLE: ";
    }
}

