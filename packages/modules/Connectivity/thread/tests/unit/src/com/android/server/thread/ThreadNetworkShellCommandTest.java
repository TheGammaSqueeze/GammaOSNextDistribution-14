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

package com.android.server.thread;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.os.Process;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Unit tests for {@link ThreadNetworkShellCommand}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ThreadNetworkShellCommandTest {
    private static final String TAG = "ThreadNetworkShellCommandTTest";
    @Mock ThreadNetworkService mThreadNetworkService;
    @Mock ThreadNetworkCountryCode mThreadNetworkCountryCode;
    @Mock PrintWriter mErrorWriter;
    @Mock PrintWriter mOutputWriter;

    ThreadNetworkShellCommand mThreadNetworkShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mThreadNetworkShellCommand = new ThreadNetworkShellCommand(mThreadNetworkCountryCode);
        mThreadNetworkShellCommand.setPrintWriters(mOutputWriter, mErrorWriter);
    }

    @After
    public void tearDown() throws Exception {
        validateMockitoUsage();
    }

    @Test
    public void getCountryCode_executeInUnrootedShell_allowed() {
        BinderUtil.setUid(Process.SHELL_UID);
        when(mThreadNetworkCountryCode.getCountryCode()).thenReturn("US");

        mThreadNetworkShellCommand.exec(
                new Binder(),
                new FileDescriptor(),
                new FileDescriptor(),
                new FileDescriptor(),
                new String[] {"get-country-code"});

        verify(mOutputWriter).println(contains("US"));
    }

    @Test
    public void forceSetCountryCodeEnabled_executeInUnrootedShell_notAllowed() {
        BinderUtil.setUid(Process.SHELL_UID);

        mThreadNetworkShellCommand.exec(
                new Binder(),
                new FileDescriptor(),
                new FileDescriptor(),
                new FileDescriptor(),
                new String[] {"force-country-code", "enabled", "US"});

        verify(mThreadNetworkCountryCode, never()).setOverrideCountryCode(eq("US"));
        verify(mErrorWriter).println(contains("force-country-code"));
    }

    @Test
    public void forceSetCountryCodeEnabled_executeInRootedShell_allowed() {
        BinderUtil.setUid(Process.ROOT_UID);

        mThreadNetworkShellCommand.exec(
                new Binder(),
                new FileDescriptor(),
                new FileDescriptor(),
                new FileDescriptor(),
                new String[] {"force-country-code", "enabled", "US"});

        verify(mThreadNetworkCountryCode).setOverrideCountryCode(eq("US"));
    }

    @Test
    public void forceSetCountryCodeDisabled_executeInUnrootedShell_notAllowed() {
        BinderUtil.setUid(Process.SHELL_UID);

        mThreadNetworkShellCommand.exec(
                new Binder(),
                new FileDescriptor(),
                new FileDescriptor(),
                new FileDescriptor(),
                new String[] {"force-country-code", "disabled"});

        verify(mThreadNetworkCountryCode, never()).setOverrideCountryCode(any());
        verify(mErrorWriter).println(contains("force-country-code"));
    }

    @Test
    public void forceSetCountryCodeDisabled_executeInRootedShell_allowed() {
        BinderUtil.setUid(Process.ROOT_UID);

        mThreadNetworkShellCommand.exec(
                new Binder(),
                new FileDescriptor(),
                new FileDescriptor(),
                new FileDescriptor(),
                new String[] {"force-country-code", "disabled"});

        verify(mThreadNetworkCountryCode).clearOverrideCountryCode();
    }
}
