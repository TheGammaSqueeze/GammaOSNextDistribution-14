/*
 * Copyright 2022 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EventUrlProviderTest {
    static final int EVENT_TYPE_ERROR = 10;
    private final EventUrlProvider mEventUrlProvider =
            new EventUrlProvider(new TestDataService());
    private static final byte[] RESPONSE_BYTES = {'A', 'B'};

    @Test public void testGetEventUrlWithEmptyResponse() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putInt("type", 5);
        params.putString("id", "abc");
        assertEquals(
                "odp://5-abc-null-null-null",
                mEventUrlProvider.createEventTrackingUrlWithResponse(
                        params, null, null).toString());
    }

    @Test public void testGetEventUrlReturnsResponseFromService() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putInt("type", 5);
        params.putString("id", "abc");
        assertEquals(
                "odp://5-abc-AB-image/gif-null",
                mEventUrlProvider.createEventTrackingUrlWithResponse(
                        params, RESPONSE_BYTES, "image/gif").toString());
    }

    @Test public void testGetEventUrlWithRedirectReturnsResponseFromService() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putInt("type", 5);
        params.putString("id", "abc");
        assertEquals(
                "odp://5-abc-null-null-http://def",
                mEventUrlProvider.createEventTrackingUrlWithRedirect(
                        params, Uri.parse("http://def"))
                .toString());
    }

    @Test public void testGetEventUrlThrowsOnError() throws Exception {
        // EventType 10 triggers error in the mock service.
        PersistableBundle params = new PersistableBundle();
        params.putInt("type", EVENT_TYPE_ERROR);
        params.putString("id", "abc");
        assertThrows(
                IllegalStateException.class,
                () -> mEventUrlProvider.createEventTrackingUrlWithResponse(
                        params, null, null));
    }

    class TestDataService extends IDataAccessService.Stub {
        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_EVENT_URL) {
                PersistableBundle eventParams = params.getParcelable(
                        Constants.EXTRA_EVENT_PARAMS, PersistableBundle.class);
                int eventType = eventParams.getInt("type");
                String id = eventParams.getString("id");
                byte[] responseDataBytes = params.getByteArray(Constants.EXTRA_RESPONSE_DATA);
                String responseData = (responseDataBytes != null)
                        ? new String(responseDataBytes, StandardCharsets.UTF_8) : "null";
                String mimeType = params.getString(Constants.EXTRA_MIME_TYPE);
                String destinationUrl = params.getString(Constants.EXTRA_DESTINATION_URL);
                if (eventType == EVENT_TYPE_ERROR) {
                    try {
                        callback.onError(Constants.STATUS_INTERNAL_ERROR);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                } else {
                    String url = String.format(
                            "odp://%d-%s-%s-%s-%s", eventType, id, responseData, mimeType,
                            destinationUrl);
                    Bundle result = new Bundle();
                    result.putParcelable(Constants.EXTRA_RESULT, Uri.parse(url));
                    try {
                        callback.onSuccess(result);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                }
            } else {
                throw new IllegalStateException("Unexpected test input");
            }
        }
    }
}
