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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.io.IOException;

public final class CarRotaryImeTest extends AbstractCarTestCase {

    private static final ComponentName ROTARY_SERVICE_COMPONENT_NAME =
            ComponentName.unflattenFromString("com.android.car.rotary/.RotaryService");

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AccessibilityManager mAccessibilityManager =
            mContext.getSystemService(AccessibilityManager.class);
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);

    /**
     * Tests that, if a rotary input method is specified via the {@code rotary_input_method} string
     * resource, it's the component name of an existing IME.
     */
    @Test
    public void rotaryInputMethodValidIfSpecified() throws Exception {
        assumeHasRotaryService();

        String rotaryInputMethod = getStringValueFromDumpsys("rotaryInputMethod");
        assumeTrue("Rotary input method not specified, skipping test",
                !rotaryInputMethod.isEmpty());
        assertWithMessage("isValidIme(" + rotaryInputMethod + ")")
                .that(isValidIme(rotaryInputMethod)).isTrue();
    }

    /**
     * The default touch input method must be specified via the {@code default_touch_input_method}
     * string resource, and it must be the component name of an existing IME.
     */
    @Test
    public void defaultTouchInputMethodSpecifiedAndValid() throws Exception {
        assumeHasRotaryService();

        String defaultTouchInputMethod = getStringValueFromDumpsys("defaultTouchInputMethod");

        assertWithMessage("defaultTouchInputMethod").that(defaultTouchInputMethod).isNotEmpty();
        assertWithMessage("isValidIme(" + defaultTouchInputMethod + ")")
                .that(isValidIme(defaultTouchInputMethod)).isTrue();
    }

    // TODO(b/327507413): switch to proto-based dumpsys.
    private static String getStringValueFromDumpsys(String key) throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        String dumpsysOutput = SystemUtil.runShellCommand(uiAutomation,
                "dumpsys activity service " + ROTARY_SERVICE_COMPONENT_NAME.flattenToShortString());
        // dumpsys output contains string like:
        // lastTouchedNode=null
        // rotaryInputMethod=com.android.car.rotaryime/.RotaryIme
        // defaultTouchInputMethod=com.google.android.apps.automotive.inputmethod/
        // .InputMethodService"
        // hunNudgeDirection=FOCUS_UP
        int startIndex = dumpsysOutput.indexOf(key) + key.length() + 1;
        int endIndex = dumpsysOutput.indexOf('\n', startIndex);
        String value = dumpsysOutput.substring(startIndex, endIndex);
        if (!"null".equals(value)) {
            return value;
        }
        return "";
    }

    private void assumeHasRotaryService() {
        assumeTrue("Rotary service not enabled; skipping test",
                mAccessibilityManager.getInstalledAccessibilityServiceList().stream().anyMatch(
                        accessibilityServiceInfo ->
                                ROTARY_SERVICE_COMPONENT_NAME.equals(
                                        accessibilityServiceInfo.getComponentName())));
    }

    /** Returns whether {@code flattenedComponentName} is an installed input method. */
    private boolean isValidIme(@NonNull String flattenedComponentName) {
        ComponentName componentName = ComponentName.unflattenFromString(flattenedComponentName);
        return mInputMethodManager.getInputMethodList().stream()
                .map(inputMethodInfo -> inputMethodInfo.getComponent())
                .anyMatch(inputMethodComponent -> inputMethodComponent.equals(componentName));
    }
}
