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

package com.android.intentresolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.UserHandle;

import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.contentpreview.ImageLoader;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.shortcuts.ShortcutLoader;

import java.util.function.Consumer;
import java.util.function.Function;

import kotlin.jvm.functions.Function2;

/**
 * Singleton providing overrides to be applied by any {@code IChooserWrapper} used in testing.
 * We cannot directly mock the activity created since instrumentation creates it, so instead we use
 * this singleton to modify behavior.
 */
public class ChooserActivityOverrideData {
    private static ChooserActivityOverrideData sInstance = null;

    public static ChooserActivityOverrideData getInstance() {
        if (sInstance == null) {
            sInstance = new ChooserActivityOverrideData();
        }
        return sInstance;
    }

    @SuppressWarnings("Since15")
    public Function<PackageManager, PackageManager> createPackageManager;
    public Function<TargetInfo, Boolean> onSafelyStartInternalCallback;
    public Function<TargetInfo, Boolean> onSafelyStartCallback;
    public Function2<UserHandle, Consumer<ShortcutLoader.Result>, ShortcutLoader>
            shortcutLoaderFactory = (userHandle, callback) -> null;
    public ChooserActivity.ChooserListController resolverListController;
    public ChooserActivity.ChooserListController workResolverListController;
    public Boolean isVoiceInteraction;
    public Cursor resolverCursor;
    public boolean resolverForceException;
    public ImageLoader imageLoader;
    public int alternateProfileSetting;
    public Resources resources;
    public AnnotatedUserHandles annotatedUserHandles;
    public boolean hasCrossProfileIntents;
    public boolean isQuietModeEnabled;
    public Integer myUserId;
    public WorkProfileAvailabilityManager mWorkProfileAvailability;
    public CrossProfileIntentsChecker mCrossProfileIntentsChecker;
    public PackageManager packageManager;

    public void reset() {
        onSafelyStartInternalCallback = null;
        isVoiceInteraction = null;
        createPackageManager = null;
        imageLoader = null;
        resolverCursor = null;
        resolverForceException = false;
        resolverListController = mock(ChooserActivity.ChooserListController.class);
        workResolverListController = mock(ChooserActivity.ChooserListController.class);
        alternateProfileSetting = 0;
        resources = null;
        annotatedUserHandles = AnnotatedUserHandles.newBuilder()
                    .setUserIdOfCallingApp(1234)  // Must be non-negative.
                    .setUserHandleSharesheetLaunchedAs(UserHandle.SYSTEM)
                    .setPersonalProfileUserHandle(UserHandle.SYSTEM)
                    .build();
        hasCrossProfileIntents = true;
        isQuietModeEnabled = false;
        myUserId = null;
        packageManager = null;
        mWorkProfileAvailability = new WorkProfileAvailabilityManager(null, null, null) {
            @Override
            public boolean isQuietModeEnabled() {
                return isQuietModeEnabled;
            }

            @Override
            public boolean isWorkProfileUserUnlocked() {
                return true;
            }

            @Override
            public void requestQuietModeEnabled(boolean enabled) {
                isQuietModeEnabled = enabled;
            }

            @Override
            public void markWorkProfileEnabledBroadcastReceived() {}

            @Override
            public boolean isWaitingToEnableWorkProfile() {
                return false;
            }
        };
        shortcutLoaderFactory = ((userHandle, resultConsumer) -> null);

        mCrossProfileIntentsChecker = mock(CrossProfileIntentsChecker.class);
        when(mCrossProfileIntentsChecker.hasCrossProfileIntents(any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> hasCrossProfileIntents);
    }

    private ChooserActivityOverrideData() {}
}

