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

package android.adservices.common;

import android.net.Uri;
import android.os.Process;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.truth.Truth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommonFixture {
    private static final String LOG_TAG = "adservices";

    public static final String TEST_PACKAGE_NAME = processName();
    public static final String TEST_PACKAGE_NAME_1 = "android.adservices.tests1";
    public static final String TEST_PACKAGE_NAME_2 = "android.adservices.tests2";
    public static final Set<String> PACKAGE_SET =
            new HashSet<>(Arrays.asList(TEST_PACKAGE_NAME_1, TEST_PACKAGE_NAME_2));

    public static final Flags FLAGS_FOR_TEST = FlagsFactory.getFlagsForTest();

    public static final Instant FIXED_NOW = Instant.now();
    public static final Instant FIXED_NOW_TRUNCATED_TO_MILLI =
            FIXED_NOW.truncatedTo(ChronoUnit.MILLIS);
    public static final Instant FIXED_EARLIER_ONE_DAY = FIXED_NOW.minus(1, ChronoUnit.DAYS);
    public static final Instant FIXED_NEXT_ONE_DAY = FIXED_NOW.plus(1, ChronoUnit.DAYS);
    public static final Clock FIXED_CLOCK_TRUNCATED_TO_MILLI =
            Clock.fixed(FIXED_NOW.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    public static final AdTechIdentifier NOT_ENROLLED_BUYER =
            AdTechIdentifier.fromString("notenrolled.com");
    public static final AdTechIdentifier VALID_BUYER_1 = AdTechIdentifier.fromString("test.com");
    public static final AdTechIdentifier VALID_BUYER_2 = AdTechIdentifier.fromString("test2.com");
    public static final AdTechIdentifier INVALID_EMPTY_BUYER = AdTechIdentifier.fromString("");
    public static final Set<AdTechIdentifier> BUYER_SET =
            new HashSet<>(Arrays.asList(VALID_BUYER_1, VALID_BUYER_2));

    public static Uri getUri(String authority, String path) {
        return Uri.parse(ValidatorUtil.HTTPS_SCHEME + "://" + authority + path);
    }

    public static Uri getUriWithGivenSubdomain(String subdomain, String authority, String path) {
        return Uri.parse(ValidatorUtil.HTTPS_SCHEME + "://" + subdomain + "." + authority + path);
    }

    public static Uri getUriWithValidSubdomain(String authority, String path) {
        return getUriWithGivenSubdomain("valid.subdomain", authority, path);
    }

    public static Uri getUri(AdTechIdentifier authority, String path) {
        return getUri(authority.toString(), path);
    }

    @SafeVarargs
    public static <T> void assertHaveSameHashCode(T... objs) {
        Set<T> helperSet = new HashSet<>(Arrays.asList(objs));
        Truth.assertThat(helperSet).hasSize(1);
    }

    @SafeVarargs
    public static <T> void assertDifferentHashCode(T... objs) {
        Set<T> helperSet = new HashSet<>(Arrays.asList(objs));
        Truth.assertThat(helperSet).hasSize(objs.length);
    }

    public static void doSleep(long timeout) {
        Log.i(LOG_TAG, "Starting to sleep for " + timeout + " ms");
        long currentTime = System.currentTimeMillis();
        long wakeupTime = currentTime + timeout;
        while (wakeupTime - currentTime > 0) {
            Log.i(LOG_TAG, "Time left to sleep: " + (wakeupTime - currentTime) + " ms");
            try {
                Thread.sleep(wakeupTime - currentTime);
            } catch (InterruptedException ignored) {

            }
            currentTime = System.currentTimeMillis();
        }
        Log.i(LOG_TAG, "Done sleeping");
    }

    private static String processName() {
        if (SdkLevel.isAtLeastT()) {
            return Process.myProcessName();
        } else {
            try {
                return ApplicationProvider.getApplicationContext().getPackageName();
            } catch (IllegalStateException e) {
                // TODO(b/275062019): Remove this try/catch once instrumentation context can be
                // passed in AppConsentSettingsUiAutomatorTest
                LogUtil.e(e, "Failed to get package name from Instrumentation context");
                return "android.adservices.tests";
            }
        }
    }
}
