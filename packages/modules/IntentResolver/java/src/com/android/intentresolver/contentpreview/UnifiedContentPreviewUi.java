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

import static com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_IMAGE;

import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.intentresolver.R;
import com.android.intentresolver.widget.ActionRow;
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback;
import com.android.intentresolver.widget.ScrollableImagePreviewView;

import java.util.List;
import java.util.Objects;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.Flow;

class UnifiedContentPreviewUi extends ContentPreviewUi {
    private final boolean mShowEditAction;
    @Nullable
    private final String mIntentMimeType;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final ImageLoader mImageLoader;
    private final MimeTypeClassifier mTypeClassifier;
    private final TransitionElementStatusCallback mTransitionElementStatusCallback;
    private final HeadlineGenerator mHeadlineGenerator;
    @Nullable
    private final CharSequence mMetadata;
    private final Flow<FileInfo> mFileInfoFlow;
    private final int mItemCount;
    @Nullable
    private List<FileInfo> mFiles;
    @Nullable
    private ViewGroup mContentPreviewView;
    @Nullable
    private View mHeadlineView;

    UnifiedContentPreviewUi(
            CoroutineScope scope,
            boolean isSingleImage,
            @Nullable String intentMimeType,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            MimeTypeClassifier typeClassifier,
            TransitionElementStatusCallback transitionElementStatusCallback,
            Flow<FileInfo> fileInfoFlow,
            int itemCount,
            HeadlineGenerator headlineGenerator,
            @Nullable CharSequence metadata) {
        mShowEditAction = isSingleImage;
        mIntentMimeType = intentMimeType;
        mActionFactory = actionFactory;
        mImageLoader = imageLoader;
        mTypeClassifier = typeClassifier;
        mTransitionElementStatusCallback = transitionElementStatusCallback;
        mFileInfoFlow = fileInfoFlow;
        mItemCount = itemCount;
        mHeadlineGenerator = headlineGenerator;
        mMetadata = metadata;

        JavaFlowHelper.collectToList(scope, fileInfoFlow, this::setFiles);
    }

    @Override
    public int getType() {
        return CONTENT_PREVIEW_IMAGE;
    }

    @Override
    public ViewGroup display(
            Resources resources,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            @Nullable View headlineViewParent) {
        ViewGroup layout = displayInternal(layoutInflater, parent, headlineViewParent);
        displayModifyShareAction(
                headlineViewParent == null ? layout : headlineViewParent, mActionFactory);
        return layout;
    }

    private void setFiles(List<FileInfo> files) {
        mImageLoader.prePopulate(files.stream()
                .map(FileInfo::getPreviewUri)
                .filter(Objects::nonNull)
                .toList());
        mFiles = files;
        if (mContentPreviewView != null) {
            updatePreviewWithFiles(mContentPreviewView, mHeadlineView, files);
        }
    }

    private ViewGroup displayInternal(
            LayoutInflater layoutInflater, ViewGroup parent, @Nullable View headlineViewParent) {
        mContentPreviewView = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_image, parent, false);
        mHeadlineView = headlineViewParent == null ? mContentPreviewView : headlineViewParent;
        inflateHeadline(mHeadlineView);

        final ActionRow actionRow =
                mContentPreviewView.findViewById(com.android.internal.R.id.chooser_action_row);
        List<ActionRow.Action> actions = mActionFactory.createCustomActions();
        actionRow.setActions(actions);

        ScrollableImagePreviewView imagePreview =
                mContentPreviewView.requireViewById(R.id.scrollable_image_preview);
        imagePreview.setImageLoader(mImageLoader);
        imagePreview.setOnNoPreviewCallback(() -> imagePreview.setVisibility(View.GONE));
        imagePreview.setTransitionElementStatusCallback(mTransitionElementStatusCallback);
        imagePreview.setPreviews(
                JavaFlowHelper.mapFileIntoToPreview(
                        mFileInfoFlow,
                        mTypeClassifier,
                        mShowEditAction ? mActionFactory.getEditButtonRunnable() : null),
                mItemCount);

        if (mFiles != null) {
            updatePreviewWithFiles(mContentPreviewView, mHeadlineView, mFiles);
        } else {
            displayHeadline(
                    mHeadlineView,
                    mItemCount,
                    mTypeClassifier.isImageType(mIntentMimeType),
                    mTypeClassifier.isVideoType(mIntentMimeType));
            imagePreview.setLoading(mItemCount);
        }

        return mContentPreviewView;
    }

    private void updatePreviewWithFiles(
            ViewGroup contentPreviewView, View headlineView, List<FileInfo> files) {
        final int count = files.size();
        ScrollableImagePreviewView imagePreview =
                contentPreviewView.requireViewById(R.id.scrollable_image_preview);
        if (count == 0) {
            Log.i(
                    TAG,
                    "Attempted to display image preview area with zero"
                            + " available images detected in EXTRA_STREAM list");
            imagePreview.setVisibility(View.GONE);
            mTransitionElementStatusCallback.onAllTransitionElementsReady();
            return;
        }

        boolean allImages = true;
        boolean allVideos = true;
        for (FileInfo fileInfo : files) {
            ScrollableImagePreviewView.PreviewType previewType =
                    getPreviewType(mTypeClassifier, fileInfo.getMimeType());
            allImages = allImages && previewType == ScrollableImagePreviewView.PreviewType.Image;
            allVideos = allVideos && previewType == ScrollableImagePreviewView.PreviewType.Video;
        }

        displayHeadline(headlineView, count, allImages, allVideos);
    }

    private void displayHeadline(
            View layout, int count, boolean allImages, boolean allVideos) {
        if (allImages) {
            displayHeadline(layout, mHeadlineGenerator.getImagesHeadline(count));
        } else if (allVideos) {
            displayHeadline(layout, mHeadlineGenerator.getVideosHeadline(count));
        } else {
            displayHeadline(layout, mHeadlineGenerator.getFilesHeadline(count));
        }
        displayMetadata(layout, mMetadata);
    }
}
