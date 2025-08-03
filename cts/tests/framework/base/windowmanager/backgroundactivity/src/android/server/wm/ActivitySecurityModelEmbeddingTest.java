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

package android.server.wm;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;

import static org.junit.Assume.assumeNotNull;

import android.content.ComponentName;

import androidx.test.filters.FlakyTest;
import androidx.window.extensions.WindowExtensions;

import org.junit.Before;
import org.junit.Test;

public class ActivitySecurityModelEmbeddingTest extends BackgroundActivityTestBase {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        checkEmbeddingSupport();

        // Set up app a with A and B running embedded
        new ActivityStartVerifier()
                .setupTaskWithEmbeddingActivity(APP_A)
                .startFromEmbeddingActivity(APP_A)
                .activity(APP_B.FOREGROUND_ACTIVITY)
                .executeAndAssertLaunch(/*succeeds*/ true)
                .thenAssertEmbeddingTaskStack(new ComponentName[]{
                        APP_B.FOREGROUND_ACTIVITY
                }, APP_A.FOREGROUND_EMBEDDING_ACTIVITY);
    }

    void checkEmbeddingSupport() {
        assumeExtensionSupportedDevice();
        WindowExtensions windowExtensions = getWindowExtensions();
        assumeNotNull(windowExtensions);
        assumeNotNull(windowExtensions.getActivityEmbeddingComponent());
    }

    @Test
    @FlakyTest(bugId = 291212072)
    public void testEmbeddingLaunchesActivity_launchAllowed() {
        // Base State:
        // | A.FGE (A1) | B.FG (B1) |   --> left | right
        new ActivityStartVerifier()
                .startFromEmbeddingActivity(APP_A)
                .activity(APP_A.FOREGROUND_ACTIVITY)
                // Test - A1 launches A.FG (A2) - succeeds
                // As B allows itself to be embedded by A, A is may close or replace B with another
                // activity (e.g, split-pane views)
                .executeAndAssertLaunch(/*succeeds*/ true)
                // Final State:
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(new ComponentName[]{
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY
                }, APP_A.FOREGROUND_EMBEDDING_ACTIVITY);
    }

    @Test
    @FlakyTest(bugId = 291212072)
    public void testEmbeddedLaunchesActivity_launchAllowedOnlyOnTop() {
        BackgroundActivityLaunchTest.assumeSdkNewerThanUpsideDownCake();
        // Base State:
        // | A.FGE (A1) | B.FG (B1) |   --> left | right
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_B)
                .activity(APP_A.FOREGROUND_ACTIVITY)
                // Test - B1 launches A.FG (A2) - succeeds
                .executeAndAssertLaunch(/*succeeds*/ true)
                // Final State:
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(new ComponentName[]{
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY
                }, APP_A.FOREGROUND_EMBEDDING_ACTIVITY);

        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_B)
                .activity(APP_A.BACKGROUND_ACTIVITY)
                // Test - B1 launches A.BG (A3) - fails
                .executeAndAssertLaunch(/*succeeds*/ false)
                // Final State (no change):
                // |            | A.FG (A2) |   --> left | right
                // | A.FGE (A1) | B.FG (B1) |   --> left | right
                .thenAssertEmbeddingTaskStack(new ComponentName[]{
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY
                }, APP_A.FOREGROUND_EMBEDDING_ACTIVITY);
    }
}
