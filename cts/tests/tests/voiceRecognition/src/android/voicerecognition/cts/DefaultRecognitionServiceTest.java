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
 * limitations under the License
 */

package android.voicerecognition.cts;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.provider.Settings;
import android.speech.SpeechRecognizer;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SettingsStateChangerRule;

import org.junit.BeforeClass;
import org.junit.Rule;

/** Recognition service tests for a default speech recognition service. */
public final class DefaultRecognitionServiceTest extends AbstractRecognitionServiceTest {

    // same as Settings.Secure.VOICE_RECOGNITION_SERVICE
    final String VOICE_RECOGNITION_SERVICE = "voice_recognition_service";

    // UiAutomation connection timeout in milliseconds.
    private static final int UIAUTOMATION_CONNECTION_TIMEOUT_MILLIS = 10000;

    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String mOriginalVoiceRecognizer = Settings.Secure.getString(
            mContext.getContentResolver(), VOICE_RECOGNITION_SERVICE);

    // Check that UiAutomation is properly connected, SettingsStateChangerRule will fail otherwise.
    @BeforeClass
    public static void checkUiAutomationNonNull() {
        eventually(() -> assertWithMessage("UiAutomation failed to connect")
                .that(InstrumentationRegistry.getInstrumentation().getUiAutomation()).isNotNull(),
                UIAUTOMATION_CONNECTION_TIMEOUT_MILLIS);
    }

    @Rule
    public final SettingsStateChangerRule mVoiceRecognitionServiceSetterRule =
            new SettingsStateChangerRule(mContext, VOICE_RECOGNITION_SERVICE,
                    mOriginalVoiceRecognizer);

    @Override
    protected void setCurrentRecognizer(SpeechRecognizer recognizer, String component) {
        runWithShellPermissionIdentity(
                () -> Settings.Secure.putString(mContext.getContentResolver(),
                        VOICE_RECOGNITION_SERVICE, component));
    }

    @Override
    boolean isOnDeviceTest() {
        return false;
    }

    @Override
    String customRecognizer() {
        // We will use the default one (specified in secure settings).
        return null;
    }
}
