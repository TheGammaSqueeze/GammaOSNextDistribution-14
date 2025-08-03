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

package com.android.adservices.data.topics.migration;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Unit tests for {@link TopicsDbMigratorV9} */
public class TopicsDbMigratorV9Test {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    // The database is created with V8 and will migrate to V9.
    private final TopicsDbHelperV8 mTopicsDbHelper = TopicsDbHelperV8.getInstance(sContext);

    private MockitoSession mStaticMockSession;
    @Mock private Flags mMockFlags;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testDbMigrationFromV8ToV9() {
        // Enable DB Schema Flag
        SQLiteDatabase db = mTopicsDbHelper.getWritableDatabase();

        // ReturnedEncryptedTopic table doesn't exist in V8
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                                /* number Of Columns */ 7))
                .isFalse();

        // Use transaction here so that the changes in the performMigration is committed.
        // In normal DB upgrade, the DB will commit the change automatically.
        db.beginTransaction();
        // Upgrade the db V9 by using TopicDbMigratorV9
        new TopicsDbMigratorV9().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // ReturnedEncryptedTopic table exists in V9
        db = mTopicsDbHelper.getReadableDatabase();
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db,
                                TopicsTables.ReturnedEncryptedTopicContract.TABLE,
                                /* number Of Columns */ 7))
                .isTrue();

        // Test dated db is cleared when upgrading.
        db.beginTransaction();
        db = mTopicsDbHelper.getWritableDatabase();
        // Insert an entry into the table and verify it.
        TopicsDao topicsDao = new TopicsDao(mTopicsDbHelper);
        final long epochId = 1;
        Map<Pair<String, String>, EncryptedTopic> appSdkEncryptedTopics =
                Map.of(
                        Pair.create("app", "sdk"),
                        EncryptedTopic.create(
                                "topic".getBytes(StandardCharsets.UTF_8),
                                "publicKey",
                                "encapsulatedKey".getBytes(StandardCharsets.UTF_8)));
        topicsDao.persistReturnedAppEncryptedTopicsMap(epochId, appSdkEncryptedTopics);
        assertThat(
                        topicsDao
                                .retrieveReturnedEncryptedTopics(
                                        epochId, /* numberOfLookBackEpochs */ 1)
                                .get(epochId))
                .isEqualTo(appSdkEncryptedTopics);

        new TopicsDbMigratorV9().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // ReturnedEncryptedTopic table should be empty when getting upgraded again.
        assertThat(
                        topicsDao.retrieveReturnedEncryptedTopics(
                                epochId, /* numberOfLookBackEpochs */ 1))
                .isEmpty();
    }
}
