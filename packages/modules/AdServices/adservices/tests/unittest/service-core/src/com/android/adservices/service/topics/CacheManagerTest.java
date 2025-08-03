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
package com.android.adservices.service.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.adservices.AdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockRandom;
import com.android.adservices.cobalt.TopicsCobaltLogger;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.CombinedTopic;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.GetTopicsReportedStats;
import com.android.adservices.service.topics.classifier.ClassifierManager;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Unit tests for {@link com.android.adservices.service.topics.CacheManager} */
@SmallTest
public final class CacheManagerTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "CacheManagerTest";

    @SuppressWarnings({"unused"})
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private CacheManager mCacheManager;
    private TopicsDao mTopicsDao;
    private BlockedTopicsManager mBlockedTopicsManager;
    private GlobalBlockedTopicsManager mGlobalBlockedTopicsManager;

    @Mock Flags mMockFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesManager mMockAdServicesManager;
    @Mock AppSearchConsentManager mAppSearchConsentManager;
    @Mock ClassifierManager mClassifierManager;
    @Mock TopicsCobaltLogger mTopicsCobaltLogger;

    @Mock Random mRandom;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopicContributorsContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        mGlobalBlockedTopicsManager =
                new GlobalBlockedTopicsManager(/* globalBlockedTopicIds= */ new HashSet<>());
        mBlockedTopicsManager =
                new BlockedTopicsManager(
                        mTopicsDao,
                        mMockAdServicesManager,
                        mAppSearchConsentManager,
                        Flags.PPAPI_AND_SYSTEM_SERVER,
                        /* enableAppSearchConsent= */ false);
        mCacheManager =
                new CacheManager(
                        mTopicsDao,
                        mMockFlags,
                        mLogger,
                        mBlockedTopicsManager,
                        mGlobalBlockedTopicsManager,
                        mTopicsCobaltLogger);
    }

    @Test
    public void testGetTopics_emptyCache() {
        // The cache is empty when first created.
        List<Topic> topics =
                getOnlyTopics(
                        /* cacheManager */ mCacheManager,
                        /* numberOfLookBackEpochs= */ 3,
                        /* epochId */ 0L,
                        "app",
                        "sdk");

        assertThat(topics).isEmpty();

        // Verify GetTopicsReportedStats created for logging.
        verify(mLogger)
                .logGetTopicsReportedStats(
                        eq(
                                GetTopicsReportedStats.builder()
                                        .setFilteredBlockedTopicCount(0)
                                        .setDuplicateTopicCount(0)
                                        .setTopicIdsCount(0)
                                        .build()));
    }

    @Test
    public void testGetTopics() {
        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);
        ArgumentCaptor<List<Topic>> cobaltArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);
        when(mMockFlags.getTopicsCobaltLoggingEnabled()).thenReturn(true);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsMap1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk5"), topic5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk5"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 has empty cache, the results are always empty.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                ""))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                ""))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEqualTo(Collections.singletonList(topic2));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic4));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic1));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app5",
                                "sdk5"))
                .isEqualTo(Collections.singletonList(topic1));

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                ""))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic1));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic4, topic3));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic5));

        // Verify Cobalt Logger.
        verify(mTopicsCobaltLogger, times(19)).logTopicOccurrences(cobaltArgumentCaptor.capture());
        // Verify 19 Topic lists.
        assertThat(cobaltArgumentCaptor.getAllValues()).hasSize(19);
        // Verify Topics logged on the last call.
        assertThat(cobaltArgumentCaptor.getAllValues().get(18))
                .containsExactly(
                        Topic.create(
                                /* topic= */ 1,
                                /* taxonomyVersion= */ 1,
                                /* modelVersion= */ 1,
                                /* loggedTopic= */ -1),
                        Topic.create(
                                /* topic= */ 5,
                                /* taxonomyVersion= */ 1,
                                /* modelVersion= */ 1,
                                /* loggedTopic= */ -1));

        // GetTopics is invoked 19 times.
        verify(mLogger, times(19)).logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(19);
        // Verify log for the first call.
        assertThat(argument.getAllValues().get(0))
                .isEqualTo(
                        GetTopicsReportedStats.builder()
                                .setFilteredBlockedTopicCount(0)
                                .setDuplicateTopicCount(0)
                                .setTopicIdsCount(0)
                                .build());
    }

    @Test
    public void testGetTopics_someTopicsBlocked() {
        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic5 = Topic.create(/* topic */ 5, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsMap1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsMap1.put(Pair.create("app5", "sdk5"), topic5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk5"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.

        // Mock IPC calls
        TopicParcel topicParcel2 = topic2.convertTopicToTopicParcel();
        TopicParcel topicParcel4 = topic4.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel2, topicParcel4))
                .when(mMockAdServicesManager)
                .retrieveAllBlockedTopics();
        // block topic 2 and 4
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic4);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 has empty cache, the results are always empty.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                ""))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        // Should return topic2, but it's blocked - so emptyList is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                ""))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEmpty();

        // Should return topic4, but it's blocked - so emptyList is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic1));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app5",
                                "sdk5"))
                .isEqualTo(Collections.singletonList(topic1));

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                ""))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1));

        // Should return topic3 and topic4, but topic4 is blocked - so only topic3 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic3));

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app4",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic5));

        // GetTopics is invoked 19 times.
        verify(mLogger, times(19)).logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(19);
        // Verify log for the first call.
        assertThat(argument.getAllValues().get(0))
                .isEqualTo(
                        GetTopicsReportedStats.builder()
                                .setFilteredBlockedTopicCount(0)
                                .setDuplicateTopicCount(0)
                                .setTopicIdsCount(0)
                                .build());
        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    @Test
    public void testGetTopics_globalBlockedTopics() {
        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 2
        // epochs: epochId in {3, 2}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(2);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsMap2.put(Pair.create("app2", "sdk1"), topic3);

        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsMap2.put(Pair.create("app5", "sdk1"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.
        // First enable the flag.
        when(mMockFlags.getGlobalBlockedTopicIds()).thenReturn(ImmutableList.of(1, 2, 6));
        HashSet<Integer> globalBlockedTopicIds =
                Stream.of(1, 2, 6).collect(Collectors.toCollection(HashSet::new));

        mCacheManager =
                new CacheManager(
                        mTopicsDao,
                        mMockFlags,
                        mLogger,
                        mBlockedTopicsManager,
                        new GlobalBlockedTopicsManager(globalBlockedTopicIds),
                        mTopicsCobaltLogger);
        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        // Should return topic2, but it's blocked - so emptyList is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                ""))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEmpty();
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app5",
                                "sdk1"))
                .isEmpty();

        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app2",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic3));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app3",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic4));

        // GetTopics is invoked 6 times.
        int numGetTopicsApiInvoked = 6;
        verify(mLogger, times(numGetTopicsApiInvoked))
                .logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(numGetTopicsApiInvoked);
        // Verify log for the first call.
        assertThat(argument.getAllValues().get(0))
                .isEqualTo(
                        GetTopicsReportedStats.builder()
                                .setFilteredBlockedTopicCount(1)
                                .setDuplicateTopicCount(0)
                                .setTopicIdsCount(0)
                                .build());
    }

    @Test
    public void testGetTopics_verifyLogs() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        GlobalBlockedTopicsManager globalBlockedTopicsManager =
                new GlobalBlockedTopicsManager(/* globalBlockedTopicIds= */ new HashSet<>());
        BlockedTopicsManager blockedTopicsManager =
                new BlockedTopicsManager(
                        topicsDao,
                        mMockAdServicesManager,
                        mAppSearchConsentManager,
                        Flags.PPAPI_AND_SYSTEM_SERVER,
                        /* enableAppSearchConsent= */ false);
        CacheManager cacheManager =
                new CacheManager(
                        topicsDao,
                        mMockFlags,
                        mLogger,
                        blockedTopicsManager,
                        globalBlockedTopicsManager,
                        mTopicsCobaltLogger);

        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(dbHelper.supportsLoggedTopicInReturnedTopicTable()).thenReturn(true);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);
        when(mMockFlags.getTopicsPrivacyBudgetForTopicIdDistribution()).thenReturn(1f);
        when(mMockFlags.getEnableLoggedTopic()).thenReturn(true);
        when(mMockFlags.getEnableDatabaseSchemaVersion8()).thenReturn(true);
        // Always log real topics
        when(mRandom.nextDouble()).thenReturn(1d);

        Topic topic1 = Topic.create(
                /* topic */ 1,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 1);
        Topic topic2 = Topic.create(
                /* topic */ 2,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 2);
        Topic topic3 = Topic.create(
                /* topic */ 3,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 3);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();

        returnedAppSdkTopicsMap3.put(Pair.create("app1", ""), topic3);
        returnedAppSdkTopicsMap3.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap3.put(Pair.create("app1", "sdk2"), topic1);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Mock IPC calls
        TopicParcel topicParcel2 = topic2.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel2)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // block topic 2.
        topicsDao.recordBlockedTopic(topic2);

        cacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topic1, topic2 and topic3, but topic2 is blocked - so only topic1 and
        // topic3 are expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                ""))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic3));
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1));

        // GetTopics is invoked 3 times.
        verify(mLogger, times(3)).logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(3);
        // Should return topic1, topic2 and topic3, but topic2 is blocked - so only topic1 and
        // topic3 are expected.
        assertThat(argument.getAllValues().get(0).getFilteredBlockedTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(0).getDuplicateTopicCount()).isEqualTo(0);
        assertThat(argument.getAllValues().get(0).getTopicIdsCount()).isEqualTo(2);
        assertThat(argument.getAllValues().get(0).getTopicIds())
                .containsExactly(topic3.getTopic(), topic1.getTopic());
        // Should return topic1 and topic2, but topic2 is blocked 2 times - so only topic1 is
        // expected.
        assertThat(argument.getAllValues().get(1).getFilteredBlockedTopicCount()).isEqualTo(2);
        assertThat(argument.getAllValues().get(1).getDuplicateTopicCount()).isEqualTo(0);
        assertThat(argument.getAllValues().get(1).getTopicIdsCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(1).getTopicIds()).containsExactly(topic1.getTopic());
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        // topic1 is deduplicated.
        assertThat(argument.getAllValues().get(2).getFilteredBlockedTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getDuplicateTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getTopicIdsCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getTopicIds()).containsExactly(topic1.getTopic());

        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    @Test
    public void testGetTopics_enableEncryption_success() {
        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);
        when(mMockFlags.getEnableDatabaseSchemaVersion8()).thenReturn(true);
        // Enable encryption
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);

        Topic topic1 =
                Topic.create(
                        /* topic */ 1,
                        /* taxonomyVersion */ 1L,
                        /* modelVersion */ 1L,
                        /* loggedTopic */ 1);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic1.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier1",
                        /* encapsulatedKey */ new byte[] {});
        Topic topic2 =
                Topic.create(
                        /* topic */ 2,
                        /* taxonomyVersion */ 1L,
                        /* modelVersion */ 1L,
                        /* loggedTopic */ 1);
        EncryptedTopic encryptedTopic2 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic2.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier2",
                        /* encapsulatedKey */ new byte[] {});

        // EpochId 3 - Plain text Topics
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
        returnedAppSdkTopicsMap.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap);

        // EpochId 3 - EncryptedTopics
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopics = new HashMap<>();
        returnedEncryptedTopics.put(Pair.create("app1", "sdk1"), encryptedTopic1);
        returnedEncryptedTopics.put(Pair.create("app1", "sdk2"), encryptedTopic2);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(/* epochId */ 3L, returnedEncryptedTopics);

        mCacheManager.loadCache(currentEpochId);

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topics and encryptedTopics for epoch 3.
        List<CombinedTopic> combinedTopics1 =
                mCacheManager.getTopics(
                        /* numberOfLookBackEpochs= */ 3, currentEpochId, "app1", "sdk1");
        assertThat(combinedTopics1.size()).isEqualTo(1);
        assertThat(combinedTopics1.get(0).getTopic()).isEqualTo(topic1);
        assertThat(combinedTopics1.get(0).getEncryptedTopic()).isEqualTo(encryptedTopic1);

        List<CombinedTopic> combinedTopics2 =
                mCacheManager.getTopics(
                        /* numberOfLookBackEpochs= */ 3, currentEpochId, "app1", "sdk2");
        assertThat(combinedTopics2.size()).isEqualTo(1);
        assertThat(combinedTopics2.get(0).getTopic()).isEqualTo(topic2);
        assertThat(combinedTopics2.get(0).getEncryptedTopic()).isEqualTo(encryptedTopic2);
    }

    @Test
    public void testGetTopics_enableEncryption_missingEncryptedTopics() {
        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);
        when(mMockFlags.getEnableDatabaseSchemaVersion8()).thenReturn(true);
        // Enable encryption
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);

        Topic topic1 =
                Topic.create(
                        /* topic */ 1,
                        /* taxonomyVersion */ 1L,
                        /* modelVersion */ 1L,
                        /* loggedTopic */ 1);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic1.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier1",
                        /* encapsulatedKey */ new byte[] {});
        Topic topic2 =
                Topic.create(
                        /* topic */ 2,
                        /* taxonomyVersion */ 1L,
                        /* modelVersion */ 1L,
                        /* loggedTopic */ 1);

        // EpochId 3 - Plain text Topics
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap1);

        // EpochId 3 - EncryptedTopics
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopics = new HashMap<>();
        returnedEncryptedTopics.put(Pair.create("app1", "sdk1"), encryptedTopic1);
        // Don't add entry for ("app1", "sdk2")
        mTopicsDao.persistReturnedAppEncryptedTopicsMap(/* epochId */ 3L, returnedEncryptedTopics);

        // EpochId 2 - Plain text Topics
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // Epoch Id 2 - Missing EncryptedTopics for EpochId - 2

        mCacheManager.loadCache(currentEpochId);

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topics and encryptedTopics for epoch 3.
        List<CombinedTopic> combinedTopics1 =
                mCacheManager.getTopics(
                        /* numberOfLookBackEpochs= */ 3, currentEpochId, "app1", "sdk1");
        assertThat(combinedTopics1.size()).isEqualTo(2);
        // Verify default instance for EncryptedTopic in case of missing map.
        assertThat(combinedTopics1)
                .containsExactly(
                        CombinedTopic.create(topic1, encryptedTopic1),
                        CombinedTopic.create(topic2, EncryptedTopic.getDefaultInstance()));

        List<CombinedTopic> combinedTopics2 =
                mCacheManager.getTopics(
                        /* numberOfLookBackEpochs= */ 3, currentEpochId, "app1", "sdk2");
        assertThat(combinedTopics2.size()).isEqualTo(2);
        // Verify default instance for EncryptedTopic in case of missing specific entries on that
        // epoch.
        assertThat(combinedTopics2)
                .containsExactly(
                        CombinedTopic.create(topic1, EncryptedTopic.getDefaultInstance()),
                        CombinedTopic.create(topic2, EncryptedTopic.getDefaultInstance()));
    }

    @Test
    public void testGetTopics_verifyLogsWithRandomizedResponse() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        TopicsDao topicsDao = new TopicsDao(dbHelper);
        GlobalBlockedTopicsManager globalBlockedTopicsManager =
                new GlobalBlockedTopicsManager(/* globalBlockedTopicIds= */ new HashSet<>());
        BlockedTopicsManager blockedTopicsManager =
                new BlockedTopicsManager(
                        topicsDao,
                        mMockAdServicesManager,
                        mAppSearchConsentManager,
                        Flags.PPAPI_AND_SYSTEM_SERVER,
                        /* enableAppSearchConsent= */ false);
        CacheManager cacheManager =
                new CacheManager(
                        topicsDao,
                        mMockFlags,
                        mLogger,
                        blockedTopicsManager,
                        globalBlockedTopicsManager,
                        mTopicsCobaltLogger);

        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(dbHelper.supportsLoggedTopicInReturnedTopicTable()).thenReturn(true);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);
        when(mMockFlags.getTopicsPrivacyBudgetForTopicIdDistribution()).thenReturn(1f);
        when(mMockFlags.getEnableLoggedTopic()).thenReturn(true);
        when(mMockFlags.getEnableDatabaseSchemaVersion8()).thenReturn(true);
        // Always log randomized topics
        when(mRandom.nextDouble()).thenReturn(0d);
        when(mClassifierManager.getTopicsTaxonomy()).thenReturn(ImmutableList.of(11, 22, 33));
        when(mRandom.nextInt(anyInt())).thenReturn(0, 1, 2, 3);

        Topic topic1 = Topic.create(
                /* topic */ 1,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 11);
        Topic topic2 = Topic.create(
                /* topic */ 2,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 22);
        Topic topic3 = Topic.create(
                /* topic */ 3,
                /* taxonomyVersion */ 1L,
                /* modelVersion */ 1L,
                /* loggedTopic */ 33);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();

        returnedAppSdkTopicsMap2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap2.put(Pair.create("app1", "sdk2"), topic2);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();

        returnedAppSdkTopicsMap3.put(Pair.create("app1", ""), topic3);
        returnedAppSdkTopicsMap3.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap3.put(Pair.create("app1", "sdk2"), topic1);

        topicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Mock IPC calls
        TopicParcel topicParcel2 = topic2.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel2)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // block topic 2.
        topicsDao.recordBlockedTopic(topic2);

        cacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId in [1,..,3] by setting numberOfLookBackEpochs = 3.
        // Should return topic1, topic2 and topic3, but topic2 is blocked - so only topic1 and
        // topic3 are expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                ""))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic3));
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1));
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is expected.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ cacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1));

        // GetTopics is invoked 3 times.
        verify(mLogger, times(3)).logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(3);
        // Should return topic1, topic2 and topic3, but topic2 is blocked - so only topic1 and
        // topic3 are returned, then expected to be replaced with 11 and 13.
        assertThat(argument.getAllValues().get(0).getFilteredBlockedTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(0).getDuplicateTopicCount()).isEqualTo(0);
        assertThat(argument.getAllValues().get(0).getTopicIdsCount()).isEqualTo(2);
        assertThat(argument.getAllValues().get(0).getTopicIds()).containsExactly(11, 33);
        // Should return topic1 and topic2, but topic2 is blocked 2 times - so only topic1 is
        // returned, then expected to be replaced with 11.
        assertThat(argument.getAllValues().get(1).getFilteredBlockedTopicCount()).isEqualTo(2);
        assertThat(argument.getAllValues().get(1).getDuplicateTopicCount()).isEqualTo(0);
        assertThat(argument.getAllValues().get(1).getTopicIdsCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(1).getTopicIds()).containsExactly(11);
        // Should return topic1 and topic2, but topic2 is blocked - so only topic1 is returned,
        // deduplicated, then expected to be replaced with 11.
        assertThat(argument.getAllValues().get(2).getFilteredBlockedTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getDuplicateTopicCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getTopicIdsCount()).isEqualTo(1);
        assertThat(argument.getAllValues().get(2).getTopicIds()).containsExactly(11);

        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    @Test
    public void testGetTopics_shuffling() {
        // In order to test the shuffling behavior, set the test case as looking back to 3 epochs
        // with 2 app/sdk pairs. Therefore, to get Topics for epoch 1 (current is 4) will return 3
        // topics for each app/sdk pair.

        // Mock the random with pre-defined values
        // The way how Collections.shuffle works is from the last element in the list, swap it with
        // a random index. Note index 0 won't be processed as it always swaps with itself
        // So for a list with size 3,
        // if (2,1) is passed into random object, the order will persist
        // if (0,1) is passed in, the order will be reversed. (swap index 2 with 0, rest remains the
        // same)

        // Since returned topics list is generated backwards from epoch 3 to epoch 1, if topics for
        // each epoch is (epoch 1 -> topic 1), (epoch 2 -> topic 2), (epoch 3 -> topic 3)
        // Without shuffling, returned topics list will be (topic3, topic2, topic1) for both app/sdk
        // pair 1
        // and app/sdk/ pair 2
        // With shuffling against above mocked random object, app/sdk pair 1 should remain the
        // order,
        // and app/sdk pair 2 should have returned topics list as (topic1, topic2, topic3)
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            /* first list */
                            2, 1, /* second list */ 0, 1
                        });

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap1);

        // EpochId 3
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic3);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic3);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap1);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Look back to epoch 1
        // As described above, (app1, sdk1) should have returned topics in order (3, 2, 1), while
        // (app2, sdk2) should have returned topics in order (1, 2, 3).
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1",
                                mockRandom))
                .isEqualTo(Arrays.asList(topic3, topic2, topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2",
                                mockRandom))
                .isEqualTo(Arrays.asList(topic1, topic2, topic3));
    }

    @Test
    public void testGetTopics_duplicateTopics() {
        ArgumentCaptor<GetTopicsReportedStats> argument =
                ArgumentCaptor.forClass(GetTopicsReportedStats.class);

        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap1);

        // EpochId 3
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic3);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap1);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 1,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .isEqualTo(Collections.singletonList(topic3));

        // Now look at epochId == [2,3] by setting numberOfLookBackEpochs == 2.
        // Note for (app1, sdk1), both Epoch 2 and Epoch 3 have same topic(topic 2), which should be
        // deduplicated.
        // Same things for (app1, ""), both Epoch 2 and Epoch 3 have same topic(topic 1), which
        // should be deduplicated.
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .isEqualTo(Collections.singletonList(topic2));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 2,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic2, topic3));

        // Now look at epochId == [1,2,3] by setting numberOfLookBackEpochs == 3.
        // Note for (app1, ""), Epoch1, Epoch 2 and Epoch 3 have same topic(topic 1), which should
        // be
        // deduplicated.
        // Note for (app1, sdk1), both Epoch 2 and Epoch 3 have same topic(topic 2), and Epoch 1 has
        // value topic 1, so it should return (topic1, topic2).
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                ""))
                .isEqualTo(Collections.singletonList(topic1));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk1"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic2));
        assertThat(
                        getOnlyTopics(
                                /* cacheManager */ mCacheManager,
                                /* numberOfLookBackEpochs= */ 3,
                                currentEpochId,
                                "app1",
                                "sdk2"))
                .containsExactlyElementsIn(Arrays.asList(topic1, topic2, topic3));

        // GetTopics is invoked 9 times.
        verify(mLogger, times(9)).logGetTopicsReportedStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(9);
        // Verify log for the first call.
        assertThat(argument.getAllValues().get(0))
                .isEqualTo(
                        GetTopicsReportedStats.builder()
                                .setFilteredBlockedTopicCount(0)
                                .setDuplicateTopicCount(0)
                                .setTopicIdsCount(1)
                                .build());
    }

    // Currently SQLException is not thrown. This test needs to be uplifted after SQLException gets
    // handled.
    // TODO(b/230669931): Handle SQLException.
    @Test
    public void testGetTopics_failToLoadFromDb() {
        // Fail to load from DB will have empty cache.
        List<Topic> topics =
                getOnlyTopics(
                        /* cacheManager */ mCacheManager,
                        /* numberOfLookBackEpochs= */ 3, /* epochId */
                        0L,
                        "app",
                        "sdk");

        assertThat(topics).isEmpty();

        // Verify GetTopicsReportedStats created for logging.
        verify(mLogger)
                .logGetTopicsReportedStats(
                        eq(
                                GetTopicsReportedStats.builder()
                                        .setFilteredBlockedTopicCount(0)
                                        .setDuplicateTopicCount(0)
                                        .setTopicIdsCount(0)
                                        .build()));
    }

    @Test
    public void testDump() {
        // Trigger the dump to verify no crash
        PrintWriter printWriter = new PrintWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {

            }

            @Override
            public void flush() throws IOException {

            }

            @Override
            public void close() throws IOException {

            }
        });
        String[] args = new String[]{};
        mCacheManager.dump(printWriter, args);
    }

    @Test
    public void testGetKnownTopicsWithConsent_noBlockedTopics() {
        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);
        Topic topic4 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(mCacheManager.getKnownTopicsWithConsent(currentEpochId))
                .containsExactly(topic1, topic2, topic4);
    }

    @Test
    public void testGetTopicsInEpochRange() {
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);

        String app1 = "app1";
        String app2 = "app2";
        String sdk = "sdk";
        Pair<String, String> app1Sdk = Pair.create(app1, sdk);
        Pair<String, String> app2Sdk = Pair.create(app2, sdk);
        long epoch1 = 1L;
        long epoch2 = 2L;
        long epoch3 = 3L;

        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        // App1 has Topic1 in Epoch 1. Topic2 in Epoch 2, Topic3 in Epoch 3.
        // App2 has Topic4 in Epoch 1, Topic5 in Epoch 2.
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(app1Sdk, topic1));
        mTopicsDao.persistReturnedAppTopicsMap(epoch2, Map.of(app1Sdk, topic2));
        mTopicsDao.persistReturnedAppTopicsMap(epoch3, Map.of(app1Sdk, topic3));
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(app2Sdk, topic4));
        mTopicsDao.persistReturnedAppTopicsMap(epoch2, Map.of(app2Sdk, topic5));

        mCacheManager.loadCache(currentEpochId);

        // App1 should have topic1/2/3 in epoch range [1, 3].
        assertThat(
                        mCacheManager.getTopicsInEpochRange(
                                /* epochLowerBound */ 1, /* epochUpperBound */ 3, app1, sdk))
                .isEqualTo(List.of(topic1, topic2, topic3));

        // App1 should have topic2/3 in epoch range [2, 3].
        assertThat(
                        mCacheManager.getTopicsInEpochRange(
                                /* epochLowerBound */ 2, /* epochUpperBound */ 3, app1, sdk))
                .isEqualTo(List.of(topic2, topic3));

        // App2 should have topic4/5 in epoch range [1, 3].
        assertThat(
                        mCacheManager.getTopicsInEpochRange(
                                /* epochLowerBound */ 1, /* epochUpperBound */ 3, app2, sdk))
                .isEqualTo(List.of(topic4, topic5));

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetKnownTopicsWithConsent_blockSomeTopics() {
        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Mock IPC calls
        TopicParcel topicParcel2 = topic2.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel2)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // Block Topics
        mTopicsDao.recordBlockedTopic(topic2);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(mCacheManager.getKnownTopicsWithConsent(currentEpochId))
                .containsExactly(topic1, topic4);

        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    private static List<Topic> getOnlyTopics(
            CacheManager cacheManager,
            int numberOfLookBackEpochs,
            long currentEpochId,
            String app,
            String sdk) {
        return extractTopics(
                cacheManager.getTopics(numberOfLookBackEpochs, currentEpochId, app, sdk));
    }

    @Test
    public void testGetKnownTopicsWithConsent_blockAllTopics() {
        // Assume the current epochId is 4L, we will load cache for returned topics in the last 3
        // epochs: epochId in {3, 2, 1}.
        long currentEpochId = 4L;
        // Mock Flags to make it independent of configuration
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);

        // EpochId 1
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
        returnedAppSdkTopicsMap1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsMap1.put(Pair.create("app1", "sdk2"), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1L, returnedAppSdkTopicsMap1);

        // EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
        returnedAppSdkTopicsMap2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, returnedAppSdkTopicsMap2);

        // EpochId 3
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap3 = new HashMap<>();
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk1"), topic4);
        returnedAppSdkTopicsMap3.put(Pair.create("app2", "sdk3"), topic4);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 3L, returnedAppSdkTopicsMap3);

        // Mock IPC calls
        TopicParcel topicParcel1 = topic1.convertTopicToTopicParcel();
        TopicParcel topicParcel2 = topic2.convertTopicToTopicParcel();
        TopicParcel topicParcel4 = topic4.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel1, topicParcel2, topicParcel4))
                .when(mMockAdServicesManager)
                .retrieveAllBlockedTopics();
        // Block Topics
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic4);

        mCacheManager.loadCache(currentEpochId);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();

        assertThat(mCacheManager.getKnownTopicsWithConsent(currentEpochId))
                .isEqualTo(Collections.emptyList());

        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    @Test
    public void testClearAllTopicsData() {
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);
        Topic topic6 =
                Topic.create(/* topic */ 6, /* taxonomyVersion= */ 1L, /* modelVersion= */ 1L);

        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk1 = "sdk1";
        final String sdk2 = "sdk2";
        final long epochId1 = 1L;
        final long epochId2 = 2L;

        List<String> tableExclusionList = List.of(TopicsTables.BlockedTopicsContract.TABLE);

        // Persist data into tables
        // Below implementation may insert duplicate data into tables, but the duplicated data
        // should be handled correctly
        for (long epochId : new long[] {epochId1, epochId2}) {
            for (String app : new String[] {app1, app2}) {
                for (String sdk : new String[] {sdk1, sdk2}) {
                    mTopicsDao.recordUsageHistory(epochId, app, sdk);
                    mTopicsDao.recordAppUsageHistory(epochId, app);

                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), topic1));
                    mTopicsDao.persistReturnedAppTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), topic2));
                }
            }
            mTopicsDao.persistAppClassificationTopics(
                    epochId,
                    Map.of(
                            app1,
                            Arrays.asList(topic1, topic2),
                            app2,
                            Arrays.asList(topic1, topic2)));

            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic1, Set.of(app1, sdk1)));
            mTopicsDao.persistCallerCanLearnTopics(epochId, Map.of(topic2, Set.of(app2, sdk2)));

            mTopicsDao.persistTopTopics(
                    epochId, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        }
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);

        // Delete all tables except excluded ones.
        mCacheManager.clearAllTopicsData(tableExclusionList);

        for (long epochId : new long[] {epochId1, epochId2}) {
            assertThat(mTopicsDao.retrieveAppUsageMap(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId)).isEmpty();
            assertThat(mTopicsDao.retrieveTopTopics(epochId)).isEmpty();

            // BlockedTopics Table is not cleared
            assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();
        }
        assertThat(
                        mTopicsDao.retrieveCallerCanLearnTopicsMap(
                                /* current Epoch Id */ 3, /* look back Epochs */ 3))
                .isEmpty();
        assertThat(
                        mTopicsDao.retrieveReturnedTopics(
                                /* current Epoch Id */ 3, /* look back Epochs */ 3))
                .isEmpty();

        mCacheManager.clearAllTopicsData(Collections.emptyList());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        // Also verify no topics will be returned as a second check
        mCacheManager.loadCache(epochId2);
        for (String app : new String[] {app1, app2}) {
            for (String sdk : new String[] {sdk1, sdk2}) {
                assertThat(
                                getOnlyTopics(
                                        /* cacheManager */ mCacheManager,
                                        (int) epochId2 + 1,
                                        epochId2,
                                        app,
                                        sdk))
                        .isEmpty();
            }
        }
    }

    private static List<Topic> getOnlyTopics(
            CacheManager cacheManager,
            int numberOfLookBackEpochs,
            long currentEpochId,
            String app,
            String sdk,
            Random random) {
        return extractTopics(
                cacheManager.getTopics(numberOfLookBackEpochs, currentEpochId, app, sdk, random));
    }

    private static List<Topic> extractTopics(List<CombinedTopic> combinedTopics) {
        return combinedTopics.stream().map(CombinedTopic::getTopic).collect(Collectors.toList());
    }
}
