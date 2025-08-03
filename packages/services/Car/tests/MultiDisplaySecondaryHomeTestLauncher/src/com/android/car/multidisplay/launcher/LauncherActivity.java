/**
 * Copyright (c) 2018 The Android Open Source Project
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

package com.android.car.multidisplay.launcher;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER;

import static com.android.car.multidisplay.launcher.PinnedAppListViewModel.PINNED_APPS_KEY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Application;
import android.app.WallpaperManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.app.CarActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory;

import com.android.car.multidisplay.R;

import com.google.android.material.circularreveal.cardview.CircularRevealCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Main launcher activity. It's launch mode is configured as "singleTop" to allow showing on
 * multiple displays and to ensure a single instance per each display.
 */
public final class LauncherActivity extends FragmentActivity implements AppPickedCallback,
        PopupMenu.OnMenuItemClickListener {

    private static final String TAG = LauncherActivity.class.getSimpleName();

    private Spinner mDisplaySpinner;
    private ArrayAdapter<DisplayItem> mDisplayAdapter;
    private int mSelectedDisplayId = Display.INVALID_DISPLAY;
    private View mRootView;
    private View mScrimView;
    private View mAppDrawerHeader;
    private AppListAdapter mAppListAdapter;
    private AppListAdapter mPinnedAppListAdapter;
    private CircularRevealCardView mAppDrawerView;
    private FloatingActionButton mFab;
    private CheckBox mNewInstanceCheckBox;
    private TextDrawable mBackgroundDrawable;

    private boolean mIsWallpaperSupported;
    private boolean mAppDrawerShown;

    private Car mCar;

    private final CarOccupantZoneManager.OccupantZoneConfigChangeListener mConfigChangeListener =
            flags -> {
                if ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER) != 0) {
                    if (isVisibleForAutofill()) {
                        launchOtherUiForInvalidUser(mCar);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WallpaperManager wallpaperMgr = getSystemService(WallpaperManager.class);
        mIsWallpaperSupported = wallpaperMgr != null && wallpaperMgr.isWallpaperSupported();
        int userId = getUserId();
        int displayId = getDisplayId();
        Log.d(TAG, "Creating for user " + userId + " on display " + displayId
                + ". Wallpaper supported: " + mIsWallpaperSupported);

        Car.createCar(/* context= */ this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    mCar = car;
                    if (!ready) {
                        Log.w(TAG, "CarService looks crashed");
                        finish();  // ATM will restart 2nd Home again.
                        return;
                    }
                    CarOccupantZoneManager occupantZoneManager = (CarOccupantZoneManager)
                            car.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
                    occupantZoneManager.registerOccupantZoneConfigChangeListener(
                            mConfigChangeListener);
                    launchOtherUiForInvalidUser(car);
                });

        setContentView(R.layout.activity_main);

        mRootView = findViewById(R.id.RootView);
        mScrimView = findViewById(R.id.Scrim);
        mAppDrawerView = findViewById(R.id.FloatingSheet);

        int pid = Process.myPid();
        mBackgroundDrawable = new TextDrawable(this, Color.WHITE, /* defaultSize= */ 100,
                "User #" + userId,
                "Display #" + displayId,
                "PID " + pid,
                "ProcessGrp " + Process.getProcessGroup(pid));
        mRootView.setBackground(mBackgroundDrawable);

        // get system insets and apply padding accordingly to the content view
        mRootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        mRootView.setOnApplyWindowInsetsListener((view, insets) -> {
            mRootView.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            mAppDrawerHeader = findViewById(R.id.FloatingSheetHeader);
            mAppDrawerHeader.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets.consumeSystemWindowInsets();
        });

        mFab = findViewById(R.id.FloatingActionButton);
        mFab.setOnClickListener((v) -> showAppDrawer(true));

        mScrimView.setOnClickListener((v) -> showAppDrawer(false));

        mDisplaySpinner = findViewById(R.id.spinner);
        mDisplaySpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelectedDisplayId = mDisplayAdapter.getItem(i).mId;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mSelectedDisplayId = Display.INVALID_DISPLAY;
            }
        });
        mDisplayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new ArrayList<DisplayItem>());
        mDisplayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDisplaySpinner.setAdapter(mDisplayAdapter);

        ViewModelProvider viewModelProvider = new ViewModelProvider(getViewModelStore(),
                new AndroidViewModelFactory((Application) getApplicationContext()));

        mPinnedAppListAdapter = new AppListAdapter(this);
        GridView pinnedAppGridView = findViewById(R.id.pinned_app_grid);
        pinnedAppGridView.setAdapter(mPinnedAppListAdapter);
        pinnedAppGridView.setOnItemClickListener(
                (a, v, position, i) -> launch(mPinnedAppListAdapter.getItem(position)));
        PinnedAppListViewModel pinnedAppListViewModel =
                viewModelProvider.get(PinnedAppListViewModel.class);
        pinnedAppListViewModel.getPinnedAppList().observe(this,
                data -> mPinnedAppListAdapter.setData(data));

        mAppListAdapter = new AppListAdapter(this);
        GridView appGridView = findViewById(R.id.app_grid);
        appGridView.setAdapter(mAppListAdapter);
        appGridView.setOnItemClickListener(
                (a, v, position, id) -> launch(mAppListAdapter.getItem(position)));
        AppListViewModel appListViewModel = viewModelProvider.get(AppListViewModel.class);
        appListViewModel.getAppList().observe(this, data -> mAppListAdapter.setData(data));

        findViewById(R.id.RefreshButton).setOnClickListener(this::refreshDisplayPicker);
        mNewInstanceCheckBox = findViewById(R.id.NewInstanceCheckBox);

        ImageButton optionsButton = findViewById(R.id.OptionsButton);
        optionsButton.setOnClickListener((v) -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.setOnMenuItemClickListener(this);
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.context_menu, popup.getMenu());
            if (!mIsWallpaperSupported) {
                popup.getMenu().findItem(R.id.set_wallpaper).setEnabled(false);
            }
            popup.show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        launchOtherUiForInvalidUser(mCar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCar == null) {
            return;
        }

        CarOccupantZoneManager occupantZoneManager = (CarOccupantZoneManager)
                mCar.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
        occupantZoneManager.unregisterOccupantZoneConfigChangeListener(mConfigChangeListener);
        mCar.disconnect();
        mCar = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Respond to picking one of the popup menu items.
        switch (item.getItemId()) {
            case R.id.add_app_shortcut:
                FragmentManager fm = getSupportFragmentManager();
                PinnedAppPickerDialog pickerDialogFragment =
                        PinnedAppPickerDialog.newInstance(mAppListAdapter, this);
                pickerDialogFragment.show(fm, "fragment_app_picker");
                return true;
            case R.id.set_wallpaper:
                Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                startActivity(Intent.createChooser(intent, getString(R.string.set_wallpaper)));
                return true;
            case R.id.exit:
                Log.i(TAG, "So long, and thanks for all the fish...");
                finish();
                return true;
            default:
                return true;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        showAppDrawer(false);
    }

    public void onBackPressed() {
        // If the app drawer was shown - hide it. Otherwise, not doing anything since we don't want
        // to close the launcher.
        showAppDrawer(false);
    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // Hide keyboard.
            View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(
                        v.getWindowToken(), 0);
            }
        }

        // A new intent will bring the launcher to top. Hide the app drawer to reset the state.
        showAppDrawer(false);
    }

    private void launchOtherUiForInvalidUser(Car car) {
        CarOccupantZoneManager occupantZoneManager = (CarOccupantZoneManager)
                car.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
        int myDisplayId = getDisplay().getDisplayId();
        int assignedUserId = occupantZoneManager.getUserForDisplayId(myDisplayId);
        if (assignedUserId == getUserId()) {
            Log.i(TAG, "Right user:" + getUserId() + " is assigned to Display#:"
                    + myDisplayId);
            return;
        }
        // To pass MultiDisplaySystemDecorationTests, UserPicker should be launched only in
        // the passenger zones.
        // TODO(b/280122459): Elaborate the logic for MUPAND case.
        OccupantZoneInfo zone = occupantZoneManager.getOccupantZoneForDisplayId(myDisplayId);
        if (zone == null
                || (zone.occupantType != OCCUPANT_TYPE_FRONT_PASSENGER
                        && zone.occupantType != OCCUPANT_TYPE_REAR_PASSENGER)) {
            Log.i(TAG, "Not a passenger zone, don't start UserPicker: Display#:" + myDisplayId);
            return;
        }
        UserManager userManager = getSystemService(UserManager.class);
        if (!userManager.isVisibleBackgroundUsersSupported()) {
            Log.i(TAG, "No visible background users are supported");
            return;
        }
        // After this point, we either should launch user picker or home
        boolean shouldLaunchUserPicker = false;
        if (assignedUserId == UserHandle.USER_NULL) {
            Log.w(TAG, "No assigned user for Display#" + myDisplayId);
            shouldLaunchUserPicker = true;
        } else if (userManager.isUserUnlocked(UserHandle.of(assignedUserId))) {
            Log.i(TAG, "User " + assignedUserId + " is unlocked for Display#" + myDisplayId
                    + ": finish itself to allow ATM to bring up the correct HOME");
            shouldLaunchUserPicker = false;
        } else {
            Log.i(TAG, "Will start user picker as user is not unlocked, User:" + assignedUserId
                    + ", Display#:" + myDisplayId);
            shouldLaunchUserPicker = true;
        }
        if (shouldLaunchUserPicker) {
            CarActivityManager am = (CarActivityManager) car.getCarManager(
                    Car.CAR_ACTIVITY_SERVICE);
            am.startUserPickerOnDisplay(myDisplayId);
        }
        finish();
    }

    private void launch(AppEntry entry) {
        Intent intent = entry.getLaunchIntent();
        Log.i(TAG, "Launching " + entry + " for user " + getUserId() + " using " + intent);

        launch(intent);
    }

    void launch(Intent launchIntent) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mNewInstanceCheckBox.isChecked()) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        if (mSelectedDisplayId != Display.INVALID_DISPLAY) {
            Log.v(TAG, "launch(): setting display to " + mSelectedDisplayId);
            options.setLaunchDisplayId(mSelectedDisplayId);
        }
        try {
            Log.v(TAG, "calling startActivity() for " + launchIntent);
            startActivity(launchIntent, options.toBundle());
        } catch (Exception e) {
            Log.e(TAG, "start activity failed", e);
            AlertDialog.Builder builder =
                    new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            builder.setTitle(R.string.couldnt_launch)
                    .setMessage(e.getLocalizedMessage())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    private void refreshDisplayPicker() {
        refreshDisplayPicker(mAppDrawerView);
    }

    private void refreshDisplayPicker(View view) {
        int currentDisplayId = view.getDisplay().getDisplayId();
        Log.d(TAG, "refreshing " + view + " from display " + currentDisplayId);

        DisplayManager dm = getSystemService(DisplayManager.class);
        mDisplayAdapter.setNotifyOnChange(false);
        mDisplayAdapter.clear();
        mDisplayAdapter.add(new DisplayItem(Display.INVALID_DISPLAY, "Do not specify display"));

        for (Display display : dm.getDisplays()) {
            int id = display.getDisplayId();
            boolean isDisplayPrivate = (display.getFlags() & Display.FLAG_PRIVATE) != 0;
            boolean isCurrentDisplay = id == currentDisplayId;
            StringBuilder sb = new StringBuilder();
            sb.append(id).append(": ").append(display.getName());
            if (isDisplayPrivate) {
                sb.append(" (private)");
            }
            if (isCurrentDisplay) {
                sb.append(" [Current display]");
            }
            String description = sb.toString();
            Log.v(TAG, "Adding DisplayItem " + id + ": " + description);
            mDisplayAdapter.add(new DisplayItem(id, description));
        }

        mDisplayAdapter.notifyDataSetChanged();
    }

    /**
     * Store the picked app to persistent pinned list and update the loader.
     */
    @Override
    public void onAppPicked(AppEntry appEntry) {
        Log.i(TAG, "pinning " + appEntry);
        SharedPreferences sp = getSharedPreferences(PINNED_APPS_KEY, 0);
        Set<String> pinnedApps = sp.getStringSet(PINNED_APPS_KEY, null);
        if (pinnedApps == null) {
            pinnedApps = new HashSet<String>();
        } else {
            // Always need to create a new object to make sure that the changes are persisted.
            pinnedApps = new HashSet<String>(pinnedApps);
        }
        pinnedApps.add(appEntry.getComponentName().flattenToString());

        SharedPreferences.Editor editor = sp.edit();
        editor.putStringSet(PINNED_APPS_KEY, pinnedApps);
        editor.apply();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length > 0 && args[0].equals("--quiet")) {
            writer.printf("%s(not dumping superclass info when using --quiet)\n\n", prefix);
        } else {
            super.dump(prefix, fd, writer, args);
        }
        String prefix2 = prefix + "  ";
        writer.printf("%smUser: %s\n", prefix, getUserId());
        writer.printf("%smDisplay: %s\n", prefix, getDisplayId());
        writer.printf("%smmIsWallpaperSupported: %s\n", prefix, mIsWallpaperSupported);
        writer.printf("%smDisplaySpinner: %s\n", prefix, mDisplaySpinner);
        writer.printf("%smDisplayAdapter:\n", prefix);
        SantasLittleHelper.dump(mDisplayAdapter, prefix2, writer);
        writer.printf("%smAppDrawerHeader: %s\n", prefix, mAppDrawerHeader);
        writer.printf("%smSelectedDisplayId: %d\n", prefix, mSelectedDisplayId);
        writer.printf("%smRootView: %s\n", prefix, mRootView);
        writer.printf("%smScrimView: %s\n", prefix, mScrimView);
        writer.printf("%smAppDrawerHeader:\n", prefix);
        SantasLittleHelper.dump(mAppListAdapter, prefix2, writer);
        writer.printf("%smPinnedAppListAdapter:\n", prefix);
        SantasLittleHelper.dump(mPinnedAppListAdapter, prefix2, writer);
        writer.printf("%smFab: %s\n", prefix, mFab);
        writer.printf("%smNewInstanceCheckBox: %s\n", prefix, mNewInstanceCheckBox);
        writer.printf("%smAppDrawerShown: %s\n", prefix, mAppDrawerShown);
        writer.printf("%smBackgroundDrawable:\n", prefix);
        mBackgroundDrawable.dump(prefix2, writer);
    }

    public int getUserId() {
        return UserHandle.myUserId();
    }

    /**
     * Show/hide app drawer card with animation.
     */
    private void showAppDrawer(boolean show) {
        Log.v(TAG, "showAppDrawer(show=" + show + ", mAppDrawerShown=" + mAppDrawerShown + ")");
        if (show == mAppDrawerShown) {
            return;
        }

        Animator animator = revealAnimator(mAppDrawerView, show);
        if (show) {
            mAppDrawerShown = true;
            mAppDrawerView.setVisibility(View.VISIBLE);
            mScrimView.setVisibility(View.VISIBLE);
            mFab.setVisibility(View.INVISIBLE);
            refreshDisplayPicker();
        } else {
            mAppDrawerShown = false;
            mScrimView.setVisibility(View.INVISIBLE);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mAppDrawerView.setVisibility(View.INVISIBLE);
                    mFab.setVisibility(View.VISIBLE);
                }
            });
        }
        animator.start();
    }

    /**
     * Create reveal/hide animator for app list card.
     */
    private Animator revealAnimator(View view, boolean open) {
        int radius = (int) Math.hypot((double) view.getWidth(), (double) view.getHeight());
        return ViewAnimationUtils.createCircularReveal(view, view.getRight(), view.getBottom(),
                open ? 0 : radius, open ? radius : 0);
    }

    private static final class DisplayItem {
        private final int mId;
        private final String mDescription;

        DisplayItem(int displayId, String description) {
            mId = displayId;
            mDescription = description;
        }

        @Override
        public String toString() {
            return mDescription;
        }
    }
}
