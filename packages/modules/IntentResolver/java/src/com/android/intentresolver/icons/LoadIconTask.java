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

package com.android.intentresolver.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Trace;
import android.os.UserHandle;
import android.util.Log;

import com.android.intentresolver.TargetPresentationGetter;
import com.android.intentresolver.chooser.DisplayResolveInfo;

import java.util.function.Consumer;

class LoadIconTask extends BaseLoadIconTask {
    private static final String TAG = "IconTask";
    protected final DisplayResolveInfo mDisplayResolveInfo;
    private final UserHandle mUserHandle;
    private final ResolveInfo mResolveInfo;

    LoadIconTask(
            Context context, DisplayResolveInfo dri,
            UserHandle userHandle,
            TargetPresentationGetter.Factory presentationFactory,
            Consumer<Drawable> callback) {
        super(context, presentationFactory, callback);
        mUserHandle = userHandle;
        mDisplayResolveInfo = dri;
        mResolveInfo = dri.getResolveInfo();
    }

    @Override
    protected Drawable doInBackground(Void... params) {
        Trace.beginSection("app-icon");
        try {
            return loadIconForResolveInfo(mResolveInfo);
        } catch (Exception e) {
            ComponentName componentName = mDisplayResolveInfo.getResolvedComponentName();
            Log.e(TAG, "Failed to load app icon for " + componentName, e);
            return loadIconPlaceholder();
        } finally {
            Trace.endSection();
        }
    }

    protected final Drawable loadIconForResolveInfo(ResolveInfo ri) {
        // Load icons based on userHandle from ResolveInfo. If in work profile/clone profile, icons
        // should be badged.
        return mPresentationFactory.makePresentationGetter(ri).getIcon(ri.userHandle);
    }

}
