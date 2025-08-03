/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.traceur;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.FileUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility functions for tracing.
 */
public class TraceUtils {

    static final String TAG = "Traceur";

    public static final String TRACE_DIRECTORY = "/data/local/traces/";

    private static TraceEngine mTraceEngine = new PerfettoUtils();

    private static final Runtime RUNTIME = Runtime.getRuntime();

    public enum RecordingType {
      UNKNOWN, TRACE, STACK_SAMPLES, HEAP_DUMP
    }
    public interface TraceEngine {
        public String getName();
        public String getOutputExtension();
        public boolean traceStart(Collection<String> tags, int bufferSizeKb, boolean winscope,
            boolean apps, boolean attachToBugreport, boolean longTrace, int maxLongTraceSizeMb,
            int maxLongTraceDurationMinutes);
        public boolean stackSampleStart(boolean attachToBugreport);
        public boolean heapDumpStart(Collection<String> processes, boolean continuousDump,
            int dumpIntervalSeconds, boolean attachToBugreport);
        public void traceStop();
        public boolean traceDump(File outFile);
        public boolean isTracingOn();
    }

    public static String currentTraceEngine() {
        return mTraceEngine.getName();
    }

    public static boolean traceStart(ContentResolver contentResolver, Collection<String> tags,
            int bufferSizeKb, boolean winscope, boolean apps, boolean longTrace,
            boolean attachToBugreport, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes) {
        if (!mTraceEngine.traceStart(tags, bufferSizeKb, winscope, apps, attachToBugreport,
                longTrace, maxLongTraceSizeMb, maxLongTraceDurationMinutes)) {
            return false;
        }
        WinscopeUtils.traceStart(contentResolver, winscope);
        return true;
    }

    public static boolean stackSampleStart(boolean attachToBugreport) {
        return mTraceEngine.stackSampleStart(attachToBugreport);
    }

    public static boolean heapDumpStart(Collection<String> processes, boolean continuousDump,
            int dumpIntervalSeconds, boolean attachToBugreport) {
        return mTraceEngine.heapDumpStart(processes, continuousDump, dumpIntervalSeconds,
                attachToBugreport);
    }

    public static void traceStop(ContentResolver contentResolver) {
        mTraceEngine.traceStop();
        WinscopeUtils.traceStop(contentResolver);
    }

    public static Optional<List<File>> traceDump(ContentResolver contentResolver,
            String outFilename) {
        File outFile = TraceUtils.getOutputFile(outFilename);
        if (!mTraceEngine.traceDump(outFile)) {
            return Optional.empty();
        }

        List<File> outFiles = new ArrayList();
        outFiles.add(outFile);

        List<File> outLegacyWinscopeFiles = WinscopeUtils.traceDump(contentResolver, outFilename);
        outFiles.addAll(outLegacyWinscopeFiles);

        return Optional.of(outFiles);
    }

    public static boolean isTracingOn() {
        return mTraceEngine.isTracingOn();
    }

    public static TreeMap<String, String> listCategories() {
        TreeMap<String, String> categories = PerfettoUtils.perfettoListCategories();
        categories.put("sys_stats", "meminfo, psi, and vmstats");
        categories.put("logs", "android logcat");
        categories.put("cpu", "callstack samples");
        return categories;
    }

