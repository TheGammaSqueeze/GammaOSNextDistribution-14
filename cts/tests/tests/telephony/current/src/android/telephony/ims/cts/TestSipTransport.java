/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.fail;

import android.telephony.ims.DelegateMessageCallback;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.DelegateStateCallback;
import android.telephony.ims.stub.SipDelegate;
import android.telephony.ims.stub.SipTransportImplBase;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class TestSipTransport extends SipTransportImplBase {

    private static final String TAG = "TestSipTransport";
    public static final String ONE_TO_ONE_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.msg\"";
    public static final String GROUP_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.session\"";
    public static final String FILE_TRANSFER_HTTP_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gppapplication.ims.iari.rcs.fthttp\"";

    public static final int LATCH_CREATE_DELEGATE = 0;
    public static final int LATCH_DESTROY_DELEGATE = 1;
    private static final int LATCH_MAX = 2;
    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    private final ArrayList<TestSipDelegate> mDelegates = new ArrayList<>();
    private final Object mLock = new Object();

    public TestSipTransport() {
        Log.d(TAG, "TestSipTransport with default constructor");
    }

    public TestSipTransport(Executor executor) {
        super(executor);
        Log.d(TAG, "TestSipTransport with Executor constructor");
    }

    @Override
    public void createSipDelegate(int subscriptionId, @NonNull DelegateRequest request,
            @NonNull DelegateStateCallback dc, @NonNull DelegateMessageCallback mc) {
        TestSipDelegate d = new TestSipDelegate(subscriptionId, request, dc, mc);
        synchronized (mLock) {
            mDelegates.add(d);
        }
        countDownLatch(LATCH_CREATE_DELEGATE);
    }

    @Override
    public void destroySipDelegate(@NonNull SipDelegate delegate, int reason) {
        if (delegate instanceof TestSipDelegate) {
            synchronized (mLock) {
                mDelegates.remove(delegate);
            }
            countDownLatch(LATCH_DESTROY_DELEGATE);
        } else {
            fail("unknown delegate passed in!");
        }
    }

    public List<TestSipDelegate> getDelegates() {
        synchronized (mLock) {
            return mDelegates;
        }
    }

    public TestSipDelegate getDelegate(DelegateRequest request) {
        synchronized (mLock) {
            return mDelegates.stream().filter((d) -> d.delegateRequest.equals(request))
                    .findFirst().orElse(null);
        }
    }

    public boolean isLatchCountDownFinished(int latchIndex) {
        CountDownLatch latch;
        synchronized (mLock) {
            latch = sLatches[latchIndex];
        }
        return latch.getCount() <= 0;
    }

    public boolean waitForLatchCountdownAndReset(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    private void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }
}
