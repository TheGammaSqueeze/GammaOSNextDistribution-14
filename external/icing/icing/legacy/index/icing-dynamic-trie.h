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

// Copyright 2011 Google Inc. All Rights Reserved.
// Author: ulas@google.com (Ulas Kirazci)
//
// Trie for word prefix lookups. Features:
//
// - Dynamic additions (but not deletions)
// - Low memory usage
// - Reasonable latency but not QPS
// - Revive from persistence is a disk read
// - Stores a 4-byte value associated with every key
//
// Associated with each value in the trie is a set of property ids.  For
// efficiency, property ids should start at 0 and be densely packed.  A value
// may have more than one id set.  There is an additional deleted property
// for each value, which is set only when all the property ids associated with a
// value have been cleared.  In the flash_index, property ids are used to track
// corpus ids.
//
// Not thread-safe.

#ifndef ICING_LEGACY_INDEX_ICING_DYNAMIC_TRIE_H_
#define ICING_LEGACY_INDEX_ICING_DYNAMIC_TRIE_H_

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/legacy/core/icing-compat.h"
#include "icing/legacy/core/icing-packed-pod.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mmapper.h"
#include "icing/legacy/index/icing-storage.h"
#include "icing/legacy/index/proto/icing-dynamic-trie-header.pb.h"
#include "icing/util/i18n-utils.h"
#include "unicode/utf8.h"

namespace icing {
namespace lib {

class IcingFlashBitmap;

class IcingDynamicTrie : public IIcingStorage {
  class Dumper;
  class IcingDynamicTrieStorage;

 public:
  // Adjacent bit fields are usually packed automatically. However, that is
  // implementation specific:
  // http://en.cppreference.com/w/cpp/language/bit_field
  // So we'll set packed to be explicit.
  class Node {
   public:
    // This object is only ever used by an ArrayStorage, which allocates
    // sizeof(Node) bytes, zeroes them out and then casts to a Node.
    Node() = delete;

    uint32_t next_index() const { return next_index_; }
    void set_next_index(uint32_t next_index) { next_index_ = next_index; }

    bool is_leaf() const { return is_leaf_; }
    void set_is_leaf(bool is_leaf) { is_leaf_ = is_leaf; }

    uint8_t log2_num_children() const { return log2_num_children_; }
    void set_log2_num_children(uint8_t log2_num_children) {
      log2_num_children_ = log2_num_children;
    }

   private:
    uint32_t next_index_ : 27;
    uint32_t is_leaf_ : 1;
    uint32_t log2_num_children_ : 4;
  } __attribute__((packed));
  static_assert(sizeof(Node) == 4, "");
  static_assert(icing_is_packed_pod<Node>::value, "go/icing-ubsan");

  // Adjacent bit fields are usually packed automatically. However, that is
  // implementation specific:
  // http://en.cppreference.com/w/cpp/language/bit_field.
  // So we'll set packed to be explicit.
  union Next {
    Next(uint8_t val, uint32_t node_index) {
      used.val = val;
      used.node_index = node_index;
    }

    uint8_t val() const { return used.val; }
    void set_val(uint8_t val) { used.val = val; }

    uint32_t node_index() const { return used.node_index; }
    void set_node_index(uint32_t node_index) { used.node_index = node_index; }

    uint32_t next_index() const { return freelink.next_index; }
    void set_next_index(uint32_t next_index) {
      freelink.next_index = next_index;
    }

    bool operator<(const Next &next2) const {
      if (val() == next2.val()) {
        return node_index() < next2.node_index();
      }
      return val() < next2.val();
    }

   private:
    // This object is only ever used by an ArrayStorage, which allocates
    // sizeof(Node) bytes, zeroes them out and then casts to a Node.
    Next() = default;

    struct {
      uint32_t val : 8;
      uint32_t node_index : 24;
    } used;
    struct {
      uint32_t next_index : 32;
    } freelink;
  } __attribute__((packed));
  static_assert(sizeof(Next) == 4, "");
  static_assert(sizeof(Next) % alignof(Next) == 0, "");
  static_assert(icing_is_packed_pod<Next>::value, "go/icing-ubsan");

  static const int kMaxNextArraySize = 256;
  static const int kNumNextAllocationBuckets = 9;  // [log2(1), log2(256)]

  static const uint32_t kMaxPropertyId = (1 << 16) - 1;

