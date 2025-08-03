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
package android.car.cluster;

import static android.content.Intent.ACTION_USER_UNLOCKED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Display.INVALID_DISPLAY;

import static java.lang.Integer.parseInt;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.car.cluster.renderer.NavigationRenderer;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.android.car.internal.common.UserHelperLite;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of {@link InstrumentClusterRenderingService} which renders an activity on a
 * virtual display that is transmitted to an external screen.
 */
public class ClusterRenderingService extends InstrumentClusterRenderingService implements
        ImageResolver.BitmapFetcher, CarAppFocusManager.OnAppFocusChangedListener {
    private static final String TAG = "Cluster.Service";

    static final String LOCAL_BINDING_ACTION = "local";
    static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";

    private List<ServiceClient> mClients = new ArrayList<>();
    private ClusterDisplayProvider mDisplayProvider;

    private int mClusterDisplayId = INVALID_DISPLAY;

    private boolean mInstrumentClusterHelperReady;

    private final IBinder mLocalBinder = new LocalBinder();
    private final ImageResolver mImageResolver = new ImageResolver(this);
    private final Handler mHandler = new Handler();
    private final Runnable mLaunchMainActivity = this::launchMainActivity;
    private ComponentName mNavigationClusterActivity = null;
    private int mNavigationClusterUserId = UserHandle.USER_SYSTEM;
    private CarAppFocusManager mAppFocusManager = null;

    private final UserReceiver mUserReceiver = new UserReceiver();

    public interface ServiceClient {
        void onKeyEvent(KeyEvent keyEvent);

        void onNavigationStateChange(NavigationStateProto navState);
    }

    public class LocalBinder extends Binder {
        ClusterRenderingService getService() {
            return ClusterRenderingService.this;
        }
    }

    private final DisplayListener mDisplayListener = new DisplayListener() {
        // Called in the main thread, since ClusterDisplayProvider.DisplayListener was registered
        // with null handler.
        @Override
        public void onDisplayAdded(int displayId) {
            Log.i(TAG, "Cluster display found, displayId: " + displayId);
            mClusterDisplayId = displayId;
            if (mInstrumentClusterHelperReady) {
                mHandler.post(mLaunchMainActivity);
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            Log.w(TAG, "Cluster display has been removed");
        }

        @Override
        public void onDisplayChanged(int displayId) {

        }
    };

    public void setActivityLaunchOptions(int displayId, ClusterActivityState state) {
        ActivityOptions options = displayId != INVALID_DISPLAY
                ? ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                : null;
        setClusterActivityLaunchOptions(options);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("activity options set: %s (displayeId: %d)",
                    options, options != null ? options.getLaunchDisplayId() : -1));
        }
        setClusterActivityState(state);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("activity state set: %s", state));
        }
    }

    public void registerClient(ServiceClient client) {
        mClients.add(client);
    }

    public void unregisterClient(ServiceClient client) {
        mClients.remove(client);
    }

    public ImageResolver getImageResolver() {
        return mImageResolver;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind, intent: " + intent);
        if (LOCAL_BINDING_ACTION.equals(intent.getAction())) {
            return mLocalBinder;
        }
        IBinder binder = super.onBind(intent);
        mInstrumentClusterHelperReady = true;
        if (mClusterDisplayId != INVALID_DISPLAY) {
            mHandler.post(mLaunchMainActivity);
        }
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        // The following will never be null, as this service is initiated by CarService itself.
        Car car = Car.createCar(this);
        mAppFocusManager = (CarAppFocusManager) car.getCarManager(Car.APP_FOCUS_SERVICE);
        mAppFocusManager.addFocusListener(this, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mDisplayProvider = new ClusterDisplayProvider(this, mDisplayListener);
        mUserReceiver.register(this);
        mNavigationClusterActivity = getNavigationClusterActivity();
        Log.i(TAG, "onCreate: set cluster to " + mNavigationClusterActivity);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAppFocusManager.removeFocusListener(this, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        mUserReceiver.unregister(this);
        mDisplayProvider.release();
    }

    @Override
    public void onAppFocusChanged(int appType, boolean active) {
        boolean useNavigationOnly = getResources().getBoolean(R.bool.navigationOnly);
        Log.i(TAG, "onAppFocusChanged: " + appType + ", active: " + active);
        if (useNavigationOnly) {
            launchMainActivity();
        } else {
            // TODO(b/193931272): Update MainClusterActivity
        }
    }

    private void launchMainActivity() {
        mHandler.removeCallbacks(mLaunchMainActivity);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(mClusterDisplayId);
        boolean useNavigationOnly = getResources().getBoolean(R.bool.navigationOnly);
        Intent intent;
        int userId = UserHandle.USER_SYSTEM;
        if (useNavigationOnly) {
            userId = ActivityManager.getCurrentUser();
            if (UserHelperLite.isHeadlessSystemUser(userId)) {
                Log.i(TAG, "Skipping the navigation activity for User 0");
                return;
            }
            ComponentName newClusterActivity = getNavigationClusterActivity();
            if (Objects.equals(newClusterActivity, mNavigationClusterActivity)
                    && userId == mNavigationClusterUserId) {
                Log.i(TAG, "Cluster activity hasn't changed. Skipping.");
                return;
            }
            Log.i(TAG, "Set cluster to " + newClusterActivity);
            onNavigationComponentChanged(newClusterActivity);
            mNavigationClusterActivity = newClusterActivity;
            mNavigationClusterUserId = userId;
            intent = getNavigationActivityIntent(mNavigationClusterActivity, mClusterDisplayId);
            startFixedActivityModeForDisplayAndUser(intent, options, userId);
        } else {
            intent = getMainClusterActivityIntent();
            startActivityAsUser(intent, options.toBundle(), UserHandle.SYSTEM);
        }
        Log.i(TAG, "launching main activity=" + intent + ", display=" + mClusterDisplayId
                + ", userId=" + userId);
    }

    /**
     * Invoked when the activity to show in the cluster changes
     *
     * @param clusterActivity current activity displayed in cluster. If no application is holding
     *                        {@link CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION}, this will be the
     *                        default map cluster activity. Otherwise, this will be the cluster
     *                        activity of the focused application (if it has one) or {@code null} if
     *                        the application doesn't have a cluster activity or the activity is
     *                        disabled.
     */
    protected void onNavigationComponentChanged(@Nullable ComponentName clusterActivity) {
        // This method can be used by OEMs to send a signal to the cluster hardware indicating
        // whether Android has or doesn't have a cluster activity.
        //
        // OEMs can use this signal to let the cluster show some other view, or to hide Android's
        // video feed altogether.
    }

    private Intent getMainClusterActivityIntent() {
        return new Intent(this, MainClusterActivity.class).setFlags(FLAG_ACTIVITY_NEW_TASK);
    }

    private ComponentName getNavigationClusterActivity() {
        List<String> focusOwnerPackageNames = mAppFocusManager.getAppTypeOwner(
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);

        if (focusOwnerPackageNames == null || focusOwnerPackageNames.isEmpty()) {
            // No application has focus. We use the default navigation app.
            Log.i(TAG, "getNavigationClusterActivity(): no focus owner -> "
                    + "using default nav app");
            ActivityInfo activityInfo = MainClusterActivity.getNavigationActivity(this);
            return new ComponentName(activityInfo.packageName, activityInfo.name);
        } else {
            ComponentName clusterActivity = getComponentFromPackages(focusOwnerPackageNames);
            if (clusterActivity == null) {
                // If currently focused app has no cluster activity, we indicate so.
                Log.i(TAG, "getNavigationClusterActivity(): focus owned by "
                        + focusOwnerPackageNames + " but it has no cluster activity -> "
                        + "using empty activity");
                return null;
            }
            // Otherwise, we use the activity of the currently focused app
            Log.i(TAG, "getNavigationClusterActivity(): focus owned and it has a cluster "
                    + "activity -> using " + focusOwnerPackageNames + " app");
            return clusterActivity;
        }
    }

    private ComponentName getComponentFromPackages(List<String> packageNames) {
        for (String packageName : packageNames) {
            ComponentName result = getComponentFromPackage(packageName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Intent getNavigationActivityIntent(ComponentName component, int displayId) {
        if (component == null) {
            Log.i(TAG, "Focused application doesn't have a cluster activity. Using fallback.");
            component = new ComponentName(this, EmptyNavigationActivity.class);
        }
        Rect displaySize = new Rect(0, 0, 240, 320);  // Arbitrary size, better than nothing.
        DisplayManager dm = getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(displayId);
        if (display != null) {
            display.getRectSize(displaySize);
        }
        setClusterActivityState(ClusterActivityState.create(/* visible= */ true,
                    /* unobscuredBounds= */ new Rect(0, 0, 240, 320)));
        return new Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE,
                ClusterActivityState.create(/* visible= */ true,
                    /* unobscuredBounds= */ displaySize).toBundle());
    }

    @Override
    public void onKeyEvent(KeyEvent keyEvent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onKeyEvent, keyEvent: " + keyEvent);
        }
        broadcastClientEvent(client -> client.onKeyEvent(keyEvent));
    }

    /**
     * Broadcasts an event to all the registered service clients
     *
     * @param event event to broadcast
     */
    private void broadcastClientEvent(Consumer<ServiceClient> event) {
        for (ServiceClient client : mClients) {
            event.accept(client);
        }
    }

    @Override
    public NavigationRenderer getNavigationRenderer() {
        NavigationRenderer navigationRenderer = new NavigationRenderer() {
            @Override
            public CarNavigationInstrumentCluster getNavigationProperties() {
                CarNavigationInstrumentCluster config =
                        CarNavigationInstrumentCluster.createCluster(1000);
                Log.d(TAG, "getNavigationProperties, returns: " + config);
                return config;
            }

            @Override
            public void onNavigationStateChanged(Bundle bundle) {
                StringBuilder bundleSummary = new StringBuilder();

                // Attempt to read proto byte array
                byte[] protoBytes = bundle.getByteArray(NAV_STATE_PROTO_BUNDLE_KEY);
                if (protoBytes != null) {
                    try {
                        NavigationStateProto navState = NavigationStateProto.parseFrom(
                                protoBytes);
                        bundleSummary.append(navState.toString());

                        // Update clients
                        broadcastClientEvent(
                                client -> client.onNavigationStateChange(navState));
                    } catch (InvalidProtocolBufferException e) {
                        Log.e(TAG, "Error parsing navigation state proto", e);
                    }
                } else {
                    Log.e(TAG, "Received nav state byte array is null");
                }
                Log.d(TAG, "onNavigationStateChanged(" + bundleSummary + ")");
            }
        };

        Log.i(TAG, "createNavigationRenderer, returns: " + navigationRenderer);
        return navigationRenderer;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length > 0) {
            execShellCommand(args);
        } else {
            super.dump(fd, writer, args);
            writer.println("DisplayProvider: " + mDisplayProvider);
        }
    }

    private void emulateKeyEvent(int keyCode) {
        Log.i(TAG, "emulateKeyEvent, keyCode: " + keyCode);
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_DOWN);
        onKeyEvent(event);

        eventTime = SystemClock.uptimeMillis();
        event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_UP);
        onKeyEvent(event);
    }

    private KeyEvent obtainKeyEvent(int keyCode, long downTime, long eventTime, int action) {
        int scanCode = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scanCode = 108;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            scanCode = 106;
        }
        return KeyEvent.obtain(
                downTime,
                eventTime,
                action,
                keyCode,
                0 /* repeat */,
                0 /* meta state */,
                0 /* deviceId*/,
                scanCode /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM /* flags */,
                InputDevice.SOURCE_KEYBOARD,
                null /* characters */);
    }

    private void execShellCommand(String[] args) {
        Log.i(TAG, "execShellCommand, args: " + Arrays.toString(args));

        String command = args[0];

        switch (command) {
            case "injectKey": {
                if (args.length > 1) {
                    emulateKeyEvent(parseInt(args[1]));
                } else {
                    Log.i(TAG, "Not enough arguments");
                }
                break;
            }
            case "destroyOverlayDisplay": {
                Settings.Global.putString(getContentResolver(),
                        Global.OVERLAY_DISPLAY_DEVICES, "");
                break;
            }

            case "createOverlayDisplay": {
                if (args.length > 1) {
                    Settings.Global.putString(getContentResolver(),
                            Global.OVERLAY_DISPLAY_DEVICES, args[1]);
                } else {
                    Log.i(TAG, "Not enough arguments, expected 2");
                }
                break;
            }

            case "setUnobscuredArea": {
                if (args.length > 5) {
                    setClusterActivityState(ClusterActivityState.create(true,
                            new Rect(parseInt(args[2]), parseInt(args[3]),
                                    parseInt(args[4]), parseInt(args[5]))));
                } else {
                    Log.i(TAG, "wrong format, expected: category left top right bottom");
                }
            }
        }
    }

    private class UserReceiver extends BroadcastReceiver {
        void register(Context context) {
            IntentFilter intentFilter = new IntentFilter(ACTION_USER_UNLOCKED);
            context.registerReceiverAsUser(this, UserHandle.ALL, intentFilter, null, null);
        }

        void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Broadcast received: " + intent);
            }
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
            if (userId == ActivityManager.getCurrentUser() &&
                mInstrumentClusterHelperReady && mClusterDisplayId != INVALID_DISPLAY) {
                mHandler.post(mLaunchMainActivity);
            }
        }
    }
}
