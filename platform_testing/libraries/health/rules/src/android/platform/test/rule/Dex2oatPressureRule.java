/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.platform.test.rule;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import org.junit.runner.Description;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * A rule that stresses the device by running dex2oat in the background.
 *
 * <p>The rule takes a list of packages from command-line arguments and compiles them in a loop
 * until the test framework informs it to stop via finished(), using a compilation filter supplied
 * also via command-line args, or {@code speed} by default.
 */
public class Dex2oatPressureRule extends TestWatcher {
    public static final String LOG_TAG = Dex2oatPressureRule.class.getSimpleName();

    // Packages names for running dex2oat as a list.
    public static final String PACKAGES_OPTION = "dex2oat-stressor-pkgs";

    // Option for filter to compile the packages with, and some associated variables.
    public static final String COMPILATION_FILTER_OPTION = "dex2oat-stressor-compilation-filter";
    public static final String SPEED_FILTER = "speed";
    public static final ImmutableList<String> SUPPORTED_FILTERS_LIST =
            ImmutableList.of(SPEED_FILTER, "quicken", "verify");

    // A switch for turning the stressor off for quick a/b comparisons.
    public static final String ENABLE_OPTION = "dex2oat-stressor-enable";
    private boolean mEnabled = false;

    @VisibleForTesting public static final String DEX2OAT_RUNNING_CHECK_COMMAND = "pgrep dex2oat";
    public static final String LIST_PACKAGES_COMMAND = "pm list packages";
    private static final String COMPILE_COMMAND_TEMPLATE = "cmd package compile -f -m %s %s";
    private static final String COMPILE_COMMAND_SUCCESS_RESPONSE = "Success";

    private static final long DEX2OAT_POLLING_INTERVAL = TimeUnit.MILLISECONDS.toMillis(50);
    private static final long DEX2OAT_POLLING_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private Dex2oatRunnable mDex2oatTask;
    private Thread mDex2oatThread;

    @Override
    protected void starting(Description description) {
        mEnabled = Boolean.valueOf(getArguments().getString(ENABLE_OPTION, String.valueOf(false)));
        if (!mEnabled) {
            return;
        }

        if (!getArguments().containsKey(PACKAGES_OPTION)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Please supply a comma-separated list of packages to compile via the "
                                    + "%s option. The rule will have no effect and results "
                                    + "should be discarded.",
                            PACKAGES_OPTION));
        }
        List<String> packagesToCompile =
                Arrays.asList(getArguments().getString(PACKAGES_OPTION).split(","));
        Set<String> installedPackages = getInstalledPackages();
        if (!packagesToCompile.stream().allMatch(pkg -> installedPackages.contains(pkg))) {
            throw new IllegalArgumentException(
                    String.format(
                            "The following supplied packages are not installed on the device and "
                                    + "can't be compiled: %s. Results should be discarded.",
                            packagesToCompile
                                    .stream()
                                    .filter(pkg -> !installedPackages.contains(pkg))
                                    .collect(joining(", "))));
        }

        String compilationFilter =
                getArguments().getString(COMPILATION_FILTER_OPTION, SPEED_FILTER);
        if (!SUPPORTED_FILTERS_LIST.contains(compilationFilter)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid compilation filter %s. Please supply a compilation filter "
                                    + "from the following: %s. Results should be discarded.",
                            compilationFilter, String.join(", ", SUPPORTED_FILTERS_LIST)));
        }

        mDex2oatTask = new Dex2oatRunnable(packagesToCompile, compilationFilter);
        mDex2oatThread = new Thread(mDex2oatTask);
        mDex2oatThread.start();

        // Wait until dex2oat is running.
        long pollingStartTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - pollingStartTime <= DEX2OAT_POLLING_TIMEOUT) {
            if (dex2oatIsRunning()) {
                return;
            }
            SystemClock.sleep(DEX2OAT_POLLING_INTERVAL);
        }
        // If we reach here, dex2oat still isn't running. At this point we should cancel our
        // attempts and throw to prevent inaccurate results.
        stopDex2oatAndWaitForFinish();
        throw new IllegalStateException(
                String.format(
                        "dex2oat still isn't running after %d ms. Results should be discarded.",
                        DEX2OAT_POLLING_TIMEOUT));
    }

    @Override
    protected void finished(Description description) {
        if (!mEnabled) {
            return;
        }
        // If the thread is null, dex2oat had never been triggered. No further actions are needed.
        if (mDex2oatThread == null) {
            return;
        }

        stopDex2oatAndWaitForFinish();
    }

    private void stopDex2oatAndWaitForFinish() {
        stopDex2oat();
        try {
            mDex2oatThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get all installed packages on the device. Leaving visible for stubbing. */
    @VisibleForTesting
    protected Set<String> getInstalledPackages() {
        String response = executeShellCommand(LIST_PACKAGES_COMMAND);
        return Arrays.asList(response.split("\n"))
                .stream()
                .map(line -> line.replace("package:", "").trim())
                .collect(toSet());
    }

    /** Stop the dex2oat thread. Leaving visible for stubbing. */
    @VisibleForTesting
    protected void stopDex2oat() {
        mDex2oatTask.pleaseStop();
    }

    /** Run the actual compilation command. Enclosed in a separate method for testing. */
    @VisibleForTesting
    protected void runCompileCommand(String pkg, String filter) {
        String response =
                executeShellCommand(String.format(COMPILE_COMMAND_TEMPLATE, filter, pkg)).trim();
        if (!response.equalsIgnoreCase(COMPILE_COMMAND_SUCCESS_RESPONSE)) {
            // Log but do not throw on the failure, so that other packages can continue.
            Log.w(
                    LOG_TAG,
                    String.format(
                            "Compilation for package %s and filter %s failed with response %s.",
                            pkg, filter, response));
        }
    }

    /** Check if dex2oat is running. Enclosed in a separate method for testing. */
    @VisibleForTesting
    protected boolean dex2oatIsRunning() {
        String dex2oatPid = executeShellCommand(DEX2OAT_RUNNING_CHECK_COMMAND).trim();
        if (!dex2oatPid.isEmpty()) {
            Log.i(
                    LOG_TAG,
                    String.format("dex2oat is now running and has a process ID %s.", dex2oatPid));
            return true;
        }
        return false;
    }

    private class Dex2oatRunnable implements Runnable {
        private final ImmutableList<String> mPackagesToCompile;
        private final String mCompilationFilter;
        private AtomicBoolean mShouldContinue = new AtomicBoolean(true);

        public Dex2oatRunnable(List<String> packagesToCompile, String compilationFilter) {
            mPackagesToCompile = ImmutableList.copyOf(packagesToCompile);
            mCompilationFilter = compilationFilter;
        }

        @Override
        public void run() {
            while (mShouldContinue.get()) {
                for (String pkg : mPackagesToCompile) {
                    runCompileCommand(pkg, mCompilationFilter);
                    if (!mShouldContinue.get()) {
                        break;
                    }
                }
            }
        }

        public void pleaseStop() {
            mShouldContinue.set(false);
        }
    }
}
