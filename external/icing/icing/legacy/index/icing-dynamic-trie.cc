// Copyright (C) 2019 Google LLC
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
// We store the trie in three areas: nodes, nexts and suffixes.
//
// Nodes contain an index to a children array (kept in nexts) or to
// suffixes (for leaf nodes). Nexts contain children arrays of
// different sizes. Each child entry has the matched char and an index
// back into the nodes. Leaf nodes index into suffixes instead of the
// nexts array. Each suffix is a NULL-terminated suffix off the trie,
// followed by a 4-byte value associated with that key.
//
// Allocation
//
// Nodes are allocated and never removed. Nexts contain arrays of
// sizes in power-of-2 increments, i.e. 1, 2, 4, ..., 256. When the
// number of children of a node increases, it is relocated to an array
// with the proper size. The (smaller) unused array is added to a free
// list. A free list is kept for each array size. Allocations happen
// from the free list first, and then from the end of the nexts
// array. Suffixes are never freed or compacted. If a node wants to
// refer to a smaller suffix, it moves the pointer forward and the
// characters before the new pointer are wasted.
//
// Keys can contain any character except '\0'. The '\0' char is
// special in that it specifies an end-of-key in the child array.
//
// Ideas to try:
//
// - Put suffix index in a Next instead of creating a leaf node.
// - Change allocation buckets to 1, 2, 3, 4, 5, 6, 7, 8, 16, 32, ..., 256
// - Compact next array
// - GroupVarByte and delta-encode the next array
// - Collapse nodes with single children
//
// Persistence
//
// We persist the trie in a binary format such that resurrecting the
// trie is simply a few file reads. The file is laid out as such:
//
// - Header
// - Nodes
// - Nexts
// - Suffixes
//
// Each section is aligned to IcingMMapper::system_page_size(). The max
// requested value for each array is pre-allocated in the file. When
// we make modifications to the arrays, we set bits in a dirty bitmap
// of pages. No changes get written to disk until an explicit call to
// Flush. Then we only write the pages that have their dirty bit set.

#include "icing/legacy/index/icing-dynamic-trie.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cinttypes>
#include <cstdint>
#include <cstring>
#include <memory>
#include <utility>

#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-packed-pod.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/core/icing-timer.h"
#include "icing/legacy/index/icing-array-storage.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-flash-bitmap.h"
#include "icing/legacy/index/icing-mmapper.h"
#include "icing/legacy/index/proto/icing-dynamic-trie-header.pb.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/logging.h"
#include "icing/util/math-util.h"
#include "icing/util/status-macros.h"

using std::inplace_merge;
using std::lower_bound;
using std::max;
using std::mismatch;
using std::pair;
using std::sort;
using std::vector;

namespace icing {
namespace lib {

namespace {
constexpr uint32_t kInvalidNodeIndex = (1U << 24) - 1;
constexpr uint32_t kInvalidNextIndex = ~0U;

void ResetMutableNext(IcingDynamicTrie::Next &mutable_next) {
  mutable_next.set_val(0xff);
  mutable_next.set_node_index(kInvalidNodeIndex);
}
}  // namespace

// Based on the bit field widths.
const uint32_t IcingDynamicTrie::Options::kMaxNodes = (1U << 24) - 1;
const uint32_t IcingDynamicTrie::Options::kMaxNexts = (1U << 27) - 1;
const uint32_t IcingDynamicTrie::Options::kMaxSuffixesSize = 1U << 27;
const uint32_t IcingDynamicTrie::Options::kMaxValueSize = 1U << 16;

const uint32_t IcingDynamicTrie::kInvalidSuffixIndex = ~0U;

const int IcingDynamicTrie::kMaxNextArraySize;
const int IcingDynamicTrie::kNumNextAllocationBuckets;

const uint32_t IcingDynamicTrie::kMaxPropertyId;

const uint32_t IcingDynamicTrie::kInvalidValueIndex;

const uint32_t IcingDynamicTrie::kNoCrc;

// Manages logical node candidates while searching for possible
// variant matches. Currently implemented as depth first search. The
// max stack depth is key length * variant fanout. Since max variant
// fanout is 3, we don't need to worry about blowup of the depth first
// search stack.
//
// Keeps track of original matched string (the string actually present
// in the trie) for every candidate.
class IcingDynamicTrie::CandidateSet {
 public:
  struct Candidate {
    LogicalNode logical_node;
    const char *key;
    int matched_prefix_len;
    std::string matched_span;

    Candidate() {}

    Candidate(const LogicalNode &logical_node_in, const char *key_in,
              int matched_prefix_len_in, const char *matched_span_in,
              int matched_span_len_in)
        : logical_node(logical_node_in),
          key(key_in),
          matched_prefix_len(matched_prefix_len_in),
          matched_span(matched_span_in, matched_span_len_in) {}

    int matched_len() const { return matched_prefix_len + matched_span.size(); }
  };

  explicit CandidateSet(bool prefix) : prefix_(prefix) {}

  bool IsTerminal(const char *key, uint32_t value_index) const {
    // Terminal match condition:
    //
    // 1. Key was entirely consumed.
    // 2. The entire suffix was consumed (hence value index is
    //    valid). OR, we are ok with prefix matches.
    return *key == 0 && (value_index != kInvalidValueIndex || prefix_);
  }

  // Push a terminal or non-terminal.
  void Push(const LogicalNode &logical_node, const char *key,
            uint32_t value_index, int matched_prefix_len,
            const char *matched_span, int matched_span_len) {
    if (!AddMatchIfTerminal(key, value_index, matched_span, matched_span_len)) {
      PushNonTerminal(logical_node, key, matched_prefix_len, matched_span,
                      matched_span_len);
    }
  }

  bool AddMatchIfTerminal(const char *key, uint32_t value_index,
                          const char *matched_span, int matched_span_len) {
    if (!IsTerminal(key, value_index)) {
      return false;
    }

    // Terminal match.
    matches_.push_back(OriginalMatch());
    OriginalMatch *match = &matches_.back();
    match->value_index = value_index;
    match->orig.reserve(cur_prefix_.size() + matched_span_len);
    match->orig.append(cur_prefix_).append(matched_span, matched_span_len);
    return true;
  }

  // Push a definite non-terminal.
  void PushNonTerminal(const LogicalNode &logical_node, const char *key,
                       int matched_prefix_len, const char *matched_span,
                       int matched_span_len) {
    candidates_.push_back(Candidate(logical_node, key, matched_prefix_len,
                                    matched_span, matched_span_len));
  }

  void Pop(Candidate *candidate) {
    *candidate = candidates_.back();
    if (cur_prefix_.size() < candidate->matched_prefix_len) {
      ICING_LOG(FATAL)
          << "Length of current prefix is smaller than length of matched "
             "prefer, there're inconsistencies in dynamic trie.";
    }

    cur_prefix_.resize(candidate->matched_prefix_len);
    cur_prefix_.append(candidate->matched_span);
    candidates_.pop_back();
  }

  bool empty() const { return candidates_.empty(); }

  void Release(vector<OriginalMatch> *ret) {
    if (!empty()) {
      ICING_LOG(FATAL) << "Candidate set not empty before releasing matches";
    }

    ret->swap(matches_);

    cur_prefix_.clear();
    candidates_.clear();
    matches_.clear();
  }

 private:
  const bool prefix_;

  std::string cur_prefix_;
  vector<Candidate> candidates_;

  vector<IcingDynamicTrie::OriginalMatch> matches_;
};

// Options.
bool IcingDynamicTrie::Options::is_valid() const {
  return max_nodes <= kMaxNodes && max_nodes > 0 && max_nexts <= kMaxNexts &&
         max_nexts > 0 && max_suffixes_size <= kMaxSuffixesSize &&
         max_suffixes_size > 0 && value_size <= kMaxValueSize;
}

// IcingDynamicTrieStorage
class IcingDynamicTrie::IcingDynamicTrieStorage {
 public:
  IcingDynamicTrieStorage(const std::string &file_basename,
                          const RuntimeOptions &runtime_options,
                          const IcingFilesystem *filesystem);
  ~IcingDynamicTrieStorage();

  bool is_initialized() const { return hdr_mmapper_.is_valid(); }

  bool CreateIfNotExist(const Options &options);
  bool Init();
  static bool Remove(const std::string &file_basename,
                     const IcingFilesystem &filesystem);
  bool Sync();
  uint64_t GetDiskUsage() const;

  // Returns the size of the elements held in the trie. This excludes the size
  // of any internal metadata of the trie, e.g. the trie's header.
  uint64_t GetElementsFileSize() const;

  void Warm();

  void Clear();

  bool empty() const { return hdr().num_nodes() == 0; }

  // Never cast off these consts when writing to the arrays. Always
  // use the GetMutable* helpers above.
  const Node *GetNode(uint32_t idx) const {
    return &array_storage_[NODE].array_cast<Node>()[idx];
  }
  const Node *GetRootNode() const { return GetNode(0); }
  const Next *GetNext(uint32_t idx, int child) const {
    return &array_storage_[NEXT].array_cast<Next>()[idx + child];
  }
  const char *GetSuffix(uint32_t idx) const {
    return &array_storage_[SUFFIX].array_cast<char>()[idx];
  }

  uint32_t GetNodeIndex(const Node *node) const { return node - GetNode(0); }
  uint32_t GetNextArrayIndex(const Next *next) const {
    return next - GetNext(0, 0);
  }
  uint32_t GetSuffixIndex(const char *suffix) const {
    return suffix - GetSuffix(0);
  }

  // By default, nodes_, nexts_ and suffixes_ are read-only. This
  // returns a writable element or array within and sets
  // dirty_pages_[array_type] as a side effect, assuming the mutable
  // area will get written to.
  Node *GetMutableNode(uint32_t idx);
  Next *GetMutableNextArray(uint32_t idx, uint32_t len);
  char *GetMutableSuffix(uint32_t idx, uint32_t len);

  // Update crcs based on current contents. Returns all_crc or kNoCrc.
  uint32_t UpdateCrc();

  // Allocators.
  uint32_t nodes_left() const;
  uint32_t nexts_left() const;
  uint32_t suffixes_left() const;

  // REQUIRES: nodes_left() > 0.
  Node *AllocNode();
  // REQUIRES: nexts_left() >= kMaxNextArraySize.
  libtextclassifier3::StatusOr<Next *> AllocNextArray(int size);
  void FreeNextArray(Next *next, int log2_size);
  // REQUIRES: suffixes_left() >= strlen(suffix) + 1 + value_size()
  uint32_t MakeSuffix(const char *suffix, const void *value,
                      uint32_t *value_index);

  const IcingDynamicTrieHeader &hdr() const { return hdr_.hdr; }

  uint32_t value_size() const { return hdr().value_size(); }

  void FillDirtyPageStats(Stats *stats) const;

  void inc_num_keys() { hdr_.hdr.set_num_keys(hdr_.hdr.num_keys() + 1); }

 private:
  friend void IcingDynamicTrie::SetHeader(
      const IcingDynamicTrieHeader &new_hdr);

  enum ArrayType { NODE, NEXT, SUFFIX, NUM_ARRAY_TYPES };

  // Returns all filenames that are part of the storage. First
  // filename is the header and the rest correspond to ArrayType enum
  // values.
  static void GetFilenames(const std::string &file_basename,
                           vector<std::string> *filenames);
  static std::string GetHeaderFilename(const std::string &file_basename);

  uint32_t GetHeaderCrc() const;

