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
package android.videoqualityfloor.cts;

import android.platform.test.annotations.Presubmit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Run the host-side video quality floor tests.
 * These encode a set of videos at particular resolutions/bitrates and
 * measure the quality of the output.
 * Measurement is with the VMAF tool.
 *
 * The body of this test is implemented in a test script, not within the java here.
 * This java code acquires the videos and the test script, unpacks them, executes the
 * script (which encodes and measures).
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsVideoQualityFloorHostTest implements IAbiReceiver, IBuildReceiver, IDeviceTest {

    static final String TAG = CtsVideoQualityFloorHostTest.class.getSimpleName();
    static final String BASE_URL =
              "https://dl.google.com/android/xts/cts/hostsidetests/videoqualityfloor/";
    static final String TEST_SCRIPT_TARBALL = "tests-20231004.tgz";
    static final String[] testClips = {
        "Fireworks.mp4",
        "Motorcycle.mp4",
        "MountainBike.mp4",
        "TreesAndGrass.mp4"
    };
    // test is not valid before sdk 31, aka Android 12, aka Android S
    static final int MINIMUM_VALID_SDK = 31;

    /**
     * A reference to the build info.
     */
    private IBuildInfo mBuildInfo;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    /**
     * A reference to the ABI under test.
     */
    private IAbi mAbi;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    private String getProperty(String prop) throws Exception {
        return mDevice.executeShellCommand("getprop " + prop).replace("\n", "");
    }

    /**
     * TODO: Add JavaDoc
     */
    @Test
    public void testEncoding() throws Exception {

        String sdkAsString = getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        if (sdk < MINIMUM_VALID_SDK) {
            LogUtil.CLog.d("Test requires sdk >= " + MINIMUM_VALID_SDK
                            + " test device has sdk =" + sdk );
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        Process subproc;
        String tmpBase = System.getProperty("java.io.tmpdir");
        String dirName = "CtsVideoQualityFloorHostTest";
        String tmpDir = tmpBase + "/" + dirName;

        LogUtil.CLog.d("tmpBase= " + tmpBase + " tmpDir =" + tmpDir);

        if (false) {
            // start with a fresh directory
            File cwd = new File(".");
            runCmd("rm -fr " + tmpDir, cwd);
        }

        // set up test directory, make sure it exists
        File destination = new File(tmpDir);
        try {
            if (!destination.isDirectory()) {
                destination.mkdirs();
            }
        } catch (SecurityException e) {
            LogUtil.CLog.d("Unable to establish temp directory " + destination.getPath());
        }
        Assert.assertTrue(destination.isDirectory());

        /*
         *
         * https://dl.google.com/android/xts/cts/hostsidetests/videoqualityfloor/
         * + the name of the file
         *
         * turning the filename into the appropriate URL is done within downloadFile()
         */

        for (String clip : testClips) {
            LogUtil.CLog.d("downloading " + clip);
            downloadFile(clip, destination);
        }

        // get the test script
        LogUtil.CLog.d("downloading " + TEST_SCRIPT_TARBALL);
        downloadFile(TEST_SCRIPT_TARBALL, destination);

        // we *always* unpack and setup, even if the downloads were cached.
        // this avoids any /tmp cleanup problems.
        //
        // unpack the test scripts
        runCmd("tar xzf " + TEST_SCRIPT_TARBALL, destination);

        // run the setup scripts
        LogUtil.CLog.d("running set_up");
        runCmd("./set_up.sh", destination);

        // run the test script
        // return code is # of failures, so 0 == success
        String clipArgs = "";
        for (String clip : testClips) {
            clipArgs= clipArgs + " " + clip;
        }
        String targetSerial = getDevice().getSerialNumber();
        String testOutput = runCmd("./testit.sh -serial " + targetSerial + clipArgs, destination);

        LogUtil.CLog.d("(Successful) Output from testit.sh:\n\n" + testOutput);

    }

    // run the indicated command, in the indicated directory
    // returns the command output
    // asserts if the command finishes with non-zero exit code.
    private String runCmd(String cmd, File cwd) {
        Runtime runtime = Runtime.getRuntime();
        Process subproc;
        LogUtil.CLog.d("runCmd()  cmd=" + cmd + "  cwd=" + cwd.getPath());
        int result = 0;
        String childStdout = "";
        try {
            subproc = runtime.exec(cmd, null, cwd);
            subproc.waitFor();      // may wait forever; there are versions with timeout
            BufferedReader kidstream =
                            new BufferedReader(new InputStreamReader(subproc.getInputStream()));
            String line;
            StringBuilder kidStdout = new StringBuilder();
            while ((line = kidstream.readLine()) != null) {
                kidStdout.append(line);
                kidStdout.append("\n");
            }
            childStdout = kidStdout.toString();
            result = subproc.exitValue();
        } catch (IOException e) {
            result = -2;
        } catch (InterruptedException e) {
            result = -3;
        }
        Assert.assertTrue("runCmd(" + cmd + ") fails result= " + result
                        + " and output:\n" + childStdout, result == 0);

        return childStdout;
    }

    // download the indicated file (within the base_url folder) to
    // our desired destination/fileName.
    // simple caching -- if file exists, we do not redownload
    private void downloadFile(String fileName, File destDir) {
        Runtime runtime = Runtime.getRuntime();
        Process subproc;

        File destination = new File(destDir, fileName);

        // save bandwidth, also allows a user to manually preload files
        LogUtil.CLog.d("Do we already have a copy of file " + destination.getPath());
        if (destination.isFile()) {
            LogUtil.CLog.d("Skipping re-download of file " + destination.getPath());
            return;
        }

        String url = BASE_URL + fileName;
        // wget doesn't do 'file'
        // String cmd = "wget -O " + destination.getPath() + " " + url;
        String cmd = "curl --silent --output " + destination.getPath() + " " + url;
        runCmd(cmd, destDir);
    }

}

