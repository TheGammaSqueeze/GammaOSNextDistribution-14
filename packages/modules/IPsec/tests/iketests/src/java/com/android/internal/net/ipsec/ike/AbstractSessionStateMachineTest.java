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

package com.android.internal.net.ipsec.test.ike;

import static com.android.internal.net.ipsec.test.ike.AbstractSessionStateMachine.CMD_KILL_SESSION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.os.Looper;
import android.os.Message;
import android.os.test.TestLooper;

import com.android.internal.net.ipsec.test.ike.AbstractSessionStateMachine.ExceptionHandlerBase;
import com.android.internal.net.ipsec.test.ike.utils.IkeMetrics;
import com.android.internal.net.ipsec.test.ike.utils.State;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;

public final class AbstractSessionStateMachineTest extends IkeSessionTestBase {
    private IkeContext mIkeContext;
    private TestLooper mLooper;
    private TestSessionStateMachine mTestSm;

    @Before
    public void setup() throws Exception {
        mLooper = new TestLooper();
        mIkeContext = mock(IkeContext.class);
        doReturn(mLooper.getLooper()).when(mIkeContext).getLooper();
        mTestSm = new TestSessionStateMachine(mLooper.getLooper());

        mTestSm.start();
        mLooper.dispatchAll();
    }

    @After
    public void tearDown() {
        mTestSm.quitNow();
        mLooper.dispatchAll();
    }

    private final class TestSessionStateMachine extends AbstractSessionStateMachine {
        static final int CMD_TEST = CMD_PRIVATE_BASE + 1;

        final ArrayList mExecutedCmds = new ArrayList<>();

        private final State mInitial = new Initial();

        TestSessionStateMachine(Looper looper) {
            super(
                    "TestSessionStateMachine",
                    AbstractSessionStateMachineTest.this.mIkeContext,
                    mock(Executor.class));

            addState(mInitial);
            setInitialState(mInitial);
        }

        class Initial extends ExceptionHandlerBase {
            @Override
            public boolean processStateMessage(Message message) {
                mExecutedCmds.add(message.what);

                switch (message.what) {
                    case CMD_TEST:
                        return HANDLED;
                    case CMD_KILL_SESSION:
                        quitNow();
                        return HANDLED;
                    default:
                        return NOT_HANDLED;
                }
            }

            @Override
            protected void cleanUpAndQuit(RuntimeException e) {
                // do nothing
            }

            @Override
            protected String getCmdString(int cmd) {
                return Integer.toString(cmd);
            }

            @Override
            protected @IkeMetrics.IkeState int getMetricsStateCode() {
                return 0;
            }
        }

        @Override
        protected @IkeMetrics.IkeSessionType int getMetricsSessionType() {
            return 0;
        }
    }

    @Test
    public void testKillSessionDiscardsOtherCmds() throws Exception {
        mTestSm.sendMessage(TestSessionStateMachine.CMD_TEST);
        mTestSm.killSession();
        mLooper.dispatchAll();

        assertEquals(Arrays.asList(CMD_KILL_SESSION), mTestSm.mExecutedCmds);
        assertNull(mTestSm.getCurrentState());
    }
}
