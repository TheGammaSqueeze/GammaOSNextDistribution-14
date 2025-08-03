/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.videoencodingquality.cts;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;
import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.CddTest;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IDeviceTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

/**
 * This class constitutes host-part of video encoding quality test (go/pc14-veq). This test is
 * aimed towards benchmarking encoders on the target device.
 * <p>
 * Video encoding quality test quantifies encoders on the test device by encoding a set of clips
 * at various configurations. The encoded output is analysed for vmaf and compared against
 * reference. This entire process is not carried on the device. The host side of the test
 * prepares the test environment by installing a VideoEncodingApp on the device. It also pushes
 * the test vectors and test configurations on to the device. The VideoEncodingApp transcodes the
 * input clips basing on the configurations shared. The host side of the test then pulls output
 * files from the device and analyses for vmaf. These values are compared against reference using
 * Bjontegaard metric.
 **/
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
@OptionClass(alias = "pc-veq-test")
public class CtsVideoEncodingQualityHostTest implements IDeviceTest {
    private static final String RES_URL =
            "https://storage.googleapis.com/android_media/cts/hostsidetests/pc14_veq/veqtests-1_2.tar.gz";

    // variables related to host-side of the test
    private static final int MEDIA_PERFORMANCE_CLASS_14 = 34;
    private static final int MINIMUM_VALID_SDK = 31;
            // test is not valid before sdk 31, aka Android 12, aka Android S

    private static final Lock sLock = new ReentrantLock();
    private static final Condition sCondition = sLock.newCondition();
    private static boolean sIsTestSetUpDone = false;
            // install apk, push necessary resources to device to run the test. lock/condition
            // pair is to keep setupTestEnv() thread safe
    private static File sHostWorkDir;

