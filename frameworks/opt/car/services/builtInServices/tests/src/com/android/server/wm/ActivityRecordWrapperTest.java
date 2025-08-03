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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * This class contains unit tests for the {@link ActivityRecordWrapper}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ActivityRecordWrapperTest {
    @Mock
    private ActivityRecord mActivityRecord;

    @Test
    public void create_returnsActivityOptionWrapper() {
        ActivityRecordWrapper wrapper = ActivityRecordWrapper.create(mActivityRecord);
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.getActivityRecord()).isSameInstanceAs(mActivityRecord);
        assertThat(wrapper.toString()).isEqualTo(mActivityRecord.toString());
    }

    @Test
    public void create_returnsNull() {
        ActivityRecordWrapper wrapper = ActivityRecordWrapper.create(null);
        assertThat(wrapper).isNull();
    }
}
