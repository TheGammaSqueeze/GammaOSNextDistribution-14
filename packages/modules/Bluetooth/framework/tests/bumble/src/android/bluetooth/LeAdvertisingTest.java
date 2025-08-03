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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.util.Log;

import androidx.core.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import io.grpc.Deadline;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import pandora.HostProto.ScanRequest;
import pandora.HostProto.ScanningResponse;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Test cases for {@link AdvertiseManager}. */
@RunWith(AndroidJUnit4.class)
public class LeAdvertisingTest {
    private static final String TAG = "LeAdvertisingTest";

    private static final int TIMEOUT_ADVERTISING_MS = 1000;

    @Rule public final AdoptShellPermissionsRule mPermissionRule = new AdoptShellPermissionsRule();

    @Rule public final PandoraDevice mBumble = new PandoraDevice();

    @Test
    public void advertisingSet() throws Exception {
        Pair<String, Integer> addressPair = startAdvertising().join();
        ScanningResponse response = scanWithBumble(addressPair);

        Log.i(TAG, "scan response: " + response);
        assertThat(response).isNotNull();
    }

    private ScanningResponse scanWithBumble(Pair<String, Integer> addressPair) {
        Log.d(TAG, "scanWithBumble");
        String address = addressPair.first;
        int addressType = addressPair.second;

        StreamObserverSpliterator<ScanningResponse> responseObserver =
                new StreamObserverSpliterator<>();
        Deadline deadline = Deadline.after(TIMEOUT_ADVERTISING_MS, TimeUnit.MILLISECONDS);
        mBumble.host()
                .withDeadline(deadline)
                .scan(ScanRequest.newBuilder().build(), responseObserver);
        Iterator<ScanningResponse> responseObserverIterator = responseObserver.iterator();
        while (true) {
            ScanningResponse scanningResponse = responseObserverIterator.next();
            String addr =
                    Utils.addressStringFromByteString(
                            addressType == AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC
                                    ? scanningResponse.getPublic()
                                    : scanningResponse.getRandom());

            if (addr.equals(address)) {
                return scanningResponse;
            }
        }
    }

    private CompletableFuture<Pair<String, Integer>> startAdvertising() {
        CompletableFuture<Pair<String, Integer>> future =
                new CompletableFuture<Pair<String, Integer>>();

        android.content.Context context = ApplicationProvider.getApplicationContext();
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Start advertising
        BluetoothLeAdvertiser leAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM)
                        .build();
        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        AdvertisingSetCallback advertisingSetCallback =
                new AdvertisingSetCallback() {
                    @Override
                    public void onAdvertisingSetStarted(
                            AdvertisingSet advertisingSet, int txPower, int status) {
                        Log.i(
                                TAG,
                                "onAdvertisingSetStarted "
                                        + " txPower:"
                                        + txPower
                                        + " status:"
                                        + status);
                        advertisingSet.enableAdvertising(true, TIMEOUT_ADVERTISING_MS, 0);
                    }

                    @Override
                    public void onOwnAddressRead(
                            AdvertisingSet advertisingSet, int addressType, String address) {
                        Log.i(
                                TAG,
                                "onOwnAddressRead "
                                        + " addressType:"
                                        + addressType
                                        + " address:"
                                        + address);
                        future.complete(new Pair<String, Integer>(address, addressType));
                    }

                    @Override
                    public void onAdvertisingEnabled(
                            AdvertisingSet advertisingSet, boolean enabled, int status) {
                        Log.i(
                                TAG,
                                "onAdvertisingEnabled "
                                        + " enabled:"
                                        + enabled
                                        + " status:"
                                        + status);
                        advertisingSet.getOwnAddress();
                    }
                };
        leAdvertiser.startAdvertisingSet(
                parameters, advertiseData, scanResponse, null, null, 0, 0, advertisingSetCallback);

        return future;
    }
}
