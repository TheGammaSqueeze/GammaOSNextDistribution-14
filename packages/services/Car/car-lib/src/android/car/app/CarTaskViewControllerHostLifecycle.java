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


import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;


/**
 * This class represents a handle to the lifecycle of the host (container) that creates the
 * {@link CarTaskViewController}.
 * The container can be an activity, fragment, view or a window.
 *
 * @hide
 */
public final class CarTaskViewControllerHostLifecycle {
    /** An interface for observing the lifecycle of the container (host). */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface CarTaskViewControllerHostLifecycleObserver {
        /** Gets called when the container is destroyed. */
        void onHostDestroyed(CarTaskViewControllerHostLifecycle lifecycle);

        /** Gets called when the container has appeared. */
        void onHostAppeared(CarTaskViewControllerHostLifecycle lifecycle);

        /** Gets called when the container has disappeared. */
        void onHostDisappeared(CarTaskViewControllerHostLifecycle lifecycle);
    }

    private final List<CarTaskViewControllerHostLifecycleObserver> mObserverList =
            new ArrayList<>();

    private boolean mIsVisible = false;

    /**
     * Notifies the lifecycle observers that the host has been destroyed and they can clean their
     * internal state.
     */
    public void hostDestroyed() {
        for (CarTaskViewControllerHostLifecycleObserver observer : mObserverList) {
            observer.onHostDestroyed(this);
        }
    }

    /** Notifies the lifecycle observers that the host has appeared. */
    public void hostAppeared() {
        mIsVisible = true;
        for (CarTaskViewControllerHostLifecycleObserver observer : mObserverList) {
            observer.onHostAppeared(this);
        }
    }

    /** Notifies the lifecycle observers that the host has disappeared. */
    public void hostDisappeared() {
        mIsVisible = false;
        for (CarTaskViewControllerHostLifecycleObserver observer : mObserverList) {
            observer.onHostDisappeared(this);
        }
    }

    /** @return true if the container is visible, false otherwise. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean isVisible() {
        return mIsVisible;
    }

    /** Registers the given observer to listen to lifecycle of the container. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void registerObserver(CarTaskViewControllerHostLifecycleObserver observer) {
        mObserverList.add(observer);
    }

    /** Unregisters the given observer to stop listening to the lifecycle of the container. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void unregisterObserver(CarTaskViewControllerHostLifecycleObserver observer) {
        mObserverList.remove(observer);
    }
}


