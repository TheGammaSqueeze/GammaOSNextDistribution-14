/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sts.common;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Various helpers to find, wait, and kill processes on the device */
public final class ProcessUtil {
    public static class KillException extends Exception {
        public enum Reason {
            UNKNOWN,
            INVALID_SIGNAL,
            INSUFFICIENT_PERMISSIONS,
            NO_SUCH_PROCESS;
        }

        private Reason reason;

        public KillException(String errorMessage, Reason r) {
            super(errorMessage);
            this.reason = r;
        }

        public Reason getReason() {
            return this.reason;
        }
    }

    private static final String LOG_TAG = ProcessUtil.class.getSimpleName();

    public static final long PROCESS_WAIT_TIMEOUT_MS = 10_000;
    public static final long PROCESS_POLL_PERIOD_MS = 250;
    public static final String[] INTENT_QUERY_CMDS = {
        "resolve-activity", "query-activities", "query-services", "query-receivers"
    };

    private ProcessUtil() {}

    /**
     * Get the pids matching a pattern passed to `pgrep`. Because /proc/pid/comm is truncated,
     * `pgrep` is passed with `-f` to check the full command line.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @return an Optional Map of pid to command line; empty if pgrep did not return EXIT_SUCCESS
     */
    public static Optional<Map<Integer, String>> pidsOf(ITestDevice device, String pgrepRegex)
            throws DeviceNotAvailableException {
        // pgrep is available since 6.0 (Marshmallow)
        // https://chromium.googlesource.com/aosp/platform/system/core/+/HEAD/shell_and_utilities/README.md
        CommandResult pgrepRes =
                device.executeShellV2Command(String.format("pgrep -f -l %s", pgrepRegex));
        if (pgrepRes.getStatus() != CommandStatus.SUCCESS) {
            Log.d(
                    LOG_TAG,
                    String.format(
                            "pgrep '%s' failed with stderr: %s", pgrepRegex, pgrepRes.getStderr()));
            return Optional.empty();
        }
        Map<Integer, String> pidToCommand = new HashMap<>();
        for (String line : pgrepRes.getStdout().split("\n")) {
            String[] pidComm = line.split(" ", 2);
            int pid = Integer.valueOf(pidComm[0]);
            String comm = pidComm[1];
            pidToCommand.put(pid, comm);
        }
        return Optional.of(pidToCommand);
    }

    /**
     * Get a single pid matching a pattern passed to `pgrep`. Throw an {@link
     * IllegalArgumentException} when there are more than one PID matching the pattern.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @return an Optional Integer of the pid; empty if pgrep did not return EXIT_SUCCESS
     */
    public static Optional<Integer> pidOf(ITestDevice device, String pgrepRegex)
            throws DeviceNotAvailableException, IllegalArgumentException {
        Optional<Map<Integer, String>> pids = pidsOf(device, pgrepRegex);
        if (!pids.isPresent()) {
            return Optional.empty();
        } else if (pids.get().size() == 1) {
            return Optional.of(pids.get().keySet().iterator().next());
        } else {
            throw new IllegalArgumentException("More than one process found for: " + pgrepRegex);
        }
    }

    /**
     * Wait until a running process is found for a given regex.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @return the pid to command map from pidsOf(...)
     */
    public static Map<Integer, String> waitProcessRunning(ITestDevice device, String pgrepRegex)
            throws TimeoutException, DeviceNotAvailableException {
        return waitProcessRunning(device, pgrepRegex, PROCESS_WAIT_TIMEOUT_MS);
    }

