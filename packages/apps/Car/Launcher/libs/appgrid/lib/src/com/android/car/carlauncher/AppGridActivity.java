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

package com.android.car.carlauncher;

import static android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS;
import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;
import static android.content.Intent.URI_INTENT_SCHEME;

import static com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import static com.android.car.carlauncher.AppGridConstants.PageOrientation;
import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_LAUNCHABLES;
import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_MEDIA_SERVICES;
import static com.android.car.hidden.apis.HiddenApiAccess.getDragSurface;

import android.animation.ValueAnimator;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.media.CarMediaManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.DragEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.AppLauncherUtils.LauncherAppsInfo;
import com.android.car.carlauncher.pagination.PageMeasurementHelper;
import com.android.car.carlauncher.pagination.PaginationController;
import com.android.car.carlauncher.recyclerview.AppGridAdapter;
import com.android.car.carlauncher.recyclerview.AppGridItemAnimator;
import com.android.car.carlauncher.recyclerview.AppGridLayoutManager;
import com.android.car.carlauncher.recyclerview.AppItemViewHolder;
import com.android.car.ui.FocusArea;
import com.android.car.ui.baselayout.Insets;
import com.android.car.ui.baselayout.InsetsChangedListener;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Launcher activity that shows a grid of apps.
 */
