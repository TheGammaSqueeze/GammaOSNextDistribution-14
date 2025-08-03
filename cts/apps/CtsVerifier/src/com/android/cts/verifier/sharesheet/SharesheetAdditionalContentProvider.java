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

package com.android.cts.verifier.sharesheet;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import static com.android.cts.verifier.sharesheet.TestContract.LogTags.TAG;

import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.chooser.AdditionalContentContract.Columns;
import android.service.chooser.AdditionalContentContract.CursorExtraKeys;
import android.service.chooser.AdditionalContentContract.MethodNames;
import android.service.chooser.ChooserAction;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sharesheet.TestContract.Keys;
import com.android.cts.verifier.sharesheet.TestContract.Uris;

import java.util.ArrayList;

public class SharesheetAdditionalContentProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable Bundle queryArgs,
            @Nullable CancellationSignal cancellationSignal) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[] {Columns.URI });
        Intent chooserIntent = queryArgs == null
                ? null
                : queryArgs.getParcelable(Intent.EXTRA_INTENT, Intent.class);
        if (chooserIntent == null) {
            return cursor;
        }
        ArrayList<Uri> uris =
                chooserIntent.getParcelableArrayListExtra(Keys.AdditionalContent, Uri.class);
        uris = uris == null ? new ArrayList<>(0) : uris;
        String callingPackage = getCallingPackage();
        for (Uri u : uris) {
            cursor.addRow(new String[] { u.toString() });
            context.grantUriPermission(
                    callingPackage, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        int startPos = chooserIntent.getIntExtra(Keys.CursorStartPos, -1);
        if (startPos >= 0) {
            Bundle cursorExtras = cursor.getExtras();
            if (cursorExtras == null) {
                cursorExtras = new Bundle();
            } else {
                cursorExtras = new Bundle(cursorExtras);
            }
            cursorExtras.putInt(CursorExtraKeys.POSITION, startPos);
            cursor.setExtras(cursorExtras);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Context context = getContext();
        if (context == null) {
            throw new RuntimeException("ContentProvider context is null");
        }
        if (!MethodNames.ON_SELECTION_CHANGED.equals(method)) {
            Log.w(TAG, "unexpected method: " + method);
            return null;
        }
        if (!Uris.ExtraContentUri.toString().equals(arg)) {
            Log.w(TAG, "Unexpected method argument: " + arg);
            return null;
        }
        if (extras == null) {
            Log.w(TAG, "extras is null");
            return null;
        }
        Intent chooserIntent = extras.getParcelable(Intent.EXTRA_INTENT, Intent.class);
        if (chooserIntent == null) {
            Log.w(TAG, "extras' Intent#EXTRA_INTENT is not an intent");
            return null;
        }
        if (!Intent.ACTION_CHOOSER.equals(chooserIntent.getAction())) {
            Log.w(TAG, "extras' Intent#EXTRA_INTENT is not a Chooser intent");
            return null;
        }
        if (!chooserIntent.hasExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS)) {
            return null;
        }
        Intent targetIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        if (targetIntent == null) {
            Log.w(TAG, "target intent is missing");
            return null;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(targetIntent.getAction())) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (uri != null) {
                uris.add(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(targetIntent.getAction())) {
            ArrayList<Uri> sharedUris =
                    targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            if (sharedUris != null) {
                uris.ensureCapacity(sharedUris.size());
                for (Uri uri: sharedUris) {
                    if (uri != null) {
                        uris.add(uri);
                    }
                }
            }
        } else {
            Log.w(TAG, "unexpected target intent action: " + targetIntent.getAction());
            return null;
        }

        ArrayList<Uri> expectedUris =
                chooserIntent.getParcelableArrayListExtra(Keys.AdditionalContent, Uri.class);
        if (expectedUris == null || expectedUris.isEmpty()) {
            Log.w(TAG, "malformed test intent");
            return null;
        }
        boolean allSelected = uris.size() == expectedUris.size() && uris.containsAll(expectedUris);
        Bundle result = new Bundle();
        Intent actionIntent = new Intent(context, SharesheetPayloadToggleActionActivity.class);
        actionIntent.putExtra(Keys.Result, allSelected);
        PendingIntent actionPendingIntent = PendingIntent.getActivity(
                context,
                /*requestCode=*/uris.size(),
                actionIntent,
                FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        ChooserAction[] customActions = new ChooserAction[] {
                new ChooserAction.Builder(
                        Icon.createWithResource(
                                context, allSelected ? R.drawable.fs_good : R.drawable.fs_error),
                        context.getString(R.string.sharesheet_chooser_action),
                        actionPendingIntent)
                        .build()
        };
        result.putParcelableArray(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, customActions);
        return result;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}
