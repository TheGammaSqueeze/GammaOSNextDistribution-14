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
 * limitations under the License.
 */

package android.devicepolicy.cts;

import static android.os.UserManager.USER_OPERATION_ERROR_LOW_STORAGE;
import static android.provider.Settings.Global.SYS_STORAGE_THRESHOLD_MAX_BYTES;
import static android.provider.Settings.Global.SYS_STORAGE_THRESHOLD_PERCENTAGE;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.app.admin.DevicePolicyManager;
import android.os.PersistableBundle;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureCanAddUser;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.CreateAndManageUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@EnsureCanAddUser
public final class CreateAndManageUserTest {

    private static final String USER_NAME = "testUserName";
    private static final PersistableBundle ADMIN_EXTRAS = new PersistableBundle();
    private static final int FLAGS = 0;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    public void createAndManageUser_userExists() {
        try (UserReference user = UserReference.of(sDeviceState.dpc().devicePolicyManager()
                .createAndManageUser(
                        sDeviceState.dpc().componentName(),
                        USER_NAME, sDeviceState.dpc().componentName(), ADMIN_EXTRAS, FLAGS))) {
            assertThat(user.exists()).isTrue();
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    public void createAndManageUser_lowStorage_throwOperationException() {
        try {
            TestApis.settings().global().putInt(SYS_STORAGE_THRESHOLD_PERCENTAGE, 100);
            TestApis.settings().global().putString(SYS_STORAGE_THRESHOLD_MAX_BYTES,
                    String.valueOf(Long.MAX_VALUE));

            UserManager.UserOperationException e = expectThrows(
                    UserManager.UserOperationException.class,
                    () -> sDeviceState.dpc().devicePolicyManager().createAndManageUser(
                            sDeviceState.dpc().componentName(),
                            USER_NAME, sDeviceState.dpc().componentName(), ADMIN_EXTRAS, FLAGS)
            );

            assertThat(e.getUserOperationResult()).isEqualTo(USER_OPERATION_ERROR_LOW_STORAGE);
        } finally {
            TestApis.settings().global().reset();
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    @Test
    public void createAndManageUser_newUserDisclaimerIsNotAcknowledged() {
        try (UserReference user = UserReference.of(sDeviceState.dpc().devicePolicyManager()
                .createAndManageUser(sDeviceState.dpc().componentName(),
                        USER_NAME, sDeviceState.dpc().componentName(),
                        ADMIN_EXTRAS, FLAGS))) {

            assertThat(TestApis.devicePolicy().isNewUserDisclaimerAcknowledged(user)).isFalse();
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = CreateAndManageUser.class, includeNonDeviceAdminStates = false)
    @Test
    public void createAndManageUser_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .createAndManageUser(sDeviceState.dpc().componentName(),
                            USER_NAME, sDeviceState.dpc().componentName(),
                            ADMIN_EXTRAS, FLAGS);
        });
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    @Test
    public void acknowledgeNewUserDisclaimer_newUserDisclaimerIsAcknowledged() {
        try (UserReference user = UserReference.of(sDeviceState.dpc().devicePolicyManager()
                .createAndManageUser(sDeviceState.dpc().componentName(),
                        USER_NAME, sDeviceState.dpc().componentName(), ADMIN_EXTRAS, FLAGS))) {

            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
                TestApis.context().androidContextAsUser(user)
                        .getSystemService(DevicePolicyManager.class).acknowledgeNewUserDisclaimer();
            }

            assertThat(TestApis.devicePolicy().isNewUserDisclaimerAcknowledged(user)).isTrue();
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    @Interactive
    @Test
    @Ignore // TODO(245232237): Figure out UX expectations
    public void createAndManageUser_switchToUser_showsDisclaimer() {
        UserReference instrumented = TestApis.users().instrumented();
        try (UserReference user = UserReference.of(sDeviceState.dpc().devicePolicyManager()
                .createAndManageUser(
                        sDeviceState.dpc().componentName(),
                        USER_NAME, sDeviceState.dpc().componentName(), ADMIN_EXTRAS, FLAGS))) {
            user.switchTo();

            // TODO(245232237): Figure out expectations here

            instrumented.switchTo();
        } finally {
            instrumented.switchTo();
        }
    }
}
