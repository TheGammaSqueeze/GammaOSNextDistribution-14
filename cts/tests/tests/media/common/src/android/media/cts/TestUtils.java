/*
 * Copyright 2018 The Android Open Source Project
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

package android.media.cts;

import static android.content.pm.PackageManager.MATCH_APEX;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;

import java.util.Objects;

/**
 * Utilities for tests.
 */
public final class TestUtils {
    private static String TAG = "TestUtils";
    private static final int WAIT_TIME_MS = 1000;
    private static final int WAIT_SERVICE_TIME_MS = 5000;

    /**
     * Compares contents of two bundles.
     *
     * @param a a bundle
     * @param b another bundle
     * @return {@code true} if two bundles are the same. {@code false} otherwise. This may be
     *     incorrect if any bundle contains a bundle.
     */
    public static boolean equals(Bundle a, Bundle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.keySet().containsAll(b.keySet())
                || !b.keySet().containsAll(a.keySet())) {
            return false;
        }
        for (String key : a.keySet()) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks {@code module} is at least {@code minVersion}
     *
     * The tests are skipped by throwing a {@link AssumptionViolatedException}.  CTS test runners
     * will report this as a {@code ASSUMPTION_FAILED}.
     *
     * @param module     the apex module name
     * @param minVersion the minimum version
     * @throws AssumptionViolatedException if module version < minVersion
     */
    public static void assumeMainlineModuleAtLeast(String module, long minVersion) {
        try {
            long actualVersion = getModuleVersion(module);
            assumeTrue("Assume  module  " + module + " version " + actualVersion + " < minVersion"
                    + minVersion, actualVersion >= minVersion);
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Checks if {@code module} is < {@code minVersion}
     *
     * <p>
     * {@link AssumptionViolatedException} is not handled properly by {@code JUnit3} so just return
     * the test
     * early instead.
     *
     * @param module     the apex module name
     * @param minVersion the minimum version
     * @deprecated convert test to JUnit4 and use
     * {@link #assumeMainlineModuleAtLeast(String, long)} instead.
     */
    @Deprecated
    public static boolean skipTestIfMainlineLessThan(String module, long minVersion) {
        try {
            long actualVersion = getModuleVersion(module);
            if (actualVersion < minVersion) {
                Log.i(TAG, "Skipping test because Module  " + module + " minVersion " + minVersion
                        + " > "
                        + minVersion
                );
                return true;
            } else {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail(e.getMessage());
            return false;
        }
    }

    private static long getModuleVersion(String module)
            throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(module,
                MATCH_APEX);
        return info.getLongVersionCode();
    }


    /**
     * Reports whether {@code module} is the version shipped with the original system image
     * or if it has been updated via a mainline update.
     *
     * @param module     the apex module name
     * @return {@code true} if the apex module is the original version shipped with the device.
     */
    public static boolean isMainlineModuleFactoryVersion(String module) {
        try {
            Context context = ApplicationProvider.getApplicationContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(module,
                    MATCH_APEX);
            if (info != null) {
                return (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore the exception on devices that do not have this module
        }
        return true;
    }

    /*
     * Report whether we are in MTS mode (vs in CTS) mode.
     * Some tests (or parts of tests) are restricted to a particular mode.
     */
    public static boolean isMtsMode() {
        Bundle bundle = InstrumentationRegistry.getArguments();
        // null if not set
        boolean isMTS = TextUtils.equals("true", bundle.getString("mts-media"));

        return isMTS;
    }

    /*
     * Report whether we want to test a particular code in the current test mode.
     * CTS is pretty much "test them all".
     * MTS should only be testing codecs that are part of the swcodec module; all of these
     * begin with "c2.android."
     *
     * Used in spots throughout the test suite where we want to limit our testing to relevant
     * codecs. This avoids false alarms that are sometimes triggered by non-compliant,
     * non-mainline codecs.
     *
     * @param name    the name of a codec
     * @return {@code} true is the codec should be tested in the current operating mode.
     */
    public static boolean isTestableCodecInCurrentMode(String name) {
        if (name == null) {
            return false;
        }
        if (!isMtsMode()) {
            // CTS mode -- test everything
            return true;
        }
        // MTS mode, just the codecs that live in the modules
        if (isMainlineCodec(name)) {
            return true;
        }
        Log.d(TAG, "Test mode MTS does not test codec " + name);
        return false;
    }

    /*
     * Report whether this codec is a google-supplied codec that lives within the
     * mainline modules.
     *
     * @param name    the name of a codec
     * @return {@code} true if the codec is one that lives within the mainline boundaries
     */
    public static boolean isMainlineCodec(String name) {
        if (name.startsWith("c2.android.")) {
            return true;
        }
        return false;
    }

    private TestUtils() {
    }

    public static class Monitor {
        private int mNumSignal;

        public synchronized void reset() {
            mNumSignal = 0;
        }

        public synchronized void signal() {
            mNumSignal++;
            notifyAll();
        }

        public synchronized boolean waitForSignal() throws InterruptedException {
            return waitForCountedSignals(1) > 0;
        }

        public synchronized int waitForCountedSignals(int targetCount) throws InterruptedException {
            while (mNumSignal < targetCount) {
                wait();
            }
            return mNumSignal;
        }

        public synchronized boolean waitForSignal(long timeoutMs) throws InterruptedException {
            return waitForCountedSignals(1, timeoutMs) > 0;
        }

        public synchronized int waitForCountedSignals(int targetCount, long timeoutMs)
                throws InterruptedException {
            if (timeoutMs == 0) {
                return waitForCountedSignals(targetCount);
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (mNumSignal < targetCount) {
                long delay = deadline - System.currentTimeMillis();
                if (delay <= 0) {
                    break;
                }
                wait(delay);
            }
            return mNumSignal;
        }

        public synchronized boolean isSignalled() {
            return mNumSignal >= 1;
        }

        public synchronized int getNumSignal() {
            return mNumSignal;
        }
    }
}
