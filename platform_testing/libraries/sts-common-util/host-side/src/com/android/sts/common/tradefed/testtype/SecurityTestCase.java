/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sts.common.tradefed.testtype;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.ddmlib.Log.LogLevel;
import com.android.sts.common.HostsideMainlineModuleDetector;
import com.android.sts.common.PocPusher;
import com.android.sts.common.RegexUtils;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.WifiHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IAbi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base test class for all STS tests.
 *
 * <p>Use {@link RootSecurityTestCase} or {@link NonRootSecurityTestCase} instead.
 */
public class SecurityTestCase extends StsExtraBusinessLogicHostTestBase {

    private static final String LOG_TAG = "SecurityTestCase";
    private static final int RADIX_HEX = 16;

    protected static final int TIMEOUT_DEFAULT = 60;
    // account for the poc timer of 5 minutes (+15 seconds for safety)
    public static final int TIMEOUT_NONDETERMINISTIC = 315;

    private long kernelStartTime = -1;

    private HostsideMainlineModuleDetector mainlineModuleDetector =
            new HostsideMainlineModuleDetector(this);

    @Rule public TestName testName = new TestName();
    @Rule public PocPusher pocPusher = new PocPusher();

    private static Map<ITestDevice, IBuildInfo> sBuildInfo = new HashMap<>();
    private static Map<ITestDevice, IAbi> sAbi = new HashMap<>();
    private static Map<ITestDevice, String> sTestName = new HashMap<>();
    private static Map<ITestDevice, PocPusher> sPocPusher = new HashMap<>();

    @Option(
            name = "set-kptr_restrict",
            description = "If kptr_restrict should be set to 2 after every reboot")
    private boolean setKptr_restrict = false;

    @Option(
            name = "wifi-connect-timeout",
            description = "time in milliseconds to timeout while enabling wifi")
    private long wifiConnectTimeout = 15_000;

    @Option(
            name = "skip-wifi-failure",
            description =
                    "Whether to throw an assumption failure instead of an assertion failure when"
                            + " wifi cannot be enabled")
    private boolean skipWifiFailure = false;

    private boolean ignoreKernelAddress = false;

    /** Waits for device to be online, marks the most recent boottime of the device */
    @Before
    public void setUp() throws Exception {
        getDevice().waitForDeviceAvailable();
        updateKernelStartTime();
        // TODO:(badash@): Watch for other things to track.
        //     Specifically time when app framework starts

        sBuildInfo.put(getDevice(), getBuild());
        sAbi.put(getDevice(), getAbi());
        sTestName.put(getDevice(), testName.getMethodName());

        pocPusher.setDevice(getDevice()).setBuild(getBuild()).setAbi(getAbi());
        sPocPusher.put(getDevice(), pocPusher);

        if (setKptr_restrict) {
            boolean wasRoot = getDevice().isAdbRoot();

            if (wasRoot || getDevice().enableAdbRoot()) {
                CLog.i("setting kptr_restrict to 2");
                getDevice().executeShellCommand("echo 2 > /proc/sys/kernel/kptr_restrict");
                if (!wasRoot) {
                    getDevice().disableAdbRoot();
                }
            } else {
                CLog.i("Not a rootable device - could not set kptr_restrict to 2");
                ignoreKernelAddress = true;
            }
        }
    }

    /** Makes sure the phone is online and checks if the device crashed */
    @After
    public void tearDown() throws Exception {
        try {
            getDevice().waitForDeviceAvailable(90 * 1000);
        } catch (DeviceNotAvailableException e) {
            // Force a disconnection of all existing sessions to see if that unsticks adbd.
            getDevice().executeAdbCommand("reconnect");
            getDevice().waitForDeviceAvailable(30 * 1000);
        }

        logAndTerminateTestProcesses();

        long lastKernelStartTime = kernelStartTime;
        kernelStartTime = -1;
        // only test when the kernel start time is valid
        if (lastKernelStartTime != -1) {
            long currentKernelStartTime = getKernelStartTime();
            String bootReason = "(could not get bootreason)";
            try {
                bootReason = getDevice().getProperty("ro.boot.bootreason");
            } catch (DeviceNotAvailableException e) {
                CLog.e("Could not get ro.boot.bootreason", e);
            }
            assertWithMessage(
                            "The device has unexpectedly rebooted (%s seconds after last recorded"
                                    + " boot time, bootreason: %s)",
                            currentKernelStartTime - lastKernelStartTime, bootReason)
                    .that(currentKernelStartTime)
                    .isLessThan(lastKernelStartTime + 10);
        }
    }

    public static IBuildInfo getBuildInfo(ITestDevice device) {
        return sBuildInfo.get(device);
    }

    public static IAbi getAbi(ITestDevice device) {
        return sAbi.get(device);
    }

