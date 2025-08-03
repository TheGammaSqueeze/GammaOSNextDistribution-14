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

package com.android.adservices.service.measurement.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.devapi.DevContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class DevContextAccessResolverTest {

    private static final String ERROR_MESSAGE = "Localhost is only permitted on user-debug builds "
            + "or with developer options enabled.";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://registration-uri.test");
    private static final Uri LOCALHOST = Uri.parse("https://localhost");

    @Mock private Context mContext;
    @Mock private RegistrationRequest mRegistrationRequest;
    @Mock private WebSourceRegistrationRequest mWebSourceRegistrationRequest;
    @Mock private WebSourceParams mWebSourceParams;
    @Mock private WebSourceParams mWebSourceParams2;
    @Mock private WebTriggerRegistrationRequest mWebTriggerRegistrationRequest;
    @Mock private WebTriggerParams mWebTriggerParams;
    @Mock private WebTriggerParams mWebTriggerParams2;
    @Mock private SourceRegistrationRequest mSourceRegistrationRequest;

    private DevContextAccessResolver mClassUnderTest;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void isAllowed_register_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_localhost_devEnabled_returnsTrue() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(LOCALHOST);
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_register_localhost_devDisabled_returnsFalse() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(LOCALHOST);
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerWebSource_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams())
                .thenReturn(List.of(mWebSourceParams, mWebSourceParams2));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebSource_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerWebSource_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebSourceParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams())
                .thenReturn(List.of(mWebSourceParams, mWebSourceParams2));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerWebTrigger_nonLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_nonLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams, mWebTriggerParams2));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebTriggerRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerWebTrigger_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerWebTrigger_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(LOCALHOST);
        when(mWebTriggerParams2.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams, mWebTriggerParams2));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextDisabled(), mWebTriggerRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerSources_noneLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabled(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_noneLocalhost_devDisabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextDisabled(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_allLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris()).thenReturn(List.of(LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabled(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_someLocalhost_devEnabled_returnsTrue() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI, LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextEnabled(), mSourceRegistrationRequest);

        // Execution
        assertTrue(mClassUnderTest.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void isAllowed_registerSources_allLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris()).thenReturn(List.of(LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextDisabled(), mSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void isAllowed_registerSources_someLocalhost_devDisabled_returnsFalse() {
        // Setup
        when(mSourceRegistrationRequest.getRegistrationUris())
                .thenReturn(List.of(REGISTRATION_URI, LOCALHOST));
        mClassUnderTest =
                new DevContextAccessResolver(getDevContextDisabled(), mSourceRegistrationRequest);

        // Execution
        AccessInfo accessInfo = mClassUnderTest.getAccessInfo(mContext);
        assertFalse(accessInfo.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST,
                accessInfo.getDeniedAccessReason());
    }

    @Test
    public void getErrorMessageRegister() {
        // Setup
        when(mRegistrationRequest.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    @Test
    public void getErrorMessageRegisterWebSource() {
        // Setup
        when(mWebSourceParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebSourceRegistrationRequest.getSourceParams()).thenReturn(List.of(mWebSourceParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebSourceRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    @Test
    public void getErrorMessageRegisterWebTrigger() {
        // Setup
        when(mWebTriggerParams.getRegistrationUri()).thenReturn(REGISTRATION_URI);
        when(mWebTriggerRegistrationRequest.getTriggerParams())
                .thenReturn(List.of(mWebTriggerParams));
        mClassUnderTest = new DevContextAccessResolver(
                getDevContextEnabled(), mWebTriggerRegistrationRequest);

        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }

    private static DevContext getDevContextEnabled() {
        return DevContext.builder()
                .setDevOptionsEnabled(true)
                .build();
    }

    private static DevContext getDevContextDisabled() {
        return DevContext.createForDevOptionsDisabled();
    }
}
