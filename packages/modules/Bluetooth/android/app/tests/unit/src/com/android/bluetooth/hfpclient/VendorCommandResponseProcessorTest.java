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

package com.android.bluetooth.hfpclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothDevice;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VendorCommandResponseProcessorTest {
    private static int TEST_VENDOR_ID = BluetoothAssignedNumbers.APPLE;

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;
    @Mock private NativeInterface mNativeInterface;
    private VendorCommandResponseProcessor mProcessor;

    @Mock
    private AdapterService mAdapterService;
    @Mock
    private HeadsetClientService mHeadsetClientService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        mProcessor = new VendorCommandResponseProcessor(mHeadsetClientService, mNativeInterface);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void sendCommand_withSemicolon() {
        String atCommand = "command;";

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, atCommand, mTestDevice)).isFalse();
    }

    @Test
    public void sendCommand_withNullDevice() {
        String atCommand = "+XAPL=";

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, atCommand, null)).isFalse();
    }

    @Test
    public void sendCommand_withInvalidCommand() {
        String invalidCommand = "invalidCommand";

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, invalidCommand, mTestDevice)).isFalse();
    }

    @Test
    public void sendCommand_withEqualSign() {
        String atCommand = "+XAPL=";
        doReturn(true).when(mNativeInterface).sendATCmd(mTestDevice,
                HeadsetClientHalConstants.HANDSFREECLIENT_AT_CMD_VENDOR_SPECIFIC_CMD, 0, 0,
                atCommand);

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, atCommand, mTestDevice)).isTrue();
    }

    @Test
    public void sendCommand_withQuestionMarkSign() {
        String atCommand = "+APLSIRI?";
        doReturn(true).when(mNativeInterface).sendATCmd(mTestDevice,
                HeadsetClientHalConstants.HANDSFREECLIENT_AT_CMD_VENDOR_SPECIFIC_CMD, 0, 0,
                atCommand);

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, atCommand, mTestDevice)).isTrue();
    }

    @Test
    public void sendCommand_failingToSendATCommand() {
        String atCommand = "+APLSIRI?";
        doReturn(false).when(mNativeInterface).sendATCmd(mTestDevice,
                HeadsetClientHalConstants.HANDSFREECLIENT_AT_CMD_VENDOR_SPECIFIC_CMD, 0, 0,
                atCommand);

        assertThat(mProcessor.sendCommand(TEST_VENDOR_ID, atCommand, mTestDevice)).isFalse();
    }

    @Test
    public void processEvent_withNullDevice() {
        String atString = "+XAPL=";

        assertThat(mProcessor.processEvent(atString, null)).isFalse();
    }

    @Test
    public void processEvent_withInvalidString() {
        String invalidString = "+APLSIRI?";

        assertThat(mProcessor.processEvent(invalidString, mTestDevice)).isFalse();
    }

    @Test
    public void processEvent_withEqualSign() {
        String atString = "+XAPL=";

        assertThat(mProcessor.processEvent(atString, mTestDevice)).isTrue();
    }

    @Test
    public void processEvent_withColonSign() {
        String atString = "+APLSIRI:";

        assertThat(mProcessor.processEvent(atString, mTestDevice)).isTrue();
    }
}