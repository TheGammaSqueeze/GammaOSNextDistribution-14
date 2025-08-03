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
package com.googlecode.android_scripting.facade.telephony;

import static com.googlecode.android_scripting.facade.telephony.InCallServiceImpl.HandleVoiceThreadState.TERMINATE;
import static com.googlecode.android_scripting.facade.telephony.InCallServiceImpl.HandleVoiceThreadState.RUN;
import static android.os.VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY;
import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;

import com.googlecode.android_scripting.facade.EventFacade;
import com.googlecode.android_scripting.Log;
import android.media.AudioDeviceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.telecom.Call;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;

/**
 * The class handles playing an audio file on the route of the telephony network during a phone
 * call.
 */
public class PlayAudioInCall {
  private static Thread playAudioThread = null;
  private AudioDeviceInfo audioDeviceInfo;
  private File audioFile;
  private AudioFileInfo audioFileInfo;
  private Call call;
  private EventFacade eventFacade;

  PlayAudioInCall(EventFacade eventFacade,
      Call call,
      File audioFile,
      AudioDeviceInfo audioDeviceInfo) {
    this.eventFacade = eventFacade;
    this.call = call;
    this.audioFile = audioFile;
    this.audioDeviceInfo = audioDeviceInfo;
    Log.d(String.format("eventFacade=%s, call=%s, audioFile=%s, audioDeviceInfo=%d",
        this.eventFacade, this.call, this.audioFile, this.audioDeviceInfo.getId()));
  }

  boolean playAudioFile() {
    if (!setupAudioFileInfo())
      return false;
    return playAudio();
  }

  private boolean playAudio() {
    AudioFormat audioFormat = getAudioFormat();
    Log.d(String.format("Audio format: %s", audioFormat.toString()));
    AudioTrack audioTrack = getAudioTrack(audioFormat, getAudioTrackBufferSize(audioFormat));
    Log.d(String.format("Audio Track: %s",audioTrack.toString()));
    if (!audioTrack.setPreferredDevice(audioDeviceInfo)) {
      audioTrack.release();
      return false;
    }
    Log.d(String.format("Set the preferred audio device to %d successfully",
        audioDeviceInfo.getId()));
    while (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
      ;
    Log.d(String.format("Audio track state: %s", audioTrack.getState()));
    return createPlayAudioThread(audioTrack);
  }

  private boolean createPlayAudioThread(AudioTrack audioTrack) {
    playAudioThread = new Thread(() -> {
      byte[] audioRaw = new byte[512];
      int readBytes;
      try {
        InCallServiceImpl.setPlayAudioInCallState(RUN);
        InCallServiceImpl.muteCall(true);
        InputStream inputStream = new FileInputStream(audioFile);
        inputStream.read(audioRaw, 0, 44);
        audioTrack.play();
        while ((readBytes = inputStream.read(audioRaw)) != -1) {
          audioTrack.write(audioRaw, 0, readBytes);
          if (stopPlayAudio()) {
            break;
          }
        }
        Log.d("End Playing audio!");
        inputStream.close();
        audioTrack.stop();
        audioTrack.release();
        eventFacade.postEvent(TelephonyConstants.EventCallPlayAudioStateChanged,
            new InCallServiceImpl.CallEvent<String>(InCallServiceImpl.getCallId(call),
                TelephonyConstants.TELEPHONY_STATE_PLAY_AUDIO_END));
      }
      catch (IOException e) {
        audioTrack.release();
        Log.d(String.format("Failed to read audio file \"%s\"!", audioFile.getName()));
        eventFacade.postEvent(TelephonyConstants.EventCallPlayAudioStateChanged,
            new InCallServiceImpl.CallEvent<String>(InCallServiceImpl.getCallId(call),
                TelephonyConstants.TELEPHONY_STATE_PLAY_AUDIO_FAIL));
      }
      finally {
        InCallServiceImpl.muteCall(false);
        InCallServiceImpl.setPlayAudioInCallState(TERMINATE);
      }
    });
    playAudioThread.start();
    return true;
  }

  private AudioTrack getAudioTrack(AudioFormat audioFormat, int bufferSize) {
    return new AudioTrack.Builder().setAudioFormat(audioFormat).setBufferSizeInBytes(bufferSize)
        .setAudioAttributes(getAudioAttributes()).setTransferMode(AudioTrack.MODE_STREAM).build();
  }

  private int getAudioTrackBufferSize(AudioFormat audioFormat) {
    return AudioTrack.getMinBufferSize(
        audioFormat.getSampleRate(),
        audioFormat.getChannelMask(),
        audioFormat.getEncoding());
  }

  private AudioAttributes getAudioAttributes() {
    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
      Log.d("AudioAttributes above Android Q is used.");
      return new AudioAttributes.Builder()
          .setUsage(USAGE_VOICE_COMMUNICATION).build();
    } else {
      Log.d("AudioAttributes below Android Q is used.");
      return new AudioAttributes.Builder()
          .setContentType(CONTENT_TYPE_MUSIC)
          .setFlags(FLAG_BYPASS_INTERRUPTION_POLICY)
          .setUsage(USAGE_MEDIA).build();
    }
  }

  private boolean setupAudioFileInfo() {
    MediaExtractor extractor = new MediaExtractor();
    try {
      extractor.setDataSource(audioFile.getAbsolutePath());
    } catch (IOException e) {
      Log.d(String.format("Failed to set data source in MediaExtrator, %s", e.getMessage()));
      return false;
    }
    extractor.selectTrack(0);
    MediaFormat format = extractor.getTrackFormat(0);
    audioFileInfo = AudioFileInfo.create(format.getString(MediaFormat.KEY_MIME),
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
    Log.d(String.format("The media format is %s", audioFileInfo));
    return true;
  }

  private AudioFormat getAudioFormat() {
    int channelMask = audioFileInfo.channelCount() == 1 ? AudioFormat.CHANNEL_OUT_MONO
        : AudioFormat.CHANNEL_OUT_STEREO;
    return new AudioFormat.Builder().setChannelMask(channelMask)
        .setSampleRate(audioFileInfo.sampleRate()).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build();
  }

  private boolean stopPlayAudio() {
    if (InCallServiceImpl.getPlayAudioInCallState().equals(RUN)) {
      return false;
    }
    Log.d("Stop playing audio!");
    return true;
  }
}
