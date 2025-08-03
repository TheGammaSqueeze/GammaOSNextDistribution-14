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

import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;

import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.adservices.common.AndroidLogger;
import com.android.adservices.common.Logger;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.util.List;

/**
 * See {@link GlobalKillSwitchFlipper} - this class contains its main logic, while subclasses offers
 * customizations.
 */
abstract class AbstractGlobalKillSwitchFlipper extends AbstractFlagsRouletteRunner {

    public static final String FLAG = KEY_GLOBAL_KILL_SWITCH;

    private static final String[] FLAGS = {FLAG};
    private static final String PROP_DISABLED = "debug.adservices.GlobalKillSwitchFlipper.disabled";

    private static final Logger sLogger =
            new Logger(AndroidLogger.getInstance(), GlobalKillSwitchFlipper.class);

    protected AbstractGlobalKillSwitchFlipper(Class<?> testClass) throws InitializationError {
        super(testClass, new DeviceConfigFlagsManager(DeviceConfig.NAMESPACE_ADSERVICES));
    }

    @Override
    protected String[] getFlagsRoulette(TestClass testClass, List<Throwable> errors) {
        return FLAGS;
    }

    @Override
    protected Logger log() {
        return sLogger;
    }

    @Override
    protected FlagState[] getRequiredFlagStates(FrameworkMethod method) {
        FlagState requiredFlag = null;
        RequiresGlobalKillSwitchEnabled enabledAnnotation =
                method.getAnnotation(RequiresGlobalKillSwitchEnabled.class);
        if (enabledAnnotation != null) {
            requiredFlag = new FlagState(FLAG, true);
        } else {
            RequiresGlobalKillSwitchDisabled disabledAnnotation =
                    method.getAnnotation(RequiresGlobalKillSwitchDisabled.class);
            if (disabledAnnotation != null) {
                requiredFlag = new FlagState(FLAG, false);
            }
        }
        if (requiredFlag == null) {
            return null;
        }
        sLogger.v("getRequiredFlags(): returning [%s] for %s", requiredFlag, method.getName());
        return new FlagState[] {requiredFlag};
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
