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

#include "aemu/base/c_header.h"

#include <stdbool.h>
#include <stdint.h>
// #include "android/settings-agent.h"
// #include "android/skin/rect.h"
// #include "android/utils/compiler.h"

#ifndef USING_ANDROID_BP
ANDROID_BEGIN_HEADER
#endif

// Window agent's possible message types
typedef enum {
    WINDOW_MESSAGE_GENERIC,
    WINDOW_MESSAGE_INFO,
    WINDOW_MESSAGE_WARNING,
    WINDOW_MESSAGE_ERROR,
    WINDOW_MESSAGE_OK,
} WindowMessageType;

typedef struct {} MultiDisplayPageChangeEvent;
typedef struct SkinLayout SkinLayout;
typedef struct QFrame QFrame;
typedef struct SkinEvent SKinEvent;

static const int kWindowMessageTimeoutInfinite = -1;

typedef struct EmulatorWindow EmulatorWindow;

typedef void (*UiUpdateFunc)(void* data);

typedef struct QAndroidEmulatorWindowAgent {
    // Get a pointer to the emulator window structure.
    EmulatorWindow* (*getEmulatorWindow)(void);

    // Rotate the screen clockwise by 90 degrees.
    // Returns true on success, false otherwise.
    bool (*rotate90Clockwise)(void);

    // Rotate to specific |rotation|
    bool (*rotate)(int skinRotation);

    // Returns the current rotation.
    int (*getRotation)(void);

    // Shows a message to the user.
    void (*showMessage)(const char* message,
                        WindowMessageType type,
                        int timeoutMs);

    // Shows a message to the user + custom dismiss op.
    void (*showMessageWithDismissCallback)(const char* message,
                                           WindowMessageType type,
                                           const char* dismissText,
                                           void* context,
                                           void (*func)(void*),
                                           int timeoutMs);
    // Fold/Unfold device
    bool (*fold)(bool is_fold);
    // Query folded state
    bool (*isFolded)(void);
    bool (*getFoldedArea)(int* x, int* y, int* w, int* h);

    // Update UI indicator which shows which foldable posture device is in
    void (*updateFoldablePostureIndicator)(bool confirmFoldedArea);
    // Set foldable device posture
    bool (*setPosture)(int posture);

    // Set the UI display region
    void (*setUIDisplayRegion)(int, int, int, int, bool);
    bool (*getMultiDisplay)(uint32_t,
                            int32_t*,
                            int32_t*,
                            uint32_t*,
                            uint32_t*,
                            uint32_t*,
                            uint32_t*,
                            bool*);
    void (*setNoSkin)(void);
    void (*restoreSkin)(void);
    void (*updateUIMultiDisplayPage)(uint32_t);
    bool (*addMultiDisplayWindow)(uint32_t, bool, uint32_t, uint32_t);
    bool (*paintMultiDisplayWindow)(uint32_t, uint32_t);
    bool (*getMonitorRect)(uint32_t*, uint32_t*);
    // moves the extended window to the given position if the window was never displayed. This does nothing
    // if the window has been show once during the lifetime of the avd.
    void (*moveExtendedWindow)(uint32_t x, uint32_t y, int horizonalAnchor, int verticalAnchor);
    // start extended window and switch to the pane specified by the index.
    // return true if extended controls window's visibility has changed.
    // The window is not necessarily visible when this method returns.
    bool (*startExtendedWindow)(int index);

    // Closes the extended window. At some point in time it will be gone.
    bool (*quitExtendedWindow)(void);

    // This will wait until the state of the visibility of the window has
    // changed to the given value. Calling show or close does not make
    // a qt frame immediately visible. Instead a series of events will be
    // fired when the frame is actually added to, or removed from the display.
    // so usually the pattern is showWindow, wait for visibility..
    //
    // Be careful to:
    //   - Not run this on a looper thread. The ui controls can post actions
    //     to the looper thread which can result in a deadlock
    //   - Not to run this on the Qt Message pump. You will deadlock.
    void (*waitForExtendedWindowVisibility)(bool);
    bool (*setUiTheme)(int type);
    void (*runOnUiThread)(UiUpdateFunc f, void* data, bool wait);
    bool (*isRunningInUiThread)(void);
    bool (*changeResizableDisplay)(int presetSize);
    void* (*getLayout)(void);
    bool (*resizableEnabled)(void);
    void (*show_virtual_scene_controls)(bool);
    void (*quit_request)(void);
    void (*getWindowPosition)(int*, int*);
    bool (*hasWindow)();

    bool (*userSettingIsDontSaveSnapshot)(void);
    void (*setUserSettingIsDontSaveSnapshot)(bool);
} QAndroidEmulatorWindowAgent;

#ifndef USING_ANDROID_BP
ANDROID_END_HEADER
#endif
