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

package android.app.sdksandbox.hosttestutils;

import static org.junit.Assert.fail;

public class AwaitUtils {

    private static final long DEFAULT_RETRY_INTERVAL_IN_MILLIS = 200;
    private static final int DEFAULT_RETRY_LIMIT = 50;

    private AwaitUtils() {}

    public static void waitFor(Condition exitCondition, String failMessage) throws Exception {
        waitFor(exitCondition, failMessage, DEFAULT_RETRY_LIMIT);
    }

    public static void waitFor(Condition exitCondition, String failMessage, int retryLimit)
            throws Exception {
        waitFor(exitCondition, failMessage, retryLimit, DEFAULT_RETRY_INTERVAL_IN_MILLIS);
    }

    public static void waitFor(
            Condition exitCondition, String failMessage, int retryLimit, long retryIntervalMs)
            throws Exception {
        int attempt = 0;
        while (attempt++ < retryLimit) {
            if (exitCondition.check()) {
                return;
            }
            Thread.sleep(retryIntervalMs);
        }
        fail(failMessage);
    }

    @FunctionalInterface
    public interface Condition {
        boolean check() throws Exception;
    }
}
