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

package com.android.tv.samples.sampletvinteractiveappservice;

import android.annotation.TargetApi;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaPlayer;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.SectionRequest;
import android.media.tv.SectionResponse;
import android.media.tv.StreamEventRequest;
import android.media.tv.StreamEventResponse;
import android.media.tv.TableRequest;
import android.media.tv.TableResponse;
import android.media.tv.TvTrackInfo;
import android.media.tv.interactive.AppLinkInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppService;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TiasSessionImpl extends TvInteractiveAppService.Session {
    private static final String TAG = "SampleTvInteractiveAppService";
    private static final boolean DEBUG = true;

    private static final String VIRTUAL_DISPLAY_NAME = "sample_tias_display";

    // For testing purposes, limit the number of response for a single request
    private static final int MAX_HANDLED_RESPONSE = 3;

    private final Context mContext;
    private TvInteractiveAppManager mTvIAppManager;
    private final Handler mHandler;
    private final String mAppServiceId;
    private final int mType;
    private final ViewGroup mViewContainer;
    private Surface mSurface;
    private VirtualDisplay mVirtualDisplay;
    private List<TvTrackInfo> mTracks;

    private TextView mTvInputIdView;
    private TextView mChannelUriView;
    private TextView mVideoTrackView;
    private TextView mAudioTrackView;
    private TextView mSubtitleTrackView;
    private TextView mLogView;

    private VideoView mVideoView;
    private SurfaceView mAdSurfaceView;
    private Surface mAdSurface;
    private ParcelFileDescriptor mAdFd;
    private FrameLayout mMediaContainer;
    private int mAdState;
    private int mWidth;
    private int mHeight;
    private int mScreenWidth;
    private int mScreenHeight;
    private String mCurrentTvInputId;
    private Uri mCurrentChannelUri;
    private String mSelectingAudioTrackId;
    private String mFirstAudioTrackId;
    private int mGeneratedRequestId = 0;
    private boolean mRequestStreamEventFinished = false;
    private int mSectionReceived = 0;
    private List<String> mStreamDataList = new ArrayList<>();
    private boolean mIsFullScreen = true;

    public TiasSessionImpl(Context context, String iAppServiceId, int type) {
        super(context);
        if (DEBUG) {
            Log.d(TAG, "Constructing service with iAppServiceId=" + iAppServiceId
                    + " type=" + type);
        }
        mContext = context;
        mAppServiceId = iAppServiceId;
        mType = type;
        mHandler = new Handler(context.getMainLooper());
        mTvIAppManager = (TvInteractiveAppManager) mContext.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);

        mViewContainer = new LinearLayout(context);
        mViewContainer.setBackground(new ColorDrawable(0));
    }

    @Override
    public View onCreateMediaView() {
        mAdSurfaceView = new SurfaceView(mContext);
        if (DEBUG) {
            Log.d(TAG, "create surfaceView");
        }
        mAdSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mAdSurfaceView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                mAdSurface = holder.getSurface();
                            }

                            @Override
                            public void surfaceChanged(
                                    SurfaceHolder holder, int format, int width, int height) {
                                mAdSurface = holder.getSurface();
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {}
                        });
        mAdSurfaceView.setVisibility(View.INVISIBLE);
        ViewGroup.LayoutParams layoutParams =
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mAdSurfaceView.setLayoutParams(layoutParams);
        mMediaContainer.addView(mVideoView);
        mMediaContainer.addView(mAdSurfaceView);
        return mMediaContainer;
    }

    @Override
    public void onAdResponse(AdResponse adResponse) {
        mAdState = adResponse.getResponseType();
        switch (mAdState) {
            case AdResponse.RESPONSE_TYPE_PLAYING:
                long time = adResponse.getElapsedTimeMillis();
                updateLogText("AD is playing. " + time);
                break;
            case AdResponse.RESPONSE_TYPE_STOPPED:
                updateLogText("AD is stopped.");
                mAdSurfaceView.setVisibility(View.INVISIBLE);
                break;
            case AdResponse.RESPONSE_TYPE_FINISHED:
                updateLogText("AD is play finished.");
                mAdSurfaceView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public void onRelease() {
        if (DEBUG) {
            Log.d(TAG, "onRelease");
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        if (DEBUG) {
            Log.d(TAG, "onSetSurface");
        }
        if (mSurface != null) {
            mSurface.release();
        }
        updateSurface(surface, mWidth, mHeight);
        mSurface = surface;
        return true;
    }

    @Override
    public void onSurfaceChanged(int format, int width, int height) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceChanged format=" + format + " width=" + width +
                    " height=" + height);
        }
        if (mSurface != null) {
            updateSurface(mSurface, width, height);
            mWidth = width;
            mHeight = height;
        }
    }

    @Override
    public void onStartInteractiveApp() {
        if (DEBUG) {
            Log.d(TAG, "onStartInteractiveApp");
        }
        mHandler.post(
                () -> {
                    initSampleView();
                    setMediaViewEnabled(true);
                    requestCurrentTvInputId();
                    requestCurrentChannelUri();
                    requestTrackInfoList();
                }
        );
    }

    @Override
    public void onStopInteractiveApp() {
        if (DEBUG) {
            Log.d(TAG, "onStopInteractiveApp");
        }
    }

    public void prepare(TvInteractiveAppService serviceCaller) {
        // Slightly delay our post to ensure the Manager has had time to register our Session
        mHandler.postDelayed(
                () -> {
                    if (serviceCaller != null) {
                        serviceCaller.notifyStateChanged(mType,
                                TvInteractiveAppManager.SERVICE_STATE_READY,
                                TvInteractiveAppManager.ERROR_NONE);
                    }
                },
                100);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        // TODO: use a menu view instead of key events for the following tests
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_RED:
                tuneToNextChannel();
                return true;
            case KeyEvent.KEYCODE_A:
                updateLogText("stop video broadcast begin");
                tuneChannelByType(
                        TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP,
                        mCurrentTvInputId,
                        null);
                updateLogText("stop video broadcast end");
                return true;
            case KeyEvent.KEYCODE_B:
                updateLogText("resume video broadcast begin");
                tuneChannelByType(
                        TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE,
                        mCurrentTvInputId,
                        mCurrentChannelUri);
                updateLogText("resume video broadcast end");
                return true;
            case KeyEvent.KEYCODE_C:
                updateLogText("unselect audio track");
                mSelectingAudioTrackId = null;
                selectTrack(TvTrackInfo.TYPE_AUDIO, null);
                return true;
            case KeyEvent.KEYCODE_D:
                updateLogText("select audio track " + mFirstAudioTrackId);
                mSelectingAudioTrackId = mFirstAudioTrackId;
                selectTrack(TvTrackInfo.TYPE_AUDIO, mFirstAudioTrackId);
                return true;
            case KeyEvent.KEYCODE_E:
                if (mVideoView != null) {
                    if (mVideoView.isPlaying()) {
                        updateLogText("stop media");
                        mVideoView.stopPlayback();
                        mVideoView.setVisibility(View.GONE);
                        tuneChannelByType(
                                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE,
                                mCurrentTvInputId,
                                mCurrentChannelUri);
                    } else {
                        updateLogText("play media");
                        tuneChannelByType(
                                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP,
                                mCurrentTvInputId,
                                null);
                        mVideoView.setVisibility(View.VISIBLE);
                        // TODO: put a file sample.mp4 in res/raw/ and use R.raw.sample for the URI
                        Uri uri = Uri.parse(
                                "android.resource://" + mContext.getPackageName() + "/");
                        mVideoView.setVideoURI(uri);
                        mVideoView.start();
                        updateLogText("media is playing");
                    }
                }
                return true;
            case KeyEvent.KEYCODE_F:
                updateLogText("request StreamEvent");
                mRequestStreamEventFinished = false;
                mStreamDataList.clear();
                // TODO: build target URI instead of using channel URI
                requestStreamEvent(
                        mCurrentChannelUri == null ? null : mCurrentChannelUri.toString(),
                        "event1");
                return true;
            case KeyEvent.KEYCODE_G:
                updateLogText("change video bounds");
                if (mIsFullScreen) {
                    setVideoBounds(new Rect(100, 150, 960, 540));
                    updateLogText("Change video broadcast size(100, 150, 960, 540)");
                    mIsFullScreen = false;
                } else {
                    setVideoBounds(new Rect(0, 0, mScreenWidth, mScreenHeight));
                    updateLogText("Change video broadcast full screen");
                    mIsFullScreen = true;
                }
                return true;
            case KeyEvent.KEYCODE_H:
                updateLogText("request section");
                mSectionReceived = 0;
                requestSection(false, 0, 0x0, -1);
                return true;
            case KeyEvent.KEYCODE_I:
                if (mTvIAppManager == null) {
                    updateLogText("TvIAppManager null");
                    return false;
                }
                List<AppLinkInfo> appLinks = getAppLinkInfoList();
                if (appLinks.isEmpty()) {
                    updateLogText("Not found AppLink");
                } else {
                    AppLinkInfo appLink = appLinks.get(0);
                    Intent intent = new Intent();
                    intent.setComponent(appLink.getComponentName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.getApplicationContext().startActivity(intent);
                    updateLogText("Launch " + appLink.getComponentName());
                }
                return true;
            case KeyEvent.KEYCODE_J:
                updateLogText("Request SI Tables ");
                // Network Information Table (NIT)
                requestTable(false, 0x40, /* TableRequest.TABLE_NAME_NIT */ 3, -1);
                // Service Description Table (SDT)
                requestTable(false, 0x42, /* TableRequest.TABLE_NAME_SDT */ 5, -1);
                // Event Information Table (EIT)
                requestTable(false, 0x4e, /* TableRequest.TABLE_NAME_EIT */ 6, -1);
                return true;
            case KeyEvent.KEYCODE_K:
                updateLogText("Request Video Bounds");
                requestCurrentVideoBoundsWrapper();
                return true;
            case KeyEvent.KEYCODE_L: {
                updateLogText("stop video broadcast with blank mode");
                Bundle params = new Bundle();
                params.putInt(
                        /* TvInteractiveAppService.COMMAND_PARAMETER_KEY_STOP_MODE */
                        "command_stop_mode",
                        /* TvInteractiveAppService.COMMAND_PARAMETER_VALUE_STOP_MODE_BLANK */
                        1);
                tuneChannelByType(TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP,
                        mCurrentTvInputId, null, params);
                return true;
            }
            case KeyEvent.KEYCODE_M: {
                updateLogText("stop video broadcast with freeze mode");
                Bundle params = new Bundle();
                params.putInt(
                        /* TvInteractiveAppService.COMMAND_PARAMETER_KEY_STOP_MODE */
                        "command_stop_mode",
                        /* TvInteractiveAppService.COMMAND_PARAMETER_VALUE_STOP_MODE_FREEZE */
                        2);
                tuneChannelByType(TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP,
                        mCurrentTvInputId, null, params);
                return true;
            }
            case KeyEvent.KEYCODE_N: {
                updateLogText("request AD");
                requestAd();
                return true;
            }
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_C:
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_G:
            case KeyEvent.KEYCODE_H:
            case KeyEvent.KEYCODE_I:
            case KeyEvent.KEYCODE_J:
            case KeyEvent.KEYCODE_K:
            case KeyEvent.KEYCODE_L:
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_N:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    public void updateLogText(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
        mLogView.setText(log);
    }

    private void updateSurface(Surface surface, int width, int height) {
        mHandler.post(
                () -> {
                    // Update our virtualDisplay if it already exists, create a new one otherwise
                    if (mVirtualDisplay != null) {
                        mVirtualDisplay.setSurface(surface);
                        mVirtualDisplay.resize(width, height, DisplayMetrics.DENSITY_DEFAULT);
                    } else {
                        DisplayManager displayManager =
                                mContext.getSystemService(DisplayManager.class);
                        if (displayManager == null) {
                            Log.e(TAG, "Failed to get DisplayManager");
                            return;
                        }
                        mVirtualDisplay = displayManager.createVirtualDisplay(VIRTUAL_DISPLAY_NAME,
                                        width,
                                        height,
                                        DisplayMetrics.DENSITY_DEFAULT,
                                        surface,
                                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

                        Presentation presentation =
                                new Presentation(mContext, mVirtualDisplay.getDisplay());
                        presentation.setContentView(mViewContainer);
                        presentation.getWindow().setBackgroundDrawable(new ColorDrawable(0));
                        presentation.show();
                    }
                });
    }

    private void initSampleView() {
        View sampleView = LayoutInflater.from(mContext).inflate(R.layout.sample_layout, null);
        TextView appServiceIdText = sampleView.findViewById(R.id.app_service_id);
        appServiceIdText.setText("App Service ID: " + mAppServiceId);

        mTvInputIdView = sampleView.findViewById(R.id.tv_input_id);
        mChannelUriView = sampleView.findViewById(R.id.channel_uri);
        mVideoTrackView = sampleView.findViewById(R.id.video_track_selected);
        mAudioTrackView = sampleView.findViewById(R.id.audio_track_selected);
        mSubtitleTrackView = sampleView.findViewById(R.id.subtitle_track_selected);
        mLogView = sampleView.findViewById(R.id.log_text);
        // Set default values for the selected tracks, since we cannot request data on them directly
        mVideoTrackView.setText("No video track selected");
        mAudioTrackView.setText("No audio track selected");
        mSubtitleTrackView.setText("No subtitle track selected");

        mVideoView = new VideoView(mContext);
        mVideoView.setVisibility(View.GONE);
        mVideoView.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        mVideoView.setVisibility(View.GONE);
                        mLogView.setText("MediaPlayer onCompletion");
                        tuneChannelByType(
                                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE,
                                mCurrentTvInputId,
                                mCurrentChannelUri);
                    }
                });
        mWidth = 0;
        mHeight = 0;
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mScreenWidth = wm.getDefaultDisplay().getWidth();
        mScreenHeight = wm.getDefaultDisplay().getHeight();

        mViewContainer.addView(sampleView);
    }

    private void updateTrackSelectedView(int type, String trackId) {
        mHandler.post(
                () -> {
                    if (mTracks == null) {
                        return;
                    }
                    TvTrackInfo newSelectedTrack = null;
                    for (TvTrackInfo track : mTracks) {
                        if (track.getType() == type && track.getId().equals(trackId)) {
                            newSelectedTrack = track;
                            break;
                        }
                    }

                    if (newSelectedTrack == null) {
                        if (DEBUG) {
                            Log.d(TAG, "Did not find selected track within track list");
                        }
                        return;
                    }
                    switch (newSelectedTrack.getType()) {
                        case TvTrackInfo.TYPE_VIDEO:
                            mVideoTrackView.setText(
                                    "Video Track: id= " + newSelectedTrack.getId()
                                    + ", height=" + newSelectedTrack.getVideoHeight()
                                    + ", width=" + newSelectedTrack.getVideoWidth()
                                    + ", frame_rate=" + newSelectedTrack.getVideoFrameRate()
                                    + ", pixel_ratio=" + newSelectedTrack.getVideoPixelAspectRatio()
                            );
                            break;
                        case TvTrackInfo.TYPE_AUDIO:
                            mAudioTrackView.setText(
                                    "Audio Track: id=" + newSelectedTrack.getId()
                                    + ", lang=" + newSelectedTrack.getLanguage()
                                    + ", sample_rate=" + newSelectedTrack.getAudioSampleRate()
                                    + ", channel_count=" + newSelectedTrack.getAudioChannelCount()
                            );
                            break;
                        case TvTrackInfo.TYPE_SUBTITLE:
                            mSubtitleTrackView.setText(
                                    "Subtitle Track: id=" + newSelectedTrack.getId()
                                    + ", lang=" + newSelectedTrack.getLanguage()
                            );
                            break;
                    }
                }
        );
    }

    private void tuneChannelByType(String type, String inputId, Uri channelUri, Bundle bundle) {
        Bundle parameters = bundle == null ? new Bundle() : bundle;
        if (TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE.equals(type)) {
            parameters.putString(
                    TvInteractiveAppService.COMMAND_PARAMETER_KEY_CHANNEL_URI,
                    channelUri == null ? null : channelUri.toString());
            parameters.putString(TvInteractiveAppService.COMMAND_PARAMETER_KEY_INPUT_ID, inputId);
        }
        mHandler.post(() -> sendPlaybackCommandRequest(type, parameters));
        // Delay request for new information to give time to tune
        mHandler.postDelayed(
                () -> {
                    requestCurrentTvInputId();
                    requestCurrentChannelUri();
                    requestTrackInfoList();
                },
                1000
        );
    }

    private void tuneChannelByType(String type, String inputId, Uri channelUri) {
        tuneChannelByType(type, inputId, channelUri, new Bundle());
    }

    private void tuneToNextChannel() {
        tuneChannelByType(TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE_NEXT, null, null);
    }

    @Override
    public void onCurrentChannelUri(Uri channelUri) {
        if (DEBUG) {
            Log.d(TAG, "onCurrentChannelUri uri=" + channelUri);
        }
        mCurrentChannelUri = channelUri;
        mChannelUriView.setText("Channel URI: " + channelUri);
    }

    @Override
    public void onTrackInfoList(List<TvTrackInfo> tracks) {
        if (DEBUG) {
            Log.d(TAG, "onTrackInfoList size=" + tracks.size());
            for (int i = 0; i < tracks.size(); i++) {
                TvTrackInfo trackInfo = tracks.get(i);
                if (trackInfo != null) {
                    Log.d(TAG, "track " + i + ": type=" + trackInfo.getType() +
                            " id=" + trackInfo.getId());
                }
            }
        }
        for (TvTrackInfo info : tracks) {
            if (info.getType() == TvTrackInfo.TYPE_AUDIO) {
                mFirstAudioTrackId = info.getId();
                break;
            }
        }
        mTracks = tracks;
    }

    @Override
    public void onTracksChanged(List<TvTrackInfo> tracks) {
        if (DEBUG) {
            Log.d(TAG, "onTracksChanged");
        }
        onTrackInfoList(tracks);
    }

    @Override
    public void onTrackSelected(int type, String trackId) {
        if (DEBUG) {
            Log.d(TAG, "onTrackSelected type=" + type + " trackId=" + trackId);
        }
        updateTrackSelectedView(type, trackId);

        if (TextUtils.equals(mSelectingAudioTrackId, trackId)) {
            if (mSelectingAudioTrackId == null) {
                updateLogText("unselect audio succeed");
            } else {
                updateLogText("select audio succeed");
            }
        }
    }

    @Override
    public void onCurrentTvInputId(String inputId) {
        if (DEBUG) {
            Log.d(TAG, "onCurrentTvInputId id=" + inputId);
        }
        mCurrentTvInputId = inputId;
        mTvInputIdView.setText("TV Input ID: " + inputId);
    }

    @Override
    public void onTuned(Uri channelUri) {
        mCurrentChannelUri = channelUri;
    }

    @Override
    public void onCurrentVideoBounds(@NonNull Rect bounds) {
        updateLogText("Received video Bounds " + bounds.toShortString());
    }

    @Override
    public void onBroadcastInfoResponse(BroadcastInfoResponse response) {
        if (mGeneratedRequestId == response.getRequestId()) {
            if (!mRequestStreamEventFinished && response instanceof StreamEventResponse) {
                handleStreamEventResponse((StreamEventResponse) response);
            } else if (mSectionReceived < MAX_HANDLED_RESPONSE
                    && response instanceof SectionResponse) {
                handleSectionResponse((SectionResponse) response);
            } else if (response instanceof TableResponse) {
                handleTableResponse((TableResponse) response);
            }
        }
    }

    private void handleSectionResponse(SectionResponse response) {
        mSectionReceived++;
        byte[] data = null;
        Bundle params = response.getSessionData();
        if (params != null) {
            // TODO: define the key
            data = params.getByteArray("key_raw_data");
        }
        int version = response.getVersion();
        updateLogText(
                "Received section data version = "
                        + version
                        + ", data = "
                        + Arrays.toString(data));
    }

    private void handleStreamEventResponse(StreamEventResponse response) {
        updateLogText("Received stream event response");
        byte[] rData = response.getData();
        if (rData == null) {
            mRequestStreamEventFinished = true;
            updateLogText("Received stream event data is null");
            return;
        }
        // TODO: convert to Hex instead
        String data = Arrays.toString(rData);
        if (mStreamDataList.contains(data)) {
            return;
        }
        mStreamDataList.add(data);
        updateLogText(
                "Received stream event data("
                        + (mStreamDataList.size() - 1)
                        + "): "
                        + data);
        if (mStreamDataList.size() >= MAX_HANDLED_RESPONSE) {
            mRequestStreamEventFinished = true;
            updateLogText("Received stream event data finished");
        }
    }

    private void handleTableResponse(TableResponse response) {
        updateLogText(
                "Received table data version = "
                        + response.getVersion()
                        + ", size="
                        + response.getSize()
                        + ", requestId="
                        + response.getRequestId()
                        + ", data = "
                        + Arrays.toString(getTableByteArray(response)));
    }

    private void selectTrack(int type, String trackId) {
        Bundle params = new Bundle();
        params.putInt(TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_TYPE, type);
        params.putString(TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_ID, trackId);
        mHandler.post(
                () ->
                        sendPlaybackCommandRequest(
                                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_SELECT_TRACK,
                                params));
    }

    private int generateRequestId() {
        return ++mGeneratedRequestId;
    }

    private void requestStreamEvent(String targetUri, String eventName) {
        if (targetUri == null) {
            return;
        }
        int requestId = generateRequestId();
        BroadcastInfoRequest request =
                new StreamEventRequest(
                        requestId,
                        BroadcastInfoRequest.REQUEST_OPTION_AUTO_UPDATE,
                        Uri.parse(targetUri),
                        eventName);
        requestBroadcastInfo(request);
    }

    private void requestSection(boolean repeat, int tsPid, int tableId, int version) {
        int requestId = generateRequestId();
        BroadcastInfoRequest request =
                new SectionRequest(
                        requestId,
                        repeat ?
                                BroadcastInfoRequest.REQUEST_OPTION_REPEAT :
                                BroadcastInfoRequest.REQUEST_OPTION_AUTO_UPDATE,
                        tsPid,
                        tableId,
                        version);
        requestBroadcastInfo(request);
    }

    private void requestTable(boolean repeat,  int tableId, int tableName, int version) {
        int requestId = generateRequestId();
        BroadcastInfoRequest request =
                new TableRequest(
                        requestId,
                        repeat
                                ? BroadcastInfoRequest.REQUEST_OPTION_REPEAT
                                : BroadcastInfoRequest.REQUEST_OPTION_AUTO_UPDATE,
                        tableId,
                        tableName,
                        version);
        requestBroadcastInfo(request);
    }

    public void requestAd() {
        try {
            // TODO: add the AD file to this project
            RandomAccessFile adiFile =
                    new RandomAccessFile(
                            mContext.getApplicationContext().getFilesDir() + "/ad.mp4", "r");
            mAdFd = ParcelFileDescriptor.dup(adiFile.getFD());
        } catch (Exception e) {
            updateLogText("open advertisement file failed. " + e.getMessage());
            return;
        }
        long startTime = 20000;
        long stopTime = startTime + 25000;
        long echoInterval = 1000;
        String mediaFileType = "MP4";
        mHandler.post(
                () -> {
                    AdRequest adRequest;
                    if (mAdState == AdResponse.RESPONSE_TYPE_PLAYING) {
                        updateLogText("RequestAd stop");
                        adRequest =
                                new AdRequest(
                                        mGeneratedRequestId,
                                        AdRequest.REQUEST_TYPE_STOP,
                                        null,
                                        0,
                                        0,
                                        0,
                                        null,
                                        null);
                    } else {
                        updateLogText("RequestAd start");
                        int requestId = generateRequestId();
                        mAdSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                        mAdSurfaceView.setVisibility(View.VISIBLE);
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("dai_surface", mAdSurface);
                        adRequest =
                                new AdRequest(
                                        requestId,
                                        AdRequest.REQUEST_TYPE_START,
                                        mAdFd,
                                        startTime,
                                        stopTime,
                                        echoInterval,
                                        mediaFileType,
                                        bundle);
                    }
                    requestAd(adRequest);
                });
    }

    @TargetApi(34)
    private List<AppLinkInfo> getAppLinkInfoList() {
        if (Build.VERSION.SDK_INT < 34 || mTvIAppManager == null) {
            return new ArrayList<>();
        }
        return mTvIAppManager.getAppLinkInfoList();
    }

    @TargetApi(34)
    private void requestCurrentVideoBoundsWrapper() {
        if (Build.VERSION.SDK_INT < 34) {
            return;
        }
        requestCurrentVideoBounds();
    }

    @TargetApi(34)
    private byte[] getTableByteArray(TableResponse response) {
        if (Build.VERSION.SDK_INT < 34) {
            return null;
        }
        return response.getTableByteArray();
    }
}
