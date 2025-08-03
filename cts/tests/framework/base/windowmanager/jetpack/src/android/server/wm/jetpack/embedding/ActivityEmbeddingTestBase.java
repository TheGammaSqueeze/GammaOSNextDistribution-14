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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityTaskManager;
import android.server.wm.UiDeviceUtils;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;

import androidx.test.core.app.ApplicationProvider;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;

import org.junit.After;
import org.junit.Before;

import java.util.Collections;
import java.util.List;

/**
 * Base test class for the {@link androidx.window.extensions} implementation provided on the device
 * (and only if one is available) for the Activity Embedding functionality.
 */
public class ActivityEmbeddingTestBase extends WindowManagerJetpackTestBase {

    protected ActivityEmbeddingComponent mActivityEmbeddingComponent;
    protected TestValueCountConsumer<List<SplitInfo>> mSplitInfoConsumer;
    protected ReportedDisplayMetrics mReportedDisplayMetrics =
            ReportedDisplayMetrics.getDisplayMetrics(Display.DEFAULT_DISPLAY);

    @Override
    @Before
    public void setUp() {
        super.setUp();
        assumeTrue(applicationSupportsMultiWindow());
        assumeExtensionSupportedDevice();
        WindowExtensions windowExtensions = getWindowExtensions();
        assumeNotNull(windowExtensions);
        mActivityEmbeddingComponent = windowExtensions.getActivityEmbeddingComponent();
        assumeNotNull(mActivityEmbeddingComponent);
        mSplitInfoConsumer = new TestValueCountConsumer<>();
        mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);

        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    /** Checks whether the device supports the multi-window feature or not. */
    private static boolean applicationSupportsMultiWindow() {
        return ActivityTaskManager.supportsMultiWindow(ApplicationProvider.getApplicationContext());
    }

    @Override
    @After
    public void tearDown() throws Throwable {
        super.tearDown();
        mReportedDisplayMetrics.restoreDisplayMetrics();
        if (mActivityEmbeddingComponent != null) {
            mActivityEmbeddingComponent.setEmbeddingRules(Collections.emptySet());
        }
    }
}
