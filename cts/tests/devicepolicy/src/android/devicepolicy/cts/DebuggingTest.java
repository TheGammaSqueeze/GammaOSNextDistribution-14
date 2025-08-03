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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_DEBUGGING_FEATURES;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.DisallowDebuggingFeatures;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class DebuggingTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Ignore
    @CannotSetPolicyTest(policy = DisallowDebuggingFeatures.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DEBUGGING_FEATURES")
    public void setUserRestriction_disallowDebuggingFeatures_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_DEBUGGING_FEATURES));
    }

//    @PolicyAppliesTest(policy = DisallowDebuggingFeatures.class)
//    @Postsubmit(reason = "new test")
//    @ApiTest(apis = "android.os.UserManager#DISALLOW_DEBUGGING_FEATURES")
//    @Ignore // We can't add positive tests because the adb connection will break and the test will crash
//    public void setUserRestriction_disallowDebuggingFeatures_isSet() {
//        try {
//            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
//                    sDeviceState.dpc().componentName(), DISALLOW_DEBUGGING_FEATURES);
//
//            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DEBUGGING_FEATURES))
//                    .isTrue();
//        } finally {
//            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
//                    sDeviceState.dpc().componentName(), DISALLOW_DEBUGGING_FEATURES);
//        }
//    }
//
//    @PolicyDoesNotApplyTest(policy = DisallowDebuggingFeatures.class)
//    @Postsubmit(reason = "new test")
//    @ApiTest(apis = "android.os.UserManager#DISALLOW_DEBUGGING_FEATURES")
//    @Ignore // We can't add positive tests because the adb connection will break and the test will crash
//    public void setUserRestriction_disallowDebuggingFeatures_isNotSet() {
//        try {
//            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
//                    sDeviceState.dpc().componentName(), DISALLOW_DEBUGGING_FEATURES);
//
//            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DEBUGGING_FEATURES))
//                    .isFalse();
//        } finally {
//
//            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
//                    sDeviceState.dpc().componentName(), DISALLOW_DEBUGGING_FEATURES);
//        }
//    }
//
//    @EnsureDoesNotHaveUserRestriction(DISALLOW_DEBUGGING_FEATURES)
//    @Test
//    @Postsubmit(reason = "new test")
//    @Interactive
//    @ApiTest(apis = "android.os.UserManager#DISALLOW_DEBUGGING_FEATURES")
//    public void disallowDebuggingFeaturesIsNotSet_todo() throws Exception {
//        // TODO: Add Test
//    }
//
////    @EnsureHasUserRestriction(DISALLOW_DEBUGGING_FEATURES)
//    @Test
//    @Postsubmit(reason = "new test")
//    @Interactive
//    @ApiTest(apis = "android.os.UserManager#DISALLOW_DEBUGGING_FEATURES")
//    @Ignore // We can't add positive tests because the adb connection will break and the test will crash
//    public void disallowDebuggingFeaturesIsSet_todo() throws Exception {
//        // TODO: Add Test
//    }
}
