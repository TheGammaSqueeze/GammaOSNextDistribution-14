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

package com.android.cts.verifier.qstiles;

import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TileServiceRequestVerifierActivity extends InteractiveVerifierActivity {

    private static final String TAG = "TileServiceRequestVerifierActivity";

    private static final String ACTION_REMOVE_PACKAGE =
            "com.android.cts.verifier.qstiles.ACTION_REMOVE_PACKAGE";

    private CharSequence mTileLabel;
    private static int sNextResultCode = 1000;
    private static final String HELPER_PACKAGE_NAME = "com.android.cts.tileserviceapp";
    private static final String HELPER_ACTIVITY_NAME = ".TileRequestActivity";
    private static final String HELPER_TILE_NAME = ".RequestTileService";
    private static final ComponentName HELPER_ACTIVITY_COMPONENT =
            ComponentName.createRelative(HELPER_PACKAGE_NAME, HELPER_ACTIVITY_NAME);
    private static final Intent INTENT = new Intent().setComponent(HELPER_ACTIVITY_COMPONENT);

    // Keep track of activity started codes to handle results.
    private final Map<Integer, Consumer<Integer>> mResultRegistry = new HashMap<>();

    @Override
    protected boolean setTileState(boolean enabled) {
        // This tile is always enabled as long as the package is installed.
        return true;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        mTileLabel = getString(R.string.tile_request_service_name);
        super.onCreate(savedState);
    }

    private void registerForResult(Consumer<Integer> consumer) {
        int code = sNextResultCode++;
        mResultRegistry.put(code, consumer);
        startActivityForResult(INTENT, code);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mResultRegistry.get(requestCode).accept(resultCode);
    }

    private boolean isHelperAppInstalled() {
        return getPackageManager().resolveActivity(INTENT, 0) != null;
    }

    @Override
    protected ComponentName getTileComponentName() {
        return ComponentName.createRelative(HELPER_PACKAGE_NAME, HELPER_TILE_NAME);
    }

    @Override
    protected int getTitleResource() {
        return R.string.tiles_request_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.tiles_request_info;
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        ArrayList<InteractiveTestCase> list = new ArrayList<>();
        list.add(new UninstallPackage());
        list.add(new InstallPackage());
        list.add(new InstallPackageVerify());
        list.add(new TileNotPresent());
        list.add(new RequestAddTileDismiss());
        list.add(new RequestAddTileCorrectInfo());
        list.add(new RequestAddTileAnswerYes());
        list.add(new TilePresentAfterRequest());
        list.add(new RequestAddTileAlreadyAdded());
        list.add(new UninstallPackage());
        return list;
    }

    // Tests
    private class UninstallPackage extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_uninstall);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        private BroadcastReceiver registerReceiver() {
            BroadcastReceiver br = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_SUCCESS);
                    if (result == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        startActivity(intent.getParcelableExtra(Intent.EXTRA_INTENT));
                        return;
                    }
                    context.unregisterReceiver(this);
                    if (!isHelperAppInstalled()) {
                        status = PASS;
                    } else {
                        setFailed("Helper App still installed");
                    }
                    next();
                }
            };
            mContext.registerReceiver(br, new IntentFilter(ACTION_REMOVE_PACKAGE),
                    Context.RECEIVER_EXPORTED);
            return br;
        }

        @Override
        protected void requestAction() {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            Log.i(TAG,
                    "Uninstalling package " + HELPER_PACKAGE_NAME + " using " + packageInstaller);
            BroadcastReceiver br = registerReceiver();
            try {
                PendingIntent pi = PendingIntent.getBroadcast(mContext, /* requestCode */ 0,
                        new Intent(ACTION_REMOVE_PACKAGE).setPackage(mContext.getPackageName()),
                        PendingIntent.FLAG_MUTABLE);
                packageInstaller.uninstall(HELPER_ACTIVITY_COMPONENT.getPackageName(),
                        pi.getIntentSender());
                status = WAIT_FOR_USER;
            } catch (IllegalArgumentException e) {
                mContext.unregisterReceiver(br);
                status = PASS;
            }
        }

        @Override
        protected void test() {
            if (status == READY) {
                if (!isHelperAppInstalled()) {
                    status = PASS;
                } else {
                    status = WAIT_FOR_USER;
                }
                next();
            }
        }
    }

    private class InstallPackage extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_install);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class InstallPackageVerify extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_install_verify);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            if (isHelperAppInstalled()) {
                status = PASS;
            } else {
                setFailed("Helper app not properly installed");
            }
            next();
        }
    }

    private class TileNotPresent extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_tile_not_present, mTileLabel);
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileDismiss extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_dismissed);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            registerForResult(
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileCorrectInfo extends InteractiveTestCase {

        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_correct_info,
                    mContext.getString(R.string.tile_request_helper_app_name),
                    mTileLabel);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            registerForResult(
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)) {
                            status = WAIT_FOR_USER;
                            setPassFailButtonsEnabledState(true);
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            setPassFailButtonsEnabledState(false);
            next();
        }
    }

    private class RequestAddTileAnswerYes extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_answer_yes);
        }

        @Override
        protected boolean showRequestAction() {
            return true;
        }

        @Override
        protected void requestAction() {
            registerForResult(
                    integer -> {
                        if (integer.equals(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class TilePresentAfterRequest extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createUserPassFail(parent, R.string.tiles_request_tile_present, mTileLabel);
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private class RequestAddTileAlreadyAdded extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.tiles_request_check_tile_already_added);
        }

        @Override
        protected void test() {
            registerForResult(
                    integer -> {
                        if (integer.equals(
                                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)) {
                            status = PASS;
                        } else {
                            setFailed("Request called back with result: " + integer);
                        }
                        next();
                    }
            );
            status = READY_AFTER_LONG_DELAY;
            next();
        }
    }
}
