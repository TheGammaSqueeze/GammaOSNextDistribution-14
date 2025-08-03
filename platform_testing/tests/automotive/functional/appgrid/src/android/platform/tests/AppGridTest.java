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

package android.platform.tests;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppGridHelper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppGridTest {

    private HelperAccessor<IAutoAppGridHelper> mAppGridHelper;

    public AppGridTest() {
        mAppGridHelper = new HelperAccessor<>(IAutoAppGridHelper.class);
    }


    @Test
    public void testOpen() {
        // Make sure app grid is not open before testing.
        mAppGridHelper.get().exit();
        assertFalse("App Grid is open even after exit.", mAppGridHelper.get().isAppInForeground());
        // Test open.
        mAppGridHelper.get().open();
        assertTrue("App Grid is not open.", mAppGridHelper.get().isAppInForeground());
    }

    @Test
    public void testExit() {
        // Make sure app grid has been opened before testing.
        mAppGridHelper.get().open();
        assertTrue("App Grid is not open.", mAppGridHelper.get().isAppInForeground());
        // Test exit.
        mAppGridHelper.get().exit();
        assertFalse("App Grid is open even after exit.", mAppGridHelper.get().isAppInForeground());
    }

    @Test
    public void testScroll() {
        // Re-enter app grid.
        mAppGridHelper.get().exit();
        mAppGridHelper.get().open();

        mAppGridHelper.get().scrollToBeginning();
        // Test scroll only when there are more than one page in app grid.
        if (!mAppGridHelper.get().isAtEnd()) {
            mAppGridHelper.get().scrollForward();
            assertFalse("Scrolling did not work.", mAppGridHelper.get().isAtBeginning());
            mAppGridHelper.get().scrollBackward();
        }
    }
}
