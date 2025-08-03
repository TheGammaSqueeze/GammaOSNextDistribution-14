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

package com.android.fakesystemapp.systemui;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextPaint;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.android.fakesystemapp.R;

/**
 * A  fork of systemui's MediaProjectionPermissionActivity
 *
 * Modifications are marked with SLIM-CHANGED
 */
public class SlimMediaProjectionPermissionActivity extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final String TAG = "MediaProjectionPermissionActivity";

    private String mPackageName;
    private int mUid;
    private IMediaProjectionManager mService;

    private AlertDialog mDialog;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPackageName = getCallingPackage();
        IBinder b = ServiceManager.getService(MEDIA_PROJECTION_SERVICE);
        mService = IMediaProjectionManager.Stub.asInterface(b);

        if (mPackageName == null) {
            finish();
            return;
        }

        PackageManager packageManager = getPackageManager();
        ApplicationInfo aInfo;
        try {
            aInfo = packageManager.getApplicationInfo(mPackageName, 0);
            mUid = aInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "unable to look up package name", e);
            finish();
            return;
        }

        try {
            if (mService.hasProjectionPermission(mUid, mPackageName)) {
                setResult(RESULT_OK, getMediaProjectionIntent(mUid, mPackageName));
                finish();
                return;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error checking projection permissions", e);
            finish();
            return;
        }

        TextPaint paint = new TextPaint();
        paint.setTextSize(42);


        CharSequence dialogText = null;
        CharSequence dialogTitle = null;
        // SLIM-CHANGED - simplify text and title

            dialogText = getString(R.string.media_projection_dialog_service_text);
            dialogTitle = getString(R.string.media_projection_dialog_service_title);

        mDialog = new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setIcon(R.drawable.ic_media_projection_permission)
                .setMessage(dialogText)
                .setPositiveButton(R.string.media_projection_action_text, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setOnCancelListener(this)
                .create();

        mDialog.create();
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);

        final Window w = mDialog.getWindow();
        w.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        w.addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        try {
            if (which == AlertDialog.BUTTON_POSITIVE) {
                setResult(RESULT_OK, getMediaProjectionIntent(mUid, mPackageName));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error granting projection permission", e);
            setResult(RESULT_CANCELED);
        } finally {
            if (mDialog != null) {
                mDialog.dismiss();
            }
            finish();
        }
    }

    private Intent getMediaProjectionIntent(int uid, String packageName)
            throws RemoteException {
        IMediaProjection projection = mService.createProjection(uid, packageName,
                MediaProjectionManager.TYPE_SCREEN_CAPTURE, false /* permanentGrant */);
        Intent intent = new Intent();
        intent.putExtra(MediaProjectionManager.EXTRA_MEDIA_PROJECTION, projection.asBinder());
        return intent;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        finish();
    }

}