  uint32_t GetAllCrc() const;

  uint32_t UpdateCrcInternal(bool write_hdr);

  // Initializes hdr_ with options and writes the resulting header to disk.
  bool CreateNewHeader(IcingScopedFd sfd, const Options &options);
  bool WriteHeader();

  // Header block. On-disk header block format is as follows:
  //
  // |serialized-header|pad|crcs|
  // <--- system_page_size() --->

  // Wrapper for header protobuf.
  class Header {
    // Serialized format:
    //
    // magic(4)|size(4)|serialized hdr(size)
    static const uint32_t kMagic;
    // TODO(b/77482303) : Remove version from the IcingFlashBitmap header -
    // magic makes it unnecessary.
    static const uint32_t kCurVersion;

   public:
    void Init(const Options &options);
    bool Init(const uint8_t *buf, uint32_t buf_size);
    void Invalidate() { hdr.Clear(); }
    bool SerializeToArray(uint8_t *buf, uint32_t buf_size) const;
    bool Verify();

    IcingDynamicTrieHeader hdr;
  };

  std::string file_basename_;

  Header hdr_;

  IcingMMapper hdr_mmapper_;

  struct Crcs {
    uint32_t all_crc;
    uint32_t header_crc;
    uint32_t array_crcs[NUM_ARRAY_TYPES];
  };
  Crcs *crcs_;

  static uint32_t serialized_header_max() {
    return IcingMMapper::system_page_size() - sizeof(Crcs);
  }

  RuntimeOptions runtime_options_;

  // Info kept about each array (NODE, NEXT, SUFFIX) to manage
  // storage.
  IcingScopedFd array_fds_[NUM_ARRAY_TYPES];
  std::vector<IcingArrayStorage> array_storage_;

  // Legacy file system. Switch to use the new Filesystem class instead.
  const IcingFilesystem *filesystem_;
};

IcingDynamicTrie::IcingDynamicTrieStorage::IcingDynamicTrieStorage(
    const std::string &file_basename, const RuntimeOptions &runtime_options,
    const IcingFilesystem *filesystem)
    : file_basename_(file_basename),
      hdr_mmapper_(false, MAP_SHARED),
      crcs_(nullptr),
      runtime_options_(runtime_options),
      array_storage_(NUM_ARRAY_TYPES, IcingArrayStorage(*filesystem)),
      filesystem_(filesystem) {}

IcingDynamicTrie::IcingDynamicTrieStorage::~IcingDynamicTrieStorage() {
  if (is_initialized()) {
    for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
      array_storage_[i].Reset();
    }
  }
}

void IcingDynamicTrie::IcingDynamicTrieStorage::GetFilenames(
    const std::string &file_basename, vector<std::string> *filenames) {
  const char *kArrayFilenameSuffixes[NUM_ARRAY_TYPES] = {
      ".n",
      ".x",
      ".s",
  };

  filenames->clear();
  filenames->push_back(GetHeaderFilename(file_basename));
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    filenames->push_back(file_basename + kArrayFilenameSuffixes[i]);
  }
}

std::string IcingDynamicTrie::IcingDynamicTrieStorage::GetHeaderFilename(
    const std::string &file_basename) {
  constexpr char kHeaderFilenameSuffix[] = ".h";
  return file_basename + kHeaderFilenameSuffix;
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Init() {
  bool init_crcs = false;
  const bool map_shared =
      runtime_options_.storage_policy == RuntimeOptions::kMapSharedWithCrc;

  // Open files.
  vector<std::string> filenames;
  GetFilenames(file_basename_, &filenames);
  for (size_t i = 0; i < filenames.size(); i++) {
    uint64_t file_size = filesystem_->GetFileSize(filenames[i].c_str());
    if (file_size == IcingFilesystem::kBadFileSize) {
      goto failed;
    }
    IcingScopedFd sfd(filesystem_->OpenForWrite(filenames[i].c_str()));
    if (!sfd.is_valid()) {
      goto failed;
    }
    // The first filename is the header and the rest correspond to ArrayType
    // enum values. The header's fd can be closed immediately after mmapping
    // (see b/114830334). Other files' fds are tracked in array_fds_ for later
    // closing.
    if (i == 0) {
      // Header.
      if (file_size != IcingMMapper::system_page_size()) {
        ICING_LOG(ERROR) << "Trie hdr wrong size: " << file_size;
        goto failed;
      }

      // Open hdr.
      hdr_mmapper_.Remap(sfd.get(), 0, IcingMMapper::system_page_size());
      if (!hdr_mmapper_.is_valid()) {
        ICING_LOG(ERROR) << "Trie map header failed";
        goto failed;
      }
    } else {
      array_fds_[i - 1] = std::move(sfd);
    }
  }

  // Point crcs_ to correct region.
  crcs_ = reinterpret_cast<Crcs *>(hdr_mmapper_.address() +
                                   serialized_header_max());
  if (crcs_->header_crc == kNoCrc) {
    // Create crcs.
    crcs_->header_crc = GetHeaderCrc();

    // Do the same for the arrays.
    init_crcs = true;
  } else {
    // Verify crc.
    if (crcs_->header_crc != GetHeaderCrc()) {
      ICING_LOG(ERROR) << "Trie header crc failed";
      goto failed;
    }
  }

  // Deserialize and verify header.
  if (!hdr_.Init(hdr_mmapper_.address(),
                 IcingMMapper::system_page_size() - sizeof(Crcs)) ||
      !hdr_.Verify()) {
    ICING_LOG(ERROR) << "Trie reading header failed";
    goto failed;
  }

  // We have the header set up. Now read in the arrays.
  if (!array_storage_[NODE].Init(array_fds_[NODE].get(), 0, map_shared,
                                 sizeof(Node), hdr_.hdr.num_nodes(),
                                 hdr_.hdr.max_nodes(), &crcs_->array_crcs[NODE],
                                 init_crcs)) {
    ICING_LOG(ERROR) << "Trie mmap node failed";
    goto failed;
  }

  if (!array_storage_[NEXT].Init(array_fds_[NEXT].get(), 0, map_shared,
                                 sizeof(Next), hdr_.hdr.num_nexts(),
                                 hdr_.hdr.max_nexts(), &crcs_->array_crcs[NEXT],
                                 init_crcs)) {
    ICING_LOG(ERROR) << "Trie mmap next failed";
    goto failed;
  }

  if (!array_storage_[SUFFIX].Init(array_fds_[SUFFIX].get(), 0, map_shared,
                                   sizeof(char), hdr_.hdr.suffixes_size(),
                                   hdr_.hdr.max_suffixes_size(),
                                   &crcs_->array_crcs[SUFFIX], init_crcs)) {
    ICING_LOG(ERROR) << "Trie mmap suffix failed";
    goto failed;
  }

  // Overall crc.
  if (init_crcs) {
    crcs_->all_crc = GetAllCrc();
  } else {
    // Verify crc.
    if (crcs_->all_crc != GetAllCrc()) {
      ICING_LOG(ERROR) << "Trie all crc failed";
      goto failed;
    }
  }

  return true;

failed:
  crcs_ = nullptr;
  hdr_mmapper_.Unmap();
  hdr_.Invalidate();
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    array_storage_[i].Reset();
    array_fds_[i].reset();
  }

  return false;
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::CreateIfNotExist(
    const Options &options) {
  vector<std::string> filenames;
  GetFilenames(file_basename_, &filenames);

  // Check already exists. Just header file check is enough.
  if (filesystem_->FileExists(filenames[0].c_str())) {
    return true;
  }

  // Ensure the storage directory exists
  std::string storage_dir = filesystem_->GetDirname(filenames[0].c_str());
  if (!filesystem_->CreateDirectoryRecursively(storage_dir.c_str())) {
    return false;
  }

  // Create files.
  for (size_t i = 0; i < filenames.size(); i++) {
    IcingScopedFd sfd(filesystem_->OpenForWrite(filenames[i].c_str()));
    if (!sfd.is_valid()) {
      Remove(file_basename_, *filesystem_);
      return false;
    }

    if (i == 0) {
      if (!CreateNewHeader(std::move(sfd), options)) {
        ICING_LOG(ERROR) << "Serialize trie header failed";
        Remove(file_basename_, *filesystem_);
        return false;
      }
    } else {
      // Crcs are automatically kNoCrc so they will be initialized
      // upon first call to Init.
      if (!filesystem_->Truncate(*sfd, 0)) {
        Remove(file_basename_, *filesystem_);
        return false;
      }
    }
  }
  return true;
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::CreateNewHeader(
    IcingScopedFd sfd, const Options &options) {
  ICING_VLOG(1) << "Creating header with write+sync";
  hdr_.Init(options);
  auto buf = std::make_unique<uint8_t[]>(IcingMMapper::system_page_size());
  // serialized_header_max must be less than system_page_size so we don't
  // overflow buf when serializing the header.
  if (serialized_header_max() > IcingMMapper::system_page_size()) {
    ICING_LOG(FATAL) << "serialized_header_max exceeds system page size";
  }

  return hdr_.SerializeToArray(buf.get(), serialized_header_max()) &&
         filesystem_->Write(sfd.get(), buf.get(),
                            IcingMMapper::system_page_size()) &&
         filesystem_->DataSync(sfd.get());
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Remove(
    const std::string &file_basename, const IcingFilesystem &filesystem) {
  bool success = true;
  vector<std::string> files;
  GetFilenames(file_basename, &files);
  for (size_t i = 0; i < files.size(); i++) {
    if (!filesystem.DeleteFile(files[i].c_str())) {
      success = false;
    }
  }
  return success;
}

void IcingDynamicTrie::IcingDynamicTrieStorage::Warm() {
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    array_storage_[i].Warm();
  }
}

void IcingDynamicTrie::IcingDynamicTrieStorage::Clear() {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  // Clear header.
  hdr_.hdr.set_num_nodes(0);
  hdr_.hdr.set_num_nexts(0);
  hdr_.hdr.set_suffixes_size(0);
  for (int i = 0; i < hdr_.hdr.free_lists_size(); i++) {
    hdr_.hdr.set_free_lists(i, kInvalidNextIndex);
  }
  hdr_.hdr.set_num_keys(0);

  // Clear array storage.
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    array_storage_[i].Clear();
  }

  // Copy to persistence.
  WriteHeader();
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Sync() {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  uint32_t total_flushed = 0;
  bool success = true;

  // Sync all array types.
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    total_flushed += array_storage_[i].Sync();
    if (!filesystem_->DataSync(array_fds_[i].get())) {
      ICING_LOG(ERROR) << "Unable to sync data for flushing";
      success = false;
    }
  }

  if (!WriteHeader()) {
    ICING_LOG(ERROR) << "Flushing trie header failed: " << strerror(errno);
    success = false;
  }

  // Need to update CRCs before we sync the header mmap.
  UpdateCrcInternal(false);

  // Sync header.
  if (!hdr_mmapper_.Sync()) {
    ICING_LOG(ERROR) << "Unable to sync trie header for flushing";
    success = false;
  }

  if (total_flushed > 0) {
    ICING_VLOG(1) << "Flushing " << total_flushed << " pages of trie";
  }

  return success;
}

uint64_t IcingDynamicTrie::IcingDynamicTrieStorage::GetDiskUsage() const {
  // Trie files themselves.
  uint64_t total = 0;
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    IcingFilesystem::IncrementByOrSetInvalid(
        filesystem_->GetDiskUsage(array_fds_[i].get()), &total);
  }

  // Header.
  std::string header_filename = GetHeaderFilename(file_basename_);
  IcingFilesystem::IncrementByOrSetInvalid(
      filesystem_->GetFileDiskUsage(header_filename.c_str()), &total);

  return total;
}

