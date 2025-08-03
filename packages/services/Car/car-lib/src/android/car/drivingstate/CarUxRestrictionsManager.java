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

package android.car.drivingstate;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.builtin.content.ContextHelper;
import android.car.builtin.os.UserManagerHelper;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import com.android.internal.annotations.GuardedBy;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * API to register and get the User Experience restrictions imposed based on the car's driving
 * state.
 */
public final class CarUxRestrictionsManager extends CarManagerBase {
    private static final String TAG = "CarUxRManager";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;
    private static final int MSG_HANDLE_UX_RESTRICTIONS_CHANGE = 0;

    /**
     * Baseline restriction mode is the default UX restrictions used for driving state.
     *
     * @hide
     */
    @SystemApi
    public static final String UX_RESTRICTION_MODE_BASELINE = "baseline";

    private final Object mLock = new Object();

    private int mDisplayId = Display.INVALID_DISPLAY;
    private final ICarUxRestrictionsManager mUxRService;
    private final EventCallbackHandler mEventCallbackHandler;
    private final UserManager mUserManager;
    @GuardedBy("mLock")
    private OnUxRestrictionsChangedListener mUxRListener;
    @GuardedBy("mLock")
    private CarUxRestrictionsChangeListenerToService mListenerToService;

    /** @hide */
    public CarUxRestrictionsManager(@NonNull Car car, @NonNull IBinder service) {
        super(car);
        mUxRService = ICarUxRestrictionsManager.Stub.asInterface(service);
        mEventCallbackHandler = new EventCallbackHandler(this,
                getEventHandler().getLooper());
        mUserManager = getContext().getSystemService(UserManager.class);
    }

    /** @hide */
    @Override
    @SystemApi
    public void onCarDisconnected() {
        synchronized (mLock) {
            mListenerToService = null;
            mUxRListener = null;
        }
    }

