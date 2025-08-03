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

#ifndef ICING_LEGACY_INDEX_ICING_LITE_INDEX_HEADER_H_
#define ICING_LEGACY_INDEX_ICING_LITE_INDEX_HEADER_H_

#include <cstddef>
#include <cstdint>

#include "icing/legacy/core/icing-string-util.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// A wrapper around the actual mmapped header data.
class LiteIndex_Header {
 public:
  virtual ~LiteIndex_Header() = default;

  // Returns true if the magic of the header matches the hard-coded magic
  // value associated with this header format.
  virtual bool check_magic() const = 0;

  virtual uint32_t lite_index_crc() const = 0;
  virtual void set_lite_index_crc(uint32_t crc) = 0;

  virtual uint32_t last_added_docid() const = 0;
  virtual void set_last_added_docid(uint32_t last_added_docid) = 0;

  virtual uint32_t cur_size() const = 0;
  virtual void set_cur_size(uint32_t cur_size) = 0;

  virtual uint32_t searchable_end() const = 0;
  virtual void set_searchable_end(uint32_t searchable_end) = 0;

  virtual uint32_t CalculateHeaderCrc() const = 0;

  virtual void Reset() = 0;
};

class LiteIndex_HeaderImpl : public LiteIndex_Header {
 public:
  struct HeaderData {
    static const uint32_t kMagic = 0x01c61418;

    uint32_t lite_index_crc;
    uint32_t magic;
    // This field is available to be reclaimed for another purpose without
    // forcing a change in header size. NOTE: claiming this fields doesn't
    // guarantee that the newly claimed field will have the proper value. If you
    // are depending on the value of this field then you will have to have a
    // migration - either a one-time event during Upgrade() or Init() or
    // determined by a flag change in Init().
    uint32_t padding;
    uint32_t last_added_docid;
    uint32_t cur_size;
    uint32_t searchable_end;
  };

  explicit LiteIndex_HeaderImpl(HeaderData *hdr) : hdr_(hdr) {}

  bool check_magic() const override {
    return hdr_->magic == HeaderData::kMagic;
  }

  uint32_t lite_index_crc() const override { return hdr_->lite_index_crc; }
  void set_lite_index_crc(uint32_t crc) override { hdr_->lite_index_crc = crc; }

  uint32_t last_added_docid() const override { return hdr_->last_added_docid; }
  void set_last_added_docid(uint32_t last_added_docid) override {
    hdr_->last_added_docid = last_added_docid;
  }

  uint32_t cur_size() const override { return hdr_->cur_size; }
  void set_cur_size(uint32_t cur_size) override { hdr_->cur_size = cur_size; }

  uint32_t searchable_end() const override { return hdr_->searchable_end; }
  void set_searchable_end(uint32_t searchable_end) override {
    hdr_->searchable_end = searchable_end;
  }

  uint32_t CalculateHeaderCrc() const override {
    return IcingStringUtil::UpdateCrc32(
        0, reinterpret_cast<const char *>(hdr_) + offsetof(HeaderData, magic),
        sizeof(HeaderData) - offsetof(HeaderData, magic));
  }

  void Reset() override {
    hdr_->lite_index_crc = 0;
    hdr_->magic = HeaderData::kMagic;
    hdr_->last_added_docid = kInvalidDocumentId;
    hdr_->cur_size = 0;
    hdr_->searchable_end = 0;
  }

 private:
  HeaderData *hdr_;
};
static_assert(24 == sizeof(LiteIndex_HeaderImpl::HeaderData),
              "sizeof(HeaderData) != 24");

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_LITE_INDEX_HEADER_H_