uint64_t IcingDynamicTrie::IcingDynamicTrieStorage::GetElementsFileSize()
    const {
  // Trie files themselves, exclude size of the header. These arrays are dense,
  // not sparse, so use file size for more accurate numbers.
  uint64_t total = 0;
  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    IcingFilesystem::IncrementByOrSetInvalid(
        filesystem_->GetFileSize(array_fds_[i].get()), &total);
  }
  return total;
}

IcingDynamicTrie::Node *IcingDynamicTrie::IcingDynamicTrieStorage::AllocNode() {
  if (nodes_left() == 0) {
    ICING_LOG(FATAL) << "No allocated nodes left";
  }

  hdr_.hdr.set_num_nodes(hdr_.hdr.num_nodes() + 1);
  return GetMutableNode(hdr_.hdr.num_nodes() - 1);
}

libtextclassifier3::StatusOr<IcingDynamicTrie::Next *>
IcingDynamicTrie::IcingDynamicTrieStorage::AllocNextArray(int size) {
  if (size > kMaxNextArraySize) {
    return absl_ports::InternalError(
        "Array size exceeds the max 'next' array size");
  }

  if (nexts_left() < static_cast<uint32_t>(kMaxNextArraySize)) {
    ICING_LOG(FATAL) << "'next' buffer not enough";
  }

  // Compute ceil(log2(size)).
  int log2_size = 0;
  while ((1 << log2_size) < size) log2_size++;
  // Note: size <= aligned_size <= kMaxNextArraySize
  int aligned_size = 1 << log2_size;

  // Look in free list.
  Next *ret;
  if (hdr_.hdr.free_lists(log2_size) != kInvalidNextIndex) {
    ret = GetMutableNextArray(hdr_.hdr.free_lists(log2_size), aligned_size);
    uint32_t next_link = ret->next_index();
    if (next_link != kInvalidNextIndex && next_link >= hdr_.hdr.max_nexts()) {
      ICING_LOG(FATAL) << "'next' index is out of range";
    }
    hdr_.hdr.set_free_lists(log2_size, next_link);
  } else {
    // Allocate a new one.
    ret = GetMutableNextArray(hdr_.hdr.num_nexts(), aligned_size);
    hdr_.hdr.set_num_nexts(hdr_.hdr.num_nexts() + aligned_size);
  }

  // Fill with char 0xff so we are sorted properly.
  for (int i = 0; i < aligned_size; i++) {
    ResetMutableNext(ret[i]);
  }
  return ret;
}

