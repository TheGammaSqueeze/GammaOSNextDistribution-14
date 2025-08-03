/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RestrictedBuildTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Host-side SELinux tests.
 *
 * These tests analyze the policy file in use on the subject device directly or
 * run as the shell user to evaluate aspects of the state of SELinux on the test
 * device which otherwise would not be available to a normal apk.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class SELinuxHostTest extends BaseHostJUnit4Test {

    // Keep in sync with AndroidTest.xml
    private static final String DEVICE_INFO_DEVICE_DIR = "/sdcard/device-info-files/";
    // Keep in sync with com.android.compatibility.common.deviceinfo.VintfDeviceInfo
    private static final String VINTF_DEVICE_CLASS = "VintfDeviceInfo";
    // Keep in sync with
    // com.android.compatibility.common.deviceinfo.DeviceInfo#testCollectDeviceInfo()
    private static final String DEVICE_INFO_SUFFIX = ".deviceinfo.json";
    private static final String VINTF_DEVICE_JSON = VINTF_DEVICE_CLASS + DEVICE_INFO_SUFFIX;
    // Keep in sync with com.android.compatibility.common.deviceinfo.VintfDeviceInfo
    private static final String SEPOLICY_VERSION_JSON_KEY = "sepolicy_version";
    private static final String PLATFORM_SEPOLICY_VERSION_JSON_KEY = "platform_sepolicy_version";

    private static final Map<ITestDevice, File> cachedDevicePolicyFiles = new HashMap<>(1);
    private static final Map<ITestDevice, File> cachedDevicePlatFcFiles = new HashMap<>(1);
    private static final Map<ITestDevice, File> cachedDeviceVendorFcFiles = new HashMap<>(1);
    private static final Map<ITestDevice, File> cachedDeviceVendorManifest = new HashMap<>(1);
    private static final Map<ITestDevice, File> cachedDeviceVintfJson = new HashMap<>(1);
    private static final Map<ITestDevice, File> cachedDeviceSystemPolicy = new HashMap<>(1);

    private File mSepolicyAnalyze;
    private File checkSeapp;
    private File checkFc;
    private File aospFcFile;
    private File aospPcFile;
    private File aospSvcFile;
    private File devicePolicyFile;
    private File deviceSystemPolicyFile;
    private File devicePlatFcFile;
    private File deviceVendorFcFile;
    private File devicePcFile;
    private File deviceSvcFile;
    private File seappNeverAllowFile;
    private File copyLibcpp;
    private File sepolicyTests;

    private IBuildInfo mBuild;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    public static File copyResourceToTempFile(String resName) throws IOException {
        InputStream is = SELinuxHostTest.class.getResourceAsStream(resName);
        String tempFileName = "SELinuxHostTest" + resName.replace("/", "_");
        File tempFile = createTempFile(tempFileName, ".tmp");
        FileOutputStream os = new FileOutputStream(tempFile);
        byte[] buf = new byte[1024];
        int len;

        while ((len = is.read(buf)) != -1) {
            os.write(buf, 0, len);
        }
        os.flush();
        os.close();
        return tempFile;
    }

    private static void appendTo(String dest, String src) throws IOException {
        try (FileInputStream is = new FileInputStream(new File(src));
             FileOutputStream os = new FileOutputStream(new File(dest))) {
            byte[] buf = new byte[1024];
            int len;

            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        mBuild = getBuild();
        // Assumes every test in this file asserts a requirement of CDD section 9.
        assumeSecurityModelCompat();

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        mSepolicyAnalyze = copyResourceToTempFile("/sepolicy-analyze");
        mSepolicyAnalyze.setExecutable(true);

        devicePolicyFile = getDevicePolicyFile(mDevice);
        if (isSepolicySplit(mDevice)) {
            devicePlatFcFile = getDeviceFile(mDevice, cachedDevicePlatFcFiles,
                    "/system/etc/selinux/plat_file_contexts", "plat_file_contexts");
            deviceVendorFcFile = getDeviceFile(mDevice, cachedDeviceVendorFcFiles,
                    "/vendor/etc/selinux/vendor_file_contexts", "vendor_file_contexts");
            deviceSystemPolicyFile =
                    android.security.cts.SELinuxHostTest.getDeviceSystemPolicyFile(mDevice);
        } else {
            devicePlatFcFile = getDeviceFile(mDevice, cachedDevicePlatFcFiles,
                    "/plat_file_contexts", "plat_file_contexts");
            deviceVendorFcFile = getDeviceFile(mDevice, cachedDeviceVendorFcFiles,
                    "/vendor_file_contexts", "vendor_file_contexts");
        }
    }

    @After
    public void cleanUp() throws Exception {
        mSepolicyAnalyze.delete();
    }

    private void assumeSecurityModelCompat() throws Exception {
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel = Math.min(PropertyUtil.getFirstApiLevel(mDevice),
                PropertyUtil.getVendorApiLevel(mDevice));
        if (firstApiLevel >= 31) {
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    getDevice().hasFeature("feature:android.hardware.security.model.compatible"));
        }
    }

    /*
     * IMPLEMENTATION DETAILS: We cache some host-side policy files on per-device basis (in case
     * CTS supports running against multiple devices at the same time). HashMap is used instead
     * of WeakHashMap because in the grand scheme of things, keeping ITestDevice and
     * corresponding File objects from being garbage-collected is not a big deal in CTS. If this
     * becomes a big deal, this can be switched to WeakHashMap.
     */
    private static File getDeviceFile(ITestDevice device,
            Map<ITestDevice, File> cache, String deviceFilePath,
            String tmpFileName) throws Exception {
        if (!device.doesFileExist(deviceFilePath)){
            throw new Exception();
        }
        File file;
        synchronized (cache) {
            file = cache.get(device);
        }
        if (file != null) {
            return file;
        }
        file = createTempFile(tmpFileName, ".tmp");
        device.pullFile(deviceFilePath, file);
        synchronized (cache) {
            cache.put(device, file);
        }
        return file;
    }

    private static File buildSystemPolicy(ITestDevice device, Map<ITestDevice, File> cache,
            String tmpFileName) throws Exception {
        File builtPolicyFile;
        synchronized (cache) {
            builtPolicyFile = cache.get(device);
        }
        if (builtPolicyFile != null) {
            return builtPolicyFile;
        }


        builtPolicyFile = createTempFile(tmpFileName, ".tmp");

        File secilc = copyResourceToTempFile("/secilc");
        secilc.setExecutable(true);

        File systemSepolicyCilFile = createTempFile("plat_sepolicy", ".cil");
        File fileContextsFile = createTempFile("file_contexts", ".txt");

        assertTrue(device.pullFile("/system/etc/selinux/plat_sepolicy.cil", systemSepolicyCilFile));

        String errorString = tryRunCommand(secilc.getAbsolutePath(),
                "-m", "-M", "true", "-c", "30",
                "-o", builtPolicyFile.getAbsolutePath(),
                "-f", fileContextsFile.getAbsolutePath(),
                systemSepolicyCilFile.getAbsolutePath());
        assertTrue(errorString, errorString.length() == 0);

        synchronized (cache) {
            cache.put(device, builtPolicyFile);
        }
        return builtPolicyFile;
    }

    /**
     * Returns the host-side file containing the SELinux policy of the device under test.
     */
    public static File getDevicePolicyFile(ITestDevice device) throws Exception {
        return getDeviceFile(device, cachedDevicePolicyFiles, "/sys/fs/selinux/policy", "sepolicy");
    }

    /**
     * Returns the host-side file containing the system SELinux policy of the device under test.
     */
    public static File getDeviceSystemPolicyFile(ITestDevice device) throws Exception {
        return buildSystemPolicy(device, cachedDeviceSystemPolicy, "system_sepolicy");
    }

    /**
     * Returns the major number of sepolicy version of device's vendor implementation.
     */
    public static int getVendorSepolicyVersion(IBuildInfo build, ITestDevice device)
            throws Exception {

        // Try different methods to get vendor SEPolicy version in the following order:
        // 1. Retrieve from IBuildInfo as stored by DeviceInfoCollector (relies on #2)
        // 2. If it fails, retrieve from device info JSON file stored on the device
        //    (relies on android.os.VintfObject)
        // 3. If it fails, retrieve from raw VINTF device manifest files by guessing its path on
        //    the device
        // Usually, the method #1 should work. If it doesn't, fallback to method #2 and #3. If
        // none works, throw the error from method #1.
        Exception buildInfoEx;
        try {
            return getVendorSepolicyVersionFromBuildInfo(build);
        } catch (Exception ex) {
            CLog.e("getVendorSepolicyVersionFromBuildInfo failed: ", ex);
            buildInfoEx = ex;
        }
        try {
            return getVendorSepolicyVersionFromDeviceJson(device);
        } catch (Exception ex) {
            CLog.e("getVendorSepolicyVersionFromDeviceJson failed: ", ex);
        }
        try {
            return getVendorSepolicyVersionFromManifests(device);
        } catch (Exception ex) {
            CLog.e("getVendorSepolicyVersionFromManifests failed: ", ex);
            throw buildInfoEx;
        }
    }

    /**
     * Returns VSR (Vendor Software Requirements) api level. Returns 0 if the property
     * ro.vendor.api_level doesn't exist
     */
    private static int getVSRApiLevel(ITestDevice device) throws Exception {
        try {
            return Integer.parseInt(device.getProperty("ro.vendor.api_level"));
        } catch (Exception ex) {
            CLog.e("getProperty(\"ro.vendor.api_level\") failed: ", ex);
            return 0;
        }
    }

    /**
     * Retrieve the major number of sepolicy version from VINTF device info stored in the given
     * IBuildInfo by {@link DeviceInfoCollector}.
     */
    private static int getVendorSepolicyVersionFromBuildInfo(IBuildInfo build) throws Exception {
        File deviceInfoDir = build.getFile(DeviceInfoCollector.DEVICE_INFO_DIR);
        File vintfJson = deviceInfoDir.toPath().resolve(VINTF_DEVICE_JSON).toFile();
        return getVendorSepolicyVersionFromJsonFile(vintfJson);
    }

    /**
     * Retrieve the major number of sepolicy version from VINTF device info stored on the device by
     * VintfDeviceInfo.
     */
    private static int getVendorSepolicyVersionFromDeviceJson(ITestDevice device) throws Exception {
        File vintfJson = getDeviceFile(device, cachedDeviceVintfJson,
                DEVICE_INFO_DEVICE_DIR + VINTF_DEVICE_JSON, VINTF_DEVICE_JSON);
        return getVendorSepolicyVersionFromJsonFile(vintfJson);
    }

    /**
     * Retrieve the major number of sepolicy version from the given JSON string that contains VINTF
     * device info.
     */
    private static int getVendorSepolicyVersionFromJsonFile(File vintfJson) throws Exception {
        String content = FileUtil.readStringFromFile(vintfJson);
        JSONObject object = new JSONObject(content);
        String version = object.getString(SEPOLICY_VERSION_JSON_KEY);
        return getSepolicyVersionFromMajorMinor(version);
    }

    /**
     * Deprecated.
     * Retrieve the major number of sepolicy version from raw device manifest XML files.
     * Note that this is depends on locations of VINTF devices files at Android 10 and do not
     * search new paths, hence this may not work on devices launching Android 11 and later.
     */
    private static int getVendorSepolicyVersionFromManifests(ITestDevice device) throws Exception {
        String deviceManifestPath =
                (device.doesFileExist("/vendor/etc/vintf/manifest.xml")) ?
                "/vendor/etc/vintf/manifest.xml" :
                "/vendor/manifest.xml";
        File vendorManifestFile = getDeviceFile(device, cachedDeviceVendorManifest,
                deviceManifestPath, "manifest.xml");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(vendorManifestFile);
        Element root = doc.getDocumentElement();
        Element sepolicy = (Element) root.getElementsByTagName("sepolicy").item(0);
        Element version = (Element) sepolicy.getElementsByTagName("version").item(0);
        return getSepolicyVersionFromMajorMinor(version.getTextContent());
    }

    /**
     * Returns the major number of sepolicy version of system.
     */
    public static int getSystemSepolicyVersion(IBuildInfo build) throws Exception {
        File deviceInfoDir = build.getFile(DeviceInfoCollector.DEVICE_INFO_DIR);
        File vintfJson = deviceInfoDir.toPath().resolve(VINTF_DEVICE_JSON).toFile();
        String content = FileUtil.readStringFromFile(vintfJson);
        JSONObject object = new JSONObject(content);
        String version = object.getString(PLATFORM_SEPOLICY_VERSION_JSON_KEY);
        return getSepolicyVersionFromMajorMinor(version);
    }

    /**
     * Get the major number from an SEPolicy version string, e.g. "27.0" => 27.
     */
    private static int getSepolicyVersionFromMajorMinor(String version) {
        String sepolicyVersion = version.split("\\.")[0];
        return Integer.parseInt(sepolicyVersion);
    }

    /**
     * Tests that the kernel is enforcing selinux policy globally.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testGlobalEnforcing() throws Exception {
        CollectingOutputReceiver out = new CollectingOutputReceiver();
        mDevice.executeShellCommand("cat /sys/fs/selinux/enforce", out);
        assertEquals("SELinux policy is not being enforced!", "1", out.getOutput());
    }

    /**
     * Tests that all domains in the running policy file are in enforcing mode
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @RestrictedBuildTest
    @Test
    public void testAllDomainsEnforcing() throws Exception {

        /* run sepolicy-analyze permissive check on policy file */
        String errorString = tryRunCommand(mSepolicyAnalyze.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(), "permissive");
        assertTrue("The following SELinux domains were found to be in permissive mode:\n"
                   + errorString, errorString.length() == 0);
    }

    /**
     * Asserts that specified type is not associated with the specified
     * attribute.
     *
     * @param attribute
     *  The attribute name.
     * @param type
     *  The type name.
     */
    private void assertNotInAttribute(String attribute, String badtype) throws Exception {
        Set<String> actualTypes = sepolicyAnalyzeGetTypesAssociatedWithAttribute(attribute);
        if (actualTypes.contains(badtype)) {
            fail("Attribute " + attribute + " includes " + badtype);
        }
    }

    private static final byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            result.write(buf, 0, chunkSize);
        }
        return result.toByteArray();
    }

    /**
     * Runs sepolicy-analyze against the device's SELinux policy and returns the set of types
     * associated with the provided attribute.
     */
    private Set<String> sepolicyAnalyzeGetTypesAssociatedWithAttribute(
            String attribute) throws Exception {
        ProcessBuilder pb =
                new ProcessBuilder(
                        mSepolicyAnalyze.getAbsolutePath(),
                        devicePolicyFile.getAbsolutePath(),
                        "attribute",
                        attribute);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int errorCode = p.waitFor();
        if (errorCode != 0) {
            fail("sepolicy-analyze attribute " + attribute + " failed with error code " + errorCode
                    + ": " + new String(readFully(p.getInputStream())));
        }
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            Set<String> types = new HashSet<>();
            String type;
            while ((type = in.readLine()) != null) {
                types.add(type.trim());
            }
            return types;
        }
    }

    /**
     * Returns {@code true} if this device is required to be a full Treble device.
     */
    public static boolean isFullTrebleDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        return PropertyUtil.getFirstApiLevel(device) > 26 &&
                PropertyUtil.propertyEquals(device, "ro.treble.enabled", "true");
    }

    private boolean isFullTrebleDevice() throws DeviceNotAvailableException {
        return isFullTrebleDevice(mDevice);
    }

    /**
     * Returns {@code true} if this device is required to enforce compatible property.
     */
    public static boolean isCompatiblePropertyEnforcedDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        return PropertyUtil.propertyEquals(
                device, "ro.actionable_compatible_property.enabled", "true");
    }

    /**
     * Returns {@code true} if this device has sepolicy split across different paritions.
     * This is possible even for devices launched at api level higher than 26.
     */
    public static boolean isSepolicySplit(ITestDevice device)
            throws DeviceNotAvailableException {
        return PropertyUtil.getFirstApiLevel(device) > 34 /* Build.VERSION_CODES.UPSIDE_DOWN_CAKE */
                || device.doesFileExist("/system/etc/selinux/plat_file_contexts");
    }

    /**
     * Asserts that no HAL server domains are exempted from the prohibition of socket use with the
     * only exceptions for the automotive device type.
     */
    @Test
    public void testNoExemptionsForSocketsUseWithinHalServer() throws Exception {
        if (!isFullTrebleDevice()) {
            return;
        }

        if (getDevice().hasFeature("feature:android.hardware.type.automotive")) {
            return;
        }

        Set<String> types = sepolicyAnalyzeGetTypesAssociatedWithAttribute(
                "hal_automotive_socket_exemption");
        if (!types.isEmpty()) {
            List<String> sortedTypes = new ArrayList<>(types);
            Collections.sort(sortedTypes);
            fail("Policy exempts domains from ban on socket usage from HAL servers: "
                    + sortedTypes);
        }
    }

    /**
     * Tests that mlstrustedsubject does not include untrusted_app
     * and that mlstrustedobject does not include app_data_file.
     * This helps prevent circumventing the per-user isolation of
     * normal apps via levelFrom=user.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testMLSAttributes() throws Exception {
        assertNotInAttribute("mlstrustedsubject", "untrusted_app");
        assertNotInAttribute("mlstrustedobject", "app_data_file");
    }

    /**
     * Tests that the seapp_contexts file on the device is valid.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testValidSeappContexts() throws Exception {
        /* obtain seapp_contexts file from running device
         *
         * PLEASE KEEP IN SYNC WITH:
         * external/selinux/libselinux/src/android/android_seapp.c
         */
        File platformSeappFile = createTempFile("plat_seapp_contexts", ".tmp");
        File systemExtSeappFile = createTempFile("system_ext_seapp_contexts", ".tmp");
        File productSeappFile = createTempFile("product_seapp_contexts", ".tmp");
        File vendorSeappFile = createTempFile("vendor_seapp_contexts", ".tmp");
        File odmSeappFile = createTempFile("odm_seapp_contexts", ".tmp");
        if (mDevice.pullFile("/system/etc/selinux/plat_seapp_contexts", platformSeappFile)) {
            mDevice.pullFile("/system_ext/etc/selinux/system_ext_seapp_contexts",
                    systemExtSeappFile);
            mDevice.pullFile("/product/etc/selinux/product_seapp_contexts", productSeappFile);
            mDevice.pullFile("/vendor/etc/selinux/vendor_seapp_contexts", vendorSeappFile);
            mDevice.pullFile("/odm/etc/selinux/odm_seapp_contexts", odmSeappFile);
        } else {
            mDevice.pullFile("/plat_seapp_contexts", platformSeappFile);
            mDevice.pullFile("/system_ext_seapp_contexts", systemExtSeappFile);
            mDevice.pullFile("/product_seapp_contexts", productSeappFile);
            mDevice.pullFile("/vendor_seapp_contexts", vendorSeappFile);
            mDevice.pullFile("/odm_seapp_contexts", odmSeappFile);
        }

        /* retrieve the checkseapp executable from jar */
        checkSeapp = copyResourceToTempFile("/checkseapp");
        checkSeapp.setExecutable(true);

        /* retrieve the AOSP seapp_neverallows file from jar */
        seappNeverAllowFile = copyResourceToTempFile("/plat_seapp_neverallows");

        /* run checkseapp on seapp_contexts */
        String errorString = tryRunCommand(checkSeapp.getAbsolutePath(),
                "-p", devicePolicyFile.getAbsolutePath(),
                seappNeverAllowFile.getAbsolutePath(),
                platformSeappFile.getAbsolutePath(),
                systemExtSeappFile.getAbsolutePath(),
                productSeappFile.getAbsolutePath(),
                vendorSeappFile.getAbsolutePath(),
                odmSeappFile.getAbsolutePath());
        assertTrue("The seapp_contexts file was invalid:\n"
                   + errorString, errorString.length() == 0);

        /* run checkseapp on vendor contexts to find coredomain violations, starting from V */
        int vsrVersion = getVSRApiLevel(getDevice());
        if (vsrVersion > 34) /* V or later */ {
            errorString = tryRunCommand(checkSeapp.getAbsolutePath(),
                    "-p", devicePolicyFile.getAbsolutePath(),
                    "-c", /* coredomain check */
                    vendorSeappFile.getAbsolutePath(),
                    odmSeappFile.getAbsolutePath());
            assertTrue("vendor seapp_contexts contains coredomain:\n"
                    + errorString, errorString.length() == 0);
        }
    }

    /**
     * Asserts that the actual file contains all the lines from the expected file.
     * It does not guarantee the order of the lines.
     *
     * @param expectedFile
     *  The file with the expected contents.
     * @param actualFile
     *  The actual file being checked.
     */
    private void assertContainsAllLines(File expectedFile, File actualFile) throws Exception {
        List<String> expectedLines = Files.readAllLines(expectedFile.toPath());
        List<String> actualLines = Files.readAllLines(actualFile.toPath());

        expectedLines.replaceAll(String::trim);
        actualLines.replaceAll(String::trim);

        HashSet<String> expected = new HashSet(expectedLines);
        HashSet<String> actual = new HashSet(actualLines);

        /* remove all seen lines from expected, ignoring new entries */
        expected.removeAll(actual);
        assertTrue("Line removed: " + String.join("\n", expected), expected.isEmpty());
    }

    /**
     * Tests that the seapp_contexts file on the device contains
     * the standard AOSP entries.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testAospSeappContexts() throws Exception {

        /* obtain seapp_contexts file from running device */
        File platformSeappFile = createTempFile("seapp_contexts", ".tmp");
        if (!mDevice.pullFile("/system/etc/selinux/plat_seapp_contexts", platformSeappFile)) {
            mDevice.pullFile("/plat_seapp_contexts", platformSeappFile);
        }
        /* retrieve the AOSP seapp_contexts file from jar */
        File aospSeappFile = copyResourceToTempFile("/plat_seapp_contexts");

        assertContainsAllLines(aospSeappFile, platformSeappFile);
    }

    /**
     * Tests that the plat_file_contexts file on the device contains
     * the standard AOSP entries.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testAospFileContexts() throws Exception {

        /* retrieve the checkfc executable from jar */
        checkFc = copyResourceToTempFile("/checkfc");
        checkFc.setExecutable(true);

        /* retrieve the AOSP file_contexts file from jar */
        aospFcFile = copyResourceToTempFile("/plat_file_contexts");

        /* run checkfc -c plat_file_contexts plat_file_contexts */
        String result = tryRunCommand(checkFc.getAbsolutePath(),
                "-c", aospFcFile.getAbsolutePath(),
                devicePlatFcFile.getAbsolutePath()).trim();
        assertTrue("The file_contexts file did not include the AOSP entries:\n"
                   + result + "\n",
                   result.equals("equal") || result.equals("subset"));
    }

    /**
     * Tests that the property_contexts file on the device contains
     * the standard AOSP entries.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testAospPropertyContexts() throws Exception {

        /* obtain property_contexts file from running device */
        devicePcFile = createTempFile("plat_property_contexts", ".tmp");
        // plat_property_contexts may be either in /system/etc/sepolicy or in /
        if (!mDevice.pullFile("/system/etc/selinux/plat_property_contexts", devicePcFile)) {
            mDevice.pullFile("/plat_property_contexts", devicePcFile);
        }

        // Retrieve the AOSP property_contexts file from JAR.
        // The location of this file in the JAR has nothing to do with the location of this file on
        // Android devices. See build script of this CTS module.
        aospPcFile = copyResourceToTempFile("/plat_property_contexts");

        assertContainsAllLines(aospPcFile, devicePcFile);
    }

    /**
     * Tests that the service_contexts file on the device contains
     * the standard AOSP entries.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testAospServiceContexts() throws Exception {

        /* obtain service_contexts file from running device */
        deviceSvcFile = createTempFile("service_contexts", ".tmp");
        if (!mDevice.pullFile("/system/etc/selinux/plat_service_contexts", deviceSvcFile)) {
            mDevice.pullFile("/plat_service_contexts", deviceSvcFile);
        }

        /* retrieve the AOSP service_contexts file from jar */
        aospSvcFile = copyResourceToTempFile("/plat_service_contexts");

        assertContainsAllLines(aospSvcFile, deviceSvcFile);
    }

    /**
     * Tests that the file_contexts file(s) on the device is valid.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testValidFileContexts() throws Exception {

        /* retrieve the checkfc executable from jar */
        checkFc = copyResourceToTempFile("/checkfc");
        checkFc.setExecutable(true);

        /* combine plat and vendor policies for testing */
        File combinedFcFile = createTempFile("combined_file_context", ".tmp");
        appendTo(combinedFcFile.getAbsolutePath(), devicePlatFcFile.getAbsolutePath());
        appendTo(combinedFcFile.getAbsolutePath(), deviceVendorFcFile.getAbsolutePath());

        /* run checkfc sepolicy file_contexts */
        String errorString = tryRunCommand(checkFc.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(),
                combinedFcFile.getAbsolutePath());
        assertTrue("file_contexts was invalid:\n"
                   + errorString, errorString.length() == 0);
    }

    /**
     * Tests that the property_contexts file on the device is valid.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testValidPropertyContexts() throws Exception {

        /* retrieve the checkfc executable from jar */
        File propertyInfoChecker = copyResourceToTempFile("/property_info_checker");
        propertyInfoChecker.setExecutable(true);

        /* obtain property_contexts file from running device */
        devicePcFile = createTempFile("property_contexts", ".tmp");
        // plat_property_contexts may be either in /system/etc/sepolicy or in /
        if (!mDevice.pullFile("/system/etc/selinux/plat_property_contexts", devicePcFile)) {
            mDevice.pullFile("/plat_property_contexts", devicePcFile);
        }

        /* run property_info_checker on property_contexts */
        String errorString = tryRunCommand(propertyInfoChecker.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(),
                devicePcFile.getAbsolutePath());
        assertTrue("The property_contexts file was invalid:\n"
                   + errorString, errorString.length() == 0);
    }

    /**
     * Tests that the service_contexts file on the device is valid.
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testValidServiceContexts() throws Exception {

        /* retrieve the checkfc executable from jar */
        checkFc = copyResourceToTempFile("/checkfc");
        checkFc.setExecutable(true);

        /* obtain service_contexts file from running device */
        deviceSvcFile = createTempFile("service_contexts", ".tmp");
        mDevice.pullFile("/service_contexts", deviceSvcFile);

        /* run checkfc -s on service_contexts */
        String errorString = tryRunCommand(checkFc.getAbsolutePath(),
                "-s", devicePolicyFile.getAbsolutePath(),
                deviceSvcFile.getAbsolutePath());
        assertTrue("The service_contexts file was invalid:\n"
                   + errorString, errorString.length() == 0);
    }

    public static boolean isMac() {
        String os = System.getProperty("os.name").toLowerCase();
        return (os.startsWith("mac") || os.startsWith("darwin"));
    }

    private void assertSepolicyTests(String test, String testExecutable,
            boolean includeVendorSepolicy) throws Exception {
        sepolicyTests = copyResourceToTempFile(testExecutable);
        sepolicyTests.setExecutable(true);

        List<String> args = new ArrayList<String>();
        args.add(sepolicyTests.getAbsolutePath());
        args.add("-f");
        args.add(devicePlatFcFile.getAbsolutePath());
        args.add("--test");
        args.add(test);

        if (includeVendorSepolicy) {
            args.add("-f");
            args.add(deviceVendorFcFile.getAbsolutePath());
            args.add("-p");
            args.add(devicePolicyFile.getAbsolutePath());
        } else {
            args.add("-p");
            args.add(deviceSystemPolicyFile.getAbsolutePath());
        }

        String errorString = tryRunCommand(args.toArray(new String[0]));
        assertTrue(errorString, errorString.length() == 0);

        sepolicyTests.delete();
    }

    /**
     * Tests that all types on /data have the data_file_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testDataTypeViolators() throws Exception {
        assertSepolicyTests("TestDataTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 27) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all types in /sys/fs/bpf have the bpffs_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testBpffsTypeViolators() throws Exception {
        assertSepolicyTests("TestBpffsTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 33) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all types in /proc have the proc_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testProcTypeViolators() throws Exception {
        assertSepolicyTests("TestProcTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 27) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all types in /sys have the sysfs_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testSysfsTypeViolators() throws Exception {
        assertSepolicyTests("TestSysfsTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 27) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all types on /vendor have the vendor_file_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testVendorTypeViolators() throws Exception {
        assertSepolicyTests("TestVendorTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 27) /* includeVendorSepolicy */);
    }

    /**
     * Tests that tracefs files(/sys/kernel/tracing and /d/tracing) are correctly labeled.
     *
     * @throws Exception
     */
    @Test
    public void testTracefsTypeViolators() throws Exception {
        assertSepolicyTests("TestTracefsTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 30) /* includeVendorSepolicy */);
    }

    /**
     * Tests that debugfs files(from /sys/kernel/debug) are correctly labeled.
     *
     * @throws Exception
     */
    @Test
    public void testDebugfsTypeViolators() throws Exception {
        assertSepolicyTests("TestDebugfsTypeViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 30) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all domains with entrypoints on /system have the coredomain
     * attribute, and that all domains with entrypoints on /vendor do not have the
     * coredomain attribute.
     *
     * @throws Exception
     */
    @Test
    public void testCoredomainViolators() throws Exception {
        assertSepolicyTests("CoredomainViolations", "/sepolicy_tests",
                PropertyUtil.isVendorApiLevelNewerThan(mDevice, 27) /* includeVendorSepolicy */);
    }

    /**
     * Tests that all labels on /dev have the dev_type attribute.
     *
     * @throws Exception
     */
    @Test
    public void testDevTypeViolators() throws Exception {
        int vsrVersion = getVSRApiLevel(getDevice());
        assumeTrue("Skipping test: dev_type is enforced for W or later", vsrVersion > 202404);
        assertSepolicyTests("TestDevTypeViolations", "/sepolicy_tests", true);
    }

   /**
     * Tests that the policy defines no booleans (runtime conditional policy).
     *
     * @throws Exception
     */
    @CddTest(requirement="9.7")
    @Test
    public void testNoBooleans() throws Exception {

        /* run sepolicy-analyze booleans check on policy file */
        String errorString = tryRunCommand(mSepolicyAnalyze.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(), "booleans");
        assertTrue("The policy contained booleans:\n"
                   + errorString, errorString.length() == 0);
    }

   /**
     * Tests that taking a bugreport does not produce any dumpstate-related
     * SELinux denials.
     *
     * @throws Exception
     */
    @Test
    public void testNoBugreportDenials() throws Exception {
        // Take a bugreport and get its logcat output.
        mDevice.executeAdbCommand("logcat", "-c");
        mDevice.getBugreport();
        String log = mDevice.executeAdbCommand("logcat", "-d");
        // Find all the dumpstate-related types and make a regex that will match them.
        Set<String> types = sepolicyAnalyzeGetTypesAssociatedWithAttribute("hal_dumpstate_server");
        types.add("dumpstate");
        String typeRegex = types.stream().collect(Collectors.joining("|"));
        Pattern p = Pattern.compile("avc: *denied.*scontext=u:(?:r|object_r):(?:" + typeRegex + "):s0.*");
        // Fail if logcat contains such a denial.
        Matcher m = p.matcher(log);
        StringBuilder errorString = new StringBuilder();
        while (m.find()) {
            errorString.append(m.group());
            errorString.append("\n");
        }
        assertTrue("Found illegal SELinux denial(s): " + errorString, errorString.length() == 0);
    }

    /**
     * Tests that important domain labels are being appropriately applied.
     */

    /**
     * Asserts that no processes are running in a domain.
     *
     * @param domain
     *  The domain or SELinux context to check.
     */
    private void assertDomainEmpty(String domain) throws DeviceNotAvailableException {
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);
        String msg = "Expected no processes in SELinux domain \"" + domain + "\""
            + " Found: \"" + procs + "\"";
        assertNull(msg, procs);
    }

    /**
     * Asserts that a domain exists and that only one, well defined, process is
     * running in that domain.
     *
     * @param domain
     *  The domain or SELinux context to check.
     * @param executable
     *  The path of the executable or application package name.
     */
    private void assertDomainOne(String domain, String executable) throws DeviceNotAvailableException {
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);
        List<ProcessDetails> exeProcs = ProcessDetails.getExeMap(mDevice).get(executable);
        String msg = "Expected 1 process in SELinux domain \"" + domain + "\""
            + " Found \"" + procs + "\"";
        assertNotNull(msg, procs);
        assertEquals(msg, 1, procs.size());

        msg = "Expected executable \"" + executable + "\" in SELinux domain \"" + domain + "\""
            + "Found: \"" + procs + "\"";
        assertEquals(msg, executable, procs.get(0).procTitle);

        msg = "Expected 1 process with executable \"" + executable + "\""
            + " Found \"" + procs + "\"";
        assertNotNull(msg, exeProcs);
        assertEquals(msg, 1, exeProcs.size());

        msg = "Expected executable \"" + executable + "\" in SELinux domain \"" + domain + "\""
            + "Found: \"" + procs + "\"";
        assertEquals(msg, domain, exeProcs.get(0).label);
    }

    /**
     * Asserts that a domain may exist. If a domain exists, the cardinality of
     * the domain is verified to be 1 and that the correct process is running in
     * that domain. If the process is running, it is running in that domain.
     *
     * @param domain
     *  The domain or SELinux context to check.
     * @param executable
     *  The path of the executable or application package name.
     */
    private void assertDomainZeroOrOne(String domain, String executable)
        throws DeviceNotAvailableException {
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);
        List<ProcessDetails> exeProcs = ProcessDetails.getExeMap(mDevice).get(executable);
        if (procs != null) {
            String msg = "Expected 1 process in SELinux domain \"" + domain + "\""
                + " Found: \"" + procs + "\"";
            assertEquals(msg, 1, procs.size());

            msg = "Expected executable \"" + executable + "\" in SELinux domain \"" + domain + "\""
                + "Found: \"" + procs.get(0) + "\"";
            assertEquals(msg, executable, procs.get(0).procTitle);
        }
        if (exeProcs != null) {
            String msg = "Expected executable \"" + executable + "\" in SELinux domain \"" + domain + "\""
                + " Instead found it running in the domain \"" + exeProcs.get(0).label + "\"";
            assertNotNull(msg, procs);

            msg = "Expected 1 process with executable \"" + executable + "\""
            + " Found: \"" + procs + "\"";
            assertEquals(msg, 1, exeProcs.size());

            msg = "Expected executable \"" + executable + "\" in SELinux domain \"" + domain + "\""
                + "Found: \"" + procs.get(0) + "\"";
            assertEquals(msg, domain, exeProcs.get(0).label);
        }
    }

    /**
     * Asserts that a domain must exist, and that the cardinality is greater
     * than or equal to 1.
     *
     * @param domain
     *  The domain or SELinux context to check.
     * @param executables
     *  The path of the allowed executables or application package names.
     */
    private void assertDomainN(String domain, String... executables)
        throws DeviceNotAvailableException {
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);
        String msg = "Expected 1 or more processes in SELinux domain but found none.";
        assertNotNull(msg, procs);

        Set<String> execList = new HashSet<String>(Arrays.asList(executables));

        for (ProcessDetails p : procs) {
            msg = "Expected one of \"" + execList + "\" in SELinux domain \"" + domain + "\""
                + " Found: \"" + p + "\"";
            assertTrue(msg, execList.contains(p.procTitle));
        }

        for (String exe : executables) {
            List<ProcessDetails> exeProcs = ProcessDetails.getExeMap(mDevice).get(exe);

            if (exeProcs != null) {
                for (ProcessDetails p : exeProcs) {
                    msg = "Expected executable \"" + exe + "\" in SELinux domain \""
                        + domain + "\"" + " Found: \"" + p + "\"";
                    assertEquals(msg, domain, p.label);
                }
            }
        }
    }

    /**
     * Asserts that a domain, if it exists, is only running the listed executables.
     *
     * @param domain
     *  The domain or SELinux context to check.
     * @param executables
     *  The path of the allowed executables or application package names.
     */
    private void assertDomainHasExecutable(String domain, String... executables)
        throws DeviceNotAvailableException {
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);

        if (procs != null) {
            Set<String> execList = new HashSet<String>(Arrays.asList(executables));

            for (ProcessDetails p : procs) {
                String msg = "Expected one of \"" + execList + "\" in SELinux domain \""
                    + domain + "\"" + " Found: \"" + p + "\"";
                assertTrue(msg, execList.contains(p.procTitle));
            }
        }
    }

    /**
     * Asserts that an executable exists and is only running in the listed domains.
     *
     * @param executable
     *  The path of the executable to check.
     * @param domains
     *  The list of allowed domains.
     */
    private void assertExecutableExistsAndHasDomain(String executable, String... domains)
        throws DeviceNotAvailableException {
        List<ProcessDetails> exeProcs = ProcessDetails.getExeMap(mDevice).get(executable);
        Set<String> domainList = new HashSet<String>(Arrays.asList(domains));

        String msg = "Expected 1 or more processes for executable \"" + executable + "\".";
        assertNotNull(msg, exeProcs);

        for (ProcessDetails p : exeProcs) {
            msg = "Expected one of  \"" + domainList + "\" for executable \"" + executable
                    + "\"" + " Found: \"" + p.label + "\"";
            assertTrue(msg, domainList.contains(p.label));
        }
    }

    /**
     * Asserts that an executable, if it exists, is only running in the listed domains.
     *
     * @param executable
     *  The path of the executable to check.
     * @param domains
     *  The list of allowed domains.
     */
    private void assertExecutableHasDomain(String executable, String... domains)
        throws DeviceNotAvailableException {
        List<ProcessDetails> exeProcs = ProcessDetails.getExeMap(mDevice).get(executable);
        Set<String> domainList = new HashSet<String>(Arrays.asList(domains));

        if (exeProcs != null) {
            for (ProcessDetails p : exeProcs) {
                String msg = "Expected one of  \"" + domainList + "\" for executable \"" + executable
                    + "\"" + " Found: \"" + p.label + "\"";
                assertTrue(msg, domainList.contains(p.label));
            }
        }
    }

    /* Init is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testInitDomain() throws DeviceNotAvailableException {
        assertDomainHasExecutable("u:r:init:s0", "/system/bin/init");
        assertDomainHasExecutable("u:r:vendor_init:s0", "/system/bin/init");
        assertExecutableExistsAndHasDomain("/system/bin/init", "u:r:init:s0", "u:r:vendor_init:s0");
    }

    /* Ueventd is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testUeventdDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:ueventd:s0", "/system/bin/ueventd");
    }

    /* healthd may or may not exist */
    @CddTest(requirement="9.7")
    @Test
    public void testHealthdDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:healthd:s0", "/system/bin/healthd");
    }

    /* Servicemanager is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testServicemanagerDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:servicemanager:s0", "/system/bin/servicemanager");
    }

    /* Vold is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testVoldDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:vold:s0", "/system/bin/vold");
    }

    /* netd is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testNetdDomain() throws DeviceNotAvailableException {
        assertDomainN("u:r:netd:s0", "/system/bin/netd", "/system/bin/iptables-restore", "/system/bin/ip6tables-restore");
    }

    /* Surface flinger is always there */
    @CddTest(requirement="9.7")
    @Test
    public void testSurfaceflingerDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:surfaceflinger:s0", "/system/bin/surfaceflinger");
    }

    /* Zygote is always running */
    @CddTest(requirement="9.7")
    @Test
    public void testZygoteDomain() throws DeviceNotAvailableException {
        assertDomainN("u:r:zygote:s0", "zygote", "zygote64", "usap32", "usap64");
    }

    /* Checks drmserver for devices that require it */
    @CddTest(requirement="9.7")
    @Test
    public void testDrmServerDomain() throws DeviceNotAvailableException {
        assertDomainHasExecutable("u:r:drmserver:s0", "/system/bin/drmserver", "/system/bin/drmserver32", "/system/bin/drmserver64");
    }

    /* Installd is always running */
    @CddTest(requirement="9.7")
    @Test
    public void testInstalldDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:installd:s0", "/system/bin/installd");
    }

    /* keystore is always running */
    @CddTest(requirement="9.7")
    @Test
    public void testKeystoreDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:keystore:s0", "/system/bin/keystore2");
    }

    /* System server better be running :-P */
    @CddTest(requirement="9.7")
    @Test
    public void testSystemServerDomain() throws DeviceNotAvailableException {
        assertDomainOne("u:r:system_server:s0", "system_server");
    }

    /* Watchdogd may or may not be there */
    @CddTest(requirement="9.7")
    @Test
    public void testWatchdogdDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:watchdogd:s0", "/system/bin/watchdogd");
    }

    /* logd may or may not be there */
    @CddTest(requirement="9.7")
    @Test
    public void testLogdDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:logd:s0", "/system/bin/logd");
    }

    /* lmkd may or may not be there */
    @CddTest(requirement="9.7")
    @Test
    public void testLmkdDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:lmkd:s0", "/system/bin/lmkd");
    }

    /* Wifi may be off so cardinality of 0 or 1 is ok */
    @CddTest(requirement="9.7")
    @Test
    public void testWpaDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:wpa:s0", "/system/bin/wpa_supplicant");
    }

    /* permissioncontroller, if running, always runs in permissioncontroller_app */
    @CddTest(requirement="9.7")
    @Test
    public void testPermissionControllerDomain() throws DeviceNotAvailableException {
        assertExecutableHasDomain("com.google.android.permissioncontroller", "u:r:permissioncontroller_app:s0");
        assertExecutableHasDomain("com.android.permissioncontroller", "u:r:permissioncontroller_app:s0");
    }

    /* vzwomatrigger may or may not be running */
    @CddTest(requirement="9.7")
    @Test
    public void testVzwOmaTriggerDomain() throws DeviceNotAvailableException {
        assertDomainZeroOrOne("u:r:vzwomatrigger_app:s0", "com.android.vzwomatrigger");
    }

    /* gmscore, if running, always runs in gmscore_app */
    @CddTest(requirement="9.7")
    @Test
    public void testGMSCoreDomain() throws DeviceNotAvailableException {
        assertExecutableHasDomain("com.google.android.gms", "u:r:gmscore_app:s0");
        assertExecutableHasDomain("com.google.android.gms.ui", "u:r:gmscore_app:s0");
        assertExecutableHasDomain("com.google.android.gms.persistent", "u:r:gmscore_app:s0");
        assertExecutableHasDomain("com.google.android.gms:snet", "u:r:gmscore_app:s0");
    }

    /*
     * Nothing should be running in this domain, cardinality test is all thats
     * needed
     */
    @CddTest(requirement="9.7")
    @Test
    public void testInitShellDomain() throws DeviceNotAvailableException {
        assertDomainEmpty("u:r:init_shell:s0");
    }

    /*
     * Nothing should be running in this domain, cardinality test is all thats
     * needed
     */
    @CddTest(requirement="9.7")
    @Test
    public void testRecoveryDomain() throws DeviceNotAvailableException {
        assertDomainEmpty("u:r:recovery:s0");
    }

    /*
     * Nothing should be running in this domain, cardinality test is all thats
     * needed
     */
    @CddTest(requirement="9.7")
    @RestrictedBuildTest
    @Test
    public void testSuDomain() throws DeviceNotAvailableException {
        assertDomainEmpty("u:r:su:s0");
    }

    /*
     * All kthreads should be in kernel context.
     */
    @CddTest(requirement="9.7")
    @Test
    public void testKernelDomain() throws DeviceNotAvailableException {
        String domain = "u:r:kernel:s0";
        List<ProcessDetails> procs = ProcessDetails.getProcMap(mDevice).get(domain);
        if (procs != null) {
            for (ProcessDetails p : procs) {
                assertTrue("Non Kernel thread \"" + p + "\" found!", p.isKernel());
            }
        }
    }

    private static class ProcessDetails {
        public String label;
        public String user;
        public int pid;
        public int ppid;
        public String procTitle;

        private static HashMap<String, ArrayList<ProcessDetails>> procMap;
        private static HashMap<String, ArrayList<ProcessDetails>> exeMap;
        private static int kernelParentThreadpid = -1;

        ProcessDetails(String label, String user, int pid, int ppid, String procTitle) {
            this.label = label;
            this.user = user;
            this.pid = pid;
            this.ppid = ppid;
            this.procTitle = procTitle;
        }

        @Override
        public String toString() {
            return "label: " + label
                    + " user: " + user
                    + " pid: " + pid
                    + " ppid: " + ppid
                    + " cmd: " + procTitle;
        }


        private static void createProcMap(ITestDevice tDevice) throws DeviceNotAvailableException {

            /* take the output of a ps -Z to do our analysis */
            CollectingOutputReceiver psOut = new CollectingOutputReceiver();
            // TODO: remove "toybox" below and just run "ps"
            tDevice.executeShellCommand("toybox ps -A -o label,user,pid,ppid,cmdline", psOut);
            String psOutString = psOut.getOutput();
            Pattern p = Pattern.compile(
                    "^([\\w_:,]+)\\s+([\\w_]+)\\s+(\\d+)\\s+(\\d+)\\s+(\\p{Graph}+)(\\s\\p{Graph}+)*\\s*$"
            );
            procMap = new HashMap<String, ArrayList<ProcessDetails>>();
            exeMap = new HashMap<String, ArrayList<ProcessDetails>>();
            for(String line : psOutString.split("\n")) {
                Matcher m = p.matcher(line);
                if(m.matches()) {
                    String domainLabel = m.group(1);
                    // clean up the domainlabel
                    String[] parts = domainLabel.split(":");
                    if (parts.length > 4) {
                        // we have an extra categories bit at the end consisting of cxxx,cxxx ...
                        // just make the domain out of the first 4 parts
                        domainLabel = String.join(":", parts[0], parts[1], parts[2], parts[3]);
                    }

                    String user = m.group(2);
                    int pid = Integer.parseInt(m.group(3));
                    int ppid = Integer.parseInt(m.group(4));
                    String procTitle = m.group(5);
                    ProcessDetails proc = new ProcessDetails(domainLabel, user, pid, ppid, procTitle);
                    if (procMap.get(domainLabel) == null) {
                        procMap.put(domainLabel, new ArrayList<ProcessDetails>());
                    }
                    procMap.get(domainLabel).add(proc);
                    if (procTitle.equals("[kthreadd]") && ppid == 0) {
                        kernelParentThreadpid = pid;
                    }
                    if (exeMap.get(procTitle) == null) {
                        exeMap.put(procTitle, new ArrayList<ProcessDetails>());
                    }
                    exeMap.get(procTitle).add(proc);
                }
            }
        }

        public static HashMap<String, ArrayList<ProcessDetails>> getProcMap(ITestDevice tDevice)
                throws DeviceNotAvailableException{
            if (procMap == null) {
                createProcMap(tDevice);
            }
            return procMap;
        }

        public static HashMap<String, ArrayList<ProcessDetails>> getExeMap(ITestDevice tDevice)
                throws DeviceNotAvailableException{
            if (exeMap == null) {
                createProcMap(tDevice);
            }
            return exeMap;
        }

        public boolean isKernel() {
            return (pid == kernelParentThreadpid || ppid == kernelParentThreadpid);
        }
    }

    private static String tryRunCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
            result.append("\n");
        }
        return result.toString();
    }

    private static File createTempFile(String name, String ext) throws IOException {
        File ret = File.createTempFile(name, ext);
        ret.deleteOnExit();
        return ret;
    }
}
