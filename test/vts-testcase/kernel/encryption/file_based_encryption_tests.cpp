/*
 * Copyright (C) 2020 The Android Open Source Project
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

//
// Test that file contents encryption is working, via:
//
// - Correctness tests.  These test the standard FBE settings supported by
//   Android R and higher.
//
// - Randomness test.  This runs on all devices that use FBE, even old ones.
//
// The correctness tests cover the following settings:
//
//    fileencryption=aes-256-xts:aes-256-cts:v2
//    fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized
//    fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized+wrappedkey_v0
//    fileencryption=aes-256-xts:aes-256-cts:v2+emmc_optimized
//    fileencryption=aes-256-xts:aes-256-cts:v2+emmc_optimized+wrappedkey_v0
//    fileencryption=adiantum:adiantum:v2
//
// On devices launching with R or higher those are equivalent to simply:
//
//    fileencryption=
//    fileencryption=::inlinecrypt_optimized
//    fileencryption=::inlinecrypt_optimized+wrappedkey_v0
//    fileencryption=::emmc_optimized
//    fileencryption=::emmc_optimized+wrappedkey_v0
//    fileencryption=adiantum
//
// The tests don't check which one of those settings, if any, the device is
// actually using; they just try to test everything they can.
// "fileencryption=aes-256-xts" is guaranteed to be available if the kernel
// supports any "fscrypt v2" features at all.  The others may not be available,
// so the tests take that into account and skip testing them when unavailable.
//
// None of these tests should ever fail.  In particular, vendors must not break
// any standard FBE settings, regardless of what the device actually uses.  If
// any test fails, make sure to check things like the byte order of keys.
//

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <asm/byteorder.h>
#include <errno.h>
#include <fcntl.h>
#include <gtest/gtest.h>
#include <limits.h>
#include <linux/f2fs.h>
#include <linux/fiemap.h>
#include <linux/fs.h>
#include <linux/fscrypt.h>
#include <lz4.h>
#include <openssl/evp.h>
#include <openssl/hkdf.h>
#include <openssl/siphash.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <chrono>
#include <thread>

#include "vts_kernel_encryption.h"

/* These values are missing from <linux/f2fs.h> */
enum f2fs_compress_algorithm {
  F2FS_COMPRESS_LZO,
  F2FS_COMPRESS_LZ4,
  F2FS_COMPRESS_ZSTD,
  F2FS_COMPRESS_LZORLE,
  F2FS_COMPRESS_MAX,
};

namespace android {
namespace kernel {

// The main mountpoint of the filesystem the test will use to test FBE.
constexpr const char *kTestMountpoint = "/data";

// A directory on the kTestMountpoint filesystem that doesn't already have an
// encryption policy, and therefore allows the creation of subdirectories with
// custom encryption policies.
constexpr const char *kUnencryptedDir = "/data/unencrypted";

// A directory on the kTestMountpoint filesystem that already has an encryption
// policy.  Any files created in this directory will be encrypted using the
// encryption settings that Android is configured to use.
constexpr const char *kTmpDir = "/data/local/tmp";

// Assumed size of filesystem blocks, in bytes
constexpr int kFilesystemBlockSize = 4096;

// Size of the test file in filesystem blocks
constexpr int kTestFileBlocks = 256;

// Size of the test file in bytes
constexpr int kTestFileBytes = kFilesystemBlockSize * kTestFileBlocks;

// fscrypt master key size in bytes
constexpr int kFscryptMasterKeySize = 64;

// fscrypt maximum IV size in bytes
constexpr int kFscryptMaxIVSize = 32;

// fscrypt per-file nonce size in bytes
constexpr int kFscryptFileNonceSize = 16;

// fscrypt HKDF context bytes, from kernel fs/crypto/fscrypt_private.h
enum FscryptHkdfContext {
  HKDF_CONTEXT_KEY_IDENTIFIER = 1,
  HKDF_CONTEXT_PER_FILE_ENC_KEY = 2,
  HKDF_CONTEXT_DIRECT_KEY = 3,
  HKDF_CONTEXT_IV_INO_LBLK_64_KEY = 4,
  HKDF_CONTEXT_DIRHASH_KEY = 5,
  HKDF_CONTEXT_IV_INO_LBLK_32_KEY = 6,
  HKDF_CONTEXT_INODE_HASH_KEY = 7,
};

struct FscryptFileNonce {
  uint8_t bytes[kFscryptFileNonceSize];
};

// Format of the initialization vector
union FscryptIV {
  struct {
    __le32 lblk_num;      // file logical block number, starts at 0
    __le32 inode_number;  // only used for IV_INO_LBLK_64
    uint8_t file_nonce[kFscryptFileNonceSize];  // only used for DIRECT_KEY
  };
  uint8_t bytes[kFscryptMaxIVSize];
};

struct TestFileInfo {
  std::vector<uint8_t> plaintext;
  std::vector<uint8_t> actual_ciphertext;
  uint64_t inode_number;
  FscryptFileNonce nonce;
};

static bool GetInodeNumber(const std::string &path, uint64_t *inode_number) {
  struct stat stbuf;
  if (stat(path.c_str(), &stbuf) != 0) {
    ADD_FAILURE() << "Failed to stat " << path << Errno();
    return false;
  }
  *inode_number = stbuf.st_ino;
  return true;
}

//
// Checks whether the kernel has support for the following fscrypt features:
//
// - Filesystem-level keyring (FS_IOC_ADD_ENCRYPTION_KEY and
//   FS_IOC_REMOVE_ENCRYPTION_KEY)
// - v2 encryption policies
// - The IV_INO_LBLK_64 encryption policy flag
// - The FS_IOC_GET_ENCRYPTION_NONCE ioctl
// - The IV_INO_LBLK_32 encryption policy flag
//
// To do this it's sufficient to just check whether FS_IOC_ADD_ENCRYPTION_KEY is
// available, as the other features were added in the same AOSP release.
//
// The easiest way to do this is to just execute the ioctl with a NULL argument.
// If available it will fail with EFAULT; otherwise it will fail with ENOTTY (or
// EOPNOTSUPP if encryption isn't enabled on the filesystem; that happens on old
// devices that aren't using FBE and are upgraded to a new kernel).
//
static bool IsFscryptV2Supported(const std::string &mountpoint) {
  android::base::unique_fd fd(
      open(mountpoint.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC));
  if (fd < 0) {
    ADD_FAILURE() << "Failed to open " << mountpoint << Errno();
    return false;
  }

  if (ioctl(fd, FS_IOC_ADD_ENCRYPTION_KEY, nullptr) == 0) {
    ADD_FAILURE()
        << "FS_IOC_ADD_ENCRYPTION_KEY(nullptr) unexpectedly succeeded on "
        << mountpoint;
    return false;
  }
  switch (errno) {
    case EFAULT:
      return true;
    case EOPNOTSUPP:
    case ENOTTY:
      GTEST_LOG_(INFO) << "No support for FS_IOC_ADD_ENCRYPTION_KEY on "
                       << mountpoint;
      return false;
    default:
      ADD_FAILURE()
          << "Unexpected error from FS_IOC_ADD_ENCRYPTION_KEY(nullptr) on "
          << mountpoint << Errno();
      return false;
  }
}

// Helper class to freeze / unfreeze a filesystem, to prevent the filesystem
// from moving the file's blocks while the test is accessing them via the
// underlying device.  ext4 doesn't need this, but f2fs does because f2fs does
// background garbage collection.  We cannot use F2FS_IOC_SET_PIN_FILE because
// F2FS_IOC_SET_PIN_FILE doesn't support compressed files.
//
// The fd given can be any fd to a file or directory on the filesystem.
// FIFREEZE operates on the whole filesystem, not on the individual file given.
class ScopedFsFreezer {
 public:
  explicit ScopedFsFreezer(int fd) {
    auto start = std::chrono::steady_clock::now();
    do {
      if (ioctl(fd, FIFREEZE, NULL) == 0) {
        fd_ = fd;
        return;
      }
      if (errno == EBUSY || errno == EINVAL) {
        // EBUSY means the filesystem is already frozen, perhaps by a concurrent
        // execution of this same test.  Since we don't have control over
        // exactly when another process unfreezes the filesystem, we don't
        // continue on with the test but rather just keep retrying the freeze
        // until it works.
        //
        // Very rarely, on f2fs FIFREEZE fails with EINVAL (b/255800104).
        // Unfortunately, the reason for this is still unknown.  Enter the retry
        // loop in this case too, in the hope that it helps.
        //
        // Both of these errors are rare, so this sleep should not normally be
        // executed.
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        continue;
      }
      ADD_FAILURE() << "Failed to freeze filesystem" << Errno();
      return;
    } while (std::chrono::steady_clock::now() - start <
             std::chrono::seconds(20));
    ADD_FAILURE() << "Timed out while waiting to freeze filesystem";
  }

