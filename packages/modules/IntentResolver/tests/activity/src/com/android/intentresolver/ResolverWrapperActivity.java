/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.icons.LabelInfo;
import com.android.intentresolver.icons.TargetDataLoader;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * Simple wrapper around chooser activity to be able to initiate it under test
 */
public class ResolverWrapperActivity extends ResolverActivity {
    static final OverrideData sOverrides = new OverrideData();

    private final CountingIdlingResource mLabelIdlingResource =
            new CountingIdlingResource("LoadLabelTask");

    public ResolverWrapperActivity() {
        super(/* isIntentPicker= */ true);
    }

    public CountingIdlingResource getLabelIdlingResource() {
        return mLabelIdlingResource;
    }

    @Override
    public ResolverListAdapter createResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            UserHandle userHandle,
            TargetDataLoader targetDataLoader) {
        return new ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                payloadIntents.get(0),  // TODO: extract upstream
                this,
                userHandle,
                new TargetDataLoaderWrapper(targetDataLoader, mLabelIdlingResource));
    }

    @Override
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        if (sOverrides.mCrossProfileIntentsChecker != null) {
            return sOverrides.mCrossProfileIntentsChecker;
        }
        return super.createCrossProfileIntentsChecker();
    }

    @Override
    protected WorkProfileAvailabilityManager createWorkProfileAvailabilityManager() {
        if (sOverrides.mWorkProfileAvailability != null) {
            return sOverrides.mWorkProfileAvailability;
        }
        return super.createWorkProfileAvailabilityManager();
    }

    ResolverListAdapter getAdapter() {
        return mMultiProfilePagerAdapter.getActiveListAdapter();
    }

    ResolverListAdapter getPersonalListAdapter() {
        return ((ResolverListAdapter) mMultiProfilePagerAdapter.getAdapterForIndex(0));
    }

    ResolverListAdapter getWorkListAdapter() {
        if (mMultiProfilePagerAdapter.getInactiveListAdapter() == null) {
            return null;
        }
        return ((ResolverListAdapter) mMultiProfilePagerAdapter.getAdapterForIndex(1));
    }

    @Override
    public boolean isVoiceInteraction() {
        if (sOverrides.isVoiceInteraction != null) {
            return sOverrides.isVoiceInteraction;
        }
        return super.isVoiceInteraction();
    }

    @Override
    public void safelyStartActivityInternal(TargetInfo cti, UserHandle user,
            @Nullable Bundle options) {
        if (sOverrides.onSafelyStartInternalCallback != null
                && sOverrides.onSafelyStartInternalCallback.apply(new Pair<>(cti, user))) {
            return;
        }
        super.safelyStartActivityInternal(cti, user, options);
    }

    @Override
    protected ResolverListController createListController(UserHandle userHandle) {
        if (userHandle == UserHandle.SYSTEM) {
            return sOverrides.resolverListController;
        }
        return sOverrides.workResolverListController;
    }

    @Override
    public PackageManager getPackageManager() {
        if (sOverrides.createPackageManager != null) {
            return sOverrides.createPackageManager.apply(super.getPackageManager());
        }
        return super.getPackageManager();
    }

    protected UserHandle getCurrentUserHandle() {
        return mMultiProfilePagerAdapter.getCurrentUserHandle();
    }

    @Override
    protected AnnotatedUserHandles computeAnnotatedUserHandles() {
        return sOverrides.annotatedUserHandles;
    }
    @Override
    public void startActivityAsUser(
            @NonNull Intent intent,
            Bundle options,
            @NonNull UserHandle user
    ) {
        super.startActivityAsUser(intent, options, user);
    }

    @Override
    protected List<UserHandle> getResolverRankerServiceUserHandleListInternal(UserHandle
            userHandle) {
        return super.getResolverRankerServiceUserHandleListInternal(userHandle);
    }

    /**
     * We cannot directly mock the activity created since instrumentation creates it.
     * <p>
     * Instead, we use static instances of this object to modify behavior.
     */
    static class OverrideData {
        @SuppressWarnings("Since15")
        public Function<PackageManager, PackageManager> createPackageManager;
        public Function<Pair<TargetInfo, UserHandle>, Boolean> onSafelyStartInternalCallback;
        public ResolverListController resolverListController;
        public ResolverListController workResolverListController;
        public Boolean isVoiceInteraction;
        public AnnotatedUserHandles annotatedUserHandles;
        public Integer myUserId;
        public boolean hasCrossProfileIntents;
        public boolean isQuietModeEnabled;
        public WorkProfileAvailabilityManager mWorkProfileAvailability;
        public CrossProfileIntentsChecker mCrossProfileIntentsChecker;

        public void reset() {
            onSafelyStartInternalCallback = null;
            isVoiceInteraction = null;
            createPackageManager = null;
            resolverListController = mock(ResolverListController.class);
            workResolverListController = mock(ResolverListController.class);
            annotatedUserHandles = AnnotatedUserHandles.newBuilder()
                    .setUserIdOfCallingApp(1234)  // Must be non-negative.
                    .setUserHandleSharesheetLaunchedAs(UserHandle.SYSTEM)
                    .setPersonalProfileUserHandle(UserHandle.SYSTEM)
                    .build();
            myUserId = null;
            hasCrossProfileIntents = true;
            isQuietModeEnabled = false;

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

            mCrossProfileIntentsChecker = mock(CrossProfileIntentsChecker.class);
            when(mCrossProfileIntentsChecker.hasCrossProfileIntents(any(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> hasCrossProfileIntents);
        }
    }

    private static class TargetDataLoaderWrapper extends TargetDataLoader {
        private final TargetDataLoader mTargetDataLoader;
        private final CountingIdlingResource mLabelIdlingResource;

        private TargetDataLoaderWrapper(
                TargetDataLoader targetDataLoader, CountingIdlingResource labelIdlingResource) {
            mTargetDataLoader = targetDataLoader;
            mLabelIdlingResource = labelIdlingResource;
        }

        @Override
        public void loadAppTargetIcon(
                @NonNull DisplayResolveInfo info,
                @NonNull UserHandle userHandle,
                @NonNull Consumer<Drawable> callback) {
            mTargetDataLoader.loadAppTargetIcon(info, userHandle, callback);
        }

        @Override
        public void loadDirectShareIcon(
                @NonNull SelectableTargetInfo info,
                @NonNull UserHandle userHandle,
                @NonNull Consumer<Drawable> callback) {
            mTargetDataLoader.loadDirectShareIcon(info, userHandle, callback);
        }

        @Override
        public void loadLabel(
                @NonNull DisplayResolveInfo info,
                @NonNull Consumer<LabelInfo> callback) {
            mLabelIdlingResource.increment();
            mTargetDataLoader.loadLabel(
                    info,
                    (result) -> {
                        mLabelIdlingResource.decrement();
                        callback.accept(result);
                    });
        }

        @Override
        public void getOrLoadLabel(@NonNull DisplayResolveInfo info) {
            mTargetDataLoader.getOrLoadLabel(info);
        }
    }
}
