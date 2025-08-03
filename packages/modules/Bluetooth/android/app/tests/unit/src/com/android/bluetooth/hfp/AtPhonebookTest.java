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

package com.android.bluetooth.hfp;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.util.DevicePolicyUtils;
import com.android.internal.telephony.GsmAlphabet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@RunWith(AndroidJUnit4.class)
public class AtPhonebookTest {
    private static final String INVALID_COMMAND = "invalid_command";
    private Context mTargetContext;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;

    @Mock private AdapterService mAdapterService;
    @Mock private HeadsetNativeInterface mNativeInterface;

    private AtPhonebook mAtPhonebook;
    @Spy
    private BluetoothMethodProxy mHfpMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        BluetoothMethodProxy.setInstanceForTesting(mHfpMethodProxy);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        // Spy on native interface
        mAtPhonebook = new AtPhonebook(mTargetContext, mNativeInterface);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.clearAdapterService(mAdapterService);
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void checkAccessPermission_returnsCorrectPermission() {
        assertThat(mAtPhonebook.checkAccessPermission(mTestDevice)).isEqualTo(
                BluetoothDevice.ACCESS_UNKNOWN);
    }

    @Test
    public void getAndSetCheckingAccessPermission_setCorrectly() {
        mAtPhonebook.setCheckingAccessPermission(true);

        assertThat(mAtPhonebook.getCheckingAccessPermission()).isTrue();
    }

    @Test
    public void handleCscsCommand() {
        mAtPhonebook.handleCscsCommand(INVALID_COMMAND, AtPhonebook.TYPE_READ, mTestDevice);
        verify(mNativeInterface).atResponseString(mTestDevice,
                "+CSCS: \"" + "UTF-8" + "\"");

        mAtPhonebook.handleCscsCommand(INVALID_COMMAND, AtPhonebook.TYPE_TEST, mTestDevice);
        verify(mNativeInterface).atResponseString(mTestDevice,
                "+CSCS: (\"UTF-8\",\"IRA\",\"GSM\")");

        mAtPhonebook.handleCscsCommand(INVALID_COMMAND, AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface, atLeastOnce()).atResponseCode(mTestDevice,
                HeadsetHalConstants.AT_RESPONSE_ERROR, -1);

        mAtPhonebook.handleCscsCommand("command=GSM", AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface, atLeastOnce()).atResponseCode(mTestDevice,
                HeadsetHalConstants.AT_RESPONSE_OK, -1);

        mAtPhonebook.handleCscsCommand("command=ERR", AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_SUPPORTED);

        mAtPhonebook.handleCscsCommand(INVALID_COMMAND, AtPhonebook.TYPE_UNKNOWN, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.TEXT_HAS_INVALID_CHARS);
    }

    @Test
    public void handleCpbsCommand() {
        mAtPhonebook.handleCpbsCommand(INVALID_COMMAND, AtPhonebook.TYPE_READ, mTestDevice);
        int size = mAtPhonebook.getPhonebookResult("ME", true).cursor.getCount();
        int maxSize = mAtPhonebook.getMaxPhoneBookSize(size);
        verify(mNativeInterface).atResponseString(mTestDevice,
                "+CPBS: \"" + "ME" + "\"," + size + "," + maxSize);

        mAtPhonebook.handleCpbsCommand(INVALID_COMMAND, AtPhonebook.TYPE_TEST, mTestDevice);
        verify(mNativeInterface).atResponseString(mTestDevice,
                "+CPBS: (\"ME\",\"SM\",\"DC\",\"RC\",\"MC\")");

        mAtPhonebook.handleCpbsCommand(INVALID_COMMAND, AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_SUPPORTED);

        mAtPhonebook.handleCpbsCommand("command=ERR", AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.OPERATION_NOT_ALLOWED);

        mAtPhonebook.handleCpbsCommand("command=SM", AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface, atLeastOnce()).atResponseCode(mTestDevice,
                HeadsetHalConstants.AT_RESPONSE_OK, -1);

        mAtPhonebook.handleCpbsCommand(INVALID_COMMAND, AtPhonebook.TYPE_UNKNOWN, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.TEXT_HAS_INVALID_CHARS);
    }

    @Test
    public void handleCpbrCommand() {
        mAtPhonebook.handleCpbrCommand(INVALID_COMMAND, AtPhonebook.TYPE_TEST, mTestDevice);
        int size = mAtPhonebook.getPhonebookResult("ME", true).cursor.getCount();
        if (size == 0) {
            size = 1;
        }
        verify(mNativeInterface).atResponseString(mTestDevice, "+CPBR: (1-" + size + "),30,30");
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_OK,
                -1);

        mAtPhonebook.handleCpbrCommand(INVALID_COMMAND, AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                -1);

