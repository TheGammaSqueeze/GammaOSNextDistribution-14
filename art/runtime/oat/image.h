/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_OAT_IMAGE_H_
#define ART_RUNTIME_OAT_IMAGE_H_

#include <string.h>

#include "base/enums.h"
#include "base/iteration_range.h"
#include "base/macros.h"
#include "base/os.h"
#include "base/unix_file/fd_file.h"
#include "mirror/object.h"
#include "runtime_globals.h"

namespace art HIDDEN {

class ArtField;
class ArtMethod;
class ImageFileGuard;

template <class MirrorType> class ObjPtr;

namespace linker {
class ImageWriter;
}  // namespace linker

class ObjectVisitor {
 public:
  virtual ~ObjectVisitor() {}

  virtual void Visit(mirror::Object* object) = 0;
};

class PACKED(4) ImageSection {
 public:
  ImageSection() : offset_(0), size_(0) { }
  ImageSection(uint32_t offset, uint32_t size) : offset_(offset), size_(size) { }
  ImageSection(const ImageSection& section) = default;
  ImageSection& operator=(const ImageSection& section) = default;

  uint32_t Offset() const {
    return offset_;
  }

  uint32_t Size() const {
    return size_;
  }

  uint32_t End() const {
    return Offset() + Size();
  }

  bool Contains(uint64_t offset) const {
    return offset - offset_ < size_;
  }

 private:
  uint32_t offset_;
  uint32_t size_;
};

// Header of image files written by ImageWriter, read and validated by Space.
// Packed to object alignment since the first object follows directly after the header.
static_assert(kObjectAlignment == 8, "Alignment check");
class PACKED(8) ImageHeader {
 public:
  enum StorageMode : uint32_t {
    kStorageModeUncompressed,
    kStorageModeLZ4,
    kStorageModeLZ4HC,
    kStorageModeCount,  // Number of elements in enum.
  };
  static constexpr StorageMode kDefaultStorageMode = kStorageModeUncompressed;

  // Solid block of the image. May be compressed or uncompressed.
  class PACKED(4) Block final {
   public:
    Block(StorageMode storage_mode,
          uint32_t data_offset,
          uint32_t data_size,
          uint32_t image_offset,
          uint32_t image_size)
        : storage_mode_(storage_mode),
          data_offset_(data_offset),
          data_size_(data_size),
          image_offset_(image_offset),
          image_size_(image_size) {}

    bool Decompress(uint8_t* out_ptr, const uint8_t* in_ptr, std::string* error_msg) const;

    StorageMode GetStorageMode() const {
      return storage_mode_;
    }

    uint32_t GetDataSize() const {
      return data_size_;
    }

    uint32_t GetImageSize() const {
      return image_size_;
    }

   private:
    // Storage method for the image, the image may be compressed.
    StorageMode storage_mode_ = kDefaultStorageMode;

    // Compressed offset and size.
    uint32_t data_offset_ = 0u;
    uint32_t data_size_ = 0u;

    // Image offset and size (decompressed or mapped location).
    uint32_t image_offset_ = 0u;
    uint32_t image_size_ = 0u;
  };

  ImageHeader() {}
  EXPORT ImageHeader(uint32_t image_reservation_size,
                     uint32_t component_count,
                     uint32_t image_begin,
                     uint32_t image_size,
                     ImageSection* sections,
                     uint32_t image_roots,
                     uint32_t oat_checksum,
                     uint32_t oat_file_begin,
                     uint32_t oat_data_begin,
                     uint32_t oat_data_end,
                     uint32_t oat_file_end,
                     uint32_t boot_image_begin,
                     uint32_t boot_image_size,
                     uint32_t boot_image_component_count,
                     uint32_t boot_image_checksum,
                     uint32_t pointer_size);

  EXPORT bool IsValid() const;
  EXPORT const char* GetMagic() const;

  uint32_t GetImageReservationSize() const {
    return image_reservation_size_;
  }

