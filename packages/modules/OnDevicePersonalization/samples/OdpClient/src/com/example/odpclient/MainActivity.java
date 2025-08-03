/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.example.odpclient;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.Trace;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final String TAG = "OdpClient";

    private EditText mTextBox;
    private Button mGetAdButton;
    private EditText mScheduleTrainingTextBox;
    private EditText mScheduleIntervalTextBox;
    private Button mScheduleTrainingButton;
    private Button mCancelTrainingButton;
    private EditText mReportConversionTextBox;
    private Button mReportConversionButton;
    private SurfaceView mRenderedView;
    private Context mContext;
    private static Executor sCallbackExecutor = Executors.newSingleThreadExecutor();

    class SurfaceCallback implements SurfaceHolder.Callback {
        @Override public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated");
        }
        @Override public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed");
        }
        @Override public void surfaceChanged(
                SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();
        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setVisibility(View.INVISIBLE);
        mRenderedView.getHolder().addCallback(new SurfaceCallback());
        mGetAdButton = findViewById(R.id.get_ad_button);
        mScheduleTrainingButton = findViewById(R.id.schedule_training_button);
        mCancelTrainingButton = findViewById(R.id.cancel_training_button);
        mReportConversionButton = findViewById(R.id.report_conversion_button);
        mTextBox = findViewById(R.id.text_box);
        mScheduleTrainingTextBox = findViewById(R.id.schedule_training_text_box);
        mScheduleIntervalTextBox = findViewById(R.id.schedule_interval_text_box);
        mReportConversionTextBox = findViewById(R.id.report_conversion_text_box);
        registerGetAdButton();
        registerScheduleTrainingButton();
        registerReportConversionButton();
        registerCancelTrainingButton();
    }

    private void registerGetAdButton() {
        mGetAdButton.setOnClickListener(
                v -> makeRequest());
    }

    private void registerReportConversionButton() {
        mReportConversionButton.setOnClickListener(v -> reportConversion());
    }

    private OnDevicePersonalizationManager getOdpManager() {
        return mContext.getSystemService(OnDevicePersonalizationManager.class);
    }

    private void makeRequest() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.get_ad)
                    + " with " + mTextBox.getHint().toString() + ": "
                    + mTextBox.getText().toString());
            AtomicReference<ExecuteResult> slotResultHandle = new AtomicReference<>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("keyword", mTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                        "com.example.odpsamplenetwork",
                        "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            if (result != null) {
                                slotResultHandle.set(result);
                            } else {
                                Log.e(TAG, "No results!");
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "makeRequest:odpManager.execute wait success");

            Trace.beginAsyncSection("OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
            odpManager.requestSurfacePackage(
                    slotResultHandle.get().getSurfacePackageToken(),
                    mRenderedView.getHostToken(),
                    getDisplay().getDisplayId(),
                    mRenderedView.getWidth(),
                    mRenderedView.getHeight(),
                    sCallbackExecutor,
                    new OutcomeReceiver<SurfacePackage, Exception>() {
                        @Override
                        public void onResult(SurfacePackage surfacePackage) {
                            Trace.endAsyncSection(
                                    "OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
                            Log.i(TAG,
                                    "requestSurfacePackage() success: "
                                    + surfacePackage.toString());
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (surfacePackage != null) {
                                    mRenderedView.setChildSurfacePackage(
                                            surfacePackage);
                                }
                                mRenderedView.setZOrderOnTop(true);
                                mRenderedView.setVisibility(View.VISIBLE);
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
                            makeToast("requestSurfacePackage() error: " + e.toString());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void registerScheduleTrainingButton() {
        mScheduleTrainingButton.setOnClickListener(
                v -> scheduleTraining());
    }

    private void scheduleTraining() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.schedule_training)
                    + " with " + mScheduleTrainingTextBox.getHint().toString() + ": "
                    + mScheduleTrainingTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("schedule_training", mScheduleTrainingTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:scheduleTraining:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            "com.example.odpsamplenetwork",
                            "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:scheduleTraining:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:scheduleTraining:odpManager.execute", 0);
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "scheduleTraining:odpManager.execute wait success");
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void registerCancelTrainingButton() {
        mCancelTrainingButton.setOnClickListener(
                v -> cancelTraining());
    }

    private void cancelTraining() {
        Log.d(TAG, "Odp Client Cancel Training called!");
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.cancel_training)
                    + " with " + mScheduleTrainingTextBox.getHint().toString() + ": "
                    + mScheduleTrainingTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("cancel_training", mScheduleTrainingTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:cancelTraining:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            "com.example.odpsamplenetwork",
                            "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:cancelTraining:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:cancelTraining:odpManager.execute", 0);
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "cancelTraining:odpManager.execute wait success");
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void reportConversion() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.report_conversion)
                    + " with " + mReportConversionTextBox.getHint().toString() + ": "
                    + mReportConversionTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("conversion_ad_id", mReportConversionTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:reportConversion:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            "com.example.odpsamplenetwork",
                            "com.example.odpsamplenetwork.SampleService"),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:reportConversion:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:reportConversion:odpManager.execute", 0);
                            makeToast("execute() error: " + e.toString());
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "reportConversion:odpManager.execute wait success");
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void makeToast(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }
}
