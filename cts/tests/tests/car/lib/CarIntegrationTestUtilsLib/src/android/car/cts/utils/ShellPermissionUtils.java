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

package android.car.cts.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.annotation.IntDef;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class contains static methods to adopt all or a subset of the shell's permissions while invoking
 * a {@link ThrowingRunnable}. Also, if an {@link AssumptionViolatedException} is thrown while
 * executing {@link ThrowingRunnable}, it will pass it through to the test infrastructure.
 *
 * TODO(b/250108245): Replace class with PermissionCheckerRule annotations.
 */
public final class ShellPermissionUtils {
    private ShellPermissionUtils() {
    }

    /**
     * Run {@code throwingRunnable} with all the shell's permissions adopted.
     *
     * <p>It is possible that the adoption failed.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        run(throwingRunnable, uiAutomation);
    }

    /**
     * No check for granted permissions.
     */
    public static final int CHECK_MODE_NONE = 1;
    /**
     * Skip the test if the required permissions are not granted after trying to adopt the shell
     * permissions.
     */
    public static final int CHECK_MODE_ASSUME = 2;
    /**
     * Fail the test if the required permissions are not granted after trying to adopt the shell
     * permissions.
     */
    public static final int CHECK_MODE_ASSERT = 3;

    /** @hide */
    @IntDef(prefix = {"CHECK_MODE_"}, value = {
            CHECK_MODE_NONE,
            CHECK_MODE_ASSUME,
            CHECK_MODE_ASSERT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PermissionCheckMode {}

    /**
     * Run {@code throwingRunnable} with a subset, {@code permissions}, of the shell's permissions
     * adopted.
     *
     * <p>This will skip the test case if the required permissions are not granted after trying
     * to adopt the shell permissions.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable,
            String... permissions) {
        runWithShellPermissionIdentity(throwingRunnable, CHECK_MODE_ASSUME, permissions);
    }

    /**
     * Run {@code throwingRunnable} with a subset, {@code permissions}, of the shell's permissions
     * adopted.
     *
     * <p>The {@code checkMode} specified whether to skip/fail/do nothing if failed to adopt the
     * required permissions.
     */
    public static void runWithShellPermissionIdentity(ThrowingRunnable throwingRunnable,
            @PermissionCheckMode int checkMode, String... permissions) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(permissions);
        if (checkMode != CHECK_MODE_NONE) {
            for (String permission : permissions) {
                boolean adopted = ContextCompat.checkSelfPermission(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        permission) == PackageManager.PERMISSION_GRANTED;
                String msg = "Unable to adopt shell permission: " + permission;
                if (checkMode == CHECK_MODE_ASSUME) {
                    assumeTrue(msg, adopted);
                }
                if (checkMode == CHECK_MODE_ASSERT) {
                    assertWithMessage(msg).that(adopted).isTrue();
                }
            }
        }
        run(throwingRunnable, uiAutomation);
    }

    private static void run(ThrowingRunnable throwingRunnable, UiAutomation uiAutomation) {
        try {
            throwingRunnable.run();
        } catch (AssumptionViolatedException e) {
            // Make sure we allow AssumptionViolatedExceptions through.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
