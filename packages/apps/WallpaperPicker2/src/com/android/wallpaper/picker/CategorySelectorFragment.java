/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wallpaper.picker;

import static com.android.wallpaper.picker.WallpaperPickerDelegate.PREVIEW_LIVE_WALLPAPER_REQUEST_CODE;
import static com.android.wallpaper.picker.WallpaperPickerDelegate.PREVIEW_WALLPAPER_REQUEST_CODE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.effects.EffectsController;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.util.DeepLinkUtils;
import com.android.wallpaper.util.DisplayMetricsRetriever;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.util.SizeCalculator;
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate;
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate.BottomSheetHost;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the UI which contains the categories of the wallpaper.
 */
public class CategorySelectorFragment extends AppbarFragment {

    // The number of ViewHolders that don't pertain to category tiles.
    // Currently 2: one for the metadata section and one for the "Select wallpaper" header.
    private static final int NUM_NON_CATEGORY_VIEW_HOLDERS = 0;
    private static final int SETTINGS_APP_INFO_REQUEST_CODE = 1;
    private static final String TAG = "CategorySelectorFragment";
    private static final String IMAGE_WALLPAPER_COLLECTION_ID = "image_wallpapers";

    /**
     * Interface to be implemented by an Fragment hosting a {@link CategorySelectorFragment}
     */
    public interface CategorySelectorFragmentHost {

        /**
         * Requests to show the Android custom photo picker for the sake of picking a photo
         * to set as the device's wallpaper.
         */
        void requestCustomPhotoPicker(MyPhotosStarter.PermissionChangedListener listener);

        /**
         * Shows the wallpaper page of the specific category.
         *
         * @param category the wallpaper's {@link Category}
         */
        void show(Category category);


        /**
         * Indicates if the host has toolbar to show the title. If it does, we should set the title
         * there.
         */
        boolean isHostToolbarShown();

        /**
         * Sets the title in the host's toolbar.
         */
        void setToolbarTitle(CharSequence title);

        /**
         * Fetches the wallpaper categories.
         */
        void fetchCategories();

        /**
         * Cleans up the listeners which will be notified when there's a package event.
         */
        void cleanUp();
    }

