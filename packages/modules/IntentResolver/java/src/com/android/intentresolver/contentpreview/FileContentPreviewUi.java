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

package com.android.intentresolver.contentpreview;

import android.content.res.Resources;
import android.util.Log;
import android.util.PluralsMessageFormatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.intentresolver.R;
import com.android.intentresolver.widget.ActionRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FileContentPreviewUi extends ContentPreviewUi {
    private static final String PLURALS_COUNT  = "count";

    @Nullable
    private String mFirstFileName = null;
    private final int mFileCount;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final HeadlineGenerator mHeadlineGenerator;
    @Nullable
    private final CharSequence mMetadata;
    @Nullable
    private ViewGroup mContentPreview = null;

    FileContentPreviewUi(
            int fileCount,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            HeadlineGenerator headlineGenerator,
            @Nullable CharSequence metadata
    ) {
        mFileCount = fileCount;
        mActionFactory = actionFactory;
        mHeadlineGenerator = headlineGenerator;
        mMetadata = metadata;
    }

    @Override
    public int getType() {
        return ContentPreviewType.CONTENT_PREVIEW_FILE;
    }

    public void setFirstFileName(String fileName) {
        mFirstFileName = fileName;
        if (mContentPreview != null) {
            showFileName(mContentPreview, fileName);
        }
    }

    @Override
    public ViewGroup display(
            Resources resources,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            @Nullable View headlineViewParent) {
        ViewGroup layout = displayInternal(resources, layoutInflater, parent, headlineViewParent);
        displayModifyShareAction(
                headlineViewParent == null ? layout : headlineViewParent, mActionFactory);
        return layout;
    }

    private ViewGroup displayInternal(
            Resources resources,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            @Nullable View headlineViewParent) {
        mContentPreview = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_file, parent, false);
        if (headlineViewParent == null) {
            headlineViewParent = mContentPreview;
        }
        inflateHeadline(headlineViewParent);

        displayHeadline(headlineViewParent, mHeadlineGenerator.getFilesHeadline(mFileCount));
        displayMetadata(headlineViewParent, mMetadata);

        if (mFileCount == 0) {
            mContentPreview.setVisibility(View.GONE);
            Log.i(TAG, "Appears to be no uris available in EXTRA_STREAM,"
                    + " removing preview area");
            return mContentPreview;
        }

        if (mFirstFileName != null) {
            showFileName(mContentPreview, mFirstFileName);
        }

        TextView secondLine = mContentPreview.findViewById(
                R.id.content_preview_more_files);
        if (mFileCount > 1) {
            int remUriCount = mFileCount - 1;
            Map<String, Object> arguments = new HashMap<>();
            arguments.put(PLURALS_COUNT, remUriCount);
            secondLine.setText(
                    PluralsMessageFormatter.format(resources, arguments, R.string.more_files));
        } else {
            ImageView icon = mContentPreview.findViewById(R.id.content_preview_file_icon);
            icon.setImageResource(R.drawable.single_file);
            secondLine.setVisibility(View.GONE);
        }

        final ActionRow actionRow =
                mContentPreview.findViewById(com.android.internal.R.id.chooser_action_row);
        List<ActionRow.Action> actions = mActionFactory.createCustomActions();
        actionRow.setActions(actions);

        return mContentPreview;
    }

    private void showFileName(ViewGroup contentPreview, String name) {
        TextView fileNameView = contentPreview.requireViewById(R.id.content_preview_filename);
        fileNameView.setText(name);
    }
}
