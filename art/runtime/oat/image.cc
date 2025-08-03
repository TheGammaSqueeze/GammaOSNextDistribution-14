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

#include "image.h"

#include <lz4.h>
#include <lz4hc.h>
#include <sstream>
#include <sys/stat.h>
#include <zlib.h>

#include "android-base/stringprintf.h"

#include "base/bit_utils.h"
#include "base/length_prefixed_array.h"
#include "base/utils.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/object_array.h"

namespace art HIDDEN {

const uint8_t ImageHeader::kImageMagic[] = { 'a', 'r', 't', '\n' };
// Last change: Split intrinsics list - with and without HIR.
const uint8_t ImageHeader::kImageVersion[] = { '1', '0', '9', '\0' };

ImageHeader::ImageHeader(uint32_t image_reservation_size,
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
                         uint32_t pointer_size)
  : image_reservation_size_(image_reservation_size),
    component_count_(component_count),
    image_begin_(image_begin),
    image_size_(image_size),
    image_checksum_(0u),
    oat_checksum_(oat_checksum),
    oat_file_begin_(oat_file_begin),
    oat_data_begin_(oat_data_begin),
    oat_data_end_(oat_data_end),
    oat_file_end_(oat_file_end),
    boot_image_begin_(boot_image_begin),
    boot_image_size_(boot_image_size),
    boot_image_component_count_(boot_image_component_count),
    boot_image_checksum_(boot_image_checksum),
    image_roots_(image_roots),
    pointer_size_(pointer_size) {
  CHECK_EQ(image_begin, RoundUp(image_begin, kElfSegmentAlignment));
  if (oat_checksum != 0u) {
    CHECK_EQ(oat_file_begin, RoundUp(oat_file_begin, kElfSegmentAlignment));
    CHECK_EQ(oat_data_begin, RoundUp(oat_data_begin, kElfSegmentAlignment));
    CHECK_LT(image_roots, oat_file_begin);
    CHECK_LE(oat_file_begin, oat_data_begin);
    CHECK_LT(oat_data_begin, oat_data_end);
    CHECK_LE(oat_data_end, oat_file_end);
  }
  CHECK(ValidPointerSize(pointer_size_)) << pointer_size_;
  memcpy(magic_, kImageMagic, sizeof(kImageMagic));
  memcpy(version_, kImageVersion, sizeof(kImageVersion));
  std::copy_n(sections, kSectionCount, sections_);
}

void ImageHeader::RelocateImageReferences(int64_t delta) {
  // App Images can be relocated to a page aligned address.
  // Unlike with the Boot Image, for which the memory is reserved in advance of
  // loading and is aligned to kElfSegmentAlignment, the App Images can be mapped
  // without reserving memory i.e. via direct file mapping in which case the
  // memory range is aligned by the kernel and the only guarantee is that it is
  // aligned to the page sizes.
  //
  // NOTE: While this might be less than alignment required via information in
  //       the ELF header, it should be sufficient in practice as the only reason
  //       for the ELF segment alignment to be more than one page size is the
  //       compatibility of the ELF with system configurations that use larger
  //       page size.
  //
  //       Adding preliminary memory reservation would introduce certain overhead.
  //
  //       However, technically the alignment requirement isn't fulfilled and that
  //       might be worth addressing even if it adds certain overhead. This will have
  //       to be done in alignment with the dynamic linker's ELF loader as
  //       otherwise inconsistency would still be possible e.g. when using
  //       `dlopen`-like calls to load OAT files.
  CHECK_ALIGNED_PARAM(delta, gPageSize) << "relocation delta must be page aligned";
  oat_file_begin_ += delta;
  oat_data_begin_ += delta;
  oat_data_end_ += delta;
  oat_file_end_ += delta;
  image_begin_ += delta;
  image_roots_ += delta;
}

void ImageHeader::RelocateBootImageReferences(int64_t delta) {
  CHECK_ALIGNED(delta, kElfSegmentAlignment) << "relocation delta must be Elf segment aligned";
  DCHECK_EQ(boot_image_begin_ != 0u, boot_image_size_ != 0u);
  if (boot_image_begin_ != 0u) {
    boot_image_begin_ += delta;
  }
  for (size_t i = 0; i < kImageMethodsCount; ++i) {
    image_methods_[i] += delta;
  }
}

bool ImageHeader::IsAppImage() const {
  // Unlike boot image and boot image extensions which include address space for
  // oat files in their reservation size, app images are loaded separately from oat
  // files and their reservation size is the image size rounded up to Elf alignment.
  return image_reservation_size_ == RoundUp(image_size_, kElfSegmentAlignment);
}

uint32_t ImageHeader::GetImageSpaceCount() const {
  DCHECK(!IsAppImage());
  DCHECK_NE(component_count_, 0u);  // Must be the header for the first component.
  // For images compiled with --single-image, there is only one oat file. To detect
  // that, check whether the reservation ends at the end of the first oat file.
  return (image_begin_ + image_reservation_size_ == oat_file_end_) ? 1u : component_count_;
}

bool ImageHeader::IsValid() const {
  if (memcmp(magic_, kImageMagic, sizeof(kImageMagic)) != 0) {
    return false;
  }
  if (memcmp(version_, kImageVersion, sizeof(kImageVersion)) != 0) {
    return false;
  }
  if (!IsAligned<kElfSegmentAlignment>(image_reservation_size_)) {
    return false;
  }
  // Unsigned so wraparound is well defined.
  if (image_begin_ >= image_begin_ + image_size_) {
    return false;
  }
  if (oat_checksum_ != 0u) {
    if (oat_file_begin_ > oat_file_end_) {
      return false;
    }
    if (oat_data_begin_ > oat_data_end_) {
      return false;
    }
    if (oat_file_begin_ >= oat_data_begin_) {
      return false;
    }
  }
  return true;
}

const char* ImageHeader::GetMagic() const {
  CHECK(IsValid());
  return reinterpret_cast<const char*>(magic_);
}

ArtMethod* ImageHeader::GetImageMethod(ImageMethod index) const {
  CHECK_LT(static_cast<size_t>(index), kImageMethodsCount);
  return reinterpret_cast<ArtMethod*>(image_methods_[index]);
}

std::ostream& operator<<(std::ostream& os, const ImageSection& section) {
  return os << "size=" << section.Size() << " range=" << section.Offset() << "-" << section.End();
}

void ImageHeader::VisitObjects(ObjectVisitor* visitor,
                               uint8_t* base,
                               PointerSize pointer_size) const {
  DCHECK_EQ(pointer_size, GetPointerSize());
  const ImageSection& objects = GetObjectsSection();
  static const size_t kStartPos = RoundUp(sizeof(ImageHeader), kObjectAlignment);
  for (size_t pos = kStartPos; pos < objects.Size(); ) {
    mirror::Object* object = reinterpret_cast<mirror::Object*>(base + objects.Offset() + pos);
    visitor->Visit(object);
    pos += RoundUp(object->SizeOf(), kObjectAlignment);
  }
}

PointerSize ImageHeader::GetPointerSize() const {
  return ConvertToPointerSize(pointer_size_);
}

bool LZ4_decompress_safe_checked(const char* source,
                                 char* dest,
                                 int compressed_size,
                                 int max_decompressed_size,
                                 /*out*/ size_t* decompressed_size_checked,
                                 /*out*/ std::string* error_msg) {
  int decompressed_size = LZ4_decompress_safe(source, dest, compressed_size, max_decompressed_size);
  if (UNLIKELY(decompressed_size < 0)) {
    *error_msg = android::base::StringPrintf("LZ4_decompress_safe() returned negative size: %d",
                                             decompressed_size);
    return false;
  } else {
    *decompressed_size_checked = static_cast<size_t>(decompressed_size);
    return true;
  }
}

bool ImageHeader::Block::Decompress(uint8_t* out_ptr,
                                    const uint8_t* in_ptr,
                                    std::string* error_msg) const {
  switch (storage_mode_) {
    case kStorageModeUncompressed: {
      CHECK_EQ(image_size_, data_size_);
      memcpy(out_ptr + image_offset_, in_ptr + data_offset_, data_size_);
      break;
    }
    case kStorageModeLZ4:
    case kStorageModeLZ4HC: {
      // LZ4HC and LZ4 have same internal format, both use LZ4_decompress.
      size_t decompressed_size;
      bool ok = LZ4_decompress_safe_checked(
          reinterpret_cast<const char*>(in_ptr) + data_offset_,
          reinterpret_cast<char*>(out_ptr) + image_offset_,
          data_size_,
          image_size_,
          &decompressed_size,
          error_msg);
      if (!ok) {
        return false;
      }
      if (decompressed_size != image_size_) {
        if (error_msg != nullptr) {
          // Maybe some disk / memory corruption, just bail.
          *error_msg = (std::ostringstream() << "Decompressed size different than image size: "
                                             << decompressed_size << ", and " << image_size_).str();
        }
        return false;
      }
      break;
    }
    default: {
      if (error_msg != nullptr) {
        *error_msg = (std::ostringstream() << "Invalid image format " << storage_mode_).str();
      }
      return false;
    }
  }
  return true;
}

const char* ImageHeader::GetImageSectionName(ImageSections index) {
  switch (index) {
    case kSectionObjects: return "Objects";
    case kSectionArtFields: return "ArtFields";
    case kSectionArtMethods: return "ArtMethods";
    case kSectionRuntimeMethods: return "RuntimeMethods";
    case kSectionImTables: return "ImTables";
    case kSectionIMTConflictTables: return "IMTConflictTables";
    case kSectionInternedStrings: return "InternedStrings";
    case kSectionClassTable: return "ClassTable";
    case kSectionStringReferenceOffsets: return "StringReferenceOffsets";
    case kSectionDexCacheArrays: return "DexCacheArrays";
    case kSectionMetadata: return "Metadata";
    case kSectionImageBitmap: return "ImageBitmap";
    case kSectionCount: return nullptr;
  }
}

// Compress data from `source` into `storage`.
static bool CompressData(ArrayRef<const uint8_t> source,
                         ImageHeader::StorageMode image_storage_mode,
                         /*out*/ dchecked_vector<uint8_t>* storage) {
  const uint64_t compress_start_time = NanoTime();

  // Bound is same for both LZ4 and LZ4HC.
  storage->resize(LZ4_compressBound(source.size()));
  size_t data_size = 0;
  if (image_storage_mode == ImageHeader::kStorageModeLZ4) {
    data_size = LZ4_compress_default(
        reinterpret_cast<char*>(const_cast<uint8_t*>(source.data())),
        reinterpret_cast<char*>(storage->data()),
        source.size(),
        storage->size());
  } else {
    DCHECK_EQ(image_storage_mode, ImageHeader::kStorageModeLZ4HC);
    data_size = LZ4_compress_HC(
        reinterpret_cast<const char*>(const_cast<uint8_t*>(source.data())),
        reinterpret_cast<char*>(storage->data()),
        source.size(),
        storage->size(),
        LZ4HC_CLEVEL_MAX);
  }

  if (data_size == 0) {
    return false;
  }
  storage->resize(data_size);

  VLOG(image) << "Compressed from " << source.size() << " to " << storage->size() << " in "
              << PrettyDuration(NanoTime() - compress_start_time);
  if (kIsDebugBuild) {
    dchecked_vector<uint8_t> decompressed(source.size());
    size_t decompressed_size;
    std::string error_msg;
    bool ok = LZ4_decompress_safe_checked(
        reinterpret_cast<char*>(storage->data()),
        reinterpret_cast<char*>(decompressed.data()),
        storage->size(),
        decompressed.size(),
        &decompressed_size,
        &error_msg);
    if (!ok) {
      LOG(FATAL) << error_msg;
      UNREACHABLE();
    }
    CHECK_EQ(decompressed_size, decompressed.size());
    CHECK_EQ(memcmp(source.data(), decompressed.data(), source.size()), 0) << image_storage_mode;
  }
  return true;
}

bool ImageHeader::WriteData(const ImageFileGuard& image_file,
                            const uint8_t* data,
                            const uint8_t* bitmap_data,
                            ImageHeader::StorageMode image_storage_mode,
                            uint32_t max_image_block_size,
                            bool update_checksum,
                            std::string* error_msg) {
  const bool is_compressed = image_storage_mode != ImageHeader::kStorageModeUncompressed;
  dchecked_vector<std::pair<uint32_t, uint32_t>> block_sources;
  dchecked_vector<ImageHeader::Block> blocks;

  // Add a set of solid blocks such that no block is larger than the maximum size. A solid block
  // is a block that must be decompressed all at once.
  auto add_blocks = [&](uint32_t offset, uint32_t size) {
    while (size != 0u) {
      const uint32_t cur_size = std::min(size, max_image_block_size);
      block_sources.emplace_back(offset, cur_size);
      offset += cur_size;
      size -= cur_size;
    }
  };

  add_blocks(sizeof(ImageHeader), this->GetImageSize() - sizeof(ImageHeader));

  // Checksum of compressed image data and header.
  uint32_t image_checksum = 0u;
  if (update_checksum) {
    image_checksum = adler32(0L, Z_NULL, 0);
    image_checksum = adler32(image_checksum,
                             reinterpret_cast<const uint8_t*>(this),
                             sizeof(ImageHeader));
  }

  // Copy and compress blocks.
  uint32_t out_offset = sizeof(ImageHeader);
  for (const std::pair<uint32_t, uint32_t> block : block_sources) {
    ArrayRef<const uint8_t> raw_image_data(data + block.first, block.second);
    dchecked_vector<uint8_t> compressed_data;
    ArrayRef<const uint8_t> image_data;
    if (is_compressed) {
      if (!CompressData(raw_image_data, image_storage_mode, &compressed_data)) {
        *error_msg = "Error compressing data for " +
            image_file->GetPath() + ": " + std::string(strerror(errno));
        return false;
      }
      image_data = ArrayRef<const uint8_t>(compressed_data);
    } else {
      image_data = raw_image_data;
      // For uncompressed, preserve alignment since the image will be directly mapped.
      out_offset = block.first;
    }

    // Fill in the compressed location of the block.
    blocks.emplace_back(ImageHeader::Block(
        image_storage_mode,
        /*data_offset=*/ out_offset,
        /*data_size=*/ image_data.size(),
        /*image_offset=*/ block.first,
        /*image_size=*/ block.second));

    if (!image_file->PwriteFully(image_data.data(), image_data.size(), out_offset)) {
      *error_msg = "Failed to write image file data " +
          image_file->GetPath() + ": " + std::string(strerror(errno));
      return false;
    }
    out_offset += image_data.size();
    if (update_checksum) {
      image_checksum = adler32(image_checksum, image_data.data(), image_data.size());
    }
  }

  if (is_compressed) {
    // Align up since the compressed data is not necessarily aligned.
    out_offset = RoundUp(out_offset, alignof(ImageHeader::Block));
    CHECK(!blocks.empty());
    const size_t blocks_bytes = blocks.size() * sizeof(blocks[0]);
    if (!image_file->PwriteFully(&blocks[0], blocks_bytes, out_offset)) {
      *error_msg = "Failed to write image blocks " +
          image_file->GetPath() + ": " + std::string(strerror(errno));
      return false;
    }
    this->blocks_offset_ = out_offset;
    this->blocks_count_ = blocks.size();
    out_offset += blocks_bytes;
  }

  // Data size includes everything except the bitmap.
  this->data_size_ = out_offset - sizeof(ImageHeader);

  // Update and write the bitmap section. Note that the bitmap section is relative to the
  // possibly compressed image.
  ImageSection& bitmap_section = GetImageSection(ImageHeader::kSectionImageBitmap);
  // Align up since data size may be unaligned if the image is compressed.
  out_offset = RoundUp(out_offset, kElfSegmentAlignment);
  bitmap_section = ImageSection(out_offset, bitmap_section.Size());

  if (!image_file->PwriteFully(bitmap_data,
                               bitmap_section.Size(),
                               bitmap_section.Offset())) {
    *error_msg = "Failed to write image file bitmap " +
        image_file->GetPath() + ": " + std::string(strerror(errno));
    return false;
  }

  int err = image_file->Flush();
  if (err < 0) {
    *error_msg = "Failed to flush image file " + image_file->GetPath() + ": " + std::to_string(err);
    return false;
  }

  if (update_checksum) {
    // Calculate the image checksum of the remaining data.
    image_checksum = adler32(image_checksum,
                             reinterpret_cast<const uint8_t*>(bitmap_data),
                             bitmap_section.Size());
    this->SetImageChecksum(image_checksum);
  }

  if (VLOG_IS_ON(image)) {
    const size_t separately_written_section_size = bitmap_section.Size();
    const size_t total_uncompressed_size = image_size_ + separately_written_section_size;
    const size_t total_compressed_size = out_offset + separately_written_section_size;

    VLOG(compiler) << "UncompressedImageSize = " << total_uncompressed_size;
    if (total_uncompressed_size != total_compressed_size) {
      VLOG(compiler) << "CompressedImageSize = " << total_compressed_size;
    }
  }

  DCHECK_EQ(bitmap_section.End(), static_cast<size_t>(image_file->GetLength()))
      << "Bitmap should be at the end of the file";
  return true;
}

}  // namespace art
