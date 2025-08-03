/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.wifi.NetworkMonitor;

/**
 * A task that enables mobile data and waits for it to successfully connect. If connection times out
 * {@link #error(int)} will be called.
 */
public class ConnectMobileNetworkTask extends AbstractProvisioningTask
        implements NetworkMonitor.NetworkConnectedCallback {
    private static final int RECONNECT_TIMEOUT_MS = 600000;

    private final NetworkMonitor mNetworkMonitor;

    private Handler mHandler;
    private boolean mTaskDone = false;

    private final Utils mUtils;
    private Runnable mTimeoutRunnable;

    public ConnectMobileNetworkTask(
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback) {
        super(context, provisioningParams, callback);
        mNetworkMonitor = new NetworkMonitor(context, /* waitForValidated */ true);
        mUtils = new Utils();
    }

    /**
     * Sets {@link Settings.Global#DEVICE_PROVISIONING_MOBILE_DATA_ENABLED} to 1, and if not already
     * connected to the network, starts listening for a connection. Calls {@link #success()} when
     * connected or {@link #error(int)} if it times out after 10 minutes.
     */
    @Override
    public void run(int userId) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED, 1);

        if (isLegacyConnected()) {
            success();
            return;
        }

        if ((mProvisioningParams.isNfc
                || mProvisioningParams.isQrProvisioning)
                && mUtils.isMobileNetworkConnectedToInternet(mContext)) {
            success();
            return;
        }

        mTaskDone = false;
        mHandler = new Handler();
        mNetworkMonitor.startListening(this);

        // NetworkMonitor will call onNetworkConnected.
        // Post time out event in case the NetworkMonitor doesn't call back.
        mTimeoutRunnable = () -> finishTask(false);
        mHandler.postDelayed(mTimeoutRunnable, RECONNECT_TIMEOUT_MS);
    }

    @Override
    public void onNetworkConnected() {
        ProvisionLogger.logd("onNetworkConnected");
        if (isLegacyConnected()
                || ((mProvisioningParams.isNfc || mProvisioningParams.isQrProvisioning)
                // TODO: instead of having NetworkMonitor wait for any default network (which could
                // be wifi), and checking that any mobile network (which may not be the default) is
                // connected, either:
                // - If the device should wait for mobile data to be default before continuing,
                //   have NetworkMonitor check that the default network it obtains has
                //   TRANSPORT_MOBILE. This will typically never happen when wifi is usable.
                // - If the device should continue when mobile data is connected but not default,
                //   have NetworkMonitor use requestNetwork with TRANSPORT_MOBILE and have
                //   all subsequent network usage explicitly specify the obtained mobile network
                //   with APIs such as Network.openConnection or Network.bindSocket.
                && mUtils.isMobileNetworkConnectedToInternet(mContext))) {
            ProvisionLogger.logd("Connected to mobile data");
            finishTask(true);
            // Remove time out callback.
            mHandler.removeCallbacks(mTimeoutRunnable);
        }
    }

    private boolean isLegacyConnected() {
        return !mProvisioningParams.isNfc
                && !mProvisioningParams.isQrProvisioning
                && mUtils.isConnectedToNetwork(mContext);
    }

    private synchronized void finishTask(boolean isSuccess) {
        if (mTaskDone) {
            return;
        }

        mTaskDone = true;
        mNetworkMonitor.stopListening();
        if (isSuccess) {
            success();
        } else {
            error(0);
        }
    }
}
