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

package android.adservices.test.scenario.adservices.measurement.load.utils;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

public class MeasurementMockServerDispatcher extends Dispatcher {

    private static final String SOURCE_PATH = "/source";
    private static final String TRIGGER_PATH = "/trigger";
    // 100ms as default to simulate an average network response latency
    private int mMsDelay = 100;
    /**
     * Returns a response to satisfy {@code request}. This method may block (for instance, to wait
     * on a CountdownLatch).
     */
    @Override
    public MockResponse dispatch(RecordedRequest request) throws InterruptedException {

        switch (request.getPath()) {
            case SOURCE_PATH:
                return MeasurementMockResponseFactory.createRegisterSourceResponse()
                        .setBodyDelayTimeMs(mMsDelay);
            case TRIGGER_PATH:
                return MeasurementMockResponseFactory.createRegisterTriggerResponse()
                        .setBodyDelayTimeMs(mMsDelay);
            default:
                throw new IllegalStateException("Unexpected value: " + request.getPath());
        }
    }

    /**
     * @param msDelay
     */
    public void setSimulatedResponseDelay(int msDelay) {
        mMsDelay = msDelay;
    }
}