  static const uint32_t kInvalidValueIndex = 0;

  static const uint32_t kNoCrc = 0;

  struct Stats {
    uint32_t num_keys;

    // Node stats

    uint32_t num_nodes;
    uint32_t max_nodes;
    // Count of intermediate nodes.
    uint32_t num_intermediates;
    // Total and maximum number of children of intermediate nodes.
    uint32_t sum_children, max_children;

    // Count of leaf nodes.
    uint32_t num_leaves;
    // Total and maximum depth of leaf nodes.
    uint32_t sum_depth, max_depth;

    // Next stats

    uint32_t num_nexts;
    uint32_t max_nexts;
    // Count of next arrays by size.
    uint32_t child_counts[kMaxNextArraySize];
    // Wasted next array space per allocation bucket (in Nexts, not
    // bytes).
    uint32_t wasted[kNumNextAllocationBuckets];
    // Sum of wasted array.
    uint32_t total_wasted;

    // Suffix stats

    uint32_t suffixes_size;
    uint32_t max_suffixes_size;
    // Bytes actually used by suffixes.
    uint32_t suffixes_used;
    // Number of suffixes that are just empty strings.
    uint32_t null_suffixes;

    // Next free-list stats
    uint32_t num_free[kNumNextAllocationBuckets];
    // Total Next nodes free (weighted sum of the above).
    uint32_t total_free;

    // Dirty pages.
    uint32_t dirty_pages_nodes;
    uint32_t dirty_pages_nexts;
    uint32_t dirty_pages_suffixes;

    // TODO(b/222349894) Convert the string output to a protocol buffer instead.
    std::string DumpStats(int verbosity) const;
  };

  // Options when creating the trie. Maximums for the node/next/suffix
  // arrays must be specified in advance.
  struct Options {
    // Absolute maximums.
    static const uint32_t kMaxNodes, kMaxNexts, kMaxSuffixesSize, kMaxValueSize;

    // The default takes 13MB of memory and can take about 1M English
    // words.
    Options()
        : max_nodes(1U << 20),
          max_nexts(1U << 20),
          max_suffixes_size(5U << 20),
          value_size(sizeof(uint32_t)) {}
    Options(uint32_t max_nodes_in, uint32_t max_nexts_in,
            uint32_t max_suffixes_size_in, uint32_t value_size_in)
        : max_nodes(max_nodes_in),
          max_nexts(max_nexts_in),
          max_suffixes_size(max_suffixes_size_in),
          value_size(value_size_in) {}

    uint32_t max_nodes;
    uint32_t max_nexts;
    uint32_t max_suffixes_size;
    uint32_t value_size;

    // True if options do not exceed absolute maximums.
    bool is_valid() const;
  };

  // These can be supplied during runtime, as opposed to the persisted
  // Options above.
  struct RuntimeOptions {
    enum StoragePolicy {
      // Changes are reflected in the underlying file immediately but
      // more vulnerable to corruption.
      kMapSharedWithCrc,

      // Changes only applied during Flush. Smaller window of
      // vulnerability to corruption.
      kExplicitFlush,
    };

    RuntimeOptions &set_storage_policy(StoragePolicy sp) {
      storage_policy = sp;
      return *this;
    }

    StoragePolicy storage_policy = kExplicitFlush;
  };

  static uint32_t max_value_index(const Options &options) {
    return options.max_suffixes_size;
  }

  // Light-weight constructor. Real work happens in Create or Init.
  IcingDynamicTrie(const std::string &filename_base,
                   const RuntimeOptions &runtime_options,
                   const IcingFilesystem *filesystem);
  ~IcingDynamicTrie() override;

  bool is_initialized() const { return is_initialized_; }

  // Create, but do not Init, a new trie with options if the file does
  // not already exist.
  //
  // Returns true if successfully created all files or files already
  // exist. Does not do a complete sanity check for when files seem to
  // exist. Cleans up files if creation fails midstream.
  bool CreateIfNotExist(const Options &options);

  bool UpgradeTo(int new_version) override { return true; }
  bool Init() override;
  void Close() override;
  bool Remove() override;
  uint64_t GetDiskUsage() const override;

  // Returns the size of the elements held in the trie. This excludes the size
  // of any internal metadata of the trie, e.g. the trie's header.
  uint64_t GetElementsSize() const;

  // REQUIRED: For all functions below is_initialized() == true.

