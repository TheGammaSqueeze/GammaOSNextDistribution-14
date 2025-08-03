package com.example.imsmediatestingapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.radio.ims.media.AmrMode;
import android.hardware.radio.ims.media.CodecType;
import android.hardware.radio.ims.media.EvsBandwidth;
import android.hardware.radio.ims.media.EvsMode;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AmrParams;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.AudioSessionCallback;
import android.telephony.imsmedia.EvsParams;
import android.telephony.imsmedia.IImsMediaCallback;
import android.telephony.imsmedia.ImsAudioSession;
import android.telephony.imsmedia.ImsMediaManager;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.ImsTextSession;
import android.telephony.imsmedia.ImsVideoSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtcpConfig;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.RtpReceptionStats;
import android.telephony.imsmedia.TextConfig;
import android.telephony.imsmedia.TextSessionCallback;
import android.telephony.imsmedia.VideoConfig;
import android.telephony.imsmedia.VideoSessionCallback;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The MainActivity is the main and default layout for the application.
 */
public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private SharedPrefsHandler prefsHandler;
    public static final String PREF_NAME = "preferences";
    private static final String HANDSHAKE_PORT_PREF = "HANDSHAKE_PORT_OPEN";
    private static final String CONFIRMATION_MESSAGE = "CONNECTED";
    private static final String TAG = MainActivity.class.getName();

    private static final int MAX_MTU_BYTES = 1500;
    private static final int DSCP = 0;
    private static final int AUDIO_RX_PAYLOAD_TYPE_NUMBER = 96;
    private static final int AUDIO_TX_PAYLOAD_TYPE_NUMBER = 96;
    private static final int VIDEO_RX_PAYLOAD_TYPE_NUMBER = 106;
    private static final int VIDEO_TX_PAYLOAD_TYPE_NUMBER = 106;
    private static final int TEXT_REDUNDANT_PAYLOAD_TYPE_NUMBER = 111;
    private static final int TEXT_RX_PAYLOAD_TYPE_NUMBER = 112;
    private static final int TEXT_TX_PAYLOAD_TYPE_NUMBER = 112;
    private static final int SAMPLING_RATE_KHZ = 16;
    private static final int P_TIME_MILLIS = 20;
    private static final int MAX_P_TIME_MILLIS = 240;
    private static final int DTMF_PAYLOAD_TYPE_NUMBER = 100;
    private static final int DTMF_SAMPLING_RATE_KHZ = 16;
    private static final int DTMF_DURATION = 140;
    private static final int IDR_INTERVAL = 1;
    private static final int RESOLUTION_WIDTH = 480;
    private static final int RESOLUTION_HEIGHT = 640;
    private static final String IMAGE = "data/user_de/0/com.android.telephony.imsmedia/test.jpg";
    private static final float DISABLED_ALPHA = 0.3f;
    private static final float ENABLED_ALPHA = 1.0f;
    private static final int VIDEO_FRAMERATE = 15;
    private static final int VIDEO_BITRATE = 384;
    private static final int CAMERA_ID = 0;
    private static final int CAMERA_ZOOM = 10;

    private static final int[] RTP_TIMEOUT = { 10000, 20000 };
    private static final int RTCP_TIMEOUT = 15000;
    private static final int RTP_HYSTERESIS_TIME = 3000;
    private static final int RTP_PACKET_LOSS_DURATION = 3000;
    private static final int[] PACKET_LOSS_RATE = { 1, 3 };
    private static final int[] JITTER_THRESHOLD = { 100, 200 };
    private static final boolean NOTIFY_STATUS = false;
    private static final int VIDEO_BITRATE_THRESHOLD_BPS = 100000;

    private Set<Integer> mSelectedCodecTypes = new HashSet<>();
    private Set<Integer> mSelectedAmrModes = new HashSet<>();
    private Set<Integer> mSelectedEvsBandwidths = new HashSet<>();
    private Set<Integer> mSelectedEvsModes = new HashSet<>();
    private int mSelectedVideoCodec = VideoConfig.VIDEO_CODEC_AVC;
    private int mSelectedVideoMode = VideoConfig.VIDEO_MODE_RECORDING;
    private int mSelectedFramerate = VIDEO_FRAMERATE;
    private int mSelectedBitrate = VIDEO_BITRATE;
    private int mSelectedCodecProfile = VideoConfig.AVC_PROFILE_BASELINE;
    private int mSelectedCodecLevel = VideoConfig.AVC_LEVEL_12;
    private int mSelectedCameraId = CAMERA_ID;
    private int mSelectedCameraZoom = CAMERA_ZOOM;
    private int mSelectedDeviceOrientationDegree = 0;
    private int mSelectedCvoValue = -1;
    private String mSelectedVideoResolution = "VGA_PR";
    private Set<Integer> mSelectedRtcpFbTypes = new HashSet<>();

    // The order of these values determines the priority in which they would be
    // selected if there
    // is a common match between the two devices' selections during the handshake
    // process.
    private static final int[] CODEC_ORDER = new int[] { CodecType.AMR, CodecType.AMR_WB,
            CodecType.EVS, CodecType.PCMA, CodecType.PCMU };
    private static final int[] EVS_BANDWIDTH_ORDER = new int[] { EvsBandwidth.NONE,
            EvsBandwidth.NARROW_BAND, EvsBandwidth.WIDE_BAND, EvsBandwidth.SUPER_WIDE_BAND,
            EvsBandwidth.FULL_BAND };
    private static final int[] AMR_MODE_ORDER = new int[] { AmrMode.AMR_MODE_0, AmrMode.AMR_MODE_1,
            AmrMode.AMR_MODE_2, AmrMode.AMR_MODE_3, AmrMode.AMR_MODE_4, AmrMode.AMR_MODE_5,
            AmrMode.AMR_MODE_6, AmrMode.AMR_MODE_7, AmrMode.AMR_MODE_8 };
    private static final int[] EVS_MODE_ORDER = new int[] { EvsMode.EVS_MODE_0, EvsMode.EVS_MODE_1,
            EvsMode.EVS_MODE_2, EvsMode.EVS_MODE_3, EvsMode.EVS_MODE_4, EvsMode.EVS_MODE_5,
            EvsMode.EVS_MODE_6, EvsMode.EVS_MODE_7, EvsMode.EVS_MODE_8, EvsMode.EVS_MODE_9,
            EvsMode.EVS_MODE_10, EvsMode.EVS_MODE_11, EvsMode.EVS_MODE_12, EvsMode.EVS_MODE_13,
            EvsMode.EVS_MODE_14, EvsMode.EVS_MODE_15, EvsMode.EVS_MODE_16, EvsMode.EVS_MODE_17,
            EvsMode.EVS_MODE_18, EvsMode.EVS_MODE_19, EvsMode.EVS_MODE_20 };

    private boolean mLoopbackModeEnabled = false;
    private boolean mIsMediaManagerReady = false;
    private boolean mIsOpenSessionSent = false;
    private boolean mIsVideoSessionOpened = false;
    private boolean mIsTextSessionOpened = false;
    private boolean mIsPreviewSurfaceSet = false;
    private boolean mIsDisplaySurfaceSet = false;
    private boolean mVideoEnabled = false;
    private boolean mTextEnabled = false;
    private final StringBuilder mDtmfInput = new StringBuilder();

    private ConnectionStatus mConnectionStatus;
    private ImsAudioSession mAudioSession;
    private ImsVideoSession mVideoSession;
    private ImsTextSession mTextSession;
    private AudioConfig mAudioConfig;
    private VideoConfig mVideoConfig;
    private TextConfig mTextConfig;
    private ImsMediaManager mImsMediaManager;
    private Executor mExecutor;
    private Thread mWaitForHandshakeThread;
    private HandshakeReceiver mHandshakeReceptionSocket;
    private DatagramSocket mAudioRtp;
    private DatagramSocket mAudioRtcp;
    private DatagramSocket mVideoRtp;
    private DatagramSocket mVideoRtcp;
    private DatagramSocket mTextRtp;
    private DatagramSocket mTextRtcp;
    private DeviceInfo mRemoteDeviceInfo;
    private DeviceInfo mLocalDeviceInfo;
    private BottomSheetDialer mBottomSheetDialog;
    private BottomSheetAudioCodecSettings mBottomSheetAudioCodecSettings;
    private TextView mLocalHandshakePortLabel;
    private TextView mLocalRtpPortLabel;
    private TextView mLocalRtcpPortLabel;
    private TextView mRemoteIpLabel;
    private TextView mRemoteHandshakePortLabel;
    private TextView mRemoteRtpPortLabel;
    private TextView mRemoteRtcpPortLabel;
    private Button mAllowCallsButton;
    private Button mConnectButton;
    private Button mOpenSessionButton;
    private SwitchCompat mLoopbackSwitch;
    private LinearLayout mActiveCallToolBar;
    private TextureView mTexturePreview;
    private TextureView mTextureDisplay;
    private Surface mPreviewSurface;
    private Surface mDisplaySurface;
    private int mDelay;

    /**
     * Enum of the CodecType from android.hardware.radio.ims.media.CodecType with
     * the matching
     * Integer value.
     */
    public enum CodecTypeEnum {
        AMR(CodecType.AMR),
        AMR_WB(CodecType.AMR_WB),
        EVS(CodecType.EVS),
        PCMA(CodecType.PCMA),
        PCMU(CodecType.PCMU);

        private final int mValue;

        CodecTypeEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the AmrMode from android.hardware.radio.ims.media.AmrMode with the
     * matching
     * Integer value.
     */
    public enum AmrModeEnum {
        AMR_MODE_0(AmrMode.AMR_MODE_0),
        AMR_MODE_1(AmrMode.AMR_MODE_1),
        AMR_MODE_2(AmrMode.AMR_MODE_2),
        AMR_MODE_3(AmrMode.AMR_MODE_3),
        AMR_MODE_4(AmrMode.AMR_MODE_4),
        AMR_MODE_5(AmrMode.AMR_MODE_5),
        AMR_MODE_6(AmrMode.AMR_MODE_6),
        AMR_MODE_7(AmrMode.AMR_MODE_7),
        AMR_MODE_8(AmrMode.AMR_MODE_8);

        private final int mValue;

        AmrModeEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }

    }

    /**
     * Enum of the EvsBandwidth from android.hardware.radio.ims.media.EvsBandwidth
     * with the
     * matching Integer value.
     */
    public enum EvsBandwidthEnum {
        NONE(EvsBandwidth.NONE),
        NARROW_BAND(EvsBandwidth.NARROW_BAND),
        WIDE_BAND(EvsBandwidth.WIDE_BAND),
        SUPER_WIDE_BAND(EvsBandwidth.SUPER_WIDE_BAND),
        FULL_BAND(EvsBandwidth.FULL_BAND);

        private final int mValue;

        EvsBandwidthEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the EvsMode from android.hardware.radio.ims.media.EvsMode with the
     * matching
     * Integer value.
     */
    public enum EvsModeEnum {
        EVS_MODE_0(EvsMode.EVS_MODE_0),
        EVS_MODE_1(EvsMode.EVS_MODE_1),
        EVS_MODE_2(EvsMode.EVS_MODE_2),
        EVS_MODE_3(EvsMode.EVS_MODE_3),
        EVS_MODE_4(EvsMode.EVS_MODE_4),
        EVS_MODE_5(EvsMode.EVS_MODE_5),
        EVS_MODE_6(EvsMode.EVS_MODE_6),
        EVS_MODE_7(EvsMode.EVS_MODE_7),
        EVS_MODE_8(EvsMode.EVS_MODE_8),
        EVS_MODE_9(EvsMode.EVS_MODE_9),
        EVS_MODE_10(EvsMode.EVS_MODE_10),
        EVS_MODE_11(EvsMode.EVS_MODE_11),
        EVS_MODE_12(EvsMode.EVS_MODE_12),
        EVS_MODE_13(EvsMode.EVS_MODE_13),
        EVS_MODE_14(EvsMode.EVS_MODE_14),
        EVS_MODE_15(EvsMode.EVS_MODE_15),
        EVS_MODE_16(EvsMode.EVS_MODE_16),
        EVS_MODE_17(EvsMode.EVS_MODE_17),
        EVS_MODE_18(EvsMode.EVS_MODE_18),
        EVS_MODE_19(EvsMode.EVS_MODE_19),
        EVS_MODE_20(EvsMode.EVS_MODE_20);

        private final int mValue;

        EvsModeEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video codecs from VideoConfig with the matching
     * Integer value.
     */
    public enum VideoCodecEnum {
        AVC(VideoConfig.VIDEO_CODEC_AVC),
        HEVC(VideoConfig.VIDEO_CODEC_HEVC);

        private final int mValue;

        VideoCodecEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video modes from VideoConfig with the matching
     * Integer value.
     */
    public enum VideoModeEnum {
        VIDEO_MODE_PREVIEW(VideoConfig.VIDEO_MODE_PREVIEW),
        VIDEO_MODE_RECORDING(VideoConfig.VIDEO_MODE_RECORDING),
        VIDEO_MODE_PAUSE_IMAGE(VideoConfig.VIDEO_MODE_PAUSE_IMAGE);

        private final int mValue;

        VideoModeEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video codec profiles from VideoConfig with the matching
     * Integer value.
     */
    public enum VideoCodecProfileEnum {
        CODEC_PROFILE_NONE(VideoConfig.CODEC_PROFILE_NONE),
        AVC_PROFILE_BASELINE(VideoConfig.AVC_PROFILE_BASELINE),
        AVC_PROFILE_CONSTRAINED_BASELINE(VideoConfig.AVC_PROFILE_CONSTRAINED_BASELINE),
        AVC_PROFILE_CONSTRAINED_HIGH(VideoConfig.AVC_PROFILE_CONSTRAINED_HIGH),
        AVC_PROFILE_HIGH(VideoConfig.AVC_PROFILE_HIGH),
        AVC_PROFILE_MAIN(VideoConfig.AVC_PROFILE_MAIN),
        HEVC_PROFILE_MAIN(VideoConfig.HEVC_PROFILE_MAIN),
        HEVC_PROFILE_MAIN10(VideoConfig.HEVC_PROFILE_MAIN10);

        private final int mValue;

        VideoCodecProfileEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video codec levels from VideoConfig with the matching
     * Integer value.
     */
    public enum VideoCodecLevelEnum {
        CODEC_LEVEL_NONE(VideoConfig.CODEC_LEVEL_NONE),
        AVC_LEVEL_1(VideoConfig.AVC_LEVEL_1),
        AVC_LEVEL_1B(VideoConfig.AVC_LEVEL_1B),
        AVC_LEVEL_11(VideoConfig.AVC_LEVEL_11),
        AVC_LEVEL_12(VideoConfig.AVC_LEVEL_12),
        AVC_LEVEL_13(VideoConfig.AVC_LEVEL_13),
        AVC_LEVEL_2(VideoConfig.AVC_LEVEL_2),
        AVC_LEVEL_21(VideoConfig.AVC_LEVEL_21),
        AVC_LEVEL_22(VideoConfig.AVC_LEVEL_22),
        AVC_LEVEL_3(VideoConfig.AVC_LEVEL_3),
        AVC_LEVEL_31(VideoConfig.AVC_LEVEL_31),
        HEVC_HIGHTIER_LEVEL_1(VideoConfig.HEVC_HIGHTIER_LEVEL_1),
        HEVC_HIGHTIER_LEVEL_2(VideoConfig.HEVC_HIGHTIER_LEVEL_2),
        HEVC_HIGHTIER_LEVEL_21(VideoConfig.HEVC_HIGHTIER_LEVEL_21),
        HEVC_HIGHTIER_LEVEL_3(VideoConfig.HEVC_HIGHTIER_LEVEL_3),
        HEVC_HIGHTIER_LEVEL_31(VideoConfig.HEVC_HIGHTIER_LEVEL_31),
        HEVC_HIGHTIER_LEVEL_4(VideoConfig.HEVC_HIGHTIER_LEVEL_4),
        HEVC_HIGHTIER_LEVEL_41(VideoConfig.HEVC_HIGHTIER_LEVEL_41),
        HEVC_MAINTIER_LEVEL_1(VideoConfig.HEVC_MAINTIER_LEVEL_1),
        HEVC_MAINTIER_LEVEL_2(VideoConfig.HEVC_MAINTIER_LEVEL_2),
        HEVC_MAINTIER_LEVEL_21(VideoConfig.HEVC_MAINTIER_LEVEL_21),
        HEVC_MAINTIER_LEVEL_3(VideoConfig.HEVC_MAINTIER_LEVEL_3),
        HEVC_MAINTIER_LEVEL_31(VideoConfig.HEVC_MAINTIER_LEVEL_31),
        HEVC_MAINTIER_LEVEL_4(VideoConfig.HEVC_MAINTIER_LEVEL_4),
        HEVC_MAINTIER_LEVEL_41(VideoConfig.HEVC_MAINTIER_LEVEL_41);

        private final int mValue;

        VideoCodecLevelEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video camera ids from VideoConfig
     * Integer value.
     */
    public enum VideoCameraIdEnum {
        ID_0(0),
        ID_1(1),
        ID_2(2),
        ID_3(3),
        ID_4(4);

        private final int mValue;

        VideoCameraIdEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video zoom levels from VideoConfig
     * Integer value.
     */
    public enum VideoCameraZoomEnum {
        LEVEL_0(0),
        LEVEL_1(1),
        LEVEL_2(2),
        LEVEL_3(3),
        LEVEL_4(4),
        LEVEL_5(5),
        LEVEL_6(6),
        LEVEL_7(7),
        LEVEL_8(8),
        LEVEL_9(9);

        private final int mValue;

        VideoCameraZoomEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video framerate from VideoConfig
     * Integer value.
     */
    public enum VideoFramerateEnum {
        FPS_10(10),
        FPS_15(15),
        FPS_20(20),
        FPS_24(24),
        FPS_30(30);

        private final int mValue;

        VideoFramerateEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video bitrate from VideoConfig
     * Integer value.
     */
    public enum VideoBitrateEnum {
        BITRATE_192kbps(192),
        BITRATE_256kbps(256),
        BITRATE_384kbps(384),
        BITRATE_512kbps(512),
        BITRATE_640kbps(640);

        private final int mValue;

        VideoBitrateEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video device orientation from VideoConfig
     * Integer value.
     */
    public enum VideoDeviceOrientationEnum {
        DEGREE_0(0),
        DEGREE_90(90),
        DEGREE_180(180),
        DEGREE_270(270);

        private final int mValue;

        VideoDeviceOrientationEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Enum of the video cvo offset value from VideoConfig
     * Integer value.
     */
    public enum VideoCvoValueEnum {
        CVO_DISABLE(-1),
        CVO_OFFSET_1(1),
        CVO_OFFSET_2(2),
        CVO_OFFSET_3(3),
        CVO_OFFSET_4(4),
        CVO_OFFSET_5(5),
        CVO_OFFSET_6(6),
        CVO_OFFSET_7(7),
        CVO_OFFSET_8(8),
        CVO_OFFSET_9(9),
        CVO_OFFSET_10(10),
        CVO_OFFSET_11(11),
        CVO_OFFSET_12(12),
        CVO_OFFSET_13(13),
        CVO_OFFSET_14(14);

        private final int mValue;

        VideoCvoValueEnum(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public String[] mVideoResolutionStrings = new String[] {
        "HD_PR", "HD_LS", "VGA_PR", "VGA_LS", "QVGA_PR", "QVGA_LS", "SIF_PR", "SIF_LS", "CIF_PR",
        "CIF_LS", "QCIF_PR", "QCIF_LS",
    };

    public int[][] mVideoResolution = {
        {720, 1280}, {1280, 720}, {480, 640}, {640, 480}, {240, 320}, {320, 240}, {240, 352},
        {352, 240}, {288, 352}, {352, 288}, {176, 144}, {144, 176},
    };

    public int getResolutionWidth(String resolution) {
        for (int i = 0; i < mVideoResolutionStrings.length; i++) {
            if (mVideoResolutionStrings[i].equals(resolution)) {
                return mVideoResolution[i][0];
            }
        }
        return RESOLUTION_WIDTH;
    }

    public int getResolutionHeight(String resolution) {
        for (int i = 0; i < mVideoResolutionStrings.length; i++) {
            if (mVideoResolutionStrings[i].equals(resolution)) {
                return mVideoResolution[i][1];
            }
        }
        return RESOLUTION_HEIGHT;
    }

    /**
     * Enum of the different states the application can be in. Mainly used to decide
     * how
     * different features of the app UI will be styled.
     */
    public enum ConnectionStatus {
        OFFLINE(0),
        DISCONNECTED(1),
        CONNECTING(2),
        CONNECTED(3),
        ACTIVE_CALL(4);

        private final int mValue;

        ConnectionStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefsHandler = new SharedPrefsHandler(prefs);
        editor = prefs.edit();
        editor.putBoolean(HANDSHAKE_PORT_PREF, false);
        editor.apply();

        Context context = getApplicationContext();
        MediaManagerCallback callback = new MediaManagerCallback();
        mExecutor = Executors.newSingleThreadExecutor();
        mImsMediaManager = new ImsMediaManager(context, mExecutor, callback);

        mBottomSheetDialog = new BottomSheetDialer(this);
        mBottomSheetDialog.setContentView(R.layout.dialer);

        mBottomSheetAudioCodecSettings = new BottomSheetAudioCodecSettings(this);
        mBottomSheetAudioCodecSettings.setContentView(R.layout.audio_codec_change);
        updateCodecSelectionFromPrefs();

        updateUI(ConnectionStatus.OFFLINE);
        updateAdditionalMedia();

        mAudioSession = null;
        mVideoSession = null;
        mTextSession = null;
        mDelay = 100;
    }

    @Override
    protected void onStart() {
        super.onStart();
        styleMainActivity();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        styleMainActivity();
        updateAdditionalMedia();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAudioRtp != null) {
            mAudioRtp.close();
        }
        if (mAudioRtcp != null) {
            mAudioRtcp.close();
        }
        if (mVideoRtp != null) {
            mVideoRtp.close();
        }
        if (mVideoRtcp != null) {
            mVideoRtcp.close();
        }
        if (mTextRtp != null) {
            mTextRtp.close();
        }
        if (mTextRtcp != null) {
            mTextRtcp.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.homeMenuButton:
                setContentView(R.layout.activity_main);
                styleMainActivity();
                break;

            case R.id.settingsMenuButton:
                setContentView(R.layout.settings);
                setupSettingsPage();
                break;

            case R.id.settingsVideoMenuButton:
                setContentView(R.layout.settings_video);
                setupVideoSettingsPage();
                break;

            default:
                throw new IllegalStateException("Unexpected value: " + item.getItemId());
        }
        return super.onOptionsItemSelected(item);
    }

    private class MediaManagerCallback implements ImsMediaManager.OnConnectedCallback {

        @Override
        public void onConnected() {
            Log.d(TAG, "ImsMediaManager - connected");
            mIsMediaManagerReady = true;
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "ImsMediaManager - disconnected");
            mIsMediaManagerReady = false;
            updateUI(ConnectionStatus.CONNECTED);
        }
    }

    private class RtpAudioSessionCallback extends AudioSessionCallback {

        @Override
        public void onModifySessionResponse(AudioConfig config, int result) {
            Log.d(TAG, "onModifySessionResponse");
        }

        @Override
        public void onOpenSessionFailure(int error) {
            Log.e(TAG, "onOpenSessionFailure - error=" + error);
        }

        @Override
        public void onOpenSessionSuccess(ImsMediaSession session) {
            mAudioSession = (ImsAudioSession) session;
            Log.d(TAG, "onOpenSessionSuccess: id=" + mAudioSession.getSessionId());
            mIsOpenSessionSent = true;

            MediaQualityThreshold threshold = createMediaQualityThreshold(RTP_TIMEOUT,
                    RTCP_TIMEOUT, RTP_HYSTERESIS_TIME, RTP_PACKET_LOSS_DURATION, PACKET_LOSS_RATE,
                    JITTER_THRESHOLD, NOTIFY_STATUS);
            mAudioSession.setMediaQualityThreshold(threshold);
            mAudioSession.modifySession(mAudioConfig);
            mAudioSession.requestRtpReceptionStats(3000);

            AudioManager audioManager = getSystemService(AudioManager.class);
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            updateUI(ConnectionStatus.ACTIVE_CALL);
        }

        @Override
        public void onAddConfigResponse(AudioConfig config, int result) {
            Log.d(TAG, "onAddConfigResponse");
        }

        @Override
        public void onConfirmConfigResponse(AudioConfig config, int result) {
            Log.d(TAG, "onConfirmConfigResponse");
        }

        @Override
        public void onFirstMediaPacketReceived(AudioConfig config) {
            Log.d(TAG, "onFirstMediaPacketReceived");
        }

        @Override
        public void onHeaderExtensionReceived(final List<RtpHeaderExtension> extensions) {
            Log.d(TAG, "onHeaderExtensionReceived, list size=" + extensions.size()
                    + "list=" + extensions);
        }

        @Override
        public void triggerAnbrQuery(AudioConfig config) {
            Log.d(TAG, "triggerAnbrQuery");
        }

        @Override
        public void onDtmfReceived(char dtmfDigit, int durationMs) {
            Log.d(TAG, "onDtmfReceived digit: " + dtmfDigit + " duration: " + durationMs);
        }

        @Override
        public void notifyMediaQualityStatus(final MediaQualityStatus status) {
            Log.d(TAG, "notifyMediaQualityStatus, status=" + status);
        }

        @Override
        public void onCallQualityChanged(CallQuality callQuality) {
            Log.d(TAG, "onCallQualityChanged, callQuality=" + callQuality);
            Log.d(TAG, "onCallQualityChanged, discardRate="
                    + (double) callQuality.getNumDroppedRtpPackets()
                    / callQuality.getNumRtpPacketsReceived() * 100);
            Log.d(TAG, "onCallQualityChanged, lossRate="
                    + (double) callQuality.getNumRtpPacketsNotReceived()
                    / callQuality.getNumRtpPacketsReceived() * 100);
            Log.d(TAG, "onCallQualityChanged, maxPlayoutDelay="
                    + (double) callQuality.getMaxPlayoutDelayMillis());
        }

        @Override
        public void notifyRtpReceptionStats(final RtpReceptionStats stats) {
            Log.d(TAG, "notifyRtpReceptionStats, RtpReceptionStats=" + stats);
        }
    }

    private class RtpVideoSessionCallback extends VideoSessionCallback {
        @Override
        public void onOpenSessionFailure(int error) {
            Log.e(TAG, "onOpenSessionFailure - error=" + error);
        }

        @Override
        public void onOpenSessionSuccess(ImsMediaSession session) {
            mVideoSession = (ImsVideoSession) session;
            Log.d(TAG, "onOpenSessionSuccess: id=" + mVideoSession.getSessionId());
            mIsVideoSessionOpened = true;

            MediaQualityThreshold threshold = createMediaQualityThreshold(RTP_TIMEOUT,
                    RTCP_TIMEOUT, RTP_HYSTERESIS_TIME, RTP_PACKET_LOSS_DURATION, PACKET_LOSS_RATE,
                    JITTER_THRESHOLD, NOTIFY_STATUS);
            mVideoSession.setMediaQualityThreshold(threshold);

            int rtcpfbTypes = 0;
            for (int types : mSelectedRtcpFbTypes) {
                rtcpfbTypes |= types;
            }

            mVideoConfig = createVideoConfig(mSelectedVideoCodec, mSelectedVideoMode,
                    mSelectedFramerate, mSelectedBitrate, mSelectedCodecProfile,
                    mSelectedCodecLevel, mSelectedCameraId, mSelectedCameraZoom,
                    mSelectedDeviceOrientationDegree,
                    mSelectedCvoValue, rtcpfbTypes,
                    getResolutionWidth(mSelectedVideoResolution),
                    getResolutionHeight(mSelectedVideoResolution));

            Log.d(TAG, "VideoConfig: " + mVideoConfig.toString());
            mVideoSession.modifySession(mVideoConfig);

            runOnUiThread(() -> {
                if (mIsPreviewSurfaceSet) {
                    mVideoSession.setPreviewSurface(mPreviewSurface);
                }
                if (mIsDisplaySurfaceSet) {
                    mVideoSession.setDisplaySurface(mDisplaySurface);
                }
            });
        }

        @Override
        public void onModifySessionResponse(VideoConfig config,
                final @ImsMediaSession.SessionOperationResult int result) {
            Log.d(TAG, "onModifySessionResponse");
        }

        @Override
        public void onPeerDimensionChanged(final int width, final int height) {
            Log.d(TAG, "onPeerDimensionChanged - width=" + width + ", height=" + height);
        }

        @Override
        public void notifyBitrate(final int bitrate) {
            Log.d(TAG, "notifyBitrate - bitrate=" + bitrate);
        }
    }

    private class RtpTextSessionCallback extends TextSessionCallback {
        @Override
        public void onOpenSessionFailure(int error) {
            Log.e(TAG, "onOpenSessionFailure - error=" + error);
        }

        @Override
        public void onOpenSessionSuccess(ImsMediaSession session) {
            mTextSession = (ImsTextSession) session;
            Log.d(TAG, "onOpenSessionSuccess: id=" + mTextSession.getSessionId());
            mIsTextSessionOpened = true;
        }

        @Override
        public void onRttReceived(final String text) {
            Log.d(TAG, "onRttReceived: text=" + text);
            if (mIsTextSessionOpened) {
                runOnUiThread(() -> {
                    TextView view = findViewById(R.id.receivedText);
                    view.setText(text);
                });
            }
        }
    }

    private void updateAdditionalMedia() {
        Log.d(TAG, "updateAdditionalMedia()");
        ArrayList<String> listAdditionalMedia = new ArrayList<>();
        ArrayAdapter<String> spinnerAdaptor = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, listAdditionalMedia);

        listAdditionalMedia.add(getString(R.string.media_none));
        listAdditionalMedia.add(getString(R.string.media_video));
        listAdditionalMedia.add(getString(R.string.media_text));
        spinnerAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinnerAdditionalMedia = findViewById(R.id.spinnerAdditionalMedia);
        spinnerAdditionalMedia.setAdapter(spinnerAdaptor);
        spinnerAdditionalMedia.setOnItemSelectedListener(mAdditionalMediaListener);
        if (mVideoEnabled) {
            spinnerAdditionalMedia.setSelection(1);
        } else if (mTextEnabled) {
            spinnerAdditionalMedia.setSelection(2);
        } else {
            spinnerAdditionalMedia.setSelection(0);
        }
    }

    private AdapterView.OnItemSelectedListener mAdditionalMediaListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view,
                        int i, long l) {
                    String str = adapterView.getItemAtPosition(i).toString();
                    Log.d(TAG, "onItemSelected() str=" + str);
                    if (str.equals(getString(R.string.media_video))) {
                        mVideoEnabled = true;
                        mTextEnabled = false;
                    } else if (str.equals(getString(R.string.media_text))) {
                        mTextEnabled = true;
                        mVideoEnabled = false;
                    } else {
                        mVideoEnabled = false;
                        mTextEnabled = false;
                    }
                    updateVideoUi(mVideoEnabled);
                    updateTextUi(mTextEnabled);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            };

    private WifiInfo retrieveNetworkConfig() {
        WifiManager wifiManager = (WifiManager) getApplication()
                .getSystemService(Context.WIFI_SERVICE);
        return wifiManager.getConnectionInfo();
    }

    private String getLocalIpAddress() {
        return Formatter.formatIpAddress(retrieveNetworkConfig().getIpAddress());
    }

    private String getOtherDeviceIp() {
        return prefs.getString("OTHER_IP_ADDRESS", "localhost");
    }

    private int getOtherDevicePort() {
        return prefs.getInt("OTHER_HANDSHAKE_PORT", -1);
    }

    private int getRemoteDevicePortEditText() {
        EditText portBox = findViewById(R.id.remotePortNumberEditText);
        return Integer.parseInt(portBox.getText().toString());
    }

    private String getRemoteDeviceIpEditText() {
        EditText ipBox = findViewById(R.id.remoteDeviceIpEditText);
        return ipBox.getText().toString();
    }

    private int getVideoRemoteDevicePortEditText() {
        EditText portBox = findViewById(R.id.remoteVideoPortNumberEditText);
        return Integer.parseInt(portBox.getText().toString());
    }

    private String getVideoRemoteDeviceIpEditText() {
        EditText ipBox = findViewById(R.id.videoRemoteDeviceIpEditText);
        return ipBox.getText().toString();
    }

    private void createTextureView() {
        Log.d(TAG, "createTextureView");
        mTexturePreview = (TextureView) findViewById(R.id.texturePreview);
        assert mTexturePreview != null;
        mTexturePreview.setSurfaceTextureListener(mPreviewListener);
        if (mTexturePreview.isAvailable()) {
            Log.d(TAG, "preview available");
            mTexturePreview.setLayoutParams(
                    new FrameLayout.LayoutParams(300, 400, Gravity.CENTER));
            mTexturePreview.setKeepScreenOn(true);
            mTexturePreview.getSurfaceTexture().setDefaultBufferSize(
                    RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
        }
        mTextureDisplay = (TextureView) findViewById(R.id.textureDisplay);
        assert mTextureDisplay != null;
        mTextureDisplay.setSurfaceTextureListener(mDisplayListener);
        if (mTextureDisplay.isAvailable()) {
            Log.d(TAG, "display available");
            mTextureDisplay.setLayoutParams(
                    new FrameLayout.LayoutParams(300, 400, Gravity.CENTER));
            mTextureDisplay.setKeepScreenOn(true);
            mTextureDisplay.getSurfaceTexture().setDefaultBufferSize(
                    RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
        }
    }

    /**
     * Opens two datagram sockets for audio rtp and rtcp, and a third for the handshake between
     * devices if true is passed in the parameter.
     *
     * @param openHandshakePort boolean value to open a port for the handshake.
     */
    private void openPorts(boolean openHandshakePort) {
        Log.d(TAG, "openPorts");
        mAudioRtp = createDatagramSocket(getLocalIpAddress(), 10000);
        mAudioRtcp = createDatagramSocket(getLocalIpAddress(),
                mAudioRtp.getLocalPort() + 1);
        mVideoRtp = createDatagramSocket(getLocalIpAddress(), 20000);
        mVideoRtcp = createDatagramSocket(getLocalIpAddress(),
                mVideoRtp.getLocalPort() + 1);
        mTextRtp = createDatagramSocket(getLocalIpAddress(), 30000);
        mTextRtcp = createDatagramSocket(getLocalIpAddress(),
                mTextRtp.getLocalPort() + 1);
        if (openHandshakePort) {
            mHandshakeReceptionSocket = new HandshakeReceiver(prefs);
            mHandshakeReceptionSocket.run();
        }
    }

    /**
     * Closes the handshake, rtp, and rtcp ports if they have been opened or instantiated.
     */
    private void closePorts() {
        Log.d(TAG, "closePorts");
        if (mHandshakeReceptionSocket != null) {
            mHandshakeReceptionSocket.close();
        }
        closeDatagramSocket(mAudioRtp);
        closeDatagramSocket(mAudioRtcp);
        closeDatagramSocket(mVideoRtp);
        closeDatagramSocket(mVideoRtcp);
        closeDatagramSocket(mTextRtp);
        closeDatagramSocket(mTextRtcp);
    }

    private DatagramSocket createDatagramSocket(@NonNull String address, int port) {
        DatagramSocket socket = null;

        try {
            socket = new DatagramSocket(null);

            if (socket == null) {
                Log.e(TAG, "socket not found");
                return null;
            }

            socket.setReuseAddress(true);
            InetAddress inetAddress = createInetAddress(address);
            if (inetAddress == null) {
                Log.e(TAG, "inetAddress not found");
                closeDatagramSocket(socket);
                return null;
            }

            InetSocketAddress sockAddr = new InetSocketAddress(inetAddress, port);
            socket.bind(sockAddr);

            Network network = getNetworkForIpAddress(inetAddress);
            if (network == null) {
                Log.e(TAG, "Network not found");
                closeDatagramSocket(socket);
                return null;
            }

            network.bindSocket(socket);
        } catch (SocketException e) {
            Log.e(TAG, "SocketException: " + e.toString());
        } catch (UnknownHostException e) {
            Log.e(TAG, "UnknownHostException: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.toString());
            closeDatagramSocket(socket);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException: " + e.toString());
            closeDatagramSocket(socket);
        }

        Log.v(TAG, "DatagramSocket created");
        return socket;
    }

    public void closeDatagramSocket(DatagramSocket socket) {
        if (socket != null) {
            socket.close();
            Log.v(TAG, "DatagramSocket closed");
        }
    }

    private Network getNetworkForIpAddress(InetAddress addr) {
        ConnectivityManager cm =
                getApplication().getSystemService(ConnectivityManager.class);

        if (cm == null) {
            return null;
        }

        Network[] networks = cm.getAllNetworks();

        if (networks == null) {
            return null;
        }

        for (Network network : networks) {
            LinkProperties lp = cm.getLinkProperties(network);

            if (lp == null) {
                continue;
            }

            List<InetAddress> linkAddrs = lp.getAddresses();
            for (InetAddress linkAddr : linkAddrs) {
                if (addr.equals(linkAddr)) {
                    return network;
                }
            }
        }
        return null;
    }

    private InetAddress createInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.toString());
        }

        return null;
    }

    /**
     * texture view listener for preview
     */
    TextureView.SurfaceTextureListener mPreviewListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable - preview, width=" + width + ",height=" + height);
            surface.setDefaultBufferSize(RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
            mPreviewSurface = new Surface(surface);
            mIsPreviewSurfaceSet = true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged, width=" + width + ", height=" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            surface.setDefaultBufferSize(RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
            mPreviewSurface = new Surface(surface);
            mIsPreviewSurfaceSet = true;
        }
    };

    /**
     * texture view listener for display
     */
    TextureView.SurfaceTextureListener mDisplayListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable - display, width=" + width + ",height=" + height);
            surface.setDefaultBufferSize(RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
            mDisplaySurface = new Surface(surface);
            mIsDisplaySurfaceSet = true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged, width=" + width + ", height=" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            surface.setDefaultBufferSize(RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
            mDisplaySurface = new Surface(surface);
            mIsDisplaySurfaceSet = true;
        }
    };

    /**
     * After the ports are open this runnable is called to wait for in incoming handshake to pair
     * with the remote device.
     */
    Runnable handleIncomingHandshake = new Runnable() {
        @Override
        public void run() {
            try {
                while (!mHandshakeReceptionSocket.isHandshakeReceived()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }

                mRemoteDeviceInfo = mHandshakeReceptionSocket.getReceivedDeviceInfo();

                HandshakeSender handshakeSender = new HandshakeSender(
                        mRemoteDeviceInfo.getInetAddress(),
                        mRemoteDeviceInfo.getHandshakePort());
                mLocalDeviceInfo = createMyDeviceInfo();
                handshakeSender.setData(mLocalDeviceInfo);
                handshakeSender.run();

                while (!mHandshakeReceptionSocket.isConfirmationReceived()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }

                handshakeSender = new HandshakeSender(mRemoteDeviceInfo.getInetAddress(),
                        mRemoteDeviceInfo.getHandshakePort());
                handshakeSender.setData(CONFIRMATION_MESSAGE);
                handshakeSender.run();
                Log.d(TAG, "Handshake has been completed. Devices are connected.");
                editor.putString("OTHER_IP_ADDRESS",
                        mRemoteDeviceInfo.getInetAddress().getHostName());
                editor.putInt("OTHER_HANDSHAKE_PORT",
                        mRemoteDeviceInfo.getAudioRtpPort());
                editor.apply();
                updateUI(ConnectionStatus.CONNECTED);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }

        }
    };

    /**
     * This runnable controls the handshake process from the user that is attempting to connect to
     * the remote device. First it will create and send a DeviceInfo object that contains the local
     * devices info, and wait until it receives the remote DeviceInfo. After it receives the remote
     * DeviceInfo it will save it into memory and send a conformation String back, then wait until
     * it receives a conformation String.
     */
    Runnable initiateHandshake = new Runnable() {
        @Override
        public void run() {
            try {
                HandshakeSender sender = new HandshakeSender(InetAddress.getByName(
                        getOtherDeviceIp()), getOtherDevicePort());
                mLocalDeviceInfo = createMyDeviceInfo();
                sender.setData(mLocalDeviceInfo);
                sender.run();
                mRemoteDeviceInfo = mHandshakeReceptionSocket.getReceivedDeviceInfo();
                sender.setData(CONFIRMATION_MESSAGE);
                sender.run();
                Log.d(TAG, "Handshake successful, devices connected.");
                updateUI(ConnectionStatus.CONNECTED);
            } catch (Exception e) {
                Log.e(TAG, "initiateHandshake(): e=" + e.toString());
            }
        }
    };

    /**
     * Creates and returns a DeviceInfo object with the local port, ip, and audio codec settings
     *
     * @return DeviceInfo object containing the local device's information
     */
    public DeviceInfo createMyDeviceInfo() {
        try {
            return new DeviceInfo.Builder()
                    .setInetAddress(InetAddress.getByName(getLocalIpAddress()))
                    .setHandshakePort(mHandshakeReceptionSocket != null
                            ? mHandshakeReceptionSocket.getBoundSocket() : 0)
                    .setAudioRtpPort(mAudioRtp.getLocalPort())
                    .setVideoRtpPort(mVideoRtp.getLocalPort())
                    .setTextRtpPort(mTextRtp.getLocalPort())
                    .setAudioCodecs(mSelectedCodecTypes)
                    .setAmrModes(mSelectedAmrModes)
                    .setEvsBandwidths(mSelectedEvsBandwidths)
                    .setEvsModes(mSelectedEvsModes)
                    .setVideoCodec(mSelectedVideoCodec)
                    .setVideoResolutionWidth(RESOLUTION_WIDTH)
                    .setVideoResolutionHeight(RESOLUTION_HEIGHT)
                    .setVideoCvoValue(mSelectedCvoValue)
                    .setRtcpFbTypes(mSelectedRtcpFbTypes)
                    .build();

        } catch (UnknownHostException e) {
            Log.e(TAG, "UnknownHostException: " + e.toString());
        }
        return null;
    }

    /**
     * Updates the mConnectionStatus and restyles the UI
     *
     * @param newStatus The new ConnectionStatus used to style the UI
     */
    public void updateUI(ConnectionStatus newStatus) {
        mConnectionStatus = newStatus;
        styleMainActivity();
    }

    /**
     * Creates and returns an InetSocketAddress from the remote device that is
     * connected to the
     * local device.
     *
     * @return the InetSocketAddress of the remote device
     */
    private InetSocketAddress getRemoteAudioSocketAddress() {
        int remotePort = mRemoteDeviceInfo.getAudioRtpPort();
        InetAddress remoteInetAddress = mRemoteDeviceInfo.getInetAddress();
        return new InetSocketAddress(remoteInetAddress, remotePort);
    }

    /**
     * Creates and returns an InetSocketAddress from the remote device that is
     * connected to the
     * local device.
     *
     * @return the InetSocketAddress of the remote device
     */
    private InetSocketAddress getRemoteVideoSocketAddress() {
        int remotePort = mRemoteDeviceInfo.getVideoRtpPort();
        InetAddress remoteInetAddress = mRemoteDeviceInfo.getInetAddress();
        return new InetSocketAddress(remoteInetAddress, remotePort);
    }

    /**
     * Creates and returns an InetSocketAddress from the remote device that is connected to the
     * local device.
     *
     * @return the InetSocketAddress of the remote device
     */
    private InetSocketAddress getRemoteTextSocketAddress() {
        int remotePort = mRemoteDeviceInfo.getTextRtpPort();
        InetAddress remoteInetAddress = mRemoteDeviceInfo.getInetAddress();
        return new InetSocketAddress(remoteInetAddress, remotePort);
    }

    /**
     * Builds and returns an RtcpConfig for the remote device that is connected to the local device.
     *
     * @return the RtcpConfig for the remote device
     */
    private RtcpConfig getRemoteAudioRtcpConfig() {
        return new RtcpConfig.Builder()
                .setCanonicalName("audio rtcp config")
                .setTransmitPort(mRemoteDeviceInfo.getAudioRtpPort() + 1)
                .setIntervalSec(5)
                .setRtcpXrBlockTypes(RtcpConfig.FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK
                        | RtcpConfig.FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK)
                .build();
    }

    private RtcpConfig getRemoteVideoRtcpConfig() {
        return new RtcpConfig.Builder()
                .setCanonicalName("video rtcp config")
                .setTransmitPort(mRemoteDeviceInfo.getVideoRtpPort() + 1)
                .setIntervalSec(5)
                .setRtcpXrBlockTypes(0)
                .build();
    }

    private RtcpConfig getRemoteTextRtcpConfig() {
        return new RtcpConfig.Builder()
                .setCanonicalName("text rtcp config")
                .setTransmitPort(mRemoteDeviceInfo.getTextRtpPort() + 1)
                .setIntervalSec(5)
                .setRtcpXrBlockTypes(0)
                .build();
    }

    /**
     * Creates and returns a new AudioConfig
     *
     * @param remoteRtpAddress - InetSocketAddress of the remote device
     * @param rtcpConfig       - RtcpConfig of the remove device
     * @param audioCodec       - the type of AudioCodec
     * @param amrParams        - the settings if the AudioCodec is an AMR variant
     * @param evsParams        - the settings if the AudioCodec is EVS
     * @return an AudioConfig with the given params
     */
    private AudioConfig createAudioConfig(InetSocketAddress remoteRtpAddress,
            RtcpConfig rtcpConfig, int audioCodec, AmrParams amrParams, EvsParams evsParams) {
        AudioConfig config;

        if (audioCodec == AudioConfig.CODEC_AMR) {
            config = new AudioConfig.Builder()
                    .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                    .setAccessNetwork(AccessNetworkType.EUTRAN)
                    .setRemoteRtpAddress(remoteRtpAddress)
                    .setRtcpConfig(rtcpConfig)
                    .setDscp((byte) DSCP)
                    .setRxPayloadTypeNumber((byte) AUDIO_RX_PAYLOAD_TYPE_NUMBER)
                    .setTxPayloadTypeNumber((byte) AUDIO_TX_PAYLOAD_TYPE_NUMBER)
                    .setSamplingRateKHz((byte) 8)
                    .setPtimeMillis((byte) P_TIME_MILLIS)
                    .setMaxPtimeMillis((byte) MAX_P_TIME_MILLIS)
                    .setDtxEnabled(true)
                    .setTxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setRxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setDtmfSamplingRateKHz((byte) 8)
                    .setCodecType(audioCodec)
                    .setAmrParams(amrParams)
                    // TODO audio is currently only working when amr params are set as well
                    .setEvsParams(evsParams)
                    .build();
        } else if (audioCodec == AudioConfig.CODEC_AMR_WB) {
            config = new AudioConfig.Builder()
                    .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                    .setAccessNetwork(AccessNetworkType.EUTRAN)
                    .setRemoteRtpAddress(remoteRtpAddress)
                    .setRtcpConfig(rtcpConfig)
                    .setDscp((byte) DSCP)
                    .setRxPayloadTypeNumber((byte) AUDIO_RX_PAYLOAD_TYPE_NUMBER)
                    .setTxPayloadTypeNumber((byte) AUDIO_TX_PAYLOAD_TYPE_NUMBER)
                    .setSamplingRateKHz((byte) SAMPLING_RATE_KHZ)
                    .setPtimeMillis((byte) P_TIME_MILLIS)
                    .setMaxPtimeMillis((byte) MAX_P_TIME_MILLIS)
                    .setDtxEnabled(true)
                    .setTxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setRxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setDtmfSamplingRateKHz((byte) SAMPLING_RATE_KHZ)
                    .setCodecType(audioCodec)
                    .setAmrParams(amrParams)
                    // TODO audio is currently only working when amr params are set as well
                    .setEvsParams(evsParams)
                    .build();
        } else if (audioCodec == AudioConfig.CODEC_EVS) {
            config = new AudioConfig.Builder()
                    .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                    .setAccessNetwork(AccessNetworkType.EUTRAN)
                    .setRemoteRtpAddress(remoteRtpAddress)
                    .setRtcpConfig(rtcpConfig)
                    .setDscp((byte) DSCP)
                    .setRxPayloadTypeNumber((byte) AUDIO_RX_PAYLOAD_TYPE_NUMBER)
                    .setTxPayloadTypeNumber((byte) AUDIO_TX_PAYLOAD_TYPE_NUMBER)
                    .setSamplingRateKHz((byte) SAMPLING_RATE_KHZ)
                    .setPtimeMillis((byte) P_TIME_MILLIS)
                    .setMaxPtimeMillis((byte) MAX_P_TIME_MILLIS)
                    .setDtxEnabled(true)
                    .setTxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setRxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setDtmfSamplingRateKHz((byte) DTMF_SAMPLING_RATE_KHZ)
                    .setCodecType(audioCodec)
                    // TODO audio is currently only working when amr params are set as well
                    .setAmrParams(amrParams)
                    .setEvsParams(evsParams)
                    .build();
        } else {
            config = new AudioConfig.Builder()
                    .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                    .setAccessNetwork(AccessNetworkType.EUTRAN)
                    .setRemoteRtpAddress(remoteRtpAddress)
                    .setRtcpConfig(rtcpConfig)
                    .setDscp((byte) DSCP)
                    .setRxPayloadTypeNumber((byte) AUDIO_RX_PAYLOAD_TYPE_NUMBER)
                    .setTxPayloadTypeNumber((byte) AUDIO_TX_PAYLOAD_TYPE_NUMBER)
                    .setSamplingRateKHz((byte) SAMPLING_RATE_KHZ)
                    .setPtimeMillis((byte) P_TIME_MILLIS)
                    .setMaxPtimeMillis((byte) MAX_P_TIME_MILLIS)
                    .setDtxEnabled(true)
                    .setTxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setRxDtmfPayloadTypeNumber((byte) DTMF_PAYLOAD_TYPE_NUMBER)
                    .setDtmfSamplingRateKHz((byte) DTMF_SAMPLING_RATE_KHZ)
                    .setCodecType(audioCodec)
                    .build();
        }
        return config;
    }

    private VideoConfig createVideoConfig(InetSocketAddress remoteRtpAddress,
            RtcpConfig rtcpConfig, int codecType, int videoMode, int framerate, int bitrate,
            int profile, int level, int cameraId, int cameraZoom, int deviceOrientation, int cvo,
            int rtcpFbTypes, int width, int height) {
        VideoConfig config = new VideoConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(remoteRtpAddress)
                .setRtcpConfig(rtcpConfig)
                .setDscp((byte) DSCP)
                .setRxPayloadTypeNumber((byte) VIDEO_RX_PAYLOAD_TYPE_NUMBER)
                .setTxPayloadTypeNumber((byte) VIDEO_TX_PAYLOAD_TYPE_NUMBER)
                .setMaxMtuBytes(MAX_MTU_BYTES)
                .setSamplingRateKHz((byte) 90)
                .setCodecType(codecType)
                .setVideoMode(videoMode)
                .setFramerate(framerate)
                .setBitrate(bitrate)
                .setCodecProfile(profile)
                .setCodecLevel(level)
                .setIntraFrameIntervalSec(IDR_INTERVAL)
                .setPacketizationMode(VideoConfig.MODE_NON_INTERLEAVED)
                .setCameraId(cameraId)
                .setCameraZoom(cameraZoom)
                .setResolutionWidth(width)
                .setResolutionHeight(height)
                .setPauseImagePath(IMAGE)
                .setDeviceOrientationDegree(deviceOrientation)
                .setCvoValue(cvo)
                .setRtcpFbTypes(rtcpFbTypes)
                .build();
        return config;
    }

    private TextConfig createTextConfig(InetSocketAddress remoteRtpAddress,
            RtcpConfig rtcpConfig, int codecType) {
        TextConfig config = new TextConfig.Builder()
                .setMediaDirection(RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE)
                .setAccessNetwork(AccessNetworkType.EUTRAN)
                .setRemoteRtpAddress(remoteRtpAddress)
                .setRtcpConfig(rtcpConfig)
                .setDscp((byte) DSCP)
                .setRxPayloadTypeNumber((byte) TEXT_RX_PAYLOAD_TYPE_NUMBER)
                .setTxPayloadTypeNumber((byte) TEXT_TX_PAYLOAD_TYPE_NUMBER)
                .setSamplingRateKHz((byte) 10)
                .setCodecType(codecType)
                .setBitrate(1000)
                .setRedundantPayload((byte) TEXT_REDUNDANT_PAYLOAD_TYPE_NUMBER)
                .setRedundantLevel((byte) 2)
                .setKeepRedundantLevel(true)
                .build();
        return config;
    }

    private MediaQualityThreshold createMediaQualityThreshold(int[] rtpInactivityTimerMillis,
            int rtcpInactivityTimerMillis, int rtpHysteresisTimeInMillis,
            int rtpPacketLossDurationMillis, int[] rtpPacketLossRate, int[] rtpJitterMillis,
            boolean notifyCurrentStatus) {
        return new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(rtpInactivityTimerMillis)
                .setRtcpInactivityTimerMillis(rtcpInactivityTimerMillis)
                .setRtpHysteresisTimeInMillis(rtpHysteresisTimeInMillis)
                .setRtpPacketLossDurationMillis(rtpPacketLossDurationMillis)
                .setRtpPacketLossRate(rtpPacketLossRate)
                .setRtpJitterMillis(rtpJitterMillis)
                .setNotifyCurrentStatus(notifyCurrentStatus)
                .setVideoBitrateBps(VIDEO_BITRATE_THRESHOLD_BPS)
                .build();
    }

    /**
     * @param amrMode Integer value of the AmrMode
     * @return AmrParams object with the passed AmrMode value
     */
    private AmrParams createAmrParams(int amrMode, boolean octateAligned, int maxRed) {
        return new AmrParams.Builder()
            .setAmrMode(amrMode)
            .setOctetAligned(octateAligned)
            .setMaxRedundancyMillis(maxRed)
            .build();
    }

    /**
     * @param evsBand Integer value of the EvsBandwidth
     * @param evsMode Integer value of the EvsMode
     * @return EvsParams object with the passed EvsBandwidth and EvsMode
     */
    private EvsParams createEvsParams(int evsBand, int evsMode) {
        return new EvsParams.Builder()
                .setEvsbandwidth(evsBand)
                .setEvsMode(evsMode)
                .setChannelAwareMode((byte) 3)
                .setHeaderFullOnly(true)
                .setCodecModeRequest((byte) 15)
                .build();
    }

    /**
     * Determines the audio codec to use to configure the AudioConfig object. The
     * function uses
     * the order arrays of Integers to determine the priority of a given codec,
     * mode, and
     * bandwidth. Then creates and returns a AudioConfig object containing it.
     *
     * @param localDevice  DeviceInfo object containing the local device's
     *                     information
     * @param remoteDevice DeviceInfo object containing the remote device's
     *                     information
     * @return AudioConfig containing the selected audio codec, determined by the
     *         algorithm
     */
    private AudioConfig determineAudioConfig(DeviceInfo localDevice, DeviceInfo remoteDevice) {
        AmrParams amrParams = null;
        EvsParams evsParams = null;

        int selectedCodec = determineCommonCodecSettings(localDevice.getAudioCodecs(),
                remoteDevice.getAudioCodecs(), CODEC_ORDER);

        switch (selectedCodec) {
            case CodecType.AMR:
            case CodecType.AMR_WB:
                int amrMode = determineCommonCodecSettings(localDevice.getAmrModes(),
                    remoteDevice.getAmrModes(), AMR_MODE_ORDER);
                amrParams = createAmrParams(amrMode, false, 0);
                break;

            case CodecType.EVS:
                int evsMode = determineCommonCodecSettings(localDevice.getEvsModes(),
                        remoteDevice.getEvsModes(), EVS_MODE_ORDER);
                int evsBand = determineCommonCodecSettings(localDevice.getEvsBandwidths(),
                        remoteDevice.getEvsBandwidths(), EVS_BANDWIDTH_ORDER);
                evsParams = createEvsParams(evsBand, evsMode);
                amrParams = createAmrParams(0, false, 0);
                break;

            case -1:
                return createAudioConfig(CodecType.AMR_WB,
                    createAmrParams(AmrMode.AMR_MODE_4, false, 0), null);
        }

        return createAudioConfig(selectedCodec, amrParams, evsParams);
    }

    /**
     * Helper function used to determine the highest ranking codec, mode, or
     * bandwidth between
     * two devices.
     *
     * @param localSet     the set containing the local device's selection of
     *                     codecs, modes, or
     *                     bandwidths
     * @param remoteSet    the set containing the remote device's selection of
     *                     codecs, modes, or
     *                     bandwidths
     * @param codecSetting the Integer array containing the ranking order of the
     *                     different values
     * @return highest ranking mode, codec, bandwidth, or -1 if no match is found
     */
    private int determineCommonCodecSettings(Set<Integer> localSet, Set<Integer> remoteSet,
            int[] codecSetting) {
        Log.d(TAG, "determineCommonCodecSettings() - localSet : " + localSet);
        int negotiatedMode = 0;
        for (int setting : codecSetting) {
            if (localSet.contains(setting) && remoteSet.contains(setting)) {
                negotiatedMode |= setting;
            }
        }
        return negotiatedMode;
    }

    /**
     * Creates an AudioConfig object depending on the passed parameters and returns it.
     *
     * @param audioCodec Integer value of the CodecType
     * @param amrParams  AmrParams object to be set in the AudioConfig
     * @param evsParams  EvsParams object to be set in the AudioConfig
     * @return an AudioConfig with the passed parameters and default values.
     */
    private AudioConfig createAudioConfig(int audioCodec, AmrParams amrParams,
            EvsParams evsParams) {
        AudioConfig mAudioConfig = null;
        // TODO - evs params must be present to hear audio currently, regardless of codec
        EvsParams mEvs = new EvsParams.Builder()
                .setEvsbandwidth(EvsParams.EVS_BAND_NONE)
                .setEvsMode(EvsParams.EVS_MODE_0)
                .setChannelAwareMode((byte) 3)
                .setHeaderFullOnly(true)
                .setCodecModeRequest((byte) 15)
                .build();

        switch (audioCodec) {
            case CodecType.AMR:
            case CodecType.AMR_WB:
                mAudioConfig = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, amrParams, mEvs);
                break;

            case CodecType.EVS:
                mAudioConfig = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, amrParams, evsParams);
                break;

            case CodecType.PCMA:
            case CodecType.PCMU:
                mAudioConfig = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, null, null);
                break;

        }

        return mAudioConfig;
    }

    /**
     * Creates a VideoConfig object depending on the passed parameters and returns it.
     *
     * @return a VideoConfig with the passed parameters and default values.
     */
    private VideoConfig createVideoConfig(int codecType, int videoMode, int framerate, int bitrate,
            int profile, int level, int cameraId, int cameraZoom, int deviceOrientation, int cvo,
            int rtcpFbTypes, int width, int height) {
        VideoConfig videoConfig = null;

        switch (codecType) {
            case VideoConfig.VIDEO_CODEC_AVC:
            case VideoConfig.VIDEO_CODEC_HEVC:
                videoConfig = createVideoConfig(getRemoteVideoSocketAddress(),
                        getRemoteVideoRtcpConfig(), codecType, videoMode, framerate, bitrate,
                        profile, level, cameraId, cameraZoom, deviceOrientation, cvo, rtcpFbTypes,
                        width, height);
                break;
        }

        return videoConfig;
    }

    /**
     * Creates a TextConfig object depending on the passed parameters and returns it.
     *
     * @param codecType Integer value of the text codec type
     * @return a TextConfig with the passed parameters and default values.
     */
    private TextConfig createTextConfig(int codecType) {
        TextConfig textConfig = null;

        switch (codecType) {
            case TextConfig.TEXT_T140:
            case TextConfig.TEXT_T140_RED:
                textConfig = createTextConfig(getRemoteTextSocketAddress(),
                        getRemoteTextRtcpConfig(), codecType);
                break;
        }

        return textConfig;
    }

    /**
     * Displays the dialer BottomSheetDialog when the button is clicked
     *
     * @param view the view form the button click
     */
    public void openDialer(View view) {
        if (!mBottomSheetDialog.isOpen()) {
            mBottomSheetDialog.show();
        }
    }

    /**
     * Sends a DTMF input to the current AudioSession and updates the TextView to
     * display the input.
     *
     * @param view the view from the button click
     */
    public void sendDtmfOnClick(View view) {
        char digit = ((Button) view).getText().toString().charAt(0);
        mDtmfInput.append(digit);

        TextView mDtmfInputBox = mBottomSheetDialog.getDtmfInput();
        mDtmfInputBox.setText(mDtmfInput.toString());

        mAudioSession.sendDtmf(digit, DTMF_DURATION);
    }

    /**
     * Resets the TextView containing the DTMF input
     *
     * @param view the view from the button click
     */
    public void clearDtmfInputOnClick(View view) {
        mDtmfInput.setLength(0);
        TextView mDtmfInputBox = mBottomSheetDialog.getDtmfInput();
        mDtmfInputBox.setText(getString(R.string.dtmfInputPlaceholder));
    }

    /**
     * Set a speaker mode on/off
     *
     * @param view the view from the button click
     */
    public void setSpeakModeOnClick(View view) {
        SwitchCompat speakerMode = findViewById(R.id.speakerModeSwitch);
        if (speakerMode != null) {
            AudioManager audioManager = getSystemService(AudioManager.class);
            audioManager.setSpeakerphoneOn(speakerMode.isChecked());
        }
    }

    /**
     * Calls closeSession() on ImsMediaManager and resets the flag on
     * mIsOpenSessionSent
     *
     * @param view the view from the button click
     */
    public void closeSessionOnClick(View view) {
        Log.d(TAG, "closeSessionOnClick");
        if (mIsOpenSessionSent) {
            mImsMediaManager.closeSession(mAudioSession);
            mIsOpenSessionSent = false;
        }
        if (mIsVideoSessionOpened) {
            mImsMediaManager.closeSession(mVideoSession);
            mIsVideoSessionOpened = false;
            TextureView texturePreview = findViewById(R.id.texturePreview);
            TextureView textureDisplay = findViewById(R.id.textureDisplay);
            texturePreview.setVisibility(View.GONE);
            textureDisplay.setVisibility(View.GONE);
        }
        if (mIsTextSessionOpened) {
            mImsMediaManager.closeSession(mTextSession);
            mIsTextSessionOpened = false;
        }
    }

    /**
     * When the button is clicked a menu is opened containing the different media
     * directions and
     * the onMenuItemClickListener is set to handle the user's selection.
     *
     * @param view The view passed in from the button that is clicked
     */
    @SuppressLint("NonConstantResourceId")
    public void mediaDirectionOnClick(View view) {
        PopupMenu mediaDirectionMenu = new PopupMenu(this, findViewById(R.id.mediaDirectionButton));
        mediaDirectionMenu.getMenuInflater()
                .inflate(R.menu.media_direction_menu, mediaDirectionMenu.getMenu());
        int[] direction = { 0 };
        mediaDirectionMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.noFlowDirectionMenuItem:
                    direction[0] = RtpConfig.MEDIA_DIRECTION_NO_FLOW;
                    break;
                case R.id.sendReceiveDirectionMenuItem:
                    direction[0] = RtpConfig.MEDIA_DIRECTION_SEND_RECEIVE;
                    break;
                case R.id.receiveOnlyDirectionMenuItem:
                    direction[0] = RtpConfig.MEDIA_DIRECTION_RECEIVE_ONLY;
                    break;
                case R.id.sendOnlyDirectionMenuItem:
                    direction[0] = RtpConfig.MEDIA_DIRECTION_SEND_ONLY;
                    break;
                case R.id.inactiveDirectionMenuItem:
                    direction[0] = RtpConfig.MEDIA_DIRECTION_INACTIVE;
                    break;
                default:
                    return false;
            }
            mAudioConfig.setMediaDirection(direction[0]);
            mAudioSession.modifySession(mAudioConfig);
            if (mIsVideoSessionOpened) {
                mVideoConfig.setMediaDirection(direction[0]);
                mVideoSession.modifySession(mVideoConfig);
            }
            if (mIsTextSessionOpened) {
                mTextConfig.setMediaDirection(direction[0]);
                mTextSession.modifySession(mTextConfig);
            }
            return true;
        });
        mediaDirectionMenu.show();
    }

    /**
     * Displays the audio codec change BottomSheetDialog when the button is clicked
     *
     * @param view the view form the button click
     */
    public void openChangeAudioCodecSheet(View view) {
        if (!mBottomSheetAudioCodecSettings.isOpen()) {
            mBottomSheetAudioCodecSettings.show();
        }
    }

    /**
     * Calls openSession() on the ImsMediaManager
     *
     * @param view the view from the button click
     */
    public void openSessionOnClick(View view) {
        Log.d(TAG, "openSessionOnClick()");
        if (mIsMediaManagerReady && !mIsOpenSessionSent) {

            Toast.makeText(getApplicationContext(), getString(R.string.connecting_call_toast_text),
                    Toast.LENGTH_SHORT).show();

            mAudioConfig = determineAudioConfig(mLocalDeviceInfo, mRemoteDeviceInfo);
            Log.d(TAG, "AudioConfig: " + mAudioConfig.toString());

            RtpAudioSessionCallback sessionAudioCallback = new RtpAudioSessionCallback();
            mImsMediaManager.openSession(mAudioRtp, mAudioRtcp,
                    ImsMediaSession.SESSION_TYPE_AUDIO,
                    null, mExecutor, sessionAudioCallback);
            Log.d(TAG, "openSession(): audio=" + mRemoteDeviceInfo.getInetAddress() + ":"
                    + mRemoteDeviceInfo.getAudioRtpPort());

            if (mVideoEnabled) {
                RtpVideoSessionCallback sessionVideoCallback = new RtpVideoSessionCallback();
                mImsMediaManager.openSession(mVideoRtp, mVideoRtcp,
                        ImsMediaSession.SESSION_TYPE_VIDEO,
                        null, mExecutor, sessionVideoCallback);
                Log.d(TAG, "openSession(): video=" + mRemoteDeviceInfo.getInetAddress() + ":"
                        + mRemoteDeviceInfo.getVideoRtpPort());
            }

            if (mTextEnabled) {
                mTextConfig = createTextConfig(TextConfig.TEXT_T140_RED);
                Log.d(TAG, "TextConfig: " + mTextConfig.toString());

                RtpTextSessionCallback sessionTextCallback = new RtpTextSessionCallback();
                mImsMediaManager.openSession(mTextRtp, mTextRtcp,
                        ImsMediaSession.SESSION_TYPE_RTT,
                        mTextConfig, mExecutor, sessionTextCallback);
                Log.d(TAG, "openSession(): text=" + mRemoteDeviceInfo.getInetAddress() + ":"
                        + mRemoteDeviceInfo.getTextRtpPort());
            }
        }
    }

    /**
     * Saves the inputted ip address and port number to SharedPreferences.
     *
     * @param view the view from the button click
     */
    public void saveSettingsOnClick(View view) {
        int port = getRemoteDevicePortEditText();
        String ip = getRemoteDeviceIpEditText();
        editor.putInt("OTHER_HANDSHAKE_PORT", port);
        editor.putString("OTHER_IP_ADDRESS", ip);
        editor.apply();
        Toast.makeText(getApplicationContext(), R.string.save_button_action_toast,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Saves the inputted ip address and video port number to SharedPreferences.
     *
     * @param view the view from the button click
     */
    public void saveVideoSettingsOnClick(View view) {
        int port = getVideoRemoteDevicePortEditText();
        String ip = getVideoRemoteDeviceIpEditText();
        editor.putInt("OTHER_HANDSHAKE_VIDEO_PORT", port);
        editor.putString("OTHER_VIDEO_IP_ADDRESS", ip);
        editor.apply();

        Spinner videoCodecSpinner = findViewById(R.id.spinnerVideoCodecs);
        Spinner videoCodecProfileSpinner = findViewById(R.id.spinnerVideoCodecProfiles);
        Spinner videoCodecLevelSpinner = findViewById(R.id.spinnerVideoCodecLevels);
        Spinner videoModeSpinner = findViewById(R.id.spinnerVideoModes);
        Spinner videoCameraIdSpinner = findViewById(R.id.spinnerVideoCameraIds);
        Spinner videoCameraZoomSpinner = findViewById(R.id.spinnerVideoCameraZoom);
        Spinner videoFramerateSpinner = findViewById(R.id.spinnerVideoFramerates);
        Spinner videoBitrateSpinner = findViewById(R.id.spinnerVideoBitrates);
        Spinner videoDeviceOrientationSpinner = findViewById(R.id.spinnerVideoDeviceOrientations);
        Spinner videoCvoValueSpinner = findViewById(R.id.spinnerVideoCvoValues);
        Spinner videoResolutionSpinner = (Spinner) findViewById(R.id.spinnerVideoResolution);

        mSelectedVideoCodec =
                ((VideoCodecEnum) videoCodecSpinner.getSelectedItem()).getValue();
        mSelectedCodecProfile =
                ((VideoCodecProfileEnum) videoCodecProfileSpinner.getSelectedItem()).getValue();
        mSelectedCodecLevel =
                ((VideoCodecLevelEnum) videoCodecLevelSpinner.getSelectedItem()).getValue();
        mSelectedVideoMode =
                ((VideoModeEnum) videoModeSpinner.getSelectedItem()).getValue();
        mSelectedCameraId =
                ((VideoCameraIdEnum) videoCameraIdSpinner.getSelectedItem()).getValue();
        mSelectedCameraZoom =
                ((VideoCameraZoomEnum) videoCameraZoomSpinner.getSelectedItem()).getValue();
        mSelectedFramerate =
                ((VideoFramerateEnum) videoFramerateSpinner.getSelectedItem()).getValue();
        mSelectedBitrate =
                ((VideoBitrateEnum) videoBitrateSpinner.getSelectedItem()).getValue();
        mSelectedDeviceOrientationDegree =
                ((VideoDeviceOrientationEnum) videoDeviceOrientationSpinner
                .getSelectedItem())
                .getValue();
        mSelectedCvoValue = ((VideoCvoValueEnum) videoCvoValueSpinner.getSelectedItem())
                .getValue();
        mSelectedVideoResolution = (String) videoResolutionSpinner.getSelectedItem();
        Toast.makeText(getApplicationContext(), R.string.save_button_action_toast,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Calls modifySession to change the audio codec on the current AudioSession.
     * Also contains
     * the logic to create the new AudioConfig.
     *
     * @param view the view form the button click
     */
    public void changeAudioCodecOnClick(View view) {
        AudioConfig config = null;
        AmrParams amrParams;
        EvsParams evsParams;
        int audioCodec = mBottomSheetAudioCodecSettings.getAudioCodec();

        switch (audioCodec) {
            case CodecType.AMR:
            case CodecType.AMR_WB:

                evsParams = new EvsParams.Builder()
                .setEvsbandwidth(EvsParams.EVS_BAND_NONE)
                .setEvsMode(EvsParams.EVS_MODE_0)
                .setChannelAwareMode((byte) 3)
                .setHeaderFullOnly(true)
                .setCodecModeRequest((byte) 15)
                .build();

                amrParams = createAmrParams(mBottomSheetAudioCodecSettings.getAmrMode(), false, 0);
                config = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, amrParams, evsParams);
                Log.d(TAG, String.format("AudioConfig switched to Codec: %s\t Params: %s",
                        mBottomSheetAudioCodecSettings.getAudioCodec(),
                        config.getAmrParams().toString()));
                break;

            case CodecType.EVS:
                evsParams = createEvsParams(mBottomSheetAudioCodecSettings.getEvsBand(),
                    mBottomSheetAudioCodecSettings.getEvsMode());
                amrParams = createAmrParams(0, false, 0);
                config = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, amrParams, evsParams);
                Log.d(TAG, String.format("AudioConfig switched to Codec: %s\t Params: %s",
                        mBottomSheetAudioCodecSettings.getAudioCodec(),
                        config.getEvsParams().toString()));
                break;

            case CodecType.PCMA:
            case CodecType.PCMU:
                config = createAudioConfig(getRemoteAudioSocketAddress(),
                        getRemoteAudioRtcpConfig(), audioCodec, null, null);
                Log.d(TAG, String.format("AudioConfig switched to Codec: %s",
                        mBottomSheetAudioCodecSettings.getAudioCodec()));
                break;
        }

        mAudioSession.modifySession(config);
        mBottomSheetAudioCodecSettings.dismiss();
    }

    /**
     * Changes the flag of loopback mode, changes the ConnectionStatus sate, and
     * restyles the UI
     *
     * @param view the view from, the button click
     */
    public void loopbackOnClick(View view) {
        SwitchCompat mLoopbackSwitch = findViewById(R.id.loopbackModeSwitch);
        if (mLoopbackSwitch.isChecked()) {
            mLoopbackModeEnabled = true;
            openPorts(true);
            editor.putString("OTHER_IP_ADDRESS", getLocalIpAddress()).apply();
            mRemoteDeviceInfo = createMyDeviceInfo();
            mLocalDeviceInfo = createMyDeviceInfo();
            updateUI(ConnectionStatus.CONNECTED);
            updateAdditionalMedia();
        } else {
            closePorts();
            mLoopbackModeEnabled = false;
            updateUI(ConnectionStatus.OFFLINE);
            updateVideoUi(false);
            updateTextUi(false);
        }
    }

    /**
     * Send text message to the text session
     *
     * @param view the view from, the button click
     */
    public void sendRttOnClick(View view) {
        if (mIsTextSessionOpened) {
            EditText edit = findViewById(R.id.textEditTextSending);
            mTextSession.sendRtt(edit.getText().toString());
        }
    }

    /**
     * Opens or closes ports and starts the waiting handshake runnable depending on
     * the current
     * state of the button.
     *
     * @param view view from the button click
     */
    public void allowCallsOnClick(View view) {
        if (prefs.getBoolean(HANDSHAKE_PORT_PREF, false)) {
            closePorts();
            Log.d(TAG, "Closed handshake, rtp, and rtcp ports.");

            Toast.makeText(getApplicationContext(),
                    "Closing ports",
                    Toast.LENGTH_SHORT).show();
            updateUI(ConnectionStatus.OFFLINE);
        } else {
            openPorts(true);
            while (!prefs.getBoolean(HANDSHAKE_PORT_PREF, false)) {
            }
            Log.d(TAG, "Handshake, rtp, and rtcp ports have been bound.");

            Toast.makeText(getApplicationContext(), getString(R.string.allowing_calls_toast_text),
                    Toast.LENGTH_SHORT).show();

            mWaitForHandshakeThread = new Thread(handleIncomingHandshake);
            mWaitForHandshakeThread.start();
            updateUI(ConnectionStatus.DISCONNECTED);
        }
    }

    /**
     * Starts the handshake process runnable that attempts to connect to two device
     * together.
     *
     * @param view view from the button click
     */
    public void initiateHandshakeOnClick(View view) {
        mWaitForHandshakeThread.interrupt();
        Thread initiateHandshakeThread = new Thread(initiateHandshake);
        initiateHandshakeThread.start();
        updateUI(ConnectionStatus.CONNECTING);
    }

    /**
     * Handles the styling of the settings layout.
     */
    public void setupSettingsPage() {
        EditText ipAddress = findViewById(R.id.remoteDeviceIpEditText);
        EditText portNumber = findViewById(R.id.remotePortNumberEditText);
        ipAddress.setText(prefs.getString("OTHER_IP_ADDRESS", ""));
        portNumber.setText(String.valueOf(prefs.getInt("OTHER_HANDSHAKE_PORT", 0)));

        setupAudioCodecSelectionLists();
        setupCodecSelectionOnClickListeners();
    }

    private int getSpinnerIndex(Spinner spinner, Object value) {
        int index = 0;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).equals(value)) {
                index = i;
            }
        }
        return index;
    }

    /**
     * Handles the styling of the video settings layout.
     */
    public void setupVideoSettingsPage() {
        EditText ipAddress = findViewById(R.id.videoRemoteDeviceIpEditText);
        EditText portNumber = findViewById(R.id.remoteVideoPortNumberEditText);
        ipAddress.setText(prefs.getString("OTHER_IP_ADDRESS", ""));
        portNumber.setText(String.valueOf(prefs.getInt("OTHER_HANDSHAKE_PORT", 0)));

        setupVideoCodecSelectionLists();
    }

    /**
     * Gets the saved user selections for the audio codec settings and updates the UI's lists to
     * match.
     */
    private void setupAudioCodecSelectionLists() {
        updateCodecSelectionFromPrefs();

        ArrayAdapter<CodecTypeEnum> codecTypeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, CodecTypeEnum.values());
        ListView codecTypeList = findViewById(R.id.audioCodecList);
        codecTypeList.setAdapter(codecTypeAdapter);
        for (int i = 0; i < codecTypeAdapter.getCount(); i++) {
            CodecTypeEnum mode = (CodecTypeEnum) codecTypeList.getItemAtPosition(i);
            codecTypeList.setItemChecked(i, mSelectedCodecTypes.contains(mode.getValue()));
        }

        ArrayAdapter<EvsBandwidthEnum> evsBandAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, EvsBandwidthEnum.values());
        ListView evsBandwidthList = findViewById(R.id.evsBandwidthsList);
        evsBandwidthList.setAdapter(evsBandAdaptor);
        for (int i = 0; i < evsBandAdaptor.getCount(); i++) {
            EvsBandwidthEnum mode = (EvsBandwidthEnum) evsBandwidthList.getItemAtPosition(i);
            evsBandwidthList.setItemChecked(i, mSelectedEvsBandwidths.contains(mode.getValue()));
        }

        ArrayAdapter<AmrModeEnum> amrModeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, AmrModeEnum.values());
        ListView amrModesList = findViewById(R.id.amrModesList);
        amrModesList.setAdapter(amrModeAdapter);
        for (int i = 0; i < amrModeAdapter.getCount(); i++) {
            AmrModeEnum mode = (AmrModeEnum) amrModesList.getItemAtPosition(i);
            amrModesList.setItemChecked(i, mSelectedAmrModes.contains(mode.getValue()));
        }

        ArrayAdapter<EvsModeEnum> evsModeAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_multiple_choice, EvsModeEnum.values());
        ListView evsModeList = findViewById(R.id.evsModesList);
        evsModeList.setAdapter(evsModeAdaptor);
        for (int i = 0; i < evsModeAdaptor.getCount(); i++) {
            EvsModeEnum mode = (EvsModeEnum) evsModeList.getItemAtPosition(i);
            evsModeList.setItemChecked(i, mSelectedEvsModes.contains(mode.getValue()));
        }
    }

    private void setupVideoCodecSelectionLists() {
        Spinner videoCodecSpinner = findViewById(R.id.spinnerVideoCodecs);
        ArrayAdapter<VideoCodecEnum> videoCodecAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCodecEnum.values());
        videoCodecAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoCodecSpinner.setAdapter(videoCodecAdaptor);
        videoCodecSpinner.setSelection(getSpinnerIndex(videoCodecSpinner, mSelectedVideoCodec));

        Spinner videoCodecProfileSpinner = findViewById(R.id.spinnerVideoCodecProfiles);
        ArrayAdapter<VideoCodecProfileEnum> videoCodecProfileAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCodecProfileEnum.values());
        videoCodecProfileAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoCodecProfileSpinner.setAdapter(videoCodecProfileAdaptor);
        videoCodecProfileSpinner.setSelection(getSpinnerIndex(videoCodecProfileSpinner,
                mSelectedCodecProfile));

        Spinner videoCodecLevelSpinner = findViewById(R.id.spinnerVideoCodecLevels);
        ArrayAdapter<VideoCodecLevelEnum> videoCodecLevelAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCodecLevelEnum.values());
        videoCodecLevelAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoCodecLevelSpinner.setAdapter(videoCodecLevelAdaptor);
        videoCodecLevelSpinner.setSelection(getSpinnerIndex(videoCodecLevelSpinner,
                mSelectedCodecLevel));

        Spinner videoModeSpinner = findViewById(R.id.spinnerVideoModes);
        ArrayAdapter<VideoModeEnum> videoModeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoModeEnum.values());
        videoModeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoModeSpinner.setAdapter(videoModeAdapter);
        videoModeSpinner.setSelection(getSpinnerIndex(videoModeSpinner, mSelectedVideoMode));

        Spinner videoCameraIdSpinner = findViewById(R.id.spinnerVideoCameraIds);
        ArrayAdapter<VideoCameraIdEnum> videoCameraIdAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCameraIdEnum.values());
        videoCameraIdAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoCameraIdSpinner.setAdapter(videoCameraIdAdaptor);
        videoCameraIdSpinner.setSelection(getSpinnerIndex(videoCameraIdSpinner, mSelectedCameraId));

        Spinner videoCameraZoomSpinner = findViewById(R.id.spinnerVideoCameraZoom);
        ArrayAdapter<VideoCameraZoomEnum> videoCameraZoomAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCameraZoomEnum.values());
        videoCameraZoomAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoCameraZoomSpinner.setAdapter(videoCameraZoomAdaptor);
        videoCameraZoomSpinner.setSelection(getSpinnerIndex(videoCameraZoomSpinner,
                mSelectedCameraZoom));

        Spinner videoFramerateSpinner = findViewById(R.id.spinnerVideoFramerates);
        ArrayAdapter<VideoFramerateEnum> videoFramerateAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoFramerateEnum.values());
        videoFramerateAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoFramerateSpinner.setAdapter(videoFramerateAdaptor);
        videoFramerateSpinner.setSelection(getSpinnerIndex(videoFramerateSpinner,
                mSelectedFramerate));

        Spinner videoBitrateSpinner = findViewById(R.id.spinnerVideoBitrates);
        ArrayAdapter<VideoBitrateEnum> videoBitrateAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoBitrateEnum.values());
        videoBitrateAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoBitrateSpinner.setAdapter(videoBitrateAdaptor);
        videoBitrateSpinner.setSelection(getSpinnerIndex(videoBitrateSpinner, mSelectedBitrate));

        Spinner videoDeviceOrientationSpinner = findViewById(R.id.spinnerVideoDeviceOrientations);
        ArrayAdapter<VideoDeviceOrientationEnum> videoDeviceOrientationAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoDeviceOrientationEnum.values());
        videoDeviceOrientationAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoDeviceOrientationSpinner.setAdapter(videoDeviceOrientationAdaptor);
        videoDeviceOrientationSpinner.setSelection(getSpinnerIndex(videoDeviceOrientationSpinner,
                mSelectedDeviceOrientationDegree));

        Spinner videoCvoValueSpinner = findViewById(R.id.spinnerVideoCvoValues);
        ArrayAdapter<VideoCvoValueEnum> videoCvoValueAdaptor = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, VideoCvoValueEnum.values());
        videoCvoValueAdaptor.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoCvoValueSpinner.setAdapter(videoCvoValueAdaptor);
        videoCvoValueSpinner.setSelection(getSpinnerIndex(videoCvoValueSpinner,
                mSelectedCvoValue));

        Spinner videoResolutionSpinner = (Spinner) findViewById(R.id.spinnerVideoResolution);
        ArrayAdapter<String> videoResolutionAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, mVideoResolutionStrings);
        videoResolutionAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        videoResolutionSpinner.setAdapter(videoResolutionAdapter);
        videoResolutionSpinner.setSelection(getSpinnerIndex(videoResolutionSpinner,
                mSelectedVideoResolution));
    }

    /**
     * Updates all of the lists containing the user's codecs selections.
     */
    private void updateCodecSelectionFromPrefs() {
        mSelectedCodecTypes =
                prefsHandler.getIntegerSetFromPrefs(SharedPrefsHandler.CODECS_PREF);
        mSelectedEvsBandwidths =
                prefsHandler.getIntegerSetFromPrefs(SharedPrefsHandler.EVS_BANDS_PREF);
        mSelectedAmrModes =
                prefsHandler.getIntegerSetFromPrefs(SharedPrefsHandler.AMR_MODES_PREF);
        mSelectedEvsModes =
                prefsHandler.getIntegerSetFromPrefs(SharedPrefsHandler.EVS_MODES_PREF);
    }

    /**
     * Adds onClickListeners to the 4 check box lists on the settings page, to
     * handle the user input
     * of the codec, bandwidth, and mode selections.
     */
    public void setupCodecSelectionOnClickListeners() {
        ListView audioCodecList, evsBandList, amrModeList, evsModeList;

        audioCodecList = findViewById(R.id.audioCodecList);
        evsBandList = findViewById(R.id.evsBandwidthsList);
        amrModeList = findViewById(R.id.amrModesList);
        evsModeList = findViewById(R.id.evsModesList);

        audioCodecList.setOnItemClickListener((adapterView, view, position, id) -> {
            CodecTypeEnum item = (CodecTypeEnum) audioCodecList.getItemAtPosition(position);

            if (audioCodecList.isItemChecked(position)) {
                mSelectedCodecTypes.add(item.getValue());
                if (item == CodecTypeEnum.AMR || item == CodecTypeEnum.AMR_WB) {
                    amrModeList.setAlpha(ENABLED_ALPHA);
                    amrModeList.setEnabled(true);
                } else if (item == CodecTypeEnum.EVS) {
                    evsBandList.setAlpha(ENABLED_ALPHA);
                    amrModeList.setEnabled(true);
                    evsModeList.setAlpha(ENABLED_ALPHA);
                    evsModeList.setEnabled(true);
                }
            } else {
                mSelectedCodecTypes.remove(item.getValue());
                if (item == CodecTypeEnum.AMR || item == CodecTypeEnum.AMR_WB) {
                    amrModeList.setAlpha(0.3f);
                    amrModeList.setEnabled(false);
                } else if (item == CodecTypeEnum.EVS) {
                    evsBandList.setAlpha(0.3f);
                    evsBandList.setEnabled(false);
                    evsModeList.setAlpha(0.3f);
                    evsModeList.setEnabled(false);
                }
            }

            prefsHandler.saveIntegerSetToPrefs(SharedPrefsHandler.CODECS_PREF,
                    mSelectedCodecTypes);

        });
        evsBandList.setOnItemClickListener((adapterView, view, position, id) -> {
            EvsBandwidthEnum item = (EvsBandwidthEnum) evsBandList.getItemAtPosition(position);

            if (evsBandList.isItemChecked(position)) {
                mSelectedEvsBandwidths.add(item.getValue());
            } else {
                mSelectedEvsBandwidths.remove(item.getValue());
            }

            prefsHandler.saveIntegerSetToPrefs(SharedPrefsHandler.EVS_BANDS_PREF,
                    mSelectedEvsBandwidths);
        });
        evsModeList.setOnItemClickListener((adapterView, view, position, id) -> {
            EvsModeEnum item = (EvsModeEnum) evsModeList.getItemAtPosition(position);

            if (evsModeList.isItemChecked(position)) {
                mSelectedEvsModes.add(item.getValue());
            } else {
                mSelectedEvsModes.remove(item.getValue());
            }

            prefsHandler.saveIntegerSetToPrefs(SharedPrefsHandler.EVS_MODES_PREF,
                    mSelectedEvsModes);
        });
        amrModeList.setOnItemClickListener((adapterView, view, position, id) -> {
            AmrModeEnum item = (AmrModeEnum) amrModeList.getItemAtPosition(position);

            if (amrModeList.isItemChecked(position)) {
                mSelectedAmrModes.add(item.getValue());
            } else {
                mSelectedAmrModes.remove(item.getValue());
            }

            prefsHandler.saveIntegerSetToPrefs(SharedPrefsHandler.AMR_MODES_PREF,
                    mSelectedAmrModes);
        });
    }

    /**
     * Styles the main activity UI based on the current ConnectionStatus enum state.
     */
    private void styleMainActivity() {
        runOnUiThread(() -> {
            updateUiViews();
            styleDevicesInfo();
            switch (mConnectionStatus) {
                case OFFLINE:
                    styleOffline();
                    break;

                case DISCONNECTED:
                    styleDisconnected();
                    break;

                case CONNECTING:
                    break;

                case CONNECTED:
                    styleConnected();
                    break;

                case ACTIVE_CALL:
                    styleActiveCall();
                    break;
            }
        });
    }

    private void styleDevicesInfo() {
        TextView localIpLabel = findViewById(R.id.localIpLabel);
        localIpLabel.setText(getString(R.string.local_ip_label, getLocalIpAddress()));

        mRemoteIpLabel = findViewById(R.id.remoteIpLabel);
        mRemoteIpLabel.setText(getString(R.string.other_device_ip_label,
                prefs.getString("OTHER_IP_ADDRESS", "null")));

        mRemoteHandshakePortLabel = findViewById(R.id.remoteHandshakePortLabel);
        mRemoteHandshakePortLabel.setText(getString(R.string.handshake_port_label,
                String.valueOf(getOtherDevicePort())));
    }

    private void styleOffline() {
        mLocalHandshakePortLabel.setText(getString(R.string.port_closed_label));
        mLocalRtpPortLabel.setText(getString(R.string.port_closed_label));
        mLocalRtcpPortLabel.setText(getString(R.string.port_closed_label));
        mRemoteRtpPortLabel.setText(getString(R.string.port_closed_label));
        mRemoteRtcpPortLabel.setText(getString(R.string.port_closed_label));

        mAllowCallsButton.setText(R.string.allow_calls_button_text);
        mAllowCallsButton.setBackgroundColor(getColor(R.color.mint_green));
        styleButtonEnabled(mAllowCallsButton);

        mConnectButton.setText(R.string.connect_button_text);
        mConnectButton.setBackgroundColor(getColor(R.color.mint_green));
        styleButtonDisabled(mConnectButton);

        styleLoopbackSwitchEnabled();
        styleButtonDisabled(mOpenSessionButton);
        styleLayoutChildrenDisabled(mActiveCallToolBar);
    }

    private void styleDisconnected() {
        mLocalHandshakePortLabel.setText(getString(R.string.handshake_port_label,
                String.valueOf(mHandshakeReceptionSocket.getBoundSocket())));
        mLocalRtpPortLabel.setText(getString(R.string.rtp_reception_port_label,
                String.valueOf(mAudioRtp.getLocalPort())));
        mLocalRtcpPortLabel.setText(getString(R.string.rtcp_reception_port_label,
                String.valueOf(mAudioRtcp.getLocalPort())));
        mRemoteRtpPortLabel.setText(getString(R.string.port_closed_label));
        mRemoteRtcpPortLabel.setText(getString(R.string.port_closed_label));

        mAllowCallsButton.setText(R.string.disable_calls_button_text);
        mAllowCallsButton.setBackgroundColor(getColor(R.color.coral_red));
        styleButtonEnabled(mAllowCallsButton);

        mConnectButton.setText(R.string.connect_button_text);
        mConnectButton.setBackgroundColor(getColor(R.color.mint_green));
        styleButtonEnabled(mConnectButton);

        styleLoopbackSwitchDisabled();
        styleButtonDisabled(mOpenSessionButton);
        styleLayoutChildrenDisabled(mActiveCallToolBar);
    }

    private void styleConnected() {
        if (mLoopbackModeEnabled) {
            mAllowCallsButton.setText(R.string.allow_calls_button_text);
            mAllowCallsButton.setBackgroundColor(getColor(R.color.mint_green));
            styleButtonDisabled(mAllowCallsButton);

            mConnectButton.setText(R.string.connect_button_text);
            mConnectButton.setBackgroundColor(getColor(R.color.mint_green));
            styleButtonDisabled(mConnectButton);
            styleLoopbackSwitchEnabled();

        } else {
            mAllowCallsButton.setText(R.string.disable_calls_button_text);
            mAllowCallsButton.setBackgroundColor(getColor(R.color.coral_red));
            styleButtonEnabled(mAllowCallsButton);

            mConnectButton.setText(R.string.disconnect_button_text);
            mConnectButton.setBackgroundColor(getColor(R.color.coral_red));
            styleButtonEnabled(mConnectButton);
            styleLoopbackSwitchDisabled();
        }

        mLocalHandshakePortLabel.setText(getString(R.string.reception_port_label,
                String.valueOf(mHandshakeReceptionSocket.getBoundSocket())));
        mLocalRtpPortLabel.setText(getString(R.string.rtp_reception_port_label,
                String.valueOf(mAudioRtp.getLocalPort())));
        mLocalRtcpPortLabel.setText(getString(R.string.rtcp_reception_port_label,
                String.valueOf(mAudioRtcp.getLocalPort())));
        mRemoteRtpPortLabel.setText(getString(R.string.rtp_reception_port_label,
                String.valueOf(mRemoteDeviceInfo.getAudioRtpPort())));
        mRemoteRtcpPortLabel.setText(getString(R.string.rtcp_reception_port_label,
                String.valueOf(mRemoteDeviceInfo.getAudioRtpPort() + 1)));
        styleButtonEnabled(mOpenSessionButton);
        styleLayoutChildrenDisabled(mActiveCallToolBar);
    }

    private void styleActiveCall() {
        if (mLoopbackModeEnabled) {
            styleLoopbackSwitchEnabled();

            mAllowCallsButton.setText(R.string.allow_calls_button_text);
            mAllowCallsButton.setBackgroundColor(getColor(R.color.mint_green));
            styleButtonDisabled(mAllowCallsButton);

            mConnectButton.setText(R.string.connect_button_text);
            mConnectButton.setBackgroundColor(getColor(R.color.mint_green));
            styleButtonDisabled(mConnectButton);
            styleLoopbackSwitchEnabled();

        } else {
            styleLoopbackSwitchDisabled();

            mAllowCallsButton.setText(R.string.disable_calls_button_text);
            mAllowCallsButton.setBackgroundColor(getColor(R.color.coral_red));
            styleButtonDisabled(mAllowCallsButton);

            mConnectButton.setText(R.string.disconnect_button_text);
            mConnectButton.setBackgroundColor(getColor(R.color.coral_red));
            styleButtonDisabled(mConnectButton);
            styleLoopbackSwitchDisabled();
        }

        mLocalHandshakePortLabel
                .setText(getString(R.string.reception_port_label, getString(R.string.connected)));
        mLocalRtpPortLabel.setText(getString(R.string.rtp_reception_port_label,
                String.valueOf(mAudioRtp.getLocalPort())));
        mLocalRtcpPortLabel.setText(getString(R.string.rtcp_reception_port_label,
                String.valueOf(mAudioRtcp.getLocalPort())));
        mRemoteRtpPortLabel.setText(getString(R.string.rtp_reception_port_label,
                String.valueOf(mRemoteDeviceInfo.getAudioRtpPort())));
        mRemoteRtcpPortLabel.setText(getString(R.string.rtcp_reception_port_label,
                String.valueOf(mRemoteDeviceInfo.getAudioRtpPort() + 1)));

        styleButtonDisabled(mOpenSessionButton);
        styleLayoutChildrenEnabled(mActiveCallToolBar);
    }

    private void styleButtonDisabled(Button button) {
        button.setEnabled(false);
        button.setClickable(false);
        button.setAlpha(DISABLED_ALPHA);
    }

    private void styleButtonEnabled(Button button) {
        button.setEnabled(true);
        button.setClickable(true);
        button.setAlpha(ENABLED_ALPHA);
    }

    private void styleLayoutChildrenDisabled(LinearLayout linearLayout) {
        linearLayout.setAlpha(DISABLED_ALPHA);
        for (int x = 0; x < linearLayout.getChildCount(); x++) {
            linearLayout.getChildAt(x).setAlpha(DISABLED_ALPHA);
            linearLayout.getChildAt(x).setEnabled(false);
            linearLayout.getChildAt(x).setClickable(false);
        }
    }

    private void styleLayoutChildrenEnabled(LinearLayout linearLayout) {
        linearLayout.setAlpha(ENABLED_ALPHA);
        for (int x = 0; x < linearLayout.getChildCount(); x++) {
            linearLayout.getChildAt(x).setAlpha(ENABLED_ALPHA);
            linearLayout.getChildAt(x).setEnabled(true);
            linearLayout.getChildAt(x).setClickable(true);
        }
    }

    private void updateUiViews() {
        mLocalHandshakePortLabel = findViewById(R.id.localHandshakePortLabel);
        mLocalRtpPortLabel = findViewById(R.id.localRtpPortLabel);
        mLocalRtcpPortLabel = findViewById(R.id.localRtcpPortLabel);
        mRemoteHandshakePortLabel = findViewById(R.id.remoteHandshakePortLabel);
        mRemoteRtpPortLabel = findViewById(R.id.remoteRtpPortLabel);
        mRemoteRtcpPortLabel = findViewById(R.id.remoteRtcpPortLabel);
        mAllowCallsButton = findViewById(R.id.allowCallsButton);
        mConnectButton = findViewById(R.id.connectButton);
        mOpenSessionButton = findViewById(R.id.openSessionButton);
        mActiveCallToolBar = findViewById(R.id.activeCallActionsLayout);
        mLoopbackSwitch = findViewById(R.id.loopbackModeSwitch);
    }

    private void styleLoopbackSwitchEnabled() {
        mLoopbackSwitch.setChecked(mLoopbackModeEnabled);
        mLoopbackSwitch.setEnabled(true);
        mLoopbackSwitch.setClickable(true);
        mLoopbackSwitch.setAlpha(ENABLED_ALPHA);
    }

    private void styleLoopbackSwitchDisabled() {
        mLoopbackSwitch.setChecked(mLoopbackModeEnabled);
        mLoopbackSwitch.setEnabled(false);
        mLoopbackSwitch.setClickable(false);
        mLoopbackSwitch.setAlpha(DISABLED_ALPHA);
    }

    private void updateVideoUi(boolean enable) {
        createTextureView();
        TextView previewLabel = findViewById(R.id.previewLabel);
        TextView displayLabel = findViewById(R.id.displayLabel);
        FrameLayout previewFrame = findViewById(R.id.previewFrame);
        FrameLayout displayFrames = findViewById(R.id.displayFrames);

        if (enable) {
            previewLabel.setVisibility(View.VISIBLE);
            displayLabel.setVisibility(View.VISIBLE);
            previewFrame.setVisibility(View.VISIBLE);
            mTexturePreview.setVisibility(View.VISIBLE);
            displayFrames.setVisibility(View.VISIBLE);
            mTextureDisplay.setVisibility(View.VISIBLE);
        } else {
            previewLabel.setVisibility(View.GONE);
            displayLabel.setVisibility(View.GONE);
            previewFrame.setVisibility(View.GONE);
            mTexturePreview.setVisibility(View.GONE);
            displayFrames.setVisibility(View.GONE);
            mTextureDisplay.setVisibility(View.GONE);
        }

        ViewGroup viewGroup = findViewById(R.id.rootLayout);
        viewGroup.invalidate();
    }

    private void updateTextUi(boolean enable) {
        TextView sendingTextTitle = findViewById(R.id.sendingTextTitle);
        EditText textEditTextSending = findViewById(R.id.textEditTextSending);
        TextView receivedTextTitle = findViewById(R.id.receivedTextTitle);
        TextView receivedText = findViewById(R.id.receivedText);
        TextView buttonTextSend = findViewById(R.id.buttonTextSend);

        if (enable) {
            sendingTextTitle.setVisibility(View.VISIBLE);
            textEditTextSending.setVisibility(View.VISIBLE);
            receivedTextTitle.setVisibility(View.VISIBLE);
            receivedText.setVisibility(View.VISIBLE);
            buttonTextSend.setVisibility(View.VISIBLE);
        } else {
            sendingTextTitle.setVisibility(View.GONE);
            textEditTextSending.setVisibility(View.GONE);
            receivedTextTitle.setVisibility(View.GONE);
            receivedText.setVisibility(View.GONE);
            buttonTextSend.setVisibility(View.GONE);
        }
        ViewGroup viewGroup = findViewById(R.id.rootLayout);
        viewGroup.invalidate();
    }

    private IImsMediaCallback.Stub mMediaUtilCallback = new IImsMediaCallback.Stub() {
        @Override
        public void onVideoSpropResponse(String[] spropList) {
            Log.d(TAG, "[GetSprop]onVideoSprop");
            for (String sprop : spropList) {
                Log.d(TAG, "[GetSprop]SPROP : " + sprop);
            }
        }
    };

    public void generateSprop(View btn) {
        if (!mIsMediaManagerReady) {
            Log.d(TAG, "[GetSprop]Media Manager not ready");
            return;
        }

        VideoConfig[] videoConfigList = new VideoConfig[1];
        VideoConfig.Builder vcbuilder = new VideoConfig.Builder();
        vcbuilder.setBitrate(384)
                .setCodecType(VideoConfig.VIDEO_CODEC_AVC)
                .setCodecProfile(VideoConfig.AVC_PROFILE_CONSTRAINED_BASELINE)
                .setCodecLevel(VideoConfig.AVC_LEVEL_12)
                .setFramerate(10)
                .setIntraFrameIntervalSec(1)
                .setPacketizationMode(VideoConfig.MODE_NON_INTERLEAVED)
                .setResolutionWidth(RESOLUTION_WIDTH)
                .setResolutionHeight(RESOLUTION_HEIGHT)
                .setVideoMode(VideoConfig.VIDEO_MODE_RECORDING)
                .setMaxMtuBytes(1500);

        videoConfigList[0] = vcbuilder.build();

        try {
            mImsMediaManager.generateVideoSprop(videoConfigList, mMediaUtilCallback.asBinder());
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }

    public void sendHeaderExtension(View btn) {
        if (mAudioSession != null) {
            List<RtpHeaderExtension> extensions = new ArrayList<>();
            byte[] testBytes1 = new byte[1];
            byte[] testBytes2 = new byte[1];
            testBytes1[0] = 5;
            testBytes2[0] = 10;
            RtpHeaderExtension extension1 = new RtpHeaderExtension(1, testBytes1);
            RtpHeaderExtension extension2 = new RtpHeaderExtension(2, testBytes2);
            extensions.add(extension1);
            extensions.add(extension2);
            Log.d(TAG, "[sendHeaderExtension] extension size=" + extensions.size());
            mAudioSession.sendHeaderExtension(extensions);
        }
    }

    /**
     * Send the parameter to adjust the audio delay
     */
    public void adjustDelay(View btn) {
        if (mAudioSession != null) {
            mDelay = (mDelay + 20) % 240;
            if (mDelay < 60) {
                mDelay = 60;
            }
            mAudioSession.adjustDelay(mDelay);
        }
    }
}
