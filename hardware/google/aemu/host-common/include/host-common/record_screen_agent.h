// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "screen-recorder.h"

#include <stdbool.h>

typedef struct QAndroidRecordScreenAgent {
    // Start recording. Returns false if already recording.
    // |recordingInfo| is the recording information the encoder should use. At
    // the minimum, the filename cannot be null. For the other parameters, if
    // the value is invalid, default values will be used in place of them.
    bool (*startRecording)(const RecordingInfo* recordingInfo);
    // Async version of startRecording(). Use |recordingInfo->cb| to get the
    // recording state.
    bool (*startRecordingAsync)(const RecordingInfo* recordingInfo);

    // Stop recording.
    bool (*stopRecording)(void);
    // Async version of stopRecording(). Use |recordingInfo->cb| to get the
    // recording state.
    bool (*stopRecordingAsync)(void);
    // Get the state of the recorder.
    RecorderStates (*getRecorderState)(void);
    // Take a screenshot.
    bool (*doSnap)(const char* dirname, uint32_t displayId);

    // Setup a shared memory region. The framerate should ideally be fps
    // Returns the name of the memory handle, or null if initialization failed.
    const char* (*startSharedMemoryModule)(int desiredFps);

    bool (*stopSharedMemoryModule)();
} QAndroidRecordScreenAgent;
