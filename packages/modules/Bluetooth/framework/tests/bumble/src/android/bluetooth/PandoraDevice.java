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

package android.bluetooth;

import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.google.protobuf.Empty;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

import org.junit.rules.ExternalResource;

import java.util.concurrent.TimeUnit;

import pandora.DckGrpc;
import pandora.GATTGrpc;
import pandora.HostGrpc;
import pandora.HostProto;
import pandora.SecurityGrpc;

public final class PandoraDevice extends ExternalResource {
    private static final String TAG = PandoraDevice.class.getSimpleName();
    private final String mNetworkAddress;
    private String mPublicBluetoothAddress;
    private final int mPort;
    private ManagedChannel mChannel;

    public PandoraDevice(String networkAddress, int port) {
        mNetworkAddress = networkAddress;
        mPort = port;
    }

    public PandoraDevice() {
        this("localhost", 7999);
    }

    @Override
    protected void before() {
        Log.i(TAG, "factoryReset");
        // FactoryReset is killing the server and restarting all channels created before the server
        // restarted that cannot be reused
        ManagedChannel channel =
                OkHttpChannelBuilder.forAddress(mNetworkAddress, mPort).usePlaintext().build();
        HostGrpc.HostBlockingStub stub = HostGrpc.newBlockingStub(channel);
        try {
            stub.factoryReset(Empty.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                // Server is shutting down, the call might be canceled with an UNAVAILABLE status
                // because the stream is closed.
            } else {
                throw e;
            }
        }
        try {
            // terminate the channel
            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        mChannel = OkHttpChannelBuilder.forAddress(mNetworkAddress, mPort).usePlaintext().build();
        stub = HostGrpc.newBlockingStub(mChannel);
        HostProto.ReadLocalAddressResponse readLocalAddressResponse =
                stub.withWaitForReady().readLocalAddress(Empty.getDefaultInstance());
        mPublicBluetoothAddress =
                Utils.addressStringFromByteString(readLocalAddressResponse.getAddress());
    }

    @Override
    protected void after() {
        Log.i(TAG, "shutdown");
        try {
            // terminate the channel
            mChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            mChannel = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return bumble as a remote device
     */
    public BluetoothDevice getRemoteDevice() {
        return ApplicationProvider.getApplicationContext()
                .getSystemService(BluetoothManager.class)
                .getAdapter()
                .getRemoteDevice(mPublicBluetoothAddress);
    }

    /** Get Pandora Host service */
    public HostGrpc.HostStub host() {
        return HostGrpc.newStub(mChannel);
    }

    /** Get Pandora Host service */
    public HostGrpc.HostBlockingStub hostBlocking() {
        return HostGrpc.newBlockingStub(mChannel);
    }

    /** Get Pandora Dck service */
    public DckGrpc.DckStub dck() {
        return DckGrpc.newStub(mChannel);
    }

    /** Get Pandora Dck blocking service */
    public DckGrpc.DckBlockingStub dckBlocking() {
        return DckGrpc.newBlockingStub(mChannel);
    }

    /** Get Pandora Security service */
    public SecurityGrpc.SecurityStub security() {
        return SecurityGrpc.newStub(mChannel);
    }

    /** Get Pandora GATT service */
    public GATTGrpc.GATTStub gatt() {
        return GATTGrpc.newStub(mChannel);
    }

    /** Get Pandora GATT blocking service */
    public GATTGrpc.GATTBlockingStub gattBlocking() {
        return GATTGrpc.newBlockingStub(mChannel);
    }
}
