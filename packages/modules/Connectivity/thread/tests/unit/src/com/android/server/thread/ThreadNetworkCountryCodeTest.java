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

package com.android.server.thread;

import static android.net.thread.ThreadNetworkException.ERROR_INTERNAL_ERROR;

import static com.android.server.thread.ThreadNetworkCountryCode.DEFAULT_COUNTRY_CODE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.thread.IOperationReceiver;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActiveCountryCodeChangedCallback;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.connectivity.resources.R;
import com.android.server.connectivity.ConnectivityResources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Unit tests for {@link ThreadNetworkCountryCode}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ThreadNetworkCountryCodeTest {
    private static final String TEST_COUNTRY_CODE_US = "US";
    private static final String TEST_COUNTRY_CODE_CN = "CN";
    private static final String TEST_COUNTRY_CODE_INVALID = "INVALID";
    private static final String TEST_WIFI_DEFAULT_COUNTRY_CODE = "00";
    private static final int TEST_SIM_SLOT_INDEX_0 = 0;
    private static final int TEST_SIM_SLOT_INDEX_1 = 1;

    @Mock Context mContext;
    @Mock LocationManager mLocationManager;
    @Mock Geocoder mGeocoder;
    @Mock ThreadNetworkControllerService mThreadNetworkControllerService;
    @Mock PackageManager mPackageManager;
    @Mock Location mLocation;
    @Mock Resources mResources;
    @Mock ConnectivityResources mConnectivityResources;
    @Mock WifiManager mWifiManager;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock List<SubscriptionInfo> mSubscriptionInfoList;
    @Mock SubscriptionInfo mSubscriptionInfo0;
    @Mock SubscriptionInfo mSubscriptionInfo1;

    private ThreadNetworkCountryCode mThreadNetworkCountryCode;
    private boolean mErrorSetCountryCode;

    @Captor private ArgumentCaptor<LocationListener> mLocationListenerCaptor;
    @Captor private ArgumentCaptor<Geocoder.GeocodeListener> mGeocodeListenerCaptor;
    @Captor private ArgumentCaptor<IOperationReceiver> mOperationReceiverCaptor;
    @Captor private ArgumentCaptor<ActiveCountryCodeChangedCallback> mWifiCountryCodeReceiverCaptor;
    @Captor private ArgumentCaptor<BroadcastReceiver> mTelephonyCountryCodeReceiverCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mConnectivityResources.get()).thenReturn(mResources);
        when(mResources.getBoolean(anyInt())).thenReturn(true);

        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(mSubscriptionInfoList);
        Iterator<SubscriptionInfo> iteratorMock = mock(Iterator.class);
        when(mSubscriptionInfoList.size()).thenReturn(2);
        when(mSubscriptionInfoList.iterator()).thenReturn(iteratorMock);
        when(iteratorMock.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(iteratorMock.next()).thenReturn(mSubscriptionInfo0).thenReturn(mSubscriptionInfo1);
        when(mSubscriptionInfo0.getSimSlotIndex()).thenReturn(TEST_SIM_SLOT_INDEX_0);
        when(mSubscriptionInfo1.getSimSlotIndex()).thenReturn(TEST_SIM_SLOT_INDEX_1);

        when(mLocation.getLatitude()).thenReturn(0.0);
        when(mLocation.getLongitude()).thenReturn(0.0);

        Answer setCountryCodeCallback =
                invocation -> {
                    Object[] args = invocation.getArguments();
                    IOperationReceiver cb = (IOperationReceiver) args[1];

                    if (mErrorSetCountryCode) {
                        cb.onError(ERROR_INTERNAL_ERROR, new String("Invalid country code"));
                    } else {
                        cb.onSuccess();
                    }
                    return new Object();
                };

        doAnswer(setCountryCodeCallback)
                .when(mThreadNetworkControllerService)
                .setCountryCode(any(), any(IOperationReceiver.class));

        mThreadNetworkCountryCode = newCountryCodeWithOemSource(null);
    }

    private ThreadNetworkCountryCode newCountryCodeWithOemSource(@Nullable String oemCountryCode) {
        return new ThreadNetworkCountryCode(
                mLocationManager,
                mThreadNetworkControllerService,
                mGeocoder,
                mConnectivityResources,
                mWifiManager,
                mContext,
                mTelephonyManager,
                mSubscriptionManager,
                oemCountryCode);
    }

    private static Address newAddress(String countryCode) {
        Address address = new Address(Locale.ROOT);
        address.setCountryCode(countryCode);
        return address;
    }

    @Test
    public void threadNetworkCountryCode_invalidOemCountryCode_illegalArgumentExceptionIsThrown() {
        assertThrows(
                IllegalArgumentException.class,
                () -> newCountryCodeWithOemSource(TEST_COUNTRY_CODE_INVALID));
    }

    @Test
    public void initialize_defaultCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(DEFAULT_COUNTRY_CODE);
    }

    @Test
    public void initialize_oemCountryCodeAvailable_oemCountryCodeIsUsed() {
        mThreadNetworkCountryCode = newCountryCodeWithOemSource(TEST_COUNTRY_CODE_US);

        mThreadNetworkCountryCode.initialize();

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_US);
    }

    @Test
    public void initialize_locationUseIsDisabled_locationFunctionIsNotCalled() {
        when(mResources.getBoolean(R.bool.config_thread_location_use_for_country_code_enabled))
                .thenReturn(false);

        mThreadNetworkCountryCode.initialize();

        verifyNoMoreInteractions(mGeocoder);
        verifyNoMoreInteractions(mLocationManager);
    }

    @Test
    public void locationCountryCode_locationChanged_locationCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();

        verify(mLocationManager)
                .requestLocationUpdates(
                        anyString(), anyLong(), anyFloat(), mLocationListenerCaptor.capture());
        mLocationListenerCaptor.getValue().onLocationChanged(mLocation);
        verify(mGeocoder)
                .getFromLocation(
                        anyDouble(), anyDouble(), anyInt(), mGeocodeListenerCaptor.capture());
        mGeocodeListenerCaptor.getValue().onGeocode(List.of(newAddress(TEST_COUNTRY_CODE_US)));

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_US);
    }

    @Test
    public void wifiCountryCode_bothWifiAndLocationAreAvailable_wifiCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mLocationManager)
                .requestLocationUpdates(
                        anyString(), anyLong(), anyFloat(), mLocationListenerCaptor.capture());
        mLocationListenerCaptor.getValue().onLocationChanged(mLocation);
        verify(mGeocoder)
                .getFromLocation(
                        anyDouble(), anyDouble(), anyInt(), mGeocodeListenerCaptor.capture());

        Address mockAddress = mock(Address.class);
        when(mockAddress.getCountryCode()).thenReturn(TEST_COUNTRY_CODE_US);
        List<Address> addresses = List.of(mockAddress);
        mGeocodeListenerCaptor.getValue().onGeocode(addresses);

        verify(mWifiManager)
                .registerActiveCountryCodeChangedCallback(
                        any(), mWifiCountryCodeReceiverCaptor.capture());
        mWifiCountryCodeReceiverCaptor.getValue().onActiveCountryCodeChanged(TEST_COUNTRY_CODE_CN);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_CN);
    }

    @Test
    public void wifiCountryCode_wifiCountryCodeIsActive_wifiCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();

        verify(mWifiManager)
                .registerActiveCountryCodeChangedCallback(
                        any(), mWifiCountryCodeReceiverCaptor.capture());
        mWifiCountryCodeReceiverCaptor.getValue().onActiveCountryCodeChanged(TEST_COUNTRY_CODE_US);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_US);
    }

    @Test
    public void wifiCountryCode_wifiDefaultCountryCodeIsActive_wifiCountryCodeIsNotUsed() {
        mThreadNetworkCountryCode.initialize();

        verify(mWifiManager)
                .registerActiveCountryCodeChangedCallback(
                        any(), mWifiCountryCodeReceiverCaptor.capture());
        mWifiCountryCodeReceiverCaptor
                .getValue()
                .onActiveCountryCodeChanged(TEST_WIFI_DEFAULT_COUNTRY_CODE);

        assertThat(mThreadNetworkCountryCode.getCountryCode())
                .isNotEqualTo(TEST_WIFI_DEFAULT_COUNTRY_CODE);
    }

    @Test
    public void wifiCountryCode_wifiCountryCodeIsInactive_defaultCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mWifiManager)
                .registerActiveCountryCodeChangedCallback(
                        any(), mWifiCountryCodeReceiverCaptor.capture());
        mWifiCountryCodeReceiverCaptor.getValue().onActiveCountryCodeChanged(TEST_COUNTRY_CODE_US);

        mWifiCountryCodeReceiverCaptor.getValue().onCountryCodeInactive();

        assertThat(mThreadNetworkCountryCode.getCountryCode())
                .isEqualTo(ThreadNetworkCountryCode.DEFAULT_COUNTRY_CODE);
    }

    @Test
    public void telephonyCountryCode_bothTelephonyAndLocationAvailable_telephonyCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mLocationManager)
                .requestLocationUpdates(
                        anyString(), anyLong(), anyFloat(), mLocationListenerCaptor.capture());
        mLocationListenerCaptor.getValue().onLocationChanged(mLocation);
        verify(mGeocoder)
                .getFromLocation(
                        anyDouble(), anyDouble(), anyInt(), mGeocodeListenerCaptor.capture());
        mGeocodeListenerCaptor.getValue().onGeocode(List.of(newAddress(TEST_COUNTRY_CODE_US)));

        verify(mContext)
                .registerReceiver(
                        mTelephonyCountryCodeReceiverCaptor.capture(),
                        any(),
                        eq(Context.RECEIVER_EXPORTED));
        Intent intent =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, TEST_COUNTRY_CODE_CN)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_0);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_CN);
    }

    @Test
    public void telephonyCountryCode_locationIsAvailable_lastKnownTelephonyCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mLocationManager)
                .requestLocationUpdates(
                        anyString(), anyLong(), anyFloat(), mLocationListenerCaptor.capture());
        mLocationListenerCaptor.getValue().onLocationChanged(mLocation);
        verify(mGeocoder)
                .getFromLocation(
                        anyDouble(), anyDouble(), anyInt(), mGeocodeListenerCaptor.capture());
        mGeocodeListenerCaptor.getValue().onGeocode(List.of(newAddress(TEST_COUNTRY_CODE_US)));

        verify(mContext)
                .registerReceiver(
                        mTelephonyCountryCodeReceiverCaptor.capture(),
                        any(),
                        eq(Context.RECEIVER_EXPORTED));
        Intent intent =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, "")
                        .putExtra(
                                TelephonyManager.EXTRA_LAST_KNOWN_NETWORK_COUNTRY,
                                TEST_COUNTRY_CODE_US)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_0);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_US);
    }

    @Test
    public void telephonyCountryCode_lastKnownCountryCodeAvailable_telephonyCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mContext)
                .registerReceiver(
                        mTelephonyCountryCodeReceiverCaptor.capture(),
                        any(),
                        eq(Context.RECEIVER_EXPORTED));
        Intent intent0 =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, "")
                        .putExtra(
                                TelephonyManager.EXTRA_LAST_KNOWN_NETWORK_COUNTRY,
                                TEST_COUNTRY_CODE_US)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_0);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent0);

        verify(mContext)
                .registerReceiver(
                        mTelephonyCountryCodeReceiverCaptor.capture(),
                        any(),
                        eq(Context.RECEIVER_EXPORTED));
        Intent intent1 =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, TEST_COUNTRY_CODE_CN)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_1);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent1);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_CN);
    }

    @Test
    public void telephonyCountryCode_multipleSims_firstSimIsUsed() {
        mThreadNetworkCountryCode.initialize();
        verify(mContext)
                .registerReceiver(
                        mTelephonyCountryCodeReceiverCaptor.capture(),
                        any(),
                        eq(Context.RECEIVER_EXPORTED));
        Intent intent1 =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, TEST_COUNTRY_CODE_CN)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_1);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent1);

        Intent intent0 =
                new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, TEST_COUNTRY_CODE_CN)
                        .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, TEST_SIM_SLOT_INDEX_0);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mContext, intent0);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_CN);
    }

    @Test
    public void updateCountryCode_noForceUpdateDefaultCountryCode_noCountryCodeIsUpdated() {
        mThreadNetworkCountryCode.initialize();
        clearInvocations(mThreadNetworkControllerService);

        mThreadNetworkCountryCode.updateCountryCode(false /* forceUpdate */);

        verify(mThreadNetworkControllerService, never()).setCountryCode(any(), any());
    }

    @Test
    public void updateCountryCode_forceUpdateDefaultCountryCode_countryCodeIsUpdated() {
        mThreadNetworkCountryCode.initialize();
        clearInvocations(mThreadNetworkControllerService);

        mThreadNetworkCountryCode.updateCountryCode(true /* forceUpdate */);

        verify(mThreadNetworkControllerService)
                .setCountryCode(eq(DEFAULT_COUNTRY_CODE), mOperationReceiverCaptor.capture());
    }

    @Test
    public void setOverrideCountryCode_defaultCountryCodeAvailable_overrideCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();

        mThreadNetworkCountryCode.setOverrideCountryCode(TEST_COUNTRY_CODE_CN);

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(TEST_COUNTRY_CODE_CN);
    }

    @Test
    public void clearOverrideCountryCode_defaultCountryCodeAvailable_defaultCountryCodeIsUsed() {
        mThreadNetworkCountryCode.initialize();
        mThreadNetworkCountryCode.setOverrideCountryCode(TEST_COUNTRY_CODE_CN);

        mThreadNetworkCountryCode.clearOverrideCountryCode();

        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(DEFAULT_COUNTRY_CODE);
    }

    @Test
    public void setCountryCodeFailed_defaultCountryCodeAvailable_countryCodeIsNotUpdated() {
        mThreadNetworkCountryCode.initialize();

        mErrorSetCountryCode = true;
        mThreadNetworkCountryCode.setOverrideCountryCode(TEST_COUNTRY_CODE_CN);

        verify(mThreadNetworkControllerService)
                .setCountryCode(eq(TEST_COUNTRY_CODE_CN), mOperationReceiverCaptor.capture());
        assertThat(mThreadNetworkCountryCode.getCountryCode()).isEqualTo(DEFAULT_COUNTRY_CODE);
    }

    @Test
    public void dump_allCountryCodeInfoAreDumped() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        mThreadNetworkCountryCode.dump(new FileDescriptor(), printWriter, null);
        String outputString = stringWriter.toString();

        assertThat(outputString).contains("mOverrideCountryCodeInfo");
        assertThat(outputString).contains("mTelephonyCountryCodeSlotInfoMap");
        assertThat(outputString).contains("mTelephonyCountryCodeInfo");
        assertThat(outputString).contains("mWifiCountryCodeInfo");
        assertThat(outputString).contains("mTelephonyLastCountryCodeInfo");
        assertThat(outputString).contains("mLocationCountryCodeInfo");
        assertThat(outputString).contains("mOemCountryCodeInfo");
        assertThat(outputString).contains("mCurrentCountryCodeInfo");
    }
}
