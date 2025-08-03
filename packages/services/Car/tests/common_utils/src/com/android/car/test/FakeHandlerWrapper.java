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

package com.android.car.test;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import org.mockito.Mockito;

import java.util.ArrayList;

/**
 * A handler that allows control over when to dispatch messages and callbacks.
 *
 * <p>NOTE: Currently only supports {@link Runnable} messages. It doesn't dispatch regular messages.
 *
 * <p>Usage: Create an instance of {@link FakeHandlerWrapper}, and use {@link #getMockHandler()}
 * in your test classes.
 *
 * <p>The implementation uses {@link Mockito} to bypass {@code final} keywords.
 */
@ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
public class FakeHandlerWrapper {
    private Mode mMode;
    private ArrayList<Message> mQueuedMessages = new ArrayList<>();

    private final Handler mMockHandler;

    public FakeHandlerWrapper(Looper looper, Mode mode) {
        mMockHandler = Mockito.spy(new Handler(looper));
        mMode = mode;
        // Stubbing #sendMessageAtTime(Message, long).
        Mockito.doAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.when = invocation.getArgument(1);  // uptimeMillis
            mQueuedMessages.add(msg);
            if (mMode == Mode.IMMEDIATE) {
                dispatchQueuedMessages();
            }
            return true;
        }).when(mMockHandler).sendMessageAtTime(Mockito.any(), Mockito.anyLong());
        // Stubbing #removeCallbacks(Runnable).
        Mockito.doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            return mQueuedMessages.removeIf(msg -> msg.getCallback() == callback);
        }).when(mMockHandler).removeCallbacks(Mockito.any());
    }

    public Handler getMockHandler() {
        return mMockHandler;
    }

    public void setMode(Mode mode) {
        mMode = mode;
    }

    /** Dispatch any messages that have been queued on the calling thread. */
    public void dispatchQueuedMessages() {
        ArrayList<Message> messages = new ArrayList<>(mQueuedMessages);
        mQueuedMessages.clear();
        for (Message msg : messages) {
            Runnable callback = msg.getCallback();
            if (callback != null) {
                callback.run();
            }
        }
    }

    /** Returns the queued messages list. */
    public ArrayList<Message> getQueuedMessages() {
        return new ArrayList<>(mQueuedMessages);
    }

    public enum Mode {
        /** Messages are dispatched immediately on the calling thread. */
        IMMEDIATE,
        /** Messages are queued until {@link #dispatchQueuedMessages()} is called. */
        QUEUEING,
    }
}
