/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.ext.services;

import android.app.Application;
import androidx.work.Configuration;

/**
 * Application class to provide default configurations for the initialization of other modules.
 */
public final class ExtServicesApplication extends Application implements Configuration.Provider {

    public ExtServicesApplication() {
        super();
    }

    /**
     * Provides initialization configuration for WorkManager, used by TextClassifierService.
     */
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().build();
    }
}
