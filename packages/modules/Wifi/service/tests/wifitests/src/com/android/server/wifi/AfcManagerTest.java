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

package com.android.server.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.HandlerExecutor;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unit tests for {@link com.android.server.wifi.AfcManager}.
 */
@SmallTest
public class AfcManagerTest extends WifiBaseTest {
    @Captor ArgumentCaptor<AfcClient.Callback> mAfcClientCallbackCaptor;
    @Mock Clock mClock;
    @Mock WifiContext mContext;
    @Mock AfcLocation mAfcLocation;
    @Mock LocationManager mLocationManager;
    @Mock Location mLocation;
    @Mock Looper mLooper;
    @Mock HandlerThread mWifiHandlerThread;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiNative mWifiNative;
    @Mock WifiGlobals mWifiGlobals;
    @Mock AfcLocationUtil mAfcLocationUtil;
    @Mock AfcClient mAfcClient;
    private AfcManager mAfcManager;
    private static final String EXPIRATION_DATE = "2020-11-03T13:34:05Z";
    private static String sAfcServerUrl1;
    private static Map<String, String> sAfcRequestProperties1;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAfcClientCallbackCaptor = ArgumentCaptor.forClass(AfcClient.Callback.class);

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);
        when(mWifiInjector.getWifiHandlerThread()).thenReturn(mWifiHandlerThread);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiInjector.getAfcLocationUtil()).thenReturn(mAfcLocationUtil);
        when(mWifiInjector.getAfcClient()).thenReturn(mAfcClient);
        when(mWifiHandlerThread.getLooper()).thenReturn(mLooper);
        when(mWifiGlobals.isAfcSupportedOnDevice()).thenReturn(true);
        when(mWifiGlobals.getAfcServerUrlsForCountry(anyString())).thenReturn(Arrays.asList(
                sAfcServerUrl1));

        when(mLocationManager.getProviders(anyBoolean())).thenReturn(List.of(
                LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER));
        when(mAfcLocationUtil.createAfcLocation(mLocation)).thenReturn(mAfcLocation);

        sAfcServerUrl1 = "https://example.com/";
        sAfcRequestProperties1 = new HashMap<>();
    }

    private AfcManager makeAfcManager() {
        return new AfcManager(mContext, mWifiInjector);
    }

    /**
     * Returns a valid mock AfcServerResponse from the AFC sever.
     */
    private AfcServerResponse buildSuccessfulSpectrumInquiryResponse() throws JSONException {
        JSONObject inquiryResponse = new JSONObject();
        inquiryResponse.put("requestId", String.valueOf(0));

        // This response's expire time in milliseconds is 1687463240000L as tested by
        // AfcClientTest#testExpireTimeSetCorrectly
        inquiryResponse.put("availabilityExpireTime", AfcManagerTest.EXPIRATION_DATE);

        JSONObject responseResultObject = new JSONObject();
        responseResultObject.put("responseCode", 0);
        responseResultObject.put("shortDescription", "Success");
        inquiryResponse.put("response", responseResultObject);

        return AfcServerResponse.fromSpectrumInquiryResponse(200, inquiryResponse);
    }

    /**
     * Verify that a crash does not occur if the AFC Manager is provided with a null location on
     * a call to {@link AfcManager#onLocationChange}.
     */
    @Test
    public void testNoCrashForNullLocation() {
        mAfcManager = makeAfcManager();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(9999L);

        mAfcManager.onLocationChange(null, false);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(null);
    }

    /**
     * Verify that a when a location change occurs, we query the Afc server for the first time
     * and update the lastKnownLocation variable.
     */
    @Test
    public void testUpdateLastKnownLocationOnLocationChange() {
        mAfcManager = makeAfcManager();
        mAfcManager.setIsAfcSupportedInCurrentCountry(true);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(9999L);

        mAfcManager.onLocationChange(mLocation, false);

        verify(mAfcClient).queryAfcServer(any(AfcLocation.class), any(Handler.class), any(AfcClient
                .Callback.class));
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(mLocation);
        assertThat(mAfcManager.getLocationManager()).isEqualTo(mLocationManager);
    }

    /**
     * Verify that we fetch a new location and re-query the server when the country code changes.
     */
    @Test
    public void testGetLocationAfterCountryCodeChange() {
        mAfcManager = makeAfcManager();
        long now = 9999L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(now);
        mAfcManager.onCountryCodeChange("US");

        ArgumentCaptor<Consumer<Location>> consumerArgumentCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        assertEquals(sAfcServerUrl1, (mAfcManager).getAfcServerUrl());
        // check that we fetch a new location
        verify(mLocationManager).getCurrentLocation(anyString(), any(),
                any(HandlerExecutor.class), consumerArgumentCaptor.capture());

        // run the consumer's callback, then verify that we updated the lastKnownLocation
        Consumer<Location> locationConsumer = consumerArgumentCaptor.getValue();
        locationConsumer.accept(mLocation);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(mLocation);

        // check that the AfcClient makes a query
        verify(mAfcClient).queryAfcServer(any(AfcLocation.class), any(Handler.class), any(AfcClient
                .Callback.class));

        // check that when a query is sent to the server, we are caching information
        // about the request
        assertThat(mAfcManager.getLastAfcServerQueryTime()).isEqualTo(now);
        verify(mAfcLocationUtil).createAfcLocation(mLocation);

        // verify we are setting lastKnownLocation to null when a null location is passed in
        locationConsumer.accept(null);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(null);
    }

    /**
     * Verify that the AFC Manager is created correctly and loads the clock and handler thread from
     * the {@link WifiInjector}
     */
    @Test
    public void testAfcManagerCorrectlyCreated() {
        mAfcManager = makeAfcManager();
        verify(mWifiInjector).getClock();
        verify(mWifiInjector).getWifiHandlerThread();
        verify(mWifiInjector).getWifiGlobals();
    }

    /**
     * Verify that when AFC is disabled, we do not query the server after a country code change.
     */
    @Test
    public void testNoQueryWhenAfcNotEnabled() {
        mAfcManager = makeAfcManager();
        when(mWifiGlobals.isAfcSupportedOnDevice()).thenReturn(false);
        mAfcManager.onCountryCodeChange("US");

        verifyNoMoreInteractions(mLocationManager);
    }

    /**
     * Verify that the network provider is used to get the current location if the fused provider
     * is not available (it is only available from API level 31 onwards).
     */
    @Test
    public void testCorrectProviderUsedForGetLocation() {
        mAfcManager = makeAfcManager();
        // list doesn't include the fused provider
        when(mLocationManager.getProviders(anyBoolean())).thenReturn(List.of(
                LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER));

        mAfcManager.onCountryCodeChange("US");
        ArgumentCaptor<String> providerArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mLocationManager).getCurrentLocation(providerArgumentCaptor.capture(), any(),
                any(HandlerExecutor.class), any(Consumer.class));

        // capture the provider, and check that it is the expected provider
        String provider = providerArgumentCaptor.getValue();
        assertThat(provider).isEqualTo(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Verify that when AFC is not available in a country, the location listener is no longer
     * informed of location updates.
     */
    @Test
    public void testStopListeningForLocationUpdates() {
        mAfcManager = makeAfcManager();
        ArgumentCaptor<LocationListener> locationListenerCaptor =
                ArgumentCaptor.forClass(LocationListener.class);
        mAfcManager.onCountryCodeChange("US");

        assertEquals(sAfcServerUrl1, (mAfcManager).getAfcServerUrl());
        // check that we listen for location updates
        verify(mLocationManager).requestLocationUpdates(anyString(), anyLong(),
                anyFloat(), locationListenerCaptor.capture(), any(Looper.class));
        LocationListener locationListener = locationListenerCaptor.getValue();

        // Mock another country code change, this time with AFC not available, and check that the
        // location listener is removed from location updates.
        when(mWifiGlobals.getAfcServerUrlsForCountry(anyString())).thenReturn(null);
        mAfcManager.onCountryCodeChange("00");

        assertEquals(null, (mAfcManager).getAfcServerUrl());
        verify(mLocationManager).removeUpdates(locationListener);
    }

    /**
     * Test that no query is executed if the location passed into AfcManager#onLocationChange is
     * null.
     */
    @Test
    public void testNullLocationChange() {
        mAfcManager = makeAfcManager();
        mAfcManager.onLocationChange(null, false);
        verify(mAfcClient, never()).queryAfcServer(any(AfcLocation.class), any(Handler.class),
                any(AfcClient.Callback.class));
    }

    /**
     * Verify that the AFC Manager triggers a query of the AFC server upon a location change if the
     * availability expiration time of the last server response has expired.
     */
    @Test
    public void testQueryAfterExpiredTime() throws JSONException {
        mAfcManager = makeAfcManager();
        mAfcManager.setIsAfcSupportedInCurrentCountry(true);

        mAfcManager.onLocationChange(mLocation, false);

        verify(mAfcClient).queryAfcServer(any(AfcLocation.class), any(Handler.class),
                mAfcClientCallbackCaptor.capture());
        AfcClient.Callback afcClientCallback = mAfcClientCallbackCaptor.getValue();

        // Set the response to the test AFC server response which has an expiration time that is
        // expired.
        afcClientCallback.onResult(buildSuccessfulSpectrumInquiryResponse(), mAfcLocation);

        // Ensure that the current time is before the expiration date
        when(mClock.getWallClockMillis()).thenReturn(AfcServerResponse
                .convertExpireTimeStringToTimestamp(EXPIRATION_DATE) - 10);
        mAfcManager.onLocationChange(mLocation, false);
        // Ensure that a query is not executed because the expiration time has not passed
        verify(mAfcClient, times(1)).queryAfcServer(any(AfcLocation.class),
                any(Handler.class), any(AfcClient.Callback.class));

        // Ensure that the current time is past the expiration date
        when(mClock.getWallClockMillis()).thenReturn(AfcServerResponse
                .convertExpireTimeStringToTimestamp(EXPIRATION_DATE) + 10);
        mAfcManager.onLocationChange(mLocation, false);
        // Ensure that a query is executed because the expiration time has passed
        verify(mAfcClient, times(2)).queryAfcServer(any(AfcLocation.class),
                any(Handler.class), any(AfcClient.Callback.class));
    }

    /**
     * Verify that the AFC Manager triggers a query of the AFC server if the location
     * change moves outside the bounds of the AfcLocation from the last successful AFC server query.
     */
    @Test
    public void testQueryAfterLocationChange() throws JSONException {
        mAfcManager = makeAfcManager();
        mAfcManager.setIsAfcSupportedInCurrentCountry(true);
        mAfcManager.onLocationChange(mLocation, false);

        verify(mAfcClient).queryAfcServer(any(AfcLocation.class), any(Handler.class),
                mAfcClientCallbackCaptor.capture());
        AfcClient.Callback afcClientCallback = mAfcClientCallbackCaptor.getValue();

        afcClientCallback.onResult(buildSuccessfulSpectrumInquiryResponse(), mAfcLocation);

        // Ensure that the current time is before the expiration date
        when(mClock.getWallClockMillis()).thenReturn(AfcServerResponse
                .convertExpireTimeStringToTimestamp(EXPIRATION_DATE) - 10);

        // Ensure that a query is not executed because the location is inside the AfcLocation
        when(mAfcLocationUtil.checkLocation(any(AfcLocation.class), any(Location.class)))
                .thenReturn(AfcLocationUtil.InBoundsCheckResult.INSIDE_AFC_LOCATION);
        mAfcManager.onLocationChange(mLocation, false);
        verify(mAfcLocationUtil).checkLocation(any(AfcLocation.class), any(Location.class));
        verify(mAfcClient, times(1)).queryAfcServer(any(AfcLocation.class),
                any(Handler.class), any(AfcClient.Callback.class));

        // Ensure that a query is not executed because the location is on the border
        when(mAfcLocationUtil.checkLocation(any(AfcLocation.class), any(Location.class)))
                .thenReturn(AfcLocationUtil.InBoundsCheckResult.ON_BORDER);
        mAfcManager.onLocationChange(mLocation, false);
        verify(mAfcLocationUtil, times(2)).checkLocation(any(AfcLocation
                .class), any(Location.class));
        verify(mAfcClient, times(1)).queryAfcServer(any(AfcLocation.class),
                any(Handler.class), any(AfcClient.Callback.class));

        // Ensure that a query is executed because the location is outside the AfcLocation
        when(mAfcLocationUtil.checkLocation(any(AfcLocation.class), any(Location.class)))
                .thenReturn(AfcLocationUtil.InBoundsCheckResult.OUTSIDE_AFC_LOCATION);
        mAfcManager.onLocationChange(mLocation, false);
        verify(mAfcLocationUtil, times(3)).checkLocation(any(AfcLocation
                .class), any(Location.class));
        verify(mAfcClient, times(2)).queryAfcServer(any(AfcLocation.class),
                any(Handler.class), any(AfcClient.Callback.class));
    }

    /*
     * Verify that when a shell command triggers a location update, that a server query is made
     * regardless of whether AFC is currently supported.
     */
    @Test
    public void testQueryMadeFromShellCommand() {
        mAfcManager = makeAfcManager();
        mAfcManager.setServerUrlAndRequestPropertyPairs(sAfcServerUrl1, sAfcRequestProperties1);
        when(mWifiGlobals.isAfcSupportedOnDevice()).thenReturn(false);
        mAfcManager.onLocationChange(mLocation, true);
        verify(mAfcClient).queryAfcServer(any(AfcLocation.class), any(Handler.class),
                any(AfcClient.Callback.class));
    }
}
