/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.cobalt;

import static android.adservices.common.AdServicesStatusUtils.STATUS_ENCRYPTION_FAILURE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA;

/** Utility class containing event codes and constants used by Cobalt Logging. */
public final class CobaltLoggerConstantUtils {
    private CobaltLoggerConstantUtils() {}

    // The per package api errors metric has an id of 2.
    //
    // See //packages/modules/AdServices/adservices/service-core/resources/cobalt_registry.textpb
    // for the full metric.
    public static final int METRIC_ID = 2;

    public static final int UNKNOWN_EVENT_CODE = 0;

    // This logging currently supports error codes in range of 1 to 19. Update the
    // RANGE_UPPER_ERROR_CODE value when adding new error codes in
    // packages/modules/AdServices/adservices/framework/java/android/adservices/common/
    // AdServicesStatusUtils.java
    public static final int RANGE_LOWER_ERROR_CODE = STATUS_INTERNAL_ERROR;

    public static final int RANGE_UPPER_ERROR_CODE = STATUS_ENCRYPTION_FAILURE;

    // This logging currently supports api codes in range of 1 to 28. Update the
    // RANGE_UPPER_API_CODE when adding new error codes in
    // com.android.adservices.service.stats.AdServicesStatsLog.java
    public static final int RANGE_LOWER_API_CODE = AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;

    public static final int RANGE_UPPER_API_CODE =
            AD_SERVICES_API_CALLED__API_NAME__PUT_AD_SERVICES_EXT_DATA;
}
