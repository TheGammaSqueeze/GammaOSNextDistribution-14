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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.map.BluetoothMapSmsPdu.SmsPdu;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapSmsPduTest {
    private static final String TEST_TEXT = "test";
    // Text below size 160 only need one SMS part
    private static final String TEST_TEXT_WITH_TWO_SMS_PARTS = "a".repeat(161);
    private static final String TEST_DESTINATION_ADDRESS = "12";
    private static final int TEST_TYPE = BluetoothMapSmsPdu.SMS_TYPE_GSM;
    private static final long TEST_DATE = 1;

    private byte[] TEST_DATA;
    private int TEST_ENCODING;
    private int TEST_LANGUAGE_TABLE;

    private SmsManager mSmsManager = SmsManager.getDefault();
    @Mock
    private Context mTargetContext;
    @Mock
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mTargetContext.getSystemServiceName(TelephonyManager.class)).thenReturn(
                "TELEPHONY_SERVICE");
        when(mTargetContext.getSystemService("TELEPHONY_SERVICE")).thenReturn(mTelephonyManager);

        int[] ted = SmsMessage.calculateLength((CharSequence) TEST_TEXT, false);
        TEST_ENCODING = ted[3];
        TEST_LANGUAGE_TABLE = ted[4];
        TEST_DATA = SmsMessage.getSubmitPdu(null, TEST_DESTINATION_ADDRESS, TEST_TEXT,
                false).encodedMessage;
    }

    @Test
    public void constructor_withDataAndType() {
        SmsPdu smsPdu = new SmsPdu(TEST_DATA, TEST_TYPE);
        int offsetExpected = 2 + ((TEST_DATA[2] + 1) & 0xff) / 2 + 5;

        assertThat(smsPdu.getData()).isEqualTo(TEST_DATA);
        assertThat(smsPdu.getEncoding()).isEqualTo(-1);
        assertThat(smsPdu.getLanguageTable()).isEqualTo(-1);
        assertThat(smsPdu.getLanguageShiftTable()).isEqualTo(-1);
        assertThat(smsPdu.getUserDataMsgOffset()).isEqualTo(offsetExpected);
        assertThat(smsPdu.getUserDataMsgSize()).isEqualTo(TEST_DATA.length - (offsetExpected));
    }

    @Test
    public void constructor_withAllParameters() {
        SmsPdu smsPdu = new SmsPdu(TEST_DATA, TEST_ENCODING, TEST_TYPE, TEST_LANGUAGE_TABLE);

        assertThat(smsPdu.getData()).isEqualTo(TEST_DATA);
        assertThat(smsPdu.getEncoding()).isEqualTo(TEST_ENCODING);
        assertThat(smsPdu.getType()).isEqualTo(TEST_TYPE);
        assertThat(smsPdu.getLanguageTable()).isEqualTo(TEST_LANGUAGE_TABLE);
    }

    @Test
    public void getSubmitPdus_withTypeGSM_whenMsgCountIsMoreThanOne() throws Exception {
        // Do not run test if sms is not supported
        Assume.assumeTrue(mSmsManager.isImsSmsSupported());
        when(mTelephonyManager.getCurrentPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_GSM);

        ArrayList<SmsPdu> pdus = BluetoothMapSmsPdu.getSubmitPdus(mTargetContext,
                TEST_TEXT_WITH_TWO_SMS_PARTS, null);

        assertThat(pdus.size()).isEqualTo(2);
        assertThat(pdus.get(0).getType()).isEqualTo(BluetoothMapSmsPdu.SMS_TYPE_GSM);

        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_GSM);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);
        messageSmsToEncode.setSmsBodyPdus(pdus);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);
        BluetoothMapbMessage messageParsed = BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_NATIVE);

        assertThat(messageParsed).isInstanceOf(BluetoothMapbMessageSms.class);
        BluetoothMapbMessageSms messageSmsParsed = (BluetoothMapbMessageSms) messageParsed;
        assertThat(messageSmsParsed.getSmsBody()).isEqualTo(TEST_TEXT_WITH_TWO_SMS_PARTS);
    }

    @Test
    public void getSubmitPdus_withTypeCDMA() throws Exception {
        // Do not run test if sms is not supported
        Assume.assumeTrue(mSmsManager.isImsSmsSupported());
        when(mTelephonyManager.getCurrentPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_CDMA);

        ArrayList<SmsPdu> pdus = BluetoothMapSmsPdu.getSubmitPdus(mTargetContext, TEST_TEXT, null);

        assertThat(pdus.size()).isEqualTo(1);
        assertThat(pdus.get(0).getType()).isEqualTo(BluetoothMapSmsPdu.SMS_TYPE_CDMA);

        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_CDMA);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);
        messageSmsToEncode.setSmsBodyPdus(pdus);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);
        BluetoothMapbMessage messageParsed = BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_NATIVE);

        assertThat(messageParsed).isInstanceOf(BluetoothMapbMessageSms.class);
    }

    @Test
    public void getDeliverPdus_withTypeGSM() throws Exception {
        // Do not run test if sms is not supported
        Assume.assumeTrue(mSmsManager.isImsSmsSupported());
        when(mTelephonyManager.getCurrentPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_GSM);

        ArrayList<SmsPdu> pdus = BluetoothMapSmsPdu.getDeliverPdus(mTargetContext, TEST_TEXT,
                TEST_DESTINATION_ADDRESS, TEST_DATE);

        assertThat(pdus.size()).isEqualTo(1);
        assertThat(pdus.get(0).getType()).isEqualTo(BluetoothMapSmsPdu.SMS_TYPE_GSM);

        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_GSM);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);
        messageSmsToEncode.setSmsBodyPdus(pdus);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);

        assertThrows(IllegalArgumentException.class, () -> BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_NATIVE));
    }

    @Test
    public void getDeliverPdus_withTypeCDMA() throws Exception {
        // Do not run test if sms is not supported
        Assume.assumeTrue(mSmsManager.isImsSmsSupported());
        when(mTelephonyManager.getCurrentPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_CDMA);

        ArrayList<SmsPdu> pdus = BluetoothMapSmsPdu.getDeliverPdus(mTargetContext, TEST_TEXT,
                TEST_DESTINATION_ADDRESS, TEST_DATE);

        assertThat(pdus.size()).isEqualTo(1);
        assertThat(pdus.get(0).getType()).isEqualTo(BluetoothMapSmsPdu.SMS_TYPE_CDMA);

        BluetoothMapbMessageSms messageSmsToEncode = new BluetoothMapbMessageSms();
        messageSmsToEncode.setType(BluetoothMapUtils.TYPE.SMS_CDMA);
        messageSmsToEncode.setFolder("placeholder");
        messageSmsToEncode.setStatus(true);
        messageSmsToEncode.setSmsBodyPdus(pdus);

        byte[] encodedMessageSms = messageSmsToEncode.encode();
        InputStream inputStream = new ByteArrayInputStream(encodedMessageSms);

        assertThrows(IllegalArgumentException.class, () -> BluetoothMapbMessage.parse(inputStream,
                BluetoothMapAppParams.CHARSET_NATIVE));
    }

    @Test
    public void getEncodingString() {
        SmsPdu smsPduGsm7bitWithLanguageTableZero = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_7BIT,
                BluetoothMapSmsPdu.SMS_TYPE_GSM, 0);
        assertThat(smsPduGsm7bitWithLanguageTableZero.getEncodingString()).isEqualTo("G-7BIT");

        SmsPdu smsPduGsm7bitWithLanguageTableOne = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_7BIT,
                BluetoothMapSmsPdu.SMS_TYPE_GSM, 1);
        assertThat(smsPduGsm7bitWithLanguageTableOne.getEncodingString()).isEqualTo("G-7BITEXT");

        SmsPdu smsPduGsm8bit = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_8BIT,
                BluetoothMapSmsPdu.SMS_TYPE_GSM, 0);
        assertThat(smsPduGsm8bit.getEncodingString()).isEqualTo("G-8BIT");

        SmsPdu smsPduGsm16bit = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_16BIT,
                BluetoothMapSmsPdu.SMS_TYPE_GSM, 0);
        assertThat(smsPduGsm16bit.getEncodingString()).isEqualTo("G-16BIT");

        SmsPdu smsPduGsmUnknown = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_UNKNOWN,
                BluetoothMapSmsPdu.SMS_TYPE_GSM, 0);
        assertThat(smsPduGsmUnknown.getEncodingString()).isEqualTo("");

        SmsPdu smsPduCdma7bit = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_7BIT,
                BluetoothMapSmsPdu.SMS_TYPE_CDMA, 0);
        assertThat(smsPduCdma7bit.getEncodingString()).isEqualTo("C-7ASCII");

        SmsPdu smsPduCdma8bit = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_8BIT,
                BluetoothMapSmsPdu.SMS_TYPE_CDMA, 0);
        assertThat(smsPduCdma8bit.getEncodingString()).isEqualTo("C-8BIT");

        SmsPdu smsPduCdma16bit = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_16BIT,
                BluetoothMapSmsPdu.SMS_TYPE_CDMA, 0);
        assertThat(smsPduCdma16bit.getEncodingString()).isEqualTo("C-UNICODE");

        SmsPdu smsPduCdmaKsc5601 = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_KSC5601,
                BluetoothMapSmsPdu.SMS_TYPE_CDMA, 0);
        assertThat(smsPduCdmaKsc5601.getEncodingString()).isEqualTo("C-KOREAN");

        SmsPdu smsPduCdmaUnknown = new SmsPdu(TEST_DATA, SmsMessage.ENCODING_UNKNOWN,
                BluetoothMapSmsPdu.SMS_TYPE_CDMA, 0);
        assertThat(smsPduCdmaUnknown.getEncodingString()).isEqualTo("");
    }
}