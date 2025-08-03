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

package com.google.android.car.kitchensink.audio;

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OemCarServiceTestFragment extends Fragment {
    private static final String TAG = OemCarServiceTestFragment.class.getSimpleName();
    private static final int TEST_ITERATIONS = 10;
    private Context mContext;
    private Car mCar;
    private AudioManager mAudioManager;
    private VolumeKeyEventsButtonManager mVolumeKeyEventHandler;
    private final ExecutorService mPool = Executors.newFixedThreadPool(TEST_ITERATIONS);
    private TextView mAudioFocusResultText;
    private TextView mAudioVolumeResultText;

    private void connectCar() {
        mContext = getContext();
        mCar = Car.createCar(mContext, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                });

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.oemcarservice, container, /* attachRoot= */ false);

        connectCar();

        mVolumeKeyEventHandler = new VolumeKeyEventsButtonManager(
                mCar.getCarManager(CarOccupantZoneManager.class));

        view.findViewById(R.id.oem_car_service_audio_volume_test_button).setOnClickListener(v -> {
            sendVolumeKeyEvent();
        });

        view.findViewById(R.id.oem_car_service_audio_focus_test_button).setOnClickListener(v -> {
            sendAudioFocusRequest();
        });

        mAudioFocusResultText = view.findViewById(R.id.oem_car_service_audio_focus_text);

        mAudioVolumeResultText = view.findViewById(R.id.oem_car_service_audio_volume_text);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");

        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        super.onDestroyView();
    }

    private void sendAudioFocusRequest() {
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            MediaWithDelayedFocusListener mediaWithDelayedFocusListener =
                    new MediaWithDelayedFocusListener();
            AudioFocusRequest mediaAudioFocusRequest = new AudioFocusRequest.Builder(
                    AUDIOFOCUS_GAIN)
                    .setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(
                            mediaWithDelayedFocusListener).build();
            int delayedFocusRequestResults = mAudioManager.requestAudioFocus(
                    mediaAudioFocusRequest);

            if (delayedFocusRequestResults != AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "sendAudioFocusRequest not granted " + delayedFocusRequestResults);
            }
        }

        dump("CarOemAudioFocusProxyService", mAudioFocusResultText);
        Toast.makeText(mContext,
                "Test Finished for Audio Focus Requests with " + TEST_ITERATIONS
                        + " iterations.", Toast.LENGTH_SHORT).show();
    }

    private void sendVolumeKeyEvent() {
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            mPool.execute(() -> mVolumeKeyEventHandler
                    .sendClickEvent(KeyEvent.KEYCODE_VOLUME_UP));
        }

        dump("CarOemAudioVolumeProxyService", mAudioVolumeResultText);
        Toast.makeText(mContext, "Test Finished for Volume Key Events with " + TEST_ITERATIONS
                + " iterations.", Toast.LENGTH_SHORT).show();
    }

    private void dump(String header, TextView textView) {
        Process dump;
        try {
            dump = Runtime.getRuntime().exec("dumpsys car_service --oem-service");
        } catch (IOException e) {
            Log.e(TAG, "Cannot flush", e);
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(dump.getInputStream()))) {
            String line = "";
            boolean captureDump = false;
            int sum = 0;
            float iterations = 0;
            while ((line = reader.readLine()) != null) {
                // End of execution time dump.
                if (captureDump) {
                    if (line.contains("time log complete")) {
                        break;
                    }
                    if (line.contains("startTime, duration")) {
                        continue;
                    }
                    if (line.contains(",")) {
                        sum += Integer.parseInt(line.split(",")[1].trim());
                        iterations++;
                    }
                }
                if (line.contains(header)) {
                    captureDump = true;
                }
            }
            if (iterations == 0) {
                textView.setText(getResources().getString(R.string.oem_car_service_no_results));
                return;
            }
            textView.setText(String.format(
                    getResources().getString(R.string.oem_car_service_average_execution_time),
                    sum / iterations));
        } catch (IOException e) {
            Log.e(TAG, "Cannot flush", e);
        }
    }

    private static final class MediaWithDelayedFocusListener implements
            AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "Focus changed" + focusChange);
        }
    }
}