  uint32_t GetComponentCount() const {
    return component_count_;
  }

  uint8_t* GetImageBegin() const {
    return reinterpret_cast<uint8_t*>(image_begin_);
  }

  size_t GetImageSize() const {
    return image_size_;
  }

  uint32_t GetImageChecksum() const {
    return image_checksum_;
  }

  void SetImageChecksum(uint32_t image_checksum) {
    image_checksum_ = image_checksum;
  }

  uint32_t GetOatChecksum() const {
    return oat_checksum_;
  }

  void SetOatChecksum(uint32_t oat_checksum) {
    oat_checksum_ = oat_checksum;
  }

  // The location that the oat file was expected to be when the image was created. The actual
  // oat file may be at a different location for application images.
  uint8_t* GetOatFileBegin() const {
    return reinterpret_cast<uint8_t*>(oat_file_begin_);
  }

  uint8_t* GetOatDataBegin() const {
    return reinterpret_cast<uint8_t*>(oat_data_begin_);
  }

  uint8_t* GetOatDataEnd() const {
    return reinterpret_cast<uint8_t*>(oat_data_end_);
  }

  uint8_t* GetOatFileEnd() const {
    return reinterpret_cast<uint8_t*>(oat_file_end_);
  }

  EXPORT PointerSize GetPointerSize() const;

  uint32_t GetPointerSizeUnchecked() const {
    return pointer_size_;
  }

  static std::string GetOatLocationFromImageLocation(const std::string& image) {
    return GetLocationFromImageLocation(image, "oat");
  }

  static std::string GetVdexLocationFromImageLocation(const std::string& image) {
    return GetLocationFromImageLocation(image, "vdex");
  }

  enum ImageMethod {
    kResolutionMethod,
    kImtConflictMethod,
    kImtUnimplementedMethod,
    kSaveAllCalleeSavesMethod,
    kSaveRefsOnlyMethod,
    kSaveRefsAndArgsMethod,
    kSaveEverythingMethod,
    kSaveEverythingMethodForClinit,
    kSaveEverythingMethodForSuspendCheck,
    kImageMethodsCount,  // Number of elements in enum.
  };

  enum ImageRoot {
    kDexCaches,
    kClassRoots,
    kSpecialRoots,                    // Different for boot image and app image, see aliases below.
    kImageRootsMax,

    // Aliases.
    kAppImageClassLoader = kSpecialRoots,   // The class loader used to build the app image.
    kBootImageLiveObjects = kSpecialRoots,  // Array of boot image objects that must be kept live.
    kAppImageOatHeader = kSpecialRoots,     // A byte array containing 1) a fake OatHeader to check
                                            // if the image can be loaded against the current
                                            // runtime, and 2) the dex checksums.
  };

  enum BootImageLiveObjects {
    kOomeWhenThrowingException,       // Pre-allocated OOME when throwing exception.
    kOomeWhenThrowingOome,            // Pre-allocated OOME when throwing OOME.
    kOomeWhenHandlingStackOverflow,   // Pre-allocated OOME when handling StackOverflowError.
    kNoClassDefFoundError,            // Pre-allocated NoClassDefFoundError.
    kClearedJniWeakSentinel,          // Pre-allocated sentinel for cleared weak JNI references.
    kIntrinsicObjectsStart
  };

  /*
   * This describes the number and ordering of sections inside of Boot
   * and App Images.  It is very important that changes to this struct
   * are reflected in the compiler and loader.
   *
   * See:
   *   - ImageWriter::ImageInfo::CreateImageSections()
   *   - ImageWriter::Write()
   *   - ImageWriter::AllocMemory()
   */
  enum ImageSections {
    kSectionObjects,
    kSectionArtFields,
    kSectionArtMethods,
    kSectionRuntimeMethods,
    kSectionImTables,
    kSectionIMTConflictTables,
    kSectionInternedStrings,
    kSectionClassTable,
    kSectionStringReferenceOffsets,
    kSectionDexCacheArrays,
    kSectionMetadata,
    kSectionImageBitmap,
    kSectionCount,  // Number of elements in enum.
  };

