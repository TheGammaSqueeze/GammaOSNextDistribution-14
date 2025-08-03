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

// The purpose of the OpenGL logger is to log
// information about such things as EGL initialization
// and possibly miscellanous OpenGL commands,
// in order to get a better idea of what is going on
// in crash reports.

// Flags for controlling fine logging and printing to stdout.
typedef enum {
    // Default logging settings
    // (coarse log active, sent to crash server, no fine logging)
    OPENGL_LOGGER_NONE = 0,

    // Log GL API calls and timestamps at fine grained level.
    OPENGL_LOGGER_DO_FINE_LOGGING = (1 << 0),

    // Print GL logs to stdout. For fine logs, do not print to storage if this flag is active.
    OPENGL_LOGGER_PRINT_TO_STDOUT = (1 << 1), 
} AndroidOpenglLoggerFlags;

// C interface for android-emugl
void android_init_opengl_logger();
void android_opengl_logger_set_flags(AndroidOpenglLoggerFlags flags);
void android_opengl_logger_write(const char* fmt, ...);
void android_stop_opengl_logger();

// This is for logging what goes on in individual OpenGL
// contexts (cxts). Only called when emugl is compiled
// with -DOPENGL_DEBUG_PRINTOUT.
void android_opengl_cxt_logger_write(const char* fmt, ...);
