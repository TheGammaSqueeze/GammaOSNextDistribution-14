/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.annotation.Annotation;

// TODO(b/315542995): provide host-side implementation
/**
 * Test case for {@link AbstractAdServicesDeviceSupportedRule} implementations.
 *
 * <p>By default, it uses a {@link
 * AbstractAdServicesDeviceSupportedRuleTest.FakeAdServicesDeviceSupportedRule bogus rule} so it can
 * be run by IDEs.
 */
public class AbstractAdServicesDeviceSupportedRuleTest {

    private final Logger.RealLogger mRealLogger = StandardStreamsLogger.getInstance();
    private final SimpleStatement mBaseStatement = new SimpleStatement();

    private FakeAdServicesDeviceSupportedRule mAdServicesDeviceSupportedRule;

    @Mock private AbstractDeviceSupportHelper mAbstractDeviceSupportHelper;

    @Rule public final Expect expect = Expect.create();
    @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Before
    public void setup() {
        mAdServicesDeviceSupportedRule =
                new FakeAdServicesDeviceSupportedRule(mRealLogger, mAbstractDeviceSupportHelper);
        mockIsGoDevice(false);
        mockIsLowRamDevice(false);
    }

    @Test
    public void testIsAdServicesSupported_supported() throws Exception {
        when(mAbstractDeviceSupportHelper.isDeviceSupported()).thenReturn(true);

        expect.that(mAdServicesDeviceSupportedRule.isAdServicesSupportedOnDevice()).isTrue();
    }

    @Test
    public void testIsAdServicesSupportedOnDeviceTest_notSupported() throws Exception {
        when(mAbstractDeviceSupportHelper.isDeviceSupported()).thenReturn(false);

        expect.that(mAdServicesDeviceSupportedRule.isAdServicesSupportedOnDevice()).isFalse();
    }

    @Test
    public void testIsLowRamDevice_returnsTrue() {
        mockIsLowRamDevice(true);

        expect.that(mAdServicesDeviceSupportedRule.isLowRamDevice()).isTrue();
    }

    @Test
    public void testIsLowRamDevice_returnsFalse() {
        mockIsLowRamDevice(false);

        expect.that(mAdServicesDeviceSupportedRule.isLowRamDevice()).isFalse();
    }

    @Test
    public void testIsGoDevice_returnsTrue() {
        mockIsGoDevice(true);

        expect.that(mAdServicesDeviceSupportedRule.isGoDevice()).isTrue();
    }

    @Test
    public void testIsGoDevice_returnsFalse() {
        mockIsGoDevice(false);

        expect.that(mAdServicesDeviceSupportedRule.isGoDevice()).isFalse();
    }

    @Test
    public void testAnnotatedWithLowRam_deviceNotLowRam() {
        mockIsLowRamDevice(false);
        Description description = createTestMethod(TestAnnotations.requiresLowRamDevice());

        assertTestThrowsAssumptionsViolatedException(
                description,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE);
    }

    @Test
    public void testAnnotatedWithLowRam_deviceLowRam() throws Throwable {
        mockIsLowRamDevice(true);
        Description description = createTestMethod(TestAnnotations.requiresLowRamDevice());

        mAdServicesDeviceSupportedRule.apply(mBaseStatement, description).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void testAnnotatedWithRequiresGoDevice_deviceNotGoDevice() {
        mockIsGoDevice(false);
        Description description = createTestMethod(TestAnnotations.requiresGoDevice());

        assertTestThrowsAssumptionsViolatedException(
                description,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_GO_DEVICE_ASSUMPTION_FAILED_ERROR_MESSAGE);
    }

    @Test
    public void testAnnotatedWithRequiresGoDevice_deviceGoDevice() throws Throwable {
        mockIsGoDevice(true);
        Description description = createTestMethod(TestAnnotations.requiresGoDevice());

        mAdServicesDeviceSupportedRule.apply(mBaseStatement, description).evaluate();

        mBaseStatement.assertEvaluated();
    }

    @Test
    public void testAnnotatedWithRequiresGoDeviceAndRequiresLowRamDevice_onlyGoDevice() {
        mockIsGoDevice(true);
        Description description =
                createTestMethod(
                        TestAnnotations.requiresGoDevice(), TestAnnotations.requiresLowRamDevice());

        assertTestThrowsAssumptionsViolatedException(
                description,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE);
    }

    @Test
    public void testAnnotatedWithRequiresGoDeviceAndRequiresLowMemoryDevice_onlyLowRamDevice() {
        mockIsLowRamDevice(true);
        Description description =
                createTestMethod(
                        TestAnnotations.requiresGoDevice(), TestAnnotations.requiresLowRamDevice());

        assertTestThrowsAssumptionsViolatedException(
                description,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_GO_DEVICE_ASSUMPTION_FAILED_ERROR_MESSAGE);
    }

    @Test
    public void testAnnotatedWithRequiresGoDeviceAndRequiresLowMemoryDevice_deviceNone() {
        mockIsLowRamDevice(false);
        mockIsGoDevice(false);
        Description description =
                createTestMethod(
                        TestAnnotations.requiresGoDevice(), TestAnnotations.requiresLowRamDevice());

        assertTestThrowsAssumptionsViolatedException(
                description,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_GO_DEVICE_ASSUMPTION_FAILED_ERROR_MESSAGE,
                AbstractAdServicesDeviceSupportedRule
                        .REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE);
    }

    @Test
    public void testAnnotatedWithRequiresGoDeviceAndRequiresLowMemoryDevice_deviceBoth()
            throws Throwable {
        mockIsLowRamDevice(true);
        mockIsGoDevice(true);
        Description description =
                createTestMethod(
                        TestAnnotations.requiresGoDevice(), TestAnnotations.requiresLowRamDevice());

        mAdServicesDeviceSupportedRule.apply(mBaseStatement, description).evaluate();

        mBaseStatement.assertEvaluated();
    }

    private void assertTestThrowsAssumptionsViolatedException(
            Description description, String... expectedReasons) {
        DeviceConditionsViolatedException e =
                assertThrows(
                        DeviceConditionsViolatedException.class,
                        () ->
                                mAdServicesDeviceSupportedRule
                                        .apply(mBaseStatement, description)
                                        .evaluate());

        expect.withMessage("exception message")
                .that(e.getConditionsViolatedReasons())
                .containsExactlyElementsIn(expectedReasons);
        mBaseStatement.assertNotEvaluated();
    }

    /** Bogus implementation of {@link AbstractAdServicesDeviceSupportedRule}. */
    private static final class FakeAdServicesDeviceSupportedRule
            extends AbstractAdServicesDeviceSupportedRule {
        /** Default constructor. */
        private FakeAdServicesDeviceSupportedRule(
                Logger.RealLogger logger, AbstractDeviceSupportHelper deviceSupportHelper) {
            super(logger, deviceSupportHelper);
        }
    }

    private Description createTestMethod(Annotation... annotations) {
        return Description.createTestDescription(
                AbstractAdServicesDeviceSupportedRuleTest.class, "method_name", annotations);
    }

    private void mockIsLowRamDevice(boolean isLowRam) {
        when(mAbstractDeviceSupportHelper.isLowRamDevice()).thenReturn(isLowRam);
    }

    private void mockIsGoDevice(boolean isGoDevice) {
        when(mAbstractDeviceSupportHelper.isGoDevice()).thenReturn(isGoDevice);
    }
}
