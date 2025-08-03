/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.car.bugreport;

import static com.android.car.bugreport.BugReportService.MAX_PROGRESS_VALUE;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import android.Manifest;
import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Activity that shows two types of dialogs: starting a new bug report and current status of already
 * in progress bug report.
 *
 * <p>If there is no in-progress bug report, it starts recording voice message. After clicking
 * submit button it initiates {@link BugReportService}.
 *
 * <p>If bug report is in-progress, it shows a progress bar.
 */
public class BugReportActivity extends Activity {
    private static final String TAG = BugReportActivity.class.getSimpleName();

    /** Starts {@link MetaBugReport#TYPE_AUDIO_FIRST} bugreporting. */
    private static final String ACTION_START_AUDIO_FIRST =
            "com.android.car.bugreport.action.START_AUDIO_FIRST";

    /** This is used internally by {@link BugReportService}. */
    private static final String ACTION_ADD_AUDIO =
            "com.android.car.bugreport.action.ADD_AUDIO";

    private static final int VOICE_MESSAGE_MAX_DURATION_MILLIS = 60 * 1000;
    private static final int PERMISSIONS_REQUEST_ID = 1;
    private static final ImmutableSortedSet<String> REQUIRED_PERMISSIONS = ImmutableSortedSet.of(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS);

    private static final String EXTRA_BUGREPORT_ID = "bugreport-id";

    private static final String AUDIO_FILE_EXTENSION_WAV = "wav";
    private static final String AUDIO_FILE_EXTENSION_3GPP = "3gp";

    /**
     * NOTE: mRecorder related messages are cleared when the activity finishes.
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final String mAudioFormat = isCodecSupported(MediaFormat.MIMETYPE_AUDIO_AMR_WB)
            ? MediaFormat.MIMETYPE_AUDIO_AMR_WB : MediaFormat.MIMETYPE_AUDIO_AAC;

    /** Look up string length, e.g. [ABCDEF]. */
    static final int LOOKUP_STRING_LENGTH = 6;

    private TextView mInProgressTitleText;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private TextView mAddAudioText;
    private TextView mTimerText;
    private VoiceRecordingView mVoiceRecordingView;
    private View mVoiceRecordingFinishedView;
    private View mSubmitBugReportLayout;
    private View mInProgressLayout;
    private View mShowBugReportsButton;
    private Button mSubmitButton;

