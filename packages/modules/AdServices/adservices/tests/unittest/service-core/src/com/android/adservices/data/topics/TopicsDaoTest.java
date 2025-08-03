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

package com.android.adservices.data.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_TABLE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Unit tests for {@link com.android.adservices.data.topics.TopicsDao} */
@MediumTest
public final class TopicsDaoTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "TopicsDaoTest";
    // TODO: (b/232807776) Replace below hardcoded taxonomy version and model version
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final String PUBLIC_KEY = "publicKey";
    private static final byte[] ENCAPSULATED_KEY =
            "encapsulatedKey".getBytes(StandardCharsets.UTF_8);

    @SuppressWarnings({"unused"})
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private MockitoSession mStaticMockSession;

    private final DbHelper mDBHelper = DbTestUtil.getDbHelperForTest();
    private final TopicsDao mTopicsDao = new TopicsDao(mDBHelper);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ErrorLogUtil.class)
                        .strictness(Strictness.WARN)
                        .startMocking();

        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedEncryptedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.EpochOriginContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopicContributorsContract.TABLE);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testPersistAndGetAppClassificationTopics() {
        final long epochId1 = 1L;
        final long epochId2 = 2L;

        final String app1 = "app1";
        final String app2 = "app2";

        // Initialize appClassificationTopicsMap and topics
        Map<String, List<Topic>> appClassificationTopicsMap1 = new HashMap<>();
        Map<String, List<Topic>> appClassificationTopicsMap2 = new HashMap<>();
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        // to test multiple topics for one app
        appClassificationTopicsMap1.put(app1, Arrays.asList(topic1, topic2));

        // to test different apps
        appClassificationTopicsMap1.put(app2, Collections.singletonList(topic1));

        // to test different epochs for same app
        appClassificationTopicsMap2.put(app1, Collections.singletonList(topic1));

        mTopicsDao.persistAppClassificationTopics(epochId1, appClassificationTopicsMap1);
        mTopicsDao.persistAppClassificationTopics(epochId2, appClassificationTopicsMap2);

        // MapEpoch1: app1 -> topic1, topic2; app2 -> topic1
        // MapEpoch2: app1 -> topic1
        Map<String, List<Topic>> expectedTopicsMap1 = new HashMap<>();
        Map<String, List<Topic>> expectedTopicsMap2 = new HashMap<>();
        expectedTopicsMap1.put(app1, Arrays.asList(topic1, topic2));
        expectedTopicsMap1.put(app2, Collections.singletonList(topic1));
        expectedTopicsMap2.put(app1, Collections.singletonList(topic1));

        Map<String, List<Topic>> topicsMapFromDb1 =
                mTopicsDao.retrieveAppClassificationTopics(epochId1);
        Map<String, List<Topic>> topicsMapFromDb2 =
                mTopicsDao.retrieveAppClassificationTopics(epochId2);
        assertThat(topicsMapFromDb1).isEqualTo(expectedTopicsMap1);
        assertThat(topicsMapFromDb2).isEqualTo(expectedTopicsMap2);

        // to test non-existed epoch ID
        final long epochId3 = 3L;
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId3)).isEmpty();
    }

    @Test
    public void testPersistAppClassificationTopics_nullTopicsMap() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.persistAppClassificationTopics(
                                /* epochId */ 1L, /* appClassificationMap */ null));
    }

    @Test
    public void testGetTopTopicsAndPersistTopics() {
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);
        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        List<Topic> topicsFromDb = mTopicsDao.retrieveTopTopics(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.

        List<Topic> expectedTopTopics =
                Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);

        assertThat(topicsFromDb).isEqualTo(expectedTopTopics);
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_notFoundEpochId() {
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);
        mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics);

        // Try to fetch TopTopics for a different epoch. It should find anything.
        List<Topic> topicsFromDb = mTopicsDao.retrieveTopTopics(/* epochId = */ 2L);

        assertThat(topicsFromDb).isEmpty();
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_invalidSize() {
        // Not enough 6 topics.
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic6);

        assertThrows(
                IllegalArgumentException.class,
                () -> mTopicsDao.persistTopTopics(/* epochId = */ 1L, topTopics));
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_nullTopTopics() {
        assertThrows(
                NullPointerException.class,
                () -> mTopicsDao.persistTopTopics(/* epochId = */ 1L, /* topTopics = */ null));
    }

    @Test
    public void testGetTopTopicsAndPersistTopics_multiPersistWithSameEpoch() {
        final long epochId = 1L;

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);
        mTopicsDao.persistTopTopics(epochId, topTopics);
        // Persist the TopTopics twice with the same epochID
        mTopicsDao.persistTopTopics(epochId, topTopics);

        List<Topic> expectedTopTopics =
                Arrays.asList(topic1, topic2, topic3, topic4, topic5, topic6);

        // This assertion is to test above persisting calls with same epoch ID didn't throw
        // any exceptions
        assertThat(mTopicsDao.retrieveTopTopics(epochId)).isEqualTo(expectedTopTopics);
        // Also check that no incremental epoch id is saved in DB
        assertThat(mTopicsDao.retrieveTopTopics(epochId + 1)).isEmpty();
    }

    @Test
    public void testRecordUsageHistory() {
        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", /* sdk = */ "");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app3", /* sdk = */ "");

        Map<String, List<String>> expectedAppSdksUsageMap = new HashMap<>();
        expectedAppSdksUsageMap.put("app1", Arrays.asList("", "sdk1", "sdk2"));
        expectedAppSdksUsageMap.put("app2", Arrays.asList("sdk1", "sdk3"));
        expectedAppSdksUsageMap.put("app3", Collections.singletonList(""));

        // Now read back the usages from DB.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.retrieveAppSdksUsageMap(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appSdksUsageMapFromDb).isEqualTo(expectedAppSdksUsageMap);
    }

    @Test
    public void testRecordUsageHistory_notFoundEpochId() {
        // Record some usages.
        // App1 called the Topics API directly and its SDKs also call Topics API.
        // Empty SDK implies the app calls the Topics API directly.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", /* sdk = */ "");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app1", "sdk2");

        // App2 only did not call Topics API directly. Only SDKs of the app2 called the Topics API.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk1");
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app2", "sdk3");

        // App3 called the Topics API directly and has not other SDKs.
        mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app3", /* sdk = */ "");

        // Now read back the usages from DB.
        // Note that we record for epochId = 1L but read from DB for epochId = 2L.
        Map<String, List<String>> appSdksUsageMapFromDb =
                mTopicsDao.retrieveAppSdksUsageMap(/* epochId = */ 2L);

        // The map from DB is empty since we read epochId = 2L.
        assertThat(appSdksUsageMapFromDb).isEmpty();
    }

    @Test
    public void testRecordUsageHistory_nullApp() {
        assertThrows(
                NullPointerException.class,
                () -> mTopicsDao.recordUsageHistory(/* epochId = */ 1L, /* app = */ null, "sdk1"));
    }

    @Test
    public void testRecordUsageHistory_nullSdk() {
        assertThrows(
                NullPointerException.class,
                () -> mTopicsDao.recordUsageHistory(/* epochId = */ 1L, "app", /* sdk = */ null));
    }

    @Test
    public void testRecordUsageHistory_emptyApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mTopicsDao.recordUsageHistory(/* epochId = */ 1L, /* app = */ "", "sdk"));
    }

    @Test
    public void testRecordAndRetrieveAppUsageHistory() {
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 2L, "app1");

        // Epoch 1
        Map<String, Integer> expectedAppUsageMap1 = new HashMap<>();
        expectedAppUsageMap1.put("app1", 1);
        expectedAppUsageMap1.put("app2", 2);
        expectedAppUsageMap1.put("app3", 3);

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb1 =
                mTopicsDao.retrieveAppUsageMap(/* epochId = */ 1L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb1).isEqualTo(expectedAppUsageMap1);

        // Epoch 2
        Map<String, Integer> expectedAppUsageMap2 = new HashMap<>();
        expectedAppUsageMap2.put("app1", 1);

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb2 =
                mTopicsDao.retrieveAppUsageMap(/* epochId = */ 2L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb2).isEqualTo(expectedAppUsageMap2);
    }

    @Test
    public void testRecordAppUsageHistory_notFoundEpochId() {
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app3");

        // Now read back the usages from DB.
        Map<String, Integer> appUsageMapFromDb = mTopicsDao.retrieveAppUsageMap(/* epochId = */ 2L);

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(appUsageMapFromDb).isEmpty();
    }

    @Test
    public void testRecordAppUsageHistory_nullApp() {
        assertThrows(
                NullPointerException.class,
                () -> mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, /* app = */ null));
    }

    @Test
    public void testRecordAppUsageHistory_emptyApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, /* app = */ ""));
    }

    @Test
    public void testRetrieveDistinctAppsFromTables() {
        // App Usages table has app1 and app2 as unique apps
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 2L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);

        // ReturnedTopic tables have app2 and app3 as unique apps
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch1 = new HashMap<>();
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", ""), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk2"), topic2);

        // Setup for EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch2 = new HashMap<>();
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app3", ""), topic1);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app3", "sdk1"), topic2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epoch Id */ 1L, returnedAppSdkTopicsForEpoch1);
        mTopicsDao.persistReturnedAppTopicsMap(/* epoch Id */ 2L, returnedAppSdkTopicsForEpoch2);

        assertThat(
                        (mTopicsDao.retrieveDistinctAppsFromTables(
                                List.of(
                                        TopicsTables.AppUsageHistoryContract.TABLE,
                                        TopicsTables.ReturnedTopicContract.TABLE),
                                List.of(
                                        TopicsTables.AppUsageHistoryContract.APP,
                                        TopicsTables.ReturnedTopicContract.APP))))
                .isEqualTo(Set.of("app1", "app2", "app3"));
    }

    @Test
    public void testRetrieveDistinctAppsFromEncryptedTables() {
        // App Usages table has app1 and app2 as unique apps
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 2L, "app1");
        mTopicsDao.recordAppUsageHistory(/* epochId = */ 1L, "app2");

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        EncryptedTopic encryptedTopic1 = buildEncryptedTopic(topic1);
        EncryptedTopic encryptedTopic2 = buildEncryptedTopic(topic2);

        // ReturnedEncryptedTopic tables have app2 and app3 as unique apps
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsForEpoch1 =
                new HashMap<>();
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", ""), encryptedTopic1);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", "sdk1"), encryptedTopic1);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", "sdk2"), encryptedTopic2);

        // Setup for EpochId 2
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsForEpoch2 =
                new HashMap<>();
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app3", ""), encryptedTopic1);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app3", "sdk1"), encryptedTopic2);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epoch Id */ 1L, returnedEncryptedTopicsForEpoch1);
        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epoch Id */ 2L, returnedEncryptedTopicsForEpoch2);

        assertThat(
                        (mTopicsDao.retrieveDistinctAppsFromTables(
                                List.of(
                                        TopicsTables.AppUsageHistoryContract.TABLE,
                                        TopicsTables.ReturnedEncryptedTopicContract.TABLE),
                                List.of(
                                        TopicsTables.AppUsageHistoryContract.APP,
                                        TopicsTables.ReturnedEncryptedTopicContract.APP))))
                .isEqualTo(Set.of("app1", "app2", "app3"));
    }

    @Test
    public void testRetrieveDistinctAppsFromTables_unequalListSizes() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mTopicsDao.retrieveDistinctAppsFromTables(
                                List.of(
                                        TopicsTables.AppUsageHistoryContract.TABLE,
                                        TopicsTables.ReturnedTopicContract.TABLE),
                                List.of(TopicsTables.AppUsageHistoryContract.APP)));
    }

    @Test
    public void testPersistCallerCanLearnTopics() {
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        // topic1 is a classification topic for app1, so all SDKs in apps1 can learn this topic.
        // In addition, the app1 called the Topics API directly, so it can learn topic1 as well.
        callerCanLearnMap.put(topic1, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2")));

        // topic2 is a classification topic for app1 and app2, so any SDKs in app1 or app2 can learn
        // this topic.
        callerCanLearnMap.put(
                topic2, new HashSet<>(Arrays.asList("app1", "sdk1", "sdk2", "sdk3", "sdk4")));

        // topic3 is a classification topic for app2, so all SDKs in apps2 can learn this topic.
        callerCanLearnMap.put(topic3, new HashSet<>(Arrays.asList("sdk1", "sdk3", "sdk4")));

        // topic4 is a classification topic for app3, so all SDKs in apps3 can learn this topic.
        callerCanLearnMap.put(topic4, new HashSet<>(Arrays.asList("sdk1", "sdk5")));

        // topic5 is a classification topic for app3 and app4, so any SDKs in apps3 or app4 can
        // learn this topic.
        // app4 called Topics API directly, so it can learn this topic.
        callerCanLearnMap.put(topic5, new HashSet<>(Arrays.asList("sdk1", "sdk5", "app4")));

        // app4 called the Topics API directly, so it can learn this topic.
        callerCanLearnMap.put(topic6, new HashSet<>(Collections.singletonList("app4")));

        mTopicsDao.persistCallerCanLearnTopics(/* epochId = */ 3L, callerCanLearnMap);

        Map<Topic, Set<String>> callerCanLearnMapFromDb =
                mTopicsDao.retrieveCallerCanLearnTopicsMap(
                        /* epochId = */ 5L, /* howManyEpochs = */ 3);

        assertThat(callerCanLearnMapFromDb).isEqualTo(callerCanLearnMap);
    }

    @Test
    public void testPersistAndRetrieveReturnedAppTopics_oneEpoch() {
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        // returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopics.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopics.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopics.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopics.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopics.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopics.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopics.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopics.put(Pair.create("app5", "sdk5"), topic5);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 1L, returnedAppSdkTopics);

        // Map<EpochId, Map<Pair<App, Sdk>, Topic>
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(
                        /* epochId = */ 1L, /* numberOfLookBackEpochs = */ 1);

        // There is 1 epoch.
        assertThat(returnedTopicsFromDb).hasSize(1);
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsFromDb = returnedTopicsFromDb.get(1L);

        // And the returnedAppSdkTopics match.
        assertThat(returnedAppSdkTopicsFromDb).isEqualTo(returnedAppSdkTopics);
    }

    @Test
    public void testPersistAndRetrieveReturnedAppEncryptedTopics_oneEpoch() {
        EncryptedTopic encryptedTopic1 =
                buildEncryptedTopic(Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic2 =
                buildEncryptedTopic(Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic3 =
                buildEncryptedTopic(Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic5 =
                buildEncryptedTopic(Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION));

        // returnedAppSdkEncryptedTopics = Map<Pair<App, Sdk>, EncryptedTopic>
        Map<Pair<String, String>, EncryptedTopic> returnedAppSdkEncryptedTopics = new HashMap<>();
        returnedAppSdkEncryptedTopics.put(Pair.create("app1", ""), encryptedTopic1);
        returnedAppSdkEncryptedTopics.put(Pair.create("app1", "sdk1"), encryptedTopic1);
        returnedAppSdkEncryptedTopics.put(Pair.create("app1", "sdk2"), encryptedTopic1);

        returnedAppSdkEncryptedTopics.put(Pair.create("app2", "sdk1"), encryptedTopic2);
        returnedAppSdkEncryptedTopics.put(Pair.create("app2", "sdk3"), encryptedTopic2);
        returnedAppSdkEncryptedTopics.put(Pair.create("app2", "sdk4"), encryptedTopic2);

        returnedAppSdkEncryptedTopics.put(Pair.create("app3", "sdk1"), encryptedTopic3);

        returnedAppSdkEncryptedTopics.put(Pair.create("app5", "sdk1"), encryptedTopic5);
        returnedAppSdkEncryptedTopics.put(Pair.create("app5", "sdk5"), encryptedTopic5);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epochId = */ 1L, returnedAppSdkEncryptedTopics);

        // Map<EpochId, Map<Pair<App, Sdk>, EncryptedTopic>
        Map<Long, Map<Pair<String, String>, EncryptedTopic>> returnedEncryptedTopicsFromDb =
                mTopicsDao.retrieveReturnedEncryptedTopics(
                        /* epochId = */ 1L, /* numberOfLookBackEpochs = */ 1);

        // There is 1 epoch.
        assertThat(returnedEncryptedTopicsFromDb).hasSize(1);
        Map<Pair<String, String>, EncryptedTopic> returnedAppSdkTopicsFromDb =
                returnedEncryptedTopicsFromDb.get(1L);

        // And the returnedAppSdkEncryptedTopics match.
        assertThat(returnedAppSdkTopicsFromDb).isEqualTo(returnedAppSdkEncryptedTopics);
    }

    @Test
    public void testPersistAndRetrieveReturnedAppTopics_multipleEpochs() {
        // We will have 5 topics and set up the returned topics for epoch 3, 2, and 1.
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);

        // Setup for EpochId 1
        // Map<Pair<App, Sdk>, Topic>
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch1 = new HashMap<>();
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", ""), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app1", "sdk2"), topic1);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk3"), topic2);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app2", "sdk4"), topic2);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app3", "sdk1"), topic3);

        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk1"), topic5);
        returnedAppSdkTopicsForEpoch1.put(Pair.create("app5", "sdk5"), topic5);

        // Setup for EpochId 2
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch2 = new HashMap<>();
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", ""), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk1"), topic2);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app1", "sdk2"), topic2);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk1"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk3"), topic3);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app2", "sdk4"), topic3);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app3", "sdk1"), topic4);

        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk1"), topic1);
        returnedAppSdkTopicsForEpoch2.put(Pair.create("app5", "sdk5"), topic1);

        // Setup for EpochId 3
        // epochId == 3 does not have any topics. This could happen if the epoch computation failed
        // or the device was offline and no epoch computation was done.
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsForEpoch3 = new HashMap<>();

        // Now persist the returned topics for 3 epochs
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 1L, returnedAppSdkTopicsForEpoch1);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 2L, returnedAppSdkTopicsForEpoch2);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId = */ 3L, returnedAppSdkTopicsForEpoch3);

        // Now retrieve from DB and verify the result for reach epoch.
        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 is empty, the results are always empty.
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 1);
        assertThat(returnedTopicsFromDb).isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 2);
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(2L, returnedAppSdkTopicsForEpoch2);
        assertThat(returnedTopicsFromDb).isEqualTo(expectedReturnedTopics);

        // Now look at epochId in {3, 2, 1} only by setting numberOfLookBackEpochs = 3.
        returnedTopicsFromDb =
                mTopicsDao.retrieveReturnedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 3);
        expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(1L, returnedAppSdkTopicsForEpoch1);
        expectedReturnedTopics.put(2L, returnedAppSdkTopicsForEpoch2);
        assertThat(returnedTopicsFromDb).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testPersistAndRetrieveReturnedEncryptedAppTopics_multipleEpochs() {
        // We will have 5 encrypted topics and set up the returned topics for epoch 3, 2, and 1.
        EncryptedTopic encryptedTopic1 =
                buildEncryptedTopic(Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic2 =
                buildEncryptedTopic(Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic3 =
                buildEncryptedTopic(Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic4 =
                buildEncryptedTopic(Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION));
        EncryptedTopic encryptedTopic5 =
                buildEncryptedTopic(Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION));

        // Setup for EpochId 1
        // Map<Pair<App, Sdk>, EncryptedTopic>
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsForEpoch1 =
                new HashMap<>();
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app1", ""), encryptedTopic1);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app1", "sdk1"), encryptedTopic1);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app1", "sdk2"), encryptedTopic1);

        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", "sdk1"), encryptedTopic2);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", "sdk3"), encryptedTopic2);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app2", "sdk4"), encryptedTopic2);

        returnedEncryptedTopicsForEpoch1.put(Pair.create("app3", "sdk1"), encryptedTopic3);

        returnedEncryptedTopicsForEpoch1.put(Pair.create("app5", "sdk1"), encryptedTopic5);
        returnedEncryptedTopicsForEpoch1.put(Pair.create("app5", "sdk5"), encryptedTopic5);

        // Setup for EpochId 2
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsForEpoch2 =
                new HashMap<>();
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app1", ""), encryptedTopic2);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app1", "sdk1"), encryptedTopic2);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app1", "sdk2"), encryptedTopic2);

        returnedEncryptedTopicsForEpoch2.put(Pair.create("app2", "sdk1"), encryptedTopic3);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app2", "sdk3"), encryptedTopic3);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app2", "sdk4"), encryptedTopic3);

        returnedEncryptedTopicsForEpoch2.put(Pair.create("app3", "sdk1"), encryptedTopic4);

        returnedEncryptedTopicsForEpoch2.put(Pair.create("app5", "sdk1"), encryptedTopic1);
        returnedEncryptedTopicsForEpoch2.put(Pair.create("app5", "sdk5"), encryptedTopic1);

        // Setup for EpochId 3
        // epochId == 3 does not have any encryptedTopics. This could happen if the epoch
        // computation failed
        // or the device was offline and no epoch computation was done.
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsForEpoch3 =
                new HashMap<>();

        // Now persist the returned encrypted topics for 3 epochs
        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epochId = */ 1L, returnedEncryptedTopicsForEpoch1);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epochId = */ 2L, returnedEncryptedTopicsForEpoch2);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                /* epochId = */ 3L, returnedEncryptedTopicsForEpoch3);

        // Now retrieve from DB and verify the result for reach epoch.
        // Now look at epochId == 3 only by setting numberOfLookBackEpochs == 1.
        // Since the epochId 3 is empty, the results are always empty.
        Map<Long, Map<Pair<String, String>, EncryptedTopic>> returnedEncryptedTopicsFromDb =
                mTopicsDao.retrieveReturnedEncryptedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 1);
        assertThat(returnedEncryptedTopicsFromDb).isEmpty();

        // Now look at epochId in {3, 2} only by setting numberOfLookBackEpochs = 2.
        returnedEncryptedTopicsFromDb =
                mTopicsDao.retrieveReturnedEncryptedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 2);
        Map<Long, Map<Pair<String, String>, EncryptedTopic>> expectedReturnedTopics =
                new HashMap<>();
        expectedReturnedTopics.put(2L, returnedEncryptedTopicsForEpoch2);
        assertThat(returnedEncryptedTopicsFromDb).isEqualTo(expectedReturnedTopics);

        // Now look at epochId in {3, 2, 1} only by setting numberOfLookBackEpochs = 3.
        returnedEncryptedTopicsFromDb =
                mTopicsDao.retrieveReturnedEncryptedTopics(
                        /* epochId = */ 3L, /* numberOfLookBackEpochs = */ 3);
        expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(1L, returnedEncryptedTopicsForEpoch1);
        expectedReturnedTopics.put(2L, returnedEncryptedTopicsForEpoch2);
        assertThat(returnedEncryptedTopicsFromDb).isEqualTo(expectedReturnedTopics);
    }

    @Test
    public void testRecordBlockedTopicAndRetrieveBlockedTopics() {
        final int topicId = 1;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        Topic topicToBlock = Topic.create(topicId, taxonomyVersion, modelVersion);
        mTopicsDao.recordBlockedTopic(topicToBlock);

        List<Topic> blockedTopics = mTopicsDao.retrieveAllBlockedTopics();

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics).hasSize(1);
        assertThat(blockedTopics).containsExactly(topicToBlock);
    }

    @Test
    public void testRecordBlockedTopicAndRemoveBlockedTopic() {
        final int topicId = 1;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        Topic topicToBlock = Topic.create(topicId, taxonomyVersion, modelVersion);
        mTopicsDao.recordBlockedTopic(topicToBlock);

        List<Topic> blockedTopics = mTopicsDao.retrieveAllBlockedTopics();

        // Make sure that what we write to db is equal to what we read from db.
        assertThat(blockedTopics).hasSize(1);
        assertThat(blockedTopics).containsExactly(topicToBlock);

        mTopicsDao.removeBlockedTopic(topicToBlock);
        blockedTopics = mTopicsDao.retrieveAllBlockedTopics();

        // Make sure that blockedTopics table is empty.
        assertThat(blockedTopics).isEmpty();
    }

    @Test
    public void testEraseDataOfOldEpochs() {
        final long epochToDeleteFrom = 1L;
        final long currentEpoch = 4L;

        List<Topic> topTopics_epoch_1 =
                Stream.of(11, 12, 13, 14, 15, 16)
                        .map(epochId -> Topic.create(epochId, TAXONOMY_VERSION, MODEL_VERSION))
                        .collect(Collectors.toList());
        List<Topic> topTopics_epoch_2 =
                Stream.of(21, 22, 23, 24, 25, 26)
                        .map(epochId -> Topic.create(epochId, TAXONOMY_VERSION, MODEL_VERSION))
                        .collect(Collectors.toList());
        List<Topic> topTopics_epoch_3 =
                Stream.of(31, 32, 33, 34, 35, 36)
                        .map(epochId -> Topic.create(epochId, TAXONOMY_VERSION, MODEL_VERSION))
                        .collect(Collectors.toList());
        List<Topic> topTopics_epoch_4 =
                Stream.of(41, 42, 43, 44, 45, 46)
                        .map(epochId -> Topic.create(epochId, TAXONOMY_VERSION, MODEL_VERSION))
                        .collect(Collectors.toList());

        mTopicsDao.persistTopTopics(/* epoch ID */ 4L, topTopics_epoch_4);
        mTopicsDao.persistTopTopics(/* epoch ID */ 3L, topTopics_epoch_3);
        mTopicsDao.persistTopTopics(/* epoch ID */ 2L, topTopics_epoch_2);
        mTopicsDao.persistTopTopics(/* epoch ID */ 1L, topTopics_epoch_1);

        mTopicsDao.deleteDataOfOldEpochs(
                TopicsTables.TopTopicsContract.TABLE,
                TopicsTables.TopTopicsContract.EPOCH_ID,
                epochToDeleteFrom);

        // Epoch 2/3/4 should still exist in DB, while epoch 1 has been erased
        assertThat(mTopicsDao.retrieveTopTopics(currentEpoch)).isEqualTo(topTopics_epoch_4);
        assertThat(mTopicsDao.retrieveTopTopics(currentEpoch - 1)).isEqualTo(topTopics_epoch_3);
        assertThat(mTopicsDao.retrieveTopTopics(currentEpoch - 2)).isEqualTo(topTopics_epoch_2);
        assertThat(mTopicsDao.retrieveTopTopics(currentEpoch - 3)).isEmpty();
    }

    @Test
    public void testDeleteAllTopicsTables_sqlExceptionOnDelete() {
        // Setup required mocks.
        DbHelper mockDbHelper = Mockito.mock(DbHelper.class);
        SQLiteDatabase mockDatabase = Mockito.mock(SQLiteDatabase.class);
        TopicsDao topicsDao = new TopicsDao(mockDbHelper);

        // Do nothing for ErrorLogUtil calls.
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        // Throw exception on deletion of any table.
        when(mockDbHelper.safeGetWritableDatabase()).thenReturn(mockDatabase);
        when(mockDatabase.delete(any(), any(), any())).thenThrow(SQLException.class);

        // Call topicsDao with empty exclusion list.
        topicsDao.deleteAllTopicsTables(/*tablesToExclude*/ List.of());

        // Verify the correct client error log is reported for all 11 tables.
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(Throwable.class),
                                eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_TABLE_FAILURE),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)),
                times(11));
    }

    @Test
    public void testDeleteAllTopicsTables() {
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic4 =
                Topic.create(/* topic */ 4, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic5 =
                Topic.create(/* topic */ 5, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        Topic topic6 =
                Topic.create(/* topic */ 6, /* taxonomyVersion = */ 1L, /* modelVersion = */ 1L);
        EncryptedTopic encryptedTopic1 = buildEncryptedTopic(topic1);
        EncryptedTopic encryptedTopic2 = buildEncryptedTopic(topic2);

        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk1 = "sdk1";
        final String sdk2 = "sdk2";
        final long epochId1 = 1L;
        final long epochId2 = 1L;

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
                    // Insert encrypted topics.
                    mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), encryptedTopic1));
                    mTopicsDao.persistReturnedAppEncryptedTopicsMap(
                            epochId, Map.of(Pair.create(app, sdk), encryptedTopic2));
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
        mTopicsDao.deleteAllTopicsTables(tableExclusionList);

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
                                /* current Epoch ID */ 3, /* look back Epochs */ 3))
                .isEmpty();
        assertThat(
                        mTopicsDao.retrieveReturnedTopics(
                                /* current Epoch ID */ 3, /* look back Epochs */ 3))
                .isEmpty();
        assertThat(
                        mTopicsDao.retrieveReturnedEncryptedTopics(
                                /* current Epoch ID */ 3, /* look back Epochs */ 3))
                .isEmpty();

        mTopicsDao.deleteAllTopicsTables(/* tablesToExclude */ Collections.emptyList());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        mTopicsDao.persistTopicContributors(epochId1, Map.of(topic1.getTopic(), Set.of(app1)));
        mTopicsDao.deleteAllTopicsTables(/* tablesToExclude */ Collections.emptyList());
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1)).isEmpty();
    }

    @Test
    public void testDeleteFromTableByColumn() {
        // Test with AppClassificationTopics Contract
        final long epochId = 1L;

        final String app1 = "app1";
        final String app2 = "app2";
        final String app3 = "app3";
        final String sdk = "sdk";

        // Initialize appClassificationTopicsMap and topics
        Map<String, List<Topic>> appClassificationTopicsMap = new HashMap<>();
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        EncryptedTopic encryptedTopic1 = buildEncryptedTopic(topic1);
        EncryptedTopic encryptedTopic2 = buildEncryptedTopic(topic2);

        appClassificationTopicsMap.put(app1, List.of(topic1, topic2));
        appClassificationTopicsMap.put(app2, List.of(topic1));
        appClassificationTopicsMap.put(app3, List.of(topic1));
        mTopicsDao.persistAppClassificationTopics(epochId, appClassificationTopicsMap);

        // Justify the status before erasing data
        // MapEpoch1: app1 -> topic1, topic2; app2 -> topic1; app3 -> topic1
        Map<String, List<Topic>> expectedTopicsMap = new HashMap<>();
        expectedTopicsMap.put(app1, List.of(topic1, topic2));
        expectedTopicsMap.put(app2, List.of(topic1));
        expectedTopicsMap.put(app3, List.of(topic1));

        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId))
                .isEqualTo(expectedTopicsMap);

        // Erase Data for app1, app2
        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.AppClassificationTopicsContract.TABLE,
                                TopicsTables.AppClassificationTopicsContract.APP)),
                List.of(app1, app2));

        expectedTopicsMap.remove(app1);
        expectedTopicsMap.remove(app2);
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId))
                .isEqualTo(expectedTopicsMap);

        // Verify ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app1, sdk), topic1);
        returnedAppSdkTopics.put(Pair.create(app1, ""), topic2);
        returnedAppSdkTopics.put(Pair.create(app2, sdk), topic1);
        mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopics);

        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                List.of(app1, app2));

        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, /* numberOfLookBackEpochs */ 1))
                .isEmpty();

        // Verify ReturnedEncryptedTopicContract
        Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopics = new HashMap<>();
        returnedEncryptedTopics.put(Pair.create(app1, sdk), encryptedTopic1);
        returnedEncryptedTopics.put(Pair.create(app1, ""), encryptedTopic2);
        returnedEncryptedTopics.put(Pair.create(app2, sdk), encryptedTopic1);
        mTopicsDao.persistReturnedAppEncryptedTopicsMap(epochId, returnedEncryptedTopics);

        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                List.of(app1, app2));

        assertThat(
                        mTopicsDao.retrieveReturnedEncryptedTopics(
                                epochId, /* numberOfLookBackEpochs */ 1))
                .isEmpty();

        // Verify TopicContributorsContract. This is also able to test a non-String value
        long epochId2 = epochId + 1;
        mTopicsDao.persistTopicContributors(epochId, Map.of(topic1.getTopic(), Set.of(app1)));
        mTopicsDao.persistTopicContributors(
                epochId2, Map.of(topic1.getTopic(), Set.of(app1, app2)));

        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.TopicContributorsContract.TABLE,
                                TopicsTables.TopicContributorsContract.EPOCH_ID)),
                List.of(String.valueOf(epochId), String.valueOf(epochId2)));

        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId)).isEmpty();
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId2)).isEmpty();
    }

    @Test
    public void testDeleteFromTableByColumn_nullArguments() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteFromTableByColumn(
                                /* tableNamesAndColumnNamePairs */ null, List.of("app ")));
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteFromTableByColumn(
                                List.of(
                                        Pair.create(
                                                TopicsTables.AppClassificationTopicsContract.TABLE,
                                                TopicsTables.AppClassificationTopicsContract.APP)),
                                /* app */ null));
    }

    @Test
    public void testDeleteEntriesFromTableByColumnWithEqualCondition() {
        final long epochId1 = 1L;
        final long epochId2 = 2L;
        final String app = "app";
        final String sdk = "sdk";

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);

        // Verify ReturnedTopicContract
        mTopicsDao.persistReturnedAppTopicsMap(epochId1, Map.of(Pair.create(app, sdk), topic1));
        mTopicsDao.persistReturnedAppTopicsMap(epochId2, Map.of(Pair.create(app, sdk), topic2));

        // Verify equalConditionValue is double by deleting with epochID = epoch2
        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                List.of(app),
                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                String.valueOf(epochId2),
                /* isStringEqualConditionColumnValue */ false);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, /* numberOfLookBackEpochs */ 1))
                .isEqualTo(Map.of(epochId1, Map.of(Pair.create(app, sdk), topic1)));
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId2, /* numberOfLookBackEpochs */ 1))
                .isEmpty();

        // Verify equalConditionValue is String by deleting sdk
        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                List.of(app),
                TopicsTables.ReturnedTopicContract.SDK,
                sdk,
                /* isStringEqualConditionColumnValue */ true);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, /* numberOfLookBackEpochs */ 1))
                .isEmpty();
    }

    @Test
    public void testDeleteEntriesFromTableByColumnWithEqualCondition_nullArguments() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                                /* tableNamesAndColumnNamePairs */ null,
                                /* Values to Delete */ List.of("app"),
                                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                                /* epoch Id */ String.valueOf(1L),
                                /* isStringEqualConditionColumnValue */ false));
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                                List.of(
                                        Pair.create(
                                                TopicsTables.ReturnedTopicContract.TABLE,
                                                TopicsTables.ReturnedTopicContract.APP)),
                                /* Values to Delete */ null,
                                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                                /* epoch Id */ String.valueOf(1L),
                                /* isStringEqualConditionColumnValue */ false));
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                                List.of(
                                        Pair.create(
                                                TopicsTables.ReturnedTopicContract.TABLE,
                                                TopicsTables.ReturnedTopicContract.APP)),
                                /* Values to Delete */ List.of("app"),
                                /* equalConditionColumnName */ null,
                                /* epoch Id */ String.valueOf(1L),
                                /* isStringEqualConditionColumnValue */ false));
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                                List.of(
                                        Pair.create(
                                                TopicsTables.ReturnedTopicContract.TABLE,
                                                TopicsTables.ReturnedTopicContract.APP)),
                                /* Values to Delete */ List.of("app"),
                                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                                /* equalConditionColumnValue */ null,
                                /* isStringEqualConditionColumnValue */ false));
    }

    @Test
    public void testDeleteEntriesFromTableByColumnWithEqualCondition_nonExistingArguments() {
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        // Persist an entry to Returned Topics Table
        final long epochId1 = 1L;
        final int numberOfLookBackEpochs = 1;
        final String app = "app";
        final String sdk = "sdk";
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        mTopicsDao.persistReturnedAppTopicsMap(epochId1, Map.of(Pair.create(app, sdk), topic1));
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopicsMap =
                Map.of(epochId1, Map.of(Pair.create(app, sdk), topic1));

        // To test passing in a non-existing table name
        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(Pair.create("Some Table", TopicsTables.ReturnedTopicContract.APP)),
                /* Values to Delete */ List.of(app),
                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                /* epoch Id */ String.valueOf(epochId1),
                /* isStringEqualConditionColumnValue */ false);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopicsMap);

        // To test passing in a non-existing Column name
        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(Pair.create(TopicsTables.ReturnedTopicContract.TABLE, "Some Column")),
                /* Values to Delete */ List.of(app),
                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                /* epoch Id */ String.valueOf(epochId1),
                /* isStringEqualConditionColumnValue */ false);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopicsMap);

        // To tests passing in a non-existing Column name for Equal Condition
        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                /* Values to Delete */ List.of(app),
                "Some Column Name",
                /* epoch Id */ String.valueOf(epochId1),
                /* isStringEqualConditionColumnValue */ false);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopicsMap);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(Throwable.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)),
                times(3));
    }

    @Test
    public void testDeleteEntriesFromTableByColumnWithEqualCondition_emptyValuesToDelete() {
        // Persist an entry to Returned Topics Table
        final long epochId1 = 1L;
        final int numberOfLookBackEpochs = 1;
        final String app = "app";
        final String sdk = "sdk";
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        mTopicsDao.persistReturnedAppTopicsMap(epochId1, Map.of(Pair.create(app, sdk), topic1));
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopicsMap =
                Map.of(epochId1, Map.of(Pair.create(app, sdk), topic1));

        mTopicsDao.deleteEntriesFromTableByColumnWithEqualCondition(
                List.of(
                        Pair.create(
                                TopicsTables.ReturnedTopicContract.TABLE,
                                TopicsTables.ReturnedTopicContract.APP)),
                /* Values to Delete */ List.of(),
                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                /* epoch Id */ String.valueOf(epochId1),
                /* isStringEqualConditionColumnValue */ false);
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopicsMap);
    }

    @Test
    public void testDeleteFromTableByColumn_mismatchedTableAndColumnName() {
        // Test with AppClassificationTopics Contract
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final long epochId1 = 1L;
        final String app1 = "app1";

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);

        // Initialize appClassificationTopicsMap and topics
        Map<String, List<Topic>> appClassificationTopicsMap1 = new HashMap<>();

        // Insert epoch 1 with app1
        appClassificationTopicsMap1.put(app1, List.of(topic1));

        mTopicsDao.persistAppClassificationTopics(epochId1, appClassificationTopicsMap1);
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        // Justify the status before erasing data
        // MapEpoch1: app1 -> topic1
        Map<String, List<Topic>> expectedTopicsMap1 = new HashMap<>();
        expectedTopicsMap1.put(app1, List.of(topic1));

        Map<String, List<Topic>> topicsMapFromDb1 =
                mTopicsDao.retrieveAppClassificationTopics(epochId1);
        assertThat(topicsMapFromDb1).isEqualTo(expectedTopicsMap1);

        // To Test a table that doesn't have "app" column
        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.TaxonomyContract.TABLE,
                                TopicsTables.AppClassificationTopicsContract.APP)),
                List.of(app1));
        // Nothing will happen as no satisfied entry to delete
        assertThat(topicsMapFromDb1).isEqualTo(expectedTopicsMap1);

        // To Test table with wrong app column name
        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.AppClassificationTopicsContract.TABLE,
                                "wrong app column name")),
                List.of(app1));
        // Nothing will happen as no satisfied entry to delete
        assertThat(topicsMapFromDb1).isEqualTo(expectedTopicsMap1);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(Throwable.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_DELETE_COLUMN_FAILURE),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)),
                times(2));
    }

    @Test
    public void testDeleteFromTableByColumn_emptyListToDelete() {
        // Test with AppClassificationTopics Contract
        final long epochId1 = 1L;
        final String app = "app";

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);

        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app, List.of(topic1)));

        // Test passing in an empty list
        mTopicsDao.deleteFromTableByColumn(
                List.of(
                        Pair.create(
                                TopicsTables.AppClassificationTopicsContract.TABLE,
                                TopicsTables.AppClassificationTopicsContract.APP)),
                List.of());

        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1))
                .isEqualTo(Map.of(app, List.of(topic1)));
    }

    @Test
    public void testDeleteFromTableByColumn_nonExistingTable() {
        // Test the process doesn't throw with non-existing table
        mTopicsDao.deleteFromTableByColumn(
                List.of(Pair.create("Some Table", "Some Column")), List.of());
    }

    @Test
    public void testPersistAndRetrieveEpochOrigin() {
        final long epochOrigin = 1234567890L;

        mTopicsDao.persistEpochOrigin(epochOrigin);
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(epochOrigin);
    }

    // TODO(b/230669931): Add test to check SQLException when it's enabled in TopicsDao.
    @Test
    public void testPersistAndRetrieveEpochOrigin_multipleInsertion() {
        final long epochOrigin1 = 1L;
        final long epochOrigin2 = 2L;

        mTopicsDao.persistEpochOrigin(epochOrigin1);
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(epochOrigin1);

        // Persist a different origin when there is an existing origin will not change the existing
        // origin.
        mTopicsDao.persistEpochOrigin(epochOrigin2);
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(epochOrigin1);

        // Persist same origin
        mTopicsDao.persistEpochOrigin(epochOrigin1);
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(epochOrigin1);
    }

    @Test
    public void testPersistAndRetrieveEpochOrigin_EmptyTable() {
        // Should return -1 if no origin is persisted
        assertThat(mTopicsDao.retrieveEpochOrigin()).isEqualTo(-1);
    }

    @Test
    public void testPersistAndRetrieveTopicContributors() {
        final long epochId = 1L;
        final int topicId1 = 1;
        final int topicId2 = 2;
        final int topicId3 = 3;
        final String app1 = "app1";
        final String app2 = "app2";

        Map<Integer, Set<String>> topicToContributorsMap =
                Map.of(
                        topicId1, Set.of(app1),
                        topicId2, Set.of(app1, app2));

        mTopicsDao.persistTopicContributors(epochId, topicToContributorsMap);
        // Trying to persist a topic without contributors, which will be ignored.
        mTopicsDao.persistTopicContributors(epochId, Map.of(topicId3, Set.of()));

        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId))
                .isEqualTo(topicToContributorsMap);
    }

    @Test
    public void testPersistAndRetrieveTopicContributors_nullMap() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mTopicsDao.persistTopicContributors(
                                /* epochId */ 1L, /* topicToContributorsMap */ null));
    }

    @Test
    public void testPersistAndRetrieveTopicContributors_emptyMap() {
        final long epochId = 1L;

        mTopicsDao.persistTopicContributors(epochId, Map.of());

        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId)).isEmpty();
    }

    @Test
    public void testDeleteAllEntriesFromTable() {
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);

        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);

        mTopicsDao.deleteAllEntriesFromTable(TopicsTables.BlockedTopicsContract.TABLE);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();
    }

    // Build EncryptedTopic using Topic.toString to distinguish unique EncryptedTopics.
    private EncryptedTopic buildEncryptedTopic(Topic topic) {
        return EncryptedTopic.create(
                /* cipherText */ topic.toString().getBytes(StandardCharsets.UTF_8),
                PUBLIC_KEY,
                ENCAPSULATED_KEY);
    }
}
