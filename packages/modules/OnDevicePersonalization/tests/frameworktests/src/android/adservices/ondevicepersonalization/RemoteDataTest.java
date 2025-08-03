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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit Tests of RemoteData API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RemoteDataTest {
    KeyValueStore mRemoteData = new RemoteDataImpl(
            IDataAccessService.Stub.asInterface(
                    new RemoteDataService()));

    @Test
    public void testLookupSuccess() throws Exception {
        assertArrayEquals(new byte[] {1, 2, 3}, mRemoteData.get("a"));
        assertArrayEquals(new byte[] {7, 8, 9}, mRemoteData.get("c"));
        assertNull(mRemoteData.get("e"));
    }

    @Test
    public void testLookupError() {
        // Triggers an expected error in the mock service.
        assertNull(mRemoteData.get("z"));
    }

    @Test
    public void testKeysetSuccess() {
        Set<String> expectedResult = new HashSet<>();
        expectedResult.add("a");
        expectedResult.add("b");
        expectedResult.add("c");

        assertEquals(expectedResult, mRemoteData.keySet());
    }

    public static class RemoteDataService extends IDataAccessService.Stub {
        HashMap<String, byte[]> mContents = new HashMap<String, byte[]>();

        public RemoteDataService() {
            mContents.put("a", new byte[] {1, 2, 3});
            mContents.put("b", new byte[] {4, 5, 6});
            mContents.put("c", new byte[] {7, 8, 9});
        }

        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {

            if (operation == Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET) {
                Bundle result = new Bundle();
                result.putSerializable(Constants.EXTRA_RESULT,
                        new HashSet<>(mContents.keySet()));
                try {
                    callback.onSuccess(result);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }

            if (operation != Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP) {
                throw new IllegalArgumentException("op: " + operation);
            }

            String key = params.getString(Constants.EXTRA_LOOKUP_KEYS);
            if (key == null) {
                throw new NullPointerException("key");
            }

            if (key.equals("z")) {
                // Raise expected error.
                try {
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            byte[] value = null;
            if (mContents.containsKey(key)) {
                value = mContents.get(key);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT, new ByteArrayParceledSlice(value));
            try {
                callback.onSuccess(result);
            } catch (RemoteException e) {
                // Ignored.
            }
        }
    }
}
