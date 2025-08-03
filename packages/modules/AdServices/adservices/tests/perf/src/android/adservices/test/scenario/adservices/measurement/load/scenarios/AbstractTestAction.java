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

package android.adservices.test.scenario.adservices.measurement.load.scenarios;

import android.adservices.measurement.MeasurementManager;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.content.Context;
import android.os.Build;
import android.platform.test.option.IntegerOption;
import android.platform.test.option.StringOption;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AbstractTestAction {
    protected static final String TAG = "MeasurementLoadAction";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    protected static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    protected static final int DEFAULT_PORT = 38383;
    protected static final String SERVER_BASE_URI =
            replaceTestDomain("https://rb-measurement.test");
    protected static final String WEB_ORIGIN = replaceTestDomain("https://rb-example-origin.test");
    protected static final String WEB_DESTINATION =
            replaceTestDomain("https://rb-example-destination.test");
    protected static final String SOURCE_PATH = "/source";
    protected static final MeasurementManager MEASUREMENT_MANAGER =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? CONTEXT.getSystemService(MeasurementManager.class)
                    : MeasurementManager.get(CONTEXT);

    // Common Options
    private static final String OPTION_SDKNAME = "sdk_name";
    private static final String REPEAT_COUNT = "repeat_count";
    private static final String MS_DELAY_PRE = "ms_delay_pre";
    private static final String MS_DELAY_POST = "ms_delay_post";

    public static MockWebServerRule mMockWebServerRule;

    @Rule
    public final IntegerOption repeatCountOption =
            new IntegerOption(REPEAT_COUNT).setRequired(false).setDefault(1);

    @Rule
    public final IntegerOption msDelayPre =
            new IntegerOption(MS_DELAY_PRE).setRequired(false).setDefault(0);

    @Rule
    public final IntegerOption msDelayPost =
            new IntegerOption(MS_DELAY_POST).setRequired(false).setDefault(0);

    @Rule
    public final StringOption sdkOption =
            new StringOption(OPTION_SDKNAME).setRequired(false).setDefault("sdkDef");

    protected String generateLog(String message) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[3];
        String methodName = e.getMethodName();
        return "(%s %s::%s: %s)".formatted(TAG, getClass().getSimpleName(), methodName, message);
    }

    private static String replaceTestDomain(String value) {
        return value.replaceAll("test", "com");
    }

    protected static void sleep(int msDelay) {
        try {
            TimeUnit.MILLISECONDS.sleep(msDelay);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void delayPre() {
        Log.i(TAG, generateLog("MSDelayPre: %sms".formatted(msDelayPre.get())));
        sleep(msDelayPre.get());
    }

    protected void delayPost() {
        Log.i(TAG, generateLog("MSDelayPost: %sms".formatted(msDelayPost.get())));
        sleep(msDelayPost.get());
    }
}
