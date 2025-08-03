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
#include <cstring>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>
#include <chrono>

#define TAG "MidiTestManager"
#include <android/log.h>
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#include "MidiTestManager.h"

static pthread_t readThread;

static const bool DEBUG = true;
static const bool DEBUG_MIDIDATA = true;

static const int MAX_PACKET_SIZE = 1024;
static const int TIMEOUT_SECONDS = 5;

//
// MIDI Messages
//
// Channel Commands
static const uint8_t kMIDIChanCmd_KeyDown = 9;
static const uint8_t kMIDIChanCmd_KeyUp = 8;
static const uint8_t kMIDIChanCmd_PolyPress = 10;
static const uint8_t kMIDIChanCmd_Control = 11;
static const uint8_t kMIDIChanCmd_ProgramChange = 12;
static const uint8_t kMIDIChanCmd_ChannelPress = 13;
static const uint8_t kMIDIChanCmd_PitchWheel = 14;
// System Commands
static const uint8_t kMIDISysCmd_SysEx = 0xF0;
static const uint8_t kMIDISysCmd_EndOfSysEx =  0xF7;
static const uint8_t kMIDISysCmd_ActiveSensing = 0xFE;
static const uint8_t kMIDISysCmd_Reset = 0xFF;

static void* readThreadRoutine(void * context) {
    MidiTestManager* testManager = (MidiTestManager*)context;
    return reinterpret_cast<void*>(static_cast<intptr_t>(testManager->ProcessInput()));
}

/*
 * TestMessage
 */
#define makeMIDICmd(cmd, channel)  (uint8_t)((cmd << 4) | (channel & 0x0F))

uint8_t warmupMsg[] = {makeMIDICmd(kMIDIChanCmd_Control, 0), 0, 0};
uint8_t msg0[] = {makeMIDICmd(kMIDIChanCmd_KeyDown, 0), 64, 120};
uint8_t msg1[] = {makeMIDICmd(kMIDIChanCmd_KeyUp, 0), 64, 35};

class TestMessage {
public:
    uint8_t*   mMsgBytes;
    int     mNumMsgBytes;

    TestMessage()
        : mMsgBytes(NULL), mNumMsgBytes(0)
    {}

    ~TestMessage() {
        delete[] mMsgBytes;
    }

    bool set(uint8_t* msgBytes, int numMsgBytes) {
        if (msgBytes == NULL || numMsgBytes <= 0) {
            return false;
        }
        mNumMsgBytes = numMsgBytes;
        mMsgBytes = new uint8_t[numMsgBytes];
        memcpy(mMsgBytes, msgBytes, mNumMsgBytes * sizeof(uint8_t));
        return true;
    }

    bool setSysExMessage(int numMsgBytes) {
        if (numMsgBytes <= 0) {
            return false;
        }
        mNumMsgBytes = numMsgBytes;
        mMsgBytes = new uint8_t[mNumMsgBytes];
        memset(mMsgBytes, 0, mNumMsgBytes * sizeof(uint8_t));
        mMsgBytes[0] = kMIDISysCmd_SysEx;
        for(int index = 1; index < numMsgBytes - 1; index++) {
            mMsgBytes[index] = (uint8_t) (index % 100);
        }
        mMsgBytes[numMsgBytes - 1] = kMIDISysCmd_EndOfSysEx;
        return true;
    }

    bool setTwoSysExMessage(int firstMsgBytes, int secondMsgBytes) {
        if (firstMsgBytes <= 0 || secondMsgBytes <= 0) {
            return false;
        }
        mNumMsgBytes = firstMsgBytes + secondMsgBytes;
        mMsgBytes = new uint8_t[mNumMsgBytes];
        memset(mMsgBytes, 0, mNumMsgBytes * sizeof(uint8_t));
        mMsgBytes[0] = kMIDISysCmd_SysEx;
        for(int index = 1; index < firstMsgBytes - 1; index++) {
            mMsgBytes[index] = (uint8_t) (index % 100);
        }
        mMsgBytes[firstMsgBytes - 1] = kMIDISysCmd_EndOfSysEx;
        mMsgBytes[firstMsgBytes] = kMIDISysCmd_SysEx;
        for(int index = firstMsgBytes + 1; index < firstMsgBytes + secondMsgBytes - 1; index++) {
            mMsgBytes[index] = (uint8_t) (index % 100);
        }
        mMsgBytes[firstMsgBytes + secondMsgBytes - 1] = kMIDISysCmd_EndOfSysEx;
        return true;
    }
}; /* class TestMessage */

