/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.android.helpers.DumpsysServiceHelper;

@OptionClass(alias = "dumpsys-service-listener")
public class DumpsysServiceListener extends BaseCollectionListener<String> {

    private static final String TAG = DumpsysServiceListener.class.getSimpleName();
    @VisibleForTesting static final String SERVICE_SEPARATOR = ",";
    @VisibleForTesting static final String SERVICE_NAMES_KEY = "service-names";

    public DumpsysServiceListener() {
        createHelperInstance(new DumpsysServiceHelper());
    }

    @VisibleForTesting
    public DumpsysServiceListener(Bundle args, DumpsysServiceHelper helper) {
        super(args, helper);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String servicesString = args.getString(SERVICE_NAMES_KEY);
        if (servicesString == null) {
            Log.w(TAG, "No process name provided. Nothing will be collected");
            return;
        }
        ((DumpsysServiceHelper) mHelper).setUp(servicesString.split(SERVICE_SEPARATOR));
    }
}
