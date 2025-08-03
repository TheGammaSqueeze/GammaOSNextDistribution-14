/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.builtin.bluetooth.le;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;

/**
 * Provides access to {@code onOwnAddressRead} in
 * {@code android.bluetooth.le.AdvertisingSetCallback}.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AdvertisingSetCallbackHelper {

    /**
     * A proxy to {@code android.bluetooth.le.AdvertisingSetCallback}, since one of its methods
     * is a hidden API. {@code AdvertisingSetCallback} is the Bluetooth LE advertising set
     * callbacks, used to deliver advertising operation status.
     */
    public abstract static class Callback {

        /**
         * Callback triggered in response to {@link BluetoothLeAdvertiser#startAdvertisingSet}
         * indicating result of the operation. If status is ADVERTISE_SUCCESS, then advertisingSet
         * contains the started set and it is advertising. If error occurred, advertisingSet is
         * null, and status will be set to proper error code.
         *
         * @param advertisingSet The advertising set that was started or null if error.
         * @param txPower tx power that will be used for this set.
         * @param status Status of the operation.
         */
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                int status) {
        }

        /**
         * Callback triggered in response to {@link BluetoothLeAdvertiser#stopAdvertisingSet}
         * indicating advertising set is stopped.
         *
         * @param advertisingSet The advertising set.
         */
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
        }

        /**
         * Callback triggered in response to {@link BluetoothLeAdvertiser#startAdvertisingSet}
         * indicating result of the operation. If status is ADVERTISE_SUCCESS, then advertising
         * set is advertising.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#setAdvertisingData} indicating
         * result of the operation. If status is ADVERTISE_SUCCESS, then data was changed.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#setAdvertisingData} indicating
         * result of the operation.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#setAdvertisingParameters}
         * indicating result of the operation.
         *
         * @param advertisingSet The advertising set.
         * @param txPower tx power that will be used for this set.
         * @param status Status of the operation.
         */
        public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                int txPower, int status) {
        }

        /**
         * Callback triggered in response to {@link
         * AdvertisingSet#setPeriodicAdvertisingParameters} indicating result of the operation.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onPeriodicAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#setPeriodicAdvertisingData}
         * indicating result of the operation.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onPeriodicAdvertisingDataSet(AdvertisingSet advertisingSet,
                int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#setPeriodicAdvertisingEnabled}
         * indicating result of the operation.
         *
         * @param advertisingSet The advertising set.
         * @param status Status of the operation.
         */
        public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
        }

        /**
         * Callback triggered in response to {@link AdvertisingSet#getOwnAddress()}
         * indicating result of the operation. (In the real callback, this was hidden API).
         *
         * @param advertisingSet The advertising set.
         * @param addressType type of address.
         * @param address advertising set bluetooth address.
         */
        public void onOwnAddressRead(AdvertisingSet advertisingSet, int addressType,
                String address) {
        }
    }

    private AdvertisingSetCallbackHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Creates a real {@link AdvertisingSetCallback} by wrapping a {@link Callback}.
     * Wrapping is needed because some of the methods in {@link AdvertisingSetCallback} are
     * hidden APIs, so cannot be overridden within a Mainline module.
     *
     * @param proxy The proxy of {@link AdvertisingSetCallback} that is to be wrapped into a
     *              real one.
     * @return A real {@link AdvertisingSetCallback}.
     */
    public static AdvertisingSetCallback createRealCallbackFromProxy(
            @NonNull Callback proxy) {

        AdvertisingSetCallback realCallback = new AdvertisingSetCallback() {
            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                    int status) {
                proxy.onAdvertisingSetStarted(advertisingSet, txPower, status);
            }

            @Override
            public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                proxy.onAdvertisingSetStopped(advertisingSet);
            }

            @Override
            public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                    int status) {
                proxy.onAdvertisingEnabled(advertisingSet, enable, status);
            }

            @Override
            public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
                proxy.onAdvertisingDataSet(advertisingSet, status);
            }

            @Override
            public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
                proxy.onScanResponseDataSet(advertisingSet, status);
            }

            @Override
            public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                    int txPower, int status) {
                proxy.onAdvertisingParametersUpdated(advertisingSet, txPower, status);
            }

            @Override
            public void onPeriodicAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                    int status) {
                proxy.onPeriodicAdvertisingParametersUpdated(advertisingSet, status);
            }

            @Override
            public void onPeriodicAdvertisingDataSet(AdvertisingSet advertisingSet,
                    int status) {
                proxy.onPeriodicAdvertisingDataSet(advertisingSet, status);
            }

            @Override
            public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                    int status) {
                proxy.onPeriodicAdvertisingEnabled(advertisingSet, enable, status);
            }

            @Override
            public void onOwnAddressRead(AdvertisingSet advertisingSet, int addressType,
                    String address) {
                proxy.onOwnAddressRead(advertisingSet, addressType, address);
            }
        };

        return realCallback;
    }
}
