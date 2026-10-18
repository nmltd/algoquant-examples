import dev.nm.misc.license.License;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.security.Permission;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartPanel;

/** Runs one unchanged demo main and captures its JFreeChart windows before exiting. */
public final class ModelDemoRunner {
    private static final AtomicInteger CHARTS = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        License.setLicenseFile(new File(System.getProperty("nm.license")));
        // These public tutorial runs have no configured database account. Reject the
        // legacy MySQL connection before the client can send embedded credentials.
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) {
                if (port == 3306) throw new SecurityException(
                    "External database access is not configured for this public tutorial run.");
            }
            @Override public void checkConnect(String host, int port, Object context) {
                checkConnect(host, port);
            }
        });
        System.out.println("DEMO_RUNNER_BEGIN " + args[0]);
        Throwable failure = null;
        try {
            String[] options = java.util.Arrays.copyOfRange(args, 1, args.length);
            Class.forName(args[0]).getMethod("main", String[].class).invoke(null, (Object) options);
            System.out.println("DEMO_RUNNER_MAIN_RETURNED");
        } catch (InvocationTargetException ex) {
            failure = ex.getCause();
        } catch (Throwable ex) {
            failure = ex;
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    capture(window);
                    window.dispose();
                }
            });
        } catch (Throwable ex) {
            System.err.println("Chart capture failed: " + ex.getClass().getSimpleName());
        }
        if (failure != null) {
            System.err.println("DEMO_RUNNER_FAILURE " + failure.getClass().getName());
            failure.printStackTrace();
        }
        System.out.println("DEMO_RUNNER_CHARTS " + CHARTS.get());
        // Desktop event threads must not turn a completed main into an apparent timeout.
        System.exit(failure == null ? 0 : 1);
    }

    private static void capture(Component component) {
        if (component instanceof ChartPanel) {
            try {
                File directory = new File("charts");
                directory.mkdirs();
                File target = new File(directory, String.format("chart-%02d.png", CHARTS.incrementAndGet()));
                ImageIO.write(((ChartPanel) component).getChart().createBufferedImage(1280, 800), "png", target);
                System.out.println("DEMO_RUNNER_CHART " + target.getPath());
            } catch (Exception ex) {
                System.err.println("Chart export failed: " + ex.getClass().getSimpleName());
            }
        }
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) capture(child);
    }
}
