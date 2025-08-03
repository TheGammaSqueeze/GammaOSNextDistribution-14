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
package com.android.adservices.appsetid;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__APPSETID;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsetid.AppSetIdServiceImpl;
import com.android.adservices.service.appsetid.AppSetIdWorker;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.util.Clock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

/** AppSetId Service */
public class AppSetIdService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private AppSetIdServiceImpl mAppSetIdService;

    @Override
    public void onCreate() {
        super.onCreate();

        if (FlagsFactory.getFlags().getAppSetIdKillSwitch()) {
            LogUtil.e("AppSetId API is disabled");
            return;
        }

        AppImportanceFilter appImportanceFilter =
                AppImportanceFilter.create(
                        this,
                        AD_SERVICES_API_CALLED__API_CLASS__APPSETID,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation());

        if (mAppSetIdService == null) {
            mAppSetIdService =
                    new AppSetIdServiceImpl(
                            this,
                            AppSetIdWorker.getInstance(),
                            AdServicesLoggerImpl.getInstance(),
                            Clock.getInstance(),
                            FlagsFactory.getFlags(),
                            Throttler.getInstance(FlagsFactory.getFlags()),
                            appImportanceFilter);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (FlagsFactory.getFlags().getAppSetIdKillSwitch()) {
            LogUtil.e("AppSetId API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }

        return Objects.requireNonNull(mAppSetIdService);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
    }
}
