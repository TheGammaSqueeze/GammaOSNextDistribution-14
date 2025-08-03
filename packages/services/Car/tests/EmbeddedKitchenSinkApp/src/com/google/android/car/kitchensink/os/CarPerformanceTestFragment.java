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

package com.google.android.car.kitchensink.os;

import static android.car.PlatformVersion.VERSION_CODES.TIRAMISU_1;
import static android.car.os.ThreadPolicyWithPriority.priorityToString;
import static android.car.os.ThreadPolicyWithPriority.schedToString;

import android.car.Car;
import android.car.os.CarPerformanceManager;
import android.car.os.ThreadPolicyWithPriority;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.internal.os.BackgroundThread;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

public final class CarPerformanceTestFragment extends Fragment {
    private CarPerformanceManager mCarPerformanceManager;
    private TextView mTextView;
    private Spinner mThreadPolicySelect;
    private EditText mThreadPriorityInput;
    private static final String NOT_SUPPORTED_MESSAGE =
            " is not supported on this platform, supported from TM-QPR-1 or up";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Runnable r = () ->
                mCarPerformanceManager = ((KitchenSinkActivity) getActivity())
                        .getPerformanceManager();
        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View v = inflater.inflate(R.layout.car_performance_test, container,
                /* attachToRoot= */ false);

        mTextView = v.findViewById(R.id.thread_priority_textview);

        Button b = v.findViewById(R.id.get_thread_priority_btn);
        b.setOnClickListener(this::getThreadPriority);

        b = v.findViewById(R.id.set_thread_priority_btn);
        b.setOnClickListener(this::setThreadPriority);

        mThreadPolicySelect = v.findViewById(R.id.policy_input);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.thread_policy_list, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mThreadPolicySelect.setAdapter(adapter);

        mThreadPriorityInput = v.findViewById(R.id.priority_input);

        int pid = Process.myPid();
        ((TextView) v.findViewById(R.id.pid_value)).setText(Integer.toString(pid));
        ((TextView) v.findViewById(R.id.uid_value)).setText(Integer.toString(Process.myUid()));
        ((TextView) v.findViewById(R.id.process_group_value)).setText(Integer.toString(
                Process.getProcessGroup(pid)));
        int bgThreadTid = BackgroundThread.get().getThreadId();
        ((TextView) v.findViewById(R.id.bg_thread_process_group_value)).setText(Integer.toString(
                Process.getProcessGroup(bgThreadTid)));

        return v;
    }

    private void getThreadPriority(View v) {
        if (!Car.getPlatformVersion().isAtLeast(TIRAMISU_1)) {
            mTextView.setText("CarPerformanceManager.getThreadPriority"
                    + NOT_SUPPORTED_MESSAGE);
            return;
        }

        try {
            ThreadPolicyWithPriority p = mCarPerformanceManager.getThreadPriority();

            mTextView.setText("Current thread scheduling policy: " + schedToString(p.getPolicy())
                    + "\nCurrent thread scheduling priority: " + priorityToString(p.getPriority()));
        } catch (Exception e) {
            mTextView.setText("Failed to get thread priority, error: " + e);
        }
    }

    private void setThreadPriority(View v) {
        if (!Car.getPlatformVersion().isAtLeast(TIRAMISU_1)) {
            mTextView.setText("CarPerformanceManager.setThreadPriority"
                    + NOT_SUPPORTED_MESSAGE);
            return;
        }

        int policyPos = mThreadPolicySelect.getSelectedItemPosition();
        int policy;
        switch (policyPos) {
            case 1:
                policy = ThreadPolicyWithPriority.SCHED_FIFO;
                break;
            case 2:
                policy = ThreadPolicyWithPriority.SCHED_RR;
                break;
            default:
                policy = ThreadPolicyWithPriority.SCHED_DEFAULT;
                break;
        }

        int priority;
        try {
            priority = Integer.parseInt(mThreadPriorityInput.getText().toString());
        } catch (Exception e) {
            mTextView.setText("Thread priority must be an integer");
            return;
        }
        try {
            mCarPerformanceManager.setThreadPriority(
                    new ThreadPolicyWithPriority(policy, priority));
        } catch (Exception e) {
            mTextView.setText("Failed to set thread priority, error: " + e);
            return;
        }
        mTextView.setText("Setting thread priority succeeded");
    }
}
