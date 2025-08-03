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

package com.android.tv.settings.device.eco;

import static android.Manifest.permission.MANAGE_LOW_POWER_STANDBY;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.tv.settings.device.eco.EnergyModesHelper.EnergyMode;

import java.util.ArrayList;
import java.util.List;

/**
 * ContentProvider that provides methods to query and set Energy Modes.
 */
public class EnergyModesContentProvider extends ContentProvider {
    /** Method to get available energy modes, default mode, and selected mode */
    private static final String METHOD_GET_ENERGY_MODES = "getEnergyModes";

    /**
     * Method to set the selected energy mode.
     * Requires permission MANAGE_LOW_POWER_STANDBY.
     */
    private static final String METHOD_SET_ENERGY_MODE = "setEnergyMode";

    /** Key for a String representing the identifier of the default mode (may be null). */
    private static final String KEY_DEFAULT_MODE = "default_mode";

    /** Key for a String representing the currently selected mode. */
    private static final String KEY_SELECTED_MODE = "selected_mode";

    /** Key for a List of Bundle representing the available energy modes. */
    private static final String KEY_ENERGY_MODES = "energy_modes";

    /** Key for a String representing the identifier for an energy mode. */
    private static final String KEY_IDENTIFIER = "identifier";

    /** Key for an Icon representing the icon of an energy mode. */
    private static final String KEY_ICON = "icon";

    /** Key for an int representing the color of an energy mode (in ARGB). */
    private static final String KEY_COLOR = "color";

    /** Key for a String representing the title of an energy mode. */
    private static final String KEY_TITLE = "title";

    /** Key for a String representing the subtitle of an energy mode. */
    private static final String KEY_SUBTITLE = "subtitle";

    /** Key for a String representing the description of an energy mode. */
    private static final String KEY_DESCRIPTION = "description";

    /** Key for a String representing a short description of an energy mode. */
    private static final String KEY_SHORT_DESCRIPTION = "short_description";

    /** Key for a String array representing the (human-friendly) features of an energy mode. */
    private static final String KEY_FEATURES_LIST = "features_list";

    /** Key for a String array representing the features of an energy mode. */
    private static final String KEY_FEATURES = "features";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        throw new UnsupportedOperationException("query operation not supported currently.");
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        throw new UnsupportedOperationException("getType operation not supported currently.");
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("insert operation not supported currently.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("delete operation not supported currently.");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("update operation not supported currently.");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_GET_ENERGY_MODES.equals(method)) {
            return getEnergyModesFromBinder();
        } else if (METHOD_SET_ENERGY_MODE.equals(method)) {
            return setEnergyModeFromBinder(arg);
        }

        throw new IllegalArgumentException("Unknown method name");
    }

    private Bundle getEnergyModesFromBinder() {
        final long ident = Binder.clearCallingIdentity();
        try {
            return getEnergyModes();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private Bundle getEnergyModes() {
        EnergyModesHelper energyModesHelper = new EnergyModesHelper(getContext());

        final EnergyMode defaultMode = energyModesHelper.getDefaultEnergyMode();
        final EnergyMode currentMode = energyModesHelper.updateEnergyMode();

        Bundle bundle = new Bundle();
        bundle.putString(KEY_DEFAULT_MODE, getModeIdentifier(defaultMode));
        bundle.putString(KEY_SELECTED_MODE, getModeIdentifier(currentMode));
        bundle.putParcelableList(KEY_ENERGY_MODES, getModes(energyModesHelper));
        return bundle;
    }

    private Bundle setEnergyModeFromBinder(String identifier) {
        getContext().enforceCallingOrSelfPermission(MANAGE_LOW_POWER_STANDBY, null);
        final long ident = Binder.clearCallingIdentity();
        try {
            EnergyModesHelper energyModesHelper = new EnergyModesHelper(getContext());
            EnergyMode energyMode = energyModesHelper.getEnergyMode(/* identifier= */ identifier);
            if (energyMode == null) {
                throw new IllegalArgumentException("Unknown energy mode: " + identifier);
            }

            energyModesHelper.setEnergyMode(energyMode);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return null;
    }

    @Nullable
    private String getModeIdentifier(@Nullable EnergyMode mode) {
        if (mode == null) {
            return null;
        }

        return getContext().getString(mode.identifierRes);
    }

    @NonNull
    private List<Bundle> getModes(@NonNull EnergyModesHelper helper) {
        final List<Bundle> result = new ArrayList<>();
        final List<EnergyMode> energyModes = helper.getEnergyModes();

        for (EnergyMode mode : energyModes) {
            result.add(convertEnergyModeToBundle(helper, mode));
        }

        return result;
    }

    @NonNull
    private Bundle convertEnergyModeToBundle(
            @NonNull EnergyModesHelper helper, @NonNull EnergyMode mode) {
        Context context = getContext();
        Bundle bundle = new Bundle();

        bundle.putString(KEY_IDENTIFIER, getModeIdentifier(mode));
        bundle.putParcelable(KEY_ICON, Icon.createWithResource(context, mode.iconRes));
        bundle.putInt(KEY_COLOR, context.getColor(mode.colorRes));
        bundle.putString(KEY_TITLE, context.getString(mode.titleRes));
        bundle.putString(KEY_SUBTITLE, context.getString(mode.subtitleRes));
        bundle.putString(KEY_DESCRIPTION, context.getString(mode.infoTextRes));
        bundle.putString(KEY_SHORT_DESCRIPTION, context.getString(mode.infoTextRes));
        bundle.putStringArray(KEY_FEATURES_LIST,
                context.getResources().getStringArray(mode.featuresRes));
        bundle.putStringArray(KEY_FEATURES,
                helper.getAllowedFeatures(mode).toArray(new String[0]));

        return bundle;
    }
}
