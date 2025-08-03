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

package android.packagewatchdog.cts_root;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.VersionedPackage;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.PackageWatchdog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PackageWatchdogTest {

    private PackageWatchdog mPackageWatchdog;


    private static final String APP_A = "com.app.a";
    private static final String APP_B = "com.app.b";
    private static final String OBSERVER_NAME_1 = "observer-1";
    private static final String OBSERVER_NAME_2 = "observer-2";
    private static final int VERSION_CODE = 1;
    private static final long SHORT_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final int FAILURE_COUNT_THRESHOLD = 5;

    private CountDownLatch mLatch1, mLatch2;
    private TestObserver mTestObserver1, mTestObserver2;

    @Before
    public void setUp() {
        Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageWatchdog = PackageWatchdog.getInstance(mContext);
        mLatch1 = new CountDownLatch(1);
        mLatch2 = new CountDownLatch(1);
    }

    @After
    public void tearDown() {
        if (mTestObserver1 != null) {
            mPackageWatchdog.unregisterHealthObserver(mTestObserver1);
        }
        if (mTestObserver2 != null) {
            mPackageWatchdog.unregisterHealthObserver(mTestObserver2);
        }
    }

    @Test
    public void testAppCrashIsMitigated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1, latch);
        mPackageWatchdog.registerHealthObserver(mTestObserver1);
        mPackageWatchdog.startObservingHealth(
                mTestObserver1, List.of(APP_A), SHORT_DURATION);
        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_A,
                VERSION_CODE)), PackageWatchdog.FAILURE_REASON_APP_CRASH);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mTestObserver1.mMitigatedPackages).isEqualTo(List.of(APP_A));
    }

    /** Test that nothing happens if an app crashes that is not watched by any observer.*/
    @Test
    public void testAppCrashWithoutObserver() throws Exception {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1, mLatch1);

        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_A), SHORT_DURATION);
        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_B,
                VERSION_CODE)), PackageWatchdog.FAILURE_REASON_APP_CRASH);

        // Small break to allow failure to be noted.
        Thread.sleep(1000);
        assertThat(mTestObserver1.mMitigatedPackages).isEmpty();
    }

    /**
     * Test that multiple observers may register to watch certain packages and that they receive
     * the correct callbacks.
     */
    @Test
    public void testRegisteringMultipleObservers() throws Exception {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1, mLatch1);
        mTestObserver2 = new TestObserver(OBSERVER_NAME_2, mLatch2);

        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_A), SHORT_DURATION);
        mPackageWatchdog.startObservingHealth(
                mTestObserver2, Arrays.asList(APP_A, APP_B), SHORT_DURATION);
        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_A, VERSION_CODE),
                new VersionedPackage(APP_B, VERSION_CODE)),
                PackageWatchdog.FAILURE_REASON_APP_CRASH);
        assertThat(mLatch1.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mLatch2.await(5, TimeUnit.SECONDS)).isTrue();

        // The failed packages should be the same as the registered ones to ensure registration is
        // done successfully
        assertThat(mTestObserver1.mHealthCheckFailedPackages).containsExactly(APP_A);
        assertThat(mTestObserver2.mHealthCheckFailedPackages).containsExactly(APP_A, APP_B);
    }


    /**
     * Test that an unregistered observer is not notified for a failing package it previous
     * observed.
     */
    @Test
    public void testUnregistration() throws Exception {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1);
        mTestObserver2 = new TestObserver(OBSERVER_NAME_2, mLatch2);
        mPackageWatchdog.startObservingHealth(mTestObserver2, Arrays.asList(APP_A), SHORT_DURATION);
        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_A), SHORT_DURATION);

        mPackageWatchdog.unregisterHealthObserver(mTestObserver1);

        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_A,
                VERSION_CODE)), PackageWatchdog.FAILURE_REASON_APP_CRASH);

        assertThat(mLatch2.await(1, TimeUnit.MINUTES)).isTrue();


        assertThat(mTestObserver1.mHealthCheckFailedPackages).isEmpty();
        assertThat(mTestObserver2.mHealthCheckFailedPackages).containsExactly(APP_A);

    }

    /**
     * Test package failure under threshold does not notify observers
     */
    @Test
    public void testNoPackageFailureBeforeThreshold() throws Exception {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1);
        mTestObserver2 = new TestObserver(OBSERVER_NAME_2);

        mPackageWatchdog.startObservingHealth(mTestObserver2, Arrays.asList(APP_A), SHORT_DURATION);
        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_A), SHORT_DURATION);

        for (int i = 0; i < FAILURE_COUNT_THRESHOLD - 1; i++) {
            mPackageWatchdog.onPackageFailure(Arrays.asList(
                    new VersionedPackage(APP_A, VERSION_CODE)),
                    PackageWatchdog.FAILURE_REASON_UNKNOWN);
        }

        // Small break to allow failure to be noted.
        Thread.sleep(1000);

        // Verify that observers are not notified
        assertThat(mTestObserver1.mHealthCheckFailedPackages).isEmpty();
        assertThat(mTestObserver2.mHealthCheckFailedPackages).isEmpty();
    }

    /** Test that observers execute correctly for failures reasons that skip thresholding. */
    @Test
    public void testImmediateFailures() throws Exception {
        mLatch1 = new CountDownLatch(2);
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1, mLatch1);

        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_A), SHORT_DURATION);

        mPackageWatchdog.onPackageFailure(Arrays.asList(new VersionedPackage(APP_A, VERSION_CODE)),
                PackageWatchdog.FAILURE_REASON_EXPLICIT_HEALTH_CHECK);
        mPackageWatchdog.onPackageFailure(Arrays.asList(new VersionedPackage(APP_B, VERSION_CODE)),
                PackageWatchdog.FAILURE_REASON_NATIVE_CRASH);

        assertThat(mLatch1.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mTestObserver1.mMitigatedPackages).containsExactly(APP_A, APP_B);
    }

    /**
     * Test that a persistent observer will mitigate failures if it wishes to observe a package.
     */
    @Test
    public void testPersistentObserverWatchesPackage() throws Exception {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1, mLatch1);
        mTestObserver1.setPersistent(true);
        mTestObserver1.setMayObservePackages(true);

        mTestObserver2 = new TestObserver(OBSERVER_NAME_2, mLatch2);

        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_B), SHORT_DURATION);

        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_A,
                VERSION_CODE)), PackageWatchdog.FAILURE_REASON_APP_CRASH);
        assertThat(mLatch1.await(5, TimeUnit.SECONDS)).isTrue();

        // Persistent observer will observe the failing package.
        assertThat(mTestObserver1.mHealthCheckFailedPackages).containsExactly(APP_A);

        // A non-persistent observer will not observe the failing package.
        assertThat(mTestObserver2.mHealthCheckFailedPackages).isEmpty();
    }

    /**
     * Test that a persistent observer will not mitigate failures if it does not wish to observe
     * a given package.
     */
    @Test
    public void testPersistentObserverDoesNotWatchPackage() {
        mTestObserver1 = new TestObserver(OBSERVER_NAME_1);
        mTestObserver1.setPersistent(true);
        mTestObserver1.setMayObservePackages(false);

        mPackageWatchdog.startObservingHealth(mTestObserver1, Arrays.asList(APP_B), SHORT_DURATION);

        raiseFatalFailure(Arrays.asList(new VersionedPackage(APP_A,
                VERSION_CODE)), PackageWatchdog.FAILURE_REASON_UNKNOWN);
        assertThat(mTestObserver1.mHealthCheckFailedPackages).isEmpty();
    }

    private void raiseFatalFailure(List<VersionedPackage> failingPackages, int failureReason) {
        int failureCount = FAILURE_COUNT_THRESHOLD;
        if (failureReason == PackageWatchdog.FAILURE_REASON_NATIVE_CRASH
                || failureReason == PackageWatchdog.FAILURE_REASON_EXPLICIT_HEALTH_CHECK) {
            failureCount = 1;
        }
        for (int i = 0; i < failureCount; i++) {
            mPackageWatchdog.onPackageFailure(failingPackages, failureReason);
        }
    }

    private static class TestObserver implements PackageWatchdog.PackageHealthObserver {
        private final String mName;
        private final int mImpact;
        private boolean mIsPersistent = false;
        private boolean mMayObservePackages = false;
        final List<String> mMitigatedPackages = new ArrayList<>();
        final List<String> mHealthCheckFailedPackages = new ArrayList<>();
        private final CountDownLatch mLatch;

        TestObserver(String name, CountDownLatch latch) {
            mName = name;
            mLatch = latch;
            mImpact = PackageWatchdog.PackageHealthObserverImpact.USER_IMPACT_LEVEL_30;
        }

        TestObserver(String name) {
            mName = name;
            mLatch = new CountDownLatch(1);
            mImpact = PackageWatchdog.PackageHealthObserverImpact.USER_IMPACT_LEVEL_30;
        }

        public int onHealthCheckFailed(VersionedPackage versionedPackage, int failureReason,
                int mitigationCount) {
            mHealthCheckFailedPackages.add(versionedPackage.getPackageName());
            return mImpact;
        }

        public boolean execute(VersionedPackage versionedPackage, int failureReason,
                int mitigationCount) {
            mMitigatedPackages.add(versionedPackage.getPackageName());
            mLatch.countDown();
            return true;
        }

        public String getName() {
            return mName;
        }

        public int onBootLoop(int level) {
            return mImpact;
        }

        public boolean executeBootLoopMitigation(int level) {
            return true;
        }

        public boolean isPersistent() {
            return mIsPersistent;
        }

        public boolean mayObservePackage(String packageName) {
            return mMayObservePackages;
        }

        private void setPersistent(boolean isPersistent) {
            mIsPersistent = isPersistent;
        }

        private void setMayObservePackages(boolean mayObservePackages) {
            mMayObservePackages = mayObservePackages;
        }
    }
}
