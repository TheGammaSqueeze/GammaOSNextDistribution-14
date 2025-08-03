/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.networkstack.tethering.util;

import android.annotation.Nullable;
import android.os.Looper;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.networkstack.tethering.util.SyncStateMachine.StateInfo;

import java.util.List;

/** A wrapper to decide whether use synchronous state machine for tethering. */
public class StateMachineShim {
    // Exactly one of mAsyncSM or mSyncSM is non-null.
    private final AsyncStateMachine mAsyncSM;
    private final SyncStateMachine mSyncSM;

    /**
     * The Looper parameter is only needed for AsyncSM, so if looper is null, the shim will be
     * created for SyncSM.
     */
    public StateMachineShim(final String name, @Nullable final Looper looper) {
        this(name, looper, new Dependencies());
    }

    @VisibleForTesting
    public StateMachineShim(final String name, @Nullable final Looper looper,
            final Dependencies deps) {
        if (looper == null) {
            mAsyncSM = null;
            mSyncSM = deps.makeSyncStateMachine(name, Thread.currentThread());
        } else {
            mAsyncSM = deps.makeAsyncStateMachine(name, looper);
            mSyncSM = null;
        }
    }

    /** A dependencies class which used for testing injection. */
    @VisibleForTesting
    public static class Dependencies {
        /** Create SyncSM instance, for injection. */
        public SyncStateMachine makeSyncStateMachine(final String name, final Thread thread) {
            return new SyncStateMachine(name, thread);
        }

        /** Create AsyncSM instance, for injection. */
        public AsyncStateMachine makeAsyncStateMachine(final String name, final Looper looper) {
            return new AsyncStateMachine(name, looper);
        }
    }

    /** Start the state machine */
    public void start(final State initialState) {
        if (mSyncSM != null) {
            mSyncSM.start(initialState);
        } else {
            mAsyncSM.setInitialState(initialState);
            mAsyncSM.start();
        }
    }

    /** Add states to state machine. */
    public void addAllStates(final List<StateInfo> stateInfos) {
        if (mSyncSM != null) {
            mSyncSM.addAllStates(stateInfos);
        } else {
            for (final StateInfo info : stateInfos) {
                mAsyncSM.addState(info.state, info.parent);
            }
        }
    }

    /**
     * Transition to given state.
     *
     * SyncSM doesn't allow this be called during state transition (#enter() or #exit() methods),
     * or multiple times while processing a single message.
     */
    public void transitionTo(final State state) {
        if (mSyncSM != null) {
            mSyncSM.transitionTo(state);
        } else {
            mAsyncSM.transitionTo(state);
        }
    }

    /** Send message to state machine. */
    public void sendMessage(int what) {
        sendMessage(what, 0, 0, null);
    }

    /** Send message to state machine. */
    public void sendMessage(int what, Object obj) {
        sendMessage(what, 0, 0, obj);
    }

    /** Send message to state machine. */
    public void sendMessage(int what, int arg1) {
        sendMessage(what, arg1, 0, null);
    }

    /**
     * Send message to state machine.
     *
     * If using asynchronous state machine, putting the message into looper's message queue.
     * Tethering runs on single looper thread that ipServers and mainSM all share with same message
     * queue. The enqueued message will be processed by asynchronous state machine when all the
     * messages before such enqueued message are processed.
     * If using synchronous state machine, the message is processed right away without putting into
     * looper's message queue.
     */
    public void sendMessage(int what, int arg1, int arg2, Object obj) {
        if (mSyncSM != null) {
            mSyncSM.processMessage(what, arg1, arg2, obj);
        } else {
            mAsyncSM.sendMessage(what, arg1, arg2, obj);
        }
    }

    /**
     * Send message after delayMillis millisecond.
     *
     * This can only be used with async state machine, so this will throw if using sync state
     * machine.
     */
    public void sendMessageDelayedToAsyncSM(final int what, final long delayMillis) {
        if (mSyncSM != null) {
            throw new IllegalStateException("sendMessageDelayed can only be used with async SM");
        }

        mAsyncSM.sendMessageDelayed(what, delayMillis);
    }

    /**
     * Enqueue a message to the front of the queue.
     * Protected, may only be called by instances of async state machine.
     *
     * Message is ignored if state machine has quit.
     */
    protected void sendMessageAtFrontOfQueueToAsyncSM(int what, int arg1) {
        if (mSyncSM != null) {
            throw new IllegalStateException("sendMessageAtFrontOfQueue can only be used with"
                    + " async SM");
        }

        mAsyncSM.sendMessageAtFrontOfQueueToAsyncSM(what, arg1);
    }

    /**
     * Send self message.
     * This can only be used with sync state machine, so this will throw if using async state
     * machine.
     */
    public void sendSelfMessageToSyncSM(final int what, final Object obj) {
        if (mSyncSM == null) {
            throw new IllegalStateException("sendSelfMessage can only be used with sync SM");
        }

        mSyncSM.sendSelfMessage(what, 0, 0, obj);
    }

    /**
     * An alias StateMahchine class with public construtor.
     *
     * Since StateMachine.java only provides protected construtor, adding a child class so that this
     * shim could create StateMachine instance.
     */
    @VisibleForTesting
    public static class AsyncStateMachine extends StateMachine {
        public AsyncStateMachine(final String name, final Looper looper) {
            super(name, looper);
        }

        /** Enqueue a message to the front of the queue for this state machine. */
        public void sendMessageAtFrontOfQueueToAsyncSM(int what, int arg1) {
            sendMessageAtFrontOfQueue(what, arg1);
        }
    }
}
