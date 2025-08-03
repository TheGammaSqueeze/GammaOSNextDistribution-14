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

package android.platform.test.rule;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IPhotosHelper;

import org.junit.runner.Description;

/** This rule verifies pictures are backed up. */
public class PhotoRule extends TestWatcher {

    private static HelperAccessor<IPhotosHelper> sPhotosHelper =
            new HelperAccessor<>(IPhotosHelper.class);

    @Override
    protected void starting(Description description) {
        sPhotosHelper.get().open();
        sPhotosHelper.get().enableBackupMode();
        sPhotosHelper.get().verifyContentBackupFinished();
    }
}
