/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.TelecomManager;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;

import androidx.test.InstrumentationRegistry;

import junit.framework.AssertionFailedError;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CarModeInCallServiceTest extends BaseTelecomTestWithMockServices {
    private static final int ASYNC_TIMEOUT = 10000;
    private static final String CARMODE_APP1_PACKAGE = "android.telecom.cts.carmodetestapp";
    private static final String CARMODE_APP2_PACKAGE = "android.telecom.cts.carmodetestapptwo";
    // Flag to temp disable (flaky) test
    private static final boolean SHOULD_IGNORE_TEST = true;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlOne;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlTwo;

    @Override
    protected void setUp() throws Exception {
        boolean isSetUpComplete = false;
        super.setUp();
        if (!mShouldTestTelecom || TestUtils.hasAutomotiveFeature()) {
            //all the tests here are assuming a non-auto device since
            //expected value from uiModemanger is UI_MODE_TYPE_NORMAL
            //In the future, we can add new testcases for auto, but skip for now
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.ENTER_CAR_MODE_PRIORITIZED",
                            "android.permission.CONTROL_INCALL_EXPERIENCE",
                            "android.permission.TOGGLE_AUTOMOTIVE_PROJECTION");

            mCarModeIncallServiceControlOne = getControlBinder(CARMODE_APP1_PACKAGE);
            mCarModeIncallServiceControlTwo = getControlBinder(CARMODE_APP2_PACKAGE);
            // Ensure we start the test without automotive projection set.
            releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
            releaseAutomotiveProjection(mCarModeIncallServiceControlTwo);
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

            final UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);
            assertEquals("Device must not be in car mode at start of test.",
                    Configuration.UI_MODE_TYPE_NORMAL, uiModeManager.getCurrentModeType());
            mInCallCallbacks.resetLock();
            isSetUpComplete = true;
        } finally {
            // Force tearDown if setUp errors out to ensure unused listeners are cleaned up.
            if (!isSetUpComplete) {
                tearDown();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (mShouldTestTelecom
                    && !mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WATCH) && !TestUtils.hasAutomotiveFeature()) {
                disableAndVerifyCarMode(mCarModeIncallServiceControlOne,
                        Configuration.UI_MODE_TYPE_NORMAL);
                disableAndVerifyCarMode(mCarModeIncallServiceControlTwo,
                        Configuration.UI_MODE_TYPE_NORMAL);
                disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);
                disconnectAllCallsAndVerify(mCarModeIncallServiceControlTwo);

                if (mCarModeIncallServiceControlOne != null) {
                    mCarModeIncallServiceControlOne.reset();
                }

                if (mCarModeIncallServiceControlTwo != null) {
                    mCarModeIncallServiceControlTwo.reset();
                }

                assertUiMode(Configuration.UI_MODE_TYPE_NORMAL);

                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .dropShellPermissionIdentity();
            }
        } finally {
            super.tearDown();
        }
    }

    /**
     * Verifies that a car mode InCallService can enter and exit car mode using a priority.
     */
    public void testSetCarMode() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
    }

    public void testRequestAutomotiveProjection() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        // Multiple calls should succeed.
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
    }

    public void testRequestAutomotiveProjectionExclusive() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlTwo, false);
        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlTwo, true);
        releaseAutomotiveProjection(mCarModeIncallServiceControlTwo);
    }

    /**
     * Verifies we bind to a car mode InCallService when a call is started when the device is
     * already in car mode.
     */
    public void testStartCallInCarMode() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);

        // Place a call and verify we bound to the Car Mode InCallService
        placeCarModeCall();
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);

        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
    }

    /**
     * Verifies we bind to a car mode InCallService when a call is started when the service has
     * already set automotive projection.
     */
    public void testStartCallInAutomotiveProjection() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);

        // Place a call and verify we bound to the Car Mode InCallService
        placeCarModeCall();
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);

        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
    }

    /**
     * Verifies we bind to a car mode InCallService when a call is started and the service has set
     * both car mode AND projection.
     */
    public void failingTestStartCallInCarModeAndAutomotiveProjection() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);

        // Place a call and verify we bound to the Car Mode InCallService
        placeCarModeCall();
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Release projection and we should still have the call.
        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Re-request projection.
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Exit car mode. Should still have the call by virtue of projection being set.
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);
        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
    }

    /**
     * Tests a scenario where we have two apps enter car mode.
     * Ensures that the higher priority app is bound and receives information about the call.
     * When the higher priority app leaves car mode, verifies that the lower priority app is bound
     * and receives information about the call.
     */
    public void testStartCallInCarModeTwoServices() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        enableAndVerifyCarMode(mCarModeIncallServiceControlTwo, 999);

        // Place a call and verify we bound to the Car Mode InCallService
        placeCarModeCall();
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Now disable car mode in the higher priority service
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_CAR);
        verifyCarModeBound(mCarModeIncallServiceControlTwo);
        assertCarModeCallCount(mCarModeIncallServiceControlTwo, 1);

        // Drop the call from the second service.
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlTwo);
        disableAndVerifyCarMode(mCarModeIncallServiceControlTwo, Configuration.UI_MODE_TYPE_NORMAL);
    }

    /**
     * Tests a scenario where one app enters car mode and the other sets automotive projection.
     * Ensures that the automotive projection app is bound and receives information about the call.
     * When the projecting app releases projection, verifies that the car mode app is bound
     * and receives information about the call.
     */
    public void testStartCallingInCarModeAndProjectionTwoServices() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlTwo, true);

        // Place a call and verify we bound to the Car Mode InCallService
        placeCarModeCall();
        verifyCarModeBound(mCarModeIncallServiceControlTwo);
        assertCarModeCallCount(mCarModeIncallServiceControlTwo, 1);

        // Now release projection from the projecting service
        releaseAutomotiveProjection(mCarModeIncallServiceControlTwo);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Drop the call from the car mode service.
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
    }

    /**
     * Verifies we can switch from the default dialer to the car-mode InCallService when car mode is
     * enabled.
     */
    public void testSwitchToCarMode() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, enable car mode; should have swapped to the InCallService.
        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);

        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
    }

    /**
     * Similar to {@link #testSwitchToCarMode()}, except exits car mode before the call terminates.
     */
    public void testSwitchToCarModeAndBack() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, enable car mode and confirm we're using the car mode ICS.
        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Now, disable car mode and confirm we're no longer using the car mode ICS.
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);

        // Verify that we did bind back to the default dialer.
        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            fail("Interrupted!");
        }

        assertEquals(1, mInCallCallbacks.getService().getCallCount());
        mInCallCallbacks.getService().disconnectAllCalls();
    }

    /**
     * Similar to {@link #testSwitchToCarMode()}, except enters car mode after the call starts.
     * Also uses multiple car mode InCallServices.
     */
    public void testSwitchToCarModeMultiple() {
        if (!mShouldTestTelecom) {
            return;
        }

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, enable car mode and confirm we're using the car mode ICS.
        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 999);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Now, enable a higher priority car mode all and confirm we're using it.
        enableAndVerifyCarMode(mCarModeIncallServiceControlTwo, 1000);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);
        verifyCarModeBound(mCarModeIncallServiceControlTwo);
        assertCarModeCallCount(mCarModeIncallServiceControlTwo, 1);

        // Disable higher priority, verify we drop back to lower priority.
        disableAndVerifyCarMode(mCarModeIncallServiceControlTwo, Configuration.UI_MODE_TYPE_CAR);
        verifyCarModeUnbound(mCarModeIncallServiceControlTwo);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Finally, disable car mode at the lower priority and confirm we're using the default
        // dialer once more.
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);

        // Verify that we did bind back to the default dialer.
        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            fail("Interrupted!");
        }

        assertEquals(1, mInCallCallbacks.getService().getCallCount());
        mInCallCallbacks.getService().disconnectAllCalls();
    }

    /**
     * Verifies we can switch from the default dialer to the car-mode InCallService when automotive
     * projection is set.
     */
    public void testSwitchToAutomotiveProjection() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, request automotive projection; should have swapped to the InCallService.
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);

        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
    }

    /**
     * Similar to {@link #testSwitchToAutomotiveProjection()}, except releases projection before the
     * call terminates.
     */
    public void testSwitchToAutomotiveProjectionAndBack() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, request automotive projection and confirm we're using the car mode ICS.
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Now, release projection and confirm we're no longer using the car mode ICS.
        releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);

        // Verify that we did bind back to the default dialer.
        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            fail("Interrupted!");
        }

        assertEquals(1, mInCallCallbacks.getService().getCallCount());
        mInCallCallbacks.getService().disconnectAllCalls();
    }

    /**
     * Similar to {@link #testSwitchToAutomotiveProjection()}, except sets automotive projection
     * after the call starts and has been bound to an InCallService using car mode.
     */
    public void testSwitchToAutomotiveProjectionMultiple() {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        // Place a call and verify it went to the default dialer
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Now, request automotive projection and confirm we're using the car mode ICS.
        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, Integer.MAX_VALUE);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Now, request automotive projection from a different ICS and confirm we're using it.
        requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlTwo, true);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);
        verifyCarModeBound(mCarModeIncallServiceControlTwo);
        assertCarModeCallCount(mCarModeIncallServiceControlTwo, 1);

        // Release automotive projection, verify we drop back to the car mode ICS.
        releaseAutomotiveProjection(mCarModeIncallServiceControlTwo);
        verifyCarModeUnbound(mCarModeIncallServiceControlTwo);
        verifyCarModeBound(mCarModeIncallServiceControlOne);
        assertCarModeCallCount(mCarModeIncallServiceControlOne, 1);

        // Finally, disable car mode and confirm we're using the default dialer once more.
        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        verifyCarModeUnbound(mCarModeIncallServiceControlOne);

        // Verify that we did bind back to the default dialer.
        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            fail("Interrupted!");
        }

        assertEquals(1, mInCallCallbacks.getService().getCallCount());
        mInCallCallbacks.getService().disconnectAllCalls();
    }

    public void testSwitchToCarModeWhenEnableCarModeApp() throws Exception {
        if (!mShouldTestTelecom || SHOULD_IGNORE_TEST) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        enableAndVerifyCarMode(mCarModeIncallServiceControlOne, 1000);
        mContext.getPackageManager().setApplicationEnabledSetting(CARMODE_APP1_PACKAGE,
                COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);


        placeCarModeCall();
        try {
            verifyCarModeBound(mCarModeIncallServiceControlOne);
            throw new Exception("Car mode 1 was disabled but bound.");
        } catch (AssertionFailedError e) {
            // Expected
        }

        mContext.getPackageManager().setApplicationEnabledSetting(CARMODE_APP1_PACKAGE,
                COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
        verifyCarModeBound(mCarModeIncallServiceControlOne);

        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        mInCallCallbacks.getService().disconnectAllCalls();
    }

    public void testNoSwitchToCarModeWhenDisabledCarModeAppAutomotiveProjection() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            return;
        }

        try {
            mContext.getPackageManager().setApplicationEnabledSetting(CARMODE_APP1_PACKAGE,
                    COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
            mInCallCallbacks.resetLatch();

            // Place a call and verify it went to the default dialer
            placeAndVerifyCall();
            verifyConnectionForOutgoingCall();

            // Now, request automotive projection; shouldn't unbind from default dialer.
            requestAndVerifyAutomotiveProjection(mCarModeIncallServiceControlOne, true);
            assertFalse(mInCallCallbacks.waitForUnbind());

            releaseAutomotiveProjection(mCarModeIncallServiceControlOne);
            mInCallCallbacks.getService().disconnectAllCalls();
        } finally {
            mContext.getPackageManager().setApplicationEnabledSetting(CARMODE_APP1_PACKAGE,
                    COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
        }
    }


    /**
     * Places a call without verifying it is handled by the default dialer InCallService.
     */
    private void placeCarModeCall() {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);

        Uri number = createTestNumber();
        mTelecomManager.placeCall(number, extras);

        verifyConnectionForOutgoingCall(number);
    }

    /**
     * Verifies that we bound to the car-mode ICS.
     */
    private void verifyCarModeBound(ICtsCarModeInCallServiceControl control) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        boolean isBound = false;
                        try {
                            isBound = control.isBound();
                        } catch (RemoteException re) {
                            fail("Bee-boop; can't control the incall service");
                        }
                        return isBound;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected to be bound"
        );
    }

    /**
     * Verifies that we bound to the car-mode ICS.
     */
    private void verifyCarModeUnbound(ICtsCarModeInCallServiceControl control) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        boolean isUnbound = false;
                        try {
                            isUnbound = control.isUnbound();
                        } catch (RemoteException re) {
                            fail("Bee-boop; can't control the incall service");
                        }
                        return isUnbound;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected to be unbound"
        );
    }

    /**
     * Uses the control interface to enable car mode at a specified priority.
     * @param priority the requested priority.
     */
    private void enableAndVerifyCarMode(ICtsCarModeInCallServiceControl control, int priority) {
        try {
            control.enableCarMode(priority);
        } catch (RemoteException re) {
            fail("Bee-boop; can't control the incall service");
        }
        assertUiMode(Configuration.UI_MODE_TYPE_CAR);
    }

    /**
     * Uses the control interface to request automotive projection assert success or failure.
     * @param expectedSuccess whether or not we expect the operation to succeed.
     */
    private void requestAndVerifyAutomotiveProjection(ICtsCarModeInCallServiceControl control,
            boolean expectedSuccess) {
        try {
            assertEquals(expectedSuccess, control.requestAutomotiveProjection());
        } catch (SecurityException se) {
            fail("Not allowed to request automotive projection!");
        } catch (RemoteException re) {
            fail("Bee-boop; can't control the incall service");
        }
    }

    /**
     * Uses the control interface to release automotive projection.
     */
    private void releaseAutomotiveProjection(ICtsCarModeInCallServiceControl control) {
        try {
            control.releaseAutomotiveProjection();
        } catch (SecurityException se) {
            fail("Not allowed to release automotive projection!");
        } catch (RemoteException re) {
            fail("Bee-boop; can't control the incall service");
        }
    }

    /**
     * Setup a binder which can be used to control the CarModeTestApp.
     * @throws InterruptedException
     */
    private ICtsCarModeInCallServiceControl getControlBinder(String packageName)
            throws Exception {
        Intent bindIntent = new Intent(
                android.telecom.cts.carmodetestapp.CtsCarModeInCallServiceControl
                        .CONTROL_INTERFACE_ACTION);
        bindIntent.setPackage(packageName);
        CompletableFuture<ICtsCarModeInCallServiceControl> future =
                new CompletableFuture<>();
        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                future.complete(android.telecom.cts.carmodetestapp
                        .ICtsCarModeInCallServiceControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                future.complete(null);
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        return future.get(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
    }
}
