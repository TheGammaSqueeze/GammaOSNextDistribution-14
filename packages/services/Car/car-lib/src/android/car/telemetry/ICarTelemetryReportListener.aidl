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

package android.car.telemetry;

import android.os.PersistableBundle;

/**
 * Binder interface implemented by {@code CarTelemetryManager}. Enables sending telemetry reports
 * from {@code CarTelemetryService} to {@code CarTelemetryManager}.
 *
 * @hide
 */
oneway interface ICarTelemetryReportListener {

    /**
     * Called by {@code CarTelemetryService} to provide finished reports. This call is destructive.
     * The parameter will no longer be stored in {@code CarTelemetryService}.
     *
     * @param metricsConfigName the metrics config name that the report is associated with.
     * @param reportFileDescriptor the metrics reports can be read from this fd.
     * @param telemetryError the serialized bytes of the telemetry error object.
     * @param status of the metrics report.
     */
    void onResult(
            in String metricsConfigName,
            in ParcelFileDescriptor reportFileDescriptor,
            in byte[] telemetryError,
            in int status);
}