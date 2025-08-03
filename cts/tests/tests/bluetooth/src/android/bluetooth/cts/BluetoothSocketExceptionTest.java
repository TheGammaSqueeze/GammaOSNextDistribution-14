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

package android.bluetooth.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.bluetooth.BluetoothSocketException;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

/** Unit test to test APIs and functionality for {@link BluetoothSocketException}. */
public final class BluetoothSocketExceptionTest extends AndroidTestCase {

    @SmallTest
    public void test_getErrorCode_returnsCorrectErrorCode() {
        BluetoothSocketException exception =
                new BluetoothSocketException(BluetoothSocketException.SOCKET_CONNECTION_FAILURE);

        assertEquals(exception.getErrorCode(), BluetoothSocketException.SOCKET_CONNECTION_FAILURE);
    }

    @SmallTest
    public void test_getMessage_returnsCustomErrorMsg() {
        String customErrMsg = "This is a custom error message";
        BluetoothSocketException exception =
                new BluetoothSocketException(BluetoothSocketException.UNSPECIFIED, customErrMsg);

        assertEquals(exception.getMessage(), customErrMsg);
    }

    @SmallTest
    public void test_getMessage_returnsErrorMsgWhenOnlyCodeIsProvided() {
        BluetoothSocketException exception =
                new BluetoothSocketException(BluetoothSocketException.UNSPECIFIED);

        assertNotNull(exception.getMessage());
    }
}
