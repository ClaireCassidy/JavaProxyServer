import com.sun.xml.internal.ws.api.message.ExceptionHasMessage;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Scanner;

public class ManagementConsole implements Runnable {

    private volatile boolean stopped = false;
    private static ArrayList<String> blockedSitesInstance = null;
    private ProxyServer proxy;

    // for easily distinguishing management console msgs
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_RESET = "\u001B[0m";
    private static final String INSTRUCTIONS = "Enter a command to configure the Proxy Server:\n"+
            "\tBLOCK [url] \t\t- block the specified URL\n"+
            "\tHELP \t\t\t\t- print this message again\n"+
            "\tQUIT \t\t\t\t- save blocked/cache config and safely exit program\n"+
            "\tBLOCKLIST \t\t\t- print list of blocked sites";


    public ManagementConsole() throws IOException {
        // open blocked sites file

        File blockedSitesFile = new File("res\\blocked.txt");
        File cacheFile = new File("res\\cache.csv");

        // load the blocked sites into memory
        try {
            if (blockedSitesFile.exists()) {
                Scanner sc = new Scanner(blockedSitesFile);     //file to be scanned
                blockedSitesInstance = new ArrayList<>();

                printMgmtStyle("Blocked:");

                String site;
                while (sc.hasNextLine()) {
                    site = sc.nextLine();
                    printMgmtStyle(site);
                    blockedSitesInstance.add(site); // update dynamic blocked sites
                }
            } else {
                printMgmtStyle(this.toString()+"Couldn't find blocked sites file - check path");
            }
        } catch (FileNotFoundException e) {
            printMgmtStyle(this.toString()+"Failed to open blocked sites file");

            e.printStackTrace();
        }

        // can't load the cache dynamically.

        // create the proxy server
        proxy = new ProxyServer(8080);

        Thread proxyThread = new Thread(proxy);
        proxyThread.start();
    }

    public static ArrayList<String> getBlockedSites() {
        return blockedSitesInstance;
    }

    // may be called from Launcher to stop the mgmt console
    public synchronized void stop() {

        printMgmtStyle("Shutting down application ...");
        this.stopped = true;
    }

    private synchronized boolean isRunning() {
        return this.stopped == false;
    }

    public static void printMgmtStyle(String toPrint) { // threads can also ask the console to print smth on their behalf
        System.out.println(ANSI_CYAN + toPrint + ANSI_RESET);
    }

    @Override
    public void run() {

        Scanner in = new Scanner(System.in);

        printMgmtStyle(INSTRUCTIONS);

        while (this.isRunning()) {
            System.out.println(this.isRunning());

            String nextInput = in.nextLine();
            try {



                String[] nextInputTokens = nextInput.split(" ");
                if (nextInputTokens[0].toLowerCase().equals("block")) {
                    printMgmtStyle("You wanna block " + nextInputTokens[1]);
                    printMgmtStyle("Blocking ...");
                    blockedSitesInstance.add(nextInputTokens[1]);
                } else if (nextInputTokens[0].toLowerCase().equals("help")) {
                    printMgmtStyle("\n" + INSTRUCTIONS + "\n");
                } else if (nextInputTokens[0].toLowerCase().equals("unblock")) {
                    int index = blockedSitesInstance.indexOf(nextInputTokens[1]);
                    if (index >= 0) {
                        blockedSitesInstance.remove(index);
                        printMgmtStyle("Successfully removed "+nextInputTokens[1]+" from blocklist.");

                        for (String site:blockedSitesInstance) {
                            printMgmtStyle(site);
                        }
                    } else {    // not blocked
                        printMgmtStyle("\""+nextInputTokens[1] + "\" not found on blocklist.");
                    }
                } else if (nextInputTokens[0].toLowerCase().equals("blocklist")) {
                    for (String site:blockedSitesInstance) {
                        printMgmtStyle(site);
                    }
                }
                else if (nextInputTokens[0].toLowerCase().equals("quit")) {
                    printMgmtStyle("Quitting ... ");
                    printMgmtStyle("\t Writing blocked sites ...");
                    saveBlockedSitesConfig();
                    printMgmtStyle("\tWriting cache ...");
                    // TODO saveCacheConfig();
                    proxy.stop();
                    this.stop();
                } else {
                    throw new IOException();
                }
            } catch (Exception e) {
                printMgmtStyle("Couldn't recognised command - please retry or enter HELP to see instructions ... ");
            }
        }

        in.close();
    }

    private void saveBlockedSitesConfig() {
        try {
            FileWriter fw = new FileWriter("res\\blocked.txt");
            for(String str:blockedSitesInstance) {
                fw.write(str + System.lineSeparator());
            }
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "\tMGMT-CONSOLE: ";
    }
}



