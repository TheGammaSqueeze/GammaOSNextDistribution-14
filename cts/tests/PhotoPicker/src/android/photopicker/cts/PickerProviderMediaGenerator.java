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

package android.photopicker.cts;

import static android.provider.CloudMediaProviderContract.AlbumColumns;
import static android.provider.CloudMediaProviderContract.EXTRA_ALBUM_ID;
import static android.provider.CloudMediaProviderContract.EXTRA_MEDIA_COLLECTION_ID;
import static android.provider.CloudMediaProviderContract.EXTRA_PAGE_SIZE;
import static android.provider.CloudMediaProviderContract.EXTRA_SYNC_GENERATION;
import static android.provider.CloudMediaProviderContract.MediaCollectionInfo;
import static android.provider.CloudMediaProviderContract.MediaColumns;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.CloudMediaProvider;
import android.provider.CloudMediaProviderContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates {@link TestMedia} items that can be accessed via test {@link CloudMediaProvider}
 * instances.
 */
public class PickerProviderMediaGenerator {
    private static final Map<String, MediaGenerator> sMediaGeneratorMap = new HashMap<>();
    private static final String[] MEDIA_PROJECTION = new String[] {
        MediaColumns.ID,
        MediaColumns.MEDIA_STORE_URI,
        MediaColumns.MIME_TYPE,
        MediaColumns.STANDARD_MIME_TYPE_EXTENSION,
        MediaColumns.DATE_TAKEN_MILLIS,
        MediaColumns.SYNC_GENERATION,
        MediaColumns.SIZE_BYTES,
        MediaColumns.DURATION_MILLIS,
        MediaColumns.IS_FAVORITE,
    };

    private static final String[] ALBUM_PROJECTION = new String[] {
        AlbumColumns.ID,
        AlbumColumns.DISPLAY_NAME,
        AlbumColumns.DATE_TAKEN_MILLIS,
        AlbumColumns.MEDIA_COVER_ID,
        AlbumColumns.MEDIA_COUNT,
    };

    private static final String[] DELETED_MEDIA_PROJECTION = new String[] { MediaColumns.ID };

    public static class MediaGenerator {
        private final List<TestMedia> mMedia = new ArrayList<>();
        private final List<TestMedia> mDeletedMedia = new ArrayList<>();
        private final List<TestAlbum> mAlbums = new ArrayList<>();
        private final File mPrivateDir;
        private final Context mContext;

        private String mCollectionId;
        private long mLastSyncGeneration;
        private String mAccountName;
        private Intent mAccountConfigurationIntent;

        public MediaGenerator(Context context) {
            mContext = context;
            mPrivateDir = context.getFilesDir();
        }

        public Cursor getMedia(long generation, String albumId, String mimeType, long sizeBytes,
                int pageSize) {
            final Cursor cursor = getCursor(mMedia, generation, albumId, mimeType, sizeBytes,
                    /* isDeleted */ false);
            cursor.setExtras(buildCursorExtras(mCollectionId, generation > 0, albumId != null,
                    pageSize > -1));
            return cursor;
        }

        public Cursor getAlbums(String mimeType, long sizeBytes) {
            final Cursor cursor = getCursor(mAlbums, mimeType, sizeBytes);
            cursor.setExtras(buildCursorExtras(mCollectionId, false, false, false));
            return cursor;
        }

        public Cursor getDeletedMedia(long generation) {
            final Cursor cursor = getCursor(mDeletedMedia, generation, /* albumId */ null,
                    /* mimeType */ null, /* sizeBytes */ 0, /* isDeleted */ true);
            cursor.setExtras(buildCursorExtras(mCollectionId, generation > 0, false, false));
            return cursor;
        }

        public Bundle getMediaCollectionInfo() {
            Bundle bundle = new Bundle();
            bundle.putString(MediaCollectionInfo.MEDIA_COLLECTION_ID, mCollectionId);
            bundle.putLong(MediaCollectionInfo.LAST_MEDIA_SYNC_GENERATION, mLastSyncGeneration);
            bundle.putString(MediaCollectionInfo.ACCOUNT_NAME, mAccountName);
            bundle.putParcelable(MediaCollectionInfo.ACCOUNT_CONFIGURATION_INTENT,
                    mAccountConfigurationIntent);

            return bundle;
        }

        public void setAccountInfo(String accountName, Intent configIntent) {
            mAccountName = accountName;
            mAccountConfigurationIntent = configIntent;
        }

