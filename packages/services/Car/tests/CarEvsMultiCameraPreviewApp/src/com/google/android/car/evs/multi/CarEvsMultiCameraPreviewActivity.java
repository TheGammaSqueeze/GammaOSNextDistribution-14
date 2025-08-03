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

package com.google.android.car.evs.multi;

import static android.car.evs.CarEvsManager.ERROR_NONE;
import static android.hardware.display.DisplayManager.DisplayListener;

import android.app.Activity;
import android.car.Car;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.annotation.GuardedBy;

import com.android.car.internal.evs.CarEvsGLSurfaceView;
import com.android.car.internal.evs.GLES20CarEvsBufferRenderer;

import java.lang.CharSequence;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CarEvsMultiCameraPreviewActivity extends Activity {

    private static final String TAG = CarEvsMultiCameraPreviewActivity.class.getSimpleName();

    /**
     * ActivityManagerService encodes the reason for a request to close system dialogs with this
     * key.
     */
    private final static String EXTRA_DIALOG_CLOSE_REASON = "reason";
    /** This string literal is from com.android.systemui.car.systembar.CarSystemBarButton class. */
    private final static String DIALOG_CLOSE_REASON_CAR_SYSTEMBAR_BUTTON = "carsystembarbutton";
    /** This string literal is from com.android.server.policy.PhoneWindowManager class. */
    private final static String DIALOG_CLOSE_REASON_HOME_KEY = "homekey";

    private final static int CAMERA_CLIENT_ID_0 = 0;
    private final static int CAMERA_CLIENT_ID_1 = 1;
    private final static int CAMERA_CLIENT_ID_DEFAULT = CAMERA_CLIENT_ID_0;

    private final static int MAX_CONCURRENT_SERVICE_TYPES = 4;

    private final static LinearLayout.LayoutParams LAYOUT_PARAMS_FOR_PREVIEW =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
            );

    private final SparseArray<ArraySet<Integer>> mNextServiceTypes = new SparseArray<>();
    private final SparseArray<SparseArray<CheckBox>> mCheckBoxes = new SparseArray<>();
    private final SparseArray<CarEvsCameraClient> mCameraClients = new SparseArray<>();

    private final Object mLock = new Object();

    /** GL backed surface view to render the camera preview */
    private CarEvsGLSurfaceView mEvsView;
    private ViewGroup mRootView;
    private LinearLayout mPreviewContainer;
    private ViewSwitcher mPreviewSwitcher;

    /** Display manager to monitor the display's state */
    private DisplayManager mDisplayManager;

    /** Current display state */
    private int mDisplayState = Display.STATE_OFF;

    /** The ID of the display we're associated with. */
    private int mDisplayId;

    @GuardedBy("mLock")
    private Car mCar;

    @GuardedBy("mLock")
    private CarEvsManager mEvsManager;

    @GuardedBy("mLock")
    private IBinder mSessionToken;

    private boolean mUseSystemWindow;
    private int mServiceType;

    /**
     * The Activity with showWhenLocked doesn't go to sleep even if the display sleeps.
     * So we'd like to monitor the display state and react on it manually.
     */
    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != mDisplayId) {
                return;
            }
            int state = decideViewVisibility();
            synchronized (mLock) {
                if (state == mDisplayState) {
                    Log.i(TAG, "Already in a target state " + state);
                    return;
                }

                mDisplayState = state;
                for (int i = 0; i < mCameraClients.size(); i++) {
                    CarEvsCameraClient client = mCameraClients.valueAt(i);
                    if (state == Display.STATE_ON) {
                        client.startVideoStream();
                    } else {
                        client.stopVideoStream();
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    String reason = extras.getString(EXTRA_DIALOG_CLOSE_REASON);
                    if (!DIALOG_CLOSE_REASON_CAR_SYSTEMBAR_BUTTON.equals(reason) &&
                        !DIALOG_CLOSE_REASON_HOME_KEY.equals(reason)) {
                        Log.i(TAG, "Ignore a request to close the system dialog with a reason = " +
                                   reason);
                        return;
                    }
                    Log.d(TAG, "Requested to close the dialog, reason = " + reason);
                }
                finish();
            } else {
                Log.e(TAG, "Unexpected intent " + intent);
            }
        }
    };

    // To close the PreviewActiivty when Home button is clicked.
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        // Need to register the receiver for all users, because we want to receive the Intent after
        // the user is changed.
        registerReceiverForAllUsers(mBroadcastReceiver, filter, /* broadcastPermission= */ null,
                /* scheduler= */ null, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        registerBroadcastReceiver();
        parseExtra(getIntent());


        setShowWhenLocked(true);
        mDisplayManager = getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        int state = decideViewVisibility();

        mDisplayId = getDisplayId();
        mRootView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.evs_preview_activity, /* root= */ null);

        addCheckBoxes();

        // Create a default camera client that runs CarEvsManager.SERVICE_TYPE_REARVIEW;
        ArraySet<Integer> types = new ArraySet<>();
        types.add(CarEvsManager.SERVICE_TYPE_REARVIEW);
        mCameraClients.put(CAMERA_CLIENT_ID_DEFAULT, new CarEvsCameraClient(this, types));

        synchronized (mLock) {
            mDisplayState = state;

            // Packaging parameters to create CarEvsGLSurfaceView. On creation, we are running the
            // rearview by default.
            ArrayList<Integer> clients = new ArrayList<>();
            clients.add(CAMERA_CLIENT_ID_DEFAULT);
            ArraySet<Integer> serviceTypes = new ArraySet<>();
            serviceTypes.add(CarEvsManager.SERVICE_TYPE_REARVIEW);
            mNextServiceTypes.put(CAMERA_CLIENT_ID_DEFAULT, serviceTypes);
            mEvsView = createCameraViewLocked(clients);
        }

        // Add a created camera view to the view switcher.
        mPreviewContainer = mRootView.findViewById(R.id.evs_switcher_view);
        mPreviewContainer.addView(mEvsView, 0);
        mPreviewSwitcher = mRootView.findViewById(R.id.evs_preview_switcher);

        // Declare in and out animations and set.
        Animation in = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        Animation out = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right);
        mPreviewSwitcher.setInAnimation(in);
        mPreviewSwitcher.setOutAnimation(out);

        // Configure buttons.
        View applyButton = mRootView.findViewById(R.id.apply_button);
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> handleButtonClicked(v));
        }

        // Configure a close button.
        View closeButton = mRootView.findViewById(R.id.close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> handleButtonClicked(v));
        }

        int width = WindowManager.LayoutParams.MATCH_PARENT;
        int height = WindowManager.LayoutParams.MATCH_PARENT;
        if (mUseSystemWindow) {
            width = getResources().getDimensionPixelOffset(R.dimen.camera_preview_width);
            height = getResources().getDimensionPixelOffset(R.dimen.camera_preview_height);
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                2020 /* WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY */,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.dimAmount = getResources().getFloat(R.dimen.config_cameraBackgroundScrim);

        if (mUseSystemWindow) {
            WindowManager wm = getSystemService(WindowManager.class);
            wm.addView(mRootView, params);
        } else {
            setContentView(mRootView, params);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        parseExtra(intent);
    }

    private void parseExtra(Intent intent) {
        Bundle extras = intent.getExtras();

        synchronized (mLock) {
            if (extras == null) {
                mSessionToken = null;
                mServiceType = CarEvsManager.SERVICE_TYPE_REARVIEW;
                mUseSystemWindow = false;
                return;
            }

            mSessionToken = extras == null ?
                    null : extras.getBinder(CarEvsManager.EXTRA_SESSION_TOKEN);
            mUseSystemWindow = mSessionToken != null;
            mServiceType = extras.getShort(Integer.toString(CarEvsManager.SERVICE_TYPE_REARVIEW));
        }
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart");
        super.onRestart();

        for (int i = 0; i < mCameraClients.size(); i++) {
            CarEvsCameraClient client = mCameraClients.valueAt(i);
            // When we come back to the top task, we start rendering the view.
            client.startVideoStream();
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        try {
            if (mUseSystemWindow && mEvsView.getWindowVisibility() == View.VISIBLE) {
                // When a new activity is launched, this activity will become the background
                // activity and, however, likely still visible to the users if it is using the
                // system window.  Therefore, we should not transition to the STOPPED state.
                //
                // Similarly, this activity continues previewing the camera when the user triggers
                // the home button.  If the users want to manually close the preview window, they
                // can trigger the close button at the bottom of the window.
                return;
            }

            for (int i = 0; i < mCameraClients.size(); i++) {
                CarEvsCameraClient client = mCameraClients.valueAt(i);
                client.stopVideoStream();
            }
        } finally {
            super.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        try {
            for (int i = 0; i < mCameraClients.size(); i++) {
                CarEvsCameraClient client = mCameraClients.valueAt(i);
                // Request to stop current service and unregister a status listener
                client.release();
            }

            mDisplayManager.unregisterDisplayListener(mDisplayListener);
            if (mUseSystemWindow) {
                WindowManager wm = getSystemService(WindowManager.class);
                wm.removeViewImmediate(mRootView);
            }

            unregisterReceiver(mBroadcastReceiver);
        } finally {
            super.onDestroy();
        }
    }

    // Hides the view when the display is off to save the system resource, since this has
    // 'showWhenLocked' attribute, this will not go to PAUSED state even if the display turns off.
    private int decideViewVisibility() {
        Display display = mDisplayManager.getDisplay(mDisplayId);
        int state = display.getState();
        Log.d(TAG, "decideShowWhenLocked: displayState=" + state);
        if (state == Display.STATE_ON) {
            getWindow().getDecorView().setVisibility(View.VISIBLE);
        } else {
            getWindow().getDecorView().setVisibility(View.INVISIBLE);
        }

        return state;
    }

    private void handleButtonClicked(View v) {
        switch (v.getId()) {
            case R.id.close_button:
                Toast toast = Toast.makeText(this, "Closing cameras...", Toast.LENGTH_LONG);
                toast.addCallback(new Toast.Callback() {
                    @Override
                    public void onToastHidden() {
                        // It is possible that we've been stopped but a video stream is still
                        // active.
                        for (int i = 0; i < mCameraClients.size(); i++) {
                            CarEvsCameraClient client = mCameraClients.valueAt(i);
                            client.release();
                        }

                        finish();
                    }

                    @Override
                    public void onToastShown() { /* Nothing to do. */ }
                });

                toast.show();
                break;

            case R.id.apply_button:
                Toast.makeText(this, "Switching the view: " + mNextServiceTypes, Toast.LENGTH_SHORT)
                        .show();
                synchronized (mLock) {
                    ArrayList<Integer> clients = new ArrayList<>();
                    clients.add(CAMERA_CLIENT_ID_0);
                    clients.add(CAMERA_CLIENT_ID_1);
                    CarEvsGLSurfaceView view = createCameraViewLocked(clients);
                    if (view == null) {
                        // Show a blank view if createCameraViewLocked() returns null.
                        mPreviewSwitcher.addView(new View(getApplication()), -1,
                                LAYOUT_PARAMS_FOR_PREVIEW);
                    } else {
                        // Switch the view; we add a newly created view at the end, switch to it,
                        // and then remove a previous view from ViewSwitcher.
                        mPreviewSwitcher.addView(view, -1, LAYOUT_PARAMS_FOR_PREVIEW);
                    }
                    mEvsView = view;
                    View currentView = mPreviewSwitcher.getCurrentView();
                    mPreviewSwitcher.showNext();
                    mPreviewSwitcher.removeView(currentView);
                }
                break;

            default:
                break;
        }
    }

    private void onCheckedChangeListener(View view, boolean isChecked) {
        int index;
        int type;
        switch (view.getId()) {
            case R.id.checkbox_rearview:
                index = CAMERA_CLIENT_ID_0;
                type = CarEvsManager.SERVICE_TYPE_REARVIEW;
                break;

            case R.id.checkbox_frontview:
                index = CAMERA_CLIENT_ID_0;
                type = CarEvsManager.SERVICE_TYPE_FRONTVIEW;
                break;

            case R.id.checkbox_leftview:
                index = CAMERA_CLIENT_ID_0;
                type = CarEvsManager.SERVICE_TYPE_LEFTVIEW;
                break;

            case R.id.checkbox_rightview:
                index = CAMERA_CLIENT_ID_0;
                type = CarEvsManager.SERVICE_TYPE_RIGHTVIEW;
                break;

            case R.id.checkbox1_rearview:
                index = CAMERA_CLIENT_ID_1;
                type = CarEvsManager.SERVICE_TYPE_REARVIEW;
                break;

            case R.id.checkbox1_frontview:
                index = CAMERA_CLIENT_ID_1;
                type = CarEvsManager.SERVICE_TYPE_FRONTVIEW;
                break;

            case R.id.checkbox1_leftview:
                index = CAMERA_CLIENT_ID_1;
                type = CarEvsManager.SERVICE_TYPE_LEFTVIEW;
                break;

            case R.id.checkbox1_rightview:
                index = CAMERA_CLIENT_ID_1;
                type = CarEvsManager.SERVICE_TYPE_RIGHTVIEW;
                break;

            default:
                return;
        }

        synchronized (mLock) {
            if (!mNextServiceTypes.contains(index)) {
                mNextServiceTypes.put(index, new ArraySet<>());
            }

            if (isChecked) {
                mNextServiceTypes.get(index).add(type);
            } else {
                mNextServiceTypes.get(index).remove(type);
            }
        }
    }

    private void addCheckBoxes() {
        // Configure checkboxes.
        mCheckBoxes.put(CAMERA_CLIENT_ID_0, new SparseArray<CheckBox>(
                /* capacity= */ MAX_CONCURRENT_SERVICE_TYPES));
        CheckBox c = mRootView.findViewById(R.id.checkbox_rearview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_0).put(CarEvsManager.SERVICE_TYPE_REARVIEW, c);

        c = mRootView.findViewById(R.id.checkbox_frontview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_0).put(CarEvsManager.SERVICE_TYPE_FRONTVIEW, c);

        c = mRootView.findViewById(R.id.checkbox_leftview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_0).put(CarEvsManager.SERVICE_TYPE_LEFTVIEW, c);

        c = mRootView.findViewById(R.id.checkbox_rightview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_0).put(CarEvsManager.SERVICE_TYPE_RIGHTVIEW, c);

        mCheckBoxes.put(CAMERA_CLIENT_ID_1, new SparseArray<CheckBox>(
                /* capacity= */ MAX_CONCURRENT_SERVICE_TYPES));
        c = mRootView.findViewById(R.id.checkbox1_rearview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_1).put(CarEvsManager.SERVICE_TYPE_REARVIEW, c);

        c = mRootView.findViewById(R.id.checkbox1_frontview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_1).put(CarEvsManager.SERVICE_TYPE_FRONTVIEW, c);

        c = mRootView.findViewById(R.id.checkbox1_leftview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_1).put(CarEvsManager.SERVICE_TYPE_LEFTVIEW, c);

        c = mRootView.findViewById(R.id.checkbox1_rightview);
        c.setOnCheckedChangeListener(this::onCheckedChangeListener);
        mCheckBoxes.get(CAMERA_CLIENT_ID_1).put(CarEvsManager.SERVICE_TYPE_RIGHTVIEW, c);
    }

    @GuardedBy("mLock")
    private CarEvsGLSurfaceView createCameraViewLocked(ArrayList<Integer> clientIds) {

        // Initialize camera clients and stop video stream if it runs.
        for (int i = 0; i < mNextServiceTypes.size(); i++) {
            int id = mNextServiceTypes.keyAt(i);
            CarEvsCameraClient client = mCameraClients.get(id);
            if (client == null) {
                client = new CarEvsCameraClient(this);
                mCameraClients.put(id, client);
            } else {
                client.stopVideoStream();
            }
        }

        // TODO(b/291770725): To avoid contentions in video stream managements on our reference
        //                    hardware, we intentionally put current thread in sleep.
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            // Nothing to do.
        }

        // Create a list of CarEvsGLSurfaceView.BufferCallback for the rendering.
        int nrows = 0;
        ArrayList<CarEvsGLSurfaceView.BufferCallback> callbacks =
                new ArrayList<>(/* capacity= */ clientIds.size());
        for (int i = 0; i < mNextServiceTypes.size(); i++) {
            int id = mNextServiceTypes.keyAt(i);
            ArraySet types = mNextServiceTypes.valueAt(i);
            if (types.isEmpty()) {
                // No type is selected for current client. Uncheck all check boxes.
                SparseArray checkBoxes = mCheckBoxes.get(id);
                for (int j = 0; j < checkBoxes.size(); j++) {
                    ((CheckBox) checkBoxes.valueAt(j)).setChecked(false);
                }
                continue;
            }

            CarEvsCameraClient client = mCameraClients.get(id);
            ArraySet<Integer> activated = client.startVideoStream(types);
            if (activated.size() < 1) {
                // We failed to start any service. Uncheck all check boxes.
                SparseArray checkBoxes = mCheckBoxes.get(id);
                for (int j = 0; j < checkBoxes.size(); j++) {
                    ((CheckBox) checkBoxes.valueAt(j)).setChecked(false);
                }
                continue;
            }

            // Update check boxes.
            SparseArray checkBoxes = mCheckBoxes.get(id);
            for (int j = 0; j < checkBoxes.size(); j++) {
                var key = checkBoxes.keyAt(j);
                if (activated.contains(key)) {
                    ((CheckBox) checkBoxes.get(key)).setChecked(true);
                    mNextServiceTypes.get(id).add(key);
                } else {
                    ((CheckBox) checkBoxes.get(key)).setChecked(false);
                    mNextServiceTypes.get(id).remove(key);
                }
            }

            callbacks.addAll(client.getBufferCallbacks());
            ++nrows;
        }

        ArrayList<float[]> positionList = new ArrayList<>();;
        float stride_y = 2.0f / nrows;

        nrows = 0;
        for (int i = 0; i < mCameraClients.size(); i++) {
            CarEvsCameraClient client = mCameraClients.valueAt(i);
            int size = client.getBufferCallbacks().size();
            if (size < 1) {
                continue;
            }

            float stride_x = 2.0f / size;
            for (int j = 0; j < size; j++) {
                float[] m = {
                      -1.0f + stride_x * j,       1.0f - stride_y * nrows,       0.0f,
                      -1.0f + stride_x * (1 + j), 1.0f - stride_y * nrows,       0.0f,
                      -1.0f + stride_x * j,       1.0f - stride_y * (1 + nrows), 0.0f,
                      -1.0f + stride_x * (1 + j), 1.0f - stride_y * (1 + nrows), 0.0f
                };
                positionList.add(m);
            }
            nrows++;
        }

        // Convert ArrayList into float[][].
        float[][] arr = new float[positionList.size()][];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = positionList.get(i).clone();
        }
        CarEvsGLSurfaceView view;
        try {
            view = CarEvsGLSurfaceView.create(getApplication(), callbacks,
                    getApplicationContext().getResources().getInteger(
                            R.integer.config_evsRearviewCameraInPlaneRotationAngle), arr);
        } catch (IllegalArgumentException err) {
            // A parameter is invalid for CarEvsGLSurfaceView instantiation.
            Log.e(TAG, "Fail to create CarEvsGLSurfaceView.");
            return null;
        }

        if (view != null) {
            view.setLayoutParams(LAYOUT_PARAMS_FOR_PREVIEW);
        }
        return view;
    }
}