/*
 * MidiTestManager
 */
MidiTestManager::MidiTestManager()
    : mTestModuleObj(NULL),
      mReceiveStreamPos(0),
      mMidiSendPort(NULL), mMidiReceivePort(NULL),
      mTestMsgs(0), mNumTestMsgs(0),
      mThrottleData(false)
{}

MidiTestManager::~MidiTestManager() {
    mMatchStream.clear();
}

void MidiTestManager::jniSetup(JNIEnv* env) {
    env->GetJavaVM(&mJvm);

    jclass clsMidiTestModule =
        env->FindClass("com/android/cts/verifier/audio/MidiNativeTestActivity$NativeMidiTestModule");
    if (DEBUG) {
        ALOGI("gClsMidiTestModule:%p", clsMidiTestModule);
    }

    // public void endTest(int endCode)
    mMidEndTest = env->GetMethodID(clsMidiTestModule, "endTest", "(I)V");
    if (DEBUG) {
        ALOGI("mMidEndTestgMidEndTest:%p", mMidEndTest);
    }
}

bool MidiTestManager::setupMessages() {
    mNumTestMsgs = 7;
    mTestMsgs.resize(mNumTestMsgs);

    if (!mTestMsgs[0].set(msg0, sizeof(msg0)) ||
        !mTestMsgs[1].set(msg1, sizeof(msg1)) ||
        !mTestMsgs[2].setSysExMessage(30) ||
        !mTestMsgs[3].setSysExMessage(6) ||
        !mTestMsgs[4].setSysExMessage(120) ||
        !mTestMsgs[5].setTwoSysExMessage(5, 13) ||
        !mTestMsgs[6].setSysExMessage(340)) {
        return false;
    }
    return true;
}

void MidiTestManager::buildMatchStream() {
    mMatchStream.clear();
    for(int byteIndex = 0; byteIndex < sizeof(warmupMsg); byteIndex++) {
        mMatchStream.push_back(warmupMsg[byteIndex]);
    }
    for(int msgIndex = 0; msgIndex < mNumTestMsgs; msgIndex++) {
        for(int byteIndex = 0; byteIndex < mTestMsgs[msgIndex].mNumMsgBytes; byteIndex++) {
            mMatchStream.push_back(mTestMsgs[msgIndex].mMsgBytes[byteIndex]);
        }
    }

    // Reset stream position
    mReceiveStreamPos = 0;
}

static void logBytes(uint8_t* bytes, int count) {
    int buffSize = (count * 6) + 1; // count of "0x??, " + '\0';

    char* logBuff = new char[buffSize];
    for (int dataIndex = 0; dataIndex < count; dataIndex++) {
        sprintf(logBuff + (dataIndex * 6), "0x%.2X", bytes[dataIndex]);
        if (dataIndex < count - 1) {
            sprintf(logBuff + (dataIndex * 6) + 4, ", ");
        }
    }
    ALOGD("logbytes(%d): %s", count, logBuff);
    delete[] logBuff;
}

/**
 * Compares the supplied bytes against the sent message stream at the current position
 * and advances the stream position.
 *
 * Returns the number of matched bytes on success and -1 on failure.
 *
 */
int MidiTestManager::matchStream(uint8_t* bytes, int count) {
    if (DEBUG) {
        ALOGI("---- matchStream() count:%d", count);
    }

    int matchedByteCount = 0;

    // a little bit of checking here...
    if (count < 0) {
        ALOGE("Negative Byte Count in MidiTestManager::matchStream()");
        return -1;
    }

    if (count > MESSAGE_MAX_BYTES) {
        ALOGE("Too Large Byte Count (%d) in MidiTestManager::matchStream()", count);
        return -1;
    }

    bool matches = true;
    for (int index = 0; index < count; index++) {
        // Check for buffer overflow
        if (mReceiveStreamPos >= mMatchStream.size()) {
            ALOGD("matchStream() out-of-bounds @%d", mReceiveStreamPos);
            matches = false;
            break;
        }

        if (bytes[index] == kMIDISysCmd_ActiveSensing) {
            if (bytes[index] == mMatchStream[mReceiveStreamPos]) {
                ALOGD("matched active sensing message");
                matchedByteCount++;
                mReceiveStreamPos++;
            } else {
                ALOGD("skipping active sensing message");
            }
        } else {
            // Check first byte for warm-up message
            if ((mReceiveStreamPos == 0) && bytes[index] != makeMIDICmd(kMIDIChanCmd_Control, 0)) {
                ALOGD("skipping warm-up message");
                matchedByteCount += sizeof(warmupMsg);
                mReceiveStreamPos += sizeof(warmupMsg);
            }

            if (bytes[index] != mMatchStream[mReceiveStreamPos]) {
                matches = false;
                ALOGD("---- mismatch @%d [rec:0x%X : exp:0x%X]",
                        index, bytes[index], mMatchStream[mReceiveStreamPos]);
            } else {
                matchedByteCount++;
                mReceiveStreamPos++;
            }
        }
    }

    if (DEBUG) {
        ALOGI("  success:%d", matches);
    }

    if (!matches) {
        ALOGD("Mismatched Received Data:");
        logBytes(bytes, count);
        return -1;
    }

    return matchedByteCount;
}

