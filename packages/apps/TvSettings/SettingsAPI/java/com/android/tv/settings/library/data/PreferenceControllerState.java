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

package com.android.tv.settings.library.data;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * State for managing preferenceCompats controlled through {@link AbstractPreferenceController} and
 * provide Lifecycle components.
 */
public abstract class PreferenceControllerState implements State {
    private static final String TAG = "PreferenceControllerState";
    protected final Context mContext;
    protected UIUpdateCallback mUIUpdateCallback;
    protected PreferenceCompatManager mPreferenceCompatManager;
    protected List<AbstractPreferenceController> mPreferenceControllers = new ArrayList<>();
    private Lifecycle mLifecycle;

    public PreferenceControllerState(Context context, UIUpdateCallback callback) {
        mUIUpdateCallback = callback;
        mContext = context;
        mPreferenceCompatManager = new PreferenceCompatManager();
    }

    @Override
    public void onAttach() {
        mLifecycle = new Lifecycle();
        mLifecycle.onAttach();
    }

    @Override
    public void onCreate(Bundle extras) {
        mLifecycle.onCreate(extras);
        List<AbstractPreferenceController> controllers = onCreatePreferenceControllers(mContext);
        if (controllers == null) {
            controllers = new ArrayList<>();
        }
        mPreferenceControllers.addAll(controllers);
        initAllPreferenceControllers();
    }

    @Override
    public void onStart() {
        mLifecycle.onStart();
        updateAllPreferenceControllers();
    }

    @Override
    public void onResume() {
        mLifecycle.onResume();
    }

    @Override
    public void onPause() {
        mLifecycle.onPause();
    }

    @Override
    public void onStop() {
        mLifecycle.onStop();
    }

    @Override
    public void onDestroy() {
        mLifecycle.onDestroy();
    }

    @Override
    public void onDetach() {
        // no-op
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        Collection<AbstractPreferenceController> controllers =
                new ArrayList<>(mPreferenceControllers);
        for (AbstractPreferenceController controller : controllers) {
            if (keyEquals(key, controller.getPreferenceKey())) {
                return controller.handlePreferenceTreeClick(status);
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        Collection<AbstractPreferenceController> controllers =
                new ArrayList<>(mPreferenceControllers);
        for (AbstractPreferenceController controller : controllers) {
            if (keyEquals(key, controller.getPreferenceKey())) {
                return controller.handlePreferenceChange(newValue);
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public abstract int getStateIdentifier();

    protected void initAllPreferenceControllers() {
        Collection<AbstractPreferenceController> controllers =
                new ArrayList<>(mPreferenceControllers);
        for (AbstractPreferenceController controller : controllers) {
            if (controller.isAvailable()) {
                controller.initAndNotify();
            }
        }
    }

    /**
     * Update state of each preference managed by PreferenceController.
     */
    protected void updateAllPreferenceControllers() {
        Collection<AbstractPreferenceController> controllers =
                new ArrayList<>(mPreferenceControllers);
        for (AbstractPreferenceController controller : controllers) {
            if (controller.isAvailable()) {
                controller.updateAndNotify();
            }
        }
    }

    /**
     * Get a list of {@link AbstractPreferenceController} for this fragment.
     */
    protected abstract List<AbstractPreferenceController> onCreatePreferenceControllers(
            Context context);

    protected Lifecycle getLifecycle() {
        return mLifecycle;
    }

    protected static boolean keyEquals(String[] key1, String[] key2) {
        return Arrays.equals(key1, key2);
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
