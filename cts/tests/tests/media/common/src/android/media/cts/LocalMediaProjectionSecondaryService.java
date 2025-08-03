/*
 * Copyright 2023 The Android Open Source Project
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
package android.media.cts;

/**
 * Helper foreground service for the media projection test, it runs on a 2nd process.
 */
public class LocalMediaProjectionSecondaryService extends LocalMediaProjectionService {

    private static final String NOTIFICATION_CHANNEL_ID = "AudioPlaybackCaptureTest2";
    private static final String CHANNEL_NAME = "ProjectionService2";
    private static final int NOTIFICATION_ID = 2;

    @Override
    int getNotificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    @Override
    String getNotificationChannelName() {
        return CHANNEL_NAME;
    }
}
