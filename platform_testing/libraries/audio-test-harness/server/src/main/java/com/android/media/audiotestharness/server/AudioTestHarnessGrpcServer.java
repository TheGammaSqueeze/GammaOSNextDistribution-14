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

import com.google.common.base.Preconditions;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * gRPC Server to the Audio Test Harness Infrastructure.
 *
 * <p>This class is designed in such a way that the spin up and tear down of the gRPC server,
 * including all dependencies are abstracted away from any users of Audio Test Harness. It is
 * possible to use the service implementation directly and customize the deployment of the service,
 * however, this is meant as an easy alternative for users who just want a working system and don't
 * care how it works under the hood.
 *
 * <p>This class should not be subclassed or extended, however is left non-final for testing
 * purposes.
 */
public class AudioTestHarnessGrpcServer implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(AudioTestHarnessGrpcServer.class.getName());

    /** The port on which the gRPC Server should be executed on. */
    private final int mPort;

    /**
     * {@link Executor} used for background task execution. These tasks can be the handling of gRPC
     * calls or other background tasks in the system such as the publishing of audio sample data by
     * a {@link com.android.media.audiotestharness.server.core.AudioCapturer}.
     */
    private final ExecutorService mExecutor;

    /** {@link Injector} used for dependency management and injection. */
    private final Injector mInjector;

    /** Underlying {@link Server} that manages gRPC calls. */
    private Server mServer;

    AudioTestHarnessGrpcServer(int port, ExecutorService executor, Injector injector) {
        LOGGER.finest(String.format("new AudioTestHarnessGrpcServer on Port (%d)", port));

        this.mPort = port;
        this.mExecutor = executor;
        this.mInjector = injector;
    }

    public static void main(String[] args) {
        try (AudioTestHarnessGrpcServer server =
                AudioTestHarnessGrpcServerFactory.createFactory()
                        .createOnTestingPort(SharedHostConfiguration.getDefault())) {
            server.open();

            // Ensure that resources are cleanly stopped upon JVM shutdown.
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));

            // Wait for server execution to complete. So that CTRL + C or some other mechanism must
            // be used to kill the server.
            server.mServer.awaitTermination();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to start the Audio Test Harness gRPC Server.", e);
        }
    }

    /**
     * Starts the Audio Test Harness gRPC Server listening on {@link #mPort}.
     *
     * @throws IOException if any errors occur while provisioning or starting the server.
     */
    public void open() throws IOException {
        Preconditions.checkState(mServer == null, "Server has already been opened.");

        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(mPort).executor(mExecutor);

        try {
            serverBuilder.addService(
                    mInjector.getInstance(AudioTestHarnessGrpc.AudioTestHarnessImplBase.class));
        } catch (ConfigurationException ce) {
            throw new IOException(
                    "Unable to create new Audio Test Harness gRPC Server, failed to resolve"
                            + " required dependency.",
                    ce);
        }

        mServer = serverBuilder.build();
        LOGGER.fine("Successfully created Audio Test Harness gRPC Server.");

        mServer.start();
        LOGGER.info(
                String.format(
                        "Started Audio Test Harness gRPC Server Listening on Port %d", mPort));
    }

    /**
     * Stops the Audio Test Harness gRPC Server immediately and closes any underlying resources
     * being used by the server.
     */
    @Override
    public void close() {
        LOGGER.info("Stopping Audio Test Harness gRPC Server");
        if (mServer != null) {
            mServer.shutdownNow();
            mServer = null;
        } else {
            LOGGER.warning(
                    "mServer is null indicating that the Audio Test Harness gRPC Server was never"
                            + " started.");
        }
    }

    public int getPort() {
        return mPort;
    }
}
