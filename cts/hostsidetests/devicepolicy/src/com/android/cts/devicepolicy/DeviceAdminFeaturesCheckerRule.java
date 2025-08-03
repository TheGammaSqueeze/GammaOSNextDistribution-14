/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom rule used to skip tests when the device doesn't have {@value #FEATURE_DEVICE_ADMIN} and/or
 * additional features (as defined the {@link RequiresAdditionalFeatures} and
 * {@link DoesNotRequireFeature} annotations.
 */
public final class DeviceAdminFeaturesCheckerRule implements TestRule {

    public static final String FEATURE_BACKUP = "android.software.backup";
    public static final String FEATURE_DEVICE_ADMIN = "android.software.device_admin";
    public static final String FEATURE_MANAGED_USERS = "android.software.managed_users";

    private final BaseDevicePolicyTest mTest;

    private boolean mHasRequiredFeatures;

    public DeviceAdminFeaturesCheckerRule(BaseDevicePolicyTest test) {
        mTest = test;
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ITestDevice testDevice = mTest.getDevice();
                assumeTrue("Test device is not available", testDevice != null);

                int apiLevel = testDevice.getApiLevel();
                assumeTrue("Device API level is " + apiLevel + ", minimum required is 21",
                        apiLevel >= 21); // requires Build.VERSION_CODES.L

                String testName = description.getDisplayName();

                TemporarilyIgnoreOnHeadlessSystemUserMode temporarilyIgnoredAnnotation = description
                        .getAnnotation(TemporarilyIgnoreOnHeadlessSystemUserMode.class);
                if (temporarilyIgnoredAnnotation != null
                        && BaseDevicePolicyTest.isHeadlessSystemUserMode(testDevice)) {
                    throw new AssumptionViolatedException(
                            "TEMPORARILY skipping " + testName + " on headless system user mode "
                                    + "(reason: " + temporarilyIgnoredAnnotation.reason() + ")");
                }

                IgnoreOnHeadlessSystemUserMode ignoredAnnotation = description
                        .getAnnotation(IgnoreOnHeadlessSystemUserMode.class);
                if (ignoredAnnotation != null
                        && BaseDevicePolicyTest.isHeadlessSystemUserMode(testDevice)) {
                    throw new AssumptionViolatedException(
                            "Skipping " + testName + " on headless system user mode (reason: "
                                    + ignoredAnnotation.reason() + ")");
                }

                List<String> requiredFeatures = new ArrayList<>();

                if (mTest.skipDeviceAdminFeatureCheck()) {
                    CLog.i("Skipping device admin check for %s", testName);
                } else {
                    requiredFeatures.add(FEATURE_DEVICE_ADMIN);
                }

                // Method annotations
                addRequiredAdditionalFeatures(requiredFeatures, description
                        .getAnnotation(RequiresAdditionalFeatures.class));
                addRequiredManagedUsersFeature(requiredFeatures, testDevice, description
                        .getAnnotation(RequiresProfileOwnerSupport.class));

                // Class annotations
                Class<?> clazz = description.getTestClass();
                while (clazz != Object.class) {
                    addRequiredAdditionalFeatures(requiredFeatures,
                            clazz.getAnnotation(RequiresAdditionalFeatures.class));
                    addRequiredManagedUsersFeature(requiredFeatures, testDevice,
                            clazz.getAnnotation(RequiresProfileOwnerSupport.class));
                    clazz = clazz.getSuperclass();
                }

                CLog.v("Required features for test %s: %s", testName, requiredFeatures);

                List<String> missingFeatures = new ArrayList<>(requiredFeatures.size());
                for (String requiredFeature : requiredFeatures) {
                    if (!testDevice.hasFeature(requiredFeature)) {
                        missingFeatures.add(requiredFeature);
                    }
                }

                mHasRequiredFeatures = missingFeatures.isEmpty();

                if (!mHasRequiredFeatures) {
                    DoesNotRequireFeature bypass = description
                            .getAnnotation(DoesNotRequireFeature.class);
                    if (bypass != null) {
                        CLog.i("Device is missing features (%s), but running test %s anyways "
                                + "because of %s annotation", missingFeatures, testName, bypass);
                    } else {
                        throw new AssumptionViolatedException("Device does not have the following "
                                + "features: " + missingFeatures);
                    }
                }

                // Finally, give the test a chance to be skipped
                mTest.assumeTestEnabled();

                base.evaluate();
            }

            private void addRequiredAdditionalFeatures(List<String> requiredFeatures,
                    RequiresAdditionalFeatures annotation) {
                if (annotation == null) return;

                for (String additionalFeature : annotation.value()) {
                    requiredFeatures.add(additionalFeature);
                }
            }

            private void addRequiredManagedUsersFeature(List<String> requiredFeatures,
                    ITestDevice testDevice, RequiresProfileOwnerSupport annotation)
                    throws DeviceNotAvailableException {
                if (annotation == null) return;

                if (BaseDevicePolicyTest.isHeadlessSystemUserMode(testDevice)) {
                    CLog.i("Not requiring feature %s on headless system user mode",
                            FEATURE_MANAGED_USERS);
                    return;
                }

                requiredFeatures.add(FEATURE_MANAGED_USERS);
            }
        };
    }

    /**
     * Checks if the device has the required features for this test.
     */
    public boolean hasRequiredFeatures() {
        return mHasRequiredFeatures;
    }

    /**
     * Used to annotate a test method that should run if when the device doesn't have the features
     * required by the test class.
     *
     * <p><b>NOTE: </b>it doesn't work when used on overridden test methods (as {@code JUnit} will
     * only return the annotations of the superclass method).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public static @interface DoesNotRequireFeature {
    }

    /**
     * Sets additional required features for a given test class or method.
     *
     * <p><b>NOTE: </b>it doesn't work when used on overridden test methods (as {@code JUnit} will
     * only return the annotations of the superclass method).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface RequiresAdditionalFeatures {
        String[] value();
    }


    // TODO(b/183105338): remove annotation if FEATURE_MANAGED_USERS is split into separate features
    // for profile owner and managed profile support.
    /**
     * Used to annotated a test method or class that requires profile owner support.
     *
     * <p>Traditionally, these tests were looking for the {@code FEATURE_MANAGE_USERS} feature, but
     * on headless system mode devices a profile owner is created in the current user when the
     * device owner is set on system user, even if the device doesn't support the feature - this
     * annotation takes care of both cases.
     *
     * <p><b>NOTE: </b>it doesn't work when used on overridden test methods (as {@code JUnit} will
     * only return the annotations of the superclass method).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface RequiresProfileOwnerSupport {
    }

    /**
     * TODO(b/132260693): STOPSHIP - temporary annotation used on tests that haven't been fixed to
     * run on headless system user yet
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public static @interface TemporarilyIgnoreOnHeadlessSystemUserMode {
        String bugId();
        String reason();
    }

    /**
     * Annotation used on tests that cannot run on devices that use headless system user mode.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public static @interface IgnoreOnHeadlessSystemUserMode {
        String reason();
    }
}
