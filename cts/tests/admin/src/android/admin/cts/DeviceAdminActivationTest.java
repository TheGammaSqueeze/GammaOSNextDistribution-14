/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.admin.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.admin.app.CtsDeviceAdminActivationTestActivity;
import android.admin.app.CtsDeviceAdminActivationTestActivity.OnActivityResultListener;
import android.admin.app.CtsDeviceAdminBrokenReceiver;
import android.admin.app.CtsDeviceAdminBrokenReceiver2;
import android.admin.app.CtsDeviceAdminBrokenReceiver3;
import android.admin.app.CtsDeviceAdminBrokenReceiver4;
import android.admin.app.CtsDeviceAdminBrokenReceiver5;
import android.admin.app.CtsDeviceAdminDeactivatedReceiver;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.regex.Pattern;

/**
 * Tests for the standard way of activating a Device Admin: by starting system UI via an
 * {@link Intent} with {@link DevicePolicyManager#ACTION_ADD_DEVICE_ADMIN}. The test requires that
 * the {@code CtsDeviceAdmin.apk} be installed.
 */
@RunWith(AndroidJUnit4.class)
public class DeviceAdminActivationTest {

    @Rule
    public ActivityTestRule<CtsDeviceAdminActivationTestActivity> mActivityRule =
            new ActivityTestRule<>(CtsDeviceAdminActivationTestActivity.class);

    private static final String TAG = DeviceAdminActivationTest.class.getSimpleName();

    // IMPLEMENTATION NOTE: Because Device Admin activation requires the use of
    // Activity.startActivityForResult, this test creates an empty Activity which then invokes
    // startActivityForResult.

    private static final int REQUEST_CODE_ACTIVATE_ADMIN = 1;

    /**
     * Maximum duration of time (milliseconds) after which the effects of programmatic actions in
     * this test should have affected the UI.
     */
    private static final int UI_EFFECT_TIMEOUT_MILLIS = 5000;

    private boolean mHasFeature;

    private Instrumentation mInstrumentation;

