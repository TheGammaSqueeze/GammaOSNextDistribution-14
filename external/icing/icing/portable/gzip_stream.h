// Copyright (C) 2009 Google LLC
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

// This file contains the definition for classes GzipInputStream and
// GzipOutputStream. It is forked from protobuf because these classes are only
// provided in libprotobuf-full but we would like to link libicing against the
// smaller libprotobuf-lite instead.
//
// GzipInputStream decompresses data from an underlying
// ZeroCopyInputStream and provides the decompressed data as a
// ZeroCopyInputStream.
//
// GzipOutputStream is an ZeroCopyOutputStream that compresses data to
// an underlying ZeroCopyOutputStream.

#ifndef GOOGLE3_ICING_PORTABLE_GZIP_STREAM_H_
#define GOOGLE3_ICING_PORTABLE_GZIP_STREAM_H_

#include "icing/portable/zlib.h"
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>

namespace icing {
namespace lib {
namespace protobuf_ports {

// A ZeroCopyInputStream that reads compressed data through zlib
class GzipInputStream : public google::protobuf::io::ZeroCopyInputStream {
 public:
  // Format key for constructor
  enum Format {
    // zlib will autodetect gzip header or deflate stream
    AUTO = 0,

    // GZIP streams have some extra header data for file attributes.
    GZIP = 1,

    // Simpler zlib stream format.
    ZLIB = 2,
  };

  // buffer_size and format may be -1 for default of 64kB and GZIP format
  explicit GzipInputStream(google::protobuf::io::ZeroCopyInputStream* sub_stream,
                           Format format = AUTO, int buffer_size = -1);
  virtual ~GzipInputStream();

  // Return last error message or NULL if no error.
  inline const char* ZlibErrorMessage() const { return zcontext_.msg; }
  inline int ZlibErrorCode() const { return zerror_; }

  // implements ZeroCopyInputStream ----------------------------------
  bool Next(const void** data, int* size) override;
  void BackUp(int count) override;
  bool Skip(int count) override;
  int64_t ByteCount() const override;

 private:
  Format format_;

  google::protobuf::io::ZeroCopyInputStream* sub_stream_;

  z_stream zcontext_;
  int zerror_;

  void* output_buffer_;
  void* output_position_;
  size_t output_buffer_length_;
  int64_t byte_count_;

  int Inflate(int flush);
  void DoNextOutput(const void** data, int* size);
};

class GzipOutputStream : public google::protobuf::io::ZeroCopyOutputStream {
 public:
  // Format key for constructor
  enum Format {
    // GZIP streams have some extra header data for file attributes.
    GZIP = 1,

    // Simpler zlib stream format.
    ZLIB = 2,
  };

  struct Options {
    // Defaults to GZIP.
    Format format;

    // What size buffer to use internally.  Defaults to 64kB.
    int buffer_size;

    // A number between 0 and 9, where 0 is no compression and 9 is best
    // compression.  Defaults to Z_DEFAULT_COMPRESSION (see zlib.h).
    int compression_level;

    // Defaults to Z_DEFAULT_STRATEGY.  Can also be set to Z_FILTERED,
    // Z_HUFFMAN_ONLY, or Z_RLE.  See the documentation for deflateInit2 in
    // zlib.h for definitions of these constants.
    int compression_strategy;

    Options();  // Initializes with default values.
  };

  // Create a GzipOutputStream with default options.
  explicit GzipOutputStream(google::protobuf::io::ZeroCopyOutputStream* sub_stream);

  // Create a GzipOutputStream with the given options.
  GzipOutputStream(google::protobuf::io::ZeroCopyOutputStream* sub_stream,
                   const Options& options);

  virtual ~GzipOutputStream();

  // Return last error message or NULL if no error.
  inline const char* ZlibErrorMessage() const { return zcontext_.msg; }
  inline int ZlibErrorCode() const { return zerror_; }

  // Flushes data written so far to zipped data in the underlying stream.
  // It is the caller's responsibility to flush the underlying stream if
  // necessary.
  // Compression may be less efficient stopping and starting around flushes.
  // Returns true if no error.
  //
  // Please ensure that block size is > 6. Here is an excerpt from the zlib
  // doc that explains why:
  //
  // In the case of a Z_FULL_FLUSH or Z_SYNC_FLUSH, make sure that avail_out
  // is greater than six to avoid repeated flush markers due to
  // avail_out == 0 on return.
  bool Flush();

  // Writes out all data and closes the gzip stream.
  // It is the caller's responsibility to close the underlying stream if
  // necessary.
  // Returns true if no error.
  bool Close();

  // implements ZeroCopyOutputStream ---------------------------------
  bool Next(void** data, int* size) override;
  void BackUp(int count) override;
  int64_t ByteCount() const override;

 private:
  google::protobuf::io::ZeroCopyOutputStream* sub_stream_;
  // Result from calling Next() on sub_stream_
  void* sub_data_;
  int sub_data_size_;

  z_stream zcontext_;
  int zerror_;
  void* input_buffer_;
  size_t input_buffer_length_;

  // Shared constructor code.
  void Init(google::protobuf::io::ZeroCopyOutputStream* sub_stream,
            const Options& options);

  // Do some compression.
  // Takes zlib flush mode.
  // Returns zlib error code.
  int Deflate(int flush);
};

}  // namespace protobuf_ports
}  // namespace lib
}  // namespace icing

#endif  // GOOGLE3_ICING_PORTABLE_GZIP_STREAM_H_