  ~ScopedFsFreezer() {
    if (fd_ != -1 && ioctl(fd_, FITHAW, NULL) != 0) {
      ADD_FAILURE() << "Failed to thaw filesystem" << Errno();
    }
  }

 private:
  int fd_ = -1;
};

// Reads the raw data of the file specified by |fd| from its underlying block
// device |blk_device|.  The file has |expected_data_size| bytes of initialized
// data; this must be a multiple of the filesystem block size
// kFilesystemBlockSize.  The file may contain holes, in which case only the
// non-holes are read; the holes are not counted in |expected_data_size|.
static bool ReadRawDataOfFile(int fd, const std::string &blk_device,
                              int expected_data_size,
                              std::vector<uint8_t> *raw_data) {
  int max_extents = expected_data_size / kFilesystemBlockSize;

  EXPECT_TRUE(expected_data_size % kFilesystemBlockSize == 0);

  if (fsync(fd) != 0) {
    ADD_FAILURE() << "Failed to sync file" << Errno();
    return false;
  }

  // Freeze the filesystem containing the file.
  ScopedFsFreezer freezer(fd);

  // Query the file's extents.
  size_t allocsize = offsetof(struct fiemap, fm_extents[max_extents]);
  std::unique_ptr<struct fiemap> map(
      new (::operator new(allocsize)) struct fiemap);
  memset(map.get(), 0, allocsize);
  map->fm_flags = 0;
  map->fm_length = UINT64_MAX;
  map->fm_extent_count = max_extents;
  if (ioctl(fd, FS_IOC_FIEMAP, map.get()) != 0) {
    ADD_FAILURE() << "Failed to get extents of file" << Errno();
    return false;
  }

  // Read the raw data, using direct I/O to avoid getting any stale cached data.
  // Direct I/O requires using a block size aligned buffer.

  std::unique_ptr<void, void (*)(void *)> buf_mem(
      aligned_alloc(kFilesystemBlockSize, expected_data_size), free);
  if (buf_mem == nullptr) {
    ADD_FAILURE() << "Out of memory";
    return false;
  }
  uint8_t *buf = static_cast<uint8_t *>(buf_mem.get());
  int offset = 0;

  android::base::unique_fd blk_fd(
      open(blk_device.c_str(), O_RDONLY | O_DIRECT | O_CLOEXEC));
  if (blk_fd < 0) {
    ADD_FAILURE() << "Failed to open raw block device " << blk_device
                  << Errno();
    return false;
  }

  for (int i = 0; i < map->fm_mapped_extents; i++) {
    const struct fiemap_extent &extent = map->fm_extents[i];

    GTEST_LOG_(INFO) << "Extent " << i + 1 << " of " << map->fm_mapped_extents
                     << " is logical offset " << extent.fe_logical
                     << ", physical offset " << extent.fe_physical
                     << ", length " << extent.fe_length << ", flags 0x"
                     << std::hex << extent.fe_flags << std::dec;
    // Make sure the flags indicate that fe_physical is actually valid.
    if (extent.fe_flags & (FIEMAP_EXTENT_UNKNOWN | FIEMAP_EXTENT_UNWRITTEN)) {
      ADD_FAILURE() << "Unsupported extent flags: 0x" << std::hex
                    << extent.fe_flags << std::dec;
      return false;
    }
    if (extent.fe_length % kFilesystemBlockSize != 0) {
      ADD_FAILURE() << "Extent is not aligned to filesystem block size";
      return false;
    }
    if (extent.fe_length > expected_data_size - offset) {
      ADD_FAILURE() << "File is longer than expected";
      return false;
    }
    if (pread(blk_fd, &buf[offset], extent.fe_length, extent.fe_physical) !=
        extent.fe_length) {
      ADD_FAILURE() << "Error reading raw data from block device" << Errno();
      return false;
    }
    offset += extent.fe_length;
  }
  if (offset != expected_data_size) {
    ADD_FAILURE() << "File is shorter than expected";
    return false;
  }
  *raw_data = std::vector<uint8_t>(&buf[0], &buf[offset]);
  return true;
}

// Writes |plaintext| to a file |path| located on the block device |blk_device|.
// Returns in |ciphertext| the file's raw ciphertext read from |blk_device|.
static bool WriteTestFile(const std::vector<uint8_t> &plaintext,
                          const std::string &path,
                          const std::string &blk_device,
                          const struct f2fs_comp_option *compress_options,
                          std::vector<uint8_t> *ciphertext) {
  GTEST_LOG_(INFO) << "Creating test file " << path << " containing "
                   << plaintext.size() << " bytes of data";
  android::base::unique_fd fd(
      open(path.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, 0600));
  if (fd < 0) {
    ADD_FAILURE() << "Failed to create " << path << Errno();
    return false;
  }

  if (compress_options != nullptr) {
    if (ioctl(fd, F2FS_IOC_SET_COMPRESS_OPTION, compress_options) != 0) {
      ADD_FAILURE() << "Error setting compression options on " << path
                    << Errno();
      return false;
    }
  }

  if (!android::base::WriteFully(fd, plaintext.data(), plaintext.size())) {
    ADD_FAILURE() << "Error writing to " << path << Errno();
    return false;
  }

  if (compress_options != nullptr) {
    // With compress_mode=user, files in a compressed directory inherit the
    // compression flag but aren't actually compressed unless
    // F2FS_IOC_COMPRESS_FILE is called.  The ioctl compresses existing data
    // only, so it must be called *after* writing the data.  With
    // compress_mode=fs, the ioctl is unnecessary and fails with EOPNOTSUPP.
    if (ioctl(fd, F2FS_IOC_COMPRESS_FILE, NULL) != 0 && errno != EOPNOTSUPP) {
      ADD_FAILURE() << "F2FS_IOC_COMPRESS_FILE failed on " << path << Errno();
      return false;
    }
  }

  GTEST_LOG_(INFO) << "Reading the raw ciphertext of " << path << " from disk";
  if (!ReadRawDataOfFile(fd, blk_device, plaintext.size(), ciphertext)) {
    ADD_FAILURE() << "Failed to read the raw ciphertext of " << path;
    return false;
  }
  return true;
}

// See MakeSomeCompressibleClusters() for explanation.
static bool IsCompressibleCluster(int cluster_num) {
  return cluster_num % 2 == 0;
}

// Given some random data that will be written to the test file, modifies every
// other compression cluster to be compressible by at least 1 filesystem block.
//
// This testing strategy is adapted from the xfstest "f2fs/002".  We use some
// compressible clusters and some incompressible clusters because we want to
// test that the encryption works correctly with both.  We also don't make the
// data *too* compressible, since we want to have enough compressed blocks in
// each cluster to see the IVs being incremented.
static bool MakeSomeCompressibleClusters(std::vector<uint8_t> &bytes,
                                         int log_cluster_size) {
  int cluster_bytes = kFilesystemBlockSize << log_cluster_size;
  if (bytes.size() % cluster_bytes != 0) {
    ADD_FAILURE() << "Test file size (" << bytes.size()
                  << " bytes) is not divisible by compression cluster size ("
                  << cluster_bytes << " bytes)";
    return false;
  }
  int num_clusters = bytes.size() / cluster_bytes;
  for (int i = 0; i < num_clusters; i++) {
    if (IsCompressibleCluster(i)) {
      memset(&bytes[i * cluster_bytes], 0, 2 * kFilesystemBlockSize);
    }
  }
  return true;
}

// On-disk format of an f2fs compressed cluster
struct f2fs_compressed_cluster {
  __le32 clen;
  __le32 reserved[5];
  uint8_t cdata[];
} __attribute__((packed));

static bool DecompressLZ4Cluster(const uint8_t *in, uint8_t *out,
                                 int cluster_bytes) {
  const struct f2fs_compressed_cluster *cluster =
      reinterpret_cast<const struct f2fs_compressed_cluster *>(in);
  uint32_t clen = __le32_to_cpu(cluster->clen);

  if (clen > cluster_bytes - kFilesystemBlockSize - sizeof(*cluster)) {
    ADD_FAILURE() << "Invalid compressed cluster (bad compressed size)";
    return false;
  }
  if (LZ4_decompress_safe(reinterpret_cast<const char *>(cluster->cdata),
                          reinterpret_cast<char *>(out), clen,
                          cluster_bytes) != cluster_bytes) {
    ADD_FAILURE() << "Invalid compressed cluster (LZ4 decompression error)";
    return false;
  }

  // As long as we're here, do a regression test for kernel commit 7fa6d59816e7
  // ("f2fs: fix leaking uninitialized memory in compressed clusters").
  // Note that if this fails, we can still continue with the rest of the test.
  size_t full_clen = offsetof(struct f2fs_compressed_cluster, cdata[clen]);
  if (full_clen % kFilesystemBlockSize != 0) {
    size_t remainder =
        kFilesystemBlockSize - (full_clen % kFilesystemBlockSize);
    std::vector<uint8_t> zeroes(remainder, 0);
    std::vector<uint8_t> actual(&cluster->cdata[clen],
                                &cluster->cdata[clen + remainder]);
    EXPECT_EQ(zeroes, actual);
  }
  return true;
}

class FBEPolicyTest : public ::testing::Test {
 protected:
  void SetUp() override;
  void TearDown() override;
  bool SetMasterKey(const std::vector<uint8_t> &master_key, uint32_t flags = 0,
                    bool required = true);
  bool CreateAndSetHwWrappedKey(std::vector<uint8_t> *enc_key,
                                std::vector<uint8_t> *sw_secret);
  int GetSkipFlagsForInoBasedEncryption();
  bool SetEncryptionPolicy(int contents_mode, int filenames_mode, int flags,
                           int skip_flags);
  bool GenerateTestFile(
      TestFileInfo *info,
      const struct f2fs_comp_option *compress_options = nullptr);
  bool VerifyKeyIdentifier(const std::vector<uint8_t> &master_key);
  bool DerivePerModeEncryptionKey(const std::vector<uint8_t> &master_key,
                                  int mode, FscryptHkdfContext context,
                                  std::vector<uint8_t> &enc_key);
  bool DerivePerFileEncryptionKey(const std::vector<uint8_t> &master_key,
                                  const FscryptFileNonce &nonce,
                                  std::vector<uint8_t> &enc_key);
  void VerifyCiphertext(const std::vector<uint8_t> &enc_key,
                        const FscryptIV &starting_iv, const Cipher &cipher,
                        const TestFileInfo &file_info);
  void TestEmmcOptimizedDunWraparound(const std::vector<uint8_t> &master_key,
                                      const std::vector<uint8_t> &enc_key);
  bool EnableF2fsCompressionOnTestDir();
  bool F2fsCompressOptionsSupported(const struct f2fs_comp_option &opts);
  std::string test_dir_;
  std::string test_file_;
  struct fscrypt_key_specifier master_key_specifier_;
  bool skip_test_ = false;
  bool key_added_ = false;
  FilesystemInfo fs_info_;
};

// Test setup procedure.  Creates a test directory test_dir_ and does other
// preparations. skip_test_ is set to true if the test should be skipped.
void FBEPolicyTest::SetUp() {
  if (!IsFscryptV2Supported(kTestMountpoint)) {
    int first_api_level;
    ASSERT_TRUE(GetFirstApiLevel(&first_api_level));
    // Devices launching with R or higher must support fscrypt v2.
    ASSERT_LE(first_api_level, __ANDROID_API_Q__);
    GTEST_LOG_(INFO) << "Skipping test because fscrypt v2 is unsupported";
    skip_test_ = true;
    return;
  }

  // Make sure that if multiple test processes run simultaneously, they generate
  // different encryption keys.
  srand(getpid());

  test_dir_ = android::base::StringPrintf("%s/FBEPolicyTest.%d",
                                          kUnencryptedDir, getpid());
  test_file_ = test_dir_ + "/file";

  ASSERT_TRUE(GetFilesystemInfo(kTestMountpoint, &fs_info_));

  DeleteRecursively(test_dir_);
  if (mkdir(test_dir_.c_str(), 0700) != 0) {
    FAIL() << "Failed to create " << test_dir_ << Errno();
  }
}

void FBEPolicyTest::TearDown() {
  DeleteRecursively(test_dir_);

  // Remove the test key from kTestMountpoint.
  if (key_added_) {
    android::base::unique_fd mntfd(
        open(kTestMountpoint, O_RDONLY | O_DIRECTORY | O_CLOEXEC));
    if (mntfd < 0) {
      FAIL() << "Failed to open " << kTestMountpoint << Errno();
    }
    struct fscrypt_remove_key_arg arg;
    memset(&arg, 0, sizeof(arg));
    arg.key_spec = master_key_specifier_;

    if (ioctl(mntfd, FS_IOC_REMOVE_ENCRYPTION_KEY, &arg) != 0) {
      FAIL() << "FS_IOC_REMOVE_ENCRYPTION_KEY failed on " << kTestMountpoint
             << Errno();
    }
  }
}

// Adds |master_key| to kTestMountpoint and places the resulting key identifier
// in master_key_specifier_.
bool FBEPolicyTest::SetMasterKey(const std::vector<uint8_t> &master_key,
                                 uint32_t flags, bool required) {
  size_t allocsize = sizeof(struct fscrypt_add_key_arg) + master_key.size();
  std::unique_ptr<struct fscrypt_add_key_arg> arg(
      new (::operator new(allocsize)) struct fscrypt_add_key_arg);
  memset(arg.get(), 0, allocsize);
  arg->key_spec.type = FSCRYPT_KEY_SPEC_TYPE_IDENTIFIER;
  arg->__flags = flags;
  arg->raw_size = master_key.size();
  std::copy(master_key.begin(), master_key.end(), arg->raw);

  GTEST_LOG_(INFO) << "Adding fscrypt master key, flags are 0x" << std::hex
                   << flags << std::dec << ", raw bytes are "
                   << BytesToHex(master_key);
  android::base::unique_fd mntfd(
      open(kTestMountpoint, O_RDONLY | O_DIRECTORY | O_CLOEXEC));
  if (mntfd < 0) {
    ADD_FAILURE() << "Failed to open " << kTestMountpoint << Errno();
    return false;
  }
  if (ioctl(mntfd, FS_IOC_ADD_ENCRYPTION_KEY, arg.get()) != 0) {
    if (required || (errno != EINVAL && errno != EOPNOTSUPP)) {
      ADD_FAILURE() << "FS_IOC_ADD_ENCRYPTION_KEY failed on " << kTestMountpoint
                    << Errno();
    }
    return false;
  }
  master_key_specifier_ = arg->key_spec;
  GTEST_LOG_(INFO) << "Master key identifier is "
                   << BytesToHex(master_key_specifier_.u.identifier);
  key_added_ = true;
  if (!(flags & __FSCRYPT_ADD_KEY_FLAG_HW_WRAPPED) &&
      !VerifyKeyIdentifier(master_key))
    return false;
  return true;
}

// Creates a hardware-wrapped key, adds it to the filesystem, and derives the
// corresponding inline encryption key |enc_key| and software secret
// |sw_secret|.  Returns false if unsuccessful (either the test failed, or the
// device doesn't support hardware-wrapped keys so the test should be skipped).
bool FBEPolicyTest::CreateAndSetHwWrappedKey(std::vector<uint8_t> *enc_key,
                                             std::vector<uint8_t> *sw_secret) {
  std::vector<uint8_t> master_key, exported_key;
  if (!CreateHwWrappedKey(&master_key, &exported_key)) return false;

  if (!SetMasterKey(exported_key, __FSCRYPT_ADD_KEY_FLAG_HW_WRAPPED, false)) {
    if (!HasFailure()) {
      GTEST_LOG_(INFO) << "Skipping test because kernel doesn't support "
                          "hardware-wrapped keys";
    }
    return false;
  }

  if (!DeriveHwWrappedEncryptionKey(master_key, enc_key)) return false;
  if (!DeriveHwWrappedRawSecret(master_key, sw_secret)) return false;

  if (!VerifyKeyIdentifier(*sw_secret)) return false;

  return true;
}

enum {
  kSkipIfNoPolicySupport = 1 << 0,
  kSkipIfNoCryptoAPISupport = 1 << 1,
  kSkipIfNoHardwareSupport = 1 << 2,
};

// Returns 0 if encryption policies that include the inode number in the IVs
// (e.g. IV_INO_LBLK_64) are guaranteed to be settable on the test filesystem.
// Else returns kSkipIfNoPolicySupport.
//
// On f2fs, they're always settable.  On ext4, they're only settable if the
// filesystem has the 'stable_inodes' feature flag.  Android only sets
// 'stable_inodes' if the device uses one of these encryption policies "for
// real", e.g. "fileencryption=::inlinecrypt_optimized" in fstab.  Since the
// fstab could contain something else, we have to allow the tests for these
// encryption policies to be skipped on ext4.
int FBEPolicyTest::GetSkipFlagsForInoBasedEncryption() {
  if (fs_info_.type == "ext4") return kSkipIfNoPolicySupport;
  return 0;
}

// Sets a v2 encryption policy on the test directory.  The policy will use the
// test key and the specified encryption modes and flags.  If the kernel doesn't
// support setting or using the encryption policy, then a failure will be added,
// unless the reason is covered by a bit set in |skip_flags|.
bool FBEPolicyTest::SetEncryptionPolicy(int contents_mode, int filenames_mode,
                                        int flags, int skip_flags) {
  if (!key_added_) {
    ADD_FAILURE() << "SetEncryptionPolicy called but no key added";
    return false;
  }

  struct fscrypt_policy_v2 policy;
  memset(&policy, 0, sizeof(policy));
  policy.version = FSCRYPT_POLICY_V2;
  policy.contents_encryption_mode = contents_mode;
  policy.filenames_encryption_mode = filenames_mode;
  // Always give PAD_16, to match the policies that Android sets for real.
  // It doesn't affect contents encryption, though.
  policy.flags = flags | FSCRYPT_POLICY_FLAGS_PAD_16;
  memcpy(policy.master_key_identifier, master_key_specifier_.u.identifier,
         FSCRYPT_KEY_IDENTIFIER_SIZE);

  android::base::unique_fd dirfd(
      open(test_dir_.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC));
  if (dirfd < 0) {
    ADD_FAILURE() << "Failed to open " << test_dir_ << Errno();
    return false;
  }
  GTEST_LOG_(INFO) << "Setting encryption policy on " << test_dir_;
  if (ioctl(dirfd, FS_IOC_SET_ENCRYPTION_POLICY, &policy) != 0) {
    if (errno == EINVAL && (skip_flags & kSkipIfNoPolicySupport)) {
      GTEST_LOG_(INFO) << "Skipping test because encryption policy is "
                          "unsupported on this filesystem / kernel";
      return false;
    }
    ADD_FAILURE() << "FS_IOC_SET_ENCRYPTION_POLICY failed on " << test_dir_
                  << " using contents_mode=" << contents_mode
                  << ", filenames_mode=" << filenames_mode << ", flags=0x"
                  << std::hex << flags << std::dec << Errno();
    return false;
  }
  if (skip_flags & (kSkipIfNoCryptoAPISupport | kSkipIfNoHardwareSupport)) {
    android::base::unique_fd fd(
        open(test_file_.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, 0600));
    if (fd < 0) {
      // Setting an encryption policy that uses modes that aren't enabled in the
      // kernel's crypto API (e.g. FSCRYPT_MODE_ADIANTUM when the kernel lacks
      // CONFIG_CRYPTO_ADIANTUM) will still succeed, but actually creating a
      // file will fail with ENOPKG.  Make sure to check for this case.
      if (errno == ENOPKG && (skip_flags & kSkipIfNoCryptoAPISupport)) {
        GTEST_LOG_(INFO)
            << "Skipping test because encryption policy is "
               "unsupported on this kernel, due to missing crypto API support";
        return false;
      }
      // We get EINVAL here when using a hardware-wrapped key and the inline
      // encryption hardware supports wrapped keys but doesn't support the
      // number of DUN bytes that the file contents encryption requires.
      if (errno == EINVAL && (skip_flags & kSkipIfNoHardwareSupport)) {
        GTEST_LOG_(INFO)
            << "Skipping test because encryption policy is not compatible with "
               "this device's inline encryption hardware";
        return false;
      }
    }
    unlink(test_file_.c_str());
  }
  return true;
}

// Generates some test data, writes it to a file in the test directory, and
// returns in |info| the file's plaintext, the file's raw ciphertext read from
// disk, and other information about the file.
bool FBEPolicyTest::GenerateTestFile(
    TestFileInfo *info, const struct f2fs_comp_option *compress_options) {
  info->plaintext.resize(kTestFileBytes);
  RandomBytesForTesting(info->plaintext);

  if (compress_options != nullptr &&
      !MakeSomeCompressibleClusters(info->plaintext,
                                    compress_options->log_cluster_size))
    return false;

  if (!WriteTestFile(info->plaintext, test_file_, fs_info_.raw_blk_device,
                     compress_options, &info->actual_ciphertext))
    return false;

  android::base::unique_fd fd(open(test_file_.c_str(), O_RDONLY | O_CLOEXEC));
  if (fd < 0) {
    ADD_FAILURE() << "Failed to open " << test_file_ << Errno();
    return false;
  }

  // Get the file's inode number.
  if (!GetInodeNumber(test_file_, &info->inode_number)) return false;
  GTEST_LOG_(INFO) << "Inode number: " << info->inode_number;

  // Get the file's nonce.
  if (ioctl(fd, FS_IOC_GET_ENCRYPTION_NONCE, info->nonce.bytes) != 0) {
    ADD_FAILURE() << "FS_IOC_GET_ENCRYPTION_NONCE failed on " << test_file_
                  << Errno();
    return false;
  }
  GTEST_LOG_(INFO) << "File nonce: " << BytesToHex(info->nonce.bytes);
  return true;
}

static std::vector<uint8_t> InitHkdfInfo(FscryptHkdfContext context) {
  return {
      'f', 's', 'c', 'r', 'y', 'p', 't', '\0', static_cast<uint8_t>(context)};
}

static bool DeriveKey(const std::vector<uint8_t> &master_key,
                      const std::vector<uint8_t> &hkdf_info,
                      std::vector<uint8_t> &out) {
  if (HKDF(out.data(), out.size(), EVP_sha512(), master_key.data(),
           master_key.size(), nullptr, 0, hkdf_info.data(),
           hkdf_info.size()) != 1) {
    ADD_FAILURE() << "BoringSSL HKDF-SHA512 call failed";
    return false;
  }
  GTEST_LOG_(INFO) << "Derived subkey " << BytesToHex(out)
                   << " using HKDF info " << BytesToHex(hkdf_info);
  return true;
}

// Derives the key identifier from |master_key| and verifies that it matches the
// value the kernel returned in |master_key_specifier_|.
bool FBEPolicyTest::VerifyKeyIdentifier(
    const std::vector<uint8_t> &master_key) {
  std::vector<uint8_t> hkdf_info = InitHkdfInfo(HKDF_CONTEXT_KEY_IDENTIFIER);
  std::vector<uint8_t> computed_key_identifier(FSCRYPT_KEY_IDENTIFIER_SIZE);
  if (!DeriveKey(master_key, hkdf_info, computed_key_identifier)) return false;

  std::vector<uint8_t> actual_key_identifier(
      std::begin(master_key_specifier_.u.identifier),
      std::end(master_key_specifier_.u.identifier));
  EXPECT_EQ(actual_key_identifier, computed_key_identifier);
  return actual_key_identifier == computed_key_identifier;
}

// Derives a per-mode encryption key from |master_key|, |mode|, |context|, and
// (if needed for the context) the filesystem UUID.
bool FBEPolicyTest::DerivePerModeEncryptionKey(
    const std::vector<uint8_t> &master_key, int mode,
    FscryptHkdfContext context, std::vector<uint8_t> &enc_key) {
  std::vector<uint8_t> hkdf_info = InitHkdfInfo(context);

  hkdf_info.push_back(mode);
  if (context == HKDF_CONTEXT_IV_INO_LBLK_64_KEY ||
      context == HKDF_CONTEXT_IV_INO_LBLK_32_KEY)
    hkdf_info.insert(hkdf_info.end(), fs_info_.uuid.bytes,
                     std::end(fs_info_.uuid.bytes));

  return DeriveKey(master_key, hkdf_info, enc_key);
}

// Derives a per-file encryption key from |master_key| and |nonce|.
bool FBEPolicyTest::DerivePerFileEncryptionKey(
    const std::vector<uint8_t> &master_key, const FscryptFileNonce &nonce,
    std::vector<uint8_t> &enc_key) {
  std::vector<uint8_t> hkdf_info = InitHkdfInfo(HKDF_CONTEXT_PER_FILE_ENC_KEY);

  hkdf_info.insert(hkdf_info.end(), nonce.bytes, std::end(nonce.bytes));

  return DeriveKey(master_key, hkdf_info, enc_key);
}

// For IV_INO_LBLK_32: Hashes the |inode_number| using the SipHash key derived
// from |master_key|.  Returns the resulting hash in |hash|.
static bool HashInodeNumber(const std::vector<uint8_t> &master_key,
                            uint64_t inode_number, uint32_t *hash) {
  union {
    uint64_t words[2];
    __le64 le_words[2];
  } siphash_key;
  union {
    __le64 inode_number;
    uint8_t bytes[8];
  } input;

  std::vector<uint8_t> hkdf_info = InitHkdfInfo(HKDF_CONTEXT_INODE_HASH_KEY);
  std::vector<uint8_t> ino_hash_key(sizeof(siphash_key));
  if (!DeriveKey(master_key, hkdf_info, ino_hash_key)) return false;

  memcpy(&siphash_key, &ino_hash_key[0], sizeof(siphash_key));
  siphash_key.words[0] = __le64_to_cpu(siphash_key.le_words[0]);
  siphash_key.words[1] = __le64_to_cpu(siphash_key.le_words[1]);

  GTEST_LOG_(INFO) << "Inode hash key is {" << std::hex << "0x"
                   << siphash_key.words[0] << ", 0x" << siphash_key.words[1]
                   << "}" << std::dec;

  input.inode_number = __cpu_to_le64(inode_number);

  *hash = SIPHASH_24(siphash_key.words, input.bytes, sizeof(input));
  GTEST_LOG_(INFO) << "Hashed inode number " << inode_number << " to 0x"
                   << std::hex << *hash << std::dec;
  return true;
}

void FBEPolicyTest::VerifyCiphertext(const std::vector<uint8_t> &enc_key,
                                     const FscryptIV &starting_iv,
                                     const Cipher &cipher,
                                     const TestFileInfo &file_info) {
  const std::vector<uint8_t> &plaintext = file_info.plaintext;

  GTEST_LOG_(INFO) << "Verifying correctness of encrypted data";
  FscryptIV iv = starting_iv;

  std::vector<uint8_t> computed_ciphertext(plaintext.size());

  // Encrypt each filesystem block of file contents.
  for (size_t i = 0; i < plaintext.size(); i += kFilesystemBlockSize) {
    int block_size =
        std::min<size_t>(kFilesystemBlockSize, plaintext.size() - i);

    ASSERT_GE(sizeof(iv.bytes), cipher.ivsize());
    ASSERT_TRUE(cipher.Encrypt(enc_key, iv.bytes, &plaintext[i],
                               &computed_ciphertext[i], block_size));

    // Update the IV by incrementing the file logical block number.
    iv.lblk_num = __cpu_to_le32(__le32_to_cpu(iv.lblk_num) + 1);
  }

  ASSERT_EQ(file_info.actual_ciphertext, computed_ciphertext);
}

static bool InitIVForPerFileKey(FscryptIV *iv) {
  memset(iv, 0, kFscryptMaxIVSize);
  return true;
}

static bool InitIVForDirectKey(const FscryptFileNonce &nonce, FscryptIV *iv) {
  memset(iv, 0, kFscryptMaxIVSize);
  memcpy(iv->file_nonce, nonce.bytes, kFscryptFileNonceSize);
  return true;
}

static bool InitIVForInoLblk64(uint64_t inode_number, FscryptIV *iv) {
  if (inode_number > UINT32_MAX) {
    ADD_FAILURE() << "inode number doesn't fit in 32 bits";
    return false;
  }
  memset(iv, 0, kFscryptMaxIVSize);
  iv->inode_number = __cpu_to_le32(inode_number);
  return true;
}

static bool InitIVForInoLblk32(const std::vector<uint8_t> &master_key,
                               uint64_t inode_number, FscryptIV *iv) {
  uint32_t hash;
  if (!HashInodeNumber(master_key, inode_number, &hash)) return false;
  memset(iv, 0, kFscryptMaxIVSize);
  iv->lblk_num = __cpu_to_le32(hash);
  return true;
}

// Tests a policy matching "fileencryption=aes-256-xts:aes-256-cts:v2"
// (or simply "fileencryption=" on devices launched with R or higher)
TEST_F(FBEPolicyTest, TestAesPerFileKeysPolicy) {
  if (skip_test_) return;

  auto master_key = GenerateTestKey(kFscryptMasterKeySize);
  ASSERT_TRUE(SetMasterKey(master_key));

  if (!SetEncryptionPolicy(FSCRYPT_MODE_AES_256_XTS, FSCRYPT_MODE_AES_256_CTS,
                           0, 0))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  std::vector<uint8_t> enc_key(kAes256XtsKeySize);
  ASSERT_TRUE(DerivePerFileEncryptionKey(master_key, file_info.nonce, enc_key));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForPerFileKey(&iv));
  VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);
}

