/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import static android.os.UserHandle.USER_SYSTEM;

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} class that disables screen doze settings before each test method running and
 * restoring to initial values after test method finished.
 */
public class DisableScreenDozeRule implements TestRule {

    private final AmbientDisplayConfiguration mConfig;
    private final Context mContext;

    public DisableScreenDozeRule() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mConfig = new AmbientDisplayConfiguration(mContext);
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    SystemUtil.runWithShellPermissionIdentity(() -> {
                        // disable current doze settings
                        mConfig.disableDozeSettings(true /* shouldDisableNonUserConfigurable */,
                                USER_SYSTEM);
                    });
                    base.evaluate();
                } finally {
                    SystemUtil.runWithShellPermissionIdentity(() -> {
                        // restore doze settings
                        mConfig.restoreDozeSettings(USER_SYSTEM);
                    });
                }
            }
        };
    }
}
