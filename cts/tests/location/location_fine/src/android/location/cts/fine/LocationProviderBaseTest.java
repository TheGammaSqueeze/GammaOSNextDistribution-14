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

package android.location.cts.fine;

import static android.location.Location.EXTRA_NO_GPS_LOCATION;

import static com.android.compatibility.common.util.LocationUtils.createLocation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.location.Location;
import android.location.provider.ILocationProvider;
import android.location.provider.ILocationProviderManager;
import android.location.provider.LocationProviderBase;
import android.location.provider.LocationProviderBase.OnFlushCompleteCallback;
import android.location.provider.ProviderProperties;
import android.location.provider.ProviderRequest;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class LocationProviderBaseTest {

    private static final String TAG = "LocationProviderBaseTest";
    private static final ProviderProperties PROPERTIES = new ProviderProperties.Builder().build();

    private Random mRandom;

    private @Mock ILocationProviderManager.Stub mManager;
    private @Mock LocationProviderBase mMock;

    private MyProvider mLocationProvider;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        long seed = System.currentTimeMillis();
        Log.i(TAG, "location random seed: " + seed);

        mRandom = new Random(seed);

        mLocationProvider = new MyProvider(ApplicationProvider.getApplicationContext(), TAG,
                PROPERTIES, mMock);
        mLocationProvider.asProvider().setLocationProviderManager(mManager);
    }

    @Test
    public void testAllowed() throws Exception {
        assertThat(mLocationProvider.isAllowed()).isTrue();

        mLocationProvider.setAllowed(false);
        verify(mManager).onSetAllowed(false);
        assertThat(mLocationProvider.isAllowed()).isFalse();
    }

    @Test
    public void testProperties() throws Exception {
        assertThat(mLocationProvider.getProperties()).isEqualTo(PROPERTIES);

        ProviderProperties properties = new ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setHasBearingSupport(true)
                .setHasSpeedSupport(true)
                .build();
        mLocationProvider.setProperties(properties);
        verify(mManager).onSetProperties(properties);
        assertThat(mLocationProvider.getProperties()).isEqualTo(properties);
    }

    @Test
    public void testReportLocation() throws Exception {
        Location location = createLocation("test", mRandom);

        mLocationProvider.reportLocation(location);
        verify(mManager).onReportLocation(location);
    }

    @Test
    public void testReportLocation_stripExtras() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_NO_GPS_LOCATION, createLocation("test", mRandom));
        bundle.putFloat("indoorProbability", 0.75f);
        bundle.putParcelable("coarseLocation", createLocation("test", mRandom));
        Location location = createLocation("test", mRandom);
        location.setExtras(bundle);

        Location expected = new Location(location);
        expected.setExtras(null);
        mLocationProvider.reportLocation(location);
        verify(mManager).onReportLocation(expected);
    }

    @Test
    public void testReportLocations() throws Exception {
        List<Location> locations = Arrays.asList(
                createLocation("test", mRandom),
                createLocation("test", mRandom));

        mLocationProvider.reportLocations(locations);
        verify(mManager).onReportLocations(locations);
    }

    @Test
    public void testReportLocations_stripExtras() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_NO_GPS_LOCATION, createLocation("test", mRandom));
        bundle.putFloat("indoorProbability", 0.75f);
        bundle.putParcelable("coarseLocation", createLocation("test", mRandom));
        Location location1 = createLocation("test", mRandom);
        location1.setExtras(bundle);
        Location location2 = createLocation("test", mRandom);
        location2.setExtras(bundle);
        List<Location> locations = Arrays.asList(location1, location2);

        Location expected1 = new Location(location1);
        expected1.setExtras(null);
        Location expected2 = new Location(location2);
        expected2.setExtras(null);
        List<Location> expected = Arrays.asList(expected1, expected2);

        mLocationProvider.reportLocations(locations);
        verify(mManager).onReportLocations(expected);
    }

    @Test
    public void testOnSetRequest() throws Exception {
        ProviderRequest providerRequest = new ProviderRequest.Builder().setIntervalMillis(500).build();
        mLocationProvider.asProvider().setRequest(providerRequest);
        verify(mMock).onSetRequest(providerRequest);
    }

    @Test
    public void testOnFlush() throws Exception {
        mLocationProvider.asProvider().flush();
        verify(mMock).onFlush(any(OnFlushCompleteCallback.class));
        verify(mManager).onFlushComplete();
    }

    @Test
    public void testOnSendExtraCommand() throws Exception {
        mLocationProvider.asProvider().sendExtraCommand("command", new Bundle());
        verify(mMock).onSendExtraCommand(eq("command"), any(Bundle.class));
    }

    public static class MyProvider extends LocationProviderBase {

        private final LocationProviderBase mMock;

        public MyProvider(@NonNull Context context, @NonNull String tag,
                @NonNull ProviderProperties properties, LocationProviderBase mock) {
            super(context, tag, properties);
            mMock = mock;
        }

        public ILocationProvider asProvider() {
            return ILocationProvider.Stub.asInterface(getBinder());
        }

        @Override
        public void onSetRequest(@NonNull ProviderRequest request) {
            mMock.onSetRequest(request);
        }

        @Override
        public void onFlush(@NonNull OnFlushCompleteCallback callback) {
            mMock.onFlush(callback);
            callback.onFlushComplete();
        }

        @Override
        public void onSendExtraCommand(@NonNull String command, @Nullable Bundle extras) {
            mMock.onSendExtraCommand(command, extras);
        }
    }
}
