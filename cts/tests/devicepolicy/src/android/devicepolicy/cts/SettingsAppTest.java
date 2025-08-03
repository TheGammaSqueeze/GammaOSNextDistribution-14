/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.enterprise.settings.AccountsRemoveWorkProfileStep;
import com.android.interactive.steps.enterprise.settings.AreWorkAccountsSeparateToPersonalStep;
import com.android.interactive.steps.enterprise.settings.DeviceAdminAppsRemoveWorkProfileStep;
import com.android.interactive.steps.enterprise.settings.IsDeviceAdminTestAppBadgedStep;
import com.android.interactive.steps.enterprise.settings.IsItPossibleToDeactivateRemoteDPCStep;
import com.android.interactive.steps.enterprise.settings.IsRemoteDPCActivatedStep;
import com.android.interactive.steps.enterprise.settings.IsRemoteDPCBadgedStep;
import com.android.interactive.steps.enterprise.settings.NavigateToDeviceAdminAppsSectionStep;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalAccountSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToWorkAccountSettingsStep;
import com.android.interactive.steps.enterprise.settings.NavigateToWorkSecuritySettingsStep;
import com.android.queryable.annotations.Query;
import com.android.queryable.annotations.StringQuery;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SettingsAppTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @EnsureHasWorkProfile
    @Interactive
    @CddTest(requirements = {"3.9.2/C-1-7"})
    public void accountSettings_hasWorkAndPersonalCategories() throws Exception {
        // TODO: Add an account and verify that account is listed in the personal category - and
        //   that one added on the work profile is not listed in the personal category
        // Launch personal settings app (or combined app) + navigate to accounts page
        Step.execute(NavigateToPersonalAccountSettingsStep.class);

        // Confirm that personal accounts are visually separate from any work accounts
        assertThat(Step.execute(AreWorkAccountsSeparateToPersonalStep.class)).isTrue();
    }


    @Test
    @EnsureHasWorkProfile
    @Interactive
    // TODO(b/221134166): Annotate correct Cdd requirement
    public void accountSettings_removeWorkProfile() throws Exception {
        // Launch work settings app (or combined app) + navigate to the accounts page
        Step.execute(NavigateToWorkAccountSettingsStep.class);

        // Remove work profile using the button
        Step.execute(AccountsRemoveWorkProfileStep.class);
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @Interactive
    // TODO(b/221134166): Annotate correct Cdd requirement
    // Available Device Admin not in the managed profile
    @EnsureTestAppInstalled(query = @Query(
            packageName = @StringQuery(
                    isEqualTo = "com.android.bedstead.testapp.DeviceAdminTestApp")))
    public void deviceAdminSettings_correctlyListsManagedProfileAndNonManagedProfileAdmins()
            throws Exception {
        // Remove RemoteDPC from the primary user so only one entry is listed
        sDeviceState.dpc().testApp().pkg().uninstall(sDeviceState.primaryUser());

        // Launch Security settings in work settings app
        Step.execute(NavigateToWorkSecuritySettingsStep.class);

        // Navigate to Device Admins section
        Step.execute(NavigateToDeviceAdminAppsSectionStep.class);

        // Verify that the non-managed-profile device admin is listed and unbadged
        assertThat(Step.execute(IsDeviceAdminTestAppBadgedStep.class)).isFalse();

        // Verify that the test dpc admin is badged
        assertThat(Step.execute(IsRemoteDPCBadgedStep.class)).isTrue();

        // Find the "RemoteDPC" Device admin and verify that it is activated
        assertThat(Step.execute(IsRemoteDPCActivatedStep.class)).isTrue();
        assertThat(Step.execute(IsItPossibleToDeactivateRemoteDPCStep.class)).isFalse();

        // Verify the remove work profile button works
        Step.execute(DeviceAdminAppsRemoveWorkProfileStep.class);
    }
}
