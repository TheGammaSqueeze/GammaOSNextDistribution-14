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

package com.android.imsserviceentitlement.entitlement;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.entitlement.EntitlementConfiguration.ClientBehavior;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EntitlementConfigurationTest {
    private static final String RAW_XML =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"VERS\">\n"
                    + "        <parm name=\"version\" value=\"1\"/>\n"
                    + "        <parm name=\"validity\" value=\"1728000\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"TOKEN\">\n"
                    + "        <parm name=\"token\" value=\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"/>\n"
                    + "        <parm name=\"validity\" value=\"3600\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2004\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"1\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2003\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"0\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";
    private static final String RAW_XML_NO_TOKEN_VALIDITY =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"VERS\">\n"
                    + "        <parm name=\"version\" value=\"1\"/>\n"
                    + "        <parm name=\"validity\" value=\"1728000\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"TOKEN\">\n"
                    + "        <parm name=\"token\" value=\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2004\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"1\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2003\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"0\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";
    private static final String RAW_XML_NO_VERS_VERSION =
            "<wap-provisioningdoc version=\"1.1\">\n"
                    + "    <characteristic type=\"VERS\">\n"
                    + "        <parm name=\"version\" value=\"\"/>\n"
                    + "        <parm name=\"validity\" value=\"1728000\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"TOKEN\">\n"
                    + "        <parm name=\"token\" value=\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"/>\n"
                    + "        <parm name=\"validity\" value=\"3600\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2004\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"1\"/>\n"
                    + "    </characteristic>\n"
                    + "    <characteristic type=\"APPLICATION\">\n"
                    + "        <parm name=\"AppID\" value=\"ap2003\"/>\n"
                    + "        <parm name=\"EntitlementStatus\" value=\"0\"/>\n"
                    + "    </characteristic>\n"
                    + "</wap-provisioningdoc>\n";
    private static final int SUB_ID = 1;

    private Context mContext;
    private EntitlementConfiguration mConfiguration;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mConfiguration = new EntitlementConfiguration(mContext, SUB_ID);
        mConfiguration.reset();
    }

    @Test
    public void updateConfigurations_verifyConfigs() {
        mConfiguration.update(RAW_XML);

        assertThat(mConfiguration.getVolteStatus()).isEqualTo(0);
        assertThat(mConfiguration.getVoWifiStatus()).isEqualTo(1);
        assertThat(mConfiguration.getSmsOverIpStatus()).isEqualTo(2);
        assertThat(mConfiguration.getToken().get()).isEqualTo("kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX");
        assertThat(mConfiguration.getTokenValidity()).isEqualTo(3600);
        assertThat(mConfiguration.getVersion()).isEqualTo("1");
        assertThat(mConfiguration.entitlementValidation()).isEqualTo(
                ClientBehavior.VALID_DURING_VALIDITY);
    }

    @Test
    public void updateConfigurations_reset_verifyDefaultValues() {
        mConfiguration.update(RAW_XML);
        mConfiguration.reset();

        assertThat(mConfiguration.getVolteStatus()).isEqualTo(2);
        assertThat(mConfiguration.getVoWifiStatus()).isEqualTo(2);
        assertThat(mConfiguration.getSmsOverIpStatus()).isEqualTo(2);
        assertThat(mConfiguration.getToken().isPresent()).isFalse();
        assertThat(mConfiguration.getTokenValidity()).isEqualTo(0);
        assertThat(mConfiguration.entitlementValidation()).isEqualTo(ClientBehavior.NEEDS_TO_RESET);
    }

    @Test
    public void updateConfigurations_noTokenValidity_tokenValid() {
        mConfiguration.update(RAW_XML_NO_TOKEN_VALIDITY);

        assertThat(mConfiguration.getToken().get()).isEqualTo("kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX");
        assertThat(mConfiguration.getTokenValidity()).isEqualTo(0);
    }

    @Test
    public void updateConfigurations_noVersVersion_getDefaultValue() {
        mConfiguration.update(RAW_XML_NO_VERS_VERSION);

        assertThat(mConfiguration.getVersion()).isEqualTo("0");
    }
}
