/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.car.kitchensink.volume;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.media.AudioManager.FLAG_PLAY_SOUND;

import android.car.feature.Flags;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.media.AudioAttributes.AttributeUsage;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.fragment.app.Fragment;

import com.android.internal.annotations.GuardedBy;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.volume.VolumeTestFragment.CarAudioZoneVolumeInfo;

import java.util.List;

public final class CarAudioZoneVolumeFragment extends Fragment {
    private static final String TAG = "CarVolumeTest."
            + CarAudioZoneVolumeFragment.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final int MSG_VOLUME_CHANGED = 0;
    private static final int MSG_REQUEST_FOCUS = 1;
    private static final int MSG_FOCUS_CHANGED = 2;
    private static final int MSG_STOP_RINGTONE = 3;
    private static final int MSG_ADJUST_VOLUME = 4;
    private static final int MSG_EVENT_RECEIVED = 5;
    private static final long RINGTONE_STOP_TIME_MS = 3_000;
    private static final int ADJUST_VOLUME_UP = 0;
    private static final int ADJUST_VOLUME_DOWN = 1;

    private final int mZoneId;
    private final Object mLock = new Object();
    private final CarAudioManager mCarAudioManager;
    private final AudioManager mAudioManager;
    private CarAudioZoneVolumeInfo[] mVolumeInfos =
            new CarAudioZoneVolumeInfo[0];
    private final Handler mHandler = new VolumeHandler();

    private CarAudioZoneVolumeAdapter mCarAudioZoneVolumeAdapter;
    private final SparseIntArray mGroupIdIndexMap = new SparseIntArray();

    @GuardedBy("mLock")
    private Ringtone mRingtone;

