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

package android.platform.test.rule;

import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IGoogleCameraHelper2;
import android.platform.helpers.IPhotosHelper;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** This rule allows to execute CUJ while new picures uploading in cloud. */
public class PhotoUploadRule extends TestWatcher {

    @VisibleForTesting static final String PHOTO_COUNT = "photo-count";
    int photoCount = 5;

    @VisibleForTesting static final String TAKE_PHOTO_DELAY = "take-photo-delay";
    long takePhotoDelay = 1000;

    @VisibleForTesting static final String PHOTO_TIMEOUT = "photo-timeout";
    long photoTimeout = 10000;

    @VisibleForTesting static final String UPLOAD_PHOTO = "upload-photo";
    boolean uploadPhoto = true;

    @VisibleForTesting static final String UPLOAD_VIDEO = "upload-video";
    boolean uploadVideo = false;

    @VisibleForTesting static final String CAPTURE_VIDEO_DURATION = "capture-video-duration";
    long captureVideoDuration = 1200000;

    private static HelperAccessor<IPhotosHelper> sPhotosHelper =
            new HelperAccessor<>(IPhotosHelper.class);

    private static HelperAccessor<IGoogleCameraHelper2> sGoogleCameraHelper =
            new HelperAccessor<>(IGoogleCameraHelper2.class);

    @Override
    protected void starting(Description description) {
        photoCount = Integer.valueOf(getArguments().getString(PHOTO_COUNT, String.valueOf(5)));
        photoTimeout = Long.valueOf(getArguments().getString(PHOTO_TIMEOUT, String.valueOf(10000)));
        takePhotoDelay =
                Long.valueOf(getArguments().getString(TAKE_PHOTO_DELAY, String.valueOf(1000)));
        captureVideoDuration =
                Long.valueOf(
                        getArguments().getString(CAPTURE_VIDEO_DURATION, String.valueOf(30000)));
        uploadPhoto = Boolean.valueOf(getArguments().getString(UPLOAD_PHOTO, String.valueOf(true)));
        uploadVideo =
                Boolean.valueOf(getArguments().getString(UPLOAD_VIDEO, String.valueOf(false)));

        sPhotosHelper.get().open();
        sPhotosHelper.get().disableBackupMode();
        sGoogleCameraHelper.get().open();
        if (uploadPhoto) {
            sGoogleCameraHelper.get().takeMultiplePhotos(photoCount, takePhotoDelay);
            SystemClock.sleep(photoTimeout);
        }
        if (uploadVideo) {
            sGoogleCameraHelper.get().clickVideoTab();
            sGoogleCameraHelper.get().clickCameraVideoButton();
            SystemClock.sleep(captureVideoDuration);
            sGoogleCameraHelper.get().clickCameraVideoButton();
        }
        sPhotosHelper.get().open();
        sPhotosHelper.get().enableBackupMode();
        sPhotosHelper.get().verifyContentStartedUploading();
        sPhotosHelper.get().exit();
    }

    @Override
    protected void finished(Description description) {
        sPhotosHelper.get().open();
        sPhotosHelper.get().removeContent();
        sPhotosHelper.get().disableBackupMode();
        sPhotosHelper.get().exit();
    }
}
