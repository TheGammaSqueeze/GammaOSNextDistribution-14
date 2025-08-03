/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.documentsui.picker;

import static android.provider.DocumentsContract.isDocumentUri;
import static android.provider.DocumentsContract.isRootUri;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.ActivityConfig;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.Injector;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.picker.ActionHandler.Addons;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.util.FileUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * Provides {@link PickActivity} action specializations to fragments.
 */
class ActionHandler<T extends FragmentActivity & Addons> extends AbstractActionHandler<T> {

    private static final String TAG = "PickerActionHandler";

    /**
     * Used to prevent applications from using {@link Intent#ACTION_OPEN_DOCUMENT_TREE} and
     * the {@link Intent#ACTION_OPEN_DOCUMENT} actions to request that the user select individual
     * files from "/Android/data", "/Android/obb", "/Android/sandbox" directories and all their
     * subdirectories (on the external storage), in accordance with the SAF privacy restrictions
     * introduced in Android 11 (R).
     *
     * <p>
     * See <a href="https://developer.android.com/about/versions/11/privacy/storage#file-access">
     * Storage updates in Android 11</a>.
     */
    private static final Pattern PATTERN_RESTRICTED_INITIAL_PATH =
            Pattern.compile("^/Android/(?:data|obb|sandbox).*", CASE_INSENSITIVE);

    private final Features mFeatures;
    private final ActivityConfig mConfig;
    private final LastAccessedStorage mLastAccessed;
    private UpdatePickResultTask mUpdatePickResultTask;

    ActionHandler(
            T activity,
            State state,
            ProvidersAccess providers,
            DocumentsAccess docs,
            SearchViewManager searchMgr,
            Lookup<String, Executor> executors,
            Injector injector,
            LastAccessedStorage lastAccessed) {
        super(activity, state, providers, docs, searchMgr, executors, injector);

        mConfig = injector.config;
        mFeatures = injector.features;
        mLastAccessed = lastAccessed;
        mUpdatePickResultTask = new UpdatePickResultTask(
                activity.getApplicationContext(), mInjector.pickResult);
    }

    @Override
    public void initLocation(Intent intent) {
        assert (intent != null);

        // stack is initialized if it's restored from bundle, which means we're restoring a
        // previously stored state.
        if (mState.stack.isInitialized()) {
            if (DEBUG) {
                Log.d(TAG, "Stack already resolved for uri: " + intent.getData());
            }
            restoreRootAndDirectory();
            return;
        }

        if (launchHomeForCopyDestination(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launching directly into Home directory for copy destination.");
            }
            return;
        }

        if (mFeatures.isLaunchToDocumentEnabled() && launchToInitialUri(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to initial uri.");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Load last accessed stack.");
        }
        initLoadLastAccessedStack();
    }

    @Override
    protected void launchToDefaultLocation() {
        loadLastAccessedStack();
    }

    private boolean launchHomeForCopyDestination(Intent intent) {
        // As a matter of policy we don't load the last used stack for the copy
        // destination picker (user is already in Files app).
        // Consensus was that the experice was too confusing.
        // In all other cases, where the user is visiting us from another app
        // we restore the stack as last used from that app.
        if (Shared.ACTION_PICK_COPY_DESTINATION.equals(intent.getAction())) {
            loadHomeDir();
            return true;
        }

        return false;
    }

    private boolean launchToInitialUri(Intent intent) {
        final Uri initialUri = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI);
        if (initialUri == null) {
            return false;
        }

        final boolean isRoot = isRootUri(mActivity, initialUri);
        final boolean isDocument = !isRoot && isDocumentUri(mActivity, initialUri);

        if (!isRoot && !isDocument) {
            // Neither a root nor a document.
            return false;
        }

        if (isRoot) {
            loadRoot(initialUri, UserId.DEFAULT_USER);
            return true;
        }
        // From here onwards: isDoc == true.

        if (shouldPreemptivelyRestrictRequestedInitialUri(initialUri)) {
            Log.w(TAG, "Requested initial URI - " + initialUri + " - is restricted: "
                    + "loading device root instead.");
            return false;
        }

