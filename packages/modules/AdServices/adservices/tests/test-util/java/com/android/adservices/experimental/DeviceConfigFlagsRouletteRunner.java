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

import com.android.adservices.common.AndroidLogger;
import com.android.adservices.common.Logger;

import org.junit.runners.model.InitializationError;

/**
 * An {@link AbstractFlagsRouletteRunner} that uses {@link android.provider.DeviceConfig} to manage
 * the flags.
 *
 * <p><b>Note: </b>this abstract class is necessary because JUnit requires the {@code TestRunner}
 * constructor to take a specific argument (the test class), hence it cannot take a String with the
 * {@link android.provider.DeviceConfig}'s namespace.
 *
 * <p><b>NOTE: </b>to disable it (and run each test just once), set the Android System property
 * {@value #PROP_DISABLED} to {@code true} - this is mostly using during development so you can use
 * {@code atest} to run just a specific test method.
 */
public abstract class DeviceConfigFlagsRouletteRunner extends AbstractFlagsRouletteRunner {

    private static final String PROP_DISABLED = "debug.DeviceConfigFlagsRouletteRunner.disabled";

    private static final Logger sLogger =
            new Logger(AndroidLogger.getInstance(), DeviceConfigFlagsRouletteRunner.class);

    /**
     * Subclass should provide a constructor that takes just {@code Class<?> testClass} and call
     * this constructor with the proper namespace.
     */
    protected DeviceConfigFlagsRouletteRunner(Class<?> testClass, String namespace)
            throws InitializationError {
        super(testClass, new DeviceConfigFlagsManager(namespace));
    }

    @Override
    protected Logger log() {
        return sLogger;
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
