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

package com.android.adservices.experimental;

import android.os.SystemProperties;
import android.provider.DeviceConfig;

import org.junit.runners.model.InitializationError;

/**
 * A {@link DeviceConfigFlagsRouletteRunner} that uses AdService's namespace.
 *
 * <p>NOTE: if you just want to switch the values of the global kill switch, please use {@link
 * GlobalKillSwitchFlipper} instead.
 *
 * <p><b>NOTE: </b>to disable it (and run each test just once), set the Android System property
 * {@value #PROP_DISABLED} to {@code true} - this is mostly using during development so you can use
 * {@code atest} to run just a specific test method.
 */
public final class AdServicesFlagsRouletteRunner extends DeviceConfigFlagsRouletteRunner {

    private static final String PROP_DISABLED = "debug.AdServicesFlagsRouletteRunner.disabled";

    public AdServicesFlagsRouletteRunner(Class<?> testClass) throws InitializationError {
        super(testClass, DeviceConfig.NAMESPACE_ADSERVICES);
    }

    @Override
    protected boolean isDisabled() {
        String propValue = SystemProperties.get(PROP_DISABLED);
        if ("true".equalsIgnoreCase(propValue)) {
            log().v("isDisabled(): returning true because of system property %s", PROP_DISABLED);
            return true;
        }
        return false;
    }
}
