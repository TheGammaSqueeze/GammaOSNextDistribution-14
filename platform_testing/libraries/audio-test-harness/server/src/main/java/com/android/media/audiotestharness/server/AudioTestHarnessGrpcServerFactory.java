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

package com.android.media.audiotestharness.server;

import com.android.media.audiotestharness.proto.AudioTestHarnessGrpc;
import com.android.media.audiotestharness.server.config.SharedHostConfiguration;
import com.android.media.audiotestharness.server.config.SharedHostConfigurationModule;
import com.android.media.audiotestharness.server.utility.PortUtility;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Factory for {@link AudioTestHarnessGrpcServer} instances.
 *
 * <p>This class is not meant to be extended, however is left non-final for mocking purposes.
 */
public class AudioTestHarnessGrpcServerFactory implements AutoCloseable {
    private static final Logger LOGGER =
            Logger.getLogger(AudioTestHarnessGrpcServerFactory.class.getName());

    /** Default port used for testing purposes. */
    private static final int TESTING_PORT = 8080;

    /**
     * Default number of threads that should be used for task execution.
     *
     * <p>This value is not used when using a provided {@link ExecutorService} and thus can be
     * overridden in cases where necessary.
     */
    private static final int DEFAULT_THREAD_COUNT = 16;

    /**
     * {@link Executor} used for task execution throughout the system.
     *
     * <p>This executor is used both by the gRPC server as well as in underlying libraries such as
     * the javasoundlib which uses the Executor to handle background capture while another thread
     * handles gRPC actions.
     */
    private final ExecutorService mExecutorService;

    /**
     * {@link AbstractModule} that should be used as the base module for the system's dependency
     * injection. This can be overridden for testing or to substitute other implementations.
     *
     * <p>At the minimum, this module must be able to provide an instance of the {@link
     * com.android.media.audiotestharness.proto.AudioTestHarnessGrpc.AudioTestHarnessImplBase} which
     * is required by the system at runtime.
     */
    private final AbstractModule mBaseModule;

    private AudioTestHarnessGrpcServerFactory(
            ExecutorService executorService, AbstractModule baseModule) {
        mExecutorService = executorService;
        mBaseModule = baseModule;
    }

    /**
     * Creates a new {@link AudioTestHarnessGrpcServerFactory} with the default ExecutorService,
     * which is a {@link java.util.concurrent.ThreadPoolExecutor} with {@link #DEFAULT_THREAD_COUNT}
     * threads.
     */
    public static AudioTestHarnessGrpcServerFactory createFactory() {
        ExecutorService executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT);
        return createInternal(
                Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT),
                AudioTestHarnessServerModule.create(executorService));
    }

    /**
     * Creates a new {@link AudioTestHarnessGrpcServerFactory} with the provided ExecutorService.
     *
     * <p>All created AudioTestHarnessGrpcServer instances will make use of this executor for tasks.
     * Furthermore, this {@link ExecutorService} will be shutdown whenever the {@link #close()}
     * method is invoked on this factory.
     */
    public static AudioTestHarnessGrpcServerFactory createFactoryWithExecutorService(
            ExecutorService executorService) {
        return createInternal(
                executorService, AudioTestHarnessServerModule.create(executorService));
    }

    @VisibleForTesting
    static AudioTestHarnessGrpcServerFactory createInternal(
            ExecutorService executorService, AbstractModule baseModule) {
        return new AudioTestHarnessGrpcServerFactory(
                Preconditions.checkNotNull(executorService, "ExecutorService cannot be null."),
                baseModule);
    }

    /**
     * Creates a new {@link AudioTestHarnessGrpcServer} on the specified port.
     *
     * <p>This port is not reserved or used until the server's {@link
     * AudioTestHarnessGrpcServer#open()} method is called.
     *
     * @param sharedHostConfiguration the {@link SharedHostConfiguration} that should be used in the
     *     system. This can be used to override configurable values (such as the device's capture
     *     device) from their defaults.
     */
    public AudioTestHarnessGrpcServer createOnPort(
            int port, @Nullable SharedHostConfiguration sharedHostConfiguration) {
        LOGGER.finest(String.format("createOnPort(%d, %s)", port, sharedHostConfiguration));
        LOGGER.info(
                String.format(
                        "Shared Host Configuration is (%s)",
                        sharedHostConfiguration == null ? "Default" : sharedHostConfiguration));

        // Create an injector for the Audio Test Harness server, if a custom sharedHostConfiguration
        // was provided, then we add another module which provides this configuration, otherwise,
        // we create the module with mBaseModule only.
        Injector injector =
                sharedHostConfiguration == null
                        ? Guice.createInjector(mBaseModule)
                        : Guice.createInjector(
                                mBaseModule,
                                SharedHostConfigurationModule.create(sharedHostConfiguration));

        // Verify that the AudioTestHarnessImplBase class is bound, without this binding, the server
        // will not operate.
        if (injector.getExistingBinding(
                        Key.get(AudioTestHarnessGrpc.AudioTestHarnessImplBase.class))
                == null) {
            throw new IllegalStateException(
                    "Cannot create new AudioTestHarnessGrpcServer because there is no binding for"
                            + " the Audio Test Harness gRPC Service in the module provided at "
                            + "factory creation.");
        }

        return new AudioTestHarnessGrpcServer(port, mExecutorService, injector);
    }

    /**
     * Creates a new {@link AudioTestHarnessGrpcServer} on the {@link #TESTING_PORT}.
     *
     * @param sharedHostConfiguration the {@link SharedHostConfiguration} that should be used in the
     *     system. This can be used to override configurable values (such as the device's capture
     *     device) from their defaults.
     */
    public AudioTestHarnessGrpcServer createOnTestingPort(
            @Nullable SharedHostConfiguration sharedHostConfiguration) {
        LOGGER.finest("createOnTestingPort()");
        return createOnPort(TESTING_PORT, sharedHostConfiguration);
    }

    /**
     * Creates a new {@link AudioTestHarnessGrpcServer} on the next available port within the
     * dynamic port range.
     *
     * @param sharedHostConfiguration the {@link SharedHostConfiguration} that should be used in the
     *     system. This can be used to override configurable values (such as the device's capture
     *     device) from their defaults.
     */
    public AudioTestHarnessGrpcServer createOnNextAvailablePort(
            @Nullable SharedHostConfiguration sharedHostConfiguration) {
        LOGGER.finest("createOnNextAvailablePort()");
        return createOnPort(PortUtility.nextAvailablePort(), sharedHostConfiguration);
    }

    /** Shuts down the {@link ExecutorService} used by the factory. */
    @Override
    public void close() {
        LOGGER.fine("Shutting down internal ExecutorService");
        mExecutorService.shutdownNow();
    }
}
