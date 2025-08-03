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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/** Provides creating and managing {@link MsrpSession} */
public class MsrpManager {
    private final ImsPdnNetworkFetcher imsPdnNetworkFetcher;
    private Context context;

    public MsrpManager(Context context) {
        this.context = context;
        imsPdnNetworkFetcher = new ImsPdnNetworkFetcher(context);
    }

    private MsrpSession createMsrpSession(ConnectivityManager manager,
            Network network, String host, int port, String localIp, int localPort,
            MsrpSessionListener listener) throws IOException {
        Socket socket = network.getSocketFactory().createSocket(host, port,
                InetAddress.getByName(localIp), localPort);
        MsrpSession msrpSession = new MsrpSession(manager, context,
                network, socket, listener);
        Thread thread = new Thread(msrpSession::run);
        thread.start();
        return msrpSession;
    }

    public ListenableFuture<MsrpSession> createMsrpSession(
            String host, int port, String localIp, int localPort, MsrpSessionListener listener) {
        return Futures.transformAsync(
                imsPdnNetworkFetcher.getImsPdnNetwork(),
                network -> {
                    if (network != null) {
                        return Futures.immediateFuture(
                                createMsrpSession(imsPdnNetworkFetcher.getConnectivityManager(),
                                        network, host, port, localIp, localPort, listener));
                    } else {
                        return Futures.immediateFailedFuture(
                                new IllegalStateException("Network is null"));
                    }
                },
                MoreExecutors.directExecutor());
    }
}
