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

package android.autofillservice.cts.commontests;

import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedFieldClassificationService;

import org.junit.After;
import org.junit.Before;

public abstract class FieldClassificationServiceManualActivityLaunchTestCase extends
        AutoFillServiceTestCase.ManualActivityLaunch {

    private static final String TAG = "FieldClassificationServiceManualActivityLaunchTestCase";

    protected static InstrumentedFieldClassificationService.Replier sClassificationReplier;

    private static InstrumentedFieldClassificationService.ServiceWatcher sServiceWatcher;

    @Before
    public void setFixtures() throws Exception {
        sClassificationReplier = InstrumentedFieldClassificationService.getReplier();
        sClassificationReplier.reset();
    }

    @After
    public void resetService() throws Exception {
        // Wait on service disconnect
        // if sServiceWatcher is null, it means connections hasn't been set up before
        if (sServiceWatcher != null) {
            // Rest service with adb command
            Helper.resetAutofillDetectionService();
            sServiceWatcher.waitOnDisconnected();
        }
    }

    protected InstrumentedFieldClassificationService enablePccDetectionService()
            throws InterruptedException {
        sServiceWatcher = InstrumentedFieldClassificationService.setServiceWatcher();

        // Set service with adb command
        Helper.setAutofillDetectionService(InstrumentedFieldClassificationService.SERVICE_NAME);
        InstrumentedFieldClassificationService service = sServiceWatcher.waitOnConnected();
        service.waitUntilConnected();
        return service;
    }
}
