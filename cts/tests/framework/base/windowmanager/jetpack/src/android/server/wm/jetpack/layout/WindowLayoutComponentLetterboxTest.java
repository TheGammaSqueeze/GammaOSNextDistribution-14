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

package android.server.wm.jetpack.layout;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assertHasDisplayFeatures;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeHasDisplayFeatures;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getExtensionWindowLayoutInfo;

import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestLetterboxLandscapeActivity;
import android.server.wm.jetpack.utils.TestLetterboxLandscapeSlimActivity;
import android.server.wm.jetpack.utils.TestLetterboxPortraitActivity;
import android.server.wm.jetpack.utils.TestLetterboxPortraitSlimActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;

import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;

/**
 * A class to test features related to letterboxed Activities.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:WindowLayoutComponentLetterboxTest
 */
public class WindowLayoutComponentLetterboxTest extends WindowManagerJetpackTestBase {

    @Rule
    public final WindowExtensionTestRule mWindowExtensionTestRule =
            new WindowExtensionTestRule(WindowLayoutComponent.class);

    /**
     * Tests that a portrait letterboxed {@link android.app.Activity} receives folding features.
     * @throws InterruptedException reading folding features requires async calls.
     */
    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testWindowLayoutComponent_providesWindowLayoutFromLetterboxPortraitActivity()
            throws InterruptedException {
        TestActivity activity = startActivityNewTask(TestActivity.class);
        WindowLayoutInfo fullInfo = getExtensionWindowLayoutInfo(activity);
        assumeHasDisplayFeatures(fullInfo);

        TestLetterboxPortraitActivity letterboxActivity =
                startActivityNewTask(TestLetterboxPortraitActivity.class);
        WindowLayoutInfo letterboxWindowLayoutInfo =
                getExtensionWindowLayoutInfo(letterboxActivity);

        assertHasDisplayFeatures(letterboxWindowLayoutInfo);
    }

    /**
     * Tests that a landscape letterboxed {@link android.app.Activity} receives folding features.
     * @throws InterruptedException reading folding features requires async calls.
     */
    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testWindowLayoutComponent_providesWindowLayoutFromLetterboxLandscapeActivity()
            throws InterruptedException {
        TestActivity activity = startActivityNewTask(TestActivity.class);
        WindowLayoutInfo fullInfo = getExtensionWindowLayoutInfo(activity);
        assumeHasDisplayFeatures(fullInfo);

        TestLetterboxLandscapeActivity letterboxActivity =
                startActivityNewTask(TestLetterboxLandscapeActivity.class);
        WindowLayoutInfo letterboxWindowLayoutInfo =
                getExtensionWindowLayoutInfo(letterboxActivity);

        assertHasDisplayFeatures(letterboxWindowLayoutInfo);
    }

    /**
     * Tests that a slim portrait letterboxed {@link android.app.Activity} receives folding
     * features. A slim portrait letterboxed {@link android.app.Activity} is one that is
     * letterboxed but the orientation matches the display orientation.
     * @throws InterruptedException reading folding features requires async calls.
     */
    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testWindowLayoutComponent_providesWindowLayoutFromLetterboxSlimPortraitActivity()
            throws InterruptedException {
        TestActivity activity = startActivityNewTask(TestActivity.class);
        WindowLayoutInfo fullInfo = getExtensionWindowLayoutInfo(activity);
        assumeHasDisplayFeatures(fullInfo);

        TestLetterboxPortraitSlimActivity letterboxActivity =
                startActivityNewTask(TestLetterboxPortraitSlimActivity.class);
        WindowLayoutInfo letterboxWindowLayoutInfo =
                getExtensionWindowLayoutInfo(letterboxActivity);

        assertHasDisplayFeatures(letterboxWindowLayoutInfo);
    }


    /**
     * Tests that a slim landscape letterboxed {@link android.app.Activity} receives folding
     * features. A slim landscape letterboxed {@link android.app.Activity} is one that is
     * letterboxed but the orientation matches the display orientation.
     * @throws InterruptedException reading folding features requires async calls.
     */
    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testWindowLayoutComponent_providesWindowLayoutFromLetterboxSlimLandscapeActivity()
            throws InterruptedException {
        TestActivity activity = startActivityNewTask(TestActivity.class);
        WindowLayoutInfo fullInfo = getExtensionWindowLayoutInfo(activity);
        assumeHasDisplayFeatures(fullInfo);

        TestLetterboxLandscapeSlimActivity letterboxActivity =
                startActivityNewTask(TestLetterboxLandscapeSlimActivity.class);
        WindowLayoutInfo letterboxWindowLayoutInfo =
                getExtensionWindowLayoutInfo(letterboxActivity);

        assertHasDisplayFeatures(letterboxWindowLayoutInfo);
    }
}
