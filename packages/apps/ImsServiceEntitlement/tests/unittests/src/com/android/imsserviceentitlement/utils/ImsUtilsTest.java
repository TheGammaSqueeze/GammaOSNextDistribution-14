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

package com.android.imsserviceentitlement.utils;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ProvisioningManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class ImsUtilsTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock ImsMmTelManager mMockImsMmTelManager;
    @Mock ProvisioningManager mMockProvisioningManager;

    private Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void isWfcEnabledByUser_invalidSubId_defaultValues() {
        ImsUtils imsUtils =
                ImsUtils.getInstance(mContext, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        assertThat(imsUtils.isWfcEnabledByUser()).isFalse();
    }

    @Test
    public void disableAndResetVoWiFiImsSettings_hasCarrierConfig() {
        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putInt(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT, 1);
        carrierConfig.putInt(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT, 2);
        ImsUtils imsUtils =
                new ImsUtils(carrierConfig, mMockImsMmTelManager, mMockProvisioningManager);

        imsUtils.disableAndResetVoWiFiImsSettings();

        verify(mMockImsMmTelManager).setVoWiFiSettingEnabled(eq(false));
        verify(mMockImsMmTelManager).setVoWiFiModeSetting(eq(1));
        verify(mMockImsMmTelManager).setVoWiFiRoamingModeSetting(eq(2));
    }

    @Test
    public void disableWfc() {
        ImsUtils imsUtils = new ImsUtils(
                new PersistableBundle(), mMockImsMmTelManager, mMockProvisioningManager);

        imsUtils.disableWfc();

        verify(mMockImsMmTelManager).setVoWiFiSettingEnabled(false);
    }
}
