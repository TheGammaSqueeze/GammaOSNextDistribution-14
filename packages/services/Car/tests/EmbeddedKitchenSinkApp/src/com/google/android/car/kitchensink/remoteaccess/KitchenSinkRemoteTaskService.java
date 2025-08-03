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

package com.google.android.car.kitchensink.remoteaccess;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import android.app.Service;
import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehiclePropertyIds;
import android.car.hardware.property.CarPropertyManager;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.Executor;

public final class KitchenSinkRemoteTaskService extends Service {

    private static final String TAG = KitchenSinkRemoteTaskService.class.getSimpleName();

    static final String PREF_KEY = "Tasks";
    static final String TASK_TIME_KEY = "TaskTime";
    static final String TASK_ID_KEY = "TaskId";
    static final String TASK_DATA_KEY = "TaskData";
    static final String TASK_DURATION_KEY = "TaskDuration";
    private final HandlerThread mHandlerThread = new HandlerThread(getClass().getSimpleName());
    private Handler mHandler;

    private static final String SET_TEMP = "SetTemp:";

    private final RemoteTaskClient mRemoteTaskClient = new RemoteTaskClient();

    private Car mCar;
    private CarRemoteAccessManager mRemoteAccessManager;
    private CarPropertyManager mCarPropertyManager;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private SharedPreferences mSharedPref;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        synchronized (mLock) {
            mSharedPref = getDefaultSharedPreferences(this);
        }
        disconnectCar();
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    if (ready) {
                        Executor executor = getMainExecutor();
                        mRemoteAccessManager = (CarRemoteAccessManager) car.getCarManager(
                                Car.CAR_REMOTE_ACCESS_SERVICE);
                        mRemoteAccessManager.setRemoteTaskClient(executor, mRemoteTaskClient);
                        mCarPropertyManager = (CarPropertyManager) car.getCarManager(
                                Car.PROPERTY_SERVICE);
                    } else {
                        mCar = null;
                        mRemoteAccessManager = null;
                        mCarPropertyManager = null;
                    }
                });
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        disconnectCar();
        mHandlerThread.quitSafely();
    }

    private void disconnectCar() {
        if (mCar != null && mCar.isConnected()) {
            mRemoteAccessManager.clearRemoteTaskClient();
            mCar.disconnect();
            mCar = null;
        }
    }

    private void handleTaskData(String taskData) {
        if (taskData.startsWith(SET_TEMP)) {
            float temp = Float.parseFloat(taskData.substring(SET_TEMP.length()));
            Log.i(TAG, "Setting row 1 left HVAC temp to: " + temp);
            try {
                mCarPropertyManager.setFloatProperty(
                        VehiclePropertyIds.HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_LEFT,
                        temp);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set hvac temp, error: " + e);
            }
        } else {
            Log.i(TAG, "Unknown task data: " + taskData + ", do nothing");
        }
    }

    private final class RemoteTaskClient
            implements CarRemoteAccessManager.RemoteTaskClientCallback {

        @Override
        public void onRegistrationUpdated(RemoteTaskClientRegistrationInfo info) {
            Log.i(TAG, "Registration information updated: serviceId=" + info.getServiceId()
                    + ", vehicleId=" + info.getVehicleId() + ", processorId="
                    + info.getProcessorId() + ", clientId=" + info.getClientId());
        }

        @Override
        public void onRegistrationFailed() {
            Log.i(TAG, "Registration to CarRemoteAccessService failed");
        }

        @Override
        public void onRemoteTaskRequested(String taskId, byte[] data, int remainingTimeSec) {
            // Although in reality task data might not be a valid string, the test task data we use
            // are always a valid string.
            String taskDataStr = new String(data, StandardCharsets.UTF_8);

            // Lock to prevent concurrent access of shared pref.
            synchronized (mLock) {
                Log.i(TAG, "Remote task(" + taskId + ") is requested with " + remainingTimeSec
                        + " sec remaining, task data: " + taskDataStr);
                String taskListJson = mSharedPref.getString(
                        KitchenSinkRemoteTaskService.PREF_KEY, "{}");
                SharedPreferences.Editor sharedPrefEditor = mSharedPref.edit();
                sharedPrefEditor.putString(PREF_KEY, toJsonString(
                        taskListJson, taskId, taskDataStr, remainingTimeSec));
                sharedPrefEditor.apply();
            }

            handleTaskData(taskDataStr);

            // Report task done after 5s.
            mHandler.postDelayed(() -> {
                if (mRemoteAccessManager != null) {
                    mRemoteAccessManager.reportRemoteTaskDone(taskId);
                }
            }, /* delayMillis= */ 5000);
        }

        @Override
        public void onShutdownStarting(CarRemoteAccessManager.CompletableRemoteTaskFuture future) {
            mHandlerThread.quitSafely();
            future.complete();
        }

        private static String formatTime(LocalDateTime t) {
            return t.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
        }

        private static String toJsonString(String taskListJson, String taskId, String taskDataStr,
                int remainingTimeSec) {
            LocalDateTime taskTime = LocalDateTime.now(ZoneId.systemDefault());
            JSONObject jsonObject = null;
            JSONArray tasks = null;
            try {
                jsonObject = new JSONObject(taskListJson);
                tasks = (JSONArray) jsonObject.get(PREF_KEY);
            } catch (JSONException e) {
                Log.w(TAG, "task list JSON is not initialized");
            }
            try {
                if (jsonObject == null || tasks == null) {
                    jsonObject = new JSONObject();
                    tasks = new JSONArray();
                    jsonObject.put(PREF_KEY, tasks);
                }
                JSONObject task = new JSONObject();
                task.put(TASK_TIME_KEY, formatTime(taskTime));
                task.put(TASK_ID_KEY, taskId);
                task.put(TASK_DATA_KEY, taskDataStr);
                task.put(TASK_DURATION_KEY, Integer.valueOf(remainingTimeSec));
                tasks.put(task);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to convert task info to JSON", e);
                return "";
            }
            return jsonObject.toString();
        }
    }
}