    public static String getTestName(ITestDevice device) {
        return sTestName.get(device);
    }

    public static PocPusher getPocPusher(ITestDevice device) {
        return sPocPusher.get(device);
    }

    // TODO convert existing assertMatches*() to RegexUtils.assertMatches*()
    // b/123237827
    @Deprecated
    public void assertMatches(String pattern, String input) throws Exception {
        RegexUtils.assertContains(pattern, input);
    }

    @Deprecated
    public void assertMatchesMultiLine(String pattern, String input) throws Exception {
        RegexUtils.assertContainsMultiline(pattern, input);
    }

    @Deprecated
    public void assertNotMatches(String pattern, String input) throws Exception {
        RegexUtils.assertNotContains(pattern, input);
    }

    @Deprecated
    public void assertNotMatchesMultiLine(String pattern, String input) throws Exception {
        RegexUtils.assertNotContainsMultiline(pattern, input);
    }

    /**
     * Runs a provided function that collects a String to test against kernel pointer leaks. The
     * getPtrFunction function implementation must return a String that starts with the pointer.
     * i.e. "01234567". Trailing characters are allowed except for [0-9a-fA-F]. In the event that
     * the pointer appears to be vulnerable, a JUnit assert is thrown. Since kernel pointers can be
     * hashed, there is a possibility the hashed pointer overlaps into the normal kernel space. The
     * test re-runs to make false positives statistically insignificant. When kernel pointers won't
     * change without a reboot, provide a device to reboot.
     *
     * @param getPtrFunction a function that returns a string that starts with a pointer
     * @param deviceToReboot device to reboot when kernel pointers won't change
     */
    public void assertNotKernelPointer(Callable<String> getPtrFunction, ITestDevice deviceToReboot)
            throws Exception {
        assumeFalse("Could not set kptr_restrict to 2, ignoring kptr test.", ignoreKernelAddress);

        MetricsReportLog reportLog = buildMetricsReportLog(getDevice());

        int kptrRestrict;
        try {
            kptrRestrict =
                    Integer.parseInt(
                            getDevice()
                                    .executeShellV2Command("cat /proc/sys/kernel/kptr_restrict")
                                    .getStdout()
                                    .trim());
        } catch (NumberFormatException e) {
            kptrRestrict = -1;
        }
        reportLog.addValue("kptr_restrict", kptrRestrict, ResultType.NEUTRAL, ResultUnit.NONE);

        boolean isKernelPointer = true;
        String ptr = null;
        for (int i = 0; i < 4; i++) { // ~0.4% chance of false positive
            ptr = getPtrFunction.call();
            if (ptr == null) {
                isKernelPointer = false;
                break;
            }
            reportLog.addValue("address" + i, ptr, ResultType.NEUTRAL, ResultUnit.NONE);

            if (!isKptr(ptr)) {
                // quit early because the ptr is likely hashed or zeroed.
                isKernelPointer = false;
                break;
            }
            if (deviceToReboot != null) {
                deviceToReboot.nonBlockingReboot();
                deviceToReboot.waitForDeviceAvailable();
                updateKernelStartTime();
            }
        }
        reportLog.addValue(
                "is_kernel_pointer", isKernelPointer, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();
        assertFalse(
                String.format(
                                "\"%s\" is an exposed kernel pointer. The device kptr_restrict is"
                                        + " \"%d\".",
                                ptr, kptrRestrict)
                        + "Please check the help center FAQ#2 at "
                        + "https://support.google.com/androidpartners_security/answer/9144408?hl=en&ref_topic=7534918",
                isKernelPointer);
    }

    private boolean isKptr(String ptr) {
        Matcher m = Pattern.compile("[0-9a-fA-F]*").matcher(ptr);
        if (!m.find() || m.start() != 0) {
            // ptr string is malformed
            return false;
        }
        int length = m.end();

        if (length == 8) {
            // 32-bit pointer
            BigInteger address = new BigInteger(ptr.substring(0, length), RADIX_HEX);
            // 32-bit kernel memory range: 0xC0000000 -> 0xffffffff
            // 0x3fffffff bytes = 1GB /  0xffffffff = 4 GB
            // 1 in 4 collision for hashed pointers
            return address.compareTo(new BigInteger("C0000000", RADIX_HEX)) >= 0;
        } else if (length == 16) {
            // 64-bit pointer
            BigInteger address = new BigInteger(ptr.substring(0, length), RADIX_HEX);
            // 64-bit kernel memory range: 0x8000000000000000 -> 0xffffffffffffffff
            // 48-bit implementation: 0xffff800000000000; 1 in 131,072 collision
            // 56-bit implementation: 0xff80000000000000; 1 in 512 collision
            // 64-bit implementation: 0x8000000000000000; 1 in 2 collision
            return address.compareTo(new BigInteger("ff80000000000000", RADIX_HEX)) >= 0;
        }

        return false;
    }

    /** Check if a driver is present and readable. */
    protected boolean containsDriver(ITestDevice device, String driver) throws Exception {
        return containsDriver(device, driver, true);
    }

    /** Check if a driver is present on a machine. */
    protected boolean containsDriver(ITestDevice device, String driver, boolean checkReadable)
            throws Exception {
        boolean containsDriver = false;
        if (driver.contains("*")) {
            // -A  list all files but . and ..
            // -d  directory, not contents
            // -1  list one file per line
            // -f  unsorted
            String ls = "ls -A -d -1 -f " + driver;
            if (device.executeShellV2Command(ls).getExitCode().intValue() == 0) {
                String[] expanded = device.executeShellCommand(ls).split("\\R");
                for (String expandedDriver : expanded) {
                    containsDriver |= containsDriver(device, expandedDriver, checkReadable);
                }
            }
        } else {
            if (checkReadable) {
                containsDriver =
                        device.executeShellV2Command("test -r " + driver).getExitCode().intValue()
                                == 0;
            } else {
                containsDriver =
                        device.executeShellV2Command("test -e " + driver).getExitCode().intValue()
                                == 0;
            }
        }

        MetricsReportLog reportLog = buildMetricsReportLog(getDevice());
        reportLog.addValue("path", driver, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue("exists", containsDriver, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        return containsDriver;
    }

    public static MetricsReportLog buildMetricsReportLog(ITestDevice device) {
        IBuildInfo buildInfo = getBuildInfo(device);
        IAbi abi = getAbi(device);
        String testName = getTestName(device);

        StackTraceElement[] stacktraces = Thread.currentThread().getStackTrace();
        int stackDepth = 2; // 0: getStackTrace(), 1: buildMetricsReportLog, 2: caller
        String className = stacktraces[stackDepth].getClassName();
        String methodName = stacktraces[stackDepth].getMethodName();
        String classMethodName = String.format("%s#%s", className, methodName);

        // The stream name must be snake_case or else json formatting breaks
        String streamName = methodName.replaceAll("(\\p{Upper})", "_$1").toLowerCase();

        MetricsReportLog reportLog =
                new MetricsReportLog(
                        buildInfo,
                        abi.getName(),
                        classMethodName,
                        "StsHostTestCases",
                        streamName,
                        true);
        reportLog.addValue("test_name", testName, ResultType.NEUTRAL, ResultUnit.NONE);
        return reportLog;
    }

    private long getDeviceUptime() throws DeviceNotAvailableException {
        String uptime = null;
        int attempts = 5;
        do {
            if (attempts-- <= 0) {
                throw new RuntimeException("could not get device uptime");
            }
            getDevice().waitForDeviceAvailable();
            uptime = getDevice().executeShellCommand("cat /proc/uptime").trim();
        } while (uptime.isEmpty());
        return Long.parseLong(uptime.substring(0, uptime.indexOf('.')));
    }

    public void safeReboot() throws DeviceNotAvailableException {
        getDevice().nonBlockingReboot();
        getDevice().waitForDeviceAvailable();
        updateKernelStartTime();
    }

    private long getKernelStartTime() throws DeviceNotAvailableException {
        long uptime = getDeviceUptime();
        return (System.currentTimeMillis() / 1000) - uptime;
    }

    /** Allows a test to pass if called after a planned reboot. */
    public void updateKernelStartTime() throws DeviceNotAvailableException {
        kernelStartTime = getKernelStartTime();
    }

    /**
     * Queries the device for any test binaries which are still running. Those found will be dumped
     * to stdout, then killed.
     */
    private void logAndTerminateTestProcesses() {
        ITestDevice device = getDevice();

        Set<String> danglingPgids = new HashSet<String>();

        try {
            // Get all pid, command pairs.
            String rawProcessList = device.executeShellCommand("ps -Ao pid,name,pgid");
            String[] processLines = rawProcessList.split("\n");

            // Extract all PIDs and commands. Format of line is "PID COMMAND"
            for (int i = 0; i < processLines.length; ++i) {
                String[] tokens = processLines[i].trim().split("\\s+");
                if (3 != tokens.length) {
                    CLog.i(
                            "process entry doesn't tokenize as expected, skipping: "
                                    + processLines[i]);
                    continue;
                }
                String pid = tokens[0];
                // Strip any brackets from the process name
                String name = tokens[1].replaceAll("\\[|\\]", "");
                String pgid = tokens[2];
                // All STS poc binaries are stored in /data/local/tmp
                if (name.startsWith("Bug") || name.startsWith("CVE")) {
                    danglingPgids.add(pgid);
                    CLog.w("Found dangling test process %s with PID %s, PGID %s", name, pid, pgid);
                }
            }
        } catch (DeviceNotAvailableException e) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    "DeviceNotAvailableException encountered while querying device for "
                            + "dangling test processes.");
            return;
        }

        try {
            if (danglingPgids.size() > 0) {
                CLog.logAndDisplay(
                        LogLevel.WARN,
                        "Found "
                                + danglingPgids.size()
                                + " dangling test process group(s). Terminating...");

                for (String pgid : danglingPgids) {
                    if (Long.parseLong(pgid) <= 1) {
                        CLog.e("PGID %s allegedly a dangling STS group, ignoring.", pgid);
                        continue;
                    }
                    String killCommand = "kill -9 -" + pgid;
                    CLog.i(killCommand);
                    String killOutput = device.executeShellCommand(killCommand);
                    CLog.i(killOutput);
                }
            }
        } catch (DeviceNotAvailableException e) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    "DeviceNotAvailableException encountered while attempting to terminate "
                            + "dangling test processes.");
        }
    }

    /**
     * Return true if a module is play managed.
     *
     * <p>Example of skipping a test based on mainline modules:
     *
     * <pre>
     *  {@literal @}Test
     *  public void testPocCVE_1234_5678() throws Exception {
     *      // This will skip the test if MODULE_METADATA mainline module is play managed.
     *      assumeFalse(moduleIsPlayManaged("com.google.android.captiveportallogin"));
     *      // Do testing...
     *  }
     * </pre>
     */
    public boolean moduleIsPlayManaged(String modulePackageName) throws Exception {
        return mainlineModuleDetector.getPlayManagedModules().contains(modulePackageName);
    }

    public void assumeIsSupportedNfcDevice(ITestDevice device) throws Exception {
        String supportedDrivers[] = {
            "/dev/nq-nci*", "/dev/pn54*", "/dev/pn551*", "/dev/pn553*",
            "/dev/pn557*", "/dev/pn65*", "/dev/pn66*", "/dev/pn67*",
            "/dev/pn80*", "/dev/pn81*", "/dev/sn100*", "/dev/sn220*",
            "/dev/st54j*", "/dev/st21nfc*"
        };
        boolean isDriverFound = false;
        for (String supportedDriver : supportedDrivers) {
            if (containsDriver(device, supportedDriver, false)) {
                isDriverFound = true;
                break;
            }
        }
        String[] output = device.executeShellCommand("ls -la /dev | grep nfc").split("\\n");
        String nfcDevice = null;
        for (String line : output) {
            if (line.contains("nfc")) {
                String text[] = line.split("\\s+");
                nfcDevice = text[text.length - 1];
            }
        }
        assumeTrue(
                "NFC device " + nfcDevice + " is not supported. Hence skipping the test",
                isDriverFound);
    }

    public WifiHelper createWifiHelper() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();
        return new WifiHelper(device, device.getOptions().getWifiUtilAPKPath(), /* doSetup */ true);
    }

    /**
     * Asserts the wifi connection status is connected. Because STS can reboot a device immediately
     * before running a test, wifi might not be connected before the test runs. We poll wifi until
     * we hit a timeout or wifi is connected.
     *
     * @param device device to be ran on
     */
    public void assertWifiConnected(ITestDevice device) throws Exception {
        assumeTrue("Wi-Fi hardware not detected", device.hasFeature("android.hardware.wifi"));

        WifiHelper wifiHelper = createWifiHelper();
        wifiHelper.enableWifi();

        long endTime = System.currentTimeMillis() + wifiConnectTimeout;
        do {
            // tests require that device are connected to a wifi network, but not requiring
            // internet connectivity
            if (!"null".equals(wifiHelper.getBSSID())) {
                return;
            }
            Thread.sleep(1000);
        } while (System.currentTimeMillis() < endTime);

        assumeFalse("Wi-Fi could not be enabled on the device; skipping", skipWifiFailure);
        // enable with one of the following:
        // '<option name="compatibility-build-provider:build-attribute" key="sts-skip-wifi-failures"
        // value="true" />'
        // '--compatibility-build-provider:build-attribute sts-skip-wifi-failures=true'
        boolean buildAttributeSkipWifiFailure =
                Boolean.parseBoolean(getBuild().getBuildAttributes().get("sts-skip-wifi-failures"));
        assumeFalse(
                "Wi-Fi could not be enabled on the device; skipping",
                buildAttributeSkipWifiFailure);
        throw new AssertionError(
                "This test requires a Wi-Fi connection on-device. "
                        + "Please consult the CTS setup guide: "
                        + "https://source.android.com/compatibility/cts/setup#wifi\n"
                        + "Also ensure \"Stay Awake\" is enabled in developer options: "
                        + "https://source.android.com/compatibility/cts/setup#config_device");
    }
}