    @Mock private OnActivityResultListener mMockOnActivityResultListener;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mActivityRule.getActivity().setOnActivityResultListener(mMockOnActivityResultListener);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mHasFeature = mInstrumentation.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_DEVICE_ADMIN);
    }

    @After
    public void tearDown() throws Exception {
        finishActivateDeviceAdminActivity();
    }

    @Test
    public void testActivateGoodReceiverDisplaysActivationUi() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateGoodReceiverDisplaysActivationUi");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminDeactivatedReceiver.class);
        assertWithTimeoutOnActivityResultNotInvoked();
        // The UI is up and running. Assert that dismissing the UI returns the corresponding result
        // to the test activity.
        finishActivateDeviceAdminActivity();
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
    }

    @Test
    public void testActivateBrokenReceiverFails() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateBrokenReceiverFails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver.class);
    }

    @Test
    public void testActivateBrokenReceiver2Fails() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver2Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver2.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver2.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver2.class);
    }

    @Test
    public void testActivateBrokenReceiver3Fails() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver3Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver3.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver3.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver3.class);
    }

    @Test
    public void testActivateBrokenReceiver4Fails() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver4Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver4.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver4.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver4.class);
    }

    @Test
    public void testActivateBrokenReceiver5Fails() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver5Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver5.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver5.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver5.class);
    }

    @Test
    public void testNonSystemAppCantBecomeAdmin_withKeyEvent() throws Exception {
        if (!mHasFeature) {
            Log.w(TAG, "Skipping testActivateGoodReceiverDisplaysActivationUi");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminDeactivatedReceiver.class);
        assertWithTimeoutOnActivityResultNotInvoked();

        // Find the "Activate this device admin app" button.
        UiDevice device = UiDevice.getInstance(mInstrumentation);
        Pattern patternToMatch = Pattern.compile("Activate.*", Pattern.CASE_INSENSITIVE);
        UiObject2 activateButton = device.findObject(By.text(patternToMatch));

        // If button doesn't exist, likely Device admin dialog is non-AOSP, skip the test.
        Assume.assumeTrue(activateButton != null);

        // inject KEYCODE_TAB and then KEYCODE_ENTER on "Activate" button should fail.
        injectUntrustedKeyEventToActivateDeviceAdmin(activateButton);
        assertWithTimeoutOnActivityResultNotInvoked();

        finishActivateDeviceAdminActivity();
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
    }

    /**
     * Inject KeyEvents to tap the "Activate this device admin app" button.
     * Sends the "tab" KeyEvent to selects the first button, then the "enter" KeyEvent presses it.
      */
    private void injectUntrustedKeyEventToActivateDeviceAdmin(UiObject2 activateButton) {
        sendUntrustedDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
        // Check if "Activate this device admin app" button was actually focused.
        // R.id.restricted_action's parent R.id.buttonPanel should be selected by pressing TAB.
        // If item is not focused, likely Device admin dialog is non-AOSP, skip the test.
        Assume.assumeTrue(
                activateButton.getParent() != null && activateButton.getParent().isFocused());

        sendUntrustedDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    /**
     * Creates a untrusted KeyEvent with KEYCODE_ENTER that appears to originate from a physical
     * keyboard.
     */
    private KeyEvent createUntrustedKeyEvent(int action, int keyCode) {
        // Note: FLAG_FROM_SYSTEM will be stripped by system.
        return new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                action,
                keyCode,
                0,
                0,
                0,
                0,
                0 /* flags: not setting FLAG_FROM_SYSTEM */,
                InputDevice.SOURCE_KEYBOARD
        );
    }

    void sendUntrustedDownUpKeyEvents(int keyCode) {
        UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        uiAutomation.injectInputEvent(
                createUntrustedKeyEvent(KeyEvent.ACTION_DOWN, keyCode), true /* sync */);
        uiAutomation.injectInputEvent(
                createUntrustedKeyEvent(KeyEvent.ACTION_UP, keyCode), true /* sync */);
    }

    private void startAddDeviceAdminActivityForResult(Class<?> receiverClass) {
        Activity activity = mActivityRule.getActivity();
        Intent intent = getAddDeviceAdminIntent(receiverClass);
        Log.d(TAG, "starting activity " + intent + " from " + activity + " on user "
                + activity.getUser());
        activity.startActivityForResult(intent, REQUEST_CODE_ACTIVATE_ADMIN);
    }

    private Intent getAddDeviceAdminIntent(Class<?> receiverClass) {
        ComponentName admin = new ComponentName(mInstrumentation.getTargetContext(),
                receiverClass);
        Log.v(TAG, "admin on " + DevicePolicyManager.EXTRA_DEVICE_ADMIN + " extra: " + admin);
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
    }

    private void assertWithTimeoutOnActivityResultNotInvoked() {
        SystemClock.sleep(UI_EFFECT_TIMEOUT_MILLIS);
        Mockito.verify(mMockOnActivityResultListener, Mockito.never())
                .onActivityResult(
                        Mockito.eq(REQUEST_CODE_ACTIVATE_ADMIN),
                        Mockito.anyInt(),
                        Mockito.nullable(Intent.class));
    }

    private void assertWithTimeoutOnActivityResultInvokedWithResultCode(int expectedResultCode) {
        ArgumentCaptor<Integer> resultCodeCaptor = ArgumentCaptor.forClass(int.class);
        Mockito.verify(mMockOnActivityResultListener, Mockito.timeout(UI_EFFECT_TIMEOUT_MILLIS))
                .onActivityResult(
                        Mockito.eq(REQUEST_CODE_ACTIVATE_ADMIN),
                        resultCodeCaptor.capture(),
                        Mockito.nullable(Intent.class));
        assertEquals(expectedResultCode, (int) resultCodeCaptor.getValue());
    }

    private void finishActivateDeviceAdminActivity() {
        mActivityRule.getActivity().finishActivity(REQUEST_CODE_ACTIVATE_ADMIN);
    }

    private void assertDeviceAdminDeactivated(Class<?> receiverClass) {
        Log.v(TAG, "assertDeviceAdminDeactivated(" + receiverClass + ")");
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) mActivityRule.getActivity().getSystemService(
                        Context.DEVICE_POLICY_SERVICE);
        assertFalse(devicePolicyManager.isAdminActive(
                new ComponentName(mInstrumentation.getTargetContext(), receiverClass)));
    }
}
