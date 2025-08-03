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

package com.android.tv.settings.device.eco;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager.LowPowerStandbyPolicy;
import android.util.ArraySet;
import android.util.Log;

import com.android.settingslib.utils.ThreadUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.device.eco.EnergyModesHelper.EnergyMode;
import com.android.tv.twopanelsettings.slices.TvSettingsStatsLog;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JobService to log available energy mode policies.
 */
public class EnergyModesStatsLogJobService extends JobService {
    private static final String TAG = "EnergyModesStatsLogJobService";
    private static final long WRITE_STATS_FREQUENCY_MS = TimeUnit.DAYS.toMillis(6);

    /** Schedule a periodic job to log available energy mode policies. */
    public static void scheduleEnergyModesStatsLog(Context context) {
        final EnergyModesHelper energyModesHelper = new EnergyModesHelper(context);
        if (!energyModesHelper.areEnergyModesAvailable()) {
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final ComponentName component =
                new ComponentName(context, EnergyModesStatsLogJobService.class);
        final JobInfo job =
                new JobInfo.Builder(R.integer.job_energy_modes_stats_log, component)
                        .setPeriodic(WRITE_STATS_FREQUENCY_MS)
                        .setRequiresDeviceIdle(true)
                        .setPersisted(true)
                        .build();
        final JobInfo pending = jobScheduler.getPendingJob(R.integer.job_energy_modes_stats_log);

        // Don't schedule it if it already exists, to make sure it runs periodically even after
        // reboot
        if (pending == null && jobScheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Energy Modes stats log job service schedule failed.");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        ThreadUtils.postOnBackgroundThread(() -> {
            writePoliciesStatsLog(getApplicationContext());
            jobFinished(params, false /* wantsReschedule */);
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

    /** Writes available energy mode policies to stats log */
    public static void writePoliciesStatsLog(Context context) {
        final EnergyModesHelper energyModesHelper = new EnergyModesHelper(context);
        final EnergyMode current = energyModesHelper.updateEnergyMode();
        final List<EnergyMode> energyModes = energyModesHelper.getEnergyModes();

        for (EnergyMode energyMode : energyModes) {
            final LowPowerStandbyPolicy policy = energyModesHelper.getPolicy(energyMode);
            TvSettingsStatsLog.write(
                    TvSettingsStatsLog.TV_LOW_POWER_STANDBY_POLICY,
                    policy.getIdentifier(),
                    getExemptPackageUids(context, policy),
                    policy.getAllowedReasons(),
                    policy.getAllowedFeatures().toArray(new String[0]),
                    energyMode == current
            );
        }
    }

    private static int[] getExemptPackageUids(Context context, LowPowerStandbyPolicy policy) {
        final PackageManager packageManager = context.getPackageManager();
        final ArraySet<Integer> exemptUids = new ArraySet<>();
        for (String exemptPackage : policy.getExemptPackages()) {
            try {
                int uid = packageManager.getPackageUid(exemptPackage, PackageManager.MATCH_ALL);
                exemptUids.add(uid);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }

        final int[] exemptUidsArray = new int[exemptUids.size()];
        int i = 0;
        for (Integer uid : exemptUids) {
            exemptUidsArray[i++] = uid;
        }

        Arrays.sort(exemptUidsArray);
        return exemptUidsArray;
    }
}
