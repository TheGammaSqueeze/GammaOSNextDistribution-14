/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink;

import android.annotation.Nullable;
import android.app.NotificationManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarProjectionManager;
import android.car.hardware.CarSensorManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.property.CarPropertyManager;
import android.car.os.CarPerformanceManager;
import android.car.telemetry.CarTelemetryManager;
import android.car.watchdog.CarWatchdogManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.kitchensink.activityresolver.ActivityResolverFragment;
import com.google.android.car.kitchensink.admin.DevicePolicyFragment;
import com.google.android.car.kitchensink.alertdialog.AlertDialogTestFragment;
import com.google.android.car.kitchensink.assistant.CarAssistantFragment;
import com.google.android.car.kitchensink.audio.AudioConfigurationTestFragment;
import com.google.android.car.kitchensink.audio.AudioMirrorTestFragment;
import com.google.android.car.kitchensink.audio.AudioTestFragment;
import com.google.android.car.kitchensink.audio.AudioUserAssignmentFragment;
import com.google.android.car.kitchensink.audio.CarAudioInputTestFragment;
import com.google.android.car.kitchensink.audio.OemCarServiceTestFragment;
import com.google.android.car.kitchensink.audiorecorder.AudioRecorderTestFragment;
import com.google.android.car.kitchensink.backup.BackupAndRestoreFragment;
import com.google.android.car.kitchensink.biometrics.BiometricPromptTestFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothHeadsetFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothUuidFragment;
import com.google.android.car.kitchensink.bluetooth.MapMceTestFragment;
import com.google.android.car.kitchensink.camera2.Camera2TestFragment;
import com.google.android.car.kitchensink.carboard.KeyboardTestFragment;
import com.google.android.car.kitchensink.cluster.InstrumentClusterFragment;
import com.google.android.car.kitchensink.connectivity.ConnectivityFragment;
import com.google.android.car.kitchensink.cube.CubesTestFragment;
import com.google.android.car.kitchensink.diagnostic.DiagnosticTestFragment;
import com.google.android.car.kitchensink.display.DisplayInfoFragment;
import com.google.android.car.kitchensink.display.DisplayMirroringFragment;
import com.google.android.car.kitchensink.display.VirtualDisplayFragment;
import com.google.android.car.kitchensink.drivemode.DriveModeSwitchFragment;
import com.google.android.car.kitchensink.experimental.ExperimentalFeatureTestFragment;
import com.google.android.car.kitchensink.hotword.CarMultiConcurrentHotwordTestFragment;
import com.google.android.car.kitchensink.hvac.HvacTestFragment;
import com.google.android.car.kitchensink.input.DisplayInputLockTestFragment;
import com.google.android.car.kitchensink.insets.WindowInsetsFullScreenFragment;
import com.google.android.car.kitchensink.key.InjectKeyTestFragment;
import com.google.android.car.kitchensink.mainline.CarMainlineFragment;
import com.google.android.car.kitchensink.media.MultidisplayMediaFragment;
import com.google.android.car.kitchensink.notification.NotificationFragment;
import com.google.android.car.kitchensink.orientation.OrientationTestFragment;
import com.google.android.car.kitchensink.os.CarPerformanceTestFragment;
import com.google.android.car.kitchensink.packageinfo.PackageInfoFragment;
import com.google.android.car.kitchensink.power.PowerTestFragment;
import com.google.android.car.kitchensink.privacy.PrivacyIndicatorFragment;
import com.google.android.car.kitchensink.projection.ProjectionFragment;
import com.google.android.car.kitchensink.property.PropertyTestFragment;
import com.google.android.car.kitchensink.qc.QCViewerFragment;
import com.google.android.car.kitchensink.radio.RadioTestFragment;
import com.google.android.car.kitchensink.remoteaccess.RemoteAccessTestFragment;
import com.google.android.car.kitchensink.rotary.RotaryFragment;
import com.google.android.car.kitchensink.sensor.SensorsTestFragment;
import com.google.android.car.kitchensink.storagelifetime.StorageLifetimeFragment;
import com.google.android.car.kitchensink.storagevolumes.StorageVolumesFragment;
import com.google.android.car.kitchensink.systembars.SystemBarsFragment;
import com.google.android.car.kitchensink.systemfeatures.SystemFeaturesFragment;
import com.google.android.car.kitchensink.telemetry.CarTelemetryTestFragment;
import com.google.android.car.kitchensink.touch.InjectMotionTestFragment;
import com.google.android.car.kitchensink.touch.TouchTestFragment;
import com.google.android.car.kitchensink.users.ProfileUserFragment;
import com.google.android.car.kitchensink.users.UserFragment;
import com.google.android.car.kitchensink.users.UserRestrictionsFragment;
import com.google.android.car.kitchensink.vehiclectrl.VehicleCtrlFragment;
import com.google.android.car.kitchensink.volume.VolumeTestFragment;
import com.google.android.car.kitchensink.watchdog.CarWatchdogTestFragment;
import com.google.android.car.kitchensink.weblinks.WebLinksTestFragment;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class KitchenSinkActivity extends FragmentActivity implements KitchenSinkHelper {

    public static final String TAG = "KitchenSinkActivity";
    private static final String LAST_FRAGMENT_TAG = "lastFragmentTag";
    private static final String DEFAULT_FRAGMENT_TAG = "";

    private static final String PROPERTY_SHOW_HEADER_INFO =
            "com.android.car.kitchensink.SHOW_HEADER_INFO";

    private RecyclerView mMenu;
    private LinearLayout mHeader;
    private Button mMenuButton;
    private TextView mUserIdView;
    private TextView mDisplayIdView;
    private View mKitchenContent;
    private String mLastFragmentTag = DEFAULT_FRAGMENT_TAG;
    @Nullable
    private Fragment mLastFragment;
    private int mNotificationId = 1000;
    private boolean mShowHeaderInfo;

    public static final String DUMP_ARG_CMD = "cmd";
    public static final String DUMP_ARG_FRAGMENT = "fragment";
    public static final String DUMP_ARG_QUIET = "quiet";
    public static final String DUMP_ARG_REFRESH = "refresh";

    private interface ClickHandler {
        void onClick();
    }

    private static abstract class MenuEntry implements ClickHandler {
        abstract String getText();

        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            writer.printf("%s doesn't implement dump()\n", this);
        }
    }

    private final class OnClickMenuEntry extends MenuEntry {
        private final String mText;
        private final ClickHandler mClickHandler;

        OnClickMenuEntry(String text, ClickHandler clickHandler) {
            mText = text;
            mClickHandler = clickHandler;
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            toggleMenuVisibility();
            mClickHandler.onClick();
        }
    }

    private final class FragmentMenuEntry<T extends Fragment> extends MenuEntry {
        private final class FragmentClassOrInstance<T extends Fragment> {
            final Class<T> mClazz;
            T mFragment = null;

            FragmentClassOrInstance(Class<T> clazz) {
                mClazz = clazz;
            }

            T getFragment() {
                if (mFragment == null) {
                    try {
                        mFragment = mClazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Log.e(TAG, "unable to create fragment", e);
                    }
                }
                return mFragment;
            }
        }

        private final String mText;
        private final FragmentClassOrInstance<T> mFragment;

        FragmentMenuEntry(String text, Class<T> clazz) {
            mText = text;
            mFragment = new FragmentClassOrInstance<>(clazz);
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                KitchenSinkActivity.this.showFragment(fragment);
                toggleMenuVisibility();
                mLastFragmentTag = fragment.getTag();
            } else {
                Log.e(TAG, "cannot show fragment for " + getText());
            }
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                fragment.dump(prefix, fd, writer, args);
            } else {
                writer.printf("Cannot dump %s\n", getText());
            }
        }
    }

    private final List<MenuEntry> mMenuEntries = new ArrayList<>();

    public static final List<Pair<String, Class>> MENU_ENTRIES = Arrays.asList(
            new Pair<>("activity resolver", ActivityResolverFragment.class),
            new Pair<>("alert window", AlertDialogTestFragment.class),
            new Pair<>("assistant", CarAssistantFragment.class),
            new Pair<>(AudioTestFragment.FRAGMENT_NAME, AudioTestFragment.class),
            new Pair<>(AudioUserAssignmentFragment.FRAGMENT_NAME,
                    AudioUserAssignmentFragment.class),
            new Pair<>(AudioConfigurationTestFragment.FRAGMENT_NAME,
                    AudioConfigurationTestFragment.class),
            new Pair<>(AudioRecorderTestFragment.FRAGMENT_NAME,
                    AudioRecorderTestFragment.class),
            new Pair<>(CarAudioInputTestFragment.FRAGMENT_NAME,
                    CarAudioInputTestFragment.class),
            new Pair<>(AudioMirrorTestFragment.FRAGMENT_NAME,
                    AudioMirrorTestFragment.class),
            new Pair<>("Hotword", CarMultiConcurrentHotwordTestFragment.class),
            new Pair<>("B&R", BackupAndRestoreFragment.class),
            new Pair<>("BT headset", BluetoothHeadsetFragment.class),
            new Pair<>("BT messaging", MapMceTestFragment.class),
            new Pair<>("BT Uuids", BluetoothUuidFragment.class),
            new Pair<>(BiometricPromptTestFragment.FRAGMENT_NAME,
                    BiometricPromptTestFragment.class),
            new Pair<>("carapi", CarApiTestFragment.class),
            new Pair<>("carboard", KeyboardTestFragment.class),
            new Pair<>("connectivity", ConnectivityFragment.class),
            new Pair<>("cubes test", CubesTestFragment.class),
            new Pair<>("device policy", DevicePolicyFragment.class),
            new Pair<>("diagnostic", DiagnosticTestFragment.class),
            new Pair<>("display info", DisplayInfoFragment.class),
            new Pair<>("display input lock", DisplayInputLockTestFragment.class),
            new Pair<>("display mirroring", DisplayMirroringFragment.class),
            new Pair<>("drive mode switch", DriveModeSwitchFragment.class),
            new Pair<>("experimental feature", ExperimentalFeatureTestFragment.class),
            new Pair<>("hvac", HvacTestFragment.class),
            new Pair<>("inst cluster", InstrumentClusterFragment.class),
            new Pair<>("mainline", CarMainlineFragment.class),
            new Pair<>("MD media", MultidisplayMediaFragment.class),
            new Pair<>("notification", NotificationFragment.class),
            new Pair<>("orientation test", OrientationTestFragment.class),
            new Pair<>("package info", PackageInfoFragment.class),
            new Pair<>("performance", CarPerformanceTestFragment.class),
            new Pair<>("power test", PowerTestFragment.class),
            new Pair<>(PrivacyIndicatorFragment.FRAGMENT_NAME,
                    PrivacyIndicatorFragment.class),
            new Pair<>("profile_user", ProfileUserFragment.class),
            new Pair<>("projection", ProjectionFragment.class),
            new Pair<>("property test", PropertyTestFragment.class),
            new Pair<>("qc viewer", QCViewerFragment.class),
            new Pair<>("remote access", RemoteAccessTestFragment.class),
            new Pair<>("rotary", RotaryFragment.class),
            new Pair<>("sensors", SensorsTestFragment.class),
            new Pair<>("storage lifetime", StorageLifetimeFragment.class),
            new Pair<>("storage volumes", StorageVolumesFragment.class),
            new Pair<>("system bars", SystemBarsFragment.class),
            new Pair<>("system features", SystemFeaturesFragment.class),
            new Pair<>("telemetry", CarTelemetryTestFragment.class),
            new Pair<>("touch test", TouchTestFragment.class),
            new Pair<>("users", UserFragment.class),
            new Pair<>("user restrictions", UserRestrictionsFragment.class),
            new Pair<>("vehicle ctrl", VehicleCtrlFragment.class),
            new Pair<>(VirtualDisplayFragment.FRAGMENT_NAME,
                    VirtualDisplayFragment.class),
            new Pair<>("volume test", VolumeTestFragment.class),
            new Pair<>("watchdog", CarWatchdogTestFragment.class),
            new Pair<>("web links", WebLinksTestFragment.class),
            new Pair<>("inject motion", InjectMotionTestFragment.class),
            new Pair<>("inject key", InjectKeyTestFragment.class),
            new Pair<>("window insets full screen",
                    WindowInsetsFullScreenFragment.class),
            new Pair<>("oem car service", OemCarServiceTestFragment.class),
            new Pair<>("Camera2", Camera2TestFragment.class),
            new Pair<>(RadioTestFragment.FRAGMENT_NAME, RadioTestFragment.class));

    private Car mCarApi;
    private CarHvacManager mHvacManager;
    private CarOccupantZoneManager mOccupantZoneManager;
    private CarPowerManager mPowerManager;
    private CarPropertyManager mPropertyManager;
    private CarSensorManager mSensorManager;
    private CarProjectionManager mCarProjectionManager;
    private CarTelemetryManager mCarTelemetryManager;
    private CarWatchdogManager mCarWatchdogManager;
    private CarPerformanceManager mCarPerformanceManager;
    private Object mPropertyManagerReady = new Object();

    @Override
    public Car getCar() {
        return mCarApi;
    }

    public KitchenSinkActivity() {
        for (Pair<String, Class> entry : MENU_ENTRIES) {
            mMenuEntries.add(new FragmentMenuEntry(entry.first, entry.second));
        }
        mMenuEntries.sort(Comparator.comparing(MenuEntry::getText));
    }

    public CarHvacManager getHvacManager() {
        return mHvacManager;
    }

    public CarOccupantZoneManager getOccupantZoneManager() {
        return mOccupantZoneManager;
    }

    public CarPowerManager getPowerManager() {
        return mPowerManager;
    }

    public CarPropertyManager getPropertyManager() {
        return mPropertyManager;
    }

    public CarSensorManager getSensorManager() {
        return mSensorManager;
    }

    public CarProjectionManager getProjectionManager() {
        return mCarProjectionManager;
    }

    public CarTelemetryManager getCarTelemetryManager() {
        return mCarTelemetryManager;
    }

    public CarWatchdogManager getCarWatchdogManager() {
        return mCarWatchdogManager;
    }

    public CarPerformanceManager getPerformanceManager() {
        return mCarPerformanceManager;
    }

    /* Open any tab directly:
     * adb shell am force-stop com.google.android.car.kitchensink
     * adb shell am 'start -n com.google.android.car.kitchensink/.KitchenSinkActivity \
     *     --es select "connectivity"'
     *
     * Test car watchdog:
     * adb shell am force-stop com.google.android.car.kitchensink
     * adb shell am start -n com.google.android.car.kitchensink/.KitchenSinkActivity \
     *     --es "watchdog" "[timeout] [not_respond_after] [inactive_main_after] [verbose]"
     * - timeout: critical | moderate | normal
     * - not_respond_after: after the given seconds, the client will not respond to car watchdog
     *                      (-1 for making the client respond always)
     * - inactive_main_after: after the given seconds, the main thread will not be responsive
     *                        (-1 for making the main thread responsive always)
     * - verbose: whether to output verbose logs (default: false)
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent");
        if (intent.getCategories().contains(NotificationFragment.INTENT_CATEGORY_SELF_DISMISS)) {
            NotificationManager nm = this.getSystemService(NotificationManager.class);
            nm.cancel(NotificationFragment.SELF_DISMISS_NOTIFICATION_ID);
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        String watchdog = extras.getString("watchdog");
        if (watchdog != null) {
            CarWatchdogClient.start(getCar(), watchdog);
        }
        String select = extras.getString("select");
        if (select != null) {
            Log.d(TAG, "Trying to launch entry '" + select + "'");
            mMenuEntries.stream().filter(me -> select.equals(me.getText()))
                    .findAny().ifPresent(me -> me.onClick());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kitchen_activity);

        // Connection to Car Service does not work for non-automotive yet.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            initCarApi();
        }

        mKitchenContent = findViewById(R.id.kitchen_content);

        mMenu = findViewById(R.id.menu);
        mMenu.setAdapter(new MenuAdapter(this));
        mMenu.setLayoutManager(new GridLayoutManager(this, 4));

        mMenuButton = findViewById(R.id.menu_button);
        mMenuButton.setOnClickListener(view -> toggleMenuVisibility());

        ((Button) findViewById(R.id.new_version_button)).setOnClickListener(
                view -> goToNewVersion());
        ((Button) findViewById(R.id.finish_button)).setOnClickListener(view -> finish());
        ((Button) findViewById(R.id.home_button)).setOnClickListener(view -> launchHome());

        mHeader = findViewById(R.id.header);

        int userId = getUserId();
        int displayId = getDisplayId();

        mUserIdView = findViewById(R.id.user_id);
        mDisplayIdView = findViewById(R.id.display_id);
        mUserIdView.setText("U#" + userId);
        mDisplayIdView.setText("D#" + displayId);

        Log.i(TAG, "onCreate: userId=" + userId + ", displayId=" + displayId);
        onNewIntent(getIntent());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // The app is being started for the first time.
        if (savedInstanceState == null) {
            return;
        }

        // The app is being reloaded, restores the last fragment UI.
        mLastFragmentTag = savedInstanceState.getString(LAST_FRAGMENT_TAG);
        if (!DEFAULT_FRAGMENT_TAG.equals(mLastFragmentTag)) {
            toggleMenuVisibility();
        }
    }

    private void toggleMenuVisibility() {
        boolean menuVisible = mMenu.getVisibility() == View.VISIBLE;
        mMenu.setVisibility(menuVisible ? View.GONE : View.VISIBLE);
        int contentVisibility = menuVisible ? View.VISIBLE : View.GONE;
        mKitchenContent.setVisibility(contentVisibility);
        mMenuButton.setText(menuVisible ? "Show KitchenSink Menu" : "Hide KitchenSink Menu");
        if (mLastFragment != null) {
            mLastFragment.onHiddenChanged(!menuVisible);
        }
    }

    /**
     * Goes to the newer version of Kitchen Sink App.
     */
    private void goToNewVersion() {
        Intent intent = new Intent(this, KitchenSink2Activity.class);
        startActivity(intent);
    }

    /**
     * Sets the visibility of the header that's shown on all fragments.
     */
    public void setHeaderVisibility(boolean visible) {
        mHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Adds a view to the main header (which by default contains the "show/ hide KS menu" button).
     */
    public void addHeaderView(View view) {
        Log.d(TAG, "Adding header view: " + view);
        mHeader.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void launchHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        startActivity(homeIntent);
    }

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }
        mCarApi = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    if (ready) {
                        initManagers(car);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        updateHeaderInfoVisibility();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(LAST_FRAGMENT_TAG, mLastFragmentTag);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        boolean skipParentState = false;
        if (args != null && args.length > 0) {
            Log.v(TAG, "dump: args=" + Arrays.toString(args));
            String arg = args[0];
            switch (arg) {
                case DUMP_ARG_CMD:
                    String[] cmdArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);
                    new KitchenSinkShellCommand(this, writer, cmdArgs, mNotificationId++).run();
                    return;
                case DUMP_ARG_FRAGMENT:
                    if (args.length < 2) {
                        writer.println("Missing fragment name");
                        return;
                    }
                    String select = args[1];
                    Optional<MenuEntry> entry = mMenuEntries.stream()
                            .filter(me -> select.equals(me.getText())).findAny();
                    if (entry.isPresent()) {
                        String[] strippedArgs = new String[args.length - 2];
                        System.arraycopy(args, 2, strippedArgs, 0, strippedArgs.length);
                        entry.get().dump(prefix, fd, writer, strippedArgs);
                    } else {
                        writer.printf("No entry called '%s'\n", select);
                    }
                    return;
                case DUMP_ARG_QUIET:
                    skipParentState = true;
                    break;
                case DUMP_ARG_REFRESH:
                    updateHeaderInfoVisibility(writer);
                    return;
                default:
                    Log.v(TAG, "dump(): unknown arg, calling super(): " + Arrays.toString(args));
            }
        }
        String innerPrefix = prefix;
        if (!skipParentState) {
            writer.printf("%sCustom state:\n", prefix);
            innerPrefix = prefix + prefix;
        }
        writer.printf("%smLastFragmentTag: %s\n", innerPrefix, mLastFragmentTag);
        writer.printf("%smLastFragment: %s\n", innerPrefix, mLastFragment);
        writer.printf("%sHeader views: %d\n", innerPrefix, mHeader.getChildCount());
        writer.printf("%sNext Notification Id: %d\n", innerPrefix, mNotificationId);
        writer.printf("%sShow header info: %b\n", innerPrefix, mShowHeaderInfo);

        if (skipParentState) {
            Log.v(TAG, "dump(): skipping parent state");
            return;
        }
        writer.println();

        super.dump(prefix, fd, writer, args);
    }

    private void showFragment(Fragment fragment) {
        if (mLastFragment != fragment) {
            Log.v(TAG, "showFragment(): from " + mLastFragment + " to " + fragment);
        } else {
            Log.v(TAG, "showFragment(): showing " + fragment + " again");
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.kitchen_content, fragment)
                .commit();
        mLastFragment = fragment;
    }

    private void initManagers(Car car) {
        synchronized (mPropertyManagerReady) {
            mHvacManager = (CarHvacManager) car.getCarManager(
                    android.car.Car.HVAC_SERVICE);
            mOccupantZoneManager = (CarOccupantZoneManager) car.getCarManager(
                    android.car.Car.CAR_OCCUPANT_ZONE_SERVICE);
            mPowerManager = (CarPowerManager) car.getCarManager(
                    android.car.Car.POWER_SERVICE);
            mPropertyManager = (CarPropertyManager) car.getCarManager(
                    android.car.Car.PROPERTY_SERVICE);
            mSensorManager = (CarSensorManager) car.getCarManager(
                    android.car.Car.SENSOR_SERVICE);
            mCarProjectionManager =
                    (CarProjectionManager) car.getCarManager(Car.PROJECTION_SERVICE);
            mCarTelemetryManager =
                    (CarTelemetryManager) car.getCarManager(Car.CAR_TELEMETRY_SERVICE);
            mCarWatchdogManager =
                    (CarWatchdogManager) car.getCarManager(Car.CAR_WATCHDOG_SERVICE);
            mCarPerformanceManager =
                    (CarPerformanceManager) car.getCarManager(Car.CAR_PERFORMANCE_SERVICE);
            mPropertyManagerReady.notifyAll();
        }
    }

    private void updateHeaderInfoVisibility() {
        mShowHeaderInfo = getBooleanProperty(PROPERTY_SHOW_HEADER_INFO, false);
        Log.i(TAG, "updateHeaderInfoVisibility(): showHeaderInfo=" + mShowHeaderInfo);
        int visibility = mShowHeaderInfo ? View.VISIBLE : View.GONE;
        mUserIdView.setVisibility(visibility);
        mDisplayIdView.setVisibility(visibility);
    }

    private void updateHeaderInfoVisibility(PrintWriter writer) {
        boolean before = mShowHeaderInfo;
        updateHeaderInfoVisibility();
        boolean after = mShowHeaderInfo;
        writer.printf("Updated header info visibility from %b to %b\n", before, after);
    }

    private final class MenuAdapter extends RecyclerView.Adapter<ItemViewHolder> {

        private final LayoutInflater mLayoutInflator;

        MenuAdapter(Context context) {
            mLayoutInflator = LayoutInflater.from(context);
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflator.inflate(R.layout.menu_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            holder.mTitle.setText(mMenuEntries.get(position).getText());
            holder.mTitle.setOnClickListener(v -> mMenuEntries.get(position).onClick());
        }

        @Override
        public int getItemCount() {
            return mMenuEntries.size();
        }
    }

    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView mTitle;

        ItemViewHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.title);
        }
    }

    // Use AsyncTask to refresh Car*Manager after car service connected
    public void requestRefreshManager(final Runnable r, final Handler h) {
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                synchronized (mPropertyManagerReady) {
                    while (!mCarApi.isConnected()) {
                        try {
                            mPropertyManagerReady.wait();
                        } catch (InterruptedException e) {
                            return null;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                h.post(r);
            }
        };
        task.execute();
    }

    private static boolean getBooleanProperty(String prop, boolean defaultValue) {
        String value = SystemProperties.get(prop);
        Log.v(TAG, "getBooleanProperty(" + prop + "): got '" + value + "'");
        if (!TextUtils.isEmpty(value)) {
            boolean finalValue = Boolean.valueOf(value);
            Log.v(TAG, "returning " + finalValue);
            return finalValue;
        }
        String persistProp = "persist." + prop;
        boolean finalValue = SystemProperties.getBoolean(persistProp, defaultValue);
        Log.v(TAG, "getBooleanProperty(" + persistProp + "): returning " + finalValue);
        return finalValue;
    }
}
