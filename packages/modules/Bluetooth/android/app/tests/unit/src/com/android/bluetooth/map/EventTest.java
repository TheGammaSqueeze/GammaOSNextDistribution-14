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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Looper;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.mapapi.BluetoothMapContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EventTest {
    private static final String TEST_EVENT_TYPE = "test_event_type";
    private static final long TEST_HANDLE = 1;
    private static final String TEST_FOLDER = "test_folder";
    private static final String TEST_OLD_FOLDER = "test_old_folder";
    private static final TYPE TEST_TYPE = TYPE.EMAIL;
    private static final String TEST_DATETIME = "20221207T16:35:21";
    private static final String TEST_SUBJECT = "test_subject";
    private static final String TEST_SENDER_NAME = "test_sender_name";
    private static final String TEST_PRIORITY = "test_priority";
    private static final long TEST_CONVERSATION_ID = 1;
    private static final String TEST_CONVERSATION_NAME = "test_conversation_name";
    private static final int TEST_PRESENCE_STATE = BluetoothMapContract.PresenceState.ONLINE;
    private static final String TEST_PRESENCE_STATUS = "test_presence_status";
    private static final int TEST_CHAT_STATE = BluetoothMapContract.ChatState.COMPOSING;
    private static final String TEST_UCI = "test_uci";
    private static final String TEST_NAME = "test_name";
    private static final String TEST_LAST_ACTIVITY = "20211207T16:35:21";

    private BluetoothMapContentObserver mObserver;
    private BluetoothMapContentObserver.Event mEvent;

    @Before
    public void setUp() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Context mockContext = mock(Context.class);
        MockContentResolver mockResolver = new MockContentResolver();
        BluetoothMapContentObserverTest.ExceptionTestProvider
                mockProvider = new BluetoothMapContentObserverTest.ExceptionTestProvider(
                mockContext);
        mockResolver.addProvider("sms", mockProvider);

        TelephonyManager mockTelephony = mock(TelephonyManager.class);
        UserManager mockUserService = mock(UserManager.class);
        BluetoothMapMasInstance mockMas = mock(BluetoothMapMasInstance.class);

        // Functions that get called when BluetoothMapContentObserver is created
        when(mockUserService.isUserUnlocked()).thenReturn(true);
        when(mockContext.getContentResolver()).thenReturn(mockResolver);
        when(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephony);
        when(mockContext.getSystemServiceName(TelephonyManager.class))
                .thenReturn(Context.TELEPHONY_SERVICE);
        when(mockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mockUserService);
        mObserver = new BluetoothMapContentObserver(mockContext, null, mockMas, null, true);
        mEvent = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE, TEST_FOLDER, TEST_TYPE);
    }

    @Test
    public void constructor() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE,
                TEST_FOLDER, TEST_TYPE);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.handle).isEqualTo(TEST_HANDLE);
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
    }

    @Test
    public void constructor_withNullOldFolder() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE,
                TEST_FOLDER, null, TEST_TYPE);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.handle).isEqualTo(TEST_HANDLE);
        assertThat(event.oldFolder).isNull();
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
    }

    @Test
    public void constructor_withNonNullOldFolder() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE,
                TEST_FOLDER, TEST_OLD_FOLDER, TEST_TYPE);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.handle).isEqualTo(TEST_HANDLE);
        assertThat(event.oldFolder).isEqualTo(TEST_OLD_FOLDER);
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
    }

    @Test
    public void constructor_forExtendedEventTypeOnePointOne() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE,
                TEST_FOLDER, TEST_TYPE, TEST_DATETIME, TEST_SUBJECT, TEST_SENDER_NAME,
                TEST_PRIORITY);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.handle).isEqualTo(TEST_HANDLE);
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
        assertThat(event.datetime).isEqualTo(TEST_DATETIME);
        assertThat(event.subject).isEqualTo(BluetoothMapUtils.stripInvalidChars(TEST_SUBJECT));
        assertThat(event.senderName).isEqualTo(
                BluetoothMapUtils.stripInvalidChars(TEST_SENDER_NAME));
        assertThat(event.priority).isEqualTo(TEST_PRIORITY);
    }

    @Test
    public void constructor_forExtendedEventTypeOnePointTwo_withMessageEvents() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_HANDLE,
                TEST_FOLDER, TEST_TYPE, TEST_DATETIME, TEST_SUBJECT, TEST_SENDER_NAME,
                TEST_PRIORITY, TEST_CONVERSATION_ID, TEST_CONVERSATION_NAME);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.handle).isEqualTo(TEST_HANDLE);
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
        assertThat(event.datetime).isEqualTo(TEST_DATETIME);
        assertThat(event.subject).isEqualTo(BluetoothMapUtils.stripInvalidChars(TEST_SUBJECT));
        assertThat(event.senderName).isEqualTo(
                BluetoothMapUtils.stripInvalidChars(TEST_SENDER_NAME));
        assertThat(event.priority).isEqualTo(TEST_PRIORITY);
        assertThat(event.conversationID).isEqualTo(TEST_CONVERSATION_ID);
        assertThat(event.conversationName).isEqualTo(
                BluetoothMapUtils.stripInvalidChars(TEST_CONVERSATION_NAME));
    }

    @Test
    public void constructor_forExtendedEventTypeOnePointTwo_withConversationEvents() {
        BluetoothMapContentObserver.Event event = mObserver.new Event(TEST_EVENT_TYPE, TEST_UCI,
                TEST_TYPE, TEST_NAME, TEST_PRIORITY, TEST_LAST_ACTIVITY, TEST_CONVERSATION_ID,
                TEST_CONVERSATION_NAME, TEST_PRESENCE_STATE, TEST_PRESENCE_STATUS, TEST_CHAT_STATE);

        assertThat(event.eventType).isEqualTo(TEST_EVENT_TYPE);
        assertThat(event.uci).isEqualTo(TEST_UCI);
        assertThat(event.msgType).isEqualTo(TEST_TYPE);
        assertThat(event.senderName).isEqualTo(BluetoothMapUtils.stripInvalidChars(TEST_NAME));
        assertThat(event.priority).isEqualTo(TEST_PRIORITY);
        assertThat(event.datetime).isEqualTo(TEST_LAST_ACTIVITY);
        assertThat(event.conversationID).isEqualTo(TEST_CONVERSATION_ID);
        assertThat(event.conversationName).isEqualTo(
                BluetoothMapUtils.stripInvalidChars(TEST_CONVERSATION_NAME));
        assertThat(event.presenceState).isEqualTo(TEST_PRESENCE_STATE);
        assertThat(event.presenceStatus).isEqualTo(
                BluetoothMapUtils.stripInvalidChars(TEST_PRESENCE_STATUS));
        assertThat(event.chatState).isEqualTo(TEST_CHAT_STATE);
    }

    @Test
    public void setFolderPath_withNullName() {
        mEvent.setFolderPath(null, null);

        assertThat(mEvent.folder).isNull();
    }

    @Test
    public void setFolderPath_withNonNullNameAndTypeIm() {
        String name = "name";
        TYPE type = TYPE.IM;

        mEvent.setFolderPath(name, type);

        assertThat(mEvent.folder).isEqualTo(name);
    }

    @Test
    public void setFolderPath_withNonNullNameAndTypeMms() {
        String name = "name";
        TYPE type = TYPE.MMS;

        mEvent.setFolderPath(name, type);

        assertThat(mEvent.folder).isEqualTo(BluetoothMapContentObserver.Event.PATH + name);
    }
}
