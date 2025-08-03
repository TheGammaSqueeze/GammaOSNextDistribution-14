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

package android.net.wifi.cts.app;

import static android.net.wifi.cts.app.RetrieveTransportInfoAndReturnStatusActivity.canRetrieveSsidFromTransportInfo;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.net.ConnectivityManager;
import android.os.ResultReceiver;

/**
 * A service that retrieves transport Info and returns status.
 */
public class RetrieveTransportInfoAndReturnStatusService extends JobService {
    private static final String TAG = "RetrieveTransportInfoAndReturnStatusService";
    private static final String RESULT_RECEIVER_EXTRA =
            "android.net.wifi.cts.app.extra.RESULT_RECEIVER";

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        ResultReceiver resultReceiver =
                jobParameters.getTransientExtras().getParcelable(RESULT_RECEIVER_EXTRA);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ConnectivityManager connectivityManager  = getSystemService(ConnectivityManager.class);
        resultReceiver.send(
                canRetrieveSsidFromTransportInfo(TAG, connectivityManager) ? 1 : 0, null);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
