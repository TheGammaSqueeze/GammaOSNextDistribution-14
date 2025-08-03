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

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import com.android.cts.mockime.MockIme;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} class that enable or disable the given app compat change config for
 * {@link MockIme} to verify the behavior of the given compat change, and will reset the compat
 * config after the test finished.
 */
public class RequireImeCompatFlagRule implements TestRule {
    private final long mCompatFlag;
    private final boolean mEnabled;

    public RequireImeCompatFlagRule(long compatFlag, boolean enabled) {
        mCompatFlag = compatFlag;
        mEnabled = enabled;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runWithShellPermissionIdentity(() -> {
                        runShellCommandOrThrow("am compat " + (mEnabled ? "enable " : "disable ")
                                + mCompatFlag + " "
                                + "com.android.cts.mockime");
                    });
                    base.evaluate();
                } finally {
                    runWithShellPermissionIdentity(() -> {
                        runShellCommandOrThrow("am compat reset " + mCompatFlag + " "
                                + "com.android.cts.mockime");
                    });
                }
            }
        };
    }
}
