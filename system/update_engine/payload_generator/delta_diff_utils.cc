//
// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "update_engine/payload_generator/delta_diff_utils.h"

#include <endian.h>
#include <sys/user.h>
#if defined(__clang__)
// TODO(*): Remove these pragmas when b/35721782 is fixed.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wmacro-redefined"
#endif
#include <ext2fs/ext2fs.h>
#if defined(__clang__)
#pragma clang diagnostic pop
#endif
#include <unistd.h>

#include <algorithm>
#include <functional>
#include <limits>
#include <list>
#include <map>
#include <memory>
#include <numeric>
#include <utility>
#include <vector>

#include <base/files/file_util.h>
#include <base/format_macros.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>
#include <base/threading/simple_thread.h>
#include <brillo/data_encoding.h>
#include <bsdiff/bsdiff.h>
#include <bsdiff/constants.h>
#include <bsdiff/control_entry.h>
#include <bsdiff/patch_reader.h>
#include <bsdiff/patch_writer_factory.h>
#include <puffin/brotli_util.h>
#include <puffin/utils.h>
#include <zucchini/buffer_view.h>
#include <zucchini/patch_writer.h>
#include <zucchini/zucchini.h>

#include "update_engine/common/hash_calculator.h"
#include "update_engine/common/utils.h"
#include "update_engine/lz4diff/lz4diff.h"
#include "update_engine/payload_consumer/payload_constants.h"
#include "update_engine/payload_generator/ab_generator.h"
#include "update_engine/payload_generator/block_mapping.h"
#include "update_engine/payload_generator/bzip.h"
#include "update_engine/payload_generator/deflate_utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_generator/xz.h"

using std::list;
using std::map;
using std::string;
using std::vector;

