/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.telephony.SmsCbMessage;

import com.android.cellbroadcastreceiver.CellBroadcastReceiverApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class CellBroadcastReceiverAppTest extends CellBroadcastTest {

    ArrayList<SmsCbMessage> mCachedMessages;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mCachedMessages = new ArrayList<>(getNewMessageList());
        clearNewMessageList();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        clearNewMessageList();
        mCachedMessages.forEach(m -> addNewMessageToList(m));
    }

    @Test
    public void testAddNewAndGetLatestMessage() throws Exception {
        SmsCbMessage fm = getFakeMessage(1);
        ArrayList<SmsCbMessage> ml = addNewMessageToList(fm);

        assertEquals(1, ml.size());
        assertEquals(fm, ml.get(0));
        assertEquals(fm, getLatestMessage());
    }

    @Test
    public void testAddNewAndGetNewMessageList() throws Exception {
        ArrayList<SmsCbMessage> ml1 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ml1.add(getFakeMessage(i));
        }
        ml1.forEach(m -> addNewMessageToList(m));

        ArrayList<SmsCbMessage> ml2 = getNewMessageList();

        assertEquals(ml1.size(), ml2.size());
        assertTrue(ml1.containsAll(ml2));
    }

    @Test
    public void testAddAndRemoveReadMessage() throws Exception {
        ArrayList<SmsCbMessage> ml1 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ml1.add(getFakeMessage(i));
        }
        ml1.forEach(m -> addNewMessageToList(m));

        for (int i = 0; i < 3; i++) {
            ArrayList<SmsCbMessage> ml2 = removeReadMessage(ml1.get(i));
            assertTrue(!ml2.contains(ml1.get(i)));
        }
    }

    private SmsCbMessage getFakeMessage(int seq) {
        return new SmsCbMessage(1, 1, seq, null, 4379, "en", 0, "AMBER Alert: " + seq, 3,
                null, null, 0, null, System.currentTimeMillis(), 1, 0);
    }

    void clearNewMessageList() throws Exception {
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod("clearNewMessageList");
        method.setAccessible(true);
        method.invoke(null);
    }

    ArrayList<SmsCbMessage> addNewMessageToList(SmsCbMessage message) {
        Class[] args = new Class[1];
        args[0] = SmsCbMessage.class;
        try {
            Method method = CellBroadcastReceiverApp.class.getDeclaredMethod(
                    "addNewMessageToList", args);
            method.setAccessible(true);
            return (ArrayList<SmsCbMessage>) method.invoke(null, message);
        } catch (Exception e) {
            return null;
        }
    }

    ArrayList<SmsCbMessage> removeReadMessage(SmsCbMessage message) throws Exception {
        Class[] args = new Class[1];
        args[0] = SmsCbMessage.class;
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod(
                "removeReadMessage", args);
        method.setAccessible(true);
        return (ArrayList<SmsCbMessage>) method.invoke(null, message);
    }

    SmsCbMessage getLatestMessage() throws Exception {
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod("getLatestMessage");
        method.setAccessible(true);
        return (SmsCbMessage) method.invoke(null);
    }

    ArrayList<SmsCbMessage> getNewMessageList() throws Exception {
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod("getNewMessageList");
        method.setAccessible(true);
        return (ArrayList<SmsCbMessage>) method.invoke(null);
    }
}
