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

import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.State
import com.android.networkstack.tethering.util.SyncStateMachine.StateInfo
import java.util.ArrayDeque
import java.util.ArrayList
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.spy
import org.mockito.Mockito.verifyNoMoreInteractions

private const val MSG_INVALID = -1
private const val MSG_1 = 1
private const val MSG_2 = 2
private const val MSG_3 = 3
private const val MSG_4 = 4
private const val MSG_5 = 5
private const val MSG_6 = 6
private const val MSG_7 = 7
private const val ARG_1 = 100
private const val ARG_2 = 200

@RunWith(AndroidJUnit4::class)
@SmallTest
class SynStateMachineTest {
    private val mState1 = spy(object : TestState(MSG_1) {})
    private val mState2 = spy(object : TestState(MSG_2) {})
    private val mState3 = spy(object : TestState(MSG_3) {})
    private val mState4 = spy(object : TestState(MSG_4) {})
    private val mState5 = spy(object : TestState(MSG_5) {})
    private val mState6 = spy(object : TestState(MSG_6) {})
    private val mState7 = spy(object : TestState(MSG_7) {})
    private val mInOrder = inOrder(mState1, mState2, mState3, mState4, mState5, mState6, mState7)
    // Lazy initialize to make sure running in test thread.
    private val mSM by lazy {
        SyncStateMachine("TestSyncStateMachine", Thread.currentThread(), true /* debug */)
    }
    private val mAllStates = ArrayList<StateInfo>()

    private val mMsgProcessedResults = ArrayDeque<Pair<State, Int>>()

    open inner class TestState(val expected: Int) : State() {
        // Control destination state in obj field for testing.
        override fun processMessage(msg: Message): Boolean {
            mMsgProcessedResults.add(this to msg.what)
            assertEquals(ARG_1, msg.arg1)
            assertEquals(ARG_2, msg.arg2)

            if (msg.what == expected) {
                msg.obj?.let { mSM.transitionTo(it as State) }
                return true
            }

            return false
        }
    }

    private fun verifyNoMoreInteractions() {
        verifyNoMoreInteractions(mState1, mState2, mState3, mState4, mState5, mState6)
    }

    private fun processMessage(what: Int, toState: State?) {
        mSM.processMessage(what, ARG_1, ARG_2, toState)
    }

    private fun verifyMessageProcessedBy(what: Int, vararg processedStates: State) {
        for (state in processedStates) {
            // InOrder.verify can't check the Message content here because SyncSM will recycle the
            // message after it's been processed. SyncSM reuses the same Message instance for all
            // messages it processes. So, if using InOrder.verify to verify the content of a message
            // after SyncSM has processed it, the content would be wrong.
            mInOrder.verify(state).processMessage(any())
            val (processedState, msgWhat) = mMsgProcessedResults.remove()
            assertEquals(state, processedState)
            assertEquals(what, msgWhat)
        }
        assertTrue(mMsgProcessedResults.isEmpty())
    }

    @Test
    fun testInitialState() {
        // mState1 -> initial
        //    |
        // mState2
        mAllStates.add(StateInfo(mState1, null))
        mAllStates.add(StateInfo(mState2, mState1))
        mSM.addAllStates(mAllStates)

        mSM.start(mState1)
        mInOrder.verify(mState1).enter()
        verifyNoMoreInteractions()
    }

    @Test
    fun testStartFromLeafState() {
        // mState1 -> initial
        //    |
        // mState2
        //    |
        // mState3
        mAllStates.add(StateInfo(mState1, null))
        mAllStates.add(StateInfo(mState2, mState1))
        mAllStates.add(StateInfo(mState3, mState2))
        mSM.addAllStates(mAllStates)

        mSM.start(mState3)
        mInOrder.verify(mState1).enter()
        mInOrder.verify(mState2).enter()
        mInOrder.verify(mState3).enter()
        verifyNoMoreInteractions()
    }

    private fun verifyStart() {
        mSM.addAllStates(mAllStates)
        mSM.start(mState1)
        mInOrder.verify(mState1).enter()
        verifyNoMoreInteractions()
    }

    fun addState(state: State, parent: State? = null) {
        mAllStates.add(StateInfo(state, parent))
    }

    @Test
    fun testAddState() {
        // Add duplicated states.
        mAllStates.add(StateInfo(mState1, null))
        mAllStates.add(StateInfo(mState1, null))
        assertFailsWith(IllegalStateException::class) {
            mSM.addAllStates(mAllStates)
        }
    }

