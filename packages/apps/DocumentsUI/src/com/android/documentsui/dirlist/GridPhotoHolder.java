/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_NOT_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.DocumentsContract.Document;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import java.util.Map;
import java.util.function.Function;

final class GridPhotoHolder extends DocumentHolder {

    private final ImageView mIconMimeLg;
    private final ImageView mIconThumb;
    private final ImageView mIconCheck;
    private final IconHelper mIconHelper;
    private final View mPreviewIcon;
    private final View mIconBadge;

    // This is used in as a convenience in our bind method.
    private final DocumentInfo mDoc = new DocumentInfo();

    GridPhotoHolder(Context context, ViewGroup parent, IconHelper iconHelper,
            ConfigStore configStore) {
        super(context, parent, R.layout.item_photo_grid, configStore);

        mIconMimeLg = (ImageView) itemView.findViewById(R.id.icon_mime_lg);
        mIconThumb = (ImageView) itemView.findViewById(R.id.icon_thumb);
        mIconCheck = (ImageView) itemView.findViewById(R.id.icon_check);
        mIconBadge = itemView.findViewById(R.id.icon_profile_badge);
        mPreviewIcon = itemView.findViewById(R.id.preview_icon);

        mIconHelper = iconHelper;

        if (SdkLevel.isAtLeastT() && !mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            setUpdatableWorkProfileIcon(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void setUpdatableWorkProfileIcon(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Drawable drawable = dpm.getResources().getDrawable(
                WORK_PROFILE_ICON, SOLID_NOT_COLORED, () ->
                        context.getDrawable(R.drawable.ic_briefcase));
        ImageView icon = (ImageView) mIconBadge.findViewById(R.id.icon_id);

        icon.setImageDrawable(drawable);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        // We always want to make sure our check box disappears if we're not selected,
        // even if the item is disabled. This is because this object can be reused
        // and this method will be called to setup initial state.
        float checkAlpha = selected ? 1f : 0f;
        if (animate) {
            fade(mIconCheck, checkAlpha).start();
        } else {
            mIconCheck.setAlpha(checkAlpha);
        }

        // But it should be an error to be set to selected && be disabled.
        if (!itemView.isEnabled()) {
            assert (!selected);
            return;
        }

        super.setSelected(selected, animate);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        float imgAlpha = enabled ? 1f : DISABLED_ALPHA;

        mIconMimeLg.setAlpha(imgAlpha);
        mIconThumb.setAlpha(imgAlpha);
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        mPreviewIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mPreviewIcon.setContentDescription(
                    getPreviewIconContentDescription(
                            mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                            mDoc.displayName, mDoc.userId));
            mPreviewIcon.setAccessibilityDelegate(new PreviewAccessibilityDelegate(clickCallback));
        }
    }

    @Override
    public void bindBriefcaseIcon(boolean show) {
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void bindProfileIcon(boolean show, int userIdIdentifier) {
        Map<UserId, Drawable> userIdToBadgeMap = DocumentsApplication.getUserManagerState(
                mContext).getUserIdToBadgeMap();
        Drawable drawable = userIdToBadgeMap.get(UserId.of(userIdIdentifier));
        ImageView icon = mIconBadge.findViewById(R.id.icon_id);
        icon.setImageDrawable(drawable);
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        // Entire grid box should be draggable
        return true;
    }

    @Override
    public boolean inSelectRegion(MotionEvent event) {
        // Photo gird should not have any select region.
        return false;
    }

    @Override
    public boolean inPreviewIconRegion(MotionEvent event) {
        return Views.isEventOver(event, itemView.getParent(), mPreviewIcon);
    }

    /**
     * Bind this view to the given document for display.
     *
     * @param cursor  Pointing to the item to be bound.
     * @param modelId The model ID of the item.
     */
    @Override
    public void bind(Cursor cursor, String modelId) {
        assert (cursor != null);

        mModelId = modelId;

        mDoc.updateFromCursor(cursor,
                UserId.of(getCursorInt(cursor, RootCursorWrapper.COLUMN_USER_ID)),
                getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY));

        mIconHelper.stopLoading(mIconThumb);

        mIconMimeLg.animate().cancel();
        mIconMimeLg.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMimeLg, null);

        final String docSize =
                Formatter.formatFileSize(mContext, getCursorLong(cursor, Document.COLUMN_SIZE));
        final String docDate = Shared.formatTime(mContext, mDoc.lastModified);
        if (mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier())) {
            itemView.setContentDescription((mContext.getText(R.string.a11y_work) + ", ")
                    + mDoc.displayName + ", " + docSize + ", " + docDate);
        } else {
            itemView.setContentDescription(mDoc.displayName + ", " + docSize + ", " + docDate);
        }
    }
}
