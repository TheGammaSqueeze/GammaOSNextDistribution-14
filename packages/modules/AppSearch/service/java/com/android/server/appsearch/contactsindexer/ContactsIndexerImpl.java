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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.GenericDocument;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The class to sync the data from CP2 to AppSearch.
 *
 * <p>This class is NOT thread-safe.
 *
 * @hide
 */
public final class ContactsIndexerImpl {
    static final String TAG = "ContactsIndexerImpl";

    // TODO(b/203605504) have and read those flags in/from AppSearchConfig.
    static final int NUM_CONTACTS_PER_BATCH_FOR_CP2 = 100;
    static final int NUM_UPDATED_CONTACTS_PER_BATCH_FOR_APPSEARCH = 50;
    static final int NUM_DELETED_CONTACTS_PER_BATCH_FOR_APPSEARCH = 500;
    // Common columns needed for all kinds of mime types
    static final String[] COMMON_NEEDED_COLUMNS = {
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHONETIC_NAME,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.CONTACT_LAST_UPDATED_TIMESTAMP
    };
    // The order for the results returned from CP2.
    static final String ORDER_BY = ContactsContract.Data.CONTACT_ID
            // MUST sort by CONTACT_ID first for our iteration to work
            + ","
            // Whether this is the primary entry of its kind for the aggregate
            // contact it belongs to.
            + ContactsContract.Data.IS_SUPER_PRIMARY
            + " DESC"
            // Then rank by importance.
            + ","
            // Whether this is the primary entry of its kind for the raw contact it
            // belongs to.
            + ContactsContract.Data.IS_PRIMARY
            + " DESC"
            + ","
            + ContactsContract.Data.RAW_CONTACT_ID;

    private final Context mContext;
    private final ContactDataHandler mContactDataHandler;
    private final String[] mProjection;
    private final AppSearchHelper mAppSearchHelper;

    public ContactsIndexerImpl(@NonNull Context context, @NonNull AppSearchHelper appSearchHelper) {
        mContext = Objects.requireNonNull(context);
        mAppSearchHelper = Objects.requireNonNull(appSearchHelper);
        mContactDataHandler = new ContactDataHandler(mContext.getResources());

        Set<String> neededColumns = new ArraySet<>(Arrays.asList(COMMON_NEEDED_COLUMNS));
        neededColumns.addAll(mContactDataHandler.getNeededColumns());
        mProjection = neededColumns.toArray(new String[0]);
    }

    /**
     * Syncs contacts in Person corpus in AppSearch, with the ones from CP2.
     *
     * <p>It deletes removed contacts, inserts newly-added ones, and updates existing ones in the
     * Person corpus in AppSearch.
     *
     * @param wantedContactIds      ids for contacts to be updated.
     * @param unWantedIds           ids for contacts to be deleted.
     * @param updateStats           to hold the counters for the update.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors.
     */
    public CompletableFuture<Void> updatePersonCorpusAsync(
            @NonNull List<String> wantedContactIds,
            @NonNull List<String> unWantedIds,
            @NonNull ContactsUpdateStats updateStats,
            boolean shouldKeepUpdatingOnError) {
        Objects.requireNonNull(wantedContactIds);
        Objects.requireNonNull(unWantedIds);
        Objects.requireNonNull(updateStats);

        return batchRemoveContactsAsync(unWantedIds, updateStats,
                shouldKeepUpdatingOnError).exceptionally(t -> {
            // Since we update the timestamps no matter the update succeeds or fails, we can
            // always try to do the indexing. Updating lastDeltaUpdateTimestamps without doing
            // indexing seems odd.
            // So catch the exception here for deletion, and we can keep doing the indexing.
            Log.w(TAG, "Error occurred during batch delete", t);
            return null;
        }).thenCompose(x -> batchUpdateContactsAsync(wantedContactIds, updateStats,
                shouldKeepUpdatingOnError));
    }

