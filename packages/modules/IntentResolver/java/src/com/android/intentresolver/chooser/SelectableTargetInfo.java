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
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserTarget;
import android.text.SpannableStringBuilder;
import android.util.HashedStringCache;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Live target, currently selectable by the user.
 * @see NotSelectableTargetInfo
 */
public final class SelectableTargetInfo extends ChooserTargetInfo {
    private static final String TAG = "SelectableTargetInfo";

    private interface TargetHashProvider {
        HashedStringCache.HashResult getHashedTargetIdForMetrics(Context context);
    }

    private interface TargetActivityStarter {
        boolean start(Activity activity, Bundle options);
        boolean startAsCaller(Activity activity, Bundle options, int userId);
        boolean startAsUser(Activity activity, Bundle options, UserHandle user);
    }

    private static final String HASHED_STRING_CACHE_TAG = "ChooserActivity";  // For legacy reasons.
    private static final int DEFAULT_SALT_EXPIRATION_DAYS = 7;

    private final int mMaxHashSaltDays = DeviceConfig.getInt(
            DeviceConfig.NAMESPACE_SYSTEMUI,
            SystemUiDeviceConfigFlags.HASH_SALT_MAX_DAYS,
            DEFAULT_SALT_EXPIRATION_DAYS);

    @Nullable
    private final DisplayResolveInfo mSourceInfo;
    @Nullable
    private final ResolveInfo mBackupResolveInfo;
    private final Intent mResolvedIntent;
    private final String mDisplayLabel;
    @Nullable
    private final AppTarget mAppTarget;
    @Nullable
    private final ShortcutInfo mShortcutInfo;

    private final ComponentName mChooserTargetComponentName;
    private final CharSequence mChooserTargetUnsanitizedTitle;
    private final Icon mChooserTargetIcon;
    private final Bundle mChooserTargetIntentExtras;
    private final boolean mIsPinned;
    private final float mModifiedScore;
    private final boolean mIsSuspended;
    private final ComponentName mResolvedComponentName;
    private final Intent mBaseIntentToSend;
    private final ResolveInfo mResolveInfo;
    private final List<Intent> mAllSourceIntents;
    private final IconHolder mDisplayIconHolder = new SettableIconHolder();
    private final TargetHashProvider mHashProvider;
    private final TargetActivityStarter mActivityStarter;

    /**
     * An intent containing referrer URI (see {@link Activity#getReferrer()} (possibly {@code null})
     * in its extended data under the key {@link Intent#EXTRA_REFERRER}.
     */
    private final Intent mReferrerFillInIntent;

    /**
     * Create a new {@link TargetInfo} instance representing a selectable target. Some target
     * parameters are copied over from the (deprecated) legacy {@link ChooserTarget} structure.
     *
     * @deprecated Use the overload that doesn't call for a {@link ChooserTarget}.
     */
    @Deprecated
    public static TargetInfo newSelectableTargetInfo(
            @Nullable DisplayResolveInfo sourceInfo,
            @Nullable ResolveInfo backupResolveInfo,
            Intent resolvedIntent,
            ChooserTarget chooserTarget,
            float modifiedScore,
            @Nullable ShortcutInfo shortcutInfo,
            @Nullable AppTarget appTarget,
            Intent referrerFillInIntent) {
        return newSelectableTargetInfo(
                sourceInfo,
                backupResolveInfo,
                resolvedIntent,
                chooserTarget.getComponentName(),
                chooserTarget.getTitle(),
                chooserTarget.getIcon(),
                chooserTarget.getIntentExtras(),
                modifiedScore,
                shortcutInfo,
                appTarget,
                referrerFillInIntent);
    }

    /**
     * Create a new {@link TargetInfo} instance representing a selectable target. `chooserTarget*`
     * parameters were historically retrieved from (now-deprecated) {@link ChooserTarget} structures
     * even when the {@link TargetInfo} was a system (internal) synthesized target that never needed
     * to be represented as a {@link ChooserTarget}. The values passed here are copied in directly
     * as if they had been provided in the legacy representation.
     *
     * TODO: clarify semantics of how clients use the `getChooserTarget*()` methods; refactor/rename
     * to avoid making reference to the legacy type; and reflect the improved semantics in the
     * signature (and documentation) of this method.
     */
    public static TargetInfo newSelectableTargetInfo(
            @Nullable DisplayResolveInfo sourceInfo,
            @Nullable ResolveInfo backupResolveInfo,
            Intent resolvedIntent,
            ComponentName chooserTargetComponentName,
            CharSequence chooserTargetUnsanitizedTitle,
            Icon chooserTargetIcon,
            @Nullable Bundle chooserTargetIntentExtras,
            float modifiedScore,
            @Nullable ShortcutInfo shortcutInfo,
            @Nullable AppTarget appTarget,
            Intent referrerFillInIntent) {
        return new SelectableTargetInfo(
                sourceInfo,
                backupResolveInfo,
                resolvedIntent,
                null,
                chooserTargetComponentName,
                chooserTargetUnsanitizedTitle,
                chooserTargetIcon,
                chooserTargetIntentExtras,
                modifiedScore,
                shortcutInfo,
                appTarget,
                referrerFillInIntent);
    }

