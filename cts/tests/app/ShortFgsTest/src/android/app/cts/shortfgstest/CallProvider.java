/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import android.app.cts.shortfgstesthelper.ShortFgsHelper;
import android.app.cts.shortfgstesthelper.ShortFgsMessage;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.junit.Assert;

import java.util.ArrayList;

import javax.annotation.concurrent.GuardedBy;

/**
 * A provider that just accepts "call".
 */
public class CallProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * Called by the helper app. The actua handling code is in {@link #handleCall(ShortFgsMessage)}.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        extras.setClassLoader(this.getClass().getClassLoader());
        final ShortFgsMessage args =
                extras.getParcelable(ShortFgsHelper.EXTRA_MESSAGE, ShortFgsMessage.class);

        final ShortFgsMessage reply = handleCall(args);

        final Bundle ret = new Bundle();
        ret.putParcelable(ShortFgsHelper.EXTRA_MESSAGE, reply);

        Log.i(TAG, "CallProvider.call: args=" + args + " reply=" + reply);

        return ret;
    }

    @GuardedBy("sMessageQueue")
    private static final ArrayList<ShortFgsMessage> sMessageQueue = new ArrayList<>();

    private static ShortFgsMessage handleCall(ShortFgsMessage args) {
        // If it's "get test info", then return send the info back.
        if (args.isCallGetTestInfo()) {
            return ActivityManagerShortFgsTest.getTestInfo();
        }
        synchronized (sMessageQueue) {
            sMessageQueue.add(args);
            sMessageQueue.notifyAll();
        }
        // Just return an ack.
        return new ShortFgsMessage().setAck(true);
    }

    /**
     * Empty the message queue.
     */
    public static void clearMessageQueue() {
        synchronized (sMessageQueue) {
            sMessageQueue.clear();
        }
    }

    @GuardedBy("sMessageQueue")
    private static void waitForMessageLocked(long timeoutUptime) {
        while (sMessageQueue.size() == 0) {
            final long wait = timeoutUptime - System.currentTimeMillis();
            if (wait <= 0) {
                Assert.fail("Timeout waiting for the next message");
            }
            try {
                sMessageQueue.wait(wait);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the next message from the helper app.
     */
    public static ShortFgsMessage waitForNextMessage(long timeoutMillis) {
        synchronized (sMessageQueue) {
            waitForMessageLocked(System.currentTimeMillis() + timeoutMillis);
            return sMessageQueue.remove(0);
        }
    }

    /**
     * Wait for ack message.
     *
     * ACK and other messages (e.g. "method called" messages) may arrive in out-of-order,
     * so in this method, we not only look at the head of the queue, but the entire queue.
     */
    public static void waitForAckMessage(long timeoutMillis) {
        final long timeoutUptime = System.currentTimeMillis() + timeoutMillis;
        synchronized (sMessageQueue) {
            waitForMessageLocked(timeoutUptime);

            for (int i = 0; i < sMessageQueue.size(); i++) {
                if (sMessageQueue.get(i).isAck()) {
                    sMessageQueue.remove(i);
                    return;
                }
            }
        }
    }

    /**
     * Fails if there's any pending messages.
     */
    public static void ensureNoMoreMessages() {
        synchronized (sMessageQueue) {
            final int size = sMessageQueue.size();
            if (size == 0) {
                return;
            }

            Assert.fail("Message queue should be empty, but it contains " + size
                    + " messages. The first message is " + sMessageQueue.get(0));
        }
    }
}
