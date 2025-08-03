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

import static com.android.bedstead.nene.permissions.CommonPermissions.CREATE_USERS;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(BedsteadJUnit4.class)
public final class ManagedProfileTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);

    @Test
    @EnsureHasNoDpc
    public void startActivityInManagedProfile_activityStarts() {
        // We want a fresh - properly created work profile
        try (RemoteDpc dpc = RemoteDpc.createWorkProfile();
             TestAppInstance testApp = sDeviceState.testApps().query()
                     .whereActivities().contains(activity().where().exported().isTrue())
                     .get().install()) {

            TestAppActivityReference activity =
                    testApp.activities().query().whereActivity().exported().isTrue().get();
            activity.start();

            assertThat(activity.events().activityCreated()).eventOccurred();
        }
    }


    @ApiTest(apis = "android.os.UserManager#createProfile")
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureHasPermission(CREATE_USERS)
    public void createProfile_noProfileExists_creates() {
        UserHandle profile = null;
        try {
            profile = sUserManager.createProfile("testProfile",
                    UserManager.USER_TYPE_PROFILE_MANAGED, Collections.emptySet());

            assertThat(profile).isNotNull();
        } finally {
            if (profile != null) {
                UserReference.of(profile).remove();
            }
        }
    }

    @ApiTest(apis = "android.os.UserManager#isManagedProfile")
    @Postsubmit(reason = "new test")
    @Test
    @RequireRunOnWorkProfile
    public void isManagedProfile_runOnWorkProfile_returnsTrue() {
        assertThat(sDeviceState.dpc().userManager().isManagedProfile()).isTrue();
    }

    @ApiTest(apis = "android.os.UserManager#isManagedProfile")
    @Postsubmit(reason = "new test")
    @UserTest({UserType.SYSTEM_USER, UserType.SECONDARY_USER, UserType.CLONE_PROFILE})
    public void isManagedProfile_runOnUser_returnsFalse() {
        assertThat(sUserManager.isManagedProfile(TestApis.users().instrumented().id()))
                .isFalse();
    }
}
