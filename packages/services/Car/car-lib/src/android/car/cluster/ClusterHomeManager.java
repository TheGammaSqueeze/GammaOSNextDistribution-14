/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.car.feature.Flags.FLAG_CLUSTER_HEALTH_MONITORING;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.Activity;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.SurfaceControlHelper;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Provides the api to manage {@code ClusterHome}.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLUSTER_HEALTH_MONITORING)
@SystemApi
public final class ClusterHomeManager extends CarManagerBase {
    private static final String TAG = ClusterHomeManager.class.getSimpleName();
    /**
     * When the client reports ClusterHome state and if there is no UI in the sub area, it can
     * reports UI_TYPE_CLUSTER_NONE instead.
     *
     * @hide
     */
    public static final int UI_TYPE_CLUSTER_NONE = -1;
    /** @hide */
    public static final int UI_TYPE_CLUSTER_HOME = 0;

    /** @hide */
    @IntDef(flag = true, prefix = { "CONFIG_" }, value = {
            CONFIG_DISPLAY_ON_OFF,
            CONFIG_DISPLAY_BOUNDS,
            CONFIG_DISPLAY_INSETS,
            CONFIG_UI_TYPE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Config {}

    /** Bit fields indicates which fields of {@link ClusterState} are changed */
    /** @hide */
    public static final int CONFIG_DISPLAY_ON_OFF = 0x01;
    /** @hide */
    public static final int CONFIG_DISPLAY_BOUNDS = 0x02;
    /** @hide */
    public static final int CONFIG_DISPLAY_INSETS = 0x04;
    /** @hide */
    public static final int CONFIG_UI_TYPE = 0x08;
    /** @hide */
    public static final int CONFIG_DISPLAY_ID = 0x10;

    /**
     * Callback for ClusterHome to get notifications when cluster state changes.
     *
     * @hide
     */
    public interface ClusterStateListener {
        /**
         * Called when ClusterOS changes the cluster display state, the geometry of cluster display,
         * or the uiType.
         * @param state newly updated {@link ClusterState}
         * @param changes the flag indicates which fields are updated
         */
        void onClusterStateChanged(ClusterState state, @Config int changes);
    }

    /**
     * Callback for ClusterHome to get notifications when cluster navigation state changes.
     */
    @FlaggedApi(FLAG_CLUSTER_HEALTH_MONITORING)
    public interface ClusterNavigationStateListener {
        /**
         * Called when the app who owns the navigation focus casts the new navigation state.
         *
         * @param navigationState Byte array that is serialized from a {@link
         *        android.car.cluster.navigation.NavigationState.NavigationStateProto} proto value.
         */
        void onNavigationStateChanged(@NonNull byte[] navigationState);
    }

    private static class ClusterStateListenerRecord {
        final Executor mExecutor;
        final ClusterStateListener mListener;
        ClusterStateListenerRecord(Executor executor, ClusterStateListener listener) {
            mExecutor = executor;
            mListener = listener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClusterStateListenerRecord)) {
                return false;
            }
            return mListener == ((ClusterStateListenerRecord) obj).mListener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public int hashCode() {
            return mListener.hashCode();
        }
    }

    private static class ClusterNavigationStateListenerRecord {
        final Executor mExecutor;
        final ClusterNavigationStateListener mListener;

        ClusterNavigationStateListenerRecord(Executor executor,
                ClusterNavigationStateListener listener) {
            mExecutor = executor;
            mListener = listener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClusterNavigationStateListenerRecord)) {
                return false;
            }
            return mListener == ((ClusterNavigationStateListenerRecord) obj).mListener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public int hashCode() {
            return mListener.hashCode();
        }
    }

    private final IClusterHomeService mService;
    private final IClusterStateListenerImpl mClusterStateListenerBinderCallback;
    private final IClusterNavigationStateListenerImpl mClusterNavigationStateListenerBinderCallback;
    private final CopyOnWriteArrayList<ClusterStateListenerRecord> mStateListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ClusterNavigationStateListenerRecord>
            mNavigationStateListeners = new CopyOnWriteArrayList<>();

    private boolean mVisibilityMonitoringStarted = false;

    /** @hide */
    @VisibleForTesting
    public ClusterHomeManager(Car car, IBinder service) {
        super(car);
        mService = IClusterHomeService.Stub.asInterface(service);
        mClusterStateListenerBinderCallback = new IClusterStateListenerImpl(this);
        mClusterNavigationStateListenerBinderCallback =
                new IClusterNavigationStateListenerImpl(this);
    }