        return launchToDocument(initialUri);
    }

    /**
     * Starting with Android 11 (R, API Level 30) applications are no longer allowed to use the
     * {@link Intent#ACTION_OPEN_DOCUMENT} and {@link Intent#ACTION_OPEN_DOCUMENT_TREE} to request
     * that the user select individual files from "Android/data/", "Android/obb/",
     * "Android/sandbox/" directories and all their subdirectories on "external storage".
     * <p>
     * See <a href="https://developer.android.com/about/versions/11/privacy/storage#file-access">
     * Storage updates in Android 11</a>.
     * <p>
     * Ideally, this should be handled on the {@code ExternalStorageProvider} side, but as of
     * Android 14 (U) FRC, {@code ExternalStorageProvider} "hides" only "Android/data/",
     * "Android/obb/" and "Android/sandbox/" directories, but NOT their subdirectories.
     */
    private boolean shouldPreemptivelyRestrictRequestedInitialUri(@NonNull Uri uri) {
        // Not restricting SAF access for the calling app.
        if (!Shared.shouldRestrictStorageAccessFramework(mActivity)) {
            return false;
        }

        // We only need to restrict some locations on the "external" storage.
        if (!Providers.AUTHORITY_STORAGE.equals(uri.getAuthority())) {
            return false;
        }

        // TODO(b/283962634): in the future this will have to be platform-version specific.
        //  For example, if the fix on the ExternalStorageProvider side makes it to the Android 15,
        //  we would change this to check if the platform version >= 15.
        //  In the upcoming Android 14 release, however, ExternalStorageProvider does NOT yet
        //  implement this logic.
        final boolean externalProviderImplementsSafRestrictions = false;
        if (externalProviderImplementsSafRestrictions) {
            return false;
        }

        // External Storage Provider's docId format is "root:path/to/file"
        // The getPathFromStorageDocId() turns that into "/path/to/file"
        // Note the missing leading "/" in the path part of the docId, while the path returned by
        // the getPathFromStorageDocId() start with "/".
        final String docId = DocumentsContract.getDocumentId(uri);
        final String filePath;
        try {
            filePath = FileUtils.getPathFromStorageDocId(docId);
        } catch (IOException e) {
            Log.w(TAG, "Could not get canonical file path from docId '" + docId + "'");
            return true;
        }

        // Check if the app is asking for /Android/data, /Android/obb, /Android/sandbox or any of
        // their subdirectories (on the external storage).
        return PATTERN_RESTRICTED_INITIAL_PATH.matcher(filePath).matches();
    }

    private void initLoadLastAccessedStack() {
        if (DEBUG) {
            Log.d(TAG, "Attempting to load last used stack for calling package.");
        }
        // Block UI until stack is fully loaded, else there is an intermediate incomplete UI state.
        onLastAccessedStackLoaded(mLastAccessed.getLastAccessed(mActivity, mProviders, mState));
    }

    private void loadLastAccessedStack() {
        if (DEBUG) {
            Log.d(TAG, "Attempting to load last used stack for calling package.");
        }
        new LoadLastAccessedStackTask<>(
                mActivity, mLastAccessed, mState, mProviders, this::onLastAccessedStackLoaded)
                .execute();
    }

    private void onLastAccessedStackLoaded(@Nullable DocumentStack stack) {
        if (stack == null) {
            loadDefaultLocation();
        } else if (shouldPreemptivelyRestrictRequestedInitialUri(stack.peek().getDocumentUri())) {
            // If the last accessed stack has restricted uri, load default location
            loadDefaultLocation();
        } else {
            mState.stack.reset(stack);
            mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        }
    }

    public UpdatePickResultTask getUpdatePickResultTask() {
        return mUpdatePickResultTask;
    }

    private void updatePickResult(Intent intent, boolean isSearching, int root) {
        ClipData cdata = intent.getClipData();
        int fileCount = 0;
        Uri uri = null;

        // There are 2 cases that would be single-select:
        // 1. getData() isn't null and getClipData() is null.
        // 2. getClipData() isn't null and the item count of it is 1.
        if (intent.getData() != null && cdata == null) {
            fileCount = 1;
            uri = intent.getData();
        } else if (cdata != null) {
            fileCount = cdata.getItemCount();
            if (fileCount == 1) {
                uri = cdata.getItemAt(0).getUri();
            }
        }

        mInjector.pickResult.setFileCount(fileCount);
        mInjector.pickResult.setIsSearching(isSearching);
        mInjector.pickResult.setRoot(root);
        mInjector.pickResult.setFileUri(uri);
        getUpdatePickResultTask().safeExecute();
    }

    private void loadDefaultLocation() {
        switch (mState.action) {
            case ACTION_CREATE:
                loadHomeDir();
                break;
            case ACTION_OPEN_TREE:
                loadDeviceRoot();
                break;
            case ACTION_GET_CONTENT:
            case ACTION_OPEN:
                loadRecent();
                break;
            default:
                throw new UnsupportedOperationException("Unexpected action type: " + mState.action);
        }
    }

    @Override
    public void showAppDetails(ResolveInfo info, UserId userId) {
        mInjector.pickResult.increaseActionCount();
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", info.activityInfo.packageName, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        userId.startActivityAsUser(mActivity, intent);
    }

    @Override
    public void openInNewWindow(DocumentStack path) {
        // Open new window support only depends on vanilla Activity, so it is
        // implemented in our parent class. But we don't support that in
        // picking. So as a matter of defensiveness, we override that here.
        throw new UnsupportedOperationException("Can't open in new window");
    }

    @Override
    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(MetricConsts.PICKER_SCOPE, root);
        mInjector.pickResult.increaseActionCount();
        mActivity.onRootPicked(root);
    }

    @Override
    public void openRoot(ResolveInfo info, UserId userId) {
        Metrics.logAppVisited(info);
        mInjector.pickResult.increaseActionCount();

        // The App root item should not show if we cannot interact with the target user.
        // But the user managed to get here, this is the final check of permission. We don't
        // perform the check on activity result.
        if (!mState.canInteractWith(userId)) {
            mInjector.dialogs.showActionNotAllowed();
            return;
        }

        Intent intent = new Intent(mActivity.getIntent());
        final int flagsRemoved = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        intent.setFlags(intent.getFlags() & ~flagsRemoved);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        intent.setComponent(new ComponentName(
                info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
        try {
            boolean isCurrentUser = UserId.CURRENT_USER.equals(userId);
            if (isCurrentUser) {
                mActivity.startActivity(intent);
            } else {
                userId.startActivityAsUser(mActivity, intent);
            }
            Metrics.logLaunchOtherApp(!UserId.CURRENT_USER.equals(userId));
            mActivity.finish();
        } catch (SecurityException | ActivityNotFoundException e) {
            Log.e(TAG, "Caught error: " + e.getLocalizedMessage());
            mInjector.dialogs.showNoApplicationFound();
        }
    }


    @Override
    public void springOpenDirectory(DocumentInfo doc) {
    }

    @Override
    public boolean openItem(ItemDetails<String> details, @ViewType int type,
            @ViewType int fallback) {
        mInjector.pickResult.increaseActionCount();
        DocumentInfo doc = mModel.getDocument(details.getSelectionKey());
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: "
                    + details.getSelectionKey());
            return false;
        }

        if (mConfig.isDocumentEnabled(doc.mimeType, doc.flags, mState)) {
            mActivity.onDocumentPicked(doc);
            mSelectionMgr.clearSelection();
            return !doc.isDirectory();
        }
        return false;
    }

    @Override
    public boolean previewItem(ItemDetails<String> details) {
        mInjector.pickResult.increaseActionCount();
        final DocumentInfo doc = mModel.getDocument(details.getSelectionKey());
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: "
                    + details.getSelectionKey());
            return false;
        }

        onDocumentOpened(doc, VIEW_TYPE_PREVIEW, VIEW_TYPE_REGULAR, true);
        return !doc.isContainer();
    }

    void pickDocument(FragmentManager fm, DocumentInfo pickTarget) {
        assert (pickTarget != null);
        mInjector.pickResult.increaseActionCount();
        Uri result;
        switch (mState.action) {
            case ACTION_OPEN_TREE:
                mInjector.dialogs.confirmAction(fm, pickTarget, ConfirmFragment.TYPE_OEPN_TREE);
                break;
            case ACTION_PICK_COPY_DESTINATION:
                result = pickTarget.derivedUri;
                finishPicking(result);
                break;
            default:
                // Should not be reached
                throw new IllegalStateException("Invalid mState.action");
        }
    }

    void saveDocument(
            String mimeType, String displayName, BooleanConsumer inProgressStateListener) {
        assert (mState.action == ACTION_CREATE);
        mInjector.pickResult.increaseActionCount();
        new CreatePickedDocumentTask(
                mActivity,
                mDocs,
                mLastAccessed,
                mState.stack,
                mimeType,
                displayName,
                inProgressStateListener,
                this::onPickFinished)
                .executeOnExecutor(getExecutorForCurrentDirectory());
    }

    // User requested to overwrite a target. If confirmed by user #finishPicking() will be
    // called.
    void saveDocument(FragmentManager fm, DocumentInfo replaceTarget) {
        assert (mState.action == ACTION_CREATE);
        mInjector.pickResult.increaseActionCount();
        assert (replaceTarget != null);

        // Adding a confirmation dialog breaks an inherited CTS test (testCreateExisting), so we
        // need to add a feature flag to bypass this feature in ARC++ environment.
        if (mFeatures.isOverwriteConfirmationEnabled()) {
            mInjector.dialogs.confirmAction(fm, replaceTarget, ConfirmFragment.TYPE_OVERWRITE);
        } else {
            finishPicking(replaceTarget.getDocumentUri());
        }
    }

    void finishPicking(Uri... docs) {
        new SetLastAccessedStackTask(
                mActivity,
                mLastAccessed,
                mState.stack,
                () -> {
                    onPickFinished(docs);
                }
        ).executeOnExecutor(getExecutorForCurrentDirectory());
    }

    private void onPickFinished(Uri... uris) {
        if (DEBUG) {
            Log.d(TAG, "onFinished() " + Arrays.toString(uris));
        }

        final Intent intent = new Intent();
        if (uris.length == 1) {
            intent.setData(uris[0]);
        } else if (uris.length > 1) {
            final ClipData clipData = new ClipData(
                    null, mState.acceptMimes, new ClipData.Item(uris[0]));
            for (int i = 1; i < uris.length; i++) {
                clipData.addItem(new ClipData.Item(uris[i]));
            }
            intent.setClipData(clipData);
        }

        updatePickResult(
                intent, mSearchMgr.isSearching(), Metrics.sanitizeRoot(mState.stack.getRoot()));

        // TODO: Separate this piece of logic per action.
        // We don't instantiate different objects for different actions at the first place, so it's
        // not a easy task to separate this logic cleanly.
        // Maybe we can add an ActionPolicy class for IoC and provide various behaviors through its
        // inheritance structure.
        if (mState.action == ACTION_GET_CONTENT) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (mState.action == ACTION_OPEN_TREE) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } else if (mState.action == ACTION_PICK_COPY_DESTINATION) {
            // Picking a copy destination is only used internally by us, so we
            // don't need to extend permissions to the caller.
            intent.putExtra(Shared.EXTRA_STACK, (Parcelable) mState.stack);
            intent.putExtra(FileOperationService.EXTRA_OPERATION_TYPE, mState.copyOperationSubType);
        } else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }

        mActivity.setResult(FragmentActivity.RESULT_OK, intent, 0);
        mActivity.finish();
    }

    private Executor getExecutorForCurrentDirectory() {
        final DocumentInfo cwd = mState.stack.peek();
        if (cwd != null && cwd.authority != null) {
            return mExecutors.lookup(cwd.authority);
        } else {
            return AsyncTask.THREAD_POOL_EXECUTOR;
        }
    }

    public interface Addons extends CommonAddons {
        @Override
        void onDocumentPicked(DocumentInfo doc);

        /**
         * Overload final method {@link FragmentActivity#setResult(int, Intent)} so that we can
         * intercept this method call in test environment.
         */
        @VisibleForTesting
        void setResult(int resultCode, Intent result, int notUsed);
    }
}