// Tests a policy matching
// "fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized"
// (or simply "fileencryption=::inlinecrypt_optimized" on devices launched with
// R or higher)
TEST_F(FBEPolicyTest, TestAesInlineCryptOptimizedPolicy) {
  if (skip_test_) return;

  auto master_key = GenerateTestKey(kFscryptMasterKeySize);
  ASSERT_TRUE(SetMasterKey(master_key));

  if (!SetEncryptionPolicy(FSCRYPT_MODE_AES_256_XTS, FSCRYPT_MODE_AES_256_CTS,
                           FSCRYPT_POLICY_FLAG_IV_INO_LBLK_64,
                           GetSkipFlagsForInoBasedEncryption()))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  std::vector<uint8_t> enc_key(kAes256XtsKeySize);
  ASSERT_TRUE(DerivePerModeEncryptionKey(master_key, FSCRYPT_MODE_AES_256_XTS,
                                         HKDF_CONTEXT_IV_INO_LBLK_64_KEY,
                                         enc_key));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForInoLblk64(file_info.inode_number, &iv));
  VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);
}

// Tests a policy matching
// "fileencryption=aes-256-xts:aes-256-cts:v2+inlinecrypt_optimized+wrappedkey_v0"
// (or simply "fileencryption=::inlinecrypt_optimized+wrappedkey_v0" on devices
// launched with R or higher)
TEST_F(FBEPolicyTest, TestAesInlineCryptOptimizedHwWrappedKeyPolicy) {
  if (skip_test_) return;

  std::vector<uint8_t> enc_key, sw_secret;
  if (!CreateAndSetHwWrappedKey(&enc_key, &sw_secret)) return;

  if (!SetEncryptionPolicy(
          FSCRYPT_MODE_AES_256_XTS, FSCRYPT_MODE_AES_256_CTS,
          FSCRYPT_POLICY_FLAG_IV_INO_LBLK_64,
          // 64-bit DUN support is not guaranteed.
          kSkipIfNoHardwareSupport | GetSkipFlagsForInoBasedEncryption()))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForInoLblk64(file_info.inode_number, &iv));
  VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);
}

