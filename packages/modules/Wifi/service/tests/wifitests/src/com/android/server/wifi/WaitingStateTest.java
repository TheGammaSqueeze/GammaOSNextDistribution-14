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

package com.android.server.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.util.WaitingState;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test harness for WaitingState.
 */
@SmallTest
public class WaitingStateTest {
    private TestLooper mTestLooper;

    private class TestState extends State {
        private final String mName;

        public boolean enterTriggered = false;
        public boolean exitTriggered = false;
        public List<Integer> commandList = new ArrayList<>();

        public void resetTestFlags() {
            enterTriggered = false;
            exitTriggered = false;
            commandList.clear();
        }

        TestState(String name) {
            super();
            mName = name;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public void enter() {
            enterTriggered = true;
        }

        @Override
        public void exit() {
            exitTriggered = true;
        }

        @Override
        public boolean processMessage(Message msg) {
            commandList.add(msg.what);
            return HANDLED;
        }
    }

    /**
     * A
     * -- B
     * C
     *
     * External transition behavior: B --> A triggers exit/enter on A (see b/220588514)
     */
    private class ExternalTransitionStateMachine extends StateMachine {
        public TestState A = new TestState("A");
        public TestState B = new TestState("B");
        public TestState C = new TestState("C");

        ExternalTransitionStateMachine(String name, Looper looper) {
            super(name, looper);

            addState(A);
            addState(B, A);
            addState(C);

            setInitialState(A);
        }

        public void resetTestFlags() {
            A.resetTestFlags();
            B.resetTestFlags();
            C.resetTestFlags();
        }
    }

    /**
     * Acontainer
     * -- A
     * -- B
     * C
     *
     * Pseudo-local transition (achieved via refactoring): B --> A will not trigger exit/enter
     *                                                     on Acontainer.
     */
    private class PseudoLocalTransitionStateMachine extends StateMachine {
        public TestState Acontainer = new TestState("Acontainer");
        public TestState A = new TestState("A");
        public TestState B = new TestState("B");
        public WaitingState Bwaiting;
        public TestState C = new TestState("C");

        PseudoLocalTransitionStateMachine(String name, Looper looper, boolean useWaitingState) {
            super(name, looper);

            addState(Acontainer);
            addState(A, Acontainer);
            if (useWaitingState) {
                Bwaiting = new WaitingState(this);
                addState(Bwaiting, Acontainer);
            } else {
                addState(B, Acontainer);
            }
            addState(C);

            setInitialState(A);
        }

        public void resetTestFlags() {
            Acontainer.resetTestFlags();
            A.resetTestFlags();
            B.resetTestFlags();
            C.resetTestFlags();
        }
    }

    private void initializeStateMachine(StateMachine sm) {
        sm.setDbg(true);
        sm.start();
    }

    private void transitionToState(StateMachine sm, State destState) {
        sm.transitionTo(destState);
        sm.sendMessage(0); // to trigger the transition
        mTestLooper.dispatchAll();
    }

    private void verifyEnterExit(TestState state, boolean enterExpected, boolean exitExpected) {
        assertEquals(state.getName() + " mismatch on Enter expectations", enterExpected,
                state.enterTriggered);
        assertEquals(state.getName() + " mismatch on Exit expectations", exitExpected,
                state.exitTriggered);
    }

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
    }

    /**
     * Validate the external transition state machine expectations.
     */
    @Test
    public void testExternalTransitionStateMachine() {
        ExternalTransitionStateMachine sm = new ExternalTransitionStateMachine(
                "ExternalTransitionStateMachine", mTestLooper.getLooper());
        initializeStateMachine(sm);
        mTestLooper.dispatchAll();

        // initial transition to the default state
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // A -> B: local transition
        transitionToState(sm, sm.B);
        verifyEnterExit(sm.A, false, false);
        verifyEnterExit(sm.B, true, false);
        sm.resetTestFlags();

        // B -> A: external transition
        transitionToState(sm, sm.A);
        verifyEnterExit(sm.B, false, true);
        verifyEnterExit(sm.A, true, true); // the external transition
        sm.resetTestFlags();

        // A -> C
        transitionToState(sm, sm.C);
        verifyEnterExit(sm.A, false, true);
        verifyEnterExit(sm.C, true, false);
        sm.resetTestFlags();

        // C -> A
        transitionToState(sm, sm.A);
        verifyEnterExit(sm.C, false, true);
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // (A ->) B -> C
        transitionToState(sm, sm.B);
        sm.resetTestFlags();
        transitionToState(sm, sm.C);
        verifyEnterExit(sm.B, false, true);
        verifyEnterExit(sm.A, false, true);
        verifyEnterExit(sm.C, true, false);
        sm.resetTestFlags();

        // C -> B
        transitionToState(sm, sm.B);
        verifyEnterExit(sm.C, false, true);
        verifyEnterExit(sm.A, true, false);
        verifyEnterExit(sm.B, true, false);
        sm.resetTestFlags();
    }