namespace chromeos_update_engine {
namespace {

// The maximum destination size allowed for bsdiff. In general, bsdiff should
// work for arbitrary big files, but the payload generation and payload
// application requires a significant amount of RAM. We put a hard-limit of
// 200 MiB that should not affect any released board, but will limit the
// Chrome binary in ASan builders.
const uint64_t kMaxBsdiffDestinationSize = 200 * 1024 * 1024;  // bytes

// The maximum destination size allowed for puffdiff. In general, puffdiff
// should work for arbitrary big files, but the payload application is quite
// memory intensive, so we limit these operations to 150 MiB.
const uint64_t kMaxPuffdiffDestinationSize = 150 * 1024 * 1024;  // bytes

// The maximum destination size allowed for zucchini. We are conservative here
// as zucchini tends to use more peak memory.
const uint64_t kMaxZucchiniDestinationSize = 150 * 1024 * 1024;  // bytes

const int kBrotliCompressionQuality = 11;

// Storing a diff operation has more overhead over replace operation in the
// manifest, we need to store an additional src_sha256_hash which is 32 bytes
// and not compressible, and also src_extents which could use anywhere from a
// few bytes to hundreds of bytes depending on the number of extents.
// This function evaluates the overhead tradeoff and determines if it's worth to
// use a diff operation with data blob of |diff_size| and |num_src_extents|
// extents over an existing |op| with data blob of |old_blob_size|.
bool IsDiffOperationBetter(const InstallOperation& op,
                           size_t old_blob_size,
                           size_t diff_size,
                           size_t num_src_extents) {
  if (!diff_utils::IsAReplaceOperation(op.type()))
    return diff_size < old_blob_size;

  // Reference: https://developers.google.com/protocol-buffers/docs/encoding
  // For |src_sha256_hash| we need 1 byte field number/type, 1 byte size and 32
  // bytes data, for |src_extents| we need 1 byte field number/type and 1 byte
  // size.
  constexpr size_t kDiffOverhead = 1 + 1 + 32 + 1 + 1;
  // Each extent has two variable length encoded uint64, here we use a rough
  // estimate of 6 bytes overhead per extent, since |num_blocks| is usually
  // very small.
  constexpr size_t kDiffOverheadPerExtent = 6;

  return diff_size + kDiffOverhead + num_src_extents * kDiffOverheadPerExtent <
         old_blob_size;
}

// Returns the levenshtein distance between string |a| and |b|.
// https://en.wikipedia.org/wiki/Levenshtein_distance
int LevenshteinDistance(const string& a, const string& b) {
  vector<int> distances(a.size() + 1);
  std::iota(distances.begin(), distances.end(), 0);

  for (size_t i = 1; i <= b.size(); i++) {
    distances[0] = i;
    int previous_distance = i - 1;
    for (size_t j = 1; j <= a.size(); j++) {
      int new_distance =
          std::min({distances[j] + 1,
                    distances[j - 1] + 1,
                    previous_distance + (a[j - 1] == b[i - 1] ? 0 : 1)});
      previous_distance = distances[j];
      distances[j] = new_distance;
    }
  }
  return distances.back();
}

static bool ShouldCreateNewOp(const std::vector<CowMergeOperation>& ops,
                              size_t src_block,
                              size_t dst_block,
                              size_t src_offset) {
  if (ops.empty()) {
    return true;
  }
  const auto& op = ops.back();
  if (op.src_offset() != src_offset) {
    return true;
  }
  const auto& src_extent = op.src_extent();
  const auto& dst_extent = op.dst_extent();
  return src_extent.start_block() + src_extent.num_blocks() != src_block ||
         dst_extent.start_block() + dst_extent.num_blocks() != dst_block;
}

void AppendXorBlock(std::vector<CowMergeOperation>* ops,
                    size_t src_block,
                    size_t dst_block,
                    size_t src_offset) {
  if (!ops->empty() && ExtentContains(ops->back().dst_extent(), dst_block)) {
    return;
  }
  CHECK_NE(src_block, std::numeric_limits<uint64_t>::max());
  CHECK_NE(dst_block, std::numeric_limits<uint64_t>::max());
  if (ShouldCreateNewOp(*ops, src_block, dst_block, src_offset)) {
    auto& op = ops->emplace_back();
    op.mutable_src_extent()->set_start_block(src_block);
    op.mutable_src_extent()->set_num_blocks(1);
    op.mutable_dst_extent()->set_start_block(dst_block);
    op.mutable_dst_extent()->set_num_blocks(1);
    op.set_src_offset(src_offset);
    op.set_type(CowMergeOperation::COW_XOR);
  } else {
    auto& op = ops->back();
    auto& src_extent = *op.mutable_src_extent();
    auto& dst_extent = *op.mutable_dst_extent();
    src_extent.set_num_blocks(src_extent.num_blocks() + 1);
    dst_extent.set_num_blocks(dst_extent.num_blocks() + 1);
  }
}

}  // namespace

namespace diff_utils {
bool BestDiffGenerator::GenerateBestDiffOperation(AnnotatedOperation* aop,
                                                  brillo::Blob* data_blob) {
  std::vector<std::pair<InstallOperation_Type, size_t>> diff_candidates = {
      {InstallOperation::SOURCE_BSDIFF, kMaxBsdiffDestinationSize},
      {InstallOperation::PUFFDIFF, kMaxPuffdiffDestinationSize},
      {InstallOperation::ZUCCHINI, kMaxZucchiniDestinationSize},
  };

  return GenerateBestDiffOperation(diff_candidates, aop, data_blob);
}

std::vector<bsdiff::CompressorType>
BestDiffGenerator::GetUsableCompressorTypes() const {
  return config_.compressors;
}

bool BestDiffGenerator::GenerateBestDiffOperation(
    const std::vector<std::pair<InstallOperation_Type, size_t>>&
        diff_candidates,
    AnnotatedOperation* aop,
    brillo::Blob* data_blob) {
  CHECK(aop);
  CHECK(data_blob);
  if (!old_block_info_.blocks.empty() && !new_block_info_.blocks.empty() &&
      config_.OperationEnabled(InstallOperation::LZ4DIFF_BSDIFF) &&
      config_.OperationEnabled(InstallOperation::LZ4DIFF_PUFFDIFF)) {
    brillo::Blob patch;
    InstallOperation::Type op_type{};
    if (Lz4Diff(old_data_,
                new_data_,
                old_block_info_,
                new_block_info_,
                &patch,
                &op_type)) {
      aop->op.set_type(op_type);
      // LZ4DIFF is likely significantly better than BSDIFF/PUFFDIFF when
      // working with EROFS. So no need to even try other diffing algorithms.
      *data_blob = std::move(patch);
      return true;
    }
  }

  const uint64_t input_bytes = std::max(utils::BlocksInExtents(src_extents_),
                                        utils::BlocksInExtents(dst_extents_)) *
                               kBlockSize;

  for (auto [op_type, limit] : diff_candidates) {
    if (!config_.OperationEnabled(op_type)) {
      continue;
    }

    // Disable the specific diff algorithm when the data is too big.
    if (input_bytes > limit) {
      LOG(INFO) << op_type << " ignored, file " << aop->name
                << " too big: " << input_bytes << " bytes";
      continue;
    }

    // Prefer BROTLI_BSDIFF as it gives smaller patch size.
    if (op_type == InstallOperation::SOURCE_BSDIFF &&
        config_.OperationEnabled(InstallOperation::BROTLI_BSDIFF)) {
      op_type = InstallOperation::BROTLI_BSDIFF;
    }

    switch (op_type) {
      case InstallOperation::SOURCE_BSDIFF:
      case InstallOperation::BROTLI_BSDIFF:
        TEST_AND_RETURN_FALSE(
            TryBsdiffAndUpdateOperation(op_type, aop, data_blob));
        break;
      case InstallOperation::PUFFDIFF:
        TEST_AND_RETURN_FALSE(TryPuffdiffAndUpdateOperation(aop, data_blob));
        break;
      case InstallOperation::ZUCCHINI:
        TEST_AND_RETURN_FALSE(TryZucchiniAndUpdateOperation(aop, data_blob));
        break;
      default:
        NOTREACHED();
    }
  }

  return true;
}

bool BestDiffGenerator::TryBsdiffAndUpdateOperation(
    InstallOperation_Type operation_type,
    AnnotatedOperation* aop,
    brillo::Blob* data_blob) {
  base::FilePath patch;
  TEST_AND_RETURN_FALSE(base::CreateTemporaryFile(&patch));
  ScopedPathUnlinker unlinker(patch.value());

  std::unique_ptr<bsdiff::PatchWriterInterface> bsdiff_patch_writer;
  if (operation_type == InstallOperation::BROTLI_BSDIFF) {
    bsdiff_patch_writer = bsdiff::CreateBSDF2PatchWriter(
        patch.value(), GetUsableCompressorTypes(), kBrotliCompressionQuality);
  } else {
    bsdiff_patch_writer = bsdiff::CreateBsdiffPatchWriter(patch.value());
  }

  brillo::Blob bsdiff_delta;
  TEST_AND_RETURN_FALSE(0 == bsdiff::bsdiff(old_data_.data(),
                                            old_data_.size(),
                                            new_data_.data(),
                                            new_data_.size(),
                                            bsdiff_patch_writer.get(),
                                            nullptr));

  TEST_AND_RETURN_FALSE(utils::ReadFile(patch.value(), &bsdiff_delta));
  TEST_AND_RETURN_FALSE(!bsdiff_delta.empty());

  InstallOperation& operation = aop->op;
  if (IsDiffOperationBetter(operation,
                            data_blob->size(),
                            bsdiff_delta.size(),
                            src_extents_.size())) {
    // VABC XOR won't work with compressed files just yet.
    if (config_.enable_vabc_xor) {
      StoreExtents(src_extents_, operation.mutable_src_extents());
      diff_utils::PopulateXorOps(aop, bsdiff_delta);
    }
    operation.set_type(operation_type);
    *data_blob = std::move(bsdiff_delta);
  }
  return true;
}

bool BestDiffGenerator::TryPuffdiffAndUpdateOperation(AnnotatedOperation* aop,
                                                      brillo::Blob* data_blob) {
  // Only Puffdiff if both files have at least one deflate left.
  if (!old_deflates_.empty() && !new_deflates_.empty()) {
    brillo::Blob puffdiff_delta;
    ScopedTempFile temp_file("puffdiff-delta.XXXXXX");
    // Perform PuffDiff operation.
    TEST_AND_RETURN_FALSE(puffin::PuffDiff(old_data_,
                                           new_data_,
                                           old_deflates_,
                                           new_deflates_,
                                           GetUsableCompressorTypes(),
                                           temp_file.path(),
                                           &puffdiff_delta));
    TEST_AND_RETURN_FALSE(!puffdiff_delta.empty());

    InstallOperation& operation = aop->op;
    if (IsDiffOperationBetter(operation,
                              data_blob->size(),
                              puffdiff_delta.size(),
                              src_extents_.size())) {
      operation.set_type(InstallOperation::PUFFDIFF);
      *data_blob = std::move(puffdiff_delta);
    }
  }
  return true;
}

bool BestDiffGenerator::TryZucchiniAndUpdateOperation(AnnotatedOperation* aop,
                                                      brillo::Blob* data_blob) {
  // zip files are ignored for now. We expect puffin to perform better on those.
  // Investigate whether puffin over zucchini yields better results on those.
  if (!deflate_utils::IsFileExtensions(
          aop->name,
          {".ko",
           ".so",
           ".art",
           ".odex",
           ".vdex",
           "<kernel>",
           "<modem-partition>",
           /*, ".capex",".jar", ".apk", ".apex"*/})) {
    return true;
  }
  zucchini::ConstBufferView src_bytes(old_data_.data(), old_data_.size());
  zucchini::ConstBufferView dst_bytes(new_data_.data(), new_data_.size());

  zucchini::EnsemblePatchWriter patch_writer(src_bytes, dst_bytes);
  auto status = zucchini::GenerateBuffer(src_bytes, dst_bytes, &patch_writer);
  TEST_AND_RETURN_FALSE(status == zucchini::status::kStatusSuccess);

  brillo::Blob zucchini_delta(patch_writer.SerializedSize());
  patch_writer.SerializeInto({zucchini_delta.data(), zucchini_delta.size()});

  // Compress the delta with brotli.
  // TODO(197361113) support compressing the delta with different algorithms,
  // similar to the usage in puffin.
  brillo::Blob compressed_delta;
  TEST_AND_RETURN_FALSE(puffin::BrotliEncode(
      zucchini_delta.data(), zucchini_delta.size(), &compressed_delta));

  InstallOperation& operation = aop->op;
  if (IsDiffOperationBetter(operation,
                            data_blob->size(),
                            compressed_delta.size(),
                            src_extents_.size())) {
    operation.set_type(InstallOperation::ZUCCHINI);
    *data_blob = std::move(compressed_delta);
  }

  return true;
}

// This class encapsulates a file delta processing thread work. The
// processor computes the delta between the source and target files;
// and write the compressed delta to the blob.
class FileDeltaProcessor : public base::DelegateSimpleThread::Delegate {
 public:
  FileDeltaProcessor(const string& old_part,
                     const string& new_part,
                     const PayloadGenerationConfig& config,
                     const File& old_extents,
                     const File& new_extents,
                     const string& name,
                     ssize_t chunk_blocks,
                     BlobFileWriter* blob_file)
      : old_part_(old_part),
        new_part_(new_part),
        config_(config),
        old_extents_(old_extents),
        new_extents_(new_extents),
        new_extents_blocks_(utils::BlocksInExtents(new_extents.extents)),
        name_(name),
        chunk_blocks_(chunk_blocks),
        blob_file_(blob_file) {}

