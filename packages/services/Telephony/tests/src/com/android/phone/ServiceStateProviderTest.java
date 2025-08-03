/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.phone;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Telephony.ServiceStateTable;
import static android.provider.Telephony.ServiceStateTable.DATA_NETWORK_TYPE;
import static android.provider.Telephony.ServiceStateTable.DATA_REG_STATE;
import static android.provider.Telephony.ServiceStateTable.DUPLEX_MODE;
import static android.provider.Telephony.ServiceStateTable.VOICE_OPERATOR_NUMERIC;
import static android.provider.Telephony.ServiceStateTable.VOICE_REG_STATE;
import static android.provider.Telephony.ServiceStateTable.getUriForSubscriptionId;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;

import static com.android.phone.ServiceStateProvider.ENFORCE_LOCATION_PERMISSION_CHECK;
import static com.android.phone.ServiceStateProvider.NETWORK_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import libcore.junit.util.compat.CoreCompatChangeRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for simple queries of ServiceStateProvider.
 *
 * Build, install and run the tests by running the commands below:
 *     atest ServiceStateProviderTest
 */
@RunWith(AndroidJUnit4.class)
public class ServiceStateProviderTest {
    private static final String TAG = "ServiceStateProviderTest";
    private static final int TEST_NETWORK_ID = 123;
    private static final int TEST_SYSTEM_ID = 123;

    private MockContentResolver mContentResolver;
    private ServiceState mTestServiceState;
    private ServiceState mTestServiceStateForSubId1;

    @Mock Context mContext;
    @Mock AppOpsManager mAppOpsManager;
    @Mock LocationManager mLocationManager;
    @Mock PackageManager mPackageManager;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    // Exception used internally to verify if the Resolver#notifyChange has been called.
    private class TestNotifierException extends RuntimeException {
        TestNotifierException() {
            super();
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mockSystemService(AppOpsManager.class, mAppOpsManager, Context.APP_OPS_SERVICE);
        mockSystemService(LocationManager.class, mLocationManager, Context.LOCATION_SERVICE);
        doReturn(mPackageManager).when(mContext).getPackageManager();

        mContentResolver = new MockContentResolver() {
            @Override
            public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) {
                throw new TestNotifierException();
            }
        };
        doReturn(mContentResolver).when(mContext).getContentResolver();

        mTestServiceState = new ServiceState();
        mTestServiceState.setStateOutOfService();
        mTestServiceState.setCdmaSystemAndNetworkId(TEST_SYSTEM_ID, TEST_NETWORK_ID);
        mTestServiceStateForSubId1 = new ServiceState();
        mTestServiceStateForSubId1.setStateOff();

        // Add NRI to trigger SS with non-default values (e.g. duplex mode)
        NetworkRegistrationInfo nriWwan = new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build();
        mTestServiceStateForSubId1.addNetworkRegistrationInfo(nriWwan);
        mTestServiceStateForSubId1.setChannelNumber(65536); // EutranBand.BAND_65, DUPLEX_MODE_FDD

        // Mock out the actual phone state
        ServiceStateProvider provider = new ServiceStateProvider() {
            @Override
            public ServiceState getServiceState(int subId) {
                if (subId == 1) {
                    return mTestServiceStateForSubId1;
                } else {
                    return mTestServiceState;
                }
            }

            @Override
            public int getDefaultSubId() {
                return 0;
            }
        };
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = "service-state";
        provider.attachInfoForTesting(mContext, providerInfo);
        mContentResolver.addProvider("service-state", provider);

        // By default, test with app target R, no READ_PRIVILEGED_PHONE_STATE permission
        setTargetSdkVersion(Build.VERSION_CODES.R);
        setCanReadPrivilegedPhoneState(false);

        // TODO(b/191995565): Turn on all ignored cases once location access is allow to be off
        // Do not allow phone process to always access location so we can test various scenarios
        // LocationAccessPolicy.alwaysAllowPrivilegedProcessToAccessLocationForTesting(false);
    }

