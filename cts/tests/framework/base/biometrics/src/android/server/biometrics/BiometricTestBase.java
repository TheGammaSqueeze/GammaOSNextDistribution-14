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

package android.server.biometrics;

import static android.os.PowerManager.FULL_WAKE_LOCK;
import static android.server.biometrics.SensorStates.SensorState;
import static android.server.biometrics.SensorStates.UserState;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_IDLE;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_PENDING_CONFIRM;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_STARTED_UI_SHOWING;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_SHOWING_DEVICE_CREDENTIAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.PromptContentView;
import android.hardware.biometrics.PromptVerticalListContentView;
import android.hardware.biometrics.SensorProperties;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TestJournalProvider.TestJournal;
import android.server.wm.UiDeviceUtils;
import android.server.wm.WindowManagerState;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.server.biometrics.nano.BiometricServiceStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Base class containing useful functionality. Actual tests should be done in subclasses.
 */
abstract class BiometricTestBase extends ActivityManagerTestBase implements TestSessionList.Idler {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "BiometricTestBase";
    private static final String DUMPSYS_BIOMETRIC = Utils.DUMPSYS_BIOMETRIC;
    private static final String FLAG_CLEAR_SCHEDULER_LOG = " --clear-scheduler-buffer";

    // Negative-side (left) buttons
    protected static final String BUTTON_ID_NEGATIVE = "button_negative";
    protected static final String BUTTON_ID_USE_CREDENTIAL = "button_use_credential";

    // Positive-side (right) buttons
    protected static final String BUTTON_ID_CONFIRM = "button_confirm";
    protected static final String BUTTON_ID_TRY_AGAIN = "button_try_again";

    // Biometric text contents
    protected static final String LOGO_VIEW = "logo";
    protected static final String LOGO_DESCRIPTION_VIEW = "logo_description";
    protected static final String TITLE_VIEW = "title";
    protected static final String SUBTITLE_VIEW = "subtitle";
    protected static final String DESCRIPTION_VIEW = "description";
    protected static final String CONTENT_CONTAINER_VIEW = "customized_view_container";

    protected static final String VIEW_ID_PASSWORD_FIELD = "lockPassword";
    protected static final String KEY_ENTER = "key_enter";
    private static final int VIEW_WAIT_TIME_MS = 10000;
    @NonNull protected Instrumentation mInstrumentation;
    @NonNull protected BiometricManager mBiometricManager;
    @NonNull protected List<SensorProperties> mSensorProperties;
    @Nullable private PowerManager.WakeLock mWakeLock;
    @NonNull protected UiDevice mDevice;
    protected boolean mHasStrongBox;

    /**
     * Expose this functionality to our package, since ActivityManagerTestBase's is `protected`.
     * @param componentName
     */
    void launchActivity(@NonNull ComponentName componentName) {
        super.launchActivity(componentName);
    }

    @Override
    public void waitForIdleSensors() {
        try {
            Utils.waitForIdleService(this::getSensorStates);
        } catch (Exception e) {
            Log.e(TAG, "Exception when waiting for idle", e);
        }
    }

    /** @see Utils#getBiometricServiceCurrentState() */
    @NonNull
    protected BiometricServiceState getCurrentState() throws Exception {
        return Utils.getBiometricServiceCurrentState();
    }

