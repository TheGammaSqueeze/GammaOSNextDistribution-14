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

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.common.AndroidLogger;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Annotation;

/**
 * Rule used to properly check a test behavior depending on whether the {@code AdServices} global
 * kill switch is set in the device.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final GlobalKillSwitchRule globalKillSwitchRule = new GlobalKillSwitchRule();
 * </pre>
 *
 * <p>In the example above, it assumes that every test should only be executed when the {@code
 * AdServices} global kill switch is disabled - if it's enabled, the test will be skipped (with an
 * {@link AssumptionViolatedException}).
 *
 * <p>The rule can also be used to make sure APIs throw {@link IllegalStateException} when the
 * global kill switch is enabled; in that case, you annotate the test method with {@link
 * RequiresGlobalKillSwitchEnabled}, then simply call the API that should throw the exception on its
 * body - the rule will make sure the exception is thrown (and fail the test if it isn't). Example:
 *
 * <pre class="prettyprint">
 * &#064;Test
 * &#064;RequiresGlobalKillSwitchEnabled
 * public void testFoo_notSupported() {
 *    mObjectUnderTest.foo();
 * }
 * </pre>
 *
 * <p>Even better if the same method can be used whether the global kill switch is enabled or not:
 *
 * <pre class="prettyprint">
 * &#064;Test
 * &#064;RequiresGlobalKillSwitchDisabledOrEnabled
 * public void testBar() {
 *    boolean foo = mObjectUnderTest.bar(); // should throw ISE when not supported
 *    assertThat(foo).isTrue();             // should pass when supported
 * }
 * </pre>
 */
public final class GlobalKillSwitchRule extends AbstractSupportedFeatureRule {

    /** Creates a rule using {@link Mode#NOT_SUPPORTED_BY_DEFAULT}. */
    public GlobalKillSwitchRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public GlobalKillSwitchRule(Mode mode) {
        super(AndroidLogger.getInstance(), mode);
    }

    @Override
    public boolean isFeatureSupported() throws Exception {
        return !isKillSwitchEnabled();
    }

    public boolean isKillSwitchEnabled() throws Exception {
        boolean isEnabled = AdServicesSupportHelper.getGlobalKillSwitch();
        mLog.v("isKillSwitchDisabled(): %b", isEnabled);
        return isEnabled;
    }

    @Override
    protected void throwFeatureNotSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("AdServices disabled by global kill-switch");
    }

    @Override
    protected void throwFeatureSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("AdServices enabled by global kill-switch");
    }

    @Override
    protected void throwUnsupporteTestDidntThrowExpectedExceptionError() {
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but didn't throw any");
    }

    // TODO(b/284971005): let constructor / build specify the expected exception
    @Override
    protected void assertUnsupportedTestThrewRightException(Throwable thrown) {
        if (!(thrown instanceof IllegalStateException)
                && (thrown.getCause() instanceof IllegalStateException)) {
            return;
        }
        super.assertUnsupportedTestThrewRightException(thrown);
    }

    @Override
    protected boolean isFeatureSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresGlobalKillSwitchDisabled;
    }

    @Override
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresGlobalKillSwitchEnabled;
    }

    @Override
    protected boolean isFeatureSupportedOrNotAnnotation(Annotation annotation) {
        return annotation instanceof RequiresGlobalKillSwitchDisabledOrEnabled;
    }
}
