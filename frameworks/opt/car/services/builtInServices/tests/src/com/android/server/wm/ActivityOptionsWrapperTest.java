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

import android.app.ActivityOptions;

import androidx.test.filters.FlakyTest;

import org.junit.Test;

/**
 * This class contains unit tests for the {@link ActivityOptionsWrapper}.
 */
public final class ActivityOptionsWrapperTest {
    @Test
    @FlakyTest(bugId = 284947065)
    public void create_returnsActivityOptionWrapper() {
        ActivityOptions options = ActivityOptions.makeBasic();
        ActivityOptionsWrapper wrapper = ActivityOptionsWrapper.create(options);
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.getOptions()).isSameInstanceAs(options);
        assertThat(wrapper.toString()).isEqualTo(options.toString());
    }

    @Test
    public void create_returnsNull() {
        ActivityOptionsWrapper wrapper = ActivityOptionsWrapper.create(null);
        assertThat(wrapper).isNull();
    }
}
