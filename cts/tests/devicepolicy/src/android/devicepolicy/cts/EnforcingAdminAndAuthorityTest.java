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

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.Authority;
import android.app.admin.DeviceAdminAuthority;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.RoleAuthority;
import android.app.admin.UnknownAuthority;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class EnforcingAdminAndAuthorityTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String PACKAGE_NAME = "packageName";

    private static final Authority AUTHORITY = DpcAuthority.DPC_AUTHORITY;

    private static final Set<String> ROLES = Set.of("role");

    private static final UserHandle USER_HANDLE = sDeviceState.primaryUser().userHandle();


    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.EnforcingAdmin#getPackageName")
    public void enforcingAdmin_getPackageName_returnsCorrectPackage() {
        EnforcingAdmin admin = new EnforcingAdmin(PACKAGE_NAME, AUTHORITY, USER_HANDLE);

        assertThat(admin.getPackageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.EnforcingAdmin#getAuthority")
    public void enforcingAdmin_getAuthority_returnsCorrectAuthority() {
        EnforcingAdmin admin = new EnforcingAdmin(PACKAGE_NAME, AUTHORITY, USER_HANDLE);

        assertThat(admin.getAuthority()).isEqualTo(AUTHORITY);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.EnforcingAdmin#getUserHandle")
    public void enforcingAdmin_getUserHandle_returnsCorrectUserHandle() {
        EnforcingAdmin admin = new EnforcingAdmin(PACKAGE_NAME, AUTHORITY, USER_HANDLE);

        assertThat(admin.getUserHandle()).isEqualTo(USER_HANDLE);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DeviceAdminAuthority#DeviceAdminAuthority")
    public void deviceAdminAuthority_equality_returnsTrue() {
        DeviceAdminAuthority authority1 = new DeviceAdminAuthority();
        DeviceAdminAuthority authority2 = new DeviceAdminAuthority();

        assertThat(authority1).isEqualTo(authority2);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DpcAuthority#DpcAuthority")
    public void dpcAuthority_equality_returnsTrue() {
        DpcAuthority authority1 = new DpcAuthority();
        DpcAuthority authority2 = new DpcAuthority();

        assertThat(authority1).isEqualTo(authority2);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.UnknownAuthority#UnknownAuthority")
    public void unknownAuthority_equality_returnsTrue() {
        UnknownAuthority authority1 = new UnknownAuthority();
        UnknownAuthority authority2 = new UnknownAuthority();

        assertThat(authority1).isEqualTo(authority2);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.UnknownAuthority#UnknownAuthority")
    public void roleAuthority_equality_returnsTrue() {
        RoleAuthority authority = new RoleAuthority(ROLES);

        assertThat(authority.getRoles()).isEqualTo(ROLES);
    }
}
