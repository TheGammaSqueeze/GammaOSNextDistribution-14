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

import static android.Manifest.permission.MANAGE_CONTENT_SUGGESTIONS;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONTENT_CAPTURE;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONTENT_SUGGESTIONS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.contentsuggestions.ContentSuggestionsManager;
import android.content.Context;
import android.view.contentcapture.ContentCaptureManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasTestContentSuggestionsService;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowContentCapture;
import com.android.bedstead.harrier.policies.DisallowContentSuggestions;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ContentTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final ContentSuggestionsManager sContentSuggestionsManager
            = sContext.getSystemService(ContentSuggestionsManager.class);

    @CannotSetPolicyTest(policy = DisallowContentCapture.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_CAPTURE")
    public void setUserRestriction_disallowContentCapture_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONTENT_CAPTURE));
    }

    @PolicyAppliesTest(policy = DisallowContentCapture.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_CAPTURE")
    @Test
    public void setUserRestriction_disallowContentCapture_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_CAPTURE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONTENT_CAPTURE))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_CAPTURE);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowContentCapture.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_CAPTURE")
    public void setUserRestriction_disallowContentCapture_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_CAPTURE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONTENT_CAPTURE))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_CAPTURE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONTENT_CAPTURE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_CAPTURE")
    @RequireSystemServiceAvailable(ContentCaptureManager.class)
    @Ignore // TODO(278998574): Restore and confirm expected behaviour
    public void disallowContentCaptureIsNotSet_canGetContentCaptureManager() throws Exception {
        assertThat(sContext.getSystemService(ContentCaptureManager.class)).isNotNull();
    }

    @EnsureHasUserRestriction(DISALLOW_CONTENT_CAPTURE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_CAPTURE")
    @RequireSystemServiceAvailable(ContentCaptureManager.class)
    @Ignore // TODO(278998574): Restore and confirm expected behaviour
    public void disallowContentCaptureIsSet_cannotGetContentCaptureManager() throws Exception {
        assertThat(sContext.getSystemService(ContentCaptureManager.class)).isNull();
    }

    @CannotSetPolicyTest(policy = DisallowContentSuggestions.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTNET_SUGGESTIONS")
    public void setUserRestriction_disallowContentSuggestions_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONTENT_SUGGESTIONS));
    }

    @PolicyAppliesTest(policy = DisallowContentSuggestions.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_SUGGESTIONS")
    public void setUserRestriction_disallowContentSuggestions_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_SUGGESTIONS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONTENT_SUGGESTIONS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_SUGGESTIONS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowContentSuggestions.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_SUGGESTIONS")
    public void setUserRestriction_disallowContentSuggestions_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_SUGGESTIONS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONTENT_SUGGESTIONS))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONTENT_SUGGESTIONS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONTENT_SUGGESTIONS)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_SUGGESTIONS")
    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureHasTestContentSuggestionsService
    @EnsureHasPermission(MANAGE_CONTENT_SUGGESTIONS)
    public void isEnabled_disallowContentSuggestionsIsNotSet_isTrue() throws Exception {
        assertThat(sContentSuggestionsManager.isEnabled()).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_CONTENT_SUGGESTIONS)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONTENT_SUGGESTIONS")
    @RequireSystemServiceAvailable(ContentSuggestionsManager.class)
    @EnsureHasTestContentSuggestionsService
    @EnsureHasPermission(MANAGE_CONTENT_SUGGESTIONS)
    public void isEnabled_disallowContentSuggestionsIsSet_isFalse() throws Exception {
        assertThat(sContentSuggestionsManager.isEnabled()).isFalse();
    }
}
