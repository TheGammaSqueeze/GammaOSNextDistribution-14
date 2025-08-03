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
 * limitations under the License
 */

package android.voicerecognition.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.speech.SpeechRecognizer;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.TruthJUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SpeechRecognizerAvailabilityTest {

    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private ActivityScenario<SpeechRecognitionActivity> mScenario;

    @Rule
    public ActivityScenarioRule<SpeechRecognitionActivity> mScenarioRule =
            new ActivityScenarioRule<>(SpeechRecognitionActivity.class);

    @Before
    public void setup() {
        prepareDevice();
        mScenario = mScenarioRule.getScenario();
    }

    private void prepareDevice() {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    @Test
    public void testOnDeviceRecognizer_canInstantiateWhenTrue() {
        mScenario.onActivity(
                activity -> {
                    TruthJUnit.assume()
                            .that(SpeechRecognizer.isOnDeviceRecognitionAvailable(activity))
                            .isTrue();

                    SpeechRecognizer recognizer =
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
                    assertThat(recognizer).isNotNull();
                });
    }

    @Test
    public void testOnDeviceRecognizer_cannotInstantiateWhenFalse() {
        mScenario.onActivity(
                activity -> {
                    TruthJUnit.assume()
                            .that(SpeechRecognizer.isOnDeviceRecognitionAvailable(activity))
                            .isFalse();

                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> SpeechRecognizer.createOnDeviceSpeechRecognizer(activity));
                });
    }
}
