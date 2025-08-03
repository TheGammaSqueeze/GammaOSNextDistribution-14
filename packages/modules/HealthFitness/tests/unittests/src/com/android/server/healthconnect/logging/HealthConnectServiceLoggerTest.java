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

package healthconnect.logging;

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__INSERT_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__ERROR;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__SUCCESS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__BACKGROUND;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__FOREGROUND;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__UNSPECIFIED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_USED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_ABOVE_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_3000_TO_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_3000_TO_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_3000_TO_4000;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_15M_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_15M_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_24H_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_24H_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;

import android.health.HealthFitnessStatsLog;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.logging.HealthConnectServiceLogger;
import com.android.server.healthconnect.logging.HealthConnectServiceLogger.ApiMethods;

import org.junit.Rule;
import org.junit.Test;

public class HealthConnectServiceLoggerTest {

    private static final int CALLER_FOREGROUND_STATE_UNSPECIFIED =
            HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__UNSPECIFIED;
    private static final int CALLER_FOREGROUND_STATE_FOREGROUND =
            HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__FOREGROUND;
    private static final int CALLER_FOREGROUND_STATE_BACKGROUND =
            HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__BACKGROUND;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(HealthFitnessStatsLog.class).build();

    @Test
    public void testDoNotLog_HoldsDataManagementPermission() {

        new HealthConnectServiceLogger.Builder(true, ApiMethods.API_METHOD_UNKNOWN).build().log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyInt(),
                                anyInt(), anyInt()),
                times(0));
    }

    @Test
    public void testLogs_notHoldsDataManagementPermission() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN).build().log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_foreground15MinRead() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_READS_PER_15M_FOREGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.FOREGROUND_15_MIN_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_background15MinRead() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_READS_PER_15M_BACKGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.BACKGROUND_15_MIN_ABOVE_3000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_background24hourRead() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_READS_PER_24H_BACKGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.BACKGROUND_24_HRS_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_foreground24hourRead() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_READS_PER_24H_FOREGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.FOREGROUND_24_HRS_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_foreground15MinWrite() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.FOREGROUND_15_MIN_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_background15MinWrite() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.BACKGROUND_15_MIN_ABOVE_3000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_background24hourWrite() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.BACKGROUND_24_HRS_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testRateLimiter_foreground24hourWrite() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND, 3000)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.FOREGROUND_24_HRS_BW_3000_TO_4000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testApiSuccessLogs() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setHealthDataServiceApiStatusSuccess()
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__SUCCESS),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testApiErrorLogs() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setHealthDataServiceApiStatusError(2)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__ERROR),
                                eq(2),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testSetNumberOfRecords() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setNumberOfRecords(10)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(10),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testAllFields_successStatus() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.INSERT_DATA)
                .setHealthDataServiceApiStatusSuccess()
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND, 3000)
                .setNumberOfRecords(10)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__INSERT_DATA),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__SUCCESS),
                                eq(0),
                                anyLong(),
                                eq(10),
                                eq(RateLimitingRanges.BACKGROUND_15_MIN_ABOVE_3000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testAllFields_errorStatus() {

        new HealthConnectServiceLogger.Builder(false, ApiMethods.INSERT_DATA)
                .setHealthDataServiceApiStatusError(1)
                .setRateLimit(QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND, 3000)
                .setNumberOfRecords(10)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__INSERT_DATA),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__ERROR),
                                eq(1),
                                anyLong(),
                                eq(10),
                                eq(RateLimitingRanges.BACKGROUND_15_MIN_ABOVE_3000),
                                eq(CALLER_FOREGROUND_STATE_UNSPECIFIED)),
                times(1));
    }

    @Test
    public void testCallerForegroundState() {
        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setCallerForegroundState(true)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_FOREGROUND)));
    }

    @Test
    public void testCallerBackgroundState() {
        new HealthConnectServiceLogger.Builder(false, ApiMethods.API_METHOD_UNKNOWN)
                .setCallerForegroundState(false)
                .build()
                .log();

        // then
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_API_CALLED),
                                eq(HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN),
                                eq(HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN),
                                eq(0),
                                anyLong(),
                                eq(0),
                                eq(RateLimitingRanges.NOT_USED),
                                eq(CALLER_FOREGROUND_STATE_BACKGROUND)));
    }

    private static final class RateLimitingRanges {

        private static final int NOT_USED = HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_USED;
        private static final int FOREGROUND_15_MIN_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_3000_TO_4000;
        private static final int BACKGROUND_15_MIN_ABOVE_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_ABOVE_3000;
        private static final int FOREGROUND_24_HRS_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_3000_TO_4000;
        private static final int BACKGROUND_24_HRS_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_3000_TO_4000;
    }
}
