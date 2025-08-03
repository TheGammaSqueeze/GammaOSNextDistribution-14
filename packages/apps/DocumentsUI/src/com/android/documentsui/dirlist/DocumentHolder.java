/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.DevicePolicyResources.Strings.PREVIEW_WORK_FILE_ACCESSIBILITY;
import static com.android.documentsui.DevicePolicyResources.Strings.UNDEFINED;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.UserManagerState;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * ViewHolder of a document item within a RecyclerView.
 */
public abstract class DocumentHolder
        extends RecyclerView.ViewHolder implements View.OnKeyListener {

    static final float DISABLED_ALPHA = 0.3f;

    protected final Context mContext;

    protected @Nullable String mModelId;

    protected @State.ActionType int mAction;
    protected final ConfigStore mConfigStore;

    // See #addKeyEventListener for details on the need for this field.
    private KeyboardEventListener<DocumentItemDetails> mKeyEventListener;

    private final DocumentItemDetails mDetails;

    public DocumentHolder(Context context, ViewGroup parent, int layout, ConfigStore configStore) {
        this(context, inflateLayout(context, parent, layout), configStore);
    }

    public DocumentHolder(Context context, View item, ConfigStore configStore) {
        super(item);

        itemView.setOnKeyListener(this);

        mContext = context;
        mDetails = new DocumentItemDetails(this);
        mConfigStore = configStore;
    }

    /**
     * Binds the view to the given item data.
     */
    public abstract void bind(Cursor cursor, String modelId);

    public String getModelId() {
        return mModelId;
    }

    /**
     * Makes the associated item view appear selected. Note that this merely affects the appearance
     * of the view, it doesn't actually select the item.
     * TODO: Use the DirectoryItemAnimator instead of manually controlling animation using a boolean
     * flag.
     *
     * @param animate Whether or not to animate the change. Only selection changes initiated by the
     *                selection manager should be animated. See
     *                {@link ModelBackedDocumentsAdapter#onBindViewHolder(DocumentHolder, int,
     *                java.util.List)}
     */
    public void setSelected(boolean selected, boolean animate) {
        itemView.setActivated(selected);
        itemView.setSelected(selected);
    }

    public void setEnabled(boolean enabled) {
        setEnabledRecursive(itemView, enabled);
    }

    public void setAction(@State.ActionType int action) {
        mAction = action;
    }

    /**
     * @param show          boolean denoting whether the current profile is non-personal
     * @param clickCallback call back function
     */
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
    }

    /**
     * @param show boolean denoting whether the current profile is managed
     */
    public void bindBriefcaseIcon(boolean show) {
    }

    /**
     * Binds profile badge icon to the documents thumbnail
     *
     * @param show             boolean denoting whether the current profile is non-personal/parent
     * @param userIdIdentifier user id of the profile the document belongs to
     */
    public void bindProfileIcon(boolean show, int userIdIdentifier) {
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        assert (mKeyEventListener != null);
        DocumentItemDetails details = getItemDetails();
        return (details == null)
                ? false
                : mKeyEventListener.onKey(details, keyCode, event);
    }

    /**
     * Installs a delegate to receive keyboard input events. This arrangement is necessitated
     * by the fact that a single listener cannot listen to all keyboard events
     * on RecyclerView (our parent view). Not sure why this is, but have been
     * assured it is the case.
     *
     * <p>Ideally we'd not involve DocumentHolder in propagation of events like this.
     */
    public void addKeyEventListener(KeyboardEventListener<DocumentItemDetails> listener) {
        assert (mKeyEventListener == null);
        mKeyEventListener = listener;
    }

    public boolean inDragRegion(MotionEvent event) {
        return false;
    }

    public boolean inSelectRegion(MotionEvent event) {
        return false;
    }

    public boolean inPreviewIconRegion(MotionEvent event) {
        return false;
    }

    public DocumentItemDetails getItemDetails() {
        return mDetails;
    }

    static void setEnabledRecursive(View itemView, boolean enabled) {
        if (itemView == null || itemView.isEnabled() == enabled) {
            return;
        }
        itemView.setEnabled(enabled);

        if (itemView instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) itemView;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                setEnabledRecursive(vg.getChildAt(i), enabled);
            }
        }
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    private static <V extends View> V inflateLayout(Context context, ViewGroup parent, int layout) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        return (V) inflater.inflate(layout, parent, false);
    }

    static ViewPropertyAnimator fade(ImageView view, float alpha) {
        return view.animate().setDuration(Shared.CHECK_ANIMATION_DURATION).alpha(alpha);
    }

    protected String getPreviewIconContentDescription(boolean isNonPersonalProfile,
            String fileName, UserId userId) {
        if (mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            UserManagerState userManagerState = DocumentsApplication.getUserManagerState(mContext);
            String profileLabel = userManagerState.getUserIdToLabelMap().get(userId);
            return isNonPersonalProfile
                    ? itemView.getResources().getString(R.string.preview_cross_profile_file,
                    profileLabel, fileName)
                    : itemView.getResources().getString(R.string.preview_file, fileName);
        }
        if (SdkLevel.isAtLeastT()) {
            return getUpdatablePreviewIconContentDescription(isNonPersonalProfile, fileName);
        } else {
            return itemView.getResources().getString(
                    isNonPersonalProfile ? R.string.preview_work_file : R.string.preview_file,
                    fileName);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private String getUpdatablePreviewIconContentDescription(
            boolean isWorkProfile, String fileName) {
        DevicePolicyManager dpm = itemView.getContext().getSystemService(
                DevicePolicyManager.class);
        String updatableStringId = isWorkProfile ? PREVIEW_WORK_FILE_ACCESSIBILITY : UNDEFINED;
        int defaultStringId =
                isWorkProfile ? R.string.preview_work_file : R.string.preview_file;
        return dpm.getResources().getString(
                updatableStringId,
                () -> itemView.getResources().getString(defaultStringId, fileName),
                /* formatArgs= */ fileName);
    }

    protected static class PreviewAccessibilityDelegate extends View.AccessibilityDelegate {
        private Function<View, Boolean> mCallback;

        public PreviewAccessibilityDelegate(Function<View, Boolean> clickCallback) {
            super();
            mCallback = clickCallback;
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                return mCallback.apply(host);
            }
            return super.performAccessibilityAction(host, action, args);
        }
    }
}