    /**
     * Wait until a running process is found for a given regex.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @param timeoutMs how long to wait before throwing a TimeoutException
     * @return the pid to command map from pidsOf(...)
     */
    public static Map<Integer, String> waitProcessRunning(
            ITestDevice device, String pgrepRegex, long timeoutMs)
            throws TimeoutException, DeviceNotAvailableException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (true) {
            Optional<Map<Integer, String>> pidToCommand = pidsOf(device, pgrepRegex);
            if (pidToCommand.isPresent()) {
                return pidToCommand.get();
            }
            if (System.currentTimeMillis() > endTime) {
                throw new TimeoutException();
            }
            try {
                Thread.sleep(PROCESS_POLL_PERIOD_MS);
            } catch (InterruptedException e) {
                // don't care, just keep looping until we time out
            }
        }
    }

    /**
     * Get the contents from /proc/pid/cmdline.
     *
     * @param device the device to use
     * @param pid the id of the process to get the name for
     * @return an Optional String of the contents of /proc/pid/cmdline; empty if the pid could not
     *     be found
     */
    public static Optional<String> getProcessName(ITestDevice device, int pid)
            throws DeviceNotAvailableException {
        // /proc/*/comm is truncated, use /proc/*/cmdline instead
        CommandResult res =
                device.executeShellV2Command(String.format("cat /proc/%d/cmdline", pid));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            return Optional.empty();
        }
        return Optional.of(res.getStdout());
    }

    /**
     * Wait for a process to be exited. This is not waiting for it to change, but simply be
     * nonexistent. It is possible, but unlikely, for a pid to be reused between polls
     *
     * @param device the device to use
     * @param pid the id of the process to wait until exited
     */
    public static void waitPidExited(ITestDevice device, int pid)
            throws TimeoutException, DeviceNotAvailableException, KillException {
        waitPidExited(device, pid, PROCESS_WAIT_TIMEOUT_MS);
    }

    /**
     * Wait for a process to be exited. This is not waiting for it to change, but simply be
     * nonexistent. It is possible, but unlikely, for a pid to be reused between polls
     *
     * @param device the device to use
     * @param pid the id of the process to wait until exited
     * @param timeoutMs how long to wait before throwing a TimeoutException
     */
    public static void waitPidExited(ITestDevice device, int pid, long timeoutMs)
            throws TimeoutException, DeviceNotAvailableException, KillException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        CommandResult res = null;
        while (true) {
            // kill -0 asserts that the process is alive and readable
            res = device.executeShellV2Command(String.format("kill -0 %d", pid));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                String err = res.getStderr();
                if (!err.contains("No such process")) {
                    throw new KillException(
                            "kill -0 returned stderr: " + err,
                            KillException.Reason.NO_SUCH_PROCESS);
                }
                // the process is most likely killed
                return;
            }
            if (System.currentTimeMillis() > endTime) {
                throw new TimeoutException();
            }
            try {
                Thread.sleep(PROCESS_POLL_PERIOD_MS);
            } catch (InterruptedException e) {
                // don't care, just keep looping until we time out
            }
        }
    }

    /**
     * Send SIGKILL to a process and wait for it to be exited.
     *
     * @param device the device to use
     * @param pid the id of the process to wait until exited
     * @param timeoutMs how long to wait before throwing a TimeoutException
     */
    public static void killPid(ITestDevice device, int pid, long timeoutMs)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        killPid(device, pid, 9, timeoutMs);
    }

    /**
     * Send a signal to a process and wait for it to be exited.
     *
     * @param device the device to use
     * @param pid the id of the process to wait until exited
     * @param signal the signal to send to the process
     * @param timeoutMs how long to wait before throwing a TimeoutException
     */
    public static void killPid(ITestDevice device, int pid, int signal, long timeoutMs)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        CommandResult res = device.executeShellV2Command(String.format("kill -%d %d", signal, pid));
        if (res.getStatus() != CommandStatus.SUCCESS) {
            String err = res.getStderr();
            if (err.contains("invalid signal specification")) {
                throw new KillException(err, KillException.Reason.INVALID_SIGNAL);
            } else if (err.contains("Operation not permitted")) {
                throw new KillException(err, KillException.Reason.INSUFFICIENT_PERMISSIONS);
            } else if (err.contains("No such process")) {
                throw new KillException(err, KillException.Reason.NO_SUCH_PROCESS);
            } else {
                throw new KillException(err, KillException.Reason.UNKNOWN);
            }
        }
        waitPidExited(device, pid, timeoutMs);
    }

    /**
     * Send SIGKILL to a all processes matching a pattern.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @param timeoutMs how long to wait before throwing a TimeoutException
     * @return whether any processes were killed
     */
    public static boolean killAll(ITestDevice device, String pgrepRegex, long timeoutMs)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        return killAll(device, pgrepRegex, timeoutMs, true);
    }

    /**
     * Send SIGKILL to a all processes matching a pattern.
     *
     * @param device the device to use
     * @param pgrepRegex a String representing the regex for pgrep
     * @param timeoutMs how long to wait before throwing a TimeoutException
     * @param expectExist whether an exception should be thrown when no processes were killed
     * @param expectExist whether an exception should be thrown when no processes were killed
     * @return whether any processes were killed
     */
    public static boolean killAll(
            ITestDevice device, String pgrepRegex, long timeoutMs, boolean expectExist)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        Optional<Map<Integer, String>> pids = pidsOf(device, pgrepRegex);
        if (!pids.isPresent()) {
            // no pids to kill
            if (expectExist) {
                throw new RuntimeException(
                        String.format("Expected to kill processes matching %s", pgrepRegex));
            }
            return false;
        }

        for (int pid : pids.get().keySet()) {
            try {
                killPid(device, pid, timeoutMs);
            } catch (KillException e) {
                // ignore pids that do not exist
                if (e.getReason() != KillException.Reason.NO_SUCH_PROCESS) {
                    throw e;
                }
            }
        }

        return true;
    }

    /**
     * Kill a process at the beginning and end of a test.
     *
     * @param device the device to use
     * @param pgrepRegex the name pattern of the process to kill to give to pgrep
     * @param beforeCloseKill a runnable for any actions that need to cleanup before killing the
     *     process in a normal environment at the end of the test. Can be null.
     * @return An object that will kill the process again when it is closed
     */
    public static AutoCloseable withProcessKill(
            final ITestDevice device, final String pgrepRegex, final Runnable beforeCloseKill)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        return withProcessKill(device, pgrepRegex, beforeCloseKill, PROCESS_WAIT_TIMEOUT_MS);
    }

    /**
     * Kill a process at the beginning and end of a test.
     *
     * @param device the device to use
     * @param pgrepRegex the name pattern of the process to kill to give to pgrep
     * @param beforeCloseKill a runnable for any actions that need to cleanup before killing the
     *     process in a normal environment at the end of the test. Can be null.
     * @param timeoutMs how long in milliseconds to wait for the process to kill
     * @return An object that will kill the process again when it is closed
     */
    public static AutoCloseable withProcessKill(
            final ITestDevice device,
            final String pgrepRegex,
            final Runnable beforeCloseKill,
            final long timeoutMs)
            throws DeviceNotAvailableException, TimeoutException, KillException {
        return new AutoCloseable() {
            {
                try {
                    if (!killAll(device, pgrepRegex, timeoutMs, /*expectExist*/ false)) {
                        Log.d(
                                LOG_TAG,
                                String.format("did not kill any processes for %s", pgrepRegex));
                    }
                } catch (KillException e) {
                    Log.d(LOG_TAG, "failed to kill a process");
                }
            }

            @Override
            public void close() throws Exception {
                if (beforeCloseKill != null) {
                    beforeCloseKill.run();
                }
                try {
                    killAll(device, pgrepRegex, timeoutMs, /*expectExist*/ false);
                } catch (KillException e) {
                    if (e.getReason() != KillException.Reason.NO_SUCH_PROCESS) {
                        throw e;
                    }
                }
            }
        };
    }

    /**
     * Returns the currently open file names of the specified process. This does not include shared
     * libraries linked by the linker.
     *
     * @param device device to be run on
     * @param pid the id of the process to search
     * @return an Optional of the open files; empty if the process wasn't found or the open files
     *     couldn't be read.
     */
    public static Optional<List<String>> listOpenFiles(ITestDevice device, int pid)
            throws DeviceNotAvailableException {
        // test if we can access the open files of the specified pid
        // `test` is available in all relevant Android versions
        CommandResult fdRes =
                device.executeShellV2Command(String.format("test -r /proc/%d/fd", pid));
        if (fdRes.getStatus() != CommandStatus.SUCCESS) {
            return Optional.empty();
        }
        // `find` and `realpath` are available since 6.0 (Marshmallow)
        // https://chromium.googlesource.com/aosp/platform/system/core/+/HEAD/shell_and_utilities/README.md
        // intentionally not using lsof because of parsing issues
        // realpath will intentionally fail for non-filesystem file descriptors
        CommandResult openFilesRes =
                device.executeShellV2Command(
                        String.format("find /proc/%d/fd -exec realpath {} + 2> /dev/null", pid));
        String[] openFilesArray = openFilesRes.getStdout().split("\n");
        return Optional.of(Arrays.asList(openFilesArray));
    }

    /**
     * Returns file names of the specified file, loaded by the specified process. This does not
     * include shared libraries linked.
     *
     * @param device device to be run on
     * @param pid the id of the process to search
     * @param filePattern a pattern of the file names to return
     * @return an Optional of the filtered files; empty if the process wasn't found or the open
     *     files couldn't be read.
     */
    public static Optional<List<String>> findFilesLoadedByProcess(
            ITestDevice device, int pid, Pattern filePattern) throws DeviceNotAvailableException {
        Optional<List<String>> openFilesOption = listOpenFiles(device, pid);
        if (!openFilesOption.isPresent()) {
            return Optional.empty();
        }
        List<String> openFiles = openFilesOption.get();
        return Optional.of(
                openFiles.stream()
                        .filter((f) -> filePattern.matcher(f).matches())
                        .collect(Collectors.toList()));
    }

    /**
     * Returns file entry of the first file loaded by the specified process with specified name.
     * This includes shared libraries linked.
     *
     * @param device device to be run on
     * @param process pgrep pattern of process to look for
     * @param filenamePattern the filename pattern to find
     * @return an Opotional of IFileEntry of the path of the file on the device if exists.
     */
    public static Optional<IFileEntry> findFileLoadedByProcess(
            ITestDevice device, String process, Pattern filenamePattern)
            throws DeviceNotAvailableException {
        Optional<Integer> pid = ProcessUtil.pidOf(device, process);
        if (pid.isPresent()) {
            String cmd = "lsof -p " + pid.get().toString() + " | grep -o '/.*'";
            String[] openFiles = CommandUtil.runAndCheck(device, cmd).getStdout().split("\n");
            for (String f : openFiles) {
                if (f.contains("Permission denied")) {
                    throw new IllegalStateException("no permission to read open files for process");
                }
                if (filenamePattern.matcher(f).find()) {
                    return Optional.of(device.getFileEntry(f.trim()));
                }
            }
        }
        return Optional.empty();
    }

    /*
    * To get application process pids of all applications that can handle the target intent
    * @param queryCmd Query command to be used. One of the values present in INTENT_QUERY_CMDS
    * @param intentOptions Map of intent option to value for target intent
    * @param device device to be run on
    * @return Optional Map of pid to process name of application processes that can handle the
           target intent
    */
    public static Optional<Map<Integer, String>> getAllProcessIdsFromComponents(
            String queryCmd, Map<String, String> intentOptions, ITestDevice device)
            throws DeviceNotAvailableException, RuntimeException {
        if (!Arrays.asList(INTENT_QUERY_CMDS).contains(queryCmd)) {
            throw new RuntimeException("Unknown command " + queryCmd);
        }
        String cmd = "pm " + queryCmd + " ";
        for (Map.Entry<String, String> entry : intentOptions.entrySet()) {
            cmd += entry.getKey() + " " + entry.getValue() + " ";
        }
        CommandResult result = device.executeShellV2Command(cmd);
        String resultString = result.getStdout();
        Log.i(LOG_TAG, String.format("Executed cmd: %s \nOutput: %s", cmd, resultString));

        // As target string (process name) is coming from system itself, regex here only checks for
        // presence of valid characters in process name and not for the actual order of characters
        Pattern processNamePattern = Pattern.compile("processName=(?<name>[a-zA-Z0-9_\\.:]+)");
        Matcher matcher = processNamePattern.matcher(resultString);
        Map<Integer, String> pidNameMap = new HashMap<Integer, String>();
        while (matcher.find()) {
            String process = matcher.group("name");
            pidsOf(device, process)
                    .ifPresent(
                            (pids) -> {
                                pidNameMap.putAll(pids);
                            });
        }
        return pidNameMap.isEmpty() ? Optional.empty() : Optional.of(pidNameMap);
    }
}