    /**
     * Removes contacts in batches.
     *
     * @param updateStats to hold the counters for the remove.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors.
     */
    @VisibleForTesting
    CompletableFuture<Void> batchRemoveContactsAsync(
            @NonNull final List<String> unWantedIds,
            @NonNull ContactsUpdateStats updateStats,
            boolean shouldKeepUpdatingOnError) {
        CompletableFuture<Void> batchRemoveFuture = CompletableFuture.completedFuture(null);
        int startIndex = 0;
        int unWantedSize = unWantedIds.size();
        updateStats.mTotalContactsToBeDeleted += unWantedSize;
        while (startIndex < unWantedSize) {
            int endIndex = Math.min(startIndex + NUM_DELETED_CONTACTS_PER_BATCH_FOR_APPSEARCH,
                    unWantedSize);
            Collection<String> currentContactIds = unWantedIds.subList(startIndex, endIndex);
            // If any removeContactsByIdAsync in the future-chain completes exceptionally, all
            // futures following it will not run and will instead complete exceptionally. However,
            // when shouldKeepUpdatingOnError is true, removeContactsByIdAsync avoids completing
            // exceptionally.
            batchRemoveFuture = batchRemoveFuture.thenCompose(
                    x -> mAppSearchHelper.removeContactsByIdAsync(currentContactIds, updateStats,
                            shouldKeepUpdatingOnError));
            startIndex = endIndex;
        }
        return batchRemoveFuture;
    }

    /**
     * Batch inserts newly-added contacts, and updates recently-updated contacts.
     *
     * @param updateStats to hold the counters for the update.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors. When enabled and
     *                                  we fail to query CP@ for a batch of contacts, we continue
     *                                  onto the next batch instead of stopping.
     */
    CompletableFuture<Void> batchUpdateContactsAsync(
            @NonNull final List<String> wantedContactIds,
            @NonNull ContactsUpdateStats updateStats,
            boolean shouldKeepUpdatingOnError) {
        int startIndex = 0;
        int wantedIdListSize = wantedContactIds.size();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        updateStats.mTotalContactsToBeUpdated += wantedIdListSize;

        //
        // Batch reading the contacts from CP2, and index the created documents to AppSearch
        //
        // Moved contactsBatcher from a member variable to a local variable. Previously, two
        // simultaneous updates would use the same ContactsBatcher, leading to updates sometimes
        // indexing each other's contacts and messing up the metrics/counts for the number of
        // succeeded/skipped contacts.
        ContactsBatcher contactsBatcher = new ContactsBatcher(mAppSearchHelper,
                NUM_UPDATED_CONTACTS_PER_BATCH_FOR_APPSEARCH, shouldKeepUpdatingOnError);
        while (startIndex < wantedIdListSize) {
            int endIndex = Math.min(startIndex + NUM_CONTACTS_PER_BATCH_FOR_CP2,
                    wantedIdListSize);
            Collection<String> currentContactIds = wantedContactIds.subList(startIndex, endIndex);
            // Read NUM_CONTACTS_PER_BATCH contacts every time from CP2.
            String selection = ContactsContract.Data.CONTACT_ID + " IN (" + TextUtils.join(
                    /*delimiter=*/ ",", currentContactIds) + ")";
            startIndex = endIndex;
            try {
                // For our iteration work, we must sort the result by contact_id first.
                Cursor cursor = mContext.getContentResolver().query(
                        ContactsContract.Data.CONTENT_URI,
                        mProjection,
                        selection, /*selectionArgs=*/null,
                        ORDER_BY);
                if (cursor == null) {
                    updateStats.mUpdateStatuses.add(ContactsUpdateStats.ERROR_CODE_CP2_NULL_CURSOR);
                    Log.w(TAG, "Cursor was returned as null while querying CP2.");
                    if (!shouldKeepUpdatingOnError) {
                        return future.thenCompose(x -> CompletableFuture.failedFuture(
                                new IllegalStateException(
                                        "Cursor was returned as null while querying CP2.")));
                    }
                } else {
                    // If any indexContactsFromCursorAsync in the future-chain completes
                    // exceptionally, all futures following it will not run and will instead
                    // complete exceptionally. However, when shouldKeepUpdatingOnError is true,
                    // indexContactsFromCursorAsync avoids completing exceptionally except for
                    // AppSearchResult#RESULT_OUT_OF_SPACE.
                    future = future.thenCompose(
                            x -> indexContactsFromCursorAsync(cursor, updateStats, contactsBatcher,
                                    shouldKeepUpdatingOnError)
                    ).whenComplete((x, t) -> {
                        // ensure the cursor is closed even when the future-chain fails
                        cursor.close();
                    });
                }
            } catch (RuntimeException e) {
                // The ContactsProvider sometimes propagates RuntimeExceptions to us
                // for when their database fails to open. Behave as if there was no
                // ContactsProvider, and flag that we were not successful.
                Log.e(TAG, "ContentResolver.query threw an exception.", e);
                updateStats.mUpdateStatuses.add(
                        ContactsUpdateStats.ERROR_CODE_CP2_RUNTIME_EXCEPTION);
                if (!shouldKeepUpdatingOnError) {
                    return future.thenCompose(x -> CompletableFuture.failedFuture(e));
                }
            }
        }

        return future;
    }

