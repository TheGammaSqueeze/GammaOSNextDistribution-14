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

package android.photopicker.cts.util;

import android.app.UiAutomation;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.UserHandle;
import android.photopicker.cts.R;
import android.provider.MediaStore;
import android.provider.cts.ProviderTestUtils;
import android.provider.cts.media.MediaStoreUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Utility methods for media files creation and deletion.
 */
public class PhotoPickerFilesUtils {
    public static final String DISPLAY_NAME_PREFIX = "ctsPhotoPicker";

    public static List<Uri> createImagesAndGetUris(int count, int userId)
            throws Exception {
        return createImagesAndGetUris(count, userId, /* isFavorite */ false);
    }

    public static List<Uri> createImagesAndGetUris(int count, int userId, boolean isFavorite)
            throws Exception {
        List<Pair<Uri, String>> createdImagesData = createImagesAndGetUriAndPath(count, userId,
                isFavorite);

        List<Uri> uriList = new ArrayList<>();
        for (Pair<Uri, String> createdImageData: createdImagesData) {
            uriList.add(createdImageData.first);
        }

        return uriList;
    }

    /**
     * Create multiple images according to the given parameters and returns the uris and filenames
     * of the images created.
     *
     * @param count number of images to create
     * @param userId user id to create images in
     *
     * @return list of data (uri and filename pair) about the images created
     */
    public static List<Pair<Uri, String>> createImagesAndGetUriAndPath(int count, int userId,
            boolean isFavorite) throws Exception {
        List<Pair<Uri, String>> createdImagesData = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Pair<Uri, String> createdImageData = createImage(userId, isFavorite);
            createdImagesData.add(createdImageData);
            clearMediaOwner(createdImageData.first, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());

        return createdImagesData;
    }

    private static Pair<Uri, String> createImage(int userId, boolean isFavorite) throws Exception {
        return getPermissionAndStageMedia(R.raw.lg_g4_iso_800_jpg,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", userId, isFavorite);
    }

    public static List<Uri> createMj2VideosAndGetUris(int count, int userId) throws Exception {
        List<Uri> uriList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Uri uri = createMj2Video(userId);
            uriList.add(uri);
            clearMediaOwner(uri, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());

        return uriList;
    }

    public static List<Uri> createVideosAndGetUris(int count, int userId) throws Exception {
        List<Uri> uriList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final Uri uri = createVideo(userId);
            uriList.add(uri);
            clearMediaOwner(uri, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());

        return uriList;
    }

    /**
     * Return media id from a given Uri
     * @param Uri uri to parse
     * @return String media id
     */
    public static String getMediaId(Uri uri) throws Exception {
        return uri.getLastPathSegment();
    }

    public static void deleteMedia(Uri uri, Context context) throws Exception {
        try {
            ProviderTestUtils.setOwner(uri, context.getPackageName());
            context.getContentResolver().delete(uri, Bundle.EMPTY);
        } catch (Exception ignored) {
        }
    }

    private static void clearMediaOwner(Uri uri, int userId) throws Exception {
        final String cmd = String.format(
                "content update --uri %s --user %d --bind owner_package_name:n:", uri, userId);
        ShellUtils.runShellCommand(cmd);
    }

    private static Uri createMj2Video(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.test_video_mj2,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", userId,
                /* isFavorite */ false).first;
    }

    private static Uri createVideo(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.test_video,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", userId,
                /* isFavorite */ false).first;
    }

    private static Pair<Uri, String> getPermissionAndStageMedia(int resId, Uri collectionUri,
            String mimeType, int userId, boolean isFavorite) throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            final Context context = InstrumentationRegistry.getTargetContext();
            final Context userContext = userId == context.getUserId() ? context :
                    context.createPackageContextAsUser("android", /* flags= */ 0,
                            UserHandle.of(userId));
            return stageMedia(resId, collectionUri, mimeType, userContext, isFavorite);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static Pair<Uri, String> stageMedia(int resId, Uri collectionUri, String mimeType,
            Context context, boolean isFavorite) throws IOException {
        final String displayName = DISPLAY_NAME_PREFIX + System.nanoTime();
        final MediaStoreUtils.PendingParams params = new MediaStoreUtils.PendingParams(
                collectionUri, displayName, mimeType);
        params.setIsFavorite(isFavorite);
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (MediaStoreUtils.PendingSession session = MediaStoreUtils.openPending(context,
                pendingUri)) {
            try (InputStream source = context.getResources().openRawResource(resId);
                 OutputStream target = session.openOutputStream()) {
                FileUtils.copy(source, target);
            }
            return new Pair(session.publish(), displayName);
        }
    }

    @NonNull
    public static Uri createSvgImage(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.lg_g4_iso_800_svg,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/svg+xml", userId,
                /* isFavorite */ false).first;
    }

    @NonNull
    public static Uri createImageWithUnknownMimeType(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.lg_g4_iso_800_unknown_mime_type,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", userId,
                /* isFavorite */ false).first;
    }

    @NonNull
    public static Uri createMpegVideo(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.test_video_mpeg,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mpeg", userId,
                /* isFavorite */ false).first;
    }

    @NonNull
    public static Uri createVideoWithUnknownMimeType(int userId) throws Exception {
        return getPermissionAndStageMedia(R.raw.test_video_unknown_mime_type,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", userId,
                /* isFavorite */ false).first;
    }
}
