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

package com.android.intentresolver;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Helper to look up the components available on this device to handle assorted built-in actions
 * like "Edit" that may be displayed for certain content/preview types. The components are queried
 * when this record is instantiated, and are then immutable for a given instance.
 *
 * Because this describes the app's external execution environment, test methods may prefer to
 * provide explicit values to override the default lookup logic.
 */
public class ChooserIntegratedDeviceComponents {
    @Nullable
    private final ComponentName mEditSharingComponent;

    @Nullable
    private final ComponentName mNearbySharingComponent;

    /** Look up the integrated components available on this device. */
    public static ChooserIntegratedDeviceComponents get(
            Context context,
            SecureSettings secureSettings) {
        return new ChooserIntegratedDeviceComponents(
                getEditSharingComponent(context),
                getNearbySharingComponent(context, secureSettings));
    }

    @VisibleForTesting
    ChooserIntegratedDeviceComponents(
            @Nullable ComponentName editSharingComponent,
            @Nullable ComponentName nearbySharingComponent) {
        mEditSharingComponent = editSharingComponent;
        mNearbySharingComponent = nearbySharingComponent;
    }

    public ComponentName getEditSharingComponent() {
        return mEditSharingComponent;
    }

    public ComponentName getNearbySharingComponent() {
        return mNearbySharingComponent;
    }

    private static ComponentName getEditSharingComponent(Context context) {
        String editorComponent = context.getApplicationContext().getString(
                R.string.config_systemImageEditor);
        return TextUtils.isEmpty(editorComponent)
                ? null : ComponentName.unflattenFromString(editorComponent);
    }

    private static ComponentName getNearbySharingComponent(Context context,
            SecureSettings secureSettings) {
        String nearbyComponent = secureSettings.getString(
                context.getContentResolver(), Settings.Secure.NEARBY_SHARING_COMPONENT);
        if (TextUtils.isEmpty(nearbyComponent)) {
            nearbyComponent = context.getString(R.string.config_defaultNearbySharingComponent);
        }
        return TextUtils.isEmpty(nearbyComponent)
                ? null : ComponentName.unflattenFromString(nearbyComponent);
    }
}
