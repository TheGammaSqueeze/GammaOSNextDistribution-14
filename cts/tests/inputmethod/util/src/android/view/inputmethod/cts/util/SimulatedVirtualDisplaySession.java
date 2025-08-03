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

package android.view.inputmethod.cts.util;

import static android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION;
import static android.provider.Settings.Global.OVERLAY_DISPLAY_DEVICES;
import static android.view.Display.FLAG_TRUSTED;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A session to create/close the simulated overlay display for testing multi-display behavior.
 */
public final class SimulatedVirtualDisplaySession implements AutoCloseable {

    private static final String OVERLAY_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS =
            "should_show_system_decorations";

    private final Display mDisplay;
    private SimulatedVirtualDisplaySession(@NonNull Display display) {
        mDisplay = display;
    }

    public static SimulatedVirtualDisplaySession create(@NonNull Context context,
            int width, int height, int density, int displayImePolicy) {
        final String displaySettingsStr = new StringJoiner(",")
                // Display size/density configuration
                .add(new Size(width, height) + "/" + density)
                // Support system decoration to show IME on the simulated display by default
                .add(OVERLAY_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS)
                .toString();
        putGlobalSetting(OVERLAY_DISPLAY_DEVICES, displaySettingsStr);

        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final WindowManager wm = context.getSystemService(WindowManager.class);
        AtomicReference<Display> simulatedDisplay = new AtomicReference();

        try {
            CommonTestUtils.waitUntil("No simulated display found", 5,
                    () -> {
                        final Display[] displays = dm.getDisplays(DISPLAY_CATEGORY_PRESENTATION);
                        for (Display display : displays) {
                            final boolean isTrusted =
                                    (display.getFlags() & FLAG_TRUSTED) == FLAG_TRUSTED;
                            if (isTrusted && display.getType() == Display.TYPE_OVERLAY) {
                                SystemUtil.runWithShellPermissionIdentity(() -> {
                                    wm.setDisplayImePolicy(display.getDisplayId(),
                                            displayImePolicy);
                                    simulatedDisplay.set(display);
                                });
                                return true;
                            }
                        }
                        return false;
                    });
        } catch (AssertionError | Exception e) {
            deleteGlobalSetting(OVERLAY_DISPLAY_DEVICES);
            throw new RuntimeException("Exception!", e);
        }
        return new SimulatedVirtualDisplaySession(simulatedDisplay.get());
    }

    private static void putGlobalSetting(String key, String value) {
        runShellCommandOrThrow("settings put global " + key + " " + value);
    }

    private static void deleteGlobalSetting(String key) {
        runShellCommandOrThrow("settings delete global " + key);
    }

    public int getDisplayId() {
        return mDisplay.getDisplayId();
    }

    @Override
    public void close() throws Exception {
        deleteGlobalSetting(OVERLAY_DISPLAY_DEVICES);
    }
}