    private boolean mBound;
    /** Audio message recording process started (including waiting for permission). */
    private boolean mAudioRecordingStarted;
    /** Audio recording using MIC is running (permission given). */
    private boolean mAudioRecordingIsRunning;
    private boolean mIsNewBugReport;
    private boolean mIsOnActivityStartedWithBugReportServiceBoundCalled;
    private boolean mIsSubmitButtonClicked;
    private BugReportService mService;
    private MediaRecorder mRecorder;
    private MetaBugReport mMetaBugReport;
    private File mTempAudioFile;
    private Car mCar;
    private CarDrivingStateManager mDrivingStateManager;
    private AudioManager mAudioManager;
    private AudioFocusRequest mLastAudioFocusRequest;
    private Config mConfig;
    private CountDownTimer mCountDownTimer;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BugReportService.ServiceBinder binder = (BugReportService.ServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            onActivityStartedWithBugReportServiceBound();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // called when service connection breaks unexpectedly.
            mBound = false;
        }
    };

    /**
     * Builds an intent that starts {@link BugReportActivity} to add audio message to the existing
     * bug report.
     */
    static Intent buildAddAudioIntent(Context context, int bugReportId) {
        Intent addAudioIntent = new Intent(context, BugReportActivity.class);
        addAudioIntent.setAction(ACTION_ADD_AUDIO);
        addAudioIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        addAudioIntent.putExtra(EXTRA_BUGREPORT_ID, bugReportId);
        return addAudioIntent;
    }

    static Intent buildStartBugReportIntent(Context context) {
        Intent intent = new Intent(context, BugReportActivity.class);
        intent.setAction(ACTION_START_AUDIO_FIRST);
        // Clearing is needed, otherwise multiple BugReportActivity-ies get opened and
        // MediaRecorder crashes.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Preconditions.checkState(Config.isBugReportEnabled(), "BugReport is disabled.");

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Bind to BugReportService.
        Intent intent = new Intent(this, BugReportService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mBound) {
            onActivityStartedWithBugReportServiceBound();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // If SUBMIT button is clicked, cancelling audio has been taken care of.
        if (!mIsSubmitButtonClicked) {
            cancelAudioMessageRecording();
        }
        if (mBound) {
            mService.removeBugReportProgressListener();
        }
        // Reset variables for the next onStart().
        mAudioRecordingStarted = false;
        mAudioRecordingIsRunning = false;
        mIsSubmitButtonClicked = false;
        mIsOnActivityStartedWithBugReportServiceBoundCalled = false;
        mMetaBugReport = null;
        mTempAudioFile = null;
    }

    @Override
    public void onDestroy() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        super.onDestroy();
    }

    private void onCarDrivingStateChanged(CarDrivingStateEvent event) {
        if (mShowBugReportsButton == null) {
            Log.w(TAG, "Cannot handle driving state change, UI is not ready");
            return;
        }
        // When adding audio message to the existing bugreport, do not show "Show Bug Reports"
        // button, users either should explicitly Submit or Cancel.
        if (mAudioRecordingStarted && !mIsNewBugReport) {
            mShowBugReportsButton.setVisibility(View.GONE);
            return;
        }
        if (event.eventValue == CarDrivingStateEvent.DRIVING_STATE_PARKED
                || event.eventValue == CarDrivingStateEvent.DRIVING_STATE_IDLING) {
            mShowBugReportsButton.setVisibility(View.VISIBLE);
        } else {
            mShowBugReportsButton.setVisibility(View.GONE);
        }
    }

    private void onProgressChanged(float progress) {
        int progressValue = (int) progress;
        mProgressBar.setProgress(progressValue);
        mProgressText.setText(progressValue + "%");
        if (progressValue == MAX_PROGRESS_VALUE) {
            mInProgressTitleText.setText(R.string.bugreport_dialog_in_progress_title_finished);
        }
    }

    private void prepareUi() {
        if (mSubmitBugReportLayout != null) {
            return;
        }
        setContentView(R.layout.bug_report_activity);

        // Connect to the services here, because they are used only when showing the dialog.
        // We need to minimize system state change when performing TYPE_AUDIO_LATER bug report.
        mConfig = Config.create();
        mCar = Car.createCar(this, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, this::onCarLifecycleChanged);

        mInProgressTitleText = findViewById(R.id.in_progress_title_text);
        mProgressBar = findViewById(R.id.progress_bar);
        mProgressText = findViewById(R.id.progress_text);
        mAddAudioText = findViewById(R.id.bug_report_add_audio_to_existing);
        mVoiceRecordingView = findViewById(R.id.voice_recording_view);
        mTimerText = findViewById(R.id.voice_recording_timer_text_view);
        mVoiceRecordingFinishedView = findViewById(R.id.voice_recording_finished_text_view);
        mSubmitBugReportLayout = findViewById(R.id.submit_bug_report_layout);
        mInProgressLayout = findViewById(R.id.in_progress_layout);
        mShowBugReportsButton = findViewById(R.id.button_show_bugreports);
        mSubmitButton = findViewById(R.id.button_submit);

        mShowBugReportsButton.setOnClickListener(this::buttonShowBugReportsClick);
        mSubmitButton.setOnClickListener(this::buttonSubmitClick);
        findViewById(R.id.button_cancel).setOnClickListener(this::buttonCancelClick);
        findViewById(R.id.button_close).setOnClickListener(this::buttonCancelClick);
        findViewById(R.id.button_record_again).setOnClickListener(this::buttonRecordAgainClick);

        if (mIsNewBugReport) {
            mSubmitButton.setText(R.string.bugreport_dialog_submit);
        } else {
            mSubmitButton.setText(mConfig.isAutoUpload()
                    ? R.string.bugreport_dialog_upload : R.string.bugreport_dialog_save);
        }
    }

    private void onCarLifecycleChanged(Car car, boolean ready) {
        if (!ready) {
            mDrivingStateManager = null;
            mCar = null;
            Log.d(TAG, "Car service is not ready, ignoring");
            // If car service is not ready for this activity, just ignore it - as it's only
            // used to control UX restrictions.
            return;
        }
        try {
            mDrivingStateManager = (CarDrivingStateManager) car.getCarManager(
                    Car.CAR_DRIVING_STATE_SERVICE);
            mDrivingStateManager.registerListener(
                    BugReportActivity.this::onCarDrivingStateChanged);
            // Call onCarDrivingStateChanged(), because it's not called when Car is connected.
            onCarDrivingStateChanged(mDrivingStateManager.getCurrentCarDrivingState());
        } catch (CarNotConnectedException e) {
            Log.w(TAG, "Failed to get CarDrivingStateManager", e);
        }
    }

    private void showInProgressUi() {
        mSubmitBugReportLayout.setVisibility(View.GONE);
        mInProgressLayout.setVisibility(View.VISIBLE);
        mInProgressTitleText.setText(R.string.bugreport_dialog_in_progress_title);
        onProgressChanged(mService.getBugReportProgress());
    }

    private void showSubmitBugReportUi(boolean isRecording) {
        mSubmitBugReportLayout.setVisibility(View.VISIBLE);
        mInProgressLayout.setVisibility(View.GONE);
        if (isRecording) {
            mVoiceRecordingFinishedView.setVisibility(View.GONE);
            mVoiceRecordingView.setVisibility(View.VISIBLE);
            mTimerText.setVisibility(View.VISIBLE);
        } else {
            mVoiceRecordingFinishedView.setVisibility(View.VISIBLE);
            mVoiceRecordingView.setVisibility(View.GONE);
            mTimerText.setVisibility(View.GONE);
        }
        // NOTE: mShowBugReportsButton visibility is also handled in #onCarDrivingStateChanged().
        mShowBugReportsButton.setVisibility(View.GONE);
        if (mDrivingStateManager != null) {
            try {
                onCarDrivingStateChanged(mDrivingStateManager.getCurrentCarDrivingState());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to get current driving state.", e);
            }
        }
    }

    /**
     * Initializes MetaBugReport in a local DB and starts audio recording.
     *
     * <p>This method expected to be called when the activity is started and bound to the service.
     */
    private void onActivityStartedWithBugReportServiceBound() {
        if (mIsOnActivityStartedWithBugReportServiceBoundCalled) {
            return;
        }
        mIsOnActivityStartedWithBugReportServiceBoundCalled = true;

        if (mService.isCollectingBugReport()) {
            Log.i(TAG, "Bug report is already being collected.");
            mService.setBugReportProgressListener(this::onProgressChanged);
            prepareUi();
            showInProgressUi();
            return;
        }

        if (ACTION_START_AUDIO_FIRST.equals(getIntent().getAction())) {
            Log.i(TAG, "Starting a TYPE_AUDIO_FIRST bugreport.");
            createNewBugReportWithAudioMessage();
        } else if (ACTION_ADD_AUDIO.equals(getIntent().getAction())) {
            addAudioToExistingBugReport(
                    getIntent().getIntExtra(EXTRA_BUGREPORT_ID, /* defaultValue= */ -1));
        } else {
            Log.w(TAG, "Unsupported intent action provided: " + getIntent().getAction());
            finish();
        }
    }

    private String getAudioFileExtension() {
        if (mAudioFormat.equals(MediaFormat.MIMETYPE_AUDIO_AMR_WB)) {
            return AUDIO_FILE_EXTENSION_WAV;
        }
        return AUDIO_FILE_EXTENSION_3GPP;
    }

    private void addAudioToExistingBugReport(int existingBugReportId) {
        MetaBugReport existingBugReport = BugStorageUtils.findBugReport(this,
                existingBugReportId).orElseThrow(() -> new RuntimeException(
                "Failed to find bug report with id " + existingBugReportId));
        Log.i(TAG, "Adding audio to the existing bugreport " + existingBugReport.getTimestamp());
        startAudioMessageRecording(/* isNewBugReport= */ false, existingBugReport,
                createTempAudioFileInCacheDirectory());
    }

    private void createNewBugReportWithAudioMessage() {
        MetaBugReport newBugReport = createBugReport(this, MetaBugReport.TYPE_AUDIO_FIRST);
        startAudioMessageRecording(/* isNewBugReport= */ true, newBugReport,
                createTempAudioFileInCacheDirectory());
    }

    /**
     * Creates a temporary audio file in cache directory for voice recording.
     *
     * For example, /data/user/10/com.android.car.bugreport/cache/audio1128264677920904030.wav
     */
    private File createTempAudioFileInCacheDirectory() {
        try {
            return File.createTempFile("audio", "." + getAudioFileExtension(),
                    getCacheDir());
        } catch (IOException e) {
            throw new RuntimeException("failed to create temp audio file", e);
        }
    }

    /** Shows a dialog UI and starts recording audio message. */
    private void startAudioMessageRecording(
            boolean isNewBugReport, MetaBugReport bugReport, File tempAudioFile) {
        if (mAudioRecordingStarted) {
            Log.i(TAG, "Audio message recording is already started.");
            return;
        }
        mAudioRecordingStarted = true;

        // Close the notification shade and other dialogs when showing the audio record dialog.
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        mAudioManager = getSystemService(AudioManager.class);
        mIsNewBugReport = isNewBugReport;
        mMetaBugReport = bugReport;
        mTempAudioFile = tempAudioFile;
        prepareUi();
        showSubmitBugReportUi(/* isRecording= */ true);
        if (isNewBugReport) {
            mAddAudioText.setVisibility(View.GONE);
        } else {
            mAddAudioText.setVisibility(View.VISIBLE);
            mAddAudioText.setText(String.format(
                    getString(R.string.bugreport_dialog_add_audio_to_existing),
                    mMetaBugReport.getTimestamp()));
        }

        ImmutableList<String> missingPermissions = findMissingPermissions();
        if (missingPermissions.isEmpty()) {
            startRecordingWithPermission();
        } else {
            requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]),
                    PERMISSIONS_REQUEST_ID);
        }
    }

    /**
     * Finds required permissions not granted.
     */
    private ImmutableList<String> findMissingPermissions() {
        return REQUIRED_PERMISSIONS.stream().filter(permission -> checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED).collect(
                collectingAndThen(toList(), ImmutableList::copyOf));
    }

    /**
     * Cancels bugreporting by stopping audio recording and deleting temp audio file.
     */
    private void cancelAudioMessageRecording() {
        // If audio recording is not running, most likely there were permission issues,
        // so leave the bugreport as is without cancelling it.
        if (!mAudioRecordingIsRunning) {
            Log.w(TAG, "Cannot cancel, audio recording is not running.");
            return;
        }
        stopAudioRecording();
        if (mIsNewBugReport) {
            BugStorageUtils.setBugReportStatus(
                    this, mMetaBugReport, Status.STATUS_USER_CANCELLED, "");
            Log.i(TAG, "Bug report " + mMetaBugReport.getTimestamp() + " is cancelled");
        }
        new DeleteFilesAndDirectoriesAsyncTask().execute(mTempAudioFile);
        mAudioRecordingStarted = false;
        mAudioRecordingIsRunning = false;
    }

    private void buttonCancelClick(View view) {
        finish();
    }

    private void buttonRecordAgainClick(View view) {
        stopAudioRecording();
        showSubmitBugReportUi(/* isRecording= */ true);
        startRecordingWithPermission();
    }

    private void buttonSubmitClick(View view) {
        stopAudioRecording();
        mIsSubmitButtonClicked = true;

        new AddAudioToBugReportAsyncTask(this, mConfig, mMetaBugReport, mTempAudioFile,
                mIsNewBugReport).execute();

        setResult(Activity.RESULT_OK);
        finish();
    }


    /**
     * Starts {@link BugReportInfoActivity} and finishes current activity, so it won't be running
     * in the background and closing {@link BugReportInfoActivity} will not open the current
     * activity again.
     */
    private void buttonShowBugReportsClick(View view) {
        // First cancel the audio recording, then delete the bug report from database.
        cancelAudioMessageRecording();
        // Delete the bugreport from database, otherwise pressing "Show Bugreports" button will
        // create unnecessary cancelled bugreports.
        if (mMetaBugReport != null) {
            BugStorageUtils.completeDeleteBugReport(this, mMetaBugReport.getId());
        }
        Intent intent = new Intent(this, BugReportInfoActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSIONS_REQUEST_ID) {
            return;
        }

        ImmutableList<String> missingPermissions = findMissingPermissions();
        if (missingPermissions.isEmpty()) {
            // Start recording from UI thread, otherwise when MediaRecord#start() fails,
            // stack trace gets confusing.
            mHandler.post(this::startRecordingWithPermission);
        } else {
            handleMissingPermissions(missingPermissions);
        }
    }

    private void handleMissingPermissions(ImmutableList missingPermissions) {
        String text = this.getText(R.string.toast_permissions_denied) + " : "
                + String.join(", ", missingPermissions);
        Log.w(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        if (mMetaBugReport == null) {
            finish();
            return;
        }
        if (mIsNewBugReport) {
            BugStorageUtils.setBugReportStatus(this, mMetaBugReport,
                    Status.STATUS_USER_CANCELLED, text);
        } else {
            BugStorageUtils.setBugReportStatus(this, mMetaBugReport,
                    Status.STATUS_AUDIO_PENDING, text);
        }
        finish();
    }

    private boolean isCodecSupported(String codec) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            for (String mimeType : codecInfo.getSupportedTypes()) {
                if (mimeType.equalsIgnoreCase(codec)) {
                    return true;
                }
            }
        }
        return false;
    }

    private MediaRecorder createMediaRecorder() {
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        if (mAudioFormat.equals(MediaFormat.MIMETYPE_AUDIO_AMR_WB)) {
            Log.i(TAG, "Audio encoding is selected to AMR_WB");
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
        } else {
            Log.i(TAG, "Audio encoding is selected to AAC");
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }
        mediaRecorder.setAudioSamplingRate(16000);
        mediaRecorder.setOnInfoListener((MediaRecorder recorder, int what, int extra) ->
                Log.i(TAG, "OnMediaRecorderInfo: what=" + what + ", extra=" + extra));
        mediaRecorder.setOnErrorListener((MediaRecorder recorder, int what, int extra) ->
                Log.i(TAG, "OnMediaRecorderError: what=" + what + ", extra=" + extra));
        mediaRecorder.setOutputFile(mTempAudioFile);
        return mediaRecorder;
    }

    private void startRecordingWithPermission() {
        Log.i(TAG, "Started voice recording, and saving audio to " + mTempAudioFile);

        mLastAudioFocusRequest = new AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(focusChange ->
                        Log.d(TAG, "AudioManager focus change " + focusChange))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAcceptsDelayedFocusGain(true)
                .build();
        int focusGranted = Objects.requireNonNull(mAudioManager)
                .requestAudioFocus(mLastAudioFocusRequest);
        // NOTE: We will record even if the audio focus was not granted.
        Log.d(TAG,
                "AudioFocus granted " + (focusGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));

        mRecorder = createMediaRecorder();

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Failed on MediaRecorder#prepare(), filename: " + mTempAudioFile, e);
            finish();
            return;
        }

        mCountDownTimer = createCountDownTimer();
        mCountDownTimer.start();

        mRecorder.start();
        mVoiceRecordingView.setRecorder(mRecorder);
        mAudioRecordingIsRunning = true;
    }

    private CountDownTimer createCountDownTimer() {
        return new CountDownTimer(VOICE_MESSAGE_MAX_DURATION_MILLIS, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                String secondText = secondsRemaining > 1 ? "seconds" : "second";
                mTimerText.setText(String.format(Locale.US, "%d %s remaining", secondsRemaining,
                        secondText));
            }

            public void onFinish() {
                Log.i(TAG, "Timed out while recording voice message.");
                stopAudioRecording();
                showSubmitBugReportUi(/* isRecording= */ false);
            }
        };
    }

    private void stopAudioRecording() {
        mCountDownTimer.cancel();
        if (mRecorder != null) {
            Log.i(TAG, "Recording ended, stopping the MediaRecorder.");
            try {
                mRecorder.stop();
            } catch (RuntimeException e) {
                // Sometimes MediaRecorder doesn't start and stopping it throws an error.
                // We just log these cases, no need to crash the app.
                Log.w(TAG, "Couldn't stop media recorder", e);
            }
            mRecorder.release();
            mRecorder = null;
        }
        if (mLastAudioFocusRequest != null) {
            int focusAbandoned = Objects.requireNonNull(mAudioManager)
                    .abandonAudioFocusRequest(mLastAudioFocusRequest);
            Log.d(TAG, "Audio focus abandoned "
                    + (focusAbandoned == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
            mLastAudioFocusRequest = null;
        }
        mVoiceRecordingView.setRecorder(null);
    }

    private static String getCurrentUserName(Context context) {
        UserManager um = UserManager.get(context);
        return um.getUserName();
    }

    /**
     * Creates a {@link MetaBugReport} and saves it in a local sqlite database.
     *
     * @param context an Android context.
     * @param type    bug report type, {@link MetaBugReport.BugReportType}.
     */
    static MetaBugReport createBugReport(Context context, int type) {
        String timestamp = MetaBugReport.toBugReportTimestamp(new Date());
        String username = getCurrentUserName(context);
        String title = BugReportTitleGenerator.generateBugReportTitle(timestamp, username);
        return BugStorageUtils.createBugReport(context, title, timestamp, username, type);
    }

    /** A helper class to generate bugreport title. */
    private static final class BugReportTitleGenerator {
        /** Contains easily readable characters. */
        private static final char[] CHARS_FOR_RANDOM_GENERATOR =
                new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P',
                        'R', 'S', 'T', 'U', 'W', 'X', 'Y', 'Z'};

        /**
         * Generates a bugreport title from given timestamp and username.
         *
         * <p>Example: "[A45E8] Feedback from user Driver at 2019-09-21_12:00:00"
         */
        static String generateBugReportTitle(String timestamp, String username) {
            // Lookup string is used to search a bug in Buganizer (see b/130915969).
            String lookupString = generateRandomString(LOOKUP_STRING_LENGTH);
            return "[" + lookupString + "] Feedback from user " + username + " at " + timestamp;
        }

        private static String generateRandomString(int length) {
            Random random = new Random();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                int randomIndex = random.nextInt(CHARS_FOR_RANDOM_GENERATOR.length);
                builder.append(CHARS_FOR_RANDOM_GENERATOR[randomIndex]);
            }
            return builder.toString();
        }
    }

    /** AsyncTask that recursively deletes files and directories. */
    private static class DeleteFilesAndDirectoriesAsyncTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... files) {
            for (File file : files) {
                Log.i(TAG, "Deleting " + file.getAbsolutePath());
                if (file.isFile()) {
                    file.delete();
                } else {
                    FileUtils.deleteDirectory(file);
                }
            }
            return null;
        }
    }

    /**
     * AsyncTask that moves temp audio file to the system user's {@link FileUtils#getPendingDir}.
     * Once the task is completed, it either starts ACTION_COLLECT_BUGREPORT or updates the status
     * to STATUS_UPLOAD_PENDING or STATUS_PENDING_USER_ACTION.
     */
    private static class AddAudioToBugReportAsyncTask extends AsyncTask<Void, Void, Void> {
        private final Context mContext;
        private final Config mConfig;
        private final File mTempAudioFile;
        private boolean mIsNewBugReport;
        private final boolean mIsFirstRecording;
        private MetaBugReport mBugReport;

        AddAudioToBugReportAsyncTask(
                Context context, Config config, MetaBugReport bugReport, File tempAudioFile,
                boolean isNewBugReport) {
            mContext = context;
            mConfig = config;
            mBugReport = bugReport;
            mTempAudioFile = tempAudioFile;
            mIsNewBugReport = isNewBugReport;
            mIsFirstRecording = Strings.isNullOrEmpty(mBugReport.getAudioFileName());
        }

        @Override
        protected Void doInBackground(Void... voids) {
            String audioFileName = createFinalAudioFileName();
            mBugReport = BugStorageUtils.update(mContext,
                    mBugReport.toBuilder().setAudioFileName(audioFileName).build());
            try (OutputStream out = BugStorageUtils.openAudioMessageFileToWrite(mContext,
                    mBugReport);
                 InputStream input = new FileInputStream(mTempAudioFile)) {
                ByteStreams.copy(input, out);
            } catch (IOException e) {
                // Allow user to try again if it fails to write audio.
                BugStorageUtils.setBugReportStatus(mContext, mBugReport,
                        com.android.car.bugreport.Status.STATUS_AUDIO_PENDING,
                        "Failed to write audio to bug report");
                Log.e(TAG, "Failed to write audio to bug report", e);
                return null;
            }

            mTempAudioFile.delete();
            return null;
        }

        /**
         * Creates a final audio file name from temp audio file.
         *
         * For example,
         * audio1128264677920904030.wav -> bugreport-Driver@2023-07-03_02-55-12-TLBZUR-message.wav
         */
        private String createFinalAudioFileName() {
            String audioFileExtension = mTempAudioFile.getName().substring(
                    mTempAudioFile.getName().lastIndexOf(".") + 1);
            String audioTimestamp = MetaBugReport.toBugReportTimestamp(new Date());
            return FileUtils.getAudioFileName(audioTimestamp, mBugReport, audioFileExtension);
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            if (mIsNewBugReport) {
                Log.i(TAG, "Starting bugreport service.");
                startBugReportCollection(mBugReport.getId());
            } else {
                if (mConfig.isAutoUpload()) {
                    BugStorageUtils.setBugReportStatus(mContext, mBugReport,
                            com.android.car.bugreport.Status.STATUS_UPLOAD_PENDING, "");
                } else {
                    BugStorageUtils.setBugReportStatus(mContext, mBugReport,
                            com.android.car.bugreport.Status.STATUS_PENDING_USER_ACTION, "");

                    // If audio file name already exists, no need to show the finish notification
                    // again for audio replacement.
                    if (mIsFirstRecording) {
                        BugReportService.showBugReportFinishedNotification(mContext, mBugReport);
                    }
                }
            }
        }

        /** Starts the {@link BugReportService} to collect bug report. */
        private void startBugReportCollection(int bugReportId) {
            Intent intent = new Intent(mContext, BugReportService.class);
            intent.setAction(BugReportService.ACTION_COLLECT_BUGREPORT);
            intent.putExtra(BugReportService.EXTRA_META_BUG_REPORT_ID, bugReportId);
            mContext.startForegroundService(intent);
        }
    }
}