void IcingDynamicTrie::IcingDynamicTrieStorage::FreeNextArray(Next *next,
                                                              int log2_size) {
  if (GetNextArrayIndex(next) + (1 << log2_size) > hdr_.hdr.max_nexts()) {
    ICING_LOG(FATAL) << "'next' array is out of range";
  }

  // Put it in free list.
  next->set_next_index(hdr_.hdr.free_lists(log2_size));
  hdr_.hdr.set_free_lists(log2_size, GetNextArrayIndex(next));
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::MakeSuffix(
    const char *suffix, const void *value, uint32_t *value_index) {
  int suffix_len = strlen(suffix);
  if (suffixes_left() < suffix_len + 1 + value_size()) {
    ICING_LOG(FATAL) << "'suffix' buffer not enough";
  }

  char *start =
      GetMutableSuffix(hdr_.hdr.suffixes_size(), suffix_len + 1 + value_size());
  memcpy(start, suffix, suffix_len + 1);
  memcpy(start + suffix_len + 1, value, value_size());
  if (value_index) *value_index = GetSuffixIndex(start + suffix_len + 1);
  hdr_.hdr.set_suffixes_size(hdr_.hdr.suffixes_size() + suffix_len + 1 +
                             value_size());

  return GetSuffixIndex(start);
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::GetHeaderCrc() const {
  return IcingStringUtil::UpdateCrc32(
      0, reinterpret_cast<const char *>(hdr_mmapper_.address()),
      serialized_header_max());
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::GetAllCrc() const {
  // Append array crcs to header crc.
  return IcingStringUtil::UpdateCrc32(
      crcs_->header_crc, reinterpret_cast<const char *>(crcs_->array_crcs),
      sizeof(crcs_->array_crcs));
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::UpdateCrc() {
  return UpdateCrcInternal(true);
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::UpdateCrcInternal(
    bool write_hdr) {
  if (write_hdr && !WriteHeader()) {
    ICING_LOG(ERROR) << "Flushing trie header failed: " << strerror(errno);
  }

  crcs_->header_crc = GetHeaderCrc();

  for (int i = 0; i < NUM_ARRAY_TYPES; i++) {
    array_storage_[i].UpdateCrc();
  }

  crcs_->all_crc = GetAllCrc();

  return crcs_->all_crc;
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::WriteHeader() {
  return hdr_.SerializeToArray(hdr_mmapper_.address(), serialized_header_max());
}

IcingDynamicTrie::Node *
IcingDynamicTrie::IcingDynamicTrieStorage::GetMutableNode(uint32_t idx) {
  return array_storage_[NODE].GetMutableMem<Node>(idx, 1);
}

IcingDynamicTrie::Next *
IcingDynamicTrie::IcingDynamicTrieStorage::GetMutableNextArray(uint32_t idx,
                                                               uint32_t len) {
  return array_storage_[NEXT].GetMutableMem<Next>(idx, len);
}

char *IcingDynamicTrie::IcingDynamicTrieStorage::GetMutableSuffix(
    uint32_t idx, uint32_t len) {
  return array_storage_[SUFFIX].GetMutableMem<char>(idx, len);
}

// Header functions.
const uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::Header::kMagic =
    0x6dfba6ae;
// For future revisions, this should be synced with global index version.
// See comments on Upgrade() in native-index-impl.h for versioning.
const uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::Header::kCurVersion =
    4;

void IcingDynamicTrie::IcingDynamicTrieStorage::Header::Init(
    const IcingDynamicTrie::Options &options) {
  hdr.Clear();

  hdr.set_version(kCurVersion);
  hdr.set_max_nodes(options.max_nodes);
  hdr.set_max_nexts(options.max_nexts);
  hdr.set_max_suffixes_size(options.max_suffixes_size);
  hdr.set_value_size(options.value_size);

  for (int i = 0; i < kNumNextAllocationBuckets; i++) {
    hdr.add_free_lists(kInvalidNextIndex);
  }
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Header::Init(
    const uint8_t *buf, uint32_t buf_size) {
  // Check magic and length.
  if (buf_size <= sizeof(kMagic) + sizeof(uint32_t)) {
    ICING_LOG(ERROR) << "Trie header too short";
    return false;
  }

  uint32_t magic;
  memcpy(&magic, buf, sizeof(magic));
  if (magic != kMagic) {
    ICING_LOG(ERROR) << "Trie header magic mismatch";
    return false;
  }
  uint32_t len;
  memcpy(&len, buf + sizeof(magic), sizeof(len));
  if (len > buf_size - sizeof(magic) - sizeof(len)) {
    ICING_LOG(ERROR) << "Trie header too short";
    return false;
  }

  return hdr.ParseFromArray(buf + sizeof(magic) + sizeof(len), len);
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Header::SerializeToArray(
    uint8_t *buf, uint32_t buf_size) const {
  uint32_t size = hdr.ByteSizeLong();
  if (size + sizeof(kMagic) + sizeof(uint32_t) > buf_size) return false;
  memcpy(buf, &kMagic, sizeof(kMagic));
  memcpy(buf + sizeof(kMagic), &size, sizeof(uint32_t));
  hdr.SerializeWithCachedSizesToArray(buf + sizeof(kMagic) + sizeof(uint32_t));
  return true;
}

bool IcingDynamicTrie::IcingDynamicTrieStorage::Header::Verify() {
  // Check version.
  if (hdr.version() != kCurVersion) {
    ICING_LOG(ERROR) << "Trie version " << hdr.version() << " mismatch";
    return false;
  }

  // Check that indices in hdr are within bounds. Note that this is
  // not a comprehensive integrity check for the entire trie.
  if (hdr.num_nodes() > hdr.max_nodes() || hdr.num_nexts() > hdr.max_nexts() ||
      hdr.suffixes_size() > hdr.max_suffixes_size() ||
      hdr.value_size() >= hdr.max_suffixes_size()) {
    ICING_LOG(ERROR) << "Trie header array size out of bounds";
    return false;
  }

  if (hdr.free_lists_size() != kNumNextAllocationBuckets) {
    ICING_LOG(ERROR) << "Bad number of free lists";
    return false;
  }

  for (int i = 0; i < kNumNextAllocationBuckets; i++) {
    if (hdr.free_lists(i) != kInvalidNextIndex &&
        hdr.free_lists(i) >= hdr.max_nexts()) {
      ICING_LOG(ERROR) << "Free list index out of bounds";
      return false;
    }
  }

  return true;
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::nodes_left() const {
  return hdr_.hdr.max_nodes() - hdr_.hdr.num_nodes();
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::nexts_left() const {
  return hdr_.hdr.max_nexts() - hdr_.hdr.num_nexts();
}

uint32_t IcingDynamicTrie::IcingDynamicTrieStorage::suffixes_left() const {
  return hdr_.hdr.max_suffixes_size() - hdr_.hdr.suffixes_size();
}

void IcingDynamicTrie::IcingDynamicTrieStorage::FillDirtyPageStats(
    Stats *stats) const {
  stats->dirty_pages_nodes = array_storage_[NODE].num_dirty_pages();
  stats->dirty_pages_nexts = array_storage_[NEXT].num_dirty_pages();
  stats->dirty_pages_suffixes = array_storage_[SUFFIX].num_dirty_pages();
}

// Dumper.
class IcingDynamicTrie::Dumper {
 public:
  explicit Dumper(const IcingDynamicTrie &trie)
      : all_props_(trie), del_prop_(trie), storage_(trie.storage_.get()) {}

  void Dump(std::ostream *pretty_print, vector<std::string> *keys) const {
    if (storage_->empty()) {
      *pretty_print << "(empty)\n";
    } else {
      DumpNodeRecursive("", *storage_->GetRootNode(), 0, pretty_print, keys);
    }
  }

 private:
  std::string SuffixToValueAsString(const char *suffix) const {
    int suffix_len = strlen(suffix);
    std::string ret;
    ret.reserve(storage_->value_size() * 2);
    for (uint32_t i = 0; i < storage_->value_size(); i++) {
      IcingStringUtil::SStringAppendF(&ret, 10, "%02x",
                                      suffix[suffix_len + 1 + i]);
    }

    // Now dump set properties.
    uint32_t value_index = storage_->GetSuffixIndex(suffix + suffix_len + 1);
    if (del_prop_.HasProperty(value_index)) {
      ret += " (deleted)";
    }
    ret += " [";
    for (size_t i = 0; i < all_props_.size(); i++) {
      if (all_props_.HasProperty(i, value_index)) {
        IcingStringUtil::SStringAppendF(&ret, 10, "%zu", i);
      }
    }
    ret += ']';

    return ret;
  }

  // Inputs:
  //   prefix - the key prefix of the current node (so we can rebuild the key)
  //   node - the node we're at
  //   level - how many levels deep we are in the trie
  //   ret - the stream to pretty print to
  //   keys - the keys encountered are appended to this
  void DumpNodeRecursive(const std::string &prefix, const Node &node, int level,
                         std::ostream *ret, vector<std::string> *keys) const {
    if (node.is_leaf()) {
      // Dump suffix and value.
      for (int i = 0; i < level; i++) {
        *ret << ' ';
      }
      const char *suffix = storage_->GetSuffix(node.next_index());
      *ret << suffix;
      *ret << ' ';
      *ret << SuffixToValueAsString(suffix);
      *ret << '\n';
      keys->push_back(prefix + suffix);
    } else {
      // Go through each child (next) node. Print char and recursively
      // print trie underneath.
      for (uint32_t i = 0; i < (1U << node.log2_num_children()); i++) {
        const Next &next = *storage_->GetNext(node.next_index(), i);
        if (next.node_index() == kInvalidNodeIndex) break;
        for (int j = 0; j < level; j++) {
          *ret << ' ';
        }
        std::string new_prefix = prefix;
        if (next.val()) {
          *ret << static_cast<char>(next.val());
          new_prefix += next.val();
        } else {
          *ret << "null";
        }
        *ret << '\n';
        DumpNodeRecursive(new_prefix, *storage_->GetNode(next.node_index()),
                          level + 1, ret, keys);
      }
    }
  }

  PropertyReadersAll all_props_;
  PropertyDeletedReader del_prop_;
  const IcingDynamicTrie::IcingDynamicTrieStorage *storage_;
};

// IcingDynamicTrie.
IcingDynamicTrie::IcingDynamicTrie(const std::string &filename_base,
                                   const RuntimeOptions &runtime_options,
                                   const IcingFilesystem *filesystem)
    : IIcingStorage(),
      filename_base_(filename_base),
      is_initialized_(false),
      runtime_options_(runtime_options),
      storage_(nullptr),
      property_bitmaps_prefix_(filename_base_ + ".prop."),
      deleted_bitmap_filename_(filename_base_ + ".deleted"),
      deleted_bitmap_(nullptr),
      filesystem_(filesystem) {}

IcingDynamicTrie::~IcingDynamicTrie() { Close(); }

bool IcingDynamicTrie::Init() {
  if (is_initialized_) return true;

  if (storage_ != nullptr) {
    ICING_LOG(FATAL) << "Storage is not null before initialization";
  }

  storage_ = std::make_unique<IcingDynamicTrieStorage>(
      filename_base_, runtime_options_, filesystem_);
  if (!storage_->Init() || !InitPropertyBitmaps()) {
    storage_.reset();
    return false;
  }
  is_initialized_ = true;
  return true;
}

bool IcingDynamicTrie::CreateIfNotExist(const Options &options) {
  // Initialized means exists.
  if (is_initialized_) return true;

  if (!options.is_valid()) {
    ICING_LOG(ERROR) << "Trie options invalid";
    return false;
  }

  auto storage = std::make_unique<IcingDynamicTrieStorage>(
      filename_base_, runtime_options_, filesystem_);
  return storage->CreateIfNotExist(options);
}

void IcingDynamicTrie::Close() {
  if (!is_initialized_) return;

  UpdateCrc();

  storage_.reset();
  property_bitmaps_.clear();
  deleted_bitmap_.reset();
  is_initialized_ = false;
}

bool IcingDynamicTrie::Remove() {
  if (is_initialized()) {
    Close();
  }

  bool success = true;

  // Remove storage files.
  if (!IcingDynamicTrieStorage::Remove(filename_base_, *filesystem_)) {
    success = false;
  }

  // Also remove property bitmaps.
  vector<std::string> files;
  if (!filesystem_->GetMatchingFiles((property_bitmaps_prefix_ + "*").c_str(),
                                     &files)) {
    return false;
  }
  for (size_t i = 0; i < files.size(); i++) {
    if (!filesystem_->DeleteFile(files[i].c_str())) success = false;
  }
  // And deleted bitmap.
  if (!filesystem_->DeleteFile(deleted_bitmap_filename_.c_str()))
    success = false;

  return success;
}

bool IcingDynamicTrie::Sync() {
  if (!is_initialized_) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  bool success = true;
  IcingTimer timer;

  // Sync property bitmaps.
  for (size_t i = 0; i < property_bitmaps_.size(); i++) {
    if (property_bitmaps_[i]) {
      if (!property_bitmaps_[i]->Sync()) success = false;
    }
  }
  if (!deleted_bitmap_->Sync()) success = false;

  // Sync storage.
  if (!storage_->Sync()) success = false;

  Warm();

  ICING_VLOG(1) << "Syncing dynamic trie " << filename_base_.c_str()
      << " took " << timer.Elapsed() * 1000 << "ms";

  return success;
}

uint64_t IcingDynamicTrie::GetDiskUsage() const {
  uint64_t total = 0;
  // Property bitmaps.
  IcingFilesystem::IncrementByOrSetInvalid(deleted_bitmap_->GetDiskUsage(),
                                           &total);

  for (auto &bitmap : property_bitmaps_) {
    if (bitmap == nullptr) continue;
    IcingFilesystem::IncrementByOrSetInvalid(bitmap->GetDiskUsage(), &total);
  }

  // Storage.
  IcingFilesystem::IncrementByOrSetInvalid(storage_->GetDiskUsage(), &total);
  return total;
}

uint64_t IcingDynamicTrie::GetElementsSize() const {
  uint64_t total = 0;

  // Bitmaps are sparsely populated, so disk usage is more accurate for those.
  // Property bitmaps.
  IcingFilesystem::IncrementByOrSetInvalid(deleted_bitmap_->GetDiskUsage(),
                                           &total);
  // The deleted bitmap is always initially grown to kGrowSize, whether there
  // are elements or not. So even if there are no elements in the trie, we'll
  // still have the bitmap of size kGrowSize, so subtract that from the size of
  // the trie's elements.
  total -= IcingFlashBitmap::kGrowSize;

  for (auto &bitmap : property_bitmaps_) {
    if (bitmap == nullptr) continue;
    IcingFilesystem::IncrementByOrSetInvalid(bitmap->GetDiskUsage(), &total);
  }

  // Storage. We can use file size here since the storage files aren't sparse.
  IcingFilesystem::IncrementByOrSetInvalid(storage_->GetElementsFileSize(),
                                           &total);
  return total;
}

std::unique_ptr<IcingFlashBitmap> IcingDynamicTrie::OpenAndInitBitmap(
    const std::string &filename, bool verify,
    const IcingFilesystem *filesystem) {
  auto bitmap = std::make_unique<IcingFlashBitmap>(filename, filesystem);
  if (!bitmap->Init() || (verify && !bitmap->Verify())) {
    ICING_LOG(ERROR) << "Init of " << filename.c_str() << " failed";
    return nullptr;
  }
  return bitmap;
}

bool IcingDynamicTrie::InitPropertyBitmaps() {
  // Only called on init.
  if (!property_bitmaps_.empty()) {
    ICING_LOG(FATAL) << "Property bitmaps not empty before initialization";
  }

  if (deleted_bitmap_ != nullptr) {
    ICING_LOG(FATAL) << "Deleted bitmap not null before initialization";
  }

  // Truncate property bitmap files at current value index. Last value
  // is at suffixes_size - value_size(). We want to clear everything
  // after that.
  uint64_t truncate_idx =
      storage_->hdr().suffixes_size() > 0
          ? ValueIndexToPropertyBitmapIndex(storage_->hdr().suffixes_size() -
                                            value_size()) +
                1
          : 0;

  // Discover property bitmaps by scanning the dir.
  vector<std::string> files;
  if (!filesystem_->GetMatchingFiles((property_bitmaps_prefix_ + "*").c_str(),
                                     &files)) {
    ICING_LOG(ERROR) << "Could not get files at prefix " << property_bitmaps_prefix_;
    goto failed;
  }
  for (size_t i = 0; i < files.size(); i++) {
    // Decode property id from filename.
    size_t property_id_start_idx = files[i].rfind('.');
    if (property_id_start_idx == std::string::npos) {
      ICING_LOG(ERROR) << "Malformed filename " << files[i];
      continue;
    }
    property_id_start_idx++;  // skip dot
    char *end;
    uint32_t property_id =
        strtol(files[i].c_str() + property_id_start_idx, &end, 10);  // NOLINT
    if (!end || end != (files[i].c_str() + files[i].size())) {
      ICING_LOG(ERROR) << "Malformed filename " << files[i];
      continue;
    }
    std::unique_ptr<IcingFlashBitmap> bitmap = OpenAndInitBitmap(
        files[i],
        runtime_options_.storage_policy == RuntimeOptions::kMapSharedWithCrc,
        filesystem_);
    if (!bitmap) {
      ICING_LOG(ERROR) << "Open prop bitmap failed: " << files[i];
      goto failed;
    }
    bitmap->Truncate(truncate_idx);
    if (property_id >= property_bitmaps_.size()) {
      property_bitmaps_.resize(property_id + 1);
    }
    property_bitmaps_[property_id] = std::move(bitmap);
  }

  deleted_bitmap_ = OpenAndInitBitmap(
      deleted_bitmap_filename_,
      runtime_options_.storage_policy == RuntimeOptions::kMapSharedWithCrc,
      filesystem_);
  if (!deleted_bitmap_) {
    goto failed;
  }
  deleted_bitmap_->Truncate(truncate_idx);

  return true;

failed:
  property_bitmaps_.clear();
  deleted_bitmap_.reset();
  return false;
}

void IcingDynamicTrie::Warm() const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  return storage_->Warm();
}

void IcingDynamicTrie::OnSleep() {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  // Update crcs so we can verify when we come back.
  UpdateCrc();
}

uint32_t IcingDynamicTrie::size() const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }
  return storage_->hdr().num_keys();
}

void IcingDynamicTrie::CollectStatsRecursive(const Node &node, Stats *stats,
                                             uint32_t depth) const {
  if (node.is_leaf()) {
    stats->num_leaves++;
    stats->sum_depth += depth;
    stats->max_depth = max(stats->max_depth, depth);
    const char *suffix = storage_->GetSuffix(node.next_index());
    stats->suffixes_used += strlen(suffix) + 1 + value_size();
    if (!suffix[0]) {
      stats->null_suffixes++;
    }
  } else {
    stats->num_intermediates++;
    uint32_t i = 0;
    for (; i < (1U << node.log2_num_children()); i++) {
      const Next &next = *storage_->GetNext(node.next_index(), i);
      if (next.node_index() == kInvalidNodeIndex) break;
      CollectStatsRecursive(*storage_->GetNode(next.node_index()), stats,
                            depth + 1);
    }

    // At least one valid node in each next array
    if (i == 0) {
      ICING_LOG(FATAL) << "No valid node in 'next' array";
    }
    stats->sum_children += i;
    stats->max_children = max(stats->max_children, i);

    stats->child_counts[i - 1]++;
    stats->wasted[node.log2_num_children()] +=
        (1 << node.log2_num_children()) - i;
    stats->total_wasted += (1 << node.log2_num_children()) - i;
  }
}

void IcingDynamicTrie::CollectStats(Stats *stats) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  memset(stats, 0, sizeof(*stats));

  stats->num_keys = storage_->hdr().num_keys();
  stats->num_nodes = storage_->hdr().num_nodes();
  stats->max_nodes = storage_->hdr().max_nodes();
  stats->num_nexts = storage_->hdr().num_nexts();
  stats->max_nexts = storage_->hdr().max_nexts();
  stats->suffixes_size = storage_->hdr().suffixes_size();
  stats->max_suffixes_size = storage_->hdr().max_suffixes_size();

  // Stats collected from traversing the trie.
  if (!storage_->empty()) {
    CollectStatsRecursive(*storage_->GetRootNode(), stats);
  }

  // Free-list stats.
  for (int i = 0; i < kNumNextAllocationBuckets; i++) {
    for (uint32_t cur = storage_->hdr().free_lists(i); cur != kInvalidNextIndex;
         cur = storage_->GetNext(cur, 0)->next_index()) {
      stats->num_free[i]++;
    }
    stats->total_free += stats->num_free[i] * (1 << i);
  }

  // Dirty page counts.
  storage_->FillDirtyPageStats(stats);
}

std::string IcingDynamicTrie::Stats::DumpStats(int verbosity) const {
  std::string ret;
  IcingStringUtil::SStringAppendF(
      &ret, 0,
      "Keys %u "
      "Nodes (%u/%u) %.3f%% "
      "Nexts (%u/%u) %.3f%% "
      "Suffixes (%u/%u) %.3f%%\n",
      num_keys, num_nodes, max_nodes,
      100. * math_util::SafeDivide(num_nodes, max_nodes), num_nexts, max_nexts,
      100. * math_util::SafeDivide(num_nexts, max_nexts), suffixes_size,
      max_suffixes_size,
      100. * math_util::SafeDivide(suffixes_size, max_suffixes_size));

  if (verbosity > 0) {
    for (int i = 0; i < kNumNextAllocationBuckets; i++) {
      if (num_free[i] > 0) {
        IcingStringUtil::SStringAppendF(&ret, 0, "Freelist@%d: %u\n", 1 << i,
                                        num_free[i]);
      }
    }
    IcingStringUtil::SStringAppendF(
        &ret, 0, "Freelist total: %u/%u %.3f%%\n", total_free, num_nexts,
        100. * math_util::SafeDivide(total_free, num_nexts));

    for (int i = 0; i < 256; i++) {
      if (child_counts[i] > 0) {
        IcingStringUtil::SStringAppendF(&ret, 0, "Child count@%d: %u\n", i + 1,
                                        child_counts[i]);
      }
    }
    for (int i = 0; i < kNumNextAllocationBuckets; i++) {
      IcingStringUtil::SStringAppendF(&ret, 0, "Wasted@%d: %u\n", 1 << i,
                                      wasted[i]);
    }
    IcingStringUtil::SStringAppendF(
        &ret, 0,
        "Wasted total: %u\n"
        "Num intermediates %u num leaves %u "
        "suffixes used %u null %u\n"
        "avg and max children for intermediates: %.3f, %u\n"
        "avg and max depth for leaves: %.3f, %u\n"
        "Total next frag: %.3f%%\n",
        total_wasted, num_intermediates, num_leaves, suffixes_used,
        null_suffixes, 1. * sum_children / num_intermediates, max_children,
        1. * sum_depth / num_leaves, max_depth,
        100. * math_util::SafeDivide((total_free + total_wasted), num_nexts));
  }
  IcingStringUtil::SStringAppendF(
      &ret, 0, "Memory usage: %zu/%zu bytes\n",
      num_nodes * sizeof(Node) + num_nexts * sizeof(Next) + suffixes_size,
      max_nodes * sizeof(Node) + max_nexts * sizeof(Next) + max_suffixes_size);

  IcingStringUtil::SStringAppendF(
      &ret, 0, "Dirty pages: nodes %u/%.0f nexts %u/%.0f suffixes %u/%.0f\n",
      dirty_pages_nodes,
      math_util::SafeDivide(num_nodes * sizeof(Node) + getpagesize() - 1,
                            getpagesize()),
      dirty_pages_nexts,
      math_util::SafeDivide(num_nexts * sizeof(Next) + getpagesize() - 1,
                            getpagesize()),
      dirty_pages_suffixes,
      math_util::SafeDivide(suffixes_size + getpagesize() - 1, getpagesize()));

  return ret;
}

void IcingDynamicTrie::DumpTrie(std::ostream *pretty_print,
                                vector<std::string> *keys) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  Dumper dumper(*this);
  dumper.Dump(pretty_print, keys);
}

void IcingDynamicTrie::Clear() {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  storage_->Clear();
  for (auto &bitmap : property_bitmaps_) {
    if (bitmap) {
      bitmap->Delete();
      bitmap.reset();
    }
  }
  deleted_bitmap_->Truncate(0);
}

bool IcingDynamicTrie::ClearSuffixAndValue(uint32_t suffix_value_index) {
  // The size 1 below is for a '\0' between the suffix and the value.
  size_t suffix_and_value_length =
      strlen(this->storage_->GetSuffix(suffix_value_index)) + 1 +
      this->value_size();
  char *mutable_suffix_and_value = this->storage_->GetMutableSuffix(
      suffix_value_index, suffix_and_value_length);

  if (mutable_suffix_and_value == nullptr) {
    return false;
  }

  memset(mutable_suffix_and_value, 0, suffix_and_value_length);
  return true;
}

bool IcingDynamicTrie::ResetNext(uint32_t next_index) {
  Next *mutable_next =
      this->storage_->GetMutableNextArray(next_index, /*len=*/1);

  if (mutable_next == nullptr) {
    return false;
  }
  ResetMutableNext(*mutable_next);
  return true;
}

bool IcingDynamicTrie::SortNextArray(const Node *node) {
  if (node == nullptr) {
    // Nothing to sort, return success directly.
    return true;
  }

  uint32_t next_array_buffer_size = 1u << node->log2_num_children();
  Next *next_array_start = this->storage_->GetMutableNextArray(
      node->next_index(), next_array_buffer_size);

  if (next_array_start == nullptr) {
    return false;
  }

  std::sort(next_array_start, next_array_start + next_array_buffer_size);
  return true;
}

libtextclassifier3::Status IcingDynamicTrie::Insert(const char *key,
                                                    const void *value,
                                                    uint32_t *value_index,
                                                    bool replace,
                                                    bool *pnew_key) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (pnew_key) *pnew_key = false;

  // Find out ahead of time whether things will fit. A conservative
  // check based on allocations made below.
  //
  // IMPORTANT: This needs to be updated if the alloc patterns below
  // change.
  size_t key_len = strlen(key);
  if (!(storage_->nodes_left() >= 2 + key_len + 1 &&
        storage_->nexts_left() >= 2 + key_len + 1 + kMaxNextArraySize &&
        storage_->suffixes_left() >= key_len + 1 + value_size())) {
    return absl_ports::ResourceExhaustedError("No more space left");
  }

  uint32_t best_node_index;
  int key_offset;
  FindBestNode(key, &best_node_index, &key_offset, false);

  // A negative key_offset indicates that storage_ is empty
  if (key_offset < 0) {
    // First key.
    if (!storage_->empty()) {
      ICING_LOG(FATAL) << "Key offset is negative but storage is not empty, "
                          "there're inconsistencies in dynamic trie.";
    }
    Node *node = storage_->AllocNode();
    node->set_next_index(storage_->MakeSuffix(key, value, value_index));
    node->set_is_leaf(true);
    node->set_log2_num_children(0);
  } else if (storage_->GetNode(best_node_index)->is_leaf()) {
    // Prefix in the trie. Split at leaf.
    Node *split_node = storage_->GetMutableNode(best_node_index);
    const char *prev_suffix = storage_->GetSuffix(split_node->next_index());

    // Find the common prefix length.
    const char *prev_suffix_cur = prev_suffix;
    const char *key_cur = key + key_offset;
    while (*prev_suffix_cur && *prev_suffix_cur == *key_cur) {
      prev_suffix_cur++;
      key_cur++;
    }

    // Equal strings?
    if (*prev_suffix_cur == 0 && *key_cur == 0) {
      // Update value if replace == true and return.
      if (value_index) {
        *value_index = storage_->GetSuffixIndex(prev_suffix_cur + 1);
      }
      if (replace) {
        char *mutable_prev_suffix_cur = storage_->GetMutableSuffix(
            storage_->GetSuffixIndex(prev_suffix_cur + 1), value_size());
        memcpy(mutable_prev_suffix_cur, value, value_size());
      }
      return libtextclassifier3::Status::OK;
    }

    if (*prev_suffix_cur == *key_cur) {
      ICING_LOG(FATAL) << "The suffix cursor and key cursor should diverge "
                          "after finding the common prefix.";
    }

    // Create single-branch children for the common prefix
    // length. After the loop, split_node points to the node that
    // will have more than 1 char.
    int common_len = prev_suffix_cur - prev_suffix;
    for (int i = 0; i < common_len; i++) {
      // Create a single-branch child node.
      ICING_ASSIGN_OR_RETURN(Next * split_next, storage_->AllocNextArray(1));
      split_node->set_next_index(storage_->GetNextArrayIndex(split_next));
      split_node->set_is_leaf(false);
      split_node->set_log2_num_children(0);
      Node *child_node = storage_->AllocNode();
      split_next[0].set_val(*(prev_suffix + i));
      split_next[0].set_node_index(storage_->GetNodeIndex(child_node));

      split_node = child_node;
    }

    // Fill a split.
    ICING_ASSIGN_OR_RETURN(Next * split_next, storage_->AllocNextArray(2));
    split_node->set_next_index(storage_->GetNextArrayIndex(split_next));
    split_node->set_is_leaf(false);
    split_node->set_log2_num_children(1);
    Node *prev_suffix_node = storage_->AllocNode();
    Node *key_node = storage_->AllocNode();
    split_next[0].set_val(*(prev_suffix + common_len));
    split_next[0].set_node_index(storage_->GetNodeIndex(prev_suffix_node));
    if (*(prev_suffix + common_len)) {
      uint32_t next_index =
          storage_->GetSuffixIndex(prev_suffix + common_len) + 1;
      prev_suffix_node->set_next_index(next_index);
    } else {
      uint32_t next_index = storage_->GetSuffixIndex(prev_suffix + common_len);
      prev_suffix_node->set_next_index(next_index);
    }
    prev_suffix_node->set_is_leaf(true);
    prev_suffix_node->set_log2_num_children(0);
    split_next[1].set_val(*(key + key_offset + common_len));
    split_next[1].set_node_index(storage_->GetNodeIndex(key_node));
    if (*(key + key_offset + common_len)) {
      uint32_t next_index = storage_->MakeSuffix(
          key + key_offset + common_len + 1, value, value_index);
      key_node->set_next_index(next_index);
    } else {
      uint32_t next_index = storage_->MakeSuffix(key + key_offset + common_len,
                                                 value, value_index);
      key_node->set_next_index(next_index);
    }
    key_node->set_is_leaf(true);
    key_node->set_log2_num_children(0);

    std::sort(split_next, split_next + 2);
  } else {
    // Insert into intermediate node.
    const Node *best_node = storage_->GetNode(best_node_index);

    // Add our value as a node + suffix.
    Node *new_leaf_node = storage_->AllocNode();
    if (*(key + key_offset)) {
      uint32_t next_index =
          storage_->MakeSuffix(key + key_offset + 1, value, value_index);
      new_leaf_node->set_next_index(next_index);
    } else {
      uint32_t next_index =
          storage_->MakeSuffix(key + key_offset, value, value_index);
      new_leaf_node->set_next_index(next_index);
    }
    new_leaf_node->set_is_leaf(true);
    new_leaf_node->set_log2_num_children(0);

    // Figure out the real length of the existing next array.
    uint32_t next_array_buffer_size = 1u << best_node->log2_num_children();
    Next *cur_next = storage_->GetMutableNextArray(best_node->next_index(),
                                                   next_array_buffer_size);
    int next_len = GetValidNextsSize(cur_next, next_array_buffer_size);
    Next *new_next = cur_next;
    if (next_len == (next_array_buffer_size)) {
      // Allocate a new, larger, array.
      ICING_ASSIGN_OR_RETURN(new_next, storage_->AllocNextArray(next_len + 1));
      memcpy(new_next, cur_next, sizeof(Next) * next_len);
    }

    // Write a link to our new leaf node and sort.
    new_next[next_len].set_val(*(key + key_offset));
    new_next[next_len].set_node_index(storage_->GetNodeIndex(new_leaf_node));
    inplace_merge(new_next, new_next + next_len, new_next + next_len + 1);
    next_len++;

    // If this was new, update the parent node and free the old next
    // array.
    if (new_next != cur_next) {
      Node *mutable_best_node =
          storage_->GetMutableNode(storage_->GetNodeIndex(best_node));
      mutable_best_node->set_next_index(storage_->GetNextArrayIndex(new_next));
      mutable_best_node->set_is_leaf(false);
      uint8_t log2_num_children = mutable_best_node->log2_num_children();

      // 8 == log2(256)
      if (log2_num_children >= 8) {
        return absl_ports::InternalError(
            "Number of children exceeds the max allowed size");
      }

      mutable_best_node->set_log2_num_children(log2_num_children + 1);

      storage_->FreeNextArray(cur_next,
                              mutable_best_node->log2_num_children() - 1);
    }
  }

  // We added a new key.
  storage_->inc_num_keys();

  if (pnew_key) *pnew_key = true;
  return libtextclassifier3::Status::OK;
}

const void *IcingDynamicTrie::GetValueAtIndex(uint32_t value_index) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  return static_cast<const void *>(storage_->GetSuffix(value_index));
}

void IcingDynamicTrie::SetValueAtIndex(uint32_t value_index,
                                       const void *value) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (value_index > storage_->hdr().max_suffixes_size() - value_size()) {
    ICING_LOG(FATAL) << "Value index is out of range";
  }

  memcpy(storage_->GetMutableSuffix(value_index, value_size()), value,
         value_size());
}

bool IcingDynamicTrie::Find(const char *key, void *value,
                            uint32_t *value_index) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  uint32_t best_node_index;
  int key_offset;
  FindBestNode(key, &best_node_index, &key_offset, false);

  const Node *best_node = storage_->GetNode(best_node_index);
  if (key_offset >= 0 && best_node->is_leaf() &&
      !strcmp(key + key_offset, storage_->GetSuffix(best_node->next_index()))) {
    uint32_t vidx = best_node->next_index() +
                    strlen(storage_->GetSuffix(best_node->next_index())) + 1;
    if (value_index) *value_index = vidx;
    if (value) memcpy(value, storage_->GetSuffix(vidx), value_size());
    return true;
  } else {
    return false;
  }
}

IcingDynamicTrie::Iterator::Iterator(const IcingDynamicTrie &trie,
                                     const char *prefix, bool reverse)
    : cur_key_(prefix),
      cur_suffix_(nullptr),
      cur_suffix_len_(0),
      single_leaf_match_(false),
      reverse_(reverse),
      trie_(trie) {
  if (!trie.is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  Reset();
}

void IcingDynamicTrie::Iterator::BranchToLeaf(uint32_t node_index,
                                              BranchType branch_type) {
  // Go down the trie, following the left-most child until we hit a
  // leaf. Push to stack and cur_key nodes and chars as we go.
  // When reverse_ is true, the method will follow the right-most child.
  const Node *node = trie_.storage_->GetNode(node_index);
  while (!node->is_leaf()) {
    const Next *next_start = trie_.storage_->GetNext(node->next_index(), 0);
    int child_idx;
    if (branch_type == BranchType::kRightMost) {
      uint32_t next_array_size = 1u << node->log2_num_children();
      child_idx = trie_.GetValidNextsSize(next_start, next_array_size) - 1;
    } else {
      // node isn't a leaf. So it must have >0 children.
      // 0 is the left-most child.
      child_idx = 0;
    }
    const Next &child_next = next_start[child_idx];
    branch_stack_.push_back(Branch(node_index, child_idx));
    cur_key_.push_back(child_next.val());

    node_index = child_next.node_index();
    node = trie_.storage_->GetNode(node_index);
  }

  // We're at a leaf.
  cur_suffix_ = trie_.storage_->GetSuffix(
      trie_.storage_->GetNode(node_index)->next_index());
  cur_suffix_len_ = strlen(cur_suffix_);
  cur_key_.append(cur_suffix_, cur_suffix_len_);
}

void IcingDynamicTrie::Iterator::Reset() {
  size_t strip_len = branch_stack_.size() + cur_suffix_len_;

  if (cur_key_.size() < strip_len) {
    ICING_LOG(FATAL) << "Key size < visited trie depth + remaining suffix "
                        "size, there're inconsistencies in dynamic trie";
  }

  // Trim back cur_key_ to original prefix.
  cur_key_.resize(cur_key_.size() - strip_len);
  cur_suffix_ = nullptr;
  cur_suffix_len_ = 0;
  single_leaf_match_ = false;
  branch_stack_.clear();

  // Nothing to do with an empty trie.
  if (trie_.storage_->empty()) return;

  // Find node matching prefix.
  uint32_t node_index;
  int key_offset;
  trie_.FindBestNode(cur_key_.c_str(), &node_index, &key_offset, true);

  // Two cases/states:
  //
  // - Found an intermediate node. If we matched all of prefix
  //   (cur_key_), BranchToLeaf.
  //
  // - Found a leaf node, which is the ONLY matching key for this
  //   prefix. Check that suffix matches the prefix. Then we set
  //   single_leaf_match_ = true and apply different logic for
  //   Advance.
  if (key_offset < 0) {
    // A negative key_offset indicates that trie_.storage_ is empty
    ICING_LOG(FATAL) << "Trie storage is empty";
  }

  const Node *best_node = trie_.storage_->GetNode(node_index);
  if (best_node->is_leaf() &&
      !strncmp(cur_key_.c_str() + key_offset,
               trie_.storage_->GetSuffix(best_node->next_index()),
               cur_key_.size() - key_offset)) {
    // Copy the entire suffix into the current key.
    cur_key_.resize(key_offset);
    cur_key_.append(trie_.storage_->GetSuffix(best_node->next_index()));
    cur_suffix_ = trie_.storage_->GetSuffix(best_node->next_index());
    cur_suffix_len_ = strlen(cur_suffix_);
    single_leaf_match_ = true;
  } else if (static_cast<size_t>(key_offset) == cur_key_.size()) {
    BranchType branch_type =
        (reverse_) ? BranchType::kRightMost : BranchType::kLeftMost;
    BranchToLeaf(node_index, branch_type);
  }
}

bool IcingDynamicTrie::Iterator::Advance() {
  if (!IsValid()) return false;
  if (single_leaf_match_) {
    // If we only have an exact match, the Advance logic does not
    // apply. Invalidate the iterator and return.
    cur_suffix_ = nullptr;
    cur_suffix_len_ = 0;
    return false;
  }

  if (cur_key_.size() < (branch_stack_.size() + cur_suffix_len_)) {
    ICING_LOG(FATAL) << "Key size < visited trie depth + remaining suffix "
                        "size, there're inconsistencies in dynamic trie";
  }

  // Move up from the current leaf.
  cur_key_.resize(cur_key_.size() - cur_suffix_len_);
  cur_suffix_ = nullptr;
  cur_suffix_len_ = 0;

  while (!branch_stack_.empty()) {
    Branch *branch = &branch_stack_.back();
    const Node *node = trie_.storage_->GetNode(branch->node_idx);
    if (reverse_) {
      branch->child_idx--;
    } else {
      branch->child_idx++;
    }
    if (branch->child_idx >= 0 &&
        branch->child_idx < (1 << node->log2_num_children())) {
      const Next *child_next =
          trie_.storage_->GetNext(node->next_index(), branch->child_idx);
      if (child_next->node_index() != kInvalidNodeIndex) {
        // Successfully incremented to the next child. Update the char
        // value at this depth.
        cur_key_[cur_key_.size() - 1] = child_next->val();
        // We successfully found a sub-trie to explore.
        BranchType branch_type =
            (reverse_) ? BranchType::kRightMost : BranchType::kLeftMost;
        BranchToLeaf(child_next->node_index(), branch_type);
        return true;
      }
    }
    branch_stack_.pop_back();
    cur_key_.resize(cur_key_.size() - 1);
  }

  // Un-wound the entire stack. We are done.
  return false;
}

bool IcingDynamicTrie::Iterator::IsValid() const {
  return cur_suffix_ != nullptr;
}

const char *IcingDynamicTrie::Iterator::GetKey() const {
  // cur_key_ can have a NULL in it so cur_key_ can be wrong but
  // cur_key_.c_str() is always right.
  return IsValid() ? cur_key_.c_str() : nullptr;
}

const void *IcingDynamicTrie::Iterator::GetValue() const {
  if (!IsValid()) return nullptr;

  return static_cast<const void *>(cur_suffix_ + cur_suffix_len_ + 1);
}

uint32_t IcingDynamicTrie::Iterator::GetValueIndex() const {
  if (!IsValid()) return kInvalidSuffixIndex;

  return trie_.storage_->GetSuffixIndex(cur_suffix_ + cur_suffix_len_ + 1);
}

void IcingDynamicTrie::Utf8Iterator::LeftBranchToUtf8End() {
  if (cur_len_ <= 0) {
    ICING_LOG(FATAL) << "Invalid UTF-8 character length";
  }

  if (branch_end_ - branch_stack_ != cur_len_) {
    ICING_LOG(FATAL) << "Depth from first visited node to last visited node "
                        "doesn't match the current UTF-8 character length";
  }

  // Use branch at top of stack to determine where to follow.
  const Branch &branch = *(branch_end_ - 1);
  const Node *node = trie_.storage_->GetNode(branch.child->node_index());

  // If we start with non-ascii, take all left branches while there is
  // a continuation byte.
  if (!i18n_utils::IsAscii(cur_[cur_len_ - 1])) {
    while (!node->is_leaf()) {
      if (cur_len_ >= U8_MAX_LENGTH) break;

      InitBranch(branch_end_, node, 0);
      // When we are looking to complete a utf8 char, skip 0s.
      if (branch_end_->child->val() == 0) {
        // Check if we already have a valid cur_.
        cur_[cur_len_] = 0;
        UChar32 uchar32 = i18n_utils::GetUChar32At(cur_, cur_len_, 0);
        if (uchar32 == i18n_utils::kInvalidUChar32 &&
            node->log2_num_children() > 0) {
          branch_end_->child++;
        } else {
          // Good termination. Just break.
          break;
        }
      }

      if (!IcingStringUtil::IsContinuationByte(branch_end_->child->val()))
        break;

      cur_[cur_len_++] = branch_end_->child->val();
      node = trie_.storage_->GetNode(branch_end_->child->node_index());
      branch_end_++;
    }

    cur_logical_node_.node = node;

    // Maybe go into suffixes and set suffix_offset.
    if (node->is_leaf()) {
      GoIntoSuffix(node);
    } else {
      cur_logical_node_.suffix_offset = 0;
    }
  } else {  // ascii
    cur_logical_node_.node = node;
    cur_logical_node_.suffix_offset = 0;
  }

  // NULL-terminate.
  cur_[cur_len_] = 0;
}

void IcingDynamicTrie::Utf8Iterator::GoIntoSuffix(const Node *node) {
  const char *suffix = trie_.storage_->GetSuffix(node->next_index());
  const char *cur_suffix;
  for (cur_suffix = suffix; cur_len_ < U8_MAX_LENGTH &&
                            IcingStringUtil::IsContinuationByte(*cur_suffix);
       cur_suffix++) {
    cur_[cur_len_++] = *cur_suffix;
  }
  cur_logical_node_.suffix_offset = cur_suffix - suffix;
}

void IcingDynamicTrie::Utf8Iterator::Reset() {
  cur_[0] = 0;
  cur_len_ = 0;
  branch_end_ = branch_stack_;

  if (start_node_) {
    // Take the first char node's children.
    const Next *next = trie_.storage_->GetNext(start_node_->next_index(), 0);
    branch_end_->node = start_node_;
    branch_end_->child_end = next + (1 << start_node_->log2_num_children());
    if (next->val() == 0) {
      // Skip any nulls at this position. We don't return empty string
      // as an iteration.
      next++;
    }
    branch_end_->child = next;
    cur_[cur_len_++] = next->val();
    branch_end_++;

    // Will NULL-terminate cur_.
    LeftBranchToUtf8End();
  } else {
    // Nothing to return.
    cur_logical_node_.node = nullptr;
    cur_logical_node_.suffix_offset = 0;
  }
}

bool IcingDynamicTrie::Utf8Iterator::Advance() {
  if (!IsValid()) return false;

  // Clip to branch.
  cur_len_ = branch_end_ - branch_stack_;

  while (branch_end_ > branch_stack_) {
    Branch *branch = branch_end_ - 1;
    branch->child++;
    if (!branch->IsFinished()) {
      // Successfully incremented to the next child. Update the char
      // value at this depth.
      cur_[cur_len_ - 1] = branch->child->val();

      // We successfully found a sub-trie to explore.
      LeftBranchToUtf8End();
      return true;
    }
    cur_len_--;
    branch_end_--;
  }

  // Un-wound the entire stack. We are done.
  return false;
}

void IcingDynamicTrie::Utf8Iterator::InitBranch(Branch *branch,
                                                const Node *start,
                                                char key_char) {
  branch->node = start;
  branch->child = trie_.storage_->GetNext(start->next_index(), 0);
  branch->child_end = branch->child + (1 << start->log2_num_children());
  if (key_char) {
    branch->child =
        trie_.LowerBound(branch->child, branch->child_end, key_char);
  }
}

bool IcingDynamicTrie::Utf8Iterator::Branch::IsFinished() {
  return child >= child_end || child->node_index() == kInvalidNodeIndex;
}

bool IcingDynamicTrie::Utf8Iterator::IsValid() const { return cur_len_ > 0; }

const IcingDynamicTrie::Next *IcingDynamicTrie::GetNextByChar(
    const Node *node, uint8_t key_char) const {
  const Next *next_start = storage_->GetNext(node->next_index(), 0);
  const Next *next_end = next_start + (1 << node->log2_num_children());

  const Next *found = LowerBound(next_start, next_end, key_char);
  if (found >= next_end || found->val() != key_char ||
      found->node_index() == kInvalidNodeIndex) {
    return nullptr;
  }

  return found;
}

int IcingDynamicTrie::GetValidNextsSize(
    const IcingDynamicTrie::Next *next_array_start,
    int next_array_length) const {
  // Only searching for key char 0xff is not sufficient, as 0xff can be a valid
  // character. We must also specify kInvalidNodeIndex as the target node index
  // when searching the next array.
  return LowerBound(next_array_start, next_array_start + next_array_length,
                    /*key_char=*/0xff, /*node_index=*/kInvalidNodeIndex) -
         next_array_start;
}

const IcingDynamicTrie::Next *IcingDynamicTrie::LowerBound(
    const Next *start, const Next *end, uint8_t key_char,
    uint32_t node_index) const {
  // Above this value will use binary search instead of linear
  // search. 16 was chosen from running some benchmarks with
  // different values.
  static const uint32_t kBinarySearchCutoff = 16;

  Next key_next(key_char, node_index);
  if (end - start >= kBinarySearchCutoff) {
    // Binary search.
    return lower_bound(start, end, key_next);
  } else {
    // Linear search.
    const Next *found;
    for (found = start; found < end; found++) {
      if (!(*found < key_next)) {
        // Should have gotten match.
        break;
      }
    }
    return found;
  }
}

void IcingDynamicTrie::FindBestNode(const char *key, uint32_t *best_node_index,
                                    int *key_offset, bool prefix,
                                    bool utf8) const {
  // Find the best node such that:
  //
  // - If key is NOT in the trie, key[0..key_offset) is a prefix to
  //   everything under best_node_index.
  //
  // - If key is in the trie, best_node_index is the leaf that points
  //   to the key suffix and key_offset == strlen(key).
  //
  // If prefix is true, when key is both in the trie AND a prefix
  // (e.g. "ab" and "abc" are in the trie), we return the intermediate
  // node with key as the prefix as opposed to the exactly matching
  // leaf node.
  if (storage_->empty()) {
    *best_node_index = 0;
    *key_offset = -1;
    return;
  }

  const Node *cur_node = storage_->GetRootNode();
  const char *cur_key = key;
  const Node *utf8_node = cur_node;
  const char *utf8_key = cur_key;
  while (!cur_node->is_leaf()) {
    const Next *found = GetNextByChar(cur_node, *cur_key);
    if (!found) break;

    if (prefix && found->val() == 0) {
      break;
    }

    cur_node = storage_->GetNode(found->node_index());

    // End of key.
    if (*cur_key == 0) {
      break;
    }
    cur_key++;

    if (utf8 && i18n_utils::IsLeadUtf8Byte(*cur_key)) {
      utf8_node = cur_node;
      utf8_key = cur_key;
    }
  }

  if (utf8) {
    // Rewind.
    cur_node = utf8_node;
    cur_key = utf8_key;
  }

  *best_node_index = storage_->GetNodeIndex(cur_node);
  *key_offset = reinterpret_cast<const char *>(cur_key) - key;
}

int IcingDynamicTrie::FindNewBranchingPrefixLength(const char *key,
                                                   bool utf8) const {
  if (storage_->empty()) {
    return kNoBranchFound;
  }

  uint32_t best_node_index;
  int key_offset;
  FindBestNode(key, &best_node_index, &key_offset, /*prefix=*/true, utf8);
  const Node *cur_node = storage_->GetNode(best_node_index);
  const char *cur_key = key + key_offset;
  if (cur_node->is_leaf()) {
    // Prefix in the trie. Split at leaf.
    const char *prev_suffix = storage_->GetSuffix(cur_node->next_index());
    while (*prev_suffix != '\0' && *prev_suffix == *cur_key) {
      prev_suffix++;
      cur_key++;
    }

    // Equal strings? No branching.
    if (*prev_suffix == '\0' && *cur_key == '\0') {
      return kNoBranchFound;
    }

    if (utf8) {
      // Rewind to utf8 boundary.
      size_t offset = i18n_utils::SafeTruncateUtf8Length(key, cur_key - key);
      cur_key = key + offset;
    }

    return cur_key - key;
  } else if (cur_node->log2_num_children() == 0) {
    // Intermediate node going from no branching to branching.
    return cur_key - key;
  }

  // If we've reached this point, then we're already at a branch point. So there
  // is no *new* branch point.
  return kNoBranchFound;
}

std::vector<int> IcingDynamicTrie::FindBranchingPrefixLengths(const char *key,
                                                              bool utf8) const {
  std::vector<int> prefix_lengths;

  if (storage_->empty()) {
    return prefix_lengths;
  }

  const Node *cur_node = storage_->GetRootNode();
  const char *cur_key = key;
  while (*cur_key && !cur_node->is_leaf()) {
    // Branching prefix?
    if (cur_node->log2_num_children() > 0) {
      int len = cur_key - key;
      if (utf8) {
        // Do not cut mid-utf8. Walk up to utf8 boundary.
        len = i18n_utils::SafeTruncateUtf8Length(key, len);
        if (prefix_lengths.empty() || len != prefix_lengths.back()) {
          prefix_lengths.push_back(len);
        }
      } else {
        prefix_lengths.push_back(len);
      }
    }

    // Move to next.
    const Next *found = GetNextByChar(cur_node, *cur_key);
    if (found == nullptr) {
      break;
    }
    cur_node = storage_->GetNode(found->node_index());

    ++cur_key;
  }
  return prefix_lengths;
}

bool IcingDynamicTrie::IsBranchingTerm(const char *key) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (storage_->empty()) {
    return false;
  }

  uint32_t best_node_index;
  int key_offset;
  FindBestNode(key, &best_node_index, &key_offset, /*prefix=*/true);
  const Node *cur_node = storage_->GetNode(best_node_index);

  if (cur_node->is_leaf()) {
    return false;
  }

  // There is no intermediate node for key in the trie.
  if (key[key_offset] != '\0') {
    return false;
  }

  // Found key as an intermediate node, but key is not a valid term stored in
  // the trie. In this case, we need at least two children for key to be a
  // branching term.
  if (GetNextByChar(cur_node, '\0') == nullptr) {
    return cur_node->log2_num_children() >= 1;
  }

  // The intermediate node for key must have more than two children for key to
  // be a branching term, one of which represents the leaf node for key itself.
  return cur_node->log2_num_children() > 1;
}

void IcingDynamicTrie::GetDebugInfo(int verbosity, std::string *out) const {
  Stats stats;
  CollectStats(&stats);
  out->append(stats.DumpStats(verbosity));

  // Property files.
  vector<std::string> files;
  if (!filesystem_->GetMatchingFiles((property_bitmaps_prefix_ + "*").c_str(),
                                     &files)) {
    ICING_LOG(ERROR) << "Could not get files at prefix " << property_bitmaps_prefix_;
    return;
  }
  for (size_t i = 0; i < files.size(); i++) {
    IcingStringUtil::SStringAppendF(
        out, 1000, "Prop file %s size %" PRIu64 "\n",
        filesystem_->GetBasename(files[i].c_str()).c_str(),
        filesystem_->GetFileSize(files[i].c_str()));
  }
  IcingStringUtil::SStringAppendF(
      out, 1000, "Deleted file %s size %" PRIu64 "\n",
      filesystem_->GetBasename(deleted_bitmap_filename_.c_str()).c_str(),
      filesystem_->GetFileSize(deleted_bitmap_filename_.c_str()));
}

double IcingDynamicTrie::min_free_fraction() const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  return 1.0 - max(max(static_cast<double>(storage_->hdr().num_nodes()) /
                           storage_->hdr().max_nodes(),
                       static_cast<double>(storage_->hdr().num_nexts()) /
                           storage_->hdr().max_nexts()),
                   static_cast<double>(storage_->hdr().suffixes_size()) /
                       storage_->hdr().max_suffixes_size());
}

