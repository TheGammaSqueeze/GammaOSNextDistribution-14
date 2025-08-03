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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.data.topics.Topic;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
public class AppSearchTopicsConsentDaoTest {
    private static final String ID = "1";
    private static final String NAMESPACE = "blockedTopics";
    private static final Topic TOPIC1 = Topic.create(0, 1, 11);
    private static final Topic TOPIC2 = Topic.create(12, 2, 22);
    private static final Topic TOPIC3 = Topic.create(123, 3, 33);
    private static final List<Integer> TOPIC_IDS = List.of(0, 12, 123);
    private static final List<Long> TOPIC_TAXONOMIES = List.of(1L, 2L, 3L);
    private static final List<Long> TOPIC_MODELS = List.of(11L, 22L, 33L);
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mAdServicesPackageName =
            AppSearchConsentWorker.getAdServicesPackageName(mContext);
    private MockitoSession mStaticMockSession;

    @Rule
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppSearchDao.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testToString_null() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(ID, ID, NAMESPACE, null, null, null);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; namespace="
                                + NAMESPACE
                                + "; blockedTopics=[]"
                                + "; blockedTopicsTaxonomyVersions=[]"
                                + "; blockedTopicsModelVersions=[]");
    }

    @Test
    public void testToString() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; namespace="
                                + NAMESPACE
                                + "; blockedTopics=[0, 12, 123]"
                                + "; blockedTopicsTaxonomyVersions=[1, 2, 3]"
                                + "; blockedTopicsModelVersions=[11, 22, 33]");
    }

    @Test
    public void testEquals() {
        AppSearchTopicsConsentDao dao1 =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        AppSearchTopicsConsentDao dao2 =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        AppSearchTopicsConsentDao dao3 =
                new AppSearchTopicsConsentDao(
                        ID, ID, "namespace", TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID;
        assertThat(AppSearchTopicsConsentDao.getQuery(ID)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        assertThat(AppSearchTopicsConsentDao.getRowId(ID)).isEqualTo(ID);
    }

    @Test
    public void testAddTopic_null() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(ID, ID, NAMESPACE, null, null, null);
        Topic topic = Topic.create(5, 6L, 7L);
        dao.addBlockedTopic(topic);

        assertThat(dao.getBlockedTopics().size()).isEqualTo(1);
        assertThat(dao.getBlockedTopicsTaxonomyVersions().size()).isEqualTo(1);
        assertThat(dao.getBlockedTopicsModelVersions().size()).isEqualTo(1);

        assertThat(dao.getBlockedTopics()).contains(5);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).contains(6L);
        assertThat(dao.getBlockedTopicsModelVersions()).contains(7L);
    }

    @Test
    public void testAddTopic() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        Topic topic = Topic.create(5, 6L, 7L);
        assertThat(dao.getBlockedTopics()).doesNotContain(5);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).doesNotContain(6L);
        assertThat(dao.getBlockedTopicsModelVersions()).doesNotContain(7L);

        dao.addBlockedTopic(topic);

        assertThat(dao.getBlockedTopics().size()).isEqualTo(4);
        assertThat(dao.getBlockedTopicsTaxonomyVersions().size()).isEqualTo(4);
        assertThat(dao.getBlockedTopicsModelVersions().size()).isEqualTo(4);

        assertThat(dao.getBlockedTopics()).contains(5);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).contains(6L);
        assertThat(dao.getBlockedTopicsModelVersions()).contains(7L);
    }

    @Test
    public void testRemoveTopic_notExists() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        Topic topic = Topic.create(5, 6L, 7L);
        dao.removeBlockedTopic(topic);

        assertThat(dao.getBlockedTopics().size()).isEqualTo(3);
        assertThat(dao.getBlockedTopicsTaxonomyVersions().size()).isEqualTo(3);
        assertThat(dao.getBlockedTopicsModelVersions().size()).isEqualTo(3);

        assertThat(dao.getBlockedTopics()).doesNotContain(5);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).doesNotContain(6L);
        assertThat(dao.getBlockedTopicsModelVersions()).doesNotContain(7L);
    }

    @Test
    public void testRemoveTopic() {
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        assertThat(dao.getBlockedTopics()).contains(0);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).contains(1L);
        assertThat(dao.getBlockedTopicsModelVersions()).contains(11L);

        Topic topic = Topic.create(0, 1L, 11L);
        dao.removeBlockedTopic(topic);

        assertThat(dao.getBlockedTopics().size()).isEqualTo(2);
        assertThat(dao.getBlockedTopicsTaxonomyVersions().size()).isEqualTo(2);
        assertThat(dao.getBlockedTopicsModelVersions().size()).isEqualTo(2);

        assertThat(dao.getBlockedTopics()).doesNotContain(0);
        assertThat(dao.getBlockedTopicsTaxonomyVersions()).doesNotContain(1L);
        assertThat(dao.getBlockedTopicsModelVersions()).doesNotContain(11L);
    }

    @Test
    public void testGetBlockedTopics_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        List result =
                AppSearchTopicsConsentDao.getBlockedTopics(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testGetBlockedTopics() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        AppSearchTopicsConsentDao dao =
                new AppSearchTopicsConsentDao(
                        ID, ID, NAMESPACE, TOPIC_IDS, TOPIC_TAXONOMIES, TOPIC_MODELS);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        List<Topic> result =
                AppSearchTopicsConsentDao.getBlockedTopics(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result).contains(TOPIC1);
        assertThat(result).contains(TOPIC2);
        assertThat(result).contains(TOPIC3);
    }
}
