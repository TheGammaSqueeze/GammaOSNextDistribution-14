/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media;

import android.app.PendingIntent;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.media.audiopolicy.AudioPolicy;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.Handler;
import android.view.KeyEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class AudioManager {
    public AudioManager() {}

    public AudioManager(Context context) { }

    public void dispatchMediaKeyEvent(KeyEvent keyEvent) { }

    public void preDispatchKeyEvent(KeyEvent event, int stream) { }

    public boolean isVolumeFixed() {
        return false;
    }

    public void adjustStreamVolume(int streamType, int direction, int flags) { }

    public void adjustVolume(int direction, int flags) { }

    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags) { }

    public void setMasterMute(boolean mute, int flags) { }

    public int getRingerMode() {
        return 0;
    }

    public int getStreamMaxVolume(int streamType) {
        return 0;
    }

    public int getStreamMinVolume(int streamType) {
        return 0;
    }

    public int getStreamMinVolumeInt(int streamType) {
        return 0;
    }

    public int getStreamVolume(int streamType) {
        return 0;
    }

    public float getStreamVolumeDb(int streamType, int index, int deviceType) {
        return 0;
    }

    public int getLastAudibleStreamVolume(int streamType) {
        return 0;
    }

    public int getUiSoundsStreamType() {
        return 0;
    }

    public void setRingerMode(int ringerMode) { }

    public void setStreamVolume(int streamType, int index, int flags) { }

    public void setVolumeIndexForAttributes(AudioAttributes attr, int index, int flags) { }

    public int getVolumeIndexForAttributes(AudioAttributes attr) {
        return 0;
    }

    public int getMaxVolumeIndexForAttributes(AudioAttributes attr) {
        return 0;
    }

    public int getMinVolumeIndexForAttributes(AudioAttributes attr) {
        return 0;
    }

    public void setSupportedSystemUsages(int[] systemUsages) { }

    public int[] getSupportedSystemUsages() {
        return null;
    }

    public void setStreamSolo(int streamType, boolean state) { }

    public void setStreamMute(int streamType, boolean state) { }

    public boolean isStreamMute(int streamType) {
        return false;
    }

    public boolean isMasterMute() {
        return false;
    }

    public void forceVolumeControlStream(int streamType) { }

    public boolean shouldVibrate(int vibrateType) {
        return false;
    }

    public int getVibrateSetting(int vibrateType) {
        return 0;
    }

    public void setVibrateSetting(int vibrateType, int vibrateSetting) { }

    public void setSpeakerphoneOn(boolean on) { }

    public boolean isSpeakerphoneOn() {
        return false;
    }

    public void setAllowedCapturePolicy(int capturePolicy) { }

    public int getAllowedCapturePolicy() {
        return 0;
    }

    public boolean setPreferredDeviceForStrategy(AudioProductStrategy strategy, AudioDeviceAttributes device) {
        return false;
    }

    public boolean removePreferredDeviceForStrategy(AudioProductStrategy strategy) {
        return false;
    }

    public AudioDeviceAttributes getPreferredDeviceForStrategy(AudioProductStrategy strategy) {
        return null;
    }

    public boolean setPreferredDevicesForStrategy(AudioProductStrategy strategy, List<AudioDeviceAttributes> devices) {
        return false;
    }

    public List<AudioDeviceAttributes> getPreferredDevicesForStrategy(AudioProductStrategy strategy) {
        return null;
    }

    public void addOnPreferredDeviceForStrategyChangedListener(Executor executor, AudioManager.OnPreferredDeviceForStrategyChangedListener listener) throws SecurityException { }

    public void removeOnPreferredDeviceForStrategyChangedListener(AudioManager.OnPreferredDeviceForStrategyChangedListener listener) { }

    public void addOnPreferredDevicesForStrategyChangedListener(Executor executor,
            AudioManager.OnPreferredDevicesForStrategyChangedListener listener) throws SecurityException { }

    public void removeOnPreferredDevicesForStrategyChangedListener(AudioManager.OnPreferredDevicesForStrategyChangedListener listener) { }

    public boolean setPreferredDeviceForCapturePreset(int capturePreset, AudioDeviceAttributes device) {
        return false;
    }

    public boolean clearPreferredDevicesForCapturePreset(int capturePreset) {
        return false;
    }

    public List<AudioDeviceAttributes> getPreferredDevicesForCapturePreset(int capturePreset) {
        return null;
    }

    public void addOnPreferredDevicesForCapturePresetChangedListener(Executor executor,
            AudioManager.OnPreferredDevicesForCapturePresetChangedListener listener) throws SecurityException { }

    public void removeOnPreferredDevicesForCapturePresetChangedListener(AudioManager.OnPreferredDevicesForCapturePresetChangedListener listener) { }

    public boolean isBluetoothScoAvailableOffCall() {
        return false;
    }

    public void startBluetoothSco() { }

    public void startBluetoothScoVirtualCall() { }

    public void stopBluetoothSco() { }

    public void setBluetoothScoOn(boolean on) { }

    public boolean isBluetoothScoOn() {
        return false;
    }

    public void setBluetoothA2dpOn(boolean on) { }

    public boolean isBluetoothA2dpOn() {
        return false;
    }

    public void setWiredHeadsetOn(boolean on) { }

    public boolean isWiredHeadsetOn() {
        return false;
    }

    public void setMicrophoneMute(boolean on) { }

    public void setMicrophoneMuteFromSwitch(boolean on) { }

    public boolean isMicrophoneMute() {
        return false;
    }

    public void setMode(int mode) { }

    public int getMode() {
        return 0;
    }

    public boolean isCallScreeningModeSupported() {
        return false;
    }

    public void setRouting(int mode, int routes, int mask) { }

    public int getRouting(int mode) {
        return 0;
    }

    public boolean isMusicActive() {
        return false;
    }

    public boolean isMusicActiveRemotely() {
        return false;
    }

    public boolean isAudioFocusExclusive() {
        return false;
    }

    public int generateAudioSessionId() {
        return 0;
    }

    public void setParameter(String key, String value) { }

    public void setParameters(String keyValuePairs) { }

    public String getParameters(String keys) {
        return null;
    }

    public void setNavigationRepeatSoundEffectsEnabled(boolean enabled) { }

    public boolean areNavigationRepeatSoundEffectsEnabled() {
        return false;
    }

    public void setHomeSoundEffectEnabled(boolean enabled) { }

    public boolean isHomeSoundEffectEnabled() {
        return false;
    }

    public void playSoundEffect(int effectType) { }

    public void playSoundEffect(int effectType, int userId) { }

    public void playSoundEffect(int effectType, float volume) { }

    public void loadSoundEffects() { }

    public void unloadSoundEffects() { }

    public void registerAudioFocusRequest(AudioFocusRequest afr) { }

    public void unregisterAudioFocusRequest(AudioManager.OnAudioFocusChangeListener l) { }

    public int requestAudioFocus(AudioManager.OnAudioFocusChangeListener l, int streamType, int durationHint) {
        return 0;
    }

    public int requestAudioFocus(AudioFocusRequest focusRequest) {
        return 0;
    }

    public int abandonAudioFocusRequest(AudioFocusRequest focusRequest) {
        return 0;
    }

    public int requestAudioFocus(AudioManager.OnAudioFocusChangeListener l, AudioAttributes requestAttributes, int durationHint, int flags) throws IllegalArgumentException {
        return 0;
    }

    public int requestAudioFocus(AudioManager.OnAudioFocusChangeListener l, AudioAttributes requestAttributes, int durationHint, int flags, AudioPolicy ap) throws IllegalArgumentException {
        return 0;
    }

    public int requestAudioFocus(AudioFocusRequest afr, AudioPolicy ap) {
        return 0;
    }

    public void requestAudioFocusForCall(int streamType, int durationHint) { }

    public int getFocusRampTimeMs(int focusGain, AudioAttributes attr) {
        return 0;
    }

    public void setFocusRequestResult(AudioFocusInfo afi, int requestResult, AudioPolicy ap) { }

    public int dispatchAudioFocusChange(AudioFocusInfo afi, int focusChange, AudioPolicy ap) {
        return 0;
    }

    public void abandonAudioFocusForCall() { }

    public int abandonAudioFocus(AudioManager.OnAudioFocusChangeListener l) {
        return 0;
    }

    public int abandonAudioFocus(AudioManager.OnAudioFocusChangeListener l, AudioAttributes aa) {
        return 0;
    }

    public void registerMediaButtonEventReceiver(ComponentName eventReceiver) { }

    public void registerMediaButtonEventReceiver(PendingIntent eventReceiver) { }

    public void registerMediaButtonIntent(PendingIntent pi, ComponentName eventReceiver) { }

    public void unregisterMediaButtonEventReceiver(ComponentName eventReceiver) { }

    public void unregisterMediaButtonEventReceiver(PendingIntent eventReceiver) { }

    public void unregisterMediaButtonIntent(PendingIntent pi) { }

    public void registerRemoteControlClient(RemoteControlClient rcClient) { }

    public void unregisterRemoteControlClient(RemoteControlClient rcClient) { }

    public boolean registerRemoteController(RemoteController rctlr) {
        return false;
    }

    public void unregisterRemoteController(RemoteController rctlr) { }

    public int registerAudioPolicy(AudioPolicy policy) {
        return 0;
    }

    public void unregisterAudioPolicyAsync(AudioPolicy policy) { }

    public void unregisterAudioPolicy(AudioPolicy policy) { }

    public boolean hasRegisteredDynamicPolicy() {
        return false;
    }

    public void registerAudioPlaybackCallback(AudioManager.AudioPlaybackCallback cb,
            Handler handler) { }

    public void unregisterAudioPlaybackCallback(AudioManager.AudioPlaybackCallback cb) { }

    public List<AudioPlaybackConfiguration> getActivePlaybackConfigurations() {
        return null;
    }

    public void registerAudioRecordingCallback(AudioManager.AudioRecordingCallback cb,
            Handler handler) { }

    public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb) { }

    public List<AudioRecordingConfiguration> getActiveRecordingConfigurations() {
        return null;
    }

    public void reloadAudioSettings() { }

    public void avrcpSupportsAbsoluteVolume(String address, boolean support) { }

    public boolean isSilentMode() {
        return false;
    }

    public int getDevicesForStream(int streamType) {
        return 0;
    }

    public List<AudioDeviceAttributes> getDevicesForAttributes(AudioAttributes attributes) {
        return null;
    }

    public void setDeviceVolumeBehavior(AudioDeviceAttributes device, int deviceVolumeBehavior) { }

    public int getDeviceVolumeBehavior(AudioDeviceAttributes device) {
        return 0;
    }

    public void setWiredDeviceConnectionState(int type, int state, String address, String name) { }

    public void setBluetoothHearingAidDeviceConnectionState(BluetoothDevice device, int state,
            boolean suppressNoisyIntent, int musicDevice) { }

    public void setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(BluetoothDevice device,
            int state, int profile, boolean suppressNoisyIntent, int a2dpVolume) { }

    public void handleBluetoothA2dpDeviceConfigChange(BluetoothDevice device) { }

    public IRingtonePlayer getRingtonePlayer() {
        return null;
    }

    public String getProperty(String key) {
        return null;
    }

    public boolean setAdditionalOutputDeviceDelay(AudioDeviceInfo device, long delayMillis) {
        return false;
    }

    public long getAdditionalOutputDeviceDelay(AudioDeviceInfo device) {
        return 0;
    }

    public long getMaxAdditionalOutputDeviceDelay(AudioDeviceInfo device) {
        return 0;
    }

    public int getOutputLatency(int streamType) {
        return 0;
    }

    public void setVolumeController(IVolumeController controller) { }

    public void notifyVolumeControllerVisible(IVolumeController controller, boolean visible) { }

    public boolean isStreamAffectedByRingerMode(int streamType) {
        return false;
    }

    public boolean isStreamAffectedByMute(int streamType) {
        return false;
    }

    public void disableSafeMediaVolume() { }

    public void setRingerModeInternal(int ringerMode) { }

    public int getRingerModeInternal() {
        return 0;
    }

    public void setVolumePolicy(VolumePolicy policy) { }

    public int setHdmiSystemAudioSupported(boolean on) {
        return 0;
    }

    public boolean isHdmiSystemAudioSupported() {
        return false;
    }

    public void registerAudioPortUpdateListener(AudioManager.OnAudioPortUpdateListener l) { }

    public void unregisterAudioPortUpdateListener(AudioManager.OnAudioPortUpdateListener l) { }

    public AudioDeviceInfo[] getDevices(int flags) {
        return null;
    }

    public void registerAudioDeviceCallback(AudioDeviceCallback callback, Handler handler) { }

    public void unregisterAudioDeviceCallback(AudioDeviceCallback callback) { }

    public List<MicrophoneInfo> getMicrophones() throws IOException {
        return null;
    }

    public List<BluetoothCodecConfig> getHwOffloadEncodingFormatsSupportedForA2DP() {
        return null;
    }

    public void setAudioServerStateCallback(Executor executor,
            AudioManager.AudioServerStateCallback stateCallback) { }

    public void clearAudioServerStateCallback() { }

    public boolean isAudioServerRunning() {
        return false;
    }

    public Map<Integer, Boolean> getSurroundFormats() {
        return null;
    }

    public boolean setSurroundFormatEnabled(int audioFormat, boolean enabled) {
        return false;
    }

    public Map<Integer, Boolean> getReportedSurroundFormats() {
        return null;
    }

    public void registerVolumeGroupCallback(Executor executor,
            AudioManager.VolumeGroupCallback callback) { }

    public void unregisterVolumeGroupCallback(AudioManager.VolumeGroupCallback callback) { }

    public void adjustSuggestedStreamVolumeForUid(int suggestedStreamType, int direction,
            int flags, String packageName, int uid, int pid, int targetSdkVersion) { }

    public void adjustStreamVolumeForUid(int streamType, int direction, int flags,
            String packageName, int uid, int pid, int targetSdkVersion) { }

    public void setStreamVolumeForUid(int streamType, int index, int flags, String packageName,
            int uid, int pid, int targetSdkVersion) { }

    public void setMultiAudioFocusEnabled(boolean enabled) { }

    public int getAudioHwSyncForSession(int sessionId) {
        return 0;
    }

    public boolean setDeviceForCommunication(AudioDeviceInfo device) {
        return false;
    }

    public void clearDeviceForCommunication() { }

    public AudioDeviceInfo getDeviceForCommunication() {
        return null;
    }

    public void addOnCommunicationDeviceChangedListener(Executor executor, AudioManager.OnCommunicationDeviceChangedListener listener) { }

    public void removeOnCommunicationDeviceChangedListener(AudioManager.OnCommunicationDeviceChangedListener listener) { }

    public interface OnCommunicationDeviceChangedListener {
        void onCommunicationDeviceChanged(AudioDeviceInfo var1);
    }

    public abstract static class VolumeGroupCallback {
        public VolumeGroupCallback() {
        }

        public void onAudioVolumeGroupChanged(int group, int flags) {
        }
    }

    public abstract static class AudioServerStateCallback {
        public AudioServerStateCallback() {
        }

        public void onAudioServerDown() {
        }

        public void onAudioServerUp() {
        }
    }

    public interface OnAudioPortUpdateListener {
        void onAudioPortListUpdate(AudioPort[] var1);

        void onAudioPatchListUpdate(AudioPatch[] var1);

        void onServiceDied();
    }

    public abstract static class AudioRecordingCallback {
        public AudioRecordingCallback() {
        }

        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
        }
    }

    public abstract static class AudioPlaybackCallback {
        public AudioPlaybackCallback() {
        }

        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
        }
    }

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int var1);
    }

    public interface OnPreferredDevicesForCapturePresetChangedListener {
        void onPreferredDevicesForCapturePresetChanged(int var1, List<AudioDeviceAttributes> var2);
    }

    public interface OnPreferredDevicesForStrategyChangedListener {
        void onPreferredDevicesForStrategyChanged(AudioProductStrategy var1, List<AudioDeviceAttributes> var2);
    }

    public interface OnPreferredDeviceForStrategyChangedListener {
        void onPreferredDeviceForStrategyChanged(AudioProductStrategy var1, AudioDeviceAttributes var2);
    }
}