uint32_t IcingDynamicTrie::value_size() const {
  return storage_->hdr().value_size();
}

uint32_t IcingDynamicTrie::max_value_index() const {
  return storage_->hdr().max_suffixes_size();
}

uint32_t IcingDynamicTrie::UpdateCrc() {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (runtime_options_.storage_policy != RuntimeOptions::kMapSharedWithCrc) {
    return kNoCrc;
  }

  // Combine storage crc with property bitmap crcs.
  uint32_t crc = storage_->UpdateCrc();

  // Update crcs on bitmaps.
  for (size_t i = 0; i < property_bitmaps_.size(); ++i) {
    if (property_bitmaps_[i]) {
      // Combine property id with the bitmap crc.
      uint64_t this_crc = property_bitmaps_[i]->UpdateCrc();
      this_crc = (this_crc << 32) | i;
      crc = IcingStringUtil::UpdateCrc32(
          crc, reinterpret_cast<const char *>(&this_crc), sizeof(this_crc));
    }
  }
  uint32_t this_crc = deleted_bitmap_->UpdateCrc();
  crc = IcingStringUtil::UpdateCrc32(
      crc, reinterpret_cast<const char *>(&this_crc), sizeof(this_crc));

  return crc;
}

IcingFlashBitmap *IcingDynamicTrie::OpenOrCreatePropertyBitmap(
    uint32_t property_id) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (property_id > kMaxPropertyId) {
    ICING_LOG(ERROR) << "Property id " << property_id << " out of range";
    return nullptr;
  }

  if (property_id >= property_bitmaps_.size()) {
    property_bitmaps_.resize(property_id + 1);
  }
  if (!property_bitmaps_[property_id]) {
    std::string filename;
    IcingStringUtil::SStringAppendF(
        &filename, property_bitmaps_prefix_.size() + 10, "%s%u",
        property_bitmaps_prefix_.c_str(), property_id);
    property_bitmaps_[property_id] =
        OpenAndInitBitmap(filename, false, filesystem_);
  }
  return property_bitmaps_[property_id].get();
}

