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

package android.server.wm.display;

import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;
import static android.window.DisplayAreaOrganizer.FEATURE_ROOT;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState.DisplayArea;
import android.server.wm.WindowManagerState.DisplayContent;
import android.view.Display;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:DisplayAreaPolicyTests
 */
@Presubmit
public class DisplayAreaPolicyTests extends ActivityManagerTestBase {

    private List<DisplayContent> mDisplays;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Verify on all displays.
        mWmState.computeState();
        mDisplays = mWmState.getDisplays();
    }

    private boolean isTrustedDisplay(DisplayContent displayContent) {
        return (displayContent.getFlags() & Display.FLAG_TRUSTED) != 0;
    }

    /**
     * DisplayContent should have feature id of FEATURE_ROOT.
     */
    @Test
    public void testDisplayContent() {
        for (DisplayContent displayContent : mDisplays) {
            assertThat(displayContent.getFeatureId()).isEqualTo(FEATURE_ROOT);
        }
    }

    /**
     * There must be exactly one default TaskDisplayArea per display.
     */
    @Test
    public void testDefaultTaskDisplayArea() {
        for (DisplayContent displayContent : mDisplays) {
            final List<DisplayArea> taskDisplayAreas = displayContent.getAllTaskDisplayAreas();
            final List<DisplayArea> defaultTda = new ArrayList<>();
            for (DisplayArea taskDisplayArea : taskDisplayAreas) {
                if (taskDisplayArea.getFeatureId() == FEATURE_DEFAULT_TASK_CONTAINER) {
                    defaultTda.add(taskDisplayArea);
                }
            }
            assertThat(defaultTda).hasSize(1);
        }
    }

    /**
     * TaskDisplayArea and RootDisplayArea must have unique feature ids per display.
     */
    @Test
    public void testDisplayAreaUniqueId() {
        for (DisplayContent displayContent : mDisplays) {
            // TaskDisplayArea and RootDisplayArea must have unique feature ids.
            final Set<Integer> uniqueIds = new HashSet<>();
            uniqueIds.add(displayContent.getFeatureId());
            // Other DisplayAreas can have non-unique feature ids, but shouldn't be the same with
            // any TaskDisplayArea or RootDisplayArea.
            final Set<Integer> nonUniqueIds = new HashSet<>();
            final List<DisplayArea> displayAreas = displayContent.getAllChildDisplayAreas();

            for (DisplayArea displayArea : displayAreas) {
                final int featureId = displayArea.getFeatureId();
                if (displayArea.isRootDisplayArea() || displayArea.isTaskDisplayArea()) {
                    assertThat(uniqueIds.add(featureId)).isTrue();
                    assertThat(nonUniqueIds).doesNotContain(featureId);
                } else {
                    nonUniqueIds.add(featureId);
                    assertThat(uniqueIds).doesNotContain(featureId);
                }
            }
        }
    }
}
