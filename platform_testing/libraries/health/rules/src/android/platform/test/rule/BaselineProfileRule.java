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
package android.platform.test.rule;

import android.util.Log;

import kotlin.Unit;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Generates a "Baseline Profile" for the provided package using the wrapped test statements.
 *
 * <p>To use this, simply apply it as a {@link org.junit.ClassRule} and pass the required arguments.
 * {@link org.junit.rules.TestRule}s aren't aware of whether they're being applied as a {@link
 * org.junit.Rule} or a {@link org.junit.ClassRule}. The {@code BaselineProfileRule} is best used as
 * at the top class- or suite-level, though unenforcible. Side-effects of using this for each {@link
 * org.junit.Test} or for each class within a {@link org.junit.runners.Suite} are not documented.
 *
 * <p>For more information on what Baseline Profiles are, how they work, and how they help, {@see
 * https://d.android.com/topic/performance/baselineprofiles}.
 */
public class BaselineProfileRule extends TestWatcher {
    private static final String LOG_TAG = BaselineProfileRule.class.getSimpleName();
    private static final String PROFILE_ACTION = "profile-action";
    // If selected, generates a Baseline Profile. If not, or unspecified, doesn't generate one.
    private static final String GEN_BASELINE_PROFILE_MODE = "generate-baseline-profile";
    // If selected, checks for profile prior to performance test.
    private static final String TEST_WITH_PROFILE_MODE = "test-performance-with-profile";
    // If selected, checks for lack of profile prior to performance test.
    private static final String TEST_WITHOUT_PROFILE_MODE = "test-performance-without-profile";

    private final String mBaselineProfilePackage;

    public BaselineProfileRule(String baselineProfilePackage) {
        mBaselineProfilePackage = baselineProfilePackage;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        switch (getArguments().getString(PROFILE_ACTION, "none")) {
            case GEN_BASELINE_PROFILE_MODE:
                // This class can't extend androidx.benchmark.macro.junit4.BaselineProfileRule,
                // because it's final; however that would be a preferable way to more cleanly
                // interact with it.
                androidx.benchmark.macro.junit4.BaselineProfileRule innerRule =
                        new androidx.benchmark.macro.junit4.BaselineProfileRule();
                return innerRule.apply(
                        new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                // Consider using the BaselineProfileRule's stability enforcement
                                // instead of relying on CrystalBall's fixed iteration count.
                                innerRule.collect(
                                        mBaselineProfilePackage,
                                        1, // Iterations are supported by most Runners already.
                                        1, // Iterations are supported by most Runners already.
                                        null, // No special prefixing necessary.
                                        false, // Ignore, not using dex layout optimizations.
                                        false, // Ignore stability enforcement for now.
                                        (any) -> true, // Don't apply any profile filters.
                                        (scope) -> {
                                            // Evaluating the base Statement may throw a Throwable,
                                            // which is checked and not compatible with the lambda
                                            // without a try-catch statement.
                                            try {
                                                base.evaluate();
                                            } catch (Throwable e) {
                                                Log.e(
                                                        LOG_TAG,
                                                        "Caught checked exception in parent "
                                                                + "statement.",
                                                        e);
                                            } finally {
                                                return Unit.INSTANCE;
                                            }
                                        });
                            }
                        },
                        description);

            case TEST_WITH_PROFILE_MODE:
                // check for profile
                String compileStatus =
                        executeShellCommand(
                                String.format("dumpsys package %s", mBaselineProfilePackage));
                if (!compileStatus.contains("status=speed-profile")) {
                    throw new IllegalStateException(
                            String.format(
                                    "The package, %s, was not found to be compiled with"
                                            + " speed-profile.\n"
                                            + "The compilation status returned this line: %s",
                                    mBaselineProfilePackage, compileStatus));
                }
                break;

            case TEST_WITHOUT_PROFILE_MODE:
                // check for no profile
                compileStatus =
                        executeShellCommand(
                                String.format("dumpsys package %s", mBaselineProfilePackage));
                if (compileStatus.contains("status=speed-profile")) {
                    throw new IllegalStateException(
                            String.format(
                                    "The package, %s, was found to be compiled with"
                                            + " speed-profile.\n"
                                            + "The compilation status returned this line: %s",
                                    mBaselineProfilePackage, compileStatus));
                }
                break;

            default:
                Log.d(
                        LOG_TAG,
                        String.format(
                                "Options %s, %s, and %s are disabled.",
                                GEN_BASELINE_PROFILE_MODE,
                                TEST_WITH_PROFILE_MODE,
                                TEST_WITHOUT_PROFILE_MODE));
                break;
        }
        return base;
    }
}
