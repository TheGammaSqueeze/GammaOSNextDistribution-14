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

package com.android.bedstead.nene.utils;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class TagsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TAG_1 = "tag1";
    private static final String TAG_2 = "tag2";

    @Before
    public void setUp() {
        Tags.clearTags();
    }

    @Test
    public void addTag_tagIsAdded() {
        Tags.addTag(TAG_1);

        assertThat(Tags.hasTag(TAG_1)).isTrue();
    }

    @Test
    public void addTag_otherTagIsNotAdded() {
        Tags.addTag(TAG_1);

        assertThat(Tags.hasTag(TAG_2)).isFalse();
    }

    @Test
    public void clearTags_tagsAreCleared() {
        Tags.addTag(TAG_1);
        Tags.clearTags();

        assertThat(Tags.hasTag(TAG_1)).isFalse();
    }
}
