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

#include "oat_writer.h"

#include <unistd.h>
#include <zlib.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "arch/arm64/instruction_set_features_arm64.h"
#include "art_method-inl.h"
#include "base/allocator.h"
#include "base/bit_vector-inl.h"
#include "base/enums.h"
#include "base/file_magic.h"
#include "base/file_utils.h"
#include "base/indenter.h"
#include "base/logging.h"  // For VLOG
#include "base/os.h"
#include "base/safe_map.h"
#include "base/stl_util.h"
#include "base/unix_file/fd_file.h"
#include "base/zip_archive.h"
#include "class_linker.h"
#include "class_table-inl.h"
#include "code_info_table_deduper.h"
#include "debug/method_debug_info.h"
#include "dex/art_dex_file_loader.h"
#include "dex/class_accessor-inl.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_loader.h"
#include "dex/dex_file_types.h"
#include "dex/dex_file_verifier.h"
#include "dex/proto_reference.h"
#include "dex/standard_dex_file.h"
#include "dex/type_lookup_table.h"
#include "dex/verification_results.h"
#include "dex_container.h"
#include "dexlayout.h"
#include "driver/compiled_method-inl.h"
#include "driver/compiler_driver-inl.h"
#include "driver/compiler_options.h"
#include "gc/space/image_space.h"
#include "gc/space/space.h"
#include "handle_scope-inl.h"
#include "image_writer.h"
#include "linker/index_bss_mapping_encoder.h"
#include "linker/linker_patch.h"
#include "linker/multi_oat_relative_patcher.h"
#include "mirror/array.h"
#include "mirror/class_loader.h"
#include "mirror/dex_cache-inl.h"
#include "mirror/object-inl.h"
#include "oat/oat.h"
#include "oat/oat_quick_method_header.h"
#include "oat/stack_map.h"
#include "profile/profile_compilation_info.h"
#include "scoped_thread_state_change-inl.h"
#include "stream/buffered_output_stream.h"
#include "stream/file_output_stream.h"
#include "stream/output_stream.h"
#include "vdex_file.h"
#include "verifier/verifier_deps.h"

namespace art {
namespace linker {

namespace {  // anonymous namespace

// If we write dex layout info in the oat file.
static constexpr bool kWriteDexLayoutInfo = true;

// Force the OAT method layout to be sorted-by-name instead of
// the default (class_def_idx, method_idx).
//
// Otherwise if profiles are used, that will act as
// the primary sort order.
//
// A bit easier to use for development since oatdump can easily
// show that things are being re-ordered when two methods aren't adjacent.
static constexpr bool kOatWriterForceOatCodeLayout = false;

static constexpr bool kOatWriterDebugOatCodeLayout = false;

using UnalignedDexFileHeader __attribute__((__aligned__(1))) = DexFile::Header;

const UnalignedDexFileHeader* AsUnalignedDexFileHeader(const uint8_t* raw_data) {
  return reinterpret_cast<const UnalignedDexFileHeader*>(raw_data);
}

inline uint32_t CodeAlignmentSize(uint32_t header_offset, const CompiledMethod& compiled_method) {
  // We want to align the code rather than the preheader.
  uint32_t unaligned_code_offset = header_offset + sizeof(OatQuickMethodHeader);
  uint32_t aligned_code_offset =  compiled_method.AlignCode(unaligned_code_offset);
  return aligned_code_offset - unaligned_code_offset;
}

}  // anonymous namespace

class OatWriter::ChecksumUpdatingOutputStream : public OutputStream {
 public:
  ChecksumUpdatingOutputStream(OutputStream* out, OatWriter* writer)
      : OutputStream(out->GetLocation()), out_(out), writer_(writer) { }

  bool WriteFully(const void* buffer, size_t byte_count) override {
    if (buffer != nullptr) {
      const uint8_t* bytes = reinterpret_cast<const uint8_t*>(buffer);
      uint32_t old_checksum = writer_->oat_checksum_;
      writer_->oat_checksum_ = adler32(old_checksum, bytes, byte_count);
    } else {
      DCHECK_EQ(0U, byte_count);
    }
    return out_->WriteFully(buffer, byte_count);
  }

  off_t Seek(off_t offset, Whence whence) override {
    return out_->Seek(offset, whence);
  }

  bool Flush() override {
    return out_->Flush();
  }

 private:
  OutputStream* const out_;
  OatWriter* const writer_;
};

// OatClassHeader is the header only part of the oat class that is required even when compilation
// is not enabled.
class OatWriter::OatClassHeader {
 public:
  OatClassHeader(uint32_t offset,
                 uint32_t num_non_null_compiled_methods,
                 uint32_t num_methods,
                 ClassStatus status)
      : status_(enum_cast<uint16_t>(status)),
        offset_(offset) {
    // We just arbitrarily say that 0 methods means OatClassType::kNoneCompiled and that we won't
    // use OatClassType::kAllCompiled unless there is at least one compiled method. This means in
    // an interpreter only system, we can assert that all classes are OatClassType::kNoneCompiled.
    if (num_non_null_compiled_methods == 0) {
      type_ = enum_cast<uint16_t>(OatClassType::kNoneCompiled);
    } else if (num_non_null_compiled_methods == num_methods) {
      type_ = enum_cast<uint16_t>(OatClassType::kAllCompiled);
    } else {
      type_ = enum_cast<uint16_t>(OatClassType::kSomeCompiled);
    }
  }

  bool Write(OatWriter* oat_writer, OutputStream* out, const size_t file_offset) const;

  static size_t SizeOf() {
    return sizeof(status_) + sizeof(type_);
  }

  // Data to write.
  static_assert(sizeof(ClassStatus) <= sizeof(uint16_t), "class status won't fit in 16bits");
  uint16_t status_;

  static_assert(sizeof(OatClassType) <= sizeof(uint16_t), "oat_class type won't fit in 16bits");
  uint16_t type_;

  // Offset of start of OatClass from beginning of OatHeader. It is
  // used to validate file position when writing.
  uint32_t offset_;
};

// The actual oat class body contains the information about compiled methods. It is only required
// for compiler filters that have any compilation.
class OatWriter::OatClass {
 public:
  OatClass(const dchecked_vector<CompiledMethod*>& compiled_methods,
           uint32_t compiled_methods_with_code,
           uint16_t oat_class_type);
  OatClass(OatClass&& src) = default;
  size_t SizeOf() const;
  bool Write(OatWriter* oat_writer, OutputStream* out) const;

  CompiledMethod* GetCompiledMethod(size_t class_def_method_index) const {
    return compiled_methods_[class_def_method_index];
  }

  // CompiledMethods for each class_def_method_index, or null if no method is available.
  dchecked_vector<CompiledMethod*> compiled_methods_;

  // Offset from OatClass::offset_ to the OatMethodOffsets for the
  // class_def_method_index. If 0, it means the corresponding
  // CompiledMethod entry in OatClass::compiled_methods_ should be
  // null and that the OatClass::type_ should be OatClassType::kSomeCompiled.
  dchecked_vector<uint32_t> oat_method_offsets_offsets_from_oat_class_;

  // Data to write.

  // Number of methods recorded in OatClass. For `OatClassType::kNoneCompiled`
  // this shall be zero and shall not be written to the file, otherwise it
  // shall be the number of methods in the class definition. It is used to
  // determine the size of `BitVector` data for `OatClassType::kSomeCompiled` and
  // the size of the `OatMethodOffsets` table for `OatClassType::kAllCompiled`.
  // (The size of the `OatMethodOffsets` table for `OatClassType::kSomeCompiled`
  // is determined by the number of bits set in the `BitVector` data.)
  uint32_t num_methods_;

  // Bit vector indexed by ClassDef method index. When OatClass::type_ is
  // OatClassType::kSomeCompiled, a set bit indicates the method has an
  // OatMethodOffsets in methods_offsets_, otherwise
  // the entry was omitted to save space. If OatClass::type_ is
  // not is OatClassType::kSomeCompiled, the bitmap will be null.
  std::unique_ptr<BitVector> method_bitmap_;

  // OatMethodOffsets and OatMethodHeaders for each CompiledMethod
  // present in the OatClass. Note that some may be missing if
  // OatClass::compiled_methods_ contains null values (and
  // oat_method_offsets_offsets_from_oat_class_ should contain 0
  // values in this case).
  dchecked_vector<OatMethodOffsets> method_offsets_;
  dchecked_vector<OatQuickMethodHeader> method_headers_;

 private:
  size_t GetMethodOffsetsRawSize() const {
    return method_offsets_.size() * sizeof(method_offsets_[0]);
  }

  DISALLOW_COPY_AND_ASSIGN(OatClass);
};

class OatWriter::OatDexFile {
 public:
  explicit OatDexFile(std::unique_ptr<const DexFile> dex_file);
  OatDexFile(OatDexFile&& src) = default;

  const DexFile* GetDexFile() const { return dex_file_.get(); }

  const char* GetLocation() const {
    return dex_file_location_data_;
  }

  size_t SizeOf() const;
  bool Write(OatWriter* oat_writer, OutputStream* out) const;
  bool WriteClassOffsets(OatWriter* oat_writer, OutputStream* out);

  size_t GetClassOffsetsRawSize() const {
    return class_offsets_.size() * sizeof(class_offsets_[0]);
  }

  std::unique_ptr<const DexFile> dex_file_;
  std::unique_ptr<std::string> dex_file_location_;

  std::vector<uint8_t> cdex_main_section_;

  // Dex file size. Passed in the constructor, but could be
  // overwritten by LayoutDexFile.
  size_t dex_file_size_;

  // Offset of start of OatDexFile from beginning of OatHeader. It is
  // used to validate file position when writing.
  size_t offset_;

  ///// Start of data to write to vdex/oat file.

  const uint32_t dex_file_location_size_;
  const char* const dex_file_location_data_;

  DexFile::Magic dex_file_magic_;

  // The checksum of the dex file.
  const uint32_t dex_file_location_checksum_;
  const DexFile::Sha1 dex_file_sha1_;

  // Offset of the dex file in the vdex file. Set when writing dex files in
  // SeekToDexFile.
  uint32_t dex_file_offset_;

  // The lookup table offset in the oat file. Set in WriteTypeLookupTables.
  uint32_t lookup_table_offset_;

  // Class and BSS offsets set in PrepareLayout.
  uint32_t class_offsets_offset_;
  uint32_t method_bss_mapping_offset_;
  uint32_t type_bss_mapping_offset_;
  uint32_t public_type_bss_mapping_offset_;
  uint32_t package_type_bss_mapping_offset_;
  uint32_t string_bss_mapping_offset_;
  uint32_t method_type_bss_mapping_offset_;

  // Offset of dex sections that will have different runtime madvise states.
  // Set in WriteDexLayoutSections.
  uint32_t dex_sections_layout_offset_;

  // Data to write to a separate section. We set the length
  // of the vector in OpenDexFiles.
  dchecked_vector<uint32_t> class_offsets_;

  // Dex section layout info to serialize.
  DexLayoutSections dex_sections_layout_;

