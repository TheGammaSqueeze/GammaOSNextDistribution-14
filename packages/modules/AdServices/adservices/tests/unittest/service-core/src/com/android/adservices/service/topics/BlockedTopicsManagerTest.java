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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_CLEAR_ALL_BLOCKED_TOPICS_IN_SYSTEM_SERVER_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_GET_BLOCKED_TOPIC_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REMOVE_BLOCKED_TOPIC_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_BLOCKED_TOPICS;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_KEY_HAS_MIGRATED;
import static com.android.adservices.service.topics.BlockedTopicsManager.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED;
import static com.android.adservices.service.topics.BlockedTopicsManager.handleBlockedTopicsMigrationIfNeeded;
import static com.android.adservices.service.topics.BlockedTopicsManager.mayClearPpApiBlockedTopics;
import static com.android.adservices.service.topics.BlockedTopicsManager.mayMigratePpApiBlockedTopicsToSystemService;
import static com.android.adservices.service.topics.BlockedTopicsManager.resetSharedPreference;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.ExtendedMockitoExpectations;
import com.android.adservices.service.Flags;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

/** Unit test to test class {@link com.android.adservices.service.topics.BlockedTopicsManager} */
@RequiresSdkLevelAtLeastS()
public class BlockedTopicsManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final long TAXONOMY_VERSION = 1L;
    private static final long MODEL_VERSION = 1L;
    private static final Topic TOPIC =
            Topic.create(/* topicId */ 1, TAXONOMY_VERSION, MODEL_VERSION);

    private final DbHelper mDBHelper = DbTestUtil.getDbHelperForTest();
    private final TopicsDao mTopicsDao = new TopicsDao(mDBHelper);

    private AdServicesManager mAdServicesManager;
    @Mock private AppSearchConsentManager mAppSearchConsentManager;

    @Mock private IAdServicesManager mMockIAdServicesManager;

    @Before
    public void setup() {
        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);

        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mAdServicesManager).when(mSpyContext).getSystemService(AdServicesManager.class);
    }

    @Test
    public void testTopicParcelCreation() {
        TopicParcel topicParcelFromParcel = TopicParcel.CREATOR.createFromParcel(Parcel.obtain());
        TopicParcel[] topicParcels = TopicParcel.CREATOR.newArray(2);
        topicParcelFromParcel.writeToParcel(Parcel.obtain(), 0);

        assertThat(topicParcelFromParcel.describeContents()).isEqualTo(0);
        assertThat(topicParcels[0]).isNull();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_PpapiOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        assertThat(blockedTopicsManager.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_SystemServerOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mMockIAdServicesManager).recordBlockedTopic(any());

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);
        verify(mMockIAdServicesManager).retrieveAllBlockedTopics();

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mMockIAdServicesManager).removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_PpapiAndSystemServer() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);
        verify(mMockIAdServicesManager).retrieveAllBlockedTopics();
        // Also verify PPAPI has recorded this topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).hasSize(1);
        assertThat(mTopicsDao.retrieveAllBlockedTopics().get(0)).isEqualTo(TOPIC);

        // Verify the topic is unblocked
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mMockIAdServicesManager).removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
        // Also verify PPAPI has removed this topic
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();
    }

    @Test
    public void testBlockUnblockRetrieveBlockedTopics_AppSearchOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.APPSEARCH_ONLY;

        // Block a topic.
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ true);
        blockedTopicsManager.blockTopic(TOPIC);
        verify(mAppSearchConsentManager).blockTopic(TOPIC);

        // Unblock a topic.
        blockedTopicsManager.unblockTopic(TOPIC);
        verify(mAppSearchConsentManager).unblockTopic(TOPIC);

        // Get all blocked topics.
        when(mAppSearchConsentManager.retrieveAllBlockedTopics()).thenReturn(List.of(TOPIC));
        List<Topic> result = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(result).isEqualTo(List.of(TOPIC));

        // Clear all blocked topics.
        blockedTopicsManager.clearAllBlockedTopics();
        verify(mAppSearchConsentManager).clearAllBlockedTopics();
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testBlockTopic_withPpapiAndAdExtDataServiceOnly_throwsException() throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.PPAPI_AND_ADEXT_SERVICE, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, () -> blockedTopicsManager.blockTopic(TOPIC));
        assertThat(e).hasCauseThat().isNotNull();
        assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(e)
                .hasCauseThat()
                .hasMessageThat()
                .startsWith("Invalid state: Attempting to block topic");
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testBlockTopic_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).blockTopic(any());
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, () -> blockedTopicsManager.blockTopic(TOPIC));
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
        ExtendedMockitoExpectations.verifyErrorLogUtilError(
                thrown,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_RECORD_BLOCKED_TOPICS_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testUnblockTopic_withPpapiAndAdExtDataServiceOnly_throwsException()
            throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.PPAPI_AND_ADEXT_SERVICE, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, () -> blockedTopicsManager.unblockTopic(TOPIC));
        assertThat(e).hasCauseThat().isNotNull();
        assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(e)
                .hasCauseThat()
                .hasMessageThat()
                .startsWith("Invalid state: Attempting to unblock topic");
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testUnblockTopic_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).unblockTopic(any());
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, () -> blockedTopicsManager.unblockTopic(TOPIC));
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
        ExtendedMockitoExpectations.verifyErrorLogUtilError(
                thrown,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_REMOVE_BLOCKED_TOPIC_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testRetrieveAllBlockedTopics_withPpapiAndAdExtDataServiceOnly_throwsException()
            throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.PPAPI_AND_ADEXT_SERVICE, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, blockedTopicsManager::retrieveAllBlockedTopics);
        assertThat(e).hasCauseThat().isNotNull();
        assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(e)
                .hasCauseThat()
                .hasMessageThat()
                .startsWith("Invalid state: Attempting to retrieve blocked topics");
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testRetrieveAllBlockedTopics_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).retrieveAllBlockedTopics();
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(
                        RuntimeException.class, blockedTopicsManager::retrieveAllBlockedTopics);
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
        ExtendedMockitoExpectations.verifyErrorLogUtilError(
                thrown,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_GET_BLOCKED_TOPIC_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testClearAllBlockedTopics_withPpapiAndAdExtDataServiceOnly_throwsException()
            throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.PPAPI_AND_ADEXT_SERVICE, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, blockedTopicsManager::clearAllBlockedTopics);
        assertThat(e).hasCauseThat().isNotNull();
        assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(e)
                .hasCauseThat()
                .hasMessageThat()
                .startsWith("Invalid state: Attempting to clear blocked topics");
    }

    @Test
    @MockStatic(ErrorLogUtil.class)
    public void testClearAllBlockedTopics_withAppSearchOnly_throwsException() throws Exception {
        Exception thrown = new IllegalStateException("test");
        ExtendedMockito.doThrow(thrown).when(mAppSearchConsentManager).clearAllBlockedTopics();
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        Flags.APPSEARCH_ONLY, /* enableAppSearchConsent= */ true);

        Exception e =
                assertThrows(RuntimeException.class, blockedTopicsManager::clearAllBlockedTopics);
        assertThat(e).hasCauseThat().isSameInstanceAs(thrown);
        ExtendedMockitoExpectations.verifyErrorLogUtilError(
                thrown,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_CLEAR_ALL_BLOCKED_TOPICS_IN_SYSTEM_SERVER_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_PpApiOnly() throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;

        // Block a topic
        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);
        blockedTopicsManager.blockTopic(TOPIC);

        // Verify the topic is blocked
        List<Topic> expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify the topic in PPAPI is not unblocked
        blockedTopicsManager.clearAllBlockedTopics();
        expectedBlockedTopics = blockedTopicsManager.retrieveAllBlockedTopics();
        assertThat(expectedBlockedTopics).hasSize(1);
        assertThat(expectedBlockedTopics.get(0)).isEqualTo(TOPIC);

        // Verify clearAllBlockedTopics() is not invoked
        verify(mMockIAdServicesManager, never()).clearAllBlockedTopics();
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_SystemServerOnly()
            throws Exception {
        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;

        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);

        blockedTopicsManager.clearAllBlockedTopics();

        // Verify clearAllBlockedTopics() is invoked
        verify(mMockIAdServicesManager).clearAllBlockedTopics();
    }

    @Test
    public void testClearAllBlockedTopicsInSystemServiceIfNeeded_PpApiAndSystemServer()
            throws Exception {
        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;

        BlockedTopicsManager blockedTopicsManager =
                getSpiedBlockedTopicsManager(
                        blockedTopicsSourceOfTruth, /* enableAppSearchConsent= */ false);

        blockedTopicsManager.clearAllBlockedTopics();

        // Verify clearAllBlockedTopics() is invoked
        verify(mMockIAdServicesManager).clearAllBlockedTopics();
    }

    @Test
    public void testResetSharedPreference() {
        SharedPreferences sharedPreferences =
                mSpyContext.getSharedPreferences(SHARED_PREFS_BLOCKED_TOPICS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);
        editor.commit();

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isTrue();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isTrue();

        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isFalse();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isFalse();
    }

    @Test
    public void testMayMigratePpApiBlockedTopicsToSystemService() throws Exception {
        doNothing()
                .when(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        mTopicsDao.recordBlockedTopic(TOPIC);

        mayMigratePpApiBlockedTopicsToSystemService(mSpyContext, mTopicsDao, mAdServicesManager);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Verify this should only happen once
        mayMigratePpApiBlockedTopicsToSystemService(mSpyContext, mTopicsDao, mAdServicesManager);
        verify(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));

        // Clear shared preference
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMayClearPpApiBlockedTopics() {
        mTopicsDao.recordBlockedTopic(TOPIC);

        mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isEmpty();

        // Verify this should only happen once
        mTopicsDao.recordBlockedTopic(TOPIC);
        mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao);
        assertThat(mTopicsDao.retrieveAllBlockedTopics()).isNotEmpty();

        // Clear shared preference
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
    }

    @Test
    public void testHandleBlockedTopicsMigrationIfNeeded_PpApiOnly() {
        // Handle migration tests are only valid for T+.
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        int blockedTopicsSourceOfTruth = Flags.PPAPI_ONLY;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        ExtendedMockito.verify(
                () -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager),
                times(0));
        ExtendedMockito.verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), times(0));

        session.finishMocking();
    }

    @Test
    public void testHandleBlockedTopicsMigrationIfNeeded_SystemServerOnly() {
        // Handle migration tests are only valid for T+.
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        int blockedTopicsSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        ExtendedMockito.verify(
                () -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED), times(0));
        ExtendedMockito.verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        session.finishMocking();
    }

    @Test
    public void testHandleBlockedTopicsMigrationIfNeeded_PpApiAndSystemServer() {
        // Handle migration tests are only valid for T+.
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        int blockedTopicsSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));

        handleBlockedTopicsMigrationIfNeeded(
                mSpyContext, mTopicsDao, mAdServicesManager, blockedTopicsSourceOfTruth);

        ExtendedMockito.verify(
                () -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED), times(0));
        ExtendedMockito.verify(
                () ->
                        mayMigratePpApiBlockedTopicsToSystemService(
                                mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.verify(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), times(0));

        session.finishMocking();
    }

    private BlockedTopicsManager getSpiedBlockedTopicsManager(
            int blockedTopicsSourceOfTruth, boolean enableAppSearchConsent) throws Exception {
        BlockedTopicsManager blockedTopicsManager =
                new BlockedTopicsManager(
                        mTopicsDao,
                        mAdServicesManager,
                        mAppSearchConsentManager,
                        blockedTopicsSourceOfTruth,
                        enableAppSearchConsent);

        // Disable IPC calls
        doNothing()
                .when(mMockIAdServicesManager)
                .recordBlockedTopic(List.of(TOPIC.convertTopicToTopicParcel()));
        doNothing()
                .when(mMockIAdServicesManager)
                .removeBlockedTopic(TOPIC.convertTopicToTopicParcel());
        doReturn(List.of(TOPIC.convertTopicToTopicParcel()))
                .when(mMockIAdServicesManager)
                .retrieveAllBlockedTopics();
        doNothing().when(mMockIAdServicesManager).clearAllBlockedTopics();

        return blockedTopicsManager;
    }

    @Test
    public void testHandleBlockedTopicsMigrationIfNeeded_ExtServices() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BlockedTopicsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        ExtendedMockito.doNothing()
                .when(() -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                mayMigratePpApiBlockedTopicsToSystemService(
                                        mSpyContext, mTopicsDao, mAdServicesManager));
        ExtendedMockito.doNothing().when(() -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao));
        ExtendedMockito.doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();

        try {
            handleBlockedTopicsMigrationIfNeeded(mSpyContext, mTopicsDao, mAdServicesManager, 2);

            ExtendedMockito.verify(
                    () -> resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                    never());
            ExtendedMockito.verify(
                    () ->
                            mayMigratePpApiBlockedTopicsToSystemService(
                                    mSpyContext, mTopicsDao, mAdServicesManager),
                    never());
            ExtendedMockito.verify(
                    () -> mayClearPpApiBlockedTopics(mSpyContext, mTopicsDao), never());
        } finally {
            session.finishMocking();
        }
    }
}
