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

import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Method.SEND;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Method.UNKNOWN;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.QosCallback;
import android.net.QosCallbackException;
import android.net.QosSession;
import android.net.QosSessionAttributes;
import android.net.QosSocketInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.concurrent.futures.CallbackToFutureAdapter.Completer;

import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Continuation;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides MSRP sending and receiving messages ability.
 */
public class MsrpSession {
    private static final String DEDICATED_BEARER_SUCCESS = "Dedicated bearer succeeded";
    private static final String DEDICATED_BEARER_ERROR = "Dedicated bearer error";
    private static final int TOAST_MSG = 1;
    private final Network network;
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final ConcurrentHashMap<String, MsrpTransaction> transactions =
            new ConcurrentHashMap<>();
    private final MsrpSessionListener listener;
    private final ConnectivityManager connectivityManager;
    private final String LOG_TAG = MsrpSession.class.getSimpleName();
    private final Context context;
    private Handler handler;

    /** Creates a new MSRP session on the given listener and the provided streams. */
    MsrpSession(ConnectivityManager connectivityManager, Context context, Network network,
            Socket socket, MsrpSessionListener listener) throws IOException {
        this.connectivityManager = connectivityManager;
        this.context = context;
        this.network = network;
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
        this.listener = listener;


        handler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Toast.makeText(context, msg.obj.toString(), Toast.LENGTH_SHORT).show();
            }
        };

        listenForBearer();
    }

    private final QosCallback qosCallback = new QosCallback() {
        @Override
        public void onError(@NonNull QosCallbackException exception) {
            Log.e(LOG_TAG, "onError: " + exception.toString());
            handler.sendMessage(handler.obtainMessage(TOAST_MSG, DEDICATED_BEARER_ERROR));
        }

        @Override
        public void onQosSessionAvailable(@NonNull QosSession session,
                @NonNull QosSessionAttributes sessionAttributes) {
            Log.d(LOG_TAG, "onQosSessionAvailable: " + session.toString() + ", "
                    + sessionAttributes.toString());
            handler.sendMessage(handler.obtainMessage(TOAST_MSG, DEDICATED_BEARER_SUCCESS));
        }

        @Override
        public void onQosSessionLost(@NonNull QosSession session) {
            Log.e(LOG_TAG, "onQosSessionLost: " + session.toString());
            handler.sendMessage(handler.obtainMessage(TOAST_MSG, DEDICATED_BEARER_ERROR));
        }
    };

    private void listenForBearer() {
        try {
            connectivityManager.registerQosCallback(new QosSocketInfo(network, socket),
                    MoreExecutors.directExecutor(), qosCallback);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends the given MSRP chunk.
     */
    public ListenableFuture<MsrpChunk> send(MsrpChunk request) {
        if (request.method() == UNKNOWN) {
            throw new IllegalArgumentException("Given chunk must be a request");
        }

        if (!isOpen.get()) {
            throw new IllegalStateException("Session terminated");
        }

        if (!socket.isConnected()) {
            throw new IllegalStateException("Socket is not connected");
        }

        if (request.method() == SEND) {
            return CallbackToFutureAdapter.getFuture(
                    completer -> {
                        final MsrpTransaction transaction = new MsrpTransaction(completer);
                        transactions.put(request.transactionId(), transaction);
                        try {
                            synchronized (output) {
                                MsrpSerializer.serialize(output, request);
                            }
                            output.flush();
                        } catch (IOException e) {
                            completer.setException(e);
                        }
                        return "MsrpSession.send(" + request.transactionId() + ")";
                    }
            );
        } else {
            try {
                synchronized (output) {
                    MsrpSerializer.serialize(output, request);
                }
                return Futures.immediateFuture(request);
            } catch (IOException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
    }

    /**
     * Blocking method which reads from the provided InputStream until the session
     * is terminated or the stream read throws an exception.
     */
    public void run() {
        new StreamReader(this).run();
    }

    public void terminate() throws IOException {
        if (isOpen.getAndSet(false)) {
            output.flush();
        }
        connectivityManager.unregisterQosCallback(qosCallback);
        socket.close();
    }

    /**
     * Reads and parses MSRP messages from the session input stream.
     */
    private static class StreamReader {

        private final MsrpSession session;
        private final InputStream stream;
        private final AtomicBoolean active;

        StreamReader(MsrpSession session) {
            this.session = session;
            this.stream = session.input;
            this.active = session.isOpen;
        }

        void run() {
            while (active.get()) {
                MsrpChunk chunk = null;
                try {
                    chunk = MsrpParser.parse(stream);

                    if (chunk.method() == UNKNOWN) {
                        completeTransaction(chunk);
                    } else {
                        receiveRequest(chunk);
                    }
                } catch (IOException e) {
                    active.compareAndSet(true, false);
                }
            }
        }

        private void receiveRequest(MsrpChunk chunk) throws IOException {
            sendResponse(chunk);
            session.listener.onChunkReceived(chunk);
        }

        private void completeTransaction(MsrpChunk chunk) {
            MsrpTransaction transaction = session.transactions.remove(chunk.transactionId());
            if (transaction != null) {
                transaction.complete(chunk);
            }
        }

        private void sendResponse(MsrpChunk chunk) throws IOException {
            // check if response is required
            MsrpChunkHeader failureReport = chunk.header(MsrpConstants.HEADER_FAILURE_REPORT);
            if (failureReport == null || failureReport.value().equals("yes")) {
                MsrpChunkHeader toPath = chunk.header(MsrpConstants.HEADER_TO_PATH);
                MsrpChunkHeader fromPath = chunk.header(MsrpConstants.HEADER_FROM_PATH);

                MsrpChunk response = MsrpChunk.newBuilder()
                        .transactionId(chunk.transactionId())
                        .responseCode(200)
                        .responseReason("OK")
                        .addHeader(MsrpConstants.HEADER_TO_PATH, fromPath.value())
                        .addHeader(MsrpConstants.HEADER_FROM_PATH, toPath.value())
                        .continuation(Continuation.COMPLETE)
                        .build();

                synchronized (session.output) {
                    MsrpSerializer.serialize(session.output, response);
                    session.output.flush();
                }
            }
        }
    }

    /**
     * Transaction holder.
     */
    private static class MsrpTransaction {
        private final Completer<MsrpChunk> completed;

        public MsrpTransaction(Completer<MsrpChunk> chunkCompleter) {
            this.completed = chunkCompleter;
        }

        public void complete(MsrpChunk response) {
            completed.set(response);
        }
    }
}
