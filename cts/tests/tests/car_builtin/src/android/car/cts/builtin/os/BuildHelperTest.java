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

package android.car.cts.builtin.os;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.builtin.os.BuildHelper;
import android.os.Build;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BuildHelperTest {

    private static final String TAG = BuildHelperTest.class.getSimpleName();
    private static final String BUILD_TYPE_USER = "user";
    private static final String BUILD_TYPE_ENG = "eng";
    private static final String BUILD_TYPE_USER_DEBUG = "userdebug";

    @Test
    public void testBuildTypeCheck() throws Exception {
        switch (Build.TYPE) {
            case BUILD_TYPE_USER:
                assertTrue(BuildHelper.isUserBuild());
                assertFalse(BuildHelper.isUserDebugBuild());
                assertFalse(BuildHelper.isEngBuild());
                break;
            case BUILD_TYPE_USER_DEBUG:
                assertFalse(BuildHelper.isUserBuild());
                assertTrue(BuildHelper.isUserDebugBuild());
                assertFalse(BuildHelper.isEngBuild());
                assertTrue(BuildHelper.isDebuggableBuild());
                break;
            case BUILD_TYPE_ENG:
                assertFalse(BuildHelper.isUserBuild());
                assertFalse(BuildHelper.isUserDebugBuild());
                assertTrue(BuildHelper.isEngBuild());
                assertTrue(BuildHelper.isDebuggableBuild());
                break;
            default:
                throw new IllegalArgumentException("Unknown Build Type: " + Build.TYPE);
        }
    }
}
