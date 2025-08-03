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

package com.android.bluetooth;

import com.android.obex.ObexTransport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * A fake obex transport used for testing Client/Server connections.
 * The transport uses two pairs of pipes to route input from the client to the server, and back.
 * The obex transport is of the simplest form, returning default values for everything.
 */
public class FakeObexTransport {
    ObexTransport mClientTransport;
    ObexTransport mServerTransport;

    PipedInputStream mClientInputStream;
    PipedInputStream mServerInputStream;
    PipedOutputStream mClientOutputStream;
    PipedOutputStream mServerOutputStream;

    public FakeObexTransport() throws IOException {
        mClientInputStream = new PipedInputStream();
        mServerOutputStream = new PipedOutputStream(mClientInputStream);
        mServerInputStream = new PipedInputStream();
        mClientOutputStream = new PipedOutputStream(mServerInputStream);

        mClientTransport = new BiDirectionalTransport(mClientInputStream, mClientOutputStream);
        mServerTransport = new BiDirectionalTransport(mServerInputStream, mServerOutputStream);
    }

    static class BiDirectionalTransport implements ObexTransport {

        InputStream mInputStream;
        OutputStream mOutputStream;

        BiDirectionalTransport(InputStream inputStream, OutputStream outputStream) {
            mInputStream = inputStream;
            mOutputStream = outputStream;
        }

        @Override
        public DataInputStream openDataInputStream() throws IOException {
            return new DataInputStream(openInputStream());
        }

        @Override
        public DataOutputStream openDataOutputStream() throws IOException {
            return new DataOutputStream(openOutputStream());
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return mInputStream;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return mOutputStream;
        }

        @Override
        public void connect() throws IOException {
        }

        @Override
        public void create() throws IOException {
        }

        @Override
        public void disconnect() throws IOException {
        }

        @Override
        public void listen() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }

        public boolean isConnected() throws IOException {
            return true;
        }

        @Override
        public int getMaxTransmitPacketSize() {
            return -1;
        }

        @Override
        public int getMaxReceivePacketSize() {
            return -1;
        }

        @Override
        public boolean isSrmSupported() {
            return true;
        }
    }
}