        mAtPhonebook.handleCpbrCommand("command=ERR", AtPhonebook.TYPE_SET, mTestDevice);
        verify(mNativeInterface).atResponseCode(mTestDevice, HeadsetHalConstants.AT_RESPONSE_ERROR,
                BluetoothCmeError.TEXT_HAS_INVALID_CHARS);

        mAtPhonebook.handleCpbrCommand("command=123,123", AtPhonebook.TYPE_SET, mTestDevice);
        assertThat(mAtPhonebook.getCheckingAccessPermission()).isTrue();

        mAtPhonebook.handleCpbrCommand(INVALID_COMMAND, AtPhonebook.TYPE_UNKNOWN, mTestDevice);
        verify(mNativeInterface, atLeastOnce()).atResponseCode(mTestDevice,
                HeadsetHalConstants.AT_RESPONSE_ERROR, BluetoothCmeError.TEXT_HAS_INVALID_CHARS);
    }

    @Test
    public void processCpbrCommand() {
        mAtPhonebook.handleCpbsCommand("command=SM", AtPhonebook.TYPE_SET, mTestDevice);
        assertThat(mAtPhonebook.processCpbrCommand(mTestDevice)).isEqualTo(
                HeadsetHalConstants.AT_RESPONSE_OK);

        mAtPhonebook.handleCpbsCommand("command=ME", AtPhonebook.TYPE_SET, mTestDevice);
        assertThat(mAtPhonebook.processCpbrCommand(mTestDevice)).isEqualTo(
                HeadsetHalConstants.AT_RESPONSE_OK);

        mAtPhonebook.mCurrentPhonebook = "ER";
        assertThat(mAtPhonebook.processCpbrCommand(mTestDevice)).isEqualTo(
                HeadsetHalConstants.AT_RESPONSE_ERROR);
    }

    @Test
    public void processCpbrCommand_withMobilePhonebook() {
        Cursor mockCursorOne = mock(Cursor.class);
        when(mockCursorOne.getCount()).thenReturn(1);
        when(mockCursorOne.getColumnIndex(Phone.TYPE)).thenReturn(1); //TypeColumn
        when(mockCursorOne.getColumnIndex(Phone.NUMBER)).thenReturn(2); //numberColumn
        when(mockCursorOne.getColumnIndex(Phone.DISPLAY_NAME)).thenReturn(3); // nameColumn
        when(mockCursorOne.getInt(1)).thenReturn(Phone.TYPE_WORK);
        when(mockCursorOne.getString(2)).thenReturn(null);
        when(mockCursorOne.getString(3)).thenReturn(null);
        when(mockCursorOne.moveToNext()).thenReturn(false);
        doReturn(mockCursorOne).when(mHfpMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any());

        mAtPhonebook.mCurrentPhonebook = "ME";
        mAtPhonebook.mCpbrIndex1 = 1;
        mAtPhonebook.mCpbrIndex2 = 2;

        mAtPhonebook.processCpbrCommand(mTestDevice);

        String expected = "+CPBR: " + 1 + ",\"" + "" + "\"," + PhoneNumberUtils.toaFromString("")
                + ",\"" + "" + "/" + AtPhonebook.getPhoneType(Phone.TYPE_WORK) + "\"" + "\r\n\r\n";
        verify(mNativeInterface).atResponseString(mTestDevice, expected);
    }

    @Test
    public void processCpbrCommand_withMissedCalls() {
        Cursor mockCursorOne = mock(Cursor.class);
        when(mockCursorOne.getCount()).thenReturn(1);
        when(mockCursorOne.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).thenReturn(1);
        when(mockCursorOne.getColumnIndexOrThrow(CallLog.Calls.NUMBER_PRESENTATION)).thenReturn(2);
        String number = "1".repeat(31);
        when(mockCursorOne.getString(1)).thenReturn(number);
        when(mockCursorOne.getInt(2)).thenReturn(CallLog.Calls.PRESENTATION_RESTRICTED);
        doReturn(mockCursorOne).when(mHfpMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any());

        Cursor mockCursorTwo = mock(Cursor.class);
        when(mockCursorTwo.moveToFirst()).thenReturn(true);
        String name = "k".repeat(30);
        when(mockCursorTwo.getString(0)).thenReturn(name);
        when(mockCursorTwo.getInt(1)).thenReturn(1);
        doReturn(mockCursorTwo).when(mHfpMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any(), any());

        mAtPhonebook.mCurrentPhonebook = "MC";
        mAtPhonebook.mCpbrIndex1 = 1;
        mAtPhonebook.mCpbrIndex2 = 2;

        mAtPhonebook.processCpbrCommand(mTestDevice);

        String expected = "+CPBR: " + 1 + ",\"" + "" + "\"," + PhoneNumberUtils.toaFromString(
                number) + ",\"" + mTargetContext.getString(R.string.unknownNumber) + "\""
                + "\r\n\r\n";
        verify(mNativeInterface).atResponseString(mTestDevice, expected);
    }

    @Test
    public void processCpbrCommand_withReceivcedCallsAndCharsetGsm() {
        Cursor mockCursorOne = mock(Cursor.class);
        when(mockCursorOne.getCount()).thenReturn(1);
        when(mockCursorOne.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).thenReturn(1);
        when(mockCursorOne.getColumnIndexOrThrow(CallLog.Calls.NUMBER_PRESENTATION)).thenReturn(-1);
        String number = "1".repeat(31);
        when(mockCursorOne.getString(1)).thenReturn(number);
        when(mockCursorOne.getInt(2)).thenReturn(CallLog.Calls.PRESENTATION_RESTRICTED);
        doReturn(mockCursorOne).when(mHfpMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any());

        Cursor mockCursorTwo = mock(Cursor.class);
        when(mockCursorTwo.moveToFirst()).thenReturn(true);
        String name = "k".repeat(30);
        when(mockCursorTwo.getString(0)).thenReturn(name);
        when(mockCursorTwo.getInt(1)).thenReturn(1);
        doReturn(mockCursorTwo).when(mHfpMethodProxy).contentResolverQuery(any(), any(), any(),
                any(), any(), any());

        mAtPhonebook.mCurrentPhonebook = "RC";
        mAtPhonebook.mCpbrIndex1 = 1;
        mAtPhonebook.mCpbrIndex2 = 2;
        mAtPhonebook.mCharacterSet = "GSM";

        mAtPhonebook.processCpbrCommand(mTestDevice);

        String expectedName = new String(GsmAlphabet.stringToGsm8BitPacked(name.substring(0, 28)));
        String expected = "+CPBR: " + 1 + ",\"" + number.substring(0, 30) + "\","
                + PhoneNumberUtils.toaFromString(number) + ",\"" + expectedName + "\"" + "\r\n\r\n";
        verify(mNativeInterface).atResponseString(mTestDevice, expected);
    }

    @Test
    public void processCpbrCommand_doesNotCrashWithEncodingNeededNumber() {
        final String encodingNeededNumber = "###0102124";

        Cursor mockCursorOne = mock(Cursor.class);
        when(mockCursorOne.getCount()).thenReturn(1);
        when(mockCursorOne.getColumnIndex(Phone.TYPE)).thenReturn(1); // TypeColumn
        when(mockCursorOne.getColumnIndex(Phone.NUMBER)).thenReturn(2); // numberColumn
        when(mockCursorOne.getColumnIndex(Phone.DISPLAY_NAME)).thenReturn(-1); // nameColumn
        when(mockCursorOne.getInt(1)).thenReturn(Phone.TYPE_WORK);
        when(mockCursorOne.getString(2)).thenReturn(encodingNeededNumber);
        when(mockCursorOne.moveToNext()).thenReturn(false);
        doReturn(mockCursorOne)
                .when(mHfpMethodProxy)
                .contentResolverQuery(
                        any(),
                        eq(DevicePolicyUtils.getEnterprisePhoneUri(mTargetContext)),
                        any(),
                        any(),
                        any());

        mAtPhonebook.mCurrentPhonebook = "ME";
        mAtPhonebook.mCpbrIndex1 = 1;
        mAtPhonebook.mCpbrIndex2 = 2;

        // This call should not crash
        mAtPhonebook.processCpbrCommand(mTestDevice);
    }

    @Test
    public void setCpbrIndex() {
        int index = 1;

        mAtPhonebook.setCpbrIndex(index);

        assertThat(mAtPhonebook.mCpbrIndex1).isEqualTo(index);
        assertThat(mAtPhonebook.mCpbrIndex2).isEqualTo(index);
    }

    @Test
    public void resetAtState() {
        mAtPhonebook.resetAtState();

        assertThat(mAtPhonebook.getCheckingAccessPermission()).isFalse();
    }

    @Test
    public void getPhoneType() {
        assertThat(AtPhonebook.getPhoneType(Phone.TYPE_HOME)).isEqualTo("H");
        assertThat(AtPhonebook.getPhoneType(Phone.TYPE_MOBILE)).isEqualTo("M");
        assertThat(AtPhonebook.getPhoneType(Phone.TYPE_WORK)).isEqualTo("W");
        assertThat(AtPhonebook.getPhoneType(Phone.TYPE_FAX_WORK)).isEqualTo("F");
        assertThat(AtPhonebook.getPhoneType(Phone.TYPE_CUSTOM)).isEqualTo("O");
    }
}
