// Copyright (C) 2023 The Android Open Source Project
// Copyright (C) 2023 BlackBerry Limited
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

#include <EGL/egl.h>
#include <GLES2/gl2.h>

// without this the build fails to properly link to the qnx EGL and GLESv2 libs:
//   SharedLibrary.cpp:266] SharedLibrary::open for [libGLESv2.so] failed (posix). dlerror:
//   [Unresolved symbols] EglOsApi_egl.cpp:208] EglOsGlLibrary: Could not open GL library
//   libGLESv2.so [Unresolved symbols]
void link_EGL_GLES2(void) {
    eglGetDisplay(EGL_DEFAULT_DISPLAY);
    glViewport(0, 0, 0, 0);
}
