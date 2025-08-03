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

package com.google.android.car.kitchensink.audiorecorder;

import static android.R.layout.simple_spinner_dropdown_item;
import static android.R.layout.simple_spinner_item;

import static com.google.android.car.kitchensink.KitchenSinkActivity.DUMP_ARG_CMD;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

public final class AudioRecorderTestFragment extends Fragment {

    public static final String FRAGMENT_NAME = "audio recorder";
    private static final String TAG = "CAR.AUDIO.RECORDER.KS";
    private static final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};
    private static final String PATTERN_FORMAT = "yyyy_MM_dd_kk_mm_ss_";

    private final Map<String, DumpCommand> mDumpCommands = Map.ofEntries(
            Map.entry("start-recording",
                    new DumpCommand("start-recording", "Starts recording audio to file.") {
                        @Override
                        boolean runCommand(IndentingPrintWriter writer) {
                            startRecording();
                            writer.println("Started recording");
                            return true;
                        }
                    }),
            Map.entry("stop-recording",
                    new DumpCommand("stop-recording", "Stops recording audio to file.") {
                        @Override
                        boolean runCommand(IndentingPrintWriter writer) {
                            stopRecording();
                            writer.println("Stopped recording");
                            return true;
                        }
                    }),
            Map.entry("start-playback",
                    new DumpCommand("start-playback", "Start audio playback.") {
                        @Override
                        boolean runCommand(IndentingPrintWriter writer) {
                            startPlayback();
                            writer.println("Started playback");
                            return true;
                        }
                    }),
            Map.entry("stop-playback",
                    new DumpCommand("stop-playback", "Stop audio playback.") {
                        @Override
                        boolean runCommand(IndentingPrintWriter writer) {
                            stopPlayback();
                            writer.println("Stopped Playback");
                            return true;
                        }
                    }),
            Map.entry("help",
                    new DumpCommand("help", "Print help information.") {
                        @Override
                        boolean runCommand(IndentingPrintWriter writer) {
                            dumpHelp(writer);
                            return true;
                        }
                    }));

    private Spinner mDeviceAddressSpinner;
    private ArrayAdapter<AudioDeviceInfoWrapper> mDeviceAddressAdapter;
    private MediaRecorder mMediaRecorder;
    private TextView mStatusTextView;
    private TextView mFilePathTextView;
    private String mFileName = "";
    private MediaPlayer mMediaPlayer;

    private final ActivityResultLauncher<String[]> mRequestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                        boolean allGranted = false;
                        for (String permission : permissions.keySet()) {
                            boolean granted = permissions.get(permission);
                            Log.d(TAG, "permission [" + permission + "] granted " + granted);
                            allGranted = allGranted && granted;
                        }

                        if (allGranted) {
                            setStatus("All Permissions Granted");
                            return;
                        }
                        setStatus("Not All Permissions Granted");
                    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.audio_recorder, container, /* attachToRoo= */ false);

        initTextViews(view);
        initButtons(view);
        initInputDevices(view);
        hasPermissionRequestIfNeeded();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");

        stopRecording();
        stopPlayback();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter writer = new IndentingPrintWriter(printWriter, /* prefix= */ "  ");
        if (args != null && args.length > 0) {
            runDumpCommand(writer, args);
            return;
        }
        writer.println(AudioRecorderTestFragment.class.getSimpleName());
        writer.increaseIndent();
        dumpRecordingState(writer);
        dumpPlaybackState(writer);
        writer.decreaseIndent();
    }

    private void runDumpCommand(IndentingPrintWriter writer, String[] args) {
        if (args.length > 1 && args[0].equals(DUMP_ARG_CMD) && mDumpCommands.containsKey(args[1])) {
            String commandString = args[1];
            DumpCommand command = mDumpCommands.get(commandString);
            if (command.supportsCommand(commandString) && command.runCommand(writer)) {
                return;
            }
        }
        dumpHelp(writer);
    }

    private void dumpHelp(IndentingPrintWriter writer) {
        writer.printf("adb shell 'dumpsys activity %s/.%s fragment \"%s\" cmd <command>'\n\n",
                KitchenSinkActivity.class.getPackage().getName(),
                KitchenSinkActivity.class.getSimpleName(),
                FRAGMENT_NAME);
        writer.increaseIndent();
        writer.printf("Supported commands: \n");
        writer.increaseIndent();
        for (DumpCommand command : mDumpCommands.values()) {
            writer.printf("%s\n", command);
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    private void dumpPlaybackState(PrintWriter writer) {
        writer.printf("Is playing: %s\n", (mMediaPlayer != null && mMediaPlayer.isPlaying()));
    }

    private void dumpRecordingState(PrintWriter writer) {
        writer.printf("Is recording: %s\n", mMediaRecorder != null);
        writer.printf("Recording path: %s\n", getFilePath());
        writer.printf("Adb command: %s\n", getFileCopyAdbCommand());
    }

    private void initTextViews(View view) {
        mStatusTextView = view.findViewById(R.id.status_text_view);
        mFilePathTextView = view.findViewById(R.id.file_path_edit);
        mFilePathTextView.setOnClickListener(v -> {
            ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
            ClipData clip = ClipData.newPlainText("adb copy command", getFileCopyAdbCommand());
            clipboard.setPrimaryClip(clip);
        });
    }

    private String getFileCopyAdbCommand() {
        return "adb pull -s " + Build.getSerial() + " " + getFilePath();
    }

    private String getFilePath() {
        return mFilePathTextView.getText().toString();
    }

    private void setStatus(String status) {
        mStatusTextView.setText(status);
        Log.d(TAG, "setStatus " + status);
    }

    private void setFilePath(String path) {
        Log.d(TAG, "setFilePath: " + path);
        mFilePathTextView.setText(path);
    }

    private void initButtons(View view) {
        Log.d(TAG, "initButtons");

        setListenerForButton(view, R.id.button_start_input, v -> startRecording());
        setListenerForButton(view, R.id.button_stop_input, v -> stopRecording());
        setListenerForButton(view , R.id.button_start_playback, v -> startPlayback());
        setListenerForButton(view, R.id.button_stop_playback, v -> stopPlayback());
    }

    private void setListenerForButton(View view, int resourceId, View.OnClickListener listener) {
        Button stopPlaybackButton = view.findViewById(resourceId);
        stopPlaybackButton.setOnClickListener(listener);
    }

    private void startPlayback() {
        Log.d(TAG, "startPlayback " + mFileName);

        if (mMediaRecorder != null) {
            setStatus("Still recording, stop first");
            return;
        }

        if (mFileName.isEmpty()) {
            setStatus("No recording available");
            return;
        }

        MediaPlayer mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(mFileName);
            mediaPlayer.setOnCompletionListener(mediaPlayer1 -> stopPlayback());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            Log.e(TAG, "startPlayback media player failed", e);
        }

        mMediaPlayer = mediaPlayer;
        setStatus("Started playback");
    }

    private void stopPlayback() {
        Log.d(TAG, "stopPlayback");

        if (mMediaPlayer == null) {
            setStatus("Playback stopped");
            return;
        }

        mMediaPlayer.stop();
        mMediaPlayer = null;
        setStatus("Stopped playback");
    }

    private boolean hasPermissionRequestIfNeeded() {
        Log.d(TAG, "hasPermissionRequestIfNeeded");

        boolean allPermissionsGranted = true;

        for (String requiredPermission : PERMISSIONS) {
            int checkValue = getContext().checkCallingOrSelfPermission(requiredPermission);
            Log.d(TAG, "hasPermissionRequestIfNeeded " + requiredPermission + " granted "
                    + (checkValue == PackageManager.PERMISSION_GRANTED));

            allPermissionsGranted = allPermissionsGranted
                    && (checkValue == PackageManager.PERMISSION_GRANTED);
        }

        if (allPermissionsGranted) {
            return true;
        }

        mRequestPermissionLauncher.launch(PERMISSIONS);
        return false;
    }

    private void initInputDevices(View view) {
        Log.d(TAG, "initInputDevices");

        AudioManager audioManager = getContext().getSystemService(AudioManager.class);

        AudioDeviceInfo[] audioDeviceInfos =
                audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        AudioDeviceInfoWrapper[] audioDeviceInfoWrappers =
                Arrays.stream(audioDeviceInfos).map(AudioDeviceInfoWrapper::new)
                .toArray(AudioDeviceInfoWrapper[]::new);

        mDeviceAddressSpinner = view.findViewById(R.id.device_spinner);

        mDeviceAddressAdapter =
                new ArrayAdapter<>(getContext(), simple_spinner_item, audioDeviceInfoWrappers);
        mDeviceAddressAdapter.setDropDownViewResource(
                simple_spinner_dropdown_item);

        mDeviceAddressSpinner.setAdapter(mDeviceAddressAdapter);

        mDeviceAddressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                stopRecording();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "onNothingSelected");
            }
        });
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording");

        if (mMediaRecorder == null) {
            setStatus("stopRecording already stopped");
            return;
        }

        mMediaRecorder.stop();
        mMediaRecorder = null;
        setStatus("stopRecording recorder stopped");
    }

    private void startRecording() {
        Log.d(TAG, "startRecording");

        if (!hasPermissionRequestIfNeeded()) {
            Log.w(TAG, "startRecording missing permission");
            return;
        }

        AudioDeviceInfoWrapper audioInputDeviceInfoWrapper = mDeviceAddressAdapter.getItem(
                mDeviceAddressSpinner.getSelectedItemPosition());

        String fileName = getFileName(audioInputDeviceInfoWrapper);

        Log.d(TAG, "startRecording file name " + fileName);

        MediaRecorder recorder = new MediaRecorder(getContext());
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setPreferredDevice(audioInputDeviceInfoWrapper.getAudioDeviceInfo());
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "startRecording prepare failed", e);
            return;
        }

        recorder.start();

        mFileName = fileName;
        mMediaRecorder = recorder;
        setFilePath(mFileName);
        setStatus("Recording Started");
    }

    private String getFileName(
            AudioDeviceInfoWrapper audioInputDeviceInfoWrapper) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT)
                .withZone(ZoneId.systemDefault());
        String shortName = formatter.format(Instant.now())
                + audioInputDeviceInfoWrapper.toStringNoSymbols();
        return getActivity().getCacheDir().getAbsolutePath() + "/" + shortName + ".mp4";
    }

    private static final class AudioDeviceInfoWrapper {

        private final AudioDeviceInfo mAudioDeviceInfo;

        AudioDeviceInfoWrapper(AudioDeviceInfo audioDeviceInfo) {
            mAudioDeviceInfo = audioDeviceInfo;
        }

        AudioDeviceInfo getAudioDeviceInfo() {
            return mAudioDeviceInfo;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder()
                    .append("Type: ")
                    .append(typeToString(mAudioDeviceInfo.getType()));

            if (!mAudioDeviceInfo.getAddress().isEmpty()) {
                builder.append(", Address: ");
                builder.append(mAudioDeviceInfo.getAddress());
            }

            return builder.toString();
        }

        public String toStringNoSymbols() {
            StringBuilder builder = new StringBuilder();

            if (!mAudioDeviceInfo.getAddress().isEmpty()) {
                builder.append("address_");
                builder.append(mAudioDeviceInfo.getAddress().replace("//s", "_"));
            } else {
                builder.append("type_");
                builder.append(typeToString(mAudioDeviceInfo.getType()));
            }

            return builder.toString();
        }

        static String typeToString(int type) {
            switch (type) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    return "MIC";
                case AudioDeviceInfo.TYPE_FM_TUNER:
                    return "FM_TUNER";
                case AudioDeviceInfo.TYPE_AUX_LINE:
                    return "AUX_LINE";
                case AudioDeviceInfo.TYPE_ECHO_REFERENCE:
                    return "ECHO_REFERENCE";
                case AudioDeviceInfo.TYPE_BUS:
                    return "BUS";
                case AudioDeviceInfo.TYPE_REMOTE_SUBMIX:
                    return "REMOTE_SUBMIX";
                default:
                    return "TYPE[" + type + "]";
            }
        }
    }

    private abstract class DumpCommand {

        private final String mDescription;
        private final String mCommand;

        DumpCommand(String command, String description) {
            mCommand = command;
            mDescription = description;
        }

        boolean supportsCommand(String command) {
            return mCommand.equals(command);
        }

        abstract boolean runCommand(IndentingPrintWriter writer);

        @Override
        public String toString() {
            return new StringBuilder()
                    .append(mCommand)
                    .append(": ")
                    .append(mDescription)
                    .toString();
        }
    }
}
