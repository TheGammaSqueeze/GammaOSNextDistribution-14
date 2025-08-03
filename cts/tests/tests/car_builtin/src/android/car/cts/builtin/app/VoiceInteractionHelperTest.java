/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.app;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.UiAutomation;
import android.car.builtin.app.VoiceInteractionHelper;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public final class VoiceInteractionHelperTest {

    private static final String PERMISSION_ACCESS_VOICE_INTERACTION_SERVICE =
            "android.permission.ACCESS_VOICE_INTERACTION_SERVICE";

    @Test
    public void testSetEnabled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(PERMISSION_ACCESS_VOICE_INTERACTION_SERVICE);

        try {
            VoiceInteractionHelper.setEnabled(/* enabled= */ true);
            assertWithMessage("VoiceInteraction enabled")
                    .that(isVoiceInteractionDisabledFromDump()).isFalse();

            VoiceInteractionHelper.setEnabled(/* enabled= */ false);
            assertWithMessage("VoiceInteraction enabled")
                    .that(isVoiceInteractionDisabledFromDump()).isTrue();

            VoiceInteractionHelper.setEnabled(/* enabled= */ true);
            assertWithMessage("VoiceInteraction enabled")
                    .that(isVoiceInteractionDisabledFromDump()).isFalse();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    // TODO(b/218551697): add a TestApi to VoiceInteractionService which returns
    // mTemporarilyDisabled of VoiceInteractionManagerService.
    private boolean isVoiceInteractionDisabledFromDump() {
        String dump = ShellUtils.runShellCommand("dumpsys voiceinteraction");
        Matcher matchDisabled = Pattern.compile("mTemporarilyDisabled: *(true|false)")
                .matcher(dump);
        assertWithMessage("inclusion of mTemporarilyDisabled in dump").that(matchDisabled.find())
                .isTrue();
        return matchDisabled.group(1).equals("true");
    }
}
