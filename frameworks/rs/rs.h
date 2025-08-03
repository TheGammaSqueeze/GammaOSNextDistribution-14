/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef RENDER_SCRIPT_H
#define RENDER_SCRIPT_H

#include <stdint.h>
#include <sys/types.h>

#include "rsInternalDefines.h"

extern "C" {
// Legacy graphics functions
RsObjectBase rsaFileA3DGetEntryByIndex(RsContext, uint32_t idx, RsFile) __DEPRECATED_IN(31);
RsFile rsaFileA3DCreateFromMemory(RsContext, const void* data, uint32_t len) __DEPRECATED_IN(31);
RsFile rsaFileA3DCreateFromAsset(RsContext, void* asset) __DEPRECATED_IN(31);
RsFile rsaFileA3DCreateFromFile(RsContext, const char* path) __DEPRECATED_IN(31);
void rsaFileA3DGetNumIndexEntries(RsContext, int32_t* numEntries, RsFile) __DEPRECATED_IN(31);
void rsaFileA3DGetIndexEntries(RsContext, RsFileIndexEntry* fileEntries, uint32_t numEntries,
                               RsFile) __DEPRECATED_IN(31);
// Mesh update functions
void rsaMeshGetVertexBufferCount(RsContext, RsMesh, int32_t* vtxCount) __DEPRECATED_IN(31);
void rsaMeshGetIndexCount(RsContext, RsMesh, int32_t* idxCount) __DEPRECATED_IN(31);
void rsaMeshGetVertices(RsContext, RsMesh, RsAllocation* vtxData, uint32_t vtxDataCount)
        __DEPRECATED_IN(31);
void rsaMeshGetIndices(RsContext, RsMesh, RsAllocation* va, uint32_t* primType,
                       uint32_t idxDataCount) __DEPRECATED_IN(31);
RsContext rsContextCreateGL(RsDevice dev, uint32_t version, uint32_t sdkVersion, RsSurfaceConfig sc,
                            uint32_t dpi) __DEPRECATED_IN(31);

void rsaGetName(RsContext, void* obj, const char** name) __DEPRECATED_IN(31);
// Allocation update
const void* rsaAllocationGetType(RsContext con, RsAllocation va) __DEPRECATED_IN(31);
// Type update
void rsaTypeGetNativeData(RsContext, RsType, uintptr_t* typeData, uint32_t typeDataSize)
        __DEPRECATED_IN(31);
// Element update
void rsaElementGetNativeData(RsContext, RsElement, uintptr_t* elemData, uint32_t elemDataSize)
        __DEPRECATED_IN(31);
void rsaElementGetSubElements(RsContext, RsElement, uintptr_t* ids, const char** names,
                              uint32_t* arraySizes, uint32_t dataSize) __DEPRECATED_IN(31);

RsDevice rsDeviceCreate() __DEPRECATED_IN(31);
void rsDeviceDestroy(RsDevice dev) __DEPRECATED_IN(31);
void rsDeviceSetConfig(RsDevice dev, RsDeviceParam p, int32_t value) __DEPRECATED_IN(31);
RsContext rsContextCreate(RsDevice dev, uint32_t version, uint32_t sdkVersion, RsContextType ct,
                          uint32_t flags) __DEPRECATED_IN(31);
}
#include "rsgApiFuncDecl.h"

#endif // RENDER_SCRIPT_H



