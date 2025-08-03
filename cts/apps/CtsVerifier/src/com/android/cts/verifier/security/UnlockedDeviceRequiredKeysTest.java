/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.annotation.IntDef;
import android.annotation.StringRes;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.biometrics.BiometricManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * This test verifies a key created with #setUnlockedDeviceRequired is not accessible when the
 * device is locked.
 * Requirements:
 *   Pin / pattern / password must be set, and if the device supports biometric unlock this must be
 *   set as well.
 * Test flow:
 *   1. Verify a pin / pattern / password has been configured; if the device supports biometric
 *   unlock verify this has been configured as well. If not direct the user to Settings -> Security.
 *   2. Prompt the user to lock the device and unlock with biometrics after 5 seconds.
 *   3. Once notification of the SCREEN_OFF has been received verify the device is locked, then
 *   verify the key cannot be accessed.
 *   4. When the device is unlocked verify the key is now accessible after the biometric unlock.
 *   5. Repeat steps 2-4, this time prompting the user to unlock using the device credentials.
 */
public class UnlockedDeviceRequiredKeysTest extends PassFailButtons.Activity {
    private static final String TAG = "UnlockedDeviceRequiredKeysTest";

    /**
     * This tag is used to display and, when necessary, remove the dialog to display the current
     * test status to the user.
     */
    private static final String FRAGMENT_TAG = "test_dialog";

    /** Alias for our key in the Android Key Store. */
    private static final String KEY_NAME = "my_lock_key";
    private static final byte[] SECRET_BYTE_ARRAY = new byte[]{1, 2, 3, 4, 5, 6};

    private Resources mResources;
    private HandlerThread mHandlerThread;
    private ScreenStateChangeReceiver mReceiver;