// With IV_INO_LBLK_32, the DUN (IV) can wrap from UINT32_MAX to 0 in the middle
// of the file.  This method tests that this case appears to be handled
// correctly, by doing I/O across the place where the DUN wraps around.  Assumes
// that test_dir_ has already been set up with an IV_INO_LBLK_32 policy.
void FBEPolicyTest::TestEmmcOptimizedDunWraparound(
    const std::vector<uint8_t> &master_key,
    const std::vector<uint8_t> &enc_key) {
  // We'll test writing 'block_count' filesystem blocks.  The first
  // 'block_count_1' blocks will have DUNs [..., UINT32_MAX - 1, UINT32_MAX].
  // The remaining 'block_count_2' blocks will have DUNs [0, 1, ...].
  constexpr uint32_t block_count_1 = 3;
  constexpr uint32_t block_count_2 = 7;
  constexpr uint32_t block_count = block_count_1 + block_count_2;
  constexpr size_t data_size = block_count * kFilesystemBlockSize;

  // Assumed maximum file size.  Unfortunately there isn't a syscall to get
  // this.  ext4 allows ~16TB and f2fs allows ~4TB.  However, an underestimate
  // works fine for our purposes, so just go with 1TB.
  constexpr off_t max_file_size = 1000000000000;
  constexpr off_t max_file_blocks = max_file_size / kFilesystemBlockSize;

  // Repeatedly create empty files until we find one that can be used for DUN
  // wraparound testing, due to SipHash(inode_number) being almost UINT32_MAX.
  std::string path;
  TestFileInfo file_info;
  uint32_t lblk_with_dun_0;
  for (int i = 0;; i++) {
    // The probability of finding a usable file is about 'max_file_blocks /
    // UINT32_MAX', or about 5.6%.  So on average we'll need about 18 tries.
    // The probability we'll need over 1000 tries is less than 1e-25.
    ASSERT_LT(i, 1000) << "Tried too many times to find a usable test file";

    path = android::base::StringPrintf("%s/file%d", test_dir_.c_str(), i);
    android::base::unique_fd fd(
        open(path.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, 0600));
    ASSERT_GE(fd, 0) << "Failed to create " << path << Errno();

    ASSERT_TRUE(GetInodeNumber(path, &file_info.inode_number));
    uint32_t hash;
    ASSERT_TRUE(HashInodeNumber(master_key, file_info.inode_number, &hash));
    // Negating the hash gives the distance to DUN 0, and hence the 0-based
    // logical block number of the block which has DUN 0.
    lblk_with_dun_0 = -hash;
    if (lblk_with_dun_0 >= block_count_1 &&
        static_cast<off_t>(lblk_with_dun_0) + block_count_2 < max_file_blocks)
      break;
  }

  GTEST_LOG_(INFO) << "DUN wraparound test: path=" << path
                   << ", inode_number=" << file_info.inode_number
                   << ", lblk_with_dun_0=" << lblk_with_dun_0;

  // Write some data across the DUN wraparound boundary and verify that the
  // resulting on-disk ciphertext is as expected.  Note that we don't actually
  // have to fill the file until the boundary; we can just write to the needed
  // part and leave a hole before it.
  for (int i = 0; i < 2; i++) {
    // Try both buffered I/O and direct I/O.
    int open_flags = O_RDWR | O_CLOEXEC;
    if (i == 1) open_flags |= O_DIRECT;

    android::base::unique_fd fd(open(path.c_str(), open_flags));
    ASSERT_GE(fd, 0) << "Failed to open " << path << Errno();

    // Generate some test data.
    file_info.plaintext.resize(data_size);
    RandomBytesForTesting(file_info.plaintext);

    // Write the test data.  To support O_DIRECT, use a block-aligned buffer.
    std::unique_ptr<void, void (*)(void *)> buf_mem(
        aligned_alloc(kFilesystemBlockSize, data_size), free);
    ASSERT_TRUE(buf_mem != nullptr);
    memcpy(buf_mem.get(), &file_info.plaintext[0], data_size);
    off_t pos = static_cast<off_t>(lblk_with_dun_0 - block_count_1) *
                kFilesystemBlockSize;
    ASSERT_EQ(data_size, pwrite(fd, buf_mem.get(), data_size, pos))
        << "Error writing data to " << path << Errno();

    // Verify the ciphertext.
    ASSERT_TRUE(ReadRawDataOfFile(fd, fs_info_.raw_blk_device, data_size,
                                  &file_info.actual_ciphertext));
    FscryptIV iv;
    memset(&iv, 0, sizeof(iv));
    iv.lblk_num = __cpu_to_le32(-block_count_1);
    VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);
  }
}

