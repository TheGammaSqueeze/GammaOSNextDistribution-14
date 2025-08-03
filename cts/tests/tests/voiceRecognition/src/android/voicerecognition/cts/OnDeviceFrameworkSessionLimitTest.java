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

package android.voicerecognition.cts;

import static android.voicerecognition.cts.TestObjects.START_LISTENING_INTENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.getEventually;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.support.test.uiautomator.UiDevice;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;


// TODO(b/297249772): Re-enable once the limit is also re-enabled in
// SpeechRecognitionManagerServiceImpl.
// @RunWith(AndroidJUnit4.class)
public class OnDeviceFrameworkSessionLimitTest {

    private static final long ACTIVITY_INIT_WAIT_TIMEOUT_MS = 5000L;
    private static final int EXPECTED_RECOGNIZER_COUNT = 11;

    @Rule
    public ActivityTestRule<SpeechRecognitionActivity> mActivityTestRule =
            new ActivityTestRule<>(SpeechRecognitionActivity.class);

    private UiDevice mUiDevice;
    private SpeechRecognitionActivity mActivity;

    @Before
    public void setup() throws Exception {
        prepareDevice();
        getNonNullUiAutomation(10_000)
                .adoptShellPermissionIdentity("android.permission.MANAGE_SPEECH_RECOGNITION");
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivity = mActivityTestRule.getActivity();
        mActivity.init(true, null, EXPECTED_RECOGNIZER_COUNT);

        PollingCheck.waitFor(ACTIVITY_INIT_WAIT_TIMEOUT_MS,
                () -> mActivity.getRecognizerCount() == EXPECTED_RECOGNIZER_COUNT);
        assertWithMessage("Test activity initialization timed out.")
                .that(mActivity.getRecognizerCount()).isEqualTo(EXPECTED_RECOGNIZER_COUNT);

        for (int idx = 0; idx < EXPECTED_RECOGNIZER_COUNT; idx++) {
            mActivity.getRecognizerInfo(idx).mRecognizer.setTemporaryOnDeviceRecognizer(
                    ComponentName.unflattenFromString(
                            AbstractRecognitionServiceTest.CTS_VOICE_RECOGNITION_SERVICE));
        }
    }

    @After
    public void tearDown() throws Exception {
        for (int idx = 0; idx < EXPECTED_RECOGNIZER_COUNT; idx++) {
            mActivity.getRecognizerInfo(idx).mRecognizer.setTemporaryOnDeviceRecognizer(
                    ComponentName.unflattenFromString(
                            AbstractRecognitionServiceTest.CTS_VOICE_RECOGNITION_SERVICE));
        }

        getNonNullUiAutomation(10_000).dropShellPermissionIdentity();
    }

    // TODO(b/297249772): Re-enable once the limit is also re-enabled in
    //  SpeechRecognitionManagerServiceImpl.
    // @Test
    public void testFrameworkSessionLimit() {
        mUiDevice.waitForIdle();
        RecognitionSupportCallback supportCallback = new RecognitionSupportCallback() {
            @Override
            public void onSupportResult(@NonNull RecognitionSupport recognitionSupport) {
            }

            @Override
            public void onError(int error) {
                // this does not get called, instead the SpeechRecognizerListener does
            }
        };

        for (int idx = 0; idx < EXPECTED_RECOGNIZER_COUNT; idx++) {
            mActivity.checkRecognitionSupport(START_LISTENING_INTENT, supportCallback, idx);
        }

        PollingCheck.waitFor(5000, () -> {
            for (int idx = 0; idx < EXPECTED_RECOGNIZER_COUNT - 1; idx++) {
                // only the last recognizer should have errors
                if (mActivity.getRecognizerInfo(idx).mErrorCodesReceived.size() > 0) {
                    fail("Recognizer " + idx + " should not have errors");
                }
            }
            return mActivity.getRecognizerInfo(EXPECTED_RECOGNIZER_COUNT - 1)
                    .mErrorCodesReceived.size() == 1;
        });
    }

    private static void prepareDevice() {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    // android.app.Instrumentation#getUiAutomation may return null if UiAutomation fails to connect.
    // That getter should be retried until a non-null value is returned or it times out.
    @NonNull
    private UiAutomation getNonNullUiAutomation(int timeoutMillis) throws Exception {
        return getEventually(() -> {
            final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
            assertWithMessage("UiAutomation failed to connect").that(uiAutomation).isNotNull();
            return uiAutomation;
        }, timeoutMillis);
    }
}