  bool operator>(const FileDeltaProcessor& other) const {
    return new_extents_blocks_ > other.new_extents_blocks_;
  }

  ~FileDeltaProcessor() override = default;

  // Overrides DelegateSimpleThread::Delegate.
  // Calculate the list of operations and write their corresponding deltas to
  // the blob_file.
  void Run() override;

  // Merge each file processor's ops list to aops.
  bool MergeOperation(vector<AnnotatedOperation>* aops);

 private:
  const string& old_part_;  // NOLINT(runtime/member_string_references)
  const string& new_part_;  // NOLINT(runtime/member_string_references)
  const PayloadGenerationConfig& config_;

  // The block ranges of the old/new file within the src/tgt image
  const File old_extents_;
  const File new_extents_;
  const size_t new_extents_blocks_;
  const string name_;
  // Block limit of one aop.
  const ssize_t chunk_blocks_;
  BlobFileWriter* blob_file_;

  // The list of ops to reach the new file from the old file.
  vector<AnnotatedOperation> file_aops_;

  bool failed_ = false;

  DISALLOW_COPY_AND_ASSIGN(FileDeltaProcessor);
};

void FileDeltaProcessor::Run() {
  TEST_AND_RETURN(blob_file_ != nullptr);
  base::TimeTicks start = base::TimeTicks::Now();

  if (!DeltaReadFile(&file_aops_,
                     old_part_,
                     new_part_,
                     old_extents_,
                     new_extents_,
                     chunk_blocks_,
                     config_,
                     blob_file_)) {
    LOG(ERROR) << "Failed to generate delta for " << name_ << " ("
               << new_extents_blocks_ << " blocks)";
    failed_ = true;
    return;
  }

  if (!ABGenerator::FragmentOperations(
          config_.version, &file_aops_, new_part_, blob_file_)) {
    LOG(ERROR) << "Failed to fragment operations for " << name_;
    failed_ = true;
    return;
  }

  LOG(INFO) << "Encoded file " << name_ << " (" << new_extents_blocks_
            << " blocks) in " << (base::TimeTicks::Now() - start);
}

bool FileDeltaProcessor::MergeOperation(vector<AnnotatedOperation>* aops) {
  if (failed_)
    return false;
  std::move(file_aops_.begin(), file_aops_.end(), std::back_inserter(*aops));
  return true;
}

FilesystemInterface::File GetOldFile(
    const map<string, FilesystemInterface::File>& old_files_map,
    const string& new_file_name) {
  if (old_files_map.empty())
    return {};

  auto old_file_iter = old_files_map.find(new_file_name);
  if (old_file_iter != old_files_map.end())
    return old_file_iter->second;

  // No old file matches the new file name. Use a similar file with the
  // shortest levenshtein distance instead.
  // This works great if the file has version number in it, but even for
  // a completely new file, using a similar file can still help.
  int min_distance =
      LevenshteinDistance(new_file_name, old_files_map.begin()->first);
  const FilesystemInterface::File* old_file = &old_files_map.begin()->second;
  for (const auto& pair : old_files_map) {
    int distance = LevenshteinDistance(new_file_name, pair.first);
    if (distance < min_distance) {
      min_distance = distance;
      old_file = &pair.second;
    }
  }
  LOG(INFO) << "Using " << old_file->name << " as source for " << new_file_name;
  return *old_file;
}

std::vector<Extent> RemoveDuplicateBlocks(const std::vector<Extent>& extents) {
  ExtentRanges extent_set;
  std::vector<Extent> ret;
  for (const auto& extent : extents) {
    auto vec = FilterExtentRanges({extent}, extent_set);
    ret.insert(ret.end(),
               std::make_move_iterator(vec.begin()),
               std::make_move_iterator(vec.end()));
    extent_set.AddExtent(extent);
  }
  return ret;
}

bool DeltaReadPartition(vector<AnnotatedOperation>* aops,
                        const PartitionConfig& old_part,
                        const PartitionConfig& new_part,
                        ssize_t hard_chunk_blocks,
                        size_t soft_chunk_blocks,
                        const PayloadGenerationConfig& config,
                        BlobFileWriter* blob_file) {
  const auto& version = config.version;
  ExtentRanges old_visited_blocks;
  ExtentRanges new_visited_blocks;

  // If verity is enabled, mark those blocks as visited to skip generating
  // operations for them.
  if (version.minor >= kVerityMinorPayloadVersion &&
      !new_part.verity.IsEmpty()) {
    LOG(INFO) << "Skipping verity hash tree blocks: "
              << ExtentsToString({new_part.verity.hash_tree_extent});
    new_visited_blocks.AddExtent(new_part.verity.hash_tree_extent);
    LOG(INFO) << "Skipping verity FEC blocks: "
              << ExtentsToString({new_part.verity.fec_extent});
    new_visited_blocks.AddExtent(new_part.verity.fec_extent);
  }

  const bool puffdiff_allowed =
      config.OperationEnabled(InstallOperation::PUFFDIFF);

  TEST_AND_RETURN_FALSE(new_part.fs_interface);
  vector<FilesystemInterface::File> new_files;
  TEST_AND_RETURN_FALSE(deflate_utils::PreprocessPartitionFiles(
      new_part, &new_files, puffdiff_allowed));

  ExtentRanges old_zero_blocks;
  // Prematurely removing moved blocks will render compression info useless.
  // Even if a single block inside a 100MB file is filtered out, the entire
  // 100MB file can't be decompressed. In this case we will fallback to BSDIFF,
  // which performs much worse than LZ4diff. It's better to let LZ4DIFF perform
  // decompression, and let underlying BSDIFF to take care of moved blocks.
  // TODO(b/206729162) Implement block filtering with compression block info
  const auto no_compressed_files =
      std::all_of(new_files.begin(), new_files.end(), [](const File& a) {
        return a.compressed_file_info.blocks.empty();
      });
  if (!config.OperationEnabled(InstallOperation::LZ4DIFF_BSDIFF) ||
      no_compressed_files) {
    TEST_AND_RETURN_FALSE(DeltaMovedAndZeroBlocks(aops,
                                                  old_part.path,
                                                  new_part.path,
                                                  old_part.size / kBlockSize,
                                                  new_part.size / kBlockSize,
                                                  soft_chunk_blocks,
                                                  config,
                                                  blob_file,
                                                  &old_visited_blocks,
                                                  &new_visited_blocks,
                                                  &old_zero_blocks));
  }

  map<string, FilesystemInterface::File> old_files_map;
  if (old_part.fs_interface) {
    vector<FilesystemInterface::File> old_files;
    TEST_AND_RETURN_FALSE(deflate_utils::PreprocessPartitionFiles(
        old_part, &old_files, puffdiff_allowed));
    for (const FilesystemInterface::File& file : old_files)
      old_files_map[file.name] = file;
  }

  list<FileDeltaProcessor> file_delta_processors;

  // The processing is very straightforward here, we generate operations for
  // every file (and pseudo-file such as the metadata) in the new filesystem
  // based on the file with the same name in the old filesystem, if any.
  // Files with overlapping data blocks (like hardlinks or filesystems with tail
  // packing or compression where the blocks store more than one file) are only
  // generated once in the new image, but are also used only once from the old
  // image due to some simplifications (see below).
  for (const FilesystemInterface::File& new_file : new_files) {
    // Ignore the files in the new filesystem without blocks. Symlinks with
    // data blocks (for example, symlinks bigger than 60 bytes in ext2) are
    // handled as normal files. We also ignore blocks that were already
    // processed by a previous file.
    vector<Extent> new_file_extents =
        FilterExtentRanges(new_file.extents, new_visited_blocks);
    new_visited_blocks.AddExtents(new_file_extents);

    if (new_file_extents.empty())
      continue;

    FilesystemInterface::File old_file =
        GetOldFile(old_files_map, new_file.name);
    old_visited_blocks.AddExtents(old_file.extents);

    // TODO(b/177104308) Filtering |new_file_extents| might confuse puffdiff, as
    // we might filterout extents with deflate streams. PUFFDIFF is written with
    // that in mind, so it will try to adapt to the filtered extents.
    // Correctness is intact, but might yield larger patch sizes. From what we
    // experimented, this has little impact on OTA size. Meanwhile, XOR ops
    // depend on this. So filter out duplicate blocks from new file.
    // TODO(b/194237829) |old_file.extents| is used instead of the de-duped
    // |old_file_extents|. This is because zucchini diffing algorithm works
    // better when given the full source file.
    // Current logic:
    // 1. src extent is completely unfiltered. It may contain
    // duplicate blocks across files, within files, it may contain zero blocks,
    // etc.
    // 2. dst extent is completely filtered, no duplicate blocks or zero blocks
    // whatsoever.
    auto filtered_new_file = new_file;
    filtered_new_file.extents = RemoveDuplicateBlocks(new_file_extents);
    file_delta_processors.emplace_back(old_part.path,
                                       new_part.path,
                                       config,
                                       std::move(old_file),
                                       std::move(filtered_new_file),
                                       new_file.name,  // operation name
                                       hard_chunk_blocks,
                                       blob_file);
  }
  // Process all the blocks not included in any file. We provided all the unused
  // blocks in the old partition as available data.
  vector<Extent> new_unvisited = {
      ExtentForRange(0, new_part.size / kBlockSize)};
  new_unvisited = FilterExtentRanges(new_unvisited, new_visited_blocks);
  if (!new_unvisited.empty()) {
    vector<Extent> old_unvisited;
    if (old_part.fs_interface) {
      old_unvisited.push_back(ExtentForRange(0, old_part.size / kBlockSize));
      old_unvisited = FilterExtentRanges(old_unvisited, old_visited_blocks);
    }

    LOG(INFO) << "Scanning " << utils::BlocksInExtents(new_unvisited)
              << " unwritten blocks using chunk size of " << soft_chunk_blocks
              << " blocks.";
    // We use the soft_chunk_blocks limit for the <non-file-data> as we don't
    // really know the structure of this data and we should not expect it to
    // have redundancy between partitions.
    File old_file;
    old_file.extents = old_unvisited;
    File new_file;
    new_file.extents = RemoveDuplicateBlocks(new_unvisited);
    file_delta_processors.emplace_back(old_part.path,
                                       new_part.path,
                                       config,
                                       old_file,
                                       new_file,
                                       "<non-file-data>",  // operation name
                                       soft_chunk_blocks,
                                       blob_file);
  }

  size_t max_threads = GetMaxThreads();

  if (config.max_threads > 0) {
    max_threads = config.max_threads;
  }

  // Sort the files in descending order based on number of new blocks to make
  // sure we start the largest ones first.
  if (file_delta_processors.size() > max_threads) {
    file_delta_processors.sort(std::greater<FileDeltaProcessor>());
  }

  base::DelegateSimpleThreadPool thread_pool("incremental-update-generator",
                                             max_threads);
  thread_pool.Start();
  for (auto& processor : file_delta_processors) {
    thread_pool.AddWork(&processor);
  }
  thread_pool.JoinAll();

  for (auto& processor : file_delta_processors) {
    TEST_AND_RETURN_FALSE(processor.MergeOperation(aops));
  }

  return true;
}

bool DeltaMovedAndZeroBlocks(vector<AnnotatedOperation>* aops,
                             const string& old_part,
                             const string& new_part,
                             size_t old_num_blocks,
                             size_t new_num_blocks,
                             ssize_t chunk_blocks,
                             const PayloadGenerationConfig& config,
                             BlobFileWriter* blob_file,
                             ExtentRanges* old_visited_blocks,
                             ExtentRanges* new_visited_blocks,
                             ExtentRanges* old_zero_blocks) {
  vector<BlockMapping::BlockId> old_block_ids;
  vector<BlockMapping::BlockId> new_block_ids;
  TEST_AND_RETURN_FALSE(MapPartitionBlocks(old_part,
                                           new_part,
                                           old_num_blocks * kBlockSize,
                                           new_num_blocks * kBlockSize,
                                           kBlockSize,
                                           &old_block_ids,
                                           &new_block_ids));

  // A mapping from the block_id to the list of block numbers with that block id
  // in the old partition. This is used to lookup where in the old partition
  // is a block from the new partition.
  map<BlockMapping::BlockId, vector<uint64_t>> old_blocks_map;

  for (uint64_t block = old_num_blocks; block-- > 0;) {
    if (old_block_ids[block] != 0 && !old_visited_blocks->ContainsBlock(block))
      old_blocks_map[old_block_ids[block]].push_back(block);

    // Mark all zeroed blocks in the old image as "used" since it doesn't make
    // any sense to spend I/O to read zeros from the source partition and more
    // importantly, these could sometimes be blocks discarded in the SSD which
    // would read non-zero values.
    if (old_block_ids[block] == 0)
      old_zero_blocks->AddBlock(block);
  }
  old_visited_blocks->AddRanges(*old_zero_blocks);

  // The collection of blocks in the new partition with just zeros. This is a
  // common case for free-space that's also problematic for bsdiff, so we want
  // to optimize it using REPLACE_BZ operations. The blob for a REPLACE_BZ of
  // just zeros is so small that it doesn't make sense to spend the I/O reading
  // zeros from the old partition.
  vector<Extent> new_zeros;

  vector<Extent> old_identical_blocks;
  vector<Extent> new_identical_blocks;

  for (uint64_t block = 0; block < new_num_blocks; block++) {
    // Only produce operations for blocks that were not yet visited.
    if (new_visited_blocks->ContainsBlock(block))
      continue;
    if (new_block_ids[block] == 0) {
      AppendBlockToExtents(&new_zeros, block);
      continue;
    }

    auto old_blocks_map_it = old_blocks_map.find(new_block_ids[block]);
    // Check if the block exists in the old partition at all.
    if (old_blocks_map_it == old_blocks_map.end() ||
        old_blocks_map_it->second.empty())
      continue;

    AppendBlockToExtents(&old_identical_blocks,
                         old_blocks_map_it->second.back());
    AppendBlockToExtents(&new_identical_blocks, block);
  }

  if (chunk_blocks == -1)
    chunk_blocks = new_num_blocks;

  // Produce operations for the zero blocks split per output extent.
  size_t num_ops = aops->size();
  new_visited_blocks->AddExtents(new_zeros);
  for (const Extent& extent : new_zeros) {
    if (config.OperationEnabled(InstallOperation::ZERO)) {
      for (uint64_t offset = 0; offset < extent.num_blocks();
           offset += chunk_blocks) {
        uint64_t num_blocks =
            std::min(static_cast<uint64_t>(extent.num_blocks()) - offset,
                     static_cast<uint64_t>(chunk_blocks));
        InstallOperation operation;
        operation.set_type(InstallOperation::ZERO);
        *(operation.add_dst_extents()) =
            ExtentForRange(extent.start_block() + offset, num_blocks);
        aops->push_back({.name = "<zeros>", .op = operation});
      }
    } else {
      File old_file;
      File new_file;
      new_file.name = "<zeros>";
      new_file.extents = {extent};
      TEST_AND_RETURN_FALSE(DeltaReadFile(aops,
                                          "",
                                          new_part,
                                          old_file,  // old_extents
                                          new_file,  // new_extents
                                          chunk_blocks,
                                          config,
                                          blob_file));
    }
  }
  LOG(INFO) << "Produced " << (aops->size() - num_ops) << " operations for "
            << utils::BlocksInExtents(new_zeros) << " zeroed blocks";

  // Produce MOVE/SOURCE_COPY operations for the moved blocks.
  num_ops = aops->size();
  uint64_t used_blocks = 0;
  old_visited_blocks->AddExtents(old_identical_blocks);
  new_visited_blocks->AddExtents(new_identical_blocks);
  for (const Extent& extent : new_identical_blocks) {
    // We split the operation at the extent boundary or when bigger than
    // chunk_blocks.
    for (uint64_t op_block_offset = 0; op_block_offset < extent.num_blocks();
         op_block_offset += chunk_blocks) {
      aops->emplace_back();
      AnnotatedOperation* aop = &aops->back();
      aop->name = "<identical-blocks>";
      aop->op.set_type(InstallOperation::SOURCE_COPY);

      uint64_t chunk_num_blocks =
          std::min(static_cast<uint64_t>(extent.num_blocks()) - op_block_offset,
                   static_cast<uint64_t>(chunk_blocks));

      // The current operation represents the move/copy operation for the
      // sublist starting at |used_blocks| of length |chunk_num_blocks| where
      // the src and dst are from |old_identical_blocks| and
      // |new_identical_blocks| respectively.
      StoreExtents(
          ExtentsSublist(old_identical_blocks, used_blocks, chunk_num_blocks),
          aop->op.mutable_src_extents());

      Extent* op_dst_extent = aop->op.add_dst_extents();
      op_dst_extent->set_start_block(extent.start_block() + op_block_offset);
      op_dst_extent->set_num_blocks(chunk_num_blocks);
      CHECK(
          vector<Extent>{*op_dst_extent} ==  // NOLINT(whitespace/braces)
          ExtentsSublist(new_identical_blocks, used_blocks, chunk_num_blocks));

      used_blocks += chunk_num_blocks;
    }
  }
  LOG(INFO) << "Produced " << (aops->size() - num_ops) << " operations for "
            << used_blocks << " identical blocks moved";

  return true;
}

bool DeltaReadFile(std::vector<AnnotatedOperation>* aops,
                   const std::string& old_part,
                   const std::string& new_part,
                   const File& old_file,
                   const File& new_file,
                   ssize_t chunk_blocks,
                   const PayloadGenerationConfig& config,
                   BlobFileWriter* blob_file) {
  const auto& old_extents = old_file.extents;
  const auto& new_extents = new_file.extents;
  const auto& name = new_file.name;

  brillo::Blob data;

  uint64_t total_blocks = utils::BlocksInExtents(new_extents);
  if (chunk_blocks == 0) {
    LOG(ERROR) << "Invalid number of chunk_blocks. Cannot be 0.";
    return false;
  }

  if (chunk_blocks == -1)
    chunk_blocks = total_blocks;

  for (uint64_t block_offset = 0; block_offset < total_blocks;
       block_offset += chunk_blocks) {
    // Split the old/new file in the same chunks. Note that this could drop
    // some information from the old file used for the new chunk. If the old
    // file is smaller (or even empty when there's no old file) the chunk will
    // also be empty.
    vector<Extent> old_extents_chunk =
        ExtentsSublist(old_extents, block_offset, chunk_blocks);
    vector<Extent> new_extents_chunk =
        ExtentsSublist(new_extents, block_offset, chunk_blocks);
    NormalizeExtents(&old_extents_chunk);
    NormalizeExtents(&new_extents_chunk);

    // Now, insert into the list of operations.
    AnnotatedOperation aop;
    aop.name = new_file.name;
    TEST_AND_RETURN_FALSE(ReadExtentsToDiff(old_part,
                                            new_part,
                                            old_extents_chunk,
                                            new_extents_chunk,
                                            old_file,
                                            new_file,
                                            config,
                                            &data,
                                            &aop));

    // Check if the operation writes nothing.
    if (aop.op.dst_extents_size() == 0) {
      LOG(ERROR) << "Empty non-MOVE operation";
      return false;
    }

    if (static_cast<uint64_t>(chunk_blocks) < total_blocks) {
      aop.name = base::StringPrintf(
          "%s:%" PRIu64, name.c_str(), block_offset / chunk_blocks);
    }

    // Write the data
    TEST_AND_RETURN_FALSE(aop.SetOperationBlob(data, blob_file));
    aops->emplace_back(aop);
  }
  return true;
}

bool GenerateBestFullOperation(const brillo::Blob& new_data,
                               const PayloadVersion& version,
                               brillo::Blob* out_blob,
                               InstallOperation::Type* out_type) {
  if (new_data.empty())
    return false;

  if (version.OperationAllowed(InstallOperation::ZERO) &&
      std::all_of(
          new_data.begin(), new_data.end(), [](uint8_t x) { return x == 0; })) {
    // The read buffer is all zeros, so produce a ZERO operation. No need to
    // check other types of operations in this case.
    *out_blob = brillo::Blob();
    *out_type = InstallOperation::ZERO;
    return true;
  }

  bool out_blob_set = false;

  // Try compressing |new_data| with xz first.
  if (version.OperationAllowed(InstallOperation::REPLACE_XZ)) {
    brillo::Blob new_data_xz;
    if (XzCompress(new_data, &new_data_xz) && !new_data_xz.empty()) {
      *out_type = InstallOperation::REPLACE_XZ;
      *out_blob = std::move(new_data_xz);
      out_blob_set = true;
    }
  }

  // Try compressing it with bzip2.
  if (version.OperationAllowed(InstallOperation::REPLACE_BZ)) {
    brillo::Blob new_data_bz;
    // TODO(deymo): Implement some heuristic to determine if it is worth trying
    // to compress the blob with bzip2 if we already have a good REPLACE_XZ.
    if (BzipCompress(new_data, &new_data_bz) && !new_data_bz.empty() &&
        (!out_blob_set || out_blob->size() > new_data_bz.size())) {
      // A REPLACE_BZ is better or nothing else was set.
      *out_type = InstallOperation::REPLACE_BZ;
      *out_blob = std::move(new_data_bz);
      out_blob_set = true;
    }
  }

  // If nothing else worked or it was badly compressed we try a REPLACE.
  if (!out_blob_set || out_blob->size() >= new_data.size()) {
    *out_type = InstallOperation::REPLACE;
    // This needs to make a copy of the data in the case bzip or xz didn't
    // compress well, which is not the common case so the performance hit is
    // low.
    *out_blob = new_data;
  }
  return true;
}

// Decide which blocks are similar from bsdiff patch.
// Blocks included in out_op->xor_map will be converted to COW_XOR during OTA
// installation
bool PopulateXorOps(AnnotatedOperation* aop, const uint8_t* data, size_t size) {
  bsdiff::BsdiffPatchReader patch_reader;
  TEST_AND_RETURN_FALSE(patch_reader.Init(data, size));
  ControlEntry entry;
  size_t new_off = 0;
  int64_t old_off = 0;
  auto& xor_ops = aop->xor_ops;
  size_t total_xor_blocks = 0;
  const auto new_file_size =
      utils::BlocksInExtents(aop->op.dst_extents()) * kBlockSize;
  while (new_off < new_file_size) {
    if (!patch_reader.ParseControlEntry(&entry)) {
      LOG(ERROR)
          << "Exhausted bsdiff patch data before reaching end of new file. "
             "Current position: "
          << new_off << " new file size: " << new_file_size;
      return false;
    }
    if (old_off >= 0) {
      auto dst_off_aligned = utils::RoundUp(new_off, kBlockSize);
      const auto skip = dst_off_aligned - new_off;
      auto src_off = old_off + skip;
      const size_t chunk_size =
          entry.diff_size - std::min(skip, entry.diff_size);
      const auto xor_blocks = (chunk_size + kBlockSize / 2) / kBlockSize;
      total_xor_blocks += xor_blocks;
      // Append chunk_size/kBlockSize number of XOR blocks, subject to rounding
      // rules: if decimal part of that division is >= 0.5, round up.
      for (size_t i = 0; i < xor_blocks; i++) {
        AppendXorBlock(
            &xor_ops,
            GetNthBlock(aop->op.src_extents(), src_off / kBlockSize),
            GetNthBlock(aop->op.dst_extents(), dst_off_aligned / kBlockSize),
            src_off % kBlockSize);
        src_off += kBlockSize;
        dst_off_aligned += kBlockSize;
      }
    }

    old_off += entry.diff_size + entry.offset_increment;
    new_off += entry.diff_size + entry.extra_size;
  }

  for (auto& op : xor_ops) {
    CHECK_EQ(op.src_extent().num_blocks(), op.dst_extent().num_blocks());
    // If |src_offset| is greater than 0, then we are reading 1
    // extra block at the end of src_extent. This dependency must
    // be honored during merge sequence generation, or we can end
    // up with a corrupted device after merge.
    if (op.src_offset() > 0) {
      op.mutable_src_extent()->set_num_blocks(op.dst_extent().num_blocks() + 1);
    }
  }

  if (xor_ops.size() > 0) {
    // TODO(177104308) Filter out duplicate blocks in XOR op
    LOG(INFO) << "Added " << total_xor_blocks << " XOR blocks, "
              << total_xor_blocks * 100.0f / new_off * kBlockSize
              << "% of blocks in this InstallOp are XOR";
  }
  return true;
}

bool ReadExtentsToDiff(const string& old_part,
                       const string& new_part,
                       const vector<Extent>& src_extents,
                       const vector<Extent>& dst_extents,
                       const File& old_file,
                       const File& new_file,
                       const PayloadGenerationConfig& config,
                       brillo::Blob* out_data,
                       AnnotatedOperation* out_op) {
  const auto& version = config.version;
  AnnotatedOperation& aop = *out_op;
  InstallOperation& operation = aop.op;

  // We read blocks from old_extents and write blocks to new_extents.
  const uint64_t blocks_to_read = utils::BlocksInExtents(src_extents);
  const uint64_t blocks_to_write = utils::BlocksInExtents(dst_extents);

  // All operations have dst_extents.
  StoreExtents(dst_extents, operation.mutable_dst_extents());

  // Read in bytes from new data.
  brillo::Blob new_data;
  TEST_AND_RETURN_FALSE(utils::ReadExtents(new_part,
                                           dst_extents,
                                           &new_data,
                                           kBlockSize * blocks_to_write,
                                           kBlockSize));
  TEST_AND_RETURN_FALSE(!new_data.empty());

  // Data blob that will be written to delta file.
  brillo::Blob data_blob;

  // Try generating a full operation for the given new data, regardless of the
  // old_data.
  InstallOperation::Type op_type{};
  TEST_AND_RETURN_FALSE(
      GenerateBestFullOperation(new_data, version, &data_blob, &op_type));
  operation.set_type(op_type);

  if (blocks_to_read > 0) {
    brillo::Blob old_data;
    // Read old data.
    TEST_AND_RETURN_FALSE(utils::ReadExtents(old_part,
                                             src_extents,
                                             &old_data,
                                             kBlockSize * blocks_to_read,
                                             kBlockSize));
    if (old_data == new_data) {
      // No change in data.
      operation.set_type(InstallOperation::SOURCE_COPY);
      data_blob = brillo::Blob();
    } else if (IsDiffOperationBetter(
                   operation, data_blob.size(), 0, src_extents.size())) {
      // No point in trying diff if zero blob size diff operation is
      // still worse than replace.

      BestDiffGenerator best_diff_generator(old_data,
                                            new_data,
                                            src_extents,
                                            dst_extents,
                                            old_file,
                                            new_file,
                                            config);
      if (!best_diff_generator.GenerateBestDiffOperation(&aop, &data_blob)) {
        LOG(INFO) << "Failed to generate diff for " << new_file.name;
        return false;
      }
    }
  }

  // WARNING: We always set legacy |src_length| and |dst_length| fields for
  // BSDIFF. For SOURCE_BSDIFF we only set them for minor version 3 and
  // lower. This is needed because we used to use these two parameters in the
  // SOURCE_BSDIFF for minor version 3 and lower, but we do not need them
  // anymore in higher minor versions. This means if we stop adding these
  // parameters for those minor versions, the delta payloads will be invalid.
  if (operation.type() == InstallOperation::SOURCE_BSDIFF &&
      version.minor <= kOpSrcHashMinorPayloadVersion) {
    operation.set_src_length(blocks_to_read * kBlockSize);
    operation.set_dst_length(blocks_to_write * kBlockSize);
  }

  // Embed extents in the operation. Replace (all variants), zero and discard
  // operations should not have source extents.
  if (!IsNoSourceOperation(operation.type())) {
    if (operation.src_extents_size() == 0) {
      StoreExtents(src_extents, operation.mutable_src_extents());
    }
  } else {
    operation.clear_src_extents();
  }

  *out_data = std::move(data_blob);
  *out_op = aop;
  return true;
}

bool IsAReplaceOperation(InstallOperation::Type op_type) {
  return (op_type == InstallOperation::REPLACE ||
          op_type == InstallOperation::REPLACE_BZ ||
          op_type == InstallOperation::REPLACE_XZ);
}

bool IsNoSourceOperation(InstallOperation::Type op_type) {
  return (IsAReplaceOperation(op_type) || op_type == InstallOperation::ZERO ||
          op_type == InstallOperation::DISCARD);
}

bool InitializePartitionInfo(const PartitionConfig& part, PartitionInfo* info) {
  info->set_size(part.size);
  HashCalculator hasher;
  TEST_AND_RETURN_FALSE(hasher.UpdateFile(part.path, part.size) ==
                        static_cast<off_t>(part.size));
  TEST_AND_RETURN_FALSE(hasher.Finalize());
  const brillo::Blob& hash = hasher.raw_hash();
  info->set_hash(hash.data(), hash.size());
  LOG(INFO) << part.path << ": size=" << part.size
            << " hash=" << HexEncode(hash);
  return true;
}

bool CompareAopsByDestination(AnnotatedOperation first_aop,
                              AnnotatedOperation second_aop) {
  // We want empty operations to be at the end of the payload.
  if (!first_aop.op.dst_extents().size() || !second_aop.op.dst_extents().size())
    return ((!first_aop.op.dst_extents().size()) <
            (!second_aop.op.dst_extents().size()));
  uint32_t first_dst_start = first_aop.op.dst_extents(0).start_block();
  uint32_t second_dst_start = second_aop.op.dst_extents(0).start_block();
  return first_dst_start < second_dst_start;
}

bool IsExtFilesystem(const string& device) {
  brillo::Blob header;
  // See include/linux/ext2_fs.h for more details on the structure. We obtain
  // ext2 constants from ext2fs/ext2fs.h header but we don't link with the
  // library.
  if (!utils::ReadFileChunk(
          device, 0, SUPERBLOCK_OFFSET + SUPERBLOCK_SIZE, &header) ||
      header.size() < SUPERBLOCK_OFFSET + SUPERBLOCK_SIZE)
    return false;

  const uint8_t* superblock = header.data() + SUPERBLOCK_OFFSET;

  // ext3_fs.h: ext3_super_block.s_blocks_count
  uint32_t block_count =
      *reinterpret_cast<const uint32_t*>(superblock + 1 * sizeof(int32_t));

  // ext3_fs.h: ext3_super_block.s_log_block_size
  uint32_t log_block_size =
      *reinterpret_cast<const uint32_t*>(superblock + 6 * sizeof(int32_t));

  // ext3_fs.h: ext3_super_block.s_magic
  uint16_t magic =
      *reinterpret_cast<const uint16_t*>(superblock + 14 * sizeof(int32_t));

  block_count = le32toh(block_count);
  log_block_size = le32toh(log_block_size) + EXT2_MIN_BLOCK_LOG_SIZE;
  magic = le16toh(magic);

  if (magic != EXT2_SUPER_MAGIC)
    return false;

  // Validation check the parameters.
  TEST_AND_RETURN_FALSE(log_block_size >= EXT2_MIN_BLOCK_LOG_SIZE &&
                        log_block_size <= EXT2_MAX_BLOCK_LOG_SIZE);
  TEST_AND_RETURN_FALSE(block_count > 0);
  return true;
}

// Return the number of CPUs on the machine, and 4 threads in minimum.
size_t GetMaxThreads() {
  return std::max(sysconf(_SC_NPROCESSORS_ONLN), 4L);
}

}  // namespace diff_utils

}  // namespace chromeos_update_engine