// Tests a policy matching
// "fileencryption=aes-256-xts:aes-256-cts:v2+emmc_optimized" (or simply
// "fileencryption=::emmc_optimized" on devices launched with R or higher)
TEST_F(FBEPolicyTest, TestAesEmmcOptimizedPolicy) {
  if (skip_test_) return;

  auto master_key = GenerateTestKey(kFscryptMasterKeySize);
  ASSERT_TRUE(SetMasterKey(master_key));

  if (!SetEncryptionPolicy(FSCRYPT_MODE_AES_256_XTS, FSCRYPT_MODE_AES_256_CTS,
                           FSCRYPT_POLICY_FLAG_IV_INO_LBLK_32,
                           GetSkipFlagsForInoBasedEncryption()))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  std::vector<uint8_t> enc_key(kAes256XtsKeySize);
  ASSERT_TRUE(DerivePerModeEncryptionKey(master_key, FSCRYPT_MODE_AES_256_XTS,
                                         HKDF_CONTEXT_IV_INO_LBLK_32_KEY,
                                         enc_key));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForInoLblk32(master_key, file_info.inode_number, &iv));
  VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);

  TestEmmcOptimizedDunWraparound(master_key, enc_key);
}

// Tests a policy matching
// "fileencryption=aes-256-xts:aes-256-cts:v2+emmc_optimized+wrappedkey_v0"
// (or simply "fileencryption=::emmc_optimized+wrappedkey_v0" on devices
// launched with R or higher)
TEST_F(FBEPolicyTest, TestAesEmmcOptimizedHwWrappedKeyPolicy) {
  if (skip_test_) return;

  std::vector<uint8_t> enc_key, sw_secret;
  if (!CreateAndSetHwWrappedKey(&enc_key, &sw_secret)) return;

  if (!SetEncryptionPolicy(FSCRYPT_MODE_AES_256_XTS, FSCRYPT_MODE_AES_256_CTS,
                           FSCRYPT_POLICY_FLAG_IV_INO_LBLK_32,
                           GetSkipFlagsForInoBasedEncryption()))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForInoLblk32(sw_secret, file_info.inode_number, &iv));
  VerifyCiphertext(enc_key, iv, Aes256XtsCipher(), file_info);

  TestEmmcOptimizedDunWraparound(sw_secret, enc_key);
}

