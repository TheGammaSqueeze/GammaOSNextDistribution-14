/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.threadnetwork.demoapp;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.android.threadnetwork.demoapp.concurrent.BackgroundExecutorProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.io.CharStreams;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ConnectivityToolsFragment extends Fragment {
    private static final String TAG = "ConnectivityTools";

    // This is a mirror of NetworkCapabilities#NET_CAPABILITY_LOCAL_NETWORK which is @hide for now
    private static final int NET_CAPABILITY_LOCAL_NETWORK = 36;

    private static final Duration PING_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration UDP_TIMEOUT = Duration.ofSeconds(10L);
    private final ListeningScheduledExecutorService mBackgroundExecutor =
            BackgroundExecutorProvider.getBackgroundExecutor();
    private final ArrayList<String> mServerIpCandidates = new ArrayList<>();
    private final ArrayList<String> mServerPortCandidates = new ArrayList<>();
    private Executor mMainExecutor;

    private ListenableFuture<String> mPingFuture;
    private ListenableFuture<String> mUdpFuture;
    private ArrayAdapter<String> mPingServerIpAdapter;
    private ArrayAdapter<String> mUdpServerIpAdapter;
    private ArrayAdapter<String> mUdpServerPortAdapter;

    private Network mThreadNetwork;
    private boolean mBindThreadNetwork = false;

    private void subscribeToThreadNetwork() {
        ConnectivityManager cm = getActivity().getSystemService(ConnectivityManager.class);
        cm.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_THREAD)
                        .addCapability(NET_CAPABILITY_LOCAL_NETWORK)
                        .build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        mThreadNetwork = network;
                    }

                    @Override
                    public void onLost(Network network) {
                        mThreadNetwork = network;
                    }
                },
                new Handler(Looper.myLooper()));
    }

    private static String getPingCommand(String serverIp) {
        try {
            InetAddress serverAddress = InetAddresses.forString(serverIp);
            return (serverAddress instanceof Inet6Address)
                    ? "/system/bin/ping6"
                    : "/system/bin/ping";
        } catch (IllegalArgumentException e) {
            // The ping command can handle the illegal argument and output error message
            return "/system/bin/ping6";
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.connectivity_tools_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainExecutor = ContextCompat.getMainExecutor(getActivity());

        subscribeToThreadNetwork();

        AutoCompleteTextView pingServerIpText = view.findViewById(R.id.ping_server_ip_address_text);
        mPingServerIpAdapter =
                new ArrayAdapter<String>(
                        getActivity(), R.layout.list_server_ip_address_view, mServerIpCandidates);
        pingServerIpText.setAdapter(mPingServerIpAdapter);
        TextView pingOutputText = view.findViewById(R.id.ping_output_text);
        Button pingButton = view.findViewById(R.id.ping_button);

        pingButton.setOnClickListener(
                v -> {
                    if (mPingFuture != null) {
                        mPingFuture.cancel(/* mayInterruptIfRunning= */ true);
                        mPingFuture = null;
                    }

                    String serverIp = pingServerIpText.getText().toString().strip();
                    updateServerIpCandidates(serverIp);
                    pingOutputText.setText("Sending ping message to " + serverIp + "\n");

                    mPingFuture = sendPing(serverIp);
                    Futures.addCallback(
                            mPingFuture,
                            new FutureCallback<String>() {
                                @Override
                                public void onSuccess(String result) {
                                    pingOutputText.append(result + "\n");
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    if (t instanceof CancellationException) {
                                        // Ignore the cancellation error
                                        return;
                                    }
                                    pingOutputText.append("Failed: " + t.getMessage() + "\n");
                                }
                            },
                            mMainExecutor);
                });

        AutoCompleteTextView udpServerIpText = view.findViewById(R.id.udp_server_ip_address_text);
        mUdpServerIpAdapter =
                new ArrayAdapter<String>(
                        getActivity(), R.layout.list_server_ip_address_view, mServerIpCandidates);
        udpServerIpText.setAdapter(mUdpServerIpAdapter);
        AutoCompleteTextView udpServerPortText = view.findViewById(R.id.udp_server_port_text);
        mUdpServerPortAdapter =
                new ArrayAdapter<String>(
                        getActivity(), R.layout.list_server_port_view, mServerPortCandidates);
        udpServerPortText.setAdapter(mUdpServerPortAdapter);
        TextInputEditText udpMsgText = view.findViewById(R.id.udp_message_text);
        TextView udpOutputText = view.findViewById(R.id.udp_output_text);

        SwitchMaterial switchBindThreadNetwork = view.findViewById(R.id.switch_bind_thread_network);
        switchBindThreadNetwork.setChecked(mBindThreadNetwork);
        switchBindThreadNetwork.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (isChecked) {
                        Log.i(TAG, "Binding to the Thread network");

                        if (mThreadNetwork == null) {
                            Log.e(TAG, "Thread network is not available");
                            Toast.makeText(
                                    getActivity().getApplicationContext(),
                                    "Thread network is not available",
                                    Toast.LENGTH_LONG);
                            switchBindThreadNetwork.setChecked(false);
                        } else {
                            mBindThreadNetwork = true;
                        }
                    } else {
                        mBindThreadNetwork = false;
                    }
                });

        Button sendUdpButton = view.findViewById(R.id.send_udp_button);
        sendUdpButton.setOnClickListener(
                v -> {
                    if (mUdpFuture != null) {
                        mUdpFuture.cancel(/* mayInterruptIfRunning= */ true);
                        mUdpFuture = null;
                    }

                    String serverIp = udpServerIpText.getText().toString().strip();
                    String serverPort = udpServerPortText.getText().toString().strip();
                    String udpMsg = udpMsgText.getText().toString().strip();
                    updateServerIpCandidates(serverIp);
                    updateServerPortCandidates(serverPort);
                    udpOutputText.setText(
                            String.format(
                                    "Sending UDP message \"%s\" to [%s]:%s",
                                    udpMsg, serverIp, serverPort));

                    mUdpFuture = sendUdpMessage(serverIp, serverPort, udpMsg);
                    Futures.addCallback(
                            mUdpFuture,
                            new FutureCallback<String>() {
                                @Override
                                public void onSuccess(String result) {
                                    udpOutputText.append("\n" + result);
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    if (t instanceof CancellationException) {
                                        // Ignore the cancellation error
                                        return;
                                    }
                                    udpOutputText.append("\nFailed: " + t.getMessage());
                                }
                            },
                            mMainExecutor);
                });
    }

    private void updateServerIpCandidates(String newServerIp) {
        if (!mServerIpCandidates.contains(newServerIp)) {
            mServerIpCandidates.add(0, newServerIp);
            mPingServerIpAdapter.notifyDataSetChanged();
            mUdpServerIpAdapter.notifyDataSetChanged();
        }
    }

    private void updateServerPortCandidates(String newServerPort) {
        if (!mServerPortCandidates.contains(newServerPort)) {
            mServerPortCandidates.add(0, newServerPort);
            mUdpServerPortAdapter.notifyDataSetChanged();
        }
    }

    private ListenableFuture<String> sendPing(String serverIp) {
        return FluentFuture.from(Futures.submit(() -> doSendPing(serverIp), mBackgroundExecutor))
                .withTimeout(PING_TIMEOUT.getSeconds(), TimeUnit.SECONDS, mBackgroundExecutor);
    }

    private String doSendPing(String serverIp) throws IOException {
        String pingCommand = getPingCommand(serverIp);
        Process process =
                new ProcessBuilder()
                        .command(pingCommand, "-c 1", serverIp)
                        .redirectErrorStream(true)
                        .start();

        return CharStreams.toString(new InputStreamReader(process.getInputStream()));
    }

    private ListenableFuture<String> sendUdpMessage(
            String serverIp, String serverPort, String msg) {
        return FluentFuture.from(
                        Futures.submit(
                                () -> doSendUdpMessage(serverIp, serverPort, msg),
                                mBackgroundExecutor))
                .withTimeout(UDP_TIMEOUT.getSeconds(), TimeUnit.SECONDS, mBackgroundExecutor);
    }

    private String doSendUdpMessage(String serverIp, String serverPort, String msg)
            throws IOException {
        SocketAddress serverAddr = new InetSocketAddress(serverIp, Integer.parseInt(serverPort));

        try (DatagramSocket socket = new DatagramSocket()) {
            if (mBindThreadNetwork && mThreadNetwork != null) {
                mThreadNetwork.bindSocket(socket);
                Log.i(TAG, "Successfully bind the socket to the Thread network");
            }

            socket.connect(serverAddr);
            Log.d(TAG, "connected " + serverAddr);

            byte[] msgBytes = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length);

            Log.d(TAG, String.format("Sending message to server %s: %s", serverAddr, msg));
            socket.send(packet);
            Log.d(TAG, "Send done");

            Log.d(TAG, "Waiting for server reply");
            socket.receive(packet);
            return new String(packet.getData(), packet.getOffset(), packet.getLength(), UTF_8);
        }
    }
}
