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

package com.android.managedprovisioning;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.view.WindowManager;

import com.android.managedprovisioning.preprovisioning.EncryptionController;

import dagger.hilt.android.HiltAndroidApp;

import javax.inject.Inject;

/**
 * A base {@link Application} that is meant to be extended.
 *
 * <p>{@code ManagedProvisioning} inheritors are required to extend this class. They
 * can map their own {@link Activity} classes to existing {@code ManagedProvisioning}
 * screens by calling {@link #setOverrideActivity(ManagedProvisioningScreens, Class)}.
 *
 * <p>By default, the existing {@code ManagedProvisioning} {@link Activity} classes are used.
 */
@HiltAndroidApp(Application.class)
public abstract class ManagedProvisioningBaseApplication extends
        Hilt_ManagedProvisioningBaseApplication {
    @Inject
    protected ScreenManager mScreenManager;
    private EncryptionController mEncryptionController;
    private boolean mKeepScreenOn;

    @Override
    public void onCreate() {
        super.onCreate();
        mEncryptionController = EncryptionController.getInstance(
                this,
                new ComponentName(
                        /* pkg= */ this,
                        getActivityClassForScreen(ManagedProvisioningScreens.POST_ENCRYPT)));
    }

    public final EncryptionController getEncryptionController() {
        return mEncryptionController;
    }

    /**
     * Maps the provided {@code screen} to the provided {@code activityClass}.
     *
     * <p>When ManagedProvisioning wants to launch any of the screens in {@link
     * ManagedProvisioningScreens}, instead of its base {@link Activity} implementation, it will
     * launch the class provided here.
     */
    public final void setOverrideActivity(
            ManagedProvisioningScreens screen, Class<? extends Activity> activityClass) {
        mScreenManager.setOverrideActivity(screen, activityClass);
    }

    /**
     * Retrieves the {@link Activity} class associated with the provided {@code screen}.
     *
     * <p>If no screens were set via {@link #setOverrideActivity(ManagedProvisioningScreens,
     * Class)}, the base ManagedProvisioning {@link Activity} implementation will be returned.
     */
    public final Class<? extends Activity> getActivityClassForScreen(
            ManagedProvisioningScreens screen) {
        return mScreenManager.getActivityClassForScreen(screen);
    }

    public ScreenManager getScreenManager() {
        return mScreenManager;
    }

    /**
     * Sets the screen On for whole provisioning flow
     */
    public void keepScreenOn(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
