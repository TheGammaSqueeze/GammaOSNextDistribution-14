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

package com.android.server.appsearch.contactsindexer;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.provider.ContactsContract;
import android.test.mock.MockContentResolver;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.appsearch.contactsindexer.ContactsIndexerImpl.ContactsBatcher;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ContactsIndexerImplTest extends FakeContactsProviderTestBase {
    // TODO(b/203605504) we could just use AppSearchHelper.
    private FakeAppSearchHelper mAppSearchHelper;
    private ContactsUpdateStats mUpdateStats;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mAppSearchHelper = new FakeAppSearchHelper(mContext);
        mUpdateStats = new ContactsUpdateStats();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // Wipe the data in AppSearchHelper.DATABASE_NAME.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        super.tearDown();
    }

    /**
     * Helper method to run a delta update in the test.
     *
     * <p> Get is called on the futures to make this helper method synchronous.
     *
     * @param lastUpdatedTimestamp used as the "since" filter for updating the contacts.
     * @param lastDeletedTimestamp used as the "since" filter for deleting the contacts.
     * @return new (lastUpdatedTimestamp, lastDeletedTimestamp) pair after the update and deletion.
     */
    private Pair<Long, Long> runDeltaUpdateOnContactsIndexerImpl(
            @NonNull ContactsIndexerImpl indexerImpl,
            long lastUpdatedTimestamp,
            long lastDeletedTimestamp,
            @NonNull ContactsUpdateStats updateStats)
            throws ExecutionException, InterruptedException {
        Objects.requireNonNull(indexerImpl);
        Objects.requireNonNull(updateStats);
        List<String> wantedContactIds = new ArrayList<>();
        List<String> unWantedContactIds = new ArrayList<>();

        lastUpdatedTimestamp = ContactsProviderUtil.getUpdatedContactIds(mContext,
                lastUpdatedTimestamp, ContactsProviderUtil.UPDATE_LIMIT_NONE,
                wantedContactIds, /*stats=*/ null);
        lastDeletedTimestamp = ContactsProviderUtil.getDeletedContactIds(mContext,
                lastDeletedTimestamp, unWantedContactIds, /*stats=*/ null);
        indexerImpl.updatePersonCorpusAsync(wantedContactIds, unWantedContactIds,
                updateStats, /*shouldKeepUpdatingOnError=*/ true).get();

        return new Pair<>(lastUpdatedTimestamp, lastDeletedTimestamp);
    }

    @Test
    public void testBatcher_noFlushBeforeReachingLimit() throws Exception {
        int batchSize = 5;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);

        for (int i = 0; i < batchSize - 1; ++i) {
            batcher.add(new PersonBuilderHelper(/*id=*/ String.valueOf(i),
                            new Person.Builder("namespace", /*id=*/ String.valueOf(i), /*name=*/
                                    String.valueOf(i))).setCreationTimestampMillis(0),
                    mUpdateStats);
        }
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
    }

    @Test
    public void testBatcher_autoFlush() throws Exception {
        int batchSize = 5;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);

        for (int i = 0; i < batchSize; ++i) {
            batcher.add(
                    new PersonBuilderHelper(
                            /*id=*/ String.valueOf(i),
                            new Person.Builder("namespace", /*id=*/ String.valueOf(i), /*name=*/
                                    String.valueOf(i))
                    ).setCreationTimestampMillis(0), mUpdateStats);
        }
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).hasSize(batchSize);
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);
    }

    @Test
    public void testBatcher_contactFingerprintSame_notIndexed() throws Exception {
        int batchSize = 2;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper builderHelper1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper builderHelper2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2")
                        .setGivenName("given2")
        ).setCreationTimestampMillis(0);
        mAppSearchHelper.setExistingContacts(ImmutableList.of(builderHelper1.buildPerson(),
                builderHelper2.buildPerson()));

        // Try to add the same contacts
        batcher.add(builderHelper1, mUpdateStats);
        batcher.add(builderHelper2, mUpdateStats);
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);
    }

    @Test
    public void testBatcher_contactFingerprintDifferent_notIndexedButBatched() throws Exception {
        int batchSize = 2;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper builderHelper1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper builderHelper2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2")
                        .setGivenName("given2")
        ).setCreationTimestampMillis(0);
        mAppSearchHelper.setExistingContacts(
                ImmutableList.of(builderHelper1.buildPerson(), builderHelper2.buildPerson()));

        PersonBuilderHelper sameAsContact1 = builderHelper1;
        // use toBuilder once it works. Now it is not found due to @hide and not sure how since
        // the test does depend on framework-appsearch.impl.
        PersonBuilderHelper notSameAsContact2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2").setGivenName(
                        "given2diff")
        ).setCreationTimestampMillis(0);
        batcher.add(sameAsContact1, mUpdateStats);
        batcher.add(notSameAsContact2, mUpdateStats);
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(1);
    }

    @Test
    public void testBatcher_contactFingerprintDifferent_Indexed() throws Exception {
        int batchSize = 2;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper contact1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper contact2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2")
                        .setGivenName("given2")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper contact3 = new PersonBuilderHelper("id3",
                new Person.Builder("namespace", "id3", "name3")
                        .setGivenName("given3")
        ).setCreationTimestampMillis(0);
        mAppSearchHelper.setExistingContacts(
                ImmutableList.of(contact1.buildPerson(), contact2.buildPerson(),
                        contact3.buildPerson()));

        PersonBuilderHelper sameAsContact1 = contact1;
        // use toBuilder once it works. Now it is not found due to @hide and not sure how since
        // the test does depend on framework-appsearch.impl.
        PersonBuilderHelper notSameAsContact2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2").setGivenName(
                        "given2diff")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper notSameAsContact3 = new PersonBuilderHelper("id3",
                new Person.Builder("namespace", "id3", "name3").setGivenName(
                        "given3diff")
        ).setCreationTimestampMillis(0);
        batcher.add(sameAsContact1, mUpdateStats);
        batcher.add(notSameAsContact2, mUpdateStats);
        batcher.add(notSameAsContact3, mUpdateStats);
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(1);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(1);

        batcher.flushAsync(mUpdateStats).get();

        assertThat(mAppSearchHelper.mIndexedContacts).containsExactly(
                notSameAsContact2.buildPerson(),
                notSameAsContact3.buildPerson());
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);
    }

    @Test
    public void testBatcher_contactFingerprintDifferent_IndexedWithOriginalCreationTimestamp()
            throws Exception {
        int batchSize = 2;
        long originalTs = System.currentTimeMillis();
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper contact1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(originalTs);
        PersonBuilderHelper contact2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2")
                        .setGivenName("given2")
        ).setCreationTimestampMillis(originalTs);
        PersonBuilderHelper contact3 = new PersonBuilderHelper("id3",
                new Person.Builder("namespace", "id3", "name3")
                        .setGivenName("given3")
        ).setCreationTimestampMillis(originalTs);
        mAppSearchHelper.setExistingContacts(
                ImmutableList.of(contact1.buildPerson(), contact2.buildPerson(),
                        contact3.buildPerson()));
        long updatedTs1 = originalTs + 1;
        long updatedTs2 = originalTs + 2;
        long updatedTs3 = originalTs + 3;
        PersonBuilderHelper sameAsContact1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(updatedTs1);
        // use toBuilder once it works. Now it is not found due to @hide and not sure how since
        // the test does depend on framework-appsearch.impl.
        PersonBuilderHelper notSameAsContact2 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2").setGivenName(
                        "given2diff")
        ).setCreationTimestampMillis(updatedTs2);
        PersonBuilderHelper notSameAsContact3 = new PersonBuilderHelper("id3",
                new Person.Builder("namespace", "id3", "name3").setGivenName(
                        "given3diff")
        ).setCreationTimestampMillis(updatedTs3);

        assertThat(sameAsContact1.buildPerson().getCreationTimestampMillis()).isEqualTo(updatedTs1);
        assertThat(notSameAsContact2.buildPerson().getCreationTimestampMillis()).isEqualTo(
                updatedTs2);
        assertThat(notSameAsContact3.buildPerson().getCreationTimestampMillis()).isEqualTo(
                updatedTs3);

        batcher.add(sameAsContact1, mUpdateStats);
        batcher.add(notSameAsContact2, mUpdateStats);
        batcher.add(notSameAsContact3, mUpdateStats);
        batcher.flushAsync(mUpdateStats).get();

        assertThat(mAppSearchHelper.mIndexedContacts).hasSize(2);
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id1").getGivenName()).isEqualTo("given1");
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id2").getGivenName()).isEqualTo("given2diff");
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id3").getGivenName()).isEqualTo("given3diff");
        // But the timestamps remain same.
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id1").getCreationTimestampMillis()).isEqualTo(originalTs);
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id2").getCreationTimestampMillis()).isEqualTo(originalTs);
        assertThat(mAppSearchHelper.mExistingContacts.get(
                "id3").getCreationTimestampMillis()).isEqualTo(originalTs);
    }

    @Test
    public void testBatcher_contactNew_notIndexedButBatched() throws Exception {
        int batchSize = 2;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper contact1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")
        ).setCreationTimestampMillis(0);
        mAppSearchHelper.setExistingContacts(ImmutableList.of(contact1.buildPerson()));

        PersonBuilderHelper sameAsContact1 = contact1;
        // use toBuilder once it works. Now it is not found due to @hide and not sure how since
        // the test does depend on framework-appsearch.impl.
        PersonBuilderHelper newContact = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2").setGivenName(
                        "given2diff")
        ).setCreationTimestampMillis(0);
        batcher.add(sameAsContact1, mUpdateStats);
        batcher.add(newContact, mUpdateStats);
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(1);
    }

    @Test
    public void testBatcher_contactNew_indexed() throws Exception {
        int batchSize = 2;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);
        PersonBuilderHelper contact1 = new PersonBuilderHelper("id1",
                new Person.Builder("namespace", "id1", "name1")
                        .setGivenName("given1")).setCreationTimestampMillis(0);
        mAppSearchHelper.setExistingContacts(ImmutableList.of(contact1.buildPerson()));

        PersonBuilderHelper sameAsContact1 = contact1;
        // use toBuilder once it works. Now it is not found due to @hide and not sure how since
        // the test does depend on framework-appsearch.impl.
        PersonBuilderHelper newContact1 = new PersonBuilderHelper("id2",
                new Person.Builder("namespace", "id2", "name2").setGivenName(
                        "given2diff")
        ).setCreationTimestampMillis(0);
        PersonBuilderHelper newContact2 = new PersonBuilderHelper("id3",
                new Person.Builder("namespace", "id3", "name3").setGivenName(
                        "given3diff")
        ).setCreationTimestampMillis(0);
        batcher.add(sameAsContact1, mUpdateStats);
        batcher.add(newContact1, mUpdateStats);
        batcher.add(newContact2, mUpdateStats);
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).isEmpty();
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(1);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(1);

        batcher.flushAsync(mUpdateStats).get();

        assertThat(mAppSearchHelper.mIndexedContacts).containsExactly(newContact1.buildPerson(),
                newContact2.buildPerson());
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);
    }

    @Test
    public void testBatcher_batchedContactClearedAfterFlush() throws Exception {
        int batchSize = 5;
        ContactsBatcher batcher = new ContactsBatcher(mAppSearchHelper, batchSize,
                /*shouldKeepUpdatingOnError=*/ true);

        // First batch
        for (int i = 0; i < batchSize; ++i) {
            batcher.add(new PersonBuilderHelper(/*id=*/ String.valueOf(i),
                    new Person.Builder("namespace", /*id=*/ String.valueOf(i), /*name=*/
                            String.valueOf(i))
            ).setCreationTimestampMillis(0), mUpdateStats);
        }
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).hasSize(batchSize);
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);


        mAppSearchHelper.mIndexedContacts.clear();
        // Second batch. Make sure the first batch has been cleared.
        for (int i = 0; i < batchSize; ++i) {
            batcher.add(new PersonBuilderHelper(/*id=*/ String.valueOf(i),
                    new Person.Builder("namespace", /*id=*/ String.valueOf(i), /*name=*/
                            String.valueOf(i))
                            // Different from previous ones to bypass the fingerprinting.
                            .addNote("note")
            ).setCreationTimestampMillis(0), mUpdateStats);
        }
        batcher.getCompositeFuture().get();

        assertThat(mAppSearchHelper.mIndexedContacts).hasSize(batchSize);
        assertThat(batcher.getPendingDiffContactsCount()).isEqualTo(0);
        assertThat(batcher.getPendingIndexContactsCount()).isEqualTo(0);
    }

    @Test
    public void testContactsIndexerImpl_batchRemoveContacts_largerThanBatchSize() throws Exception {
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(mContext,
                mAppSearchHelper);
        int totalNum = ContactsIndexerImpl.NUM_DELETED_CONTACTS_PER_BATCH_FOR_APPSEARCH + 1;
        List<String> removedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            removedIds.add(String.valueOf(i));
        }

        contactsIndexerImpl.batchRemoveContactsAsync(removedIds,
                mUpdateStats, /*shouldKeepUpdatingOnError=*/ true).get();

        assertThat(mAppSearchHelper.mRemovedIds).hasSize(removedIds.size());
        assertThat(mAppSearchHelper.mRemovedIds).isEqualTo(removedIds);
    }

    @Test
    public void testContactsIndexerImpl_batchRemoveContacts_smallerThanBatchSize()
            throws Exception {
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(mContext,
                mAppSearchHelper);
        int totalNum = ContactsIndexerImpl.NUM_DELETED_CONTACTS_PER_BATCH_FOR_APPSEARCH - 1;
        List<String> removedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            removedIds.add(String.valueOf(i));
        }

        contactsIndexerImpl.batchRemoveContactsAsync(removedIds,
                mUpdateStats, /*shouldKeepUpdatingOnError=*/ true).get();

        assertThat(mAppSearchHelper.mRemovedIds).hasSize(removedIds.size());
        assertThat(mAppSearchHelper.mRemovedIds).isEqualTo(removedIds);
    }

    @Test
    public void testContactsIndexerImpl_batchUpdateContactsNullCursor_shouldContinueOnError()
            throws Exception {
        MockContentResolver spyContentResolver = spy(mMockContentResolver);
        doCallRealMethod()
                .doReturn(null)
                .doCallRealMethod()
                .when(spyContentResolver).query(any(), any(), any(), any(), any());
        Context spyContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return spyContentResolver;
            }
        };
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(spyContext,
                mAppSearchHelper);

        // Insert contacts
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        int totalNum = 3 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2;
        for (int i = 0; i < totalNum; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Index three batches of contacts
        List<String> wantedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            wantedIds.add(String.valueOf(i));
        }
        contactsIndexerImpl.batchUpdateContactsAsync(wantedIds,
                mUpdateStats, /*shouldKeepUpdatingOnError=*/ true).get();

        // Contacts indexer should have failed on the second batch but continued to the third batch
        List<String> ids = new ArrayList<>(
                2 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2);
        for (int i = 0; i < ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2; ++i) {
            ids.add(String.valueOf(i));
        }
        for (int i = 2 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2; i < totalNum; ++i) {
            ids.add(String.valueOf(i));
        }
        List<String> indexedIds = mAppSearchHelper.mIndexedContacts.stream().map(
                Person::getId).collect(Collectors.toList());
        assertThat(indexedIds).isEqualTo(ids);
        verify(spyContentResolver, times(3)).query(any(), any(), any(), any(), any());
    }

    @Test
    public void testContactsIndexerImpl_batchUpdateContactsNullCursor_shouldNotContinueOnError() {
        MockContentResolver spyContentResolver = spy(mMockContentResolver);
        doCallRealMethod()
                .doReturn(null)
                .doCallRealMethod()
                .when(spyContentResolver).query(any(), any(), any(), any(), any());
        Context spyContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return spyContentResolver;
            }
        };
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(spyContext,
                mAppSearchHelper);

        // Insert contacts
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        int totalNum = 3 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2;
        for (int i = 0; i < totalNum; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Index three batches of contacts
        List<String> wantedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            wantedIds.add(String.valueOf(i));
        }
        CompletableFuture<Void> future = contactsIndexerImpl.batchUpdateContactsAsync(wantedIds,
                mUpdateStats, /*shouldKeepUpdatingOnError=*/ false);
        ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
        assertThat(thrown).hasMessageThat().contains(
                "Cursor was returned as null while querying CP2.");

        // Contacts indexer should have stopped indexing on/before the second batch
        assertThat(mAppSearchHelper.mIndexedContacts.size()).isAtMost(
                ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2);
        verify(spyContentResolver, times(2)).query(any(), any(), any(), any(), any());
    }

    @Test
    public void testContactsIndexerImpl_batchUpdateContactsThrows_shouldContinueOnError()
            throws Exception {
        MockContentResolver spyContentResolver = spy(mMockContentResolver);
        doCallRealMethod()
                .doThrow(new RuntimeException("mock exception"))
                .doCallRealMethod()
                .when(spyContentResolver).query(any(), any(), any(), any(), any());
        Context spyContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return spyContentResolver;
            }
        };
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(spyContext,
                mAppSearchHelper);

        // Insert contacts
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        int totalNum = 3 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2;
        for (int i = 0; i < totalNum; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Index three batches of contacts
        List<String> wantedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            wantedIds.add(String.valueOf(i));
        }
        contactsIndexerImpl.batchUpdateContactsAsync(wantedIds,
                mUpdateStats, /*/*shouldKeepUpdatingOnError=*/ true).get();

        // Contacts indexer should have failed on the second batch but continued to the third batch
        List<String> ids = new ArrayList<>(
                2 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2);
        for (int i = 0; i < ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2; ++i) {
            ids.add(String.valueOf(i));
        }
        for (int i = 2 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2; i < totalNum; ++i) {
            ids.add(String.valueOf(i));
        }
        List<String> indexedIds = mAppSearchHelper.mIndexedContacts.stream().map(
                Person::getId).collect(Collectors.toList());
        assertThat(indexedIds).isEqualTo(ids);
        verify(spyContentResolver, times(3)).query(any(), any(), any(), any(), any());
    }

    @Test
    public void testContactsIndexerImpl_batchUpdateContactsThrows_shouldNotContinueOnError() {
        MockContentResolver spyContentResolver = spy(mMockContentResolver);
        doCallRealMethod()
                .doThrow(new RuntimeException("mock exception"))
                .doCallRealMethod()
                .when(spyContentResolver).query(any(), any(), any(), any(), any());
        Context spyContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return spyContentResolver;
            }
        };
        ContactsIndexerImpl contactsIndexerImpl = new ContactsIndexerImpl(spyContext,
                mAppSearchHelper);

        // Insert contacts
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        int totalNum = 3 * ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2;
        for (int i = 0; i < totalNum; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Index three batches of contacts
        List<String> wantedIds = new ArrayList<>(totalNum);
        for (int i = 0; i < totalNum; ++i) {
            wantedIds.add(String.valueOf(i));
        }
        CompletableFuture<Void> future = contactsIndexerImpl.batchUpdateContactsAsync(wantedIds,
                mUpdateStats, /*shouldKeepUpdatingOnError=*/ false);
        ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
        assertThat(thrown).hasMessageThat().contains("mock exception");

        // Contacts indexer should have stopped indexing on/before the second batch
        assertThat(mAppSearchHelper.mIndexedContacts.size()).isAtMost(
                ContactsIndexerImpl.NUM_CONTACTS_PER_BATCH_FOR_CP2);
        verify(spyContentResolver, times(2)).query(any(), any(), any(), any(), any());
    }
}