// Tests a policy matching "fileencryption=adiantum:adiantum:v2" (or simply
// "fileencryption=adiantum" on devices launched with R or higher)
TEST_F(FBEPolicyTest, TestAdiantumPolicy) {
  if (skip_test_) return;

  auto master_key = GenerateTestKey(kFscryptMasterKeySize);
  ASSERT_TRUE(SetMasterKey(master_key));

  // Adiantum support isn't required (since CONFIG_CRYPTO_ADIANTUM can be unset
  // in the kernel config), so we may skip the test here.
  //
  // We don't need to use GetSkipFlagsForInoBasedEncryption() here, since the
  // "DIRECT_KEY" IV generation method doesn't include inode numbers in the IVs.
  if (!SetEncryptionPolicy(FSCRYPT_MODE_ADIANTUM, FSCRYPT_MODE_ADIANTUM,
                           FSCRYPT_POLICY_FLAG_DIRECT_KEY,
                           kSkipIfNoCryptoAPISupport))
    return;

  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info));

  std::vector<uint8_t> enc_key(kAdiantumKeySize);
  ASSERT_TRUE(DerivePerModeEncryptionKey(master_key, FSCRYPT_MODE_ADIANTUM,
                                         HKDF_CONTEXT_DIRECT_KEY, enc_key));

  FscryptIV iv;
  ASSERT_TRUE(InitIVForDirectKey(file_info.nonce, &iv));
  VerifyCiphertext(enc_key, iv, AdiantumCipher(), file_info);
}

