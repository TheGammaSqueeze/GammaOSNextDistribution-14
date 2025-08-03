/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver.grid;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.intentresolver.ChooserListAdapter;
import com.android.intentresolver.FeatureFlags;
import com.android.intentresolver.R;
import com.android.intentresolver.ResolverListAdapter.ViewHolder;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.collect.Lists;

/**
 * Adapter for all types of items and targets in ShareSheet.
 * Note that ranked sections like Direct Share - while appearing grid-like - are handled on the
 * row level by this adapter but not on the item level. Individual targets within the row are
 * handled by {@link ChooserListAdapter}
 */
@VisibleForTesting
public final class ChooserGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /**
     * The transition time between placeholders for direct share to a message
     * indicating that none are available.
     */
    public static final int NO_DIRECT_SHARE_ANIM_IN_MILLIS = 200;

    /**
     * Injectable interface for any considerations that should be delegated to other components
     * in the {@link com.android.intentresolver.ChooserActivity}.
     * TODO: determine whether any of these methods return parameters that can safely be
     * precomputed; whether any should be converted to `ChooserGridAdapter` setters to be
     * invoked by external callbacks; and whether any reflect requirements that should be moved
     * out of `ChooserGridAdapter` altogether.
     */
    public interface ChooserActivityDelegate {
        /** @return whether we're showing a tabbed (multi-profile) UI. */
        boolean shouldShowTabs();

        /**
         * @return a content preview {@link View} that's appropriate for the caller's share
         * content, constructed for display in the provided {@code parent} group.
         */
        View buildContentPreview(ViewGroup parent);

        /** Notify the client that the item with the selected {@code itemIndex} was selected. */
        void onTargetSelected(int itemIndex);

        /**
         * Notify the client that the item with the selected {@code itemIndex} was
         * long-pressed.
         */
        void onTargetLongPressed(int itemIndex);
    }

    private static final int VIEW_TYPE_DIRECT_SHARE = 0;
    private static final int VIEW_TYPE_NORMAL = 1;
    private static final int VIEW_TYPE_CONTENT_PREVIEW = 2;
    private static final int VIEW_TYPE_AZ_LABEL = 4;
    private static final int VIEW_TYPE_CALLER_AND_RANK = 5;
    private static final int VIEW_TYPE_FOOTER = 6;

    private final ChooserActivityDelegate mChooserActivityDelegate;
    private final ChooserListAdapter mChooserListAdapter;
    private final LayoutInflater mLayoutInflater;

    private final int mMaxTargetsPerRow;
    private final boolean mShouldShowContentPreview;
    private final int mChooserWidthPixels;
    private final int mChooserRowTextOptionTranslatePixelSize;
    private final FeatureFlags mFeatureFlags;
    @Nullable
    private RecyclerView mRecyclerView;

    private int mChooserTargetWidth = 0;

    private int mFooterHeight = 0;

    private boolean mAzLabelVisibility = false;

    public ChooserGridAdapter(
            Context context,
            ChooserActivityDelegate chooserActivityDelegate,
            ChooserListAdapter wrappedAdapter,
            boolean shouldShowContentPreview,
            int maxTargetsPerRow,
            FeatureFlags featureFlags) {
        super();

        mChooserActivityDelegate = chooserActivityDelegate;

        mChooserListAdapter = wrappedAdapter;
        mLayoutInflater = LayoutInflater.from(context);

        mShouldShowContentPreview = shouldShowContentPreview;
        mMaxTargetsPerRow = maxTargetsPerRow;

        mChooserWidthPixels = context.getResources().getDimensionPixelSize(R.dimen.chooser_width);
        mChooserRowTextOptionTranslatePixelSize = context.getResources().getDimensionPixelSize(
                R.dimen.chooser_row_text_option_translate);
        mFeatureFlags = featureFlags;

        wrappedAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                super.onInvalidated();
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        if (mFeatureFlags.scrollablePreview()) {
            mRecyclerView = recyclerView;
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = null;
    }

    public void setFooterHeight(int height) {
        if (mFooterHeight != height) {
            mFooterHeight = height;
            if (mFeatureFlags.fixTargetListFooter()) {
                // we always have at least one view, the footer, see getItemCount() and
                // getFooterRowCount()
                notifyItemChanged(getItemCount() - 1);
            }
        }
    }

    /**
     * Calculate the chooser target width to maximize space per item
     *
     * @param width The new row width to use for recalculation
     * @return true if the view width has changed
     */
    public boolean calculateChooserTargetWidth(int width) {
        if (width == 0) {
            return false;
        }

        // Limit width to the maximum width of the chooser activity, if the maximum width is set
        if (mChooserWidthPixels >= 0) {
            width = Math.min(mChooserWidthPixels, width);
        }

        int newWidth = width / mMaxTargetsPerRow;
        if (newWidth != mChooserTargetWidth) {
            mChooserTargetWidth = newWidth;
            return true;
        }

        return false;
    }

    public int getRowCount() {
        return (int) (
                getSystemRowCount()
                        + getServiceTargetRowCount()
                        + getCallerAndRankedTargetRowCount()
                        + getAzLabelRowCount()
                        + Math.ceil(
                        (float) mChooserListAdapter.getAlphaTargetCount()
                                / mMaxTargetsPerRow)
            );
    }

    /**
     * Whether the "system" row of targets is displayed.
     * This area includes the content preview (if present) and action row.
     */
    public int getSystemRowCount() {
        // For the tabbed case we show the sticky content preview above the tabs,
        // please refer to shouldShowStickyContentPreview
        if (mChooserActivityDelegate.shouldShowTabs()
                || mFeatureFlags.scrollablePreview()) {
            return 0;
        }

        if (!mShouldShowContentPreview) {
            return 0;
        }

        if (mChooserListAdapter == null || mChooserListAdapter.getCount() == 0) {
            return 0;
        }

        return 1;
    }

    public int getFooterRowCount() {
        return 1;
    }

    public int getCallerAndRankedTargetRowCount() {
        return (int) Math.ceil(
                ((float) mChooserListAdapter.getCallerTargetCount()
                        + mChooserListAdapter.getRankedTargetCount()) / mMaxTargetsPerRow);
    }

    // There can be at most one row in the listview, that is internally
    // a ViewGroup with 2 rows
    public int getServiceTargetRowCount() {
        if (mShouldShowContentPreview && !ActivityManager.isLowRamDeviceStatic()) {
            return 1;
        }
        return 0;
    }

    public int getAzLabelRowCount() {
        // Only show a label if the a-z list is showing
        return (mChooserListAdapter.getAlphaTargetCount() > 0) ? 1 : 0;
    }

    private int getAzLabelRowPosition() {
        int azRowCount = getAzLabelRowCount();
        if (azRowCount == 0) {
            return -1;
        }

        return getSystemRowCount()
                + getServiceTargetRowCount()
                + getCallerAndRankedTargetRowCount();
    }

    @Override
    public int getItemCount() {
        return getSystemRowCount()
                + getServiceTargetRowCount()
                + getCallerAndRankedTargetRowCount()
                + getAzLabelRowCount()
                + mChooserListAdapter.getAlphaTargetCount()
                + getFooterRowCount();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_CONTENT_PREVIEW:
                return new ItemViewHolder(
                        mChooserActivityDelegate.buildContentPreview(parent),
                        viewType,
                        null,
                        null);
            case VIEW_TYPE_AZ_LABEL:
                return new ItemViewHolder(
                        createAzLabelView(parent),
                        viewType,
                        null,
                        null);
            case VIEW_TYPE_NORMAL:
                return new ItemViewHolder(
                        mChooserListAdapter.createView(parent),
                        viewType,
                        mChooserActivityDelegate::onTargetSelected,
                        mChooserActivityDelegate::onTargetLongPressed);
            case VIEW_TYPE_DIRECT_SHARE:
            case VIEW_TYPE_CALLER_AND_RANK:
                return createItemGroupViewHolder(viewType, parent);
            case VIEW_TYPE_FOOTER:
                Space sp = new Space(parent.getContext());
                sp.setLayoutParams(new RecyclerView.LayoutParams(
                        LayoutParams.MATCH_PARENT, mFooterHeight));
                return new FooterViewHolder(sp, viewType);
            default:
                // Since we catch all possible viewTypes above, no chance this is being called.
                throw new IllegalStateException("unmatched view type");
        }
    }

    /**
     * Set the app divider's visibility, when it's present.
     */
    public void setAzLabelVisibility(boolean isVisible) {
        if (mAzLabelVisibility == isVisible) {
            return;
        }
        mAzLabelVisibility = isVisible;
        int azRowPos = getAzLabelRowPosition();
        if (azRowPos >= 0) {
            if (mRecyclerView != null) {
                for (int i = 0, size = mRecyclerView.getChildCount(); i < size; i++) {
                    View child = mRecyclerView.getChildAt(i);
                    if (mRecyclerView.getChildAdapterPosition(child) == azRowPos) {
                        child.setVisibility(isVisible ? View.VISIBLE : View.GONE);
                    }
                }
                return;
            }
            notifyItemChanged(azRowPos);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == VIEW_TYPE_AZ_LABEL) {
            holder.itemView.setVisibility(
                    mAzLabelVisibility ? View.VISIBLE : View.INVISIBLE);
        }
        int viewType = ((ViewHolderBase) holder).getViewType();
        switch (viewType) {
            case VIEW_TYPE_DIRECT_SHARE:
            case VIEW_TYPE_CALLER_AND_RANK:
                bindItemGroupViewHolder(position, (ItemGroupViewHolder) holder);
                break;
            case VIEW_TYPE_NORMAL:
                bindItemViewHolder(position, (ItemViewHolder) holder);
                break;
            default:
        }
    }

    @Override
    public int getItemViewType(int position) {
        int count;

        int countSum = (count = getSystemRowCount());
        if (count > 0 && position < countSum) return VIEW_TYPE_CONTENT_PREVIEW;

        countSum += (count = getServiceTargetRowCount());
        if (count > 0 && position < countSum) return VIEW_TYPE_DIRECT_SHARE;

        countSum += (count = getCallerAndRankedTargetRowCount());
        if (count > 0 && position < countSum) return VIEW_TYPE_CALLER_AND_RANK;

        countSum += (count = getAzLabelRowCount());
        if (count > 0 && position < countSum) return VIEW_TYPE_AZ_LABEL;

        if (position == getItemCount() - 1) return VIEW_TYPE_FOOTER;

        return VIEW_TYPE_NORMAL;
    }

    public int getTargetType(int position) {
        return mChooserListAdapter.getPositionTargetType(getListPosition(position));
    }

    private View createAzLabelView(ViewGroup parent) {
        return mLayoutInflater.inflate(R.layout.chooser_az_label_row, parent, false);
    }

    private ItemGroupViewHolder loadViewsIntoGroup(ItemGroupViewHolder holder) {
        final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int exactSpec = MeasureSpec.makeMeasureSpec(mChooserTargetWidth, MeasureSpec.EXACTLY);
        int columnCount = holder.getColumnCount();

        final boolean isDirectShare = holder instanceof DirectShareViewHolder;

        for (int i = 0; i < columnCount; i++) {
            final View v = mChooserListAdapter.createView(holder.getRowByIndex(i));
            final int column = i;
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mChooserActivityDelegate.onTargetSelected(holder.getItemIndex(column));
                }
            });

            // Show menu for both direct share and app share targets after long click.
            v.setOnLongClickListener(v1 -> {
                mChooserActivityDelegate.onTargetLongPressed(holder.getItemIndex(column));
                return true;
            });

            holder.addView(i, v);

            // Force Direct Share to be 2 lines and auto-wrap to second line via hoz scroll =
            // false. TextView#setHorizontallyScrolling must be reset after #setLines. Must be
            // done before measuring.
            if (isDirectShare) {
                final ViewHolder vh = (ViewHolder) v.getTag();
                vh.text.setLines(2);
                vh.text.setHorizontallyScrolling(false);
                vh.text2.setVisibility(View.GONE);
            }

            // Force height to be a given so we don't have visual disruption during scaling.
            v.measure(exactSpec, spec);
            setViewBounds(v, v.getMeasuredWidth(), v.getMeasuredHeight());
        }

        final ViewGroup viewGroup = holder.getViewGroup();

        // Pre-measure and fix height so we can scale later.
        holder.measure();
        setViewBounds(viewGroup, LayoutParams.MATCH_PARENT, holder.getMeasuredRowHeight());

        if (isDirectShare) {
            DirectShareViewHolder dsvh = (DirectShareViewHolder) holder;
            setViewBounds(dsvh.getRow(0), LayoutParams.MATCH_PARENT, dsvh.getMinRowHeight());
            setViewBounds(dsvh.getRow(1), LayoutParams.MATCH_PARENT, dsvh.getMinRowHeight());
        }

        viewGroup.setTag(holder);
        return holder;
    }

    private void setViewBounds(View view, int widthPx, int heightPx) {
        LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(widthPx, heightPx);
            view.setLayoutParams(lp);
        } else {
            lp.height = heightPx;
            lp.width = widthPx;
        }
    }

    ItemGroupViewHolder createItemGroupViewHolder(int viewType, ViewGroup parent) {
        if (viewType == VIEW_TYPE_DIRECT_SHARE) {
            ViewGroup parentGroup = (ViewGroup) mLayoutInflater.inflate(
                    R.layout.chooser_row_direct_share, parent, false);
            ViewGroup row1 = (ViewGroup) mLayoutInflater.inflate(
                    R.layout.chooser_row, parentGroup, false);
            ViewGroup row2 = (ViewGroup) mLayoutInflater.inflate(
                    R.layout.chooser_row, parentGroup, false);
            parentGroup.addView(row1);
            parentGroup.addView(row2);

            DirectShareViewHolder directShareViewHolder = new DirectShareViewHolder(parentGroup,
                    Lists.newArrayList(row1, row2), mMaxTargetsPerRow, viewType);
            loadViewsIntoGroup(directShareViewHolder);

            return directShareViewHolder;
        } else {
            ViewGroup row = (ViewGroup) mLayoutInflater.inflate(
                    R.layout.chooser_row, parent, false);
            ItemGroupViewHolder holder =
                    new SingleRowViewHolder(row, mMaxTargetsPerRow, viewType);
            loadViewsIntoGroup(holder);

            return holder;
        }
    }

    /**
     * Need to merge CALLER + ranked STANDARD into a single row and prevent a separator from
     * showing on top of the AZ list if the AZ label is visible. All other types are placed into
     * their own row as determined by their target type, and dividers are added in the list to
     * separate each type.
     */
    int getRowType(int rowPosition) {
        // Merge caller and ranked standard into a single row
        int positionType = mChooserListAdapter.getPositionTargetType(rowPosition);
        if (positionType == ChooserListAdapter.TARGET_CALLER) {
            return ChooserListAdapter.TARGET_STANDARD;
        }

        // If an A-Z label is shown, prevent a separator from appearing by making the A-Z
        // row type the same as the suggestion row type
        if (getAzLabelRowCount() > 0 && positionType == ChooserListAdapter.TARGET_STANDARD_AZ) {
            return ChooserListAdapter.TARGET_STANDARD;
        }

        return positionType;
    }

    void bindItemViewHolder(int position, ItemViewHolder holder) {
        View v = holder.itemView;
        int listPosition = getListPosition(position);
        holder.setListPosition(listPosition);
        mChooserListAdapter.bindView(listPosition, v);
    }

    void bindItemGroupViewHolder(int position, ItemGroupViewHolder holder) {
        final ViewGroup viewGroup = (ViewGroup) holder.itemView;
        int start = getListPosition(position);
        int startType = getRowType(start);

        int columnCount = holder.getColumnCount();
        int end = start + columnCount - 1;
        while (getRowType(end) != startType && end >= start) {
            end--;
        }

        if (end == start && mChooserListAdapter.getItem(start).isEmptyTargetInfo()) {
            final TextView textView = viewGroup.findViewById(
                    com.android.internal.R.id.chooser_row_text_option);

            if (textView.getVisibility() != View.VISIBLE) {
                textView.setAlpha(0.0f);
                textView.setVisibility(View.VISIBLE);
                textView.setText(R.string.chooser_no_direct_share_targets);

                ValueAnimator fadeAnim = ObjectAnimator.ofFloat(textView, "alpha", 0.0f, 1.0f);
                fadeAnim.setInterpolator(new DecelerateInterpolator(1.0f));

                textView.setTranslationY(mChooserRowTextOptionTranslatePixelSize);
                ValueAnimator translateAnim =
                        ObjectAnimator.ofFloat(textView, "translationY", 0.0f);
                translateAnim.setInterpolator(new DecelerateInterpolator(1.0f));

                AnimatorSet animSet = new AnimatorSet();
                animSet.setDuration(NO_DIRECT_SHARE_ANIM_IN_MILLIS);
                animSet.setStartDelay(NO_DIRECT_SHARE_ANIM_IN_MILLIS);
                animSet.playTogether(fadeAnim, translateAnim);
                animSet.start();
            }
        }

        for (int i = 0; i < columnCount; i++) {
            final View v = holder.getView(i);

            if (start + i <= end) {
                holder.setViewVisibility(i, View.VISIBLE);
                holder.setItemIndex(i, start + i);
                mChooserListAdapter.bindView(holder.getItemIndex(i), v);
            } else {
                holder.setViewVisibility(i, View.INVISIBLE);
            }
        }
    }

    int getListPosition(int position) {
        position -= getSystemRowCount();

        final int serviceCount = mChooserListAdapter.getServiceTargetCount();
        final int serviceRows = (int) Math.ceil((float) serviceCount / mMaxTargetsPerRow);
        if (position < serviceRows) {
            return position * mMaxTargetsPerRow;
        }

        position -= serviceRows;

        final int callerAndRankedCount =
                mChooserListAdapter.getCallerTargetCount()
                + mChooserListAdapter.getRankedTargetCount();
        final int callerAndRankedRows = getCallerAndRankedTargetRowCount();
        if (position < callerAndRankedRows) {
            return serviceCount + position * mMaxTargetsPerRow;
        }

        position -= getAzLabelRowCount() + callerAndRankedRows;

        return callerAndRankedCount + serviceCount + position;
    }

    public ChooserListAdapter getListAdapter() {
        return mChooserListAdapter;
    }

    public boolean shouldCellSpan(int position) {
        return getItemViewType(position) == VIEW_TYPE_NORMAL;
    }
}