    /**
     * Registers the callback for ClusterHome.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void registerClusterStateListener(
            @NonNull Executor executor, @NonNull ClusterStateListener callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        ClusterStateListenerRecord clusterStateListenerRecord =
                new ClusterStateListenerRecord(executor, callback);
        if (!mStateListeners.addIfAbsent(clusterStateListenerRecord)) {
            return;
        }
        if (mStateListeners.size() == 1) {
            try {
                mService.registerClusterStateListener(mClusterStateListenerBinderCallback);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Registers a listener for navigation state changes.
     *
     * <p>Note that multiple listeners can be registered. All registered listeners are invoked
     * when the navigation app that has the focus sends a state change.
     * <p>A listener is invoked only for changes that occur after the registration. It is not
     * called for the previous or current states at the time of the registration.
     */
    @RequiresPermission(Car.PERMISSION_CAR_MONITOR_CLUSTER_NAVIGATION_STATE)
    public void registerClusterNavigationStateListener(
            @NonNull Executor executor, @NonNull ClusterNavigationStateListener callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        ClusterNavigationStateListenerRecord clusterStateListenerRecord =
                new ClusterNavigationStateListenerRecord(executor, callback);
        if (!mNavigationStateListeners.addIfAbsent(clusterStateListenerRecord)) {
            return;
        }
        if (mNavigationStateListeners.size() == 1) {
            try {
                mService.registerClusterNavigationStateListener(
                        mClusterNavigationStateListenerBinderCallback);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Unregisters the callback.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void unregisterClusterStateListener(@NonNull ClusterStateListener callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        if (!mStateListeners
                .remove(new ClusterStateListenerRecord(/* executor= */ null, callback))) {
            return;
        }
        if (mStateListeners.isEmpty()) {
            try {
                mService.unregisterClusterStateListener(mClusterStateListenerBinderCallback);
            } catch (RemoteException ignored) {
                // ignore for unregistering
            }
        }
    }

    /**
     * Unregisters a listener for navigation state changes.
     */
    @RequiresPermission(Car.PERMISSION_CAR_MONITOR_CLUSTER_NAVIGATION_STATE)
    public void unregisterClusterNavigationStateListener(
            @NonNull ClusterNavigationStateListener callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        if (!mNavigationStateListeners.remove(new ClusterNavigationStateListenerRecord(
                /* executor= */ null, callback))) {
            return;
        }
        if (mNavigationStateListeners.isEmpty()) {
            try {
                mService.unregisterClusterNavigationStateListener(
                        mClusterNavigationStateListenerBinderCallback);
            } catch (RemoteException ignored) {
                // ignore for unregistering
            }
        }
    }

    private static class IClusterStateListenerImpl extends IClusterStateListener.Stub {
        private final WeakReference<ClusterHomeManager> mManager;

        private IClusterStateListenerImpl(ClusterHomeManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onClusterStateChanged(@NonNull ClusterState state, @Config int changes) {
            ClusterHomeManager manager = mManager.get();
            if (manager != null) {
                for (ClusterStateListenerRecord cb : manager.mStateListeners) {
                    cb.mExecutor.execute(
                            () -> cb.mListener.onClusterStateChanged(state, changes));
                }
            }
        }
    }

    private static class IClusterNavigationStateListenerImpl extends
            IClusterNavigationStateListener.Stub {
        private final WeakReference<ClusterHomeManager> mManager;

        private IClusterNavigationStateListenerImpl(ClusterHomeManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onNavigationStateChanged(@NonNull byte[] navigationState) {
            ClusterHomeManager manager = mManager.get();
            if (manager != null) {
                for (ClusterNavigationStateListenerRecord lr : manager.mNavigationStateListeners) {
                    lr.mExecutor.execute(
                            () -> lr.mListener.onNavigationStateChanged(navigationState));
                }
            }
        }
    }

    /**
     * Reports the current ClusterUI state.
     * @param uiTypeMain uiType that ClusterHome tries to show in main area
     * @param uiTypeSub uiType that ClusterHome tries to show in sub area
     * @param uiAvailability the byte array to represent the availability of ClusterUI.
     *    0 indicates non-available and 1 indicates available.
     *    Index 0 is reserved for ClusterHome, The other indexes are followed by OEM's definition.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void reportState(int uiTypeMain, int uiTypeSub, @NonNull byte[] uiAvailability) {
        try {
            mService.reportState(uiTypeMain, uiTypeSub, uiAvailability);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Requests to turn the cluster display on to show some ClusterUI.
     * @param uiType uiType that ClusterHome tries to show in main area
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void requestDisplay(int uiType) {
        try {
            mService.requestDisplay(uiType);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns the current {@code ClusterState}.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @Nullable
    public ClusterState getClusterState() {
        ClusterState state = null;
        try {
            state = mService.getClusterState();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return state;
    }

    /**
     * Start an activity as specified user. The activity is considered as in fixed mode for
     * the cluster display and will be re-launched if the activity crashes, the package
     * is updated or goes to background for whatever reason.
     * Only one activity can exist in fixed mode for the display and calling this multiple
     * times with different {@code Intent} will lead into making all previous activities into
     * non-fixed normal state (= will not be re-launched.)
     * @param intent the Intent to start
     * @param options additional options for how the Activity should be started
     * @param userId the user the new activity should run as
     * @return true if it launches the given Intent as FixedActivity successfully
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public boolean startFixedActivityModeAsUser(
            Intent intent, @Nullable Bundle options, int userId) {
        try {
            return mService.startFixedActivityModeAsUser(intent, options, userId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return false;
    }

    /**
     * The activity launched on the cluster display is no longer in fixed mode. Re-launching or
     * finishing should not trigger re-launching any more. Note that Activity for non-current user
     * will be auto-stopped and there is no need to call this for user switching. Note that this
     * does not stop the activity but it will not be re-launched any more.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void stopFixedActivityMode() {
        try {
            mService.stopFixedActivityMode();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Sends a heartbeat to ClusterOS.
     * @param epochTimeNs the current time
     * @param appMetadata the application specific metadata which will be delivered with
     *                    the heartbeat.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void sendHeartbeat(long epochTimeNs, @Nullable byte[] appMetadata) {
        try {
            mService.sendHeartbeat(epochTimeNs, appMetadata);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Starts the visibility monitoring of given {@link Activity}.
     *
     * Note: This is supposed to be called in {@link Activity#onStart()} generally.
     *
     * @param activity               the {@link Activity} to track the visibility of its Window
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    public void startVisibilityMonitoring(@NonNull Activity activity) {
        // We'd like to check the permission locally too, since the actual execution happens later.
        if (getContext().checkCallingOrSelfPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
                != PERMISSION_GRANTED) {
            throw new SecurityException(
                    "requires permission " + Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
        }
        if (mVisibilityMonitoringStarted) {
            Slogf.w(TAG, "startVisibilityMonitoring is already started");
            return;
        }
        if (SurfaceControlHelper.getSurfaceControl(activity) != null) {
            throw new IllegalStateException(
                    "startVisibilityMonitoring is expected to be called before onAttachedToWindow");
        }
        mVisibilityMonitoringStarted = true;
        ViewTreeObserver observer = getViewTreeObserver(activity);
        // Can't use onWindowAttached, because SurfaceControl is available at the time, but invalid.
        // TODO: b/286406553 - Move the callback below to onWindowAttached.
        observer.addOnPreDrawListener(
                new OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // The existing 'observer' would be invalid, so gets it again.
                        ViewTreeObserver observer = getViewTreeObserver(activity);
                        observer.removeOnPreDrawListener(this);
                        startVisibilityMonitoringInternal(activity);
                        return true;
                    }
                });
        observer.addOnWindowAttachListener(
                new ViewTreeObserver.OnWindowAttachListener() {
                    @Override
                    public void onWindowAttached() {
                        // Using onPreDraw instead, check b/286406553.
                    }

                    @Override
                    public void onWindowDetached() {
                        ViewTreeObserver observer = getViewTreeObserver(activity);
                        observer.removeOnWindowAttachListener(this);
                        stopVisibilityMonitoringInternal();
                    }
                }
        );
    }

    private static ViewTreeObserver getViewTreeObserver(@NonNull Activity activity) {
        return activity.getWindow().getDecorView().getViewTreeObserver();
    }

    private void startVisibilityMonitoringInternal(Activity activity) {
        SurfaceControl surfaceControl = SurfaceControlHelper.getSurfaceControl(activity);
        try {
            mService.startVisibilityMonitoring(surfaceControl);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to startVisibilityMonitoring", e);
        }
    }

    private void stopVisibilityMonitoringInternal() {
        try {
            mService.stopVisibilityMonitoring();
            mVisibilityMonitoringStarted = false;
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to stopVisibilityMonitoring", e);
        }
    }

    /** @hide */
    @Override
    protected void onCarDisconnected() {
        mStateListeners.clear();
        mNavigationStateListeners.clear();
    }
}