    /**
     * Reads through cursor, converts the contacts to AppSearch documents, and indexes the
     * documents into AppSearch.
     *
     * @param cursor      pointing to the contacts read from CP2.
     * @param updateStats to hold the counters for the update.
     * @param contactsBatcher the batcher that indexes the contacts for this update.
     * @param shouldKeepUpdatingOnError ContactsIndexer flag controlling whether or not updates
     *                                  should continue after encountering errors. When enabled and
     *                                  an exception is thrown, stops further indexing and flushes
     *                                  the current batch of contacts but does not return a failed
     *                                  future.
     */
    private CompletableFuture<Void> indexContactsFromCursorAsync(@NonNull Cursor cursor,
            @NonNull ContactsUpdateStats updateStats, @NonNull ContactsBatcher contactsBatcher,
            boolean shouldKeepUpdatingOnError) {
        Objects.requireNonNull(cursor);
        try {
            int contactIdIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            int lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY);
            int thumbnailUriIndex = cursor.getColumnIndex(
                    ContactsContract.Data.PHOTO_THUMBNAIL_URI);
            int displayNameIndex = cursor.getColumnIndex(
                    ContactsContract.Data.DISPLAY_NAME_PRIMARY);
            int starredIndex = cursor.getColumnIndex(ContactsContract.Data.STARRED);
            int phoneticNameIndex = cursor.getColumnIndex(ContactsContract.Data.PHONETIC_NAME);
            long currentContactId = -1;
            Person.Builder personBuilder;
            PersonBuilderHelper personBuilderHelper = null;
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(contactIdIndex);
                if (contactId != currentContactId) {
                    // Either it is the very first row (currentContactId = -1), or a row for a new
                    // new contact_id.
                    if (currentContactId != -1) {
                        // It is the first row for a new contact_id. We can wrap up the
                        // ContactData for the previous contact_id.
                        contactsBatcher.add(personBuilderHelper, updateStats);
                    }
                    // New set of builder and builderHelper for the new contact.
                    currentContactId = contactId;
                    String displayName = getStringFromCursor(cursor, displayNameIndex);
                    if (displayName == null) {
                        // For now, we don't abandon the data if displayName is missing. In the
                        // schema the name is required for building a person. It might look bad
                        // if there are contacts in CP2, but not in AppSearch, even though the
                        // name is missing.
                        displayName = "";
                    }
                    personBuilder = new Person.Builder(AppSearchHelper.NAMESPACE_NAME,
                            String.valueOf(contactId), displayName);
                    String imageUri = getStringFromCursor(cursor, thumbnailUriIndex);
                    String lookupKey = getStringFromCursor(cursor, lookupKeyIndex);
                    boolean starred = starredIndex != -1 && cursor.getInt(starredIndex) != 0;
                    Uri lookupUri = lookupKey != null ?
                            ContactsContract.Contacts.getLookupUri(currentContactId, lookupKey)
                            : null;
                    personBuilder.setIsImportant(starred);
                    if (lookupUri != null) {
                        personBuilder.setExternalUri(lookupUri);
                    }
                    if (imageUri != null) {
                        personBuilder.setImageUri(Uri.parse(imageUri));
                    }
                    String phoneticName = getStringFromCursor(cursor, phoneticNameIndex);
                    if (phoneticName != null) {
                        personBuilder.addAdditionalName(Person.TYPE_PHONETIC_NAME, phoneticName);
                    }
                    // Always use current system timestamp first. If that contact already exists
                    // in AppSearch, the creationTimestamp for this doc will be reset with the
                    // original value stored in AppSearch during performDiffAsync.
                    personBuilderHelper = new PersonBuilderHelper(String.valueOf(contactId),
                            personBuilder)
                            .setCreationTimestampMillis(System.currentTimeMillis());
                }
                if (personBuilderHelper != null) {
                    mContactDataHandler.convertCursorToPerson(cursor, personBuilderHelper);
                }
            }

