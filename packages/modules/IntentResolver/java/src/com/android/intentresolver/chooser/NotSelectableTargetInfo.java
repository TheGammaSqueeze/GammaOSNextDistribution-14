/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.intentresolver.chooser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.intentresolver.R;

import java.util.function.Supplier;

/**
 * Distinguish between targets that selectable by the user, vs those that are
 * placeholders for the system while information is loading in an async manner.
 */
public final class NotSelectableTargetInfo {
    /** Create a non-selectable {@link TargetInfo} with no content. */
    public static TargetInfo newEmptyTargetInfo() {
        return ImmutableTargetInfo.newBuilder()
                .setLegacyType(ImmutableTargetInfo.LegacyTargetType.EMPTY_TARGET_INFO)
                .setDisplayIconHolder(makeReadOnlyIconHolder(() -> null))
                .setActivityStarter(makeNoOpActivityStarter())
                .build();
    }

    /**
     * Create a non-selectable {@link TargetInfo} with placeholder content to be displayed
     * unless/until it can be replaced by the result of a pending asynchronous load.
     */
    public static TargetInfo newPlaceHolderTargetInfo(Context context) {
        return ImmutableTargetInfo.newBuilder()
                .setLegacyType(ImmutableTargetInfo.LegacyTargetType.PLACEHOLDER_TARGET_INFO)
                .setDisplayIconHolder(
                        makeReadOnlyIconHolder(() -> makeStartedPlaceholderDrawable(context)))
                .setActivityStarter(makeNoOpActivityStarter())
                .build();
    }

    private static Drawable makeStartedPlaceholderDrawable(Context context) {
        AnimatedVectorDrawable avd = (AnimatedVectorDrawable) context.getDrawable(
                R.drawable.chooser_direct_share_icon_placeholder);
        avd.start();  // Start animation after generation.
        return avd;
    }

    private static ImmutableTargetInfo.IconHolder makeReadOnlyIconHolder(
            Supplier</* @Nullable */ Drawable> iconProvider) {
        return new ImmutableTargetInfo.IconHolder() {
            @Override
            @Nullable
            public Drawable getDisplayIcon() {
                return iconProvider.get();
            }

            @Override
            public void setDisplayIcon(Drawable icon) {}
        };
    }

    private static ImmutableTargetInfo.TargetActivityStarter makeNoOpActivityStarter() {
        return new ImmutableTargetInfo.TargetActivityStarter() {
            @Override
            public boolean startAsCaller(
                    TargetInfo target, Activity activity, Bundle options, int userId) {
                return false;
            }

            @Override
            public boolean startAsUser(
                    TargetInfo target, Activity activity, Bundle options, UserHandle user) {
                return false;
            }
        };
    }

    // TODO: merge all the APIs up to a single `TargetInfo` class.
    private NotSelectableTargetInfo() {}
}
