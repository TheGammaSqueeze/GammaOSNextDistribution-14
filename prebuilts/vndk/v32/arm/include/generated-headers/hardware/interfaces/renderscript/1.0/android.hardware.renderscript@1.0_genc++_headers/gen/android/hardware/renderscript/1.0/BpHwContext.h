#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWCONTEXT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWCONTEXT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/renderscript/1.0/IHwContext.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {

struct BpHwContext : public ::android::hardware::BpInterface<IContext>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwContext(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IContext Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::renderscript::V1_0::IContext follow.
    static ::android::hardware::Return<uint64_t>  _hidl_allocationAdapterCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t type, uint64_t baseAlloc);
    static ::android::hardware::Return<void>  _hidl_allocationAdapterOffset(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t alloc, const ::android::hardware::hidl_vec<uint32_t>& offsets);
    static ::android::hardware::Return<uint64_t>  _hidl_allocationGetType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation);
    static ::android::hardware::Return<uint64_t>  _hidl_allocationCreateTyped(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage, void* ptr);
    static ::android::hardware::Return<uint64_t>  _hidl_allocationCreateFromBitmap(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage);
    static ::android::hardware::Return<uint64_t>  _hidl_allocationCubeCreateFromBitmap(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage);
    static ::android::hardware::Return<uint64_t>  _hidl_allocationGetNativeWindow(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation);
    static ::android::hardware::Return<void>  _hidl_allocationSetNativeWindow(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint64_t nativewindow);
    static ::android::hardware::Return<void>  _hidl_allocationSetupBufferQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t alloc, uint32_t numBuffer);
    static ::android::hardware::Return<void>  _hidl_allocationShareBufferQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t baseAlloc, uint64_t subAlloc);
    static ::android::hardware::Return<void>  _hidl_allocationCopyToBitmap(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, void* data, uint64_t sizeBytes);
    static ::android::hardware::Return<void>  _hidl_allocation1DWrite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t offset, uint32_t lod, uint32_t count, const ::android::hardware::hidl_vec<uint8_t>& data);
    static ::android::hardware::Return<void>  _hidl_allocationElementWrite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t compIdx);
    static ::android::hardware::Return<void>  _hidl_allocation2DWrite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride);
    static ::android::hardware::Return<void>  _hidl_allocation3DWrite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride);
    static ::android::hardware::Return<void>  _hidl_allocationGenerateMipmaps(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation);
    static ::android::hardware::Return<void>  _hidl_allocationRead(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, void* data, uint64_t sizeBytes);
    static ::android::hardware::Return<void>  _hidl_allocation1DRead(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t xoff, uint32_t lod, uint32_t count, void* data, uint64_t sizeBytes);
    static ::android::hardware::Return<void>  _hidl_allocationElementRead(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, void* data, uint64_t sizeBytes, uint64_t compIdx);
    static ::android::hardware::Return<void>  _hidl_allocation2DRead(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, void* data, uint64_t sizeBytes, uint64_t stride);
    static ::android::hardware::Return<void>  _hidl_allocation3DRead(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, void* data, uint64_t sizeBytes, uint64_t stride);
    static ::android::hardware::Return<void>  _hidl_allocationSyncAll(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, ::android::hardware::renderscript::V1_0::AllocationUsageType usageType);
    static ::android::hardware::Return<void>  _hidl_allocationResize1D(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t dimX);
    static ::android::hardware::Return<void>  _hidl_allocationCopy2DRange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace dstFace, uint32_t width, uint32_t height, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace srcFace);
    static ::android::hardware::Return<void>  _hidl_allocationCopy3DRange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstZoff, uint32_t dstMip, uint32_t width, uint32_t height, uint32_t depth, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcZoff, uint32_t srcMip);
    static ::android::hardware::Return<void>  _hidl_allocationIoSend(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation);
    static ::android::hardware::Return<void>  _hidl_allocationIoReceive(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation);
    static ::android::hardware::Return<void>  _hidl_allocationGetPointer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t allocation, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t z, allocationGetPointer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_elementGetNativeMetadata(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t element, elementGetNativeMetadata_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_elementGetSubElements(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t element, uint64_t numSubElem, elementGetSubElements_cb _hidl_cb);
    static ::android::hardware::Return<uint64_t>  _hidl_elementCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::renderscript::V1_0::DataType dt, ::android::hardware::renderscript::V1_0::DataKind dk, bool norm, uint32_t size);
    static ::android::hardware::Return<uint64_t>  _hidl_elementComplexCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint64_t>& einsPtr, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& names, const ::android::hardware::hidl_vec<uint64_t>& arraySizesPtr);
    static ::android::hardware::Return<void>  _hidl_typeGetNativeMetadata(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t type, typeGetNativeMetadata_cb _hidl_cb);
    static ::android::hardware::Return<uint64_t>  _hidl_typeCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t element, uint32_t dimX, uint32_t dimY, uint32_t dimZ, bool mipmaps, bool faces, ::android::hardware::renderscript::V1_0::YuvFormat yuv);
    static ::android::hardware::Return<void>  _hidl_contextDestroy(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_contextGetMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, void* data, uint64_t size, contextGetMessage_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_contextPeekMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, contextPeekMessage_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_contextSendMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& data);
    static ::android::hardware::Return<void>  _hidl_contextInitToClient(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_contextDeinitToClient(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_contextFinish(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_contextLog(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_contextSetCacheDir(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& cacheDir);
    static ::android::hardware::Return<void>  _hidl_contextSetPriority(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::renderscript::V1_0::ThreadPriorities priority);
    static ::android::hardware::Return<void>  _hidl_assignName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t obj, const ::android::hardware::hidl_string& name);
    static ::android::hardware::Return<void>  _hidl_getName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t obj, getName_cb _hidl_cb);
    static ::android::hardware::Return<uint64_t>  _hidl_closureCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t kernelID, uint64_t returnValue, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes, const ::android::hardware::hidl_vec<uint64_t>& depClosures, const ::android::hardware::hidl_vec<uint64_t>& depFieldIDS);
    static ::android::hardware::Return<uint64_t>  _hidl_invokeClosureCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t invokeID, const ::android::hardware::hidl_vec<uint8_t>& params, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes);
    static ::android::hardware::Return<void>  _hidl_closureSetArg(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t closure, uint32_t index, void* value, int32_t size);
    static ::android::hardware::Return<void>  _hidl_closureSetGlobal(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t closure, uint64_t fieldID, int64_t value, int32_t size);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptKernelIDCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t script, int32_t slot, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::MetadataSignatureBitval> sig);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptInvokeIDCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t script, int32_t slot);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptFieldIDCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t script, int32_t slot);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptGroupCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint64_t>& kernels, const ::android::hardware::hidl_vec<uint64_t>& srcK, const ::android::hardware::hidl_vec<uint64_t>& dstK, const ::android::hardware::hidl_vec<uint64_t>& dstF, const ::android::hardware::hidl_vec<uint64_t>& types);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptGroup2Create(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& name, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint64_t>& closures);
    static ::android::hardware::Return<void>  _hidl_scriptGroupSetOutput(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t sg, uint64_t kid, uint64_t alloc);
    static ::android::hardware::Return<void>  _hidl_scriptGroupSetInput(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t sg, uint64_t kid, uint64_t alloc);
    static ::android::hardware::Return<void>  _hidl_scriptGroupExecute(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t sg);
    static ::android::hardware::Return<void>  _hidl_objDestroy(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t obj);
    static ::android::hardware::Return<uint64_t>  _hidl_samplerCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::renderscript::V1_0::SamplerValue magFilter, ::android::hardware::renderscript::V1_0::SamplerValue minFilter, ::android::hardware::renderscript::V1_0::SamplerValue wrapS, ::android::hardware::renderscript::V1_0::SamplerValue wrapT, ::android::hardware::renderscript::V1_0::SamplerValue wrapR, float aniso);
    static ::android::hardware::Return<void>  _hidl_scriptBindAllocation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t script, uint64_t allocation, uint32_t slot);
    static ::android::hardware::Return<void>  _hidl_scriptSetTimeZone(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t script, const ::android::hardware::hidl_string& timeZone);
    static ::android::hardware::Return<void>  _hidl_scriptInvoke(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot);
    static ::android::hardware::Return<void>  _hidl_scriptInvokeV(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data);
    static ::android::hardware::Return<void>  _hidl_scriptForEach(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, const ::android::hardware::hidl_vec<uint8_t>& params, void* sc);
    static ::android::hardware::Return<void>  _hidl_scriptReduce(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, void* sc);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarI(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, int32_t value);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarObj(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, uint64_t obj);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarJ(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, int64_t value);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarF(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, float value);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarD(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, double value);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarV(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data);
    static ::android::hardware::Return<void>  _hidl_scriptGetVarV(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, uint64_t len, scriptGetVarV_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_scriptSetVarVE(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t ve, const ::android::hardware::hidl_vec<uint32_t>& dims);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptCCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& resName, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint8_t>& text);
    static ::android::hardware::Return<uint64_t>  _hidl_scriptIntrinsicCreate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::renderscript::V1_0::ScriptIntrinsicID id, uint64_t elem);

    // Methods from ::android::hardware::renderscript::V1_0::IContext follow.
    ::android::hardware::Return<uint64_t> allocationAdapterCreate(uint64_t type, uint64_t baseAlloc) override;
    ::android::hardware::Return<void> allocationAdapterOffset(uint64_t alloc, const ::android::hardware::hidl_vec<uint32_t>& offsets) override;
    ::android::hardware::Return<uint64_t> allocationGetType(uint64_t allocation) override;
    ::android::hardware::Return<uint64_t> allocationCreateTyped(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage, void* ptr) override;
    ::android::hardware::Return<uint64_t> allocationCreateFromBitmap(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage) override;
    ::android::hardware::Return<uint64_t> allocationCubeCreateFromBitmap(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage) override;
    ::android::hardware::Return<uint64_t> allocationGetNativeWindow(uint64_t allocation) override;
    ::android::hardware::Return<void> allocationSetNativeWindow(uint64_t allocation, uint64_t nativewindow) override;
    ::android::hardware::Return<void> allocationSetupBufferQueue(uint64_t alloc, uint32_t numBuffer) override;
    ::android::hardware::Return<void> allocationShareBufferQueue(uint64_t baseAlloc, uint64_t subAlloc) override;
    ::android::hardware::Return<void> allocationCopyToBitmap(uint64_t allocation, void* data, uint64_t sizeBytes) override;
    ::android::hardware::Return<void> allocation1DWrite(uint64_t allocation, uint32_t offset, uint32_t lod, uint32_t count, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> allocationElementWrite(uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t compIdx) override;
    ::android::hardware::Return<void> allocation2DWrite(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride) override;
    ::android::hardware::Return<void> allocation3DWrite(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride) override;
    ::android::hardware::Return<void> allocationGenerateMipmaps(uint64_t allocation) override;
    ::android::hardware::Return<void> allocationRead(uint64_t allocation, void* data, uint64_t sizeBytes) override;
    ::android::hardware::Return<void> allocation1DRead(uint64_t allocation, uint32_t xoff, uint32_t lod, uint32_t count, void* data, uint64_t sizeBytes) override;
    ::android::hardware::Return<void> allocationElementRead(uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, void* data, uint64_t sizeBytes, uint64_t compIdx) override;
    ::android::hardware::Return<void> allocation2DRead(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, void* data, uint64_t sizeBytes, uint64_t stride) override;
    ::android::hardware::Return<void> allocation3DRead(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, void* data, uint64_t sizeBytes, uint64_t stride) override;
    ::android::hardware::Return<void> allocationSyncAll(uint64_t allocation, ::android::hardware::renderscript::V1_0::AllocationUsageType usageType) override;
    ::android::hardware::Return<void> allocationResize1D(uint64_t allocation, uint32_t dimX) override;
    ::android::hardware::Return<void> allocationCopy2DRange(uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace dstFace, uint32_t width, uint32_t height, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace srcFace) override;
    ::android::hardware::Return<void> allocationCopy3DRange(uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstZoff, uint32_t dstMip, uint32_t width, uint32_t height, uint32_t depth, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcZoff, uint32_t srcMip) override;
    ::android::hardware::Return<void> allocationIoSend(uint64_t allocation) override;
    ::android::hardware::Return<void> allocationIoReceive(uint64_t allocation) override;
    ::android::hardware::Return<void> allocationGetPointer(uint64_t allocation, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t z, allocationGetPointer_cb _hidl_cb) override;
    ::android::hardware::Return<void> elementGetNativeMetadata(uint64_t element, elementGetNativeMetadata_cb _hidl_cb) override;
    ::android::hardware::Return<void> elementGetSubElements(uint64_t element, uint64_t numSubElem, elementGetSubElements_cb _hidl_cb) override;
    ::android::hardware::Return<uint64_t> elementCreate(::android::hardware::renderscript::V1_0::DataType dt, ::android::hardware::renderscript::V1_0::DataKind dk, bool norm, uint32_t size) override;
    ::android::hardware::Return<uint64_t> elementComplexCreate(const ::android::hardware::hidl_vec<uint64_t>& einsPtr, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& names, const ::android::hardware::hidl_vec<uint64_t>& arraySizesPtr) override;
    ::android::hardware::Return<void> typeGetNativeMetadata(uint64_t type, typeGetNativeMetadata_cb _hidl_cb) override;
    ::android::hardware::Return<uint64_t> typeCreate(uint64_t element, uint32_t dimX, uint32_t dimY, uint32_t dimZ, bool mipmaps, bool faces, ::android::hardware::renderscript::V1_0::YuvFormat yuv) override;
    ::android::hardware::Return<void> contextDestroy() override;
    ::android::hardware::Return<void> contextGetMessage(void* data, uint64_t size, contextGetMessage_cb _hidl_cb) override;
    ::android::hardware::Return<void> contextPeekMessage(contextPeekMessage_cb _hidl_cb) override;
    ::android::hardware::Return<void> contextSendMessage(uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> contextInitToClient() override;
    ::android::hardware::Return<void> contextDeinitToClient() override;
    ::android::hardware::Return<void> contextFinish() override;
    ::android::hardware::Return<void> contextLog() override;
    ::android::hardware::Return<void> contextSetCacheDir(const ::android::hardware::hidl_string& cacheDir) override;
    ::android::hardware::Return<void> contextSetPriority(::android::hardware::renderscript::V1_0::ThreadPriorities priority) override;
    ::android::hardware::Return<void> assignName(uint64_t obj, const ::android::hardware::hidl_string& name) override;
    ::android::hardware::Return<void> getName(uint64_t obj, getName_cb _hidl_cb) override;
    ::android::hardware::Return<uint64_t> closureCreate(uint64_t kernelID, uint64_t returnValue, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes, const ::android::hardware::hidl_vec<uint64_t>& depClosures, const ::android::hardware::hidl_vec<uint64_t>& depFieldIDS) override;
    ::android::hardware::Return<uint64_t> invokeClosureCreate(uint64_t invokeID, const ::android::hardware::hidl_vec<uint8_t>& params, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes) override;
    ::android::hardware::Return<void> closureSetArg(uint64_t closure, uint32_t index, void* value, int32_t size) override;
    ::android::hardware::Return<void> closureSetGlobal(uint64_t closure, uint64_t fieldID, int64_t value, int32_t size) override;
    ::android::hardware::Return<uint64_t> scriptKernelIDCreate(uint64_t script, int32_t slot, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::MetadataSignatureBitval> sig) override;
    ::android::hardware::Return<uint64_t> scriptInvokeIDCreate(uint64_t script, int32_t slot) override;
    ::android::hardware::Return<uint64_t> scriptFieldIDCreate(uint64_t script, int32_t slot) override;
    ::android::hardware::Return<uint64_t> scriptGroupCreate(const ::android::hardware::hidl_vec<uint64_t>& kernels, const ::android::hardware::hidl_vec<uint64_t>& srcK, const ::android::hardware::hidl_vec<uint64_t>& dstK, const ::android::hardware::hidl_vec<uint64_t>& dstF, const ::android::hardware::hidl_vec<uint64_t>& types) override;
    ::android::hardware::Return<uint64_t> scriptGroup2Create(const ::android::hardware::hidl_string& name, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint64_t>& closures) override;
    ::android::hardware::Return<void> scriptGroupSetOutput(uint64_t sg, uint64_t kid, uint64_t alloc) override;
    ::android::hardware::Return<void> scriptGroupSetInput(uint64_t sg, uint64_t kid, uint64_t alloc) override;
    ::android::hardware::Return<void> scriptGroupExecute(uint64_t sg) override;
    ::android::hardware::Return<void> objDestroy(uint64_t obj) override;
    ::android::hardware::Return<uint64_t> samplerCreate(::android::hardware::renderscript::V1_0::SamplerValue magFilter, ::android::hardware::renderscript::V1_0::SamplerValue minFilter, ::android::hardware::renderscript::V1_0::SamplerValue wrapS, ::android::hardware::renderscript::V1_0::SamplerValue wrapT, ::android::hardware::renderscript::V1_0::SamplerValue wrapR, float aniso) override;
    ::android::hardware::Return<void> scriptBindAllocation(uint64_t script, uint64_t allocation, uint32_t slot) override;
    ::android::hardware::Return<void> scriptSetTimeZone(uint64_t script, const ::android::hardware::hidl_string& timeZone) override;
    ::android::hardware::Return<void> scriptInvoke(uint64_t vs, uint32_t slot) override;
    ::android::hardware::Return<void> scriptInvokeV(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> scriptForEach(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, const ::android::hardware::hidl_vec<uint8_t>& params, void* sc) override;
    ::android::hardware::Return<void> scriptReduce(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, void* sc) override;
    ::android::hardware::Return<void> scriptSetVarI(uint64_t vs, uint32_t slot, int32_t value) override;
    ::android::hardware::Return<void> scriptSetVarObj(uint64_t vs, uint32_t slot, uint64_t obj) override;
    ::android::hardware::Return<void> scriptSetVarJ(uint64_t vs, uint32_t slot, int64_t value) override;
    ::android::hardware::Return<void> scriptSetVarF(uint64_t vs, uint32_t slot, float value) override;
    ::android::hardware::Return<void> scriptSetVarD(uint64_t vs, uint32_t slot, double value) override;
    ::android::hardware::Return<void> scriptSetVarV(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> scriptGetVarV(uint64_t vs, uint32_t slot, uint64_t len, scriptGetVarV_cb _hidl_cb) override;
    ::android::hardware::Return<void> scriptSetVarVE(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t ve, const ::android::hardware::hidl_vec<uint32_t>& dims) override;
    ::android::hardware::Return<uint64_t> scriptCCreate(const ::android::hardware::hidl_string& resName, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint8_t>& text) override;
    ::android::hardware::Return<uint64_t> scriptIntrinsicCreate(::android::hardware::renderscript::V1_0::ScriptIntrinsicID id, uint64_t elem) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_BPHWCONTEXT_H