    private TestController mController;
    private TestDialogFragment mDialogFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sec_screen_lock_keys_main);
        getPassButton().setEnabled(false);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.sec_unlocked_device_required_keys_test,
                R.string.sec_unlocked_device_required_keys_test_info, -1);
        mResources = getApplicationContext().getResources();

        // There are no broadcasts / notifications when a device state changes between locked and
        // unlocked, but these two actions are most closely related to when the device should
        // transition to a new lock state. Since the lock state may not immediately change when
        // one of these broadcasts is sent use a HandlerThread to run off the UI thread to wait for
        // the device to complete the transition to the new lock state.
        mController = new TestController(this);
        mReceiver = new ScreenStateChangeReceiver(mController);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        mHandlerThread = new HandlerThread("receiver_thread");
        mHandlerThread.start();
        Handler handler = new Handler(mHandlerThread.getLooper());
        registerReceiver(mReceiver, intentFilter, null, handler);

        // The test button should only be available when the DialogFragment is not currently
        // displayed. When the button is clicked a new test is started (or the previous test
        // resumed if it did not run through to completion).
        mDialogFragment = TestDialogFragment.createDialogFragment(mController);
        Button startTestButton = findViewById(R.id.sec_start_test_button);
        startTestButton.setOnClickListener(view -> {
            mController.updateTestState(true);
            showDialog();
        });
    }

    /**
     * Shows the dialog with the next steps required by the user, or a completion status if the
     * test has finished.
     */
    public void showDialog() {
        // Remove any previously displayed fragments.
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment fragment = getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment != null) {
            transaction.remove(fragment);
        }
        transaction.addToBackStack(null);

        mDialogFragment = TestDialogFragment.createDialogFragment(mController);
        mDialogFragment.show(transaction, FRAGMENT_TAG);
    }

    /**
     * Updates the text within the {@link DialogFragment}'s {@link AlertDialog} with the current
     * state of the test and any required user actions.
     */
    private void updateDialogText() {
        Dialog dialog = mDialogFragment.getDialog();
        if (dialog instanceof AlertDialog) {
            ((AlertDialog) dialog).setMessage(mResources.getString(mController.getDialogMessage()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // When the app is resumed update the dialog text to ensure the user is directed to the
        // next required action.
        updateDialogText();
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * unlocked the device.
     */
    private static void createKey() {
        // Generate a key to decrypt payment credentials, tokens, etc.
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constraints (purposes) in the constructor of the Builder
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUnlockedDeviceRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException | KeyStoreException
                | CertificateException | IOException e) {
            throw new RuntimeException("Failed to create a symmetric key", e);
        }
    }

    /**
     * Tries to encrypt some data with the generated key in {@link #createKey} which
     * only works if the user has unlocked the device.
     *
     * @param shouldFail boolean indicating whether an exception is expected; this is intended to
     *                   prevent extra Logcat entries when the encrypt fails as expected
     */
    private static boolean tryEncrypt(boolean shouldFail) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);
            Cipher cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            // Try encrypting something, it will only work if the device is unlocked.
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            cipher.doFinal(SECRET_BYTE_ARRAY);
            return true;
        } catch (Exception e) {
            if (!shouldFail) {
                Log.w(TAG, "", e);
            }
            return false;
        }
    }

    /**
     * {@link BroadcastReceiver} intended to receive notification when the device's lock state
     * should be changing.
     *
     * <p>{@link Intent#ACTION_SCREEN_OFF} should be sent when the device's screen is shut off;
     * shortly after this event the device should be locked. Similarly {@link
     * Intent#ACTION_USER_PRESENT} should be sent when the device's screen is on, the device is
     * unlocked, and the user should be present at the device. This receiver forwards the
     * expected lock state change to the {@link TestController} to verify the device behaves as
     * expected depending on the current state of the test.
     */
    private static class ScreenStateChangeReceiver extends BroadcastReceiver {
        private TestController mController;

        /**
         * Private constructor that accepts the {@code controller} that will be used to drive the
         * test when lock state changes occur.
         */
        private ScreenStateChangeReceiver(TestController controller) {
            mController = controller;
        }

        /**
         * Receives one of the registered broadcasts and sends the expected device state to the
         * {@link TestController}.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    mController.deviceStateChanged(true);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    mController.deviceStateChanged(false);
                    break;
                default:
                    Log.w(TAG, "Ignoring unexpected broadcast: " + intent.getAction());
            }
        }
    }

    /**
     * Controls the flow of the test, verifying the prereqs are met and the device behaves as
     * expected based on the current state of the test.
     */
    private static class TestController {
        /**
         * Number of times to retry the lock state query after a device has started the transition
         * to a new lock state. This is intended to allow time for the device to enter the new state
         * as returned by {@link KeyguardManager#isDeviceLocked()}.
         */
        private static final int MAX_DEVICE_STATE_RETRIES = 20;
        /**
         * Number of times to retry the encryption after successful unlock of the device.
         */
        private static final int MAX_ENCRYPT_RETRIES = 10;
        /**
         * Time to sleep between lock state queries, and encryption tries. This will allow the
         * device up to one second for the new lock state change, and up to half a second for the
         * KeyStore authentication.
         */
        private static final long DEVICE_STATE_SLEEP_TIME = 50;
        /**
         * The test has been initialized and is waiting to verify that the device has met the
         * requirements for the test.
         */
        private static final int STATE_INITIALIZED = 0;
        /**
         * The test is waiting for the user to configure a pin / pattern / password and a biometric
         * unlock (where applicable).
         */
        private static final int STATE_AWAITING_LOCK_SCREEN_CONFIG = 1;
        /**
         * The test is waiting for the user to configure a biometric unlock, but a pin / pattern /
         * password is configured on the device.
         */
        private static final int STATE_AWAITING_BIOMETRIC_CONFIG = 2;
        /**
         * The test is waiting for the user to lock the device; after the lock the device should be
         * unlocked via biometrics.
         */
        private static final int STATE_AWAITING_BIOMETRIC_LOCK = 3;
        /**
         * The test successfully verified the key was not available in the lock state; waiting for
         * the user to unlock the device with biometrics to verify the key is available after the
         * unlock.
         */
        private static final int STATE_BIOMETRIC_UNLOCK_COMPLETE = 4;
        /**
         * The test is waiting for the user to lock the device; after the lock the device should be
         * unlocked via pin / pattern / password.
         */
        private static final int STATE_AWAITING_CREDENTIAL_LOCK = 5;
        /**
         * The test successfully verified the key was not available in the lock state; waiting for
         * the user to unlock the device with the pin / pattern / password to verify the key is
         * available after the unlock.
         */
        private static final int STATE_CREDENTIAL_UNLOCK_COMPLETE = 6;
        /**
         * The test failed since the key was available when the device was in the lock state.
         */
        private static final int STATE_FAILED_KEY_AVAILABLE_IN_LOCK_STATE = 7;
        /**
         * The test failed since the key was not available when the device was unlocked.
         */
        private static final int STATE_FAILED_KEY_NOT_AVAILABLE_IN_UNLOCKED_STATE = 8;
        /**
         * The test completed successfully.
         */
        private static final int STATE_TEST_SUCCESSFUL = 9;

        @IntDef(value = {
                STATE_INITIALIZED,
                STATE_AWAITING_LOCK_SCREEN_CONFIG,
                STATE_AWAITING_BIOMETRIC_CONFIG,
                STATE_AWAITING_BIOMETRIC_LOCK,
                STATE_BIOMETRIC_UNLOCK_COMPLETE,
                STATE_AWAITING_CREDENTIAL_LOCK,
                STATE_CREDENTIAL_UNLOCK_COMPLETE,
                STATE_FAILED_KEY_AVAILABLE_IN_LOCK_STATE,
                STATE_FAILED_KEY_NOT_AVAILABLE_IN_UNLOCKED_STATE,
                STATE_TEST_SUCCESSFUL,
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface TestState {
        }

        private BiometricManager mBiometricManager;
        private KeyguardManager mKeyguardManager;
        private @TestState int mTestState;
        private boolean mBiometricsSupported;
        private UnlockedDeviceRequiredKeysTest mActivity;

        private TestController(UnlockedDeviceRequiredKeysTest activity) {
            mBiometricManager = activity.getSystemService(BiometricManager.class);
            mKeyguardManager = activity.getSystemService(KeyguardManager.class);
            // Initially assume biometrics are supported; when checking if test requirements are
            // satisfied this can be set to false if the hardware is not available.
            mBiometricsSupported = true;
            mActivity = activity;
        }

        /**
         * Updates the current state of the test based on whether the test's requirements are met;
         * if {@code startNewTest} is true this will also start a new test if the previous test
         * reached a terminal state.
         */
        private void updateTestState(boolean startNewTest) {
            // If the test requirements are not met then return now as the verification process
            // will set the appropriate test state based on what needs to be configured.
            if (!verifyTestRequirements()) {
                return;
            }
            // If the test was just initialized, requirements just satisfied, or a terminal state
            // was reached then update the state to the first applicable test to be performed.
            @TestState int initialTestState = STATE_AWAITING_BIOMETRIC_LOCK;
            if (!mBiometricsSupported) {
                initialTestState = STATE_AWAITING_CREDENTIAL_LOCK;
            }
            switch (mTestState) {
                case STATE_INITIALIZED:
                case STATE_AWAITING_LOCK_SCREEN_CONFIG:
                case STATE_AWAITING_BIOMETRIC_CONFIG:
                    // When starting a new test recreate the key in case there are any problems
                    // accessing the key on previous attempts.
                    createKey();
                    mTestState = initialTestState;
                    break;
                case STATE_FAILED_KEY_AVAILABLE_IN_LOCK_STATE:
                case STATE_FAILED_KEY_NOT_AVAILABLE_IN_UNLOCKED_STATE:
                case STATE_TEST_SUCCESSFUL: {
                    if (startNewTest) {
                        mTestState = initialTestState;
                    }
                    break;
                }
            }
        }

        /**
         * Called when the device should be entering a new lock state; verifies if the test is in
         * a state where the new lock state is expected and if so runs the next portion of the test.
         *
         * @param enteringLockState boolean indicating whether the device should be entering a
         *                          locked state
         */
        private void deviceStateChanged(boolean enteringLockState) {
            // The tests should only be run once the device meets the requirements.
            if (verifyTestRequirements()) {
                // If the device is entering a lock state then run any of the awaiting lock tests.
                if (enteringLockState) {
                    if (mTestState == STATE_AWAITING_BIOMETRIC_LOCK
                            || mTestState == STATE_AWAITING_CREDENTIAL_LOCK) {
                        runDeviceTest(enteringLockState);
                    }
                } else {
                    // else the device is entering an unlocked state; if a previous lock state was
                    // verified then run the unlock test now.
                    if (mTestState == STATE_BIOMETRIC_UNLOCK_COMPLETE ||
                            mTestState == STATE_CREDENTIAL_UNLOCK_COMPLETE) {
                        runDeviceTest(enteringLockState);
                    }
                }
            }
            // Once the test has completed update the dialog's text to prompt the user for the next
            // required action or to show the completion of the test.
            mActivity.runOnUiThread(() -> mActivity.updateDialogText());
            // Once the test completes successfully enable the pass button.
            if (mTestState == STATE_TEST_SUCCESSFUL) {
                mActivity.runOnUiThread(() -> mActivity.getPassButton().setEnabled(true));
            }
        }

        /**
         * Runs the next portion of the test based on the device's lock state.
         *
         * <p>This method will first wait for the device to reach the expected lock state since it
         * can take some time from a lock state change event before the device is actually locked.
         * If the test fails the lock state is verified again in case the user modified the lock
         * state after the previous lock state was verified.
         *
         * @param enteringLockState boolean indicating whether the device should be entering a
         *                          locked state.
         */
        private void runDeviceTest(boolean enteringLockState) {
            // Wait for the device to reach the expected lock state before attempting the test.
            if (waitForDeviceState(enteringLockState)) {
                boolean encryptSuccessful =
                        verifyEncrypt(enteringLockState);
                // The test has failed if the encryption success is the same as the lock state; if
                // the device is being locked then the encryption should fail, and if the device is
                // being unlocked the encryption should be successful.
                if (encryptSuccessful == enteringLockState) {
                    // The test was expected to fail; run one more check to ensure the device
                    // is still in the expected lock state since it's possible the user locked /
                    // unlocked the device after its state was previously verified.
                    if (mKeyguardManager.isDeviceLocked() == enteringLockState) {
                        // The test failed; set the appropriate failed state.
                        if (enteringLockState) {
                            mTestState = STATE_FAILED_KEY_AVAILABLE_IN_LOCK_STATE;
                        } else {
                            mTestState = STATE_FAILED_KEY_NOT_AVAILABLE_IN_UNLOCKED_STATE;
                        }
                    } else {
                        Log.d(TAG, "Device state was changed while running test; need to"
                                + " retry the current test");
                    }
                } else {
                    // The current test passed; update the state to prompt the user for the next
                    // event.
                    if (enteringLockState) {
                        if (mTestState == STATE_AWAITING_BIOMETRIC_LOCK) {
                            mTestState = STATE_BIOMETRIC_UNLOCK_COMPLETE;
                        } else {
                            mTestState = STATE_CREDENTIAL_UNLOCK_COMPLETE;
                        }
                    } else {
                        if (mTestState == STATE_BIOMETRIC_UNLOCK_COMPLETE) {
                            mTestState = STATE_AWAITING_CREDENTIAL_LOCK;
                        } else {
                            mTestState = STATE_TEST_SUCCESSFUL;
                        }
                    }
                }
            } else {
                Log.w(TAG, "The device did not reach the "
                        + (enteringLockState ? "locked" : "unlocked")
                        + " state within the timeout period; this may need to be increased for"
                        + " future tests");
            }
        }

        /**
         * Verify encryption tries reaches the expected state. Returns a boolean value whether the
         * encryption was successful or not.
         * In case KeyStore is not fully processed the device lock state change, this will allow
         * half a second for retries, while returns immediately when reaches an expected state.
         *
         * @param shouldFail boolean indicating whether an expectation of the encryption is to fail
         *                   or to success
         */
        private boolean verifyEncrypt(boolean shouldFail) {
            int numRetries = 0;
            boolean encryptSuccess = false;
            while (numRetries < MAX_ENCRYPT_RETRIES) {
                numRetries++;

                encryptSuccess = tryEncrypt(shouldFail);
                if (shouldFail != encryptSuccess) {
                    Log.d(
                            TAG,
                            "The encryption got the expected result : "
                                    + (encryptSuccess ? "success" : "fail"));
                    return encryptSuccess;
                }

                try {
                    Thread.sleep(DEVICE_STATE_SLEEP_TIME);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Caught an Exception while sleeping: ", e);
                }
            }
            Log.e(
                    TAG,
                    "The encryption reached maximum retries, and got the unexpected result : "
                            + (encryptSuccess ? "success" : "fail"));
            return encryptSuccess;
        }

        /**
         * Waits for the lock state of the device as returned by {@link
         * KeyguardManager#isDeviceLocked()} to match the expected state, returning {@code true} if
         * the device reached the expected state within the timeout period.
         */
        private boolean waitForDeviceState(boolean enteringLockState) {
            int numRetries = 0;
            while (numRetries < MAX_DEVICE_STATE_RETRIES) {
                numRetries++;
                boolean lockState = mKeyguardManager.isDeviceLocked();
                if (lockState == enteringLockState) {
                    return true;
                }
                try {
                    Thread.sleep(DEVICE_STATE_SLEEP_TIME);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Caught an Exception while sleeping: ", e);
                }
            }
            return false;
        }

        /**
         * Verifies the device meets the requirements that a pin / pattern / password is set and
         * that a biometric unlock is configured where supported.
         *
         * <p>If the device does not meet the requirements for the test then the test state is
         * updated to indicate the unmet requirements to ensure the user is prompted to resolve
         * this.
         *
         * @return {@code true} if the device meets the requirements for the test
         */
        private boolean verifyTestRequirements() {
            boolean requirementsMet = true;
            // Check for biometric support at least once to ensure the user is not prompted to
            // configure a biometric unlock if not supported by the device.
            if (mBiometricsSupported) {
                int biometricResponse = mBiometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG);
                switch (biometricResponse) {
                    // If the device does not have the hardware to support biometrics then set the
                    // boolean to indicate the lack of support to prevent this check on future
                    // invocations.
                    case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                        mBiometricsSupported = false;
                        break;
                    // A success response indicates at least one biometric is registered for device
                    // unlock.
                    case BiometricManager.BIOMETRIC_SUCCESS:
                        break;
                    // A response of none enrolled indicates the device has the hardware to support
                    // biometrics, but a biometric unlock has not yet been configured.
                    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                        mTestState = STATE_AWAITING_BIOMETRIC_CONFIG;
                        requirementsMet = false;
                        break;
                    // Treat any other response as a biometric unlock still needs to be configured.
                    default:
                        mTestState = STATE_AWAITING_BIOMETRIC_CONFIG;
                        Log.w(TAG,
                                "An unexpected response was received when querying "
                                        + "BiometricManager#canAuthenticate: " + biometricResponse);
                        requirementsMet = false;
                        break;
                }
            }
            if (!mKeyguardManager.isDeviceSecure()) {
                mTestState = STATE_AWAITING_LOCK_SCREEN_CONFIG;
                requirementsMet = false;
            }
            return requirementsMet;
        }

        /**
         * Returns the dialog message that should be displayed to the user based on the current
         * state of the test.
         */
        private @StringRes int getDialogMessage() {
            // Update the test state in case it was recently initialized to ensure the next
            // required user action is displayed.
            updateTestState(false);
            switch (mTestState) {
                case STATE_AWAITING_LOCK_SCREEN_CONFIG:
                    if (mBiometricsSupported) {
                        return R.string.unlock_req_config_lock_screen_and_biometrics;
                    }
                    return R.string.unlock_req_config_lock_screen;
                case STATE_AWAITING_BIOMETRIC_CONFIG:
                    return R.string.unlock_req_config_biometrics;
                case STATE_AWAITING_BIOMETRIC_LOCK:
                    return R.string.unlock_req_biometric_lock;
                case STATE_BIOMETRIC_UNLOCK_COMPLETE:
                case STATE_CREDENTIAL_UNLOCK_COMPLETE:
                    return R.string.unlock_req_unlocked;
                case STATE_AWAITING_CREDENTIAL_LOCK:
                    return R.string.unlock_req_credential_lock;
                case STATE_TEST_SUCCESSFUL:
                    return R.string.unlock_req_successful;
                case STATE_FAILED_KEY_AVAILABLE_IN_LOCK_STATE:
                    return R.string.unlock_req_failed_key_available_when_locked;
                case STATE_FAILED_KEY_NOT_AVAILABLE_IN_UNLOCKED_STATE:
                    return R.string.unlock_req_failed_key_unavailable_when_unlocked;
                // While all states are accounted for a default return is required; report an
                // unknown state if this is reached to ensure the test is retried from a clean
                // state.
                default:
                    return R.string.unlock_req_unknown_state;
            }
        }
    }

    /**
     * {@link DialogFragment} used to display an AlertDialog to guide the user through the steps
     * required for the test. A {@code DialogFragment} is used since it automatically handles
     * configuration changes.
     */
    public static class TestDialogFragment extends DialogFragment {
        private TestController mController;

        /**
         * Creates a new {@link DialogFragment} that can obtain its text from the provided {@code
         * controller}.
         */
        private static TestDialogFragment createDialogFragment(TestController controller) {
            TestDialogFragment fragment = new TestDialogFragment();
            fragment.mController = controller;
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext()).setMessage(
                    mController.getDialogMessage()).create();
        }
    }
}
