package android.security.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileSystemPermissionTest extends DeviceTestCase {

   /**
    * A reference to the device under test.
    */
    private ITestDevice mDevice;

    /**
     * Used to build the find command for finding insecure file system components
     */
    private static final String INSECURE_DEVICE_ADB_COMMAND = "find %s -type %s -perm /o=rwx 2>/dev/null";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    public void testAllBlockDevicesAreSecure() throws Exception {
        Set<String> insecure = getAllInsecureDevicesInDirAndSubdir("/dev", "b");
        assertTrue("Found insecure block devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    /**
     * Searches for all world accessable files, note this may need sepolicy to search the desired
     * location and stat files.
     * @path The path to search, must be a directory.
     * @type The type of file to search for, must be a valid find command argument to the type
     *       option.
     * @returns The set of insecure fs objects found.
     */
    private Set<String> getAllInsecureDevicesInDirAndSubdir(String path, String type) throws DeviceNotAvailableException {

        String cmd = getInsecureDeviceAdbCommand(path, type);
        String output = mDevice.executeShellCommand(cmd);
        // Splitting an empty string results in an array of an empty string.
        String [] found = output.length() > 0 ? output.split("\\s") : new String[0];
        return new HashSet<String>(Arrays.asList(found));
    }

    private static String getInsecureDeviceAdbCommand(String path, String type) {
        return String.format(INSECURE_DEVICE_ADB_COMMAND, path, type);
    }

    private static String HW_RNG_DEVICE = "/dev/hw_random";

    public void testDevHwRandomPermissions() throws Exception {
        if (!mDevice.doesFileExist(HW_RNG_DEVICE)) {
            // Hardware RNG device is missing. This is OK because it is not required to be exposed
            // on all devices.
            return;
        }

        // This test asserts that, if present, /dev/hw_random must:
        //
        // 1. Have ownership prng_seeder:prng_seeder
        // 2. Have permissions 0400 - The only user space process requiring
        //    access is the PRNG seeder daemon which only needs read access.
        // 3. Be a character device with major:minor 10:183 (the kernel
        //    default).

        // That translates to `ls -l` output like this:
        // cr-------- 1 prng_seeder prng_seeder 10, 183 2021-02-11 17:55 /dev/hw_random

        String command = "ls -l " + HW_RNG_DEVICE;
        String output = mDevice.executeShellCommand(command).trim();
        if (!output.endsWith(" " + HW_RNG_DEVICE)) {
            fail("Unexpected output from " + command + ": \"" + output + "\"");
        }
        String[] outputWords = output.split("\\s");
        assertEquals("Wrong mode on " + HW_RNG_DEVICE, "cr--------", outputWords[0]);
        assertEquals("Wrong owner of " + HW_RNG_DEVICE, "prng_seeder", outputWords[2]);
        assertEquals("Wrong group of " + HW_RNG_DEVICE, "prng_seeder", outputWords[3]);
        assertEquals("Wrong device major on " + HW_RNG_DEVICE, "10,", outputWords[4]);
        assertEquals("Wrong device minor on " + HW_RNG_DEVICE, "183", outputWords[5]);
    }
}
