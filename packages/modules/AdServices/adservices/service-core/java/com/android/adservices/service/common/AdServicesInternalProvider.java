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
package com.android.adservices.service.common;

import android.content.Context;

import com.android.adservices.shared.common.ApplicationContextProvider;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Provider used to set the application context singleton and other common stuff (like dumping data
 * not associated with a service).
 */
public final class AdServicesInternalProvider extends ApplicationContextProvider {

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        try {
            Context appContext = ApplicationContextSingleton.get();
            writer.printf("ApplicationContextSingleton: %s\n", appContext);
            if (appContext != null) {
                AppManifestConfigMetricsLogger.dump(appContext, writer);
            }
        } catch (Exception e) {
            writer.printf("Failed to get ApplicationContextSingleton: %s\n", e);
        }
    }
}
