/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

#include "QComOMXPlugin.h"

#include <dlfcn.h>

#include <media/hardware/HardwareAPI.h>

namespace android {

OMXPluginBase *createOMXPlugin() {
    return new QComOMXPlugin;
}

QComOMXPlugin::QComOMXPlugin()
    : mLibHandle(dlopen("libOmxCore.so", RTLD_NOW)),
      mInit(NULL),
      mDeinit(NULL),
      mComponentNameEnum(NULL),
      mGetHandle(NULL),
      mFreeHandle(NULL),
      mGetRolesOfComponentHandle(NULL) {
    if (mLibHandle != NULL) {
        mInit = (InitFunc)dlsym(mLibHandle, "OMX_Init");
        mDeinit = (DeinitFunc)dlsym(mLibHandle, "OMX_Deinit");

        mComponentNameEnum =
            (ComponentNameEnumFunc)dlsym(mLibHandle, "OMX_ComponentNameEnum");

        mGetHandle = (GetHandleFunc)dlsym(mLibHandle, "OMX_GetHandle");
        mFreeHandle = (FreeHandleFunc)dlsym(mLibHandle, "OMX_FreeHandle");

        mGetRolesOfComponentHandle =
            (GetRolesOfComponentFunc)dlsym(
                    mLibHandle, "OMX_GetRolesOfComponent");

        if (!mInit || !mDeinit || !mComponentNameEnum || !mGetHandle ||
            !mFreeHandle || !mGetRolesOfComponentHandle) {
            dlclose(mLibHandle);
            mLibHandle = NULL;
        } else
            (*mInit)();
    }
}

QComOMXPlugin::~QComOMXPlugin() {
    if (mLibHandle != NULL) {
        (*mDeinit)();

        dlclose(mLibHandle);
        mLibHandle = NULL;
    }
}

OMX_ERRORTYPE QComOMXPlugin::makeComponentInstance(
        const char *name,
        const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData,
        OMX_COMPONENTTYPE **component) {
    if (mLibHandle == NULL) {
        return OMX_ErrorUndefined;
    }

    return (*mGetHandle)(
            reinterpret_cast<OMX_HANDLETYPE *>(component),
            const_cast<char *>(name),
            appData, const_cast<OMX_CALLBACKTYPE *>(callbacks));
}

OMX_ERRORTYPE QComOMXPlugin::destroyComponentInstance(
        OMX_COMPONENTTYPE *component) {
    if (mLibHandle == NULL) {
        return OMX_ErrorUndefined;
    }

    return (*mFreeHandle)(reinterpret_cast<OMX_HANDLETYPE>(component));
}

OMX_ERRORTYPE QComOMXPlugin::enumerateComponents(
        OMX_STRING name,
        size_t size,
        OMX_U32 index) {
    if (mLibHandle == NULL) {
        return OMX_ErrorUndefined;
    }

    return (*mComponentNameEnum)(name, size, index);
}

void QComOMXPlugin::freeArrayOfAllocatedMemory(
        OMX_U8 **array,
        OMX_S32 count) {

    if (count == 0)
        return;

    do {
        count--;
        delete[] array[count];
        array[count] = NULL;
    } while (count > 0);
}

OMX_ERRORTYPE QComOMXPlugin::getRolesOfComponent(
        const char *name,
        Vector<String8> *roles) {
    roles->clear();

    if (mLibHandle == NULL) {
        return OMX_ErrorUndefined;
    }

    OMX_U32 numRoles;
    OMX_ERRORTYPE err = (*mGetRolesOfComponentHandle)(
            const_cast<OMX_STRING>(name), &numRoles, NULL);

    if (err != OMX_ErrorNone) {
        return err;
    }

    if (numRoles > 0) {
        OMX_U8 **array = new OMX_U8 *[numRoles];
        if (array == NULL) {
            return OMX_ErrorInsufficientResources;
        }

        for (OMX_U32 i = 0; i < numRoles; ++i) {
            array[i] = new OMX_U8[OMX_MAX_STRINGNAME_SIZE];
            if (array[i] == NULL) {
                freeArrayOfAllocatedMemory(array, i);
                delete[] array;
                array = NULL;

                return OMX_ErrorInsufficientResources;
            }
        }

        OMX_U32 numRoles2;
        err = (*mGetRolesOfComponentHandle)(
                const_cast<OMX_STRING>(name), &numRoles2, array);


        if (err != OMX_ErrorNone || numRoles2 != numRoles) {
            freeArrayOfAllocatedMemory(array, numRoles);
            delete[] array;
            array = NULL;
            return err == OMX_ErrorNone?OMX_ErrorBadParameter:err;
        }

        for (OMX_U32 i = 0; i < numRoles; ++i) {
            String8 s((const char *)array[i]);
            roles->push(s);

            delete[] array[i];
            array[i] = NULL;
        }

        delete[] array;
        array = NULL;
    }

    return OMX_ErrorNone;
}

}  // namespace android