bool IcingDynamicTrie::SetProperty(uint32_t value_index, uint32_t property_id) {
  IcingFlashBitmap *bitmap = OpenOrCreatePropertyBitmap(property_id);
  if (!bitmap) {
    return false;
  }
  uint64_t idx = ValueIndexToPropertyBitmapIndex(value_index);

  // Also clear deleted bit.
  return bitmap->SetBit(idx, true) && deleted_bitmap_->SetBit(idx, false);
}

bool IcingDynamicTrie::ClearProperty(uint32_t value_index,
                                     uint32_t property_id) {
  if (property_id >= property_bitmaps_.size() ||
      !property_bitmaps_[property_id]) {
    // No bitmap is ok for clearing.
    return true;
  }

  uint64_t idx = ValueIndexToPropertyBitmapIndex(value_index);
  return property_bitmaps_[property_id]->SetBit(idx, false);
}

bool IcingDynamicTrie::SetDeleted(uint32_t value_index) {
  uint64_t idx = ValueIndexToPropertyBitmapIndex(value_index);
  return deleted_bitmap_->SetBit(idx, true);
}

bool IcingDynamicTrie::ClearDeleted(uint32_t value_index) {
  uint64_t idx = ValueIndexToPropertyBitmapIndex(value_index);
  return deleted_bitmap_->SetBit(idx, false);
}

