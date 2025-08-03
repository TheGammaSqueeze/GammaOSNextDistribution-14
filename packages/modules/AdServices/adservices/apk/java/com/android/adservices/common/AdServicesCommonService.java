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

package com.android.adservices.common;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.AdServicesCommonServiceImpl;
import com.android.adservices.service.common.AdServicesSyncUtil;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.ui.UxEngine;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.util.Clock;
import com.android.adservices.ui.notifications.ConsentNotificationTrigger;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Common service for work that applies to all PPAPIs. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesCommonService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private AdServicesCommonServiceImpl mAdServicesCommonService;

    @Override
    public void onCreate() {
        super.onCreate();

        Trace.beginSection("AdServicesCommonService#Initialization");
        if (mAdServicesCommonService == null) {
            mAdServicesCommonService =
                    new AdServicesCommonServiceImpl(
                            this,
                            FlagsFactory.getFlags(),
                            UxEngine.getInstance(this),
                            UxStatesManager.getInstance(),
                            AdIdWorker.getInstance(),
                            AdServicesLoggerImpl.getInstance(),
                            Clock.getInstance());
        }
        LogUtil.d("created adservices common service");
        try {
            AdServicesSyncUtil.getInstance()
                    .register(
                            new BiConsumer<Context, Boolean>() {
                                @Override
                                public void accept(
                                        Context context, Boolean shouldDisplayEuNotification) {
                                    LogUtil.d(
                                            "running trigger command with "
                                                    + shouldDisplayEuNotification);
                                    ConsentNotificationTrigger.showConsentNotification(
                                            context, shouldDisplayEuNotification);
                                }
                            });
        } catch (Exception e) {
            LogUtil.e(
                    "getting exception when register consumer in AdServicesSyncUtil of "
                            + e.getMessage());
        }
        Trace.endSection();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return Objects.requireNonNull(mAdServicesCommonService);
    }
}
