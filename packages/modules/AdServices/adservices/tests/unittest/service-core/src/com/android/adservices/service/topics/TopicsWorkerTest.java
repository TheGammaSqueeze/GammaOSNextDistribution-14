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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.topics.GetTopicsResult;
import android.app.adservices.AdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockRandom;
import com.android.adservices.cobalt.TopicsCobaltLogger;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/** Unit test for {@link com.android.adservices.service.topics.TopicsWorker}. */
public class TopicsWorkerTest {
    // Spy the Context to test app reconciliation
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final DbHelper mDbHelper = spy(DbTestUtil.getDbHelperForTest());

    private TopicsWorker mTopicsWorker;
    private TopicsDao mTopicsDao;
    private CacheManager mCacheManager;
    private BlockedTopicsManager mBlockedTopicsManager;
    // Spy DbHelper to mock supportsTopContributorsTable feature.

    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesManager mMockAdServicesManager;
    @Mock AppSearchConsentManager mAppSearchConsentManager;
    @Mock TopicsCobaltLogger mTopicsCobaltLogger;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Clean DB before each test
        DbTestUtil.deleteTable(TopicsTables.TaxonomyContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopicContributorsContract.TABLE);

        mTopicsDao = new TopicsDao(mDbHelper);
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
                        new GlobalBlockedTopicsManager(
                                /* globalBlockedTopicIds= */ new HashSet<>()),
                        mTopicsCobaltLogger);
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, new Random(), mMockFlags);

        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);
    }

    @Test
    public void testGetTopics() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_enableEncryption_disablePlaintextTopics() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic1.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier1",
                        /* encapsulatedKey */ "encapsulatedKey1".getBytes(StandardCharsets.UTF_8));
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        EncryptedTopic encryptedTopic2 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic2.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier2",
                        /* encapsulatedKey */ "encapsulatedKey2".getBytes(StandardCharsets.UTF_8));
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        EncryptedTopic encryptedTopic3 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic3.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier3",
                        /* encapsulatedKey */ "encapsulatedKey3".getBytes(StandardCharsets.UTF_8));
        Topic[] topics = {topic1, topic2, topic3};
        EncryptedTopic[] encryptedTopics = {encryptedTopic1, encryptedTopic2, encryptedTopic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);

            EncryptedTopic currentEncryptedTopic =
                    encryptedTopics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsMap = new HashMap<>();
            returnedEncryptedTopicsMap.put(appSdkKey, currentEncryptedTopic);
            mTopicsDao.persistReturnedAppEncryptedTopicsMap(numEpoch, returnedEncryptedTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsDisablePlaintextResponse()).thenReturn(true);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(List.of())
                        .setModelVersions(List.of())
                        .setTopics(List.of())
                        .setEncryptedTopics(
                                Arrays.asList(
                                        encryptedTopic1.getEncryptedTopic(),
                                        encryptedTopic2.getEncryptedTopic(),
                                        encryptedTopic3.getEncryptedTopic()))
                        .setEncryptionKeys(
                                Arrays.asList(
                                        encryptedTopic1.getKeyIdentifier(),
                                        encryptedTopic2.getKeyIdentifier(),
                                        encryptedTopic3.getKeyIdentifier()))
                        .setEncapsulatedKeys(
                                Arrays.asList(
                                        encryptedTopic1.getEncapsulatedKey(),
                                        encryptedTopic2.getEncapsulatedKey(),
                                        encryptedTopic3.getEncapsulatedKey()))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
        Correspondence<byte[], byte[]> equalByteArray =
                Correspondence.from(Arrays::equals, "Equal byte array check");
        assertThat(getTopicsResult.getEncryptedTopics())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptedTopics());
        assertThat(getTopicsResult.getEncryptionKeys())
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptionKeys());
        assertThat(getTopicsResult.getEncapsulatedKeys())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncapsulatedKeys());

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_enableEncryption_featureOn() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic1.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier1",
                        /* encapsulatedKey */ "encapsulatedKey1".getBytes(StandardCharsets.UTF_8));
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        EncryptedTopic encryptedTopic2 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic2.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier2",
                        /* encapsulatedKey */ "encapsulatedKey2".getBytes(StandardCharsets.UTF_8));
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        EncryptedTopic encryptedTopic3 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic3.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier3",
                        /* encapsulatedKey */ "encapsulatedKey3".getBytes(StandardCharsets.UTF_8));
        Topic[] topics = {topic1, topic2, topic3};
        EncryptedTopic[] encryptedTopics = {encryptedTopic1, encryptedTopic2, encryptedTopic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);

            EncryptedTopic currentEncryptedTopic =
                    encryptedTopics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsMap = new HashMap<>();
            returnedEncryptedTopicsMap.put(appSdkKey, currentEncryptedTopic);
            mTopicsDao.persistReturnedAppEncryptedTopicsMap(numEpoch, returnedEncryptedTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .setEncryptedTopics(
                                Arrays.asList(
                                        encryptedTopic1.getEncryptedTopic(),
                                        encryptedTopic2.getEncryptedTopic(),
                                        encryptedTopic3.getEncryptedTopic()))
                        .setEncryptionKeys(
                                Arrays.asList(
                                        encryptedTopic1.getKeyIdentifier(),
                                        encryptedTopic2.getKeyIdentifier(),
                                        encryptedTopic3.getKeyIdentifier()))
                        .setEncapsulatedKeys(
                                Arrays.asList(
                                        encryptedTopic1.getEncapsulatedKey(),
                                        encryptedTopic2.getEncapsulatedKey(),
                                        encryptedTopic3.getEncapsulatedKey()))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
        Correspondence<byte[], byte[]> equalByteArray =
                Correspondence.from(Arrays::equals, "Equal byte array check");
        assertThat(getTopicsResult.getEncryptedTopics())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptedTopics());
        assertThat(getTopicsResult.getEncryptionKeys())
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptionKeys());
        assertThat(getTopicsResult.getEncapsulatedKeys())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncapsulatedKeys());

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_enableEncryption_featureOff() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        EncryptedTopic encryptedTopic1 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic1.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier1",
                        /* encapsulatedKey */ "encapsulatedKey1".getBytes(StandardCharsets.UTF_8));
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        EncryptedTopic encryptedTopic2 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic2.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier2",
                        /* encapsulatedKey */ "encapsulatedKey2".getBytes(StandardCharsets.UTF_8));
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        EncryptedTopic encryptedTopic3 =
                EncryptedTopic.create(
                        /* encryptedTopic */ topic3.toString().getBytes(StandardCharsets.UTF_8),
                        /* keyIdentifier */ "keyIdentifier3",
                        /* encapsulatedKey */ "encapsulatedKey3".getBytes(StandardCharsets.UTF_8));
        Topic[] topics = {topic1, topic2, topic3};
        EncryptedTopic[] encryptedTopics = {encryptedTopic1, encryptedTopic2, encryptedTopic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);

            EncryptedTopic currentEncryptedTopic =
                    encryptedTopics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, EncryptedTopic> returnedEncryptedTopicsMap = new HashMap<>();
            returnedEncryptedTopicsMap.put(appSdkKey, currentEncryptedTopic);
            mTopicsDao.persistReturnedAppEncryptedTopicsMap(numEpoch, returnedEncryptedTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Feature off; Db flag on.
        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(false);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        // Expect empty encrypted fields.
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .setEncryptedTopics(Arrays.asList())
                        .setEncryptionKeys(Arrays.asList())
                        .setEncapsulatedKeys(Arrays.asList())
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
        Correspondence<byte[], byte[]> equalByteArray =
                Correspondence.from(Arrays::equals, "Equal byte array check");
        assertThat(getTopicsResult.getEncryptedTopics())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptedTopics());
        assertThat(getTopicsResult.getEncryptionKeys())
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncryptionKeys());
        assertThat(getTopicsResult.getEncapsulatedKeys())
                .comparingElementsUsing(equalByteArray)
                .containsExactlyElementsIn(expectedGetTopicsResult.getEncapsulatedKeys());

        // getTopic() + loadCache() + handleSdkTopicsAssignmentForAppInstallation()
        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_emptyCache() {
        final long epochId = 4L;

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);

        // // There is no returned Topics persisted in the DB so cache is empty
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
    }

    @Test
    public void testGetTopics_appNotInCache() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 1;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app_not_in_cache", "sdk");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);
    }

    @Test
    public void testGetTopics_sdkNotInCache() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 1;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic[] topics = {topic1};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics("app", "sdk_not_in_cache");

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);
    }

    @Test
    public void testGetTopics_handleSdkTopicAssignment() {
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;

        final String app = "app";
        final String sdk = "sdk";

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};

        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            // Assign returned topics to app-only caller for epochs in [current - 3, current - 1]
            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));

            // Make the topic learnable to app-sdk caller for epochs in [current - 3, current - 1].
            // In order to achieve this, persist learnability in [current - 5, current - 3]. This
            // ensures to test the earliest epoch to be learnt from.
            long earliestEpochIdToLearnFrom = epochId - numberOfLookBackEpochs + 1;
            mTopicsDao.persistCallerCanLearnTopics(
                    earliestEpochIdToLearnFrom, Map.of(topic, Set.of(sdk)));
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app, sdk);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
    }

    @Test
    public void testGetTopics_handleSdkTopicAssignment_existingTopicsForSdk() {
        final int numberOfLookBackEpochs = 3;
        final long currentEpochId = 5L;

        final String app = "app";
        final String sdk = "sdk";

        Pair<String, String> appOnlyCaller = Pair.create(app, /* sdk */ "");
        Pair<String, String> appSdkCaller = Pair.create(app, sdk);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};

        for (long epoch = 0; epoch < numberOfLookBackEpochs; epoch++) {
            long epochId = currentEpochId - 1 - epoch;
            Topic topic = topics[(int) epoch];

            // Assign returned topics to app-only caller for epochs in [current - 3, current - 1]
            mTopicsDao.persistReturnedAppTopicsMap(epochId, Map.of(appOnlyCaller, topic));

            // Make the topic learnable to app-sdk caller for epochs in [current - 3, current - 1].
            // In order to achieve this, persist learnability in [current - 5, current - 3]. This
            // ensures to test the earliest epoch to be learnt from.
            long earliestEpochIdToLearnFrom = epochId - numberOfLookBackEpochs + 1;
            mTopicsDao.persistCallerCanLearnTopics(
                    earliestEpochIdToLearnFrom, Map.of(topic, Set.of(sdk)));
        }

        // Current epoch is 5. Sdk has an existing topic in Epoch 2, which is an epoch in
        // [current epoch - 3, current epoch - 1]
        mTopicsDao.persistReturnedAppTopicsMap(
                currentEpochId - numberOfLookBackEpochs, Map.of(appSdkCaller, topic1));

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        mTopicsWorker.loadCache();
        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app, sdk);

        // Only the existing topic will be returned, i.e. No topic assignment has happened.
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(List.of(1L))
                        .setModelVersions(List.of(4L))
                        .setTopics(List.of(1))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
    }

    @Test
    public void testRecordUsage() {
        mTopicsWorker.recordUsage("app", "sdk");
        verify(mMockEpochManager, only()).recordUsageHistory(eq("app"), eq("sdk"));
    }

    @Test
    public void testComputeEpoch() {
        mTopicsWorker.computeEpoch();
        verify(mMockEpochManager, times(1)).processEpoch();
    }

    @Test
    public void testGetKnownTopicsWithConsent_oneTopicBlocked() {
        final long lastEpoch = 3;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into Db
        // populate topics for different epochs to get realistic state of the Db for testing
        // blocked topics.
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(lastEpoch);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);
        Topic blockedTopic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);

        // Mock IPC calls
        TopicParcel topicParcel1 = blockedTopic1.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel1)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        mTopicsDao.recordBlockedTopic(blockedTopic1);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> knownTopicsWithConsent = mTopicsWorker.getKnownTopicsWithConsent();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        // there is only one blocked topic.
        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(blockedTopic1);
        // out of 3 existing topics, 2 of them are not blocked.
        assertThat(knownTopicsWithConsent).hasSize(2);
        assertThat(knownTopicsWithConsent).containsExactly(topic2, topic3);

        // Verify IPC calls
        // loadCache() + retrieveAllBlockedTopics()
        verify(mMockAdServicesManager, times(2)).retrieveAllBlockedTopics();
    }

    @Test
    public void testGetKnownTopicsWithConsent_allTopicsBlocked() {
        final long lastEpoch = 3;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(lastEpoch);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        List<TopicParcel> topicParcels =
                Arrays.stream(topics)
                        .map(Topic::convertTopicToTopicParcel)
                        .collect(Collectors.toList());
        // Mock IPC calls
        doReturn(topicParcels).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // block all topics
        mTopicsDao.recordBlockedTopic(topic1);
        mTopicsDao.recordBlockedTopic(topic2);
        mTopicsDao.recordBlockedTopic(topic3);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> knownTopicsWithConsent = mTopicsWorker.getKnownTopicsWithConsent();

        assertThat(knownTopicsWithConsent).isEmpty();

        // Verify IPC calls
        verify(mMockAdServicesManager).retrieveAllBlockedTopics();
    }

    @Test
    public void testTopicsWithRevokedConsent() {
        Topic blockedTopic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic blockedTopic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic blockedTopic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);

        // Mock IPC calls
        TopicParcel topicParcel1 = blockedTopic1.convertTopicToTopicParcel();
        TopicParcel topicParcel2 = blockedTopic2.convertTopicToTopicParcel();
        TopicParcel topicParcel3 = blockedTopic3.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel1, topicParcel2, topicParcel3))
                .when(mMockAdServicesManager)
                .retrieveAllBlockedTopics();
        // block all blockedTopics
        mTopicsDao.recordBlockedTopic(blockedTopic1);
        mTopicsDao.recordBlockedTopic(blockedTopic2);
        mTopicsDao.recordBlockedTopic(blockedTopic3);

        // persist one not blocked topic.
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 4, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
        returnedAppSdkTopicsMap.put(appSdkKey, topic1);
        mTopicsDao.persistReturnedAppTopicsMap(/* epochId */ 1, returnedAppSdkTopicsMap);

        mTopicsWorker.loadCache();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        // Three topics are persisted into blocked topic table
        assertThat(topicsWithRevokedConsent).hasSize(3);
        assertThat(topicsWithRevokedConsent)
                .containsExactly(blockedTopic1, blockedTopic2, blockedTopic3);

        // Verify IPC calls
        // loadCache() + retrieveAllBlockedTopics()
        verify(mMockAdServicesManager, times(2)).retrieveAllBlockedTopics();
    }

    @Test
    public void testTopicsWithRevokedConsent_noTopicsBlocked() {
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create("app", "sdk");
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        // populate topics for different epochs to get realistic state of the Db for testing
        // blocked topics.
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        mTopicsWorker.loadCache();

        // Mock IPC calls
        doReturn(List.of()).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).isEmpty();
        // Verify IPC calls. loadCache() + retrieveAllBlockedTopics().
        verify(mMockAdServicesManager, times(2)).retrieveAllBlockedTopics();
    }

    @Test
    public void testRevokeConsent() {
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        mTopicsWorker.loadCache();

        // Mock IPC calls
        TopicParcel topicParcel1 = topic1.convertTopicToTopicParcel();
        doNothing().when(mMockAdServicesManager).recordBlockedTopic(List.of(topicParcel1));
        doReturn(List.of(topicParcel1)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        mTopicsWorker.revokeConsentForTopic(topic1);

        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(topic1);

        // Verify IPC calls
        verify(mMockAdServicesManager).recordBlockedTopic(List.of(topicParcel1));
        // revokeConsentForTopic() + loadCache() + retrieveAllBlockedTopics()
        verify(mMockAdServicesManager, times(3)).retrieveAllBlockedTopics();
    }

    @Test
    public void testRevokeAndRestoreConsent() {
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        mTopicsWorker.loadCache();

        // Mock IPC calls
        TopicParcel topicParcel1 = topic1.convertTopicToTopicParcel();
        doNothing().when(mMockAdServicesManager).recordBlockedTopic(List.of(topicParcel1));
        doReturn(List.of(topicParcel1)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // Revoke consent for topic1
        mTopicsWorker.revokeConsentForTopic(topic1);
        ImmutableList<Topic> topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).hasSize(1);
        assertThat(topicsWithRevokedConsent).containsExactly(topic1);

        // Verify IPC calls
        verify(mMockAdServicesManager).recordBlockedTopic(List.of(topicParcel1));
        // revokeConsentForTopic() + loadCache() + retrieveAllBlockedTopics()
        verify(mMockAdServicesManager, times(3)).retrieveAllBlockedTopics();

        // Mock IPC calls
        doNothing().when(mMockAdServicesManager).removeBlockedTopic(topicParcel1);
        doReturn(List.of()).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        // Restore consent for topic1
        mTopicsWorker.restoreConsentForTopic(topic1);
        topicsWithRevokedConsent = mTopicsWorker.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent).isEmpty();

        // Verify IPC calls
        verify(mMockAdServicesManager).removeBlockedTopic(topicParcel1);
        // revokeConsentForTopic() * 2 + loadCache() + retrieveAllBlockedTopics() * 2
        verify(mMockAdServicesManager, times(5)).retrieveAllBlockedTopics();
    }

    @Test
    public void testClearAllTopicsData() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final String app = "app";
        final String sdk = "sdk";

        ArrayList<String> tableExclusionList = new ArrayList<>();
        tableExclusionList.add(TopicsTables.BlockedTopicsContract.TABLE);

        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();

            // Test both cases of app and app-sdk calling getTopics()
            returnedAppSdkTopicsMap.put(Pair.create(app, sdk), currentTopic);
            returnedAppSdkTopicsMap.put(Pair.create(app, /* sdk */ ""), currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        // Mock IPC calls
        TopicParcel topicParcel1 = topic1.convertTopicToTopicParcel();
        doReturn(List.of(topicParcel1)).when(mMockAdServicesManager).retrieveAllBlockedTopics();
        mTopicsDao.recordBlockedTopic(topic1);

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        // Verify topics are persisted in the database
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(2L, 3L))
                        .setModelVersions(Arrays.asList(5L, 6L))
                        .setTopics(Arrays.asList(2, 3))
                        .build();
        GetTopicsResult getTopicsResultAppOnly1 = mTopicsWorker.getTopics(app, /* sdk */ "");
        GetTopicsResult getTopicsResultAppSdk1 = mTopicsWorker.getTopics(app, sdk);

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResultAppOnly1.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResultAppOnly1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResultAppOnly1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResultAppOnly1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());
        assertThat(getTopicsResultAppSdk1.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResultAppSdk1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResultAppSdk1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResultAppSdk1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // Mock AdServicesManager.clearAllBlockedTopics
        doNothing().when(mMockAdServicesManager).clearAllBlockedTopics();
        // Clear all data in database belonging to app except blocked topics table
        mTopicsWorker.clearAllTopicsData(tableExclusionList);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();
        // Verify AdServicesManager.clearAllBlockedTopics is not invoked because tableExclusionList
        // contains blocked topics table
        verify(mMockAdServicesManager, never()).clearAllBlockedTopics();

        mTopicsWorker.clearAllTopicsData(new ArrayList<>());
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();
        // Verify AdServicesManager.clearAllBlockedTopics is invoked
        verify(mMockAdServicesManager, times(1)).clearAllBlockedTopics();

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        assertThat(mTopicsWorker.getTopics(app, sdk)).isEqualTo(emptyGetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app, /* sdk */ "")).isEqualTo(emptyGetTopicsResult);

        // Verify IPC calls
        // 1 loadCache() + 2 clearAllTopicsData()
        verify(mMockAdServicesManager, times(3)).retrieveAllBlockedTopics();
    }

    @Test
    public void testClearAllTopicsData_topicContributorsTable() {
        final long epochId = 1;
        final int topicId = 1;
        final String app = "app";
        Map<Integer, Set<String>> topicContributorsMap = Map.of(topicId, Set.of(app));
        mTopicsDao.persistTopicContributors(epochId, topicContributorsMap);

        // Mock AdServicesManager.clearAllBlockedTopics
        doNothing().when(mMockAdServicesManager).clearAllBlockedTopics();

        // To test feature flag is on
        mTopicsWorker.clearAllTopicsData(/* tables to exclude */ new ArrayList<>());
        // TopicContributors table be cleared.
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId)).isEmpty();
        // Verify AdServicesManager.clearAllBlockedTopics is invoked
        verify(mMockAdServicesManager).clearAllBlockedTopics();
    }

    @Test
    public void testClearAllTopicsData_ImmutableList() {
        assertThrows(
                ClassCastException.class,
                () -> mTopicsWorker.clearAllTopicsData((ArrayList<String>) List.of("anyString")));
    }

    @Test
    public void testReconcileApplicationUpdate() {
        final String app1 = "app1"; // regular app
        final String app2 = "app2"; // unhandled uninstalled app
        final String app3 = "app3"; // unhandled installed app
        final String app4 = "app4"; // uninstalled app but with only usage
        final String app5 = "app5"; // installed app but with only returned topic
        final String sdk = "sdk";

        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final int numOfLookBackEpochs = 3;
        final int topicsNumberOfTopTopics = 5;
        final int topicsPercentageForRandomTopic = 5;

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
        List<Topic> topTopics = List.of(topic1, topic2, topic3, topic4, topic5, topic6);

        // In order to mock Package Manager, context also needs to be mocked to return
        // mocked Package Manager
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(mockPackageManager);

        // Mock Package Manager for installed applications
        // Note app2 is not here to mock uninstallation, app3 is here to mock installation
        ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.packageName = app1;
        ApplicationInfo appInfo3 = new ApplicationInfo();
        appInfo3.packageName = app3;

        if (SdkLevel.isAtLeastT()) {
            when(mockPackageManager.getInstalledApplications(
                            any(PackageManager.ApplicationInfoFlags.class)))
                    .thenReturn(List.of(appInfo1, appInfo3));
        } else {
            when(mockPackageManager.getInstalledApplications(anyInt()))
                    .thenReturn(List.of(appInfo1, appInfo3));
        }

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
                            0 // Select the first random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);

        Topic[] topics1 = {topic1, topic2, topic3};
        Topic[] topics2 = {topic4, topic5, topic6};
        for (int numEpoch = 0; numEpoch < numOfLookBackEpochs; numEpoch++) {
            long epochId = currentEpochId - 1 - numEpoch;
            // Persist returned topics into DB
            Topic currentTopic1 = topics1[numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap1 = new HashMap<>();
            returnedAppSdkTopicsMap1.put(Pair.create(app1, sdk), currentTopic1);
            mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopicsMap1);

            Topic currentTopic2 = topics2[numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap2 = new HashMap<>();
            returnedAppSdkTopicsMap2.put(Pair.create(app2, sdk), currentTopic2);
            mTopicsDao.persistReturnedAppTopicsMap(epochId, returnedAppSdkTopicsMap2);

            // Persist top topics
            mTopicsDao.persistTopTopics(epochId, topTopics);

            // Since AppUpdateManager evaluates previously installed apps through App Usage, usages
            // should be persisted into database.
            //
            // Note app3 doesn't have usage as newly installation. And app4 only has usage but
            // doesn't have returned topics
            mTopicsDao.recordAppUsageHistory(epochId, app1);
            mTopicsDao.recordAppUsageHistory(epochId, app2);
            mTopicsDao.recordAppUsageHistory(epochId, app4);
            // Persist into AppSdkUsage table to mimic reality but this is unnecessary.
            mTopicsDao.recordUsageHistory(epochId, app1, sdk);
            mTopicsDao.recordUsageHistory(epochId, app2, sdk);
            mTopicsDao.recordUsageHistory(epochId, app4, sdk);

            // Persist topics to TopicContributors Table avoid being filtered out
            for (Topic topic : topTopics) {
                mTopicsDao.persistTopicContributors(
                        epochId, Map.of(topic.getTopic(), Set.of(app1, app2, app3, app4)));
            }
        }
        // Persist returned topic to app 5. Note that the epoch id to persist is older than
        // (currentEpochId - numOfLookBackEpochs). Therefore, app5 won't be handled as a newly
        // installed app.
        mTopicsDao.persistReturnedAppTopicsMap(
                currentEpochId - numOfLookBackEpochs - 1, Map.of(Pair.create(app5, ""), topic1));

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);

        // Initialize a local TopicsWorker to use mocked AppUpdateManager
        TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);
        // Reconcile the unhandled uninstalled apps.
        // As PackageManager is mocked, app2 will be identified as unhandled uninstalled app.
        // All data belonging to app2 will be deleted.
        topicsWorker.reconcileApplicationUpdate(mContext);

        // Both reconciling uninstalled apps and installed apps call these mocked functions
        verify(mContext, times(2)).getPackageManager();

        PackageManager verifier = verify(mockPackageManager, times(2));
        if (SdkLevel.isAtLeastT()) {
            verifier.getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
        } else {
            verifier.getInstalledApplications(anyInt());
        }

        // App1 should get topics 1, 2, 3
        GetTopicsResult expectedGetTopicsResult1 =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();
        GetTopicsResult getTopicsResult1 = topicsWorker.getTopics(app1, sdk);
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult1.getResultCode())
                .isEqualTo(expectedGetTopicsResult1.getResultCode());
        assertThat(getTopicsResult1.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getTaxonomyVersions());
        assertThat(getTopicsResult1.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getModelVersions());
        assertThat(getTopicsResult1.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult1.getTopics());

        // App2 is uninstalled so should return empty topics.
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat((topicsWorker.getTopics(app2, sdk))).isEqualTo(emptyGetTopicsResult);

        // App4 is uninstalled so usage table should be clear. As it originally doesn't have
        // returned topic, getTopic won't be checked
        assertThat(
                        mTopicsDao.retrieveDistinctAppsFromTables(
                                List.of(TopicsTables.AppUsageHistoryContract.TABLE),
                                List.of(TopicsTables.AppUsageHistoryContract.APP)))
                .doesNotContain(app4);

        // App3 is newly installed and should topics 1, 2, 6
        GetTopicsResult expectedGetTopicsResult3 =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 6))
                        .build();
        GetTopicsResult getTopicsResult3 = topicsWorker.getTopics(app3, /* sdk */ "");
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult3.getResultCode())
                .isEqualTo(expectedGetTopicsResult3.getResultCode());
        assertThat(getTopicsResult3.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getTaxonomyVersions());
        assertThat(getTopicsResult3.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getModelVersions());
        assertThat(getTopicsResult3.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult3.getTopics());

        // App5 has a returned topic in old epoch, so it won't be regarded as newly installed app
        // Therefore, it won't get any topic in recent epochs.
        assertThat((topicsWorker.getTopics(app5, sdk))).isEqualTo(emptyGetTopicsResult);

        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }

    @Test
    public void testHandleAppUninstallation() {
        final long epochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final String app = "app";
        final String sdk = "sdk";

        final Pair<String, String> appSdkKey = Pair.create(app, sdk);
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion */ 1L, /* modelVersion */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion */ 2L, /* modelVersion */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion */ 3L, /* modelVersion */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // Real Cache Manager requires loading cache before getTopics() being called.
        mTopicsWorker.loadCache();

        GetTopicsResult getTopicsResult = mTopicsWorker.getTopics(app, sdk);

        verify(mMockEpochManager, times(3)).getCurrentEpochId();
        verify(mMockFlags, times(3)).getTopicsNumberOfLookBackEpochs();

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // Delete data belonging to the app
        Uri packageUri = Uri.parse("package:" + app);
        mTopicsWorker.handleAppUninstallation(packageUri);

        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat((mTopicsWorker.getTopics(app, sdk))).isEqualTo(emptyGetTopicsResult);
    }

    @Test
    public void testHandleAppUninstallation_handleTopTopicsWithoutContributors() {
        // The test sets up to handle below scenarios:
        // * Both app1 and app2 have usage in the epoch and all 6 topics are top topics.
        // * app1 is classified to topic1, topic2. app2 is classified to topic1 and topic3.
        // * Both app1 and app2 calls Topics API via .
        // * In Epoch1, as app2 is able to learn topic2 via sdk, though topic2 is not a classified
        //   topic of app2, both app1 and app2 can have topic2 as the returned topic.
        // * In Epoch4, app1 gets uninstalled. Since app1 is the only contributor of topic2, topic2
        //   will be deleted from epoch1. Therefore, app2 will also have empty returned topic in
        //   Epoch1, along with app1.
        // * Comparison case in Epoch2 (multiple contributors): Both app1 and app3 has usages and
        //   are classified topic1 and topic2 with topic1 as the returned topic . When app1 gets
        //   uninstalled in Epoch4, app3 will still be able to return topic2 which comes from
        //   Epoch2.
        // * Comparison case in Epoch3 (the feature topic is only removed on epoch basis): app4 has
        //   same setup as app2 in Epoch1: it has topic2 as returned topic in Epoch1, but is NOT
        //   classified to topic2. app4 also has topic2 as returned topic in Epoch3. Therefore, when
        //   app1 is uninstalled in Epoch4, topic1 will be removed for app4 as returned topic in
        //   Epoch1 but not in Epoch3. So if app4 calls Topics API in Epoch4, it's still able to
        //   return topic1 as a result.
        final long epochId1 = 1;
        final long epochId2 = 2;
        final long epochId3 = 3;
        final long epochId4 = 4;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final String app1 = "app1"; // app to uninstall at Epoch4
        final String app2 = "app2"; // positive case to verify the removal of the returned topic
        final String app3 = "app3"; // negative case to verify scenario of multiple contributors
        final String app4 = "app4"; // negative ase to verify the removal is on epoch basis
        final String sdk = "sdk";
        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);

        // Set the number in flag so that it doesn't depend on the actual value in PhFlags.
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(3);

        // Persist Top topics for epoch1 ~ epoch4
        mTopicsDao.persistTopTopics(
                epochId1, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        mTopicsDao.persistTopTopics(
                epochId2, List.of(topic1, topic2, topic3, topic4, topic5, topic6));
        mTopicsDao.persistTopTopics(
                epochId3, List.of(topic1, topic2, topic3, topic4, topic5, topic6));

        // Persist AppClassificationTopics Table
        mTopicsDao.persistAppClassificationTopics(
                epochId1,
                Map.of(
                        app1, List.of(topic1, topic2),
                        app2, List.of(topic1, topic3),
                        app4, List.of(topic1, topic3)));
        mTopicsDao.persistAppClassificationTopics(
                epochId2, // app1 and app3 have same setup in epoch2
                Map.of(
                        app1, List.of(topic1, topic2),
                        app3, List.of(topic1, topic2)));
        mTopicsDao.persistAppClassificationTopics(
                epochId3, // app4 has topic2 as returned topic in epoch3, which won't be removed.
                Map.of(app4, List.of(topic2)));

        // Compute and persist TopicContributors table
        mTopicsDao.persistTopicContributors(
                epochId1,
                Map.of(
                        topic1.getTopic(), Set.of(app1, app2, app4),
                        topic2.getTopic(), Set.of(app1),
                        topic3.getTopic(), Set.of(app2, app4)));
        mTopicsDao.persistTopicContributors(
                epochId2,
                Map.of(
                        topic1.getTopic(), Set.of(app1, app3),
                        topic2.getTopic(), Set.of(app1, app3)));
        mTopicsDao.persistTopicContributors(epochId3, Map.of(topic2.getTopic(), Set.of(app4)));

        // Persist Usage table to ensure each app has called Topics API in favored epoch
        mTopicsDao.recordUsageHistory(epochId1, app1, sdk);
        mTopicsDao.recordUsageHistory(epochId1, app2, sdk);
        mTopicsDao.recordUsageHistory(epochId2, app1, sdk);
        mTopicsDao.recordUsageHistory(epochId2, app3, sdk);
        mTopicsDao.recordUsageHistory(epochId3, app4, sdk);

        // Persist ReturnedTopics table, all returned topics should be topic2 based on the setup
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId1,
                Map.of(
                        Pair.create(app1, sdk), topic2,
                        Pair.create(app2, sdk), topic2));
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId2,
                Map.of(
                        Pair.create(app1, sdk), topic2,
                        Pair.create(app3, sdk), topic2));
        mTopicsDao.persistReturnedAppTopicsMap(epochId3, Map.of(Pair.create(app4, sdk), topic2));

        // Real Cache Manager requires loading cache before getTopics() being called.
        // The results are observed at Epoch4
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId4);
        mTopicsWorker.loadCache();

        // Verify apps are able to get topic before uninstallation happens
        GetTopicsResult topic2GetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(List.of(taxonomyVersion))
                        .setModelVersions(List.of(modelVersion))
                        .setTopics(List.of(topic2.getTopic()))
                        .build();
        assertThat(mTopicsWorker.getTopics(app1, sdk)).isEqualTo(topic2GetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app2, sdk)).isEqualTo(topic2GetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app3, sdk)).isEqualTo(topic2GetTopicsResult);
        assertThat(mTopicsWorker.getTopics(app4, sdk)).isEqualTo(topic2GetTopicsResult);

        // Uninstall app1
        Uri packageUri = Uri.parse("package:" + app1);
        mTopicsWorker.handleAppUninstallation(packageUri);

        // Verify Topics API results at Epoch 4
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // app1 doesn't have any returned topics due to uninstallation
        assertThat(mTopicsWorker.getTopics(app1, sdk)).isEqualTo(emptyGetTopicsResult);
        // app2 doesn't have returned topics as it only calls Topics API at Epoch1 and its returned
        // topic topic2 is cleaned due to app1's uninstallation.
        assertThat(mTopicsWorker.getTopics(app2, sdk)).isEqualTo(emptyGetTopicsResult);
        // app3 has topic2 as returned topic. topic2 won't be cleaned at Epoch2 as both app1 and
        // app3 are contributors to topic2 in Epoch3.
        assertThat(mTopicsWorker.getTopics(app3, sdk)).isEqualTo(topic2GetTopicsResult);
        // app4 has topic2 as returned topic. topic2 is cleaned as returned topic for app4 in
        // Epoch1. However, app4 is still able to return topic2 as topic2 is a returned topic for
        // app4 at Epoch3.
        assertThat(mTopicsWorker.getTopics(app4, sdk)).isEqualTo(topic2GetTopicsResult);

        // Verify TopicContributors Map is updated: app1 should be removed after the uninstallation.
        // To make the result more readable, original TopicContributors Map before uninstallation is
        // Epoch1:  topic1 -> app1, app2, app4
        //          topic2 -> app1
        //          topic3 -> app2, app4
        // Epoch2:  topic1 -> app1, app3
        //          topic2 -> app1, app3
        // Epoch3:  topic2 -> app4
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1))
                .isEqualTo(
                        Map.of(
                                topic1.getTopic(),
                                Set.of(app2, app4),
                                topic3.getTopic(),
                                Set.of(app2, app4)));
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId2))
                .isEqualTo(
                        Map.of(topic1.getTopic(), Set.of(app3), topic2.getTopic(), Set.of(app3)));
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId3))
                .isEqualTo(Map.of(topic2.getTopic(), Set.of(app4)));
    }

    @Test
    public void testHandleAppUninstallation_contributorDeletionsToSameTopic() {
        // To test the scenario a topic has two contributors, and both are deleted consecutively.
        // Both app1 and app2 are contributors to topic1 and return topic1. app3 is not the
        // contributor but also returns topic1, learnt via same SDK.
        final long epochId1 = 1;
        final long epochId2 = 2;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
        final String app1 = "app1";
        final String app2 = "app2";
        final String app3 = "app3";
        final String sdk = "sdk";
        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);

        // Set the number in flag so that it doesn't depend on the actual value in PhFlags.
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(1);

        // Persist Top topics for epoch1 ~ epoch4
        mTopicsDao.persistTopTopics(
                epochId1, List.of(topic1, topic2, topic3, topic4, topic5, topic6));

        // Persist AppClassificationTopics Table
        mTopicsDao.persistAppClassificationTopics(
                epochId1,
                Map.of(
                        app1, List.of(topic1),
                        app2, List.of(topic1)));

        // Compute and persist TopicContributors table
        mTopicsDao.persistTopicContributors(
                epochId1, Map.of(topic1.getTopic(), Set.of(app1, app2)));

        // Persist ReturnedTopics table. App3 is able to
        mTopicsDao.persistReturnedAppTopicsMap(
                epochId1,
                Map.of(
                        Pair.create(app1, sdk), topic1,
                        Pair.create(app2, sdk), topic1,
                        Pair.create(app3, sdk), topic1));

        // Real Cache Manager requires loading cache before getTopics() being called.
        // The results are observed at EpochId = 2
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(epochId2);
        mTopicsWorker.loadCache();

        // An empty getTopics() result to verify
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // Delete app1
        mTopicsWorker.handleAppUninstallation(Uri.parse(app1));
        // app1 should be deleted from TopicContributors Map
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1))
                .isEqualTo(Map.of(topic1.getTopic(), Set.of(app2)));
        // app1 should have empty result
        assertThat(mTopicsWorker.getTopics(app1, sdk)).isEqualTo(emptyGetTopicsResult);

        // Delete app2
        mTopicsWorker.handleAppUninstallation(Uri.parse(app2));
        // topic1 has app2 as the only contributor, and will be removed.
        assertThat(mTopicsDao.retrieveTopicToContributorsMap(epochId1)).isEmpty();
        // app2 should have empty result
        assertThat(mTopicsWorker.getTopics(app2, sdk)).isEqualTo(emptyGetTopicsResult);

        // As topic1 is removed, app3 also has empty result
        assertThat(mTopicsWorker.getTopics(app3, sdk)).isEqualTo(emptyGetTopicsResult);
    }

    @Test
    public void testHandleAppInstallation() {
        final String appName = "app";
        Uri packageUri = Uri.parse("package:" + appName);
        final long currentEpochId = 4L;
        final long taxonomyVersion = 1L;
        final long modelVersion = 1L;
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
                            0 // Select the first random topic
                        });
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mDbHelper, mTopicsDao, mockRandom, mMockFlags);
        // Create a local TopicsWorker in order to user above local AppUpdateManager
        TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mCacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);

        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numOfLookBackEpochs);
        when(mMockFlags.getTopicsNumberOfTopTopics()).thenReturn(topicsNumberOfTopTopics);
        when(mMockFlags.getTopicsPercentageForRandomTopic())
                .thenReturn(topicsPercentageForRandomTopic);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);

        Topic topic1 = Topic.create(/* topic */ 1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(/* topic */ 2, taxonomyVersion, modelVersion);
        Topic topic3 = Topic.create(/* topic */ 3, taxonomyVersion, modelVersion);
        Topic topic4 = Topic.create(/* topic */ 4, taxonomyVersion, modelVersion);
        Topic topic5 = Topic.create(/* topic */ 5, taxonomyVersion, modelVersion);
        Topic topic6 = Topic.create(/* topic */ 6, taxonomyVersion, modelVersion);
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

        // Verify getTopics() returns nothing before calling assignTopicsToNewlyInstalledApps()
        GetTopicsResult emptyGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();
        assertThat(topicsWorker.getTopics(appName, /* sdk */ "")).isEqualTo(emptyGetTopicsResult);

        // Assign topics to past epochs
        topicsWorker.handleAppInstallation(packageUri);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(STATUS_SUCCESS)
                        .setTaxonomyVersions(
                                Arrays.asList(taxonomyVersion, taxonomyVersion, taxonomyVersion))
                        .setModelVersions(Arrays.asList(modelVersion, modelVersion, modelVersion))
                        .setTopics(Arrays.asList(1, 2, 6))
                        .build();
        GetTopicsResult getTopicsResult = topicsWorker.getTopics(appName, /* sdk */ "");

        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        verify(mMockFlags).getTopicsNumberOfTopTopics();
        verify(mMockFlags).getTopicsPercentageForRandomTopic();
    }
}
