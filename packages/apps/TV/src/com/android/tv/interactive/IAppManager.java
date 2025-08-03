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

package com.android.tv.interactive;

import static com.android.tv.util.CaptionSettings.OPTION_OFF;
import static com.android.tv.util.CaptionSettings.OPTION_ON;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.media.tv.TvTrackInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.AitInfo;
import android.media.tv.interactive.TvInteractiveAppService;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.tv.interactive.TvInteractiveAppView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.util.ContentUriUtils;
import com.android.tv.data.api.Channel;
import com.android.tv.dialog.InteractiveAppDialogFragment;
import com.android.tv.features.TvFeatures;
import com.android.tv.ui.TunableTvView;
import com.android.tv.util.TvSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class IAppManager {
    private static final String TAG = "IAppManager";
    private static final boolean DEBUG = false;

    private final MainActivity mMainActivity;
    private final TvInteractiveAppManager mTvIAppManager;
    private final TvInteractiveAppView mTvIAppView;
    private final TunableTvView mTvView;
    private final Handler mHandler;
    private AitInfo mCurrentAitInfo;
    private AitInfo mHeldAitInfo; // AIT info that has been held pending dialog confirmation
    private boolean mTvAppDialogShown = false;

    public IAppManager(@NonNull MainActivity parentActivity, @NonNull TunableTvView tvView,
            @NonNull Handler handler) {
        SoftPreconditions.checkFeatureEnabled(parentActivity, TvFeatures.HAS_TIAF, TAG);

        mMainActivity = parentActivity;
        mTvView = tvView;
        mHandler = handler;
        mTvIAppManager = mMainActivity.getSystemService(TvInteractiveAppManager.class);
        mTvIAppView = mMainActivity.findViewById(R.id.tv_app_view);
        if (mTvIAppManager == null || mTvIAppView == null) {
            Log.e(TAG, "Could not find interactive app view or manager");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        mTvIAppManager.registerCallback(
                executor,
                new MyInteractiveAppManagerCallback()
        );
        mTvIAppView.setCallback(
                executor,
                new MyInteractiveAppViewCallback()
        );
        mTvIAppView.setOnUnhandledInputEventListener(executor,
                inputEvent -> {
                    if (mMainActivity.isKeyEventBlocked()) {
                        return true;
                    }
                    if (inputEvent instanceof KeyEvent) {
                        KeyEvent keyEvent = (KeyEvent) inputEvent;
                        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN
                                && keyEvent.isLongPress()) {
                            if (mMainActivity.onKeyLongPress(keyEvent.getKeyCode(), keyEvent)) {
                                return true;
                            }
                        }
                        if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                            return mMainActivity.onKeyUp(keyEvent.getKeyCode(), keyEvent);
                        } else if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                            return mMainActivity.onKeyDown(keyEvent.getKeyCode(), keyEvent);
                        }
                    }
                    return false;
                });
    }

    public void stop() {
        mTvIAppView.stopInteractiveApp();
        mTvIAppView.reset();
        mCurrentAitInfo = null;
    }

    /*
     * Update current info based on ait info that was held when the dialog was shown.
     */
    public void processHeldAitInfo() {
        if (mHeldAitInfo != null) {
            onAitInfoUpdated(mHeldAitInfo);
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mTvIAppView != null && mTvIAppView.getVisibility() == View.VISIBLE
                && mTvIAppView.dispatchKeyEvent(event)){
            return true;
        }
        return false;
    }

    public void onAitInfoUpdated(AitInfo aitInfo) {
        if (mTvIAppManager == null || aitInfo == null) {
            return;
        }
        if (mCurrentAitInfo != null && mCurrentAitInfo.getType() == aitInfo.getType()) {
            if (DEBUG) {
                Log.d(TAG, "Ignoring AIT update: Same type as current");
            }
            return;
        }

        List<TvInteractiveAppServiceInfo> tvIAppInfoList =
                mTvIAppManager.getTvInteractiveAppServiceList();
        if (tvIAppInfoList.isEmpty()) {
            if (DEBUG) {
                Log.d(TAG, "Ignoring AIT update: No interactive app services registered");
            }
            return;
        }

        // App Type ID numbers allocated by DVB Services
        int type = -1;
        switch (aitInfo.getType()) {
            case 0x0010: // HBBTV
                type = TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV;
                break;
            case 0x0006: // DCAP-J: DCAP Java applications
            case 0x0007: // DCAP-X: DCAP XHTML applications
                type = TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_ATSC;
                break;
            case 0x0001: // Ginga-J
            case 0x0009: // Ginga-NCL
            case 0x000b: // Ginga-HTML5
                type = TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_GINGA;
                break;
            default:
                Log.e(TAG, "AIT info contained unknown type: " + aitInfo.getType());
                return;
        }

        if (TvSettings.isTvIAppOn(mMainActivity.getApplicationContext())) {
            mTvAppDialogShown = false;
            for (TvInteractiveAppServiceInfo info : tvIAppInfoList) {
                if ((info.getSupportedTypes() & type) > 0) {
                    mCurrentAitInfo = aitInfo;
                    if (mTvIAppView != null) {
                        mTvIAppView.setVisibility(View.VISIBLE);
                        mTvIAppView.prepareInteractiveApp(info.getId(), type);
                    }
                    break;
                }
            }
        } else if (!mTvAppDialogShown) {
            if (DEBUG) {
                Log.d(TAG, "TV IApp is not enabled");
            }

            for (TvInteractiveAppServiceInfo info : tvIAppInfoList) {
                if ((info.getSupportedTypes() & type) > 0) {
                    mMainActivity.getOverlayManager().showDialogFragment(
                            InteractiveAppDialogFragment.DIALOG_TAG,
                            InteractiveAppDialogFragment.create(info.getServiceInfo().packageName),
                            false);
                    mHeldAitInfo = aitInfo;
                    mTvAppDialogShown = true;
                    break;
                }
            }
        }
    }

    private class MyInteractiveAppManagerCallback extends
            TvInteractiveAppManager.TvInteractiveAppCallback {
        @Override
        public void onInteractiveAppServiceAdded(String iAppServiceId) {}

        @Override
        public void onInteractiveAppServiceRemoved(String iAppServiceId) {}

        @Override
        public void onInteractiveAppServiceUpdated(String iAppServiceId) {}

        @Override
        public void onTvInteractiveAppServiceStateChanged(String iAppServiceId, int type, int state,
                int err) {
            if (state == TvInteractiveAppManager.SERVICE_STATE_READY && mTvIAppView != null) {
                mTvIAppView.startInteractiveApp();
                mTvIAppView.setTvView(mTvView.getTvView());
                if (mTvView.getTvView() != null) {
                    mTvView.getTvView().setInteractiveAppNotificationEnabled(true);
                }
            }
        }
    }

    private class MyInteractiveAppViewCallback extends
            TvInteractiveAppView.TvInteractiveAppCallback {
        @Override
        public void onPlaybackCommandRequest(String iAppServiceId, String cmdType,
                Bundle parameters) {
            if (mTvView == null || cmdType == null) {
                return;
            }
            switch (cmdType) {
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE:
                    if (parameters == null) {
                        return;
                    }
                    String uriString = parameters.getString(
                            TvInteractiveAppService.COMMAND_PARAMETER_KEY_CHANNEL_URI);
                    if (uriString != null) {
                        Uri channelUri = Uri.parse(uriString);
                        Channel channel = mMainActivity.getChannelDataManager().getChannel(
                                ContentUriUtils.safeParseId(channelUri));
                        if (channel != null) {
                            mHandler.post(() -> mMainActivity.tuneToChannel(channel));
                        }
                    }
                    break;
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_SELECT_TRACK:
                    if (mTvView != null && parameters != null) {
                        int trackType = parameters.getInt(
                                TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_TYPE,
                                -1);
                        String trackId = parameters.getString(
                                TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_ID,
                                null);
                        switch (trackType) {
                            case TvTrackInfo.TYPE_AUDIO:
                                // When trackId is null, deselects current audio track.
                                mHandler.post(() -> mMainActivity.selectAudioTrack(trackId));
                                break;
                            case TvTrackInfo.TYPE_SUBTITLE:
                                // When trackId is null, turns off captions.
                                mHandler.post(() -> mMainActivity.selectSubtitleTrack(
                                        trackId == null ? OPTION_OFF : OPTION_ON, trackId));
                                break;
                        }
                    }
                    break;
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_SET_STREAM_VOLUME:
                    if (parameters == null) {
                        return;
                    }
                    float volume = parameters.getFloat(
                            TvInteractiveAppService.COMMAND_PARAMETER_KEY_VOLUME, -1);
                    if (volume >= 0.0 && volume <= 1.0) {
                        mHandler.post(() -> mTvView.setStreamVolume(volume));
                    }
                    break;
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE_NEXT:
                    mHandler.post(mMainActivity::channelUp);
                    break;
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE_PREV:
                    mHandler.post(mMainActivity::channelDown);
                    break;
                case TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP:
                    int mode = 1; // TvInteractiveAppService.COMMAND_PARAMETER_VALUE_STOP_MODE_BLANK
                    if (parameters != null) {
                        mode = parameters.getInt(
                                /* TvInteractiveAppService.COMMAND_PARAMETER_KEY_STOP_MODE */
                                "command_stop_mode",
                                /*TvInteractiveAppService.COMMAND_PARAMETER_VALUE_STOP_MODE_BLANK*/
                                1);
                    }
                    mHandler.post(mMainActivity::stopTv);
                    break;
                default:
                    Log.e(TAG, "PlaybackCommandRequest had unknown cmdType:"
                            + cmdType);
                    break;
            }
        }

        @Override
        public void onStateChanged(String iAppServiceId, int state, int err) {
        }

        @Override
        public void onBiInteractiveAppCreated(String iAppServiceId, Uri biIAppUri,
                String biIAppId) {}

        @Override
        public void onTeletextAppStateChanged(String iAppServiceId, int state) {}

        @Override
        public void onSetVideoBounds(String iAppServiceId, Rect rect) {
            if (mTvView != null) {
                ViewGroup.MarginLayoutParams layoutParams = mTvView.getTvViewLayoutParams();
                layoutParams.setMargins(rect.left, rect.top, rect.right, rect.bottom);
                mTvView.setTvViewLayoutParams(layoutParams);
            }
        }

        @Override
        @TargetApi(34)
        public void onRequestCurrentVideoBounds(@NonNull String iAppServiceId) {
            mHandler.post(
                    () -> {
                        if (DEBUG) {
                            Log.d(TAG, "onRequestCurrentVideoBounds service ID = "
                                    + iAppServiceId);
                        }
                        Rect bounds = new Rect(mTvView.getLeft(), mTvView.getTop(),
                                mTvView.getRight(), mTvView.getBottom());
                        mTvIAppView.sendCurrentVideoBounds(bounds);
                    });
        }

        @Override
        public void onRequestCurrentChannelUri(String iAppServiceId) {
            if (mTvIAppView == null) {
                return;
            }
            Channel currentChannel = mMainActivity.getCurrentChannel();
            Uri currentUri = (currentChannel == null)
                    ? null
                    : currentChannel.getUri();
            mTvIAppView.sendCurrentChannelUri(currentUri);
        }

        @Override
        public void onRequestCurrentChannelLcn(String iAppServiceId) {
            if (mTvIAppView == null) {
                return;
            }
            Channel currentChannel = mMainActivity.getCurrentChannel();
            if (currentChannel == null || currentChannel.getDisplayNumber() == null) {
                return;
            }
            // Expected format is major channel number, delimiter, minor channel number
            String displayNumber = currentChannel.getDisplayNumber();
            String format = "[0-9]+" + Channel.CHANNEL_NUMBER_DELIMITER + "[0-9]+";
            if (!displayNumber.matches(format)) {
                return;
            }
            // Major channel number is returned
            String[] numbers = displayNumber.split(
                    String.valueOf(Channel.CHANNEL_NUMBER_DELIMITER));
            mTvIAppView.sendCurrentChannelLcn(Integer.parseInt(numbers[0]));
        }

        @Override
        public void onRequestStreamVolume(String iAppServiceId) {
            if (mTvIAppView == null || mTvView == null) {
                return;
            }
            mTvIAppView.sendStreamVolume(mTvView.getStreamVolume());
        }

        @Override
        public void onRequestTrackInfoList(String iAppServiceId) {
            if (mTvIAppView == null || mTvView == null) {
                return;
            }
            List<TvTrackInfo> allTracks = new ArrayList<>();
            int[] trackTypes = new int[] {TvTrackInfo.TYPE_AUDIO,
                    TvTrackInfo.TYPE_VIDEO, TvTrackInfo.TYPE_SUBTITLE};

            for (int trackType : trackTypes) {
                List<TvTrackInfo> currentTracks = mTvView.getTracks(trackType);
                if (currentTracks == null) {
                    continue;
                }
                for (TvTrackInfo track : currentTracks) {
                    if (track != null) {
                        allTracks.add(track);
                    }
                }
            }
            mTvIAppView.sendTrackInfoList(allTracks);
        }

        @Override
        public void onRequestCurrentTvInputId(String iAppServiceId) {
            if (mTvIAppView == null) {
                return;
            }
            Channel currentChannel = mMainActivity.getCurrentChannel();
            String currentInputId = (currentChannel == null)
                    ? null
                    : currentChannel.getInputId();
            mTvIAppView.sendCurrentTvInputId(currentInputId);
        }

        @Override
        public void onRequestSigning(String iAppServiceId, String signingId, String algorithm,
                String alias, byte[] data) {}
    }
}
