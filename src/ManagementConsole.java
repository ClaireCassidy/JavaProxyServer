import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class ManagementConsole implements Runnable {

    private volatile boolean stopped = false;
    private static ArrayList<String> blockedSitesInstance = null;   // dynamic copy of blocked.txt
    private ProxyServer proxy;          // maintain reference so can stop proxy server from console

    // for easily distinguishing management console msgs
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_RESET = "\u001B[0m";
    private static final String INSTRUCTIONS = "Enter a command to configure the Proxy Server:\n"+
            "\tBLOCK [url] \t\t- block the specified URL\n"+
            "\tUNBLOCK [url] \t\t- remove URL from blocklist\n"+
            "\tHELP \t\t\t\t- print this message again\n"+
            "\tQUIT \t\t\t\t- save blocked/cache config and safely exit program\n"+
            "\tBLOCKLIST \t\t\t- print list of blocked sites";



    public ManagementConsole() throws IOException {

        // 1. create necessary resources if they don't exist
        // 2. load blocked sites so worker threads can intercept requests
        // 3. create proxy server
        // 4. repeatedly listen for user input at stdin

        createAndLoadResources();

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

    // disambiguate between thread print statements and communication between console and end user
    public static void printMgmtStyle(String toPrint) { // threads can also ask the console to print smth on their behalf
        System.out.println(ANSI_CYAN + toPrint + ANSI_RESET);
    }

    private void createAndLoadResources() throws IOException {

        // create blocked.txt if doesn't exist
        File blockedSitesFile = new File("res\\blocked.txt");
        if (!blockedSitesFile.getParentFile().exists()) {
            if (blockedSitesFile.getParentFile().mkdirs()) {
                printMgmtStyle("Successfully created directory \"res\".");// create 'res' dir if doesn't exist)
            } else {
                printMgmtStyle("Error creating directory \"res\".");
                throw new IOException();
            }
        }
        // create res/cache if doesn't exist
        File cacheDir = new File("res\\cache");
        if (!cacheDir.exists()) {
            if (cacheDir.mkdir()) {
                printMgmtStyle("Successfully created directory \"res\\cache\".");
            } else {
                printMgmtStyle("Error creating directory \"res\".");
                throw new IOException();
            }
        }

        blockedSitesFile.getParentFile().mkdir();

        // load the blocked sites into memory
        try {
            if (!blockedSitesFile.exists()) {
                printMgmtStyle(this.toString() + "Couldn't find blocked sites file - Creating ...");
                blockedSitesFile.createNewFile();
                printMgmtStyle("Created \"res/blocked.txt\"");
            }


            Scanner sc = new Scanner(blockedSitesFile);     //file to be scanned
            blockedSitesInstance = new ArrayList<>();

            String site;
            while (sc.hasNextLine()) {
                site = sc.nextLine();
                blockedSitesInstance.add(site); // update dynamic blocked sites
            }

        } catch (FileNotFoundException e) {
            printMgmtStyle(this.toString()+"Failed to open blocked sites file");

            e.printStackTrace();
        }

    }

    @Override
    public void run() {

        Scanner in = new Scanner(System.in);

        // instructions for user
        printMgmtStyle(INSTRUCTIONS);

        while (this.isRunning()) {

            // parse next user command
            String nextInput = in.nextLine();
            try {

                String[] nextInputTokens = nextInput.split(" ");

                if (nextInputTokens[0].toLowerCase().equals("block")) {

                    printMgmtStyle("You want to block " + nextInputTokens[1]);
                    blockedSitesInstance.add(nextInputTokens[1]);
                    printMgmtStyle(nextInputTokens[1] + " successfully blocked.");

                } else if (nextInputTokens[0].toLowerCase().equals("help")) {

                    printMgmtStyle("\n" + INSTRUCTIONS + "\n");

                } else if (nextInputTokens[0].toLowerCase().equals("unblock")) {

                    int index = blockedSitesInstance.indexOf(nextInputTokens[1]);
                    if (index >= 0) {
                        blockedSitesInstance.remove(index);
                        printMgmtStyle("Successfully removed "+nextInputTokens[1]+" from blocklist.");
                    } else {    // was not blocked
                        printMgmtStyle("\""+nextInputTokens[1] + "\" not found on blocklist.");
                    }

                } else if (nextInputTokens[0].toLowerCase().equals("blocklist")) {

                    printMgmtStyle("Contents of blocklist.txt: ");
                    for (String site:blockedSitesInstance) {
                        printMgmtStyle(site);
                    }

                } else if (nextInputTokens[0].toLowerCase().equals("quit")) {
                    printMgmtStyle("Quitting ... ");

                    printMgmtStyle("\t Writing blocked sites ...");
                    saveBlockedSitesConfig();

                    // stop the proxy and the management console
                    proxy.stop();
                    this.stop();

                } else {
                    printMgmtStyle("Couldn't recognised command - please retry or enter HELP to see instructions ... ");

                }
            } catch (Exception e) {
                printMgmtStyle("Couldn't recognised command - please retry or enter HELP to see instructions ... ");

                //e.printStackTrace();
            }
        }

        // reached after QUIT entered
        in.close();
    }

    // On shutdown, called to write dynamic blocked files arraylist to blocked.txt for persistent blocking
    private void saveBlockedSitesConfig() {
        try {
            FileWriter fw = new FileWriter("res\\blocked.txt");
            for(String str:blockedSitesInstance) {
                fw.write(str + System.lineSeparator());
            }
            fw.close();
        } catch (IOException e) {
            printMgmtStyle("Error writing to blocked.txt");
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "\tMGMT-CONSOLE: ";
    }
}