    @Test
    fun testProcessMessage() {
        // mState1
        //    |
        // mState2
        addState(mState1)
        addState(mState2, mState1)
        verifyStart()

        processMessage(MSG_1, null)
        verifyMessageProcessedBy(MSG_1, mState1)
        verifyNoMoreInteractions()
    }

    @Test
    fun testTwoStates() {
        // mState1 <-initial, mState2
        addState(mState1)
        addState(mState2)
        verifyStart()

        // Test transition to mState2
        processMessage(MSG_1, mState2)
        verifyMessageProcessedBy(MSG_1, mState1)
        mInOrder.verify(mState1).exit()
        mInOrder.verify(mState2).enter()
        verifyNoMoreInteractions()

        // If set destState to mState2 (current state), no state transition.
        processMessage(MSG_2, mState2)
        verifyMessageProcessedBy(MSG_2, mState2)
        verifyNoMoreInteractions()
    }

    @Test
    fun testTwoStateTrees() {
        //    mState1 -> initial  mState4
        //    /     \             /     \
        // mState2 mState3     mState5 mState6
        addState(mState1)
        addState(mState2, mState1)
        addState(mState3, mState1)
        addState(mState4)
        addState(mState5, mState4)
        addState(mState6, mState4)
        verifyStart()

        //    mState1 -> current     mState4
        //    /     \                /     \
        // mState2 mState3 -> dest mState5 mState6
        processMessage(MSG_1, mState3)
        verifyMessageProcessedBy(MSG_1, mState1)
        mInOrder.verify(mState3).enter()
        verifyNoMoreInteractions()

        //           mState1                     mState4
        //           /     \                     /     \
        // dest <- mState2 mState3 -> current mState5 mState6
        processMessage(MSG_1, mState2)
        verifyMessageProcessedBy(MSG_1, mState3, mState1)
        mInOrder.verify(mState3).exit()
        mInOrder.verify(mState2).enter()
        verifyNoMoreInteractions()

        //               mState1          mState4
        //               /     \          /     \
        // current <- mState2 mState3 mState5 mState6 -> dest
        processMessage(MSG_2, mState6)
        verifyMessageProcessedBy(MSG_2, mState2)
        mInOrder.verify(mState2).exit()
        mInOrder.verify(mState1).exit()
        mInOrder.verify(mState4).enter()
        mInOrder.verify(mState6).enter()
        verifyNoMoreInteractions()
    }

    @Test
    fun testMultiDepthTransition() {
        //      mState1 -> current
        //    |          \
        //  mState2         mState6
        //    |   \           |
        //  mState3 mState5  mState7
        //    |
        //  mState4
        addState(mState1)
        addState(mState2, mState1)
        addState(mState6, mState1)
        addState(mState3, mState2)
        addState(mState5, mState2)
        addState(mState7, mState6)
        addState(mState4, mState3)
        verifyStart()

        //      mState1 -> current
        //    |          \
        //  mState2         mState6
        //    |   \           |
        //  mState3 mState5  mState7
        //    |
        //  mState4 -> dest
        processMessage(MSG_1, mState4)
        verifyMessageProcessedBy(MSG_1, mState1)
        mInOrder.verify(mState2).enter()
        mInOrder.verify(mState3).enter()
        mInOrder.verify(mState4).enter()
        verifyNoMoreInteractions()

        //            mState1
        //        /            \
        //  mState2             mState6
        //    |   \                 \
        //  mState3 mState5 -> dest  mState7
        //    |
        //  mState4 -> current
        processMessage(MSG_1, mState5)
        verifyMessageProcessedBy(MSG_1, mState4, mState3, mState2, mState1)
        mInOrder.verify(mState4).exit()
        mInOrder.verify(mState3).exit()
        mInOrder.verify(mState5).enter()
        verifyNoMoreInteractions()

        //            mState1
        //        /              \
        //  mState2               mState6
        //    |   \                    \
        //  mState3 mState5 -> current  mState7 -> dest
        //    |
        //  mState4
        processMessage(MSG_2, mState7)
        verifyMessageProcessedBy(MSG_2, mState5, mState2)
        mInOrder.verify(mState5).exit()
        mInOrder.verify(mState2).exit()
        mInOrder.verify(mState6).enter()
        mInOrder.verify(mState7).enter()
        verifyNoMoreInteractions()
    }
}