// Tests adding a corrupted wrapped key to fscrypt keyring.
// If wrapped key is corrupted, fscrypt should return a failure.
TEST_F(FBEPolicyTest, TestHwWrappedKeyCorruption) {
  if (skip_test_) return;

  std::vector<uint8_t> master_key, exported_key;
  if (!CreateHwWrappedKey(&master_key, &exported_key)) return;

  for (int i = 0; i < exported_key.size(); i++) {
    std::vector<uint8_t> corrupt_key(exported_key.begin(), exported_key.end());
    corrupt_key[i] = ~corrupt_key[i];
    ASSERT_FALSE(
        SetMasterKey(corrupt_key, __FSCRYPT_ADD_KEY_FLAG_HW_WRAPPED, false));
  }
}

bool FBEPolicyTest::EnableF2fsCompressionOnTestDir() {
  android::base::unique_fd fd(open(test_dir_.c_str(), O_RDONLY | O_CLOEXEC));
  if (fd < 0) {
    ADD_FAILURE() << "Failed to open " << test_dir_ << Errno();
    return false;
  }

  int flags;
  if (ioctl(fd, FS_IOC_GETFLAGS, &flags) != 0) {
    ADD_FAILURE() << "Unexpected error getting flags of " << test_dir_
                  << Errno();
    return false;
  }
  flags |= FS_COMPR_FL;
  if (ioctl(fd, FS_IOC_SETFLAGS, &flags) != 0) {
    if (errno == EOPNOTSUPP) {
      GTEST_LOG_(INFO)
          << "Skipping test because f2fs compression is not supported on "
          << kTestMountpoint;
      return false;
    }
    ADD_FAILURE() << "Unexpected error enabling compression on " << test_dir_
                  << Errno();
    return false;
  }
  return true;
}

static std::string F2fsCompressAlgorithmName(int algorithm) {
  switch (algorithm) {
    case F2FS_COMPRESS_LZO:
      return "LZO";
    case F2FS_COMPRESS_LZ4:
      return "LZ4";
    case F2FS_COMPRESS_ZSTD:
      return "ZSTD";
    case F2FS_COMPRESS_LZORLE:
      return "LZORLE";
    default:
      return android::base::StringPrintf("%d", algorithm);
  }
}

bool FBEPolicyTest::F2fsCompressOptionsSupported(
    const struct f2fs_comp_option &opts) {
  android::base::unique_fd fd(
      open(test_file_.c_str(), O_WRONLY | O_CREAT, 0600));
  if (fd < 0) {
    // If the filesystem has the compression feature flag enabled but f2fs
    // compression support was compiled out of the kernel, then setting
    // FS_COMPR_FL on the directory will succeed, but creating a file in the
    // directory will fail with EOPNOTSUPP.
    if (errno == EOPNOTSUPP) {
      GTEST_LOG_(INFO)
          << "Skipping test because kernel doesn't support f2fs compression";
      return false;
    }
    ADD_FAILURE() << "Unexpected error creating " << test_file_
                  << " after enabling f2fs compression on parent directory"
                  << Errno();
    return false;
  }

  if (ioctl(fd, F2FS_IOC_SET_COMPRESS_OPTION, &opts) != 0) {
    if (errno == ENOTTY || errno == EOPNOTSUPP) {
      GTEST_LOG_(INFO) << "Skipping test because kernel doesn't support "
                          "F2FS_IOC_SET_COMPRESS_OPTION on "
                       << kTestMountpoint;
      return false;
    }
    ADD_FAILURE() << "Unexpected error from F2FS_IOC_SET_COMPRESS_OPTION"
                  << Errno();
    return false;
  }
  // Unsupported compression algorithms aren't detected until the file is
  // reopened.
  fd.reset(open(test_file_.c_str(), O_WRONLY));
  if (fd < 0) {
    if (errno == EOPNOTSUPP || errno == ENOPKG) {
      GTEST_LOG_(INFO) << "Skipping test because kernel doesn't support "
                       << F2fsCompressAlgorithmName(opts.algorithm)
                       << " compression";
      return false;
    }
    ADD_FAILURE() << "Unexpected error when reopening file after "
                     "F2FS_IOC_SET_COMPRESS_OPTION"
                  << Errno();
    return false;
  }
  unlink(test_file_.c_str());
  return true;
}

// Tests that encryption is done correctly on compressed files.
//
// This works by creating a compressed+encrypted file, then decrypting the
// file's on-disk data, then decompressing it, then comparing the result to the
// original data.  We don't do it the other way around (compress+encrypt the
// original data and compare to the on-disk data) because different
// implementations of a compression algorithm can produce different results.
//
// This is adapted from the xfstest "f2fs/002"; see there for some more details.
//
// This test will skip itself if any of the following is true:
//   - f2fs compression isn't enabled on /data
//   - f2fs compression isn't enabled in the kernel (CONFIG_F2FS_FS_COMPRESSION)
//   - The kernel doesn't support the needed algorithm (CONFIG_F2FS_FS_LZ4)
//   - The kernel doesn't support the F2FS_IOC_SET_COMPRESS_OPTION ioctl
//
// Note, this test will be flaky if the kernel is missing commit 093f0bac32b
// ("f2fs: change fiemap way in printing compression chunk").
TEST_F(FBEPolicyTest, TestF2fsCompression) {
  if (skip_test_) return;

  // Currently, only f2fs supports compression+encryption.
  if (fs_info_.type != "f2fs") {
    GTEST_LOG_(INFO) << "Skipping test because device uses " << fs_info_.type
                     << ", not f2fs";
    return;
  }

  // Enable compression and encryption on the test directory.  Afterwards, both
  // of these features will be inherited by any file created in this directory.
  //
  // If compression is not supported, skip the test.  Use the default encryption
  // settings, which should always be supported.
  if (!EnableF2fsCompressionOnTestDir()) return;
  auto master_key = GenerateTestKey(kFscryptMasterKeySize);
  ASSERT_TRUE(SetMasterKey(master_key));
  ASSERT_TRUE(SetEncryptionPolicy(FSCRYPT_MODE_AES_256_XTS,
                                  FSCRYPT_MODE_AES_256_CTS, 0, 0));

  // This test will use LZ4 compression with a cluster size of 2^2 = 4 blocks.
  // Check that this setting is supported.
  //
  // Note that the precise choice of algorithm and cluster size isn't too
  // important for this test.  We just (somewhat arbitrarily) chose a setting
  // which is commonly used and for which a decompression library is available.
  const int log_cluster_size = 2;
  const int cluster_bytes = kFilesystemBlockSize << log_cluster_size;
  struct f2fs_comp_option comp_opt;
  memset(&comp_opt, 0, sizeof(comp_opt));
  comp_opt.algorithm = F2FS_COMPRESS_LZ4;
  comp_opt.log_cluster_size = log_cluster_size;
  if (!F2fsCompressOptionsSupported(comp_opt)) return;

  // Generate the test file and retrieve its on-disk data.  Note: despite being
  // compressed, the on-disk data here will still be |kTestFileBytes| long.
  // This is because FS_IOC_FIEMAP doesn't natively support compression, and the
  // way that f2fs handles it on compressed files results in us reading extra
  // blocks appended to the compressed clusters.  It works out in the end
  // though, since these extra blocks get ignored during decompression.
  TestFileInfo file_info;
  ASSERT_TRUE(GenerateTestFile(&file_info, &comp_opt));

  GTEST_LOG_(INFO) << "Decrypting the blocks of the compressed file";
  std::vector<uint8_t> enc_key(kAes256XtsKeySize);
  ASSERT_TRUE(DerivePerFileEncryptionKey(master_key, file_info.nonce, enc_key));
  std::vector<uint8_t> decrypted_data(kTestFileBytes);
  FscryptIV iv;
  memset(&iv, 0, sizeof(iv));
  ASSERT_EQ(0, kTestFileBytes % kFilesystemBlockSize);
  for (int i = 0; i < kTestFileBytes; i += kFilesystemBlockSize) {
    int block_num = i / kFilesystemBlockSize;
    int cluster_num = i / cluster_bytes;

    // In compressed clusters, IVs start at 1 higher than the expected value.
    // Fortunately, due to the compression there is no overlap...
    if (IsCompressibleCluster(cluster_num)) block_num++;

    iv.lblk_num = __cpu_to_le32(block_num);
    ASSERT_TRUE(Aes256XtsCipher().Decrypt(
        enc_key, iv.bytes, &file_info.actual_ciphertext[i], &decrypted_data[i],
        kFilesystemBlockSize));
  }

  GTEST_LOG_(INFO) << "Decompressing the decrypted blocks of the file";
  std::vector<uint8_t> decompressed_data(kTestFileBytes);
  ASSERT_EQ(0, kTestFileBytes % cluster_bytes);
  for (int i = 0; i < kTestFileBytes; i += cluster_bytes) {
    int cluster_num = i / cluster_bytes;
    if (IsCompressibleCluster(cluster_num)) {
      // We had filled this cluster with compressible data, so it should have
      // been stored compressed.
      ASSERT_TRUE(DecompressLZ4Cluster(&decrypted_data[i],
                                       &decompressed_data[i], cluster_bytes));
    } else {
      // We had filled this cluster with random data, so it should have been
      // incompressible and thus stored uncompressed.
      memcpy(&decompressed_data[i], &decrypted_data[i], cluster_bytes);
    }
  }

  // Finally do the actual test.  The data we got after decryption+decompression
  // should match the original file contents.
  GTEST_LOG_(INFO) << "Comparing the result to the original data";
  ASSERT_EQ(file_info.plaintext, decompressed_data);
}

