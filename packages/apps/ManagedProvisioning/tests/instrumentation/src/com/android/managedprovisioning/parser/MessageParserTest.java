/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests {@link MessageParser} */
@SmallTest
public class MessageParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");

    @Mock
    private Context mContext;

    private Utils mUtils;

    private MessageParser mMessageParser;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);
        mUtils = spy(new Utils());
        mMessageParser = new MessageParser(
                mContext, mUtils, new ParserUtils(), new SettingsFacade());
    }

    public void test_correctParserUsedToParseOtherSupportedProvisioningIntent() throws Exception {
        // GIVEN the device admin app is installed.
        doReturn(TEST_COMPONENT_NAME)
                .when(mUtils)
                .findDeviceAdmin(null, TEST_COMPONENT_NAME, mContext, UserHandle.myUserId());
        // GIVEN a list of supported provisioning actions, except NFC.
        String[] supportedProvisioningActions = new String[] {
                ACTION_PROVISION_MANAGED_DEVICE,
                ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE,
                ACTION_PROVISION_MANAGED_PROFILE
        };

        for (String provisioningAction : supportedProvisioningActions) {
            // WHEN the mMessageParser.getParser is invoked.
            ProvisioningDataParser parser = mMessageParser.getParser();

            // THEN the extras parser is returned.
            assertTrue(parser instanceof ExtrasProvisioningDataParser);
        }
    }
}
