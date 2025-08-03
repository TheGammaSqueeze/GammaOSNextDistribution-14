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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.getEventually;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

/** Recognition service tests for a default speech recognition service. */
public final class OnDeviceRecognitionServiceTest extends AbstractRecognitionServiceTest {
    private static final String TAG = OnDeviceRecognitionServiceTest.class.getSimpleName();

    // UiAutomation connection timeout in milliseconds.
    private static final int UIAUTOMATION_CONNECTION_TIMEOUT_MILLIS = 10000;

    private final List<SpeechRecognizer> mRecognizers = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        getNonNullUiAutomation(UIAUTOMATION_CONNECTION_TIMEOUT_MILLIS)
                .adoptShellPermissionIdentity("android.permission.MANAGE_SPEECH_RECOGNITION");
    }

    @After
    public void tearDown() throws Exception {
        if (mRecognizers != null) {
            for (SpeechRecognizer recognizer : mRecognizers) {
                if (recognizer != null) {
                    recognizer.setTemporaryOnDeviceRecognizer(null);
                }
            }
            mRecognizers.clear();
        }

        try {
            getNonNullUiAutomation(UIAUTOMATION_CONNECTION_TIMEOUT_MILLIS)
                    .dropShellPermissionIdentity();
        } catch (Exception e) {
            Log.w(TAG, "Tear down failed.", e);
        }
    }

    @Override
    protected void setCurrentRecognizer(SpeechRecognizer recognizer, String component) {
        Log.i(TAG, "Setting recognizer to " + component);
        recognizer.setTemporaryOnDeviceRecognizer(ComponentName.unflattenFromString(component));
        mRecognizers.add(recognizer);
    }

    @Override
    boolean isOnDeviceTest() {
        return true;
    }

    @Override
    String customRecognizer() {
        // We will use the default one (specified in config).
        return null;
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
