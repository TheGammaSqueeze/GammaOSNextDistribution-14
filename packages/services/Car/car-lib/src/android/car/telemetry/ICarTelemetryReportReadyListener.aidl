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

/**
 * Enables clients to receive telemetry report ready notifications.
 *
 * @hide
 */
oneway interface ICarTelemetryReportReadyListener {

    /**
     * Called by {@code CarTelemetryService} to provide notifications on finished reports or script
     * runtime error.
     */
    void onReady(in String metricsConfigName);
}