#define THROTTLE_PERIOD_MS 20
#define THROTTLE_MAX_PACKET_SIZE 15

/**
 * Send a number of bytes.
 *
 * Returns the number of sent bytes on success or negative error code on failure.
 *
 */
int portSend(AMidiInputPort* sendPort, uint8_t* msg, int length, bool throttle) {

    int numSent = 0;
    if (throttle) {
        for(int index = 0; index < length; index += THROTTLE_MAX_PACKET_SIZE) {
            int packetSize = std::min(length - index, THROTTLE_MAX_PACKET_SIZE);
            int curSent = AMidiInputPort_send(sendPort, msg + index, packetSize);
            if (curSent < 0) {
                return curSent;
            }
            numSent += curSent;
            usleep(THROTTLE_PERIOD_MS * 1000);
        }
    } else {
        numSent = AMidiInputPort_send(sendPort, msg, length);
    }
    return numSent;
}

/**
 * Writes out the list of MIDI messages to the output port.
 * Returns total number of bytes sent or negative error code.
 */
int MidiTestManager::sendMessages() {
    if (DEBUG) {
        ALOGI("---- sendMessages()...");
        for(int msgIndex = 0; msgIndex < mNumTestMsgs; msgIndex++) {
            if (DEBUG_MIDIDATA) {
                ALOGI("--------");
                for(int byteIndex = 0; byteIndex < mTestMsgs[msgIndex].mNumMsgBytes; byteIndex++) {
                    ALOGI("  0x%X", mTestMsgs[msgIndex].mMsgBytes[byteIndex]);
                }
            }
        }
    }

    // Send "Warm-up" message
    portSend(mMidiSendPort, warmupMsg, sizeof(warmupMsg), mThrottleData);

    int totalSent = 0;
    for(int msgIndex = 0; msgIndex < mNumTestMsgs; msgIndex++) {
        ssize_t numSent =
            portSend(mMidiSendPort, mTestMsgs[msgIndex].mMsgBytes, mTestMsgs[msgIndex].mNumMsgBytes,
                mThrottleData);
        if (numSent < 0) {
            ALOGE("sendMessages(): Send failed. index: %d, error: %zd", msgIndex, numSent);
            return numSent;
        }
        totalSent += numSent;
    }

    if (DEBUG) {
        ALOGI("---- totalSent:%d", totalSent);
    }

    return totalSent;
}

int MidiTestManager::ProcessInput() {
    uint8_t readBuffer[MAX_PACKET_SIZE];
    size_t totalNumReceived = 0;

    int testResult = TESTSTATUS_NOTRUN;

    int32_t opCode;
    size_t numBytesReceived;
    int64_t timeStamp;

    auto startTime = std::chrono::system_clock::now();

    while (true) {
        // AMidiOutputPort_receive is non-blocking, so let's not burn up the CPU unnecessarily
        usleep(2000);

        numBytesReceived = 0;
        ssize_t numMessagesReceived =
            AMidiOutputPort_receive(mMidiReceivePort, &opCode, readBuffer, MAX_PACKET_SIZE,
                        &numBytesReceived, &timeStamp);

        if (DEBUG && numBytesReceived > 0) {
            logBytes(readBuffer, numBytesReceived);
        }

        if (numBytesReceived > 0 &&
            opCode == AMIDI_OPCODE_DATA &&
            readBuffer[0] != kMIDISysCmd_Reset) {
            if (DEBUG) {
                ALOGI("---- msgs:%zd, bytes:%zu", numMessagesReceived, numBytesReceived);
            }

            int matchResult = matchStream(readBuffer, numBytesReceived);
            if (matchResult < 0) {
                testResult = TESTSTATUS_FAILED_MISMATCH;
                if (DEBUG) {
                    ALOGE("---- TESTSTATUS_FAILED_MISMATCH");
                }
                return testResult;
            }
            totalNumReceived += matchResult;

            if (totalNumReceived > mMatchStream.size()) {
                testResult = TESTSTATUS_FAILED_OVERRUN;
                if (DEBUG) {
                    ALOGE("---- TESTSTATUS_FAILED_OVERRUN");
                }
                return testResult;
            }
            if (totalNumReceived == mMatchStream.size()) {
                testResult = TESTSTATUS_PASSED;
                if (DEBUG) {
                    ALOGE("---- TESTSTATUS_PASSED");
                }
                return testResult;
            }
        }

        auto currentTime = std::chrono::system_clock::now();
        std::chrono::duration<double> elapsedSeconds = currentTime - startTime;
        if (elapsedSeconds.count() > TIMEOUT_SECONDS) {
            testResult = TESTSTATUS_FAILED_TIMEOUT;
            if (DEBUG) {
                ALOGE("---- TESTSTATUS_FAILED_TIMEOUT");
            }
            return testResult;
        }
    }

    return testResult;
}

