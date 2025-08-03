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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.SignedLongLong;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapAppParamsTest {
    public static final long TEST_PARAMETER_MASK = 1;
    public static final int TEST_MAX_LIST_COUNT = 3;
    public static final int TEST_START_OFFSET = 1;
    public static final int TEST_FILTER_MESSAGE_TYPE = 1;
    public static final int TEST_FILTER_PRIORITY = 1;
    public static final int TEST_ATTACHMENT = 1;
    public static final int TEST_CHARSET = 1;
    public static final int TEST_CHAT_STATE = 1;
    public static final long TEST_ID_HIGH = 1;
    public static final long TEST_ID_LOW = 1;
    public static final int TEST_CONVO_LISTING_SIZE = 1;
    public static final long TEST_COUNT_LOW = 1;
    public static final long TEST_COUNT_HIGH = 1;
    public static final long TEST_CONVO_PARAMETER_MASK = 1;
    public static final String TEST_FILTER_CONVO_ID = "1111";
    public static final long TEST_FILTER_LAST_ACTIVITY_BEGIN = 0;
    public static final long TEST_FILTER_LAST_ACTIVITY_END = 0;
    public static final String TEST_FILTER_MSG_HANDLE = "1";
    public static final String TEST_FILTER_ORIGINATOR = "test_filter_originator";
    public static final long TEST_FILTER_PERIOD_BEGIN = 0;
    public static final long TEST_FILTER_PERIOD_END = 0;
    public static final int TEST_FILTER_PRESENCE = 1;
    public static final int TEST_FILTER_READ_STATUS = 1;
    public static final String TEST_FILTER_RECIPIENT = "test_filter_recipient";
    public static final int TEST_FOLDER_LISTING_SIZE = 1;
    public static final int TEST_FILTER_UID_PRESENT = 1;
    public static final int TEST_FRACTION_DELIVER = 1;
    public static final int TEST_FRACTION_REQUEST = 1;
    public static final long TEST_LAST_ACTIVITY = 0;
    public static final int TEST_MAS_INSTANCE_ID = 1;
    public static final int TEST_MESSAGE_LISTING_SIZE = 1;
    public static final long TEST_MSE_TIME = 0;
    public static final int TEST_NEW_MESSAGE = 1;
    public static final long TEST_NOTIFICATION_FILTER = 1;
    public static final int TEST_NOTIFICATION_STATUS = 1;
    public static final int TEST_PRESENCE_AVAILABILITY = 1;
    public static final String TEST_PRESENCE_STATUS = "test_presence_status";
    public static final int TEST_RETRY = 1;
    public static final int TEST_STATUS_INDICATOR = 1;
    public static final int TEST_STATUS_VALUE = 1;
    public static final int TEST_SUBJECT_LENGTH = 1;
    public static final int TEST_TRANSPARENT = 1;

    @Test
    public void encodeToBuffer_thenDecode() throws Exception {
        ByteBuffer ret = ByteBuffer.allocate(16);
        ret.putLong(TEST_COUNT_HIGH);
        ret.putLong(TEST_COUNT_LOW);

        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setMaxListCount(TEST_MAX_LIST_COUNT);
        appParams.setStartOffset(TEST_START_OFFSET);
        appParams.setFilterMessageType(TEST_FILTER_MESSAGE_TYPE);
        appParams.setFilterPeriodBegin(TEST_FILTER_PERIOD_BEGIN);
        appParams.setFilterPeriodEnd(TEST_FILTER_PERIOD_END);
        appParams.setFilterReadStatus(TEST_FILTER_READ_STATUS);
        appParams.setFilterRecipient(TEST_FILTER_RECIPIENT);
        appParams.setFilterOriginator(TEST_FILTER_ORIGINATOR);
        appParams.setFilterPriority(TEST_FILTER_PRIORITY);
        appParams.setAttachment(TEST_ATTACHMENT);
        appParams.setTransparent(TEST_TRANSPARENT);
        appParams.setRetry(TEST_RETRY);
        appParams.setNewMessage(TEST_NEW_MESSAGE);
        appParams.setNotificationFilter(TEST_NOTIFICATION_FILTER);
        appParams.setMasInstanceId(TEST_MAS_INSTANCE_ID);
        appParams.setParameterMask(TEST_PARAMETER_MASK);
        appParams.setFolderListingSize(TEST_FOLDER_LISTING_SIZE);
        appParams.setMessageListingSize(TEST_MESSAGE_LISTING_SIZE);
        appParams.setSubjectLength(TEST_SUBJECT_LENGTH);
        appParams.setCharset(TEST_CHARSET);
        appParams.setFractionRequest(TEST_FRACTION_REQUEST);
        appParams.setFractionDeliver(TEST_FRACTION_DELIVER);
        appParams.setStatusIndicator(TEST_STATUS_INDICATOR);
        appParams.setStatusValue(TEST_STATUS_VALUE);
        appParams.setMseTime(TEST_MSE_TIME);
        appParams.setDatabaseIdentifier(TEST_ID_HIGH, TEST_ID_LOW);
        appParams.setConvoListingVerCounter(TEST_COUNT_LOW, TEST_COUNT_HIGH);
        appParams.setPresenceStatus(TEST_PRESENCE_STATUS);
        appParams.setLastActivity(TEST_LAST_ACTIVITY);
        appParams.setConvoListingSize(TEST_CONVO_LISTING_SIZE);
        appParams.setChatStateConvoId(TEST_ID_HIGH, TEST_ID_LOW);
        appParams.setFolderVerCounter(TEST_COUNT_LOW, TEST_COUNT_HIGH);

        byte[] encodedParams = appParams.encodeParams();
        BluetoothMapAppParams appParamsDecoded = new BluetoothMapAppParams(encodedParams);

        assertThat(appParamsDecoded.getMaxListCount()).isEqualTo(TEST_MAX_LIST_COUNT);
        assertThat(appParamsDecoded.getStartOffset()).isEqualTo(TEST_START_OFFSET);
        assertThat(appParamsDecoded.getFilterMessageType()).isEqualTo(TEST_FILTER_MESSAGE_TYPE);
        assertThat(appParamsDecoded.getFilterPeriodBegin()).isEqualTo(TEST_FILTER_PERIOD_BEGIN);
        assertThat(appParamsDecoded.getFilterPeriodEnd()).isEqualTo(TEST_FILTER_PERIOD_END);
        assertThat(appParamsDecoded.getFilterReadStatus()).isEqualTo(TEST_FILTER_READ_STATUS);
        assertThat(appParamsDecoded.getFilterRecipient()).isEqualTo(TEST_FILTER_RECIPIENT);
        assertThat(appParamsDecoded.getFilterOriginator()).isEqualTo(TEST_FILTER_ORIGINATOR);
        assertThat(appParamsDecoded.getFilterPriority()).isEqualTo(TEST_FILTER_PRIORITY);
        assertThat(appParamsDecoded.getAttachment()).isEqualTo(TEST_ATTACHMENT);
        assertThat(appParamsDecoded.getTransparent()).isEqualTo(TEST_TRANSPARENT);
        assertThat(appParamsDecoded.getRetry()).isEqualTo(TEST_RETRY);
        assertThat(appParamsDecoded.getNewMessage()).isEqualTo(TEST_NEW_MESSAGE);
        assertThat(appParamsDecoded.getNotificationFilter()).isEqualTo(TEST_NOTIFICATION_FILTER);
        assertThat(appParamsDecoded.getMasInstanceId()).isEqualTo(TEST_MAS_INSTANCE_ID);
        assertThat(appParamsDecoded.getParameterMask()).isEqualTo(TEST_PARAMETER_MASK);
        assertThat(appParamsDecoded.getFolderListingSize()).isEqualTo(TEST_FOLDER_LISTING_SIZE);
        assertThat(appParamsDecoded.getMessageListingSize()).isEqualTo(TEST_MESSAGE_LISTING_SIZE);
        assertThat(appParamsDecoded.getSubjectLength()).isEqualTo(TEST_SUBJECT_LENGTH);
        assertThat(appParamsDecoded.getCharset()).isEqualTo(TEST_CHARSET);
        assertThat(appParamsDecoded.getFractionRequest()).isEqualTo(TEST_FRACTION_REQUEST);
        assertThat(appParamsDecoded.getFractionDeliver()).isEqualTo(TEST_FRACTION_DELIVER);
        assertThat(appParamsDecoded.getStatusIndicator()).isEqualTo(TEST_STATUS_INDICATOR);
        assertThat(appParamsDecoded.getStatusValue()).isEqualTo(TEST_STATUS_VALUE);
        assertThat(appParamsDecoded.getMseTime()).isEqualTo(TEST_MSE_TIME);
        assertThat(appParamsDecoded.getDatabaseIdentifier()).isEqualTo(ret.array());
        assertThat(appParamsDecoded.getConvoListingVerCounter()).isEqualTo(ret.array());
        assertThat(appParamsDecoded.getPresenceStatus()).isEqualTo(TEST_PRESENCE_STATUS);
        assertThat(appParamsDecoded.getLastActivity()).isEqualTo(TEST_LAST_ACTIVITY);
        assertThat(appParamsDecoded.getConvoListingSize()).isEqualTo(TEST_CONVO_LISTING_SIZE);
        assertThat(appParamsDecoded.getChatStateConvoId()).isEqualTo(new SignedLongLong(
                TEST_ID_HIGH, TEST_ID_LOW));
    }
    @Test
    public void settersAndGetters() throws Exception {
        ByteBuffer ret = ByteBuffer.allocate(16);
        ret.putLong(TEST_COUNT_HIGH);
        ret.putLong(TEST_COUNT_LOW);

        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setParameterMask(TEST_PARAMETER_MASK);
        appParams.setMaxListCount(TEST_MAX_LIST_COUNT);
        appParams.setStartOffset(TEST_START_OFFSET);
        appParams.setFilterMessageType(TEST_FILTER_MESSAGE_TYPE);
        appParams.setFilterPriority(TEST_FILTER_PRIORITY);
        appParams.setAttachment(TEST_ATTACHMENT);
        appParams.setCharset(TEST_CHARSET);
        appParams.setChatState(TEST_CHAT_STATE);
        appParams.setChatStateConvoId(TEST_ID_HIGH, TEST_ID_LOW);
        appParams.setConvoListingSize(TEST_CONVO_LISTING_SIZE);
        appParams.setConvoListingVerCounter(TEST_COUNT_LOW, TEST_COUNT_HIGH);
        appParams.setConvoParameterMask(TEST_CONVO_PARAMETER_MASK);
        appParams.setDatabaseIdentifier(TEST_ID_HIGH, TEST_ID_LOW);
        appParams.setFilterConvoId(TEST_FILTER_CONVO_ID);
        appParams.setFilterMsgHandle(TEST_FILTER_MSG_HANDLE);
        appParams.setFilterOriginator(TEST_FILTER_ORIGINATOR);
        appParams.setFilterPresence(TEST_FILTER_PRESENCE);
        appParams.setFilterReadStatus(TEST_FILTER_READ_STATUS);
        appParams.setFilterRecipient(TEST_FILTER_RECIPIENT);
        appParams.setFolderListingSize(TEST_FOLDER_LISTING_SIZE);
        appParams.setFilterUidPresent(TEST_FILTER_UID_PRESENT);
        appParams.setFolderVerCounter(TEST_COUNT_LOW, TEST_COUNT_HIGH);
        appParams.setFractionDeliver(TEST_FRACTION_DELIVER);
        appParams.setFractionRequest(TEST_FRACTION_REQUEST);
        appParams.setMasInstanceId(TEST_MAS_INSTANCE_ID);
        appParams.setMessageListingSize(TEST_MESSAGE_LISTING_SIZE);
        appParams.setNewMessage(TEST_NEW_MESSAGE);
        appParams.setNotificationFilter(TEST_NOTIFICATION_FILTER);
        appParams.setNotificationStatus(TEST_NOTIFICATION_STATUS);
        appParams.setPresenceAvailability(TEST_PRESENCE_AVAILABILITY);
        appParams.setPresenceStatus(TEST_PRESENCE_STATUS);
        appParams.setRetry(TEST_RETRY);
        appParams.setStatusIndicator(TEST_STATUS_INDICATOR);
        appParams.setStatusValue(TEST_STATUS_VALUE);
        appParams.setSubjectLength(TEST_SUBJECT_LENGTH);
        appParams.setTransparent(TEST_TRANSPARENT);

        assertThat(appParams.getParameterMask()).isEqualTo(TEST_PARAMETER_MASK);
        assertThat(appParams.getMaxListCount()).isEqualTo(TEST_MAX_LIST_COUNT);
        assertThat(appParams.getStartOffset()).isEqualTo(TEST_START_OFFSET);
        assertThat(appParams.getFilterMessageType()).isEqualTo(TEST_FILTER_MESSAGE_TYPE);
        assertThat(appParams.getFilterPriority()).isEqualTo(TEST_FILTER_PRIORITY);
        assertThat(appParams.getAttachment()).isEqualTo(TEST_ATTACHMENT);
        assertThat(appParams.getCharset()).isEqualTo(TEST_CHARSET);
        assertThat(appParams.getChatState()).isEqualTo(TEST_CHAT_STATE);
        assertThat(appParams.getChatStateConvoId()).isEqualTo(new SignedLongLong(
                TEST_ID_HIGH, TEST_ID_LOW));
        assertThat(appParams.getChatStateConvoIdByteArray()).isEqualTo(ret.array());
        assertThat(appParams.getChatStateConvoIdString()).isEqualTo(new String(ret.array()));
        assertThat(appParams.getConvoListingSize()).isEqualTo(TEST_CONVO_LISTING_SIZE);
        assertThat(appParams.getConvoListingVerCounter()).isEqualTo(ret.array());
        assertThat(appParams.getConvoParameterMask()).isEqualTo(TEST_CONVO_PARAMETER_MASK);
        assertThat(appParams.getDatabaseIdentifier()).isEqualTo(ret.array());
        assertThat(appParams.getFilterConvoId()).isEqualTo(
                SignedLongLong.fromString(TEST_FILTER_CONVO_ID));
        assertThat(appParams.getFilterConvoIdString()).isEqualTo(BluetoothMapUtils.getLongAsString(
                SignedLongLong.fromString(TEST_FILTER_CONVO_ID).getLeastSignificantBits()));
        assertThat(appParams.getFilterMsgHandle()).isEqualTo(
                BluetoothMapUtils.getLongFromString(TEST_FILTER_MSG_HANDLE));
        assertThat(appParams.getFilterMsgHandleString()).isEqualTo(
                BluetoothMapUtils.getLongAsString(appParams.getFilterMsgHandle()));
        assertThat(appParams.getFilterOriginator()).isEqualTo(TEST_FILTER_ORIGINATOR);
        assertThat(appParams.getFilterPresence()).isEqualTo(TEST_FILTER_PRESENCE);
        assertThat(appParams.getFilterReadStatus()).isEqualTo(TEST_FILTER_READ_STATUS);
        assertThat(appParams.getFilterRecipient()).isEqualTo(TEST_FILTER_RECIPIENT);
        assertThat(appParams.getFolderListingSize()).isEqualTo(TEST_FOLDER_LISTING_SIZE);
        assertThat(appParams.getFilterUidPresent()).isEqualTo(TEST_FILTER_UID_PRESENT);
        assertThat(appParams.getFolderVerCounter()).isEqualTo(ret.array());
        assertThat(appParams.getFractionDeliver()).isEqualTo(TEST_FRACTION_DELIVER);
        assertThat(appParams.getFractionRequest()).isEqualTo(TEST_FRACTION_REQUEST);
        assertThat(appParams.getMasInstanceId()).isEqualTo(TEST_MAS_INSTANCE_ID);
        assertThat(appParams.getMessageListingSize()).isEqualTo(TEST_MESSAGE_LISTING_SIZE);
        assertThat(appParams.getNewMessage()).isEqualTo(TEST_NEW_MESSAGE);
        assertThat(appParams.getNotificationFilter()).isEqualTo(TEST_NOTIFICATION_FILTER);
        assertThat(appParams.getNotificationStatus()).isEqualTo(TEST_NOTIFICATION_STATUS);
        assertThat(appParams.getPresenceAvailability()).isEqualTo(TEST_PRESENCE_AVAILABILITY);
        assertThat(appParams.getPresenceStatus()).isEqualTo(TEST_PRESENCE_STATUS);
        assertThat(appParams.getRetry()).isEqualTo(TEST_RETRY);
        assertThat(appParams.getStatusIndicator()).isEqualTo(TEST_STATUS_INDICATOR);
        assertThat(appParams.getStatusValue()).isEqualTo(TEST_STATUS_VALUE);
        assertThat(appParams.getSubjectLength()).isEqualTo(TEST_SUBJECT_LENGTH);
        assertThat(appParams.getTransparent()).isEqualTo(TEST_TRANSPARENT);
    }

    @Test
    public void setAndGetFilterLastActivity_withString() throws Exception {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setFilterLastActivityBegin(TEST_FILTER_LAST_ACTIVITY_BEGIN);
        appParams.setFilterLastActivityEnd(TEST_FILTER_LAST_ACTIVITY_END);
        String lastActivityBeginString = appParams.getFilterLastActivityBeginString();
        String lastActivityEndString = appParams.getFilterLastActivityEndString();

        appParams.setFilterLastActivityBegin(lastActivityBeginString);
        appParams.setFilterLastActivityEnd(lastActivityEndString);

        assertThat(appParams.getFilterLastActivityBegin()).isEqualTo(
                TEST_FILTER_LAST_ACTIVITY_BEGIN);
        assertThat(appParams.getFilterLastActivityEnd()).isEqualTo(TEST_FILTER_LAST_ACTIVITY_END);
    }

    @Test
    public void setAndGetLastActivity_withString() throws Exception {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setLastActivity(TEST_LAST_ACTIVITY);
        String lastActivityString = appParams.getLastActivityString();

        appParams.setLastActivity(lastActivityString);

        assertThat(appParams.getLastActivity()).isEqualTo(TEST_LAST_ACTIVITY);
    }

    @Test
    public void setAndGetFilterPeriod_withString() throws Exception {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setFilterPeriodBegin(TEST_FILTER_PERIOD_BEGIN);
        appParams.setFilterPeriodEnd(TEST_FILTER_PERIOD_END);
        String filterPeriodBeginString = appParams.getFilterPeriodBeginString();
        String filterPeriodEndString = appParams.getFilterPeriodEndString();

        appParams.setFilterPeriodBegin(filterPeriodBeginString);
        appParams.setFilterPeriodEnd(filterPeriodEndString);

        assertThat(appParams.getFilterPeriodBegin()).isEqualTo(TEST_FILTER_PERIOD_BEGIN);
        assertThat(appParams.getFilterPeriodEnd()).isEqualTo(TEST_FILTER_PERIOD_END);
    }

    @Test
    public void setAndGetMseTime_withString() throws Exception {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        appParams.setMseTime(TEST_MSE_TIME);
        String mseTimeString = appParams.getMseTimeString();

        appParams.setMseTime(mseTimeString);

        assertThat(appParams.getMseTime()).isEqualTo(TEST_MSE_TIME);
    }

    @Test
    public void setters_withIllegalArguments() {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();
        int ILLEGAL_PARAMETER_INT = -2;
        long ILLEGAL_PARAMETER_LONG = -2;

        assertThrows(IllegalArgumentException.class,
                () -> appParams.setAttachment(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setCharset(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setChatState(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setConvoListingSize(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setConvoParameterMask(ILLEGAL_PARAMETER_LONG));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFilterMessageType(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFilterPresence(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFilterPriority(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFilterReadStatus(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFilterUidPresent(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFolderListingSize(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFractionDeliver(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setFractionRequest(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setMasInstanceId(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setMaxListCount(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setMessageListingSize(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setNewMessage(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setNotificationFilter(ILLEGAL_PARAMETER_LONG));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setNotificationStatus(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setParameterMask(ILLEGAL_PARAMETER_LONG));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setPresenceAvailability(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setRetry(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setStartOffset(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setStatusIndicator(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setStatusValue(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setSubjectLength(ILLEGAL_PARAMETER_INT));
        assertThrows(IllegalArgumentException.class,
                () -> appParams.setTransparent(ILLEGAL_PARAMETER_INT));
    }

    @Test
    public void setters_withIllegalStrings() {
        BluetoothMapAppParams appParams = new BluetoothMapAppParams();

        appParams.setFilterConvoId(" ");
        appParams.setFilterMsgHandle("=");

        assertThat(appParams.getFilterConvoId()).isNull();
        assertThat(appParams.getFilterMsgHandle()).isEqualTo(-1);
    }
}