#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_ICONTEXT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_ICONTEXT_H

#include <android/hardware/renderscript/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace renderscript {
namespace V1_0 {

struct IContext : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.renderscript@1.0::IContext"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * TODO: Do we need to define "selectors"? It may be a property of the
     * "adapted allocation" that's returned.
     *
     * Creates an arbitrary window into the base allocation. The type describes
     * the shape of the window. Any dimensions present in the type must be
     * equal to or smaller than the dimensions in the source allocation. A
     * dimension present in the allocation that is not present in the type must
     * be constrained away with the selectors. If a dimension is present in
     * both the type and allocation, one of two things must happen. If the type
     * is smaller than the allocation, a window must be created, the selected
     * value in the adapter for that dimension must act as the base address,
     * and the type must describe the size of the view starting at that point.
     * If the type and allocation dimension are of the same size, then setting
     * the selector for the dimension must be an error.
     *
     * @param type Type describing data layout
     * @param baseAlloc Allocation
     * @return subAlloc AllocationAdapter
     */
    virtual ::android::hardware::Return<uint64_t> allocationAdapterCreate(uint64_t type, uint64_t baseAlloc) = 0;

    /**
     * TODO: Need to relate "offset" back to the terminology in
     * allocationAdapterCreate() -- the latter uses the terms "selector" and
     * "selected value". Can we use consistent terminology? Are "offset" and
     * "selector" actually two different things?
     *
     * TODO: Explain the flattened layout in the offsets vec
     *
     * Sets the offsets for an Allocation Adapter.
     *
     * @param alloc AllocationAdapter
     * @param offsets Collection of offsets
     */
    virtual ::android::hardware::Return<void> allocationAdapterOffset(uint64_t alloc, const ::android::hardware::hidl_vec<uint32_t>& offsets) = 0;

    /**
     * TODO: add more explanation here.
     *
     * Returns the Type of the Allocation.
     *
     * @param allocation Allocation
     * @return type Allocation's Type
     */
    virtual ::android::hardware::Return<uint64_t> allocationGetType(uint64_t allocation) = 0;

    /**
     * TODO: more clarification needed describing if the pointer can be aliased
     * or if the data can outlive the allocation.
     *
     * Creates an Allocation for use by scripts with a given Type and a backing
     * pointer. For use with ALLOCATION_USAGE_SHARED.
     *
     * @param type Type describing data layout
     * @param mips AllocationMipmapControl specifies desired mipmap behavior for
     *             the allocation
     * @param usage Bit field specifying how the Allocation is utilized
     * @param ptr Pointer to client-side data
     * @return allocation Created Allocation
     */
    virtual ::android::hardware::Return<uint64_t> allocationCreateTyped(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage, void* ptr) = 0;

    /**
     * Creates an Allocation from a Bitmap.
     *
     * @param type Type describing data layout
     * @param mips AllocationMipmapControl specifies desired mipmap behavior for
     *             the allocation
     * @param bitmap Bitmap source for the allocation data
     * @param usage Bit field specifying how the Allocation is utilized
     * @return allocation Created Allocation containing bitmap data
     */
    virtual ::android::hardware::Return<uint64_t> allocationCreateFromBitmap(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage) = 0;

    /**
     * Creates a Cubemapped Allocation from a Bitmap.
     *
     * @param type Type describing data layout
     * @param mips AllocationMipmapControl specifies desired mipmap behavior
     *             for the allocation
     * @param bitmap Bitmap with cubemap faces layed out in the following
     *               format: right, left, top, bottom, front, back
     * @param usage Bit field specifying how the Allocation is used
     * @return allocation Created Allocation containing cubemap data
     */
    virtual ::android::hardware::Return<uint64_t> allocationCubeCreateFromBitmap(uint64_t type, ::android::hardware::renderscript::V1_0::AllocationMipmapControl mips, const ::android::hardware::hidl_vec<uint8_t>& bitmap, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::AllocationUsageType> usage) = 0;

    /**
     * Returns the handle to a raw buffer that is being managed by the screen
     * compositor. This operation is only valid for Allocations with
     * USAGE_IO_INPUT.
     *
     * @param allocation Allocation
     * @return nativeWindow NativeWindow object associated with allocation
     */
    virtual ::android::hardware::Return<uint64_t> allocationGetNativeWindow(uint64_t allocation) = 0;

    /**
     * TODO: more clarification needed
     *
     * Sets the NativeWindow of an Allocation. This operation is only valid
     * for Allocations with USAGE_IO_INPUT.
     *
     * @param allocation Allocation to be modified
     * @pram nativeWindow NativeWindow to associate with allocation
     */
    virtual ::android::hardware::Return<void> allocationSetNativeWindow(uint64_t allocation, uint64_t nativewindow) = 0;

    /**
     * Initialize BufferQueue with specified max number of buffers.
     *
     * @param alloc Allocation
     * @param numBuffer Maximum number of buffers
     */
    virtual ::android::hardware::Return<void> allocationSetupBufferQueue(uint64_t alloc, uint32_t numBuffer) = 0;

