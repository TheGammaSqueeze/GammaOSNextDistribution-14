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

package android.car.test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.app.UiAutomation;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.VisibleForTesting;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * {@code JUnit} rule that uses {@link UiAutomation} to adopt the Shell permissions defined by
 * {@link EnsureHasPermission}.
 */
// TODO(b/250108245): move to Bedstead itself or merge with
// {@code com.android.compatibility.common.util.AdoptShellPermissionsRule} (which currently takes
// the permissions from the constructor)
public final class PermissionsCheckerRule implements TestRule {

    @VisibleForTesting
    static final String TAG = PermissionsCheckerRule.class.getSimpleName();

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final UiAutomation mUiAutomation;

    public PermissionsCheckerRule() {
        this(InstrumentationRegistry.getInstrumentation().getUiAutomation());
    }

    @VisibleForTesting
    PermissionsCheckerRule(UiAutomation uiAutomation) {
        mUiAutomation = uiAutomation;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (DBG) {
                    Log.d(TAG, "evaluating " + description.getDisplayName());
                }

                Set<String> permissionsBefore = mUiAutomation.getAdoptedShellPermissions();
                if (permissionsBefore != null && !permissionsBefore.isEmpty()) {
                    Log.w(TAG, "Permissions were adopted before the test: " + permissionsBefore);
                }

                // Gets all permissions, from test, test class, and superclasses
                ArraySet<String> permissions = new ArraySet<>();
                // Test itself
                addPermissions(permissions,
                        description.getAnnotation(EnsureHasPermission.class));
                // Test class and superclasses
                Class<?> testClass = description.getTestClass();
                while (testClass != null) {
                    addPermissions(permissions,
                            testClass.getAnnotation(EnsureHasPermission.class));
                    testClass = testClass.getSuperclass();
                }

                if (permissions.isEmpty()) {
                    if (DBG) {
                        Log.d(TAG, "No annotation, running tests as-is");
                    }
                    base.evaluate();
                    return;
                }

                adoptShellPermissions(permissions, "Adopting Shell permissions before test: %s");
                try {
                    base.evaluate();
                } finally {
                    if (DBG) {
                        Log.d(TAG, "Clearing shell permissions");
                    }
                    mUiAutomation.dropShellPermissionIdentity();
                    adoptShellPermissions(permissionsBefore, "Restoring previous permissions: %s");
                }
            }
        };
    } // apply()

    private void adoptShellPermissions(Set<String> permissionsSet, String messageTemplate) {
        if (permissionsSet == null || permissionsSet.isEmpty()) {
            return;
        }
        Log.d(TAG, String.format(messageTemplate, permissionsSet));
        String[] permissions = permissionsSet.stream().toArray(n -> new String[n]);
        mUiAutomation.adoptShellPermissionIdentity(permissions);
    }

    private static void addPermissions(Set<String> permissions, EnsureHasPermission annotation) {
        if (annotation == null) {
            return;
        }
        for (String value : annotation.value()) {
            permissions.add(value);
        }
    }

    // NOTE: ideally rule should use com.android.bedstead.harrier.annotations.EnsureHasPermission
    // instead, but that annotation requires adding HarrierCommon, which causes other issues in the
    // tests
    /**
     * Lists the permissions that will be adopted by a test method or class.
     *
     * <p>When defined by both method and class (or even superclasses), it will merge the
     * permissions defined by such annotations.
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    public @interface EnsureHasPermission {

        /**
         * List of permissions to be adopted by the test.
         */
        String[] value();
    }
}
