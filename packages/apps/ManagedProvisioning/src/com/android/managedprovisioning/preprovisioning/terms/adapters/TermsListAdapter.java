/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms.adapters;

import static android.view.View.TEXT_ALIGNMENT_TEXT_START;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;

import com.google.android.setupdesign.util.DescriptionStyler;

import java.util.List;

/**
 * Displays {@link TermsDocument} objects in a {@link RecyclerView}.
 */
public class TermsListAdapter extends RecyclerView.Adapter<TermsListAdapter.TermsViewHolder> {
    private static final int VIEW_TYPE_GENERAL_DISCLAIMER = 1;
    private static final int VIEW_TYPE_REGULAR_DISCLAIMER = 2;

    private final TermsDocument mGenericDisclaimer;
    private final List<TermsDocument> mTermsDocuments;
    private final LayoutInflater mInflater;
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final Context mContext;
    private final TermsBridge mTermsBridge;
    private final Utils mUtils;
    private final StylerHelper mStylerHelper;

    public TermsListAdapter(Context context, TermsDocument genericDisclaimer,
            List<TermsDocument> termsDocuments, LayoutInflater layoutInflater,
            AccessibilityContextMenuMaker contextMenuMaker, TermsBridge termsBridge, Utils utils,
            StylerHelper stylerHelper) {
        mGenericDisclaimer = requireNonNull(genericDisclaimer);
        mTermsDocuments = requireNonNull(termsDocuments);
        mInflater = requireNonNull(layoutInflater);
        mContextMenuMaker = requireNonNull(contextMenuMaker);
        mContext = requireNonNull(context);
        mTermsBridge = requireNonNull(termsBridge);
        mUtils = requireNonNull(utils);
        mStylerHelper = requireNonNull(stylerHelper);
    }

    @Override
    public TermsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View rootView = null;
        switch (viewType) {
            case VIEW_TYPE_GENERAL_DISCLAIMER:
                rootView = createGeneralDisclaimerView(parent);
                break;
            case VIEW_TYPE_REGULAR_DISCLAIMER:
                rootView = createRegularDisclaimerView(parent);
                break;
        }
        return new TermsViewHolder(rootView);
    }

    private View createRegularDisclaimerView(ViewGroup parent) {
        View rootView = mInflater.inflate(
                R.layout.terms_disclaimer_header, parent, /* attachToRoot= */ false);
        mStylerHelper.applyListItemStyling(rootView,
                new LinearLayout.LayoutParams(rootView.getLayoutParams()));
        return rootView;
    }

    private View createGeneralDisclaimerView(ViewGroup parent) {
        View rootView = mInflater.inflate(
                R.layout.terms_disclaimer_general, parent, /* attachToRoot= */ false);
        TextView textView = rootView.findViewById(R.id.terms_disclaimer_general);
        DescriptionStyler.applyPartnerCustomizationHeavyStyle(textView);
        textView.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
        return rootView;
    }

    @Override
    public void onBindViewHolder(TermsViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);
        switch (itemViewType) {
            case VIEW_TYPE_GENERAL_DISCLAIMER:
                onBindGeneralDisclaimerViewHolder(holder);
                break;
            case VIEW_TYPE_REGULAR_DISCLAIMER:
                onBindDisclaimerViewHolder(holder, /* disclaimerIndex= */ position - 1);
                break;
        }
    }

    private void onBindGeneralDisclaimerViewHolder(TermsViewHolder holder) {
        holder.mGeneralDisclaimer.setText(mGenericDisclaimer.getContent());
    }

    private void onBindDisclaimerViewHolder(TermsViewHolder holder, int disclaimerIndex) {
        TermsDocument disclaimer = getDisclaimer(disclaimerIndex);
        String heading = disclaimer.getHeading();
        setupRootView(disclaimerIndex, heading, holder);
        setupDisclaimerHeading(heading, holder);
        setupDisclaimerContent(disclaimer, holder);
        setExpanded(isExpanded(disclaimerIndex), disclaimerIndex, holder);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_GENERAL_DISCLAIMER;
        }
        return VIEW_TYPE_REGULAR_DISCLAIMER;
    }

    private void setupRootView(int position, String heading, TermsViewHolder viewHolder) {
        viewHolder.itemView
                .setContentDescription(mContext.getString(R.string.section_heading, heading));
        viewHolder.itemView.setOnClickListener(view ->
                setExpanded(!isExpanded(position), position, viewHolder));
    }

    private void setupDisclaimerHeading(String heading, TermsViewHolder viewHolder) {
        viewHolder.mDisclaimerHeader.setText(heading);
    }

    private void setupDisclaimerContent(TermsDocument disclaimer, TermsViewHolder viewHolder) {
        TextView disclaimerContent = viewHolder.mDisclaimerContent;
        TermsAdapterUtils.populateContentTextView(
                mContext, disclaimerContent, disclaimer, new ClickableSpanFactory(
                        mUtils.getAccentColor(mContext),
                        mTermsBridge::onLinkClicked));
        mContextMenuMaker.registerWithActivity(disclaimerContent);
    }

    private void updateViewsForExpandedState(boolean expanded, TermsViewHolder viewHolder) {
        viewHolder.mDisclaimerContentContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        viewHolder.mChevron.setRotation(expanded ? 90 : -90);
    }

    @Override
    public int getItemCount() {
        // First item is always the general disclaimer
        return 1 + mTermsDocuments.size();
    }

    private TermsDocument getDisclaimer(int index) {
        return mTermsDocuments.get(index);
    }

    private Boolean isExpanded(int position) {
        return mTermsBridge.isTermExpanded(position);
    }

    private void setExpanded(boolean expanded, int position, TermsViewHolder viewHolder) {
        mTermsBridge.onTermExpanded(position, expanded);
        updateViewsForExpandedState(expanded, viewHolder);
    }

    /**
     * Used for communicating with the owner of the adapter.
     */
    public interface TermsBridge {
        /**
         * Returns whether the disclaimer at position {@code position} is expanded.
         */
        boolean isTermExpanded(int position);

        /**
         * Expands the disclaimer at position {@code position} if {@code expanded} is {@code true},
         * otherwise collapses it.
         */
        void onTermExpanded(int position, boolean expanded);

        /**
         * Callback invoked when a link is tapped from the terms content text.
         * @param intent a web intent ready to be launched.
         */
        void onLinkClicked(Intent intent);
    }

    static class TermsViewHolder extends RecyclerView.ViewHolder {
        final TextView mGeneralDisclaimer;
        final TextView mDisclaimerHeader;
        final TextView mDisclaimerContent;
        final ViewGroup mDisclaimerContentContainer;
        final ImageView mChevron;

        TermsViewHolder(View itemView) {
            super(itemView);
            mGeneralDisclaimer = itemView.findViewById(R.id.terms_disclaimer_general);
            mDisclaimerHeader = itemView.findViewById(R.id.sud_items_title);
            mDisclaimerContent = itemView.findViewById(R.id.disclaimer_content);
            mDisclaimerContentContainer = itemView.findViewById(R.id.disclaimer_content_container);
            mChevron = itemView.findViewById(R.id.chevron);
        }
    }
}
