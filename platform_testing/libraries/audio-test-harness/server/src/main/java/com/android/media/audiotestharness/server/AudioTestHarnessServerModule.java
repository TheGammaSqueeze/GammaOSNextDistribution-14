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

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.media.audiotestharness.server.javasound.JavaSoundModule;
import com.android.media.audiotestharness.server.service.AudioCaptureSessionFactory;
import com.android.media.audiotestharness.server.service.AudioCaptureSessionFactoryImpl;
import com.android.media.audiotestharness.server.service.AudioTestHarnessImpl;
import com.android.media.audiotestharness.server.service.StreamObserverOutputStreamFactory;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

import java.util.concurrent.Executor;

/**
 * Guice {@link AbstractModule} that defines all of the core dependencies for the AudioTestHarness
 * system.
 *
 * <p>While most dependencies are provided dynamically as needed, certain dependencies such as the
 * {@link java.util.concurrent.Executor} used for scheduling system tasks are defined in a singleton
 * format and are thus injected here upon creation.
 */
public final class AudioTestHarnessServerModule extends AbstractModule {

    private final Executor mExecutor;

    private AudioTestHarnessServerModule(Executor executor) {
        this.mExecutor = executor;
    }

    /**
     * Creates a new {@link AudioTestHarnessServerModule}.
     *
     * @param executor the {@link Executor} used for all background tasks executed throughout the
     *     system. This is used to ensure that at server startup and teardown any background tasks
     *     are properly stopped or cancelled.
     */
    public static AudioTestHarnessServerModule create(Executor executor) {
        Preconditions.checkNotNull(executor, "Executor cannot be null.");
        return new AudioTestHarnessServerModule(executor);
    }

    @Override
    protected void configure() {
        bind(Executor.class).toInstance(mExecutor);

        // Tie the gRPC Server to the JavaSound implementation.
        install(JavaSoundModule.create());

        // Audio Test Harness gRPC Service Implementation
        bind(AudioTestHarnessGrpc.AudioTestHarnessImplBase.class).to(AudioTestHarnessImpl.class);
        bind(StreamObserverOutputStreamFactory.class);
        bind(AudioCaptureSessionFactory.class).to(AudioCaptureSessionFactoryImpl.class);

        OptionalBinder.newOptionalBinder(binder(), SharedHostConfiguration.class)
                .setDefault()
                .toInstance(SharedHostConfiguration.getDefault());
    }
}
