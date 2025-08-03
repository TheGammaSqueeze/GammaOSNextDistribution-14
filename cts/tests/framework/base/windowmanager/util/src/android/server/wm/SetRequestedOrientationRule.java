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

package android.server.wm;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PackageUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Rule for using setRequestedOrientation API in tests.
 */
public class SetRequestedOrientationRule implements TestRule {
    @Override
    public Statement apply(Statement base,
            Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final DisableFixedToUserRotationRule mDisableFixedToUserRotationRule =
                        new DisableFixedToUserRotationRule();
                mDisableFixedToUserRotationRule.before();
                try {
                    try (IgnoreOrientationRequestSession session =
                                 new IgnoreOrientationRequestSession(false /* don' ignore */)) {
                        base.evaluate();
                    }
                } finally {
                    mDisableFixedToUserRotationRule.after();
                }
            }
        };
    }

    /**
     * Builds launch args to launch Activity in fullscreen windowing mode.
     *
     * Activities which request orientation need to be in fullscreen windowing mode.
     */
    public static <T extends Activity> Pair<Intent, ActivityOptions> buildFullScreenLaunchArgs(
            Class<T> klass) {
        final Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                klass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);

        return Pair.create(intent, options);
    }

    public static class DisableFixedToUserRotationRule extends ExternalResource {
        private static final String TAG = "DisableFixToUserRotationRule";
        private static final String COMMAND = "cmd window fixed-to-user-rotation ";

        private final boolean mSupportsRotation;

        private String mOriginalValue;

        public DisableFixedToUserRotationRule() {
            mSupportsRotation = PackageUtil.supportsRotation();
        }

        @Override
        protected void before() throws Throwable {
            if (!mSupportsRotation) {
                return;
            }
            mOriginalValue = SystemUtil.runShellCommand(COMMAND);
            executeShellCommandAndPrint(COMMAND + "disabled");
        }

        @Override
        protected void after() {
            if (!mSupportsRotation) {
                return;
            }
            executeShellCommandAndPrint(COMMAND + mOriginalValue);
        }

        private void executeShellCommandAndPrint(String cmd) {
            Log.i(TAG, "Command: " + cmd + " Output: " + SystemUtil.runShellCommand(cmd));
        }
    }
}