  // Number of keys in trie.
  uint32_t size() const;

  // Collecting stats.
  void CollectStats(Stats *stats) const;

  // Gets all of the contents of the trie for debugging purposes. Note: this
  // stores the entire set of terms in memory.
  //   pretty_print - The tree structure of the trie will be written to this.
  //   keys - All keys in the trie are appended to this vector.
  void DumpTrie(std::ostream *pretty_print,
                std::vector<std::string> *keys) const;

  // Empty out the trie without closing or removing.
  void Clear();

  // Clears the suffix and value at the given index. Returns true on success.
  bool ClearSuffixAndValue(uint32_t suffix_value_index);

  // Resets the next at the given index so that it points to no node.
  // Returns true on success.
  bool ResetNext(uint32_t next_index);

  // Sorts the next array of the node. Returns true on success.
  bool SortNextArray(const Node *node);

  // Sync to disk.
  bool Sync() override;

  // Tell kernel we will access the memory shortly.
  void Warm() const;

  // Potentially about to get nuked.
  void OnSleep() override;

  // Insert value at key. If key already exists and replace == true,
  // replaces old value with value. We take a copy of value.
  //
  // If value_index is not NULL, returns a pointer to value in
  // value_index. This can then be used with SetValueAtIndex
  // below. value_index is not valid past a Clear/Read/Write.
  //
  // REQUIRES: value a buffer of size value_size()
  //
  // Returns:
  //   OK on success
  //   RESOURCE_EXHAUSTED if no disk space is available
  //   INTERNAL_ERROR if there are inconsistencies in the dynamic trie.
  libtextclassifier3::Status Insert(const char *key, const void *value) {
    return Insert(key, value, nullptr, true, nullptr);
  }
  libtextclassifier3::Status Insert(const char *key, const void *value,
                                    uint32_t *value_index, bool replace) {
    return Insert(key, value, value_index, replace, nullptr);
  }
  libtextclassifier3::Status Insert(const char *key, const void *value,
                                    uint32_t *value_index, bool replace,
                                    bool *pnew_key);

  // Get a value returned by Insert value_index. This points to the
  // value in the trie. The pointer is immutable and always valid
  // while the trie is alive.
  const void *GetValueAtIndex(uint32_t value_index) const;

  // Set a value returned by Insert value_index. We take a copy of
  // value.
  //
  // REQUIRES: value a buffer of size value_size()
  void SetValueAtIndex(uint32_t value_index, const void *value);

  // Returns true if key is found and sets value. If value_index is
  // not NULL, returns value_index (see Insert discussion above).
  // If the key is not found, returns false and neither value nor
  // value_index is modified.
  //
  // REQUIRES: value a buffer of size value_size()
  bool Find(const char *key, void *value) const {
    return Find(key, value, nullptr);
  }
  bool Find(const char *key, void *value, uint32_t *value_index) const;

  // Find the input key and all keys that are a variant of the input
  // key according to a variant map. Currently supports
  // transliteration. For example "a" is a variant for "à" or "á" so
  // an "a" in the input key can match those characters in the trie in
  // addition to itself.
  //
  // If prefix is set, also returns any prefix matches (so value_index
  // will be invalid).
  //
  // REQUIRES: all terms in the lexicon to be valid utf8.
  struct OriginalMatch {
    uint32_t value_index;
    std::string orig;

    OriginalMatch() : value_index(kInvalidValueIndex) {}

    bool is_full_match() const { return value_index != kInvalidValueIndex; }
  };

  static constexpr int kNoBranchFound = -1;
  // Return prefix of any new branches created if key were inserted. If utf8 is
  // true, does not cut key mid-utf8. Returns kNoBranchFound if no branches
  // would be created.
  int FindNewBranchingPrefixLength(const char *key, bool utf8) const;

  // Find all prefixes of key where the trie branches. Excludes the key
  // itself. If utf8 is true, does not cut key mid-utf8.
  std::vector<int> FindBranchingPrefixLengths(const char *key, bool utf8) const;

  // Check if key is a branching term.
  //
  // key is a branching term, if and only if there exists terms s1 and s2 in the
  // trie such that key is the maximum common prefix of s1 and s2, but s1 and s2
  // are not prefixes of each other.
  bool IsBranchingTerm(const char *key) const;

  void GetDebugInfo(int verbosity, std::string *out) const override;