    /**
     * TODO: clearly define baseAlloc vs subAlloc
     *
     * Shares the BufferQueue with another Allocation. Both must be
     * USAGE_IO_INPUT Allocations.
     *
     * @param baseAlloc Base Allocation
     * @param subAlloc Allocation to use the same buffer queue as the Base
     *                 Allocation
     */
    virtual ::android::hardware::Return<void> allocationShareBufferQueue(uint64_t baseAlloc, uint64_t subAlloc) = 0;

    /**
     * Copies from the Allocation into a Bitmap. The bitmap must match the
     * dimensions of the Allocation.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     */
    virtual ::android::hardware::Return<void> allocationCopyToBitmap(uint64_t allocation, void* data, uint64_t sizeBytes) = 0;

    /**
     * TODO: should we consolidate all [123]DWrite functions or [123]DRead
     * functions into the same API call? Our current plan is to be very similar
     * to the dispatch table API. How much should we deviate from the original
     * API?
     * TODO: better description on Vec3/Vec4 and padding.
     *
     * Copies data into a 1D region of this Allocation.
     *
     * When this HAL entry is executed, all Vec3 elements have been explicitly
     * padded as Vec4 elements.
     *
     * The size of the region is: count * Element's size.
     *
     * @param allocation Allocation to be modified
     * @param offset The offset of the first element to be copied
     * @param lod Selected mipmap level of detail
     * @param count Number of elements to be copied
     * @param data Source data to be copied to Allocation
     */
    virtual ::android::hardware::Return<void> allocation1DWrite(uint64_t allocation, uint32_t offset, uint32_t lod, uint32_t count, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Copies a value into a single sub-Element of this Allocation.
     *
     * @param allocation Allocation to be updated
     * @param x X position of the first element in the Allocation to be updated
     * @param y Y position of the first element in the Allocation to be
     *          updated; for a 1D Allocation, this value must be 0
     * @param z Z position of the first element in the Allocation to be
     *          updated; for a 1D or 2D Allocation, this value must be 0
     * @param lod Selected mipmap level of detail
     * @param data Data to be copied from
     * @param compIdx Component number to identify which sub-Element is updated
     */
    virtual ::android::hardware::Return<void> allocationElementWrite(uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t compIdx) = 0;

    /**
     * Copies from an array into a rectangular region in this Allocation.
     *
     * When this HAL entry is executed, all Vec3 elements have been explicitly
     * padded as Vec4 elements.
     *
     * The size of the region is: w * h * Element's size.
     *
     * @param allocation Allocation to be modified
     * @param xoff X offset of the region to update in this Allocation
     * @param yoff Y offset of the region to update in this Allocation
     * @param lod Selected mipmap level of detail
     * @param face AllocationCubemapFace
     * @param w Width of the region to update
     * @param h Height of the region to update
     * @param data Data to be placed into the Allocation
     * @param stride For 1D Allocation, the stride must be the number of bytes
     *               of this Allocation. For 2D and 3D Allocations, the stride
     *               must be the stride in X dimension measuring in bytes.
     */
    virtual ::android::hardware::Return<void> allocation2DWrite(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride) = 0;

    /**
     * Copies from an array into a 3D region in this Allocation.
     *
     * When this HAL entry is executed, all Vec3 elements have been explicitly
     * padded as Vec4 elements.
     *
     * The size of the region is: w * h * d * Element's size.
     *
     * @param allocation Allocation to be modified
     * @param xoff X offset of the region to update in this Allocation
     * @param yoff Y offset of the region to update in this Allocation
     * @param zoff Z offset of the region to update in this Allocation
     * @param lod Selected mipmap level of detail
     * @param w Width of the region to update
     * @param h Height of the region to update
     * @param d Depth of the region to update
     * @param data Data to be placed in the Allocation
     * @param stride For 1D Allocation, the stride must be the number of bytes
     *               of this Allocation. For 2D and 3D Allocations, the stride
     *               must be the stride in X dimension measuring in bytes.
     */
    virtual ::android::hardware::Return<void> allocation3DWrite(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t stride) = 0;

    /**
     * Generates a mipmap chain. This is only valid if the Type of the
     * Allocation includes mipmaps.
     *
     * This function generates a complete set of mipmaps from the top level
     * LOD.
     *
     * If the Allocation is also using other memory spaces, a call to
     * allocationSyncAll(context, allocation, usage) is required.
     *
     * @param allocation Allocation which has its top LOD read and lower LOD
     *                   written to
     */
    virtual ::android::hardware::Return<void> allocationGenerateMipmaps(uint64_t allocation) = 0;

    /**
     * Copies all of an Allocation's data into an array.
     *
     * All Vec3 elements of an Allocation are padded to be Vec4, so the data
     * returned by this function automatically includes padding.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation to be read
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     */
    virtual ::android::hardware::Return<void> allocationRead(uint64_t allocation, void* data, uint64_t sizeBytes) = 0;

    /**
     * Copies a 1D region of this Allocation into an array.
     *
     * All Vec3 elements of an Allocation are padded to be Vec4, so the data
     * returned by this function automatically includes padding.
     *
     * The size of the region is: count * Element's size.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation to be read
     * @param xoff X offset of the first element to be copied
     * @param lod Mipmap level of detail
     * @param count The number of elements to be copied
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     */
    virtual ::android::hardware::Return<void> allocation1DRead(uint64_t allocation, uint32_t xoff, uint32_t lod, uint32_t count, void* data, uint64_t sizeBytes) = 0;

    /**
     * Returns the value of a single sub-Element of this Allocation.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation to be read
     * @param x X position of the first element in the Allocation to be read
     * @param y Y position of the first element in the Allocation to be read
     * @param z Z position of the first element in the Allocation to be read
     * @param lod Mipmap level of detail
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     * @param compIdx Component number to identify which sub-Element is updated
     */
    virtual ::android::hardware::Return<void> allocationElementRead(uint64_t allocation, uint32_t x, uint32_t y, uint32_t z, uint32_t lod, void* data, uint64_t sizeBytes, uint64_t compIdx) = 0;

    /**
     * Copies from a rectangular region in this Allocation to an array.
     *
     * All Vec3 elements of an Allocation are padded to be Vec4, so the data
     * returned by this function automatically includes padding.
     *
     * The size of the region is: w * h * Element's size.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation to be read
     * @param xoff X offset of the region to copy in this array
     * @param yoff Y offset of the region to copy in this array
     * @param lod Mipmap level of detail
     * @param face AllocationCubemapFace
     * @param w Width of the region to copy
     * @param h Height of the region to copy
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     * @param stride For 1D Allocation, the stride must be the number of bytes
     *               of this Allocation. For 2D and 3D Allocations, the stride
     *               must be the stride in X dimension measuring in bytes.
     */
    virtual ::android::hardware::Return<void> allocation2DRead(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t w, uint32_t h, void* data, uint64_t sizeBytes, uint64_t stride) = 0;

    /**
     * Copies from a rectangular cuboid region in this Allocation to an array.
     *
     * All Vec3 elements of an Allocation are padded to be Vec4, so the data
     * returned by this function automatically includes padding.
     *
     * The size of the region is: w * h * d * Element's size.
     *
     * HIDL is always running in Passthrough mode for RenderScript, so the
     * buffer is modified directly by the driver.
     *
     * @param allocation Allocation to be read
     * @param xoff X offset of the region to copy in this array
     * @param yoff Y offset of the region to copy in this array
     * @param zoff Z offset of the region to copy in this array
     * @param lod Mipmap level of detail
     * @param w Width of the region to copy
     * @param h Height of the region to copy
     * @param d Depth of the region to copy
     * @param data Buffer to be copied into
     * @param sizeBytes Size of the buffer pointed to by "data"
     * @param stride For 1D Allocation, the stride must be the number of bytes
     *               of this Allocation. For 2D and 3D Allocations, the stride
     *               must be the stride in X dimension measuring in bytes.
     */
    virtual ::android::hardware::Return<void> allocation3DRead(uint64_t allocation, uint32_t xoff, uint32_t yoff, uint32_t zoff, uint32_t lod, uint32_t w, uint32_t h, uint32_t d, void* data, uint64_t sizeBytes, uint64_t stride) = 0;

    /**
     * Propagates changes from one usage of the Allocation to the other usages
     * of the Allocation.
     *
     * @param allocation First usage of the Allocation
     * @param usageType Allocation usage type
     */
    virtual ::android::hardware::Return<void> allocationSyncAll(uint64_t allocation, ::android::hardware::renderscript::V1_0::AllocationUsageType usageType) = 0;

    /**
     * TODO: describe the functionality of resize1D better
     * TODO: original Java Doc description seems to contradict itself ("with
     * null contents and the region is otherwise undefined")
     * TODO: should "new elements" be "new cells"?
     * TODO: what does "objects are created" mean?
     * TODO: what does "new dimension" mean? IS the type of the resized
     * allocation different than the type before resizing?
     *
     * Resizes a 1D allocation. The contents of the allocation are preserved.
     * If new elements are allocated, objects are created with null contents
     * and the new region is otherwise undefined.
     *
     * If the new region is smaller, the references of any object outside the
     * new region must be released.
     *
     * A new type must be created with the new dimension.
     *
     * @param allocation Allocation to be resized
     * @param dimX New size along the x dimension of the Allocation
     */
    virtual ::android::hardware::Return<void> allocationResize1D(uint64_t allocation, uint32_t dimX) = 0;

    /**
     * TODO: There are allocationCopy2DRange and 3DRange, but no 1DRange. Should
     * the interface be cleaned up more?
     *
     * Copies a rectangular region from an Allocation into a rectangular region
     * in this Allocation.
     *
     * @param dstAlloc Allocation to be updated
     * @param dstXoff X offset of the region to update
     * @param dstYoff Y offset of the region to update
     * @param dstMip Selected mipmap level of the Allocation to update
     * @param dstFace Destination AllocationCubemapFace
     * @param width Width of the region to update
     * @param height Height of the region to update
     * @param srcAlloc Source Allocation, to be read
     * @param srcXoff X offset of the region in the source Allocation
     * @param srcYoff Y offset of the region in the source Allocation
     * @param srcMip Selected mipmap level of the source Allocation
     * @param srcFace Source AllocationCubemapFace
     */
    virtual ::android::hardware::Return<void> allocationCopy2DRange(uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace dstFace, uint32_t width, uint32_t height, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcMip, ::android::hardware::renderscript::V1_0::AllocationCubemapFace srcFace) = 0;

    /**
     * Copies a rectangular cuboid region into the allocation from another
     * Allocation.
     *
     * @param dstAlloc Allocation to be updated
     * @param dstXoff X offset of the region to update
     * @param dstYoff Y offset of the region to update
     * @param dstZoff Z offset of the region to update
     * @param dstMip Selected mipmap level of the Allocation to update
     * @param width Width of the region to update
     * @param height Height of the region to update
     * @param depth Depth of the region to update
     * @param srcAlloc Source Allocation, to be read
     * @param srcXoff Source X offset of the region in the source Allocation
     * @param srcYoff Source Y offset of the region in the source Allocation
     * @param srcZoff Source Z offset of the region in the souce Allocation
     * @param srcMip Selected mipmap level of the Allocation to read
     */
    virtual ::android::hardware::Return<void> allocationCopy3DRange(uint64_t dstAlloc, uint32_t dstXoff, uint32_t dstYoff, uint32_t dstZoff, uint32_t dstMip, uint32_t width, uint32_t height, uint32_t depth, uint64_t srcAlloc, uint32_t srcXoff, uint32_t srcYoff, uint32_t srcZoff, uint32_t srcMip) = 0;

    /**
     * TODO: define buffer and output stream
     *
     * Sends a buffer to the output stream. The contents of the Allocation may
     * be undefined after this operation. This operation is only valid if
     * USAGE_IO_OUTPUT is set on the Allocation.
     *
     * @param allocation Allocation to be sent
     */
    virtual ::android::hardware::Return<void> allocationIoSend(uint64_t allocation) = 0;

    /**
     * Receives the latest input into the Allocation. This operation is only
     * valid if USAGE_IO_INPUT is set on the Allocation, otherwise an error
     * must be reported and no operations may be executed.
     *
     * @param allocation Allocation to be updated
     */
    virtual ::android::hardware::Return<void> allocationIoReceive(uint64_t allocation) = 0;

    /**
     * Return callback for allocationGetPointer
     */
    using allocationGetPointer_cb = std::function<void(void* dataPtr, uint64_t stride)>;
    /**
     * TODO: describe default values for lod, face, and z better.
     * TODO: what cases can invalidate the pointer? Resize? It should be
     * clarified that this method should always return a valid pointer, but the
     * returned pointer might become invalid later.
     *
     * Retrieves the pointer to the actual data an Allocation contains as well
     * as the data's stride.
     *
     * If Allocation lacks the corresponding dimension for lod, face, or z, an
     * error message must be sent to the message queue and nullptr must be
     * returned for dataPtr and 0 for stride. All missing values must be 0 or
     * NONE in the corresponding enum.
     *
     * @param allocation Allocation
     * @param lod Mipmap level of detail
     * @param face AllocationCubemapFace
     * @param z Z position
     * @return pointer Pointer to the server-side data; if this points to an
     *                 invalid location in memory (because the buffer was
     *                 freed), this may result in undefined behavior
     * @return stride For 1D Allocation, the stride must be the number of bytes
     *                of this Allocation. For 2D and 3D Allocations, the stride
     *                must be the stride in X dimension measuring in bytes.
     */
    virtual ::android::hardware::Return<void> allocationGetPointer(uint64_t allocation, uint32_t lod, ::android::hardware::renderscript::V1_0::AllocationCubemapFace face, uint32_t z, allocationGetPointer_cb _hidl_cb) = 0;

    /**
     * Return callback for elementGetNativeMetadata
     */
    using elementGetNativeMetadata_cb = std::function<void(const ::android::hardware::hidl_vec<uint32_t>& elemData)>;
    /**
     * Retrieves an Element's metadata from native code.
     *
     * @param element Element to be read
     * @return elemData Element data
     */
    virtual ::android::hardware::Return<void> elementGetNativeMetadata(uint64_t element, elementGetNativeMetadata_cb _hidl_cb) = 0;

    /**
     * Return callback for elementGetSubElements
     */
    using elementGetSubElements_cb = std::function<void(const ::android::hardware::hidl_vec<uint64_t>& ids, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& names, const ::android::hardware::hidl_vec<uint64_t>& arraySizes)>;
    /**
     * TODO: define Sub-Element handles better.
     *
     * Retrieves an Element's sub Elements, specifically their identifiers,
     * names, and sizes.
     *
     * @param element Element to be read
     * @param numSubElem Number of sub-Elements
     * @return ids Sub-Element handles
     * @return names Sub-Element Names
     * @return arraySizes Sizes of sub-Element arrays
     */
    virtual ::android::hardware::Return<void> elementGetSubElements(uint64_t element, uint64_t numSubElem, elementGetSubElements_cb _hidl_cb) = 0;

    /**
     * TODO: can normalization flag be removed?
     *
     * Creates an Element.
     *
     * @param dt Data type
     * @param dk Data kind
     * @param norm Flag for normalization
     * @param size Vector length, with scalar = 1
     * @return element Created Element
     */
    virtual ::android::hardware::Return<uint64_t> elementCreate(::android::hardware::renderscript::V1_0::DataType dt, ::android::hardware::renderscript::V1_0::DataKind dk, bool norm, uint32_t size) = 0;

    /**
     * Creates a complex Element.
     *
     * @param einsPtr Container of input Elements
     * @param namesPtr Container of input names
     * @param arraySizesPtr Container of array sizes
     * @return element Created Element
     */
    virtual ::android::hardware::Return<uint64_t> elementComplexCreate(const ::android::hardware::hidl_vec<uint64_t>& einsPtr, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& names, const ::android::hardware::hidl_vec<uint64_t>& arraySizesPtr) = 0;

    /**
     * Return callback for typeGetNativeMetadata
     */
    using typeGetNativeMetadata_cb = std::function<void(const ::android::hardware::hidl_vec<uint64_t>& metadata)>;
    /**
     * Retrives a Type's metadata from native code.
     *
     * @param type Type describing data layout
     * @return metadata Type's native metadata
     */
    virtual ::android::hardware::Return<void> typeGetNativeMetadata(uint64_t type, typeGetNativeMetadata_cb _hidl_cb) = 0;

    /**
     * Creates a new Type.
     *
     * If Type is 1D, Y and Z must be 0. If Type is 2D, Z must be 0.
     *
     * @param element Element of the Type
     * @param dimX X dimension
     * @param dimY Y dimension
     * @param dimZ Z dimension
     * @param mipmaps Flag indicating whether Type has mipmaps
     * @param faces Flag indicating whether Type has faces
     * @param yuv Enumeration specifying which type of YUV format, if any, Type
     *            uses
     * @return type Created Type
     */
    virtual ::android::hardware::Return<uint64_t> typeCreate(uint64_t element, uint32_t dimX, uint32_t dimY, uint32_t dimZ, bool mipmaps, bool faces, ::android::hardware::renderscript::V1_0::YuvFormat yuv) = 0;

    /**
     * Destroys provided RenderScript context, including all objects created in
     * this context.
     */
    virtual ::android::hardware::Return<void> contextDestroy() = 0;

    /**
     * Return callback for contextGetMessage
     */
    using contextGetMessage_cb = std::function<void(::android::hardware::renderscript::V1_0::MessageToClientType messageType, uint64_t receiveLen)>;
    /**
     * TODO: provide overview of messaging model and figure out if this should
     * be part of HAL or not.
     * TODO: what is the "client" for purposes of this interface?
     * TODO: consider using send/receive to be more similar to other calls
     * TODO: define the purpose of size more
     *
     * Fills the provided buffer with message data. "size" should be at least
     * as large as the message size. Returns the MessageType and size of the
     * message are returned.
     *
     * @param data A pointer to a buffer to be filled with a message
     * @param size Size in bytes of the buffer pointed to by "data"
     * @return messageType Type of message sent to the client
     * @return receiveLen Length of the message in bytes
     */
    virtual ::android::hardware::Return<void> contextGetMessage(void* data, uint64_t size, contextGetMessage_cb _hidl_cb) = 0;

    /**
     * Return callback for contextPeekMessage
     */
    using contextPeekMessage_cb = std::function<void(::android::hardware::renderscript::V1_0::MessageToClientType messageType, uint64_t receiveLen, uint32_t subID)>;
    /**
     * TODO: define subID better.
     *
     * Gets the metadata of a message to ensure entire message can be properly
     * received. Can be used to determine size of data to allocate when calling
     * contextGetMessage.
     *
     * @return messageType Type of message sent to the client
     * @return receiveLen Length of message
     * @return subID Message sub identifier
     */
    virtual ::android::hardware::Return<void> contextPeekMessage(contextPeekMessage_cb _hidl_cb) = 0;

    /**
     * TODO: Define "previous commands" better
     * TODO: Is the message identifier the same as subID?
     *
     * Places a message into the message queue to be sent back to the message
     * handler once all previous commands have been executed. The message data
     * is copied into the queue and can be discarded by the client after this
     * call.
     *
     * @param id Message identifier
     * @param data Message data
     */
    virtual ::android::hardware::Return<void> contextSendMessage(uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * TODO: Can this be done automatically as part of context creation? What
     * happens if we perform message operations before doing this?
     *
     * Initializes the messaging thread, so that the front-end API can receive
     * messages from the driver. This call also waits for the messaging FIFO to
     * start up.
     */
    virtual ::android::hardware::Return<void> contextInitToClient() = 0;

    /**
     * TODO: Why doesn't this happen automatically as part of context
     * destruction? What happens if the FIFO is not empty?
     *
     * Deinitializes a the messaging thread. Shuts down the FIFO.
     */
    virtual ::android::hardware::Return<void> contextDeinitToClient() = 0;

    /**
     * TODO: do we need to mark asynchronous operations in this interface
     * definition?
     *
     * Waits for any pending asynchronous operations (such as copies to a RS
     * allocation or RS script executions) to complete.
     */
    virtual ::android::hardware::Return<void> contextFinish() = 0;

    /**
     * Prints the currently available debugging information about the state of
     * the RS context to the logcat.
     */
    virtual ::android::hardware::Return<void> contextLog() = 0;

    /**
     * TODO: full path? relative path? Investigate further.
     *
     * Sets the cache directory of the context.
     *
     * @param cacheDir Name of the application's cache directory
     */
    virtual ::android::hardware::Return<void> contextSetCacheDir(const ::android::hardware::hidl_string& cacheDir) = 0;

    /**
     * TODO: does this apply to the GPU as well?
     *
     * Changes the priority of the cpu worker threads for this context.
     *
     * @param priority Priority of the thread
     */
    virtual ::android::hardware::Return<void> contextSetPriority(::android::hardware::renderscript::V1_0::ThreadPriorities priority) = 0;

    /**
     * TODO: does this need to be part of the HAL? What if the object already
     * has a name?
     *
     * Assigns a name to a base object.
     *
     * @param obj Object to be named
     * @param name Assigned name
     */
    virtual ::android::hardware::Return<void> assignName(uint64_t obj, const ::android::hardware::hidl_string& name) = 0;

    /**
     * Return callback for getName
     */
    using getName_cb = std::function<void(const ::android::hardware::hidl_string& name)>;
    /**
     * TODO: what if the object has no name?
     *
     * Returns the name of an object.
     *
     * @param obj Object to be read
     * @return name Name of the object
     */
    virtual ::android::hardware::Return<void> getName(uint64_t obj, getName_cb _hidl_cb) = 0;

    /**
     * TODO: starting here we have a set of interfaces for use with
     * ScriptGroups. At the very least we should indicate for each one that's
     * what it's for. Should we include ScriptGroup in the interface names?
     * TODO: sweep whole file and remove prefix "v" from all parameter names
     * TODO: there are some places where we use Size for size, and others where
     * we use int32_t. Is there a reason it's int32_t? In some cases, it
     * requires a negative value.
     *
     * Creates a Closure which represents a function call to a ForEach Kernel
     * combined with arguments and values for global variables.
     *
     * @param kernelID Kernel identifier
     * @param returnValue Allocation used in output of Closure
     * @param fieldIDS Collection of Script's Field identifiers
     * @param values Collection of Script's data values
     * @param sizes Collection of Script's data sizes
     * @param depClosures Collection of Closures
     * @param depFieldIDS Collection of Script's dependent Field identifiers
     * @return closure Created Closure
     */
    virtual ::android::hardware::Return<uint64_t> closureCreate(uint64_t kernelID, uint64_t returnValue, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes, const ::android::hardware::hidl_vec<uint64_t>& depClosures, const ::android::hardware::hidl_vec<uint64_t>& depFieldIDS) = 0;

    /**
     * Creates a Closure which represents a function call to a invocable
     * function, combined with arguments and values for global variables.
     *
     * @param invokeID Invokable function identifier
     * @param params Collection of Invoke script parameters
     * @param fieldIDS Collection of Script Field identifiers
     * @param values Collection of values
     * @param sizes Collection of sizes
     * @return closure Created Closure
     */
    virtual ::android::hardware::Return<uint64_t> invokeClosureCreate(uint64_t invokeID, const ::android::hardware::hidl_vec<uint8_t>& params, const ::android::hardware::hidl_vec<uint64_t>& fieldIDS, const ::android::hardware::hidl_vec<int64_t>& values, const ::android::hardware::hidl_vec<int32_t>& sizes) = 0;

    /**
     * Sets the argument of a Closure at specified index and size to provided
     * value.
     *
     * @param closure Closure to be modified
     * @param index Index
     * @param value Value
     * @param size Size
     */
    virtual ::android::hardware::Return<void> closureSetArg(uint64_t closure, uint32_t index, void* value, int32_t size) = 0;

    /**
     * Sets a global variable in a Closure.
     *
     * @param closure Closure
     * @param fieldID Global's Field identifier
     * @param value Value
     * @param size Size
     */
    virtual ::android::hardware::Return<void> closureSetGlobal(uint64_t closure, uint64_t fieldID, int64_t value, int32_t size) = 0;

    /**
     * TODO: should slot be unsigned? (applies to other two ID interfaces, too)
     *
     * Creates a Script Kernel ID.
     *
     * @param script Script
     * @param slot Slot
     * @param sig Bitfield describing Kernel signature and operation
     * @return scriptKernelID Script's Kernel identifier
     */
    virtual ::android::hardware::Return<uint64_t> scriptKernelIDCreate(uint64_t script, int32_t slot, ::android::hardware::hidl_bitfield<::android::hardware::renderscript::V1_0::MetadataSignatureBitval> sig) = 0;

    /**
     * Creates a Script Invoke ID.
     *
     * @param script Script
     * @param slot Slot
     * @return scriptInvokeID Invoke Script's identifier
     */
    virtual ::android::hardware::Return<uint64_t> scriptInvokeIDCreate(uint64_t script, int32_t slot) = 0;

    /**
     * TODO: describe the return value better. What is it?
     *
     * Creates a Script Field ID.
     *
     * @param script Script
     * @param slot Slot
     * @return scriptFieldID Script's Field identifier
     */
    virtual ::android::hardware::Return<uint64_t> scriptFieldIDCreate(uint64_t script, int32_t slot) = 0;

    /**
     * TODO: add more description
     *
     * Creates a Script Group.
     *
     * @param kernels Collection of Scripts' Kernel identifiers
     * @param srcK Source Kernel identifiers
     * @param dstK Destination Kernel identifiers
     * @param dstF Destination Script Field identifiers
     * @param types Collection of Types describing data layout
     * @return scriptGroup Created Script Group
     */
    virtual ::android::hardware::Return<uint64_t> scriptGroupCreate(const ::android::hardware::hidl_vec<uint64_t>& kernels, const ::android::hardware::hidl_vec<uint64_t>& srcK, const ::android::hardware::hidl_vec<uint64_t>& dstK, const ::android::hardware::hidl_vec<uint64_t>& dstF, const ::android::hardware::hidl_vec<uint64_t>& types) = 0;

    /**
     * Creates a Script Group.
     *
     * @param name Name
     * @param cacheDir Cache directory
     * @param closures Collection of Closures
     * @return scriptGroup2 Created Script Group
     */
    virtual ::android::hardware::Return<uint64_t> scriptGroup2Create(const ::android::hardware::hidl_string& name, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint64_t>& closures) = 0;

    /**
     * TODO: if SetInput/Output corresponds to the Java API setInput() and
     * setOutput(), which are documented as deprecated in API 23, do we need to
     * support them? Or can we fallback to the CPU when they're used? Or can't
     * we tell whether they're used early enough to do fallback?
     *
     * Sets an output of the ScriptGroup. This specifies an Allocation to be
     * used for the kernels that require an output Allocation visible after the
     * ScriptGroup is executed.
     *
     * @param sg Script Group
     * @param kid Script's Kernel identifier to be changed
     * @param alloc Allocation to be filled by output
     */
    virtual ::android::hardware::Return<void> scriptGroupSetOutput(uint64_t sg, uint64_t kid, uint64_t alloc) = 0;

    /**
     * Sets an input of the Script Group. This specifies an Allocation to be
     * used for kernels that require an input Allocation provided from outside
     * of the Script Group.
     *
     * @param sg Script Group
     * @param kid Script's Kernel identifier to be changed
     * @param alloc Allocation to be read as input
     */
    virtual ::android::hardware::Return<void> scriptGroupSetInput(uint64_t sg, uint64_t kid, uint64_t alloc) = 0;

    /**
     * Executes a Script Group.
     *
     * @param sg Script Group to be executed.
     */
    virtual ::android::hardware::Return<void> scriptGroupExecute(uint64_t sg) = 0;

    /**
     * Frees any native resources associated with this object. The primary use
     * is to force immediate cleanup of resources when it is believed the GC
     * may not respond quickly enough.
     *
     * @param handle Opaque handle to the server-side object to be destroyed
     */
    virtual ::android::hardware::Return<void> objDestroy(uint64_t obj) = 0;

    /**
     * Creates a Sampler.
     *
     * @param magFilter Magnification value for the filter
     * @param minFilter Minification value for the filter
     * @param wrapS S wrapping mode for the sampler
     * @param wrapT T wrapping mode for the sampler
     * @param wrapR R wrapping mode for the sampler
     * @param aniso Anisotropy setting for the sampler
     * @return sampler Created Sampler
     */
    virtual ::android::hardware::Return<uint64_t> samplerCreate(::android::hardware::renderscript::V1_0::SamplerValue magFilter, ::android::hardware::renderscript::V1_0::SamplerValue minFilter, ::android::hardware::renderscript::V1_0::SamplerValue wrapS, ::android::hardware::renderscript::V1_0::SamplerValue wrapT, ::android::hardware::renderscript::V1_0::SamplerValue wrapR, float aniso) = 0;

    /**
     * Binds an Allocation to a global pointer in the Script.
     *
     * @param script Script to be bound to
     * @param allocation Allocation to be bound
     * @param slot Slot of a global variable
     */
    virtual ::android::hardware::Return<void> scriptBindAllocation(uint64_t script, uint64_t allocation, uint32_t slot) = 0;

    /**
     * TODO: is this necessary?
     *
     * Sets the timezone of a Script.
     *
     * @param script Script to be altered
     * @param timeZone Time Zone value as text
     */
    virtual ::android::hardware::Return<void> scriptSetTimeZone(uint64_t script, const ::android::hardware::hidl_string& timeZone) = 0;

    /**
     * TODO: can scriptInvoke be combined with scriptInvokeV?
     *
     * Launches an invokable function.
     *
     * @param vs Script to be invoked
     * @param slot Slot of invokable function
     */
    virtual ::android::hardware::Return<void> scriptInvoke(uint64_t vs, uint32_t slot) = 0;

    /**
     * Invokes a Script with values.
     *
     * @param vs Script to be invoked
     * @param slot Slot
     * @param data Data buffer of packed arguments
     */
    virtual ::android::hardware::Return<void> scriptInvokeV(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * TODO: add documentation for params
     * TODO: Should we rename "ScriptCall" to "LaunchOptions"?
     *
     * Launches a ForEach kernel.
     *
     * @param vs Script
     * @param slot Slot of ForEach Kernel
     * @param vains Collection of input Allocations or null
     * @param vaout Output Allocation or null
     * @param params Collection of parameters
     * @param sc Pointer to a ScriptCall, nullptr if unused
     */
    virtual ::android::hardware::Return<void> scriptForEach(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, const ::android::hardware::hidl_vec<uint8_t>& params, void* sc) = 0;

    /**
     * Launches a Reduction kernel.
     *
     * @param vs Script
     * @param slot Slot of Reduction Kernel
     * @param vains Collection of input Allocations
     * @param vaout Output Allocation
     * @param sc Pointer to a ScriptCall, nullptr if unused
     */
    virtual ::android::hardware::Return<void> scriptReduce(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint64_t>& vains, uint64_t vaout, void* sc) = 0;

    /**
     * Sets a Script's integer variable to a value.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param value Value to be pushed to variable
     */
    virtual ::android::hardware::Return<void> scriptSetVarI(uint64_t vs, uint32_t slot, int32_t value) = 0;

    /**
     * Sets a Script's Object variable to a value
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param obj ObjectBase
     */
    virtual ::android::hardware::Return<void> scriptSetVarObj(uint64_t vs, uint32_t slot, uint64_t obj) = 0;

    /**
     * Sets a Script's long variable to a value.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param value Value to be pushed to variable
     */
    virtual ::android::hardware::Return<void> scriptSetVarJ(uint64_t vs, uint32_t slot, int64_t value) = 0;

    /**
     * Sets a Script's float variable to a value.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param value Value to be pushed to variable
     */
    virtual ::android::hardware::Return<void> scriptSetVarF(uint64_t vs, uint32_t slot, float value) = 0;

    /**
     * Sets a Script's double variable to a value.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param value Value to be pushed to variable
     */
    virtual ::android::hardware::Return<void> scriptSetVarD(uint64_t vs, uint32_t slot, double value) = 0;

    /**
     * Sets a Script's struct variable to a value.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param data Data to be pushed to variable
     */
    virtual ::android::hardware::Return<void> scriptSetVarV(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Return callback for scriptGetVarV
     */
    using scriptGetVarV_cb = std::function<void(const ::android::hardware::hidl_vec<uint8_t>& data)>;
    /**
     * TODO: Why do we have typed setters but only untyped getter?
     *
     * Retrieves the value from a global variable in a script.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be read
     * @param len Size of data to be filled
     * @return data Data to be updated
     */
    virtual ::android::hardware::Return<void> scriptGetVarV(uint64_t vs, uint32_t slot, uint64_t len, scriptGetVarV_cb _hidl_cb) = 0;

    /**
     * TODO: Is this a value to be replicated for each member of the array? Or
     * is there a representation for each separate member?
     *
     * Sets the value of a global array of structs, given the Element and
     * dimension.
     *
     * @param vs RenderScript Script
     * @param slot Slot number of variable to be updated
     * @param data Data
     * @param ve Element
     * @param dims Collection of dimensions
     */
    virtual ::android::hardware::Return<void> scriptSetVarVE(uint64_t vs, uint32_t slot, const ::android::hardware::hidl_vec<uint8_t>& data, uint64_t ve, const ::android::hardware::hidl_vec<uint32_t>& dims) = 0;

    /**
     * TODO: is cacheDir redundant with createCache() function? Can we remove
     * it?
     * TODO: define resName more clearly
     *
     * Creates a RenderScript C99 kernel script.
     *
     * @param resName Resource name of the bitcode
     * @param cacheDir Cache directory name
     * @param text The kernel's bitcode as a uint8_t vector
     * @return script Created Script
     */
    virtual ::android::hardware::Return<uint64_t> scriptCCreate(const ::android::hardware::hidl_string& resName, const ::android::hardware::hidl_string& cacheDir, const ::android::hardware::hidl_vec<uint8_t>& text) = 0;

    /**
     * Creates a RenderScript Intrinsic script.
     *
     * @param id Intrinsic Script identifier
     * @param elem Element
     * @return script Created Script
     */
    virtual ::android::hardware::Return<uint64_t> scriptIntrinsicCreate(::android::hardware::renderscript::V1_0::ScriptIntrinsicID id, uint64_t elem) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::renderscript::V1_0::IContext>> castFrom(const ::android::sp<::android::hardware::renderscript::V1_0::IContext>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::renderscript::V1_0::IContext>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<IContext> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IContext> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IContext> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IContext> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IContext> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IContext> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IContext> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IContext> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::sp<::android::hardware::renderscript::V1_0::IContext>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::renderscript::V1_0::IContext>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::renderscript::V1_0::IContext::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace renderscript
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RENDERSCRIPT_V1_0_ICONTEXT_H
