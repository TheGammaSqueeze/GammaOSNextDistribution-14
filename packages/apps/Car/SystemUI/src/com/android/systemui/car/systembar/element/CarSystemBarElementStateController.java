/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar.element;

import android.os.Bundle;
import android.view.View;

import com.android.systemui.car.systembar.CarSystemBarRestartTracker;
import com.android.systemui.dagger.SysUISingleton;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import javax.inject.Inject;

@SysUISingleton
public class CarSystemBarElementStateController {
    private final HashMap<Long, WeakReference<CarSystemBarElementController>> mControllers =
            new HashMap<>();
    private final HashMap<Long, Bundle> mStates = new HashMap<>();

    private final CarSystemBarRestartTracker.Listener mRestartListener =
            new CarSystemBarRestartTracker.Listener() {
                @Override
                public void onPendingRestart(boolean recreateWindows,
                        boolean provisionedStateChanged) {
                    if (!recreateWindows && !provisionedStateChanged) {
                        notifyPendingRestart();
                    }
                }

                @Override
                public void onRestartComplete(boolean windowsRecreated,
                        boolean provisionedStateChanged) {
                    if (!windowsRecreated && !provisionedStateChanged) {
                        notifyRestartComplete();
                    }
                }
            };

    @Inject
    public CarSystemBarElementStateController(CarSystemBarRestartTracker restartTracker) {
        restartTracker.addListener(mRestartListener);
    }

    void registerController(CarSystemBarElementController controller) {
        long viewId = controller.getUniqueViewId();
        if (viewId == View.NO_ID) {
            throw new IllegalArgumentException("Cannot restore state on view with no id");
        }
        synchronized (mControllers) {
            if (mControllers.containsKey(viewId)) {
                throw new IllegalArgumentException("Cannot restore state on duplicate view ids");
            }
            mControllers.put(viewId, new WeakReference<>(controller));
            Bundle bundle = mStates.remove(viewId);
            if (bundle != null) {
                controller.restoreState(bundle);
            }
        }
    }

    void unregisterController(CarSystemBarElementController controller) {
        long viewId = controller.getUniqueViewId();
        if (viewId == View.NO_ID) {
            return;
        }
        synchronized (mControllers) {
            mControllers.remove(viewId);
        }
    }

    private void notifyPendingRestart() {
        synchronized (mControllers) {
            mStates.clear();
            for (long id : mControllers.keySet()) {
                CarSystemBarElementController controller = mControllers.get(id).get();
                if (controller != null) {
                    Bundle outBundle = new Bundle();
                    mStates.put(id, controller.getState(outBundle));
                } else {
                    mControllers.remove(id);
                }
            }
        }
    }

    private void notifyRestartComplete() {
        synchronized (mControllers) {
            for (long id : mControllers.keySet()) {
                Bundle bundle = mStates.remove(id);
                if (bundle != null) {
                    CarSystemBarElementController controller = mControllers.get(id).get();
                    if (controller != null) {
                        controller.restoreState(bundle);
                    } else {
                        mControllers.remove(id);
                    }
                }
            }
        }
    }
}