    void sendVolumeChangedMessage(int groupId, int flags) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_VOLUME_CHANGED, groupId, flags));
    }

    void adjustVolumeUp(int groupId) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ADJUST_VOLUME, groupId, ADJUST_VOLUME_UP));
    }

    void adjustVolumeDown(int groupId) {
        mHandler.sendMessage(mHandler
                .obtainMessage(MSG_ADJUST_VOLUME, groupId, ADJUST_VOLUME_DOWN));
    }

    void sendEventReceivedMessage(CarVolumeGroupEvent event) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_EVENT_RECEIVED, event));
    }

    private class VolumeHandler extends Handler {
        private AudioFocusListener mFocusListener;

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) {
                Log.d(TAG, "zone " + mZoneId + " handleMessage : " + getMessageName(msg));
            }
            switch (msg.what) {
                case MSG_VOLUME_CHANGED:
                    initVolumeInfo();
                    playRingtoneForGroup(msg.arg1, msg.arg2);
                    break;
                case MSG_STOP_RINGTONE:
                    stopRingtone();
                    break;
                case MSG_REQUEST_FOCUS:
                    int groupId = msg.arg1;
                    if (mFocusListener != null) {
                        mAudioManager.abandonAudioFocus(mFocusListener);
                        mVolumeInfos[mGroupIdIndexMap.get(groupId)].hasAudioFocus = false;
                        mCarAudioZoneVolumeAdapter.notifyDataSetChanged();
                    }

                    mFocusListener = new AudioFocusListener(groupId);
                    mAudioManager.requestAudioFocus(mFocusListener, groupId,
                            AudioManager.AUDIOFOCUS_GAIN);
                    break;
                case MSG_FOCUS_CHANGED:
                    int focusGroupId = msg.arg1;
                    mVolumeInfos[mGroupIdIndexMap.get(focusGroupId)].hasAudioFocus = true;
                    mCarAudioZoneVolumeAdapter.refreshVolumes(mVolumeInfos);
                    break;
                case MSG_ADJUST_VOLUME:
                    adjustVolumeByOne(msg.arg1, msg.arg2 == ADJUST_VOLUME_UP);
                    break;
                case MSG_EVENT_RECEIVED:
                    CarVolumeGroupEvent event = (CarVolumeGroupEvent) msg.obj;
                    handleVolumeGroupEventReceived(event);
                    break;
                default:
                    Log.wtf(TAG, "VolumeHandler handleMessage called with unknown message"
                            + msg.what);
                    break;
            }
        }
    }

    public CarAudioZoneVolumeFragment(int zoneId, CarAudioManager carAudioManager,
            AudioManager audioManager) {
        mZoneId = zoneId;
        mCarAudioManager = carAudioManager;
        mAudioManager = audioManager;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView " + mZoneId);
        }
        View v = inflater.inflate(R.layout.zone_volume_tab, container, false);
        ListView volumeListView = v.findViewById(R.id.volume_list);
        mCarAudioZoneVolumeAdapter =
                new CarAudioZoneVolumeAdapter(getContext(), R.layout.volume_item, mVolumeInfos,
                        this, mCarAudioManager.isAudioFeatureEnabled(
                        AUDIO_FEATURE_VOLUME_GROUP_MUTING));
        initVolumeInfo();
        volumeListView.setAdapter(mCarAudioZoneVolumeAdapter);
        return v;
    }

    void initVolumeInfo() {
        int volumeGroupCount = mCarAudioManager.getVolumeGroupCount(mZoneId);
        mVolumeInfos = new CarAudioZoneVolumeInfo[volumeGroupCount + 1];
        mGroupIdIndexMap.clear();
        CarAudioZoneVolumeInfo titlesInfo = new CarAudioZoneVolumeInfo();
        titlesInfo.id = "Group id";
        titlesInfo.currentGain = "Current";
        mVolumeInfos[0] = titlesInfo;

        int i = 1;
        for (int groupId = 0; groupId < volumeGroupCount; groupId++) {
            CarVolumeGroupInfo groupInfo = mCarAudioManager.getVolumeGroupInfo(mZoneId, groupId);
            mVolumeInfos[i] = createCarAudioZoneVolumeInfo(groupInfo);
            mGroupIdIndexMap.put(groupId, i);
            i++;
        }
        mCarAudioZoneVolumeAdapter.refreshVolumes(mVolumeInfos);
    }

    private CarAudioZoneVolumeInfo createCarAudioZoneVolumeInfo(CarVolumeGroupInfo info) {
        CarAudioZoneVolumeInfo volumeInfo = new CarAudioZoneVolumeInfo();

        volumeInfo.groupId = info.getId();
        volumeInfo.id = String.valueOf(info.getId());
        volumeInfo.currentGain = String.valueOf(info.getVolumeGainIndex());
        volumeInfo.maxGain = info.getMaxVolumeGainIndex();
        volumeInfo.minGain = info.getMinVolumeGainIndex();
        volumeInfo.isMuted = info.isMuted();
        volumeInfo.isAttenuated = info.isAttenuated();
        volumeInfo.isBlocked = info.isBlocked();
        if (Flags.carAudioMuteAmbiguity()) {
            volumeInfo.isSystemMuted = info.isMutedBySystem();
        } else {
            volumeInfo.isSystemMuted = info.isBlocked() && info.isMuted();
        }
        if (DEBUG) {
            Log.d(TAG, "createCarAudioZoneVolumeInfo: Group Id: " + info.getId()
                    + " max: " + volumeInfo.maxGain + " current: " + volumeInfo.currentGain
                    + " is muted " + volumeInfo.isMuted
                    + " is attenuated " + volumeInfo.isAttenuated
                    + " is blocked " + volumeInfo.isBlocked
                    + " is muted by system " + volumeInfo.isSystemMuted);
        }
        return volumeInfo;
    }

    private void adjustVolumeByOne(int groupId, boolean up) {
        if (mCarAudioManager == null) {
            Log.e(TAG, "CarAudioManager is null");
            return;
        }
        int current = mCarAudioManager.getGroupVolume(mZoneId, groupId);
        CarAudioZoneVolumeInfo info = getVolumeInfo(groupId);
        int volume = up ? current + 1 : current - 1;
        if (volume > info.maxGain) {
            if (DEBUG) {
                Log.d(TAG, "Reached " + groupId + " max volume "
                        + " limit " + volume);
            }
            return;
        }
        if (volume < info.minGain) {
            if (DEBUG) {
                Log.d(TAG, "Reached " + groupId + " min volume "
                        + " limit " + volume);
            }
            return;
        }
        mCarAudioManager.setGroupVolume(mZoneId, groupId, volume, /* flags= */ 0);
        if (DEBUG) {
            Log.d(TAG, "Set group " + groupId + " volume "
                    + mCarAudioManager.getGroupVolume(mZoneId, groupId)
                    + " in audio zone " + mZoneId);
        }
    }

    private CarAudioZoneVolumeInfo getVolumeInfo(int groupId) {
        return mVolumeInfos[mGroupIdIndexMap.get(groupId)];
    }

    public void toggleMute(int groupId) {
        if (mCarAudioManager == null) {
            Log.e(TAG, "CarAudioManager is null");
            return;
        }
        boolean isMuted = mCarAudioManager.isVolumeGroupMuted(mZoneId, groupId);
        mCarAudioManager.setVolumeGroupMute(mZoneId, groupId, !isMuted, AudioManager.FLAG_SHOW_UI);
        if (DEBUG) {
            Log.d(TAG, "Set group mute " + groupId + " mute " + !isMuted + " in audio zone "
                    + mZoneId);
        }
    }

    void requestFocus(int groupId) {
        // Automatic volume change only works for primary audio zone.
        if (mZoneId == CarAudioManager.PRIMARY_AUDIO_ZONE) {
            mHandler.sendMessage(mHandler
                    .obtainMessage(MSG_REQUEST_FOCUS, groupId, /* arg2= */ 0));
        }
    }

    private void playRingtoneForGroup(int groupId, int flags) {
        if (DEBUG) {
            Log.d(TAG, "playRingtoneForGroup(" + groupId + ") in zone " + mZoneId);
        }

        if ((flags & FLAG_PLAY_SOUND) == 0) {
            return;
        }

        int usage = mCarAudioManager.getUsagesForVolumeGroupId(mZoneId, groupId)[0];
        if (isRingtoneActiveForUsage(usage)) {
            return;
        }

        mHandler.removeMessages(MSG_STOP_RINGTONE);

        stopRingtone();
        startRingtone(usage);

        mHandler.sendEmptyMessageDelayed(MSG_STOP_RINGTONE, RINGTONE_STOP_TIME_MS);
    }

    private void startRingtone(@AttributeUsage int usage) {
        if (DEBUG) {
            Log.d(TAG, "Start ringtone for zone " + mZoneId + " and usage "
                    + AudioAttributes.usageToString(usage));
        }

        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);

        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }

        AudioAttributes attributes = builder.build();

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(getContext(),
                AudioAttributes.toLegacyStreamType(attributes));

        Ringtone ringtone =
                RingtoneManager.getRingtone(mCarAudioZoneVolumeAdapter.getContext(), uri);
        ringtone.setAudioAttributes(attributes);
        ringtone.setLooping(true);

        ringtone.play();

        synchronized (mLock) {
            mRingtone = ringtone;
        }
    }

    private void stopRingtone() {
        synchronized (mLock) {
            if (mRingtone == null) {
                return;
            }
            if (mRingtone.isPlaying()) {
                mRingtone.stop();
            }
            mRingtone = null;
        }
    }

    boolean isRingtoneActiveForUsage(@AttributeUsage int usage) {
        synchronized (mLock) {
            return mRingtone != null && mRingtone.isPlaying()
                    && mRingtone.getAudioAttributes().getUsage() == usage;
        }
    }

    private class AudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        private final int mGroupId;
        AudioFocusListener(int groupId) {
            mGroupId = groupId;
        }
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(MSG_FOCUS_CHANGED, mGroupId, /* arg2= */ 0));
            } else {
                Log.e(TAG, "Audio focus request failed");
            }
        }
    }

    private void handleVolumeGroupEventReceived(CarVolumeGroupEvent event) {
        if (DEBUG) {
            Log.d(TAG, "Handling volume group event: " + event);
        }

        if ((event.getEventTypes() & CarVolumeGroupEvent.EVENT_TYPE_ZONE_CONFIGURATION_CHANGED)
                == CarVolumeGroupEvent.EVENT_TYPE_ZONE_CONFIGURATION_CHANGED) {
            initVolumeInfo();
            return;
        }

        int flags = CarVolumeGroupEvent.convertExtraInfoToFlags(event.getExtraInfos());
        List<CarVolumeGroupInfo> infos = event.getCarVolumeGroupInfos();
        for (int index = 0; index < infos.size(); index++) {
            CarVolumeGroupInfo info = infos.get(index);
            int groupId = info.getId();
            if (info.getZoneId() == mZoneId) {
                mVolumeInfos[groupId + 1] = createCarAudioZoneVolumeInfo(info);
                playRingtoneForGroup(groupId, flags);
            }
        }
        mCarAudioZoneVolumeAdapter.refreshVolumes(mVolumeInfos);
    }
}
