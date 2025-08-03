/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef MEGA_PLAYER_OBOEPLAYER_H
#define MEGA_PLAYER_OBOEPLAYER_H

#include <jni.h>

#include <oboe/Oboe.h>

#include "Player.h"

class OboePlayer : public Player, oboe::AudioStreamCallback {
public:
    OboePlayer(JNIEnv *env, AudioSource* source, int playerSubtype);
    virtual ~OboePlayer() {}

    // Inherited from oboe::AudioStreamCallback
    virtual oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData,
                                                    int32_t numFrames) override;
    virtual void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    virtual void onErrorBeforeClose(oboe::AudioStream * oboeStream, oboe::Result error) override;

    Result setupStream(int32_t channelCount, int32_t sampleRate,
        int32_t performanceMode, int32_t sharingMode, int32_t routeDeviceId);
    virtual Result startStream() override;

    bool getJavaTimestamp(jobject timestampObj);

    int getLastErrorCallbackResult();

    void setMMap(bool isMMap);
    bool isMMap();

private:
    // AudioTimestamp Field IDs
    JavaVM* mJvm;

    jfieldID    mFidFramePosition;
    jfieldID    mFidNanoTime;
};

#endif // MEGA_PLAYER_OBOEPLAYER_H
