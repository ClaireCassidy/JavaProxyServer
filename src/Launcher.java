import java.io.IOException;


public class Launcher {
    public static void main(String[] args) throws IOException {

        Thread mgmtConsole = new Thread(new ManagementConsole());
        mgmtConsole.start();

    }
}
