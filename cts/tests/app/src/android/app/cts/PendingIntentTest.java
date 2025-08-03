/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.MockActivity;
import android.app.stubs.MockReceiver;
import android.app.stubs.MockService;
import android.app.stubs.PendingIntentStubActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;
import android.test.AndroidTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PendingIntentTest extends AndroidTestCase {

    private static final int WAIT_TIME = 10000;
    private PendingIntent mPendingIntent;
    private Intent mIntent;
    private Context mContext;
    private boolean mFinishResult;
    private boolean mHandleResult;
    private String mResultAction;
    private PendingIntent.OnFinished mFinish;
    private boolean mLooperStart;
    private Looper mLooper;
    private Handler mHandler;
    private Activity mActivity;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mFinish = new PendingIntent.OnFinished() {
            public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                    String resultData, Bundle resultExtras) {
                synchronized (mFinish) {
                    mFinishResult = true;
                    if (intent != null) {
                        mResultAction = intent.getAction();
                    }
                    mFinish.notifyAll();
                }
            }
        };

        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLooperStart = true;
                Looper.loop();
            }
        }.start();
        while (!mLooperStart) {
            Thread.sleep(50);
        }
        mHandler = new Handler(mLooper) {
            @Override
            public void dispatchMessage(Message msg) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                super.dispatchMessage(msg);
            }

            @Override
            public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                return super.sendMessageAtTime(msg, uptimeMillis);
            }

            @Override
            public void handleMessage(Message msg) {
                synchronized (mFinish) {
                    mHandleResult = true;
                }
                super.handleMessage(msg);
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mLooper.quit();
    }

    private void prepareFinish() {
        synchronized (mFinish) {
            mFinishResult = false;
            mHandleResult = false;
        }
    }

    public boolean waitForFinish(long timeout) {
        long now = SystemClock.elapsedRealtime();
        final long endTime = now + timeout;
        synchronized (mFinish) {
            while (!mFinishResult && now < endTime) {
                try {
                    mFinish.wait(endTime - now);
                } catch (InterruptedException e) {
                }
                now = SystemClock.elapsedRealtime();
            }
            return mFinishResult;
        }
    }

    public void testGetActivity() throws InterruptedException, CanceledException {
        PendingIntentStubActivity.prepare();
        mPendingIntent = null;
        mIntent = new Intent();

        mIntent.setClass(mContext, PendingIntentStubActivity.class);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertEquals(mContext.getPackageName(), mPendingIntent.getTargetPackage());

        mPendingIntent.send(ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());

        PendingIntentStubActivity.waitForCreate(WAIT_TIME);
        assertNotNull(mPendingIntent);
        assertEquals(PendingIntentStubActivity.status, PendingIntentStubActivity.ON_CREATE);

        // test getActivity return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError(mPendingIntent);

        try {
            mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the PendingIntent");
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept new mutable implicit PendingIntent");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testGetActivities() throws InterruptedException, CanceledException {
        PendingIntentStubActivity.prepare();
        mPendingIntent = null;
        Intent[] mIntents = new Intent[]{new Intent(), new Intent()};

        for (int i = 0; i < mIntents.length; i++) {
            mIntents[i].setClass(mContext, PendingIntentStubActivity.class);
            mIntents[i].setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertEquals(mContext.getPackageName(), mPendingIntent.getTargetPackage());

        mPendingIntent.send(ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle());

        PendingIntentStubActivity.waitForCreate(WAIT_TIME);
        assertNotNull(mPendingIntent);
        assertEquals(PendingIntentStubActivity.status, PendingIntentStubActivity.ON_CREATE);

        // test getActivities return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError(mPendingIntent);

        try {
            mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the PendingIntent");
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE);

        // make mIntents implicit
        for (int i = 0; i < mIntents.length; i++) {
            mIntents[i].setComponent(null);
            mIntents[i].setPackage(null);
        }

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getActivities(mContext, 1, mIntents,
                    PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept new mutable implicit PendingIntent");
        } catch (IllegalArgumentException expected) {
        }
    }

    private void pendingIntentSendError(PendingIntent pendingIntent) {
        try {
            // From the doc send function will throw CanceledException if the PendingIntent
            // is no longer allowing more intents to be sent through it. So here call it twice then
            // a CanceledException should be caught.
            mPendingIntent.send();
            mPendingIntent.send();
            fail("CanceledException expected, but not thrown");
        } catch (PendingIntent.CanceledException e) {
            // expected
        }
    }

    public void testGetBroadcast() throws InterruptedException, CanceledException {
        MockReceiver.prepareReceive(null, 0);
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // test getBroadcast return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError(mPendingIntent);

        try {
            mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the PendingIntent");
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept new mutable implicit PendingIntent");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Local receiver for examining delivered broadcast intents
    private class ExtraReceiver extends BroadcastReceiver {
        private final String extraName;

        public volatile int extra = 0;
        public CountDownLatch latch = null;

        public ExtraReceiver(String name) {
            extraName = name;
        }

        public void onReceive(Context ctx, Intent intent) {
            extra = intent.getIntExtra(extraName, 0);
            latch.countDown();
        }

        public void reset() {
            extra = 0;
            latch = new CountDownLatch(1);
        }

        public boolean waitForReceipt() throws InterruptedException {
            return latch.await(WAIT_TIME, TimeUnit.MILLISECONDS);
        }
    }

    public void testUpdateCurrent() throws InterruptedException, CanceledException {
        final int EXTRA_1 = 50;
        final int EXTRA_2 = 38;
        final String EXTRA_NAME = "test_extra";
        final String BROADCAST_ACTION = "testUpdateCurrent_action";

        final Context context = getContext();
        final ExtraReceiver br = new ExtraReceiver(EXTRA_NAME);
        final IntentFilter filter = new IntentFilter(BROADCAST_ACTION);
        context.registerReceiver(br, filter, Context.RECEIVER_EXPORTED_UNAUDITED);

        // Baseline: establish that we get the extra properly
        PendingIntent pi;
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra(EXTRA_NAME, EXTRA_1);

        pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        try {
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_1);

            // Change the extra in the Intent
            intent.putExtra(EXTRA_NAME, EXTRA_2);

            // Repeat PendingIntent.getBroadcast() *without* UPDATE_CURRENT, so we expect
            // the underlying Intent to still be the initial one with EXTRA_1
            pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_1);

            // This time use UPDATE_CURRENT, and expect to get the updated extra when the
            // PendingIntent is sent
            pi = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            br.reset();
            pi.send();
            assertTrue(br.waitForReceipt());
            assertTrue(br.extra == EXTRA_2);
        } finally {
            pi.cancel();
            context.unregisterReceiver(br);
        }
    }

    public void testGetService() throws InterruptedException, CanceledException {
        MockService.prepareStart();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockService.waitForStart(WAIT_TIME);
        assertTrue(MockService.result);

        // test getService return null
        mPendingIntent.cancel();
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNull(mPendingIntent);

        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        pendingIntentSendError(mPendingIntent);

        try {
            mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept both FLAG_IMMUTABLE and FLAG_MUTABLE for the PendingIntent");
        } catch (IllegalArgumentException expected) {
        }

        // creating a mutable explicit PendingIntent works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingIntent works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingIntent with NO_CREATE works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingIntent with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        try {
            mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                    PendingIntent.FLAG_MUTABLE);
            fail("Shouldn't accept new mutable implicit PendingIntent");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testStartServiceOnFinishedHandler() throws InterruptedException, CanceledException {
        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null, mFinish, null);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertTrue(MockService.result);

        assertTrue(mFinishResult);
        assertFalse(mHandleResult);
        mPendingIntent.cancel();

        MockService.prepareStart();
        prepareFinish();
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getService(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null, mFinish, mHandler);

        MockService.waitForStart(WAIT_TIME);
        waitForFinish(WAIT_TIME);
        assertTrue(MockService.result);

        assertTrue(mFinishResult);
        assertTrue(mHandleResult);
        mPendingIntent.cancel();

    }

    public void testCreatePendingResult() {
        Intent intent = new Intent(mContext, MockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);

        // creating a mutable explicit PendingResult works fine
        mPendingIntent = mActivity.createPendingResult(1, mIntent,
                PendingIntent.FLAG_MUTABLE);

        // make mIntent implicit
        mIntent.setComponent(null);
        mIntent.setPackage(null);

        // creating an immutable implicit PendingResult works fine
        mPendingIntent = mActivity.createPendingResult(1, mIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // retrieving a mutable implicit PendingResult with NO_CREATE works fine
        mPendingIntent = mActivity.createPendingResult(1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE);

        // creating a mutable implicit PendingResult with ALLOW_UNSAFE_IMPLICIT_INTENT works fine
        mPendingIntent = mActivity.createPendingResult(1, mIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // creating a mutable implicit PendingResult works fine
        mPendingIntent = mActivity.createPendingResult(1, mIntent,
                PendingIntent.FLAG_MUTABLE);
    }

    public void testCancel() throws CanceledException {
        mIntent = new Intent();
        mIntent.setClass(mContext, MockService.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    private void pendingIntentSendShouldFail(PendingIntent pendingIntent) {
        try {
            pendingIntent.send();
            fail("CanceledException expected, but not thrown");
        } catch (CanceledException e) {
            // expected
        }
    }

    public void testSend() throws InterruptedException, CanceledException {
        MockReceiver.prepareReceive(null, -1);
        mIntent = new Intent();
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send();

        MockReceiver.waitForReceive(WAIT_TIME);

        // send function to send default code 0
        assertEquals(0, MockReceiver.sResultCode);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        pendingIntentSendShouldFail(mPendingIntent);
    }

    public void testSendWithParamInt() throws InterruptedException, CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        // send result code 1.
        mPendingIntent.send(1);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert the result code
        assertEquals(1, MockReceiver.sResultCode);
        assertEquals(mResultAction, null);

        MockReceiver.prepareReceive(null, 0);
        // send result code 2
        mPendingIntent.send(2);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert the result code
        assertEquals(2, MockReceiver.sResultCode);
        assertEquals(MockReceiver.sAction, MockReceiver.MOCKACTION);
        assertNull(mResultAction);
        mPendingIntent.cancel();
        pendingIntentSendShouldFail(mPendingIntent);
    }

    public void testSendWithParamContextIntIntent() throws InterruptedException, CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        MockReceiver.prepareReceive(null, 0);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);

        mPendingIntent.send(mContext, 1, null);
        MockReceiver.waitForReceive(WAIT_TIME);

        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        assertEquals(1, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);

        mPendingIntent.send(mContext, 2, mIntent);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        assertEquals(2, MockReceiver.sResultCode);
        mPendingIntent.cancel();
    }

    public void testSendWithParamIntOnFinishedHandler() throws InterruptedException,
            CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setClass(mContext, MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(1, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertFalse(mFinishResult);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(1, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();

        mPendingIntent.send(2, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(2, MockReceiver.sResultCode);
        mPendingIntent.cancel();

        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        mPendingIntent.send(3, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertTrue(mFinishResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);

        // assert result code
        assertEquals(3, MockReceiver.sResultCode);
        mPendingIntent.cancel();
    }

    public void testSendWithParamContextIntIntentOnFinishedHandler() throws InterruptedException,
            CanceledException {
        mIntent = new Intent(MockReceiver.MOCKACTION);
        mIntent.setAction(MockReceiver.MOCKACTION);
        mIntent.setClass(getContext(), MockReceiver.class);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, null, null);
        MockReceiver.waitForReceive(WAIT_TIME);
        assertFalse(mFinishResult);
        assertFalse(mHandleResult);
        assertNull(mResultAction);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertEquals(mResultAction, MockReceiver.MOCKACTION);
        assertFalse(mHandleResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertEquals(mResultAction, MockReceiver.MOCKACTION);
        assertTrue(mFinishResult);
        assertEquals(MockReceiver.MOCKACTION, MockReceiver.sAction);
        mPendingIntent.cancel();
    }


    public void testSendNoReceiverOnFinishedHandler() throws InterruptedException,
            CanceledException {
        // This action won't match anything, so no receiver will run but we should
        // still get a finish result.
        final String BAD_ACTION = MockReceiver.MOCKACTION + "_bad";
        mIntent = new Intent(BAD_ACTION);
        mIntent.setAction(BAD_ACTION);

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, null);
        waitForFinish(WAIT_TIME);
        assertTrue(mFinishResult);
        assertEquals(mResultAction, BAD_ACTION);
        assertFalse(mHandleResult);
        assertNull(MockReceiver.sAction);
        mPendingIntent.cancel();

        mPendingIntent = PendingIntent.getBroadcast(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        MockReceiver.prepareReceive(null, 0);
        prepareFinish();
        mPendingIntent.send(mContext, 1, mIntent, mFinish, mHandler);
        waitForFinish(WAIT_TIME);
        assertTrue(mHandleResult);
        assertEquals(mResultAction, BAD_ACTION);
        assertTrue(mFinishResult);
        assertNull(MockReceiver.sAction);
        mPendingIntent.cancel();
    }

    public void testGetTargetPackage() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertEquals(mContext.getPackageName(), mPendingIntent.getTargetPackage());
    }

    public void testIsImmutable() {
        mIntent = new Intent();
        mIntent.setPackage(mContext.getPackageName()); // explicit intent

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(mPendingIntent.isImmutable());

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
        assertFalse(mPendingIntent.isImmutable());
    }

    public void testEquals() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent target = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        assertFalse(mPendingIntent.equals(target));
        assertFalse(mPendingIntent.hashCode() == target.hashCode());
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);

        target = PendingIntent.getActivity(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(mPendingIntent.equals(target));

        mIntent = new Intent(MockReceiver.MOCKACTION);
        target = PendingIntent.getBroadcast(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);
        assertFalse(mPendingIntent.equals(target));
        assertFalse(mPendingIntent.hashCode() == target.hashCode());

        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                1 | PendingIntent.FLAG_IMMUTABLE);
        target = PendingIntent.getActivity(mContext, 1, mIntent, 1 | PendingIntent.FLAG_IMMUTABLE);

        assertTrue(mPendingIntent.equals(target));
        assertEquals(mPendingIntent.hashCode(), target.hashCode());
    }

    public void testDescribeContents() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final int expected = 0;
        assertEquals(expected, mPendingIntent.describeContents());
    }

    public void testWriteToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Parcel parcel = Parcel.obtain();

        mPendingIntent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PendingIntent pendingIntent = PendingIntent.CREATOR.createFromParcel(parcel);
        assertTrue(mPendingIntent.equals(pendingIntent));
    }

    public void testReadAndWritePendingIntentOrNullToParcel() {
        mIntent = new Intent();
        mPendingIntent = PendingIntent.getActivity(mContext, 1, mIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertNotNull(mPendingIntent.toString());

        Parcel parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        parcel.setDataPosition(0);
        PendingIntent target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertEquals(mPendingIntent, target);
        assertEquals(mPendingIntent.getTargetPackage(), target.getTargetPackage());

        mPendingIntent = null;
        parcel = Parcel.obtain();
        PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, parcel);
        target = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        assertNull(target);
    }

    public void testGetIntentComponentAndType() {
        Intent broadcastReceiverIntent = new Intent(MockReceiver.MOCKACTION);
        broadcastReceiverIntent.setClass(mContext, MockReceiver.class);
        PendingIntent broadcastReceiverPI = PendingIntent.getBroadcast(mContext, 1,
                broadcastReceiverIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(broadcastReceiverPI.isBroadcast());
        assertFalse(broadcastReceiverPI.isActivity());
        assertFalse(broadcastReceiverPI.isForegroundService());
        assertFalse(broadcastReceiverPI.isService());

        List<ResolveInfo> broadcastReceiverResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(broadcastReceiverPI,
                        (pi) -> pi.queryIntentComponents(0));
        if (broadcastReceiverResolveInfos != null && broadcastReceiverResolveInfos.size() > 0) {
            ResolveInfo resolveInfo = broadcastReceiverResolveInfos.get(0);
            assertNotNull(resolveInfo.activityInfo);
            assertEquals(MockReceiver.class.getPackageName(), resolveInfo.activityInfo.packageName);
            assertEquals(MockReceiver.class.getName(), resolveInfo.activityInfo.name);
        } else {
            fail("Cannot resolve broadcast receiver pending intent");
        }

        Intent activityIntent = new Intent();
        activityIntent.setClass(mContext, MockActivity.class);
        PendingIntent activityPI = PendingIntent.getActivity(mContext, 1,
                activityIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(activityPI.isActivity());
        assertFalse(activityPI.isBroadcast());
        assertFalse(activityPI.isForegroundService());
        assertFalse(activityPI.isService());

        List<ResolveInfo> activityResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(activityPI,
                        (pi) -> pi.queryIntentComponents(0));
        if (activityResolveInfos != null && activityResolveInfos.size() > 0) {
            ResolveInfo resolveInfo = activityResolveInfos.get(0);
            assertNotNull(resolveInfo.activityInfo);
            assertEquals(MockActivity.class.getPackageName(), resolveInfo.activityInfo.packageName);
            assertEquals(MockActivity.class.getName(), resolveInfo.activityInfo.name);
        } else {
            fail("Cannot resolve activity pending intent");
        }

        Intent serviceIntent = new Intent();
        serviceIntent.setClass(mContext, MockService.class);
        PendingIntent servicePI = PendingIntent.getService(mContext, 1, serviceIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(servicePI.isService());
        assertFalse(servicePI.isActivity());
        assertFalse(servicePI.isBroadcast());
        assertFalse(servicePI.isForegroundService());

        List<ResolveInfo> serviceResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(servicePI,
                        (pi) -> pi.queryIntentComponents(0));
        if (serviceResolveInfos != null && serviceResolveInfos.size() > 0) {
            ResolveInfo resolveInfo = serviceResolveInfos.get(0);
            assertNotNull(resolveInfo.serviceInfo);
            assertEquals(MockService.class.getPackageName(), resolveInfo.serviceInfo.packageName);
            assertEquals(MockService.class.getName(), resolveInfo.serviceInfo.name);
        } else {
            fail("Cannot resolve service pending intent");
        }

        PendingIntent foregroundServicePI = PendingIntent.getForegroundService(mContext, 1,
                serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(foregroundServicePI.isForegroundService());
        assertFalse(foregroundServicePI.isActivity());
        assertFalse(foregroundServicePI.isBroadcast());
        assertFalse(foregroundServicePI.isService());

        List<ResolveInfo> foregroundServiceResolveInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(foregroundServicePI,
                        (pi) -> pi.queryIntentComponents(0));
        if (foregroundServiceResolveInfos != null && foregroundServiceResolveInfos.size() > 0) {
            ResolveInfo resolveInfo = serviceResolveInfos.get(0);
            assertNotNull(resolveInfo.serviceInfo);
            assertEquals(MockService.class.getPackageName(), resolveInfo.serviceInfo.packageName);
            assertEquals(MockService.class.getName(), resolveInfo.serviceInfo.name);
        } else {
            fail("Cannot resolve foreground service pending intent");
        }
    }

    public void testCancelListener() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        // To make sure the executor is used, we count the number of times the executor
        // is invoked.
        final AtomicInteger executorCount = new AtomicInteger();
        final Executor e = (runnable) -> {
            executorCount.incrementAndGet();
            runnable.run();
        };

        // Add 4 listeners and remove the first one and the last one.
        PendingIntent.CancelListener listener1 = (pi) -> {
            called.add("listener1");
            assertEquals(pi1, pi);
        };
        PendingIntent.CancelListener listener2 = (pi) -> {
            called.add("listener2");
            assertEquals(pi1, pi);
        };
        PendingIntent.CancelListener listener3 = (pi) -> {
            called.add("listener3");
            assertEquals(pi1, pi);
        };
        PendingIntent.CancelListener listener4 = (pi) -> {
            called.add("listener4");
            assertEquals(pi1, pi);
        };
        assertTrue(pi1.addCancelListener(e, listener1));
        assertTrue(pi1.addCancelListener(e, listener2));
        assertTrue(pi1.addCancelListener(e, listener3));
        assertTrue(pi1.addCancelListener(e, listener4));

        pi1.removeCancelListener(listener1);
        pi1.removeCancelListener(listener4);

        pi1.cancel();

        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener2") && called.contains("listener3"));
        // Wait a bit more just in case, and make sure the last one isn't called.
        Thread.sleep(200);
        assertFalse(called.contains("listener1"));
        assertFalse(called.contains("listener4"));
        assertEquals(2, executorCount.get());

        // It's already canceled, so more calls should return false.
        assertFalse(pi1.addCancelListener(e, (pi) -> {
            assertEquals(pi1, pi);
        }));
        // Should still return false.
        assertFalse(pi1.addCancelListener(e, (pi) -> {
            assertEquals(pi1, pi);
        }));

        // Clear the trackers.
        called.clear();
        executorCount.set(0);

        // Try with a new PI using the same intent.
        final PendingIntent pi2 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        assertTrue(pi2.addCancelListener(e, (pi) -> {
            called.add("listener1");
            assertEquals(pi2, pi);
        }));
        pi2.cancel();

        TestUtils.waitUntil("listener1 not called",
                () -> called.contains("listener1"));
        assertEquals(1, executorCount.get());
    }

    public void testCancelListener_cancelCurrent() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);

        // Create the first PI.
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        PendingIntent.CancelListener listener1 = (pi) -> {
            called.add("listener1");
            assertEquals(pi1, pi);
        };
        assertTrue(pi1.addCancelListener(Runnable::run, listener1));

        // Update-current won't cancel the previous PI.
        final PendingIntent pi2 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent.CancelListener listener2 = (pi) -> {
            called.add("listener2");
            assertEquals(pi2, pi);
        };
        assertTrue(pi2.addCancelListener(Runnable::run, listener2));

        // So this shouldn't be called. (oops I don't want to use sleep(), but...)
        Thread.sleep(200);
        assertFalse(called.contains("listener1"));

        // Cancel-current will cancel both pi1 and pi2
        final PendingIntent pi3 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener1") && called.contains("listener2"));
    }

    public void testCancelListener_oneShot() throws Exception {
        final Intent i = new Intent(Intent.ACTION_VIEW);

        // Create the first PI.
        final PendingIntent pi1 = PendingIntent.getBroadcast(mContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT
                        | PendingIntent.FLAG_IMMUTABLE);
        final Set<String> called = Collections.synchronizedSet(new HashSet<>());

        PendingIntent.CancelListener listener1 = (pi) -> {
            called.add("listener1");
            assertEquals(pi1, pi);
        };
        assertTrue(pi1.addCancelListener(Runnable::run, listener1));

        pi1.send();

        TestUtils.waitUntil("listeners not called",
                () -> called.contains("listener1"));
    }
}
