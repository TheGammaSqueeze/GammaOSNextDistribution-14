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

package android.adservices.debuggablects;

import android.adservices.utils.SimpleActivityBase;
import android.content.Context;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** A simple activity to launch to make the CTS be considered a foreground app by PPAPI. */
public class SimpleActivity extends SimpleActivityBase {
    /**
     * Starts a subclass of {@link SimpleActivity} and wait for the activity to be started the
     * specified max waiting time.
     *
     * @param targetContext the context to start the activity in
     * @param maxWaitTime the max waiting time
     * @throws TimeoutException if the activity didn't start within timeout
     */
    protected static void startAndWait(Context targetContext, Duration maxWaitTime)
            throws TimeoutException {
        SimpleActivityBase.startAndWait(SimpleActivity.class, targetContext, maxWaitTime);
    }

    /**
     * Stops a {@link SimpleActivity}, doesn't wait for the activity to stop or check if the
     * activity was actually running.
     */
    protected static void stop(Context targetContext) {
        SimpleActivityBase.stop(SimpleActivity.class, targetContext);
    }
}
