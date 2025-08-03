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
package android.carrierapi.cts.targetprep;

import com.android.compatibility.common.tradefed.targetprep.ApkInstrumentationPreparer;
import com.android.tradefed.config.OptionClass;

/** Ensures that the CTS SIM's content matches the latest spec. */
@OptionClass(alias = "carrier-api-preparer")
public class CarrierApiPreparer extends ApkInstrumentationPreparer {

    public CarrierApiPreparer() {
        mWhen = When.BEFORE;
    }
}