  ///// End of data to write to vdex/oat file.
 private:
  DISALLOW_COPY_AND_ASSIGN(OatDexFile);
};

#define DCHECK_OFFSET() \
  DCHECK_EQ(static_cast<off_t>(file_offset + relative_offset), out->Seek(0, kSeekCurrent)) \
    << "file_offset=" << file_offset << " relative_offset=" << relative_offset

#define DCHECK_OFFSET_() \
  DCHECK_EQ(static_cast<off_t>(file_offset + offset_), out->Seek(0, kSeekCurrent)) \
    << "file_offset=" << file_offset << " offset_=" << offset_

OatWriter::OatWriter(const CompilerOptions& compiler_options,
                     const VerificationResults* verification_results,
                     TimingLogger* timings,
                     ProfileCompilationInfo* info,
                     CompactDexLevel compact_dex_level)
    : write_state_(WriteState::kAddingDexFileSources),
      timings_(timings),
      compiler_driver_(nullptr),
      compiler_options_(compiler_options),
      verification_results_(verification_results),
      image_writer_(nullptr),
      extract_dex_files_into_vdex_(true),
      vdex_begin_(nullptr),
      dex_files_(nullptr),
      primary_oat_file_(false),
      vdex_size_(0u),
      vdex_dex_files_offset_(0u),
      vdex_dex_shared_data_offset_(0u),
      vdex_verifier_deps_offset_(0u),
      vdex_lookup_tables_offset_(0u),
      oat_checksum_(adler32(0L, Z_NULL, 0)),
      code_size_(0u),
      oat_size_(0u),
      data_bimg_rel_ro_start_(0u),
      data_bimg_rel_ro_size_(0u),
      bss_start_(0u),
      bss_size_(0u),
      bss_methods_offset_(0u),
      bss_roots_offset_(0u),
      data_bimg_rel_ro_entries_(),
      bss_method_entry_references_(),
      bss_method_entries_(),
      bss_type_entries_(),
      bss_public_type_entries_(),
      bss_package_type_entries_(),
      bss_string_entries_(),
      bss_method_type_entries_(),
      oat_data_offset_(0u),
      oat_header_(nullptr),
      relative_patcher_(nullptr),
      profile_compilation_info_(info),
      compact_dex_level_(compact_dex_level) {}

static bool ValidateDexFileHeader(const uint8_t* raw_header, const char* location) {
  const bool valid_standard_dex_magic = DexFileLoader::IsMagicValid(raw_header);
  if (!valid_standard_dex_magic) {
    LOG(ERROR) << "Invalid magic number in dex file header. " << " File: " << location;
    return false;
  }
  if (!DexFileLoader::IsVersionAndMagicValid(raw_header)) {
    LOG(ERROR) << "Invalid version number in dex file header. " << " File: " << location;
    return false;
  }
  const UnalignedDexFileHeader* header = AsUnalignedDexFileHeader(raw_header);
  if (header->file_size_ < sizeof(DexFile::Header)) {
    LOG(ERROR) << "Dex file header specifies file size insufficient to contain the header."
               << " File: " << location;
    return false;
  }
  return true;
}

bool OatWriter::AddDexFileSource(const char* filename, const char* location) {
  DCHECK(write_state_ == WriteState::kAddingDexFileSources);
  File fd(filename, O_RDONLY, /* check_usage= */ false);
  if (fd.Fd() == -1) {
    PLOG(ERROR) << "Failed to open dex file: '" << filename << "'";
    return false;
  }

  return AddDexFileSource(std::move(fd), location);
}

// Add dex file source(s) from a file specified by a file handle.
// Note: The `dex_file_fd` specifies a plain dex file or a zip file.
bool OatWriter::AddDexFileSource(File&& dex_file_fd, const char* location) {
  DCHECK(write_state_ == WriteState::kAddingDexFileSources);
  std::string error_msg;
  ArtDexFileLoader loader(&dex_file_fd, location);
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  if (!loader.Open(/*verify=*/false,
                   /*verify_checksum=*/false,
                   &error_msg,
                   &dex_files)) {
    LOG(ERROR) << "Failed to open dex file '" << location << "': " << error_msg;
    return false;
  }
  for (auto& dex_file : dex_files) {
    if (dex_file->IsCompactDexFile()) {
      LOG(ERROR) << "Compact dex is only supported from vdex: " << location;
      return false;
    }
    oat_dex_files_.emplace_back(std::move(dex_file));
  }
  return true;
}

// Add dex file source(s) from a vdex file specified by a file handle.
bool OatWriter::AddVdexDexFilesSource(const VdexFile& vdex_file, const char* location) {
  DCHECK(write_state_ == WriteState::kAddingDexFileSources);
  DCHECK(vdex_file.HasDexSection());
  auto container = std::make_shared<MemoryDexFileContainer>(vdex_file.Begin(), vdex_file.End());
  const uint8_t* current_dex_data = nullptr;
  size_t i = 0;
  for (; i < vdex_file.GetNumberOfDexFiles(); ++i) {
    current_dex_data = vdex_file.GetNextDexFileData(current_dex_data, i);
    if (current_dex_data == nullptr) {
      LOG(ERROR) << "Unexpected number of dex files in vdex " << location;
      return false;
    }

    if (!DexFileLoader::IsMagicValid(current_dex_data)) {
      LOG(ERROR) << "Invalid magic in vdex file created from " << location;
      return false;
    }
    // We used `zipped_dex_file_locations_` to keep the strings in memory.
    std::string multidex_location = DexFileLoader::GetMultiDexLocation(i, location);
    if (!AddRawDexFileSource(container,
                             current_dex_data,
                             multidex_location.c_str(),
                             vdex_file.GetLocationChecksum(i))) {
      return false;
    }
  }

  if (vdex_file.GetNextDexFileData(current_dex_data, i) != nullptr) {
    LOG(ERROR) << "Unexpected number of dex files in vdex " << location;
    return false;
  }

  if (oat_dex_files_.empty()) {
    LOG(ERROR) << "No dex files in vdex file created from " << location;
    return false;
  }
  return true;
}

// Add dex file source from raw memory.
bool OatWriter::AddRawDexFileSource(std::shared_ptr<DexFileContainer> container,
                                    const uint8_t* dex_file_begin,
                                    const char* location,
                                    uint32_t location_checksum) {
  DCHECK(write_state_ == WriteState::kAddingDexFileSources);
  std::string error_msg;
  ArtDexFileLoader loader(container->Begin(), container->Size(), location);
  CHECK_GE(dex_file_begin, container->Begin());
  CHECK_LE(dex_file_begin, container->End());
  auto dex_file = loader.OpenOne(dex_file_begin - container->Begin(),
                                 location_checksum,
                                 nullptr,
                                 /*verify=*/false,
                                 /*verify_checksum=*/false,
                                 &error_msg);
  if (dex_file == nullptr) {
    LOG(ERROR) << "Failed to open dex file '" << location << "': " << error_msg;
    return false;
  }
  oat_dex_files_.emplace_back(std::move(dex_file));
  return true;
}

dchecked_vector<std::string> OatWriter::GetSourceLocations() const {
  dchecked_vector<std::string> locations;
  locations.reserve(oat_dex_files_.size());
  for (const OatDexFile& oat_dex_file : oat_dex_files_) {
    locations.push_back(oat_dex_file.GetLocation());
  }
  return locations;
}

bool OatWriter::MayHaveCompiledMethods() const {
  return GetCompilerOptions().IsAnyCompilationEnabled();
}

bool OatWriter::WriteAndOpenDexFiles(
    File* vdex_file,
    bool verify,
    bool use_existing_vdex,
    CopyOption copy_dex_files,
    /*out*/ std::vector<MemMap>* opened_dex_files_map,
    /*out*/ std::vector<std::unique_ptr<const DexFile>>* opened_dex_files) {
  CHECK(write_state_ == WriteState::kAddingDexFileSources);

  // Reserve space for Vdex header, sections, and checksums.
  size_vdex_header_ = sizeof(VdexFile::VdexFileHeader) +
      VdexSection::kNumberOfSections * sizeof(VdexFile::VdexSectionHeader);
  size_vdex_checksums_ = oat_dex_files_.size() * sizeof(VdexFile::VdexChecksum);
  vdex_size_ = size_vdex_header_ + size_vdex_checksums_;

  // Write DEX files into VDEX, mmap and open them.
  std::vector<MemMap> dex_files_map;
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  if (!WriteDexFiles(vdex_file, verify, use_existing_vdex, copy_dex_files, &dex_files_map) ||
      !OpenDexFiles(vdex_file, &dex_files_map, &dex_files)) {
    return false;
  }

  *opened_dex_files_map = std::move(dex_files_map);
  *opened_dex_files = std::move(dex_files);
  // Create type lookup tables to speed up lookups during compilation.
  InitializeTypeLookupTables(*opened_dex_files);
  write_state_ = WriteState::kStartRoData;
  return true;
}

bool OatWriter::StartRoData(const std::vector<const DexFile*>& dex_files,
                            OutputStream* oat_rodata,
                            SafeMap<std::string, std::string>* key_value_store) {
  CHECK(write_state_ == WriteState::kStartRoData);

  // Record the ELF rodata section offset, i.e. the beginning of the OAT data.
  if (!RecordOatDataOffset(oat_rodata)) {
     return false;
  }

  // Record whether this is the primary oat file.
  primary_oat_file_ = (key_value_store != nullptr);

  // Initialize OAT header.
  oat_size_ = InitOatHeader(dchecked_integral_cast<uint32_t>(oat_dex_files_.size()),
                            key_value_store);

  ChecksumUpdatingOutputStream checksum_updating_rodata(oat_rodata, this);

  // Write dex layout sections into the oat file.
  if (!WriteDexLayoutSections(&checksum_updating_rodata, dex_files)) {
    return false;
  }

  write_state_ = WriteState::kInitialize;
  return true;
}

// Initialize the writer with the given parameters.
void OatWriter::Initialize(const CompilerDriver* compiler_driver,
                           ImageWriter* image_writer,
                           const std::vector<const DexFile*>& dex_files) {
  CHECK(write_state_ == WriteState::kInitialize);
  compiler_driver_ = compiler_driver;
  image_writer_ = image_writer;
  dex_files_ = &dex_files;
  write_state_ = WriteState::kPrepareLayout;
}

void OatWriter::PrepareLayout(MultiOatRelativePatcher* relative_patcher) {
  CHECK(write_state_ == WriteState::kPrepareLayout);

  relative_patcher_ = relative_patcher;
  SetMultiOatRelativePatcherAdjustment();

  if (GetCompilerOptions().IsBootImage() || GetCompilerOptions().IsBootImageExtension()) {
    CHECK(image_writer_ != nullptr);
  }
  InstructionSet instruction_set = compiler_options_.GetInstructionSet();
  CHECK_EQ(instruction_set, oat_header_->GetInstructionSet());

  {
    TimingLogger::ScopedTiming split("InitBssLayout", timings_);
    InitBssLayout(instruction_set);
  }

  uint32_t offset = oat_size_;
  {
    TimingLogger::ScopedTiming split("InitClassOffsets", timings_);
    offset = InitClassOffsets(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitOatClasses", timings_);
    offset = InitOatClasses(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitIndexBssMappings", timings_);
    offset = InitIndexBssMappings(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitOatMaps", timings_);
    offset = InitOatMaps(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitOatDexFiles", timings_);
    oat_header_->SetOatDexFilesOffset(offset);
    offset = InitOatDexFiles(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitBcpBssInfo", timings_);
    offset = InitBcpBssInfo(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitOatCode", timings_);
    offset = InitOatCode(offset);
  }
  {
    TimingLogger::ScopedTiming split("InitOatCodeDexFiles", timings_);
    offset = InitOatCodeDexFiles(offset);
    code_size_ = offset - GetOatHeader().GetExecutableOffset();
  }
  {
    TimingLogger::ScopedTiming split("InitDataBimgRelRoLayout", timings_);
    offset = InitDataBimgRelRoLayout(offset);
  }
  oat_size_ = offset;  // .bss does not count towards oat_size_.
  bss_start_ = (bss_size_ != 0u) ? RoundUp(oat_size_, kElfSegmentAlignment) : 0u;

  CHECK_EQ(dex_files_->size(), oat_dex_files_.size());

  write_state_ = WriteState::kWriteRoData;
}

OatWriter::~OatWriter() {
}

class OatWriter::DexMethodVisitor {
 public:
  DexMethodVisitor(OatWriter* writer, size_t offset)
      : writer_(writer),
        offset_(offset),
        dex_file_(nullptr),
        class_def_index_(dex::kDexNoIndex) {}

  virtual bool StartClass(const DexFile* dex_file, size_t class_def_index) {
    DCHECK(dex_file_ == nullptr);
    DCHECK_EQ(class_def_index_, dex::kDexNoIndex);
    dex_file_ = dex_file;
    class_def_index_ = class_def_index;
    return true;
  }

  virtual bool VisitMethod(size_t class_def_method_index, const ClassAccessor::Method& method) = 0;

  virtual bool EndClass() {
    if (kIsDebugBuild) {
      dex_file_ = nullptr;
      class_def_index_ = dex::kDexNoIndex;
    }
    return true;
  }

  size_t GetOffset() const {
    return offset_;
  }

 protected:
  virtual ~DexMethodVisitor() { }

  OatWriter* const writer_;

  // The offset is usually advanced for each visited method by the derived class.
  size_t offset_;

  // The dex file and class def index are set in StartClass().
  const DexFile* dex_file_;
  size_t class_def_index_;
};

class OatWriter::OatDexMethodVisitor : public DexMethodVisitor {
 public:
  OatDexMethodVisitor(OatWriter* writer, size_t offset)
      : DexMethodVisitor(writer, offset),
        oat_class_index_(0u),
        method_offsets_index_(0u) {}

  bool StartClass(const DexFile* dex_file, size_t class_def_index) override {
    DexMethodVisitor::StartClass(dex_file, class_def_index);
    if (kIsDebugBuild && writer_->MayHaveCompiledMethods()) {
      // There are no oat classes if there aren't any compiled methods.
      CHECK_LT(oat_class_index_, writer_->oat_classes_.size());
    }
    method_offsets_index_ = 0u;
    return true;
  }

  bool EndClass() override {
    ++oat_class_index_;
    return DexMethodVisitor::EndClass();
  }

 protected:
  size_t oat_class_index_;
  size_t method_offsets_index_;
};

static bool HasCompiledCode(const CompiledMethod* method) {
  return method != nullptr && !method->GetQuickCode().empty();
}

class OatWriter::InitBssLayoutMethodVisitor : public DexMethodVisitor {
 public:
  explicit InitBssLayoutMethodVisitor(OatWriter* writer)
      : DexMethodVisitor(writer, /* offset */ 0u) {}

  bool VisitMethod([[maybe_unused]] size_t class_def_method_index,
                   const ClassAccessor::Method& method) override {
    // Look for patches with .bss references and prepare maps with placeholders for their offsets.
    CompiledMethod* compiled_method = writer_->compiler_driver_->GetCompiledMethod(
        MethodReference(dex_file_, method.GetIndex()));
    if (HasCompiledCode(compiled_method)) {
      for (const LinkerPatch& patch : compiled_method->GetPatches()) {
        if (patch.GetType() == LinkerPatch::Type::kDataBimgRelRo) {
          writer_->data_bimg_rel_ro_entries_.Overwrite(patch.BootImageOffset(),
                                                       /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kMethodBssEntry) {
          MethodReference target_method = patch.TargetMethod();
          AddBssReference(target_method,
                          target_method.dex_file->NumMethodIds(),
                          &writer_->bss_method_entry_references_);
          writer_->bss_method_entries_.Overwrite(target_method, /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kTypeBssEntry) {
          TypeReference target_type(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
          AddBssReference(target_type,
                          target_type.dex_file->NumTypeIds(),
                          &writer_->bss_type_entry_references_);
          writer_->bss_type_entries_.Overwrite(target_type, /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kPublicTypeBssEntry) {
          TypeReference target_type(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
          AddBssReference(target_type,
                          target_type.dex_file->NumTypeIds(),
                          &writer_->bss_public_type_entry_references_);
          writer_->bss_public_type_entries_.Overwrite(target_type, /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kPackageTypeBssEntry) {
          TypeReference target_type(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
          AddBssReference(target_type,
                          target_type.dex_file->NumTypeIds(),
                          &writer_->bss_package_type_entry_references_);
          writer_->bss_package_type_entries_.Overwrite(target_type, /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kStringBssEntry) {
          StringReference target_string(patch.TargetStringDexFile(), patch.TargetStringIndex());
          AddBssReference(target_string,
                          target_string.dex_file->NumStringIds(),
                          &writer_->bss_string_entry_references_);
          writer_->bss_string_entries_.Overwrite(target_string, /* placeholder */ 0u);
        } else if (patch.GetType() == LinkerPatch::Type::kMethodTypeBssEntry) {
          ProtoReference target_proto(patch.TargetProtoDexFile(), patch.TargetProtoIndex());
          AddBssReference(target_proto,
                          target_proto.dex_file->NumProtoIds(),
                          &writer_->bss_method_type_entry_references_);
          writer_->bss_method_type_entries_.Overwrite(target_proto, /* placeholder */ 0u);
        }
      }
    } else {
      DCHECK(compiled_method == nullptr || compiled_method->GetPatches().empty());
    }
    return true;
  }

 private:
  void AddBssReference(const DexFileReference& ref,
                       size_t number_of_indexes,
                       /*inout*/ SafeMap<const DexFile*, BitVector>* references) {
    DCHECK(ContainsElement(*writer_->dex_files_, ref.dex_file) ||
           ContainsElement(Runtime::Current()->GetClassLinker()->GetBootClassPath(), ref.dex_file));
    DCHECK_LT(ref.index, number_of_indexes);

    auto refs_it = references->find(ref.dex_file);
    if (refs_it == references->end()) {
      refs_it = references->Put(
          ref.dex_file,
          BitVector(number_of_indexes, /* expandable */ false, Allocator::GetMallocAllocator()));
      refs_it->second.ClearAllBits();
    }
    refs_it->second.SetBit(ref.index);
  }
};

class OatWriter::InitOatClassesMethodVisitor : public DexMethodVisitor {
 public:
  InitOatClassesMethodVisitor(OatWriter* writer, size_t offset)
      : DexMethodVisitor(writer, offset),
        compiled_methods_(),
        compiled_methods_with_code_(0u) {
    size_t num_classes = 0u;
    for (const OatDexFile& oat_dex_file : writer_->oat_dex_files_) {
      num_classes += oat_dex_file.class_offsets_.size();
    }
    // If we aren't compiling only reserve headers.
    writer_->oat_class_headers_.reserve(num_classes);
    if (writer->MayHaveCompiledMethods()) {
      writer->oat_classes_.reserve(num_classes);
    }
    compiled_methods_.reserve(256u);
    // If there are any classes, the class offsets allocation aligns the offset.
    DCHECK(num_classes == 0u || IsAligned<4u>(offset));
  }

  bool StartClass(const DexFile* dex_file, size_t class_def_index) override {
    DexMethodVisitor::StartClass(dex_file, class_def_index);
    compiled_methods_.clear();
    compiled_methods_with_code_ = 0u;
    return true;
  }

  bool VisitMethod([[maybe_unused]] size_t class_def_method_index,
                   const ClassAccessor::Method& method) override {
    // Fill in the compiled_methods_ array for methods that have a
    // CompiledMethod. We track the number of non-null entries in
    // compiled_methods_with_code_ since we only want to allocate
    // OatMethodOffsets for the compiled methods.
    uint32_t method_idx = method.GetIndex();
    CompiledMethod* compiled_method =
        writer_->compiler_driver_->GetCompiledMethod(MethodReference(dex_file_, method_idx));
    compiled_methods_.push_back(compiled_method);
    if (HasCompiledCode(compiled_method)) {
      ++compiled_methods_with_code_;
    }
    return true;
  }

  bool EndClass() override {
    ClassReference class_ref(dex_file_, class_def_index_);
    ClassStatus status;
    bool found = writer_->compiler_driver_->GetCompiledClass(class_ref, &status);
    if (!found) {
      const VerificationResults* results = writer_->verification_results_;
      if (results != nullptr && results->IsClassRejected(class_ref)) {
        // The oat class status is used only for verification of resolved classes,
        // so use ClassStatus::kErrorResolved whether the class was resolved or unresolved
        // during compile-time verification.
        status = ClassStatus::kErrorResolved;
      } else {
        status = ClassStatus::kNotReady;
      }
    }
    // We never emit kRetryVerificationAtRuntime, instead we mark the class as
    // resolved and the class will therefore be re-verified at runtime.
    if (status == ClassStatus::kRetryVerificationAtRuntime) {
      status = ClassStatus::kResolved;
    }

    writer_->oat_class_headers_.emplace_back(offset_,
                                             compiled_methods_with_code_,
                                             compiled_methods_.size(),
                                             status);
    OatClassHeader& header = writer_->oat_class_headers_.back();
    offset_ += header.SizeOf();
    if (writer_->MayHaveCompiledMethods()) {
      writer_->oat_classes_.emplace_back(compiled_methods_,
                                         compiled_methods_with_code_,
                                         header.type_);
      offset_ += writer_->oat_classes_.back().SizeOf();
    }
    return DexMethodVisitor::EndClass();
  }

 private:
  dchecked_vector<CompiledMethod*> compiled_methods_;
  size_t compiled_methods_with_code_;
};

// .bss mapping offsets used for BCP DexFiles.
struct OatWriter::BssMappingInfo {
  // Offsets set in PrepareLayout.
  uint32_t method_bss_mapping_offset = 0u;
  uint32_t type_bss_mapping_offset = 0u;
  uint32_t public_type_bss_mapping_offset = 0u;
  uint32_t package_type_bss_mapping_offset = 0u;
  uint32_t string_bss_mapping_offset = 0u;
  uint32_t method_type_bss_mapping_offset = 0u;

  // Offset of the BSSInfo start from beginning of OatHeader. It is used to validate file position
  // when writing.
  size_t offset_ = 0u;

  static size_t SizeOf() {
    return sizeof(method_bss_mapping_offset) +
           sizeof(type_bss_mapping_offset) +
           sizeof(public_type_bss_mapping_offset) +
           sizeof(package_type_bss_mapping_offset) +
           sizeof(string_bss_mapping_offset) +
           sizeof(method_type_bss_mapping_offset);
  }
  bool Write(OatWriter* oat_writer, OutputStream* out) const;
};

// CompiledMethod + metadata required to do ordered method layout.
//
// See also OrderedMethodVisitor.
struct OatWriter::OrderedMethodData {
  uint32_t hotness_bits;
  OatClass* oat_class;
  CompiledMethod* compiled_method;
  MethodReference method_reference;
  size_t method_offsets_index;

  size_t class_def_index;
  uint32_t access_flags;
  const dex::CodeItem* code_item;

  // A value of -1 denotes missing debug info
  static constexpr size_t kDebugInfoIdxInvalid = static_cast<size_t>(-1);
  // Index into writer_->method_info_
  size_t debug_info_idx;

  bool HasDebugInfo() const {
    return debug_info_idx != kDebugInfoIdxInvalid;
  }

  // Bin each method according to the profile flags.
  //
  // Groups by e.g.
  //  -- not hot at all
  //  -- hot
  //  -- hot and startup
  //  -- hot and post-startup
  //  -- hot and startup and poststartup
  //  -- startup
  //  -- startup and post-startup
  //  -- post-startup
  //
  // (See MethodHotness enum definition for up-to-date binning order.)
  bool operator<(const OrderedMethodData& other) const {
    if (kOatWriterForceOatCodeLayout) {
      // Development flag: Override default behavior by sorting by name.

      std::string name = method_reference.PrettyMethod();
      std::string other_name = other.method_reference.PrettyMethod();
      return name < other_name;
    }

    // Use the profile's method hotness to determine sort order.
    if (hotness_bits < other.hotness_bits) {
      return true;
    }

    // Default: retain the original order.
    return false;
  }
};

// Given a queue of CompiledMethod in some total order,
// visit each one in that order.
class OatWriter::OrderedMethodVisitor {
 public:
  explicit OrderedMethodVisitor(OrderedMethodList ordered_methods)
      : ordered_methods_(std::move(ordered_methods)) {
  }

  virtual ~OrderedMethodVisitor() {}

  // Invoke VisitMethod in the order of `ordered_methods`, then invoke VisitComplete.
  bool Visit() REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!VisitStart()) {
      return false;
    }

    for (const OrderedMethodData& method_data : ordered_methods_)  {
      if (!VisitMethod(method_data)) {
        return false;
      }
    }

    return VisitComplete();
  }

  // Invoked once at the beginning, prior to visiting anything else.
  //
  // Return false to abort further visiting.
  virtual bool VisitStart() { return true; }

  // Invoked repeatedly in the order specified by `ordered_methods`.
  //
  // Return false to short-circuit and to stop visiting further methods.
  virtual bool VisitMethod(const OrderedMethodData& method_data)
      REQUIRES_SHARED(Locks::mutator_lock_)  = 0;

  // Invoked once at the end, after every other method has been successfully visited.
  //
  // Return false to indicate the overall `Visit` has failed.
  virtual bool VisitComplete() = 0;

  OrderedMethodList ReleaseOrderedMethods() {
    return std::move(ordered_methods_);
  }

 private:
  // List of compiled methods, sorted by the order defined in OrderedMethodData.
  // Methods can be inserted more than once in case of duplicated methods.
  OrderedMethodList ordered_methods_;
};

// Visit every compiled method in order to determine its order within the OAT file.
// Methods from the same class do not need to be adjacent in the OAT code.
class OatWriter::LayoutCodeMethodVisitor final : public OatDexMethodVisitor {
 public:
  LayoutCodeMethodVisitor(OatWriter* writer, size_t offset)
      : OatDexMethodVisitor(writer, offset),
        profile_index_(ProfileCompilationInfo::MaxProfileIndex()),
        profile_index_dex_file_(nullptr) {
  }

  bool StartClass(const DexFile* dex_file, size_t class_def_index) final {
    // Update the cached `profile_index_` if needed. This happens only once per dex file
    // because we visit all classes in a dex file together, so mark that as `UNLIKELY`.
    if (UNLIKELY(dex_file != profile_index_dex_file_)) {
      if (writer_->profile_compilation_info_ != nullptr) {
        profile_index_ = writer_->profile_compilation_info_->FindDexFile(*dex_file);
      } else {
        DCHECK_EQ(profile_index_, ProfileCompilationInfo::MaxProfileIndex());
      }
      profile_index_dex_file_ = dex_file;
    }
    return OatDexMethodVisitor::StartClass(dex_file, class_def_index);
  }

  bool VisitMethod(size_t class_def_method_index, const ClassAccessor::Method& method) final
      REQUIRES_SHARED(Locks::mutator_lock_)  {
    Locks::mutator_lock_->AssertSharedHeld(Thread::Current());

    OatClass* oat_class = &writer_->oat_classes_[oat_class_index_];
    CompiledMethod* compiled_method = oat_class->GetCompiledMethod(class_def_method_index);

    if (HasCompiledCode(compiled_method)) {
      size_t debug_info_idx = OrderedMethodData::kDebugInfoIdxInvalid;

      {
        const CompilerOptions& compiler_options = writer_->GetCompilerOptions();
        ArrayRef<const uint8_t> quick_code = compiled_method->GetQuickCode();
        uint32_t code_size = quick_code.size() * sizeof(uint8_t);

        // Debug method info must be pushed in the original order
        // (i.e. all methods from the same class must be adjacent in the debug info sections)
        // ElfCompilationUnitWriter::Write requires this.
        if (compiler_options.GenerateAnyDebugInfo() && code_size != 0) {
          debug::MethodDebugInfo info = debug::MethodDebugInfo();
          writer_->method_info_.push_back(info);

          // The debug info is filled in LayoutReserveOffsetCodeMethodVisitor
          // once we know the offsets.
          //
          // Store the index into writer_->method_info_ since future push-backs
          // could reallocate and change the underlying data address.
          debug_info_idx = writer_->method_info_.size() - 1;
        }
      }

      // Determine the `hotness_bits`, used to determine relative order
      // for OAT code layout when determining binning.
      uint32_t method_index = method.GetIndex();
      MethodReference method_ref(dex_file_, method_index);
      uint32_t hotness_bits = 0u;
      if (profile_index_ != ProfileCompilationInfo::MaxProfileIndex()) {
        ProfileCompilationInfo* pci = writer_->profile_compilation_info_;
        DCHECK(pci != nullptr);
        // Note: Bin-to-bin order does not matter. If the kernel does or does not read-ahead
        // any memory, it only goes into the buffer cache and does not grow the PSS until the
        // first time that memory is referenced in the process.
        constexpr uint32_t kHotBit = 1u;
        constexpr uint32_t kStartupBit = 2u;
        constexpr uint32_t kPostStartupBit = 4u;
        hotness_bits =
            (pci->IsHotMethod(profile_index_, method_index) ? kHotBit : 0u) |
            (pci->IsStartupMethod(profile_index_, method_index) ? kStartupBit : 0u) |
            (pci->IsPostStartupMethod(profile_index_, method_index) ? kPostStartupBit : 0u);
        if (kIsDebugBuild) {
          // Check for bins that are always-empty given a real profile.
          if (hotness_bits == kHotBit) {
            // This is not fatal, so only warn.
            LOG(WARNING) << "Method " << method_ref.PrettyMethod() << " was hot but wasn't marked "
                         << "either start-up or post-startup. Possible corrupted profile?";
          }
        }
      }

      // Handle duplicate methods by pushing them repeatedly.
      OrderedMethodData method_data = {
          hotness_bits,
          oat_class,
          compiled_method,
          method_ref,
          method_offsets_index_,
          class_def_index_,
          method.GetAccessFlags(),
          method.GetCodeItem(),
          debug_info_idx
      };
      ordered_methods_.push_back(method_data);

      method_offsets_index_++;
    }

    return true;
  }

  OrderedMethodList ReleaseOrderedMethods() {
    if (kOatWriterForceOatCodeLayout || writer_->profile_compilation_info_ != nullptr) {
      // Sort by the method ordering criteria (in OrderedMethodData).
      // Since most methods will have the same ordering criteria,
      // we preserve the original insertion order within the same sort order.
      std::stable_sort(ordered_methods_.begin(), ordered_methods_.end());
    } else {
      // The profile-less behavior is as if every method had 0 hotness
      // associated with it.
      //
      // Since sorting all methods with hotness=0 should give back the same
      // order as before, don't do anything.
      DCHECK(std::is_sorted(ordered_methods_.begin(), ordered_methods_.end()));
    }

    return std::move(ordered_methods_);
  }

 private:
  // Cached profile index for the current dex file.
  ProfileCompilationInfo::ProfileIndexType profile_index_;
  const DexFile* profile_index_dex_file_;

  // List of compiled methods, later to be sorted by order defined in OrderedMethodData.
  // Methods can be inserted more than once in case of duplicated methods.
  OrderedMethodList ordered_methods_;
};

// Given a method order, reserve the offsets for each CompiledMethod in the OAT file.
class OatWriter::LayoutReserveOffsetCodeMethodVisitor : public OrderedMethodVisitor {
 public:
  LayoutReserveOffsetCodeMethodVisitor(OatWriter* writer,
                                       size_t offset,
                                       OrderedMethodList ordered_methods)
      : LayoutReserveOffsetCodeMethodVisitor(writer,
                                             offset,
                                             writer->GetCompilerOptions(),
                                             std::move(ordered_methods)) {
  }

  bool VisitComplete() override {
    offset_ = writer_->relative_patcher_->ReserveSpaceEnd(offset_);
    if (generate_debug_info_) {
      std::vector<debug::MethodDebugInfo> thunk_infos =
          relative_patcher_->GenerateThunkDebugInfo(executable_offset_);
      writer_->method_info_.insert(writer_->method_info_.end(),
                                   std::make_move_iterator(thunk_infos.begin()),
                                   std::make_move_iterator(thunk_infos.end()));
    }
    return true;
  }

  bool VisitMethod(const OrderedMethodData& method_data) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    OatClass* oat_class = method_data.oat_class;
    CompiledMethod* compiled_method = method_data.compiled_method;
    const MethodReference& method_ref = method_data.method_reference;
    uint16_t method_offsets_index_ = method_data.method_offsets_index;
    size_t class_def_index = method_data.class_def_index;
    uint32_t access_flags = method_data.access_flags;
    bool has_debug_info = method_data.HasDebugInfo();
    size_t debug_info_idx = method_data.debug_info_idx;

    DCHECK(HasCompiledCode(compiled_method)) << method_ref.PrettyMethod();

    // Derived from CompiledMethod.
    uint32_t quick_code_offset = 0;

    ArrayRef<const uint8_t> quick_code = compiled_method->GetQuickCode();
    uint32_t code_size = quick_code.size() * sizeof(uint8_t);
    uint32_t thumb_offset = compiled_method->GetEntryPointAdjustment();

    // Deduplicate code arrays if we are not producing debuggable code.
    bool deduped = true;
    if (debuggable_) {
      quick_code_offset = relative_patcher_->GetOffset(method_ref);
      if (quick_code_offset != 0u) {
        // Duplicate methods, we want the same code for both of them so that the oat writer puts
        // the same code in both ArtMethods so that we do not get different oat code at runtime.
      } else {
        quick_code_offset = NewQuickCodeOffset(compiled_method, method_ref, thumb_offset);
        deduped = false;
      }
    } else {
      quick_code_offset = dedupe_map_.GetOrCreate(
          compiled_method,
          [this, &deduped, compiled_method, &method_ref, thumb_offset]() {
            deduped = false;
            return NewQuickCodeOffset(compiled_method, method_ref, thumb_offset);
          });
    }

    if (code_size != 0) {
      if (relative_patcher_->GetOffset(method_ref) != 0u) {
        // TODO: Should this be a hard failure?
        LOG(WARNING) << "Multiple definitions of "
            << method_ref.dex_file->PrettyMethod(method_ref.index)
            << " offsets " << relative_patcher_->GetOffset(method_ref)
            << " " << quick_code_offset;
      } else {
        relative_patcher_->SetOffset(method_ref, quick_code_offset);
      }
    }

    // Update quick method header.
    DCHECK_LT(method_offsets_index_, oat_class->method_headers_.size());
    OatQuickMethodHeader* method_header = &oat_class->method_headers_[method_offsets_index_];
    uint32_t code_info_offset = method_header->GetCodeInfoOffset();
    uint32_t code_offset = quick_code_offset - thumb_offset;
    CHECK(!compiled_method->GetQuickCode().empty());
    // If the code is compiled, we write the offset of the stack map relative
    // to the code. The offset was previously stored relative to start of file.
    if (code_info_offset != 0u) {
      DCHECK_LT(code_info_offset, code_offset);
      code_info_offset = code_offset - code_info_offset;
    }
    *method_header = OatQuickMethodHeader(code_info_offset);

    if (!deduped) {
      // Update offsets. (Checksum is updated when writing.)
      offset_ += sizeof(*method_header);  // Method header is prepended before code.
      offset_ += code_size;
    }

    // Exclude dex methods without native code.
    if (generate_debug_info_ && code_size != 0) {
      DCHECK(has_debug_info);
      const uint8_t* code_info = compiled_method->GetVmapTable().data();
      DCHECK(code_info != nullptr);

      // Record debug information for this function if we are doing that.
      debug::MethodDebugInfo& info = writer_->method_info_[debug_info_idx];
      // Simpleperf relies on art_jni_trampoline to detect jni methods.
      info.custom_name = (access_flags & kAccNative) ? "art_jni_trampoline" : "";
      info.dex_file = method_ref.dex_file;
      info.class_def_index = class_def_index;
      info.dex_method_index = method_ref.index;
      info.access_flags = access_flags;
      // For intrinsics emitted by codegen, the code has no relation to the original code item.
      info.code_item = compiled_method->IsIntrinsic() ? nullptr : method_data.code_item;
      info.isa = compiled_method->GetInstructionSet();
      info.deduped = deduped;
      info.is_native_debuggable = native_debuggable_;
      info.is_optimized = method_header->IsOptimized();
      info.is_code_address_text_relative = true;
      info.code_address = code_offset - executable_offset_;
      info.code_size = code_size;
      info.frame_size_in_bytes = CodeInfo::DecodeFrameInfo(code_info).FrameSizeInBytes();
      info.code_info = code_info;
      info.cfi = compiled_method->GetCFIInfo();
    } else {
      DCHECK(!has_debug_info);
    }

    DCHECK_LT(method_offsets_index_, oat_class->method_offsets_.size());
    OatMethodOffsets* offsets = &oat_class->method_offsets_[method_offsets_index_];
    offsets->code_offset_ = quick_code_offset;

    return true;
  }

  size_t GetOffset() const {
    return offset_;
  }

 private:
  LayoutReserveOffsetCodeMethodVisitor(OatWriter* writer,
                                       size_t offset,
                                       const CompilerOptions& compiler_options,
                                       OrderedMethodList ordered_methods)
      : OrderedMethodVisitor(std::move(ordered_methods)),
        writer_(writer),
        offset_(offset),
        relative_patcher_(writer->relative_patcher_),
        executable_offset_(writer->oat_header_->GetExecutableOffset()),
        debuggable_(compiler_options.GetDebuggable()),
        native_debuggable_(compiler_options.GetNativeDebuggable()),
        generate_debug_info_(compiler_options.GenerateAnyDebugInfo()) {}

  struct CodeOffsetsKeyComparator {
    bool operator()(const CompiledMethod* lhs, const CompiledMethod* rhs) const {
      // Code is deduplicated by CompilerDriver, compare only data pointers.
      if (lhs->GetQuickCode().data() != rhs->GetQuickCode().data()) {
        return lhs->GetQuickCode().data() < rhs->GetQuickCode().data();
      }
      // If the code is the same, all other fields are likely to be the same as well.
      if (UNLIKELY(lhs->GetVmapTable().data() != rhs->GetVmapTable().data())) {
        return lhs->GetVmapTable().data() < rhs->GetVmapTable().data();
      }
      if (UNLIKELY(lhs->GetPatches().data() != rhs->GetPatches().data())) {
        return lhs->GetPatches().data() < rhs->GetPatches().data();
      }
      if (UNLIKELY(lhs->IsIntrinsic() != rhs->IsIntrinsic())) {
        return rhs->IsIntrinsic();
      }
      return false;
    }
  };

  uint32_t NewQuickCodeOffset(CompiledMethod* compiled_method,
                              const MethodReference& method_ref,
                              uint32_t thumb_offset) {
    offset_ = relative_patcher_->ReserveSpace(offset_, compiled_method, method_ref);
    offset_ += CodeAlignmentSize(offset_, *compiled_method);
    DCHECK_ALIGNED_PARAM(offset_ + sizeof(OatQuickMethodHeader),
                         GetInstructionSetCodeAlignment(compiled_method->GetInstructionSet()));
    return offset_ + sizeof(OatQuickMethodHeader) + thumb_offset;
  }

  OatWriter* writer_;

  // Offset of the code of the compiled methods.
  size_t offset_;

  // Deduplication is already done on a pointer basis by the compiler driver,
  // so we can simply compare the pointers to find out if things are duplicated.
  SafeMap<const CompiledMethod*, uint32_t, CodeOffsetsKeyComparator> dedupe_map_;

  // Cache writer_'s members and compiler options.
  MultiOatRelativePatcher* relative_patcher_;
  uint32_t executable_offset_;
  const bool debuggable_;
  const bool native_debuggable_;
  const bool generate_debug_info_;
};

template <bool kDeduplicate>
class OatWriter::InitMapMethodVisitor : public OatDexMethodVisitor {
 public:
  InitMapMethodVisitor(OatWriter* writer, size_t offset)
      : OatDexMethodVisitor(writer, offset),
        dedupe_bit_table_(&writer_->code_info_data_) {
    if (kDeduplicate) {
      // Reserve large buffers for `CodeInfo` and bit table deduplication except for
      // multi-image compilation as we do not want to reserve multiple large buffers.
      // User devices should not do any multi-image compilation.
      const CompilerOptions& compiler_options = writer->GetCompilerOptions();
      DCHECK(compiler_options.IsAnyCompilationEnabled());
      if (compiler_options.DeduplicateCode() && !compiler_options.IsMultiImage()) {
        size_t unique_code_infos =
            writer->compiler_driver_->GetCompiledMethodStorage()->UniqueVMapTableEntries();
        dedupe_code_info_.reserve(unique_code_infos);
        dedupe_bit_table_.ReserveDedupeBuffer(unique_code_infos);
      }
    }
  }

  bool VisitMethod(size_t class_def_method_index,
                   [[maybe_unused]] const ClassAccessor::Method& method) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    OatClass* oat_class = &writer_->oat_classes_[oat_class_index_];
    CompiledMethod* compiled_method = oat_class->GetCompiledMethod(class_def_method_index);

    if (HasCompiledCode(compiled_method)) {
      DCHECK_LT(method_offsets_index_, oat_class->method_offsets_.size());
      DCHECK_EQ(oat_class->method_headers_[method_offsets_index_].GetCodeInfoOffset(), 0u);

      ArrayRef<const uint8_t> map = compiled_method->GetVmapTable();
      if (map.size() != 0u) {
        size_t offset = offset_ + writer_->code_info_data_.size();
        if (kDeduplicate) {
          auto [it, inserted] = dedupe_code_info_.insert(std::make_pair(map.data(), offset));
          DCHECK_EQ(inserted, it->second == offset);
          if (inserted) {
            size_t dedupe_bit_table_offset = dedupe_bit_table_.Dedupe(map.data());
            DCHECK_EQ(offset, offset_ + dedupe_bit_table_offset);
          } else {
            offset = it->second;
          }
        } else {
          writer_->code_info_data_.insert(writer_->code_info_data_.end(), map.begin(), map.end());
        }
        // Code offset is not initialized yet, so set file offset for now.
        DCHECK_EQ(oat_class->method_offsets_[method_offsets_index_].code_offset_, 0u);
        oat_class->method_headers_[method_offsets_index_].SetCodeInfoOffset(offset);
      }
      ++method_offsets_index_;
    }

    return true;
  }

 private:
  // Deduplicate at CodeInfo level. The value is byte offset within code_info_data_.
  // This deduplicates the whole CodeInfo object without going into the inner tables.
  // The compiler already deduplicated the pointers but it did not dedupe the tables.
  HashMap<const uint8_t*, size_t> dedupe_code_info_;

  // Deduplicate at BitTable level.
  CodeInfoTableDeduper dedupe_bit_table_;
};

class OatWriter::InitImageMethodVisitor final : public OatDexMethodVisitor {
 public:
  InitImageMethodVisitor(OatWriter* writer,
                         size_t offset,
                         const std::vector<const DexFile*>* dex_files)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : OatDexMethodVisitor(writer, offset),
        pointer_size_(GetInstructionSetPointerSize(writer_->compiler_options_.GetInstructionSet())),
        class_loader_(writer->image_writer_->GetAppClassLoader()),
        dex_files_(dex_files),
        class_linker_(Runtime::Current()->GetClassLinker()),
        dex_cache_dex_file_(nullptr),
        dex_cache_(nullptr),
        klass_(nullptr) {}

  // Handle copied methods here. Copy pointer to quick code from
  // an origin method to a copied method only if they are
  // in the same oat file. If the origin and the copied methods are
  // in different oat files don't touch the copied method.
  // References to other oat files are not supported yet.
  bool StartClass(const DexFile* dex_file, size_t class_def_index) final
      REQUIRES_SHARED(Locks::mutator_lock_) {
    OatDexMethodVisitor::StartClass(dex_file, class_def_index);
    // Skip classes that are not in the image.
    const dex::TypeId& type_id =
        dex_file_->GetTypeId(dex_file->GetClassDef(class_def_index).class_idx_);
    const char* class_descriptor = dex_file->GetTypeDescriptor(type_id);
    if (!writer_->GetCompilerOptions().IsImageClass(class_descriptor)) {
      klass_ = nullptr;
      return true;
    }
    if (UNLIKELY(dex_file != dex_cache_dex_file_)) {
      dex_cache_ = class_linker_->FindDexCache(Thread::Current(), *dex_file);
      DCHECK(dex_cache_ != nullptr);
      DCHECK(dex_cache_->GetDexFile() == dex_file);
      dex_cache_dex_file_ = dex_file;
    }
    const dex::ClassDef& class_def = dex_file->GetClassDef(class_def_index);
    klass_ = class_linker_->LookupResolvedType(class_def.class_idx_, dex_cache_, class_loader_);
    if (klass_ != nullptr) {
      if (UNLIKELY(klass_->GetDexCache() != dex_cache_)) {
        klass_ = nullptr;  // This class definition is hidden by another dex file.
        return true;
      }
      for (ArtMethod& method : klass_->GetCopiedMethods(pointer_size_)) {
        // Find origin method. Declaring class and dex_method_idx
        // in the copied method should be the same as in the origin
        // method.
        ObjPtr<mirror::Class> declaring_class = method.GetDeclaringClass();
        ArtMethod* origin = declaring_class->FindClassMethod(
            declaring_class->GetDexCache(),
            method.GetDexMethodIndex(),
            pointer_size_);
        CHECK(origin != nullptr);
        CHECK(!origin->IsDirect());
        CHECK(origin->GetDeclaringClass() == declaring_class);
        if (IsInOatFile(&declaring_class->GetDexFile())) {
          const void* code_ptr =
              origin->GetEntryPointFromQuickCompiledCodePtrSize(pointer_size_);
          if (code_ptr == nullptr) {
            methods_to_process_.push_back(std::make_pair(&method, origin));
          } else {
            method.SetEntryPointFromQuickCompiledCodePtrSize(
                code_ptr, pointer_size_);
          }
        }
      }
    }
    return true;
  }

  bool VisitMethod(size_t class_def_method_index, const ClassAccessor::Method& method) final
      REQUIRES_SHARED(Locks::mutator_lock_) {
    // Skip methods that are not in the image.
    if (klass_ == nullptr) {
      return true;
    }

    OatClass* oat_class = &writer_->oat_classes_[oat_class_index_];
    CompiledMethod* compiled_method = oat_class->GetCompiledMethod(class_def_method_index);

    if (HasCompiledCode(compiled_method)) {
      DCHECK_LT(method_offsets_index_, oat_class->method_offsets_.size());
      OatMethodOffsets offsets = oat_class->method_offsets_[method_offsets_index_];
      ++method_offsets_index_;

      // Do not try to use the `DexCache` via `ClassLinker::LookupResolvedMethod()`.
      // As we're going over all methods, `DexCache` entries would be quickly evicted
      // and we do not want the overhead of `hiddenapi` checks in the slow-path call
      // to `ClassLinker::FindResolvedMethod()` for a method that we have compiled.
      ArtMethod* resolved_method = klass_->IsInterface()
          ? klass_->FindInterfaceMethod(dex_cache_, method.GetIndex(), pointer_size_)
          : klass_->FindClassMethod(dex_cache_, method.GetIndex(), pointer_size_);
      DCHECK(resolved_method != nullptr);
      resolved_method->SetEntryPointFromQuickCompiledCodePtrSize(
          reinterpret_cast<void*>(offsets.code_offset_), pointer_size_);
    }

    return true;
  }

  // Check whether specified dex file is in the compiled oat file.
  bool IsInOatFile(const DexFile* dex_file) {
    return ContainsElement(*dex_files_, dex_file);
  }

  // Assign a pointer to quick code for copied methods
  // not handled in the method StartClass
  void Postprocess() REQUIRES_SHARED(Locks::mutator_lock_) {
    for (std::pair<ArtMethod*, ArtMethod*>& p : methods_to_process_) {
      ArtMethod* method = p.first;
      ArtMethod* origin = p.second;
      const void* code_ptr =
          origin->GetEntryPointFromQuickCompiledCodePtrSize(pointer_size_);
      if (code_ptr != nullptr) {
        method->SetEntryPointFromQuickCompiledCodePtrSize(code_ptr, pointer_size_);
      }
    }
  }

 private:
  const PointerSize pointer_size_;
  const ObjPtr<mirror::ClassLoader> class_loader_;
  const std::vector<const DexFile*>* dex_files_;
  ClassLinker* const class_linker_;
  const DexFile* dex_cache_dex_file_;  // Updated in `StartClass()`.
  ObjPtr<mirror::DexCache> dex_cache_;  // Updated in `StartClass()`.
  ObjPtr<mirror::Class> klass_;  // Updated in `StartClass()`.
  std::vector<std::pair<ArtMethod*, ArtMethod*>> methods_to_process_;
};

class OatWriter::WriteCodeMethodVisitor : public OrderedMethodVisitor {
 public:
  WriteCodeMethodVisitor(OatWriter* writer,
                         OutputStream* out,
                         const size_t file_offset,
                         size_t relative_offset,
                         OrderedMethodList ordered_methods)
      : OrderedMethodVisitor(std::move(ordered_methods)),
        writer_(writer),
        offset_(relative_offset),
        dex_file_(nullptr),
        pointer_size_(GetInstructionSetPointerSize(writer_->compiler_options_.GetInstructionSet())),
        class_loader_(writer->HasImage() ? writer->image_writer_->GetAppClassLoader() : nullptr),
        out_(out),
        file_offset_(file_offset),
        class_linker_(Runtime::Current()->GetClassLinker()),
        dex_cache_(nullptr),
        no_thread_suspension_("OatWriter patching") {
    patched_code_.reserve(16 * KB);
    if (writer_->GetCompilerOptions().IsBootImage() ||
        writer_->GetCompilerOptions().IsBootImageExtension()) {
      // If we're creating the image, the address space must be ready so that we can apply patches.
      CHECK(writer_->image_writer_->IsImageAddressSpaceReady());
    }
  }

  bool VisitStart() override {
    return true;
  }

  void UpdateDexFileAndDexCache(const DexFile* dex_file)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    dex_file_ = dex_file;

    // Ordered method visiting is only for compiled methods.
    DCHECK(writer_->MayHaveCompiledMethods());

    if (writer_->GetCompilerOptions().IsAotCompilationEnabled()) {
      // Only need to set the dex cache if we have compilation. Other modes might have unloaded it.
      if (dex_cache_ == nullptr || dex_cache_->GetDexFile() != dex_file) {
        dex_cache_ = class_linker_->FindDexCache(Thread::Current(), *dex_file);
        DCHECK(dex_cache_ != nullptr);
      }
    }
  }

  bool VisitComplete() override {
    offset_ = writer_->relative_patcher_->WriteThunks(out_, offset_);
    if (UNLIKELY(offset_ == 0u)) {
      PLOG(ERROR) << "Failed to write final relative call thunks";
      return false;
    }
    return true;
  }

  bool VisitMethod(const OrderedMethodData& method_data) override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    const MethodReference& method_ref = method_data.method_reference;
    UpdateDexFileAndDexCache(method_ref.dex_file);

    OatClass* oat_class = method_data.oat_class;
    CompiledMethod* compiled_method = method_data.compiled_method;
    uint16_t method_offsets_index = method_data.method_offsets_index;

    // No thread suspension since dex_cache_ that may get invalidated if that occurs.
    ScopedAssertNoThreadSuspension tsc(__FUNCTION__);
    DCHECK(HasCompiledCode(compiled_method)) << method_ref.PrettyMethod();

    // TODO: cleanup DCHECK_OFFSET_ to accept file_offset as parameter.
    size_t file_offset = file_offset_;  // Used by DCHECK_OFFSET_ macro.
    OutputStream* out = out_;

    ArrayRef<const uint8_t> quick_code = compiled_method->GetQuickCode();
    uint32_t code_size = quick_code.size() * sizeof(uint8_t);

    // Deduplicate code arrays.
    const OatMethodOffsets& method_offsets = oat_class->method_offsets_[method_offsets_index];
    if (method_offsets.code_offset_ > offset_) {
      offset_ = writer_->relative_patcher_->WriteThunks(out, offset_);
      if (offset_ == 0u) {
        ReportWriteFailure("relative call thunk", method_ref);
        return false;
      }
      uint32_t alignment_size = CodeAlignmentSize(offset_, *compiled_method);
      if (alignment_size != 0) {
        if (!writer_->WriteCodeAlignment(out, alignment_size)) {
          ReportWriteFailure("code alignment padding", method_ref);
          return false;
        }
        offset_ += alignment_size;
        DCHECK_OFFSET_();
      }
      DCHECK_ALIGNED_PARAM(offset_ + sizeof(OatQuickMethodHeader),
                           GetInstructionSetCodeAlignment(compiled_method->GetInstructionSet()));
      DCHECK_EQ(
          method_offsets.code_offset_,
          offset_ + sizeof(OatQuickMethodHeader) + compiled_method->GetEntryPointAdjustment())
          << dex_file_->PrettyMethod(method_ref.index);
      const OatQuickMethodHeader& method_header =
          oat_class->method_headers_[method_offsets_index];
      if (!out->WriteFully(&method_header, sizeof(method_header))) {
        ReportWriteFailure("method header", method_ref);
        return false;
      }
      writer_->size_method_header_ += sizeof(method_header);
      offset_ += sizeof(method_header);
      DCHECK_OFFSET_();

      if (!compiled_method->GetPatches().empty()) {
        patched_code_.assign(quick_code.begin(), quick_code.end());
        quick_code = ArrayRef<const uint8_t>(patched_code_);
        for (const LinkerPatch& patch : compiled_method->GetPatches()) {
          uint32_t literal_offset = patch.LiteralOffset();
          switch (patch.GetType()) {
            case LinkerPatch::Type::kIntrinsicReference: {
              uint32_t target_offset = GetTargetIntrinsicReferenceOffset(patch);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kDataBimgRelRo: {
              uint32_t target_offset =
                  writer_->data_bimg_rel_ro_start_ +
                  writer_->data_bimg_rel_ro_entries_.Get(patch.BootImageOffset());
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kMethodBssEntry: {
              uint32_t target_offset =
                  writer_->bss_start_ + writer_->bss_method_entries_.Get(patch.TargetMethod());
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kCallRelative: {
              // NOTE: Relative calls across oat files are not supported.
              uint32_t target_offset = GetTargetOffset(patch);
              writer_->relative_patcher_->PatchCall(&patched_code_,
                                                    literal_offset,
                                                    offset_ + literal_offset,
                                                    target_offset);
              break;
            }
            case LinkerPatch::Type::kStringRelative: {
              uint32_t target_offset = GetTargetObjectOffset(GetTargetString(patch));
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kStringBssEntry: {
              StringReference ref(patch.TargetStringDexFile(), patch.TargetStringIndex());
              uint32_t target_offset =
                  writer_->bss_start_ + writer_->bss_string_entries_.Get(ref);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kMethodTypeBssEntry: {
              ProtoReference ref(patch.TargetProtoDexFile(), patch.TargetProtoIndex());
              uint32_t target_offset =
                  writer_->bss_start_ + writer_->bss_method_type_entries_.Get(ref);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kTypeRelative: {
              uint32_t target_offset = GetTargetObjectOffset(GetTargetType(patch));
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kTypeBssEntry: {
              TypeReference ref(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
              uint32_t target_offset = writer_->bss_start_ + writer_->bss_type_entries_.Get(ref);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kPublicTypeBssEntry: {
              TypeReference ref(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
              uint32_t target_offset =
                  writer_->bss_start_ + writer_->bss_public_type_entries_.Get(ref);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kPackageTypeBssEntry: {
              TypeReference ref(patch.TargetTypeDexFile(), patch.TargetTypeIndex());
              uint32_t target_offset =
                  writer_->bss_start_ + writer_->bss_package_type_entries_.Get(ref);
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kMethodRelative: {
              uint32_t target_offset = GetTargetMethodOffset(GetTargetMethod(patch));
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kJniEntrypointRelative: {
              DCHECK(GetTargetMethod(patch)->IsNative());
              uint32_t target_offset =
                  GetTargetMethodOffset(GetTargetMethod(patch)) +
                  ArtMethod::EntryPointFromJniOffset(pointer_size_).Uint32Value();
              writer_->relative_patcher_->PatchPcRelativeReference(&patched_code_,
                                                                   patch,
                                                                   offset_ + literal_offset,
                                                                   target_offset);
              break;
            }
            case LinkerPatch::Type::kCallEntrypoint: {
              writer_->relative_patcher_->PatchEntrypointCall(&patched_code_,
                                                              patch,
                                                              offset_ + literal_offset);
              break;
            }
            case LinkerPatch::Type::kBakerReadBarrierBranch: {
              writer_->relative_patcher_->PatchBakerReadBarrierBranch(&patched_code_,
                                                                      patch,
                                                                      offset_ + literal_offset);
              break;
            }
            default: {
              DCHECK(false) << "Unexpected linker patch type: " << patch.GetType();
              break;
            }
          }
        }
      }

      if (!out->WriteFully(quick_code.data(), code_size)) {
        ReportWriteFailure("method code", method_ref);
        return false;
      }
      writer_->size_code_ += code_size;
      offset_ += code_size;
    }
    DCHECK_OFFSET_();

    return true;
  }

  size_t GetOffset() const {
    return offset_;
  }

 private:
  OatWriter* const writer_;

  // Updated in VisitMethod as methods are written out.
  size_t offset_;

  // Potentially varies with every different VisitMethod.
  // Used to determine which DexCache to use when finding ArtMethods.
  const DexFile* dex_file_;

  // Pointer size we are compiling to.
  const PointerSize pointer_size_;
  // The image writer's classloader, if there is one, else null.
  ObjPtr<mirror::ClassLoader> class_loader_;
  // Stream to output file, where the OAT code will be written to.
  OutputStream* const out_;
  const size_t file_offset_;
  ClassLinker* const class_linker_;
  ObjPtr<mirror::DexCache> dex_cache_;
  std::vector<uint8_t> patched_code_;
  const ScopedAssertNoThreadSuspension no_thread_suspension_;

  void ReportWriteFailure(const char* what, const MethodReference& method_ref) {
    PLOG(ERROR) << "Failed to write " << what << " for "
        << method_ref.PrettyMethod() << " to " << out_->GetLocation();
  }

  ArtMethod* GetTargetMethod(const LinkerPatch& patch)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    MethodReference ref = patch.TargetMethod();
    ObjPtr<mirror::DexCache> dex_cache =
        (dex_file_ == ref.dex_file) ? dex_cache_ : class_linker_->FindDexCache(
            Thread::Current(), *ref.dex_file);
    ArtMethod* method =
        class_linker_->LookupResolvedMethod(ref.index, dex_cache, class_loader_);
    CHECK(method != nullptr);
    return method;
  }

  uint32_t GetTargetOffset(const LinkerPatch& patch) REQUIRES_SHARED(Locks::mutator_lock_) {
    uint32_t target_offset = writer_->relative_patcher_->GetOffset(patch.TargetMethod());
    // If there's no new compiled code, we need to point to the correct trampoline.
    if (UNLIKELY(target_offset == 0)) {
      ArtMethod* target = GetTargetMethod(patch);
      DCHECK(target != nullptr);
      // TODO: Remove kCallRelative? This patch type is currently not in use.
      // If we want to use it again, we should make sure that we either use it
      // only for target methods that were actually compiled, or call the
      // method dispatch thunk. Currently, ARM/ARM64 patchers would emit the
      // thunk for far `target_offset` (so we could teach them to use the
      // thunk for `target_offset == 0`) but x86/x86-64 patchers do not.
      // (When this was originally implemented, every oat file contained
      // trampolines, so we could just return their offset here. Now only
      // the boot image contains them, so this is not always an option.)
      LOG(FATAL) << "The target method was not compiled.";
    }
    return target_offset;
  }

  ObjPtr<mirror::DexCache> GetDexCache(const DexFile* target_dex_file)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return (target_dex_file == dex_file_)
        ? dex_cache_
        : class_linker_->FindDexCache(Thread::Current(), *target_dex_file);
  }

  ObjPtr<mirror::Class> GetTargetType(const LinkerPatch& patch)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(writer_->HasImage());
    ObjPtr<mirror::DexCache> dex_cache = GetDexCache(patch.TargetTypeDexFile());
    ObjPtr<mirror::Class> type =
        class_linker_->LookupResolvedType(patch.TargetTypeIndex(), dex_cache, class_loader_);
    CHECK(type != nullptr);
    return type;
  }

  ObjPtr<mirror::String> GetTargetString(const LinkerPatch& patch)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ClassLinker* linker = Runtime::Current()->GetClassLinker();
    ObjPtr<mirror::String> string =
        linker->LookupString(patch.TargetStringIndex(), GetDexCache(patch.TargetStringDexFile()));
    DCHECK(string != nullptr);
    DCHECK(writer_->GetCompilerOptions().IsBootImage() ||
           writer_->GetCompilerOptions().IsBootImageExtension());
    return string;
  }

  uint32_t GetTargetIntrinsicReferenceOffset(const LinkerPatch& patch)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(writer_->GetCompilerOptions().IsBootImage());
    const void* address =
        writer_->image_writer_->GetIntrinsicReferenceAddress(patch.IntrinsicData());
    size_t oat_index = writer_->image_writer_->GetOatIndexForDexFile(dex_file_);
    uintptr_t oat_data_begin = writer_->image_writer_->GetOatDataBegin(oat_index);
    // TODO: Clean up offset types. The target offset must be treated as signed.
    return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(address) - oat_data_begin);
  }

  uint32_t GetTargetMethodOffset(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(writer_->GetCompilerOptions().IsBootImage() ||
           writer_->GetCompilerOptions().IsBootImageExtension());
    method = writer_->image_writer_->GetImageMethodAddress(method);
    size_t oat_index = writer_->image_writer_->GetOatIndexForDexFile(dex_file_);
    uintptr_t oat_data_begin = writer_->image_writer_->GetOatDataBegin(oat_index);
    // TODO: Clean up offset types. The target offset must be treated as signed.
    return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(method) - oat_data_begin);
  }

  uint32_t GetTargetObjectOffset(ObjPtr<mirror::Object> object)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(writer_->GetCompilerOptions().IsBootImage() ||
           writer_->GetCompilerOptions().IsBootImageExtension());
    object = writer_->image_writer_->GetImageAddress(object.Ptr());
    size_t oat_index = writer_->image_writer_->GetOatIndexForDexFile(dex_file_);
    uintptr_t oat_data_begin = writer_->image_writer_->GetOatDataBegin(oat_index);
    // TODO: Clean up offset types. The target offset must be treated as signed.
    return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(object.Ptr()) - oat_data_begin);
  }
};

// Visit all methods from all classes in all dex files with the specified visitor.
bool OatWriter::VisitDexMethods(DexMethodVisitor* visitor) {
  for (const DexFile* dex_file : *dex_files_) {
    for (ClassAccessor accessor : dex_file->GetClasses()) {
      if (UNLIKELY(!visitor->StartClass(dex_file, accessor.GetClassDefIndex()))) {
        return false;
      }
      if (MayHaveCompiledMethods()) {
        size_t class_def_method_index = 0u;
        for (const ClassAccessor::Method& method : accessor.GetMethods()) {
          if (!visitor->VisitMethod(class_def_method_index, method)) {
            return false;
          }
          ++class_def_method_index;
        }
      }
      if (UNLIKELY(!visitor->EndClass())) {
        return false;
      }
    }
  }
  return true;
}

size_t OatWriter::InitOatHeader(uint32_t num_dex_files,
                                SafeMap<std::string, std::string>* key_value_store) {
  TimingLogger::ScopedTiming split("InitOatHeader", timings_);
  // Check that oat version when runtime was compiled matches the oat version
  // when dex2oat was compiled. We have seen cases where they got out of sync.
  constexpr std::array<uint8_t, 4> dex2oat_oat_version = OatHeader::kOatVersion;
  OatHeader::CheckOatVersion(dex2oat_oat_version);
  oat_header_.reset(OatHeader::Create(GetCompilerOptions().GetInstructionSet(),
                                      GetCompilerOptions().GetInstructionSetFeatures(),
                                      num_dex_files,
                                      key_value_store));
  size_oat_header_ += sizeof(OatHeader);
  size_oat_header_key_value_store_ += oat_header_->GetHeaderSize() - sizeof(OatHeader);
  return oat_header_->GetHeaderSize();
}

size_t OatWriter::InitClassOffsets(size_t offset) {
  // Reserve space for class offsets in OAT and update class_offsets_offset_.
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    DCHECK_EQ(oat_dex_file.class_offsets_offset_, 0u);
    if (!oat_dex_file.class_offsets_.empty()) {
      // Class offsets are required to be 4 byte aligned.
      offset = RoundUp(offset, 4u);
      oat_dex_file.class_offsets_offset_ = offset;
      offset += oat_dex_file.GetClassOffsetsRawSize();
      DCHECK_ALIGNED(offset, 4u);
    }
  }
  return offset;
}

size_t OatWriter::InitOatClasses(size_t offset) {
  // calculate the offsets within OatDexFiles to OatClasses
  InitOatClassesMethodVisitor visitor(this, offset);
  bool success = VisitDexMethods(&visitor);
  CHECK(success);
  offset = visitor.GetOffset();

  // Update oat_dex_files_.
  auto oat_class_it = oat_class_headers_.begin();
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    for (uint32_t& class_offset : oat_dex_file.class_offsets_) {
      DCHECK(oat_class_it != oat_class_headers_.end());
      class_offset = oat_class_it->offset_;
      ++oat_class_it;
    }
  }
  CHECK(oat_class_it == oat_class_headers_.end());

  return offset;
}

size_t OatWriter::InitOatMaps(size_t offset) {
  if (!MayHaveCompiledMethods()) {
    return offset;
  }
  if (GetCompilerOptions().DeduplicateCode()) {
    InitMapMethodVisitor</*kDeduplicate=*/ true> visitor(this, offset);
    bool success = VisitDexMethods(&visitor);
    DCHECK(success);
  } else {
    InitMapMethodVisitor</*kDeduplicate=*/ false> visitor(this, offset);
    bool success = VisitDexMethods(&visitor);
    DCHECK(success);
  }
  code_info_data_.shrink_to_fit();
  offset += code_info_data_.size();
  return offset;
}

template <typename GetBssOffset>
static size_t CalculateNumberOfIndexBssMappingEntries(size_t number_of_indexes,
                                                      size_t slot_size,
                                                      const BitVector& indexes,
                                                      GetBssOffset get_bss_offset) {
  IndexBssMappingEncoder encoder(number_of_indexes, slot_size);
  size_t number_of_entries = 0u;
  bool first_index = true;
  for (uint32_t index : indexes.Indexes()) {
    uint32_t bss_offset = get_bss_offset(index);
    if (first_index || !encoder.TryMerge(index, bss_offset)) {
      encoder.Reset(index, bss_offset);
      ++number_of_entries;
      first_index = false;
    }
  }
  DCHECK_NE(number_of_entries, 0u);
  return number_of_entries;
}

template <typename GetBssOffset>
static size_t CalculateIndexBssMappingSize(size_t number_of_indexes,
                                           size_t slot_size,
                                           const BitVector& indexes,
                                           GetBssOffset get_bss_offset) {
  size_t number_of_entries = CalculateNumberOfIndexBssMappingEntries(number_of_indexes,
                                                                     slot_size,
                                                                     indexes,
                                                                     get_bss_offset);
  return IndexBssMapping::ComputeSize(number_of_entries);
}

static size_t CalculateIndexBssMappingSize(
    const DexFile* dex_file,
    const BitVector& type_indexes,
    const SafeMap<TypeReference, size_t, TypeReferenceValueComparator>& bss_entries) {
  return CalculateIndexBssMappingSize(
      dex_file->NumTypeIds(),
      sizeof(GcRoot<mirror::Class>),
      type_indexes,
      [=](uint32_t index) { return bss_entries.Get({dex_file, dex::TypeIndex(index)}); });
}

size_t OatWriter::InitIndexBssMappings(size_t offset) {
  if (bss_method_entry_references_.empty() &&
      bss_type_entry_references_.empty() &&
      bss_public_type_entry_references_.empty() &&
      bss_package_type_entry_references_.empty() &&
      bss_string_entry_references_.empty() &&
      bss_method_type_entry_references_.empty()) {
    return offset;
  }
  // If there are any classes, the class offsets allocation aligns the offset
  // and we cannot have any index bss mappings without class offsets.
  static_assert(alignof(IndexBssMapping) == 4u, "IndexBssMapping alignment check.");
  DCHECK_ALIGNED(offset, 4u);

  size_t number_of_method_dex_files = 0u;
  size_t number_of_type_dex_files = 0u;
  size_t number_of_public_type_dex_files = 0u;
  size_t number_of_package_type_dex_files = 0u;
  size_t number_of_string_dex_files = 0u;
  size_t number_of_method_type_dex_files = 0u;
  for (size_t i = 0, size = dex_files_->size(); i != size; ++i) {
    const DexFile* dex_file = (*dex_files_)[i];
    offset = InitIndexBssMappingsHelper(offset,
                                        dex_file,
                                        number_of_method_dex_files,
                                        number_of_type_dex_files,
                                        number_of_public_type_dex_files,
                                        number_of_package_type_dex_files,
                                        number_of_string_dex_files,
                                        number_of_method_type_dex_files,
                                        oat_dex_files_[i].method_bss_mapping_offset_,
                                        oat_dex_files_[i].type_bss_mapping_offset_,
                                        oat_dex_files_[i].public_type_bss_mapping_offset_,
                                        oat_dex_files_[i].package_type_bss_mapping_offset_,
                                        oat_dex_files_[i].string_bss_mapping_offset_,
                                        oat_dex_files_[i].method_type_bss_mapping_offset_);
  }

  if (!(compiler_options_.IsBootImage() || compiler_options_.IsBootImageExtension())) {
    ArrayRef<const DexFile* const> boot_class_path(
        Runtime::Current()->GetClassLinker()->GetBootClassPath());
    // We initialize bcp_bss_info for single image and purposively leave it empty for the multi
    // image case.
    // Note that we have an early break at the beginning of the method, so `bcp_bss_info_` will also
    // be empty in the case of having no mappings at all.
    DCHECK(bcp_bss_info_.empty());
    bcp_bss_info_.resize(boot_class_path.size());
    for (size_t i = 0, size = bcp_bss_info_.size(); i != size; ++i) {
      const DexFile* dex_file = boot_class_path[i];
      DCHECK(!ContainsElement(*dex_files_, dex_file));
      offset = InitIndexBssMappingsHelper(offset,
                                          dex_file,
                                          number_of_method_dex_files,
                                          number_of_type_dex_files,
                                          number_of_public_type_dex_files,
                                          number_of_package_type_dex_files,
                                          number_of_string_dex_files,
                                          number_of_method_type_dex_files,
                                          bcp_bss_info_[i].method_bss_mapping_offset,
                                          bcp_bss_info_[i].type_bss_mapping_offset,
                                          bcp_bss_info_[i].public_type_bss_mapping_offset,
                                          bcp_bss_info_[i].package_type_bss_mapping_offset,
                                          bcp_bss_info_[i].string_bss_mapping_offset,
                                          bcp_bss_info_[i].method_type_bss_mapping_offset);
    }
  }

  // Check that all dex files targeted by bss entries are in `*dex_files_`, or in the bootclaspath's
  // DexFiles in the single image case.
  CHECK_EQ(number_of_method_dex_files, bss_method_entry_references_.size());
  CHECK_EQ(number_of_type_dex_files, bss_type_entry_references_.size());
  CHECK_EQ(number_of_public_type_dex_files, bss_public_type_entry_references_.size());
  CHECK_EQ(number_of_package_type_dex_files, bss_package_type_entry_references_.size());
  CHECK_EQ(number_of_string_dex_files, bss_string_entry_references_.size());
  CHECK_EQ(number_of_method_type_dex_files, bss_method_type_entry_references_.size());

  return offset;
}

size_t OatWriter::InitIndexBssMappingsHelper(size_t offset,
                                             const DexFile* dex_file,
                                             /*inout*/ size_t& number_of_method_dex_files,
                                             /*inout*/ size_t& number_of_type_dex_files,
                                             /*inout*/ size_t& number_of_public_type_dex_files,
                                             /*inout*/ size_t& number_of_package_type_dex_files,
                                             /*inout*/ size_t& number_of_string_dex_files,
                                             /*inout*/ size_t& number_of_method_type_dex_files,
                                             /*inout*/ uint32_t& method_bss_mapping_offset,
                                             /*inout*/ uint32_t& type_bss_mapping_offset,
                                             /*inout*/ uint32_t& public_type_bss_mapping_offset,
                                             /*inout*/ uint32_t& package_type_bss_mapping_offset,
                                             /*inout*/ uint32_t& string_bss_mapping_offset,
                                             /*inout*/ uint32_t& method_type_bss_mapping_offset) {
  const PointerSize pointer_size = GetInstructionSetPointerSize(oat_header_->GetInstructionSet());
  auto method_it = bss_method_entry_references_.find(dex_file);
  if (method_it != bss_method_entry_references_.end()) {
    const BitVector& method_indexes = method_it->second;
    ++number_of_method_dex_files;
    method_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(dex_file->NumMethodIds(),
                                           static_cast<size_t>(pointer_size),
                                           method_indexes,
                                           [=](uint32_t index) {
                                             return bss_method_entries_.Get({dex_file, index});
                                           });
  }

  auto type_it = bss_type_entry_references_.find(dex_file);
  if (type_it != bss_type_entry_references_.end()) {
    const BitVector& type_indexes = type_it->second;
    ++number_of_type_dex_files;
    type_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(dex_file, type_indexes, bss_type_entries_);
  }

  auto public_type_it = bss_public_type_entry_references_.find(dex_file);
  if (public_type_it != bss_public_type_entry_references_.end()) {
    const BitVector& type_indexes = public_type_it->second;
    ++number_of_public_type_dex_files;
    public_type_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(dex_file, type_indexes, bss_public_type_entries_);
  }

  auto package_type_it = bss_package_type_entry_references_.find(dex_file);
  if (package_type_it != bss_package_type_entry_references_.end()) {
    const BitVector& type_indexes = package_type_it->second;
    ++number_of_package_type_dex_files;
    package_type_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(dex_file, type_indexes, bss_package_type_entries_);
  }

  auto string_it = bss_string_entry_references_.find(dex_file);
  if (string_it != bss_string_entry_references_.end()) {
    const BitVector& string_indexes = string_it->second;
    ++number_of_string_dex_files;
    string_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(
        dex_file->NumStringIds(),
        sizeof(GcRoot<mirror::String>),
        string_indexes,
        [=](uint32_t index) {
          return bss_string_entries_.Get({dex_file, dex::StringIndex(index)});
        });
  }

  auto method_type_it = bss_method_type_entry_references_.find(dex_file);
  if (method_type_it != bss_method_type_entry_references_.end()) {
    const BitVector& proto_indexes = method_type_it->second;
    ++number_of_method_type_dex_files;
    method_type_bss_mapping_offset = offset;
    offset += CalculateIndexBssMappingSize(
        dex_file->NumProtoIds(),
        sizeof(GcRoot<mirror::MethodType>),
        proto_indexes,
        [=](uint32_t index) {
          return bss_method_type_entries_.Get({dex_file, dex::ProtoIndex(index)});
        });
  }

  return offset;
}

size_t OatWriter::InitOatDexFiles(size_t offset) {
  // Initialize offsets of oat dex files.
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    oat_dex_file.offset_ = offset;
    offset += oat_dex_file.SizeOf();
  }
  return offset;
}

size_t OatWriter::InitBcpBssInfo(size_t offset) {
  if (bcp_bss_info_.size() == 0) {
    return offset;
  }

  // We first increase the offset to make room to store the number of BCP DexFiles, if we have at
  // least one entry.
  oat_header_->SetBcpBssInfoOffset(offset);
  offset += sizeof(uint32_t);

  for (BssMappingInfo& info : bcp_bss_info_) {
    info.offset_ = offset;
    offset += BssMappingInfo::SizeOf();
  }
  return offset;
}

size_t OatWriter::InitOatCode(size_t offset) {
  // calculate the offsets within OatHeader to executable code
  size_t old_offset = offset;
  // required to be on a new page boundary
  offset = RoundUp(offset, kElfSegmentAlignment);
  oat_header_->SetExecutableOffset(offset);
  size_executable_offset_alignment_ = offset - old_offset;
  InstructionSet instruction_set = compiler_options_.GetInstructionSet();
  if (GetCompilerOptions().IsBootImage() && primary_oat_file_) {
    const bool generate_debug_info = GetCompilerOptions().GenerateAnyDebugInfo();
    size_t adjusted_offset = offset;

    #define DO_TRAMPOLINE(field, fn_name)                                                 \
      /* Pad with at least four 0xFFs so we can do DCHECKs in OatQuickMethodHeader */     \
      offset = CompiledCode::AlignCode(offset + 4, instruction_set);                      \
      adjusted_offset = offset + GetInstructionSetEntryPointAdjustment(instruction_set);  \
      oat_header_->Set ## fn_name ## Offset(adjusted_offset);                             \
      (field) = compiler_driver_->Create ## fn_name();                                    \
      if (generate_debug_info) {                                                          \
        debug::MethodDebugInfo info = {};                                                 \
        info.custom_name = #fn_name;                                                      \
        info.isa = instruction_set;                                                       \
        info.is_code_address_text_relative = true;                                        \
        /* Use the code offset rather than the `adjusted_offset`. */                      \
        info.code_address = offset - oat_header_->GetExecutableOffset();                  \
        info.code_size = (field)->size();                                                 \
        method_info_.push_back(std::move(info));                                          \
      }                                                                                   \
      offset += (field)->size();

    DO_TRAMPOLINE(jni_dlsym_lookup_trampoline_, JniDlsymLookupTrampoline);
    DO_TRAMPOLINE(jni_dlsym_lookup_critical_trampoline_, JniDlsymLookupCriticalTrampoline);
    DO_TRAMPOLINE(quick_generic_jni_trampoline_, QuickGenericJniTrampoline);
    DO_TRAMPOLINE(quick_imt_conflict_trampoline_, QuickImtConflictTrampoline);
    DO_TRAMPOLINE(quick_resolution_trampoline_, QuickResolutionTrampoline);
    DO_TRAMPOLINE(quick_to_interpreter_bridge_, QuickToInterpreterBridge);
    DO_TRAMPOLINE(nterp_trampoline_, NterpTrampoline);

    #undef DO_TRAMPOLINE
  } else {
    oat_header_->SetJniDlsymLookupTrampolineOffset(0);
    oat_header_->SetJniDlsymLookupCriticalTrampolineOffset(0);
    oat_header_->SetQuickGenericJniTrampolineOffset(0);
    oat_header_->SetQuickImtConflictTrampolineOffset(0);
    oat_header_->SetQuickResolutionTrampolineOffset(0);
    oat_header_->SetQuickToInterpreterBridgeOffset(0);
    oat_header_->SetNterpTrampolineOffset(0);
  }
  return offset;
}

size_t OatWriter::InitOatCodeDexFiles(size_t offset) {
  if (!GetCompilerOptions().IsAnyCompilationEnabled()) {
    if (kOatWriterDebugOatCodeLayout) {
      LOG(INFO) << "InitOatCodeDexFiles: OatWriter("
                << this << "), "
                << "compilation is disabled";
    }

    return offset;
  }
  bool success = false;

  {
    ScopedObjectAccess soa(Thread::Current());

    LayoutCodeMethodVisitor layout_code_visitor(this, offset);
    success = VisitDexMethods(&layout_code_visitor);
    DCHECK(success);

    LayoutReserveOffsetCodeMethodVisitor layout_reserve_code_visitor(
        this,
        offset,
        layout_code_visitor.ReleaseOrderedMethods());
    success = layout_reserve_code_visitor.Visit();
    DCHECK(success);
    offset = layout_reserve_code_visitor.GetOffset();

    // Save the method order because the WriteCodeMethodVisitor will need this
    // order again.
    DCHECK(ordered_methods_ == nullptr);
    ordered_methods_.reset(
        new OrderedMethodList(
            layout_reserve_code_visitor.ReleaseOrderedMethods()));

    if (kOatWriterDebugOatCodeLayout) {
      LOG(INFO) << "IniatOatCodeDexFiles: method order: ";
      for (const OrderedMethodData& ordered_method : *ordered_methods_) {
        std::string pretty_name = ordered_method.method_reference.PrettyMethod();
        LOG(INFO) << pretty_name
                  << "@ offset "
                  << relative_patcher_->GetOffset(ordered_method.method_reference)
                  << " X hotness "
                  << ordered_method.hotness_bits;
      }
    }
  }

  if (HasImage()) {
    ScopedObjectAccess soa(Thread::Current());
    ScopedAssertNoThreadSuspension sants("Init image method visitor", Thread::Current());
    InitImageMethodVisitor image_visitor(this, offset, dex_files_);
    success = VisitDexMethods(&image_visitor);
    image_visitor.Postprocess();
    DCHECK(success);
    offset = image_visitor.GetOffset();
  }

  return offset;
}

size_t OatWriter::InitDataBimgRelRoLayout(size_t offset) {
  DCHECK_EQ(data_bimg_rel_ro_size_, 0u);
  if (data_bimg_rel_ro_entries_.empty()) {
    // Nothing to put to the .data.bimg.rel.ro section.
    return offset;
  }

  data_bimg_rel_ro_start_ = RoundUp(offset, kElfSegmentAlignment);

  for (auto& entry : data_bimg_rel_ro_entries_) {
    size_t& entry_offset = entry.second;
    entry_offset = data_bimg_rel_ro_size_;
    data_bimg_rel_ro_size_ += sizeof(uint32_t);
  }

  offset = data_bimg_rel_ro_start_ + data_bimg_rel_ro_size_;
  return offset;
}

void OatWriter::InitBssLayout(InstructionSet instruction_set) {
  {
    InitBssLayoutMethodVisitor visitor(this);
    bool success = VisitDexMethods(&visitor);
    DCHECK(success);
  }

  DCHECK_EQ(bss_size_, 0u);
  if (bss_method_entries_.empty() &&
      bss_type_entries_.empty() &&
      bss_public_type_entries_.empty() &&
      bss_package_type_entries_.empty() &&
      bss_string_entries_.empty() &&
      bss_method_type_entries_.empty()) {
    // Nothing to put to the .bss section.
    return;
  }

  PointerSize pointer_size = GetInstructionSetPointerSize(instruction_set);
  bss_methods_offset_ = bss_size_;

  // Prepare offsets for .bss ArtMethod entries.
  for (auto& entry : bss_method_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += static_cast<size_t>(pointer_size);
  }

  bss_roots_offset_ = bss_size_;

  // Prepare offsets for .bss Class entries.
  for (auto& entry : bss_type_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += sizeof(GcRoot<mirror::Class>);
  }
  // Prepare offsets for .bss public Class entries.
  for (auto& entry : bss_public_type_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += sizeof(GcRoot<mirror::Class>);
  }
  // Prepare offsets for .bss package Class entries.
  for (auto& entry : bss_package_type_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += sizeof(GcRoot<mirror::Class>);
  }
  // Prepare offsets for .bss String entries.
  for (auto& entry : bss_string_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += sizeof(GcRoot<mirror::String>);
  }
  // Prepare offsets for .bss MethodType entries.
  for (auto& entry : bss_method_type_entries_) {
    DCHECK_EQ(entry.second, 0u);
    entry.second = bss_size_;
    bss_size_ += sizeof(GcRoot<mirror::MethodType>);
  }
}

bool OatWriter::WriteRodata(OutputStream* out) {
  CHECK(write_state_ == WriteState::kWriteRoData);

  size_t file_offset = oat_data_offset_;
  off_t current_offset = out->Seek(0, kSeekCurrent);
  if (current_offset == static_cast<off_t>(-1)) {
    PLOG(ERROR) << "Failed to retrieve current position in " << out->GetLocation();
  }
  DCHECK_GE(static_cast<size_t>(current_offset), file_offset + oat_header_->GetHeaderSize());
  size_t relative_offset = current_offset - file_offset;

  // Wrap out to update checksum with each write.
  ChecksumUpdatingOutputStream checksum_updating_out(out, this);
  out = &checksum_updating_out;

  relative_offset = WriteClassOffsets(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write class offsets to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteClasses(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write classes to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteIndexBssMappings(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write method bss mappings to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteMaps(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write oat code to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteOatDexFiles(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write oat dex information to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteBcpBssInfo(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    PLOG(ERROR) << "Failed to write BCP bss information to " << out->GetLocation();
    return false;
  }

  // Write padding.
  off_t new_offset = out->Seek(size_executable_offset_alignment_, kSeekCurrent);
  relative_offset += size_executable_offset_alignment_;
  DCHECK_EQ(relative_offset, oat_header_->GetExecutableOffset());
  size_t expected_file_offset = file_offset + relative_offset;
  if (static_cast<uint32_t>(new_offset) != expected_file_offset) {
    PLOG(ERROR) << "Failed to seek to oat code section. Actual: " << new_offset
                << " Expected: " << expected_file_offset << " File: " << out->GetLocation();
    return false;
  }
  DCHECK_OFFSET();

  write_state_ = WriteState::kWriteText;
  return true;
}

void OatWriter::WriteVerifierDeps(verifier::VerifierDeps* verifier_deps,
                                  /*out*/std::vector<uint8_t>* buffer) {
  if (verifier_deps == nullptr) {
    // Nothing to write. Record the offset, but no need
    // for alignment.
    vdex_verifier_deps_offset_ = vdex_size_;
    return;
  }

  TimingLogger::ScopedTiming split("VDEX verifier deps", timings_);

  DCHECK(buffer->empty());
  verifier_deps->Encode(*dex_files_, buffer);
  size_verifier_deps_ = buffer->size();

  // Verifier deps data should be 4 byte aligned.
  size_verifier_deps_alignment_ = RoundUp(vdex_size_, 4u) - vdex_size_;
  buffer->insert(buffer->begin(), size_verifier_deps_alignment_, 0u);

  vdex_size_ += size_verifier_deps_alignment_;
  vdex_verifier_deps_offset_ = vdex_size_;
  vdex_size_ += size_verifier_deps_;
}

bool OatWriter::WriteCode(OutputStream* out) {
  CHECK(write_state_ == WriteState::kWriteText);

  // Wrap out to update checksum with each write.
  ChecksumUpdatingOutputStream checksum_updating_out(out, this);
  out = &checksum_updating_out;

  SetMultiOatRelativePatcherAdjustment();

  const size_t file_offset = oat_data_offset_;
  size_t relative_offset = oat_header_->GetExecutableOffset();
  DCHECK_OFFSET();

  relative_offset = WriteCode(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    LOG(ERROR) << "Failed to write oat code to " << out->GetLocation();
    return false;
  }

  relative_offset = WriteCodeDexFiles(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    LOG(ERROR) << "Failed to write oat code for dex files to " << out->GetLocation();
    return false;
  }

  if (data_bimg_rel_ro_size_ != 0u) {
    write_state_ = WriteState::kWriteDataBimgRelRo;
  } else {
    if (!CheckOatSize(out, file_offset, relative_offset)) {
      return false;
    }
    write_state_ = WriteState::kWriteHeader;
  }
  return true;
}

bool OatWriter::WriteDataBimgRelRo(OutputStream* out) {
  CHECK(write_state_ == WriteState::kWriteDataBimgRelRo);

  // Wrap out to update checksum with each write.
  ChecksumUpdatingOutputStream checksum_updating_out(out, this);
  out = &checksum_updating_out;

  const size_t file_offset = oat_data_offset_;
  size_t relative_offset = data_bimg_rel_ro_start_;

  // Record the padding before the .data.bimg.rel.ro section.
  // Do not write anything, this zero-filled part was skipped (Seek()) when starting the section.
  size_t code_end = GetOatHeader().GetExecutableOffset() + code_size_;
  DCHECK_EQ(RoundUp(code_end, kElfSegmentAlignment), relative_offset);
  size_t padding_size = relative_offset - code_end;
  DCHECK_EQ(size_data_bimg_rel_ro_alignment_, 0u);
  size_data_bimg_rel_ro_alignment_ = padding_size;

  relative_offset = WriteDataBimgRelRo(out, file_offset, relative_offset);
  if (relative_offset == 0) {
    LOG(ERROR) << "Failed to write boot image relocations to " << out->GetLocation();
    return false;
  }

  if (!CheckOatSize(out, file_offset, relative_offset)) {
    return false;
  }
  write_state_ = WriteState::kWriteHeader;
  return true;
}

bool OatWriter::CheckOatSize(OutputStream* out, size_t file_offset, size_t relative_offset) {
  const off_t oat_end_file_offset = out->Seek(0, kSeekCurrent);
  if (oat_end_file_offset == static_cast<off_t>(-1)) {
    LOG(ERROR) << "Failed to get oat end file offset in " << out->GetLocation();
    return false;
  }

  if (kIsDebugBuild) {
    uint32_t size_total = 0;
    #define DO_STAT(x) \
      VLOG(compiler) << #x "=" << PrettySize(x) << " (" << (x) << "B)"; \
      size_total += (x);

    DO_STAT(size_vdex_header_);
    DO_STAT(size_vdex_checksums_);
    DO_STAT(size_dex_file_alignment_);
    DO_STAT(size_executable_offset_alignment_);
    DO_STAT(size_oat_header_);
    DO_STAT(size_oat_header_key_value_store_);
    DO_STAT(size_dex_file_);
    DO_STAT(size_verifier_deps_);
    DO_STAT(size_verifier_deps_alignment_);
    DO_STAT(size_vdex_lookup_table_);
    DO_STAT(size_vdex_lookup_table_alignment_);
    DO_STAT(size_interpreter_to_interpreter_bridge_);
    DO_STAT(size_interpreter_to_compiled_code_bridge_);
    DO_STAT(size_jni_dlsym_lookup_trampoline_);
    DO_STAT(size_jni_dlsym_lookup_critical_trampoline_);
    DO_STAT(size_quick_generic_jni_trampoline_);
    DO_STAT(size_quick_imt_conflict_trampoline_);
    DO_STAT(size_quick_resolution_trampoline_);
    DO_STAT(size_quick_to_interpreter_bridge_);
    DO_STAT(size_nterp_trampoline_);
    DO_STAT(size_trampoline_alignment_);
    DO_STAT(size_method_header_);
    DO_STAT(size_code_);
    DO_STAT(size_code_alignment_);
    DO_STAT(size_data_bimg_rel_ro_);
    DO_STAT(size_data_bimg_rel_ro_alignment_);
    DO_STAT(size_relative_call_thunks_);
    DO_STAT(size_misc_thunks_);
    DO_STAT(size_vmap_table_);
    DO_STAT(size_method_info_);
    DO_STAT(size_oat_dex_file_location_size_);
    DO_STAT(size_oat_dex_file_location_data_);
    DO_STAT(size_oat_dex_file_magic_);
    DO_STAT(size_oat_dex_file_location_checksum_);
    DO_STAT(size_oat_dex_file_sha1_);
    DO_STAT(size_oat_dex_file_offset_);
    DO_STAT(size_oat_dex_file_class_offsets_offset_);
    DO_STAT(size_oat_dex_file_lookup_table_offset_);
    DO_STAT(size_oat_dex_file_dex_layout_sections_offset_);
    DO_STAT(size_oat_dex_file_dex_layout_sections_);
    DO_STAT(size_oat_dex_file_dex_layout_sections_alignment_);
    DO_STAT(size_oat_dex_file_method_bss_mapping_offset_);
    DO_STAT(size_oat_dex_file_type_bss_mapping_offset_);
    DO_STAT(size_oat_dex_file_public_type_bss_mapping_offset_);
    DO_STAT(size_oat_dex_file_package_type_bss_mapping_offset_);
    DO_STAT(size_oat_dex_file_string_bss_mapping_offset_);
    DO_STAT(size_oat_dex_file_method_type_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_size_);
    DO_STAT(size_bcp_bss_info_method_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_type_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_public_type_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_package_type_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_string_bss_mapping_offset_);
    DO_STAT(size_bcp_bss_info_method_type_bss_mapping_offset_);
    DO_STAT(size_oat_class_offsets_alignment_);
    DO_STAT(size_oat_class_offsets_);
    DO_STAT(size_oat_class_type_);
    DO_STAT(size_oat_class_status_);
    DO_STAT(size_oat_class_num_methods_);
    DO_STAT(size_oat_class_method_bitmaps_);
    DO_STAT(size_oat_class_method_offsets_);
    DO_STAT(size_method_bss_mappings_);
    DO_STAT(size_type_bss_mappings_);
    DO_STAT(size_public_type_bss_mappings_);
    DO_STAT(size_package_type_bss_mappings_);
    DO_STAT(size_string_bss_mappings_);
    DO_STAT(size_method_type_bss_mappings_);
    #undef DO_STAT

    VLOG(compiler) << "size_total=" << PrettySize(size_total) << " (" << size_total << "B)";

    CHECK_EQ(vdex_size_ + oat_size_, size_total);
    CHECK_EQ(file_offset + size_total - vdex_size_, static_cast<size_t>(oat_end_file_offset));
  }

  CHECK_EQ(file_offset + oat_size_, static_cast<size_t>(oat_end_file_offset));
  CHECK_EQ(oat_size_, relative_offset);

  write_state_ = WriteState::kWriteHeader;
  return true;
}

bool OatWriter::WriteHeader(OutputStream* out) {
  CHECK(write_state_ == WriteState::kWriteHeader);

  // Update checksum with header data.
  DCHECK_EQ(oat_header_->GetChecksum(), 0u);  // For checksum calculation.
  const uint8_t* header_begin = reinterpret_cast<const uint8_t*>(oat_header_.get());
  const uint8_t* header_end = oat_header_->GetKeyValueStore() + oat_header_->GetKeyValueStoreSize();
  uint32_t old_checksum = oat_checksum_;
  oat_checksum_ = adler32(old_checksum, header_begin, header_end - header_begin);
  oat_header_->SetChecksum(oat_checksum_);

  const size_t file_offset = oat_data_offset_;

  off_t current_offset = out->Seek(0, kSeekCurrent);
  if (current_offset == static_cast<off_t>(-1)) {
    PLOG(ERROR) << "Failed to get current offset from " << out->GetLocation();
    return false;
  }
  if (out->Seek(file_offset, kSeekSet) == static_cast<off_t>(-1)) {
    PLOG(ERROR) << "Failed to seek to oat header position in " << out->GetLocation();
    return false;
  }
  DCHECK_EQ(file_offset, static_cast<size_t>(out->Seek(0, kSeekCurrent)));

  // Flush all other data before writing the header.
  if (!out->Flush()) {
    PLOG(ERROR) << "Failed to flush before writing oat header to " << out->GetLocation();
    return false;
  }
  // Write the header.
  size_t header_size = oat_header_->GetHeaderSize();
  if (!out->WriteFully(oat_header_.get(), header_size)) {
    PLOG(ERROR) << "Failed to write oat header to " << out->GetLocation();
    return false;
  }
  // Flush the header data.
  if (!out->Flush()) {
    PLOG(ERROR) << "Failed to flush after writing oat header to " << out->GetLocation();
    return false;
  }

  if (out->Seek(current_offset, kSeekSet) == static_cast<off_t>(-1)) {
    PLOG(ERROR) << "Failed to seek back after writing oat header to " << out->GetLocation();
    return false;
  }
  DCHECK_EQ(current_offset, out->Seek(0, kSeekCurrent));

  write_state_ = WriteState::kDone;
  return true;
}

size_t OatWriter::WriteClassOffsets(OutputStream* out, size_t file_offset, size_t relative_offset) {
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    if (oat_dex_file.class_offsets_offset_ != 0u) {
      // Class offsets are required to be 4 byte aligned.
      if (UNLIKELY(!IsAligned<4u>(relative_offset))) {
        size_t padding_size =  RoundUp(relative_offset, 4u) - relative_offset;
        if (!WriteUpTo16BytesAlignment(out, padding_size, &size_oat_class_offsets_alignment_)) {
          return 0u;
        }
        relative_offset += padding_size;
      }
      DCHECK_OFFSET();
      if (!oat_dex_file.WriteClassOffsets(this, out)) {
        return 0u;
      }
      relative_offset += oat_dex_file.GetClassOffsetsRawSize();
    }
  }
  return relative_offset;
}

size_t OatWriter::WriteClasses(OutputStream* out, size_t file_offset, size_t relative_offset) {
  const bool may_have_compiled = MayHaveCompiledMethods();
  if (may_have_compiled) {
    CHECK_EQ(oat_class_headers_.size(), oat_classes_.size());
  }
  for (size_t i = 0; i < oat_class_headers_.size(); ++i) {
    // If there are any classes, the class offsets allocation aligns the offset.
    DCHECK_ALIGNED(relative_offset, 4u);
    DCHECK_OFFSET();
    if (!oat_class_headers_[i].Write(this, out, oat_data_offset_)) {
      return 0u;
    }
    relative_offset += oat_class_headers_[i].SizeOf();
    if (may_have_compiled) {
      if (!oat_classes_[i].Write(this, out)) {
        return 0u;
      }
      relative_offset += oat_classes_[i].SizeOf();
    }
  }
  return relative_offset;
}

size_t OatWriter::WriteMaps(OutputStream* out, size_t file_offset, size_t relative_offset) {
  {
    if (UNLIKELY(!out->WriteFully(code_info_data_.data(), code_info_data_.size()))) {
      return 0;
    }
    relative_offset += code_info_data_.size();
    size_vmap_table_ = code_info_data_.size();
    DCHECK_OFFSET();
  }

  return relative_offset;
}


template <typename GetBssOffset>
size_t WriteIndexBssMapping(OutputStream* out,
                            size_t number_of_indexes,
                            size_t slot_size,
                            const BitVector& indexes,
                            GetBssOffset get_bss_offset) {
  // Allocate the IndexBssMapping.
  size_t number_of_entries = CalculateNumberOfIndexBssMappingEntries(
      number_of_indexes, slot_size, indexes, get_bss_offset);
  size_t mappings_size = IndexBssMapping::ComputeSize(number_of_entries);
  DCHECK_ALIGNED(mappings_size, sizeof(uint32_t));
  std::unique_ptr<uint32_t[]> storage(new uint32_t[mappings_size / sizeof(uint32_t)]);
  IndexBssMapping* mappings = new(storage.get()) IndexBssMapping(number_of_entries);
  mappings->ClearPadding();
  // Encode the IndexBssMapping.
  IndexBssMappingEncoder encoder(number_of_indexes, slot_size);
  auto init_it = mappings->begin();
  bool first_index = true;
  for (uint32_t index : indexes.Indexes()) {
    size_t bss_offset = get_bss_offset(index);
    if (first_index) {
      first_index = false;
      encoder.Reset(index, bss_offset);
    } else if (!encoder.TryMerge(index, bss_offset)) {
      *init_it = encoder.GetEntry();
      ++init_it;
      encoder.Reset(index, bss_offset);
    }
  }
  // Store the last entry.
  *init_it = encoder.GetEntry();
  ++init_it;
  DCHECK(init_it == mappings->end());

  if (!out->WriteFully(storage.get(), mappings_size)) {
    return 0u;
  }
  return mappings_size;
}

size_t WriteIndexBssMapping(
    OutputStream* out,
    const DexFile* dex_file,
    const BitVector& type_indexes,
    const SafeMap<TypeReference, size_t, TypeReferenceValueComparator>& bss_entries) {
  return WriteIndexBssMapping(
      out,
      dex_file->NumTypeIds(),
      sizeof(GcRoot<mirror::Class>),
      type_indexes,
      [=](uint32_t index) { return bss_entries.Get({dex_file, dex::TypeIndex(index)}); });
}

size_t OatWriter::WriteIndexBssMappingsHelper(OutputStream* out,
                                              size_t file_offset,
                                              size_t relative_offset,
                                              const DexFile* dex_file,
                                              uint32_t method_bss_mapping_offset,
                                              uint32_t type_bss_mapping_offset,
                                              uint32_t public_type_bss_mapping_offset,
                                              uint32_t package_type_bss_mapping_offset,
                                              uint32_t string_bss_mapping_offset,
                                              uint32_t method_type_bss_mapping_offset) {
  const PointerSize pointer_size = GetInstructionSetPointerSize(oat_header_->GetInstructionSet());
  auto method_it = bss_method_entry_references_.find(dex_file);
  if (method_it != bss_method_entry_references_.end()) {
    const BitVector& method_indexes = method_it->second;
    DCHECK_EQ(relative_offset, method_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t method_mappings_size =
        WriteIndexBssMapping(out,
                             dex_file->NumMethodIds(),
                             static_cast<size_t>(pointer_size),
                             method_indexes,
                             [=](uint32_t index) {
                               return bss_method_entries_.Get({dex_file, index});
                             });
    if (method_mappings_size == 0u) {
      return 0u;
    }
    size_method_bss_mappings_ += method_mappings_size;
    relative_offset += method_mappings_size;
  } else {
    DCHECK_EQ(0u, method_bss_mapping_offset);
  }

  auto type_it = bss_type_entry_references_.find(dex_file);
  if (type_it != bss_type_entry_references_.end()) {
    const BitVector& type_indexes = type_it->second;
    DCHECK_EQ(relative_offset, type_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t type_mappings_size =
        WriteIndexBssMapping(out, dex_file, type_indexes, bss_type_entries_);
    if (type_mappings_size == 0u) {
      return 0u;
    }
    size_type_bss_mappings_ += type_mappings_size;
    relative_offset += type_mappings_size;
  } else {
    DCHECK_EQ(0u, type_bss_mapping_offset);
  }

  auto public_type_it = bss_public_type_entry_references_.find(dex_file);
  if (public_type_it != bss_public_type_entry_references_.end()) {
    const BitVector& type_indexes = public_type_it->second;
    DCHECK_EQ(relative_offset, public_type_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t public_type_mappings_size =
        WriteIndexBssMapping(out, dex_file, type_indexes, bss_public_type_entries_);
    if (public_type_mappings_size == 0u) {
      return 0u;
    }
    size_public_type_bss_mappings_ += public_type_mappings_size;
    relative_offset += public_type_mappings_size;
  } else {
    DCHECK_EQ(0u, public_type_bss_mapping_offset);
  }

  auto package_type_it = bss_package_type_entry_references_.find(dex_file);
  if (package_type_it != bss_package_type_entry_references_.end()) {
    const BitVector& type_indexes = package_type_it->second;
    DCHECK_EQ(relative_offset, package_type_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t package_type_mappings_size =
        WriteIndexBssMapping(out, dex_file, type_indexes, bss_package_type_entries_);
    if (package_type_mappings_size == 0u) {
      return 0u;
    }
    size_package_type_bss_mappings_ += package_type_mappings_size;
    relative_offset += package_type_mappings_size;
  } else {
    DCHECK_EQ(0u, package_type_bss_mapping_offset);
  }

  auto string_it = bss_string_entry_references_.find(dex_file);
  if (string_it != bss_string_entry_references_.end()) {
    const BitVector& string_indexes = string_it->second;
    DCHECK_EQ(relative_offset, string_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t string_mappings_size =
        WriteIndexBssMapping(out,
                             dex_file->NumStringIds(),
                             sizeof(GcRoot<mirror::String>),
                             string_indexes,
                             [=](uint32_t index) {
                               return bss_string_entries_.Get({dex_file, dex::StringIndex(index)});
                             });
    if (string_mappings_size == 0u) {
      return 0u;
    }
    size_string_bss_mappings_ += string_mappings_size;
    relative_offset += string_mappings_size;
  } else {
    DCHECK_EQ(0u, string_bss_mapping_offset);
  }

  auto method_type_it = bss_method_type_entry_references_.find(dex_file);
  if (method_type_it != bss_method_type_entry_references_.end()) {
    const BitVector& method_type_indexes = method_type_it->second;
    DCHECK_EQ(relative_offset, method_type_bss_mapping_offset);
    DCHECK_OFFSET();
    size_t method_type_mappings_size =
        WriteIndexBssMapping(out,
                             dex_file->NumProtoIds(),
                             sizeof(GcRoot<mirror::MethodType>),
                             method_type_indexes,
                             [=](uint32_t index) {
                               return bss_method_type_entries_
                                   .Get({dex_file, dex::ProtoIndex(index)});
                             });
    if (method_type_mappings_size == 0u) {
      return 0u;
    }
    size_method_type_bss_mappings_ += method_type_mappings_size;
    relative_offset += method_type_mappings_size;
  } else {
    DCHECK_EQ(0u, method_type_bss_mapping_offset);
  }

  return relative_offset;
}

size_t OatWriter::WriteIndexBssMappings(OutputStream* out,
                                        size_t file_offset,
                                        size_t relative_offset) {
  TimingLogger::ScopedTiming split("WriteMethodBssMappings", timings_);
  if (bss_method_entry_references_.empty() &&
      bss_type_entry_references_.empty() &&
      bss_public_type_entry_references_.empty() &&
      bss_package_type_entry_references_.empty() &&
      bss_string_entry_references_.empty() &&
      bss_method_type_entry_references_.empty()) {
    return relative_offset;
  }
  // If there are any classes, the class offsets allocation aligns the offset
  // and we cannot have method bss mappings without class offsets.
  static_assert(alignof(IndexBssMapping) == sizeof(uint32_t), "IndexBssMapping alignment check.");
  DCHECK_ALIGNED(relative_offset, sizeof(uint32_t));

  for (size_t i = 0, size = dex_files_->size(); i != size; ++i) {
    const DexFile* dex_file = (*dex_files_)[i];
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    relative_offset = WriteIndexBssMappingsHelper(out,
                                                  file_offset,
                                                  relative_offset,
                                                  dex_file,
                                                  oat_dex_file->method_bss_mapping_offset_,
                                                  oat_dex_file->type_bss_mapping_offset_,
                                                  oat_dex_file->public_type_bss_mapping_offset_,
                                                  oat_dex_file->package_type_bss_mapping_offset_,
                                                  oat_dex_file->string_bss_mapping_offset_,
                                                  oat_dex_file->method_type_bss_mapping_offset_);
    if (relative_offset == 0u) {
      return 0u;
    }
  }

  if (!(compiler_options_.IsBootImage() || compiler_options_.IsBootImageExtension())) {
    ArrayRef<const DexFile* const> boot_class_path(
        Runtime::Current()->GetClassLinker()->GetBootClassPath());
    for (size_t i = 0, size = bcp_bss_info_.size(); i != size; ++i) {
      const DexFile* dex_file = boot_class_path[i];
      DCHECK(!ContainsElement(*dex_files_, dex_file));
      relative_offset =
          WriteIndexBssMappingsHelper(out,
                                      file_offset,
                                      relative_offset,
                                      dex_file,
                                      bcp_bss_info_[i].method_bss_mapping_offset,
                                      bcp_bss_info_[i].type_bss_mapping_offset,
                                      bcp_bss_info_[i].public_type_bss_mapping_offset,
                                      bcp_bss_info_[i].package_type_bss_mapping_offset,
                                      bcp_bss_info_[i].string_bss_mapping_offset,
                                      bcp_bss_info_[i].method_type_bss_mapping_offset);
      if (relative_offset == 0u) {
        return 0u;
      }
    }
  }
  return relative_offset;
}

size_t OatWriter::WriteOatDexFiles(OutputStream* out, size_t file_offset, size_t relative_offset) {
  TimingLogger::ScopedTiming split("WriteOatDexFiles", timings_);

  for (size_t i = 0, size = oat_dex_files_.size(); i != size; ++i) {
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    DCHECK_EQ(relative_offset, oat_dex_file->offset_);
    DCHECK_OFFSET();

    // Write OatDexFile.
    if (!oat_dex_file->Write(this, out)) {
      return 0u;
    }
    relative_offset += oat_dex_file->SizeOf();
  }

  return relative_offset;
}

size_t OatWriter::WriteBcpBssInfo(OutputStream* out, size_t file_offset, size_t relative_offset) {
  TimingLogger::ScopedTiming split("WriteBcpBssInfo", timings_);

  const uint32_t number_of_bcp_dexfiles = bcp_bss_info_.size();
  // We skip adding the number of DexFiles if we have no .bss mappings.
  if (number_of_bcp_dexfiles == 0) {
    return relative_offset;
  }

  if (!out->WriteFully(&number_of_bcp_dexfiles, sizeof(number_of_bcp_dexfiles))) {
    PLOG(ERROR) << "Failed to write the number of BCP dexfiles to " << out->GetLocation();
    return false;
  }
  size_bcp_bss_info_size_ = sizeof(number_of_bcp_dexfiles);
  relative_offset += size_bcp_bss_info_size_;

  for (size_t i = 0, size = number_of_bcp_dexfiles; i != size; ++i) {
    DCHECK_EQ(relative_offset, bcp_bss_info_[i].offset_);
    DCHECK_OFFSET();
    if (!bcp_bss_info_[i].Write(this, out)) {
      return 0u;
    }
    relative_offset += BssMappingInfo::SizeOf();
  }

  return relative_offset;
}

size_t OatWriter::WriteCode(OutputStream* out, size_t file_offset, size_t relative_offset) {
  InstructionSet instruction_set = compiler_options_.GetInstructionSet();
  if (GetCompilerOptions().IsBootImage() && primary_oat_file_) {
    #define DO_TRAMPOLINE(field) \
      do { \
        /* Pad with at least four 0xFFs so we can do DCHECKs in OatQuickMethodHeader */ \
        uint32_t aligned_offset = CompiledCode::AlignCode(relative_offset + 4, instruction_set); \
        uint32_t alignment_padding = aligned_offset - relative_offset; \
        for (size_t i = 0; i < alignment_padding; i++) { \
          uint8_t padding = 0xFF; \
          out->WriteFully(&padding, 1); \
        } \
        size_trampoline_alignment_ += alignment_padding; \
        if (!out->WriteFully((field)->data(), (field)->size())) { \
          PLOG(ERROR) << "Failed to write " # field " to " << out->GetLocation(); \
          return false; \
        } \
        size_ ## field += (field)->size(); \
        relative_offset += alignment_padding + (field)->size(); \
        DCHECK_OFFSET(); \
      } while (false)

    DO_TRAMPOLINE(jni_dlsym_lookup_trampoline_);
    DO_TRAMPOLINE(jni_dlsym_lookup_critical_trampoline_);
    DO_TRAMPOLINE(quick_generic_jni_trampoline_);
    DO_TRAMPOLINE(quick_imt_conflict_trampoline_);
    DO_TRAMPOLINE(quick_resolution_trampoline_);
    DO_TRAMPOLINE(quick_to_interpreter_bridge_);
    DO_TRAMPOLINE(nterp_trampoline_);
    #undef DO_TRAMPOLINE
  }
  return relative_offset;
}

size_t OatWriter::WriteCodeDexFiles(OutputStream* out,
                                    size_t file_offset,
                                    size_t relative_offset) {
  if (!GetCompilerOptions().IsAnyCompilationEnabled()) {
    // As with InitOatCodeDexFiles, also skip the writer if
    // compilation was disabled.
    if (kOatWriterDebugOatCodeLayout) {
      LOG(INFO) << "WriteCodeDexFiles: OatWriter("
                << this << "), "
                << "compilation is disabled";
    }

    return relative_offset;
  }
  ScopedObjectAccess soa(Thread::Current());
  DCHECK(ordered_methods_ != nullptr);
  std::unique_ptr<OrderedMethodList> ordered_methods_ptr =
      std::move(ordered_methods_);
  WriteCodeMethodVisitor visitor(this,
                                 out,
                                 file_offset,
                                 relative_offset,
                                 std::move(*ordered_methods_ptr));
  if (UNLIKELY(!visitor.Visit())) {
    return 0;
  }
  relative_offset = visitor.GetOffset();

  size_code_alignment_ += relative_patcher_->CodeAlignmentSize();
  size_relative_call_thunks_ += relative_patcher_->RelativeCallThunksSize();
  size_misc_thunks_ += relative_patcher_->MiscThunksSize();

  return relative_offset;
}

size_t OatWriter::WriteDataBimgRelRo(OutputStream* out,
                                     size_t file_offset,
                                     size_t relative_offset) {
  if (data_bimg_rel_ro_entries_.empty()) {
    return relative_offset;
  }

  // Write the entire .data.bimg.rel.ro with a single WriteFully().
  std::vector<uint32_t> data;
  data.reserve(data_bimg_rel_ro_entries_.size());
  for (const auto& entry : data_bimg_rel_ro_entries_) {
    uint32_t boot_image_offset = entry.first;
    data.push_back(boot_image_offset);
  }
  DCHECK_EQ(data.size(), data_bimg_rel_ro_entries_.size());
  DCHECK_OFFSET();
  if (!out->WriteFully(data.data(), data.size() * sizeof(data[0]))) {
    PLOG(ERROR) << "Failed to write .data.bimg.rel.ro in " << out->GetLocation();
    return 0u;
  }
  DCHECK_EQ(size_data_bimg_rel_ro_, 0u);
  size_data_bimg_rel_ro_ = data.size() * sizeof(data[0]);
  relative_offset += size_data_bimg_rel_ro_;
  return relative_offset;
}

bool OatWriter::RecordOatDataOffset(OutputStream* out) {
  // Get the elf file offset of the oat file.
  const off_t raw_file_offset = out->Seek(0, kSeekCurrent);
  if (raw_file_offset == static_cast<off_t>(-1)) {
    LOG(ERROR) << "Failed to get file offset in " << out->GetLocation();
    return false;
  }
  oat_data_offset_ = static_cast<size_t>(raw_file_offset);
  return true;
}

bool OatWriter::WriteDexFiles(File* file,
                              bool verify,
                              bool use_existing_vdex,
                              CopyOption copy_dex_files,
                              /*out*/ std::vector<MemMap>* opened_dex_files_map) {
  TimingLogger::ScopedTiming split("Write Dex files", timings_);

  // If extraction is enabled, only do it if not all the dex files are aligned and uncompressed.
  if (copy_dex_files == CopyOption::kOnlyIfCompressed) {
    extract_dex_files_into_vdex_ = false;
    for (OatDexFile& oat_dex_file : oat_dex_files_) {
      const DexFileContainer* container = oat_dex_file.GetDexFile()->GetContainer().get();
      if (!(container->IsZip() && container->IsFileMap())) {
        extract_dex_files_into_vdex_ = true;
        break;
      }
    }
  } else if (copy_dex_files == CopyOption::kAlways) {
    extract_dex_files_into_vdex_ = true;
  } else {
    DCHECK(copy_dex_files == CopyOption::kNever);
    extract_dex_files_into_vdex_ = false;
  }

  if (verify) {
    TimingLogger::ScopedTiming split2("Verify input Dex files", timings_);
    for (OatDexFile& oat_dex_file : oat_dex_files_) {
      const DexFile* dex_file = oat_dex_file.GetDexFile();
      if (dex_file->IsCompactDexFile()) {
        continue;  // Compact dex files can not be verified.
      }
      std::string error_msg;
      if (!dex::Verify(dex_file,
                       dex_file->GetLocation().c_str(),
                       /*verify_checksum=*/true,
                       &error_msg)) {
        LOG(ERROR) << "Failed to verify " << dex_file->GetLocation() << ": " << error_msg;
        return false;
      }
    }
  }

  // Compact dex reader/writer does not understand dex containers,
  // which is ok since dex containers replace compat-dex.
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    const DexFile* dex_file = oat_dex_file.GetDexFile();
    if (dex_file->HasDexContainer()) {
      compact_dex_level_ = CompactDexLevel::kCompactDexLevelNone;
    }
  }

  if (extract_dex_files_into_vdex_) {
    vdex_dex_files_offset_ = vdex_size_;

    // Perform dexlayout if compact dex is enabled. Also see
    // Dex2Oat::DoDexLayoutOptimizations.
    if (compact_dex_level_ != CompactDexLevel::kCompactDexLevelNone) {
      for (OatDexFile& oat_dex_file : oat_dex_files_) {
        // use_existing_vdex should not be used with compact dex and layout.
        CHECK(!use_existing_vdex)
            << "We should never update the input vdex when doing dexlayout or compact dex";
        if (!LayoutDexFile(&oat_dex_file)) {
          return false;
        }
      }
    }

    // Calculate the total size after the dex files.
    size_t vdex_size_with_dex_files = vdex_size_;
    for (OatDexFile& oat_dex_file : oat_dex_files_) {
      // Dex files are required to be 4 byte aligned.
      vdex_size_with_dex_files = RoundUp(vdex_size_with_dex_files, 4u);
      // Record offset for the dex file.
      oat_dex_file.dex_file_offset_ = vdex_size_with_dex_files;
      // Add the size of the dex file.
      if (oat_dex_file.dex_file_size_ < sizeof(DexFile::Header)) {
        LOG(ERROR) << "Dex file " << oat_dex_file.GetLocation() << " is too short: "
            << oat_dex_file.dex_file_size_ << " < " << sizeof(DexFile::Header);
        return false;
      }
      vdex_size_with_dex_files += oat_dex_file.dex_file_size_;
    }
    // Add the shared data section size.
    const uint8_t* raw_dex_file_shared_data_begin = nullptr;
    uint32_t shared_data_size = 0u;
    if (dex_container_ != nullptr) {
      shared_data_size = dex_container_->GetDataSection()->Size();
    } else {
      // Dex files from input vdex are represented as raw dex files and they can be
      // compact dex files. These need to specify the same shared data section if any.
      for (const OatDexFile& oat_dex_file : oat_dex_files_) {
        const DexFile* dex_file = oat_dex_file.GetDexFile();
        auto& header = dex_file->GetHeader();
        if (!dex_file->IsCompactDexFile() || header.data_size_ == 0u) {
          // Non compact dex does not have shared data section.
          continue;
        }
        const uint8_t* cur_data_begin = dex_file->Begin() + header.data_off_;
        if (raw_dex_file_shared_data_begin == nullptr) {
          raw_dex_file_shared_data_begin = cur_data_begin;
        } else if (raw_dex_file_shared_data_begin != cur_data_begin) {
          LOG(ERROR) << "Mismatched shared data sections in raw dex files: "
                     << static_cast<const void*>(raw_dex_file_shared_data_begin)
                     << " != " << static_cast<const void*>(cur_data_begin);
          return false;
        }
        // The different dex files currently can have different data sizes since
        // the dex writer writes them one at a time into the shared section.:w
        shared_data_size = std::max(shared_data_size, header.data_size_);
      }
    }
    if (shared_data_size != 0u) {
      // Shared data section is required to be 4 byte aligned.
      vdex_size_with_dex_files = RoundUp(vdex_size_with_dex_files, 4u);
    }
    vdex_dex_shared_data_offset_ = vdex_size_with_dex_files;
    vdex_size_with_dex_files += shared_data_size;

    // Extend the file and include the full page at the end as we need to write
    // additional data there and do not want to mmap that page twice.
    //
    // The page size value here is used to figure out the size of the mapping below,
    // however it doesn't affect the file contents or its size, so should not be
    // replaced with kElfSegmentAlignment.
    size_t page_aligned_size = RoundUp(vdex_size_with_dex_files, MemMap::GetPageSize());
    if (!use_existing_vdex) {
      if (file->SetLength(page_aligned_size) != 0) {
        PLOG(ERROR) << "Failed to resize vdex file " << file->GetPath();
        return false;
      }
    }

    std::string error_msg;
    MemMap dex_files_map = MemMap::MapFile(
        page_aligned_size,
        use_existing_vdex ? PROT_READ : PROT_READ | PROT_WRITE,
        MAP_SHARED,
        file->Fd(),
        /*start=*/ 0u,
        /*low_4gb=*/ false,
        file->GetPath().c_str(),
        &error_msg);
    if (!dex_files_map.IsValid()) {
      LOG(ERROR) << "Failed to mmap() dex files from oat file. File: " << file->GetPath()
                 << " error: " << error_msg;
      return false;
    }
    vdex_begin_ = dex_files_map.Begin();

    // Write dex files.
    for (OatDexFile& oat_dex_file : oat_dex_files_) {
      // Dex files are required to be 4 byte aligned.
      size_t old_vdex_size = vdex_size_;
      vdex_size_ = RoundUp(vdex_size_, 4u);
      size_dex_file_alignment_ += vdex_size_ - old_vdex_size;
      // Write the actual dex file.
      DCHECK_EQ(vdex_size_, oat_dex_file.dex_file_offset_);
      uint8_t* out = vdex_begin_ + oat_dex_file.dex_file_offset_;
      const std::vector<uint8_t>& cdex_data = oat_dex_file.cdex_main_section_;
      if (!cdex_data.empty()) {
        CHECK(!use_existing_vdex);
        // Use the compact dex version instead of the original dex file.
        DCHECK_EQ(oat_dex_file.dex_file_size_, cdex_data.size());
        memcpy(out, cdex_data.data(), cdex_data.size());
      } else {
        const DexFile* dex_file = oat_dex_file.GetDexFile();
        DCHECK_EQ(oat_dex_file.dex_file_size_, dex_file->Size());
        if (use_existing_vdex) {
          // The vdex already contains the data.
          DCHECK_EQ(memcmp(out, dex_file->Begin(), dex_file->Size()), 0);
        } else {
          memcpy(out, dex_file->Begin(), dex_file->Size());
        }
      }

      // Update current size and account for the written data.
      vdex_size_ += oat_dex_file.dex_file_size_;
      size_dex_file_ += oat_dex_file.dex_file_size_;
    }

    // Write shared dex file data section and fix up the dex file headers.
    if (shared_data_size != 0u) {
      DCHECK_EQ(RoundUp(vdex_size_, 4u), vdex_dex_shared_data_offset_);
      if (!use_existing_vdex) {
        memset(vdex_begin_ + vdex_size_, 0, vdex_dex_shared_data_offset_ - vdex_size_);
      }
      size_dex_file_alignment_ += vdex_dex_shared_data_offset_ - vdex_size_;
      vdex_size_ = vdex_dex_shared_data_offset_;

      if (dex_container_ != nullptr) {
        CHECK(!use_existing_vdex) << "Use existing vdex should have empty dex container";
        CHECK(compact_dex_level_ != CompactDexLevel::kCompactDexLevelNone);
        DexContainer::Section* const section = dex_container_->GetDataSection();
        DCHECK_EQ(shared_data_size, section->Size());
        memcpy(vdex_begin_ + vdex_size_, section->Begin(), shared_data_size);
        section->Clear();
        dex_container_.reset();
      } else if (!use_existing_vdex) {
        memcpy(vdex_begin_ + vdex_size_, raw_dex_file_shared_data_begin, shared_data_size);
      }
      vdex_size_ += shared_data_size;
      size_dex_file_ += shared_data_size;
      if (!use_existing_vdex) {
        // Fix up the dex headers to have correct offsets to the data section.
        for (OatDexFile& oat_dex_file : oat_dex_files_) {
          DexFile::Header* header =
              reinterpret_cast<DexFile::Header*>(vdex_begin_ + oat_dex_file.dex_file_offset_);
          if (!CompactDexFile::IsMagicValid(header->magic_)) {
            // Non-compact dex file, probably failed to convert due to duplicate methods.
            continue;
          }
          CHECK_GT(vdex_dex_shared_data_offset_, oat_dex_file.dex_file_offset_);
          // Offset is from the dex file base.
          header->data_off_ = vdex_dex_shared_data_offset_ - oat_dex_file.dex_file_offset_;
          // The size should already be what part of the data buffer may be used by the dex.
          CHECK_LE(header->data_size_, shared_data_size);
        }
      }
    }
    opened_dex_files_map->push_back(std::move(dex_files_map));
  } else {
    vdex_dex_shared_data_offset_ = vdex_size_;
  }

  if (use_existing_vdex) {
    // If we re-use an existing vdex, artificially set the verifier deps size,
    // so the compiler has a correct computation of the vdex size.
    size_t actual_size = file->GetLength();
    size_verifier_deps_ = actual_size - vdex_size_;
    vdex_size_ = actual_size;
  }

  return true;
}

void OatWriter::CloseSources() {
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    oat_dex_file.dex_file_.reset();
  }
}

bool OatWriter::LayoutDexFile(OatDexFile* oat_dex_file) {
  TimingLogger::ScopedTiming split("Dex Layout", timings_);
  std::string error_msg;
  std::string location(oat_dex_file->GetLocation());
  std::unique_ptr<const DexFile>& dex_file = oat_dex_file->dex_file_;
  Options options;
  options.compact_dex_level_ = compact_dex_level_;
  options.update_checksum_ = true;
  DexLayout dex_layout(options, profile_compilation_info_, /*file*/ nullptr, /*header*/ nullptr);
  {
    TimingLogger::ScopedTiming extract("ProcessDexFile", timings_);
    if (dex_layout.ProcessDexFile(location.c_str(),
                                  dex_file.get(),
                                  0,
                                  &dex_container_,
                                  &error_msg)) {
      oat_dex_file->dex_sections_layout_ = dex_layout.GetSections();
      oat_dex_file->cdex_main_section_ = dex_container_->GetMainSection()->ReleaseData();
      // Dex layout can affect the size of the dex file, so we update here what we have set
      // when adding the dex file as a source.
      const UnalignedDexFileHeader* header =
          AsUnalignedDexFileHeader(oat_dex_file->cdex_main_section_.data());
      oat_dex_file->dex_file_size_ = header->file_size_;
    } else {
      LOG(WARNING) << "Failed to run dex layout, reason:" << error_msg;
      // Since we failed to convert the dex, just copy the input dex.
      if (dex_container_ != nullptr) {
        // Clear the main section before processing next dex file in case we have written some data.
        dex_container_->GetMainSection()->Clear();
      }
    }
  }
  CHECK_EQ(oat_dex_file->dex_file_location_checksum_, dex_file->GetLocationChecksum());
  CHECK(oat_dex_file->dex_file_sha1_ == dex_file->GetSha1());
  return true;
}

bool OatWriter::OpenDexFiles(
    File* file,
    /*inout*/ std::vector<MemMap>* opened_dex_files_map,
    /*out*/ std::vector<std::unique_ptr<const DexFile>>* opened_dex_files) {
  TimingLogger::ScopedTiming split("OpenDexFiles", timings_);

  if (oat_dex_files_.empty()) {
    // Nothing to do.
    return true;
  }

  if (!extract_dex_files_into_vdex_) {
    DCHECK_EQ(opened_dex_files_map->size(), 0u);
    std::vector<std::unique_ptr<const DexFile>> dex_files;
    for (OatDexFile& oat_dex_file : oat_dex_files_) {
      // The dex file is already open, release the reference.
      dex_files.emplace_back(std::move(oat_dex_file.dex_file_));
      oat_dex_file.class_offsets_.resize(dex_files.back()->GetHeader().class_defs_size_);
    }
    *opened_dex_files = std::move(dex_files);
    CloseSources();
    return true;
  }
  // We could have closed the sources at the point of writing the dex files, but to
  // make it consistent with the case we're not writing the dex files, we close them now.
  CloseSources();

  DCHECK_EQ(opened_dex_files_map->size(), 1u);
  DCHECK(vdex_begin_ == opened_dex_files_map->front().Begin());
  std::vector<std::unique_ptr<const DexFile>> dex_files;
  auto dex_container = std::make_shared<MemoryDexFileContainer>(vdex_begin_, vdex_size_);
  for (OatDexFile& oat_dex_file : oat_dex_files_) {
    const uint8_t* raw_dex_file = vdex_begin_ + oat_dex_file.dex_file_offset_;

    if (kIsDebugBuild) {
      // Check the validity of the input files.
      // Note that ValidateDexFileHeader() logs error messages.
      CHECK(ValidateDexFileHeader(raw_dex_file, oat_dex_file.GetLocation()))
          << "Failed to verify written dex file header!"
          << " Output: " << file->GetPath()
          << " ~ " << std::hex << static_cast<const void*>(raw_dex_file);

      const UnalignedDexFileHeader* header = AsUnalignedDexFileHeader(raw_dex_file);
      CHECK_EQ(header->file_size_, oat_dex_file.dex_file_size_)
          << "File size mismatch in written dex file header! Expected: "
          << oat_dex_file.dex_file_size_ << " Actual: " << header->file_size_
          << " Output: " << file->GetPath();
    }

    // Now, open the dex file.
    std::string error_msg;
    ArtDexFileLoader dex_file_loader(dex_container, oat_dex_file.GetLocation());
    // All dex files have been already verified in WriteDexFiles before we copied them.
    dex_files.emplace_back(dex_file_loader.OpenOne(oat_dex_file.dex_file_offset_,
                                                   oat_dex_file.dex_file_location_checksum_,
                                                   /*oat_dex_file=*/nullptr,
                                                   /*verify=*/false,
                                                   /*verify_checksum=*/false,
                                                   &error_msg));
    if (dex_files.back() == nullptr) {
      LOG(ERROR) << "Failed to open dex file from oat file. File: " << oat_dex_file.GetLocation()
                 << " Error: " << error_msg;
      return false;
    }

    // Set the class_offsets size now that we have easy access to the DexFile and
    // it has been verified in dex_file_loader.Open.
    oat_dex_file.class_offsets_.resize(dex_files.back()->GetHeader().class_defs_size_);
  }

  *opened_dex_files = std::move(dex_files);
  return true;
}

void OatWriter::InitializeTypeLookupTables(
    const std::vector<std::unique_ptr<const DexFile>>& opened_dex_files) {
  TimingLogger::ScopedTiming split("InitializeTypeLookupTables", timings_);
  DCHECK_EQ(opened_dex_files.size(), oat_dex_files_.size());
  for (size_t i = 0, size = opened_dex_files.size(); i != size; ++i) {
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    DCHECK_EQ(oat_dex_file->lookup_table_offset_, 0u);

    size_t table_size = TypeLookupTable::RawDataLength(oat_dex_file->class_offsets_.size());
    if (table_size == 0u) {
      // We want a 1:1 mapping between `dex_files_` and `type_lookup_table_oat_dex_files_`,
      // to simplify `WriteTypeLookupTables`. We push a null entry to notify
      // that the dex file at index `i` does not have a type lookup table.
      type_lookup_table_oat_dex_files_.push_back(nullptr);
      continue;
    }

    const DexFile& dex_file = *opened_dex_files[i].get();
    TypeLookupTable type_lookup_table = TypeLookupTable::Create(dex_file);
    type_lookup_table_oat_dex_files_.push_back(
        std::make_unique<art::OatDexFile>(std::move(type_lookup_table)));
    dex_file.SetOatDexFile(type_lookup_table_oat_dex_files_.back().get());
  }
}

bool OatWriter::WriteDexLayoutSections(OutputStream* oat_rodata,
                                       const std::vector<const DexFile*>& opened_dex_files) {
  TimingLogger::ScopedTiming split(__FUNCTION__, timings_);

  if (!kWriteDexLayoutInfo) {
    return true;
  }

  uint32_t expected_offset = oat_data_offset_ + oat_size_;
  off_t actual_offset = oat_rodata->Seek(expected_offset, kSeekSet);
  if (static_cast<uint32_t>(actual_offset) != expected_offset) {
    PLOG(ERROR) << "Failed to seek to dex layout section offset section. Actual: " << actual_offset
                << " Expected: " << expected_offset << " File: " << oat_rodata->GetLocation();
    return false;
  }

  DCHECK_EQ(opened_dex_files.size(), oat_dex_files_.size());
  size_t rodata_offset = oat_size_;
  for (size_t i = 0, size = opened_dex_files.size(); i != size; ++i) {
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    DCHECK_EQ(oat_dex_file->dex_sections_layout_offset_, 0u);

    // Write dex layout section alignment bytes.
    const size_t padding_size =
        RoundUp(rodata_offset, alignof(DexLayoutSections)) - rodata_offset;
    if (padding_size != 0u) {
      std::vector<uint8_t> buffer(padding_size, 0u);
      if (!oat_rodata->WriteFully(buffer.data(), padding_size)) {
        PLOG(ERROR) << "Failed to write lookup table alignment padding."
                    << " File: " << oat_dex_file->GetLocation()
                    << " Output: " << oat_rodata->GetLocation();
        return false;
      }
      size_oat_dex_file_dex_layout_sections_alignment_ += padding_size;
      rodata_offset += padding_size;
    }

    DCHECK_ALIGNED(rodata_offset, alignof(DexLayoutSections));
    DCHECK_EQ(oat_data_offset_ + rodata_offset,
              static_cast<size_t>(oat_rodata->Seek(0u, kSeekCurrent)));
    DCHECK(oat_dex_file != nullptr);
    if (!oat_rodata->WriteFully(&oat_dex_file->dex_sections_layout_,
                                sizeof(oat_dex_file->dex_sections_layout_))) {
      PLOG(ERROR) << "Failed to write dex layout sections."
                  << " File: " << oat_dex_file->GetLocation()
                  << " Output: " << oat_rodata->GetLocation();
      return false;
    }
    oat_dex_file->dex_sections_layout_offset_ = rodata_offset;
    size_oat_dex_file_dex_layout_sections_ += sizeof(oat_dex_file->dex_sections_layout_);
    rodata_offset += sizeof(oat_dex_file->dex_sections_layout_);
  }
  oat_size_ = rodata_offset;

  if (!oat_rodata->Flush()) {
    PLOG(ERROR) << "Failed to flush stream after writing type dex layout sections."
                << " File: " << oat_rodata->GetLocation();
    return false;
  }

  return true;
}

void OatWriter::WriteTypeLookupTables(/*out*/std::vector<uint8_t>* buffer) {
  TimingLogger::ScopedTiming split("WriteTypeLookupTables", timings_);
  size_t type_lookup_table_size = 0u;
  for (const DexFile* dex_file : *dex_files_) {
    type_lookup_table_size +=
        sizeof(uint32_t) + TypeLookupTable::RawDataLength(dex_file->NumClassDefs());
  }
  // Reserve the space to avoid reallocations later on.
  buffer->reserve(buffer->size() + type_lookup_table_size);

  // Align the start of the first type lookup table.
  size_t initial_offset = vdex_size_;
  size_t table_offset = RoundUp(initial_offset, 4);
  size_t padding_size = table_offset - initial_offset;

  size_vdex_lookup_table_alignment_ += padding_size;
  for (uint32_t j = 0; j < padding_size; ++j) {
    buffer->push_back(0);
  }
  vdex_size_ += padding_size;
  vdex_lookup_tables_offset_ = vdex_size_;
  for (size_t i = 0, size = type_lookup_table_oat_dex_files_.size(); i != size; ++i) {
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    if (type_lookup_table_oat_dex_files_[i] == nullptr) {
      buffer->insert(buffer->end(), {0u, 0u, 0u, 0u});
      size_vdex_lookup_table_ += sizeof(uint32_t);
      vdex_size_ += sizeof(uint32_t);
      oat_dex_file->lookup_table_offset_ = 0u;
    } else {
      oat_dex_file->lookup_table_offset_ = vdex_size_ + sizeof(uint32_t);
      const TypeLookupTable& table = type_lookup_table_oat_dex_files_[i]->GetTypeLookupTable();
      uint32_t table_size = table.RawDataLength();
      DCHECK_NE(0u, table_size);
      DCHECK_ALIGNED(table_size, 4);
      size_t old_buffer_size = buffer->size();
      buffer->resize(old_buffer_size + table.RawDataLength() + sizeof(uint32_t), 0u);
      memcpy(buffer->data() + old_buffer_size, &table_size, sizeof(uint32_t));
      memcpy(buffer->data() + old_buffer_size + sizeof(uint32_t), table.RawData(), table_size);
      vdex_size_ += table_size + sizeof(uint32_t);
      size_vdex_lookup_table_ += table_size + sizeof(uint32_t);
    }
  }
}

bool OatWriter::FinishVdexFile(File* vdex_file, verifier::VerifierDeps* verifier_deps) {
  size_t old_vdex_size = vdex_size_;
  std::vector<uint8_t> buffer;
  buffer.reserve(64 * KB);
  WriteVerifierDeps(verifier_deps, &buffer);
  WriteTypeLookupTables(&buffer);
  DCHECK_EQ(vdex_size_, old_vdex_size + buffer.size());

  // Resize the vdex file.
  if (vdex_file->SetLength(vdex_size_) != 0) {
    PLOG(ERROR) << "Failed to resize vdex file " << vdex_file->GetPath();
    return false;
  }

  uint8_t* vdex_begin = vdex_begin_;
  MemMap extra_map;
  if (extract_dex_files_into_vdex_) {
    DCHECK(vdex_begin != nullptr);
    // Write data to the last already mmapped page of the vdex file.
    // The size should match the page_aligned_size in the OatWriter::WriteDexFiles.
    size_t mmapped_vdex_size = RoundUp(old_vdex_size, MemMap::GetPageSize());
    size_t first_chunk_size = std::min(buffer.size(), mmapped_vdex_size - old_vdex_size);
    memcpy(vdex_begin + old_vdex_size, buffer.data(), first_chunk_size);

    if (first_chunk_size != buffer.size()) {
      size_t tail_size = buffer.size() - first_chunk_size;
      std::string error_msg;
      extra_map = MemMap::MapFile(
          tail_size,
          PROT_READ | PROT_WRITE,
          MAP_SHARED,
          vdex_file->Fd(),
          /*start=*/ mmapped_vdex_size,
          /*low_4gb=*/ false,
          vdex_file->GetPath().c_str(),
          &error_msg);
      if (!extra_map.IsValid()) {
        LOG(ERROR) << "Failed to mmap() vdex file tail. File: " << vdex_file->GetPath()
                   << " error: " << error_msg;
        return false;
      }
      memcpy(extra_map.Begin(), buffer.data() + first_chunk_size, tail_size);
    }
  } else {
    DCHECK(vdex_begin == nullptr);
    std::string error_msg;
    extra_map = MemMap::MapFile(
        vdex_size_,
        PROT_READ | PROT_WRITE,
        MAP_SHARED,
        vdex_file->Fd(),
        /*start=*/ 0u,
        /*low_4gb=*/ false,
        vdex_file->GetPath().c_str(),
        &error_msg);
    if (!extra_map.IsValid()) {
      LOG(ERROR) << "Failed to mmap() vdex file. File: " << vdex_file->GetPath()
                 << " error: " << error_msg;
      return false;
    }
    vdex_begin = extra_map.Begin();
    memcpy(vdex_begin + old_vdex_size, buffer.data(), buffer.size());
  }

  // Write checksums
  off_t checksums_offset = VdexFile::GetChecksumsOffset();
  VdexFile::VdexChecksum* checksums_data =
      reinterpret_cast<VdexFile::VdexChecksum*>(vdex_begin + checksums_offset);
  for (size_t i = 0, size = oat_dex_files_.size(); i != size; ++i) {
    OatDexFile* oat_dex_file = &oat_dex_files_[i];
    checksums_data[i] = oat_dex_file->dex_file_location_checksum_;
  }

  // Write sections.
  uint8_t* ptr = vdex_begin + sizeof(VdexFile::VdexFileHeader);

  // Checksums section.
  new (ptr) VdexFile::VdexSectionHeader(VdexSection::kChecksumSection,
                                        checksums_offset,
                                        size_vdex_checksums_);
  ptr += sizeof(VdexFile::VdexSectionHeader);

  // Dex section.
  new (ptr) VdexFile::VdexSectionHeader(
      VdexSection::kDexFileSection,
      extract_dex_files_into_vdex_ ? vdex_dex_files_offset_ : 0u,
      extract_dex_files_into_vdex_ ? vdex_verifier_deps_offset_ - vdex_dex_files_offset_ : 0u);
  ptr += sizeof(VdexFile::VdexSectionHeader);

  // VerifierDeps section.
  new (ptr) VdexFile::VdexSectionHeader(VdexSection::kVerifierDepsSection,
                                        vdex_verifier_deps_offset_,
                                        size_verifier_deps_);
  ptr += sizeof(VdexFile::VdexSectionHeader);

  // TypeLookupTable section.
  new (ptr) VdexFile::VdexSectionHeader(VdexSection::kTypeLookupTableSection,
                                        vdex_lookup_tables_offset_,
                                        vdex_size_ - vdex_lookup_tables_offset_);

  // All the contents (except the header) of the vdex file has been emitted in memory. Flush it
  // to disk.
  {
    TimingLogger::ScopedTiming split("VDEX flush contents", timings_);
    // Sync the data to the disk while the header is invalid. We do not want to end up with
    // a valid header and invalid data if the process is suddenly killed.
    if (extract_dex_files_into_vdex_) {
      // Note: We passed the ownership of the vdex dex file MemMap to the caller,
      // so we need to use msync() for the range explicitly.
      //
      // The page size here is not replaced with kElfSegmentAlignment as the
      // rounded up size should match the page_aligned_size in OatWriter::WriteDexFiles
      // which is the size the original (non-extra) mapping created there.
      if (msync(vdex_begin, RoundUp(old_vdex_size, MemMap::GetPageSize()), MS_SYNC) != 0) {
        PLOG(ERROR) << "Failed to sync vdex file contents" << vdex_file->GetPath();
        return false;
      }
    }
    if (extra_map.IsValid() && !extra_map.Sync()) {
      PLOG(ERROR) << "Failed to sync vdex file contents" << vdex_file->GetPath();
      return false;
    }
  }

  // Now that we know all contents have been flushed to disk, we can write
  // the header which will mke the vdex usable.
  bool has_dex_section = extract_dex_files_into_vdex_;
  new (vdex_begin) VdexFile::VdexFileHeader(has_dex_section);

  // Note: If `extract_dex_files_into_vdex_`, we passed the ownership of the vdex dex file
  // MemMap to the caller, so we need to use msync() for the range explicitly.
  //
  // The page size here should not be replaced with kElfSegmentAlignment as the size
  // here should match the header size rounded up to the page size. Any higher value
  // might happen to be larger than the size of the mapping which can in some circumstances
  // cause msync to fail.
  if (msync(vdex_begin, MemMap::GetPageSize(), MS_SYNC) != 0) {
    PLOG(ERROR) << "Failed to sync vdex file header " << vdex_file->GetPath();
    return false;
  }

  return true;
}

bool OatWriter::WriteCodeAlignment(OutputStream* out, uint32_t aligned_code_delta) {
  return WriteUpTo16BytesAlignment(out, aligned_code_delta, &size_code_alignment_);
}

bool OatWriter::WriteUpTo16BytesAlignment(OutputStream* out, uint32_t size, uint32_t* stat) {
  static const uint8_t kPadding[] = {
      0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u
  };
  DCHECK_LE(size, sizeof(kPadding));
  if (UNLIKELY(!out->WriteFully(kPadding, size))) {
    return false;
  }
  *stat += size;
  return true;
}

void OatWriter::SetMultiOatRelativePatcherAdjustment() {
  DCHECK(dex_files_ != nullptr);
  DCHECK(relative_patcher_ != nullptr);
  DCHECK_NE(oat_data_offset_, 0u);
  if (image_writer_ != nullptr && !dex_files_->empty()) {
    // The oat data begin may not be initialized yet but the oat file offset is ready.
    size_t oat_index = image_writer_->GetOatIndexForDexFile(dex_files_->front());
    size_t elf_file_offset = image_writer_->GetOatFileOffset(oat_index);
    relative_patcher_->StartOatFile(elf_file_offset + oat_data_offset_);
  }
}

OatWriter::OatDexFile::OatDexFile(std::unique_ptr<const DexFile> dex_file)
    : dex_file_(std::move(dex_file)),
      dex_file_location_(std::make_unique<std::string>(dex_file_->GetLocation())),
      dex_file_size_(dex_file_->Size()),
      offset_(0),
      dex_file_location_size_(strlen(dex_file_location_->c_str())),
      dex_file_location_data_(dex_file_location_->c_str()),
      dex_file_magic_(dex_file_->GetHeader().magic_),
      dex_file_location_checksum_(dex_file_->GetLocationChecksum()),
      dex_file_sha1_(dex_file_->GetSha1()),
      dex_file_offset_(0u),
      lookup_table_offset_(0u),
      class_offsets_offset_(0u),
      method_bss_mapping_offset_(0u),
      type_bss_mapping_offset_(0u),
      public_type_bss_mapping_offset_(0u),
      package_type_bss_mapping_offset_(0u),
      string_bss_mapping_offset_(0u),
      method_type_bss_mapping_offset_(0u),
      dex_sections_layout_offset_(0u),
      class_offsets_() {}

size_t OatWriter::OatDexFile::SizeOf() const {
  return sizeof(dex_file_location_size_) + dex_file_location_size_ + sizeof(dex_file_magic_) +
         sizeof(dex_file_location_checksum_) + sizeof(dex_file_sha1_) + sizeof(dex_file_offset_) +
         sizeof(class_offsets_offset_) + sizeof(lookup_table_offset_) +
         sizeof(method_bss_mapping_offset_) + sizeof(type_bss_mapping_offset_) +
         sizeof(public_type_bss_mapping_offset_) + sizeof(package_type_bss_mapping_offset_) +
         sizeof(string_bss_mapping_offset_) + sizeof(method_type_bss_mapping_offset_) +
         sizeof(dex_sections_layout_offset_);
}

bool OatWriter::OatDexFile::Write(OatWriter* oat_writer, OutputStream* out) const {
  const size_t file_offset = oat_writer->oat_data_offset_;
  DCHECK_OFFSET_();

  if (!out->WriteFully(&dex_file_location_size_, sizeof(dex_file_location_size_))) {
    PLOG(ERROR) << "Failed to write dex file location length to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_location_size_ += sizeof(dex_file_location_size_);

  if (!out->WriteFully(dex_file_location_data_, dex_file_location_size_)) {
    PLOG(ERROR) << "Failed to write dex file location data to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_location_data_ += dex_file_location_size_;

  if (!out->WriteFully(&dex_file_magic_, sizeof(dex_file_magic_))) {
    PLOG(ERROR) << "Failed to write dex file magic to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_magic_ += sizeof(dex_file_magic_);

  if (!out->WriteFully(&dex_file_location_checksum_, sizeof(dex_file_location_checksum_))) {
    PLOG(ERROR) << "Failed to write dex file location checksum to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_location_checksum_ += sizeof(dex_file_location_checksum_);

  if (!out->WriteFully(&dex_file_sha1_, sizeof(dex_file_sha1_))) {
    PLOG(ERROR) << "Failed to write dex file sha1 to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_sha1_ += sizeof(dex_file_sha1_);

  if (!out->WriteFully(&dex_file_offset_, sizeof(dex_file_offset_))) {
    PLOG(ERROR) << "Failed to write dex file offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_offset_ += sizeof(dex_file_offset_);

  if (!out->WriteFully(&class_offsets_offset_, sizeof(class_offsets_offset_))) {
    PLOG(ERROR) << "Failed to write class offsets offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_class_offsets_offset_ += sizeof(class_offsets_offset_);

  if (!out->WriteFully(&lookup_table_offset_, sizeof(lookup_table_offset_))) {
    PLOG(ERROR) << "Failed to write lookup table offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_lookup_table_offset_ += sizeof(lookup_table_offset_);

  if (!out->WriteFully(&dex_sections_layout_offset_, sizeof(dex_sections_layout_offset_))) {
    PLOG(ERROR) << "Failed to write dex section layout info to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_dex_layout_sections_offset_ += sizeof(dex_sections_layout_offset_);

  if (!out->WriteFully(&method_bss_mapping_offset_, sizeof(method_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write method bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_method_bss_mapping_offset_ += sizeof(method_bss_mapping_offset_);

  if (!out->WriteFully(&type_bss_mapping_offset_, sizeof(type_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_type_bss_mapping_offset_ += sizeof(type_bss_mapping_offset_);

  if (!out->WriteFully(&public_type_bss_mapping_offset_, sizeof(public_type_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write public type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_public_type_bss_mapping_offset_ +=
      sizeof(public_type_bss_mapping_offset_);

  if (!out->WriteFully(&package_type_bss_mapping_offset_,
                       sizeof(package_type_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write package type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_package_type_bss_mapping_offset_ +=
      sizeof(package_type_bss_mapping_offset_);

  if (!out->WriteFully(&string_bss_mapping_offset_, sizeof(string_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write string bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_string_bss_mapping_offset_ += sizeof(string_bss_mapping_offset_);

  if (!out->WriteFully(&method_type_bss_mapping_offset_, sizeof(method_type_bss_mapping_offset_))) {
    PLOG(ERROR) << "Failed to write MethodType bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_dex_file_method_type_bss_mapping_offset_ +=
      sizeof(method_type_bss_mapping_offset_);

  return true;
}

bool OatWriter::BssMappingInfo::Write(OatWriter* oat_writer, OutputStream* out) const {
  const size_t file_offset = oat_writer->oat_data_offset_;
  DCHECK_OFFSET_();

  if (!out->WriteFully(&method_bss_mapping_offset, sizeof(method_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write method bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_method_bss_mapping_offset_ += sizeof(method_bss_mapping_offset);

  if (!out->WriteFully(&type_bss_mapping_offset, sizeof(type_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_type_bss_mapping_offset_ += sizeof(type_bss_mapping_offset);

  if (!out->WriteFully(&public_type_bss_mapping_offset, sizeof(public_type_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write public type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_public_type_bss_mapping_offset_ +=
      sizeof(public_type_bss_mapping_offset);

  if (!out->WriteFully(&package_type_bss_mapping_offset, sizeof(package_type_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write package type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_package_type_bss_mapping_offset_ +=
      sizeof(package_type_bss_mapping_offset);

  if (!out->WriteFully(&string_bss_mapping_offset, sizeof(string_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write string bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_string_bss_mapping_offset_ += sizeof(string_bss_mapping_offset);

  if (!out->WriteFully(&method_type_bss_mapping_offset, sizeof(method_type_bss_mapping_offset))) {
    PLOG(ERROR) << "Failed to write method type bss mapping offset to " << out->GetLocation();
    return false;
  }
  oat_writer->size_bcp_bss_info_method_type_bss_mapping_offset_ +=
      sizeof(method_type_bss_mapping_offset);

  return true;
}

bool OatWriter::OatDexFile::WriteClassOffsets(OatWriter* oat_writer, OutputStream* out) {
  if (!out->WriteFully(class_offsets_.data(), GetClassOffsetsRawSize())) {
    PLOG(ERROR) << "Failed to write oat class offsets for " << GetLocation()
                << " to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_class_offsets_ += GetClassOffsetsRawSize();
  return true;
}

OatWriter::OatClass::OatClass(const dchecked_vector<CompiledMethod*>& compiled_methods,
                              uint32_t compiled_methods_with_code,
                              uint16_t oat_class_type)
    : compiled_methods_(compiled_methods) {
  const uint32_t num_methods = compiled_methods.size();
  CHECK_LE(compiled_methods_with_code, num_methods);

  oat_method_offsets_offsets_from_oat_class_.resize(num_methods);

  method_offsets_.resize(compiled_methods_with_code);
  method_headers_.resize(compiled_methods_with_code);

  uint32_t oat_method_offsets_offset_from_oat_class = OatClassHeader::SizeOf();
  // We only write method-related data if there are at least some compiled methods.
  num_methods_ = 0u;
  DCHECK(method_bitmap_ == nullptr);
  if (oat_class_type != enum_cast<uint16_t>(OatClassType::kNoneCompiled)) {
    num_methods_ = num_methods;
    oat_method_offsets_offset_from_oat_class += sizeof(num_methods_);
    if (oat_class_type == enum_cast<uint16_t>(OatClassType::kSomeCompiled)) {
      method_bitmap_.reset(new BitVector(num_methods, false, Allocator::GetMallocAllocator()));
      uint32_t bitmap_size = BitVector::BitsToWords(num_methods) * BitVector::kWordBytes;
      DCHECK_EQ(bitmap_size, method_bitmap_->GetSizeOf());
      oat_method_offsets_offset_from_oat_class += bitmap_size;
    }
  }

  for (size_t i = 0; i < num_methods; i++) {
    CompiledMethod* compiled_method = compiled_methods_[i];
    if (HasCompiledCode(compiled_method)) {
      oat_method_offsets_offsets_from_oat_class_[i] = oat_method_offsets_offset_from_oat_class;
      oat_method_offsets_offset_from_oat_class += sizeof(OatMethodOffsets);
      if (oat_class_type == enum_cast<uint16_t>(OatClassType::kSomeCompiled)) {
        method_bitmap_->SetBit(i);
      }
    } else {
      oat_method_offsets_offsets_from_oat_class_[i] = 0;
    }
  }
}

size_t OatWriter::OatClass::SizeOf() const {
  return ((num_methods_ == 0) ? 0 : sizeof(num_methods_)) +
         ((method_bitmap_ != nullptr) ? method_bitmap_->GetSizeOf() : 0u) +
         (sizeof(method_offsets_[0]) * method_offsets_.size());
}

bool OatWriter::OatClassHeader::Write(OatWriter* oat_writer,
                                      OutputStream* out,
                                      const size_t file_offset) const {
  DCHECK_OFFSET_();
  if (!out->WriteFully(&status_, sizeof(status_))) {
    PLOG(ERROR) << "Failed to write class status to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_class_status_ += sizeof(status_);

  if (!out->WriteFully(&type_, sizeof(type_))) {
    PLOG(ERROR) << "Failed to write oat class type to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_class_type_ += sizeof(type_);
  return true;
}

bool OatWriter::OatClass::Write(OatWriter* oat_writer, OutputStream* out) const {
  if (num_methods_ != 0u) {
    if (!out->WriteFully(&num_methods_, sizeof(num_methods_))) {
      PLOG(ERROR) << "Failed to write number of methods to " << out->GetLocation();
      return false;
    }
    oat_writer->size_oat_class_num_methods_ += sizeof(num_methods_);
  }

  if (method_bitmap_ != nullptr) {
    if (!out->WriteFully(method_bitmap_->GetRawStorage(), method_bitmap_->GetSizeOf())) {
      PLOG(ERROR) << "Failed to write method bitmap to " << out->GetLocation();
      return false;
    }
    oat_writer->size_oat_class_method_bitmaps_ += method_bitmap_->GetSizeOf();
  }

  if (!out->WriteFully(method_offsets_.data(), GetMethodOffsetsRawSize())) {
    PLOG(ERROR) << "Failed to write method offsets to " << out->GetLocation();
    return false;
  }
  oat_writer->size_oat_class_method_offsets_ += GetMethodOffsetsRawSize();
  return true;
}

debug::DebugInfo OatWriter::GetDebugInfo() const {
  debug::DebugInfo debug_info{};
  debug_info.compiled_methods = ArrayRef<const debug::MethodDebugInfo>(method_info_);
  if (VdexWillContainDexFiles()) {
    DCHECK_EQ(dex_files_->size(), oat_dex_files_.size());
    for (size_t i = 0, size = dex_files_->size(); i != size; ++i) {
      const DexFile* dex_file = (*dex_files_)[i];
      const OatDexFile& oat_dex_file = oat_dex_files_[i];
      uint32_t dex_file_offset = oat_dex_file.dex_file_offset_;
      if (dex_file_offset != 0) {
        debug_info.dex_files.emplace(dex_file_offset, dex_file);
      }
    }
  }
  return debug_info;
}

}  // namespace linker
}  // namespace art
