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

package com.android.adservices.common;

import android.os.Build;

import com.android.adservices.common.AndroidSdk.Level;
import com.android.adservices.common.AndroidSdk.Range;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Device-side version of {@link AbstractSdkLevelSupportedRule}.
 *
 * <p>See {@link AbstractSdkLevelSupportedRule} for usage and examples.
 */
public final class SdkLevelSupportRule extends AbstractSdkLevelSupportedRule {

    @Nullable private Supplier<Level> mDeviceLevelSupplier;

    SdkLevelSupportRule(Level atLeast) {
        super(AndroidLogger.getInstance(), Range.forAtLeast(atLeast.getLevel()));
    }

    @VisibleForTesting
    void setDeviceLevelSupplier(Supplier<Level> levelSupplier) {
        mDeviceLevelSupplier = Objects.requireNonNull(levelSupplier);
    }

    /**
     * Gets a rule that don't skip any test by default.
     *
     * <p>This rule is typically used when:
     *
     * <ul>
     *   <li>Only a few tests require a specific SDK release - such tests will be annotated with a
     *       {@code @RequiresSdkLevel...} annotation.
     *   <li>Some test methods (typically <code>@Before</code>) need to check the SDK release inside
     *       them - these tests call call rule methods such as {@code isAtLeastS()}.
     * </ul>
     */
    public static SdkLevelSupportRule forAnyLevel() {
        return new SdkLevelSupportRule(Level.ANY);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android S+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra
     * {@code @RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastS() {
        return new SdkLevelSupportRule(Level.S);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android T+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra
     * {@code @RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastT() {
        return new SdkLevelSupportRule(Level.T);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android U+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra
     * {@code @RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastU() {
        return new SdkLevelSupportRule(Level.U);
    }

    @Override
    public Level getDeviceApiLevel() {
        if (mDeviceLevelSupplier != null) {
            Level level = mDeviceLevelSupplier.get();
            mLog.d("getDeviceApiLevel(): returning %s as set by supplier", level);
            return level;
        }
        return Level.forLevel(Build.VERSION.SDK_INT);
    }
}
