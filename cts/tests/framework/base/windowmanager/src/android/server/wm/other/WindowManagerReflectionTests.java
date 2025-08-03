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
package android.server.wm.other;


import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Presubmit
public class WindowManagerReflectionTests {

    /** Regression test for b/273906410. */
    @Test
    public void requestAppKeyboardShortcuts_requiresPermission_b273906410() throws Throwable {
        Object wms = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null);

        assertThrows(SecurityException.class, () -> {
            runAndUnwrapTargetException(() -> {
                Class.forName("android.view.IWindowManager")
                        .getMethod("requestAppKeyboardShortcuts",
                                Class.forName("com.android.internal.os.IResultReceiver"),
                                Integer.TYPE)
                        .invoke(wms, null, 0);
            });
        });
    }

    @Test
    public void requestImeKeyboardShortcuts_requiresPermission() throws Throwable {
        Object wms = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .invoke(null);
        try {
            Method method = Class.forName("android.view.IWindowManager")
                    .getMethod("requestImeKeyboardShortcuts",
                            Class.forName("com.android.internal.os.IResultReceiver"),
                            Integer.TYPE);
            assertThrows(SecurityException.class,
                    () -> runAndUnwrapTargetException(() -> method.invoke(wms, null, 0)));
        } catch (NoSuchMethodException e) {
            // Ignores this test if the method is not available.
            assumeNoException(e);
        }
    }

    private void runAndUnwrapTargetException(ThrowingRunnable r) throws Throwable {
        try {
            r.run();
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
