/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.car.cluster.cts;

import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.car.feature.Flags.FLAG_CLUSTER_HEALTH_MONITORING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarAppFocusManager.OnAppFocusOwnershipCallback;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterHomeManager.ClusterNavigationStateListener;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cts.utils.DumpUtils;
import android.car.navigation.CarNavigationStatusManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ClusterHomeManagerTest {
    private static final long TIMEOUT_MS = 5_000;
    private static final String CLUSTER_HOME_SERVICE = "ClusterHomeService";
    private static final String DUMP_TPL_COUNT = "mTrustedPresentationListenerCount";
    private static final String DUMP_CLUSTER_SURFACE = "mClusterActivitySurface";
    private static final String DUMP_CLUSTER_VISIBLE = "mClusterActivityVisible";
    private static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";
    private static final NavigationStateProto NAVIGATION_STATE_1 =
            NavigationStateProto.newBuilder().setServiceStatus(
                    NavigationStateProto.ServiceStatus.NORMAL).build();;
    private static final NavigationStateProto NAVIGATION_STATE_2 =
            NavigationStateProto.newBuilder().setServiceStatus(
                    NavigationStateProto.ServiceStatus.REROUTING).build();
    private static final Bundle NAVIGATION_STATE_BUNDLE_1 = new Bundle();
    private static final Bundle NAVIGATION_STATE_BUNDLE_2 = new Bundle();

    static {
        NAVIGATION_STATE_BUNDLE_1.putByteArray(
                NAV_STATE_PROTO_BUNDLE_KEY, NAVIGATION_STATE_1.toByteArray());
        NAVIGATION_STATE_BUNDLE_2.putByteArray(
                NAV_STATE_PROTO_BUNDLE_KEY, NAVIGATION_STATE_2.toByteArray());
    }

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    private final ComponentName mTestActivityName =
            new ComponentName(mTargetContext, ClusterHomeManagerTest.TestActivity.class);
    private ClusterHomeManager mClusterHomeManager;
    private CarAppFocusManager mCarAppFocusManager;
    private CarNavigationStatusManager mCarNavigationStatusManager;
    private TestActivity mTestActivity;

    @Before
    public void setUp() {
        mUiAutomation.adoptShellPermissionIdentity(
                Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL,
                Car.PERMISSION_CAR_MONITOR_CLUSTER_NAVIGATION_STATE,
                Car.PERMISSION_CAR_NAVIGATION_MANAGER);

        Car car = Car.createCar(mContext);
        mClusterHomeManager = car.getCarManager(ClusterHomeManager.class);
        assumeTrue(mClusterHomeManager != null);
        mCarAppFocusManager = car.getCarManager(CarAppFocusManager.class);
        mCarNavigationStatusManager = car.getCarManager(CarNavigationStatusManager.class);
    }

    @After
    public void tearDown() throws Exception {
        // mTestActivity is not cleaned up here so each test that uses it needs to clean it up.

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CLUSTER_HEALTH_MONITORING)
    @ApiTest(apis = {"android.car.cluster.ClusterHomeManager#startVisibilityMonitoring(Activity)"})
    public void testStartVisibilityMonitoring() throws Exception {
        var oldDump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
        int oldCount = Integer.valueOf(oldDump.get(DUMP_TPL_COUNT));

        mTestActivity = (TestActivity) mInstrumentation.startActivitySync(
                Intent.makeMainActivity(mTestActivityName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        // The callback will be called with 'true' as soon as the system bars disappear.
        PollingCheck.waitFor(TIMEOUT_MS, () -> {
            var dump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
            int count = Integer.valueOf(dump.get(DUMP_TPL_COUNT));
            boolean visible = Boolean.parseBoolean(dump.get(DUMP_CLUSTER_VISIBLE));
            return count > oldCount && visible;
        });

        var oldDump2 = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
        int oldCount2 = Integer.valueOf(oldDump2.get(DUMP_TPL_COUNT));

        // Insets can be accessible only in the Activity's thread.
        mTestActivity.getMainExecutor().execute(() -> {
                    WindowInsetsController insets =
                            mTestActivity.getWindow().getDecorView().getWindowInsetsController();
                    insets.show(WindowInsets.Type.systemBars());
                }
        );

        // The callback will be called with 'false' as soon as the system bars appear.
        PollingCheck.waitFor(TIMEOUT_MS, () -> {
            var dump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
            int count = Integer.valueOf(dump.get(DUMP_TPL_COUNT));
            boolean visible = Boolean.parseBoolean(dump.get(DUMP_CLUSTER_VISIBLE));
            return count > oldCount2 && !visible;
        });

        // Destroy the test activity.
        if (mTestActivity != null) {
            mTestActivity.finishAndRemoveTask();
            mTestActivity.waitForDestroyed();
            mTestActivity = null;
        }
        // Ensure that visibility monitoring has stopped.
        PollingCheck.waitFor(TIMEOUT_MS, () -> {
            String monitoringSurface = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE)
                    .get(DUMP_CLUSTER_SURFACE);
            return monitoringSurface.equals("null");
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CLUSTER_HEALTH_MONITORING)
    @ApiTest(apis = {
            "android.car.cluster.ClusterHomeManager#registerClusterNavigationStateListener",
            "android.car.cluster.ClusterHomeManager#unregisterClusterNavigationStateListener"})
    public void testRegisterAndUnregisterClusterNavigationStateListener() {
        TestNavigationStateListener listener1 = new TestNavigationStateListener();
        TestNavigationStateListener listener2 = new TestNavigationStateListener();
        mClusterHomeManager.registerClusterNavigationStateListener(
                mContext.getMainExecutor(), listener1);
        mClusterHomeManager.registerClusterNavigationStateListener(
                mContext.getMainExecutor(), listener2);
        TestAppFocusCallback focusCallback = new TestAppFocusCallback();
        mCarAppFocusManager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, focusCallback);
        focusCallback.waitForFocusGranted();

        // Send the 1st navigation state.
        mCarNavigationStatusManager.sendNavigationStateChange(NAVIGATION_STATE_BUNDLE_1);
        // Both listeners should receive the 1st navigation state change.
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> (listener1.getNavigationState().equals(NAVIGATION_STATE_1)
                        && listener2.getNavigationState().equals(NAVIGATION_STATE_1)));

        // Unregister listener1.
        mClusterHomeManager.unregisterClusterNavigationStateListener(listener1);
        // Send the 2nd navigation state.
        mCarNavigationStatusManager.sendNavigationStateChange(NAVIGATION_STATE_BUNDLE_2);

        // Only listener2 is expected to receive the new navigation state.
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> (listener2.getNavigationState().equals(NAVIGATION_STATE_2)));
        assertThat(listener1.getNavigationState()).isEqualTo(NAVIGATION_STATE_1);
    }

    private static class TestAppFocusCallback implements OnAppFocusOwnershipCallback {
        private boolean mHasFocus = false;
        @Override
        public void onAppFocusOwnershipGranted(int appType) {
            mHasFocus = true;
        }

        @Override
        public void onAppFocusOwnershipLost(int appType) {
            mHasFocus = false;
        }

        public void waitForFocusGranted() {
            PollingCheck.waitFor(TIMEOUT_MS, () -> mHasFocus);
        }
    }

    private static class TestNavigationStateListener implements ClusterNavigationStateListener {
        private NavigationStateProto mReceivedSate = NavigationStateProto.getDefaultInstance();


        public NavigationStateProto getNavigationState() {
            return mReceivedSate;
        }

        @Override
        public void onNavigationStateChanged(byte[] navigationState) {
            try {
                mReceivedSate = NavigationStateProto.parseFrom(navigationState);
            } catch (Exception e) {
                // This should never happen.
                throw new AssertionError("Received an invalid byte stream ", e);
            }
        }
    }

    public static final class TestActivity extends Activity {
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onStart() {
            super.onStart();
            Car car = Car.createCar(this);
            ClusterHomeManager clusterHomeManager = car.getCarManager(ClusterHomeManager.class);
            clusterHomeManager.startVisibilityMonitoring(this);

            // SystemBar also hides ActivitySurface, so hide the system bars.
            WindowInsetsController insets = getWindow().getDecorView().getWindowInsetsController();
            insets.hide(WindowInsets.Type.systemBars());
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
