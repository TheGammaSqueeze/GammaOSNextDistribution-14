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

import android.app.admin.AccountTypePolicyKey;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.PackagePermissionPolicyKey;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyKey;
import android.app.admin.UserRestrictionPolicyKey;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PolicyKeysTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();


    private static final String POLICY_KEY = "policyKey";

    private static final String ACCOUNT_TYPE = "accountType";

    private static final String INTENT_FILTER_ACTION = "action";
    private static final IntentFilter INTENT_FILTER = new IntentFilter(INTENT_FILTER_ACTION);

    private static final String PACKAGE_NAME = "packageName";

    private static final String PERMISSION_NAME = "permissionName";

    private static final String RESTRICTION = "restriction";

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.AccountTypePolicyKey#getAccountType")
    public void accountTypePolicyKey_getAccountType_returnsCorrectType() {
        AccountTypePolicyKey key = new AccountTypePolicyKey(POLICY_KEY, ACCOUNT_TYPE);

        assertThat(key.getAccountType()).isEqualTo(ACCOUNT_TYPE);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.IntentFilterPolicyKey#getIntentFilter")
    public void intentFilterPolicyKey_getIntentFilter_returnsCorrectIntentFilter() {
        IntentFilterPolicyKey key = new IntentFilterPolicyKey(POLICY_KEY, INTENT_FILTER);

        assertThat(key.getIntentFilter()).isEqualTo(INTENT_FILTER);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PackagePermissionPolicyKey#getPackageName")
    public void packagePermissionPolicyKey_getPackageName_returnsCorrectPackageName() {
        PackagePermissionPolicyKey key = new PackagePermissionPolicyKey(
                POLICY_KEY, PACKAGE_NAME, PERMISSION_NAME);

        assertThat(key.getPackageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PackagePermissionPolicyKey#getPermissionName")
    public void packagePermissionPolicyKey_getPermissionName_returnsCorrectPermissionName() {
        PackagePermissionPolicyKey key = new PackagePermissionPolicyKey(
                POLICY_KEY, PACKAGE_NAME, PERMISSION_NAME);

        assertThat(key.getPermissionName()).isEqualTo(PERMISSION_NAME);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PackagePolicyKey#getPackageName")
    public void packagePolicyKey_getPackageName_returnsCorrectPackageName() {
        PackagePolicyKey key = new PackagePolicyKey(POLICY_KEY, PACKAGE_NAME);

        assertThat(key.getPackageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.UserRestrictionPolicyKey#getRestriction")
    public void userRestrictionPolicyKey_getRestriction_returnsCorrectRestriction() {
        UserRestrictionPolicyKey key = new UserRestrictionPolicyKey(POLICY_KEY, RESTRICTION);

        assertThat(key.getRestriction()).isEqualTo(RESTRICTION);
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PolicyKey#getIdentifier")
    public void policyKey_getIdentifier_returnsCorrectRestriction() {
        PolicyKey key = new UserRestrictionPolicyKey(POLICY_KEY, RESTRICTION);

        assertThat(key.getIdentifier()).isEqualTo(POLICY_KEY);
    }
}
