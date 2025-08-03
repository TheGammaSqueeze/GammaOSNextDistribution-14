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

package com.android.car.setupwizardlib;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.robolectric.Robolectric;

/**
 * An implementation of
 * {@link com.google.android.car.setupwizard.common.config.FeatureManagementProvider} for
 * Robolectric tests.
 */
public class FakeFeatureManagementProvider extends ContentProvider {
    private static final String SUW_AUTHORITY =
            "com.google.android.car.setupwizard.feature_management";
    private static final String GET_FEATURE_VERSION_METHOD = "getFeatureVersion";
    private static final String SPLIT_NAV_LAYOUT = "split_nav_layout";
    private static final String TYPE = "type";
    private static final String BOOLEAN_TYPE = "BOOLEAN";
    private static final String VALUE = "value";

    public static FakeFeatureManagementProvider installProvider() {
        return Robolectric.setupContentProvider(FakeFeatureManagementProvider.class, SUW_AUTHORITY);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException("query operation not supported currently.");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("getType operation not supported currently.");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert operation not supported currently.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete operation not supported currently.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update operation not supported currently.");
    }

    @Override
    public Bundle call(String method, String feature, Bundle extras) {
        Bundle bundle = new Bundle();
        if (!GET_FEATURE_VERSION_METHOD.equals(method)) {
            return bundle;
        }

        switch(feature) {
            case SPLIT_NAV_LAYOUT:
                bundle.putString(TYPE, BOOLEAN_TYPE);
                bundle.putBoolean(VALUE, true);
                break;
            default:
                // Do nothing
        }
        return bundle;
    }
}
