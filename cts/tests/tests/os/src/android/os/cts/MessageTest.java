/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MessageTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            .setProvideMainThread(true).build();

    public static final int WHAT = 1;
    public static final int ARG1 = 1;
    public static final int ARG2 = 2;
    public static final String KEY = "android";
    public static final int VALUE = 3;

    private Message mMessage;
    private CountDownLatch mMessageHandlerCalled;

    private Handler mHandler;

    private Runnable mRunnable = new Runnable() {
        public void run() {
        }
    };

    final Object OBJ = new Object();

    @Before
    public void setUp() throws Exception {
        mMessage = new Message();
        mMessageHandlerCalled = new CountDownLatch(1);
        mHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                mMessageHandlerCalled.countDown();
            }
        };
    }

    @Test
    public void testConstructor() {
        new Message();
    }

    @Test
    public void testAccessMessageProperties() {
        assertEquals(0, mMessage.getWhen());
        mMessage.setTarget(mHandler);
        assertEquals(mHandler, mMessage.getTarget());

        assertNull(mMessage.getCallback());
        Message expected = Message.obtain(mHandler, mRunnable);
        assertEquals(mRunnable, expected.getCallback());

        Bundle bundle = mMessage.getData();
        assertNotNull(bundle);
        Bundle expectedBundle = new Bundle();
        mMessage.setData(expectedBundle);
        assertNotNull(mMessage.getData());
        assertNotSame(bundle, mMessage.getData());
        assertEquals(expectedBundle, mMessage.getData());

        assertEquals(0, mMessage.describeContents());
    }

    @Test
    public void testObtain() {
        Message message = Message.obtain();
        assertNotNull(message);
        assertEquals(0, message.what);
        assertEquals(0, message.arg1);
        assertEquals(0, message.arg2);
        assertNull(message.obj);
        assertNull(message.replyTo);
        assertNull(message.getTarget());
        assertNull(message.getCallback());
        assertNull(message.peekData());
    }

    @Test
    public void testObtain2() {
        Message message = Message.obtain(mHandler, WHAT, ARG1, ARG2, OBJ);
        Message expected = Message.obtain(message);

        assertEquals(message.getTarget(), expected.getTarget());
        assertEquals(message.what, expected.what);
        assertEquals(message.arg1, expected.arg1);
        assertEquals(message.arg2, expected.arg2);
        assertEquals(message.obj, expected.obj);
    }

    @Test
    public void testObtain3() {
        Message expected = Message.obtain(mHandler);
        assertEquals(mHandler, expected.getTarget());
    }

    @Test
    public void testObtain4() {
        Message expected = Message.obtain(mHandler, mRunnable);
        assertEquals(mHandler, expected.getTarget());
        assertEquals(mRunnable, expected.getCallback());
    }

    @Test
    public void testObtain5() {
        Message expected = Message.obtain(mHandler, WHAT);
        assertEquals(mHandler, expected.getTarget());
        assertEquals(WHAT, expected.what);
    }

    @Test
    public void testObtain6() {
        Message expected = Message.obtain(mHandler, WHAT, OBJ);
        assertEquals(mHandler, expected.getTarget());
        assertEquals(WHAT, expected.what);
        assertEquals(OBJ, expected.obj);
    }

    @Test
    public void testObtain7() {
        Message expected = Message.obtain(mHandler, WHAT, ARG1, ARG2);
        assertEquals(mHandler, expected.getTarget());
        assertEquals(WHAT, expected.what);
        assertEquals(ARG1, expected.arg1);
        assertEquals(ARG2, expected.arg2);
    }

    @Test
    public void testObtain8() {
        Message expected = Message.obtain(mHandler, WHAT, ARG1, ARG2, OBJ);
        assertEquals(mHandler, expected.getTarget());
        assertEquals(WHAT, expected.what);
        assertEquals(ARG1, expected.arg1);
        assertEquals(ARG2, expected.arg2);
        assertEquals(OBJ, expected.obj);
    }

    @Test
    public void testToString() {
        assertNotNull(mMessage.toString());
    }

    @Test
    public void testPeekData() {
        Bundle expected = new Bundle();
        assertNull(mMessage.peekData());
        mMessage.setData(expected);
        assertNotNull(mMessage.peekData());
        assertEquals(expected, mMessage.peekData());
    }

    @Test
    public void testCopyFrom() {
        Message message = Message.obtain(mHandler, WHAT, ARG1, ARG2, OBJ);
        Bundle bundle = new Bundle();
        bundle.putInt(KEY, VALUE);
        message.setData(bundle);
        message.setAsynchronous(true);
        mMessage.copyFrom(message);
        assertEquals(WHAT, mMessage.what);
        assertEquals(ARG1, mMessage.arg1);
        assertEquals(ARG2, mMessage.arg2);
        assertEquals(OBJ, mMessage.obj);
        assertEquals(VALUE, mMessage.getData().getInt(KEY));
        assertTrue(mMessage.isAsynchronous());
    }

    @Test
    public void testRecycle() {
        Message message = Message.obtain(mHandler, WHAT, ARG1, ARG2, OBJ);
        message.recycle();
        assertEquals(0, message.what);
        assertEquals(0, message.arg1);
        assertEquals(0, message.arg2);
        assertNull(message.obj);
        assertNull(message.replyTo);
        assertNull(message.getTarget());
        assertNull(message.getCallback());
        assertNull(message.peekData());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = android.os.Messenger.class)
    public void testWriteToParcel() {
        Message message = Message.obtain(mHandler, WHAT, ARG1, ARG2);
        Bundle bundle = new Bundle();
        bundle.putInt(KEY, VALUE);
        message.setData(bundle);
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mMessage = Message.CREATOR.createFromParcel(parcel);
        assertNull(mMessage.getTarget());
        assertEquals(WHAT, mMessage.what);
        assertEquals(ARG1, mMessage.arg1);
        assertEquals(ARG2, mMessage.arg2);
        assertEquals(VALUE, mMessage.getData().getInt(KEY));

        message = Message.obtain(mHandler, WHAT, ARG1, ARG2, OBJ);
        try {
            message.writeToParcel(parcel, 1);
            fail("should throw excetion");
        } catch (RuntimeException e) {
            //expected
        }
    }

    @Test
    public void testSendToTarget() throws Exception {
        try {
            mMessage.sendToTarget();
            fail("should throw exception");
        } catch (Exception e) {
            //expected
        }

        Message message = Message.obtain(mHandler);
        assertFalse(mMessageHandlerCalled.getCount() == 0);
        message.sendToTarget();
        assertTrue(mMessageHandlerCalled.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testAsynchronous() {
        Message message = Message.obtain();
        assertFalse(message.isAsynchronous());

        message.setAsynchronous(true);
        assertTrue(message.isAsynchronous());

        message.setAsynchronous(false);
        assertFalse(message.isAsynchronous());
    }

    @Test
    public void testRecycleThrowsIfMessageAlreadyRecycled() {
        Message message = Message.obtain();
        message.recycle();

        try {
            message.recycle();
            fail("should throw IllegalStateException");
        } catch (IllegalStateException ex) {
            // expected
        }
    }

    @Test
    public void testSendMessageThrowsIfMessageAlreadyRecycled() {
        Message message = Message.obtain();
        message.recycle();

        try {
            mHandler.sendMessage(message);
            fail("should throw IllegalStateException");
        } catch (IllegalStateException ex) {
            // expected
        }
    }

    @Test
    public void testRecycleThrowsIfMessageIsBeingDelivered() throws Exception {
        final CompletableFuture<IllegalStateException> res = new CompletableFuture<>();
        Handler handler = new Handler(mHandler.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    msg.recycle();
                } catch (IllegalStateException ex) {
                    res.complete(ex);
                }
            }
        };
        handler.sendEmptyMessage(WHAT);
        assertNotNull("should throw IllegalStateException", res.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSendMessageThrowsIfMessageIsBeingDelivered() throws Exception {
        final CompletableFuture<IllegalStateException> res = new CompletableFuture<>();
        Handler handler = new Handler(mHandler.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    mHandler.sendMessage(msg);
                } catch (IllegalStateException ex) {
                    res.complete(ex);
                }
            }
        };
        handler.sendEmptyMessage(WHAT);
        assertNotNull("should throw IllegalStateException", res.get(1, TimeUnit.SECONDS));
    }
}
