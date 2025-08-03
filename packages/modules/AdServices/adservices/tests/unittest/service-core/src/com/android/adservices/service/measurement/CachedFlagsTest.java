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

package com.android.adservices.service.measurement;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public final class CachedFlagsTest {

    private static final boolean CACHED_BOOL_FLAG_VALUE = false;
    private static final boolean NON_CACHED_BOOL_FLAG_VALUE = true;
    private static final String CACHED_STRING_FLAG_VALUE = "cached_string_flag_value";
    private static final String NON_CACHED_STRING_FLAG_VALUE = "non_cached_string_flag_value";
    @Mock private Flags mMockFlags;
    private CachedFlags mFlags;

    private void setUp(boolean enableSessionStableKillSwitches) {
        MockitoAnnotations.initMocks(this);
        setupMockFlags(
                enableSessionStableKillSwitches, CACHED_BOOL_FLAG_VALUE, CACHED_STRING_FLAG_VALUE);
        mFlags = new CachedFlags(mMockFlags);
        setupMockFlags(
                !enableSessionStableKillSwitches,
                NON_CACHED_BOOL_FLAG_VALUE,
                NON_CACHED_STRING_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterSourcesKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiRegisterSourcesKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterWebSourceKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterWebTriggerKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiDeleteRegistrationsKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMeasurementApiStatusKillSwitch()).isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterSource_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterTrigger_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterSources_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterWebSource_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterWebTrigger_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtDeleteRegistrations_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtEnableApiStatusAllowListCheck_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMsmtEnableApiStatusAllowListCheck()).isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetConsentNotifiedDebugMode_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getConsentNotifiedDebugMode()).isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementStatus_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtApiAppAllowList_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMsmtApiAppAllowList()).isEqualTo(CACHED_STRING_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtApiAppBlockList_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getMsmtApiAppBlockList()).isEqualTo(CACHED_STRING_FLAG_VALUE);
    }

    @Test
    public void testGetWebContextClientAppAllowList_flagEnabled_returnsCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ true);
        assertThat(mFlags.getWebContextClientAppAllowList()).isEqualTo(CACHED_STRING_FLAG_VALUE);
    }

    // START

    @Test
    public void testGetMeasurementApiRegisterSourceKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiRegisterSourceKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterSourcesKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiRegisterSourcesKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void
            testGetMeasurementApiRegisterWebSourceKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiRegisterTriggerKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiRegisterTriggerKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void
            testGetMeasurementApiRegisterWebTriggerKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtApiDeleteRegistrationsKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMeasurementApiStatusKillSwitch_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMeasurementApiStatusKillSwitch())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterSource_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterTrigger_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterSources_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterWebSource_flagDisabled_returnsNonCached() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtRegisterWebTrigger_flagDisabled_returnsNonCached() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundForMsmtDeleteRegistrations_flagDisabled_returnsNonCached() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtEnableApiStatusAllowListCheck_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMsmtEnableApiStatusAllowListCheck())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetConsentNotifiedDebugMode_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getConsentNotifiedDebugMode()).isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetEnforceForegroundStatusForMeasurementStatus_flagDisabled_returnsNonCached() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getEnforceForegroundStatusForMeasurementStatus())
                .isEqualTo(NON_CACHED_BOOL_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtApiAppAllowList_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMsmtApiAppAllowList()).isEqualTo(NON_CACHED_STRING_FLAG_VALUE);
    }

    @Test
    public void testGetMsmtApiAppBlockList_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getMsmtApiAppBlockList()).isEqualTo(NON_CACHED_STRING_FLAG_VALUE);
    }

    @Test
    public void testGetWebContextClientAppAllowList_flagDisabled_returnsNonCachedFlag() {
        setUp(/* enableSessionStableKillSwitches= */ false);
        assertThat(mFlags.getWebContextClientAppAllowList())
                .isEqualTo(NON_CACHED_STRING_FLAG_VALUE);
    }

    private void setupMockFlags(
            boolean enableSessionStableKillSwitches, boolean booleanValue, String stringValue) {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches())
                .thenReturn(enableSessionStableKillSwitches);
        when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .thenReturn(booleanValue);
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(booleanValue);
        when(mMockFlags.getConsentNotifiedDebugMode()).thenReturn(booleanValue);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementStatus()).thenReturn(booleanValue);
        when(mMockFlags.getMsmtApiAppAllowList()).thenReturn(stringValue);
        when(mMockFlags.getMsmtApiAppBlockList()).thenReturn(stringValue);
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn(stringValue);
    }
}
