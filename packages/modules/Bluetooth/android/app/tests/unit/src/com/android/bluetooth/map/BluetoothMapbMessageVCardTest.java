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

import android.telephony.PhoneNumberUtils;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.map.BluetoothMapbMessage.BMsgReader;
import com.android.bluetooth.map.BluetoothMapbMessage.VCard;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapbMessageVCardTest {
    private static final String TEST_NAME = "test_name";
    private static final String TEST_FORMATTED_NAME = "test_formatted_name";
    private static final String TEST_FIRST_PHONE_NUMBER = "111-1111-1111";
    private static final String[] TEST_PHONE_NUMBERS =
            new String[]{TEST_FIRST_PHONE_NUMBER, "222-2222-2222"};
    private static final String TEST_FIRST_EMAIL = "testFirst@email.com";
    private static final String[] TEST_EMAIL_ADDRESSES =
            new String[]{TEST_FIRST_EMAIL, "testSecond@email.com"};
    private static final String TEST_FIRST_BT_UCI = "test_first_bt_uci";
    private static final String[] TEST_BT_UCIS =
            new String[]{TEST_FIRST_BT_UCI, "test_second_bt_uci"};
    private static final String TEST_FIRST_BT_UID = "1111";
    private static final String[] TEST_BT_UIDS = new String[]{TEST_FIRST_BT_UID, "1112"};
    private static final int TEST_ENV_LEVEL = 1;

    @Test
    public void constructor_forVersionTwoPointOne() {
        VCard vcard = new VCard(TEST_NAME, TEST_PHONE_NUMBERS, TEST_EMAIL_ADDRESSES);
        assertThat(vcard.getName()).isEqualTo(TEST_NAME);
        assertThat(vcard.getFirstPhoneNumber()).isEqualTo(
                PhoneNumberUtils.stripSeparators(TEST_FIRST_PHONE_NUMBER));
        assertThat(vcard.getFirstEmail()).isEqualTo(TEST_FIRST_EMAIL);
    }

    @Test
    public void constructor_forVersionTwoPointOne_withEnvLevel() {
        VCard vcard = new VCard(TEST_NAME, TEST_PHONE_NUMBERS, TEST_EMAIL_ADDRESSES,
                TEST_ENV_LEVEL);
        assertThat(vcard.getName()).isEqualTo(TEST_NAME);
        assertThat(vcard.getFirstPhoneNumber()).isEqualTo(
                PhoneNumberUtils.stripSeparators(TEST_FIRST_PHONE_NUMBER));
        assertThat(vcard.getFirstEmail()).isEqualTo(TEST_FIRST_EMAIL);
        assertThat(vcard.getEnvLevel()).isEqualTo(TEST_ENV_LEVEL);
    }

    @Test
    public void constructor_forVersionThree() {
        VCard vcard = new VCard(TEST_NAME, TEST_FORMATTED_NAME, TEST_PHONE_NUMBERS,
                TEST_EMAIL_ADDRESSES, TEST_ENV_LEVEL);
        assertThat(vcard.getName()).isEqualTo(TEST_NAME);
        assertThat(vcard.getFirstPhoneNumber()).isEqualTo(
                PhoneNumberUtils.stripSeparators(TEST_FIRST_PHONE_NUMBER));
        assertThat(vcard.getFirstEmail()).isEqualTo(TEST_FIRST_EMAIL);
        assertThat(vcard.getEnvLevel()).isEqualTo(TEST_ENV_LEVEL);
    }

    @Test
    public void constructor_forVersionThree_withUcis() {
        VCard vcard = new VCard(TEST_NAME, TEST_FORMATTED_NAME, TEST_PHONE_NUMBERS,
                TEST_EMAIL_ADDRESSES, TEST_BT_UIDS, TEST_BT_UCIS);
        assertThat(vcard.getName()).isEqualTo(TEST_NAME);
        assertThat(vcard.getFirstPhoneNumber()).isEqualTo(
                PhoneNumberUtils.stripSeparators(TEST_FIRST_PHONE_NUMBER));
        assertThat(vcard.getFirstEmail()).isEqualTo(TEST_FIRST_EMAIL);
        assertThat(vcard.getFirstBtUci()).isEqualTo(TEST_FIRST_BT_UCI);
    }

    @Test
    public void getters_withInitWithNulls_returnsCorrectly() {
        VCard vcard = new VCard(null, null, null);
        assertThat(vcard.getName()).isEqualTo("");
        assertThat(vcard.getFirstPhoneNumber()).isNull();
        assertThat(vcard.getFirstEmail()).isNull();
        assertThat(vcard.getFirstBtUci()).isNull();
        assertThat(vcard.getFirstBtUid()).isNull();
    }

    @Test
    public void encodeToStringBuilder_thenParseBackToVCard_returnsCorrectly() {
        VCard vcardOriginal = new VCard(TEST_NAME, TEST_FORMATTED_NAME, TEST_PHONE_NUMBERS,
                TEST_EMAIL_ADDRESSES, TEST_BT_UIDS, TEST_BT_UCIS);
        StringBuilder stringBuilder = new StringBuilder();
        vcardOriginal.encode(stringBuilder);
        InputStream inputStream = new ByteArrayInputStream(stringBuilder.toString().getBytes());

        VCard vcardParsed = VCard.parseVcard(new BMsgReader(inputStream), TEST_ENV_LEVEL);

        assertThat(vcardParsed.getName()).isEqualTo(TEST_NAME);
        assertThat(vcardParsed.getFirstPhoneNumber()).isEqualTo(
                PhoneNumberUtils.stripSeparators(TEST_FIRST_PHONE_NUMBER));
        assertThat(vcardParsed.getFirstEmail()).isEqualTo(TEST_FIRST_EMAIL);
        assertThat(vcardParsed.getEnvLevel()).isEqualTo(TEST_ENV_LEVEL);
    }
}