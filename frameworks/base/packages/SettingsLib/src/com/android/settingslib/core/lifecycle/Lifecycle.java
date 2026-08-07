/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settingslib.core.lifecycle;

import static androidx.lifecycle.Lifecycle.Event.ON_ANY;

import android.annotation.UiThread;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.core.lifecycle.events.OnAttach;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnCreateOptionsMenu;
import com.android.settingslib.core.lifecycle.events.OnDestroy;
import com.android.settingslib.core.lifecycle.events.OnOptionsItemSelected;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnPrepareOptionsMenu;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.android.settingslib.core.lifecycle.events.OnSaveInstanceState;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.core.lifecycle.events.SetPreferenceScreen;
import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatcher for lifecycle events.
 */
public class Lifecycle extends LifecycleRegistry {
    private static final String TAG = "LifecycleObserver";

    private final List<LifecycleObserver> mObservers = new ArrayList<>();
    private final LifecycleProxy mProxy = new LifecycleProxy();

    /**
     * Creates a new LifecycleRegistry for the given provider.
     * <p>
     * You should usually create this inside your LifecycleOwner class's constructor and hold
     * onto the same instance.
     *
     * @param provider The owner LifecycleOwner
     */
    public Lifecycle(@NonNull LifecycleOwner provider) {
        super(provider);
        addObserver(mProxy);
    }

    // GammaOS Nano: in minimal_boot many framework services are deliberately not started, so a
    // PreferenceController's lifecycle callback (onAttach/onCreate/onStart/onResume/onPause/onStop/
    // onDestroy) can dereference a null/absent system service and throw. The DashboardFragment
    // main-thread safety net only wraps displayPreference()/updateState(), NOT these lifecycle-observer
    // dispatches, so an absent-service NPE here would kill the whole Settings process. Fail gracefully:
    // skip the offending observer's callback and keep the rest of the screen alive. Full Android is
    // byte-for-byte unaffected (every guard rethrows when not in minimal_boot).
    private static boolean isNanoMinimalBoot() {
        return SystemProperties.getBoolean("sys.gammaos.minimal_boot", false);
    }

    private static void safeDispatch(LifecycleObserver observer, String event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (isNanoMinimalBoot()) {
                Log.w(TAG, "GammaOS Nano: skipping " + observer.getClass().getName() + "." + event
                        + " in minimal_boot (absent service): " + e);
            } else {
                throw e;
            }
        }
    }

    /**
     * Catches lifecycle-dispatch exceptions from androidx {@code @OnLifecycleEvent} observers (e.g.
     * PreferenceControllers such as InternetPreferenceController) in GammaOS Nano minimal_boot, where an
     * absent framework service can make an onResume/onPause callback throw. Those observers are dispatched
     * by {@link androidx.lifecycle.LifecycleRegistry}'s forwardPass (not the settingslib for-loops below),
     * so without this override the exception propagates out and crashes the Settings process. Full Android
     * rethrows unchanged.
     */
    @Override
    public void handleLifecycleEvent(@NonNull androidx.lifecycle.Lifecycle.Event event) {
        try {
            super.handleLifecycleEvent(event);
        } catch (RuntimeException e) {
            if (isNanoMinimalBoot()) {
                Log.w(TAG, "GammaOS Nano: swallowing lifecycle " + event
                        + " exception in minimal_boot (absent service): " + e);
            } else {
                throw e;
            }
        }
    }

    /**
     * Registers a new observer of lifecycle events.
     */
    @UiThread
    @Override
    public void addObserver(androidx.lifecycle.LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        super.addObserver(observer);
        if (observer instanceof LifecycleObserver) {
            mObservers.add((LifecycleObserver) observer);
        }
    }

    @UiThread
    @Override
    public void removeObserver(androidx.lifecycle.LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        super.removeObserver(observer);
        if (observer instanceof LifecycleObserver) {
            mObservers.remove(observer);
        }
    }

    /**
     * Pass all onAttach event to {@link LifecycleObserver}.
     */
    public void onAttach(Context context) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnAttach) {
                safeDispatch(observer, "onAttach", () -> ((OnAttach) observer).onAttach());
            }
        }
    }

    // This method is not called from the proxy because it does not have access to the
    // savedInstanceState
    public void onCreate(Bundle savedInstanceState) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnCreate) {
                safeDispatch(observer, "onCreate",
                        () -> ((OnCreate) observer).onCreate(savedInstanceState));
            }
        }
    }

    private void onStart() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnStart) {
                safeDispatch(observer, "onStart", () -> ((OnStart) observer).onStart());
            }
        }
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof SetPreferenceScreen) {
                ((SetPreferenceScreen) observer).setPreferenceScreen(preferenceScreen);
            }
        }
    }

    private void onResume() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnResume) {
                safeDispatch(observer, "onResume", () -> ((OnResume) observer).onResume());
            }
        }
    }

    private void onPause() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnPause) {
                safeDispatch(observer, "onPause", () -> ((OnPause) observer).onPause());
            }
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnSaveInstanceState) {
                ((OnSaveInstanceState) observer).onSaveInstanceState(outState);
            }
        }
    }

    private void onStop() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnStop) {
                safeDispatch(observer, "onStop", () -> ((OnStop) observer).onStop());
            }
        }
    }

    private void onDestroy() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnDestroy) {
                safeDispatch(observer, "onDestroy", () -> ((OnDestroy) observer).onDestroy());
            }
        }
    }

    public void onCreateOptionsMenu(final Menu menu, final @Nullable MenuInflater inflater) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnCreateOptionsMenu) {
                ((OnCreateOptionsMenu) observer).onCreateOptionsMenu(menu, inflater);
            }
        }
    }

    public void onPrepareOptionsMenu(final Menu menu) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnPrepareOptionsMenu) {
                ((OnPrepareOptionsMenu) observer).onPrepareOptionsMenu(menu);
            }
        }
    }

    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnOptionsItemSelected) {
                if (((OnOptionsItemSelected) observer).onOptionsItemSelected(menuItem)) {
                    return true;
                }
            }
        }
        return false;
    }

    private class LifecycleProxy
            implements androidx.lifecycle.LifecycleObserver {
        @OnLifecycleEvent(ON_ANY)
        public void onLifecycleEvent(LifecycleOwner owner, Event event) {
            switch (event) {
                case ON_CREATE:
                    // onCreate is called directly since we don't have savedInstanceState here
                    break;
                case ON_START:
                    onStart();
                    break;
                case ON_RESUME:
                    onResume();
                    break;
                case ON_PAUSE:
                    onPause();
                    break;
                case ON_STOP:
                    onStop();
                    break;
                case ON_DESTROY:
                    onDestroy();
                    break;
                case ON_ANY:
                    Log.wtf(TAG, "Should not receive an 'ANY' event!");
                    break;
            }
        }
    }
}
