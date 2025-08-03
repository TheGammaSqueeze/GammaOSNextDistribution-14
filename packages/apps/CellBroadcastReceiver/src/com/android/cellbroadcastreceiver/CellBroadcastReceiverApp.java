/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.annotation.NonNull;
import android.app.Application;
import android.telephony.SmsCbMessage;

import android.util.Log;
import java.util.ArrayList;

/**
 * The application class loads the default preferences at first start,
 * and remembers the time of the most recently received broadcast.
 */
public class CellBroadcastReceiverApp extends Application {
    private static final String TAG = "CellBroadcastReceiverApp";
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    /** List of unread non-emergency alerts to show when user selects the notification. */
    private static final ArrayList<SmsCbMessage> sNewMessageList = new ArrayList<>(4);

    /** Adds a new unread non-emergency message and returns the current list. */
    static ArrayList<SmsCbMessage> addNewMessageToList(SmsCbMessage message) {
        if (VDBG) Log.v(TAG, "addNewMessageToList: " + message);
        sNewMessageList.add(message);
        return sNewMessageList;
    }

    /** Clears the list of unread non-emergency messages. */
    static void clearNewMessageList() {
        if (VDBG) Log.v(TAG, "clearNewMessageList");
        sNewMessageList.clear();
    }

    /** Remove the read message from the unread message list. */
    static ArrayList<SmsCbMessage> removeReadMessage(@NonNull SmsCbMessage message) {
        if (sNewMessageList.removeIf(msg -> msg.getReceivedTime() == message.getReceivedTime())) {
            if (VDBG) Log.v(TAG, "removeReadMessage succeed, msg: " + message);
        } else {
            if (VDBG) Log.v(TAG, "removeReadMessage failed, no matching message: " + message);
        }
        return sNewMessageList;
    }

    /** Returns the latest unread message. */
    static SmsCbMessage getLatestMessage() {
        return sNewMessageList.isEmpty() ? null : sNewMessageList.get(sNewMessageList.size()-1);
    }

    /** Returns a list of unread non-emergency alerts */
    static ArrayList<SmsCbMessage> getNewMessageList() {
        return sNewMessageList;
    }
}
