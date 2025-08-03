/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.voiceinteraction.common;

import static android.service.voice.HotwordAudioStream.KEY_AUDIO_STREAM_COPY_BUFFER_LENGTH_BYTES;

import android.app.VoiceInteractor.PickOptionRequest.Option;
import android.content.LocusId;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.service.voice.HotwordAudioStream;
import android.service.voice.HotwordDetectedResult;
import android.util.Log;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public class Utils {
    public enum TestCaseType {
        COMPLETION_REQUEST_TEST,
        COMPLETION_REQUEST_CANCEL_TEST,
        CONFIRMATION_REQUEST_TEST,
        CONFIRMATION_REQUEST_CANCEL_TEST,
        ABORT_REQUEST_TEST,
        ABORT_REQUEST_CANCEL_TEST,
        PICKOPTION_REQUEST_TEST,
        PICKOPTION_REQUEST_CANCEL_TEST,
        COMMANDREQUEST_TEST,
        COMMANDREQUEST_CANCEL_TEST,
        SUPPORTS_COMMANDS_TEST
    }

    private static final String TAG = Utils.class.getSimpleName();

    public static final long OPERATION_TIMEOUT_MS = 10000;

    private static long sAdjustedOperationTimeoutMs = -1;

    /** CDD restricts the max size of each successful hotword result is 100 bytes. */
    public static final int MAX_HOTWORD_DETECTED_RESULT_SIZE = 100;

    /**
     * Limits the max value for the hotword offset.
     *
     * Note: Must match the definition in
     * frameworks/base/core/java/android/service/voice/HotwordDetectedResult.java.
     */
    public static final int LIMIT_HOTWORD_OFFSET_MAX_VALUE = 60 * 60 * 1000; // 1 hour

    /**
     * Limits the max value for the triggered audio channel.
     *
     * Note: Must match the definition in
     * frameworks/base/core/java/android/service/voice/HotwordDetectedResult.java.
     */
    public static final int LIMIT_AUDIO_CHANNEL_MAX_VALUE = 63;

    /**
     * Indicate which test event for testing.
     *
     * Note: The VIS is the abbreviation of VoiceInteractionService
     */
    public static final int VIS_NORMAL_TEST = 0;

    /** Indicate which test scenario for testing. */
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH = 1;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_UNEXPECTED_CALLBACK = 2;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_SEND_OVER_MAX_INIT_STATUS = 3;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_SEND_CUSTOM_INIT_STATUS = 4;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS = 5;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_CLEAR_SOFTWARE_DETECTION_JOB = 6;
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_NO_NEED_ACTION_DURING_DETECTION = 7;
    // test scenario to verify the HotwordDetectionService was created after a given time
    // This can be used to verify the service was restarted or recreated.
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_SEND_SUCCESS_IF_CREATED_AFTER = 8;
    // Check the HotwordDetectionService can read audio and the data is not zero
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_CAN_READ_AUDIO_DATA_IS_NOT_ZERO = 9;

    /** Indicate to start a new activity for testing. */
    public static final int ACTIVITY_NEW = 0;
    /** Indicate to finish an activity for testing. */
    public static final int ACTIVITY_FINISH = 1;
    /** Indicate to crash an activity for testing. */
    public static final int ACTIVITY_CRASH = 2;

    /** Indicate what kind of parameters for calling registerVisibleActivityCallback. */
    public static final int VISIBLE_ACTIVITY_CALLBACK_REGISTER_NORMAL = 0;
    public static final int VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_EXECUTOR = 1;
    public static final int VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_CALLBACK = 2;

    public static final int NUM_TEST_RESOURCE_FILE_MULTIPLE = 50;
    public static final int NUM_TEST_QUERY_SESSION_MULTIPLE = 20;

    public static final String TEST_APP_PACKAGE = "android.voiceinteraction.testapp";
    public static final String TESTCASE_TYPE = "testcase_type";
    public static final String TESTINFO = "testinfo";
    public static final String BROADCAST_INTENT = "android.intent.action.VOICE_TESTAPP";
    public static final String TEST_PROMPT = "testprompt";
    public static final String PICKOPTON_1 = "one";
    public static final String PICKOPTON_2 = "two";
    public static final String PICKOPTON_3 = "3";
    public static final String TEST_COMMAND = "test_command";
    public static final String TEST_ONCOMMAND_RESULT = "test_oncommand_result";
    public static final String TEST_ONCOMMAND_RESULT_VALUE = "test_oncommand_result value";

    public static final String CONFIRMATION_REQUEST_SUCCESS = "confirmation ok";
    public static final String COMPLETION_REQUEST_SUCCESS = "completion ok";
    public static final String ABORT_REQUEST_SUCCESS = "abort ok";
    public static final String PICKOPTION_REQUEST_SUCCESS = "pickoption ok";
    public static final String COMMANDREQUEST_SUCCESS = "commandrequest ok";
    public static final String SUPPORTS_COMMANDS_SUCCESS = "supportsCommands ok";

    public static final String CONFIRMATION_REQUEST_CANCEL_SUCCESS = "confirm cancel ok";
    public static final String COMPLETION_REQUEST_CANCEL_SUCCESS = "completion canel ok";
    public static final String ABORT_REQUEST_CANCEL_SUCCESS = "abort cancel ok";
    public static final String PICKOPTION_REQUEST_CANCEL_SUCCESS = "pickoption  cancel ok";
    public static final String COMMANDREQUEST_CANCEL_SUCCESS = "commandrequest cancel ok";
    public static final String TEST_ERROR = "Error In Test:";

    public static final String PRIVATE_OPTIONS_KEY = "private_key";
    public static final String PRIVATE_OPTIONS_VALUE = "private_value";

    public static final String DIRECT_ACTION_EXTRA_KEY = "directActionExtraKey";
    public static final String DIRECT_ACTION_EXTRA_VALUE = "directActionExtraValue";
    public static final String DIRECT_ACTION_FILE_NAME = "directActionFileName";
    public static final String DIRECT_ACTION_FILE_CONTENT = "directActionFileContent";
    public static final String DIRECT_ACTION_AUTHORITY =
            "android.voiceinteraction.testapp.fileprovider";

    public static final String DIRECT_ACTIONS_KEY_CANCEL_CALLBACK = "cancelCallback";
    public static final String DIRECT_ACTIONS_KEY_RESULT = "result";

    public static final String DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION = "performAction";
    public static final String DIRECT_ACTIONS_SESSION_CMD_PERFORM_ACTION_CANCEL =
            "performActionCancel";
    public static final String DIRECT_ACTIONS_SESSION_CMD_DETECT_ACTIONS_CHANGED =
            "detectActionsChanged";
    public static final String DIRECT_ACTIONS_SESSION_CMD_GET_ACTIONS = "getActions";

    public static final String DIRECT_ACTIONS_ACTIVITY_CMD_DESTROYED_INTERACTOR =
            "destroyedInteractor";
    public static final String DIRECT_ACTIONS_ACTIVITY_CMD_INVALIDATE_ACTIONS = "invalidateActions";
    public static final String DIRECT_ACTIONS_ACTIVITY_CMD_GET_PACKAGE_NAME = "getpackagename";
    public static final String DIRECT_ACTIONS_ACTIVITY_CMD_GET_PACKAGE_INFO = "getpackageinfo";

    public static final String DIRECT_ACTIONS_RESULT_PERFORMED = "performed";
    public static final String DIRECT_ACTIONS_RESULT_CANCELLED = "cancelled";
    public static final String DIRECT_ACTIONS_RESULT_EXECUTING = "executing";

    public static final String DIRECT_ACTIONS_ACTION_ID = "actionId";
    public static final Bundle DIRECT_ACTIONS_ACTION_EXTRAS = new Bundle();
    static {
        DIRECT_ACTIONS_ACTION_EXTRAS.putString(DIRECT_ACTION_EXTRA_KEY,
                DIRECT_ACTION_EXTRA_VALUE);
    }
    public static final LocusId DIRECT_ACTIONS_LOCUS_ID = new LocusId("locusId");

    public static final String SERVICE_NAME =
            "android.voiceinteraction.service/.MainInteractionService";

    public static final String KEY_TEST_EVENT = "testEvent";
    public static final String KEY_TEST_RESULT = "testResult";
    public static final String KEY_TEST_SCENARIO = "testScenario";
    public static final String KEY_DETECTION_DELAY_MS = "detectionDelayMs";
    public static final String KEY_DETECTION_REJECTED = "detection_rejected";

    /**
     * The options key to indicate whether the MainHotwordDetectionService should accept the hotword
     * audio stream no matter what it is.
     */
    public static final String KEY_ACCEPT_DETECTION = "accept_detection";

    public static final String KEY_INITIALIZATION_STATUS = "initialization_status";
    /**
     * It only works when the test scenario is
     * {@link #EXTRA_HOTWORD_DETECTION_SERVICE_ENABLE_AUDIO_EGRESS}
     *
     * Type: Boolean
     */
    public static final String KEY_AUDIO_EGRESS_USE_ILLEGAL_COPY_BUFFER_SIZE =
            "useIllegalCopyBufferSize";

    /**
     * The AlwaysOnHotwordDetector#updateState options key to indicate whether the
     * MainHotwordDetectionService should close the audio stream received from an external source
     * immediately after reading from it.
     */
    public static final String KEY_AUDIO_EGRESS_CLOSE_AUDIO_STREAM_AFTER_READ =
            "closeAudioStreamAfterRead";

    /**
     * The AlwaysOnHotwordDetector#updateState options key to indicate whether the
     * MainHotwordDetectionService should close the input audio stream if the output audio pipe is
     * broken.
     */
    public static final String KEY_CLOSE_INPUT_AUDIO_STREAM_IF_OUTPUT_PIPE_BROKEN =
            "closeInputAudioStreamIfOutputPipeBroken";

    public static final String KEY_TIMESTAMP_MILLIS = "timestamp_millis";

    public static final String VOICE_INTERACTION_KEY_CALLBACK = "callback";
    public static final String VOICE_INTERACTION_KEY_CONTROL = "control";
    public static final String VOICE_INTERACTION_KEY_COMMAND = "command";
    public static final String VOICE_INTERACTION_KEY_TASKID = "taskId";
    public static final String VOICE_INTERACTION_DIRECT_ACTIONS_KEY_ACTION = "action";
    public static final String VOICE_INTERACTION_KEY_ARGUMENTS = "arguments";
    public static final String VOICE_INTERACTION_KEY_CLASS = "class";

    public static final String VOICE_INTERACTION_KEY_REMOTE_CALLBACK_FOR_NEW_SESSION =
            "remoteCallbackForNewSession";
    public static final String VOICE_INTERACTION_KEY_USE_ACTIVITY_OPTIONS = "useActivityOptions";
    public static final String VOICE_INTERACTION_SESSION_CMD_FINISH = "hide";
    public static final String VOICE_INTERACTION_ACTIVITY_CMD_FINISH = "finish";
    public static final String VOICE_INTERACTION_ACTIVITY_CMD_CRASH = "crash";

    // For v2 reliable visible activity lookup feature
    public static final String VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT =
            "android.intent.action.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT";
    public static final String VISIBLE_ACTIVITY_CALLBACK_ONINVISIBLE_INTENT =
            "android.intent.action.VISIBLE_ACTIVITY_CALLBACK_ONINVISIBLE_INTENT";
    public static final String VISIBLE_ACTIVITY_KEY_RESULT = "result";

    public static final String VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK = "registerCallback";
    public static final String VISIBLE_ACTIVITY_CMD_UNREGISTER_CALLBACK = "unregisterCallback";

    // For asking to bind to a test VoiceInteractionService if it supports it
    public static final String ACTION_BIND_TEST_VOICE_INTERACTION =
            "android.intent.action.ACTION_BIND_TEST_VOICE_INTERACTION";
    public static final String TEST_VOICE_INTERACTION_SERVICE_PACKAGE_NAME =
            "android.voiceinteraction.service";
    public static final String PROXY_VOICE_INTERACTION_SERVICE_CLASS_NAME =
            "android.voiceinteraction.service.ProxyVoiceInteractionService";
    public static final String PROXY_VOICEINTERACTION_SERVICE_COMPONENT =
            TEST_VOICE_INTERACTION_SERVICE_PACKAGE_NAME + "/"
                    + PROXY_VOICE_INTERACTION_SERVICE_CLASS_NAME;
    public static final String VOICE_INTERACTION_SERVICE_BINDING_HELPER_CLASS_NAME =
            "android.voiceinteraction.service.VoiceInteractionServiceBindingHelper";
    // File opening related
    public static final String TEST_RESOURCE_FILE_NAME = "test_resource";
    public static final String TEST_RESOURCE_FILE_CONTENT = "This file contains test resource";
    private static final String KEY_FAKE_DATA = "fakeData";
    private static final String VALUE_FAKE_DATA = "fakeData";

    private static final long FRAME_POSITION = 0;
    private static final long NANO_TIME_NS = 1000;

    private static final byte[] FAKE_HOTWORD_AUDIO_DATA =
            new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};

    public static final int FAKE_HOTWORD_TRAINING_AUDIO_TYPE = 7;
    public static final int FAKE_HOTWORD_OFFSET_MILLIS = 9;
    public static final int FAKE_HOTWORD_TRAINING_DATA_TIMEOUT_STAGE = 8;

    private static final HotwordAudioStream HOTWORD_AUDIO_STREAM = createNewHotwordAudioStream();

    private static final HotwordAudioStream HOTWORD_AUDIO_STREAM_WRONG_COPY_BUFFER_SIZE =
            new HotwordAudioStream.Builder(createFakeAudioFormat(), createFakeAudioStream())
                    .setInitialAudio(FAKE_HOTWORD_AUDIO_DATA)
                    .setMetadata(createFakePersistableBundleData(0))
                    .setTimestamp(createFakeAudioTimestamp())
                    .build();

    public static final HotwordDetectedResult AUDIO_EGRESS_DETECTED_RESULT =
            createNewAudioEgressDetectedResult(HOTWORD_AUDIO_STREAM);

    public static final HotwordDetectedResult AUDIO_EGRESS_DETECTED_RESULT_WRONG_COPY_BUFFER_SIZE =
            createNewAudioEgressDetectedResult(HOTWORD_AUDIO_STREAM_WRONG_COPY_BUFFER_SIZE);

    public static final boolean SYSPROP_VISUAL_QUERY_SERVICE_ENABLED =
            SystemProperties.getBoolean("ro.hotword.visual_query_service_enabled", false);

    /**
     * Creates a new instance of HotwordDetectedResult that contains the provided
     * hotwordAudioStream.
     */
    public static HotwordDetectedResult createNewAudioEgressDetectedResult(
            HotwordAudioStream hotwordAudioStream) {
        return new HotwordDetectedResult.Builder()
                .setAudioStreams(List.of(hotwordAudioStream))
                .build();
    }

    /** Creates an audio stream with FAKE_HOTWORD_AUDIO_DATA. */
    public static HotwordAudioStream createNewHotwordAudioStream() {
        return createNewHotwordAudioStream(createFakeAudioStream());
    }

    /** Creates an audio stream with FAKE_HOTWORD_AUDIO_DATA. */
    public static HotwordAudioStream createNewHotwordAudioStream(ParcelFileDescriptor audioStream) {
        return new HotwordAudioStream.Builder(createFakeAudioFormat(), audioStream)
                .setInitialAudio(FAKE_HOTWORD_AUDIO_DATA)
                .setMetadata(createFakePersistableBundleData())
                .setTimestamp(createFakeAudioTimestamp())
                .build();
    }

    /**
     * Returns the PersistableBundle data that is used for testing.
     */
    private static PersistableBundle createFakePersistableBundleData() {
        return createFakePersistableBundleData(/* copyBufferSize= */ -1);
    }

    /**
     * Returns the PersistableBundle data that is used for testing.
     */
    private static PersistableBundle createFakePersistableBundleData(int copyBufferSize) {
        // TODO : Add more data for testing
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(KEY_FAKE_DATA, VALUE_FAKE_DATA);
        if (copyBufferSize > -1) {
            persistableBundle.putInt(KEY_AUDIO_STREAM_COPY_BUFFER_LENGTH_BYTES, copyBufferSize);
        }
        return persistableBundle;
    }

    /**
     * Returns the AudioFormat data that is used for testing.
     */
    private static AudioFormat createFakeAudioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(32000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }

    /**
     * Returns the ParcelFileDescriptor data that is used for testing.
     */
    private static ParcelFileDescriptor createFakeAudioStream() {
        ParcelFileDescriptor[] tempParcelFileDescriptors = null;
        try {
            tempParcelFileDescriptors = ParcelFileDescriptor.createPipe();
            try (OutputStream fos =
                         new ParcelFileDescriptor.AutoCloseOutputStream(
                                 tempParcelFileDescriptors[1])) {
                fos.write(FAKE_HOTWORD_AUDIO_DATA, 0, 8);
            } catch (IOException e) {
                Log.w(TAG, "Failed to pipe audio data : ", e);
                throw new IllegalStateException();
            }
            return tempParcelFileDescriptors[0];
        } catch (IOException e) {
            Log.w(TAG, "Failed to create a pipe : " + e);
        }
        throw new IllegalStateException();
    }

    /**
     * Returns the AudioTimestamp for test
     */
    private static AudioTimestamp createFakeAudioTimestamp() {
        final AudioTimestamp timestamp = new AudioTimestamp();
        timestamp.framePosition = FRAME_POSITION;
        timestamp.nanoTime = NANO_TIME_NS;
        return timestamp;
    }

    public static final String toBundleString(Bundle bundle) {
        if (bundle == null) {
            return "null_bundle";
        }
        StringBuffer buf = new StringBuffer("Bundle[ ");
        String testType = bundle.getString(TESTCASE_TYPE);
        boolean empty = true;
        if (testType != null) {
            empty = false;
            buf.append("testcase type = " + testType);
        }
        ArrayList<String> info = bundle.getStringArrayList(TESTINFO);
        if (info != null) {
            for (String s : info) {
                empty = false;
                buf.append(s + "\n\t\t");
            }
        } else {
            for (String key : bundle.keySet()) {
                empty = false;
                Object value = bundle.get(key);
                if (value instanceof Bundle) {
                    value = toBundleString((Bundle) value);
                }
                buf.append(key).append('=').append(value).append(' ');
            }
        }
        return empty ? "empty_bundle" : buf.append(']').toString();
    }

    public static final String toOptionsString(Option[] options) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < options.length; i++) {
            if (i >= 1) {
                sb.append(", ");
            }
            sb.append(options[i].getLabel());
        }
        sb.append("}");
        return sb.toString();
    }

    public static final void addErrorResult(final Bundle testinfo, final String msg) {
        testinfo.getStringArrayList(testinfo.getString(Utils.TESTCASE_TYPE))
                .add(TEST_ERROR + " " + msg);
    }

    public static boolean await(CountDownLatch latch) {
        final long timeoutMs = getAdjustedOperationTimeoutMs();
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return true;
            Log.e(TAG, "latch timed out");
        } catch (InterruptedException e) {
            /* ignore */
            Log.e(TAG, "Interrupted", e);
        }
        return false;
    }

    public static boolean await(Condition condition) {
        final long timeoutMs = getAdjustedOperationTimeoutMs();
        try {
            if (condition.await(timeoutMs, TimeUnit.MILLISECONDS)) return true;
            Log.e(TAG, "condition timed out");
        } catch (InterruptedException e) {
            /* ignore */
            Log.e(TAG, "Interrupted", e);
        }
        return false;
    }

    public static int getParcelableSize(Parcelable parcelable) {
        final Parcel p = Parcel.obtain();
        parcelable.writeToParcel(p, 0);
        p.setDataPosition(0);
        final int size = p.dataSize();
        p.recycle();
        return size;
    }

    public static int bitCount(long value) {
        int bits = 0;
        while (value > 0) {
            bits++;
            value = value >> 1;
        }
        return bits;
    }

    public static boolean isVirtualDevice() {
        final String property = PropertyUtil.getProperty("ro.hardware.virtual_device");
        Log.v(TAG, "virtual device property=" + property);
        return Objects.equals(property, "1");
    }

    /**
     * Returns an operation timeout (in milliseconds) adjusted when running on
     * a slower device.
     */
    public static long getAdjustedOperationTimeoutMs() {
        // We cache the value in sAdjustedOperationTimeoutMs so we don't need to
        // send a command to the device every time this method is called.  The
        // hw_timeout_multiplier is not a dynamic value.
        if (sAdjustedOperationTimeoutMs == -1) {
            final String property = PropertyUtil.getProperty("ro.hw_timeout_multiplier");
            int multiplier = 1;
            if (property != null) {
                try {
                    multiplier = Integer.parseInt(property);
                } catch (NumberFormatException e) {
                    // Ignore and keep 'multiplier' at 1.
                }
            }
            sAdjustedOperationTimeoutMs = OPERATION_TIMEOUT_MS * multiplier;
        }
        return sAdjustedOperationTimeoutMs;
    }

    /** Sets the secure accessibility settings for visual query detector */
    public static void toggleVisualQueryAccessibilitySettings(boolean enable) {
        final StringBuilder cmd = new StringBuilder("settings put secure ")
                .append("visual_query_accessibility_detection_enabled").append(" ")
                .append(enable ? "1" : "0");
        SystemUtil.runShellCommand(cmd.toString());
    }

}
