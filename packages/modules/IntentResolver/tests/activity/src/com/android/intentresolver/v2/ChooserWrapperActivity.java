/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver.v2;

import android.annotation.Nullable;
import android.app.prediction.AppPredictor;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.lifecycle.ViewModelProvider;

import com.android.intentresolver.ChooserListAdapter;
import com.android.intentresolver.IChooserWrapper;
import com.android.intentresolver.ResolverListController;
import com.android.intentresolver.TestContentPreviewViewModel;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Simple wrapper around chooser activity to be able to initiate it under test. For more
 * information, see {@code com.android.internal.app.ChooserWrapperActivity}.
 */
public class ChooserWrapperActivity extends ChooserActivity implements IChooserWrapper {
    static final ChooserActivityOverrideData sOverrides = ChooserActivityOverrideData.getInstance();
    private UsageStatsManager mUsm;

    @Override
    protected final ChooserActivityLogic createActivityLogic() {
        return new TestChooserActivityLogic(
                "ChooserWrapper",
                /* activity = */ this,
                this::onWorkProfileStatusUpdated,
                sOverrides.annotatedUserHandles,
                sOverrides.mWorkProfileAvailability);
    }

    @Override
    public ChooserListAdapter createChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            Intent referrerFillInIntent,
            int maxTargetsPerRow) {

        return new ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                targetIntent,
                referrerFillInIntent,
                this,
                mPackageManager,
                getEventLog(),
                maxTargetsPerRow,
                userHandle,
                mTargetDataLoader,
                null,
                mFeatureFlags);
    }

    @Override
    public ChooserListAdapter getAdapter() {
        return mChooserMultiProfilePagerAdapter.getActiveListAdapter();
    }

    @Override
    public ChooserListAdapter getPersonalListAdapter() {
        return mChooserMultiProfilePagerAdapter.getPersonalListAdapter();
    }

    @Override
    public ChooserListAdapter getWorkListAdapter() {
        return mChooserMultiProfilePagerAdapter.getWorkListAdapter();
    }

    @Override
    public boolean getIsSelected() {
        return mIsSuccessfullySelected;
    }

    @Override
    public UsageStatsManager getUsageStatsManager() {
        if (mUsm == null) {
            mUsm = getSystemService(UsageStatsManager.class);
        }
        return mUsm;
    }

    @Override
    public boolean isVoiceInteraction() {
        if (sOverrides.isVoiceInteraction != null) {
            return sOverrides.isVoiceInteraction;
        }
        return super.isVoiceInteraction();
    }

    @Override
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        if (sOverrides.mCrossProfileIntentsChecker != null) {
            return sOverrides.mCrossProfileIntentsChecker;
        }
        return super.createCrossProfileIntentsChecker();
    }

    @Override
    public void safelyStartActivityInternal(TargetInfo cti, UserHandle user,
            @Nullable Bundle options) {
        if (sOverrides.onSafelyStartInternalCallback != null
                && sOverrides.onSafelyStartInternalCallback.apply(cti)) {
            return;
        }
        super.safelyStartActivityInternal(cti, user, options);
    }

    @Override
    protected ChooserListController createListController(UserHandle userHandle) {
        if (userHandle == UserHandle.SYSTEM) {
            return sOverrides.resolverListController;
        }
        return sOverrides.workResolverListController;
    }

    @Override
    public Resources getResources() {
        if (sOverrides.resources != null) {
            return sOverrides.resources;
        }
        return super.getResources();
    }

    @Override
    protected ViewModelProvider.Factory createPreviewViewModelFactory() {
        return TestContentPreviewViewModel.Companion.wrap(
                super.createPreviewViewModelFactory(),
                sOverrides.imageLoader);
    }

    @Override
    public Cursor queryResolver(ContentResolver resolver, Uri uri) {
        if (sOverrides.resolverCursor != null) {
            return sOverrides.resolverCursor;
        }

        if (sOverrides.resolverForceException) {
            throw new SecurityException("Test exception handling");
        }

        return super.queryResolver(resolver, uri);
    }

    @Override
    protected boolean isWorkProfile() {
        if (sOverrides.alternateProfileSetting != 0) {
            return sOverrides.alternateProfileSetting == MetricsEvent.MANAGED_PROFILE;
        }
        return super.isWorkProfile();
    }

    @Override
    public DisplayResolveInfo createTestDisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo pri,
            CharSequence pLabel,
            CharSequence pInfo,
            Intent replacementIntent) {
        return DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                pri,
                pLabel,
                pInfo,
                replacementIntent);
    }

    @Override
    public UserHandle getCurrentUserHandle() {
        return mChooserMultiProfilePagerAdapter.getCurrentUserHandle();
    }

    @Override
    public Context createContextAsUser(UserHandle user, int flags) {
        // return the current context as a work profile doesn't really exist in these tests
        return this;
    }

    @Override
    protected ShortcutLoader createShortcutLoader(
            Context context,
            AppPredictor appPredictor,
            UserHandle userHandle,
            IntentFilter targetIntentFilter,
            Consumer<ShortcutLoader.Result> callback) {
        ShortcutLoader shortcutLoader =
                sOverrides.shortcutLoaderFactory.invoke(userHandle, callback);
        if (shortcutLoader != null) {
            return shortcutLoader;
        }
        return super.createShortcutLoader(
                context, appPredictor, userHandle, targetIntentFilter, callback);
    }
}
