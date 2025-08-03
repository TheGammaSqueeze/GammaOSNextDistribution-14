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

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

final class ListDocumentHolder extends DocumentHolder {
    private static final String TAG = "ListDocumentHolder";

    private final TextView mTitle;
    private final @Nullable TextView mDate; // Non-null for tablets/sw720dp, null for other devices.
    private final @Nullable TextView mSize; // Non-null for tablets/sw720dp, null for other devices.
    private final @Nullable TextView mType; // Non-null for tablets/sw720dp, null for other devices.
    // Container for date + size + summary, null only for tablets/sw720dp
    private final @Nullable LinearLayout mDetails;
    // TextView for date + size + summary, null only for tablets/sw720dp
    private final @Nullable TextView mMetadataView;
    private final ImageView mIconMime;
    private final ImageView mIconThumb;
    private final ImageView mIconCheck;
    private final ImageView mIconBadge;
    private final View mIconLayout;
    final View mPreviewIcon;

    private final IconHelper mIconHelper;
    private final Lookup<String, String> mFileTypeLookup;
    // This is used in as a convenience in our bind method.
    private final DocumentInfo mDoc;

    public ListDocumentHolder(Context context, ViewGroup parent, IconHelper iconHelper,
            Lookup<String, String> fileTypeLookup, ConfigStore configStore) {
        super(context, parent, R.layout.item_doc_list, configStore);

        mIconLayout = itemView.findViewById(R.id.icon);
        mIconMime = (ImageView) itemView.findViewById(R.id.icon_mime);
        mIconThumb = (ImageView) itemView.findViewById(R.id.icon_thumb);
        mIconCheck = (ImageView) itemView.findViewById(R.id.icon_check);
        mIconBadge = (ImageView) itemView.findViewById(R.id.icon_profile_badge);
        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mSize = (TextView) itemView.findViewById(R.id.size);
        mDate = (TextView) itemView.findViewById(R.id.date);
        mType = (TextView) itemView.findViewById(R.id.file_type);
        mMetadataView = (TextView) itemView.findViewById(R.id.metadata);
        // Warning: mDetails view doesn't exists in layout-sw720dp-land layout
        mDetails = (LinearLayout) itemView.findViewById(R.id.line2);
        mPreviewIcon = itemView.findViewById(R.id.preview_icon);

        mIconHelper = iconHelper;
        mFileTypeLookup = fileTypeLookup;
        mDoc = new DocumentInfo();

        if (SdkLevel.isAtLeastT() && !mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            setUpdatableWorkProfileIcon(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void setUpdatableWorkProfileIcon(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Drawable drawable = dpm.getResources().getDrawable(WORK_PROFILE_ICON, SOLID_COLORED, () ->
                context.getDrawable(R.drawable.ic_briefcase));
        mIconBadge.setImageDrawable(drawable);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        // We always want to make sure our check box disappears if we're not selected,
        // even if the item is disabled. But it should be an error (see assert below)
        // to be set to selected && be disabled.
        float checkAlpha = selected ? 1f : 0f;
        if (animate) {
            fade(mIconCheck, checkAlpha).start();
        } else {
            mIconCheck.setAlpha(checkAlpha);
        }

        if (!itemView.isEnabled()) {
            assert (!selected);
        }

        super.setSelected(selected, animate);

        if (animate) {
            fade(mIconMime, 1f - checkAlpha).start();
            fade(mIconThumb, 1f - checkAlpha).start();
        } else {
            mIconMime.setAlpha(1f - checkAlpha);
            mIconThumb.setAlpha(1f - checkAlpha);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        // Text colors enabled/disabled is handle via a color set.
        final float imgAlpha = enabled ? 1f : DISABLED_ALPHA;
        mIconMime.setAlpha(imgAlpha);
        mIconThumb.setAlpha(imgAlpha);
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        if (mDoc.isDirectory()) {
            mPreviewIcon.setVisibility(View.GONE);
        } else {
            mPreviewIcon.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                mPreviewIcon.setContentDescription(
                        getPreviewIconContentDescription(
                                mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                                mDoc.displayName, mDoc.userId));
                mPreviewIcon.setAccessibilityDelegate(
                        new PreviewAccessibilityDelegate(clickCallback));
            }
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
        mIconBadge.setImageDrawable(drawable);
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        // If itemView is activated = selected, then whole region is interactive
        if (itemView.isActivated()) {
            return true;
        }

        // Do everything in global coordinates - it makes things simpler.
        int[] coords = new int[2];
        mIconLayout.getLocationOnScreen(coords);

        Rect textBounds = new Rect();
        mTitle.getPaint().getTextBounds(
                mTitle.getText().toString(), 0, mTitle.getText().length(), textBounds);

        Rect rect = new Rect(
                coords[0],
                coords[1],
                coords[0] + mIconLayout.getWidth() + textBounds.width(),
                coords[1] + Math.max(mIconLayout.getHeight(), textBounds.height()));

        // If the tap occurred inside icon or the text, these are interactive spots.
        return rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override
    public boolean inSelectRegion(MotionEvent event) {
        return (mDoc.isDirectory() && !(mAction == State.ACTION_BROWSE)) ?
                false : Views.isEventOver(event, itemView.getParent(), mIconLayout);
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

        mIconMime.animate().cancel();
        mIconMime.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMime, null);

        mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        mTitle.setVisibility(View.VISIBLE);

        if (mDoc.isDirectory()) {
            // Note, we don't show any details for any directory...ever.
            if (mDetails != null) {
                // Non-tablets
                mDetails.setVisibility(View.GONE);
            }
        } else {
            // For tablets metadata is provided in columns mDate, mSize, mType.
            // For other devices mMetadataView consolidates the metadata info.
            if (mMetadataView != null) {
                // Non-tablets
                boolean hasDetails = false;
                ArrayList<String> metadataList = new ArrayList<>();
                if (mDoc.lastModified > 0) {
                    hasDetails = true;
                    metadataList.add(Shared.formatTime(mContext, mDoc.lastModified));
                }
                if (mDoc.size > -1) {
                    hasDetails = true;
                    metadataList.add(Formatter.formatFileSize(mContext, mDoc.size));
                }
                metadataList.add(mFileTypeLookup.lookup(mDoc.mimeType));
                mMetadataView.setText(TextUtils.join(", ", metadataList));
                if (mDetails != null) {
                    mDetails.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
                } else {
                    Log.w(TAG, "mDetails is unexpectedly null for non-tablet devices!");
                }
            } else {
                // Tablets
                if (mDoc.lastModified > 0) {
                    mDate.setVisibility(View.VISIBLE);
                    mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
                } else {
                    mDate.setVisibility(View.INVISIBLE);
                }
                if (mDoc.size > -1) {
                    mSize.setVisibility(View.VISIBLE);
                    mSize.setText(Formatter.formatFileSize(mContext, mDoc.size));
                } else {
                    mSize.setVisibility(View.INVISIBLE);
                }
                mType.setText(mFileTypeLookup.lookup(mDoc.mimeType));
            }
        }

        // TODO: Add document debug info
        // Call includeDebugInfo
    }
}
