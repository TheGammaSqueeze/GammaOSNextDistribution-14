/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Geocoder.GeocodeListener;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class GeocoderTest {
    private static final String TAG = GeocoderTest.class.getSimpleName();

    private Context mContext;
    private Geocoder mGeocoder;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mGeocoder = new Geocoder(mContext, Locale.US);

        // geocoding is not supported for instant apps until S (b/238831704)
        assumeTrue(
                !mContext.getPackageManager().isInstantApp() || VERSION.SDK_INT >= VERSION_CODES.S);
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @Test
    public void testGetFromLocation() {
        assumeTrue(Geocoder.isPresent());

        Runnable runnable = mock(Runnable.class);
        mGeocoder.getFromLocation(60, 30, 5, new TestGeocodeListener(runnable));
        verify(runnable, timeout(10000)).run();
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @Test
    public void testGetFromLocation_sync() {
        assumeTrue(Geocoder.isPresent());

        try {
            mGeocoder.getFromLocation(60, 30, 5);
        } catch (IOException e) {
            // test succeeds, we don't care about geocoding failure
            Log.e(TAG, "getFromLocation() request failed", e);
        }
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocation")
    @Test
    public void testGetFromLocation_badInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(-91, 30, 5, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(91, 30, 5, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(10, -181, 5, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> mGeocoder.getFromLocation(10, 181, 5, addresses -> {}));
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @Test
    public void testGetFromLocationName() {
        assumeTrue(Geocoder.isPresent());

        Runnable runnable = mock(Runnable.class);
        mGeocoder.getFromLocationName("Dalvik,Iceland", 5, new TestGeocodeListener(runnable));
        verify(runnable, timeout(10000)).run();
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @Test
    public void testGetFromLocationName_sync() {
        assumeTrue(Geocoder.isPresent());

        try {
            mGeocoder.getFromLocationName("Dalvik,Iceland", 5);
        } catch (IOException e) {
            // test succeeds, we don't care about geocoding failure
            Log.e(TAG, "getFromLocationName() request failed", e);
        }
    }

    @ApiTest(apis = "android.location.Geocoder#getFromLocationName")
    @Test
    public void testGetFromLocationName_badInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mGeocoder.getFromLocationName(null, 5, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mGeocoder.getFromLocationName(
                                "Beijing", 5, -91, 100, 45, 130, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mGeocoder.getFromLocationName(
                                "Beijing", 5, 25, 190, 45, 130, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mGeocoder.getFromLocationName(
                                "Beijing", 5, 25, 100, 91, 130, addresses -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mGeocoder.getFromLocationName(
                                "Beijing", 5, 25, 100, 45, -181, addresses -> {}));
    }

    @ApiTest(
            apis = {
                "android.location.Geocoder.GeocodeListener#onGeocode",
                "android.location.Geocoder.GeocodeListener#onError",
            })
    @Test
    public void testGeocodeListener() {
        GeocodeListener listener = addresses -> {};
        listener.onGeocode(new ArrayList<>());
        listener.onError(null);
    }

    private static class TestGeocodeListener implements GeocodeListener {
        private final Runnable mRunnable;

        TestGeocodeListener(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void onGeocode(List<Address> addresses) {
            mRunnable.run();
        }

        @Override
        public void onError(String errorMessage) {
            mRunnable.run();
        }
    }
}