    @After
    public void tearDown() throws Exception {
        // LocationAccessPolicy.alwaysAllowPrivilegedProcessToAccessLocationForTesting(true);
    }

    /**
     * Verify that when calling query with no subId in the uri the default ServiceState is returned.
     * In this case the subId is set to 0 and the expected service state is mTestServiceState.
     */
    // TODO(b/191995565): Turn this on when location access can be off
    @Ignore
    @SmallTest
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void testQueryServiceState_withNoSubId_withoutLocation() {
        setLocationPermissions(false);

        verifyServiceStateForSubId(ServiceStateTable.CONTENT_URI, mTestServiceState,
                false /*hasLocation*/);
    }

    @Test
    @SmallTest
    public void testQueryServiceState_withNoSubId_withLocation() {
        setLocationPermissions(true);

        verifyServiceStateForSubId(ServiceStateTable.CONTENT_URI, mTestServiceState,
                true /*hasLocation*/);
    }

    /**
     * Verify that when calling with the DEFAULT_SUBSCRIPTION_ID the correct ServiceState is
     * returned. In this case the subId is set to 0 and the expected service state is
     * mTestServiceState.
     */
    // TODO(b/191995565): Turn case on when location access can be off
    @Ignore
    @SmallTest
    public void testGetServiceState_withDefaultSubId_withoutLocation() {
        setLocationPermissions(false);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, false /*hasLocation*/);
    }

    @Test
    @SmallTest
    public void testGetServiceState_withDefaultSubId_withLocation() {
        setLocationPermissions(true);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasLocation*/);
    }

    /**
     * Verify that when calling with a specific subId the correct ServiceState is returned. In this
     * case the subId is set to 1 and the expected service state is mTestServiceStateForSubId1
     */
    @Test
    @SmallTest
    public void testGetServiceStateForSubId_withoutLocation() {
        setLocationPermissions(false);

        verifyServiceStateForSubId(getUriForSubscriptionId(1), mTestServiceStateForSubId1,
                false /*hasLocation*/);
    }

    @Test
    @SmallTest
    public void testGetServiceStateForSubId_withLocation() {
        setLocationPermissions(true);

        verifyServiceStateForSubId(getUriForSubscriptionId(1), mTestServiceStateForSubId1,
                true /*hasLocation*/);
    }

    /**
     * Verify that apps target S+ without READ_PRIVILEGED_PHONE_STATE permission can access the
     * public columns of ServiceStateTable.
     */
    @Test
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void
    query_publicColumns_enforceLocatoinEnabled_targetS_noReadPrivilege_getPublicColumns() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(false);

        verifyServiceStateWithPublicColumns(mTestServiceState, null /*projection*/);
    }

    @Test
    @CoreCompatChangeRule.DisableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void
    query_publicColumns_enforceLocationDisabled_targetS_noReadPrivilege_getPublicColumns() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(false);

        verifyServiceStateWithPublicColumns(mTestServiceState, null /*projection*/);
    }

    /**
     * Verify that apps target S+ without READ_PRIVILEGED_PHONE_STATE permission try to access
     * non-public columns should throw IllegalArgumentException.
     */
    @Test
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_hideColumn_targetS_noReadPrivilege_throwIllegalArgumentException() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(false);

        // DATA_ROAMING_TYPE is a non-public column
        String[] projection = new String[]{"data_roaming_type"};

        assertThrows(IllegalArgumentException.class,
                () -> verifyServiceStateWithPublicColumns(mTestServiceState, projection));
    }

    /**
     * Verify that with changeId ENFORCE_LOCATION_PERMISSION_CHECK enabled, apps target S+ with
     * READ_PRIVILEGED_PHONE_STATE and location permissions should be able to access all columns.
     */
    @Test
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void
    query_allColumn_enforceLocationEnabled_targetS_withReadPrivilegedAndLocation_getUnredacted() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(true);
        setLocationPermissions(true);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasPermission*/);
    }

    /**
     * Verify that with changeId ENFORCE_LOCATION_PERMISSION_CHECK disabled, apps target S+ with
     * READ_PRIVILEGED_PHONE_STATE and location permissions should be able to access all columns.
     */
    @Test
    @CoreCompatChangeRule.DisableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void
    query_allColumn_enforceLocationDisabled_targetS_withReadPrivilegedAndLocation_getUnredacted() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(true);
        setLocationPermissions(true);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasPermission*/);
    }

    /**
     * Verify that apps target S+ with READ_PRIVILEGED_PHONE_STATE permission but no location
     * permission, try to access location sensitive columns should throw SecurityException.
     */
    // TODO(b/191995565): Turn this on once b/191995565 is integrated
    @Ignore
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_locationColumn_targetS_withReadPrivilegeNoLocation_throwSecurityExecption() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setCanReadPrivilegedPhoneState(true);
        setLocationPermissions(false);

        assertThrows(SecurityException.class,
                () -> verifyServiceStateWithLocationColumns(mTestServiceState));
    }

    /**
     * Verify that when changeId ENFORCE_LOCATION_PERMISSION_CHECK is enabled, apps target R- with
     * location permissions should be able to access all columns.
     */
    @Test
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_allColumn_enforceLocationEnabled_targetR_withLocation_getUnredacted() {
        setTargetSdkVersion(Build.VERSION_CODES.R);
        setLocationPermissions(true);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasPermission*/);
    }

    /**
     * Verify that when changeId ENFORCE_LOCATION_PERMISSION_CHECK is disabled, apps target R- with
     * location permissions should be able to access all columns.
     */
    @Test
    @CoreCompatChangeRule.DisableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_allColumn_enforceLocationDisabled_targetR_withLocation_getUnredacted() {
        setTargetSdkVersion(Build.VERSION_CODES.R);
        setLocationPermissions(true);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasPermission*/);
    }

    /**
     * Verify that changeId ENFORCE_LOCATION_PERMISSION_CHECK is enabled, apps target R- w/o
     * location permissions should be able to access all columns but with redacted ServiceState.
     */
    // TODO(b/191995565): Turn case on when location access can be off
    @Ignore
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_allColumn_enforceLocationEnabled_targetR_noLocation_getRedacted() {
        setTargetSdkVersion(Build.VERSION_CODES.R);
        setLocationPermissions(false);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                ServiceStateProvider.getLocationRedactedServiceState(mTestServiceState),
                true /*hasPermission*/);
    }

    /**
     * Verify that changeId ENFORCE_LOCATION_PERMISSION_CHECK is disabled, apps target R- w/o
     * location permissions should be able to access all columns and with unredacted ServiceState.
     */
    // TODO(b/191995565): Turn case on when location access can be off
    @Ignore
    @CoreCompatChangeRule.DisableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void query_allColumn_enforceLocationDisabled_targetR_noLocation_getUnredacted() {
        setTargetSdkVersion(Build.VERSION_CODES.R);
        setLocationPermissions(false);

        verifyServiceStateForSubId(
                getUriForSubscriptionId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                mTestServiceState, true /*hasPermission*/);
    }

    /**
     * Verify that when caller with targetSDK S+ has location permission and try to query
     * location non-sensitive info, it should not get blamed.
     */
    @Test
    @CoreCompatChangeRule.EnableCompatChanges({ENFORCE_LOCATION_PERMISSION_CHECK})
    public void testQuery_noLocationBlamed_whenQueryNonLocationInfo_withPermission() {
        setTargetSdkVersion(Build.VERSION_CODES.S);
        setLocationPermissions(true);

        verifyServiceStateWithPublicColumns(mTestServiceState, null /*projection*/);
        verify(mAppOpsManager, never()).noteOpNoThrow(any(), anyInt(), any(), any(), any());
    }

    private void verifyServiceStateWithLocationColumns(ServiceState ss) {
        // NETWORK_ID is a location-sensitive column
        try (Cursor cursor = mContentResolver.query(ServiceStateTable.CONTENT_URI,
                new String[]{NETWORK_ID}, null, null)) {
            assertNotNull(cursor);
        }
    }

    private void verifyServiceStateWithPublicColumns(ServiceState ss, String[] projection) {
        try (Cursor cursor = mContentResolver.query(ServiceStateTable.CONTENT_URI, projection, null,
                null)) {
            assertNotNull(cursor);

            cursor.moveToFirst();
            assertEquals(ss.getVoiceRegState(),
                    cursor.getInt(cursor.getColumnIndex(VOICE_REG_STATE)));
            assertEquals(ss.getDataRegistrationState(),
                    cursor.getInt(cursor.getColumnIndex(DATA_REG_STATE)));
            assertEquals(ss.getOperatorNumeric(),
                    cursor.getString(cursor.getColumnIndex(VOICE_OPERATOR_NUMERIC)));
            assertEquals(ss.getDataNetworkType(),
                    cursor.getInt(cursor.getColumnIndex(DATA_NETWORK_TYPE)));
            assertEquals(ss.getDuplexMode(), cursor.getInt(cursor.getColumnIndex(DUPLEX_MODE)));
        }
    }

    private void verifyServiceStateForSubId(Uri uri, ServiceState ss, boolean hasLocation) {
        Cursor cursor = mContentResolver.query(uri, ServiceStateProvider.ALL_COLUMNS, "",
                null, null);
        assertNotNull(cursor);
        cursor.moveToFirst();

        final int voiceRegState = ss.getState();
        final int dataRegState = ss.getDataRegistrationState();
        final int voiceRoamingType = ss.getVoiceRoamingType();
        final int dataRoamingType = ss.getDataRoamingType();
        final String voiceOperatorAlphaLong = hasLocation ? ss.getOperatorAlphaLong() : null;
        final String voiceOperatorAlphaShort = hasLocation ? ss.getOperatorAlphaShort() : null;
        final String voiceOperatorNumeric = hasLocation ? ss.getOperatorNumeric() : null;
        final String dataOperatorAlphaLong = hasLocation ? ss.getOperatorAlphaLong() : null;
        final String dataOperatorAlphaShort = hasLocation ? ss.getOperatorAlphaShort() : null;
        final String dataOperatorNumeric = hasLocation ? ss.getOperatorNumeric() : null;
        final int isManualNetworkSelection = (ss.getIsManualSelection()) ? 1 : 0;
        final int rilVoiceRadioTechnology = ss.getRilVoiceRadioTechnology();
        final int rilDataRadioTechnology = ss.getRilDataRadioTechnology();
        final int cssIndicator = ss.getCssIndicator();
        final int networkId = hasLocation ? ss.getCdmaNetworkId() : ServiceState.UNKNOWN_ID;
        final int systemId = hasLocation ? ss.getCdmaSystemId() : ServiceState.UNKNOWN_ID;
        final int cdmaRoamingIndicator = ss.getCdmaRoamingIndicator();
        final int cdmaDefaultRoamingIndicator = ss.getCdmaDefaultRoamingIndicator();
        final int cdmaEriIconIndex = ss.getCdmaEriIconIndex();
        final int cdmaEriIconMode = ss.getCdmaEriIconMode();
        final int isEmergencyOnly = (ss.isEmergencyOnly()) ? 1 : 0;
        final int isUsingCarrierAggregation = (ss.isUsingCarrierAggregation()) ? 1 : 0;
        final String operatorAlphaLongRaw = ss.getOperatorAlphaLongRaw();
        final String operatorAlphaShortRaw = ss.getOperatorAlphaShortRaw();
        final int dataNetworkType = ss.getDataNetworkType();
        final int duplexMode = ss.getDuplexMode();

        assertEquals(voiceRegState, cursor.getInt(0));
        assertEquals(dataRegState, cursor.getInt(1));
        assertEquals(voiceRoamingType, cursor.getInt(2));
        assertEquals(dataRoamingType, cursor.getInt(3));
        assertEquals(voiceOperatorAlphaLong, cursor.getString(4));
        assertEquals(voiceOperatorAlphaShort, cursor.getString(5));
        assertEquals(voiceOperatorNumeric, cursor.getString(6));
        assertEquals(dataOperatorAlphaLong, cursor.getString(7));
        assertEquals(dataOperatorAlphaShort, cursor.getString(8));
        assertEquals(dataOperatorNumeric, cursor.getString(9));
        assertEquals(isManualNetworkSelection, cursor.getInt(10));
        assertEquals(rilVoiceRadioTechnology, cursor.getInt(11));
        assertEquals(rilDataRadioTechnology, cursor.getInt(12));
        assertEquals(cssIndicator, cursor.getInt(13));
        assertEquals(networkId, cursor.getInt(14));
        assertEquals(systemId, cursor.getInt(15));
        assertEquals(cdmaRoamingIndicator, cursor.getInt(16));
        assertEquals(cdmaDefaultRoamingIndicator, cursor.getInt(17));
        assertEquals(cdmaEriIconIndex, cursor.getInt(18));
        assertEquals(cdmaEriIconMode, cursor.getInt(19));
        assertEquals(isEmergencyOnly, cursor.getInt(20));
        assertEquals(isUsingCarrierAggregation, cursor.getInt(21));
        assertEquals(operatorAlphaLongRaw, cursor.getString(22));
        assertEquals(operatorAlphaShortRaw, cursor.getString(23));
        assertEquals(dataNetworkType, cursor.getInt(24));
        assertEquals(duplexMode, cursor.getInt(25));
    }

    /**
     * Test that we don't notify for certain field changes. (e.g. we don't notify when the NetworkId
     * or SystemId change) This is an intentional behavior change from the broadcast.
     */
    @Test
    @SmallTest
    public void testNoNotify() {
        int subId = 0;

        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setCdmaSystemAndNetworkId(1, 1);

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();
        newSS.setCdmaSystemAndNetworkId(0, 0);

        // Test that notifyChange is not called for these fields
        assertFalse(notifyChangeCalledForSubIdAndField(oldSS, newSS, subId));
    }

    @Test
    @SmallTest
    public void testNotifyChanged_noStateUpdated() {
        int subId = 0;

        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState copyOfOldSS = new ServiceState();
        copyOfOldSS.setStateOutOfService();
        copyOfOldSS.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);

        // Test that notifyChange is not called with no change in notifyChangeForSubIdAndField
        assertFalse(notifyChangeCalledForSubId(oldSS, copyOfOldSS, subId));

        // Test that notifyChange is not called with no change in notifyChangeForSubId
        assertFalse(notifyChangeCalledForSubIdAndField(oldSS, copyOfOldSS, subId));
    }

    @Test
    @SmallTest
    public void testNotifyChanged_voiceRegStateUpdated() {
        int subId = 0;

        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setVoiceRegState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();
        newSS.setVoiceRegState(ServiceState.STATE_POWER_OFF);

        // Test that notifyChange is called by notifyChangeForSubIdAndField when the voice_reg_state
        // changes
        assertTrue(notifyChangeCalledForSubId(oldSS, newSS, subId));

        // Test that notifyChange is called by notifyChangeForSubId when the voice_reg_state changes
        assertTrue(notifyChangeCalledForSubIdAndField(oldSS, newSS, subId));
    }

    @Test
    @SmallTest
    public void testNotifyChanged_dataNetworkTypeUpdated() {
        int subId = 0;

        // While we don't have a method to directly set dataNetworkType, we emulate a ServiceState
        // change that will trigger the change of dataNetworkType, according to the logic in
        // ServiceState#getDataNetworkType
        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();

        NetworkRegistrationInfo nriWwan = new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setRegistrationState(REGISTRATION_STATE_HOME)
                .build();
        newSS.addNetworkRegistrationInfo(nriWwan);

        // Test that notifyChange is called by notifyChangeForSubId when the
        // data_network_type changes
        assertTrue(notifyChangeCalledForSubId(oldSS, newSS, subId));

        // Test that notifyChange is called by notifyChangeForSubIdAndField when the
        // data_network_type changes
        assertTrue(notifyChangeCalledForSubIdAndField(oldSS, newSS, subId));
    }

    @Test
    @SmallTest
    public void testNotifyChanged_dataRegStateUpdated() {
        int subId = 0;

        ServiceState oldSS = new ServiceState();
        oldSS.setStateOutOfService();
        oldSS.setDataRegState(ServiceState.STATE_OUT_OF_SERVICE);

        ServiceState newSS = new ServiceState();
        newSS.setStateOutOfService();
        newSS.setDataRegState(ServiceState.STATE_POWER_OFF);

        // Test that notifyChange is called by notifyChangeForSubId
        // when the data_reg_state changes
        assertTrue(notifyChangeCalledForSubId(oldSS, newSS, subId));

        // Test that notifyChange is called by notifyChangeForSubIdAndField
        // when the data_reg_state changes
        assertTrue(notifyChangeCalledForSubIdAndField(oldSS, newSS, subId));
    }

    // Check if notifyChange was called by notifyChangeForSubId
    private boolean notifyChangeCalledForSubId(ServiceState oldSS,
            ServiceState newSS, int subId) {
        try {
            ServiceStateProvider.notifyChangeForSubId(mContext, oldSS, newSS, subId);
        } catch (TestNotifierException e) {
            return true;
        }
        return false;
    }

    // Check if notifyChange was called by notifyChangeForSubIdAndField
    private boolean notifyChangeCalledForSubIdAndField(ServiceState oldSS,
            ServiceState newSS, int subId) {
        try {
            ServiceStateProvider.notifyChangeForSubIdAndField(mContext, oldSS, newSS, subId);
        } catch (TestNotifierException e) {
            return true;
        }
        return false;
    }

    private void setLocationPermissions(boolean hasPermission) {
        if (!hasPermission) {
            // System location off, LocationAccessPolicy#checkLocationPermission returns DENIED_SOFT
            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class)))
                    .thenReturn(false);
        } else {
            // Turn on all to let LocationAccessPolicy#checkLocationPermission returns ALLOWED
            when(mContext.checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

            when(mContext.checkPermission(eq(Manifest.permission.ACCESS_COARSE_LOCATION),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);

            when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OPSTR_FINE_LOCATION),
                    anyInt(), anyString(), nullable(String.class), nullable(String.class)))
                    .thenReturn(AppOpsManager.MODE_ALLOWED);
            when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OPSTR_COARSE_LOCATION),
                    anyInt(), anyString(), nullable(String.class), nullable(String.class)))
                    .thenReturn(AppOpsManager.MODE_ALLOWED);

            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class))).thenReturn(true);
            when(mContext.checkPermission(eq(Manifest.permission.INTERACT_ACROSS_USERS_FULL),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        }
    }

    private <T> void mockSystemService(Class<T> clazz , T obj, String serviceName) {
        when(mContext.getSystemServiceName(eq(clazz))).thenReturn(serviceName);
        when(mContext.getSystemService(eq(serviceName))).thenReturn(obj);
    }

    private void setTargetSdkVersion(int version) {
        ApplicationInfo testAppInfo = new ApplicationInfo();
        testAppInfo.targetSdkVersion = version;
        try {
            when(mPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), any()))
                    .thenReturn(testAppInfo);
        } catch (Exception ignored) {
        }
    }

    private void setCanReadPrivilegedPhoneState(boolean granted) {
        doReturn(granted ? PERMISSION_GRANTED : PERMISSION_DENIED).when(mContext)
                .checkCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }
}