bool MidiTestManager::StartReading(AMidiDevice* nativeReadDevice) {
    if (DEBUG) {
        ALOGI("StartReading()...");
    }

    media_status_t m_status =
        AMidiOutputPort_open(nativeReadDevice, 0, &mMidiReceivePort);
    if (m_status != 0) {
        ALOGE("Can't open MIDI device for reading err:%d", m_status);
        return false;
    }

    // Start read thread
    int status = pthread_create(&readThread, NULL, readThreadRoutine, this);
    if (status != 0) {
        ALOGE("Can't start readThread: %s (%d)", strerror(status), status);
    }
    return status == 0;
}

bool MidiTestManager::StartWriting(AMidiDevice* nativeWriteDevice) {
    ALOGI("StartWriting()...");

    media_status_t status =
        AMidiInputPort_open(nativeWriteDevice, 0, &mMidiSendPort);
    if (status != 0) {
        ALOGE("Can't open MIDI device for writing err:%d", status);
        return false;
    }
    return true;
}

bool MidiTestManager::RunTest(jobject testModuleObj, AMidiDevice* sendDevice,
        AMidiDevice* receiveDevice, bool throttleData) {
    if (DEBUG) {
        ALOGI("RunTest(%p, %p, %p)", testModuleObj, sendDevice, receiveDevice);
    }

    mThrottleData = throttleData;

    JNIEnv* env;
    mJvm->AttachCurrentThread(&env, NULL);
    if (env == NULL) {
        EndTest(TESTSTATUS_FAILED_JNI);
        return false; // bail
    }

    if (!setupMessages()) {
        EndTest(TESTSTATUS_FAILED_SETUP);
        return false; // bail
    }
    buildMatchStream();

    mTestModuleObj = env->NewGlobalRef(testModuleObj);

    // Call StartWriting first because StartReading starts a thread.
    if (!StartWriting(sendDevice) || !StartReading(receiveDevice)) {
        // Test call to EndTest will close any open devices.
        EndTest(TESTSTATUS_FAILED_DEVICE);
        return false; // bail
    }

    int bytesSent = sendMessages();
    void* threadRetval = (void*)TESTSTATUS_NOTRUN;
    int status = pthread_join(readThread, &threadRetval);

    // If send fails, the test should still wait for the receiver port to timeout to flush buffers.
    if (bytesSent < 0) {
        EndTest(TESTSTATUS_FAILED_SEND);
        return false;
    }

    if (status != 0) {
        ALOGE("Failed to join readThread: %s (%d)", strerror(status), status);
    }
    EndTest(static_cast<int>(reinterpret_cast<intptr_t>(threadRetval)));
    return true;
}

void MidiTestManager::EndTest(int endCode) {

    JNIEnv* env;
    mJvm->AttachCurrentThread(&env, NULL);
    if (env == NULL) {
        ALOGE("Error retrieving JNI Env");
    }

    env->CallVoidMethod(mTestModuleObj, mMidEndTest, endCode);
    env->DeleteGlobalRef(mTestModuleObj);

    // EndTest() will ALWAYS be called, so we can close the ports here.
    if (mMidiSendPort != NULL) {
        AMidiInputPort_close(mMidiSendPort);
        mMidiSendPort = NULL;
    }
    if (mMidiReceivePort != NULL) {
        AMidiOutputPort_close(mMidiReceivePort);
        mMidiReceivePort = NULL;
    }
}