  double min_free_fraction() const;

  uint32_t value_size() const;

  uint32_t max_value_index() const;

  // If in kMapSharedWithCrc mode, update crcs and return the master
  // crc, else return kNoCrc. This crc includes both the trie files
  // and property bitmaps.
  uint32_t UpdateCrc();

  // Store dynamic properties for each value.  When a property is added to
  // a value, the deleted flag is cleared for it (if it was previously set).
  bool SetProperty(uint32_t value_index, uint32_t property_id);
  bool ClearProperty(uint32_t value_index, uint32_t property_id);

  // Store deleted property for each value.
  // This method is not the only way the deleted property can be set; the trie
  // may set this property itself during other operations if it can determine a
  // value becomes superfluous.
  bool SetDeleted(uint32_t value_index);

  // Clears the deleted property for each value.
  bool ClearDeleted(uint32_t value_index);

  // Deletes the entry associated with the key. Data can not be recovered after
  // the deletion. Returns true on success.
  bool Delete(std::string_view key);

  // Clear a specific property id from all values.  For each value that has this
  // property cleared, also check to see if it was the only property set;  if
  // so, set the deleted property for the value to indicate it no longer has any
  // properties associated with it.
  bool ClearPropertyForAllValues(uint32_t property_id);

  // Access properties. Usage:
  //
  // IcingDynamicTrie::PropertyReader reader(trie, 10);
  // char value[SIZE];
  // uint32_t value_index;
  // if (trie.Find("abc", value, &value_index) &&
  //     reader.HasProperty(value_index)) {
  //     ...
  // }
  //
  // Readers are valid as long as the underlying trie is open.
  class PropertyReaderBase {
   public:
    // Whether underlying file exists.
    bool Exists() const;

    // Returns false for all values if underlying file is missing.
    bool HasProperty(uint32_t value_index) const;

   protected:
    PropertyReaderBase(const IcingDynamicTrie &trie, bool deleted,
                       uint32_t property_id);

    // Does not own.
    const IcingFlashBitmap *bitmap_;
    const IcingDynamicTrie &trie_;
  };

  // Reader for a given property. It is invalidated when the underlying property
  // is deleted, or the trie is closed.
  class PropertyReader : public PropertyReaderBase {
   public:
    PropertyReader(const IcingDynamicTrie &trie, uint32_t property_id)
        : PropertyReaderBase(trie, false, property_id) {}
  };

  // Reader for the deleted property. It is invalidated when the trie is closed.
  class PropertyDeletedReader : public PropertyReaderBase {
   public:
    explicit PropertyDeletedReader(const IcingDynamicTrie &trie)
        : PropertyReaderBase(trie, true, 0) {}
  };

  // Reader for all properties (but not the deleted one). It is invalidated when
  // the trie is closed.
  class PropertyReadersAll {
   public:
    explicit PropertyReadersAll(const IcingDynamicTrie &trie);

    // Whether underlying file for property_id exists.
    bool Exists(uint32_t property_id) const;

    // Returns false if underlying file or property doesn't exist.
    bool HasProperty(uint32_t property_id, uint32_t value_index) const;

    // Returns true if the value at value_index is set for the only the supplied
    // property_id, and none of the other properties.
    bool IsPropertyUnique(uint32_t property_id, uint32_t value_index) const;

    // For iterating.
    size_t size() const;

   private:
    const IcingDynamicTrie &trie_;
  };

  // Iterate through trie in lexicographic order.
  //
  // Not thread-safe.
  //
  // Change in underlying trie invalidates iterator.
  //
  // TODO(b/241784804): change IcingDynamicTrie::Iterator to follow the common
  //                    iterator pattern in our codebase.
  class Iterator {
   public:
    Iterator(const IcingDynamicTrie &trie, const char *prefix,
             bool reverse = false);
    void Reset();
    bool Advance();

    // If !IsValid(), GetKey() will return NULL and GetValue() will
    // return 0.
    bool IsValid() const;
    const char *GetKey() const;
    // This points directly to the underlying data and is valid while
    // the trie is alive. We keep ownership of the pointer.
    const void *GetValue() const;
    uint32_t GetValueIndex() const;

   private:
    Iterator();
    // Copy is ok.