  static size_t NumberOfImageRoots([[maybe_unused]] bool app_image) {
    // At the moment, boot image and app image have the same number of roots,
    // though the meaning of the kSpecialRoots is different.
    return kImageRootsMax;
  }

  EXPORT ArtMethod* GetImageMethod(ImageMethod index) const;

  EXPORT static const char* GetImageSectionName(ImageSections index);

  ImageSection& GetImageSection(ImageSections index) {
    DCHECK_LT(static_cast<size_t>(index), kSectionCount);
    return sections_[index];
  }

  const ImageSection& GetImageSection(ImageSections index) const {
    DCHECK_LT(static_cast<size_t>(index), kSectionCount);
    return sections_[index];
  }

  const ImageSection& GetObjectsSection() const {
    return GetImageSection(kSectionObjects);
  }

  const ImageSection& GetFieldsSection() const {
    return GetImageSection(ImageHeader::kSectionArtFields);
  }

  const ImageSection& GetMethodsSection() const {
    return GetImageSection(kSectionArtMethods);
  }

  const ImageSection& GetRuntimeMethodsSection() const {
    return GetImageSection(kSectionRuntimeMethods);
  }

  const ImageSection& GetImTablesSection() const {
    return GetImageSection(kSectionImTables);
  }

  const ImageSection& GetIMTConflictTablesSection() const {
    return GetImageSection(kSectionIMTConflictTables);
  }

  const ImageSection& GetInternedStringsSection() const {
    return GetImageSection(kSectionInternedStrings);
  }

  const ImageSection& GetClassTableSection() const {
    return GetImageSection(kSectionClassTable);
  }

  const ImageSection& GetImageStringReferenceOffsetsSection() const {
    return GetImageSection(kSectionStringReferenceOffsets);
  }

  const ImageSection& GetMetadataSection() const {
    return GetImageSection(kSectionMetadata);
  }

  const ImageSection& GetImageBitmapSection() const {
    return GetImageSection(kSectionImageBitmap);
  }

  template <ReadBarrierOption kReadBarrierOption = kWithReadBarrier>
  ObjPtr<mirror::Object> GetImageRoot(ImageRoot image_root) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  template <ReadBarrierOption kReadBarrierOption = kWithReadBarrier>
  ObjPtr<mirror::ObjectArray<mirror::Object>> GetImageRoots() const
      REQUIRES_SHARED(Locks::mutator_lock_);

  void RelocateImageReferences(int64_t delta);
  void RelocateBootImageReferences(int64_t delta);

  uint32_t GetBootImageBegin() const {
    return boot_image_begin_;
  }

  uint32_t GetBootImageSize() const {
    return boot_image_size_;
  }

  uint32_t GetBootImageComponentCount() const {
    return boot_image_component_count_;
  }

  uint32_t GetBootImageChecksum() const {
    return boot_image_checksum_;
  }

  uint64_t GetDataSize() const {
    return data_size_;
  }

  EXPORT bool IsAppImage() const;

  EXPORT uint32_t GetImageSpaceCount() const;

  // Visit mirror::Objects in the section starting at base.
  // TODO: Delete base parameter if it is always equal to GetImageBegin.
  EXPORT void VisitObjects(ObjectVisitor* visitor, uint8_t* base, PointerSize pointer_size) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit ArtMethods in the section starting at base. Includes runtime methods.
  // TODO: Delete base parameter if it is always equal to GetImageBegin.
  // NO_THREAD_SAFETY_ANALYSIS for template visitor pattern.
  template <typename Visitor>
  void VisitPackedArtMethods(const Visitor& visitor,
                             uint8_t* base,
                             PointerSize pointer_size) const NO_THREAD_SAFETY_ANALYSIS;