    public static void clearSavedTraces() {
        String cmd = "rm -f " + TRACE_DIRECTORY + "trace-*.*trace " +
                TRACE_DIRECTORY + "recovered-trace*.*trace " +
                TRACE_DIRECTORY + "stack-samples*.*trace " +
                TRACE_DIRECTORY + "heap-dump*.*trace";

        Log.v(TAG, "Clearing trace directory: " + cmd);
        try {
            Process rm = exec(cmd);

            if (rm.waitFor() != 0) {
                Log.e(TAG, "clearSavedTraces failed with: " + rm.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Process exec(String cmd) throws IOException {
        return exec(cmd, null);
    }

    public static Process exec(String cmd, String tmpdir) throws IOException {
        return exec(cmd, tmpdir, true);
    }

    public static Process exec(String cmd, String tmpdir, boolean logOutput) throws IOException {
        String[] cmdarray = {"sh", "-c", cmd};
        String[] envp = {"TMPDIR=" + tmpdir};
        envp = tmpdir == null ? null : envp;

        Log.v(TAG, "exec: " + Arrays.toString(envp) + " " + Arrays.toString(cmdarray));

        Process process = RUNTIME.exec(cmdarray, envp);
        new Logger("traceService:stderr", process.getErrorStream());
        if (logOutput) {
            new Logger("traceService:stdout", process.getInputStream());
        }

        return process;
    }

    // Returns the Process if the command terminated on time and null if not.
    public static Process execWithTimeout(String cmd, String tmpdir, long timeout)
            throws IOException {
        Process process = exec(cmd, tmpdir, true);
        try {
            if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Command '" + cmd + "' has timed out after " + timeout + " ms.");
                process.destroyForcibly();
                // Return null to signal a timeout and that the Process was destroyed.
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return process;
    }

    public static String getOutputFilename(RecordingType type) {
        String prefix;
        switch (type) {
            case TRACE:
                prefix = "trace";
                break;
            case STACK_SAMPLES:
                prefix = "stack-samples";
                break;
            case HEAP_DUMP:
                prefix = "heap-dump";
                break;
            case UNKNOWN:
            default:
                prefix = "recording";
                break;
        }
        String format = "yyyy-MM-dd-HH-mm-ss";
        String now = new SimpleDateFormat(format, Locale.US).format(new Date());
        return String.format("%s-%s-%s-%s.%s", prefix, Build.BOARD, Build.ID, now,
            mTraceEngine.getOutputExtension());
    }

    public static String getRecoveredFilename() {
        // Knowing what the previous Traceur session was recording would require adding a
        // recordingWasTrace parameter to TraceUtils.traceStart().
        return "recovered-" + getOutputFilename(RecordingType.UNKNOWN);
    }

    public static File getOutputFile(String filename) {
        return new File(TraceUtils.TRACE_DIRECTORY, filename);
    }

    protected static void cleanupOlderFiles(final int minCount, final long minAge) {
        FutureTask<Void> task = new FutureTask<Void>(
                () -> {
                    try {
                        FileUtils.deleteOlderFiles(new File(TRACE_DIRECTORY), minCount, minAge);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Failed to delete older traces", e);
                    }
                    return null;
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // execute() instead of submit() because we don't need the result.
        executor.execute(task);
    }

    static Set<String> getRunningAppProcesses(Context context) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> processes =
                am.getRunningAppProcesses();
        // AM will return null instead of an empty list if no apps are found.
        if (processes == null) {
            return Collections.emptySet();
        }

        Set<String> processNames = processes.stream()
                .map(process -> process.processName)
                .collect(Collectors.toSet());

        return processNames;
    }

    /**
     * Streams data from an InputStream to an OutputStream
     */
    static class Streamer {
        private boolean mDone;

        Streamer(final String tag, final InputStream in, final OutputStream out) {
            new Thread(tag) {
                @Override
                public void run() {
                    int read;
                    byte[] buf = new byte[2 << 10];
                    try {
                        while ((read = in.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error while streaming " + tag);
                    } finally {
                        try {
                            out.close();
                        } catch (IOException e) {
                            // Welp.
                        }
                        synchronized (Streamer.this) {
                            mDone = true;
                            Streamer.this.notify();
                        }
                    }
                }
            }.start();
        }

        synchronized boolean isDone() {
            return mDone;
        }

        synchronized void waitForDone() {
            while (!isDone()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Redirects an InputStream to logcat.
     */
    private static class Logger {

        Logger(final String tag, final InputStream in) {
            new Thread(tag) {
                @Override
                public void run() {
                    String line;
                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    try {
                        while ((line = r.readLine()) != null) {
                            Log.e(TAG, tag + ": " + line);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error while streaming " + tag);
                    } finally {
                        try {
                            r.close();
                        } catch (IOException e) {
                            // Welp.
                        }
                    }
                }
            }.start();
        }
    }
}
