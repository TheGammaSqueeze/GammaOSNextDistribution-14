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

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the ActivityEmbedding {@link android.content.pm.PackageManager.Property} tags.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingPropertyTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingPropertyTests extends WindowManagerJetpackTestBase {

    private Activity mTestActivity;
    private PackageManager mPackageManager;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        mTestActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        mPackageManager = mTestActivity.getPackageManager();
    }

    /**
     * {@link WindowManager#PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE} is set as
     * {@code true} in AndroidManifest.
     */
    @Test
    public void testPropertyAllowSystemOverride() throws PackageManager.NameNotFoundException {
        assertTrue(getProperty(WindowManager.PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE));
    }

    private boolean getProperty(String propertyName) throws PackageManager.NameNotFoundException {
        final PackageManager.Property property = mPackageManager.getProperty(propertyName,
                mTestActivity.getApplicationContext().getPackageName());
        if (!property.isBoolean()) {
            throw new IllegalStateException("Property=" + propertyName
                    + " must have a boolean value");
        }
        return property.getBoolean();
    }
}
