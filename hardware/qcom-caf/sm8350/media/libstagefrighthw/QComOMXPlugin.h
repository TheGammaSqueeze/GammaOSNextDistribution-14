/*
 * Copyright (C) 2009 The Android Open Source Project
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

 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *
 *    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * Not a contribution.
 */

#ifndef QCOM_OMX_PLUGIN_H_

#define QCOM_OMX_PLUGIN_H_

#include <media/hardware/OMXPluginBase.h>

namespace android {

struct QComOMXPlugin : public OMXPluginBase {
    QComOMXPlugin();
    virtual ~QComOMXPlugin();

    virtual OMX_ERRORTYPE makeComponentInstance(
            const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component);

    virtual OMX_ERRORTYPE destroyComponentInstance(
            OMX_COMPONENTTYPE *component);

    virtual OMX_ERRORTYPE enumerateComponents(
            OMX_STRING name,
            size_t size,
            OMX_U32 index);

    virtual OMX_ERRORTYPE getRolesOfComponent(
            const char *name,
            Vector<String8> *roles);

private:
    void *mLibHandle;

    typedef OMX_ERRORTYPE (*InitFunc)();
    typedef OMX_ERRORTYPE (*DeinitFunc)();
    typedef OMX_ERRORTYPE (*ComponentNameEnumFunc)(
            OMX_STRING, OMX_U32, OMX_U32);

    typedef OMX_ERRORTYPE (*GetHandleFunc)(
            OMX_HANDLETYPE *, OMX_STRING, OMX_PTR, OMX_CALLBACKTYPE *);

    typedef OMX_ERRORTYPE (*FreeHandleFunc)(OMX_HANDLETYPE);

    typedef OMX_ERRORTYPE (*GetRolesOfComponentFunc)(
            OMX_STRING, OMX_U32 *, OMX_U8 **);

    void freeArrayOfAllocatedMemory(OMX_U8 **array, OMX_S32 count);

    InitFunc mInit;
    DeinitFunc mDeinit;
    ComponentNameEnumFunc mComponentNameEnum;
    GetHandleFunc mGetHandle;
    FreeHandleFunc mFreeHandle;
    GetRolesOfComponentFunc mGetRolesOfComponentHandle;

    QComOMXPlugin(const QComOMXPlugin &);
    QComOMXPlugin &operator=(const QComOMXPlugin &);
};

}  // namespace android

#endif  // QCOM_OMX_PLUGIN_H_
