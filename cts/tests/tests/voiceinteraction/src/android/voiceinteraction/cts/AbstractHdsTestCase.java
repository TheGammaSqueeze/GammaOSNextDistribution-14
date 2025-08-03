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

package android.voiceinteraction.cts;

import android.permission.flags.Flags;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.runner.RunWith;


/**
 * Base class for all test cases that involve the trusted hotword detection service.
 */
@RunWith(AndroidJUnit4.class)
abstract class AbstractHdsTestCase {

    public static final String RECEIVE_SANDBOX_TRIGGER_AUDIO_OP_STR =
            "android:receive_sandbox_trigger_audio";

    public static final String RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA_OP_STR =
            "android:RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA";

    boolean mVoiceActivationPermissionEnabled;

    @Before
    public void setVoiceActivationPermissionEnabled() {
        DeviceFlagsValueProvider provider = new DeviceFlagsValueProvider();
        provider.setUp();
        mVoiceActivationPermissionEnabled =
                provider.getBoolean(Flags.FLAG_VOICE_ACTIVATION_PERMISSION_APIS);
        provider.tearDownBeforeTest();
    }
}
