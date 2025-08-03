/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_RS_API_STUBS_H
#define ANDROID_RS_API_STUBS_H

#include "rsInternalDefines.h"

// Device
extern "C" RsDevice rsDeviceCreate() __DEPRECATED_IN(31);
extern "C" void rsDeviceDestroy(RsDevice dev) __DEPRECATED_IN(31);
extern "C" void rsDeviceSetConfig(RsDevice dev, RsDeviceParam p, int32_t value) __DEPRECATED_IN(31);

// Context
extern "C" RsContext rsContextCreate(RsDevice vdev, uint32_t version, uint32_t sdkVersion,
                                     RsContextType ct, uint32_t flags) __DEPRECATED_IN(31);
extern "C" void rsContextDestroy(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextFinish(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextDump(RsContext rsc, int32_t bits) __DEPRECATED_IN(31);
extern "C" void rsContextSetPriority(RsContext rsc, int32_t priority) __DEPRECATED_IN(31);
extern "C" void rsContextDestroyWorker(RsContext rsc) __DEPRECATED_IN(31);
extern "C" RsMessageToClientType rsContextGetMessage(RsContext rsc, void* data, size_t data_length,
                                                     size_t* receiveLen, size_t receiveLen_length,
                                                     uint32_t* usrID, size_t usrID_length)
        __DEPRECATED_IN(31);
extern "C" RsMessageToClientType rsContextPeekMessage(RsContext rsc, size_t* receiveLen,
                                                      size_t receiveLen_length, uint32_t* usrID,
                                                      size_t usrID_length) __DEPRECATED_IN(31);
extern "C" void rsContextSendMessage(RsContext rsc, uint32_t id, const uint8_t* data,
                                     size_t data_length) __DEPRECATED_IN(31);
extern "C" void rsContextInitToClient(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextDeinitToClient(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextSetCacheDir(RsContext rsc, const char* cacheDir, size_t cacheDir_length)
        __DEPRECATED_IN(31);
extern "C" void rsaContextSetNativeLibDir(RsContext con, char* libDir, size_t length)
        __DEPRECATED_IN(31);

// BaseObject
extern "C" void rsAssignName(RsContext rsc, RsObjectBase obj, const char* name, size_t name_length)
        __DEPRECATED_IN(31);
extern "C" void rsaGetName(RsContext con, void* obj, const char** name) __DEPRECATED_IN(31);
extern "C" void rsObjDestroy(RsContext rsc, RsAsyncVoidPtr objPtr) __DEPRECATED_IN(31);

// Element
extern "C" RsElement rsElementCreate(RsContext rsc, RsDataType mType, RsDataKind mKind,
                                     bool mNormalized, uint32_t mVectorSize) __DEPRECATED_IN(31);
extern "C" RsElement rsElementCreate2(RsContext rsc, const RsElement* elements,
                                      size_t elements_length, const char** names,
                                      size_t names_length_length, const size_t* names_length,
                                      const uint32_t* arraySize, size_t arraySize_length)
        __DEPRECATED_IN(31);
extern "C" void rsaElementGetNativeData(RsContext con, RsElement elem, uint32_t* elemData,
                                        uint32_t elemDataSize) __DEPRECATED_IN(31);
extern "C" void rsaElementGetSubElements(RsContext con, RsElement elem, uintptr_t* ids,
                                         const char** names, size_t* arraySizes, uint32_t dataSize)
        __DEPRECATED_IN(31);

// Type
extern "C" RsType rsTypeCreate(RsContext rsc, RsElement e, uint32_t dimX, uint32_t dimY,
                               uint32_t dimZ, bool mipmaps, bool faces, uint32_t yuv)
        __DEPRECATED_IN(31);
extern "C" RsType rsTypeCreate2(RsContext rsc, const RsTypeCreateParams* dat, size_t dat_length)
        __DEPRECATED_IN(31);
extern "C" void rsaTypeGetNativeData(RsContext con, RsType type, uintptr_t* typeData,
                                     uint32_t typeDataSize) __DEPRECATED_IN(31);

// Allocation
extern "C" RsAllocation rsAllocationCreateTyped(RsContext rsc, RsType vtype,
                                                RsAllocationMipmapControl mipmaps, uint32_t usages,
                                                uintptr_t ptr) __DEPRECATED_IN(31);
extern "C" RsAllocation rsAllocationCreateFromBitmap(RsContext rsc, RsType vtype,
                                                     RsAllocationMipmapControl mipmaps,
                                                     const void* data, size_t data_length,
                                                     uint32_t usages) __DEPRECATED_IN(31);
extern "C" RsAllocation rsAllocationCubeCreateFromBitmap(RsContext rsc, RsType vtype,
                                                         RsAllocationMipmapControl mipmaps,
                                                         const void* data, size_t data_length,
                                                         uint32_t usages) __DEPRECATED_IN(31);
extern "C" RsAllocation rsAllocationAdapterCreate(RsContext rsc, RsType vtype,
                                                  RsAllocation baseAlloc) __DEPRECATED_IN(31);
extern "C" const void* rsaAllocationGetType(RsContext con, RsAllocation va) __DEPRECATED_IN(31);
extern "C" RsNativeWindow rsAllocationGetSurface(RsContext rsc, RsAllocation alloc)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationSetupBufferQueue(RsContext rsc, RsAllocation alloc, uint32_t numAlloc)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationShareBufferQueue(RsContext rsc, RsAllocation alloc1,
                                             RsAllocation alloc2) __DEPRECATED_IN(31);
extern "C" void rsAllocationSetSurface(RsContext rsc, RsAllocation alloc, RsNativeWindow sur)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationAdapterOffset(RsContext rsc, RsAllocation alloc,
                                          const uint32_t* offsets, size_t offsets_length)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationCopyToBitmap(RsContext rsc, RsAllocation alloc, void* data,
                                         size_t data_length) __DEPRECATED_IN(31);
extern "C" void* rsAllocationGetPointer(RsContext rsc, RsAllocation va, uint32_t lod,
                                        RsAllocationCubemapFace face, uint32_t z, uint32_t array,
                                        size_t* stride, size_t stride_length) __DEPRECATED_IN(31);
extern "C" void rsAllocation1DData(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t lod,
                                   uint32_t count, const void* data, size_t data_length)
        __DEPRECATED_IN(31);
extern "C" void rsAllocation1DElementData(RsContext rsc, RsAllocation va, uint32_t x, uint32_t lod,
                                          const void* data, size_t data_length, size_t comp_offset)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationElementData(RsContext rsc, RsAllocation va, uint32_t x, uint32_t y,
                                        uint32_t z, uint32_t lod, const void* data,
                                        size_t data_length, size_t comp_offset) __DEPRECATED_IN(31);
extern "C" void rsAllocation2DData(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t yoff,
                                   uint32_t lod, RsAllocationCubemapFace face, uint32_t w,
                                   uint32_t h, const void* data, size_t data_length, size_t stride)
        __DEPRECATED_IN(31);
extern "C" void rsAllocation3DData(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t yoff,
                                   uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d,
                                   const void* data, size_t data_length, size_t stride)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationGenerateMipmaps(RsContext rsc, RsAllocation va) __DEPRECATED_IN(31);
extern "C" void rsAllocationRead(RsContext rsc, RsAllocation va, void* data, size_t data_length)
        __DEPRECATED_IN(31);
extern "C" void rsAllocation1DRead(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t lod,
                                   uint32_t count, void* data, size_t data_length)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationElementRead(RsContext rsc, RsAllocation va, uint32_t x, uint32_t y,
                                        uint32_t z, uint32_t lod, void* data, size_t data_length,
                                        size_t comp_offset) __DEPRECATED_IN(31);
extern "C" void rsAllocation2DRead(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t yoff,
                                   uint32_t lod, RsAllocationCubemapFace face, uint32_t w,
                                   uint32_t h, void* data, size_t data_length, size_t stride)
        __DEPRECATED_IN(31);
extern "C" void rsAllocation3DRead(RsContext rsc, RsAllocation va, uint32_t xoff, uint32_t yoff,
                                   uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d,
                                   void* data, size_t data_length, size_t stride)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationSyncAll(RsContext rsc, RsAllocation va, RsAllocationUsageType src)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationResize1D(RsContext rsc, RsAllocation va, uint32_t dimX)
        __DEPRECATED_IN(31);
extern "C" void rsAllocationCopy2DRange(RsContext rsc, RsAllocation dest, uint32_t destXoff,
                                        uint32_t destYoff, uint32_t destMip, uint32_t destFace,
                                        uint32_t width, uint32_t height, RsAllocation src,
                                        uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip,
                                        uint32_t srcFace) __DEPRECATED_IN(31);
extern "C" void rsAllocationCopy3DRange(RsContext rsc, RsAllocation dest, uint32_t destXoff,
                                        uint32_t destYoff, uint32_t destZoff, uint32_t destMip,
                                        uint32_t width, uint32_t height, uint32_t depth,
                                        RsAllocation src, uint32_t srcXoff, uint32_t srcYoff,
                                        uint32_t srcZoff, uint32_t srcMip) __DEPRECATED_IN(31);
extern "C" void rsAllocationIoSend(RsContext rsc, RsAllocation alloc) __DEPRECATED_IN(31);
extern "C" int64_t rsAllocationIoReceive(RsContext rsc, RsAllocation alloc) __DEPRECATED_IN(31);

// ScriptGroup
extern "C" RsScriptGroup rsScriptGroupCreate(RsContext rsc, RsScriptKernelID* kernels,
                                             size_t kernels_length, RsScriptKernelID* src,
                                             size_t src_length, RsScriptKernelID* dstK,
                                             size_t dstK_length, RsScriptFieldID* dstF,
                                             size_t dstF_length, const RsType* type,
                                             size_t type_length) __DEPRECATED_IN(31);
extern "C" RsScriptGroup2 rsScriptGroup2Create(RsContext rsc, const char* name, size_t name_length,
                                               const char* cacheDir, size_t cacheDir_length,
                                               RsClosure* closures, size_t closures_length)
        __DEPRECATED_IN(31);
extern "C" RsClosure rsClosureCreate(RsContext rsc, RsScriptKernelID kernelID,
                                     RsAllocation returnValue, RsScriptFieldID* fieldIDs,
                                     size_t fieldIDs_length, const int64_t* values,
                                     size_t values_length, const int* sizes, size_t sizes_length,
                                     RsClosure* depClosures, size_t depClosures_length,
                                     RsScriptFieldID* depFieldIDs, size_t depFieldIDs_length)
        __DEPRECATED_IN(31);
extern "C" RsClosure rsInvokeClosureCreate(RsContext rsc, RsScriptInvokeID invokeID,
                                           const void* params, size_t params_length,
                                           const RsScriptFieldID* fieldIDs, size_t fieldIDs_length,
                                           const int64_t* values, size_t values_length,
                                           const int* sizes, size_t sizes_length)
        __DEPRECATED_IN(31);
extern "C" void rsClosureSetArg(RsContext rsc, RsClosure closureID, uint32_t index, uintptr_t value,
                                int valueSize) __DEPRECATED_IN(31);
extern "C" void rsClosureSetGlobal(RsContext rsc, RsClosure closureID, RsScriptFieldID fieldID,
                                   int64_t value, int valueSize) __DEPRECATED_IN(31);
extern "C" RsScriptKernelID rsScriptKernelIDCreate(RsContext rsc, RsScript sid, int slot, int sig)
        __DEPRECATED_IN(31);
extern "C" RsScriptInvokeID rsScriptInvokeIDCreate(RsContext rsc, RsScript s, uint32_t slot)
        __DEPRECATED_IN(31);
extern "C" RsScriptFieldID rsScriptFieldIDCreate(RsContext rsc, RsScript sid, int slot)
        __DEPRECATED_IN(31);
extern "C" void rsScriptGroupSetOutput(RsContext rsc, RsScriptGroup group, RsScriptKernelID kernel,
                                       RsAllocation alloc) __DEPRECATED_IN(31);
extern "C" void rsScriptGroupSetInput(RsContext rsc, RsScriptGroup group, RsScriptKernelID kernel,
                                      RsAllocation alloc) __DEPRECATED_IN(31);
extern "C" void rsScriptGroupExecute(RsContext rsc, RsScriptGroup group) __DEPRECATED_IN(31);

// Sampler
extern "C" RsSampler rsSamplerCreate(RsContext rsc, RsSamplerValue magFilter,
                                     RsSamplerValue minFilter, RsSamplerValue wrapS,
                                     RsSamplerValue wrapT, RsSamplerValue wrapR, float mAniso)
        __DEPRECATED_IN(31);

// Script
extern "C" RsScript rsScriptCCreate(RsContext rsc, const char* resName, size_t resName_length,
                                    const char* cacheDir, size_t cacheDir_length, const char* text,
                                    size_t text_length) __DEPRECATED_IN(31);
extern "C" RsScript rsScriptIntrinsicCreate(RsContext rsc, uint32_t id, RsElement eid)
        __DEPRECATED_IN(31);
extern "C" void rsScriptBindAllocation(RsContext rsc, RsScript vtm, RsAllocation va, uint32_t slot)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetTimeZone(RsContext rsc, RsScript s, const char* timeZone,
                                    size_t timeZone_length) __DEPRECATED_IN(31);
extern "C" void rsScriptInvoke(RsContext rsc, RsScript s, uint32_t slot) __DEPRECATED_IN(31);
extern "C" void rsScriptInvokeV(RsContext rsc, RsScript s, uint32_t slot, const void* data,
                                size_t data_length) __DEPRECATED_IN(31);
extern "C" void rsScriptForEach(RsContext rsc, RsScript s, uint32_t slot, RsAllocation ain,
                                RsAllocation aout, const void* usr, size_t usr_length,
                                const RsScriptCall* sc, size_t sc_length) __DEPRECATED_IN(31);
extern "C" void rsScriptForEachMulti(RsContext rsc, RsScript s, uint32_t slot, RsAllocation* ains,
                                     size_t ains_length, RsAllocation aout, const void* usr,
                                     size_t usr_length, const RsScriptCall* sc, size_t sc_length)
        __DEPRECATED_IN(31);
extern "C" void rsScriptReduce(RsContext rsc, RsScript s, uint32_t slot, RsAllocation* ains,
                               size_t ains_length, RsAllocation aout, const RsScriptCall* sc,
                               size_t sc_length) __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarI(RsContext rsc, RsScript s, uint32_t slot, int value)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarObj(RsContext rsc, RsScript s, uint32_t slot, RsObjectBase value)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarJ(RsContext rsc, RsScript s, uint32_t slot, int64_t value)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarF(RsContext rsc, RsScript s, uint32_t slot, float value)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarD(RsContext rsc, RsScript s, uint32_t slot, double value)
        __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarV(RsContext rsc, RsScript s, uint32_t slot, const void* data,
                                size_t data_length) __DEPRECATED_IN(31);
extern "C" void rsScriptGetVarV(RsContext rsc, RsScript s, uint32_t slot, void* data,
                                size_t data_length) __DEPRECATED_IN(31);
extern "C" void rsScriptSetVarVE(RsContext rsc, RsScript s, uint32_t slot, const void* data,
                                 size_t data_length, RsElement e, const uint32_t* dims,
                                 size_t dims_length) __DEPRECATED_IN(31);

// Graphics
extern "C" RsContext rsContextCreateGL(RsDevice vdev, uint32_t version, uint32_t sdkVersion,
                                       RsSurfaceConfig sc, uint32_t dpi) __DEPRECATED_IN(31);
extern "C" void rsContextSetSurface(RsContext rsc, uint32_t width, uint32_t height,
                                    RsNativeWindow sur) __DEPRECATED_IN(31);
extern "C" void rsContextPause(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextResume(RsContext rsc) __DEPRECATED_IN(31);
extern "C" void rsContextBindProgramStore(RsContext rsc, RsProgramStore pgm) __DEPRECATED_IN(31);
extern "C" void rsContextBindProgramFragment(RsContext rsc, RsProgramFragment pgm)
        __DEPRECATED_IN(31);
extern "C" void rsContextBindProgramVertex(RsContext rsc, RsProgramVertex pgm) __DEPRECATED_IN(31);
extern "C" void rsContextBindProgramRaster(RsContext rsc, RsProgramRaster pgm) __DEPRECATED_IN(31);
extern "C" void rsContextBindFont(RsContext rsc, RsFont pgm) __DEPRECATED_IN(31);
extern "C" void rsContextBindRootScript(RsContext rsc, RsScript sampler) __DEPRECATED_IN(31);

extern "C" RsProgramStore rsProgramStoreCreate(RsContext rsc, bool colorMaskR, bool colorMaskG,
                                               bool colorMaskB, bool colorMaskA, bool depthMask,
                                               bool ditherEnable, RsBlendSrcFunc srcFunc,
                                               RsBlendDstFunc destFunc, RsDepthFunc depthFunc)
        __DEPRECATED_IN(31);
extern "C" RsProgramRaster rsProgramRasterCreate(RsContext rsc, bool pointSprite, RsCullMode cull)
        __DEPRECATED_IN(31);
extern "C" RsProgramFragment rsProgramFragmentCreate(
        RsContext rsc, const char* shaderText, size_t shaderText_length, const char** textureNames,
        size_t textureNames_length_length, const size_t* textureNames_length,
        const uintptr_t* params, size_t params_length) __DEPRECATED_IN(31);
extern "C" RsProgramVertex rsProgramVertexCreate(
        RsContext rsc, const char* shaderText, size_t shaderText_length, const char** textureNames,
        size_t textureNames_length_length, const size_t* textureNames_length,
        const uintptr_t* params, size_t params_length) __DEPRECATED_IN(31);
extern "C" RsFont rsFontCreateFromFile(RsContext rsc, const char* name, size_t name_length,
                                       float fontSize, uint32_t dpi) __DEPRECATED_IN(31);
extern "C" RsFont rsFontCreateFromMemory(RsContext rsc, const char* name, size_t name_length,
                                         float fontSize, uint32_t dpi, const void* data,
                                         size_t data_length) __DEPRECATED_IN(31);
extern "C" RsMesh rsMeshCreate(RsContext rsc, RsAllocation* vtx, size_t vtx_length,
                               RsAllocation* idx, size_t idx_length, uint32_t* primType,
                               size_t primType_length) __DEPRECATED_IN(31);
extern "C" void rsProgramBindConstants(RsContext rsc, RsProgram vp, uint32_t slot,
                                       RsAllocation constants) __DEPRECATED_IN(31);
extern "C" void rsProgramBindTexture(RsContext rsc, RsProgramFragment pf, uint32_t slot,
                                     RsAllocation a) __DEPRECATED_IN(31);
extern "C" void rsProgramBindSampler(RsContext rsc, RsProgramFragment pf, uint32_t slot,
                                     RsSampler s) __DEPRECATED_IN(31);

extern "C" RsObjectBase rsaFileA3DGetEntryByIndex(RsContext con, uint32_t index, RsFile file)
        __DEPRECATED_IN(31);
extern "C" RsFile rsaFileA3DCreateFromMemory(RsContext con, const void* data, uint32_t len)
        __DEPRECATED_IN(31);
extern "C" RsFile rsaFileA3DCreateFromAsset(RsContext con, void* _asset) __DEPRECATED_IN(31);
extern "C" RsFile rsaFileA3DCreateFromFile(RsContext con, const char* path) __DEPRECATED_IN(31);
extern "C" void rsaFileA3DGetNumIndexEntries(RsContext con, int32_t* numEntries, RsFile file)
        __DEPRECATED_IN(31);
extern "C" void rsaFileA3DGetIndexEntries(RsContext con, RsFileIndexEntry* fileEntries,
                                          uint32_t numEntries, RsFile file) __DEPRECATED_IN(31);
extern "C" void rsaMeshGetVertexBufferCount(RsContext con, RsMesh mv, int32_t* numVtx)
        __DEPRECATED_IN(31);
extern "C" void rsaMeshGetIndexCount(RsContext con, RsMesh mv, int32_t* numIdx) __DEPRECATED_IN(31);
extern "C" void rsaMeshGetVertices(RsContext con, RsMesh mv, RsAllocation* vtxData,
                                   uint32_t vtxDataCount) __DEPRECATED_IN(31);
extern "C" void rsaMeshGetIndices(RsContext con, RsMesh mv, RsAllocation* va, uint32_t* primType,
                                  uint32_t idxDataCount) __DEPRECATED_IN(31);

#endif // ANDROID_RS_API_STUBS_H