  // Visit ArtMethods in the section starting at base.
  // TODO: Delete base parameter if it is always equal to GetImageBegin.
  // NO_THREAD_SAFETY_ANALYSIS for template visitor pattern.
  template <typename Visitor>
  void VisitPackedArtFields(const Visitor& visitor, uint8_t* base) const NO_THREAD_SAFETY_ANALYSIS;

  template <typename Visitor>
  void VisitPackedImTables(const Visitor& visitor,
                           uint8_t* base,
                           PointerSize pointer_size) const;

  template <typename Visitor>
  void VisitPackedImtConflictTables(const Visitor& visitor,
                                    uint8_t* base,
                                    PointerSize pointer_size) const;

  IterationRange<const Block*> GetBlocks() const {
    return GetBlocks(GetImageBegin());
  }

  IterationRange<const Block*> GetBlocks(const uint8_t* image_begin) const {
    const Block* begin = reinterpret_cast<const Block*>(image_begin + blocks_offset_);
    return {begin, begin + blocks_count_};
  }

  // Return true if the image has any compressed blocks.
  bool HasCompressedBlock() const {
    return blocks_count_ != 0u;
  }

  uint32_t GetBlockCount() const {
    return blocks_count_;
  }

  // Helper for writing `data` and `bitmap_data` into `image_file`, following
  // the information stored in this header and passed as arguments.
  EXPORT bool WriteData(const ImageFileGuard& image_file,
                        const uint8_t* data,
                        const uint8_t* bitmap_data,
                        ImageHeader::StorageMode image_storage_mode,
                        uint32_t max_image_block_size,
                        bool update_checksum,
                        std::string* error_msg);

 private:
  static const uint8_t kImageMagic[4];
  static const uint8_t kImageVersion[4];

  static std::string GetLocationFromImageLocation(const std::string& image,
                                                  const std::string& extension) {
    std::string filename = image;
    if (filename.length() <= 3) {
      filename += "." + extension;
    } else {
      filename.replace(filename.length() - 3, 3, extension);
    }
    return filename;
  }

  uint8_t magic_[4];
  uint8_t version_[4];

  // The total memory reservation size for the image.
  // For boot image or boot image extension, the primary image includes the reservation
  // for all image files and oat files, secondary images have the reservation set to 0.
  // App images have reservation equal to `image_size_` rounded up to page size because
  // their oat files are mmapped independently.
  uint32_t image_reservation_size_ = 0u;

  // The number of components (jar files contributing to the image).
  // For boot image or boot image extension, the primary image stores the total number
  // of components, secondary images have this set to 0. App images have 1 component.
  // The component count usually matches the total number of images (one image per component), but
  // if multiple components are compiled with --single-image there will only be 1 image associated
  // with those components.
  uint32_t component_count_ = 0u;

  // Required base address for mapping the image.
  uint32_t image_begin_ = 0u;

  // Image size, not page aligned.
  uint32_t image_size_ = 0u;

  // Image file checksum (calculated with the checksum field set to 0).
  uint32_t image_checksum_ = 0u;

  // Checksum of the oat file we link to for load time consistency check.
  uint32_t oat_checksum_ = 0u;

  // Start address for oat file. Will be before oat_data_begin_ for .so files.
  uint32_t oat_file_begin_ = 0u;

  // Required oat address expected by image Method::GetCode() pointers.
  uint32_t oat_data_begin_ = 0u;

  // End of oat data address range for this image file.
  uint32_t oat_data_end_ = 0u;

  // End of oat file address range. will be after oat_data_end_ for
  // .so files. Used for positioning a following alloc spaces.
  uint32_t oat_file_end_ = 0u;

  // Boot image begin and end (only applies to boot image extension and app image headers).
  uint32_t boot_image_begin_ = 0u;
  uint32_t boot_image_size_ = 0u;  // Includes heap (*.art) and code (.oat).

