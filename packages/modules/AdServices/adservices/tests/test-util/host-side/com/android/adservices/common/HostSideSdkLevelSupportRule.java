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

import com.android.adservices.common.AndroidSdk.Level;
import com.android.adservices.common.AndroidSdk.Range;
import com.android.tradefed.device.ITestDevice;

/** See {@link AbstractSdkLevelSupportedRule}. */
public final class HostSideSdkLevelSupportRule extends AbstractSdkLevelSupportedRule {

    private HostSideSdkLevelSupportRule(Level atLeast) {
        super(ConsoleLogger.getInstance(), Range.forAtLeast(atLeast.getLevel()));
    }

    public void setDevice(ITestDevice device) {
        TestDeviceHelper.setTestDevice(device);
    }

    /**
     * Gets a rule that don't skip any test by default.
     *
     * <p>This rule is typically used when:
     *
     * <ul>
     *   <li>Only a few tests require a specific SDK release - such tests will be annotated with a
     *       <code>@RequiresSdkLevel...</code> annotation.
     *   <li>Some test methods (typically <code>&#064;Before</code>) need to check the SDK release
     *       inside them - these tests call call rule methods such as {@code isAtLeastS()}.
     * </ul>
     */
    public static HostSideSdkLevelSupportRule forAnyLevel() {
        return new HostSideSdkLevelSupportRule(Level.ANY);
    }

    /** Gets a rule that ensures test is executed on Android S+. Skips test otherwise. */
    public static HostSideSdkLevelSupportRule forAtLeastS() {
        return new HostSideSdkLevelSupportRule(Level.S);
    }

    /** Gets a rule that ensures test is executed on Android T+. Skips test otherwise. */
    public static HostSideSdkLevelSupportRule forAtLeastT() {
        return new HostSideSdkLevelSupportRule(Level.T);
    }

    /** Gets a rule that ensures test is executed on Android U+. Skips test otherwise. */
    public static HostSideSdkLevelSupportRule forAtLeastU() {
        return new HostSideSdkLevelSupportRule(Level.U);
    }

    @Override
    public Level getDeviceApiLevel() {
        return Level.forLevel(TestDeviceHelper.getApiLevel());
    }
}
