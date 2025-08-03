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

package com.android.server.wm;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ActivityInfo;
import android.view.Gravity;

import org.junit.Test;

/**
 * This class contains unit tests for the {@link WindowLayoutWrapper}.
 */
public final class WindowLayoutWrapperTest {
    @Test
    public void create_returnsActivityOptionWrapper() {
        ActivityInfo.WindowLayout layout = new ActivityInfo.WindowLayout(
                /* width= */ 1280, /* widthFraction= */ 0.5f,
                /* height= */ 800, /* heightFraction= */ 1.0f,
                /* gravity= */ Gravity.CENTER, /* minWidth= */ 400, /* minHeight= */ 300);
        WindowLayoutWrapper wrapper = WindowLayoutWrapper.create(layout);
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.toString()).isEqualTo(layout.toString());
    }

    @Test
    public void create_returnsNull() {
        WindowLayoutWrapper wrapper = WindowLayoutWrapper.create(null);
        assertThat(wrapper).isNull();
    }
}
