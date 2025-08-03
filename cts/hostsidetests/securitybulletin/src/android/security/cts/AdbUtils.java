/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.sts.common.tradefed.testtype.SecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdbUtils {

    final static String TMP_PATH = "/data/local/tmp/";
    final static int TIMEOUT_SEC = 9 * 60;
    final static String RESOURCE_ROOT = "/";

    final static String regexSpecialChars = "<([{\\^-=$!|]})?*+.>";
    @SuppressWarnings("InvalidPatternSyntax") // the errorprone test is incorrect for the following
    final static String regexSpecialCharsEscaped = regexSpecialChars.replaceAll(".", "\\\\$0");
    final static Pattern regexSpecialCharsEscapedPattern =
            Pattern.compile("[" + regexSpecialCharsEscaped + "]");

    /**
     * @deprecated Use {@link NativePoc} instead.
     */
    @Deprecated
    public static class pocConfig {
        String binaryName;
        String arguments;
        Map<String, String> envVars;
        String inputFilesDestination;
        ITestDevice device;
        TombstoneUtils.Config config = new TombstoneUtils.Config();
        List<String> inputFiles = Collections.emptyList();

        pocConfig(String binaryName, ITestDevice device) {
            this.binaryName = binaryName;
            this.device = device;
        }
    }

    /**
     * Runs a commandline on the specified device
     *
     * @deprecated Use {@link CommandUtil} instead.
     * @param command the command to be ran
     * @param device device for the command to be ran on
     * @return the console output from running the command
     */
    @Deprecated
    public static String runCommandLine(String command, ITestDevice device) throws Exception {
        if ("reboot".equals(command)) {
            throw new IllegalArgumentException(
                    "You called a forbidden command! Please fix your tests.");
        }
        return device.executeShellCommand(command);
    }

    /**
     * Pushes and runs a binary to the selected device
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @return the console output from the binary
     */
    @Deprecated
    public static String runPoc(String pocName, ITestDevice device) throws Exception {
        return runPoc(pocName, device, SecurityTestCase.TIMEOUT_NONDETERMINISTIC);
    }

    /**
     * Pushes and runs a binary to the selected device
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @return the console output from the binary
     */
    @Deprecated
    public static String runPoc(String pocName, ITestDevice device, int timeout) throws Exception {
        return runPoc(pocName, device, timeout, null);
    }

    /**
     * Pushes and runs a binary to the selected device
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @param arguments the input arguments for the poc
     * @return the console output from the binary
     */
    @Deprecated
    public static String runPoc(String pocName, ITestDevice device, int timeout, String arguments)
            throws Exception {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        runPoc(pocName, device, timeout, arguments, receiver);
        return receiver.getOutput();
    }

    /**
     * Pushes and runs a binary to the selected device and ignores any of its output.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static void runPocNoOutput(String pocName, ITestDevice device, int timeout)
            throws Exception {
        runPocNoOutput(pocName, device, timeout, null);
    }

    /**
     * Pushes and runs a binary with arguments to the selected device and ignores any of its output.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @param arguments input arguments for the poc
     */
    @Deprecated
    public static void runPocNoOutput(
            String pocName, ITestDevice device, int timeout, String arguments) throws Exception {
        runPoc(pocName, device, timeout, arguments, null);
    }

    /**
     * Pushes and runs a binary with arguments to the selected device and ignores any of its output.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @param arguments input arguments for the poc
     * @param receiver the type of receiver to run against
     */
    @Deprecated
    public static int runPoc(
            String pocName,
            ITestDevice device,
            int timeout,
            String arguments,
            IShellOutputReceiver receiver)
            throws Exception {
              return runPoc(pocName, device, timeout, arguments, null, receiver);
    }

    /**
     * Pushes and runs a binary with arguments to the selected device and ignores any of its output.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @param arguments input arguments for the poc
     * @param envVars run the poc with environment variables
     * @param receiver the type of receiver to run against
     */
    @Deprecated
    public static int runPoc(
            String pocName,
            ITestDevice device,
            int timeout,
            String arguments,
            Map<String, String> envVars,
            IShellOutputReceiver receiver)
            throws Exception {
        String remoteFile = String.format("%s%s", TMP_PATH, pocName);
        SecurityTestCase.getPocPusher(device).pushFile(pocName + "_sts", remoteFile);

        assertPocExecutable(pocName, device);
        if (receiver == null) {
            receiver = new NullOutputReceiver();
        }
        if (arguments == null) {
            arguments = "";
        }

        String env = "";
        if (envVars != null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                sb
                    .append(entry.getKey().trim())
                    .append('=')
                    .append(entry.getValue().trim())
                    .append(' ');
            }
            env = sb.toString();
            CLog.i("Running poc '%s' with env variables '%s'", pocName, env);
        }

        // since we have to return the exit status AND the poc stdout+stderr we redirect the exit
        // status to a file temporarily
        String exitStatusFilepath = TMP_PATH + "exit_status";
        runCommandLine("rm " + exitStatusFilepath, device); // remove any old exit status
        device.executeShellCommand(
                env + TMP_PATH + pocName + " " + arguments +
                "; echo $? > " + exitStatusFilepath, // echo exit status to file
                receiver, timeout, TimeUnit.SECONDS, 0);

        // cat the exit status
        String exitStatusString = runCommandLine("cat " + exitStatusFilepath, device).trim();

        MetricsReportLog reportLog = SecurityTestCase.buildMetricsReportLog(device);
        reportLog.addValue("poc_name", pocName, ResultType.NEUTRAL, ResultUnit.NONE);
        int exitStatus = -1;
        try {
            exitStatus = Integer.parseInt(exitStatusString);
            reportLog.addValue("exit_status", exitStatus, ResultType.NEUTRAL, ResultUnit.NONE);
        } catch (NumberFormatException e) {
            // Getting the exit status is a bonus. We can continue without it.
            CLog.w("Could not parse exit status to int: %s", exitStatusString);
        }
        reportLog.submit();

        runCommandLine("rm " + exitStatusFilepath, device);
        return exitStatus;
    }

    /**
     * Assert the poc is executable
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param device device to be ran on
     */
    @Deprecated
    private static void assertPocExecutable(String pocName, ITestDevice device) throws Exception {
        String fullPocPath = TMP_PATH + pocName;
        device.executeShellCommand("chmod 777 " + fullPocPath);
        assertEquals("'" + pocName + "' must exist and be readable.", 0,
                runCommandGetExitCode("test -r " + fullPocPath, device));
        assertEquals("'" + pocName + "'poc must exist and be writable.", 0,
                runCommandGetExitCode("test -w " + fullPocPath, device));
        assertEquals("'" + pocName + "'poc must exist and be executable.", 0,
                runCommandGetExitCode("test -x " + fullPocPath, device));
    }

    /**
     * Pushes and installs an apk to the selected device
     *
     * @param pathToApk a string path to apk from the /res folder
     * @param device device to be ran on
     * @return the output from attempting to install the apk
     */
    public static String installApk(String pathToApk, ITestDevice device) throws Exception {

        String fullResourceName = pathToApk;
        File apkFile = File.createTempFile("apkFile", ".apk");
        try {
            apkFile = extractResource(fullResourceName, apkFile);
            return device.installPackage(apkFile, true);
        } finally {
            apkFile.delete();
        }
    }

    /**
     * Extracts a resource and pushes it to the device
     *
     * @param fullResourceName a string path to resource from the res folder
     * @param deviceFilePath the remote destination absolute file path
     * @param device device to be ran on
     */
    public static void pushResource(String fullResourceName, String deviceFilePath,
                                    ITestDevice device) throws Exception {
        File resFile = File.createTempFile("CTSResource", "");
        try {
            resFile = extractResource(fullResourceName, resFile);
            device.pushFile(resFile, deviceFilePath);
        } finally {
            resFile.delete();
        }
    }

    /**
     * Pushes the specified files to the specified destination directory
     *
     * @param inputFiles files required as input
     * @param inputFilesDestination destination directory to which input files are
     *        pushed
     * @param device device to be run on
     */
    public static void pushResources(String[] inputFiles, String inputFilesDestination,
            ITestDevice device) throws Exception {
        if (inputFiles == null || inputFilesDestination == null) {
            throw new IllegalArgumentException(
                    "Can't push resources: input files or destination is null");
        }
        for (String tempFile : inputFiles) {
            pushResource(RESOURCE_ROOT + tempFile, inputFilesDestination + tempFile, device);
        }
    }

    /**
     * Removes the specified files from the specified destination directory
     *
     * @param inputFiles files required as input
     * @param inputFilesDestination destination directory where input files are
     *        present
     * @param device device to be run on
     */
    public static void removeResources(String[] inputFiles, String inputFilesDestination,
            ITestDevice device) throws Exception {
        if (inputFiles == null || inputFilesDestination == null) {
            throw new IllegalArgumentException(
                    "Can't remove resources: input files or destination is null");
        }
        for (String tempFile : inputFiles) {
            runCommandLine("rm " + inputFilesDestination + tempFile, device);
        }
    }

   /**
     * Extracts the binary data from a resource and writes it to a temp file
     */
    private static File extractResource(String fullResourceName, File file) throws Exception {
        try (InputStream in = AdbUtils.class.getResourceAsStream(fullResourceName);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fullResourceName);
            }
            byte[] buf = new byte[65536];
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
            return file;
        }

    }
    /**
     * Utility function to help check the exit code of a shell command
     *
     * @deprecated Use {@link CommandUtil} instead.
     */
    @Deprecated
    public static int runCommandGetExitCode(String cmd, ITestDevice device) throws Exception {
        long time = System.currentTimeMillis();
        String exitStatusString = runCommandLine(
                "(" + cmd + ") > /dev/null 2>&1; echo $?", device).trim();
        time = System.currentTimeMillis() - time;

        try {
            int exitStatus = Integer.parseInt(exitStatusString);
            MetricsReportLog reportLog = SecurityTestCase.buildMetricsReportLog(device);
            reportLog.addValue("command", cmd, ResultType.NEUTRAL, ResultUnit.NONE);
            reportLog.addValue("exit_status", exitStatus, ResultType.NEUTRAL, ResultUnit.NONE);
            reportLog.submit();
            return exitStatus;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(
                    "Could not get the exit status (%s) for '%s' (%d ms).",
                    exitStatusString, cmd, time));
        }
    }

    /**
     * Pushes and runs a binary to the selected device and checks exit code Return code 113 is used
     * to indicate the vulnerability
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static boolean runPocCheckExitCode(String pocName, ITestDevice device, int timeout)
            throws Exception {

       //Refer to go/asdl-sts-guide Test section for knowing the significance of 113 code
       return runPocGetExitStatus(pocName, device, timeout) == 113;
    }

    /**
     * Pushes and runs a binary to the device and returns the exit status.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static int runPocGetExitStatus(String pocName, ITestDevice device, int timeout)
            throws Exception {
       return runPocGetExitStatus(pocName, null, device, timeout);
    }

    /**
     * Pushes and runs a binary to the device and returns the exit status.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param arguments input arguments for the poc
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static int runPocGetExitStatus(
            String pocName, String arguments, ITestDevice device, int timeout) throws Exception {
              return runPocGetExitStatus(pocName, arguments, null, device, timeout);
    }

    /**
     * Pushes and runs a binary to the device and returns the exit status.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param arguments input arguments for the poc
     * @param envVars run the poc with environment variables
     * @param device device to be run on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static int runPocGetExitStatus(
            String pocName,
            String arguments,
            Map<String, String> envVars,
            ITestDevice device,
            int timeout)
            throws Exception {
        return runPoc(pocName, device, timeout, arguments, envVars, null);
    }

    /**
     * Pushes and runs a binary and asserts that the exit status isn't 113: vulnerable.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static void runPocAssertExitStatusNotVulnerable(
            String pocName, ITestDevice device, int timeout) throws Exception {
        runPocAssertExitStatusNotVulnerable(pocName, null, device, timeout);
    }

    /**
     * Pushes and runs a binary and asserts that the exit status isn't 113: vulnerable.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param arguments input arguments for the poc
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static void runPocAssertExitStatusNotVulnerable(
            String pocName, String arguments, ITestDevice device, int timeout) throws Exception {
        runPocAssertExitStatusNotVulnerable(pocName, arguments, null, device, timeout);
    }

    /**
     * Pushes and runs a binary and asserts that the exit status isn't 113: vulnerable.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName name of the poc binary
     * @param arguments input arguments for the poc
     * @param envVars run the poc with environment variables
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    @Deprecated
    public static void runPocAssertExitStatusNotVulnerable(
            String pocName,
            String arguments,
            Map<String, String> envVars,
            ITestDevice device,
            int timeout)
            throws Exception {
        assertTrue("PoC returned exit status 113: vulnerable",
                runPocGetExitStatus(pocName, arguments, envVars, device, timeout) != 113);
    }

    /**
     * Runs the poc binary and asserts that there are no security crashes that match the expected
     * process pattern.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param processPatternStrings a Pattern string to match the crash tombstone process
     */
    @Deprecated
    public static void runPocAssertNoCrashes(
            String pocName, ITestDevice device, String... processPatternStrings) throws Exception {
        runPocAssertNoCrashes(pocName, device,
                new TombstoneUtils.Config().setProcessPatterns(processPatternStrings));
    }

    /**
     * Runs the poc binary and asserts that there are no security crashes that match the expected
     * process pattern.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param config a crash parser configuration
     */
    @Deprecated
    public static void runPocAssertNoCrashes(
            String pocName, ITestDevice device, TombstoneUtils.Config config) throws Exception {
        runPocAssertNoCrashes(pocName, device, null, config);
    }

    /**
     * Runs the poc binary and asserts that there are no security crashes that match the expected
     * process pattern, including arguments when running.
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param arguments input arguments for the poc
     * @param config a crash parser configuration
     */
    @Deprecated
    public static void runPocAssertNoCrashes(
            String pocName, ITestDevice device, String arguments, TombstoneUtils.Config config)
            throws Exception {
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(device, config)) {
            AdbUtils.runPocNoOutput(pocName, device,
                    SecurityTestCase.TIMEOUT_NONDETERMINISTIC, arguments);
        }
    }

    /**
     * Runs the poc binary and asserts following 2 conditions. 1. There are no security crashes in
     * the binary. 2. The exit status isn't 113 (Code 113 is used to indicate the vulnerability
     * condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param binaryName name of the binary
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(
            String binaryName, String arguments, ITestDevice device) throws Exception {
        runPocAssertNoCrashesNotVulnerable(binaryName, arguments, null, null, device, null);
    }

    /**
     * Runs the poc binary and asserts following 2 conditions. 1. There are no security crashes in
     * the binary. 2. The exit status isn't 113 (Code 113 is used to indicate the vulnerability
     * condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param binaryName name of the binary
     * @param arguments arguments for running the binary
     * @param device device to be run on
     * @param processPatternStrings a Pattern string to match the crash tombstone process
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(
            String binaryName, String arguments, ITestDevice device, String processPatternStrings[])
            throws Exception {
        runPocAssertNoCrashesNotVulnerable(binaryName, arguments, null, null, device,
                processPatternStrings);
    }

    /**
     * Runs the poc binary and asserts following 2 conditions. 1. There are no security crashes in
     * the binary. 2. The exit status isn't 113 (Code 113 is used to indicate the vulnerability
     * condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param binaryName name of the binary
     * @param arguments arguments for running the binary
     * @param inputFiles files required as input
     * @param inputFilesDestination destination directory to which input files are pushed
     * @param device device to be run on
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(
            String binaryName,
            String arguments,
            String inputFiles[],
            String inputFilesDestination,
            ITestDevice device)
            throws Exception {
        runPocAssertNoCrashesNotVulnerable(binaryName, arguments, inputFiles, inputFilesDestination,
                device, null);
    }

    /**
     * Runs the poc binary and asserts following 3 conditions. 1. There are no security crashes in
     * the binary. 2. There are no security crashes that match the expected process pattern. 3. The
     * exit status isn't 113 (Code 113 is used to indicate the vulnerability condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param binaryName name of the binary
     * @param arguments arguments for running the binary
     * @param inputFiles files required as input
     * @param inputFilesDestination destination directory to which input files are pushed
     * @param device device to be run on
     * @param processPatternStrings a Pattern string to match the crash tombstone process
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(
            String binaryName,
            String arguments,
            String inputFiles[],
            String inputFilesDestination,
            ITestDevice device,
            String processPatternStrings[])
            throws Exception {
        runPocAssertNoCrashesNotVulnerable(binaryName, arguments, null,
                inputFiles, inputFilesDestination, device, processPatternStrings);
    }

    /**
     * Runs the poc binary and asserts following 3 conditions. 1. There are no security crashes in
     * the binary. 2. There are no security crashes that match the expected process pattern. 3. The
     * exit status isn't 113 (Code 113 is used to indicate the vulnerability condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param binaryName name of the binary
     * @param arguments arguments for running the binary
     * @param envVars run the poc with environment variables
     * @param inputFiles files required as input
     * @param inputFilesDestination destination directory to which input files are pushed
     * @param device device to be run on
     * @param processPatternStrings a Pattern string (other than binary name) to match the crash
     *     tombstone process
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(
            String binaryName,
            String arguments,
            Map<String, String> envVars,
            String inputFiles[],
            String inputFilesDestination,
            ITestDevice device,
            String... processPatternStrings)
            throws Exception {
        pocConfig testConfig = new pocConfig(binaryName, device);
        testConfig.arguments = arguments;
        testConfig.envVars = envVars;

        if (inputFiles != null) {
            testConfig.inputFiles = Arrays.asList(inputFiles);
            testConfig.inputFilesDestination = inputFilesDestination;
        }

        List<String> processPatternList = new ArrayList<>();
        if (processPatternStrings != null) {
            processPatternList.addAll(Arrays.asList(processPatternStrings));
        }
        processPatternList.add(binaryName);
        String[] processPatternStringsWithSelf = new String[processPatternList.size()];
        processPatternList.toArray(processPatternStringsWithSelf);
        testConfig.config =
                new TombstoneUtils.Config().setProcessPatterns(processPatternStringsWithSelf);

        runPocAssertNoCrashesNotVulnerable(testConfig);
    }

    /**
     * Runs the poc binary and asserts following 3 conditions. 1. There are no security crashes in
     * the binary. 2. There are no security crashes that match the expected process pattern. 3. The
     * exit status isn't 113 (Code 113 is used to indicate the vulnerability condition).
     *
     * @deprecated Use {@link NativePoc} instead.
     * @param testConfig test configuration
     */
    @Deprecated
    public static void runPocAssertNoCrashesNotVulnerable(pocConfig testConfig) throws Exception {
        String[] inputFiles = null;
        if(!testConfig.inputFiles.isEmpty()) {
            inputFiles = testConfig.inputFiles.toArray(new String[testConfig.inputFiles.size()]);
            pushResources(inputFiles, testConfig.inputFilesDestination, testConfig.device);
        }
        try (AutoCloseable a =
                TombstoneUtils.withAssertNoSecurityCrashes(testConfig.device, testConfig.config)) {
            runPocAssertExitStatusNotVulnerable(testConfig.binaryName, testConfig.arguments,
                    testConfig.envVars, testConfig.device, TIMEOUT_SEC);
        } catch (IllegalArgumentException e) {
            /*
             * Since 'runPocGetExitStatus' method raises IllegalArgumentException upon
             * hang/timeout, catching the exception here and ignoring it. Hangs are of
             * Moderate severity and hence patches may not be ported. This piece of code can
             * be removed once 'runPocGetExitStatus' is updated to handle hangs.
             */
            CLog.w("Ignoring IllegalArgumentException: " + e);
        } finally {
            if (!testConfig.inputFiles.isEmpty()) {
                removeResources(inputFiles, testConfig.inputFilesDestination, testConfig.device);
            }
        }
    }

    public static void assumeHasNfc(ITestDevice device) throws DeviceNotAvailableException {
        assumeTrue("nfc not available on device", device.hasFeature("android.hardware.nfc"));
    }

    /**
     * Escapes regex special characters in the given string
     *
     * @param testString string for which special characters need to be escaped
     *
     * @return string with escaped special charcters
     */
    public static String escapeRegexSpecialChars(String testString) {
        Matcher m = regexSpecialCharsEscapedPattern.matcher(testString);
        return m.replaceAll("\\\\$0");
    }
}
