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
package com.googlecode.android_scripting.facade.telephony;

import static com.googlecode.android_scripting.facade.telephony.InCallServiceImpl.HandleVoiceThreadState.RUN;
import static com.googlecode.android_scripting.facade.telephony.InCallServiceImpl.HandleVoiceThreadState.TERMINATE;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.facade.EventFacade;
import android.telecom.Call;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import com.googlecode.android_scripting.facade.telephony.InCallServiceImpl.HandleVoiceThreadState;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * The class handles recording voice on the route of the telephony network during a phone
 * call.
 */public class RecordVoiceInCall {
  public static final int SAMPLE_RATE_16K = 16000;
  public static final int SAMPLE_RATE_48K = 48000;
  public static final int MONO_CHANNEL = 1;
  public static final int STEREO_CHANNEL = 2;
  private static final int PCM_16_BITS = 16;
  private EventFacade eventFacade;
  private Call call;
  private int sampleRate;
  private int channelCount;
  private File recordFile;
  private AudioFileInfo audioFileInfo;
  private AudioFormat audioFormat;
  private NoiseSuppressor noiseSuppressor;
  private AcousticEchoCanceler acousticEchoCanceler;
  private int minRecordAudioBufferSize;
  private boolean cancelNoiseEcho;
  private static Thread recordVoiceThread = null;

  RecordVoiceInCall(EventFacade eventFacade,
      Call call,
      File recordFile,
      int sampleRate,
      int channelCount,
      boolean cancelNoiseEcho) {
    this.eventFacade = eventFacade;
    this.call = call;
    this.recordFile = recordFile;
    this.sampleRate = sampleRate;
    this.channelCount = channelCount;
    this.cancelNoiseEcho = cancelNoiseEcho;
    Log.d(String.format("eventFacade=%s, call=%s, recordFile=%s, sampleRate=%d, channelCount=%d",
        this.eventFacade, this.call, this.recordFile, this.sampleRate, this.channelCount));
  }

  /**Handles functional flows of voice recording and exposes to be invoked by users.*/
  boolean recordVoice() {
    setRecordAudioInfo();
    setAudioFormat();
    setMinRecordAudioBufferSize();
    AudioRecord audioRecord = new AudioRecord.Builder()
        .setAudioSource(AudioSource.VOICE_DOWNLINK)
        .setAudioFormat(audioFormat)
        .setBufferSizeInBytes(minRecordAudioBufferSize)
        .build();
    if (cancelNoiseEcho) {
      enableNoiseSuppressor(audioRecord);
      enableAcousticEchoCanceler(audioRecord);
    }
    return createRecordVoiceThread(audioRecord);
  }

  private void setRecordAudioInfo() {
    audioFileInfo = AudioFileInfo.create("WAVE", sampleRate, channelCount);
  }

  /**Configures an object of {@code AudioFormat} needed for voice recording in a call.
   * The minimal info is to provide channel count and voice encoding scheme.
   */
  private void setAudioFormat() {
    int channelMask = audioFileInfo.channelCount() == 1 ? AudioFormat.CHANNEL_IN_MONO
        : AudioFormat.CHANNEL_IN_STEREO;
    audioFormat = new AudioFormat.Builder().setChannelMask(channelMask)
        .setSampleRate(audioFileInfo.sampleRate()).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build();
    Log.d(String.format(
        "Audio format for recording, channel mask: %d, sample rate: %d, encoding: PCM_16bit",
        channelMask, sampleRate));
  }

  /**Acquires minimal buffer size for sampling voice data.*/
  private void setMinRecordAudioBufferSize() {
    minRecordAudioBufferSize = AudioRecord.getMinBufferSize(audioFormat.getSampleRate(),
        audioFormat.getChannelCount(),
        audioFormat.getEncoding());
    Log.d(String.format("Buffer size to record voice data: %d", minRecordAudioBufferSize));
  }

  private void enableNoiseSuppressor(AudioRecord audioRecord) {
    if (NoiseSuppressor.isAvailable()) {
      noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
      if (noiseSuppressor.setEnabled(true) != AudioEffect.SUCCESS) {
        Log.w("Failed to enable noiseSuppressor!");
        return;
      }
      Log.d("Enable noiseSuppressor!");
      return;
    }
    Log.d("Noise suppressor is not available!");
  }

  private void enableAcousticEchoCanceler(AudioRecord audioRecord) {
    if (AcousticEchoCanceler.isAvailable()) {
      acousticEchoCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
      if (acousticEchoCanceler.setEnabled(true) != AudioEffect.SUCCESS) {
        Log.w("Failed to enable AcousticEchoCanceler");
        return;
      }
      Log.d("Enable AcousticEchoCanceler!");
      return;
    }
    Log.d("AcousticEchoCanceler is not available!");
  }

  /**Creates a background thread to perform voice recording.*/
  private boolean createRecordVoiceThread(AudioRecord audioRecord) {
    recordVoiceThread = new Thread(()-> {
      int totalVoiceBytes = 0;
      try {
        InCallServiceImpl.setRecordVoiceInCallState(RUN);
        InCallServiceImpl.muteCall(true);
        audioRecord.startRecording();
        FileOutputStream outputStream = new FileOutputStream(recordFile);
        DataOutputStream recordVoiceOutStream = new DataOutputStream(outputStream);

        writeWaveHeader(recordVoiceOutStream, totalVoiceBytes);
        short[] buffer = new short[minRecordAudioBufferSize];

        while (InCallServiceImpl.getRecordVoiceInCallState().equals(RUN)) {
          int shortsRead = audioRecord.read(buffer, 0, minRecordAudioBufferSize);
          totalVoiceBytes += shortsRead * 2;
          for (int count = 0; count < shortsRead; count++) {
            recordVoiceOutStream.writeShort(getShortByOrder(buffer[count], LITTLE_ENDIAN));
          }
        }
        recordVoiceOutStream.flush();
        recordVoiceOutStream.close();
        correctWaveHeaderChunkSize(totalVoiceBytes);
        Log.d("End recording voice!");
        eventFacade.postEvent(TelephonyConstants.EventCallRecordVoiceStateChanged,
            new InCallServiceImpl.CallEvent<String>(InCallServiceImpl.getCallId(call),
                TelephonyConstants.TELEPHONY_STATE_RECORD_VOICE_END));

      } catch (IOException e) {
        Log.d(String.format("Failed to record voice to \"%s\"!", recordFile.getName()));
        eventFacade.postEvent(TelephonyConstants.EventCallPlayAudioStateChanged,
            new InCallServiceImpl.CallEvent<String>(InCallServiceImpl.getCallId(call),
                TelephonyConstants.TELEPHONY_STATE_PLAY_AUDIO_FAIL));

      } finally {
        InCallServiceImpl.muteCall(false);
        releaseRecordSources(audioRecord);
      }
    });
    recordVoiceThread.start();
    return true;
  }

  private void releaseRecordSources(AudioRecord audioRecord) {
    audioRecord.stop();
    audioRecord.release();
    if (noiseSuppressor != null) {
      noiseSuppressor.release();
    }
    if (acousticEchoCanceler != null) {
      acousticEchoCanceler.release();
    }
    InCallServiceImpl.setRecordVoiceInCallState(TERMINATE);
  }
  /**Creates and writes wave header to the beginning of the wave file.*/
  private void writeWaveHeader(DataOutputStream dataOutputStream, int totalBytes)
      throws IOException {
    /* 1-4 */
    dataOutputStream.write("RIFF".getBytes(StandardCharsets.UTF_8));
    /* 5-8 Write Chunk Size ~= the byte size of voice data + 36 */
    dataOutputStream.writeInt(getIntByOrder(totalBytes + 36, LITTLE_ENDIAN));
    /* 9-12 */
    dataOutputStream.write("WAVE".getBytes(StandardCharsets.UTF_8));
    /* 13-16 */
    dataOutputStream.write("fmt ".getBytes(StandardCharsets.UTF_8));
    /* 17-20 Writes SubChunk1Size */
    dataOutputStream.writeInt(getIntByOrder(16, LITTLE_ENDIAN));
    /* 21-22 Writes audio format, PCM: 1 */
    dataOutputStream.writeShort(getShortByOrder((short) 1, LITTLE_ENDIAN));
    /* 23-24 Writes number of channels */
    dataOutputStream.writeShort(
        getShortByOrder((short) audioFileInfo.channelCount(), LITTLE_ENDIAN));
    /* 25-28 Writes sampling rate */
    dataOutputStream.writeInt(
        getIntByOrder(audioFileInfo.sampleRate(), LITTLE_ENDIAN));
    /* 29-32 Writes byte rate */
    int byteRate = audioFileInfo.sampleRate() * audioFileInfo.channelCount() * PCM_16_BITS/2;
    dataOutputStream.writeInt(
        getIntByOrder(byteRate, LITTLE_ENDIAN));
    /* 33-34 Writes block align */
    int blockAlign = audioFileInfo.channelCount() * PCM_16_BITS / 2;
    dataOutputStream.writeShort(
        getShortByOrder((short) blockAlign, LITTLE_ENDIAN));
    /* 35-36 Writes PCM bits */
    dataOutputStream.writeShort(
        getShortByOrder((short) PCM_16_BITS, LITTLE_ENDIAN));
    /* 37-40 */
    dataOutputStream.write("data".getBytes(StandardCharsets.UTF_8));
    /* 41-44 SubChunk2Size ~= the byte size of voice data */
    dataOutputStream.writeInt(
        getIntByOrder(totalBytes, LITTLE_ENDIAN));
  }

  private void correctWaveHeaderChunkSize(int totalVoiceBytes) throws IOException {
    RandomAccessFile randomVoiceAccessFile = new RandomAccessFile(recordFile, "rw");
    writeWaveHeaderChunkSize(randomVoiceAccessFile, totalVoiceBytes);
    writeWaveHeaderSubChunk2Size(randomVoiceAccessFile, totalVoiceBytes);
    randomVoiceAccessFile.close();
  }

  /**A wav file consists of two chunks. The first chunk is the header data which describes
   * the sample rate, channel count, the size of voice data and so on. See {@code #writeWaveHeader}.
   */
  private void writeWaveHeaderChunkSize(RandomAccessFile randomAccessFile, int totalVoiceBytes)
      throws IOException {
    randomAccessFile.seek(4);
    randomAccessFile.write(intToBytes(totalVoiceBytes + 36, LITTLE_ENDIAN), 0, 4);
  }

  /**A wav file consists of two chunks. The second chunk is the voice data.*/
  private void writeWaveHeaderSubChunk2Size(RandomAccessFile randomAccessFile, int totalVoiceBytes)
      throws IOException {
    randomAccessFile.seek(40);
    randomAccessFile.write(intToBytes(totalVoiceBytes, LITTLE_ENDIAN), 0, 4);
  }

  /**A short type of integer can be represented with little endian or big endian.*/
  private short getShortByOrder(short value, ByteOrder byteOrder) {
    byte[] bytes = ByteBuffer.allocate(2).putShort(value).array();
    return ByteBuffer.wrap(bytes).order(byteOrder).getShort();
  }

  /**Converts integer to byte array.*/
  private byte[] intToBytes(int value, ByteOrder byteOrder) {
    return ByteBuffer.allocate(4).order(byteOrder).putInt(value).array();
  }

  /**An integer type of integer can be represented with little endian or big endian.*/
  private int getIntByOrder(int value, ByteOrder byteOrder) {
    byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
    return ByteBuffer.wrap(bytes).order(byteOrder).getInt();
  }
}
