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
package com.android.tv.settings.library.util.lifecycle;

import android.annotation.UiThread;
import android.os.Bundle;

import androidx.preference.PreferenceScreen;

import com.android.tv.settings.library.util.ThreadUtils;
import com.android.tv.settings.library.util.lifecycle.events.OnAttach;
import com.android.tv.settings.library.util.lifecycle.events.OnCreate;
import com.android.tv.settings.library.util.lifecycle.events.OnDestroy;
import com.android.tv.settings.library.util.lifecycle.events.OnPause;
import com.android.tv.settings.library.util.lifecycle.events.OnResume;
import com.android.tv.settings.library.util.lifecycle.events.OnSaveInstanceState;
import com.android.tv.settings.library.util.lifecycle.events.OnStart;
import com.android.tv.settings.library.util.lifecycle.events.OnStop;
import com.android.tv.settings.library.util.lifecycle.events.SetPreferenceScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatcher for lifecycle events.
 */
public class Lifecycle {
    private static final String TAG = "LifecycleObserver";

    private final List<LifecycleObserver> mObservers = new ArrayList<>();

    /**
     * Registers a new observer of lifecycle events.
     */
    @UiThread
    public void addObserver(LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        mObservers.add(observer);
    }

    @UiThread
    public void removeObserver(LifecycleObserver observer) {
        ThreadUtils.ensureMainThread();
        mObservers.remove(observer);
    }

    /**
     * Pass all onAttach event to {@link LifecycleObserver}.
     */
    public void onAttach() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnAttach) {
                ((OnAttach) observer).onAttach();
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnCreate) {
                ((OnCreate) observer).onCreate(savedInstanceState);
            }
        }
    }

    public void onStart() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnStart) {
                ((OnStart) observer).onStart();
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

    public void onResume() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnResume) {
                ((OnResume) observer).onResume();
            }
        }
    }

    public void onPause() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnPause) {
                ((OnPause) observer).onPause();
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

    public void onStop() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnStop) {
                ((OnStop) observer).onStop();
            }
        }
    }

    public void onDestroy() {
        for (int i = 0, size = mObservers.size(); i < size; i++) {
            final LifecycleObserver observer = mObservers.get(i);
            if (observer instanceof OnDestroy) {
                ((OnDestroy) observer).onDestroy();
            }
        }
    }
}