    // Variables related to device-side of the test. These need to kept in sync with definitions of
    // VideoEncodingApp.apk
    private static final String DEVICE_IN_DIR = "/sdcard/veq/input/";
    private static final String DEVICE_OUT_DIR = "/sdcard/veq/output/";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "android.videoencoding.app";
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.videoencoding.app.VideoTranscoderTest";
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_CONFIG_INST_ARGS_KEY = "conf-json";
    private static final long DEFAULT_SHELL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final String TEST_TIMEOUT_INST_ARGS_KEY = "timeout_msec";
    private static final long DEFAULT_TEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3);

    // local variables related to host-side of the test
    private final String mJsonName;
    private ITestDevice mDevice;

    @Option(name = "force-to-run", description = "Force to run the test even if the device is not"
            + " a right performance class device.")
    private boolean mForceToRun = false;

    @Option(name = "skip-avc", description = "Skip avc encoder testing")
    private boolean mSkipAvc = false;

    @Option(name = "skip-hevc", description = "Skip hevc encoder testing")
    private boolean mSkipHevc = false;

    @Option(name = "skip-p", description = "Skip P only testing")
    private boolean mSkipP = false;

    @Option(name = "skip-b", description = "Skip B frame testing")
    private boolean mSkipB = false;

    @Option(name = "reset", description = "Start with a fresh directory.")
    private boolean mReset = false;

    @Option(name = "quick-check", description = "Run a quick check.")
    private boolean mQuickCheck = false;

    public CtsVideoEncodingQualityHostTest(String jsonName) {
        mJsonName = jsonName;
    }

    private static final List<String> AVC_VBR_B0_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json");

    private static final List<String> AVC_VBR_B3_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b3.json");

    private static final List<String> HEVC_VBR_B0_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json");

    private static final List<String> HEVC_VBR_B3_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            // Abnormal curve, not monotonically increasing.
            /*"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",*/
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b3.json");

    private static final List<String> QUICK_RUN_PARAMS = Arrays.asList(
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json");

    @Parameterized.Parameters(name = "{index}_{0}")
    public static List<String> input() {
        final List<String> args = new ArrayList<>();
        args.addAll(AVC_VBR_B0_PARAMS);
        args.addAll(AVC_VBR_B3_PARAMS);
        args.addAll(HEVC_VBR_B0_PARAMS);
        args.addAll(HEVC_VBR_B3_PARAMS);
        return args;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Sets up the necessary environment for the video encoding quality test.
     */
    public void setupTestEnv() throws Exception {
        String sdkAsString = getDevice().getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        Assume.assumeTrue("Test requires sdk >= " + MINIMUM_VALID_SDK
                + " test device has sdk = " + sdk, sdk >= MINIMUM_VALID_SDK);

        String pcAsString = getDevice().getProperty("ro.odm.build.media_performance_class");
        int mpc = 0;
        try {
            mpc = Integer.parseInt("0" + pcAsString);
        } catch (Exception e) {
            LogUtil.CLog.i("Invalid pcAsString: " + pcAsString + ", exception: " + e);
        }
        Assume.assumeTrue("Test device does not advertise performance class",
                mForceToRun || (mpc >= MEDIA_PERFORMANCE_CLASS_14));

        Assert.assertTrue("Failed to install package on device : " + DEVICE_SIDE_TEST_PACKAGE,
                getDevice().isPackageInstalled(DEVICE_SIDE_TEST_PACKAGE));

        // set up host-side working directory
        String tmpBase = System.getProperty("java.io.tmpdir");
        String dirName = "CtsVideoEncodingQualityHostTest_" + getDevice().getSerialNumber();
        String tmpDir = tmpBase + "/" + dirName;
        LogUtil.CLog.i("tmpBase= " + tmpBase + " tmpDir =" + tmpDir);
        sHostWorkDir = new File(tmpDir);
        if (mReset || sHostWorkDir.isFile()) {
            File cwd = new File(".");
            runCommand("rm -rf " + tmpDir, cwd);
        }
        try {
            if (!sHostWorkDir.isDirectory()) {
                Assert.assertTrue("Failed to create directory : " + sHostWorkDir.getAbsolutePath(),
                        sHostWorkDir.mkdirs());
            }
        } catch (SecurityException e) {
            LogUtil.CLog.e("Unable to establish temp directory " + sHostWorkDir.getPath());
        }

        // Clean up output folders before starting the test
        runCommand("rm -rf " + "output_*", sHostWorkDir);

        // Download the test suite tar file.
        downloadFile(RES_URL, sHostWorkDir);

        // Unpack the test suite tar file.
        String fileName = RES_URL.substring(RES_URL.lastIndexOf('/') + 1);
        int result = runCommand("tar xvzf " + fileName, sHostWorkDir);
        Assert.assertEquals("Failed to untar " + fileName, 0, result);

        // Push input files to device
        Assert.assertNotNull("Failed to create directory " + DEVICE_IN_DIR + " on device ",
                getDevice().executeAdbCommand("shell", "mkdir", "-p", DEVICE_IN_DIR));
        Assert.assertTrue("Failed to push json files to " + DEVICE_IN_DIR + " on device ",
                getDevice().syncFiles(new File(sHostWorkDir.getPath() + "/json/"), DEVICE_IN_DIR));
        Assert.assertTrue("Failed to push mp4 files to " + DEVICE_IN_DIR + " on device ",
                getDevice().syncFiles(new File(sHostWorkDir.getPath() + "/samples/"),
                        DEVICE_IN_DIR));

        sIsTestSetUpDone = true;
    }

    /**
     * Verify the video encoding quality requirements for the performance class 14 devices.
     */
    @CddTest(requirements = {"2.2.7.1/5.8/H-1-1"})
    @Test
    public void testEncoding() throws Exception {
        Assume.assumeFalse("Skipping due to quick run mode",
                mQuickCheck && !QUICK_RUN_PARAMS.contains(mJsonName));
        Assume.assumeFalse("Skipping avc encoder tests",
                mSkipAvc && (AVC_VBR_B0_PARAMS.contains(mJsonName) || AVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping hevc encoder tests",
                mSkipHevc && (HEVC_VBR_B0_PARAMS.contains(mJsonName) || HEVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping b-frame tests",
                mSkipB && (AVC_VBR_B3_PARAMS.contains(mJsonName) || HEVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping non b-frame tests",
                mSkipP && (AVC_VBR_B0_PARAMS.contains(mJsonName) || HEVC_VBR_B0_PARAMS.contains(
                        mJsonName)));

        // set up test environment
        sLock.lock();
        try {
            if (!sIsTestSetUpDone) setupTestEnv();
            sCondition.signalAll();
        } finally {
            sLock.unlock();
        }

        // transcode input
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, DEVICE_SIDE_TEST_CLASS, "testTranscode");

        // copy the encoded output from the device to the host.
        String outDir = "output_" + mJsonName.substring(0, mJsonName.indexOf('.'));
        File outHostPath = new File(sHostWorkDir, outDir);
        try {
            if (!outHostPath.isDirectory()) {
                Assert.assertTrue("Failed to create directory : " + outHostPath.getAbsolutePath(),
                        outHostPath.mkdirs());
            }
        } catch (SecurityException e) {
            LogUtil.CLog.e("Unable to establish output host directory : " + outHostPath.getPath());
        }
        String outDevPath = DEVICE_OUT_DIR + outDir;
        Assert.assertTrue("Failed to pull mp4 files from " + outDevPath
                + " to " + outHostPath.getPath(), getDevice().pullDir(outDevPath, outHostPath));
        getDevice().deleteFile(outDevPath);

        // Parse json file
        String jsonPath = sHostWorkDir.getPath() + "/json/" + mJsonName;
        String jsonString =
                new String(Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8);
        JSONArray jsonArray = new JSONArray(jsonString);
        JSONObject obj = jsonArray.getJSONObject(0);
        String refFileName = obj.getString("RefFileName");
        int fps = obj.getInt("FrameRate");
        int frameCount = obj.getInt("FrameCount");
        int clipDuration = frameCount / fps;

        // Compute Vmaf
        try (FileWriter writer = new FileWriter(outHostPath.getPath() + "/" + "all_vmafs.txt")) {
            JSONArray codecConfigs = obj.getJSONArray("CodecConfigs");
            int th = Runtime.getRuntime().availableProcessors() / 2;
            th = Math.min(Math.max(1, th), 8);
            String filter = "libvmaf=feature=name=psnr:model=version=vmaf_v0.6.1:n_threads=" + th;
            for (int i = 0; i < codecConfigs.length(); i++) {
                JSONObject codecConfig = codecConfigs.getJSONObject(i);
                String outputName = codecConfig.getString("EncodedFileName");
                outputName = outputName.substring(0, outputName.lastIndexOf("."));
                String outputVmafPath = outDir + "/" + outputName + ".txt";
                String cmd = "./bin/ffmpeg";
                cmd += " -hide_banner";
                cmd += " -i " + outDir + "/" + outputName + ".mp4" + " -an";
                cmd += " -i " + "samples/" + refFileName + " -an";
                cmd += " -filter_complex " + "\"" + filter + "\"";
                cmd += " -f null -";
                cmd += " > " + outputVmafPath + " 2>&1";
                LogUtil.CLog.i("ffmpeg command : " + cmd);
                int result = runCommand(cmd, sHostWorkDir);
                Assert.assertEquals("Encountered error during vmaf computation.", 0, result);

                String vmafLine = "";
                try (BufferedReader reader = new BufferedReader(
                        new FileReader(sHostWorkDir.getPath() + "/" + outputVmafPath))) {
                    String token = "VMAF score: ";
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(token)) {
                            line = line.substring(line.indexOf(token));
                            vmafLine = "VMAF score = " + line.substring(token.length());
                            LogUtil.CLog.i(vmafLine);
                            break;
                        }
                    }
                } catch (IOException e) {
                    throw new AssertionError("Unexpected IOException: " + e.getMessage());
                }

                writer.write(vmafLine + "\n");
                writer.write("Y4M file = " + refFileName + "\n");
                writer.write("MP4 file = " + refFileName + "\n");
                File file = new File(outHostPath + "/" + outputName + ".mp4");
                Assert.assertTrue("output file from device missing", file.exists());
                long fileSize = file.length();
                writer.write("Filesize = " + fileSize + "\n");
                writer.write("FPS = " + fps + "\n");
                writer.write("FRAME_COUNT = " + frameCount + "\n");
                writer.write("CLIP_DURATION = " + clipDuration + "\n");
                long totalBits = fileSize * 8;
                long totalBits_kbps = totalBits / 1000;
                long bitrate_kbps = totalBits_kbps / clipDuration;
                writer.write("Bitrate kbps = " + bitrate_kbps + "\n");
            }
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException: " + e.getMessage());
        }

        // bd rate verification
        String jarCmd = "java -jar " + "./bin/cts-media-videoquality-bdrate.jar "
                + "--uid= --gid= --chroot= "
                + "--REF_JSON_FILE=" + "json/" + mJsonName + " "
                + "--TEST_VMAF_FILE=" + outDir + "/" + "all_vmafs.txt "
                + "> " + outDir + "/result.txt";
        LogUtil.CLog.i("bdrate command : " + jarCmd);
        int result = runCommand(jarCmd, sHostWorkDir);
        Assert.assertEquals("bd rate validation failed.", 0, result);

        LogUtil.CLog.i("Finished executing the process.");
    }

    private int runCommand(String command, File dir) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        String line;
        while ((line = stdInput.readLine()) != null || (line = stdError.readLine()) != null) {
            LogUtil.CLog.i(line + "\n");
        }
        return p.waitFor();
    }

    // Download the indicated file (within the base_url folder) to our desired destination
    // simple caching -- if file exists, we do not re-download
    private void downloadFile(String url, File destDir) {
        String fileName = url.substring(RES_URL.lastIndexOf('/') + 1);
        File destination = new File(destDir, fileName);

        // save bandwidth, also allows a user to manually preload files
        LogUtil.CLog.i("Do we already have a copy of file " + destination.getPath());
        if (destination.isFile()) {
            LogUtil.CLog.i("Skipping re-download of file " + destination.getPath());
            return;
        }

        String cmd = "wget -O " + destination.getPath() + " " + url;
        LogUtil.CLog.i("wget_cmd = " + cmd);

        int result = 0;
        try {
            result = runCommand(cmd, destDir);
        } catch (IOException e) {
            result = -2;
        } catch (InterruptedException e) {
            result = -3;
        }
        Assert.assertEquals("download file failed.\n", 0, result);
    }

    private void runDeviceTests(String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = getTestRunner(pkgName, testClassName, testMethodName);
        CollectingTestListener listener = new CollectingTestListener();
        Assert.assertTrue(getDevice().runInstrumentationTests(testRunner, listener));
        assertTestsPassed(listener.getCurrentRunResults());
    }

    private RemoteAndroidTestRunner getTestRunner(String pkgName, String testClassName,
            String testMethodName) {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }
        RemoteAndroidTestRunner testRunner =
                new RemoteAndroidTestRunner(pkgName, RUNNER, getDevice().getIDevice());
        testRunner.setMaxTimeToOutputResponse(DEFAULT_SHELL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        testRunner.addInstrumentationArg(TEST_TIMEOUT_INST_ARGS_KEY,
                Long.toString(DEFAULT_TEST_TIMEOUT_MILLIS));
        testRunner.addInstrumentationArg(TEST_CONFIG_INST_ARGS_KEY, mJsonName);
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }
        return testRunner;
    }

    private void assertTestsPassed(TestRunResult testRunResult) {
        if (testRunResult.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + testRunResult.getName() + ": " + testRunResult.getRunFailureMessage());
        }
        if (testRunResult.getNumTests() != testRunResult.getPassedTests().size()) {
            StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    testRunResult.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus()
                        .equals(com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }
}
