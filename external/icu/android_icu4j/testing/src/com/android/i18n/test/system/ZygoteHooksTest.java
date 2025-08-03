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

package com.android.i18n.test.system;

import android.icu.testsharding.MainTestShard;

import com.android.i18n.system.ZygoteHooks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class ZygoteHooksTest {

    /**
     * Invoke the {@link ZygoteHooks} preload methods in tests to add API coverage. Invoking methods
     * shouldn't have any visible changes, but preloading some objects into the internal ICU4J
     * caches.
     */
    @Test
    public void testPreload() {
        ZygoteHooks.onBeginPreload();
        ZygoteHooks.onEndPreload();
    }

}
