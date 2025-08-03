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
package android.car.app;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.car.app.CarTaskViewControllerHostLifecycle.CarTaskViewControllerHostLifecycleObserver;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Executor;

/**
 * This class is responsible to create and manage the {@link CarTaskViewController} instances.
 * - It connects to the {@link android.car.app.CarSystemUIProxy} & listens to the {@link Activity}'s
 * lifecycle.
 * - It is also responsible to dispatch {@link CarTaskViewControllerCallback} methods to the
 * clients.
 */
final class CarTaskViewControllerSupervisor {
    private static final String TAG = CarTaskViewControllerSupervisor.class.getSimpleName();
    private final ArrayMap<CarTaskViewControllerHostLifecycle, ActivityHolder> mActivityHolders =
            new ArrayMap<>();
    private final ICarActivityService mCarActivityService;
    private final Executor mMainExecutor;

    @Nullable private ICarSystemUIProxyCallback mSystemUIProxyCallback = null;
    @Nullable private ICarSystemUIProxy mICarSystemUI = null;

    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            long identity = Binder.clearCallingIdentity();
            try {
                mMainExecutor.execute(() -> onSystemUIProxyDisconnected());
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    };

    private final CarTaskViewControllerHostLifecycleObserver
            mCarTaskViewControllerHostLifecycleObserver =
            new CarTaskViewControllerHostLifecycleObserver() {
                public void onHostAppeared(CarTaskViewControllerHostLifecycle lifecycle) {
                    mActivityHolders.get(lifecycle).maybeShowControlledTasks();
                }

                @Override
                public void onHostDisappeared(CarTaskViewControllerHostLifecycle lifecycle) {
                }

                @Override
                public void onHostDestroyed(CarTaskViewControllerHostLifecycle lifecycle) {
                    lifecycle.unregisterObserver(this);

                    ActivityHolder activityHolder = mActivityHolders.remove(lifecycle);
                    activityHolder.onActivityDestroyed();

                    // When all the underlying activities are destroyed, the callback should be
                    // removed from the CarActivityService as it's no longer required.
                    // A new callback will be registered when a new activity calls the
                    // createTaskViewController.
                    if (mActivityHolders.isEmpty()) {
                        try {
                            mCarActivityService.removeCarSystemUIProxyCallback(
                                    mSystemUIProxyCallback);
                            mSystemUIProxyCallback = null;
                        } catch (RemoteException e) {
                            Slogf.e(TAG, "Failed to remove CarSystemUIProxyCallback", e);
                        }
                    }
                }
            };

