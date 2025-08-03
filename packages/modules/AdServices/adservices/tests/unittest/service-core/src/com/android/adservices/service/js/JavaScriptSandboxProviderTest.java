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

package com.android.adservices.service.js;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.javascriptengine.JavaScriptSandbox;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.FutureSyncCallback;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.profiling.JSScriptEngineLogConstants;
import com.android.adservices.service.profiling.Profiler;
import com.android.adservices.service.profiling.StopWatch;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SmallTest
public class JavaScriptSandboxProviderTest {
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private final LoggerFactory.Logger mLogger = LoggerFactory.getFledgeLogger();
    private StaticMockitoSession mStaticMockSession;
    @Mock private StopWatch mSandboxInitWatch;
    @Mock private JavaScriptSandbox mSandbox;
    @Mock private JavaScriptSandbox mSandbox2;

    @Mock private Profiler mProfilerMock;

    private JSScriptEngine.JavaScriptSandboxProvider mJsSandboxProvider;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(JavaScriptSandbox.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void shutDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testJsSandboxProviderCreateFailsIfSandboxNotSupported() {
        when(JavaScriptSandbox.isSupported()).thenReturn(false);
        mJsSandboxProvider = new JSScriptEngine.JavaScriptSandboxProvider(mProfilerMock, mLogger);
        ThrowingRunnable getFutureInstance =
                () -> mJsSandboxProvider.getFutureInstance(mApplicationContext).get();
        Exception futureException = assertThrows(ExecutionException.class, getFutureInstance);
        assertThat(futureException)
                .hasCauseThat()
                .isInstanceOf(JSSandboxIsNotAvailableException.class);
        verify(JavaScriptSandbox::isSupported);
    }

    @Test
    public void testJsSandboxProviderCreatesOnlyOneInstance()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(JavaScriptSandbox.isSupported()).thenReturn(true);
        doReturn(Futures.immediateFuture(mSandbox))
                .when(
                        () -> {
                            return JavaScriptSandbox.createConnectedInstanceAsync(
                                    mApplicationContext);
                        });

        when(mProfilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        mJsSandboxProvider = new JSScriptEngine.JavaScriptSandboxProvider(mProfilerMock, mLogger);

        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        verify(() -> JavaScriptSandbox.createConnectedInstanceAsync(mApplicationContext));
        verify(mProfilerMock).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }

    @Test
    public void testJsSandboxProviderCreatesNewInstanceAfterFirstIsDestroyed()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(JavaScriptSandbox.isSupported()).thenReturn(true);
        doReturn(Futures.immediateFuture(mSandbox))
                .when(
                        () -> {
                            return JavaScriptSandbox.createConnectedInstanceAsync(
                                    mApplicationContext);
                        });

        when(mProfilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);
        mJsSandboxProvider = new JSScriptEngine.JavaScriptSandboxProvider(mProfilerMock, mLogger);
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        // Waiting for the first instance closure
        mJsSandboxProvider.destroyCurrentInstance().get(4, TimeUnit.SECONDS);

        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);

        verify(
                () -> JavaScriptSandbox.createConnectedInstanceAsync(mApplicationContext),
                Mockito.times(2));

        verify(mProfilerMock, Mockito.times(2)).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }

    @Test
    public void testJsSandboxProviderDestroysOnlyIfCurrentInstance()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(JavaScriptSandbox.isSupported()).thenReturn(true);
        when(JavaScriptSandbox.createConnectedInstanceAsync(mApplicationContext))
                .thenReturn(Futures.immediateFuture(mSandbox))
                .thenReturn(Futures.immediateFuture(mSandbox2));
        doNothing().when(mSandbox).close();
        when(mProfilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);

        mJsSandboxProvider = new JSScriptEngine.JavaScriptSandboxProvider(mProfilerMock, mLogger);
        JavaScriptSandbox sandbox1 =
                mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);
        // Waiting for the first instance closure
        mJsSandboxProvider.destroyIfCurrentInstance(sandbox1).get(4, TimeUnit.SECONDS);
        mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);
        mJsSandboxProvider.destroyIfCurrentInstance(sandbox1).get(4, TimeUnit.SECONDS);

        verify(mSandbox).close();
        verify(mSandbox2, Mockito.never()).close();
        verify(mProfilerMock, Mockito.times(2)).start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME);
    }

    @Test
    public void testJsSandboxProviderDestroysOnlyIfCurrentInstanceOnlyOnce()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(JavaScriptSandbox.isSupported()).thenReturn(true);
        when(JavaScriptSandbox.createConnectedInstanceAsync(mApplicationContext))
                .thenReturn(Futures.immediateFuture(mSandbox))
                .thenThrow(
                        new IllegalStateException(
                                "createConnectedInstanceAsync should only be called once from the"
                                        + " test"));
        doNothing().when(mSandbox).close();
        when(mProfilerMock.start(JSScriptEngineLogConstants.SANDBOX_INIT_TIME))
                .thenReturn(mSandboxInitWatch);

        mJsSandboxProvider = new JSScriptEngine.JavaScriptSandboxProvider(mProfilerMock, mLogger);
        JavaScriptSandbox sandbox1 =
                mJsSandboxProvider.getFutureInstance(mApplicationContext).get(5, TimeUnit.SECONDS);
        FutureSyncCallback<Void> callback1 = new FutureSyncCallback<>();
        FutureSyncCallback<Void> callback2 = new FutureSyncCallback<>();
        FluentFuture.from(mJsSandboxProvider.destroyIfCurrentInstance(sandbox1))
                .addCallback(callback1, Runnable::run);
        FluentFuture.from(mJsSandboxProvider.destroyIfCurrentInstance(sandbox1))
                .addCallback(callback2, Runnable::run);

        callback1.assertResultReceived();
        callback2.assertResultReceived();
        verify(mSandbox).close();
    }
}
