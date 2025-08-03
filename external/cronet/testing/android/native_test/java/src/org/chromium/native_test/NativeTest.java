// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.native_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;

import org.jni_zero.JNINamespace;
import org.jni_zero.NativeMethods;

import org.chromium.base.Log;
import org.chromium.base.test.util.UrlUtils;
import org.chromium.build.gtest_apk.NativeTestIntent;
import org.chromium.test.reporter.TestStatusReporter;

import java.io.File;

/** Helper to run tests inside Activity or NativeActivity. */
@JNINamespace("testing::android")
public class NativeTest {
    private static final String TAG = "NativeTest";

    private String mCommandLineFilePath;
    private StringBuilder mCommandLineFlags = new StringBuilder();
    private TestStatusReporter mReporter;
    private boolean mRunInSubThread;
    private String mStdoutFilePath;
    private static final String LOG_TAG = "NativeTestRunner";
    // Signal used to trigger a dump of Clang coverage information.
    private final int COVERAGE_SIGNAL = 37;
    private boolean mDumpCoverage = false;
    private static final String DUMP_COVERAGE =
            "org.chromium.native_test.NativeTestInstrumentationTestRunner.DumpCoverage";

    private static class ReportingUncaughtExceptionHandler
            implements Thread.UncaughtExceptionHandler {

        private TestStatusReporter mReporter;
        private Thread.UncaughtExceptionHandler mWrappedHandler;

        public ReportingUncaughtExceptionHandler(
                TestStatusReporter reporter, Thread.UncaughtExceptionHandler wrappedHandler) {
            mReporter = reporter;
            mWrappedHandler = wrappedHandler;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            mReporter.uncaughtException(Process.myPid(), ex);
            if (mWrappedHandler != null) mWrappedHandler.uncaughtException(thread, ex);
        }
    }

    public void preCreate(Activity activity) {
        String coverageDeviceFile =
                activity.getIntent().getStringExtra(NativeTestIntent.EXTRA_COVERAGE_DEVICE_FILE);
        if (coverageDeviceFile != null) {
            try {
                Os.setenv("LLVM_PROFILE_FILE", coverageDeviceFile, true);
            } catch (ErrnoException e) {
                Log.w(TAG, "failed to set LLVM_PROFILE_FILE", e);
            }
        }
        // Set TMPDIR to make perfetto_unittests not to use /data/local/tmp as a tmp directory.
        try {
            Os.setenv("TMPDIR", activity.getApplicationContext().getCacheDir().getPath(), false);
        } catch (ErrnoException e) {
            Log.w(TAG, "failed to set TMPDIR", e);
        }
    }

    public void postCreate(Activity activity) {
        parseArgumentsFromIntent(activity, activity.getIntent());
        mReporter = new TestStatusReporter(activity);
        mReporter.testRunStarted(Process.myPid());
        Thread.setDefaultUncaughtExceptionHandler(
                new ReportingUncaughtExceptionHandler(
                        mReporter, Thread.getDefaultUncaughtExceptionHandler()));
    }