    @NonNull
    protected BiometricServiceState getCurrentStateAndClearSchedulerLog() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_BIOMETRIC
                + FLAG_CLEAR_SCHEDULER_LOG);
        final BiometricServiceStateProto proto = BiometricServiceStateProto.parseFrom(dump);
        return BiometricServiceState.parseFrom(proto);
    }


    @Nullable
    protected String getCurrentPackageName() {
        return mDevice.getCurrentPackageName();
    }

    @Nullable
    protected UiObject2 findView(String id) {
        Log.d(TAG, "Finding view: " + id);
        return mDevice.findObject(By.res(mBiometricManager.getUiPackage(), id));
    }

    @Nullable
    protected UiObject2 waitForView(String id) {
        Log.d(TAG, "Waiting for view " + id);
        return mDevice.wait(Until.findObject(By.res(mBiometricManager.getUiPackage(), id)),
                VIEW_WAIT_TIME_MS);
    }

    protected void waitAndPressButton(String id) {
        final UiObject2 button = waitForView(id);
        assertNotNull(button);
        Log.d(TAG, "Waiting & clicking button: " + id);
        button.click();
    }
    protected void findAndPressButton(String id) {
        final UiObject2 button = findView(id);
        assertNotNull(button);
        Log.d(TAG, "Clicking button: " + id);
        button.click();
    }

    protected SensorStates getSensorStates() throws Exception {
        return getCurrentState().mSensorStates;
    }

    protected void waitForState(@BiometricServiceState.AuthSessionState int state)
            throws Exception {
        for (int i = 0; i < 20; i++) {
            final BiometricServiceState serviceState = getCurrentState();
            if (serviceState.mState != state) {
                Log.d(TAG, "Not in state " + state + " yet, current: " + serviceState.mState);
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for state to become: " + state);
    }

    private void waitForStateNotEqual(@BiometricServiceState.AuthSessionState int state)
            throws Exception {
        for (int i = 0; i < 20; i++) {
            final BiometricServiceState serviceState = getCurrentState();
            if (serviceState.mState == state) {
                Log.d(TAG, "Not out of state yet, current: " + serviceState.mState);
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for state to not equal: " + state);
    }

    private boolean anyEnrollmentsExist() throws Exception {
        final BiometricServiceState serviceState = getCurrentState();

        for (SensorState sensorState : serviceState.mSensorStates.sensorStates.values()) {
            for (UserState userState : sensorState.getUserStates().values()) {
                if (userState.numEnrolled != 0) {
                    Log.d(TAG, "Enrollments still exist: " + serviceState);
                    return true;
                }
            }
        }
        return false;
    }

    private void successfullyAuthenticate(@NonNull BiometricTestSession session, int userId)
            throws Exception {
        session.acceptAuthentication(userId);
        mInstrumentation.waitForIdleSync();
        waitForStateNotEqual(STATE_AUTH_STARTED_UI_SHOWING);
        BiometricServiceState state = getCurrentState();
        Log.d(TAG, "State after acceptAuthentication: " + state);
        if (state.mState == STATE_AUTH_PENDING_CONFIRM) {
            waitAndPressButton(BUTTON_ID_CONFIRM);
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_IDLE);
        } else {
            waitForState(STATE_AUTH_IDLE);
        }

        assertEquals("Failed to become idle after authenticating",
                STATE_AUTH_IDLE, getCurrentState().mState);
    }

    protected void successfullyAuthenticate(@NonNull BiometricTestSession session, int userId,
            TestJournal journal) throws Exception {
        successfullyAuthenticate(session, userId);
        mInstrumentation.waitForIdleSync();
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
    }

    protected void successfullyAuthenticate(@NonNull BiometricTestSession session, int userId,
            BiometricPrompt.AuthenticationCallback callback) throws Exception {
        successfullyAuthenticate(session, userId);
        ArgumentCaptor<BiometricPrompt.AuthenticationResult> resultCaptor =
                ArgumentCaptor.forClass(BiometricPrompt.AuthenticationResult.class);
        verify(callback).onAuthenticationSucceeded(resultCaptor.capture());
        assertEquals("Must be TYPE_BIOMETRIC",
                BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC,
                resultCaptor.getValue().getAuthenticationType());
    }

    protected void successfullyEnterCredential() throws Exception {
        waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.areAllSensorsIdle());
        assertEquals(state.toString(), STATE_SHOWING_DEVICE_CREDENTIAL, state.mState);

        // Wait for any animations to complete. Ideally, this should be reflected in
        // STATE_SHOWING_DEVICE_CREDENTIAL, but SysUI and BiometricService are different processes
        // so we'd need to add some additional plumbing. We can improve this in the future.
        // TODO(b/152240892)
        Thread.sleep(1000);

        // Enter credential. AuthSession done, authentication callback received
        final UiObject2 passwordField = findView(VIEW_ID_PASSWORD_FIELD);
        Log.d(TAG, "Focusing, entering, submitting credential");
        passwordField.click();
        passwordField.setText(LOCK_CREDENTIAL);
        if (mInstrumentation.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE)) {
            final UiObject2 enterButton = findView(KEY_ENTER);
            enterButton.click();
        } else {
            mDevice.pressEnter();
        }
        waitForState(STATE_AUTH_IDLE);

        state = getCurrentState();
        assertEquals(state.toString(), STATE_AUTH_IDLE, state.mState);
    }

    protected void cancelAuthentication(@NonNull CancellationSignal cancel) throws Exception {
        cancel.cancel();
        mInstrumentation.waitForIdleSync();
        waitForState(STATE_AUTH_IDLE);

        //TODO(b/152240892): Currently BiometricService does not get a signal from SystemUI
        //  when the dialog finishes animating away.
        Thread.sleep(1000);

        BiometricServiceState state = getCurrentState();
        assertEquals("Not idle after requesting cancellation", state.mState, STATE_AUTH_IDLE);
    }

    protected void waitForAllUnenrolled() throws Exception {
        for (int i = 0; i < 20; i++) {
            if (anyEnrollmentsExist()) {
                Log.d(TAG, "Enrollments still exist..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        fail("Some sensors still have enrollments. State: " + getCurrentState());
    }

    /**
     * Shows a BiometricPrompt that specifies {@link Authenticators#DEVICE_CREDENTIAL}.
     */
    protected void showCredentialOnlyBiometricPrompt(
            @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull CancellationSignal cancellationSignal,
            boolean shouldShow) throws Exception {
        showCredentialOnlyBiometricPromptWithContents(callback, cancellationSignal, shouldShow,
                "Title", "Subtitle", "Description", null /* contentView */);
    }

    /**
     * Shows a BiometricPrompt that specifies {@link Authenticators#DEVICE_CREDENTIAL}
     * and the specified contents.
     */
    protected void showCredentialOnlyBiometricPromptWithContents(
            @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull CancellationSignal cancellationSignal, boolean shouldShow,
            @NonNull String title, @NonNull String subtitle,
            @NonNull String description, @Nullable PromptContentView contentView) throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricPrompt prompt = new BiometricPrompt.Builder(mContext)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setContentView(contentView)
                .setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)
                .setAllowBackgroundAuthentication(true)
                .build();

        prompt.authenticate(cancellationSignal, executor, callback);

        waitForCredentialIdle(shouldShow, contentView == null);
    }

    /**
     * SHows a BiometricPrompt that sets
     * {@link BiometricPrompt.Builder#setDeviceCredentialAllowed(boolean)} to true.
     */
    protected void showDeviceCredentialAllowedBiometricPrompt(
            @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull CancellationSignal cancellationSignal,
            boolean shouldShow) throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricPrompt prompt = new BiometricPrompt.Builder(mContext)
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setDescription("Description")
                .setDeviceCredentialAllowed(true)
                .setAllowBackgroundAuthentication(true)
                .build();

        prompt.authenticate(cancellationSignal, executor, callback);

        waitForCredentialIdle(shouldShow, true /* isContentViewNull */);
    }

    protected BiometricPrompt showDefaultBiometricPrompt(int sensorId,
            @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull CancellationSignal cancellationSignal) throws Exception {
        return showDefaultBiometricPromptWithLogo(sensorId, callback, cancellationSignal,
                -1 /* logoRes */, null /* logoBitmap */, null /* logoDescription */);
    }

    protected BiometricPrompt showDefaultBiometricPromptWithLogo(int sensorId,
            @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull CancellationSignal cancellationSignal, int logoRes, Bitmap logoBitmap,
            String logoDescription) throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricPrompt prompt = new BiometricPrompt.Builder(mContext)
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setDescription("Description")
                .setConfirmationRequired(true)
                .setNegativeButton("Negative Button", executor, (dialog, which) -> {
                    Log.d(TAG, "Negative button pressed");
                })
                .setAllowBackgroundAuthentication(true)
                .setAllowedSensorIds(new ArrayList<>(Collections.singletonList(sensorId)))
                .setLogoRes(logoRes)
                .setLogoBitmap(logoBitmap)
                .setLogoDescription(logoDescription)
                .build();
        prompt.authenticate(cancellationSignal, executor, callback);

        waitForState(STATE_AUTH_STARTED_UI_SHOWING);
        return prompt;
    }

    /**
     * Shows the default BiometricPrompt (sensors meeting BIOMETRIC_WEAK) with a negative button,
     * but does not complete authentication. In other words, the dialog will stay on the screen.
     */
    protected void showDefaultBiometricPromptWithContents(int sensorId, int userId,
            boolean requireConfirmation, @NonNull BiometricPrompt.AuthenticationCallback callback,
            @NonNull String title, @NonNull String subtitle, @NonNull String description,
            @Nullable PromptContentView contentView, @NonNull String negativeButtonText)
            throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricPrompt prompt = new BiometricPrompt.Builder(mContext)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setContentView(contentView)
                .setConfirmationRequired(requireConfirmation)
                .setNegativeButton(negativeButtonText, executor, (dialog, which) -> {
                    Log.d(TAG, "Negative button pressed");
                })
                .setAllowBackgroundAuthentication(true)
                .setAllowedSensorIds(new ArrayList<>(Collections.singletonList(sensorId)))
                .build();
        prompt.authenticate(new CancellationSignal(), executor, callback);

        waitForState(STATE_AUTH_STARTED_UI_SHOWING);
    }

    /**
     * Shows the default BiometricPrompt (sensors meeting BIOMETRIC_WEAK) with a negative button,
     * and fakes successful authentication via TestApis.
     */
    protected void showDefaultBiometricPromptAndAuth(@NonNull BiometricTestSession session,
            int sensorId, int userId) throws Exception {
        BiometricPrompt.AuthenticationCallback callback = mock(
                BiometricPrompt.AuthenticationCallback.class);
        showDefaultBiometricPromptWithContents(sensorId, userId, false /* requireConfirmation */,
                callback, "Title", "Subtitle", "Description",
                new PromptVerticalListContentView.Builder().build(), "Negative Button");
        successfullyAuthenticate(session, userId);
    }

    protected BiometricPrompt showBiometricPromptWithAuthenticators(int authenticators) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final BiometricPrompt.Builder promptBuilder = new BiometricPrompt.Builder(mContext)
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setDescription("Description")
                .setAllowBackgroundAuthentication(true)
                .setAllowedAuthenticators(authenticators);
        if ((authenticators & Authenticators.DEVICE_CREDENTIAL) == 0) {
            promptBuilder.setNegativeButton("Negative Button", executor, (dialog, which) -> {
                Log.d(TAG, "Negative button pressed");
            });
        }

        final BiometricPrompt prompt = promptBuilder.build();
        prompt.authenticate(new CancellationSignal(), executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Log.d(TAG, "onAuthenticationError: " + errorCode);
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        Log.d(TAG, "onAuthenticationSucceeded");
                    }
                });
        return prompt;
    }

    protected void launchActivityAndWaitForResumed(@NonNull ActivitySession activitySession) {
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();
    }

    protected void closeActivity(@NonNull ActivitySession activitySession) throws Exception {
        activitySession.close();
        mInstrumentation.waitForIdleSync();
    }

    protected int getCurrentStrength(int sensorId) throws Exception {
        final BiometricServiceState serviceState = getCurrentState();
        return serviceState.mSensorStates.sensorStates.get(sensorId).getCurrentStrength();
    }

    protected List<Integer> getSensorsOfTargetStrength(int targetStrength) {
        final List<Integer> sensors = new ArrayList<>();
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == targetStrength) {
                sensors.add(prop.getSensorId());
            }
        }
        Log.d(TAG, "getSensorsOfTargetStrength: num of target sensors=" + sensors.size());
        return sensors;
    }

    @NonNull
    protected static BiometricCallbackHelper.State getCallbackState(@NonNull TestJournal journal) {
        Utils.waitFor("Waiting for authentication callback",
                () -> journal.extras.containsKey(BiometricCallbackHelper.KEY),
                (lastResult) -> fail("authentication callback never received - died waiting"));

        final Bundle bundle = journal.extras.getBundle(BiometricCallbackHelper.KEY);
        final BiometricCallbackHelper.State state =
                BiometricCallbackHelper.State.fromBundle(bundle);

        // Clear the extras since we want to wait for the journal to sync any new info the next
        // time it's read
        journal.extras.clear();

        return state;
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = getInstrumentation();
        mBiometricManager = mInstrumentation.getContext().getSystemService(BiometricManager.class);

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mSensorProperties = mBiometricManager.getSensorProperties();

        assumeTrue(mInstrumentation.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SECURE_LOCK_SCREEN));

        mHasStrongBox = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_STRONGBOX_KEYSTORE);

        // Keep the screen on for the duration of each test, since BiometricPrompt goes away
        // when screen turns off.
        final PowerManager pm = mInstrumentation.getContext().getSystemService(PowerManager.class);
        mWakeLock = pm.newWakeLock(FULL_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        // Turn screen on and dismiss keyguard
        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    @After
    public void cleanup() {
        mInstrumentation.waitForIdleSync();

        // Authentication lifecycle is done
        waitForIdleSensors();

        if (mWakeLock != null) {
            mWakeLock.release();
        }
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    protected void enrollForSensor(@NonNull BiometricTestSession session, int sensorId)
            throws Exception {
        Log.d(TAG, "Enrolling for sensor: " + sensorId);
        final int userId = Utils.getUserId();

        session.startEnroll(userId);
        mInstrumentation.waitForIdleSync();
        Utils.waitForBusySensor(sensorId, this::getSensorStates);

        //Wait for enrollment operation in biometrics sensor to be complete before
        //retrieving enrollment results. The operation takes a little time especically
        //on Cutterfish where multiple biometric operations must be completed during
        //the enrollent
        //TODO(b/217275524)
        Thread.sleep(200);
        session.finishEnroll(userId);
        mInstrumentation.waitForIdleSync();
        Utils.waitForIdleService(this::getSensorStates);

        final BiometricServiceState state = getCurrentState();
        assertEquals("Sensor: " + sensorId + " should have exactly one enrollment",
                1, state.mSensorStates.sensorStates
                        .get(sensorId).getUserStates().get(userId).numEnrolled);
    }

    protected void waitForCredentialIdle(boolean shouldShow, boolean isContentViewNull)
            throws Exception {
        boolean shouldShowBpWithoutIconForCredential = Utils.shouldShowBpWithoutIconForCredential(
                isContentViewNull, false /*isBiometricAllowed*/);
        mInstrumentation.waitForIdleSync();

        // Wait for any animations to complete. Ideally, this should be reflected in
        // STATE_SHOWING_DEVICE_CREDENTIAL, but SysUI and BiometricService are different processes
        // so we'd need to add some additional plumbing. We can improve this in the future.
        // TODO(b/152240892)
        Thread.sleep(1000);

        if (shouldShow) {
            if (shouldShowBpWithoutIconForCredential) {
                waitForState(STATE_AUTH_STARTED_UI_SHOWING);
                findAndPressButton(BUTTON_ID_USE_CREDENTIAL);
                waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
            }
            waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
            BiometricServiceState state = getCurrentState();
            assertEquals(state.toString(), STATE_SHOWING_DEVICE_CREDENTIAL, state.mState);
        } else {
            Utils.waitForIdleService(this::getSensorStates);
        }
    }
}
