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

import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_ENTER_GARAGE_MODE;
import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_CUSTOM;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;

import android.car.Car;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.CarRemoteAccessManager.InVehicleTaskScheduler;
import android.car.remoteaccess.CarRemoteAccessManager.ScheduleInfo;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RemoteAccessTestFragment extends Fragment {

    private static final String TAG = KitchenSinkRemoteTaskService.class.getSimpleName();

    private SharedPreferences mSharedPref;

    private Car mCar;
    private CarRemoteAccessManager mRemoteAccessManager;
    private AtomicInteger mScheduleId = new AtomicInteger(0);
    private Spinner mTaskType;
    private EditText mRemoteTaskDataView;
    private EditText mTaskDelayView;
    private EditText mTaskRepeatView;
    private EditText mTaskIntervalView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPref = getDefaultSharedPreferences(getContext());
        disconnectCar();
        mCar = Car.createCar(getContext(), /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    if (ready) {
                        mRemoteAccessManager = (CarRemoteAccessManager) car.getCarManager(
                                Car.CAR_REMOTE_ACCESS_SERVICE);
                    } else {
                        mCar = null;
                        mRemoteAccessManager = null;
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View v = inflater.inflate(R.layout.remote_access, container,
                /* attachToRoot= */ false);

        v.findViewById(R.id.refresh_remote_task_btn).setOnClickListener(this::refresh);
        v.findViewById(R.id.clear_remote_task_btn).setOnClickListener(this::clear);
        v.findViewById(R.id.schedule_task_btn).setOnClickListener(this::scheduleTask);

        Spinner taskTypeSpinner = (Spinner) v.findViewById(R.id.remote_task_type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.remote_task_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        taskTypeSpinner.setAdapter(adapter);

        mRemoteTaskDataView = (EditText) v.findViewById(R.id.remote_task_data);
        mTaskDelayView = (EditText) v.findViewById(R.id.task_delay);
        mTaskType = taskTypeSpinner;
        mTaskRepeatView = (EditText) v.findViewById(R.id.task_repeat);
        mTaskIntervalView = (EditText) v.findViewById(R.id.task_interval);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        refresh(getView());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnectCar();
    }

    private void disconnectCar() {
        if (mCar != null && mCar.isConnected()) {
            mRemoteAccessManager.clearRemoteTaskClient();
            mCar.disconnect();
            mCar = null;
        }
    }

    private static class TaskInfo {
        public String taskTime;
        public String taskId;
        public String taskData;
        public int remainingTimeSec;
    }

    private void createNewTd(TableRow taskRow, String tag, String text) {
        TextView tdView = new TextView(getContext());
        tdView.setLayoutParams(new TableRow.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        tdView.setTag(tag);
        tdView.setText(text);
        taskRow.addView(tdView);
    }

    private void showTaskInfoList(List<TaskInfo> tasks) {
        TableLayout taskList = (TableLayout) getView().findViewById(R.id.remote_task_list);
        // Remove all rows except the first one.
        while (taskList.getChildCount() > 1) {
            taskList.removeView(taskList.getChildAt(taskList.getChildCount() - 1));
        }
        for (TaskInfo info : tasks) {
            TableRow taskRow;
            taskRow = new TableRow(getContext());
            int viewId = View.generateViewId();
            taskRow.setId(viewId);
            taskRow.setLayoutParams(new TableRow.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            createNewTd(taskRow, "taskTime", info.taskTime);
            createNewTd(taskRow, "taskId", info.taskId);
            createNewTd(taskRow, "taskData", info.taskData);
            createNewTd(taskRow, "remainingTimeSec", String.valueOf(info.remainingTimeSec));
            taskList.addView(taskRow);
        }
    }

    private void refresh(View v) {
        String taskListJson = mSharedPref.getString(KitchenSinkRemoteTaskService.PREF_KEY, "{}");
        System.out.println(taskListJson);
        List<TaskInfo> tasks = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(taskListJson);
            JSONArray taskList = (JSONArray) jsonObject.get(KitchenSinkRemoteTaskService.PREF_KEY);
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = (JSONObject) taskList.get(i);
                TaskInfo taskInfo = new TaskInfo();
                taskInfo.taskTime = task.getString(KitchenSinkRemoteTaskService.TASK_TIME_KEY);
                taskInfo.taskId = task.getString(KitchenSinkRemoteTaskService.TASK_ID_KEY);
                taskInfo.taskData = task.getString(
                        KitchenSinkRemoteTaskService.TASK_DATA_KEY);
                taskInfo.remainingTimeSec = task.getInt(
                        KitchenSinkRemoteTaskService.TASK_DURATION_KEY);
                tasks.add(taskInfo);
            }
        } catch (JSONException e) {
            Log.e(TAG, "failed to parse task JSON: " + taskListJson, e);
        }
        showTaskInfoList(tasks);
    }

    private void clear(View v) {
        mSharedPref.edit().putString(KitchenSinkRemoteTaskService.PREF_KEY, "{}").apply();
        refresh(v);
    }

    private void scheduleTask(View v) {
        Log.e(TAG, "scheduleTask");
        InVehicleTaskScheduler taskScheduler = mRemoteAccessManager.getInVehicleTaskScheduler();
        if (taskScheduler == null) {
            Log.e(TAG, "Task scheduling is not supported");
            return;
        }
        int taskTypePos = mTaskType.getSelectedItemPosition();
        String taskData = mRemoteTaskDataView.getText().toString();
        if (taskTypePos != 0 && taskData.length() == 0) {
            Log.e(TAG, "No task data specified");
            return;
        }
        int delay = Integer.parseInt(mTaskDelayView.getText().toString());
        long startTimeInEpochSeconds = (long) (System.currentTimeMillis() / 1000) + (long) delay;
        String scheduleId = "scheduleId" + mScheduleId.getAndIncrement();
        ScheduleInfo.Builder scheduleInfoBuilder;
        if (taskTypePos == 0) {
            // Enter garage mode.
            scheduleInfoBuilder = new ScheduleInfo.Builder(
                    scheduleId, TASK_TYPE_ENTER_GARAGE_MODE, startTimeInEpochSeconds);
        } else {
            if (taskTypePos == 1) {
                taskData = "SetTemp:" + Float.parseFloat(taskData);
            }
            scheduleInfoBuilder = new ScheduleInfo.Builder(
                    scheduleId, TASK_TYPE_CUSTOM, startTimeInEpochSeconds)
                    .setTaskData(taskData.getBytes());
        }

        int count = Integer.parseInt(mTaskRepeatView.getText().toString());
        scheduleInfoBuilder.setCount(count);
        if (count > 1) {
            int taskInterval = Integer.parseInt(mTaskIntervalView.getText().toString());
            scheduleInfoBuilder.setPeriodic(Duration.ofSeconds(taskInterval));
        }
        try {
            Log.i(TAG, "Scheduling task to be executed");
            taskScheduler.scheduleTask(scheduleInfoBuilder.build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule task: " + e);
            return;
        }
        Log.i(TAG, "Task scheduled successfully");
    }
}