    /**
     * Validate the pseudo-local transition state machine expectations.
     */
    @Test
    public void testPseudoLocalTransitionStateMachine() {
        PseudoLocalTransitionStateMachine sm = new PseudoLocalTransitionStateMachine(
                "PseudoLocalTransitionStateMachine", mTestLooper.getLooper(), false);
        initializeStateMachine(sm);
        mTestLooper.dispatchAll();

        // initial transition to the default state
        verifyEnterExit(sm.Acontainer, true, false);
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // A -> B
        transitionToState(sm, sm.B);
        verifyEnterExit(sm.Acontainer, false, false);
        verifyEnterExit(sm.A, false, true);
        verifyEnterExit(sm.B, true, false);
        sm.resetTestFlags();

        // B -> A
        transitionToState(sm, sm.A);
        verifyEnterExit(sm.Acontainer, false, false);
        verifyEnterExit(sm.B, false, true);
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // A -> C
        transitionToState(sm, sm.C);
        verifyEnterExit(sm.A, false, true);
        verifyEnterExit(sm.Acontainer, false, true);
        verifyEnterExit(sm.C, true, false);
        sm.resetTestFlags();

        // C -> A
        transitionToState(sm, sm.A);
        verifyEnterExit(sm.C, false, true);
        verifyEnterExit(sm.Acontainer, true, false);
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // (A ->) B -> C
        transitionToState(sm, sm.B);
        sm.resetTestFlags();
        transitionToState(sm, sm.C);
        verifyEnterExit(sm.B, false, true);
        verifyEnterExit(sm.Acontainer, false, true);
        verifyEnterExit(sm.C, true, false);
        sm.resetTestFlags();

        // C -> B
        transitionToState(sm, sm.B);
        verifyEnterExit(sm.C, false, true);
        verifyEnterExit(sm.Acontainer, true, false);
        verifyEnterExit(sm.B, true, false);
        sm.resetTestFlags();
    }

    /**
     * Validate that the WaitingState transition command works and that it defers commands.
     */
    @Test
    public void testWaitingStateTransitionAndDeferral() {
        PseudoLocalTransitionStateMachine sm = new PseudoLocalTransitionStateMachine(
                "PseudoLocalTransitionStateMachine", mTestLooper.getLooper(), true);
        initializeStateMachine(sm);
        mTestLooper.dispatchAll();

        // initial transition to the default state
        verifyEnterExit(sm.Acontainer, true, false);
        verifyEnterExit(sm.A, true, false);
        sm.resetTestFlags();

        // execute commands and validate that they're run
        sm.sendMessage(10);
        sm.sendMessage(20);
        sm.sendMessage(15);
        mTestLooper.dispatchAll();
        assertArrayEquals("Sequence of commands", new Integer[]{10, 20, 15},
                sm.A.commandList.toArray());
        sm.resetTestFlags();

        // transition to the waiting state
        transitionToState(sm, sm.Bwaiting);
        mTestLooper.dispatchAll();
        verifyEnterExit(sm.Acontainer, false, false);
        verifyEnterExit(sm.A, false, true);
        sm.resetTestFlags();

        // execute commands - nothing should be run
        Message msg50 = sm.obtainMessage(50);
        Message msg70 = sm.obtainMessage(70);
        Message msg60 = sm.obtainMessage(60);
        assertFalse(WaitingState.wasMessageInWaitingState(msg50));
        assertFalse(WaitingState.wasMessageInWaitingState(msg70));
        assertFalse(WaitingState.wasMessageInWaitingState(msg60));
        sm.sendMessage(msg50);
        sm.sendMessage(msg70);
        sm.sendMessage(msg60);
        mTestLooper.dispatchAll();
        assertTrue("Nothing run in Acontainer", sm.Acontainer.commandList.isEmpty());
        assertTrue("Nothing run in A", sm.A.commandList.isEmpty());
        assertTrue(WaitingState.wasMessageInWaitingState(msg50));
        assertTrue(WaitingState.wasMessageInWaitingState(msg70));
        assertTrue(WaitingState.wasMessageInWaitingState(msg60));
        sm.resetTestFlags();

        // transition back to A
        sm.Bwaiting.sendTransitionStateCommand(sm.A);
        mTestLooper.dispatchAll();
        verifyEnterExit(sm.Acontainer, false, false);
        verifyEnterExit(sm.A, true, false);
        assertArrayEquals("Sequence of deferred commands now executed", new Integer[]{50, 70, 60},
                sm.A.commandList.toArray());
        sm.resetTestFlags();
    }
}
