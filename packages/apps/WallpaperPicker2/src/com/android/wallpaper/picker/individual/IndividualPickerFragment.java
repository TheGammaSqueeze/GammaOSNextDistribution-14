/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.picker.individual;

import android.annotation.MenuRes;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.graphics.Point;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.wallpaper.R;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.CategoryReceiver;
import com.android.wallpaper.model.WallpaperCategory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.model.WallpaperReceiver;
import com.android.wallpaper.model.WallpaperRotationInitializer;
import com.android.wallpaper.model.WallpaperRotationInitializer.Listener;
import com.android.wallpaper.model.WallpaperRotationInitializer.NetworkPreference;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PackageStatusNotifier;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.picker.FragmentTransactionChecker;
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosStarterProvider;
import com.android.wallpaper.picker.RotationStarter;
import com.android.wallpaper.picker.StartRotationDialogFragment;
import com.android.wallpaper.picker.StartRotationErrorDialogFragment;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.LaunchUtils;
import com.android.wallpaper.util.SizeCalculator;
import com.android.wallpaper.widget.GridPaddingDecoration;
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate;
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate.BottomSheetHost;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Displays the Main UI for picking an individual wallpaper image.
 */
public class IndividualPickerFragment extends AppbarFragment
        implements RotationStarter, StartRotationErrorDialogFragment.Listener,
        StartRotationDialogFragment.Listener {

    /**
     * Position of a special tile that doesn't belong to an individual wallpaper of the category,
     * such as "my photos" or "daily rotation".
     */
    static final int SPECIAL_FIXED_TILE_ADAPTER_POSITION = 0;
    static final String ARG_CATEGORY_COLLECTION_ID = "category_collection_id";

    protected static final int MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT = 8;

    private static final String TAG = "IndividualPickerFrgmnt";
    private static final int UNUSED_REQUEST_CODE = 1;
    private static final String TAG_START_ROTATION_DIALOG = "start_rotation_dialog";
    private static final String TAG_START_ROTATION_ERROR_DIALOG = "start_rotation_error_dialog";
    private static final String PROGRESS_DIALOG_NO_TITLE = null;
    private static final boolean PROGRESS_DIALOG_INDETERMINATE = true;
    private static final String KEY_NIGHT_MODE = "IndividualPickerFragment.NIGHT_MODE";

    /**
     * Interface to be implemented by a Fragment(or an Activity) hosting
     * a {@link IndividualPickerFragment}.
     */
    public interface IndividualPickerFragmentHost {
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
         * Configures the menu in the toolbar.
         *
         * @param menuResId the resource id of the menu
         */
        void setToolbarMenu(@MenuRes int menuResId);

        /**
         * Removes the menu in the toolbar.
         */
        void removeToolbarMenu();

        /**
         * Moves to the previous fragment.
         */
        void moveToPreviousFragment();
    }

    RecyclerView mImageGrid;
    IndividualAdapter mAdapter;
    WallpaperCategory mCategory;
    WallpaperRotationInitializer mWallpaperRotationInitializer;
    List<WallpaperInfo> mWallpapers;
    Point mTileSizePx;
    PackageStatusNotifier mPackageStatusNotifier;

    boolean mIsWallpapersReceived;
    PackageStatusNotifier.Listener mAppStatusListener;

    private ProgressDialog mProgressDialog;
    private ContentLoadingProgressBar mLoading;
    private CategoryProvider mCategoryProvider;

    /**
     * Staged error dialog fragments that were unable to be shown when the activity didn't allow
     * committing fragment transactions.
     */
    private StartRotationErrorDialogFragment mStagedStartRotationErrorDialogFragment;

    private WallpaperManager mWallpaperManager;
    private Set<String> mAppliedWallpaperIds;

    public static IndividualPickerFragment newInstance(String collectionId) {
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_COLLECTION_ID, collectionId);

        IndividualPickerFragment fragment = new IndividualPickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Injector injector = InjectorProvider.getInjector();
        Context appContext = getContext().getApplicationContext();

        mWallpaperManager = WallpaperManager.getInstance(appContext);

        mPackageStatusNotifier = injector.getPackageStatusNotifier(appContext);

        mWallpapers = new ArrayList<>();

        // Clear Glide's cache if night-mode changed to ensure thumbnails are reloaded
        if (savedInstanceState != null && (savedInstanceState.getInt(KEY_NIGHT_MODE)
                != (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK))) {
            Glide.get(getContext()).clearMemory();
        }

        mCategoryProvider = injector.getCategoryProvider(appContext);
        mCategoryProvider.fetchCategories(new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                // Do nothing.
            }

            @Override
            public void doneFetchingCategories() {
                Category category = mCategoryProvider.getCategory(
                        getArguments().getString(ARG_CATEGORY_COLLECTION_ID));
                if (category != null && !(category instanceof WallpaperCategory)) {
                    return;
                }
                mCategory = (WallpaperCategory) category;
                if (mCategory == null) {
                    // The absence of this category in the CategoryProvider indicates a broken
                    // state, see b/38030129. Hence, finish the activity and return.
                    getIndividualPickerFragmentHost().moveToPreviousFragment();
                    Toast.makeText(getContext(), R.string.collection_not_exist_msg,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                onCategoryLoaded();
            }
        }, false);
    }


    protected void onCategoryLoaded() {
        if (getIndividualPickerFragmentHost() == null) {
            return;
        }
        if (getIndividualPickerFragmentHost().isHostToolbarShown()) {
            getIndividualPickerFragmentHost().setToolbarTitle(mCategory.getTitle());
        } else {
            setTitle(mCategory.getTitle());
        }
        mWallpaperRotationInitializer = mCategory.getWallpaperRotationInitializer();
        if (mToolbar != null && isRotationEnabled()) {
            setUpToolbarMenu(R.menu.individual_picker_menu);
        }
        fetchWallpapers(false);

        if (mCategory.supportsThirdParty()) {
            mAppStatusListener = (packageName, status) -> {
                if (status != PackageStatusNotifier.PackageStatus.REMOVED ||
                        mCategory.containsThirdParty(packageName)) {
                    fetchWallpapers(true);
                }
            };
            mPackageStatusNotifier.addListener(mAppStatusListener,
                    WallpaperService.SERVICE_INTERFACE);
        }
    }

    void fetchWallpapers(boolean forceReload) {
        mWallpapers.clear();
        mIsWallpapersReceived = false;
        updateLoading();
        mCategory.fetchWallpapers(getActivity().getApplicationContext(), new WallpaperReceiver() {
            @Override
            public void onWallpapersReceived(List<WallpaperInfo> wallpapers) {
                mIsWallpapersReceived = true;
                updateLoading();
                for (WallpaperInfo wallpaper : wallpapers) {
                    mWallpapers.add(wallpaper);
                }
                maybeSetUpImageGrid();

                // Wallpapers may load after the adapter is initialized, in which case we have
                // to explicitly notify that the data set has changed.
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }

                if (wallpapers.isEmpty()) {
                    // If there are no more wallpapers and we're on phone, just finish the
                    // Activity.
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.finish();
                    }
                }
            }
        }, forceReload);
    }

    void updateLoading() {
        if (mLoading == null) {
            return;
        }

        if (mIsWallpapersReceived) {
            mLoading.hide();
        } else {
            mLoading.show();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_NIGHT_MODE,
                getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_individual_picker, container, false);
        if (getIndividualPickerFragmentHost().isHostToolbarShown()) {
            view.findViewById(R.id.header_bar).setVisibility(View.GONE);
            setUpArrowEnabled(/* upArrow= */ true);
            if (isRotationEnabled()) {
                getIndividualPickerFragmentHost().setToolbarMenu(R.menu.individual_picker_menu);
            }
        } else {
            setUpToolbar(view);
            if (isRotationEnabled()) {
                setUpToolbarMenu(R.menu.individual_picker_menu);
            }
            if (mCategory != null) {
                setTitle(mCategory.getTitle());
            }
        }

        mAppliedWallpaperIds = getAppliedWallpaperIds();

        mImageGrid = (RecyclerView) view.findViewById(R.id.wallpaper_grid);
        mLoading = view.findViewById(R.id.loading_indicator);
        updateLoading();
        maybeSetUpImageGrid();
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

    private IndividualPickerFragmentHost getIndividualPickerFragmentHost() {
        Fragment parentFragment = getParentFragment();
        if (parentFragment != null) {
            return (IndividualPickerFragmentHost) parentFragment;
        } else {
            return (IndividualPickerFragmentHost) getActivity();
        }
    }

    protected void maybeSetUpImageGrid() {
        // Skip if mImageGrid been initialized yet
        if (mImageGrid == null) {
            return;
        }
        // Skip if category hasn't loaded yet
        if (mCategory == null) {
            return;
        }
        if (getContext() == null) {
            return;
        }

        // Wallpaper count could change, so we may need to change the layout(2 or 3 columns layout)
        GridLayoutManager gridLayoutManager = (GridLayoutManager) mImageGrid.getLayoutManager();
        boolean needUpdateLayout =
                gridLayoutManager != null && gridLayoutManager.getSpanCount() != getNumColumns();

        // Skip if the adapter was already created and don't need to change the layout
        if (mAdapter != null && !needUpdateLayout) {
            return;
        }

        // Clear the old decoration
        int decorationCount = mImageGrid.getItemDecorationCount();
        for (int i = 0; i < decorationCount; i++) {
            mImageGrid.removeItemDecorationAt(i);
        }

        mImageGrid.addItemDecoration(new GridPaddingDecoration(getGridItemPaddingHorizontal(),
                getGridItemPaddingBottom()));
        int edgePadding = getEdgePadding();
        mImageGrid.setPadding(edgePadding, mImageGrid.getPaddingTop(), edgePadding,
                mImageGrid.getPaddingBottom());
        mTileSizePx = isFewerColumnLayout()
                ? SizeCalculator.getFeaturedIndividualTileSize(getActivity())
                : SizeCalculator.getIndividualTileSize(getActivity());
        setUpImageGrid();
        mImageGrid.setAccessibilityDelegateCompat(
                new WallpaperPickerRecyclerViewAccessibilityDelegate(
                        mImageGrid, (BottomSheetHost) getParentFragment(), getNumColumns()));
    }

    boolean isFewerColumnLayout() {
        return mWallpapers != null && mWallpapers.size() <= MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT;
    }

    private int getGridItemPaddingHorizontal() {
        return isFewerColumnLayout()
                ? getResources().getDimensionPixelSize(
                R.dimen.grid_item_featured_individual_padding_horizontal)
                : getResources().getDimensionPixelSize(
                        R.dimen.grid_item_individual_padding_horizontal);
    }

    private int getGridItemPaddingBottom() {
        return isFewerColumnLayout()
                ? getResources().getDimensionPixelSize(
                R.dimen.grid_item_featured_individual_padding_bottom)
                : getResources().getDimensionPixelSize(R.dimen.grid_item_individual_padding_bottom);
    }

    private int getEdgePadding() {
        return isFewerColumnLayout()
                ? getResources().getDimensionPixelSize(R.dimen.featured_wallpaper_grid_edge_space)
                : getResources().getDimensionPixelSize(R.dimen.wallpaper_grid_edge_space);
    }

    /**
     * Create the adapter and assign it to mImageGrid.
     * Both mImageGrid and mCategory are guaranteed to not be null when this method is called.
     */
    void setUpImageGrid() {
        mAdapter = new IndividualAdapter(mWallpapers);
        mImageGrid.setAdapter(mAdapter);
        mImageGrid.setLayoutManager(new GridLayoutManager(getActivity(), getNumColumns()));
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences = InjectorProvider.getInjector()
                .getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());

        // Reset Glide memory settings to a "normal" level of usage since it may have been lowered in
        // PreviewFragment.
        Glide.get(getActivity()).setMemoryCategory(MemoryCategory.NORMAL);

        // Show the staged 'start rotation' error dialog fragment if there is one that was unable to be
        // shown earlier when this fragment's hosting activity didn't allow committing fragment
        // transactions.
        if (mStagedStartRotationErrorDialogFragment != null) {
            mStagedStartRotationErrorDialogFragment.show(
                    getFragmentManager(), TAG_START_ROTATION_ERROR_DIALOG);
            mStagedStartRotationErrorDialogFragment = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getIndividualPickerFragmentHost().removeToolbarMenu();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (mAppStatusListener != null) {
            mPackageStatusNotifier.removeListener(mAppStatusListener);
        }
    }

    @Override
    public void onStartRotationDialogDismiss(@NonNull DialogInterface dialog) {
        // TODO(b/159310028): Refactor fragment layer to make it able to restore from config change.
        // This is to handle config change with StartRotationDialog popup,  the StartRotationDialog
        // still holds a reference to the destroyed Fragment and is calling
        // onStartRotationDialogDismissed on that destroyed Fragment.
    }

    @Override
    public void retryStartRotation(@NetworkPreference int networkPreference) {
        startRotation(networkPreference);
    }

    @Override
    public void startRotation(@NetworkPreference final int networkPreference) {
        if (!isRotationEnabled()) {
            Log.e(TAG, "Rotation is not enabled for this category " + mCategory.getTitle());
            return;
        }

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which therefore
        // causes Espresso to hang once the dialog is shown.
        int themeResId;
        if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
            themeResId = R.style.ProgressDialogThemePreL;
        } else {
            themeResId = R.style.LightDialogTheme;
        }
        mProgressDialog = new ProgressDialog(getActivity(), themeResId);

        mProgressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE);
        mProgressDialog.setMessage(
                getResources().getString(R.string.start_rotation_progress_message));
        mProgressDialog.setIndeterminate(PROGRESS_DIALOG_INDETERMINATE);
        mProgressDialog.show();

        final Context appContext = getActivity().getApplicationContext();

        mWallpaperRotationInitializer.setFirstWallpaperInRotation(
                appContext,
                networkPreference,
                new Listener() {
                    @Override
                    public void onFirstWallpaperInRotationSet() {
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }

                        // The fragment may be detached from its containing activity if the user exits the
                        // app before the first wallpaper image in rotation finishes downloading.
                        Activity activity = getActivity();

                        if (mWallpaperRotationInitializer.startRotation(appContext)) {
                            if (activity != null) {
                                try {
                                    Toast.makeText(activity,
                                            R.string.wallpaper_set_successfully_message,
                                            Toast.LENGTH_SHORT).show();
                                } catch (NotFoundException e) {
                                    Log.e(TAG, "Could not show toast " + e);
                                }

                                activity.setResult(Activity.RESULT_OK);
                                activity.finish();
                                if (!ActivityUtils.isSUWMode(appContext)) {
                                    // Go back to launcher home.
                                    LaunchUtils.launchHome(appContext);
                                }
                            }
                        } else { // Failed to start rotation.
                            showStartRotationErrorDialog(networkPreference);
                        }
                    }

                    @Override
                    public void onError() {
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }

                        showStartRotationErrorDialog(networkPreference);
                    }
                });
    }

    private void showStartRotationErrorDialog(@NetworkPreference int networkPreference) {
        FragmentTransactionChecker activity = (FragmentTransactionChecker) getActivity();
        if (activity != null) {
            StartRotationErrorDialogFragment startRotationErrorDialogFragment =
                    StartRotationErrorDialogFragment.newInstance(networkPreference);
            startRotationErrorDialogFragment.setTargetFragment(
                    IndividualPickerFragment.this, UNUSED_REQUEST_CODE);

            if (activity.isSafeToCommitFragmentTransaction()) {
                startRotationErrorDialogFragment.show(
                        getFragmentManager(), TAG_START_ROTATION_ERROR_DIALOG);
            } else {
                mStagedStartRotationErrorDialogFragment = startRotationErrorDialogFragment;
            }
        }
    }

    int getNumColumns() {
        Activity activity = getActivity();
        if (activity == null) {
            return 1;
        }
        return isFewerColumnLayout()
                ? SizeCalculator.getNumFeaturedIndividualColumns(activity)
                : SizeCalculator.getNumIndividualColumns(activity);
    }

    /**
     * Returns whether rotation is enabled for this category.
     */
    boolean isRotationEnabled() {
        return mWallpaperRotationInitializer != null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.daily_rotation) {
            showRotationDialog();
            return true;
        }
        return super.onMenuItemClick(item);
    }

    /**
     * Popups a daily rotation dialog for the uses to confirm.
     */
    public void showRotationDialog() {
        DialogFragment startRotationDialogFragment = new StartRotationDialogFragment();
        startRotationDialogFragment.setTargetFragment(
                IndividualPickerFragment.this, UNUSED_REQUEST_CODE);
        startRotationDialogFragment.show(getFragmentManager(), TAG_START_ROTATION_DIALOG);
    }

    private Set<String> getAppliedWallpaperIds() {
        WallpaperPreferences prefs =
                InjectorProvider.getInjector().getPreferences(getContext());
        android.app.WallpaperInfo wallpaperInfo = mWallpaperManager.getWallpaperInfo();
        Set<String> appliedWallpaperIds = new ArraySet<>();

        String homeWallpaperId = wallpaperInfo != null ? wallpaperInfo.getServiceName()
                : prefs.getHomeWallpaperRemoteId();
        if (!TextUtils.isEmpty(homeWallpaperId)) {
            appliedWallpaperIds.add(homeWallpaperId);
        }

        boolean isLockWallpaperApplied =
                mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0;
        String lockWallpaperId = prefs.getLockWallpaperRemoteId();
        if (isLockWallpaperApplied && !TextUtils.isEmpty(lockWallpaperId)) {
            appliedWallpaperIds.add(lockWallpaperId);
        }

        return appliedWallpaperIds;
    }

    /**
     * RecyclerView Adapter subclass for the wallpaper tiles in the RecyclerView.
     */
    class IndividualAdapter extends RecyclerView.Adapter<ViewHolder> {
        static final int ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER = 2;
        static final int ITEM_VIEW_TYPE_MY_PHOTOS = 3;

        private final List<WallpaperInfo> mWallpapers;

        IndividualAdapter(List<WallpaperInfo> wallpapers) {
            mWallpapers = wallpapers;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER:
                    return createIndividualHolder(parent);
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    return createMyPhotosHolder(parent);
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in IndividualAdapter");
                    return null;
            }
        }

        @Override
        public int getItemViewType(int position) {
            // A category cannot have both a "start rotation" tile and a "my photos" tile.
            if (mCategory.supportsCustomPhotos()
                    && !isRotationEnabled()
                    && position == SPECIAL_FIXED_TILE_ADAPTER_POSITION) {
                return ITEM_VIEW_TYPE_MY_PHOTOS;
            }

            return ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            int viewType = getItemViewType(position);

            switch (viewType) {
                case ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER:
                    onBindIndividualHolder(holder, position);
                    break;
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    ((MyPhotosViewHolder) holder).bind();
                    break;
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in IndividualAdapter");
            }
        }

        @Override
        public int getItemCount() {
            return mCategory.supportsCustomPhotos() ? mWallpapers.size() + 1 : mWallpapers.size();
        }

        private ViewHolder createIndividualHolder(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.grid_item_image, parent, false);

            return new PreviewIndividualHolder(getActivity(), mTileSizePx.y, view);
        }

        private ViewHolder createMyPhotosHolder(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.grid_item_my_photos, parent, false);

            return new MyPhotosViewHolder(getActivity(),
                    ((MyPhotosStarterProvider) getActivity()).getMyPhotosStarter(),
                    mTileSizePx.y, view);
        }

        void onBindIndividualHolder(ViewHolder holder, int position) {
            int wallpaperIndex = mCategory.supportsCustomPhotos() ? position - 1 : position;
            WallpaperInfo wallpaper = mWallpapers.get(wallpaperIndex);
            wallpaper.computeColorInfo(holder.itemView.getContext());
            ((IndividualHolder) holder).bindWallpaper(wallpaper);
            boolean isWallpaperApplied = isWallpaperApplied(wallpaper);

            CardView container = holder.itemView.findViewById(R.id.wallpaper_container);
            int radiusId = isFewerColumnLayout() ? R.dimen.grid_item_all_radius
                    : R.dimen.grid_item_all_radius_small;
            container.setRadius(getResources().getDimension(radiusId));
            showBadge(holder, R.drawable.wallpaper_check_circle_24dp, isWallpaperApplied);
        }

        protected boolean isWallpaperApplied(WallpaperInfo wallpaper) {
            return mAppliedWallpaperIds.contains(wallpaper.getWallpaperId());
        }

        protected void showBadge(ViewHolder holder, @DrawableRes int icon, boolean show) {
            ImageView badge = holder.itemView.findViewById(R.id.indicator_icon);
            if (show) {
                final float margin = isFewerColumnLayout() ? getResources().getDimension(
                        R.dimen.grid_item_badge_margin) : getResources().getDimension(
                        R.dimen.grid_item_badge_margin_small);
                final RelativeLayout.LayoutParams layoutParams =
                        (RelativeLayout.LayoutParams) badge.getLayoutParams();
                layoutParams.setMargins(/* left= */ (int) margin, /* top= */ (int) margin,
                        /* right= */ (int) margin, /* bottom= */ (int) margin);
                badge.setLayoutParams(layoutParams);
                badge.setBackgroundResource(icon);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
        }
    }
}