        public Bundle buildCursorExtras(String mediaCollectionId, boolean honoredSyncGeneration,
                boolean honoredAlbumdId, boolean honoredPageSize) {
            final ArrayList<String> honoredArgs = new ArrayList<>();
            if (honoredSyncGeneration) {
                honoredArgs.add(EXTRA_SYNC_GENERATION);
            }
            if (honoredAlbumdId) {
                honoredArgs.add(EXTRA_ALBUM_ID);
            }

            if (honoredPageSize) {
                honoredArgs.add(EXTRA_PAGE_SIZE);
            }

            final Bundle bundle = new Bundle();
            bundle.putString(EXTRA_MEDIA_COLLECTION_ID, mediaCollectionId);
            bundle.putStringArrayList(ContentResolver.EXTRA_HONORED_ARGS, honoredArgs);

            return bundle;
        }

        public void addMedia(String localId, String cloudId, String albumId, String mimeType,
                int standardMimeTypeExtension, long sizeBytes, boolean isFavorite, int resId)
                throws IOException {
            mDeletedMedia.remove(createPlaceholderMedia(localId, cloudId));
            mMedia.add(0, createTestMedia(localId, cloudId, albumId, mimeType,
                            standardMimeTypeExtension, sizeBytes, isFavorite, resId));
        }

        public void deleteMedia(String localId, String cloudId, boolean trackDeleted)
                throws IOException {
            if (mMedia.remove(createPlaceholderMedia(localId, cloudId)) && trackDeleted) {
                mDeletedMedia.add(createTestMedia(localId, cloudId, /* albumId */ null,
                                /* mimeType */ null, /* mimeTypeExtension */ 0, /* sizeBytes */ 0,
                                /* isFavorite */ false, /* resId */ -1));
            }
        }

        public ParcelFileDescriptor openMedia(String cloudId) throws FileNotFoundException {
            try {
                return ParcelFileDescriptor.open(getTestMedia(cloudId),
                        ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (IOException e) {
                throw new FileNotFoundException("Failed to open: " + cloudId);
            }
        }

        public void createAlbum(String id) {
            mAlbums.add(createTestAlbum(id));
        }

        public void resetAll() {
            mMedia.clear();
            mDeletedMedia.clear();
            mAlbums.clear();
        }

        public void setMediaCollectionId(String id) {
            mCollectionId = id;
        }

        public long getCount() {
            return mMedia.size();
        }

        private TestAlbum createTestAlbum(String id) {
            return new TestAlbum(id, mMedia);
        }

        private TestMedia createTestMedia(String localId, String cloudId, String albumId,
                String mimeType, int standardMimeTypeExtension, long sizeBytes,
                boolean isFavorite, int resId) throws IOException {
            // Increase generation
            TestMedia media = new TestMedia(localId, cloudId, albumId, mimeType,
                    standardMimeTypeExtension, sizeBytes, /* durationMs */ 0, ++mLastSyncGeneration,
                    isFavorite);

            if (resId >= 0) {
                media.createFile(mContext, resId, getTestMedia(cloudId));
            }

            return media;
        }

        private static TestMedia createPlaceholderMedia(String localId, String cloudId) {
            // Don't increase generation. Used to create a throw-away element used for removal from
            // |mMedia| or |mDeletedMedia|
            return new TestMedia(localId, cloudId, /* albumId */ null,
                    /* mimeType */ null, /* mimeTypeExtension */ 0, /* sizeBytes */ 0,
                    /* durationMs */ 0, /* generation */ 0, /* isFavorite */ false);
        }

        private File getTestMedia(String cloudId) {
            return new File(mPrivateDir, cloudId);
        }

        private static Cursor getCursor(List<TestMedia> mediaList, long generation,
                String albumId, String mimeType, long sizeBytes, boolean isDeleted) {
            final MatrixCursor matrix;
            if (isDeleted) {
                matrix = new MatrixCursor(DELETED_MEDIA_PROJECTION);
            } else {
                matrix = new MatrixCursor(MEDIA_PROJECTION);
            }

            for (TestMedia media : mediaList) {
                if (media.generation > generation
                        && matchesFilter(media, albumId, mimeType, sizeBytes)) {
                    matrix.addRow(media.toArray(isDeleted));
                }
            }
            return matrix;
        }

        private static Cursor getCursor(List<TestAlbum> albumList, String mimeType,
                long sizeBytes) {
            final MatrixCursor matrix = new MatrixCursor(ALBUM_PROJECTION);

            for (TestAlbum album : albumList) {
                final String[] res = album.toArray(mimeType, sizeBytes);
                if (res != null) {
                    matrix.addRow(res);
                }
            }
            return matrix;
        }
    }

    private static class TestMedia {
        public final String localId;
        public final String cloudId;
        public final String albumId;
        public final String mimeType;
        public final long dateTakenMs;
        public final long durationMs;
        public final long generation;
        public final int standardMimeTypeExtension;
        public final boolean isFavorite;
        public long sizeBytes;

        TestMedia(String localId, String cloudId, String albumId, String mimeType,
                int standardMimeTypeExtension, long sizeBytes, long durationMs, long generation,
                boolean isFavorite) {
            this.localId = localId;
            this.cloudId = cloudId;
            this.albumId = albumId;
            this.mimeType = mimeType;
            this.standardMimeTypeExtension = standardMimeTypeExtension;
            this.sizeBytes = sizeBytes;
            this.dateTakenMs = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.generation = generation;
            this.isFavorite = isFavorite;
        }

        public String[] toArray(boolean isDeleted) {
            if (isDeleted) {
                return new String[] {getId()};
            }

            return new String[] {
                getId(),
                localId == null ? null : "content://media/external/files/" + localId,
                mimeType,
                String.valueOf(standardMimeTypeExtension),
                String.valueOf(dateTakenMs),
                String.valueOf(generation),
                String.valueOf(sizeBytes),
                String.valueOf(durationMs),
                String.valueOf(isFavorite ? 1 : 0)
            };
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof TestMedia)) {
                return false;
            }
            TestMedia other = (TestMedia) o;
            return Objects.equals(localId, other.localId) && Objects.equals(cloudId, other.cloudId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(localId, cloudId);
        }

        public String getId() {
            return cloudId;
        }

        public void createFile(Context context, int sourceResId, File targetFile)
                throws IOException {
            try (InputStream source = context.getResources().openRawResource(sourceResId);
                    FileOutputStream target = new FileOutputStream(targetFile)) {
                FileUtils.copy(source, target);
            }

            // Set size
            sizeBytes = targetFile.length();
        }
    }