    private SelectableTargetInfo(
            @Nullable DisplayResolveInfo sourceInfo,
            @Nullable ResolveInfo backupResolveInfo,
            Intent resolvedIntent,
            @Nullable Intent baseIntentToSend,
            ComponentName chooserTargetComponentName,
            CharSequence chooserTargetUnsanitizedTitle,
            Icon chooserTargetIcon,
            Bundle chooserTargetIntentExtras,
            float modifiedScore,
            @Nullable ShortcutInfo shortcutInfo,
            @Nullable AppTarget appTarget,
            Intent referrerFillInIntent) {
        mSourceInfo = sourceInfo;
        mBackupResolveInfo = backupResolveInfo;
        mResolvedIntent = resolvedIntent;
        mModifiedScore = modifiedScore;
        mShortcutInfo = shortcutInfo;
        mAppTarget = appTarget;
        mReferrerFillInIntent = referrerFillInIntent;
        mChooserTargetComponentName = chooserTargetComponentName;
        mChooserTargetUnsanitizedTitle = chooserTargetUnsanitizedTitle;
        mChooserTargetIcon = chooserTargetIcon;
        mChooserTargetIntentExtras = chooserTargetIntentExtras;

        mIsPinned = (shortcutInfo != null) && shortcutInfo.isPinned();
        mDisplayLabel = sanitizeDisplayLabel(mChooserTargetUnsanitizedTitle);
        mIsSuspended = (mSourceInfo != null) && mSourceInfo.isSuspended();
        mResolveInfo = (mSourceInfo != null) ? mSourceInfo.getResolveInfo() : mBackupResolveInfo;

        mResolvedComponentName = getResolvedComponentName(mSourceInfo, mBackupResolveInfo);

        mBaseIntentToSend = getBaseIntentToSend(
                baseIntentToSend,
                mResolvedIntent,
                mReferrerFillInIntent);

        mAllSourceIntents = getAllSourceIntents(sourceInfo, mBaseIntentToSend);

        mHashProvider = context -> {
            final String plaintext =
                    getChooserTargetComponentName().getPackageName()
                    + mChooserTargetUnsanitizedTitle;
            return HashedStringCache.getInstance().hashString(
                    context,
                    HASHED_STRING_CACHE_TAG,
                    plaintext,
                    mMaxHashSaltDays);
        };

        mActivityStarter = new TargetActivityStarter() {
            @Override
            public boolean start(Activity activity, Bundle options) {
                throw new RuntimeException("ChooserTargets should be started as caller.");
            }

            @Override
            public boolean startAsCaller(Activity activity, Bundle options, int userId) {
                final Intent intent = mBaseIntentToSend;
                if (intent == null) {
                    return false;
                }
                intent.setComponent(getChooserTargetComponentName());
                intent.putExtras(mChooserTargetIntentExtras);
                TargetInfo.prepareIntentForCrossProfileLaunch(intent, userId);

                // Important: we will ignore the target security checks in ActivityManager if and
                // only if the ChooserTarget's target package is the same package where we got the
                // ChooserTargetService that provided it. This lets a ChooserTargetService provide
                // a non-exported or permission-guarded target for the user to pick.
                //
                // If mSourceInfo is null, we got this ChooserTarget from the caller or elsewhere
                // so we'll obey the caller's normal security checks.
                final boolean ignoreTargetSecurity = (mSourceInfo != null)
                        && mSourceInfo.getResolvedComponentName().getPackageName()
                                .equals(getChooserTargetComponentName().getPackageName());
                activity.startActivityAsCaller(intent, options, ignoreTargetSecurity, userId);
                return true;
            }

            @Override
            public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
                throw new RuntimeException("ChooserTargets should be started as caller.");
            }
        };
    }

    private SelectableTargetInfo(SelectableTargetInfo other, Intent baseIntentToSend) {
        this(
                other.mSourceInfo,
                other.mBackupResolveInfo,
                other.mResolvedIntent,
                baseIntentToSend,
                other.mChooserTargetComponentName,
                other.mChooserTargetUnsanitizedTitle,
                other.mChooserTargetIcon,
                other.mChooserTargetIntentExtras,
                other.mModifiedScore,
                other.mShortcutInfo,
                other.mAppTarget,
                other.mReferrerFillInIntent);
    }

    @Override
    @Nullable
    public TargetInfo tryToCloneWithAppliedRefinement(Intent proposedRefinement) {
        Intent matchingBase =
                getAllSourceIntents()
                        .stream()
                        .filter(i -> i.filterEquals(proposedRefinement))
                        .findFirst()
                        .orElse(null);
        if (matchingBase == null) {
            return null;
        }

        return new SelectableTargetInfo(
                this,
                TargetInfo.mergeRefinementIntoMatchingBaseIntent(matchingBase, proposedRefinement));
    }

    @Override
    public HashedStringCache.HashResult getHashedTargetIdForMetrics(Context context) {
        return mHashProvider.getHashedTargetIdForMetrics(context);
    }

    @Override
    public boolean isSelectableTargetInfo() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return mIsSuspended;
    }

    @Override
    @Nullable
    public DisplayResolveInfo getDisplayResolveInfo() {
        return mSourceInfo;
    }

    @Override
    public float getModifiedScore() {
        return mModifiedScore;
    }

    @Override
    public Intent getResolvedIntent() {
        return mResolvedIntent;
    }

    @Override
    public ComponentName getResolvedComponentName() {
        return mResolvedComponentName;
    }

    @Override
    public ComponentName getChooserTargetComponentName() {
        return mChooserTargetComponentName;
    }

    @Nullable
    public Icon getChooserTargetIcon() {
        return mChooserTargetIcon;
    }

    @Override
    public boolean startAsCaller(Activity activity, Bundle options, int userId) {
        return mActivityStarter.startAsCaller(activity, options, userId);
    }

    @Override
    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        return mActivityStarter.startAsUser(activity, options, user);
    }

    @Nullable
    @Override
    public Intent getTargetIntent() {
        return mBaseIntentToSend;
    }

    @Override
    public ResolveInfo getResolveInfo() {
        return mResolveInfo;
    }

    @Override
    public CharSequence getDisplayLabel() {
        return mDisplayLabel;
    }

    @Override
    public CharSequence getExtendedInfo() {
        // ChooserTargets have badge icons, so we won't show the extended info to disambiguate.
        return null;
    }

    @Override
    public IconHolder getDisplayIconHolder() {
        return mDisplayIconHolder;
    }

    @Override
    @Nullable
    public ShortcutInfo getDirectShareShortcutInfo() {
        return mShortcutInfo;
    }

    @Override
    @Nullable
    public AppTarget getDirectShareAppTarget() {
        return mAppTarget;
    }

    @Override
    public List<Intent> getAllSourceIntents() {
        return mAllSourceIntents;
    }

    @Override
    public boolean isPinned() {
        return mIsPinned;
    }

    private static String sanitizeDisplayLabel(CharSequence label) {
        SpannableStringBuilder sb = new SpannableStringBuilder(label);
        sb.clearSpans();
        return sb.toString();
    }

    private static List<Intent> getAllSourceIntents(
            @Nullable DisplayResolveInfo sourceInfo, Intent fallbackSourceIntent) {
        final List<Intent> results = new ArrayList<>();
        if (sourceInfo != null) {
            results.addAll(sourceInfo.getAllSourceIntents());
        } else {
            // This target wasn't joined to a `DisplayResolveInfo` result from our intent-resolution
            // step, so it was provided directly by the caller. We don't support alternate intents
            // in this case, but we still permit refinement of the intent we'll dispatch; e.g.,
            // clients may use this hook to defer the computation of "lazy" extras in their share
            // payload. Note this accommodation isn't strictly "necessary" because clients could
            // always implement equivalent behavior by pointing custom targets back at their own app
            // for any amount of further refinement/modification outside of the Sharesheet flow;
            // nevertheless, it's offered as a convenience for clients who may expect their normal
            // refinement logic to apply equally in the case of these "special targets."
            results.add(fallbackSourceIntent);
        }
        return results;
    }

    private static ComponentName getResolvedComponentName(
            @Nullable DisplayResolveInfo sourceInfo, ResolveInfo backupResolveInfo) {
        if (sourceInfo != null) {
            return sourceInfo.getResolvedComponentName();
        } else if (backupResolveInfo != null) {
            return new ComponentName(
                    backupResolveInfo.activityInfo.packageName,
                    backupResolveInfo.activityInfo.name);
        }
        return null;
    }

    @Nullable
    private static Intent getBaseIntentToSend(
            @Nullable Intent providedBase,
            @Nullable Intent fallbackBase,
            Intent referrerFillInIntent) {
        Intent result = (providedBase != null) ? providedBase : fallbackBase;
        if (result == null) {
            Log.e(TAG, "ChooserTargetInfo: no base intent available to send");
        } else {
            result = new Intent(result);
            result.fillIn(referrerFillInIntent, 0);
        }
        return result;
    }
}