  // Number of boot image components that this image depends on and their composite checksum
  // (only applies to boot image extension and app image headers).
  uint32_t boot_image_component_count_ = 0u;
  uint32_t boot_image_checksum_ = 0u;

  // Absolute address of an Object[] of objects needed to reinitialize from an image.
  uint32_t image_roots_ = 0u;

  // Pointer size, this affects the size of the ArtMethods.
  uint32_t pointer_size_ = 0u;

  // Image section sizes/offsets correspond to the uncompressed form.
  ImageSection sections_[kSectionCount];

  // Image methods, may be inside of the boot image for app images.
  uint64_t image_methods_[kImageMethodsCount];

  // Data size for the image data excluding the bitmap and the header. For compressed images, this
  // is the compressed size in the file.
  uint32_t data_size_ = 0u;

  // Image blocks, only used for compressed images.
  uint32_t blocks_offset_ = 0u;
  uint32_t blocks_count_ = 0u;

  friend class linker::ImageWriter;
  friend class RuntimeImageHelper;
};

// Helper class that erases the image file if it isn't properly flushed and closed.
class ImageFileGuard {
 public:
  ImageFileGuard() noexcept = default;
  ImageFileGuard(ImageFileGuard&& other) noexcept = default;
  ImageFileGuard& operator=(ImageFileGuard&& other) noexcept = default;

  ~ImageFileGuard() {
    if (image_file_ != nullptr) {
      // Failure, erase the image file.
      image_file_->Erase();
    }
  }

  void reset(File* image_file) {
    image_file_.reset(image_file);
  }

  bool operator==(std::nullptr_t) {
    return image_file_ == nullptr;
  }

  bool operator!=(std::nullptr_t) {
    return image_file_ != nullptr;
  }

  File* operator->() const {
    return image_file_.get();
  }

  bool WriteHeaderAndClose(const std::string& image_filename,
                           const ImageHeader* image_header,
                           std::string* error_msg) {
    // The header is uncompressed since it contains whether the image is compressed or not.
    if (!image_file_->PwriteFully(image_header, sizeof(ImageHeader), 0)) {
      *error_msg = "Failed to write image file header "
          + image_filename + ": " + std::string(strerror(errno));
      return false;
    }

    // FlushCloseOrErase() takes care of erasing, so the destructor does not need
    // to do that whether the FlushCloseOrErase() succeeds or fails.
    std::unique_ptr<File> image_file = std::move(image_file_);
    if (image_file->FlushCloseOrErase() != 0) {
      *error_msg = "Failed to flush and close image file "
          + image_filename + ": " + std::string(strerror(errno));
      return false;
    }

    return true;
  }

 private:
  std::unique_ptr<File> image_file_;
};


/*
 * This type holds the information necessary to fix up AppImage string
 * references.
 *
 * The first element indicates the location of a managed object with a field that needs fixing up.
 * The second element of the pair is an object-relative offset to the field in question.
 */
using AppImageReferenceOffsetInfo = std::pair<uint32_t, uint32_t>;

std::ostream& operator<<(std::ostream& os, ImageHeader::ImageMethod method);
std::ostream& operator<<(std::ostream& os, ImageHeader::ImageRoot root);
EXPORT std::ostream& operator<<(std::ostream& os, ImageHeader::ImageSections section);
EXPORT std::ostream& operator<<(std::ostream& os, ImageHeader::StorageMode mode);

EXPORT std::ostream& operator<<(std::ostream& os, const ImageSection& section);

// Wrapper over LZ4_decompress_safe() that checks if return value is negative. See b/242914915.
bool LZ4_decompress_safe_checked(const char* source,
                                 char* dest,
                                 int compressed_size,
                                 int max_decompressed_size,
                                 /*out*/ size_t* decompressed_size_checked,
                                 /*out*/ std::string* error_msg);

}  // namespace art

#endif  // ART_RUNTIME_OAT_IMAGE_H_
