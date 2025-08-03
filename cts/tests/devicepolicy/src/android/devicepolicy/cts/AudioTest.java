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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.MODIFY_AUDIO_SETTINGS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_UNMUTE_MICROPHONE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.media.AudioManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowUnmuteMicrophone;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AudioTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final AudioManager sAudioManager = TestApis.context().instrumentedContext()
            .getSystemService(AudioManager.class);

    @CannotSetPolicyTest(policy = DisallowUnmuteMicrophone.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_UNMUTE_MICROPHONE));
    }

    @PolicyAppliesTest(policy = DisallowUnmuteMicrophone.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_UNMUTE_MICROPHONE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_UNMUTE_MICROPHONE))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_UNMUTE_MICROPHONE);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowUnmuteMicrophone.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    public void setUserRestriction_disallowUnmuteMicrophone_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_UNMUTE_MICROPHONE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_UNMUTE_MICROPHONE))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_UNMUTE_MICROPHONE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_UNMUTE_MICROPHONE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    @EnsureHasPermission(MODIFY_AUDIO_SETTINGS)
    public void disallowUnmuteMicrophoneIsNotSet_canUnmuteMicrophone() throws Exception {
        sAudioManager.setMicrophoneMute(true);

        sAudioManager.setMicrophoneMute(false);

        Poll.forValue("isMicrophoneMute", sAudioManager::isMicrophoneMute)
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @EnsureHasUserRestriction(DISALLOW_UNMUTE_MICROPHONE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE")
    @EnsureHasPermission(MODIFY_AUDIO_SETTINGS)
    public void disallowUnmuteMicrophoneIsSet_canNotUnmuteMicrophone() throws Exception {
        sAudioManager.setMicrophoneMute(true);

        sAudioManager.setMicrophoneMute(false);

        Poll.forValue("isMicrophoneMute", sAudioManager::isMicrophoneMute)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    // TODO: Figure out where policy transparency for this control appears and add a test
}
