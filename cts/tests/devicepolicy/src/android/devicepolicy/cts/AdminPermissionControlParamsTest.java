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


import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.os.Parcel;
import android.permission.AdminPermissionControlParams;

import com.android.bedstead.harrier.BedsteadJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AdminPermissionControlParamsTest {
    private static final String PKG = "somePackage";
    private static final String PERMISSION = "somePackage";
    private static final int GRANT_STATE = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
    private static final boolean CAN_ADMIN_GRANT = true;

    @Test
    public void gettersReturnConstructorValue() {
        AdminPermissionControlParams params = createViaParcel(
                PKG, PERMISSION, GRANT_STATE, CAN_ADMIN_GRANT);

        assertThat(params.getGranteePackageName()).isEqualTo(PKG);
        assertThat(params.getPermission()).isEqualTo(PERMISSION);
        assertThat(params.getGrantState()).isEqualTo(GRANT_STATE);
        assertThat(params.canAdminGrantSensorsPermissions()).isEqualTo(CAN_ADMIN_GRANT);
    }

    @Test
    public void correctParcelingAndUnparceling() {
        AdminPermissionControlParams params = createViaParcel();

        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdminPermissionControlParams loadedParams =
                AdminPermissionControlParams.CREATOR.createFromParcel(parcel);

        assertThat(params.getGranteePackageName()).isEqualTo(loadedParams.getGranteePackageName());
        assertThat(params.getPermission()).isEqualTo(loadedParams.getPermission());
        assertThat(params.getGrantState()).isEqualTo(loadedParams.getGrantState());
        assertThat(params.canAdminGrantSensorsPermissions())
                .isEqualTo(loadedParams.canAdminGrantSensorsPermissions());
    }

    private AdminPermissionControlParams createViaParcel(
            String packageName, String permission, int grantState, boolean canAdminGrant) {
        Parcel parcel = Parcel.obtain();
        parcel.writeString(packageName);
        parcel.writeString(permission);
        parcel.writeInt(grantState);
        parcel.writeBoolean(canAdminGrant);
        parcel.setDataPosition(0);

        return AdminPermissionControlParams.CREATOR.createFromParcel(parcel);
    }

    private AdminPermissionControlParams createViaParcel() {
        return createViaParcel(PKG, PERMISSION, GRANT_STATE, CAN_ADMIN_GRANT);
    }
}
