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
package com.android.customization.widget;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.wallpaper.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple controller for a RecyclerView-based widget to hold the options for each customization
 * section (eg, thumbnails for themes, clocks, grid sizes).
 * To use, just pass the RV that will contain the tiles and the list of {@link CustomizationOption}
 * representing each option, and call {@link #initOptions(CustomizationManager)} to populate the
 * widget.
 */
public class OptionSelectorController<T extends CustomizationOption<T>> {

    /**
     * Interface to be notified when an option is selected by the user.
     */
    public interface OptionSelectedListener {

        /**
         * Called when an option has been selected (and marked as such in the UI)
         */
        void onOptionSelected(CustomizationOption selected);
    }

    @IntDef({CheckmarkStyle.NONE, CheckmarkStyle.CORNER, CheckmarkStyle.CENTER,
            CheckmarkStyle.CENTER_CHANGE_COLOR_WHEN_NOT_SELECTED})
    public @interface CheckmarkStyle {
        int NONE = 0;
        int CORNER = 1;
        int CENTER = 2;
        int CENTER_CHANGE_COLOR_WHEN_NOT_SELECTED = 3;
    }

    private final float mLinearLayoutHorizontalDisplayOptionsMax;

    private final RecyclerView mContainer;
    private final List<T> mOptions;
    private final boolean mUseGrid;
    @CheckmarkStyle
    private final int mCheckmarkStyle;

    private final Set<OptionSelectedListener> mListeners = new HashSet<>();
    private RecyclerView.Adapter<TileViewHolder> mAdapter;
    private T mSelectedOption;
    private T mAppliedOption;

    public OptionSelectorController(RecyclerView container, List<T> options) {
        this(container, options, true, CheckmarkStyle.CORNER);
    }

    public OptionSelectorController(RecyclerView container, List<T> options,
            boolean useGrid, @CheckmarkStyle int checkmarkStyle) {
        mContainer = container;
        mOptions = options;
        mUseGrid = useGrid;
        mCheckmarkStyle = checkmarkStyle;
        TypedValue typedValue = new TypedValue();
        mContainer.getResources().getValue(R.dimen.linear_layout_horizontal_display_options_max,
                typedValue, true);
        mLinearLayoutHorizontalDisplayOptionsMax = typedValue.getFloat();
    }

    public void addListener(OptionSelectedListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(OptionSelectedListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Mark the given option as selected
     */
    public void setSelectedOption(T option) {
        if (!mOptions.contains(option)) {
            throw new IllegalArgumentException("Invalid option");
        }
        T lastSelectedOption = mSelectedOption;
        mSelectedOption = option;
        mAdapter.notifyItemChanged(mOptions.indexOf(option));
        if (lastSelectedOption != null) {
            mAdapter.notifyItemChanged(mOptions.indexOf(lastSelectedOption));
        }
        notifyListeners();
    }

    /**
     * @return whether this controller contains the given option
     */
    public boolean containsOption(T option) {
        return mOptions.contains(option);
    }

    /**
     * Mark an option as the one which is currently applied on the device. This will result in a
     * check being displayed in the lower-right corner of the corresponding ViewHolder.
     */
    public void setAppliedOption(T option) {
        if (!mOptions.contains(option)) {
            throw new IllegalArgumentException("Invalid option");
        }
        T lastAppliedOption = mAppliedOption;
        mAppliedOption = option;
        mAdapter.notifyItemChanged(mOptions.indexOf(option));
        if (lastAppliedOption != null) {
            mAdapter.notifyItemChanged(mOptions.indexOf(lastAppliedOption));
        }
    }

    /**
     * Notify that a given option has changed.
     *
     * @param option the option that changed
     */
    public void optionChanged(T option) {
        if (!mOptions.contains(option)) {
            throw new IllegalArgumentException("Invalid option");
        }
        mAdapter.notifyItemChanged(mOptions.indexOf(option));
    }

    /**
     * Initializes the UI for the options passed in the constructor of this class.
     */
    public void initOptions(final CustomizationManager<T> manager) {
        mContainer.setAccessibilityDelegateCompat(
                new OptionSelectorAccessibilityDelegate(mContainer));

        mAdapter = new RecyclerView.Adapter<TileViewHolder>() {
            @Override
            public int getItemViewType(int position) {
                return mOptions.get(position).getLayoutResId();
            }

            @NonNull
            @Override
            public TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
                // Provide width constraint when a grid layout manager is not use and width is set
                // to match parent
                if (!mUseGrid
                        && v.getLayoutParams().width == RecyclerView.LayoutParams.MATCH_PARENT) {
                    Resources res = mContainer.getContext().getResources();
                    RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                            res.getDimensionPixelSize(R.dimen.option_tile_width),
                            RecyclerView.LayoutParams.WRAP_CONTENT);
                    v.setLayoutParams(layoutParams);
                }
                return new TileViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
                T option = mOptions.get(position);
                if (option.isActive(manager)) {
                    mAppliedOption = option;
                    if (mSelectedOption == null) {
                        mSelectedOption = option;
                    }
                }
                if (holder.labelView != null) {
                    holder.labelView.setText(option.getTitle());
                    holder.labelView.setSelected(true);
                }
                holder.itemView.setActivated(option.equals(mSelectedOption));
                option.bindThumbnailTile(holder.tileView);
                holder.itemView.setOnClickListener(view -> setSelectedOption(option));

                Resources res = mContainer.getContext().getResources();
                if (mCheckmarkStyle == CheckmarkStyle.CORNER && option.equals(mAppliedOption)) {
                    drawCheckmark(option, holder,
                            res.getDrawable(R.drawable.check_circle_accent_24dp,
                                    mContainer.getContext().getTheme()),
                            Gravity.BOTTOM | Gravity.RIGHT,
                            res.getDimensionPixelSize(R.dimen.check_size),
                            res.getDimensionPixelOffset(R.dimen.check_offset), true);
                } else if (mCheckmarkStyle == CheckmarkStyle.CENTER
                        && option.equals(mAppliedOption)) {
                    drawCheckmark(option, holder,
                            res.getDrawable(R.drawable.check_circle_grey_large,
                                    mContainer.getContext().getTheme()),
                            Gravity.CENTER, res.getDimensionPixelSize(R.dimen.center_check_size),
                            0, true);
                } else if (mCheckmarkStyle == CheckmarkStyle.CENTER_CHANGE_COLOR_WHEN_NOT_SELECTED
                        && option.equals(mAppliedOption)) {
                    int drawableRes = option.equals(mSelectedOption)
                            ? R.drawable.check_circle_grey_large
                            : R.drawable.check_circle_grey_large_not_select;
                    drawCheckmark(option, holder,
                            res.getDrawable(drawableRes,
                                    mContainer.getContext().getTheme()),
                            Gravity.CENTER, res.getDimensionPixelSize(R.dimen.center_check_size),
                            0, option.equals(mSelectedOption));
                } else if (option.equals(mAppliedOption)) {
                    // Initialize with "previewed" description if we don't show checkmark
                    holder.setContentDescription(mContainer.getContext(), option,
                            R.string.option_previewed_description);
                } else if (mCheckmarkStyle != CheckmarkStyle.NONE) {
                    if (mCheckmarkStyle == CheckmarkStyle.CENTER_CHANGE_COLOR_WHEN_NOT_SELECTED) {
                        if (option.equals(mSelectedOption)) {
                            holder.setContentDescription(mContainer.getContext(), option,
                                    R.string.option_previewed_description);
                        } else {
                            holder.setContentDescription(mContainer.getContext(), option,
                                    R.string.option_change_applied_previewed_description);
                        }
                    }

                    holder.tileView.setForeground(null);
                }
            }

            @Override
            public int getItemCount() {
                return mOptions.size();
            }

            private void drawCheckmark(CustomizationOption<?> option, TileViewHolder holder,
                    Drawable checkmark, int gravity, @Dimension int checkSize,
                    @Dimension int checkOffset, boolean currentlyPreviewed) {
                Drawable frame = holder.tileView.getForeground();
                Drawable[] layers = {frame, checkmark};
                if (frame == null) {
                    layers = new Drawable[]{checkmark};
                }
                LayerDrawable checkedFrame = new LayerDrawable(layers);

                // Position according to the given gravity and offset
                int idx = layers.length - 1;
                checkedFrame.setLayerGravity(idx, gravity);
                checkedFrame.setLayerWidth(idx, checkSize);
                checkedFrame.setLayerHeight(idx, checkSize);
                checkedFrame.setLayerInsetBottom(idx, checkOffset);
                checkedFrame.setLayerInsetRight(idx, checkOffset);
                holder.tileView.setForeground(checkedFrame);

                // Initialize the currently applied option
                if (currentlyPreviewed) {
                    holder.setContentDescription(mContainer.getContext(), option,
                            R.string.option_applied_previewed_description);
                } else {
                    holder.setContentDescription(mContainer.getContext(), option,
                            R.string.option_applied_description);
                }
            }
        };

        Resources res = mContainer.getContext().getResources();
        mContainer.setAdapter(mAdapter);
        final DisplayMetrics metrics = new DisplayMetrics();
        mContainer.getContext().getSystemService(WindowManager.class)
                .getDefaultDisplay().getMetrics(metrics);
        final boolean hasDecoration = mContainer.getItemDecorationCount() != 0;

        if (mUseGrid) {
            int numColumns = res.getInteger(R.integer.options_grid_num_columns);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(mContainer.getContext(),
                    numColumns);
            mContainer.setLayoutManager(gridLayoutManager);
        } else {
            final int padding = res.getDimensionPixelSize(
                    R.dimen.option_tile_linear_padding_horizontal);
            final int widthPerItem = res.getDimensionPixelSize(R.dimen.option_tile_width) + (
                    hasDecoration ? 0 : 2 * padding);
            mContainer.setLayoutManager(new LinearLayoutManager(mContainer.getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            mContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int availableWidth = metrics.widthPixels;
            int extraSpace = availableWidth - mContainer.getMeasuredWidth();
            if (extraSpace >= 0) {
                mContainer.setOverScrollMode(View.OVER_SCROLL_NEVER);
            }

            if (mAdapter.getItemCount() >= mLinearLayoutHorizontalDisplayOptionsMax) {
                int spaceBetweenItems = availableWidth
                        - Math.round(widthPerItem * mLinearLayoutHorizontalDisplayOptionsMax)
                        - mContainer.getPaddingLeft();
                int itemEndMargin =
                        spaceBetweenItems / (int) mLinearLayoutHorizontalDisplayOptionsMax;
                itemEndMargin = Math.max(itemEndMargin, res.getDimensionPixelOffset(
                        R.dimen.option_tile_margin_horizontal));
                mContainer.addItemDecoration(new ItemEndHorizontalSpaceItemDecoration(
                        mContainer.getContext(), itemEndMargin));
                return;
            }

            int spaceBetweenItems = extraSpace / (mAdapter.getItemCount() + 1);
            int itemSideMargin = spaceBetweenItems / 2;
            mContainer.addItemDecoration(new HorizontalSpacerItemDecoration(itemSideMargin));
        }
    }

    public void resetOptions(List<T> options) {
        mOptions.clear();
        mOptions.addAll(options);
        mAdapter.notifyDataSetChanged();
    }

    private void notifyListeners() {
        if (mListeners.isEmpty()) {
            return;
        }
        T option = mSelectedOption;
        Set<OptionSelectedListener> iterableListeners = new HashSet<>(mListeners);
        for (OptionSelectedListener listener : iterableListeners) {
            listener.onOptionSelected(option);
        }
    }

    private static class TileViewHolder extends RecyclerView.ViewHolder {
        TextView labelView;
        View tileView;
        CharSequence title;

        TileViewHolder(@NonNull View itemView) {
            super(itemView);
            labelView = itemView.findViewById(R.id.option_label);
            tileView = itemView.findViewById(R.id.option_tile);
            title = null;
        }

        /**
         * Set the content description for this holder using the given string id.
         * If the option does not have a label, the description will be set on the tile view.
         *
         * @param context The view's context
         * @param option  The customization option
         * @param id      Resource ID of the string to use for the content description
         */
        public void setContentDescription(Context context, CustomizationOption<?> option, int id) {
            title = option.getTitle();
            if (TextUtils.isEmpty(title) && tileView != null) {
                title = tileView.getContentDescription();
            }

            CharSequence cd = context.getString(id, title);
            if (labelView != null && !TextUtils.isEmpty(labelView.getText())) {
                labelView.setAccessibilityPaneTitle(cd);
                labelView.setContentDescription(cd);
                labelView.setSelected(true);
            } else if (tileView != null) {
                tileView.setAccessibilityPaneTitle(cd);
                tileView.setContentDescription(cd);
            }
        }

        public void resetContentDescription() {
            if (labelView != null && !TextUtils.isEmpty(labelView.getText())) {
                labelView.setAccessibilityPaneTitle(title);
                labelView.setContentDescription(title);
                labelView.setSelected(true);
            } else if (tileView != null) {
                tileView.setAccessibilityPaneTitle(title);
                tileView.setContentDescription(title);
            }
        }
    }

    private class OptionSelectorAccessibilityDelegate extends RecyclerViewAccessibilityDelegate {

        OptionSelectorAccessibilityDelegate(RecyclerView recyclerView) {
            super(recyclerView);
        }

        @Override
        public boolean onRequestSendAccessibilityEvent(
                ViewGroup host, View child, AccessibilityEvent event) {

            // Apply this workaround to horizontal recyclerview only,
            // since the symptom is TalkBack will lose focus when navigating horizontal list items.
            if (mContainer.getLayoutManager() != null
                    && mContainer.getLayoutManager().canScrollHorizontally()
                    && event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                int itemPos = mContainer.getChildLayoutPosition(child);
                int itemWidth = mContainer.getContext().getResources()
                        .getDimensionPixelOffset(R.dimen.option_tile_width);
                int itemMarginHorizontal = mContainer.getContext().getResources()
                        .getDimensionPixelOffset(R.dimen.option_tile_margin_horizontal) * 2;
                int scrollOffset = itemWidth + itemMarginHorizontal;

                // Make focusing item's previous/next item totally visible when changing focus,
                // ensure TalkBack won't lose focus when recyclerview scrolling.
                if (itemPos >= ((LinearLayoutManager) mContainer.getLayoutManager())
                        .findLastCompletelyVisibleItemPosition()) {
                    mContainer.scrollBy(scrollOffset, 0);
                } else if (itemPos <= ((LinearLayoutManager) mContainer.getLayoutManager())
                        .findFirstCompletelyVisibleItemPosition() && itemPos != 0) {
                    mContainer.scrollBy(-scrollOffset, 0);
                }
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    }

    /** Custom ItemDecorator to add specific spacing between items in the list. */
    private static final class ItemEndHorizontalSpaceItemDecoration
            extends RecyclerView.ItemDecoration {
        private final int mHorizontalSpacePx;
        private final boolean mDirectionLTR;

        private ItemEndHorizontalSpaceItemDecoration(Context context, int horizontalSpacePx) {
            mDirectionLTR = context.getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_LTR;
            mHorizontalSpacePx = horizontalSpacePx;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView recyclerView,
                RecyclerView.State state) {
            if (recyclerView.getAdapter() == null) {
                return;
            }

            if (recyclerView.getChildAdapterPosition(view)
                    != checkNotNull(recyclerView.getAdapter()).getItemCount() - 1) {
                // Don't add spacing behind the last item
                if (mDirectionLTR) {
                    outRect.right = mHorizontalSpacePx;
                } else {
                    outRect.left = mHorizontalSpacePx;
                }
            }
        }
    }
}