    enum class BranchType { kLeftMost = 0, kRightMost = 1 };
    // Helper function that takes the left-most or the right-most branch down
    // intermediate nodes to a leaf, based on branch_type.
    void BranchToLeaf(uint32_t node_index, BranchType branch_type);

    std::string cur_key_;
    const char *cur_suffix_;
    int cur_suffix_len_;
    struct Branch {
      uint32_t node_idx;
      int child_idx;

      explicit Branch(uint32_t node_index, int child_index)
          : node_idx(node_index), child_idx(child_index) {}
    };
    std::vector<Branch> branch_stack_;
    bool single_leaf_match_;
    bool reverse_;

    const IcingDynamicTrie &trie_;
  };

  // Represents a non-leaf node or a "virtual" trie node in the suffix
  // region.
  struct LogicalNode {
    const Node *node;
    int suffix_offset;

    LogicalNode() : node(nullptr), suffix_offset(0) {}
    LogicalNode(const Node *node_in, int suffix_offset_in)
        : node(node_in), suffix_offset(suffix_offset_in) {}
  };

  // Iterate over all utf8 chars in the trie anchored at prefix (or
  // node). If trie has invalid utf8 chars, behavior is undefined (but
  // won't crash).
  class Utf8Iterator {
   public:
    void Reset();
    bool Advance();

    bool IsValid() const;

   private:
    struct Branch {
      const Node *node;
      const Next *child;
      const Next *child_end;

      bool IsFinished();
    };

    Utf8Iterator();
    // Copy is ok.

    void LeftBranchToUtf8End();
    void InitBranch(Branch *branch, const Node *start, char key_char);
    void GoIntoSuffix(const Node *node);

    char cur_[U8_MAX_LENGTH + 1];  // NULL-terminated
    int cur_len_;
    LogicalNode cur_logical_node_;

    Branch branch_stack_[U8_MAX_LENGTH];
    Branch *branch_end_;

    const IcingDynamicTrie &trie_;
    const Node *start_node_;
  };

 private:
  class CandidateSet;

  // For testing only.
  friend class IcingDynamicTrieTest_TrieShouldRespectLimits_Test;
  friend class IcingDynamicTrieTest_SyncErrorRecovery_Test;
  friend class IcingDynamicTrieTest_BitmapsClosedWhenInitFails_Test;
  void GetHeader(IcingDynamicTrieHeader *hdr) const;
  void SetHeader(const IcingDynamicTrieHeader &new_hdr);

  static const uint32_t kInvalidSuffixIndex;

  // Stats helpers.
  void CollectStatsRecursive(const Node &node, Stats *stats,
                             uint32_t depth = 0) const;

  // Helpers for Find and Insert.
  const Next *GetNextByChar(const Node *node, uint8_t key_char) const;
  const Next *LowerBound(const Next *start, const Next *end, uint8_t key_char,
                         uint32_t node_index = 0) const;
  // Returns the number of valid nexts in the array.
  int GetValidNextsSize(const IcingDynamicTrie::Next *next_array_start,
                        int next_array_length) const;
  void FindBestNode(const char *key, uint32_t *best_node_index, int *key_offset,
                    bool prefix, bool utf8 = false) const;

  // For value properties.  This truncates the data by clearing it, but leaving
  // the storage intact.
  bool InitPropertyBitmaps();

  // Returns a pointer to a bitmap that is successfully opened.
  static std::unique_ptr<IcingFlashBitmap> OpenAndInitBitmap(
      const std::string &filename, bool verify,
      const IcingFilesystem *filesystem);

  // Returns a pointer to a writable bitmap, creating it if necessary.  Returned
  // pointer should not be freed, it will be maintained by property_bitmaps_.
  // Returns null if bitmap failed to load.
  IcingFlashBitmap *OpenOrCreatePropertyBitmap(uint32_t property_id);

  uint64_t ValueIndexToPropertyBitmapIndex(uint32_t value_index) const;

  const std::string filename_base_;
  bool is_initialized_;
  const RuntimeOptions runtime_options_;
  std::unique_ptr<IcingDynamicTrieStorage> storage_;
  const std::string property_bitmaps_prefix_;
  std::vector<std::unique_ptr<IcingFlashBitmap>> property_bitmaps_;
  const std::string deleted_bitmap_filename_;
  std::unique_ptr<IcingFlashBitmap> deleted_bitmap_;
  const IcingFilesystem *const filesystem_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_DYNAMIC_TRIE_H_
