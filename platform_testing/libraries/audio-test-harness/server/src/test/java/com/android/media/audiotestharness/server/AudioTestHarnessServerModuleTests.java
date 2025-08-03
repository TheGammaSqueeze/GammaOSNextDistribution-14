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

package com.android.media.audiotestharness.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.media.audiotestharness.server.core.AudioSystemService;
import com.android.media.audiotestharness.server.service.AudioCaptureSessionFactory;
import com.android.media.audiotestharness.server.service.StreamObserverOutputStreamFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public class AudioTestHarnessServerModuleTests {

    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock Executor mExecutor;

    private Injector mInjector;

    @Before
    public void setUp() throws Exception {
        this.mInjector = Guice.createInjector(AudioTestHarnessServerModule.create(mExecutor));
    }

    @Test
    public void getInstance_executor_returnsProperExecutor() throws Exception {
        assertEquals(mExecutor, mInjector.getInstance(Executor.class));
    }

    @Test
    public void getInstance_AudioTestHarnessBaseImpl_returnsInstance() throws Exception {
        assertNotNull(mInjector.getInstance(AudioTestHarnessGrpc.AudioTestHarnessImplBase.class));
    }

    @Test
    public void getInstance_StreamObserverOutputStreamFactory_returnsInstance() throws Exception {
        assertNotNull(mInjector.getInstance(StreamObserverOutputStreamFactory.class));
    }

    @Test
    public void getInstance_AudioSystemService_returnsInstance() throws Exception {
        assertNotNull(mInjector.getInstance(AudioSystemService.class));
    }

    @Test
    public void getInstance_AudioCaptureSessionFactory_returnsInstance() throws Exception {
        assertNotNull(mInjector.getInstance(AudioCaptureSessionFactory.class));
    }

    @Test
    public void getInstance_SharedHostConfiguration_returnsInstance() throws Exception {
        assertNotNull(mInjector.getInstance(SharedHostConfiguration.class));
    }
}
