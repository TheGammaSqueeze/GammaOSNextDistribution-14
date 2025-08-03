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

import static com.android.adservices.service.topics.EpochManager.PADDED_TOP_TOPICS_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Unit tests for {@link com.android.adservices.service.topics.AppUpdateManager} */
public class AppUpdateManagerTest {
    @SuppressWarnings({"unused"})
    private static final String TAG = "AppInstallationInfoManagerTest";

    private static final String EMPTY_SDK = "";
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final DbHelper mDbHelper = spy(DbTestUtil.getDbHelperForTest());

    private AppUpdateManager mAppUpdateManager;
    private TopicsDao mTopicsDao;

    @Mock PackageManager mMockPackageManager;
    @Mock Flags mMockFlags;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);

        mTopicsDao = new TopicsDao(mDbHelper);
        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopicContributorsContract.TABLE);

        mAppUpdateManager = new AppUpdateManager(mDbHelper, mTopicsDao, new Random(), mMockFlags);
    }

    @Test
    public void testReconcileUninstalledApps() {
        // Both app1 and app2 have usages in database. App 2 won't be current installed app list
        // that is returned by mocked Package Manager, so it'll be regarded as an unhanded installed
        // app.
        final String app1 = "app1";
        final String app2 = "app2";

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;

        mockInstalledApplications(Collections.singletonList(appInfo1));

        // Begin to persist data into database
        // Handle AppClassificationTopicsContract
        final long epochId1 = 1L;
        final int topicId1 = 1;
        final int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId1, TAXONOMY_VERSION, MODEL_VERSION);

        Map<String, List<Topic>> appClassificationTopicsMap1 = new HashMap<>();
        appClassificationTopicsMap1.put(app1, Collections.singletonList(topic1));
        appClassificationTopicsMap1.put(app2, Collections.singletonList(topic1));

        mTopicsDao.persistAppClassificationTopics(epochId1, appClassificationTopicsMap1);
        // Verify AppClassificationContract has both apps
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .containsExactly(app1, app2);

        // Handle UsageHistoryContract
        final String sdk1 = "sdk1";

        mTopicsDao.recordUsageHistory(epochId1, app1, EMPTY_SDK);
        mTopicsDao.recordUsageHistory(epochId1, app1, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app2, EMPTY_SDK);
        mTopicsDao.recordUsageHistory(epochId1, app2, sdk1);

        // Verify UsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .containsExactly(app1, app2);

        // Handle AppUsageHistoryContract
        mTopicsDao.recordAppUsageHistory(epochId1, app1);
        mTopicsDao.recordAppUsageHistory(epochId1, app2);

        // Verify AppUsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet()).containsExactly(app1, app2);

        // Handle CallerCanLearnTopicsContract
        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        callerCanLearnMap.put(topic1, new HashSet<>(Arrays.asList(app1, app2, sdk1)));
        mTopicsDao.persistCallerCanLearnTopics(epochId1, callerCanLearnMap);

        // Verify CallerCanLearnTopicsContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .containsAtLeast(app1, app2);

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app1, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app1, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, sdk1), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId1, returnedAppSdkTopics);
        Map<Pair<String, String>, Topic> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(Pair.create(app1, EMPTY_SDK), topic1);
        expectedReturnedTopics.put(Pair.create(app1, sdk1), topic1);
        expectedReturnedTopics.put(Pair.create(app2, EMPTY_SDK), topic1);
        expectedReturnedTopics.put(Pair.create(app2, sdk1), topic1);

        // Verify ReturnedTopicContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopics);

        // Reconcile uninstalled applications
        mAppUpdateManager.reconcileUninstalledApps(mContext, epochId1);

        verify(mContext).getPackageManager();

        if (SdkLevel.isAtLeastT()) {
            verify(mMockPackageManager).getInstalledApplications(Mockito.any());
        } else {
            verify(mMockPackageManager).getInstalledApplications(anyInt());
        }

        // Each Table should have wiped off all data belonging to app2
        Set<String> setContainsOnlyApp1 = new HashSet<>(Collections.singletonList(app1));
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .doesNotContain(app2);
        // Returned Topics Map contains only App1 paris
        Map<Pair<String, String>, Topic> expectedReturnedTopicsAfterWiping = new HashMap<>();
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, EMPTY_SDK), topic1);
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, sdk1), topic1);
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopicsAfterWiping);
    }

    @Test
    public void testReconcileUninstalledApps_handleTopicsWithoutContributor() {
        // Test Setup:
        // * Both app1 and app2 have usages in database. app2 won't be current installed app list
        //   that is returned by mocked Package Manager, so it'll be regarded as an unhandled
        //   uninstalled app.
        // * In Epoch1, app1 is classified to topic1, topic2. app2 is classified to topic1, topic3.
        //   Both app1 and app2 have topic3 as returned topic as they both call Topics API via sdk.
        // * In Epoch2, both app1 and app2 are classified to topic1, topic3. (verify epoch basis)
        // * In Epoch3, both app2 and app3 are classified to topic1. app4 learns topic1 from sdk and
        //   also returns topic1. After app2 and app4 are uninstalled, topic1 should be removed for
        //   epoch3 and app3 should have no returned topic. (verify consecutive deletion on a topic)
        // * In Epoch4, app2 is uninstalled. topic3 will be removed in Epoch1 as it has app2 as the
        //   only contributor, while topic3 will stay in Epoch2 as app2 contributes to it.
        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk = "sdk";
        final long epoch1 = 1L;
        final long epoch2 = 2L;
        final long epoch4 = 4L;
        final int numberOfLookBackEpochs = 3;

        Topic topic1 = Topic.create(1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(6, TAXONOMY_VERSION, MODEL_VERSION);

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;

        mockInstalledApplications(List.of(appInfo1));

        // Persist to AppClassificationTopics table
        mTopicsDao.persistAppClassificationTopics(
                epoch1, Map.of(app1, List.of(topic1, topic2), app2, List.of(topic1, topic3)));
        mTopicsDao.persistAppClassificationTopics(
                epoch2, Map.of(app1, List.of(topic1, topic3), app2, List.of(topic1, topic3)));

        // Persist to TopTopics table
        mTopicsDao.persistTopTopics(
                epoch1, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        mTopicsDao.persistTopTopics(
                epoch2, List.of(topic1, topic2, topic3, topic4, topic5, topic6));

        // Persist to TopicContributors table
        mTopicsDao.persistTopicContributors(epoch1, Map.of(topic1.getTopic(), Set.of(app1, app2)));
        mTopicsDao.persistTopicContributors(epoch1, Map.of(topic2.getTopic(), Set.of(app1)));
        mTopicsDao.persistTopicContributors(epoch1, Map.of(topic3.getTopic(), Set.of(app2)));
        mTopicsDao.persistTopicContributors(epoch2, Map.of(topic1.getTopic(), Set.of(app1, app2)));
        mTopicsDao.persistTopicContributors(epoch2, Map.of(topic3.getTopic(), Set.of(app1, app2)));

        // Persist to ReturnedTopics table
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(Pair.create(app1, sdk), topic3));
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(Pair.create(app2, sdk), topic3));
        mTopicsDao.persistReturnedAppTopicsMap(epoch2, Map.of(Pair.create(app1, sdk), topic3));
        mTopicsDao.persistReturnedAppTopicsMap(epoch2, Map.of(Pair.create(app2, sdk), topic3));

        // Mock flag value to remove dependency of actual flag value
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Execute reconciliation to handle app2
        mAppUpdateManager.reconcileUninstalledApps(mContext, epoch4);

        // Verify Returned Topics in [1, 3]. app2 should have no returnedTopics as it's uninstalled.
        // app1 only has returned topic at Epoch2 as topic3 is removed from Epoch1.
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopicsMap =
                Map.of(epoch2, Map.of(Pair.create(app1, sdk), topic3));
        assertThat(mTopicsDao.retrieveReturnedTopics(epoch4 - 1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopicsMap);

        // Verify TopicContributors Map is updated: app1 should be removed after the uninstallation.
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epoch1))
                .isEqualTo(
                        Map.of(topic1.getTopic(), Set.of(app1), topic2.getTopic(), Set.of(app1)));
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epoch2))
                .isEqualTo(
                        Map.of(topic1.getTopic(), Set.of(app1), topic3.getTopic(), Set.of(app1)));
    }

    @Test
    public void testReconcileUninstalledApps_contributorDeletionsToSameTopic() {
        // Test Setup:
        // * app1 has usages in database. Both app2 and app3 won't be current installed app list
        //   that is returned by mocked Package Manager, so they'll be regarded as an unhandled
        //   uninstalled apps.
        // * Both app2 and app3 are contributors to topic1 and return topic1. app1 is not the
        //   contributor but also returns topic1, learnt via same SDK.
        final String app1 = "app1";
        final String app2 = "app2";
        final String app3 = "app3";
        final String sdk = "sdk";
        final long epoch1 = 1L;
        final long epoch2 = 2L;
        final int numberOfLookBackEpochs = 3;

        Topic topic1 = Topic.create(1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(6, TAXONOMY_VERSION, MODEL_VERSION);

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;

        mockInstalledApplications(List.of(appInfo1));

        // Persist to AppClassificationTopics table
        mTopicsDao.persistAppClassificationTopics(
                epoch1, Map.of(app2, List.of(topic1), app3, List.of(topic1)));

        // Persist to TopTopics table
        mTopicsDao.persistTopTopics(
                epoch1, List.of(topic1, topic2, topic3, topic4, topic5, topic6));

        // Persist to TopicContributors table
        mTopicsDao.persistTopicContributors(epoch1, Map.of(topic1.getTopic(), Set.of(app2, app3)));

        // Persist to ReturnedTopics table
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(Pair.create(app1, sdk), topic1));
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(Pair.create(app2, sdk), topic1));
        mTopicsDao.persistReturnedAppTopicsMap(epoch1, Map.of(Pair.create(app3, sdk), topic1));

        // Mock flag value to remove dependency of actual flag value
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Execute reconciliation to handle app2 and app3
        mAppUpdateManager.reconcileUninstalledApps(mContext, epoch2);

        // Verify Returned Topics in epoch 1. app2 and app3 are uninstalled, so they definitely
        // don't have a returned topic. As topic1 has no contributors after uninstallations of app2
        // and app3, it's removed from database. Therefore, app1 should have no returned topics as
        // well.
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epoch1)).isEmpty();
        assertThat(mTopicsDao.retrieveReturnedTopics(epoch1, numberOfLookBackEpochs)).isEmpty();
    }

    @Test
    public void testGetUnhandledUninstalledApps() {
        final long epochId = 1L;
        Set<String> currentInstalledApps = Set.of("app1", "app2", "app5");

        // Add app1 and app3 into usage table
        mTopicsDao.recordAppUsageHistory(epochId, "app1");
        mTopicsDao.recordAppUsageHistory(epochId, "app3");

        // Add app2 and app4 into returned topic table
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId,
                Map.of(
                        Pair.create("app2", EMPTY_SDK),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */ 1L, /* model version */ 1L),
                        Pair.create("app4", EMPTY_SDK),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */
                                1L, /* model version */
                                1L)));

        // Unhandled apps = usageTable U returnedTopicTable - currentInstalled
        //                = ((app1, app3) U (app2, app4)) - (app1, app2, app5) = (app3, app4)
        // Note that app5 is installed but doesn't have usage of returned topic, so it won't be
        // handled.
        assertThat(mAppUpdateManager.getUnhandledUninstalledApps(currentInstalledApps))
                .isEqualTo(Set.of("app3", "app4"));
    }

    @Test
    public void testGetUnhandledInstalledApps() {
        final long epochId = 10L;
        Set<String> currentInstalledApps = Set.of("app1", "app2", "app3", "app4");

        // Add app1 and app5 into usage table
        mTopicsDao.recordAppUsageHistory(epochId, "app1");
        mTopicsDao.recordAppUsageHistory(epochId, "app5");

        // Add app2 and app6 into returned topic table
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId,
                Map.of(
                        Pair.create("app2", EMPTY_SDK),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */ 1L, /* model version */ 1L),
                        Pair.create("app6", EMPTY_SDK),
                        Topic.create(
                                /* topic ID */ 1, /* taxonomyVersion */
                                1L, /* model version */
                                1L)));

        // Unhandled apps = currentInstalled - usageTable U returnedTopicTable
        //          = (app1, app2, app3, app4) - ((app1, app5) U (app2, app6)) -  = (app3, app4)
        // Note that app5 and app6 have usages or returned topics, but not currently installed, so
        // they won't be handled.
        assertThat(mAppUpdateManager.getUnhandledInstalledApps(currentInstalledApps))
                .isEqualTo(Set.of("app3", "app4"));
    }

    @Test
    public void testDeleteAppDataFromTableByApps() {
        final String app1 = "app1";
        final String app2 = "app2";
        final String app3 = "app3";

        // Begin to persist data into database.
        // app1, app2 and app3 have usages in database. Derived data of app2 and app3 will be wiped.
        // Therefore, database will only contain app1's data.

        // Handle AppClassificationTopicsContract
        final long epochId1 = 1L;
        final int topicId1 = 1;
        final int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId1, TAXONOMY_VERSION, MODEL_VERSION);

        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app1, List.of(topic1)));
        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app2, List.of(topic1)));
        mTopicsDao.persistAppClassificationTopics(epochId1, Map.of(app3, List.of(topic1)));
        // Verify AppClassificationContract has both apps
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle UsageHistoryContract
        final String sdk1 = "sdk1";

        mTopicsDao.recordUsageHistory(epochId1, app1, EMPTY_SDK);
        mTopicsDao.recordUsageHistory(epochId1, app1, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app2, EMPTY_SDK);
        mTopicsDao.recordUsageHistory(epochId1, app2, sdk1);
        mTopicsDao.recordUsageHistory(epochId1, app3, EMPTY_SDK);
        mTopicsDao.recordUsageHistory(epochId1, app3, sdk1);

        // Verify UsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle AppUsageHistoryContract
        mTopicsDao.recordAppUsageHistory(epochId1, app1);
        mTopicsDao.recordAppUsageHistory(epochId1, app2);
        mTopicsDao.recordAppUsageHistory(epochId1, app3);

        // Verify AppUsageHistoryContract has both apps
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(Set.of(app1, app2, app3));

        // Handle CallerCanLearnTopicsContract
        Map<Topic, Set<String>> callerCanLearnMap = new HashMap<>();
        callerCanLearnMap.put(topic1, new HashSet<>(List.of(app1, app2, app3, sdk1)));
        mTopicsDao.persistCallerCanLearnTopics(epochId1, callerCanLearnMap);

        // Verify CallerCanLearnTopicsContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .isEqualTo(Set.of(app1, app2, app3, sdk1));

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app1, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app1, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app2, sdk1), topic1);
        returnedAppSdkTopics.put(Pair.create(app3, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app3, sdk1), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId1, returnedAppSdkTopics);

        // Verify ReturnedTopicContract has both apps
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(returnedAppSdkTopics);

        // Handle Topics Contributors Table
        Map<Integer, Set<String>> topicContributorsMap = Map.of(topicId1, Set.of(app1, app2, app3));
        mTopicsDao.persistTopicContributors(epochId1, topicContributorsMap);

        // Verify Topics Contributors Table has all apps
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1))
                .isEqualTo(topicContributorsMap);

        // Delete app2's derived data
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of(app2, app3));

        // Each Table should have wiped off all data belonging to app2
        Set<String> setContainsOnlyApp1 = Set.of(app1);
        assertThat(mTopicsDao.retrieveAppClassificationTopics(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppSdksUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(mTopicsDao.retrieveAppUsageMap(epochId1).keySet())
                .isEqualTo(setContainsOnlyApp1);
        assertThat(
                        mTopicsDao
                                .retrieveCallerCanLearnTopicsMap(epochId1, numberOfLookBackEpochs)
                                .get(topic1))
                .isEqualTo(Set.of(app1, sdk1));
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1).get(topicId1))
                .isEqualTo(setContainsOnlyApp1);
        // Returned Topics Map contains only App1 paris
        Map<Pair<String, String>, Topic> expectedReturnedTopicsAfterWiping = new HashMap<>();
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, EMPTY_SDK), topic1);
        expectedReturnedTopicsAfterWiping.put(Pair.create(app1, sdk1), topic1);
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, numberOfLookBackEpochs)
                                .get(epochId1))
                .isEqualTo(expectedReturnedTopicsAfterWiping);
    }

    @Test
    public void testDeleteAppDataFromTableByApps_encryptedTopicsTable() {
        final String app = "app";
        final String sdk = "sdk";
        final long epochId = 1L;
        final int topicId = 1;
        final int numberOfLookBackEpochs = 1;

        Topic topic1 = Topic.create(topicId, TAXONOMY_VERSION, MODEL_VERSION);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        topic1.toString().getBytes(StandardCharsets.UTF_8),
                        "publicKey",
                        "encapsulatedKey".getBytes(StandardCharsets.UTF_8));

        // Handle ReturnedTopicContract
        Map<Pair<String, String>, Topic> returnedAppSdkTopics = new HashMap<>();
        returnedAppSdkTopics.put(Pair.create(app, EMPTY_SDK), topic1);
        returnedAppSdkTopics.put(Pair.create(app, sdk), topic1);

        mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopics);
        // Handle ReturnedEncryptedTopicContract
        Map<Pair<String, String>, EncryptedTopic> encryptedTopics =
                Map.of(Pair.create(app, sdk), encryptedTopic1);

        mTopicsDao.persistReturnedAppEncryptedTopicsMap(epochId, encryptedTopics);

        // Verify ReturnedTopicContract has expected apps
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, numberOfLookBackEpochs).get(epochId))
                .isEqualTo(returnedAppSdkTopics);
        // Verify ReturnedEncryptedTopicContract has expected apps
        assertThat(
                        mTopicsDao
                                .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                                .get(epochId))
                .isEqualTo(encryptedTopics);

        // When db flag is off for version 9.
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(false);
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of(app));
        // Unencrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, numberOfLookBackEpochs)).isEmpty();
        // Encrypted table is not cleared.
        assertThat(
                        mTopicsDao
                                .retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs)
                                .get(epochId))
                .isEqualTo(encryptedTopics);

        // When db flag is on for version 9.
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of(app));
        // Unencrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedTopics(epochId, numberOfLookBackEpochs)).isEmpty();
        // Encrypted table is cleared.
        assertThat(mTopicsDao.retrieveReturnedEncryptedTopics(epochId, numberOfLookBackEpochs))
                .isEmpty();
    }

    @Test
    public void testDeleteAppDataFromTableByApps_nullUninstalledAppName() {
        assertThrows(
                NullPointerException.class,
                () -> mAppUpdateManager.deleteAppDataFromTableByApps(null));
    }

    @Test
    public void testDeleteAppDataFromTableByApps_nonExistingUninstalledAppName() {
        // To test it won't throw by calling the method with non-existing application name
        mAppUpdateManager.deleteAppDataFromTableByApps(List.of("app"));
    }

    @Test
    public void testReconcileInstalledApps() {
        final String app1 = "app1";
        final String app2 = "app2";
        final long currentEpochId = 4L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsPercentageForRandomTopic = 5;

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            0, // Select the first random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);
        // Mock Flags to get an independent result
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        // Mock Package Manager for installed applications
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;
        ApplicationInfo appInfo2 = new ApplicationInfo();
        appInfo2.packageName = app2;

        mockInstalledApplications(List.of(appInfo1, appInfo2));

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // Begin to persist data into database
        // Both app1 and app2 are currently installed apps according to Package Manager, but
        // Only app1 will have usage in database. Therefore, app2 will be regarded as newly
        // installed app.
        mTopicsDao.recordAppUsageHistory(currentEpochId - 1, app1);
        // Unused but to mimic what happens in reality
        mTopicsDao.recordUsageHistory(currentEpochId - 1, app1, "sdk");

        // Persist top topics into database for last 3 epochs
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numOfLookBackEpochs;
                epochId--) {
            mTopicsDao.persistTopTopics(epochId, topTopics);
            // Persist topics to TopicContributors Table avoid being filtered out
            for (Topic topic : topTopics) {
                mTopicsDao.persistTopicContributors(
                        epochId, Map.of(topic.getTopic(), Set.of(app1, app2)));
            }
        }

        // Assign topics to past epochs
        appUpdateManager.reconcileInstalledApps(mContext, currentEpochId);

        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(
                currentEpochId - 1, Map.of(Pair.create(app2, EMPTY_SDK), topic1));
        expectedReturnedTopics.put(
                currentEpochId - 2, Map.of(Pair.create(app2, EMPTY_SDK), topic2));
        expectedReturnedTopics.put(
                currentEpochId - 3, Map.of(Pair.create(app2, EMPTY_SDK), topic6));

        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testSelectAssignedTopicFromTopTopics() {
        final int topicsPercentageForRandomTopic = 5;

        // Test the randomness with pre-defined values
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            0, // Will select a random topic
                            0, // Select the first random topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0 // Select the first regular topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        List<Topic> regularTopics = List.of(topic1, topic2, topic3);
        List<Topic> randomTopics = List.of(topic6);

        // In the first invocation, mockRandom returns a 0 that indicates a random top topic will
        // be returned, and followed by another 0 to select the first(only) random top topic.
        Topic randomTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        regularTopics, randomTopics, topicsPercentageForRandomTopic);
        assertThat(randomTopTopic).isEqualTo(topic6);

        // In the second invocation, mockRandom returns a 5 that indicates a regular top topic will
        // be returned, and following by a 0 to select the first regular top topic.
        Topic regularTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        regularTopics, randomTopics, topicsPercentageForRandomTopic);
        assertThat(regularTopTopic).isEqualTo(topic1);
    }

    @Test
    public void testSelectAssignedTopicFromTopTopics_bothListsAreEmpty() {
        final int topicsPercentageForRandomTopic = 5;

        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, new Random(), mMockFlags);

        List<Topic> regularTopics = List.of();
        List<Topic> randomTopics = List.of();

        Topic selectedTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        regularTopics, randomTopics, topicsPercentageForRandomTopic);
        assertThat(selectedTopic).isNull();
    }

    @Test
    public void testSelectAssignedTopicFromTopTopics_oneListIsEmpty() {
        final int topicsPercentageForRandomTopic = 5;

        // Test the randomness with pre-defined values. Ask it to select the second element for next
        // two random draws.
        MockRandom mockRandom = new MockRandom(new long[] {1, 1});
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);

        // Return a regular topic if the list of random topics is empty.
        List<Topic> regularTopics = List.of(topic1, topic2, topic3);
        List<Topic> randomTopics = List.of();

        Topic regularTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        regularTopics, randomTopics, topicsPercentageForRandomTopic);
        assertThat(regularTopTopic).isEqualTo(topic2);

        // Return a random topic if the list of regular topics is empty.
        regularTopics = List.of();
        randomTopics = List.of(topic1, topic2, topic3);

        Topic randomTopTopic =
                appUpdateManager.selectAssignedTopicFromTopTopics(
                        regularTopics, randomTopics, topicsPercentageForRandomTopic);
        assertThat(randomTopTopic).isEqualTo(topic2);
    }

    @Test
    public void testAssignTopicsToNewlyInstalledApps() {
        final String appName = "app";
        final long currentEpochId = 4L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsPercentageForRandomTopic = 5;

        // As selectAssignedTopicFromTopTopics() randomly assigns a top topic, pass in a Mocked
        // Random object to make the result deterministic.
        //
        // In this test, topic 1, 2, and 6 are supposed to be returned. For each topic, it needs 2
        // random draws: the first is to determine whether to select a random topic, the second is
        // draw the actual topic index.
        MockRandom mockRandom =
                new MockRandom(
                        new long[] {
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            0, // Index of first topic
                            topicsPercentageForRandomTopic, // Will select a regular topic
                            1, // Index of second topic
                            0, // Will select a random topic
                            0, // Select the first random topic
                        });

        // Spy an instance of AppUpdateManager in order to mock selectAssignedTopicFromTopTopics()
        // to avoid randomness.
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);
        // Mock Flags to get an independent result
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // Persist top topics into database for last 3 epochs
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numOfLookBackEpochs;
                epochId--) {
            mTopicsDao.persistTopTopics(epochId, topTopics);

            // Persist topics to TopicContributors Table avoid being filtered out
            for (Topic topic : topTopics) {
                mTopicsDao.persistTopicContributors(
                        epochId, Map.of(topic.getTopic(), Set.of(appName)));
            }
        }

        // Assign topics to past epochs
        appUpdateManager.assignTopicsToNewlyInstalledApps(appName, currentEpochId);

        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        expectedReturnedTopics.put(
                currentEpochId - 1, Map.of(Pair.create(appName, EMPTY_SDK), topic1));
        expectedReturnedTopics.put(
                currentEpochId - 2, Map.of(Pair.create(appName, EMPTY_SDK), topic2));
        expectedReturnedTopics.put(
                currentEpochId - 3, Map.of(Pair.create(appName, EMPTY_SDK), topic6));

        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation() {
        final String app = "app";
        final String sdk = "sdk";
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;

        Pair<String, String> appOnlyCaller = Pair.create(app, EMPTY_SDK);
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic[] topics = {topic1, topic2, topic3};

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Assign returned topics to app-only caller for epochs in [current - 3, current - 1]
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfLookBackEpochs;
                epochId--) {
            Topic topic = topics[(int) (currentEpochId - 1 - epochId)];

            // Assign the returned topic for the app in this epoch.
            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));

            // Make the topic learnable to app-sdk caller for epochs in [current - 3, current - 1].
            // In order to achieve this, persist learnability in [current - 5, current - 3]. This
            // ensures to test the earliest epoch to be learnt from.
            long earliestEpochIdToLearnFrom = epochId - numberOfLookBackEpochs + 1;
            mTopicsDao.persistCallerCanLearnTopics(
                    earliestEpochIdToLearnFrom, Map.of(topic, Set.of(sdk)));
        }

        // Check app-sdk doesn't have returned topic before calling the method
        Map<Long, Map<Pair<String, String>, Topic>> returnedTopicsWithoutAssignment =
                mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs);
        for (Map.Entry<Long, Map<Pair<String, String>, Topic>> entry :
                returnedTopicsWithoutAssignment.entrySet()) {
            assertThat(entry.getValue()).doesNotContainKey(appSdkCaller);
        }

        assertTrue(mAppUpdateManager.assignTopicsToSdkForAppInstallation(app, sdk, currentEpochId));

        // Check app-sdk has been assigned with topic after calling the method
        Map<Long, Map<Pair<String, String>, Topic>> expectedReturnedTopics = new HashMap<>();
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfLookBackEpochs;
                epochId--) {
            Topic topic = topics[(int) (currentEpochId - 1 - epochId)];

            expectedReturnedTopics.put(epochId, Map.of(appSdkCaller, topic, appOnlyCaller, topic));
        }
        assertThat(mTopicsDao.retrieveReturnedTopics(currentEpochId - 1, numberOfLookBackEpochs))
                .isEqualTo(expectedReturnedTopics);

        verify(mMockFlags).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_NonSdk() {
        final String app = "app";
        final String sdk = EMPTY_SDK; // App calls Topics API directly
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;

        Pair<String, String> appOnlyCaller = Pair.create(app, sdk);

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic[] topics = {topic1, topic2, topic3};

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Assign returned topics to app-only caller for epochs in [current - 3, current - 1]
        for (long epochId = currentEpochId - 1;
                epochId >= currentEpochId - numberOfLookBackEpochs;
                epochId--) {
            Topic topic = topics[(int) (currentEpochId - 1 - epochId)];

            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));
            mTopicsDao.persistCallerCanLearnTopics(epochId - 1, Map.of(topic, Set.of(sdk)));
        }

        // No topic will be assigned even though app itself has returned topics
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(app, sdk, currentEpochId));
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_unsatisfiedApp() {
        final String app = "app";
        final String sdk = "sdk";
        final int numberOfLookBackEpochs = 1;

        Pair<String, String> appOnlyCaller = Pair.create(app, EMPTY_SDK);
        Pair<String, String> otherAppOnlyCaller = Pair.create("otherApp", EMPTY_SDK);
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // For Epoch 3, no topic will be assigned to app because epoch in [0,2] doesn't have any
        // returned topics.
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        // Persist returned topics to otherAppOnlyCaller instead of appOnlyCaller
        // Also persist sdk to CallerCanLearnTopics Map to allow sdk to learn the topic.
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(otherAppOnlyCaller, topic));
        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 2L, Map.of(topic, Set.of(sdk)));

        // Epoch 3 won't be assigned topics as appOnlyCaller doesn't have a returned Topic for epoch
        // in [0,2].
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        // Persist returned topics to appOnlyCaller
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(appOnlyCaller, topic));

        assertTrue(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));
        assertThat(mTopicsDao.retrieveReturnedTopics(/* epochId */ 2L, numberOfLookBackEpochs))
                .isEqualTo(
                        Map.of(
                                /* epochId */ 2L,
                                Map.of(
                                        appOnlyCaller,
                                        topic,
                                        appSdkCaller,
                                        topic,
                                        otherAppOnlyCaller,
                                        topic)));
    }

    @Test
    public void testAssignTopicsToSdkForAppInstallation_unsatisfiedSdk() {
        final String app = "app";
        final String sdk = "sdk";
        final String otherSDK = "otherSdk";
        final int numberOfLookBackEpochs = 1;

        Pair<String, String> appOnlyCaller = Pair.create(app, EMPTY_SDK);
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 2L, Map.of(appOnlyCaller, topic));

        // No topic will be assigned as topic is not learned in past epochs
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        // Enable learnability for otherSDK instead of sdk
        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 1L, Map.of(topic, Set.of(otherSDK)));

        // No topic will be assigned as topic is not learned by "sdk" in past epochs
        assertFalse(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));

        // Enable learnability for sdk
        mTopicsDao.persistCallerCanLearnTopics(/* epochId */ 2L, Map.of(topic, Set.of(sdk)));

        // Topic will be assigned as both app and sdk are satisfied
        assertTrue(
                mAppUpdateManager.assignTopicsToSdkForAppInstallation(
                        app, sdk, /* currentEpochId */ 3L));
        assertThat(mTopicsDao.retrieveReturnedTopics(/* epochId */ 2L, numberOfLookBackEpochs))
                .isEqualTo(
                        Map.of(
                                /* epochId */ 2L,
                                Map.of(appOnlyCaller, topic, appSdkCaller, topic)));
    }

    @Test
    public void testConvertUriToAppName() {
        final String samplePackageName = "com.example.measurement.sampleapp";
        final String packageScheme = "package:";

        Uri uri = Uri.parse(packageScheme + samplePackageName);
        assertThat(mAppUpdateManager.convertUriToAppName(uri)).isEqualTo(samplePackageName);
    }

    @Test
    public void testHandleTopTopicsWithoutContributors() {
        final long epochId1 = 1;
        final long epochId2 = 2;
        final String app1 = "app1";
        final String app2 = "app2";
        final String sdk = "sdk";
        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic4 = Topic.create(/* topic */ 4, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic5 = Topic.create(/* topic */ 5, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic6 = Topic.create(/* topic */ 6, TAXONOMY_VERSION, MODEL_VERSION);

        // Both app1 and app2 have usage in the epoch and all 6 topics are top topics
        // Both Topic1 and Topic2 have 2 contributors, app1, and app2. Topic3 has the only
        // contributor app1.
        // Therefore, Topic3 will be removed from ReturnedTopics if app1 is uninstalled.
        mTopicsDao.persistTopTopics(
                epochId1, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        mTopicsDao.persistAppClassificationTopics(
                epochId1,
                Map.of(
                        app1, List.of(topic1, topic2, topic3),
                        app2, List.of(topic1, topic2)));
        mTopicsDao.persistTopicContributors(
                epochId1,
                Map.of(
                        topic1.getTopic(), Set.of(app1, app2),
                        topic2.getTopic(), Set.of(app1, app2),
                        topic3.getTopic(), Set.of(app1)));
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId1,
                Map.of(
                        Pair.create(app1, EMPTY_SDK), topic3,
                        Pair.create(app1, sdk), topic3,
                        Pair.create(app2, EMPTY_SDK), topic2,
                        Pair.create(app2, sdk), topic1));

        // Copy data of Epoch1 to Epoch2 to verify the removal is on epoch basis
        mTopicsDao.persistTopTopics(epochId2, mTopicsDao.retrieveTopTopics(epochId1));
        mTopicsDao.persistAppClassificationTopics(
                epochId2, mTopicsDao.retrieveAppClassificationTopics(epochId1));
        mTopicsDao.persistTopicContributors(
                epochId2, mTopicsDao.retrieveTopicToContributorsMap(epochId1));
        mTopicsDao.persistTopicContributors(
                epochId2, mTopicsDao.retrieveTopicToContributorsMap(epochId1));
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId2,
                mTopicsDao
                        .retrieveReturnedTopics(epochId1, /* numberOfLookBackEpochs */ 1)
                        .get(epochId1));

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(1);
        mAppUpdateManager.handleTopTopicsWithoutContributors(
                /* only handle past epochs */ epochId2, app1);

        // Only observe current epoch per the setup of this test
        // Topic3 should be removed from returnedTopics
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId1, /* numberOfLookBackEpochs */ 1)
                                .get(epochId1))
                .isEqualTo(
                        Map.of(
                                Pair.create(app2, EMPTY_SDK), topic2,
                                Pair.create(app2, sdk), topic1));
        // Epoch2 has no changes.
        assertThat(
                        mTopicsDao
                                .retrieveReturnedTopics(epochId2, /* numberOfLookBackEpochs */ 1)
                                .get(epochId2))
                .isEqualTo(
                        Map.of(
                                Pair.create(app1, EMPTY_SDK), topic3,
                                Pair.create(app1, sdk), topic3,
                                Pair.create(app2, EMPTY_SDK), topic2,
                                Pair.create(app2, sdk), topic1));
    }

    @Test
    public void testFilterRegularTopicsWithoutContributors() {
        final long epochId = 1;
        final String app = "app";

        Topic topic1 = Topic.create(/* topic */ 1, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic2 = Topic.create(/* topic */ 2, TAXONOMY_VERSION, MODEL_VERSION);
        Topic topic3 = Topic.create(/* topic */ 3, TAXONOMY_VERSION, MODEL_VERSION);

        List<Topic> regularTopics = List.of(topic1, topic2, topic3);
        // topic1 has a contributor. topic2 has empty contributor set and topic3 is annotated with
        // PADDED_TOP_TOPICS_STRING. (See EpochManager#PADDED_TOP_TOPICS_STRING for details)
        mTopicsDao.persistTopicContributors(
                epochId,
                Map.of(
                        topic1.getTopic(),
                        Set.of(app),
                        topic2.getTopic(),
                        Set.of(),
                        topic3.getTopic(),
                        Set.of(PADDED_TOP_TOPICS_STRING)));

        // topic2 is filtered out.
        assertThat(mAppUpdateManager.filterRegularTopicsWithoutContributors(regularTopics, epochId))
                .isEqualTo(List.of(topic1, topic3));
    }

    // For test coverage only. The actual e2e logic is tested in TopicsWorkerTest. Methods invoked
    // are tested respectively in this test class.
    @Test
    public void testHandleAppInstallationInRealTime() {
        final String app = "app";
        final long epochId = 1L;

        AppUpdateManager appUpdateManager =
                spy(new AppUpdateManager(mDbHelper, mTopicsDao, new Random(), mMockFlags));

        appUpdateManager.handleAppInstallationInRealTime(Uri.parse(app), epochId);

        verify(appUpdateManager).assignTopicsToNewlyInstalledApps(app, epochId);
    }

    private void mockInstalledApplications(List<ApplicationInfo> applicationInfos) {
        if (SdkLevel.isAtLeastT()) {
            when(mMockPackageManager.getInstalledApplications(
                            any(PackageManager.ApplicationInfoFlags.class)))
                    .thenReturn(applicationInfos);
        } else {
            when(mMockPackageManager.getInstalledApplications(anyInt()))
                    .thenReturn(applicationInfos);
        }
    }
}
