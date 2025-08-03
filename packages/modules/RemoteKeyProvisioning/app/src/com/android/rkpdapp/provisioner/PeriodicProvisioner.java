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

package com.android.rkpdapp.provisioner;

import android.annotation.NonNull;
import android.content.Context;
import android.util.Log;

import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.metrics.ProvisioningAttempt;
import com.android.rkpdapp.metrics.RkpdStatsLog;
import com.android.rkpdapp.utils.Settings;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import co.nstant.in.cbor.CborException;

/**
 * A class that extends Worker in order to be scheduled to maintain the attestation key pool at
 * regular intervals. If the job determines that more keys need to be generated and signed, it would
 * drive that process.
 */
public class PeriodicProvisioner extends Worker {
    public static final String UNIQUE_WORK_NAME = "ProvisioningJob";
    private static final String TAG = "RkpdPeriodicProvisioner";
    private static final boolean IS_ASYNC = true;

    private final Context mContext;
    private final ProvisionedKeyDao mKeyDao;

    public PeriodicProvisioner(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mContext = context;
        mKeyDao = RkpdDatabase.getDatabase(context).provisionedKeyDao();
    }

    /**
     * Overrides the default doWork method to handle checking and provisioning the device.
     */
    @Override
    public Result doWork() {
        Log.i(TAG, "Waking up; checking provisioning state.");

        SystemInterface[] irpcs = ServiceManagerInterface.getAllInstances();
        if (irpcs.length == 0) {
            Log.i(TAG, "Stopping periodic provisioner: there are no IRPC HALs");
            WorkManager.getInstance(mContext).cancelWorkById(getId());
            return Result.success();
        }

        if (Settings.getUrl(mContext).isEmpty()) {
            Log.i(TAG, "Stopping periodic provisioner: system has no configured server endpoint");
            WorkManager.getInstance(mContext).cancelWorkById(getId());
            return Result.success();
        }

        try (ProvisioningAttempt metrics = ProvisioningAttempt.createScheduledAttemptMetrics(
                mContext)) {
            // Clean up the expired keys
            mKeyDao.deleteExpiringKeys(Instant.now());

            // Fetch geek from the server and figure out whether provisioning needs to be stopped.
            GeekResponse response;
            try {
                response = new ServerInterface(mContext, IS_ASYNC).fetchGeekAndUpdate(metrics);
            } catch (InterruptedException | RkpdException e) {
                Log.e(TAG, "Error fetching configuration from the RKP server", e);
                return Result.failure();
            }

            if (response.numExtraAttestationKeys == 0) {
                Log.i(TAG, "Disable provisioning and delete all keys.");
                metrics.setEnablement(ProvisioningAttempt.Enablement.DISABLED);
                metrics.setStatus(ProvisioningAttempt.Status.PROVISIONING_DISABLED);

                mKeyDao.deleteAllKeys();
                metrics.setIsKeyPoolEmpty(true);
                return Result.success();
            }

            Log.i(TAG, "Total services found implementing IRPC: " + irpcs.length);
            Provisioner provisioner = new Provisioner(mContext, mKeyDao, IS_ASYNC);
            final AtomicBoolean result = new AtomicBoolean(true);
            Arrays.stream(irpcs).parallel().forEach(irpc -> {
                Log.i(TAG, "Starting provisioning for " + irpc);
                try {
                    provisioner.provisionKeys(metrics, irpc, response);
                    recordKeyPoolStatsAtom(irpc);
                    Log.i(TAG, "Successfully provisioned " + irpc);
                } catch (CborException e) {
                    Log.e(TAG, "Error parsing CBOR for " + irpc, e);
                    result.set(false);
                } catch (InterruptedException | RkpdException e) {
                    Log.e(TAG, "Error provisioning keys for " + irpc, e);
                    result.set(false);
                }
            });
            return result.get() ? Result.success() : Result.failure();
        }
    }

    private void recordKeyPoolStatsAtom(SystemInterface irpc) {
        String halName = irpc.getServiceName();
        final int numExpiring = mKeyDao.getTotalExpiringKeysForIrpc(halName,
                Settings.getExpirationTime(mContext));
        final int numUnassigned = mKeyDao.getTotalUnassignedKeysForIrpc(halName);
        final int total = mKeyDao.getTotalKeysForIrpc(halName);
        Log.i(TAG, "Logging atom metric for pool status, total: " + total + ", numExpiring: "
                + numExpiring + ", numUnassigned: " + numUnassigned);
        RkpdStatsLog.write(RkpdStatsLog.RKPD_POOL_STATS, irpc.getServiceName(), numExpiring,
                numUnassigned, total);
    }
}