// Steps:
// 1. Find the key in the trie.
// 2. Remove the suffix and the value.
// 3. Reset the nexts that point to the nodes to be removed.
// 4. Sort any next array if needed.
bool IcingDynamicTrie::Delete(const std::string_view key) {
  if (!is_initialized()) {
    ICING_LOG(ERROR) << "DynamicTrie not initialized";
    return false;
  }

  if (storage_->empty()) {
    // Nothing to delete.
    return true;
  }

  // Tries to find the key in the trie, starting from the root.
  const Node *current_node = storage_->GetRootNode();

  // The node after which we start to remove data.
  const Node *last_multichild_node = nullptr;

  // While visiting the trie nodes, we store the indices of Nexts that point
  // to all the nodes after last_multichild_node. Those nodes must be
  // consecutive and all have only one child. Resetting those Nexts means that
  // we remove the data of the key.
  std::vector<uint32_t> nexts_to_reset;
  nexts_to_reset.reserve(key.length());

  // Iterates through chars in the key, finds nodes in the trie until a leaf
  // node is reached. The max number of loops is key.length() + 1 because we
  // start from the root.
  for (size_t i = 0; i <= key.length(); ++i) {
    if (current_node->is_leaf()) {
      // Leaf node, now check the suffix.
      if (key.substr(i) != storage_->GetSuffix(current_node->next_index())) {
        // Key does not exist in the trie, nothing to delete.
        return true;
      }
      // Otherwise, key is found.
      break;
    }

    // Finds the next char.
    const Next *next;
    if (i == key.length()) {
      // When we're at the end of the key, the next char is the termination char
      // '\0'.
      next = GetNextByChar(current_node, '\0');
    } else {
      next = GetNextByChar(current_node, key[i]);
    }

    if (next == nullptr) {
      // Key does not exist in the trie, nothing to delete.
      return true;
    }

    // Checks the real size of next array.
    uint32_t next_array_buffer_size = 1u << current_node->log2_num_children();
    Next *next_array_start = storage_->GetMutableNextArray(
        current_node->next_index(), next_array_buffer_size);
    int valid_next_array_size =
        GetValidNextsSize(next_array_start, next_array_buffer_size);
    if (valid_next_array_size == 0) {
      // Key does not exist in the trie, nothing to delete.
      // This shouldn't happen, but we put a sanity check here in case something
      // is wrong.
      return true;
    } else if (valid_next_array_size == 1) {
      // Single-child branch will be deleted.
      nexts_to_reset.push_back(storage_->GetNextArrayIndex(next));
    } else {
      // We see a new node with multiple children, all the previously seen nodes
      // shouldn't be removed.
      last_multichild_node = current_node;
      nexts_to_reset.clear();
      nexts_to_reset.push_back(storage_->GetNextArrayIndex(next));
    }

    // Updates current_node.
    current_node = storage_->GetNode(next->node_index());
  }
  // Now we've found the key in the trie.

  ClearSuffixAndValue(current_node->next_index());

  // Resets nexts to remove key information.
  for (uint32_t next_index : nexts_to_reset) {
    ResetNext(next_index);
  }

  if (last_multichild_node != nullptr) {
    SortNextArray(last_multichild_node);
    uint32_t next_array_buffer_size =
        1u << last_multichild_node->log2_num_children();
    Next *next_array_start = this->storage_->GetMutableNextArray(
        last_multichild_node->next_index(), next_array_buffer_size);
    uint32_t num_children =
        GetValidNextsSize(next_array_start, next_array_buffer_size);
    // Shrink the next array if we can.
    if (num_children == next_array_buffer_size / 2) {
      Node *mutable_node = storage_->GetMutableNode(
          storage_->GetNodeIndex(last_multichild_node));
      mutable_node->set_log2_num_children(mutable_node->log2_num_children() -
                                          1);
      // Add the unused second half of the next array to the free list.
      storage_->FreeNextArray(next_array_start + next_array_buffer_size / 2,
                              mutable_node->log2_num_children());
    }
  }

  return true;
}

