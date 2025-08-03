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

package com.android.tools.metalava

import com.android.tools.metalava.testing.java
import org.junit.Test

class RequiresFeatureTest : DriverTest() {

    private fun checkRequiresFeatureHandling(
        feature: String,
        import: String = "import android.content.pm.PackageManager;",
        enforcement: String = "",
        expectedText: String,
        expectedIssues: String? = "",
    ) {
        val attributes =
            if (enforcement == "") feature else """value = $feature, enforcement = "$enforcement""""
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import android.annotation.RequiresFeature;
                        $import
                        @SuppressWarnings("WeakerAccess")
                        @RequiresFeature($attributes)
                        public class FeatureUser {
                        }
                    """
                    ),
                    java(
                        """
                        package android.content.pm;
                        public abstract class PackageManager {
                            public static final String FEATURE_LOCATION = "android.hardware.location";
                            /** @hide */
                            public static final String FEATURE_HIDDEN = "android.feature.hidden";
                            public boolean hasSystemFeature(String feature) { return false; }
                        }
                    """
                    ),
                    java(
                        """
                        package android.pkg.other;
                        public abstract class OtherFeatureManager {
                            public static final String FEATURE_OTHER = "android.pkg.other.feature";
                            public boolean hasMyFeature(String feature) { return false; }
                        }
                    """
                    ),
                    requiresFeatureSource,
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        $import
                        /**
                         * $expectedText
                         */
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class FeatureUser {
                        public FeatureUser() { throw new RuntimeException("Stub!"); }
                        }
                    """
                    ),
                ),
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `Check RequiresFeature handling`() {
        checkRequiresFeatureHandling(
            feature = "PackageManager.FEATURE_LOCATION",
            expectedText =
                "Requires the {@link android.content.pm.PackageManager#FEATURE_LOCATION PackageManager#FEATURE_LOCATION} feature which can be detected using {@link android.content.pm.PackageManager#hasSystemFeature(String) PackageManager.hasSystemFeature(String)}.",
        )
    }

    @Test
    fun `Check RequiresFeature handling - missing feature`() {
        checkRequiresFeatureHandling(
            feature = "PackageManager.FEATURE_UNKNOWN",
            expectedText =
                "Requires the {@link PackageManager.FEATURE_UNKNOWN} feature which can be detected using {@link android.content.pm.PackageManager#hasSystemFeature(String) PackageManager.hasSystemFeature(String)}.",
            expectedIssues =
                "src/test/pkg/FeatureUser.java:6: lint: Cannot find feature field for PackageManager.FEATURE_UNKNOWN required by class test.pkg.FeatureUser (may be hidden or removed) [MissingPermission]",
        )
    }

    @Test
    fun `Check RequiresFeature handling - hidden feature`() {
        checkRequiresFeatureHandling(
            feature = "PackageManager.FEATURE_HIDDEN",
            expectedText =
                "Requires the PackageManager#FEATURE_HIDDEN feature which can be detected using {@link android.content.pm.PackageManager#hasSystemFeature(String) PackageManager.hasSystemFeature(String)}.",
            expectedIssues =
                "src/test/pkg/FeatureUser.java:6: lint: Feature field PackageManager.FEATURE_HIDDEN required by class test.pkg.FeatureUser is hidden or removed [MissingPermission]",
        )
    }

    @Test
    fun `Check RequiresFeature handling - custom enforcement`() {
        checkRequiresFeatureHandling(
            feature = "OtherFeatureManager.FEATURE_OTHER",
            import = "import android.pkg.other.OtherFeatureManager;",
            enforcement = "android.pkg.other.OtherFeatureManager#hasMyFeature",
            expectedText =
                "Requires the {@link android.pkg.other.OtherFeatureManager#FEATURE_OTHER OtherFeatureManager#FEATURE_OTHER} feature which can be detected using {@link android.pkg.other.OtherFeatureManager#hasMyFeature(String) OtherFeatureManager.hasMyFeature(String)}.",
        )
    }

    @Test
    fun `Check RequiresFeature handling - invalid enforcement`() {
        checkRequiresFeatureHandling(
            feature = "OtherFeatureManager.FEATURE_OTHER",
            import = "import android.pkg.other.OtherFeatureManager;",
            enforcement = "invalid enforcement value",
            expectedText =
                "Requires the {@link android.pkg.other.OtherFeatureManager#FEATURE_OTHER OtherFeatureManager#FEATURE_OTHER} feature which can be detected using {@link android.content.pm.PackageManager#hasSystemFeature(String) PackageManager.hasSystemFeature(String)}.",
            expectedIssues =
                "src/test/pkg/FeatureUser.java:6: lint: Invalid 'enforcement' value 'invalid enforcement value', must be of the form <qualified-class>#<method-name>, using default [InvalidFeatureEnforcement]",
        )
    }
}