    private static class TestAlbum {
        public final String id;
        private final List<TestMedia> mMedia;

        TestAlbum(String id, List<TestMedia> media) {
            this.id = id;
            this.mMedia = media;
        }

        public String[] toArray(String mimeType, long sizeBytes) {
            long mediaCount = 0;
            String mediaCoverId = null;
            long dateTakenMs = 0;

            for (TestMedia m : mMedia) {
                if (matchesFilter(m, id, mimeType, sizeBytes)) {
                    if (mediaCount++ == 0) {
                        mediaCoverId = m.getId();
                        dateTakenMs = m.dateTakenMs;
                    }
                }
            }

            if (mediaCount == 0) {
                return null;
            }

            return new String[] {
                id,
                mediaCoverId,
                /* displayName */ id,
                String.valueOf(dateTakenMs),
                String.valueOf(mediaCount),
            };
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof TestAlbum)) {
                return false;
            }

            TestAlbum other = (TestAlbum) o;
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static boolean matchesFilter(TestMedia media, String albumId, String mimeType,
            long sizeBytes) {
        if ((albumId != null) && albumId != media.albumId) {
            return false;
        }
        if ((mimeType != null) && !media.mimeType.startsWith(mimeType)) {
            return false;
        }
        if (sizeBytes != 0 && media.sizeBytes > sizeBytes) {
            return false;
        }

        return true;
    }

    public static MediaGenerator getMediaGenerator(Context context, String authority) {
        MediaGenerator generator = sMediaGeneratorMap.get(authority);
        if (generator == null) {
            generator = new MediaGenerator(context);
            sMediaGeneratorMap.put(authority, generator);
        }
        return generator;
    }

    public static void setCloudProvider(Context context, String authority) {
        // TODO(b/190713331): Use constants from MediaStore after visible from test
        Bundle in = new Bundle();
        in.putString("cloud_provider", authority);

        callMediaStore(context, "set_cloud_provider", in);
    }

    public static void syncCloudProvider(Context context) {
        // TODO(b/190713331): Use constants from MediaStore after visible from test

        callMediaStore(context, "sync_providers", /* in */ null);
    }

    private static void callMediaStore(Context context, String method, Bundle in) {
        context.getContentResolver().call(MediaStore.AUTHORITY, method, null, in);
    }

    public static class QueryExtras {
        public final String albumId;
        public final String mimeType;
        public final long sizeBytes;
        public final long generation;
        public final int pageSize;

        public QueryExtras(Bundle bundle) {
            if (bundle == null) {
                bundle = new Bundle();
            }

            albumId = bundle.getString(CloudMediaProviderContract.EXTRA_ALBUM_ID, null);
            mimeType = bundle.getString(Intent.EXTRA_MIME_TYPES, null);
            sizeBytes = bundle.getLong(CloudMediaProviderContract.EXTRA_SIZE_LIMIT_BYTES, 0);
            generation = bundle.getLong(CloudMediaProviderContract.EXTRA_SYNC_GENERATION, 0);
            pageSize = bundle.getInt(CloudMediaProviderContract.EXTRA_PAGE_SIZE, -1);
        }
    }
}
