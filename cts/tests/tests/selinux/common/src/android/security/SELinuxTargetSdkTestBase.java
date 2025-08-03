package android.security;

import android.system.Os;
import android.test.AndroidTestCase;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.junit.Assert;

abstract class SELinuxTargetSdkTestBase extends AndroidTestCase
{
    static {
        System.loadLibrary("ctsselinux_jni");
    }

    static final byte[] ANONYMIZED_HARDWARE_ADDRESS = { 0x02, 0x00, 0x00, 0x00, 0x00, 0x00 };

    protected static String getFile(String filename) throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filename));
            return in.readLine().trim();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    protected static String getProperty(String property)
            throws IOException {
        Process process = new ProcessBuilder("getprop", property).start();
        Scanner scanner = null;
        String line = "";
        try {
            scanner = new Scanner(process.getInputStream());
            line = scanner.nextLine();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return line;
    }

    /**
     * Verify that net.dns properties may not be read
     */
    protected static void noDns() throws IOException {
        String[] dnsProps = {"net.dns1", "net.dns2", "net.dns3", "net.dns4"};
        for(int i = 0; i < dnsProps.length; i++) {
            String dns = getProperty(dnsProps[i]);
            assertEquals("DNS properties may not be readable by apps past " +
                    "targetSdkVersion 26", "", dns);
        }
    }

    protected static void noNetlinkRouteGetlink() throws IOException {
        assertEquals(
                "RTM_GETLINK is not allowed on netlink route sockets. Verify that the"
                    + " following patch has been applied to your kernel: "
                    + "https://android-review.googlesource.com/q/I7b44ce60ad98f858c412722d41b9842f8577151f",
                13,
                checkNetlinkRouteGetlink());
    }

    protected static void checkNetlinkRouteGetneigh(boolean expectAllowed) throws IOException {
        if (!expectAllowed) {
            assertEquals(
                    "RTM_GETNEIGH is not allowed on netlink route sockets. Verify that the"
                        + " following patch has been applied to your kernel: "
                        + "https://r.android.com/1690896",
                    3,
                    checkNetlinkRouteGetneigh());
        } else {
            assertEquals(
                    "RTM_GETNEIGH should be allowed on netlink route sockets for apps with "
                            + "targetSdkVersion <= S",
                    0,
                    checkNetlinkRouteGetneigh());
        }
    }

    protected static void noNetlinkRouteBind() throws IOException {
        assertEquals(
                "bind() is not allowed on netlink route sockets",
                13,
                checkNetlinkRouteBind());
    }

    /**
     * Check expectations of being able to read/execute dex2oat.
     */
    protected static void checkDex2oatAccess(boolean expectedAllowed) throws Exception {
        // Check the dex2oat binary in its current and legacy locations.
        String[] locations = {"/apex/com.android.art/bin",
                              "/apex/com.android.runtime/bin",
                              "/system/bin"};
        for (String loc : locations) {
            File dex2oatBinary = new File(loc + "/dex2oat");
            if (dex2oatBinary.exists()) {
                checkDex2oatBinaryAccess(dex2oatBinary, expectedAllowed);
            }
        }
    }

    private static void checkDex2oatBinaryAccess(File dex2oatBinary, boolean expectedAllowed)
        throws Exception {
        // Check permissions.
        assertEquals(expectedAllowed, dex2oatBinary.canRead());
        assertEquals(expectedAllowed, dex2oatBinary.canExecute());

        // Try to execute dex2oat.
        try {
            Runtime rt = Runtime.getRuntime();
            Process p = rt.exec(dex2oatBinary.getAbsolutePath());
            p.waitFor();
            assertEquals(expectedAllowed, true);
        } catch (IOException ex) {
            assertEquals(expectedAllowed, false);
            assertEquals(ex.getMessage(),
                    "Cannot run program \"" + dex2oatBinary.getAbsolutePath() +
                    "\": error=13, Permission denied");
        }
    }

    /**
     * Verify that selinux context is the expected domain based on
     * targetSdkVersion,
     */
    protected void appDomainContext(String contextRegex, String errorMsg) throws IOException {
        Pattern p = Pattern.compile(contextRegex);
        Matcher m = p.matcher(getFile("/proc/self/attr/current"));
        String context = getFile("/proc/self/attr/current");
        String msg = errorMsg + context;
        assertTrue(msg, m.matches());
    }

    /**
     * Verify that selinux context is the expected type based on
     * targetSdkVersion,
     */
    protected void appDataContext(String contextRegex, String errorMsg) throws Exception {
        Pattern p = Pattern.compile(contextRegex);
        File appDataDir = getContext().getFilesDir();
        Matcher m = p.matcher(getFileContext(appDataDir.getAbsolutePath()));
        String context = getFileContext(appDataDir.getAbsolutePath());
        String msg = errorMsg + context;
        assertTrue(msg, m.matches());
    }

    protected boolean canExecuteFromHomeDir() throws Exception {
        File appDataDir = getContext().getFilesDir();
        File temp = File.createTempFile("badbin", "exe", appDataDir);
        temp.deleteOnExit();
        String path = temp.getPath();
        Os.chmod(path, 0700);
        try {
            Process process = new ProcessBuilder(path).start();
        } catch (IOException e) {
            return !e.toString().contains("Permission denied");
        } finally {
            temp.delete();
        }
        return true;
    }

    /**
     * Verify that apps are not able to see MAC addresses of ethernet devices.
     */
    protected static void checkNetworkInterfaceHardwareAddress_returnsNull() throws Exception {
        assertNotNull(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            assertNull(nif.getHardwareAddress());
        }
    }

    /**
     * Verify that apps having targetSdkVersion <= 29 get an anonymized MAC
     * address (02:00:00:00:00:00) instead of a null MAC for ethernet interfaces.
     */
    protected static void checkNetworkInterface_returnsAnonymizedHardwareAddresses()
        throws Exception {
        assertNotNull(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (isEthernet(nif.getName())) {
                Assert.assertArrayEquals(ANONYMIZED_HARDWARE_ADDRESS, nif.getHardwareAddress());
            }
        }
    }

    /**
     * Verify that hidden ro properties are not readable.
     */
    protected static void noHiddenSystemProperties() throws IOException {
        String[] hiddenProperties = {"ro.debuggable", "ro.secure"};
        for(int i = 0; i < hiddenProperties.length; i++) {
            String roHidden = getProperty(hiddenProperties[i]);
            assertEquals("Hidden system properties may not be readable by apps", "", roHidden);
        }
    }

    /**
     * Checks whether a network interface is an ethernet interface.
     */
    private static Pattern ethernetNamePattern = Pattern.compile("^(eth|wlan)[0-9]+$");
    private static boolean isEthernet(String ifName) throws Exception {
        return ethernetNamePattern.matcher(ifName).matches();
    }

    private static final native int checkNetlinkRouteGetlink();
    private static final native int checkNetlinkRouteGetneigh();
    private static final native int checkNetlinkRouteBind();
    private static final native String getFileContext(String path);
}
