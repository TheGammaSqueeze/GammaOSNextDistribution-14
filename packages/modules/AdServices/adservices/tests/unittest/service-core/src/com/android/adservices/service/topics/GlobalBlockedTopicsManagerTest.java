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

package com.android.adservices.service.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Unit tests for {@link com.android.adservices.service.topics.GlobalBlockedTopicsManager} */
@SmallTest
public class GlobalBlockedTopicsManagerTest {
    @Mock Flags mMockFlags;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetGlobalBlockedTopicIds() {
        // Blocked topics values are empty.
        HashSet<Integer> globalBlockedTopicIds = new HashSet<>();
        GlobalBlockedTopicsManager globalBlockedTopicsManager =
                new GlobalBlockedTopicsManager(globalBlockedTopicIds);

        assertThat(globalBlockedTopicsManager.getGlobalBlockedTopicIds()).isEmpty();

        // Add global blocked topic ids
        Stream.of(1, 2).forEach(globalBlockedTopicIds::add);
        globalBlockedTopicsManager = new GlobalBlockedTopicsManager(globalBlockedTopicIds);

        assertThat(globalBlockedTopicsManager.getGlobalBlockedTopicIds())
                .isEqualTo(globalBlockedTopicIds);
    }

    @Test
    public void testGetGlobalBlockedTopicIdsFromFlag() {
        // Blocked topics flag values are empty.
        assertThat(GlobalBlockedTopicsManager.getGlobalBlockedTopicIdsFromFlag(mMockFlags))
                .isEmpty();

        // Add global blocked topics to the flag
        ImmutableList<Integer> globalBlockedTopicIdsList = ImmutableList.of(1, 2);
        when(mMockFlags.getGlobalBlockedTopicIds()).thenReturn(globalBlockedTopicIdsList);
        HashSet<Integer> expectedBlockedTopicIds =
                globalBlockedTopicIdsList.stream().collect(Collectors.toCollection(HashSet::new));

        assertThat(GlobalBlockedTopicsManager.getGlobalBlockedTopicIdsFromFlag(mMockFlags))
                .isEqualTo(expectedBlockedTopicIds);
    }
}
