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

package android.content.pm.cts;

import static android.system.OsConstants.EAGAIN;
import static android.system.OsConstants.EINTR;

import static org.junit.Assert.assertNull;

import android.annotation.NonNull;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import android.util.Slog;

import com.android.incfs.install.IDeviceConnection;
import com.android.incfs.install.ILogger;

import libcore.io.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class IncrementalDeviceConnection implements IDeviceConnection {
    private static final String TAG = "IncrementalDeviceConnection";
    private static final boolean DEBUG = false;

    private static final int POLL_TIMEOUT_MS = 300000;

    private enum ConnectionType {
        RELIABLE,
        UNRELIABLE,
    }

    private ConnectionType mConnectionType;
    private final Thread mShellCommand;
    private final ParcelFileDescriptor mPfd;
    private final FileDescriptor mFd;
    private final StructPollfd[] mPollfds = new StructPollfd[]{
            new StructPollfd()
    };

    private IncrementalDeviceConnection(ConnectionType connectionType, Thread shellCommand,
            ParcelFileDescriptor pfd) {
        mConnectionType = connectionType;
        mShellCommand = shellCommand;
        mPfd = pfd;
        mFd = pfd.getFileDescriptor();

        mPollfds[0].fd = mFd;
        mPollfds[0].events = (short) OsConstants.POLLIN;
        mPollfds[0].revents = 0;
        mPollfds[0].userData = null;

        mShellCommand.start();
    }

    @Override
    public int read(ByteBuffer buffer, long timeoutMs) throws IOException {
        final boolean blocking = buffer.position() == 0;
        while (true) {
            try {
                int res = Os.poll(mPollfds, blocking ? POLL_TIMEOUT_MS : 0);
                if (res < 0) {
                    return res;
                }
                if (res == 0) {
                    if (blocking) {
                        throw new IOException("timeout");
                    }
                    return 0;
                }
                return Os.read(mFd, buffer);
            } catch (ErrnoException e) {
                if (e.errno == EINTR) {
                    continue;
                }
                if (mConnectionType == ConnectionType.UNRELIABLE) {
                    e.rethrowAsIOException();
                }
                if (e.errno == EAGAIN) {
                    if (!blocking) {
                        return 0;
                    }
                    continue;
                }
                e.rethrowAsIOException();
            }
        }
    }

    @Override
    public int write(ByteBuffer buffer, long timeoutMs) throws IOException {
        try {
            return Os.write(mFd, buffer);
        } catch (ErrnoException e) {
            e.rethrowAsIOException();
        }
        return 0;
    }

    @Override
    public void close() throws Exception {
        mShellCommand.join();
        IoUtils.closeQuietly(mPfd);
    }

    static class Logger implements ILogger {
        @Override
        public void error(Throwable t, String msgFormat, Object... args) {
            Slog.e(TAG, String.format(msgFormat, args), t);
        }

        @Override
        public void warning(String msgFormat, Object... args) {
            Slog.w(TAG, String.format(msgFormat, args));
        }

        @Override
        public void info(String msgFormat, Object... args) {
            Slog.i(TAG, String.format(msgFormat, args));
        }

        @Override
        public void verbose(String msgFormat, Object... args) {
            if (!DEBUG) {
                return;
            }
            Slog.v(TAG, String.format(msgFormat, args));
        }
    }

    static class Factory implements IDeviceConnection.Factory {
        private final ConnectionType mConnectionType;
        private final boolean mExpectInstallationSuccess;

        static Factory reliable() {
            return new Factory(ConnectionType.RELIABLE, true);
        }

        static Factory ureliable() {
            return new Factory(ConnectionType.UNRELIABLE, false);
        }

        static Factory reliableExpectInstallationFailure() {
            return new Factory(ConnectionType.RELIABLE, false);
        }

        private Factory(ConnectionType connectionType, boolean expectInstallationSuccess) {
            mConnectionType = connectionType;
            mExpectInstallationSuccess = expectInstallationSuccess;
        }

        @Override
        public IDeviceConnection connectToService(@NonNull String service,
                @NonNull String[] parameters) throws IOException {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createSocketPair();
            IoUtils.setBlocking(pipe[0].getFileDescriptor(), false);
            IoUtils.setBlocking(pipe[1].getFileDescriptor(), false);

            final ParcelFileDescriptor localPfd = pipe[0];
            final ParcelFileDescriptor processPfd = pipe[1];

            final ResultReceiver resultReceiver = new ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (resultCode == 0) {
                        return;
                    }
                    Log.i(TAG, (mExpectInstallationSuccess ? "" : "ERROR: ")
                            + "Installation finished with code: " + resultCode + ", message: "
                            + resultData);
                }
            };

            final Thread shellCommand = new Thread(() -> {
                try {
                    final FileDescriptor processFd = processPfd.getFileDescriptor();
                    ServiceManager.getService(service).shellCommand(processFd, processFd, processFd,
                            parameters, null, resultReceiver);
                } catch (RemoteException e) {
                    if (mConnectionType == ConnectionType.RELIABLE) {
                        assertNull(e);
                    }
                } finally {
                    IoUtils.closeQuietly(processPfd);
                }
            });
            return new IncrementalDeviceConnection(mConnectionType, shellCommand, localPfd);
        }
    }

    private static String readFullStreamOrError(InputStream inputStream) {
        try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            try {
                final byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
            } catch (IOException e) {
                return result.toString("UTF-8") + " exception [" + e + "]";
            }
            return result.toString("UTF-8");
        } catch (IOException e) {
            return e.toString();
        }
    }
}