    /**
     * Listener Interface for clients to implement to get updated on driving state related
     * changes.
     */
    public interface OnUxRestrictionsChangedListener {
        /**
         * Called when the UX restrictions due to a car's driving state changes.
         *
         * @param restrictionInfo The new UX restriction information
         */
        void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo);
    }

    /**
     * Registers a {@link OnUxRestrictionsChangedListener} for listening to changes in the
     * UX Restrictions to adhere to.
     * <p>
     * If a listener has already been registered, it has to be unregistered before registering
     * the new one.
     *
     * @param listener {@link OnUxRestrictionsChangedListener}
     */
    public void registerListener(@NonNull OnUxRestrictionsChangedListener listener) {
        registerListener(listener, getDisplayId());
    }

    /**
     * @hide
     */
    @Deprecated
    public void registerListener(@NonNull OnUxRestrictionsChangedListener listener, int displayId) {
        setListener(displayId, listener);
    }
    /**
     * @hide
     */
    @SystemApi
    public void setListener(int displayId, @NonNull OnUxRestrictionsChangedListener listener) {
        CarUxRestrictionsChangeListenerToService serviceListener;
        synchronized (mLock) {
            // Check if the listener has been already registered.
            if (mUxRListener != null) {
                if (DBG) {
                    Log.d(TAG, "Listener already registered listener");
                }
                return;
            }
            mUxRListener = listener;
            if (mListenerToService == null) {
                mListenerToService = new CarUxRestrictionsChangeListenerToService(this);
            }
            serviceListener = mListenerToService;
        }

        try {
            // register to the Service to listen for changes.
            mUxRService.registerUxRestrictionsChangeListener(serviceListener, displayId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Unregisters the registered {@link OnUxRestrictionsChangedListener}
     */
    public void unregisterListener() {
        CarUxRestrictionsChangeListenerToService serviceListener;
        synchronized (mLock) {
            if (mUxRListener == null) {
                if (DBG) {
                    Log.d(TAG, "Listener was not previously registered");
                }
                return;
            }
            mUxRListener = null;
            serviceListener = mListenerToService;
        }
        try {
            if (serviceListener != null) {
                mUxRService.unregisterUxRestrictionsChangeListener(serviceListener);
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Sets new {@link CarUxRestrictionsConfiguration}s for next trip.
     * <p>
     * Saving new configurations does not affect current configuration. The new configuration will
     * only be used after UX Restrictions service restarts when the vehicle is parked.
     * <p>
     * Input configurations must be one-to-one mapped to displays, namely each display must have
     * exactly one configuration.
     * See {@link CarUxRestrictionsConfiguration.Builder#setDisplayAddress(DisplayAddress)}.
     *
     * @param configs Map of display Id to UX restrictions configurations to be persisted.
     * @return {@code true} if input config was successfully saved; {@code false} otherwise.
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @SystemApi
    public boolean saveUxRestrictionsConfigurationForNextBoot(
            @NonNull List<CarUxRestrictionsConfiguration> configs) {
        try {
            return mUxRService.saveUxRestrictionsConfigurationForNextBoot(configs);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Gets the current UX restrictions ({@link CarUxRestrictions}) in place.
     *
     * @return current UX restrictions that is in effect.
     */
    @Nullable
    public CarUxRestrictions getCurrentCarUxRestrictions() {
        return getCurrentCarUxRestrictions(getDisplayId());
    }

    /**
     * @hide
     */
    @Nullable
    @SystemApi
    public CarUxRestrictions getCurrentCarUxRestrictions(int displayId) {
        try {
            return mUxRService.getCurrentUxRestrictions(displayId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Sets restriction mode. Returns {@code true} if the operation succeeds.
     *
     * <p>The default mode is {@link #UX_RESTRICTION_MODE_BASELINE}.
     *
     * <p>If a new {@link CarUxRestrictions} is available upon mode transition, it'll
     * be immediately dispatched to listeners.
     *
     * <p>If the given mode is not configured for current driving state, it
     * will fall back to the default value.
     *
     * <p>If a configuration was set for a passenger mode before an upgrade to Android R, that
     * passenger configuration is now called "passenger".
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @SystemApi
    public boolean setRestrictionMode(@NonNull String mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        try {
            return mUxRService.setRestrictionMode(mode);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Returns the current restriction mode.
     *
     * <p>The default mode is {@link #UX_RESTRICTION_MODE_BASELINE}.
     *
     * <p>If a configuration was set for a passenger mode before an upgrade to Android R, that
     * passenger configuration is now called "passenger".
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @NonNull
    @SystemApi
    public String getRestrictionMode() {
        try {
            return mUxRService.getRestrictionMode();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Sets a new {@link CarUxRestrictionsConfiguration} for next trip.
     * <p>
     * Saving a new configuration does not affect current configuration. The new configuration will
     * only be used after UX Restrictions service restarts when the vehicle is parked.
     *
     * @param config UX restrictions configuration to be persisted.
     * @return {@code true} if input config was successfully saved; {@code false} otherwise.
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @SystemApi
    public boolean saveUxRestrictionsConfigurationForNextBoot(
            @NonNull CarUxRestrictionsConfiguration config) {
        return saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(config));
    }

    /**
     * Gets the staged configurations.
     * <p>
     * Configurations set by {@link #saveUxRestrictionsConfigurationForNextBoot(List)} do not
     * immediately affect current drive. Instead, they are staged to take effect when car service
     * boots up the next time.
     * <p>
     * This methods is only for test purpose, please do not use in production.
     *
     * @return current staged configuration, {@code null} if it's not available
     * @hide
     */
    @Nullable
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @SystemApi
    public List<CarUxRestrictionsConfiguration> getStagedConfigs() {
        try {
            return mUxRService.getStagedConfigs();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Gets the current configurations.
     *
     * @return current configurations that is in effect.
     * @hide
     */
    @Nullable
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @SystemApi
    public List<CarUxRestrictionsConfiguration> getConfigs() {
        try {
            return mUxRService.getConfigs();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Class that implements the listener interface and gets called back from the
     * {@link com.android.car.CarDrivingStateService} across the binder interface.
     */
    private static class CarUxRestrictionsChangeListenerToService extends
            ICarUxRestrictionsChangeListener.Stub {
        private final WeakReference<CarUxRestrictionsManager> mUxRestrictionsManager;

        CarUxRestrictionsChangeListenerToService(CarUxRestrictionsManager manager) {
            mUxRestrictionsManager = new WeakReference<>(manager);
        }

        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
            CarUxRestrictionsManager manager = mUxRestrictionsManager.get();
            if (manager != null) {
                manager.handleUxRestrictionsChanged(restrictionInfo);
            }
        }
    }

    /**
     * Gets the {@link CarUxRestrictions} from the service listener
     * {@link CarUxRestrictionsChangeListenerToService} and dispatches it to a handler provided
     * to the manager.
     *
     * @param restrictionInfo {@link CarUxRestrictions} that has been registered to listen on
     */
    private void handleUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        // send a message to the handler
        mEventCallbackHandler.sendMessage(mEventCallbackHandler.obtainMessage(
                MSG_HANDLE_UX_RESTRICTIONS_CHANGE, restrictionInfo));
    }

    /**
     * Callback Handler to handle dispatching the UX restriction changes to the corresponding
     * listeners.
     */
    private static final class EventCallbackHandler extends Handler {
        private final WeakReference<CarUxRestrictionsManager> mUxRestrictionsManager;

        EventCallbackHandler(CarUxRestrictionsManager manager, Looper looper) {
            super(looper);
            mUxRestrictionsManager = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            CarUxRestrictionsManager mgr = mUxRestrictionsManager.get();
            if (mgr != null) {
                mgr.dispatchUxRChangeToClient((CarUxRestrictions) msg.obj);
            }
        }
    }

    /**
     * Checks for the listeners to list of {@link CarUxRestrictions} and calls them back
     * in the callback handler thread.
     *
     * @param restrictionInfo {@link CarUxRestrictions}
     */
    private void dispatchUxRChangeToClient(CarUxRestrictions restrictionInfo) {
        if (restrictionInfo == null) {
            return;
        }
        OnUxRestrictionsChangedListener listener;
        synchronized (mLock) {
            listener = mUxRListener;
        }
        if (listener != null) {
            listener.onUxRestrictionsChanged(restrictionInfo);
        }
    }

    private int getDisplayId() {
        if (mDisplayId != Display.INVALID_DISPLAY) {
            return mDisplayId;
        }

        // First, attempt to get the id of the display asssociated with the context.
        // For example, if it is an Activity context, a valid display id will already be obtained
        // here. But if it is an Application context, it will return invalid display id.
        mDisplayId = ContextHelper.getAssociatedDisplayId(getContext());
        Log.d(TAG, "Context returns associated display ID " + mDisplayId);

        if (mDisplayId == Display.INVALID_DISPLAY) {
            // If there is no display id associated with the context, further obtain the display
            // id by mapping the user to display id.
            mDisplayId = UserManagerHelper.getMainDisplayIdAssignedToUser(mUserManager);
            Log.d(TAG, "Display ID assigned to user is display " + mDisplayId);
        }

        if (mDisplayId == Display.INVALID_DISPLAY) {
            mDisplayId = Display.DEFAULT_DISPLAY;
            Log.e(TAG, "Could not retrieve display id. Using default: " + mDisplayId);
        }

        return mDisplayId;
    }
}
