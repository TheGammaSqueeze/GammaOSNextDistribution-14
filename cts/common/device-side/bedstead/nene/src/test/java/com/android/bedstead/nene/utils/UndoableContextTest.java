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

package com.android.bedstead.nene.utils;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.compatibility.common.util.BlockingCallback.BlockingRunnable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class UndoableContextTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void close_executesCallback() throws Exception {
        BlockingRunnable b = new BlockingRunnable();
        UndoableContext c = new UndoableContext(b);

        c.close();

        // Would throw if not called
        b.await();
    }

}
