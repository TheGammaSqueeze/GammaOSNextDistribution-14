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
package com.android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.ProfileService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Test for Utils.java
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UtilsTest {
    @Test
    public void byteArrayToShort() {
        byte[] valueBuf = new byte[] {0x01, 0x02};
        short s = Utils.byteArrayToShort(valueBuf);
        assertThat(s).isEqualTo(0x0201);
    }

    @Test
    public void byteArrayToString() {
        byte[] valueBuf = new byte[] {0x01, 0x02};
        String str = Utils.byteArrayToString(valueBuf);
        assertThat(str).isEqualTo("01 02");
    }

    @Test
    public void uuidsToByteArray() {
        ParcelUuid[] uuids = new ParcelUuid[] {
                new ParcelUuid(new UUID(10, 20)),
                new ParcelUuid(new UUID(30, 40))
        };
        ByteBuffer converter = ByteBuffer.allocate(uuids.length * 16);
        converter.order(ByteOrder.BIG_ENDIAN);
        converter.putLong(0, 10);
        converter.putLong(8, 20);
        converter.putLong(16, 30);
        converter.putLong(24, 40);
        assertThat(Utils.uuidsToByteArray(uuids)).isEqualTo(converter.array());
    }

    @Test
    public void checkServiceAvailable() {
        final String tag = "UTILS_TEST";
        assertThat(Utils.checkServiceAvailable(null, tag)).isFalse();

        ProfileService mockProfile = Mockito.mock(ProfileService.class);
        when(mockProfile.isAvailable()).thenReturn(false);
        assertThat(Utils.checkServiceAvailable(mockProfile, tag)).isFalse();

        when(mockProfile.isAvailable()).thenReturn(true);
        assertThat(Utils.checkServiceAvailable(mockProfile, tag)).isTrue();
    }

    @Test
    public void blockedByLocationOff() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        UserHandle userHandle = new UserHandle(UserHandle.USER_SYSTEM);
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        boolean enableStatus = locationManager.isLocationEnabledForUser(userHandle);
        assertThat(Utils.blockedByLocationOff(context, userHandle)).isEqualTo(!enableStatus);

        locationManager.setLocationEnabledForUser(!enableStatus, userHandle);
        assertThat(Utils.blockedByLocationOff(context, userHandle)).isEqualTo(enableStatus);

        locationManager.setLocationEnabledForUser(enableStatus, userHandle);
    }

    @Test
    public void checkCallerHasCoarseLocation_doesNotCrash() {
        Context context = InstrumentationRegistry.getTargetContext();
        UserHandle userHandle = new UserHandle(UserHandle.USER_SYSTEM);
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        boolean enabledStatus = locationManager.isLocationEnabledForUser(userHandle);

        locationManager.setLocationEnabledForUser(false, userHandle);
        assertThat(Utils.checkCallerHasCoarseLocation(
                       context, context.getAttributionSource(), userHandle))
                .isFalse();

        locationManager.setLocationEnabledForUser(true, userHandle);
        Utils.checkCallerHasCoarseLocation(context, context.getAttributionSource(), userHandle);
        if (!enabledStatus) {
            locationManager.setLocationEnabledForUser(false, userHandle);
        }
    }

    @Test
    public void checkCallerHasCoarseOrFineLocation_doesNotCrash() {
        Context context = InstrumentationRegistry.getTargetContext();
        UserHandle userHandle = new UserHandle(UserHandle.USER_SYSTEM);
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        boolean enabledStatus = locationManager.isLocationEnabledForUser(userHandle);

        locationManager.setLocationEnabledForUser(false, userHandle);
        assertThat(Utils.checkCallerHasCoarseOrFineLocation(
                       context, context.getAttributionSource(), userHandle))
                .isFalse();

        locationManager.setLocationEnabledForUser(true, userHandle);
        Utils.checkCallerHasCoarseOrFineLocation(
                context, context.getAttributionSource(), userHandle);
        if (!enabledStatus) {
            locationManager.setLocationEnabledForUser(false, userHandle);
        }
    }

    @Test
    public void checkPermissionMethod_doesNotCrash() {
        Context context = InstrumentationRegistry.getTargetContext();
        try {
            Utils.checkAdvertisePermissionForDataDelivery(context, null, "message");
            Utils.checkAdvertisePermissionForPreflight(context);
            Utils.checkCallerHasWriteSmsPermission(context);
            Utils.checkScanPermissionForPreflight(context);
            Utils.checkConnectPermissionForPreflight(context);
        } catch (SecurityException e) {
            // SecurityException could happen.
        }
    }

    @Test
    public void enforceDumpPermission_doesNotCrash() {
        Context context = InstrumentationRegistry.getTargetContext();
        try {
            Utils.enforceDumpPermission(context);
        } catch (SecurityException e) {
            // SecurityException could happen.
        }
    }

    @Test
    public void getLoggableAddress() {
        assertThat(Utils.getLoggableAddress(null)).isEqualTo("00:00:00:00:00:00");

        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 1);
        String loggableAddress = "xx:xx:xx:xx:" + device.getAddress().substring(12);
        assertThat(Utils.getLoggableAddress(device)).isEqualTo(loggableAddress);
    }

    @Test
    public void checkCallerIsSystemMethods_doesNotCrash() {
        Context context = InstrumentationRegistry.getTargetContext();
        String tag = "test_tag";

        Utils.checkCallerIsSystemOrActiveOrManagedUser(context, tag);
        Utils.checkCallerIsSystemOrActiveOrManagedUser(null, tag);
        Utils.checkCallerIsSystemOrActiveUser(tag);
    }

    @Test
    public void testCopyStream() throws Exception {
        byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bufferSize = 4;

        Utils.copyStream(in, out, bufferSize);

        assertThat(out.toByteArray()).isEqualTo(data);
    }

    @Test
    public void debugGetAdapterStateString() {
        assertThat(Utils.debugGetAdapterStateString(BluetoothAdapter.STATE_OFF))
                .isEqualTo("STATE_OFF");
        assertThat(Utils.debugGetAdapterStateString(BluetoothAdapter.STATE_ON))
                .isEqualTo("STATE_ON");
        assertThat(Utils.debugGetAdapterStateString(BluetoothAdapter.STATE_TURNING_ON))
                .isEqualTo("STATE_TURNING_ON");
        assertThat(Utils.debugGetAdapterStateString(BluetoothAdapter.STATE_TURNING_OFF))
                .isEqualTo("STATE_TURNING_OFF");
        assertThat(Utils.debugGetAdapterStateString(-124))
                .isEqualTo("UNKNOWN");
    }

    @Test
    public void ellipsize() {
        if (!Build.TYPE.equals("user")) {
            // Only ellipsize release builds
            String input = "a_long_string";
            assertThat(Utils.ellipsize(input)).isEqualTo(input);
            return;
        }

        assertThat(Utils.ellipsize("ab")).isEqualTo("ab");
        assertThat(Utils.ellipsize("abc")).isEqualTo("a⋯c");
        assertThat(Utils.ellipsize(null)).isEqualTo(null);
    }

    @Test
    public void safeCloseStream_inputStream_doesNotCrash() throws Exception {
        InputStream is = mock(InputStream.class);
        Utils.safeCloseStream(is);
        verify(is).close();

        Mockito.clearInvocations(is);
        doThrow(new IOException()).when(is).close();
        Utils.safeCloseStream(is);
    }

    @Test
    public void safeCloseStream_outputStream_doesNotCrash() throws Exception {
        OutputStream os = mock(OutputStream.class);
        Utils.safeCloseStream(os);
        verify(os).close();

        Mockito.clearInvocations(os);
        doThrow(new IOException()).when(os).close();
        Utils.safeCloseStream(os);
    }

    @Test
    public void truncateUtf8_toZeroLength_isEmpty() {
        assertThat(Utils.truncateStringForUtf8Storage("abc", 0)).isEmpty();
    }

    @Test
    public void truncateUtf8_longCase_isExpectedResult() {
        StringBuilder builder = new StringBuilder();

        int n = 50;
        for (int i = 0; i < 2 * n; i++) {
            builder.append("哈");
        }
        String initial = builder.toString();
        String result = Utils.truncateStringForUtf8Storage(initial, n);

        // Result should be the beginning of initial
        assertThat(initial.startsWith(result)).isTrue();

        // Result should take less than n bytes in UTF-8
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isAtMost(n);

        // result + the next codePoint should take strictly more than
        // n bytes in UTF-8
        assertThat(
                        initial.substring(0, initial.offsetByCodePoints(result.length(), 1))
                                .getBytes(StandardCharsets.UTF_8)
                                .length)
                .isGreaterThan(n);
    }

    @Test
    public void truncateUtf8_untruncatedString_isEqual() {
        String s = "sf\u20ACgk\u00E9ls\u00E9fg";
        assertThat(Utils.truncateStringForUtf8Storage(s, 100)).isEqualTo(s);
    }

    @Test
    public void truncateUtf8_inMiddleOfSurrogate_isStillUtf8() {
        StringBuilder builder = new StringBuilder();
        String beginning = "a";
        builder.append(beginning);
        builder.append(Character.toChars(0x1D11E));

        // \u1D11E is a surrogate and needs 4 bytes in UTF-8. beginning == "a" uses
        // only 1 bytes in UTF8
        // As we allow only 3 bytes for the whole string, so just 2 for this
        // codePoint, there is not enough place and the string will be truncated
        // just before it
        assertThat(Utils.truncateStringForUtf8Storage(builder.toString(), 3)).isEqualTo(beginning);
    }

    @Test
    public void truncateUtf8_inMiddleOfChar_isStillUtf8() {
        StringBuilder builder = new StringBuilder();
        String beginning = "a";
        builder.append(beginning);
        builder.append(Character.toChars(0x20AC));

        // Like above, \u20AC uses 3 bytes in UTF-8, with "beginning", that makes
        // 4 bytes so it is too big and should be truncated
        assertThat(Utils.truncateStringForUtf8Storage(builder.toString(), 3)).isEqualTo(beginning);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void truncateUtf8_toNegativeSize_ThrowsException() {
        Utils.truncateStringForUtf8Storage("abc", -1);
    }
}