static bool DeviceUsesFBE() {
  if (android::base::GetProperty("ro.crypto.type", "") == "file") return true;
  // FBE has been required since Android Q.
  int first_api_level;
  if (!GetFirstApiLevel(&first_api_level)) return true;
  if (first_api_level >= __ANDROID_API_Q__) {
    ADD_FAILURE() << "File-based encryption is required";
  } else {
    GTEST_LOG_(INFO)
        << "Skipping test because device doesn't use file-based encryption";
  }
  return false;
}

// Retrieves the encryption key specifier used in the file-based encryption
// policy of |dir|.  This isn't the key itself, but rather a "name" for the key.
// If the key specifier cannot be retrieved, e.g. due to the directory being
// unencrypted, then false is returned and a failure is added.
static bool GetKeyUsedByDir(const std::string &dir,
                            std::string *key_specifier) {
  android::base::unique_fd fd(open(dir.c_str(), O_RDONLY));
  if (fd < 0) {
    ADD_FAILURE() << "Failed to open " << dir << Errno();
    return false;
  }
  struct fscrypt_get_policy_ex_arg arg = {.policy_size = sizeof(arg.policy)};
  int res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY_EX, &arg);
  if (res != 0 && errno == ENOTTY) {
    // Handle old kernels that don't support FS_IOC_GET_ENCRYPTION_POLICY_EX.
    res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY, &arg.policy.v1);
  }
  if (res != 0) {
    if (errno == ENODATA) {
      ADD_FAILURE() << "Directory " << dir << " is not encrypted!";
    } else {
      ADD_FAILURE() << "Failed to get encryption policy of " << dir << Errno();
    }
    return false;
  }
  switch (arg.policy.version) {
    case FSCRYPT_POLICY_V1:
      *key_specifier = BytesToHex(arg.policy.v1.master_key_descriptor);
      return true;
    case FSCRYPT_POLICY_V2:
      *key_specifier = BytesToHex(arg.policy.v2.master_key_identifier);
      return true;
    default:
      ADD_FAILURE() << dir << " uses unknown encryption policy version ("
                    << arg.policy.version << ")";
      return false;
  }
}

// Tests that if the device uses FBE, then the ciphertext for file contents in
// encrypted directories seems to be random.
//
// This isn't as strong a test as the correctness tests, but it's useful because
// it applies regardless of the encryption format and key.  Thus it runs even on
// old devices, including ones that used a vendor-specific encryption format.
TEST(FBETest, TestFileContentsRandomness) {
  const std::string path_1 =
      android::base::StringPrintf("%s/FBETest-1.%d", kTmpDir, getpid());
  const std::string path_2 =
      android::base::StringPrintf("%s/FBETest-2.%d", kTmpDir, getpid());

  if (!DeviceUsesFBE()) return;

  FilesystemInfo fs_info;
  ASSERT_TRUE(GetFilesystemInfo(kTestMountpoint, &fs_info));

  std::vector<uint8_t> zeroes(kTestFileBytes, 0);
  std::vector<uint8_t> ciphertext_1;
  std::vector<uint8_t> ciphertext_2;
  ASSERT_TRUE(WriteTestFile(zeroes, path_1, fs_info.raw_blk_device, nullptr,
                            &ciphertext_1));
  ASSERT_TRUE(WriteTestFile(zeroes, path_2, fs_info.raw_blk_device, nullptr,
                            &ciphertext_2));

  GTEST_LOG_(INFO) << "Verifying randomness of ciphertext";

  // Each individual file's ciphertext should be random.
  ASSERT_TRUE(VerifyDataRandomness(ciphertext_1));
  ASSERT_TRUE(VerifyDataRandomness(ciphertext_2));

  // The files' ciphertext concatenated should also be random.
  // I.e., each file should be encrypted differently.
  std::vector<uint8_t> concatenated_ciphertext;
  concatenated_ciphertext.insert(concatenated_ciphertext.end(),
                                 ciphertext_1.begin(), ciphertext_1.end());
  concatenated_ciphertext.insert(concatenated_ciphertext.end(),
                                 ciphertext_2.begin(), ciphertext_2.end());
  ASSERT_TRUE(VerifyDataRandomness(concatenated_ciphertext));

  ASSERT_EQ(unlink(path_1.c_str()), 0);
  ASSERT_EQ(unlink(path_2.c_str()), 0);
}

// Tests that all of user 0's directories that should be encrypted actually are,
// and that user 0's CE and DE keys are different.
TEST(FBETest, TestUserDirectoryPolicies) {
  if (!DeviceUsesFBE()) return;

  std::string user0_ce_key, user0_de_key;
  EXPECT_TRUE(GetKeyUsedByDir("/data/user/0", &user0_ce_key));
  EXPECT_TRUE(GetKeyUsedByDir("/data/user_de/0", &user0_de_key));
  EXPECT_NE(user0_ce_key, user0_de_key) << "CE and DE keys must differ";

  // Check the CE directories other than /data/user/0.
  for (const std::string &dir : {"/data/media/0", "/data/misc_ce/0",
                                 "/data/system_ce/0", "/data/vendor_ce/0"}) {
    std::string key;
    EXPECT_TRUE(GetKeyUsedByDir(dir, &key));
    EXPECT_EQ(key, user0_ce_key) << dir << " must be encrypted with CE key";
  }

  // Check the DE directories other than /data/user_de/0.
  for (const std::string &dir :
       {"/data/misc_de/0", "/data/system_de/0", "/data/vendor_de/0"}) {
    std::string key;
    EXPECT_TRUE(GetKeyUsedByDir(dir, &key));
    EXPECT_EQ(key, user0_de_key) << dir << " must be encrypted with DE key";
  }
}

}  // namespace kernel
}  // namespace android