bool IcingDynamicTrie::ClearPropertyForAllValues(uint32_t property_id) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  PropertyReadersAll readers(*this);
  if (!readers.Exists(property_id)) {
    ICING_VLOG(1) << "Properties for id " << property_id << " don't exist";
    return true;
  }

  // Mark values that have no other properties set as as deleted.
  uint64_t max_idx =
      ValueIndexToPropertyBitmapIndex(storage_->hdr().suffixes_size());
  // TODO(vishwajith) Inefficient to do this bit by bit, should be word by
  // word. Removing a corpus is likely rare enough that this is low priority.
  for (uint64_t i = 0; i < max_idx; ++i) {
    // See if the bit is set in our property map.
    if (readers.IsPropertyUnique(property_id, i)) {
      deleted_bitmap_->SetBit(i, true);
    }
  }

  // Now delete the bitmap file for this property.
  std::unique_ptr<IcingFlashBitmap> bitmap(
      std::move(property_bitmaps_[property_id]));
  // bitmap cannot be null here, because then readers.Exists(property_id) would
  // have returned false earlier, and we wouldn't get here.
  if (bitmap == nullptr) {
    ICING_LOG(ERROR) << "Property bitmap is null";
    return false;
  }

  return bitmap->Delete();
}

bool IcingDynamicTrie::PropertyReaderBase::Exists() const {
  return bitmap_ != nullptr;
}

bool IcingDynamicTrie::PropertyReaderBase::HasProperty(
    uint32_t value_index) const {
  return bitmap_ &&
         bitmap_->GetBit(trie_.ValueIndexToPropertyBitmapIndex(value_index));
}

IcingDynamicTrie::PropertyReaderBase::PropertyReaderBase(
    const IcingDynamicTrie &trie, bool deleted, uint32_t property_id)
    : trie_(trie) {
  if (!trie.is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  if (deleted) {
    bitmap_ = trie.deleted_bitmap_.get();
  } else if (property_id < trie.property_bitmaps_.size()) {
    bitmap_ = trie.property_bitmaps_[property_id].get();
  } else {
    bitmap_ = nullptr;
  }
}

IcingDynamicTrie::PropertyReadersAll::PropertyReadersAll(
    const IcingDynamicTrie &trie)
    : trie_(trie) {
  if (!trie.is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }
}

bool IcingDynamicTrie::PropertyReadersAll::Exists(uint32_t property_id) const {
  return property_id < trie_.property_bitmaps_.size() &&
         trie_.property_bitmaps_[property_id];
}

bool IcingDynamicTrie::PropertyReadersAll::HasProperty(
    uint32_t property_id, uint32_t value_index) const {
  return property_id < trie_.property_bitmaps_.size() &&
         trie_.property_bitmaps_[property_id] &&
         trie_.property_bitmaps_[property_id]->GetBit(
             trie_.ValueIndexToPropertyBitmapIndex(value_index));
}

bool IcingDynamicTrie::PropertyReadersAll::IsPropertyUnique(
    uint32_t property_id, uint32_t value_index) const {
  uint32_t idx = trie_.ValueIndexToPropertyBitmapIndex(value_index);

  // First check that value is set for the requested id.
  if (property_id >= trie_.property_bitmaps_.size() ||
      !trie_.property_bitmaps_[property_id] ||
      !trie_.property_bitmaps_[property_id]->GetBit(idx)) {
    return false;
  }

  // Now check that the value is not set for the rest.
  for (size_t i = 0; i < trie_.property_bitmaps_.size(); ++i) {
    if (i == property_id) {
      continue;
    }
    if (trie_.property_bitmaps_[i] && trie_.property_bitmaps_[i]->GetBit(idx)) {
      return false;
    }
  }
  return true;
}

size_t IcingDynamicTrie::PropertyReadersAll::size() const {
  return trie_.property_bitmaps_.size();
}

uint64_t IcingDynamicTrie::ValueIndexToPropertyBitmapIndex(
    uint32_t value_index) const {
  // We know that value indices are separated by at least 1 +
  // value_size() bytes (for the null terminator and the value).
  return value_index / (value_size() + 1);
}

// Testing hooks.
void IcingDynamicTrie::GetHeader(IcingDynamicTrieHeader *hdr) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  *hdr = storage_->hdr();
}

void IcingDynamicTrie::SetHeader(const IcingDynamicTrieHeader &new_hdr) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "DynamicTrie not initialized";
  }

  storage_->hdr_.hdr = new_hdr;
  storage_->WriteHeader();
}

}  // namespace lib
}  // namespace icing
