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

import static com.android.intentresolver.util.UriFilters.isOwnedByCurrentUser;

import android.content.res.Resources;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.intentresolver.ContentTypeHint;
import com.android.intentresolver.R;
import com.android.intentresolver.widget.ActionRow;

import kotlinx.coroutines.CoroutineScope;

class TextContentPreviewUi extends ContentPreviewUi {
    private final CoroutineScope mScope;
    @Nullable
    private final CharSequence mSharingText;
    @Nullable
    private final CharSequence mPreviewTitle;
    @Nullable
    private final CharSequence mMetadata;
    @Nullable
    private final Uri mPreviewThumbnail;
    private final ImageLoader mImageLoader;
    private final ChooserContentPreviewUi.ActionFactory mActionFactory;
    private final HeadlineGenerator mHeadlineGenerator;
    private final ContentTypeHint mContentTypeHint;

    TextContentPreviewUi(
            CoroutineScope scope,
            @Nullable CharSequence sharingText,
            @Nullable CharSequence previewTitle,
            @Nullable CharSequence metadata,
            @Nullable Uri previewThumbnail,
            ChooserContentPreviewUi.ActionFactory actionFactory,
            ImageLoader imageLoader,
            HeadlineGenerator headlineGenerator,
            ContentTypeHint contentTypeHint) {
        mScope = scope;
        mSharingText = sharingText;
        mPreviewTitle = previewTitle;
        mMetadata = metadata;
        mPreviewThumbnail = previewThumbnail;
        mImageLoader = imageLoader;
        mActionFactory = actionFactory;
        mHeadlineGenerator = headlineGenerator;
        mContentTypeHint = contentTypeHint;
    }

    @Override
    public int getType() {
        return ContentPreviewType.CONTENT_PREVIEW_TEXT;
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

    private ViewGroup displayInternal(
            LayoutInflater layoutInflater,
            ViewGroup parent,
            @Nullable View headlineViewParent) {
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_text, parent, false);
        if (headlineViewParent == null) {
            headlineViewParent = contentPreviewLayout;
        }
        inflateHeadline(headlineViewParent);

        final ActionRow actionRow =
                contentPreviewLayout.findViewById(com.android.internal.R.id.chooser_action_row);
        actionRow.setActions(mActionFactory.createCustomActions());

        if (mSharingText == null) {
            contentPreviewLayout
                    .findViewById(R.id.text_preview_layout)
                    .setVisibility(View.GONE);
            return contentPreviewLayout;
        }

        TextView textView = contentPreviewLayout.findViewById(
                com.android.internal.R.id.content_preview_text);

        textView.setText(
                textView.getMaxLines() == 1 ? replaceLineBreaks(mSharingText) : mSharingText);

        TextView previewTitleView = contentPreviewLayout.findViewById(
                com.android.internal.R.id.content_preview_title);
        if (TextUtils.isEmpty(mPreviewTitle)) {
            previewTitleView.setVisibility(View.GONE);
        } else {
            previewTitleView.setText(mPreviewTitle);
        }

        ImageView previewThumbnailView = contentPreviewLayout.findViewById(
                com.android.internal.R.id.content_preview_thumbnail);
        if (!isOwnedByCurrentUser(mPreviewThumbnail)) {
            previewThumbnailView.setVisibility(View.GONE);
        } else {
            mImageLoader.loadImage(
                    mScope,
                    mPreviewThumbnail,
                    (bitmap) -> updateViewWithImage(
                            contentPreviewLayout.findViewById(
                                    com.android.internal.R.id.content_preview_thumbnail),
                            bitmap));
        }

        Runnable onCopy = mActionFactory.getCopyButtonRunnable();
        View copyButton = contentPreviewLayout.findViewById(R.id.copy);
        if (onCopy != null) {
            copyButton.setOnClickListener((v) -> onCopy.run());
        } else {
            copyButton.setVisibility(View.GONE);
        }

        String headlineText = (mContentTypeHint == ContentTypeHint.ALBUM)
                ? mHeadlineGenerator.getAlbumHeadline()
                : mHeadlineGenerator.getTextHeadline(mSharingText);
        displayHeadline(headlineViewParent, headlineText);
        displayMetadata(headlineViewParent, mMetadata);

        return contentPreviewLayout;
    }

    @Nullable
    private static CharSequence replaceLineBreaks(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        SpannableStringBuilder string = new SpannableStringBuilder(text);
        for (int i = 0, size = string.length(); i < size; i++) {
            if (string.charAt(i) == '\n') {
                string.replace(i, i + 1, " ");
            }
        }
        return string;
    }
}
