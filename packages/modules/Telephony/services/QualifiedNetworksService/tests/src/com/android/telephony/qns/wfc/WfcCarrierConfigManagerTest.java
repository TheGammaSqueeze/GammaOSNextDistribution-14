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

package com.android.telephony.qns.wfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.PersistableBundle;

import com.android.telephony.qns.QnsTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

/** Tests for {@link WfcCarrierConfigManagerTest} */
@RunWith(JUnit4.class)
public final class WfcCarrierConfigManagerTest extends QnsTest {
    private WfcCarrierConfigManager mConfigManager;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mConfigManager = new WfcCarrierConfigManager(sMockContext, 0);
        mConfigManager.loadConfigurations();
    }

    @Test
    public void testIsShowVowifiPortalAfterTimeout() {
        // Test for the default setting
        assertTrue(mConfigManager.isShowVowifiPortalAfterTimeout());

        // Test for a new setting
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                WfcCarrierConfigManager
                        .KEY_QNS_SHOW_VOWIFI_PORTAL_AFTER_TIMEOUT_BOOL,
                false);
        mConfigManager.loadConfigurationsFromCarrierConfig(bundle);
        assertFalse(mConfigManager.isShowVowifiPortalAfterTimeout());
    }

    @Test
    public void testSupportJsCallbackForVowifiPortal() {
        // Test for the default setting
        assertFalse(mConfigManager.supportJsCallbackForVowifiPortal());

        // Test for a new setting
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                WfcCarrierConfigManager
                        .KEY_QNS_JS_CALLBACK_FOR_VOWIFI_PORTAL_BOOL,
                true);
        mConfigManager.loadConfigurationsFromCarrierConfig(bundle);
        assertTrue(mConfigManager.supportJsCallbackForVowifiPortal());
    }

    @Test
    public void testGetVowifiRegistrationTimerForVowifiActivation() {
        // Test for the default setting
        int defaultTimer = mConfigManager.getVowifiRegistrationTimerForVowifiActivation();
        Assert.assertEquals(WfcCarrierConfigManager.CONFIG_DEFAULT_VOWIFI_REGISTATION_TIMER,
                defaultTimer);

        // Test for a new setting
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(
                WfcCarrierConfigManager
                        .KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT,
                60000);
        mConfigManager.loadConfigurationsFromCarrierConfig(bundle);
        Assert.assertEquals(60000, mConfigManager.getVowifiRegistrationTimerForVowifiActivation());
    }

    @Test
    public void testGetVowifiEntitlementServerUrl() {
        // Test for the default setting
        Assert.assertEquals("", mConfigManager.getVowifiEntitlementServerUrl());

        // Test for a new setting
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(
                WfcCarrierConfigManager
                        .KEY_QNS_VOWIFI_ENTITLEMENT_SERVER_URL_STRING,
                "www.google.com");
        mConfigManager.loadConfigurationsFromCarrierConfig(bundle);
        Assert.assertEquals(
                "www.google.com", mConfigManager.getVowifiEntitlementServerUrl());
    }
}