    /**
     * @param carActivityService the handle to the {@link com.android.car.am.CarActivityService}.
     */
    CarTaskViewControllerSupervisor(ICarActivityService carActivityService, Executor mainExecutor,
            @NonNull CarUserManager carUserManager) {
        mCarActivityService = carActivityService;
        mMainExecutor = mainExecutor;
        UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED).build();
        carUserManager.addListener(mainExecutor, filter, mUserLifecycleListener);
    }

    private static IBinder getToken(Activity activity) {
        return ActivityManagerHelper.getActivityToken(activity);
    }

    /**
     * Creates a new {@link CarTaskViewController} instance for the provided {@code hostActivity}.
     *
     * @param callbackExecutor the executor which the {@code carTaskViewControllerCallback} methods
     *                         will be called upon.
     * @param carTaskViewControllerCallback the life callback methods for the
     *                                    {@link CarTaskViewController}.
     * @param hostActivity the activity which will be hosting the taskviews that will be created
     *                     using the underlying {@link CarTaskViewController}.
     * @throws RemoteException as thrown by
     * {@link ICarSystemUIProxy#createCarTaskView(CarTaskViewClient)}.
     */
    @MainThread
    void createCarTaskViewController(
            Context context,
            @NonNull CarTaskViewControllerHostLifecycle hostActivity,
            @NonNull Executor callbackExecutor,
            @NonNull CarTaskViewControllerCallback carTaskViewControllerCallback)
            throws RemoteException {
        if (mActivityHolders.containsKey(hostActivity)) {
            throw new IllegalArgumentException("A CarTaskViewController already exists for this "
                    + "activity. Cannot create another one.");
        }
        hostActivity.registerObserver(mCarTaskViewControllerHostLifecycleObserver);
        ActivityHolder activityHolder = new ActivityHolder(context, hostActivity, callbackExecutor,
                carTaskViewControllerCallback, mCarActivityService);
        mActivityHolders.put(hostActivity, activityHolder);

        if (mSystemUIProxyCallback != null && mICarSystemUI != null) {
            // If there is already a connection with the CarSystemUIProxy, trigger onConnected
            // right away.
            activityHolder.onCarSystemUIConnected(mICarSystemUI);
            return;
        }
        if (mSystemUIProxyCallback != null) {
            // If there is no connection, but callback is registered, do nothing; as when
            // connection is made, it will automatically trigger onConnected for all the  activity
            // holders.
            Slogf.d(TAG, "SystemUIProxyCallback already registered but not connected yet.");
            return;
        }

        // If the CarSystemUIProxyCallback is not registered, register it now.
        mSystemUIProxyCallback = new ICarSystemUIProxyCallback.Stub() {
            @Override
            public void onConnected(ICarSystemUIProxy carSystemUIProxy) {
                long identity = Binder.clearCallingIdentity();
                try {
                    mMainExecutor.execute(() -> onSystemUIProxyConnected(carSystemUIProxy));
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        };
        try {
            mCarActivityService.addCarSystemUIProxyCallback(mSystemUIProxyCallback);
        } catch (RemoteException e) {
            mSystemUIProxyCallback = null;
            throw e;
        }
    }

    @MainThread
    private void onSystemUIProxyConnected(ICarSystemUIProxy systemUIProxy) {
        mICarSystemUI = systemUIProxy;
        try {
            systemUIProxy.asBinder().linkToDeath(mDeathRecipient, /* flags= */ 0);
        } catch (RemoteException ex) {
            throw new IllegalStateException("Linking to binder death failed for "
                    + "ICarSystemUIProxy, the System UI might already died", ex);
        }

        for (ActivityHolder activityHolder : mActivityHolders.values()) {
            activityHolder.onCarSystemUIConnected(systemUIProxy);
        }
    }

    @MainThread
    private void onSystemUIProxyDisconnected() {
        mICarSystemUI.asBinder().unlinkToDeath(mDeathRecipient, /* flags= */ 0);
        mICarSystemUI = null;

        for (ActivityHolder activityHolder : mActivityHolders.values()) {
            activityHolder.onCarSystemUIDisconnected();
        }
        // No need to remove the holders as activities are still active and will create the
        // taskviews again, when system ui will be connected again.
    }

    private static final class ActivityHolder {
        private final Context mContext;
        private final CarTaskViewControllerHostLifecycle mActivity;
        private final Executor mCallbackExecutor;
        private final CarTaskViewControllerCallback mCarTaskViewControllerCallback;
        private final ICarActivityService mCarActivityService;
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private CarTaskViewController mCarTaskViewController;

        private ActivityHolder(Context context,
                CarTaskViewControllerHostLifecycle activity,
                Executor callbackExecutor,
                CarTaskViewControllerCallback carTaskViewControllerCallback,
                ICarActivityService carActivityService) {
            mContext = context;
            mActivity = activity;
            mCallbackExecutor = callbackExecutor;
            mCarTaskViewControllerCallback = carTaskViewControllerCallback;
            mCarActivityService = carActivityService;
        }

        private void maybeShowControlledTasks() {
            synchronized (mLock) {
                if (mCarTaskViewController == null || !mCarTaskViewController.isHostVisible()) {
                    return;
                }
                mCarTaskViewController.showEmbeddedControlledTasks();
            }
        }

        private void onCarSystemUIConnected(ICarSystemUIProxy systemUIProxy) {
            synchronized (mLock) {
                mCarTaskViewController =
                        new CarTaskViewController(mContext, mActivity, systemUIProxy,
                                mCarActivityService);
            }
            mCallbackExecutor.execute(() -> {
                synchronized (mLock) {
                    // Check for null because the mCarTaskViewController might have already been
                    // released but this code path is executed later because the executor was
                    // busy.
                    if (mCarTaskViewController == null) {
                        Slogf.w(TAG,
                                "car task view controller not found when triggering callback, not"
                                        + " dispatching onConnected");
                        return;
                    }
                    mCarTaskViewControllerCallback.onConnected(mCarTaskViewController);
                }
            });
        }

        private void onCarSystemUIDisconnected() {
            synchronized (mLock) {
                if (mCarTaskViewController == null) {
                    Slogf.w(TAG,
                            "car task view controller not found, not dispatching onDisconnected");
                    return;
                }
                // Only release the taskviews and not the controller because the system ui might get
                // connected while the activity is still visible.
                mCarTaskViewController.releaseTaskViews();
            }
            mCallbackExecutor.execute(() -> {
                synchronized (mLock) {
                    if (mCarTaskViewController == null) {
                        Slogf.w(TAG, "car task view controller not found when triggering "
                                + "callback, not dispatching onDisconnected");
                        return;
                    }
                    mCarTaskViewControllerCallback.onDisconnected(mCarTaskViewController);
                }
            });
        }

        private void onActivityDestroyed() {
            releaseController();
        }

        private void releaseController() {
            synchronized (mLock) {
                if (mCarTaskViewController == null) {
                    Slogf.w(TAG, "car task view controller not found, not releasing");
                    return;
                }
                mCarTaskViewController.release();
                mCarTaskViewController = null;
            }
        }
    }

    private UserLifecycleListener mUserLifecycleListener = new UserLifecycleListener() {
        @Override
        public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
            // Only called when USER_LIFECYCLE_EVENT_TYPE_UNLOCKED.
            for (int i = mActivityHolders.size() - 1; i >= 0; --i) {
                ActivityHolder activityHolder = mActivityHolders.valueAt(i);
                activityHolder.maybeShowControlledTasks();
            }
        }
    };
}