            if (cursor.isAfterLast() && currentContactId != -1) {
                // The ContactData for the last contact has not been handled yet. So we need to
                // build and index it.
                if (personBuilderHelper != null) {
                    contactsBatcher.add(personBuilderHelper, updateStats);
                }
            }
        } catch (RuntimeException e) {
            updateStats.mUpdateStatuses.add(ContactsUpdateStats.ERROR_CODE_CP2_RUNTIME_EXCEPTION);
            // TODO(b/203605504) see if we could catch more specific exceptions/errors.
            Log.e(TAG, "Error while indexing documents from the cursor", e);
            if (!shouldKeepUpdatingOnError) {
                return contactsBatcher.flushAsync(updateStats).thenCompose(
                        x -> CompletableFuture.failedFuture(e));
            }
        }

        // finally force flush all the remaining batched contacts.
        return contactsBatcher.flushAsync(updateStats);
    }

    /**
     * Helper method to read the value from a {@link Cursor} for {@code index}.
     *
     * @return A string value, or {@code null} if the value is missing, or {@code index} is -1.
     */
    @Nullable
    private static String getStringFromCursor(@NonNull Cursor cursor, int index) {
        Objects.requireNonNull(cursor);
        if (index != -1) {
            return cursor.getString(index);
        }
        return null;
    }

    /**
     * Class for helping batching the {@link Person} to be indexed.
     *
     * <p>This class is thread unsafe and all its methods must be called from the same thread.
     */
    static class ContactsBatcher {
        // 1st layer of batching. Contact builders are pushed into this list first before comparing
        // fingerprints.
        private List<PersonBuilderHelper> mPendingDiffContactBuilders;
        // 2nd layer of batching. We do the filtering based on the fingerprint saved in the
        // AppSearch documents, and save the filtered contacts into this mPendingIndexContacts.
        private final List<Person> mPendingIndexContacts;

        /**
         * Batch size for both {@link #mPendingDiffContactBuilders} and {@link
         * #mPendingIndexContacts}. It
         * is strictly followed by {@link #mPendingDiffContactBuilders}. But for {@link
         * #mPendingIndexContacts}, when we merge the former set into {@link
         * #mPendingIndexContacts}, it could exceed this limit. At maximum it could hold 2 *
         * {@link #mBatchSize} contacts before cleared.
         */
        private final int mBatchSize;
        private final AppSearchHelper mAppSearchHelper;
        private final boolean mShouldKeepUpdatingOnError;

        private CompletableFuture<Void> mIndexContactsCompositeFuture =
                CompletableFuture.completedFuture(null);

        ContactsBatcher(@NonNull AppSearchHelper appSearchHelper, int batchSize,
                boolean shouldKeepUpdatingOnError) {
            mAppSearchHelper = Objects.requireNonNull(appSearchHelper);
            mBatchSize = batchSize;
            mShouldKeepUpdatingOnError = shouldKeepUpdatingOnError;
            mPendingDiffContactBuilders = new ArrayList<>(mBatchSize);
            mPendingIndexContacts = new ArrayList<>(mBatchSize);
        }

        CompletableFuture<Void> getCompositeFuture() {
            return mIndexContactsCompositeFuture;
        }

        @VisibleForTesting
        int getPendingDiffContactsCount() {
            return mPendingDiffContactBuilders.size();
        }

        @VisibleForTesting
        int getPendingIndexContactsCount() {
            return mPendingIndexContacts.size();
        }

        public void add(@NonNull PersonBuilderHelper builderHelper,
                @NonNull ContactsUpdateStats updateStats) {
            Objects.requireNonNull(builderHelper);
            mPendingDiffContactBuilders.add(builderHelper);
            if (mPendingDiffContactBuilders.size() >= mBatchSize) {
                // Pass in current mPendingDiffContactBuilders to performDiffAsync and create a new
                // list for batching
                List<PersonBuilderHelper> pendingDiffContactBuilders = mPendingDiffContactBuilders;
                mPendingDiffContactBuilders = new ArrayList<>(mBatchSize);
                mIndexContactsCompositeFuture = mIndexContactsCompositeFuture
                        .thenCompose(x -> performDiffAsync(pendingDiffContactBuilders, updateStats))
                        .thenCompose(y -> {
                            if (mPendingIndexContacts.size() >= mBatchSize) {
                                return flushPendingIndexAsync(updateStats);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
            }
        }

        public CompletableFuture<Void> flushAsync(@NonNull ContactsUpdateStats updateStats) {
            if (!mPendingDiffContactBuilders.isEmpty() || !mPendingIndexContacts.isEmpty()) {
                // Pass in current mPendingDiffContactBuilders to performDiffAsync and create a new
                // list for batching
                List<PersonBuilderHelper> pendingDiffContactBuilders = mPendingDiffContactBuilders;
                mPendingDiffContactBuilders = new ArrayList<>(mBatchSize);
                mIndexContactsCompositeFuture = mIndexContactsCompositeFuture
                        .thenCompose(x -> performDiffAsync(pendingDiffContactBuilders, updateStats))
                        .thenCompose(y -> flushPendingIndexAsync(updateStats));
            }

            CompletableFuture<Void> flushFuture = mIndexContactsCompositeFuture;
            mIndexContactsCompositeFuture = CompletableFuture.completedFuture(null);
            return flushFuture;
        }

        /**
         * Flushes batched contacts into {@link #mPendingIndexContacts}.
         *
         * @param pendingDiffContactBuilders the batched contacts to index
         */
        private CompletableFuture<Void> performDiffAsync(
                @NonNull List<PersonBuilderHelper> pendingDiffContactBuilders,
                @NonNull ContactsUpdateStats updateStats) {
            List<String> ids = new ArrayList<>(pendingDiffContactBuilders.size());
            for (int i = 0; i < pendingDiffContactBuilders.size(); ++i) {
                ids.add(pendingDiffContactBuilders.get(i).getId());
            }
            // getContactsWithFingerPrintsAsync may return no fingerprints if it would normally
            // completely exceptionally and mShouldAppSearchHelperCompleteNormallyOnError is true.
            // In this case, we may unnecessarily update some contacts in the following step, but
            // some unnecessary updates is better than no updates and should not cause a significant
            // impact on performance.
            return mAppSearchHelper.getContactsWithFingerprintsAsync(ids,
                            mShouldKeepUpdatingOnError, updateStats)
                    .thenCompose(contactsWithFingerprints -> {
                        List<Person> contactsToBeIndexed = new ArrayList<>(
                                pendingDiffContactBuilders.size());
                        // Before indexing a contact into AppSearch, we will check if the
                        // contact with same id exists, and whether the fingerprint has
                        // changed. If fingerprint has not been changed for the same
                        // contact, we won't index it.
                        for (int i = 0; i < pendingDiffContactBuilders.size(); ++i) {
                            PersonBuilderHelper builderHelper =
                                    pendingDiffContactBuilders.get(i);
                            GenericDocument doc = contactsWithFingerprints.get(i);
                            byte[] oldFingerprint =
                                    doc != null ? doc.getPropertyBytes(
                                            Person.PERSON_PROPERTY_FINGERPRINT) : null;
                            long docCreationTimestampMillis =
                                    doc != null ? doc.getCreationTimestampMillis()
                                            : -1;
                            if (oldFingerprint != null) {
                                // We already have this contact in AppSearch. Reset the
                                // creationTimestamp here with the original one.
                                builderHelper.setCreationTimestampMillis(
                                        docCreationTimestampMillis);
                                Person person = builderHelper.buildPerson();
                                if (!Arrays.equals(person.getFingerprint(),
                                        oldFingerprint)) {
                                    contactsToBeIndexed.add(person);
                                } else {
                                    // Fingerprint is same. So this update is skipped.
                                    ++updateStats.mContactsUpdateSkippedCount;
                                }
                            } else {
                                // New contact.
                                ++updateStats.mNewContactsToBeUpdated;
                                contactsToBeIndexed.add(builderHelper.buildPerson());
                            }
                        }
                        mPendingIndexContacts.addAll(contactsToBeIndexed);
                        return CompletableFuture.completedFuture(null);
                    });
        }

        /** Flushes the contacts batched in {@link #mPendingIndexContacts} to AppSearch. */
        private CompletableFuture<Void> flushPendingIndexAsync(
                @NonNull ContactsUpdateStats updateStats) {
            if (mPendingIndexContacts.size() > 0) {
                CompletableFuture<Void> future = mAppSearchHelper.indexContactsAsync(
                        mPendingIndexContacts, updateStats, mShouldKeepUpdatingOnError);
                mPendingIndexContacts.clear();
                return future;
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
