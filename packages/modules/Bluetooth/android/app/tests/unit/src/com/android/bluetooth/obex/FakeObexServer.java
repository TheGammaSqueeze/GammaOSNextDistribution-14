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

import com.android.obex.HeaderSet;
import com.android.obex.ObexTransport;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;
import com.android.obex.ServerRequestHandler;
import com.android.obex.ServerSession;

import java.io.IOException;

/**
 * A fake obex server for testing obex clients. Test cases should implement *Validator functions to
 * validate input and return appropriate responses for individual tests.
 *
 * Note: it is important to not perform any testing Assert operations within the validators as that
 * would crash the testing framework.
 */
public abstract class FakeObexServer {

    public ObexTransport mClientObexTransport;

    private ObexTransport mServerObexTransport;
    private Server mFakeServer;
    private FakeObexTransport mFakeObexTransport;

    public FakeObexServer() throws IOException {
        mFakeServer = new Server();
        mFakeObexTransport = new FakeObexTransport();
        mServerObexTransport = mFakeObexTransport.mServerTransport;
        mClientObexTransport = mFakeObexTransport.mClientTransport;
        new ServerSession(mServerObexTransport, mFakeServer, null);
    }

    public abstract int onGetValidator(Operation op);

    public abstract int onPutValidator(Operation op);

    public abstract int onSetPathValidator(HeaderSet request, HeaderSet reply,
            boolean backup, boolean create);

    class Server extends ServerRequestHandler {

        @Override
        public int onConnect(final HeaderSet request, HeaderSet reply) {
            return ResponseCodes.OBEX_HTTP_OK;
        }

        @Override
        public void onDisconnect(final HeaderSet request, HeaderSet reply) {
        }

        @Override
        public int onGet(final Operation op) {
            return onGetValidator(op);
        }

        @Override
        public int onPut(final Operation op) {
            return onPutValidator(op);
        }

        @Override
        public int onAbort(final HeaderSet request, HeaderSet reply) {
            return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
        }

        @Override
        public int onSetPath(final HeaderSet request, HeaderSet reply, final boolean backup,
                final boolean create) {
            return onSetPathValidator(request, reply, backup, create);
        }

        @Override
        public void onClose() {
        }
    }
}
