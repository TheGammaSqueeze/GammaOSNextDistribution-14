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

package com.android.intentresolver;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.service.chooser.ChooserAction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi;
import com.android.intentresolver.logging.EventLog;
import com.android.intentresolver.widget.ActionRow;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Implementation of {@link ChooserContentPreviewUi.ActionFactory} specialized to the application
 * requirements of Sharesheet / {@link ChooserActivity}.
 */
public final class ChooserActionFactory implements ChooserContentPreviewUi.ActionFactory {
    /** Delegate interface to launch activities when the actions are selected. */
    public interface ActionActivityStarter {
        /**
         * Request an activity launch for the provided target. Implementations may choose to exit
         * the current activity when the target is launched.
         */
        void safelyStartActivityAsPersonalProfileUser(TargetInfo info);

        /**
         * Request an activity launch for the provided target, optionally employing the specified
         * shared element transition. Implementations may choose to exit the current activity when
         * the target is launched.
         */
        default void safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                TargetInfo info, View sharedElement, String sharedElementName) {
            safelyStartActivityAsPersonalProfileUser(info);
        }
    }

    private static final String TAG = "ChooserActions";

    private static final int URI_PERMISSION_INTENT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    // Boolean extra used to inform the editor that it may want to customize the editing experience
    // for the sharesheet editing flow.
    private static final String EDIT_SOURCE = "edit_source";
    private static final String EDIT_SOURCE_SHARESHEET = "sharesheet";

    private static final String CHIP_LABEL_METADATA_KEY = "android.service.chooser.chip_label";
    private static final String CHIP_ICON_METADATA_KEY = "android.service.chooser.chip_icon";

    private static final String IMAGE_EDITOR_SHARED_ELEMENT = "screenshot_preview_image";

    private final Context mContext;

    @Nullable
    private final Runnable mCopyButtonRunnable;
    private final Runnable mEditButtonRunnable;
    private final ImmutableList<ChooserAction> mCustomActions;
    private final @Nullable ChooserAction mModifyShareAction;
    private final Consumer<Boolean> mExcludeSharedTextAction;
    private final Consumer</* @Nullable */ Integer> mFinishCallback;
    private final EventLog mLog;

    /**
     * @param context
     * @param chooserRequest data about the invocation of the current Sharesheet session.
     * device to implement the supported action types.
     * @param onUpdateSharedTextIsExcluded a delegate to be invoked when the "exclude shared text"
     * setting is updated. The argument is whether the shared text is to be excluded.
     * @param firstVisibleImageQuery a delegate that provides a reference to the first visible image
     * View in the Sharesheet UI, if any, or null.
     * @param activityStarter a delegate to launch activities when actions are selected.
     * @param finishCallback a delegate to close the Sharesheet UI (e.g. because some action was
     * completed).
     */
    public ChooserActionFactory(
            Context context,
            ChooserRequestParameters chooserRequest,
            ChooserIntegratedDeviceComponents integratedDeviceComponents,
            EventLog log,
            Consumer<Boolean> onUpdateSharedTextIsExcluded,
            Callable</* @Nullable */ View> firstVisibleImageQuery,
            ActionActivityStarter activityStarter,
            Consumer</* @Nullable */ Integer> finishCallback) {
        this(
                context,
                makeCopyButtonRunnable(
                        context,
                        chooserRequest.getTargetIntent(),
                        chooserRequest.getReferrerPackageName(),
                        finishCallback,
                        log),
                makeEditButtonRunnable(
                        getEditSharingTarget(
                                context,
                                chooserRequest.getTargetIntent(),
                                integratedDeviceComponents),
                        firstVisibleImageQuery,
                        activityStarter,
                        log),
                chooserRequest.getChooserActions(),
                chooserRequest.getModifyShareAction(),
                onUpdateSharedTextIsExcluded,
                log,
                finishCallback);
    }

    @VisibleForTesting
    ChooserActionFactory(
            Context context,
            @Nullable Runnable copyButtonRunnable,
            Runnable editButtonRunnable,
            List<ChooserAction> customActions,
            @Nullable ChooserAction modifyShareAction,
            Consumer<Boolean> onUpdateSharedTextIsExcluded,
            EventLog log,
            Consumer</* @Nullable */ Integer> finishCallback) {
        mContext = context;
        mCopyButtonRunnable = copyButtonRunnable;
        mEditButtonRunnable = editButtonRunnable;
        mCustomActions = ImmutableList.copyOf(customActions);
        mModifyShareAction = modifyShareAction;
        mExcludeSharedTextAction = onUpdateSharedTextIsExcluded;
        mLog = log;
        mFinishCallback = finishCallback;
    }

    @Override
    @Nullable
    public Runnable getEditButtonRunnable() {
        return mEditButtonRunnable;
    }

    @Override
    @Nullable
    public Runnable getCopyButtonRunnable() {
        return mCopyButtonRunnable;
    }

    /** Create custom actions */
    @Override
    public List<ActionRow.Action> createCustomActions() {
        List<ActionRow.Action> actions = new ArrayList<>();
        for (int i = 0; i < mCustomActions.size(); i++) {
            final int position = i;
            ActionRow.Action actionRow = createCustomAction(
                    mContext,
                    mCustomActions.get(i),
                    mFinishCallback,
                    () -> {
                        mLog.logCustomActionSelected(position);
                    }
            );
            if (actionRow != null) {
                actions.add(actionRow);
            }
        }
        return actions;
    }

    /**
     * Provides a share modification action, if any.
     */
    @Override
    @Nullable
    public ActionRow.Action getModifyShareAction() {
        return createCustomAction(
                mContext,
                mModifyShareAction,
                mFinishCallback,
                () -> {
                    mLog.logActionSelected(EventLog.SELECTION_TYPE_MODIFY_SHARE);
                });
    }

    /**
     * <p>
     * Creates an exclude-text action that can be called when the user changes shared text
     * status in the Media + Text preview.
     * </p>
     * <p>
     * <code>true</code> argument value indicates that the text should be excluded.
     * </p>
     */
    @Override
    public Consumer<Boolean> getExcludeSharedTextAction() {
        return mExcludeSharedTextAction;
    }

    @Nullable
    private static Runnable makeCopyButtonRunnable(
            Context context,
            Intent targetIntent,
            String referrerPackageName,
            Consumer<Integer> finishCallback,
            EventLog log) {
        final ClipData clipData;
        try {
            clipData = extractTextToCopy(targetIntent);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to extract data to copy", t);
            return null;
        }
        if (clipData == null) {
            return null;
        }
        return () -> {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClipAsPackage(clipData, referrerPackageName);

            log.logActionSelected(EventLog.SELECTION_TYPE_COPY);
            finishCallback.accept(Activity.RESULT_OK);
        };
    }

    @Nullable
    private static ClipData extractTextToCopy(Intent targetIntent) {
        if (targetIntent == null) {
            return null;
        }

        final String action = targetIntent.getAction();

        ClipData clipData = null;
        if (Intent.ACTION_SEND.equals(action)) {
            String extraText = targetIntent.getStringExtra(Intent.EXTRA_TEXT);

            if (extraText != null) {
                clipData = ClipData.newPlainText(null, extraText);
            } else {
                Log.w(TAG, "No data available to copy to clipboard");
            }
        } else {
            // expected to only be visible with ACTION_SEND (when a text is shared)
            Log.d(TAG, "Action (" + action + ") not supported for copying to clipboard");
        }
        return clipData;
    }

    private static TargetInfo getEditSharingTarget(
            Context context,
            Intent originalIntent,
            ChooserIntegratedDeviceComponents integratedComponents) {
        final ComponentName editorComponent = integratedComponents.getEditSharingComponent();

        final Intent resolveIntent = new Intent(originalIntent);
        // Retain only URI permission grant flags if present. Other flags may prevent the scene
        // transition animation from running (i.e FLAG_ACTIVITY_NO_ANIMATION,
        // FLAG_ACTIVITY_NEW_TASK, FLAG_ACTIVITY_NEW_DOCUMENT) but also not needed.
        resolveIntent.setFlags(originalIntent.getFlags() & URI_PERMISSION_INTENT_FLAGS);
        resolveIntent.setComponent(editorComponent);
        resolveIntent.setAction(Intent.ACTION_EDIT);
        resolveIntent.putExtra(EDIT_SOURCE, EDIT_SOURCE_SHARESHEET);
        String originalAction = originalIntent.getAction();
        if (Intent.ACTION_SEND.equals(originalAction)) {
            if (resolveIntent.getData() == null) {
                Uri uri = resolveIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    String mimeType = context.getContentResolver().getType(uri);
                    resolveIntent.setDataAndType(uri, mimeType);
                }
            }
        } else {
            Log.e(TAG, originalAction + " is not supported.");
            return null;
        }
        final ResolveInfo ri = context.getPackageManager().resolveActivity(
                resolveIntent, PackageManager.GET_META_DATA);
        if (ri == null || ri.activityInfo == null) {
            Log.e(TAG, "Device-specified editor (" + editorComponent + ") not available");
            return null;
        }

        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                ri,
                context.getString(R.string.screenshot_edit),
                "",
                resolveIntent);
        dri.getDisplayIconHolder().setDisplayIcon(
                context.getDrawable(com.android.internal.R.drawable.ic_screenshot_edit));
        return dri;
    }

    private static Runnable makeEditButtonRunnable(
            TargetInfo editSharingTarget,
            Callable</* @Nullable */ View> firstVisibleImageQuery,
            ActionActivityStarter activityStarter,
            EventLog log) {
        return () -> {
            // Log share completion via edit.
            log.logActionSelected(EventLog.SELECTION_TYPE_EDIT);

            View firstImageView = null;
            try {
                firstImageView = firstVisibleImageQuery.call();
            } catch (Exception e) { /* ignore */ }
            // Action bar is user-independent; always start as primary.
            if (firstImageView == null) {
                activityStarter.safelyStartActivityAsPersonalProfileUser(editSharingTarget);
            } else {
                activityStarter.safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                        editSharingTarget, firstImageView, IMAGE_EDITOR_SHARED_ELEMENT);
            }
        };
    }

    @Nullable
    private static ActionRow.Action createCustomAction(
            Context context,
            ChooserAction action,
            Consumer<Integer> finishCallback,
            Runnable loggingRunnable) {
        if (action == null || action.getAction() == null) {
            return null;
        }
        Drawable icon = action.getIcon().loadDrawable(context);
        if (icon == null && TextUtils.isEmpty(action.getLabel())) {
            return null;
        }
        return new ActionRow.Action(
                action.getLabel(),
                icon,
                () -> {
                    try {
                        action.getAction().send(
                                null,
                                0,
                                null,
                                null,
                                null,
                                null,
                                ActivityOptions.makeCustomAnimation(
                                                context,
                                                R.anim.slide_in_right,
                                                R.anim.slide_out_left)
                                        .toBundle());
                    } catch (PendingIntent.CanceledException e) {
                        Log.d(TAG, "Custom action, " + action.getLabel() + ", has been cancelled");
                    }
                    if (loggingRunnable != null) {
                        loggingRunnable.run();
                    }
                    finishCallback.accept(Activity.RESULT_OK);
                }
        );
    }
}
