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

package android.location.cts.fine;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.location.Address;
import android.location.provider.ForwardGeocodeRequest;
import android.location.provider.GeocodeProviderBase;
import android.location.provider.IGeocodeCallback;
import android.location.provider.IGeocodeProvider;
import android.location.provider.ReverseGeocodeRequest;
import android.os.OutcomeReceiver;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class GeocodeProviderBaseTest {

    private static final String TAG = "GeocodeProviderBaseTest";

    @Rule public final MockitoRule mocks = MockitoJUnit.rule();

    private Context mContext;

    @Mock private IGeocodeCallback mMock;

    private MyProvider mGeocodeProvider;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mGeocodeProvider = new MyProvider(mContext, TAG);
    }

    @ApiTest(apis = "android.location.provider.GeocodeProviderBase#onReverseGeocode")
    @Test
    public void testReverseGeocode() throws Exception {
        ReverseGeocodeRequest request =
                new ReverseGeocodeRequest.Builder(1, 2, 3, Locale.CANADA, 4, "package")
                        .setCallingAttributionTag("attribution")
                        .build();
        mGeocodeProvider.asProvider().reverseGeocode(request, mMock);
        verify(mMock).onResults(Collections.emptyList());
        verify(mMock, never()).onError(anyString());
    }

    @ApiTest(apis = "android.location.provider.GeocodeProviderBase#onForwardGeocode")
    @Test
    public void testGetFromLocationName() throws Exception {
        ForwardGeocodeRequest request =
                new ForwardGeocodeRequest.Builder(
                                "location", 1, 2, 3, 4, 5, Locale.CANADA, 4, "package")
                        .setCallingAttributionTag("attribution")
                        .build();
        mGeocodeProvider.asProvider().forwardGeocode(request, mMock);
        verify(mMock).onResults(Collections.emptyList());
        verify(mMock, never()).onError(anyString());
    }

    private static class MyProvider extends GeocodeProviderBase {

        MyProvider(Context context, String tag) {
            super(context, tag);
        }

        public IGeocodeProvider asProvider() {
            return IGeocodeProvider.Stub.asInterface(getBinder());
        }

        @Override
        public void onReverseGeocode(
                ReverseGeocodeRequest request, OutcomeReceiver<List<Address>, Exception> callback) {
            callback.onResult(Collections.emptyList());
        }

        @Override
        public void onForwardGeocode(
                ForwardGeocodeRequest request, OutcomeReceiver<List<Address>, Exception> callback) {
            callback.onResult(Collections.emptyList());
        }
    }
}
