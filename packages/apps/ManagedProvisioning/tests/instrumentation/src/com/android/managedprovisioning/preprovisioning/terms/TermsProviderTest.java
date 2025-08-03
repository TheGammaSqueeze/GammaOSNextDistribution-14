/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.managedprovisioning.provisioning.ProvisioningActivityTest.DEVICE_OWNER_PARAMS;
import static com.android.managedprovisioning.provisioning.ProvisioningActivityTest.PROFILE_OWNER_PARAMS;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.util.DeviceHelper;

import org.junit.Test;

import java.util.ArrayList;

@SmallTest
public class TermsProviderTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CharSequence TEST_DEVICE_NAME = DeviceHelper.getDeviceName(mContext);
    private final String mStringGeneralPo = mContext.getString(R.string.work_profile_info);
    private final String mStringGeneralDo =
            mContext.getString(R.string.managed_device_info, TEST_DEVICE_NAME);
    private final String mStringAdminDisclaimerDo =
            mContext.getString(R.string.admin_has_ability_to_monitor_device, TEST_DEVICE_NAME);
    private final String mStringAdminDisclaimerPo = mContext.getString(R.string.admin_has_ability_to_monitor_profile);

    @Test
    public void getGeneralDisclaimer_presentAsFirst_profileOwner() {
        assumeHasManagedUsersFeature();
        final TermsDocument terms =
                createTermsProvider(PROFILE_OWNER_PARAMS).getGeneralDisclaimer();

        assertThat(terms.getHeading(), equalTo(mStringGeneralPo));
        assertThat(terms.getContent(), equalTo(mStringAdminDisclaimerPo));
    }

    @Test
    public void getGeneralDisclaimer_presentAsFirst_deviceOwner() {
        assumeHasDeviceAdminFeature();
        TermsDocument terms = createTermsProvider(DEVICE_OWNER_PARAMS).getGeneralDisclaimer();

        assertThat(terms.getHeading(), equalTo(mStringGeneralDo));
        assertThat(terms.getContent(), equalTo(mStringAdminDisclaimerDo));
    }

    @NonNull
    private TermsProvider createTermsProvider(ProvisioningParams params) {
        return new TermsProvider(mContext, s -> "", params, new Utils(), ArrayList::new);
    }

    private void assumeHasManagedUsersFeature() {
        assumeTrue("Device doesn't support managed profile",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS));
    }

    private void assumeHasDeviceAdminFeature() {
        assumeTrue("Device doesn't support managed device",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
    }
}