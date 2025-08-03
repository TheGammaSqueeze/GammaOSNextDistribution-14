/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.Console.CaptureList;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.Pair;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link Console}. */
@RunWith(JUnit4.class)
public class ConsoleTest {

    @Mock ICommandScheduler mMockScheduler;
    private Console mConsole;
    private ProxyExceptionHandler mProxyExceptionHandler;
    private boolean mIsConsoleFunctional;

    private static class ProxyExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable mThrowable = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            mThrowable = e;
        }

        public void verify() throws Throwable {
            if (mThrowable != null) {
                throw mThrowable;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mIsConsoleFunctional = false;
        /**
         * Note: Eclipse doesn't play nice with consoles allocated like {@code new ConsoleReader()}.
         * To make an actual ConsoleReader instance, you should likely use the four-arg {@link
         * jline.ConsoleReader} constructor and use {@link jline.UnsupportedTerminal} or similar as
         * the implementation.
         */
        mConsole =
                new Console(null) {
                    @Override
                    boolean isConsoleFunctional() {
                        return mIsConsoleFunctional;
                    }
                };
        mConsole.setCommandScheduler(mMockScheduler);
        mProxyExceptionHandler = new ProxyExceptionHandler();
        mConsole.setUncaughtExceptionHandler(mProxyExceptionHandler);
    }

    @After
    public void tearDown() {
        if (mConsole != null) {
            mConsole.exitConsole();
            mConsole.interrupt();
        }
    }

    /** Test normal Console run when system console is available */
    @Test
    public void testRun_withConsole() throws Throwable {
        mConsole.setName("testRun_withConsole");
        mIsConsoleFunctional = true;

        // non interactive mode needs some args to start
        mConsole.setArgs(Arrays.asList("help"));
        mConsole.start();
        mConsole.join();
        mProxyExceptionHandler.verify();
        InOrder inOrder = Mockito.inOrder(mMockScheduler);
        inOrder.verify(mMockScheduler).start();
        inOrder.verify(mMockScheduler).shutdown();
        verify(mMockScheduler).start();
        verify(mMockScheduler).shutdown();
    }

    /**
     * Test that an interactive console does return and doesn't not stay up when started with
     * 'help'.
     */
    @Test
    public void testRun_withConsoleInteractiveHelp() throws Throwable {
        mConsole =
                new Console() {
                    @Override
                    boolean isConsoleFunctional() {
                        return mIsConsoleFunctional;
                    }
                };
        mConsole.setName("testRun_withConsoleInteractiveHelp");
        mConsole.setCommandScheduler(mMockScheduler);
        mProxyExceptionHandler = new ProxyExceptionHandler();
        mConsole.setUncaughtExceptionHandler(mProxyExceptionHandler);
        mIsConsoleFunctional = true;

        mConsole.setArgs(Arrays.asList("help"));
        mConsole.start();
        // join has a timeout otherwise it may hang forever.
        mConsole.join(2000);
        assertFalse(mConsole.isAlive());
        mProxyExceptionHandler.verify();
        InOrder inOrder = Mockito.inOrder(mMockScheduler);
        inOrder.verify(mMockScheduler).start();
        inOrder.verify(mMockScheduler).shutdown();
        verify(mMockScheduler).start();
        verify(mMockScheduler).shutdown();
    }

    /**
     * Test that an interactive console stays up when started without 'help' and scheduler does not
     * shutdown.
     */
    @Test
    public void testRun_withConsoleInteractive_noHelp() throws Throwable {
        mConsole =
                new Console() {
                    @Override
                    boolean isConsoleFunctional() {
                        return mIsConsoleFunctional;
                    }

                    @Override
                    String getConsoleInput() throws IOException {
                        return "test";
                    }
                };
        mConsole.setName("testRun_withConsoleInteractive_noHelp");
        mConsole.setCommandScheduler(mMockScheduler);
        mProxyExceptionHandler = new ProxyExceptionHandler();
        mConsole.setUncaughtExceptionHandler(mProxyExceptionHandler);
        mIsConsoleFunctional = true;

        // No scheduler shutdown is expected.

        try {
            mConsole.start();
            // join has a timeout otherwise it hangs forever.
            mConsole.join(100);
            assertTrue(mConsole.isAlive());
            mProxyExceptionHandler.verify();

            verify(mMockScheduler).start();
        } finally {
            mConsole.exitConsole();
            RunUtil.getDefault()
                    .interrupt(
                            mConsole, "interrupting", InfraErrorIdentifier.TRADEFED_SHUTTING_DOWN);
            mConsole.interrupt();
            mConsole.join(2000);
        }
        assertFalse("Console thread has not stopped.", mConsole.isAlive());
    }

    /** Test normal Console run when system console is _not_ available */
    @Test
    public void testRun_noConsole() throws Throwable {
        mConsole.setName("testRun_noConsole");
        mIsConsoleFunctional = false;

        // non interactive mode needs some args to start
        mConsole.setArgs(Arrays.asList("help"));
        mConsole.start();
        mConsole.join();
        mProxyExceptionHandler.verify();

        verify(mMockScheduler).start();
        verify(mMockScheduler).shutdown();
    }

    /** Make sure that "run command foo config.xml" works properly. */
    @Test
    public void testRunCommand() throws Exception {
        mConsole.setName("testRunCommand");
        String[] command = new String[] {"run", "command", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        when(mMockScheduler.addCommand(AdditionalMatchers.aryEq(expected)))
                .thenReturn(Pair.create(Boolean.TRUE, 1));

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(
                String.format("Console didn't match input %s", Arrays.toString(command)), runnable);
        mConsole.executeCmdRunnable(runnable, captures);
    }

    /** Make sure that the "run foo config.xml" shortcut works properly. */
    @Test
    public void testRunCommand_shortcut() throws Exception {
        mConsole.setName("testRunCommand_shortcut");
        String[] command = new String[] {"run", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        when(mMockScheduler.addCommand(AdditionalMatchers.aryEq(expected)))
                .thenReturn(Pair.create(Boolean.TRUE, 1));

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(
                String.format("Console didn't match input %s", Arrays.toString(command)), runnable);
        mConsole.executeCmdRunnable(runnable, captures);
    }

    /**
     * Make sure that the command "run command command foo config.xml" properly considers the second
     * "command" to be the first token of the command to be executed.
     */
    @Test
    public void testRunCommand_startsWithCommand() throws Exception {
        mConsole.setName("testRunCommand_startsWithCommand");
        String[] command =
                new String[] {"run", "command", "command", "--arg", "value", "config.xml"};
        String[] expected = new String[] {"command", "--arg", "value", "config.xml"};
        CaptureList captures = new CaptureList();
        RegexTrie<Runnable> trie = mConsole.getCommandTrie();

        when(mMockScheduler.addCommand(AdditionalMatchers.aryEq(expected)))
                .thenReturn(Pair.create(Boolean.TRUE, 1));

        Runnable runnable = trie.retrieve(captures, command);
        assertNotNull(
                String.format("Console didn't match input %s", Arrays.toString(command)), runnable);
        mConsole.executeCmdRunnable(runnable, captures);
    }

    /** Make sure that {@link Console#getFlatArgs} works as expected. */
    @Test
    public void testFlatten() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        List<String> flat = Console.getFlatArgs(1, cl);
        assertEquals(2, flat.size());
        assertEquals("alpha", flat.get(0));
        assertEquals("beta", flat.get(1));
    }

    /** Make sure that {@link Console#getFlatArgs} throws an exception when argIdx is wrong. */
    @Test
    public void testFlatten_wrongArgIdx() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        // argIdx is 0, and element 0 has size 2
        try {
            Console.getFlatArgs(0, cl);
            fail("IllegalArgumentException not thrown!");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** Make sure that {@link Console#getFlatArgs} throws an exception when argIdx is OOB. */
    @Test
    public void testFlatten_argIdxOOB() throws Exception {
        CaptureList cl = new CaptureList();
        cl.add(Arrays.asList("run", null));
        cl.add(Arrays.asList("alpha"));
        cl.add(Arrays.asList("beta"));
        try {
            Console.getFlatArgs(1 + cl.size(), cl);
            fail("IndexOutOfBoundsException not thrown!");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }
}
