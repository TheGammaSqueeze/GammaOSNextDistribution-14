/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <amidi/AMidi.h>

#include <vector>

#include <jni.h>

class TestMessage;

class MidiTestManager {
public:
    MidiTestManager();
    ~MidiTestManager();

    void jniSetup(JNIEnv* env);

    bool RunTest(jobject testModuleObj, AMidiDevice* sendDevice, AMidiDevice* receiveDevice,
                    bool throttleData);
    void EndTest(int testCode);

    // Called by the thread routine.
    int ProcessInput();

private:
    void buildMatchStream();
    int matchStream(uint8_t* bytes, int count);

    bool setupMessages();
    int sendMessages();

    jobject mTestModuleObj;

    // build the stream for matching.
    std::vector<uint8_t>    mMatchStream;

    int     mReceiveStreamPos;
    static const int MESSAGE_MAX_BYTES = 1024;

    AMidiInputPort* mMidiSendPort;
    AMidiOutputPort* mMidiReceivePort;

    // The array of messages to send/receive
    std::vector<TestMessage>    mTestMsgs;
    int             mNumTestMsgs;

    bool            mThrottleData;

    // JNI
    JavaVM* mJvm;
    jmethodID mMidEndTest;

    // Test result codes
    static const int TESTSTATUS_NOTRUN = 0;
    static const int TESTSTATUS_PASSED = 1;
    static const int TESTSTATUS_FAILED_MISMATCH = 2;
    static const int TESTSTATUS_FAILED_TIMEOUT = 3;
    static const int TESTSTATUS_FAILED_OVERRUN = 4;
    static const int TESTSTATUS_FAILED_DEVICE = 5;
    static const int TESTSTATUS_FAILED_JNI = 6;
    static const int TESTSTATUS_FAILED_SETUP = 7;
    static const int TESTSTATUS_FAILED_SEND = 8;

    bool StartReading(AMidiDevice* nativeReadDevice);
    bool StartWriting(AMidiDevice* nativeWriteDevice);
};