    private RecyclerView mImageGrid;
    private CategoryAdapter mAdapter;
    private GroupedCategoryAdapter mGroupedCategoryAdapter;
    private CategoryProvider mCategoryProvider;
    private ArrayList<Category> mCategories = new ArrayList<>();
    private Point mTileSizePx;
    private boolean mAwaitingCategories;
    private ProgressBar mLoadingIndicator;
    private ArrayList<Category> mCreativeCategories = new ArrayList<>();
    private boolean mIsFeaturedCollectionAvailable;
    private boolean mIsCreativeCategoryCollectionAvailable;
    private boolean mIsCreativeWallpaperEnabled = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCategoryProvider = InjectorProvider.getInjector().getCategoryProvider(requireContext());
        mIsCreativeWallpaperEnabled = InjectorProvider.getInjector()
            .getFlags().isAIWallpaperEnabled(requireContext());
        if (mIsCreativeWallpaperEnabled) {
            mGroupedCategoryAdapter = new GroupedCategoryAdapter(mCategories);
        } else {
            mAdapter = new CategoryAdapter(mCategories);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category_selector, container,
                /* attachToRoot= */ false);
        mImageGrid = view.findViewById(R.id.category_grid);
        mImageGrid.addItemDecoration(new GridPaddingDecoration(getResources().getDimensionPixelSize(
                R.dimen.grid_item_category_padding_horizontal)));
        mTileSizePx = SizeCalculator.getCategoryTileSize(getActivity());
        // In case CreativeWallpapers are enabled, it means we want to show the new view
        // in the picker for which we have made a new adaptor
        if (mIsCreativeWallpaperEnabled) {
            mImageGrid.setAdapter(mGroupedCategoryAdapter);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(),
                    getNumColumns()
                            * GroupedCategorySpanSizeLookup.DEFAULT_CATEGORY_SPAN_SIZE);
            gridLayoutManager.setSpanSizeLookup(new
                    GroupedCategorySpanSizeLookup(mGroupedCategoryAdapter));
            mImageGrid.setLayoutManager(gridLayoutManager);
            //TODO (b/290267060): To be fixed when re-factoring of loading categories is done
            mImageGrid.setItemAnimator(null);
        } else {
            mImageGrid.setAdapter(mAdapter);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(),
                    getNumColumns() * CategorySpanSizeLookup.DEFAULT_CATEGORY_SPAN_SIZE);
            gridLayoutManager.setSpanSizeLookup(new CategorySpanSizeLookup(mAdapter));
            mImageGrid.setLayoutManager(gridLayoutManager);
        }

        mLoadingIndicator = view.findViewById(R.id.loading_indicator);
        mLoadingIndicator.setVisibility(View.VISIBLE);
        mImageGrid.setVisibility(View.INVISIBLE);
        mImageGrid.setAccessibilityDelegateCompat(
                new WallpaperPickerRecyclerViewAccessibilityDelegate(
                        mImageGrid, (BottomSheetHost) getParentFragment(), getNumColumns()));

        if (getCategorySelectorFragmentHost().isHostToolbarShown()) {
            view.findViewById(R.id.header_bar).setVisibility(View.GONE);
            getCategorySelectorFragmentHost().setToolbarTitle(getText(R.string.wallpaper_title));
        } else {
            setUpToolbar(view);
            setTitle(getText(R.string.wallpaper_title));
        }

        if (!DeepLinkUtils.isDeepLink(getActivity().getIntent())) {
            getCategorySelectorFragmentHost().fetchCategories();
        }

        // For nav bar edge-to-edge effect.
        mImageGrid.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    windowInsets.getSystemWindowInsetBottom());
            return windowInsets.consumeSystemWindowInsets();
        });
        return view;
    }

    @Override
    public void onDestroyView() {
        getCategorySelectorFragmentHost().cleanUp();
        super.onDestroyView();
    }

    /**
     * Inserts the given category into the categories list in priority order.
     */
    void addCategory(Category category, boolean loading) {
        // If not previously waiting for categories, enter the waiting state by showing the loading
        // indicator.
        if (mIsCreativeWallpaperEnabled) {
            if (loading && !mAwaitingCategories) {
                mAwaitingCategories = true;
            }
            // Not add existing category to category list
            if (mCategories.indexOf(category) >= 0) {
                updateCategory(category);
                return;
            }

            int priority = category.getPriority();
            if (category.supportsUserCreatedWallpapers()) {
                mCreativeCategories.add(category);
            }

            int index = 0;
            while (index < mCategories.size() && priority >= mCategories.get(index).getPriority()) {
                index++;
            }

            mCategories.add(index, category);
        } else {
            if (loading && !mAwaitingCategories) {
                mAdapter.notifyItemChanged(getNumColumns());
                mAdapter.notifyItemInserted(getNumColumns());
                mAwaitingCategories = true;
            }
            // Not add existing category to category list
            if (mCategories.indexOf(category) >= 0) {
                updateCategory(category);
                return;
            }

            int priority = category.getPriority();

            int index = 0;
            while (index < mCategories.size() && priority >= mCategories.get(index).getPriority()) {
                index++;
            }

            mCategories.add(index, category);
            if (mAdapter != null) {
                // Offset the index because of the static metadata element
                // at beginning of RecyclerView.
                mAdapter.notifyItemInserted(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
            }
        }
    }

    void removeCategory(Category category) {
        int index = mCategories.indexOf(category);
        if (index >= 0) {
            mCategories.remove(index);
            if (mIsCreativeWallpaperEnabled) {
                int indexCreativeCategory = mCreativeCategories.indexOf(category);
                if (indexCreativeCategory >= 0) {
                    mCreativeCategories.remove(indexCreativeCategory);
                }
            } else {
                mAdapter.notifyItemRemoved(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
            }
        }
    }

    void updateCategory(Category category) {
        int index = mCategories.indexOf(category);
        if (index >= 0) {
            mCategories.set(index, category);
            if (mIsCreativeWallpaperEnabled) {
                int indexCreativeCategory = mCreativeCategories.indexOf(category);
                if (indexCreativeCategory >= 0) {
                    mCreativeCategories.set(indexCreativeCategory, category);
                }
            } else {
                mAdapter.notifyItemChanged(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
            }
        }
    }

    void clearCategories() {
        mCategories.clear();
        if (mIsCreativeWallpaperEnabled) {
            mCreativeCategories.clear();
            mGroupedCategoryAdapter.notifyDataSetChanged();
        } else {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Notifies that no further categories are expected.
     */
    void doneFetchingCategories() {
        notifyDataSetChanged();
        mLoadingIndicator.setVisibility(View.INVISIBLE);
        mImageGrid.setVisibility(View.VISIBLE);
        mAwaitingCategories = false;
        mIsFeaturedCollectionAvailable = mCategoryProvider.isFeaturedCollectionAvailable();
        mIsCreativeCategoryCollectionAvailable = mCategoryProvider.isCreativeCategoryAvailable();
    }

    void notifyDataSetChanged() {
        if (mIsCreativeWallpaperEnabled) {
            mGroupedCategoryAdapter.notifyDataSetChanged();
        } else {
            mAdapter.notifyDataSetChanged();
        }
    }

    private int getNumColumns() {
        Activity activity = getActivity();
        return activity == null ? 1 : SizeCalculator.getNumCategoryColumns(activity);
    }


    private CategorySelectorFragmentHost getCategorySelectorFragmentHost() {
        Fragment parentFragment = getParentFragment();
        if (parentFragment != null) {
            return (CategorySelectorFragmentHost) parentFragment;
        } else {
            return (CategorySelectorFragmentHost) getActivity();
        }
    }

    /**
     * ViewHolder subclass for a category tile in the RecyclerView.
     */
    private class CategoryHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Category mCategory;
        private ImageView mImageView;
        private ImageView mOverlayIconView;
        private TextView mTitleView;

        CategoryHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);

            mImageView = itemView.findViewById(R.id.image);
            mOverlayIconView = itemView.findViewById(R.id.overlay_icon);
            mTitleView = itemView.findViewById(R.id.category_title);

            CardView categoryView = itemView.findViewById(R.id.category);
            categoryView.getLayoutParams().height = mTileSizePx.y;
            categoryView.setRadius(getResources().getDimension(R.dimen.grid_item_all_radius_small));
        }

        @Override
        public void onClick(View view) {
            Activity activity = getActivity();

            if (mCategory.supportsCustomPhotos()) {
                EffectsController effectsController =
                        InjectorProvider.getInjector().getEffectsController(getContext());
                if (effectsController != null && !effectsController.isEffectTriggered()) {
                    effectsController.triggerEffect(getContext());
                }
                getCategorySelectorFragmentHost().requestCustomPhotoPicker(
                        new MyPhotosStarter.PermissionChangedListener() {
                            @Override
                            public void onPermissionsGranted() {
                                drawThumbnailAndOverlayIcon();
                            }

                            @Override
                            public void onPermissionsDenied(boolean dontAskAgain) {
                                if (dontAskAgain) {
                                    showPermissionSnackbar();
                                }
                            }
                        });
                return;
            }

            if (mCategory.isSingleWallpaperCategory()) {
                WallpaperInfo wallpaper = mCategory.getSingleWallpaper();

                InjectorProvider.getInjector().getWallpaperPersister(activity)
                        .setWallpaperInfoInPreview(wallpaper);
                wallpaper.showPreview(activity,
                        InjectorProvider.getInjector().getPreviewActivityIntentFactory(),
                        wallpaper instanceof LiveWallpaperInfo ? PREVIEW_LIVE_WALLPAPER_REQUEST_CODE
                                : PREVIEW_WALLPAPER_REQUEST_CODE, true);
                return;
            }

            getCategorySelectorFragmentHost().show(mCategory);
        }

        /**
         * Binds the given category to this CategoryHolder.
         */
        private void bindCategory(Category category) {
            mCategory = category;
            mTitleView.setText(category.getTitle());
            drawThumbnailAndOverlayIcon();
            // We do this since itemView here refers to the broader LinearLayout defined in
            // xml layout file of myPhotos block. Doing this allows us to make sure that the
            // onClickListener is configured only on the CardView of MyPhotos and nowhere else
            if (mIsCreativeWallpaperEnabled && mCategory != null
                    && TextUtils.equals(mCategory.getCollectionId(),
                    getActivity().getApplicationContext().getString(
                            R.string.image_wallpaper_collection_id))) {
                itemView.setOnClickListener(null);
                CardView categoryView = itemView.findViewById(R.id.category);
                categoryView.setOnClickListener(this);
            }
        }

        /**
         * Draws the CategoryHolder's thumbnail and overlay icon.
         */
        private void drawThumbnailAndOverlayIcon() {
            mOverlayIconView.setImageDrawable(mCategory.getOverlayIcon(
                    getActivity().getApplicationContext()));
            Asset thumbnail = mCategory.getThumbnail(getActivity().getApplicationContext());
            if (thumbnail != null) {
                // Size the overlay icon according to the category.
                int overlayIconDimenDp = mCategory.getOverlayIconSizeDp();
                DisplayMetrics metrics = DisplayMetricsRetriever.getInstance().getDisplayMetrics(
                        getResources(), getActivity().getWindowManager().getDefaultDisplay());
                int overlayIconDimenPx = (int) (overlayIconDimenDp * metrics.density);
                mOverlayIconView.getLayoutParams().width = overlayIconDimenPx;
                mOverlayIconView.getLayoutParams().height = overlayIconDimenPx;
                thumbnail.loadDrawable(getActivity(), mImageView,
                        ResourceUtils.getColorAttr(
                                getActivity(),
                                android.R.attr.colorSecondary
                        ));
            } else {
                // TODO(orenb): Replace this workaround for b/62584914 with a proper way of
                //  unloading the ImageView such that no incorrect image is improperly loaded upon
                //  rapid scroll.
                mImageView.setBackgroundColor(
                        getResources().getColor(R.color.myphoto_background_color));
                Object nullObj = null;
                Glide.with(getActivity())
                        .asDrawable()
                        .load(nullObj)
                        .into(mImageView);

            }
        }
    }

    private void showPermissionSnackbar() {
        Snackbar snackbar = Snackbar.make(getView(), R.string.settings_snackbar_description,
                Snackbar.LENGTH_LONG);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
        TextView textView = (TextView) layout.findViewById(
                com.google.android.material.R.id.snackbar_text);
        layout.setBackgroundResource(R.drawable.snackbar_background);
        TypedArray typedArray = getContext().obtainStyledAttributes(
                new int[]{android.R.attr.textColorPrimary,
                        com.android.internal.R.attr.materialColorPrimaryContainer});
        textView.setTextColor(typedArray.getColor(0, Color.TRANSPARENT));
        snackbar.setActionTextColor(typedArray.getColor(1, Color.TRANSPARENT));
        typedArray.recycle();

        snackbar.setAction(getContext().getString(R.string.settings_snackbar_enable),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startSettings(SETTINGS_APP_INFO_REQUEST_CODE);
                    }
                });
        snackbar.show();
    }

    private void startSettings(int resultCode) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), /* fragment= */ null);
        appInfoIntent.setData(uri);
        startActivityForResult(appInfoIntent, resultCode);
    }

    /*
       This is for FeaturedCategories and only present in CategoryAdaptor
     */
    private class FeaturedCategoryHolder extends CategoryHolder {

        FeaturedCategoryHolder(View itemView) {
            super(itemView);
            CardView categoryView = itemView.findViewById(R.id.category);
            categoryView.getLayoutParams().height =
                    SizeCalculator.getFeaturedCategoryTileSize(getActivity()).y;
            categoryView.setRadius(getResources().getDimension(R.dimen.grid_item_all_radius));
        }
    }

    /*
       This is re-used between both GroupedCategoryAdaptor and CategoryAdaptor
     */
    private class MyPhotosCategoryHolder extends CategoryHolder {

        MyPhotosCategoryHolder(View itemView) {
            super(itemView);
            // Reuse the height of featured category since My Photos category & featured category
            // have the same height in current UI design.
            CardView categoryView = itemView.findViewById(R.id.category);
            int height = SizeCalculator.getFeaturedCategoryTileSize(getActivity()).y;
            categoryView.getLayoutParams().height = height;
            // Use the height as the card corner radius for the "My photos" category
            // for a stadium border.
            categoryView.setRadius(height);
        }
    }

    private class GroupCategoryHolder extends RecyclerView.ViewHolder {
        private static final float INDIVIDUAL_TILE_WEIGHT = 1.0f;
        LayoutInflater mLayoutInflater = LayoutInflater.from(getActivity());
        private ArrayList<Category> mCategories = new ArrayList<>();
        private ArrayList<ImageView> mImageViews = new ArrayList<>();
        private ArrayList<ImageView> mOverlayIconViews = new ArrayList<>();
        private ArrayList<TextView> mTextViews = new ArrayList<>();

        GroupCategoryHolder(View itemView, int mCreativeCategoriesSize) {
            super(itemView);
            LinearLayout linearLayout = itemView.findViewById(R.id.linear_layout_for_cards);
            for (int i = 0; i < mCreativeCategoriesSize; i++) {
                LinearLayout gridItemCategory = (LinearLayout)
                        mLayoutInflater.inflate(R.layout.grid_item_category, null);
                if (gridItemCategory != null) {
                    int position = i; //Used in onClickListener
                    mImageViews.add(gridItemCategory.findViewById(R.id.image));
                    mOverlayIconViews.add(gridItemCategory.findViewById(R.id.overlay_icon));
                    mTextViews.add(gridItemCategory.findViewById(R.id.category_title));
                    setLayoutParams(gridItemCategory);
                    linearLayout.addView(gridItemCategory);
                    gridItemCategory.setOnClickListener(view -> {
                        onClickListenerForCreativeCategory(position);
                    });
                }
            }
        }

        private void onClickListenerForCreativeCategory(int position) {
            Activity activity = getActivity();
            if (mCategories.get(position).supportsCustomPhotos()) {
                getCategorySelectorFragmentHost().requestCustomPhotoPicker(
                        new MyPhotosStarter.PermissionChangedListener() {
                            @Override
                            public void onPermissionsGranted() {
                                drawThumbnailAndOverlayIcon(
                                        mOverlayIconViews.get(position),
                                        mCategories.get(position),
                                        mImageViews.get(position));
                            }

                            @Override
                            public void onPermissionsDenied(boolean dontAskAgain) {
                                if (dontAskAgain) {
                                    showPermissionSnackbar();
                                }
                            }
                        });
                return;
            }

            if (mCategories.get(position).isSingleWallpaperCategory()) {
                WallpaperInfo wallpaper = mCategories.get(position)
                        .getSingleWallpaper();

                InjectorProvider.getInjector().getWallpaperPersister(activity)
                        .setWallpaperInfoInPreview(wallpaper);
                wallpaper.showPreview(activity,
                        InjectorProvider.getInjector().getPreviewActivityIntentFactory(),
                        wallpaper instanceof LiveWallpaperInfo
                                ? PREVIEW_LIVE_WALLPAPER_REQUEST_CODE
                                : PREVIEW_WALLPAPER_REQUEST_CODE, true);
                return;
            }

            getCategorySelectorFragmentHost().show(mCategories.get(position));
        }

        private void setLayoutParams(LinearLayout gridItemCategory) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) gridItemCategory.getLayoutParams();
            if (params == null) {
                params =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
            }
            params.setMargins(
                    (int) getResources().getDimension(
                            R.dimen.creative_category_grid_padding_horizontal),
                    (int) getResources().getDimension(
                            R.dimen.creative_category_grid_padding_vertical),
                    (int) getResources().getDimension(
                            R.dimen.creative_category_grid_padding_horizontal),
                    (int) getResources().getDimension(
                            R.dimen.creative_category_grid_padding_vertical));
            CardView cardView = gridItemCategory.findViewById(R.id.category);
            cardView.getLayoutParams().height = SizeCalculator
                    .getFeaturedCategoryTileSize(getActivity()).y / 2;
            cardView.setRadius(getResources().getDimension(R.dimen.grid_item_all_radius));
            params.weight = INDIVIDUAL_TILE_WEIGHT;
            gridItemCategory.setLayoutParams(params);
        }

        private void drawThumbnailAndOverlayIcon(ImageView mOverlayIconView,
                Category mCategory, ImageView mImageView) {
            mOverlayIconView.setImageDrawable(mCategory.getOverlayIcon(
                    getActivity().getApplicationContext()));
            Asset thumbnail = mCategory.getThumbnail(getActivity().getApplicationContext());
            if (thumbnail != null) {
                // Size the overlay icon according to the category.
                int overlayIconDimenDp = mCategory.getOverlayIconSizeDp();
                DisplayMetrics metrics = DisplayMetricsRetriever.getInstance().getDisplayMetrics(
                        getResources(), getActivity().getWindowManager().getDefaultDisplay());
                int overlayIconDimenPx = (int) (overlayIconDimenDp * metrics.density);
                mOverlayIconView.getLayoutParams().width = overlayIconDimenPx;
                mOverlayIconView.getLayoutParams().height = overlayIconDimenPx;
                thumbnail.loadDrawable(getActivity(), mImageView,
                        ResourceUtils.getColorAttr(
                                getActivity(),
                                android.R.attr.colorSecondary
                        ));
            } else {
                mImageView.setBackgroundColor(
                        getResources().getColor(R.color.myphoto_background_color));
                Object nullObj = null;
                Glide.with(getActivity())
                        .asDrawable()
                        .load(nullObj)
                        .into(mImageView);
            }
        }

        private void bindCategory(ArrayList<Category> creativeCategories) {
            for (int i = 0; i < creativeCategories.size(); i++) {
                mCategories.add(creativeCategories.get(i));
                mTextViews.get(i).setText(creativeCategories.get(i).getTitle());
                drawThumbnailAndOverlayIcon(mOverlayIconViews.get(i), mCategories.get(i),
                        mImageViews.get(i));
            }
        }
    }

    /**
     * RecyclerView Adapter subclass for the category tiles in the RecyclerView. This excludes
     * CreativeCategory and has FeaturedCategory
     */
    private class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements MyPhotosStarter.PermissionChangedListener {
        private static final int ITEM_VIEW_TYPE_MY_PHOTOS = 1;
        private static final int ITEM_VIEW_TYPE_FEATURED_CATEGORY = 2;
        private static final int ITEM_VIEW_TYPE_CATEGORY = 3;
        private List<Category> mCategories;

        private CategoryAdapter(List<Category> categories) {
            mCategories = categories;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return ITEM_VIEW_TYPE_MY_PHOTOS;
            }

            if (mIsFeaturedCollectionAvailable && (position == 1 || position == 2)) {
                return ITEM_VIEW_TYPE_FEATURED_CATEGORY;
            }

            return ITEM_VIEW_TYPE_CATEGORY;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view;

            switch (viewType) {
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    view = layoutInflater.inflate(R.layout.grid_item_category,
                            parent, /* attachToRoot= */ false);
                    return new MyPhotosCategoryHolder(view);
                case ITEM_VIEW_TYPE_FEATURED_CATEGORY:
                    view = layoutInflater.inflate(R.layout.grid_item_category,
                            parent, /* attachToRoot= */ false);
                    return new FeaturedCategoryHolder(view);
                case ITEM_VIEW_TYPE_CATEGORY:
                    view = layoutInflater.inflate(R.layout.grid_item_category,
                            parent, /* attachToRoot= */ false);
                    return new CategoryHolder(view);
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            switch (viewType) {
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                case ITEM_VIEW_TYPE_FEATURED_CATEGORY:
                case ITEM_VIEW_TYPE_CATEGORY:
                    // Offset position to get category index to account for the non-category view
                    // holders.
                    Category category = mCategories.get(position - NUM_NON_CATEGORY_VIEW_HOLDERS);
                    ((CategoryHolder) holder).bindCategory(category);
                    break;
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
            }
        }

        @Override
        public int getItemCount() {
            // Add to size of categories to account for the metadata related views.
            int size = mCategories.size() + NUM_NON_CATEGORY_VIEW_HOLDERS;

            return size;
        }

        @Override
        public void onPermissionsGranted() {
            notifyDataSetChanged();
        }

        @Override
        public void onPermissionsDenied(boolean dontAskAgain) {
            if (!dontAskAgain) {
                return;
            }

            String permissionNeededMessage =
                    getString(R.string.permission_needed_explanation_go_to_settings);
            AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                    .setMessage(permissionNeededMessage)
                    .setPositiveButton(android.R.string.ok, null /* onClickListener */)
                    .setNegativeButton(
                            R.string.settings_button_label,
                            (dialogInterface, i) -> {
                                startSettings(SETTINGS_APP_INFO_REQUEST_CODE);
                            })
                    .create();
            dialog.show();
        }
    }

    /**
     * RecyclerView GroupedCategoryAdaptor subclass for the category tiles in the RecyclerView.
     * This removes FeaturedCategory and adds CreativeCategory with a slightly different layout
     */
    private class GroupedCategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements MyPhotosStarter.PermissionChangedListener {
        private static final int ITEM_VIEW_TYPE_MY_PHOTOS = 1;
        private static final int ITEM_VIEW_TYPE_CREATIVE_CATEGORY = 2;
        private static final int ITEM_VIEW_TYPE_CATEGORY = 3;
        private List<Category> mCategories;

        private GroupedCategoryAdapter(List<Category> categories) {
            mCategories = categories;
        }

        @Override
        public int getItemViewType(int position) {
            if (mCategories.stream().anyMatch(Category::supportsUserCreatedWallpapers)) {
                if (position == 0) {
                    return ITEM_VIEW_TYPE_CREATIVE_CATEGORY;
                }
                if (position == 1) {
                    return ITEM_VIEW_TYPE_MY_PHOTOS;
                }
            } else {
                if (position == 0) {
                    return ITEM_VIEW_TYPE_MY_PHOTOS;
                }
            }
            return ITEM_VIEW_TYPE_CATEGORY;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());

            switch (viewType) {
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    View view = layoutInflater.inflate(R.layout.my_photos,
                            parent, /* attachToRoot= */ false);
                    return new MyPhotosCategoryHolder(view);
                case ITEM_VIEW_TYPE_CREATIVE_CATEGORY:
                    view = layoutInflater.inflate(R.layout.creative_wallpaper,
                            parent, /* attachToRoot= */ false);
                    return new GroupCategoryHolder(view, mCreativeCategories.size());
                case ITEM_VIEW_TYPE_CATEGORY:
                    view = layoutInflater.inflate(R.layout.grid_item_category,
                            parent, /* attachToRoot= */ false);
                    return new CategoryHolder(view);
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            switch (viewType) {
                case ITEM_VIEW_TYPE_CREATIVE_CATEGORY:
                    ((GroupCategoryHolder) holder).bindCategory(mCreativeCategories);
                    break;
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    holder.setIsRecyclable(false);
                case ITEM_VIEW_TYPE_CATEGORY:
                    // Offset position to get category index to account for the non-category view
                    // holders.
                    if (mIsCreativeCategoryCollectionAvailable) {
                        int numCreativeCategories = mCreativeCategories.size();
                        int positionRelativeToCreativeCategory = position + numCreativeCategories
                                - 1;
                        Category category = mCategories.get(
                                positionRelativeToCreativeCategory - NUM_NON_CATEGORY_VIEW_HOLDERS);
                        ((CategoryHolder) holder).bindCategory(category);
                    } else {
                        Category category = mCategories.get(position
                                - NUM_NON_CATEGORY_VIEW_HOLDERS);
                        ((CategoryHolder) holder).bindCategory(category);
                    }
                    break;
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
            }
        }

        @Override
        public int getItemCount() {
            // Add to size of categories to account for the metadata related views.
            int size = mCategories.size() + NUM_NON_CATEGORY_VIEW_HOLDERS;
            // This is done to make sure all CreativeCategories are accounted for
            // in one single block, therefore subtracted the size of CreativeCategories
            // from total count
            if (mCreativeCategories.size() >= 2) {
                size = size - (mCreativeCategories.size() - 1);
            }
            return size;
        }

        @Override
        public void onPermissionsGranted() {
            notifyDataSetChanged();
        }

        @Override
        public void onPermissionsDenied(boolean dontAskAgain) {
            if (!dontAskAgain) {
                return;
            }

            String permissionNeededMessage =
                    getString(R.string.permission_needed_explanation_go_to_settings);
            AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                    .setMessage(permissionNeededMessage)
                    .setPositiveButton(android.R.string.ok, null /* onClickListener */)
                    .setNegativeButton(
                            R.string.settings_button_label,
                            (dialogInterface, i) -> {
                                startSettings(SETTINGS_APP_INFO_REQUEST_CODE);
                            })
                    .create();
            dialog.show();
        }
    }

    private class GridPaddingDecoration extends RecyclerView.ItemDecoration {

        private final int mPadding;

        GridPaddingDecoration(int padding) {
            mPadding = padding;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view) - NUM_NON_CATEGORY_VIEW_HOLDERS;
            if (position >= 0) {
                outRect.left = mPadding;
                outRect.right = mPadding;
            }

            RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(view);
            if (viewHolder instanceof MyPhotosCategoryHolder
                    || viewHolder instanceof GroupCategoryHolder
                    || viewHolder instanceof FeaturedCategoryHolder) {
                outRect.bottom = getResources().getDimensionPixelSize(
                        R.dimen.grid_item_featured_category_padding_bottom);
            } else {
                outRect.bottom = getResources().getDimensionPixelSize(
                        R.dimen.grid_item_category_padding_bottom);
            }
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SETTINGS_APP_INFO_REQUEST_CODE) {
            notifyDataSetChanged();
        }
    }

    /**
     * SpanSizeLookup subclass which works with CategoryAdaptor and provides that the item in the
     * first position spans the number of columns in the RecyclerView and all other items only
     * take up a single span.
     */
    private class CategorySpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        private static final int DEFAULT_CATEGORY_SPAN_SIZE = 2;

        CategoryAdapter mAdapter;

        private CategorySpanSizeLookup(CategoryAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public int getSpanSize(int position) {
            if (position < NUM_NON_CATEGORY_VIEW_HOLDERS || mAdapter.getItemViewType(
                    position) == CategoryAdapter.ITEM_VIEW_TYPE_MY_PHOTOS) {
                return getNumColumns() * DEFAULT_CATEGORY_SPAN_SIZE;
            }

            if (mAdapter.getItemViewType(position)
                    == CategoryAdapter.ITEM_VIEW_TYPE_FEATURED_CATEGORY) {
                return getNumColumns() * DEFAULT_CATEGORY_SPAN_SIZE / 2;
            }
            return DEFAULT_CATEGORY_SPAN_SIZE;
        }
    }

    /**
     * SpanSizeLookup subclass which works with GroupCategoryAdaptor and provides that
     * item of type photos and items of type CreativeCategory spans the number of columns in the
     * RecyclerView and all other items only take up a single span.
     */
    private class GroupedCategorySpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        private static final int DEFAULT_CATEGORY_SPAN_SIZE = 2;

        GroupedCategoryAdapter mAdapter;

        private GroupedCategorySpanSizeLookup(GroupedCategoryAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public int getSpanSize(int position) {
            if (position < NUM_NON_CATEGORY_VIEW_HOLDERS || mAdapter.getItemViewType(
                    position) == GroupedCategoryAdapter.ITEM_VIEW_TYPE_MY_PHOTOS) {
                return getNumColumns() * DEFAULT_CATEGORY_SPAN_SIZE;
            }

            if (mAdapter.getItemViewType(position)
                    == GroupedCategoryAdapter.ITEM_VIEW_TYPE_CREATIVE_CATEGORY) {
                return getNumColumns() * DEFAULT_CATEGORY_SPAN_SIZE;
            }
            return DEFAULT_CATEGORY_SPAN_SIZE;
        }
    }

    @Override
    protected int getToolbarColorId() {
        return android.R.color.transparent;
    }

    @Override
    protected int getToolbarTextColor() {
        return ContextCompat.getColor(requireContext(), R.color.system_on_surface);
    }
}
