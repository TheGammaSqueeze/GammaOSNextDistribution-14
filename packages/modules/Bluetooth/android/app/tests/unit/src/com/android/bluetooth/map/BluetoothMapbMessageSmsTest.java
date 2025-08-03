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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.telephony.SmsManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.map.BluetoothMapSmsPdu.SmsPdu;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapbMessageSmsTest {
    private static final String TEST_SMS_BODY = "test_sms_body";
    private static final String TEST_MESSAGE = "test";
    private static final String TEST_ADDRESS = "12";

    private SmsManager mSmsManager = SmsManager.getDefault();
    private Context mTargetContext;
    private ArrayList<SmsPdu> TEST_SMS_BODY_PDUS;

    @Before
    public void setUp() throws Exception {
        // Do not run test if sms is not supported
        Assume.assumeTrue(mSmsManager.isImsSmsSupported());
        mTargetContext = InstrumentationRegistry.getTargetContext();
        TEST_SMS_BODY_PDUS = BluetoothMapSmsPdu.getSubmitPdus(mTargetContext, TEST_MESSAGE,
                TEST_ADDRESS);
    }

    @Test
    public void settersAndGetters() {
        BluetoothMapbMessageSms messageSms = new BluetoothMapbMessageSms();
        messageSms.setSmsBody(TEST_SMS_BODY);
        messageSms.setSmsBodyPdus(TEST_SMS_BODY_PDUS);

        assertThat(messageSms.getSmsBody()).isEqualTo(TEST_SMS_BODY);
        assertThat(messageSms.mEncoding).isEqualTo(TEST_SMS_BODY_PDUS.get(0).getEncodingString());
    }

    @Test
    public void parseMsgInit() {
        BluetoothMapbMessageSms messageSms = new BluetoothMapbMessageSms();
        messageSms.parseMsgInit();
        assertThat(messageSms.getSmsBody()).isEqualTo("");
    }

    @Test
    public void encodeToByteArray_thenAddByParsing() throws Exception {
        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_GSM);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);
        messageSmsToEncode.setSmsBodyPdus(TEST_SMS_BODY_PDUS);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);

        BluetoothMapbMessage messageParsed = BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_NATIVE);
        assertThat(messageParsed).isInstanceOf(BluetoothMapbMessageSms.class);
        BluetoothMapbMessageSms messageSmsParsed = (BluetoothMapbMessageSms) messageParsed;
        assertThat(messageSmsParsed.getSmsBody()).isEqualTo(TEST_MESSAGE);
    }

    @Test
    public void encodeToByteArray_withEmptyMessage_thenAddByParsing() throws Exception {
        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_GSM);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);

        BluetoothMapbMessage messageParsed = BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_UTF8);
        assertThat(messageParsed).isInstanceOf(BluetoothMapbMessageSms.class);
        BluetoothMapbMessageSms messageSmsParsed = (BluetoothMapbMessageSms) messageParsed;
        assertThat(messageSmsParsed.getSmsBody()).isEqualTo("");
    }
}