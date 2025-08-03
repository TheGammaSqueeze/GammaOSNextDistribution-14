/**
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
package com.android.networkstack.tethering.util

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.State
import com.android.networkstack.tethering.util.StateMachineShim.AsyncStateMachine
import com.android.networkstack.tethering.util.StateMachineShim.Dependencies
import com.android.networkstack.tethering.util.SyncStateMachine.StateInfo
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
@SmallTest
class StateMachineShimTest {
    private val mSyncSM = mock(SyncStateMachine::class.java)
    private val mAsyncSM = mock(AsyncStateMachine::class.java)
    private val mState1 = mock(State::class.java)
    private val mState2 = mock(State::class.java)

    inner class MyDependencies() : Dependencies() {

        override fun makeSyncStateMachine(name: String, thread: Thread) = mSyncSM

        override fun makeAsyncStateMachine(name: String, looper: Looper) = mAsyncSM
    }

    @Test
    fun testUsingSyncStateMachine() {
        val inOrder = inOrder(mSyncSM, mAsyncSM)
        val shimUsingSyncSM = StateMachineShim("ShimTest", null, MyDependencies())
        shimUsingSyncSM.start(mState1)
        inOrder.verify(mSyncSM).start(mState1)

        val allStates = ArrayList<StateInfo>()
        allStates.add(StateInfo(mState1, null))
        allStates.add(StateInfo(mState2, mState1))
        shimUsingSyncSM.addAllStates(allStates)
        inOrder.verify(mSyncSM).addAllStates(allStates)

        shimUsingSyncSM.transitionTo(mState1)
        inOrder.verify(mSyncSM).transitionTo(mState1)

        val what = 10
        shimUsingSyncSM.sendMessage(what)
        inOrder.verify(mSyncSM).processMessage(what, 0, 0, null)
        val obj = Object()
        shimUsingSyncSM.sendMessage(what, obj)
        inOrder.verify(mSyncSM).processMessage(what, 0, 0, obj)
        val arg1 = 11
        shimUsingSyncSM.sendMessage(what, arg1)
        inOrder.verify(mSyncSM).processMessage(what, arg1, 0, null)
        val arg2 = 12
        shimUsingSyncSM.sendMessage(what, arg1, arg2, obj)
        inOrder.verify(mSyncSM).processMessage(what, arg1, arg2, obj)

        assertFailsWith(IllegalStateException::class) {
            shimUsingSyncSM.sendMessageDelayedToAsyncSM(what, 1000 /* delayMillis */)
        }

        assertFailsWith(IllegalStateException::class) {
            shimUsingSyncSM.sendMessageAtFrontOfQueueToAsyncSM(what, arg1)
        }

        shimUsingSyncSM.sendSelfMessageToSyncSM(what, obj)
        inOrder.verify(mSyncSM).sendSelfMessage(what, 0, 0, obj)

        verifyNoMoreInteractions(mSyncSM, mAsyncSM)
    }

    @Test
    fun testUsingAsyncStateMachine() {
        val inOrder = inOrder(mSyncSM, mAsyncSM)
        val shimUsingAsyncSM = StateMachineShim("ShimTest", mock(Looper::class.java),
                MyDependencies())
        shimUsingAsyncSM.start(mState1)
        inOrder.verify(mAsyncSM).setInitialState(mState1)
        inOrder.verify(mAsyncSM).start()

        val allStates = ArrayList<StateInfo>()
        allStates.add(StateInfo(mState1, null))
        allStates.add(StateInfo(mState2, mState1))
        shimUsingAsyncSM.addAllStates(allStates)
        inOrder.verify(mAsyncSM).addState(mState1, null)
        inOrder.verify(mAsyncSM).addState(mState2, mState1)

        shimUsingAsyncSM.transitionTo(mState1)
        inOrder.verify(mAsyncSM).transitionTo(mState1)

        val what = 10
        shimUsingAsyncSM.sendMessage(what)
        inOrder.verify(mAsyncSM).sendMessage(what, 0, 0, null)
        val obj = Object()
        shimUsingAsyncSM.sendMessage(what, obj)
        inOrder.verify(mAsyncSM).sendMessage(what, 0, 0, obj)
        val arg1 = 11
        shimUsingAsyncSM.sendMessage(what, arg1)
        inOrder.verify(mAsyncSM).sendMessage(what, arg1, 0, null)
        val arg2 = 12
        shimUsingAsyncSM.sendMessage(what, arg1, arg2, obj)
        inOrder.verify(mAsyncSM).sendMessage(what, arg1, arg2, obj)

        shimUsingAsyncSM.sendMessageDelayedToAsyncSM(what, 1000 /* delayMillis */)
        inOrder.verify(mAsyncSM).sendMessageDelayed(what, 1000)

        shimUsingAsyncSM.sendMessageAtFrontOfQueueToAsyncSM(what, arg1)
        inOrder.verify(mAsyncSM).sendMessageAtFrontOfQueueToAsyncSM(what, arg1)

        assertFailsWith(IllegalStateException::class) {
            shimUsingAsyncSM.sendSelfMessageToSyncSM(what, obj)
        }

        verifyNoMoreInteractions(mSyncSM, mAsyncSM)
    }
}