    private void parseArgumentsFromIntent(Activity activity, Intent intent) {
        Log.i(TAG, "Extras:");
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String s : extras.keySet()) {
                Log.i(TAG, "  %s", s);
            }
        }

        mCommandLineFilePath = intent.getStringExtra(NativeTestIntent.EXTRA_COMMAND_LINE_FILE);
        if (mCommandLineFilePath == null) {
            mCommandLineFilePath = "";
        } else {
            File commandLineFile = new File(mCommandLineFilePath);
            if (!commandLineFile.isAbsolute()) {
                mCommandLineFilePath =
                        Environment.getExternalStorageDirectory() + "/" + mCommandLineFilePath;
            }
            Log.i(TAG, "command line file path: %s", mCommandLineFilePath);
        }

        String commandLineFlags = intent.getStringExtra(NativeTestIntent.EXTRA_COMMAND_LINE_FLAGS);
        if (commandLineFlags != null) mCommandLineFlags.append(commandLineFlags);

        mRunInSubThread = intent.hasExtra(NativeTestIntent.EXTRA_RUN_IN_SUB_THREAD);

        String gtestFilter = intent.getStringExtra(NativeTestIntent.EXTRA_GTEST_FILTER);
        if (gtestFilter != null) {
            appendCommandLineFlags("--gtest_filter=" + gtestFilter);
        }

        mStdoutFilePath = intent.getStringExtra(NativeTestIntent.EXTRA_STDOUT_FILE);
        mDumpCoverage = intent.hasExtra(DUMP_COVERAGE);
    }

    public void appendCommandLineFlags(String flags) {
        mCommandLineFlags.append(" ").append(flags);
    }

    public void postStart(final Activity activity, boolean forceRunInSubThread) {
        final Runnable runTestsTask =
                new Runnable() {
                    @Override
                    public void run() {
                        runTests(activity);
                    }
                };

        if (mRunInSubThread || forceRunInSubThread) {
            // Post a task that posts a task that creates a new thread and runs tests on it.

            // On L and M, the system posts a task to the main thread that prints to stdout
            // from android::Layout (https://goo.gl/vZA38p). Chaining the subthread creation
            // through multiple tasks executed on the main thread ensures that this task
            // runs before we start running tests s.t. its output doesn't interfere with
            // the test output. See crbug.com/678146 for additional context.

            final Handler handler = new Handler();
            final Runnable startTestThreadTask =
                    new Runnable() {
                        @Override
                        public void run() {
                            new Thread(runTestsTask).start();
                        }
                    };
            final Runnable postTestStarterTask =
                    new Runnable() {
                        @Override
                        public void run() {
                            handler.post(startTestThreadTask);
                        }
                    };
            handler.post(postTestStarterTask);
        } else {
            // Post a task to run the tests. This allows us to not block
            // onCreate and still run tests on the main thread.
            new Handler().post(runTestsTask);
        }
    }

    private void runTests(Activity activity) {
        // Chromium pushes data to "chrome/test/data/chromium_test_root". But in AOSP, it's more
        // common to push testing data to data/local/tmp.
        // This has to be consistent with the AndroidTest configuration file.
        NativeTestJni.get().runTests(mCommandLineFlags.toString(), mCommandLineFilePath,
                mStdoutFilePath, activity.getApplicationContext(), "/data/local/tmp");
        if (mDumpCoverage) {
            new Handler(Looper.getMainLooper()).post(() -> {
                maybeDumpNativeCoverage();
                activity.finish();
                mReporter.testRunFinished(Process.myPid());
            });
        } else {
            activity.finish();
            mReporter.testRunFinished(Process.myPid());
        }
    }

    /**
     * If this test process is instrumented for native coverage, then trigger a dump
     * of the coverage data and wait until either we detect the dumping has finished or 60 seconds,
     * whichever is shorter.
     *
     * Background: Coverage builds install a signal handler for signal 37 which flushes coverage
     * data to disk, which may take a few seconds.  Tests running as an app process will get
     * killed with SIGKILL once the app code exits, even if the coverage handler is still running.
     *
     * Method: If a handler is installed for signal 37, then assume this is a coverage run and
     * send signal 37.  The handler is non-reentrant and so signal 37 will then be blocked until
     * the handler completes. So after we send the signal, we loop checking the blocked status
     * for signal 37 until we hit the 60 second deadline.  If the signal is blocked then sleep for
     * 2 seconds, and if it becomes unblocked then the handler exitted so we can return early.
     * If the signal is not blocked at the start of the loop then most likely the handler has
     * not yet been invoked.  This should almost never happen as it should get blocked on delivery
     * when we call {@code Os.kill()}, so sleep for a shorter duration (100ms) and try again.  There
     * is a race condition here where the handler is delayed but then runs for less than 100ms and
     * gets missed, in which case this method will loop with 100ms sleeps until the deadline.
     *
     * In the case where the handler runs for more than 60 seconds, the test process will be allowed
     * to exit so coverage information may be incomplete.
     *
     * There is no API for determining signal dispositions, so this method uses the
     * {@link SignalMaskInfo} class to read the data from /proc.  If there is an error parsing
     * the /proc data then this method will also loop until the 60s deadline passes.
     */
    private void maybeDumpNativeCoverage() {
        SignalMaskInfo siginfo = new SignalMaskInfo();
        if (!siginfo.isValid()) {
            Log.e(LOG_TAG, "Invalid signal info");
            return;
        }

        if (!siginfo.isCaught(COVERAGE_SIGNAL)) {
            // Process is not instrumented for coverage
            Log.i(LOG_TAG, "Not dumping coverage, no handler installed");
            return;
        }

        Log.i(LOG_TAG,
                String.format("Sending coverage dump signal %d to pid %d uid %d", COVERAGE_SIGNAL,
                        Os.getpid(), Os.getuid()));
        try {
            Os.kill(Os.getpid(), COVERAGE_SIGNAL);
        } catch (ErrnoException e) {
            Log.e(LOG_TAG, "Unable to send coverage signal", e);
            return;
        }

        long start = System.currentTimeMillis();
        long deadline = start + 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            siginfo.refresh();
            try {
                if (siginfo.isValid() && siginfo.isBlocked(COVERAGE_SIGNAL)) {
                    // Signal is currently blocked so assume a handler is running
                    Thread.sleep(2000L);
                    siginfo.refresh();
                    if (siginfo.isValid() && !siginfo.isBlocked(COVERAGE_SIGNAL)) {
                        // Coverage handler exited while we were asleep
                        Log.i(LOG_TAG,
                                String.format("Coverage dump detected finished after %dms",
                                        System.currentTimeMillis() - start));
                        break;
                    }
                } else {
                    // Coverage signal handler not yet started or invalid siginfo
                    Thread.sleep(100L);
                }
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    @NativeMethods
    interface Natives {
        void runTests(
                String commandLineFlags,
                String commandLineFilePath,
                String stdoutFilePath,
                Context appContext,
                String testDataDir);
    }
}