public class AppGridActivity extends AppCompatActivity implements InsetsChangedListener,
        AppGridPageSnapper.PageSnapListener, AppItemViewHolder.AppItemDragListener,
        AppLauncherUtils.ShortcutsListener, PaginationController.DimensionUpdateListener {
    private static final String TAG = "AppGridActivity";
    private static final boolean DEBUG_BUILD = false;
    private static final String MODE_INTENT_EXTRA = "com.android.car.carlauncher.mode";
    @VisibleForTesting
    static final String TOS_BANNER_DISMISS_TIME_KEY = "TOS_BANNER_DISMISS_TIME";
    private static CarUiShortcutsPopup sCarUiShortcutsPopup;

    private boolean mShowAllApps = true;
    private boolean mShowToolbar = true;
    private final Set<String> mHiddenApps = new HashSet<>();
    private PackageManager mPackageManager;
    private UsageStatsManager mUsageStatsManager;
    private AppInstallUninstallReceiver mInstallUninstallReceiver;
    private Car mCar;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    private CarPackageManager mCarPackageManager;
    private CarMediaManager mCarMediaManager;
    private Mode mMode;
    private LauncherAppsInfo mAppsInfo;
    private LauncherViewModel mLauncherModel;
    private AppGridAdapter mAdapter;
    private AppGridRecyclerView mRecyclerView;
    private PageIndicator mPageIndicator;
    private AppGridLayoutManager mLayoutManager;
    private boolean mIsCurrentlyDragging;
    private long mOffPageHoverBeforeScrollMs;
    private Banner mBanner;

    private AppGridDragController mAppGridDragController;
    private PaginationController mPaginationController;

    private int mNumOfRows;
    private int mNumOfCols;
    private int mAppGridMarginHorizontal;
    private int mAppGridMarginVertical;
    private int mAppGridWidth;
    private int mAppGridHeight;
    @PageOrientation
    private int mPageOrientation;

    private int mCurrentScrollOffset;
    private int mCurrentScrollState;
    private int mNextScrollDestination;
    private AppGridPageSnapper.AppGridPageSnapCallback mSnapCallback;
    private AppItemViewHolder.AppItemDragCallback mDragCallback;

    private Messenger mMirroringService;
    private Messenger mMessenger;
    private String mMirroringPackageName;
    private Intent mMirroringIntentRedirect;
    private Clock mClock;
    @VisibleForTesting
    ContentObserver mTosContentObserver;
    @VisibleForTesting
    ContentObserver mTosDisabledAppsContentObserver;
    private BackgroundAnimationHelper mBackgroundAnimationHelper;

    /**
     * enum to define the state of display area possible.
     * CONTROL_BAR state is when only control bar is visible.
     * FULL state is when display area hosting default apps  cover the screen fully.
     * DEFAULT state where maps are shown above DA for default apps.
     */
    public enum CAR_LAUNCHER_STATE {
        CONTROL_BAR, DEFAULT, FULL
    }

    public enum Mode {
        ALL_APPS(R.string.app_launcher_title_all_apps,
                APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                true),
        MEDIA_ONLY(R.string.app_launcher_title_media_only,
                APP_TYPE_MEDIA_SERVICES,
                true),
        MEDIA_POPUP(R.string.app_launcher_title_media_only,
                APP_TYPE_MEDIA_SERVICES,
                false),
        ;
        public final @StringRes int mTitleStringId;
        public final @AppLauncherUtils.AppTypes int mAppTypes;
        public final boolean mOpenMediaCenter;

        Mode(@StringRes int titleStringId, @AppLauncherUtils.AppTypes int appTypes,
                boolean openMediaCenter) {
            mTitleStringId = titleStringId;
            mAppTypes = appTypes;
            mOpenMediaCenter = openMediaCenter;
        }
    }

    private ServiceConnection mCarConnectionListener = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mCarUxRestrictionsManager = (CarUxRestrictionsManager) mCar.getCarManager(
                        Car.CAR_UX_RESTRICTION_SERVICE);
                CarUxRestrictions carUxRestrictions = mCarUxRestrictionsManager
                        .getCurrentCarUxRestrictions();
                boolean isDistractionOptimizationRequired;
                if (carUxRestrictions == null) {
                    Log.v(TAG, "No CarUxRestrictions on display");
                    isDistractionOptimizationRequired = false;
                } else {
                    isDistractionOptimizationRequired = carUxRestrictions
                            .isRequiresDistractionOptimization();
                }
                mAdapter.setIsDistractionOptimizationRequired(isDistractionOptimizationRequired);
                mAdapter.setMode(mMode);
                // set listener to update the app grid components and apply interaction restrictions
                // when driving state changes
                mCarUxRestrictionsManager.registerListener(restrictionInfo -> {
                    handleDistractionOptimization(/* requiresDistractionOptimization */
                            restrictionInfo.isRequiresDistractionOptimization());
                });
                mCarPackageManager = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);
                mCarMediaManager = (CarMediaManager) mCar.getCarManager(Car.CAR_MEDIA_SERVICE);
                reinitializeLauncherModel();
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in CarConnectionListener", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mCarUxRestrictionsManager = null;
            mCarPackageManager = null;
        }
    };

    /**
     * Updates the state of the app grid components depending on the driving state.
     */
    private void handleDistractionOptimization(boolean requiresDistractionOptimization) {
        mAdapter.setIsDistractionOptimizationRequired(requiresDistractionOptimization);
        if (requiresDistractionOptimization) {
            // if the user start driving while drag is in action, we cancel existing drag operations
            if (mIsCurrentlyDragging) {
                mIsCurrentlyDragging = false;
                mLayoutManager.setShouldLayoutChildren(true);
                mRecyclerView.cancelDragAndDrop();
            }
            dismissShortcutPopup();
        }
    }

    private void reinitializeLauncherModel() {
        ExecutorService fetchOrderExecutorService = Executors.newSingleThreadExecutor();
        fetchOrderExecutorService.execute(() -> {
            // first, we fetch apps order from data store file into memory
            mLauncherModel.loadAppsOrderFromFile();
            fetchOrderExecutorService.shutdown();
        });
        ExecutorService alphabetizeExecutorService = Executors.newSingleThreadExecutor();
        alphabetizeExecutorService.execute(() -> {
            Set<String> appsToHide = mShowAllApps ? Collections.emptySet() : mHiddenApps;
            mAppsInfo = AppLauncherUtils.getLauncherApps(getApplicationContext(),
                    appsToHide,
                    mMode.mAppTypes,
                    mMode.mOpenMediaCenter,
                    getSystemService(LauncherApps.class),
                    mCarPackageManager,
                    mPackageManager,
                    mCarMediaManager,
                    AppGridActivity.this,
                    mMirroringPackageName,
                    mMirroringIntentRedirect);
            // then, we ingest all apps info
            mLauncherModel.processAppsInfoFromPlatform(mAppsInfo);
            alphabetizeExecutorService.shutdown();
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // TODO (b/267548246) deprecate toolbar and find another way to hide debug apps
        mShowToolbar = false;
        if (mShowToolbar) {
            setTheme(R.style.Theme_Launcher_AppGridActivity);
        } else {
            setTheme(R.style.Theme_Launcher_AppGridActivity_NoToolbar);
        }
        super.onCreate(savedInstanceState);

        mPackageManager = getPackageManager();
        mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        mLauncherModel = new ViewModelProvider(AppGridActivity.this,
                new LauncherViewModelFactory(getFilesDir())).get(
                LauncherViewModel.class);
        mLauncherModel.getCurrentLauncher().observe(
                AppGridActivity.this, new Observer<List<LauncherItem>>() {
                    @Override
                    public void onChanged(List<LauncherItem> launcherItems) {
                        mAdapter.setLauncherItems(launcherItems);
                        mNextScrollDestination = mSnapCallback.getSnapPosition();
                        updateScrollState();
                        if (mMode == Mode.ALL_APPS) {
                            mLauncherModel.handleAppListChange();
                        }
                    }
                }
        );
        mCar = Car.createCar(this, mCarConnectionListener);
        mHiddenApps.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        mClock = Clock.systemUTC();
        setContentView(R.layout.app_grid_activity);
        updateMode();

        ServiceConnection mirroringConnectionListener = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Mirroring service connected");
                mMirroringService = new Messenger(service);
                mMessenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));
                Message msg = Message.obtain(null, getResources()
                        .getInteger(R.integer.config_msg_register_mirroring_pkg_code));
                msg.replyTo = mMessenger;
                try {
                    mMirroringService.send(msg);
                } catch (RemoteException e) {
                    Log.d(TAG, "Exception sending message to mirroring service: " + e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "Mirroring service disconnected");
                mMirroringPackageName = null;
                mMirroringIntentRedirect = null;
            }
        };

        // Bind to service that will inform about apps that are being mirrored
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    getString(R.string.config_msg_mirroring_service_pkg_name),
                    getString(R.string.config_msg_mirroring_service_class_name)));
            if (mPackageManager.resolveService(intent, /* flags = */ 0) != null) {
                bindService(intent, mirroringConnectionListener,
                        BIND_AUTO_CREATE | BIND_IMPORTANT);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error binding to mirroring service: " + e);
        }

        if (mShowToolbar) {
            ToolbarController toolbar = CarUi.requireToolbar(this);

            toolbar.setNavButtonMode(NavButtonMode.CLOSE);

            if (DEBUG_BUILD) {
                toolbar.setMenuItems(Collections.singletonList(MenuItem.builder(this)
                        .setDisplayBehavior(MenuItem.DisplayBehavior.NEVER)
                        .setTitle(R.string.hide_debug_apps)
                        .setOnClickListener(i -> {
                            mShowAllApps = !mShowAllApps;
                            i.setTitle(mShowAllApps
                                    ? R.string.hide_debug_apps
                                    : R.string.show_debug_apps);
                        })
                        .build()));
            }
        }

        mSnapCallback = new AppGridPageSnapper.AppGridPageSnapCallback(this);
        mDragCallback = new AppItemViewHolder.AppItemDragCallback(this);

        mNumOfCols = getResources().getInteger(R.integer.car_app_selector_column_number);
        mNumOfRows = getResources().getInteger(R.integer.car_app_selector_row_number);
        mAppGridDragController = new AppGridDragController();
        mOffPageHoverBeforeScrollMs = getResources().getInteger(
                R.integer.ms_off_page_hover_before_scroll);

        mPageOrientation = getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;

        mRecyclerView = requireViewById(R.id.apps_grid);
        mRecyclerView.setFocusable(false);
        mLayoutManager = new AppGridLayoutManager(this, mNumOfCols, mNumOfRows, mPageOrientation);
        mRecyclerView.setLayoutManager(mLayoutManager);

        AppGridPageSnapper pageSnapper = new AppGridPageSnapper(
                this,
                mNumOfCols,
                mNumOfRows,
                mSnapCallback);
        pageSnapper.attachToRecyclerView(mRecyclerView);

        mRecyclerView.setItemAnimator(new AppGridItemAnimator());

        // hide the default scrollbar and replace it with a visual page indicator
        mRecyclerView.setVerticalScrollBarEnabled(false);
        mRecyclerView.setHorizontalScrollBarEnabled(false);
        mRecyclerView.addOnScrollListener(new AppGridOnScrollListener());

        // TODO: (b/271637411) move this to be contained in a scroll controller
        mPageIndicator = requireViewById(R.id.page_indicator);
        FrameLayout pageIndicatorContainer = requireViewById(R.id.page_indicator_container);
        mPageIndicator.setContainer(pageIndicatorContainer);

        // recycler view is set to LTR to prevent layout manager from reassigning layout direction.
        // instead, PageIndexinghelper will determine the grid index based on the system layout
        // direction and provide LTR mapping at adapter level.
        mRecyclerView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pageIndicatorContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // we create but do not attach the adapter to recyclerview until view tree layout is
        // complete and the total size of the app grid is measureable.
        mAdapter = new AppGridAdapter(this, mNumOfCols, mNumOfRows,
                /* dataModel */ mLauncherModel, /* dragCallback */ mDragCallback,
                /* snapCallback */ mSnapCallback);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                // scroll state will need to be updated after item has been dropped
                mNextScrollDestination = mSnapCallback.getSnapPosition();
                updateScrollState();
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        // set drag listener and global layout listener, which will dynamically adjust app grid
        // height and width depending on device screen size.
        if (getResources().getBoolean(R.bool.config_allow_reordering)) {
            mRecyclerView.setOnDragListener(new AppGridDragListener());
        }

        // since some measurements for window size may not be available yet during onCreate or may
        // later change, we add a listener that redraws the app grid when window size changes.
        LinearLayout windowBackground = requireViewById(R.id.apps_grid_background);
        windowBackground.setOrientation(
                isHorizontal() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        PaginationController.DimensionUpdateCallback dimensionUpdateCallback =
                new PaginationController.DimensionUpdateCallback();
        dimensionUpdateCallback.addListener(mRecyclerView);
        dimensionUpdateCallback.addListener(mPageIndicator);
        dimensionUpdateCallback.addListener(this);
        mPaginationController = new PaginationController(windowBackground, dimensionUpdateCallback);

        mBanner = requireViewById(R.id.tos_banner);

        mBackgroundAnimationHelper = new BackgroundAnimationHelper(windowBackground, mBanner);

        setupTosBanner();

        setupContentObserversForTos();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateMode();
        if (mCar.isConnected()) {
            reinitializeLauncherModel();
        }
    }

    @Override
    protected void onDestroy() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }

        if (mMirroringService != null) {
            Message msg = Message.obtain(null,
                    getResources().getInteger(R.integer.config_msg_unregister_mirroring_pkg_code));
            msg.replyTo = mMessenger;
            try {
                mMirroringService.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Exception sending message to mirroring service: " + e);
            }
        }

        unregisterContentObserversForTos();

        super.onDestroy();
    }

    private void updateMode() {
        mMode = parseMode(getIntent());
        setTitle(mMode.mTitleStringId);
        if (mShowToolbar) {
            CarUi.requireToolbar(this).setTitle(mMode.mTitleStringId);
        }
    }

    @VisibleForTesting
    boolean isHorizontal() {
        return AppGridConstants.isHorizontal(mPageOrientation);
    }

    /**
     * Note: This activity is exported, meaning that it might receive intents from any source.
     * Intent data parsing must be extra careful.
     */
    @NonNull
    private Mode parseMode(@Nullable Intent intent) {
        String mode = intent != null ? intent.getStringExtra(MODE_INTENT_EXTRA) : null;
        try {
            return mode != null ? Mode.valueOf(mode) : Mode.ALL_APPS;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Received invalid mode: " + mode, e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTosBannerVisibility();
        updateScrollState();
        mAdapter.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
    }

    @Override
    public void onDimensionsUpdated(PageMeasurementHelper.PageDimensions pageDimens,
            PageMeasurementHelper.GridDimensions gridDimens) {
        // TODO(b/271637411): move this method into a scroll controller
        mAppGridMarginHorizontal = pageDimens.marginHorizontalPx;
        mAppGridMarginVertical = pageDimens.marginVerticalPx;
        mAppGridWidth = gridDimens.gridWidthPx;
        mAppGridHeight = gridDimens.gridHeightPx;
    }

    /**
     * Updates the scroll state after receiving data changes, such as new apps being added or
     * reordered, and when user returns to launcher onResume.
     *
     * Additionally, notify page indicator to handle resizing in case new app addition creates a
     * new page or deleted a page.
     */
    void updateScrollState() {
        // TODO(b/271637411): move this method into a scroll controller
        // to calculate how many pages we need to offset, we use the scroll offset anchor position
        // as item count and map to the page which the anchor is on.
        int offsetPageCount = mAdapter.getPageCount(mNextScrollDestination + 1) - 1;
        mRecyclerView.suppressLayout(false);
        mCurrentScrollOffset = offsetPageCount * (isHorizontal()
                ? (mAppGridWidth + 2 * mAppGridMarginHorizontal)
                : (mAppGridHeight + 2 * mAppGridMarginVertical));
        mLayoutManager.scrollToPositionWithOffset(/* position */
                offsetPageCount * mNumOfRows * mNumOfCols, /* offset */ 0);

        mPageIndicator.updateOffset(mCurrentScrollOffset);
        mPageIndicator.updatePageCount(mAdapter.getPageCount());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // register broadcast receiver for package installation and uninstallation
        mInstallUninstallReceiver = new AppInstallUninstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(mInstallUninstallReceiver, filter);

        // Connect to car service
        mCar.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // disconnect from app install/uninstall receiver
        if (mInstallUninstallReceiver != null) {
            unregisterReceiver(mInstallUninstallReceiver);
            mInstallUninstallReceiver = null;
        }
        // disconnect from car listeners
        try {
            if (mCarUxRestrictionsManager != null) {
                mCarUxRestrictionsManager.unregisterListener();
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Error unregistering listeners", e);
        }
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    @Override
    protected void onPause() {
        dismissShortcutPopup();
        // Required as banner needs to be animated when activity is resumed
        if (showTosBanner(/* context = */ this)) {
            mBackgroundAnimationHelper.hideBanner();
        }
        super.onPause();
    }

    @Override
    public void onSnapToPosition(int position) {
        mNextScrollDestination = position;
    }

    @Override
    public void onItemLongPressed(boolean isLongPressed) {
        // after the user long presses the app icon, scrolling should be disabled until long press
        // is canceled as to allow MotionEvent to be interpreted as attempt to drag the app icon.
        mRecyclerView.suppressLayout(isLongPressed);
    }

    @Override
    public void onItemSelected(int gridPositionFrom) {
        mIsCurrentlyDragging = true;
        mLayoutManager.setShouldLayoutChildren(false);
        mAdapter.setDragStartPoint(gridPositionFrom);
        dismissShortcutPopup();
    }

    @Override
    public void onItemDragged() {
        mAppGridDragController.cancelDelayedPageFling();
    }

    @Override
    public void onDragExited(int gridPosition, @AppItemBoundDirection int exitDirection) {
        if (mAdapter.getOffsetBoundDirection(gridPosition) == exitDirection) {
            mAppGridDragController.postDelayedPageFling(exitDirection);
        }
    }

    @Override
    public void onItemDropped(int gridPositionFrom, int gridPositionTo) {
        mLayoutManager.setShouldLayoutChildren(true);
        mAdapter.moveAppItem(gridPositionFrom, gridPositionTo);
    }

    /**
     * Note that in order to obtain usage stats from the previous boot,
     * the device must have gone through a clean shut down process.
     */
    private List<AppMetaData> getMostRecentApps(LauncherAppsInfo appsInfo) {
        ArrayList<AppMetaData> apps = new ArrayList<>();
        if (appsInfo.isEmpty()) {
            return apps;
        }

        // get the usage stats starting from 1 year ago with a INTERVAL_YEARLY granularity
        // returning entries like:
        // "During 2017 App A is last used at 2017/12/15 18:03"
        // "During 2017 App B is last used at 2017/6/15 10:00"
        // "During 2018 App A is last used at 2018/1/1 15:12"
        List<UsageStats> stats =
                mUsageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_YEARLY,
                        System.currentTimeMillis() - DateUtils.YEAR_IN_MILLIS,
                        System.currentTimeMillis());

        if (stats == null || stats.size() == 0) {
            return apps; // empty list
        }

        stats.sort(new LastTimeUsedComparator());

        int currentIndex = 0;
        int itemsAdded = 0;
        int statsSize = stats.size();
        int itemCount = Math.min(mNumOfCols, statsSize);
        while (itemsAdded < itemCount && currentIndex < statsSize) {
            UsageStats usageStats = stats.get(currentIndex);
            String packageName = usageStats.getPackageName();
            currentIndex++;

            // do not include self
            if (packageName.equals(getPackageName())) {
                continue;
            }

            // TODO(b/136222320): UsageStats is obtained per package, but a package may contain
            //  multiple media services. We need to find a way to get the usage stats per service.
            ComponentName componentName = AppLauncherUtils.getMediaSource(mPackageManager,
                    packageName);
            // Exempt media services from background and launcher checks
            if (!appsInfo.isMediaService(componentName)) {
                // do not include apps that only ran in the background
                if (usageStats.getTotalTimeInForeground() == 0) {
                    continue;
                }

                // do not include apps that don't support starting from launcher
                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent == null || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                    continue;
                }
            }

            AppMetaData app = appsInfo.getAppMetaData(componentName);
            // Prevent duplicated entries
            // e.g. app is used at 2017/12/31 23:59, and 2018/01/01 00:00
            if (app != null && !apps.contains(app)) {
                apps.add(app);
                itemsAdded++;
            }
        }
        return apps;
    }

    @Override
    public void onCarUiInsetsChanged(Insets insets) {
        requireViewById(R.id.apps_grid)
                .setPadding(0, insets.getTop(), 0, insets.getBottom());
        FocusArea focusArea = requireViewById(R.id.focus_area);
        focusArea.setHighlightPadding(0, insets.getTop(), 0, insets.getBottom());
        focusArea.setBoundsOffset(0, insets.getTop(), 0, insets.getBottom());

        requireViewById(android.R.id.content)
                .setPadding(insets.getLeft(), 0, insets.getRight(), 0);
    }

    @Override
    public void onShortcutsShow(CarUiShortcutsPopup carUiShortcutsPopup) {
        sCarUiShortcutsPopup = carUiShortcutsPopup;
    }

    private void dismissShortcutPopup() {
        // TODO (b/268563442): shortcut popup is set to be static since its
        // sometimes recreated when taskview is present, find out why
        if (sCarUiShortcutsPopup != null) {
            sCarUiShortcutsPopup.dismiss();
            sCarUiShortcutsPopup = null;
        }
    }

    /**
     * Comparator for {@link UsageStats} that sorts the list by the "last time used" property
     * in descending order.
     */
    private static class LastTimeUsedComparator implements Comparator<UsageStats> {
        @Override
        public int compare(UsageStats stat1, UsageStats stat2) {
            Long time1 = stat1.getLastTimeUsed();
            Long time2 = stat2.getLastTimeUsed();
            return time2.compareTo(time1);
        }
    }

    private class AppInstallUninstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (TextUtils.isEmpty(packageName)) {
                Log.e(TAG, "System sent an empty app install/uninstall broadcast");
                return;
            }
            // TODO b/256684061: find better way to get AppInfo from package name.
            reinitializeLauncherModel();
        }
    }

    private class AppGridOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mCurrentScrollOffset = mCurrentScrollOffset + (isHorizontal() ? dx : dy);
            mPageIndicator.updateOffset(mCurrentScrollOffset);
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            mCurrentScrollState = newState;
            mSnapCallback.setScrollState(mCurrentScrollState);
            switch (newState) {
                case RecyclerView.SCROLL_STATE_DRAGGING:
                    if (!mIsCurrentlyDragging) {
                        mDragCallback.cancelDragTasks();
                    }
                    dismissShortcutPopup();
                    mPageIndicator.animateAppearance();
                    break;

                case RecyclerView.SCROLL_STATE_SETTLING:
                    mPageIndicator.animateAppearance();
                    break;

                case RecyclerView.SCROLL_STATE_IDLE:
                    if (mIsCurrentlyDragging) {
                        mLayoutManager.setShouldLayoutChildren(false);
                    }
                    mPageIndicator.animateFading();
                    // in case the recyclerview was scrolled by rotary input, we need to handle
                    // focusing the correct element: either on the first or last element on page
                    mRecyclerView.maybeHandleRotaryFocus();
            }
        }
    }

    private class AppGridDragController {
        // TODO: (b/271320404) move DragController to separate directory called dragndrop and
        // migrate logic this class and AppItemViewHolder there.
        private final Handler mHandler;

        AppGridDragController() {
            mHandler = new Handler(getMainLooper());
        }

        void cancelDelayedPageFling() {
            mHandler.removeCallbacksAndMessages(null);
        }

        void postDelayedPageFling(@AppItemBoundDirection int exitDirection) {
            boolean scrollToNextPage = isHorizontal()
                    ? exitDirection == AppItemBoundDirection.RIGHT
                    : exitDirection == AppItemBoundDirection.BOTTOM;
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    if (mCurrentScrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        mAdapter.updatePageScrollDestination(scrollToNextPage);
                        mNextScrollDestination = mSnapCallback.getSnapPosition();

                        mLayoutManager.setShouldLayoutChildren(true);
                        mRecyclerView.smoothScrollToPosition(mNextScrollDestination);
                    }
                    // another delayed scroll will be queued to enable the user to input multiple
                    // page scrolls by holding the recyclerview at the app grid margin
                    postDelayedPageFling(exitDirection);
                }
            }, mOffPageHoverBeforeScrollMs);
        }
    }

    /**
     * Private onDragListener for handling dispatching off page scroll event when user holds the app
     * icon at the page margin.
     */
    private class AppGridDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
                mIsCurrentlyDragging = false;
                mAppGridDragController.cancelDelayedPageFling();
                mDragCallback.resetCallbackState();
                mLayoutManager.setShouldLayoutChildren(true);
                if (action == DragEvent.ACTION_DROP) {
                    return false;
                } else {
                    animateDropEnded(getDragSurface(event));
                }
            }
            return true;
        }
    }

    private void animateDropEnded(@Nullable SurfaceControl dragSurface) {
        if (dragSurface == null) {
            Log.d(TAG, "animateDropEnded, dragSurface unavailable");
            return;
        }
        // update default animation for the drag shadow after user lifts their finger
        SurfaceControl.Transaction txn = new SurfaceControl.Transaction();
        // set an animator to animate a delay before clearing the dragSurface
        ValueAnimator delayedDismissAnimator = ValueAnimator.ofFloat(0f, 1f);
        delayedDismissAnimator.setStartDelay(
                getResources().getInteger(R.integer.ms_drop_animation_delay));
        delayedDismissAnimator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        txn.setAlpha(dragSurface, 0);
                        txn.apply();
                    }
                });
        delayedDismissAnimator.start();
    }

    private void setupTosBanner() {
        if (AppLauncherUtils.tosAccepted(/* context = */ this)) {
            return;
        }
        mBanner.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateTosBannerVisibility();
                mBanner.getViewTreeObserver().removeOnGlobalLayoutListener(/* victim = */ this);
            }
        });
        mBanner.setFirstButtonOnClickListener(v -> {
            Intent tosIntent = AppLauncherUtils.getIntentForTosAcceptanceFlow(v.getContext());
            AppLauncherUtils.launchApp(v.getContext(), tosIntent);
        });
        mBanner.setSecondButtonOnClickListener(
                v -> {
                    mBackgroundAnimationHelper.hideBanner();
                    saveTosBannerDismissalTime();
                });
    }

    private void updateTosBannerVisibility() {
        if (showTosBanner(/* context = */ this)) {
            mBackgroundAnimationHelper.showBanner();
        } else {
            mBanner.setVisibility(View.GONE);
        }
    }

    private void setupContentObserversForTos() {
        if (AppLauncherUtils.tosStatusUninitialized(/* context = */ this)
                || !AppLauncherUtils.tosAccepted(/* context = */ this)) {
            Log.i(TAG, "TOS not accepted, setting up content observers for TOS state");
        } else {
            Log.i(TAG, "TOS accepted, state will remain accepted, "
                    + "don't need to observe this value");
            return;
        }
        mTosContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                boolean tosState = AppLauncherUtils.tosAccepted(getBaseContext());
                Log.i(TAG, "TOS state updated:" + tosState);
                reinitializeLauncherModel();
                if (tosState) {
                    unregisterContentObserversForTos();
                }
            }
        };
        mTosDisabledAppsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                reinitializeLauncherModel();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_USER_TOS_ACCEPTED),
                /* notifyForDescendants*/ false,
                mTosContentObserver);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_UNACCEPTED_TOS_DISABLED_APPS),
                /* notifyForDescendants*/ false,
                mTosDisabledAppsContentObserver
        );
    }

    private void unregisterContentObserversForTos() {
        if (mTosContentObserver != null) {
            Log.i(TAG, "Unregister content observer for tos state");
            getContentResolver().unregisterContentObserver(mTosContentObserver);
            mTosContentObserver = null;
        }
        if (mTosDisabledAppsContentObserver != null) {
            Log.i(TAG, "Unregister content observer for tos disabled apps");
            getContentResolver().unregisterContentObserver(mTosDisabledAppsContentObserver);
            mTosDisabledAppsContentObserver = null;
        }
    }

    @VisibleForTesting
    boolean showTosBanner(Context context) {
        if (AppLauncherUtils.tosAccepted(context)) {
            return false;
        }
        // Convert days to seconds
        long bannerResurfaceTimeInSeconds = TimeUnit.DAYS.toSeconds(context.getResources()
                .getInteger(R.integer.config_tos_banner_resurface_time_days));
        long bannerDismissTime = PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(TOS_BANNER_DISMISS_TIME_KEY, /* defValue = */ 0);

        // Show on next drive / reboot, when banner has not been dismissed in current session
        if (bannerResurfaceTimeInSeconds == 0) {
            // If banner is dismissed in current drive session, it will have a timestamp greater
            // than the system boot time timestamp.
            return bannerDismissTime < getSystemBootTime();
        }
        return mClock.instant().getEpochSecond() - bannerDismissTime > bannerResurfaceTimeInSeconds;
    }

    private void saveTosBannerDismissalTime() {
        long dismissTime = mClock.instant().getEpochSecond();
        PreferenceManager.getDefaultSharedPreferences(/* context = */ this)
                .edit().putLong(TOS_BANNER_DISMISS_TIME_KEY, dismissTime).apply();
    }

    @VisibleForTesting
    long getSystemBootTime() {
        long uptimeInSeconds = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime());
        return mClock.instant().getEpochSecond() - uptimeInSeconds;
    }

    @VisibleForTesting
    void setCarUxRestrictionsManager(CarUxRestrictionsManager carUxRestrictionsManager) {
        mCarUxRestrictionsManager = carUxRestrictionsManager;
    }

    @VisibleForTesting
    void setPageIndicator(PageIndicator pageIndicator) {
        mPageIndicator = pageIndicator;
    }

    class IncomingHandler extends Handler {

        int mSendMirroringPkgCode = getResources()
                .getInteger(R.integer.config_msg_send_mirroring_pkg_code);
        String mMirroringPkgNameKey = getString(R.string.config_msg_mirroring_pkg_name_key);
        String mMirroringRedirectUriKey = getString(R.string.config_msg_mirroring_redirect_uri_key);

        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Message received: " + msg);
            if (msg.what
                    == mSendMirroringPkgCode) {
                Bundle bundle = (Bundle) msg.obj;
                mMirroringPackageName =
                        bundle.getString(mMirroringPkgNameKey);
                Log.d(TAG, "message received with package name = " + mMirroringPackageName);
                try {
                    mMirroringIntentRedirect = Intent.parseUri(
                            bundle.getString(mMirroringRedirectUriKey),
                            URI_INTENT_SCHEME);
                    Log.d(TAG, "intent is: " + mMirroringIntentRedirect);
                    mLauncherModel.updateMirroringItem(mMirroringPackageName,
                            mMirroringIntentRedirect);
                } catch (URISyntaxException e) {
                    Log.d(TAG, "Error parsing mirroring redirect intent " + e);
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }
}
