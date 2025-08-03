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

import static android.app.admin.DevicePolicyManager.EXTRA_RESTRICTION;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADJUST_VOLUME;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DisallowAdjustVolume;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class VolumeTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_ADJUST_VOLUME"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowAdjustVolume.class)
    @EnsureHasUserRestriction(DISALLOW_ADJUST_VOLUME)
    public void createAdminSupportIntent_disallowAdjustVolumeRestriction_createsIntent() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_ADJUST_VOLUME);

        assertThat(intent).isNotNull();
        assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(DISALLOW_ADJUST_VOLUME);
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_ADJUST_VOLUME"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowAdjustVolume.class)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_ADJUST_VOLUME)
    public void createAdminSupportIntent_allowAdjustVolumeRestriction_doesNotCreate() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_ADJUST_VOLUME);

        assertThat(intent).isNull();
    }
}
