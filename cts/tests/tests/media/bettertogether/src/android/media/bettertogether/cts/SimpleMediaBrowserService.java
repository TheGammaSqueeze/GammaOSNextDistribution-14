/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.bettertogether.cts;

import android.media.browse.MediaBrowser.MediaItem;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.service.media.MediaBrowserService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** (@link MediaBrowserService} that does not set a session by default. */
public class SimpleMediaBrowserService extends MediaBrowserService {

    public static final AtomicReference<MediaBrowserService> sInstance = new AtomicReference<>();
    public static final ConditionVariable sInstanceInitializedCondition = new ConditionVariable();

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance.set(this);
        sInstanceInitializedCondition.open();
    }

    @Override
    public void onDestroy() {
        sInstanceInitializedCondition.close();
        sInstance.set(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(
            @NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("placeholder root id", /* extras= */ null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaItem>> result) {}
}
