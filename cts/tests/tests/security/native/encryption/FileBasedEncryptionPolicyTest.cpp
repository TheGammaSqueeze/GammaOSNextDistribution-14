/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <cutils/properties.h>
#include <fcntl.h>
#include <fscrypt/fscrypt.h>
#include <gtest/gtest.h>
#include <linux/fscrypt.h>
#include <setjmp.h>
#include <signal.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include "utils.h"

// Non-upstream encryption modes that are used on some devices.
#define FSCRYPT_MODE_AES_256_HEH 126
#define FSCRYPT_MODE_PRIVATE 127

#ifdef __arm__
// For ARM32, assemble the 'aese.8' instruction as an .inst, since otherwise
// clang does not accept it.  It would be allowed in a separate file compiled
// with -march=armv8+crypto, but this way is much easier.  And it's not yet
// possible to use a target function attribute, because clang doesn't yet
// support target("fpu=crypto-neon-fp-armv8") like gcc does.
//
// We use the ARM encoding of the instruction, not the Thumb encoding.  So make
// sure to use target("arm") to mark the function as containing ARM code.
static void __attribute__((target("arm"))) executeAESInstruction(void) {
    // aese.8  q0, q1
    asm volatile(".inst  0xf3b00302" : : : "q0");
}
#elif defined(__aarch64__)
static void __attribute__((target("crypto"))) executeAESInstruction(void) {
    asm volatile("aese  v0.16b, v1.16b" : : : "v0");
}
#elif defined(__i386__) || defined(__x86_64__)
static void __attribute__((target("sse"))) executeAESInstruction(void) {
    asm volatile("aesenc %%xmm1, %%xmm0" : : : "xmm0");
}
#else
// For unknown architectures, we cannot confirm that AES instructions are
// unsupported.  So, assume they are supported, i.e. don't raise SIGILL.  This
// disallows Adiantum until code is explicitly added for the architecture.
#warning "unknown architecture, assuming Adiantum is not allowed"
static void executeAESInstruction(void) {}
#endif

static jmp_buf jump_buf;

static void handleSIGILL(int __attribute__((unused)) signum) {
    longjmp(jump_buf, 1);
}

// Checks whether Adiantum encryption is allowed.  Adiantum encryption is
// allowed only when the CPU does *not* support AES instructions.
//
// ARM processors don't have a standard way for user processes to determine CPU
// features.  On Linux it's possible to read the AT_HWCAP and AT_HWCAP2 values
// from /proc/self/auxv.  But, this relies on the kernel exposing the features
// correctly, which we don't want to rely on.  Instead we actually try to
// execute the instruction, and see whether SIGILL is raised or not.
//
// To keep things consistent we use the same approach on x86 to detect AES-NI,
// though in principle the 'cpuid' instruction could be used there.
static bool isAdiantumEncryptionAllowed(void) {
    struct sigaction act;
    struct sigaction oldact;
    bool allowed;

    memset(&act, 0, sizeof(act));
    act.sa_handler = handleSIGILL;

    EXPECT_EQ(0, sigaction(SIGILL, &act, &oldact));

    if (setjmp(jump_buf) != 0) {
        // Trying to execute an AES instruction raised SIGILL, which shows that
        // AES instructions are unsupported.  Thus, Adiantum is allowed.
        allowed = true;
    } else {
        executeAESInstruction();
        // Either an AES instruction was successfully executed, or the
        // architecture is unknown.  So, it has *not* been shown that AES
        // instructions are unsupported.  Thus, Adiantum is *not* allowed.
        allowed = false;
    }

    EXPECT_EQ(0, sigaction(SIGILL, &oldact, NULL));

    return allowed;
}

// CDD 9.9.3/C-1-5: must use AES-256-XTS or Adiantum contents encryption.
// CDD 9.9.3/C-1-6: must use AES-256-CTS, AES-256-HCTR2, or Adiantum filenames encryption.
// CDD 9.9.3/C-1-12: mustn't use Adiantum if the CPU has AES instructions.
static void validateEncryptionModes(int contents_mode, int filenames_mode,
                                    bool allow_legacy_modes) {
    bool allowed = false;
    switch (contents_mode) {
        case FSCRYPT_MODE_AES_256_XTS:
        case FSCRYPT_MODE_ADIANTUM:
            allowed = true;
            break;
        case FSCRYPT_MODE_PRIVATE:
            // Some devices shipped with custom kernel patches implementing
            // AES-256-XTS inline encryption behind "FSCRYPT_MODE_PRIVATE", so
            // we need to let it pass on old devices.  It's up to the vendor to
            // ensure it's really AES-256-XTS.
            allowed = allow_legacy_modes;
            if (allowed) {
                GTEST_LOG_(INFO) << "Allowing FSCRYPT_MODE_PRIVATE because this is an old device";
            }
            break;
    }
    if (!allowed) {
        ADD_FAILURE() << "Contents encryption mode not allowed: " << contents_mode;
    }

    allowed = false;
    switch (filenames_mode) {
        case FSCRYPT_MODE_AES_256_CTS:
        case FSCRYPT_MODE_AES_256_HCTR2:
        case FSCRYPT_MODE_ADIANTUM:
            allowed = true;
            break;
        case FSCRYPT_MODE_AES_256_HEH:
            // At least one device shipped with the experimental AES-256-HEH
            // filenames encryption, which was never added to the CDD and was
            // only supported by one kernel version (android-4.4).  It's
            // cryptographically superior to AES-256-CTS for the use case,
            // though, so it's compliant in spirit; let it pass on old devices.
            allowed = allow_legacy_modes;
            if (allowed) {
                GTEST_LOG_(INFO)
                        << "Allowing FSCRYPT_MODE_AES_256_HEH because this is an old device";
            }
            break;
    }
    if (!allowed) {
        ADD_FAILURE() << "Filenames encryption mode not allowed: " << filenames_mode;
    }

    if (contents_mode == FSCRYPT_MODE_ADIANTUM || filenames_mode == FSCRYPT_MODE_ADIANTUM) {
        EXPECT_TRUE(isAdiantumEncryptionAllowed());
    }
}

// Ideally we'd check whether /data is on eMMC, but that is hard to do from a
// CTS test.  To keep things simple we just check whether the system knows about
// at least one eMMC device.
//
// virtio devices may provide inline encryption support that is backed by eMMC
// inline encryption on the host, thus inheriting the DUN size limitation.  So
// virtio devices must be allowed here too.  TODO(b/207390665): check the
// maximum DUN size directly instead.
static bool mightBeUsingEmmcStorage() {
    struct stat stbuf;
    return lstat("/sys/class/block/mmcblk0", &stbuf) == 0 ||
            lstat("/sys/class/block/vda", &stbuf) == 0;
}

// CDD 9.9.3/C-1-15: must not reuse IVs for file contents encryption except when
// limited by hardware that only supports 32-bit IVs.  Like most other
// encryption security requirements, CTS can't directly test this.  But the most
// likely case where this requirement wouldn't be met is a misconfiguration
// where FSCRYPT_POLICY_FLAG_IV_INO_LBLK_32 ("emmc_optimized" in the fstab) is
// used on a non-eMMC based device.  CTS can test for that, so we do so below.
static void validateEncryptionFlags(int flags, bool is_adoptable_storage) {
    if (flags & FSCRYPT_POLICY_FLAG_IV_INO_LBLK_32) {
        EXPECT_TRUE(mightBeUsingEmmcStorage());
        EXPECT_FALSE(is_adoptable_storage);
    }
}

// We check the encryption policy of /data/local/tmp because it's one of the
// only encrypted directories the shell domain has permission to open.  Ideally
// we'd check the user's credential-encrypted storage (/data/user/0) instead.
// It shouldn't matter in practice though, since AOSP code doesn't provide any
// way to configure different directories to use different algorithms...
#define DIR_TO_CHECK "/data/local/tmp/"

static std::string loggedGetProperty(const std::string &name, const std::string &default_value) {
    auto value = android::base::GetProperty(name, "");
    if (value == "" && default_value != "") {
        GTEST_LOG_(INFO) << name << "=\"\" [defaults to \"" << default_value << "\"]";
        return default_value;
    }
    GTEST_LOG_(INFO) << name << "=\"" << value << "\"";
    return value;
}

static void validateAdoptableStorageSettings(int first_api_level) {
    GTEST_LOG_(INFO) << "Validating FBE settings for adoptable storage";

    // Determine the options string being used.  This matches the logic in vold.
    auto contents_mode = loggedGetProperty("ro.crypto.volume.contents_mode", "");
    auto filenames_mode =
            loggedGetProperty("ro.crypto.volume.filenames_mode",
                              first_api_level > __ANDROID_API_Q__ ? "" : "aes-256-heh");
    auto options_string =
            loggedGetProperty("ro.crypto.volume.options", contents_mode + ":" + filenames_mode);

    // Parse the options string.
    android::fscrypt::EncryptionOptions options;
    ASSERT_TRUE(android::fscrypt::ParseOptions(options_string, &options));

    // Log the full options for debugging purposes.
    std::string options_string_full;
    ASSERT_TRUE(android::fscrypt::OptionsToString(options, &options_string_full));
    GTEST_LOG_(INFO) << "options_string_full=\"" << options_string_full << "\"";

    // Validate the encryption options.
    if (first_api_level > __ANDROID_API_Q__) {
        // CDD 9.9.3/C-1-13 and 9.9.3/C-1-14, same as internal storage.
        EXPECT_EQ(2, options.version);
    }
    validateEncryptionModes(options.contents_mode, options.filenames_mode, options.version == 1);
    validateEncryptionFlags(options.flags, true);
}

// Test that the device is using appropriate settings for file-based encryption.
// If this test fails, you should ensure that the device's fstab has the correct
// fileencryption= option for the userdata partition and that the ro.crypto
// system properties have been set to the correct values.  See
// https://source.android.com/security/encryption/file-based.html
//
// @CddTest = 9.9.2/C-0-3|9.9.3/C-1-5,C-1-6,C-1-12,C-1-13,C-1-14,C-1-15
TEST(FileBasedEncryptionPolicyTest, allowedPolicy) {
    int first_api_level = getFirstApiLevel();
    int vendor_api_level = getVendorApiLevel();
    struct fscrypt_get_policy_ex_arg arg;
    int res;
    int contents_mode;
    int filenames_mode;
    int flags;
    bool allow_legacy_modes = false;

    std::string crypto_type = loggedGetProperty("ro.crypto.type", "");
    GTEST_LOG_(INFO) << "First API level is " << first_api_level;
    GTEST_LOG_(INFO) << "Vendor API level is " << vendor_api_level;

    // This feature name check only applies to devices that first shipped with
    // SC or later.
    int min_api_level = (first_api_level < vendor_api_level) ? first_api_level
                                                             : vendor_api_level;
    if (min_api_level >= __ANDROID_API_S__ &&
        !deviceSupportsFeature("android.hardware.security.model.compatible")) {
        GTEST_SKIP()
            << "Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.";
        return;
    }

    GTEST_LOG_(INFO) << "Validating FBE settings for internal storage";

    android::base::unique_fd fd(open(DIR_TO_CHECK, O_RDONLY | O_CLOEXEC));
    if (fd < 0) {
        FAIL() << "Failed to open " DIR_TO_CHECK ": " << strerror(errno);
    }

    // Note: SELinux policy allows the shell domain to use these ioctls, but not
    // apps.  Therefore this test needs to be a real native test that's run
    // through the shell, not a JNI test run through an installed APK.
    arg.policy_size = sizeof(arg.policy);
    res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY_EX, &arg);
    if (res != 0 && errno == ENOTTY) {
        // Handle old kernels that don't support FS_IOC_GET_ENCRYPTION_POLICY_EX
        GTEST_LOG_(INFO) << "Old kernel, falling back to FS_IOC_GET_ENCRYPTION_POLICY";
        res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY, &arg.policy.v1);
    }
    if (res != 0) {
        if (errno == ENODATA ||     // Directory is unencrypted
            errno == ENOENT ||      // Directory is unencrypted (on older kernels)
            errno == EOPNOTSUPP ||  // Filesystem encryption feature not enabled
            errno == ENOTTY) {      // Very old kernel, doesn't know about encryption at all

            // Starting with Android 10, file-based encryption is required on
            // new devices [CDD 9.9.2/C-0-3].
            if (first_api_level < __ANDROID_API_Q__) {
                GTEST_LOG_(INFO)
                        << "Exempt from file-based encryption due to old starting API level";
                return;
            }
            if (crypto_type == "managed") {
                // Android is running in a virtualized environment and the file system is encrypted
                // by the host system.
                GTEST_LOG_(INFO) << "Exempt from file-based encryption because the file system is "
                                 << "encrypted by the host system";
                // Note: All encryption-related CDD requirements still must be met,
                // but they can't be tested directly in this case.
                return;
            }
            FAIL() << "Device isn't using file-based encryption";
        } else {
            FAIL() << "Failed to get encryption policy of " DIR_TO_CHECK ": " << strerror(errno);
        }
    }

    switch (arg.policy.version) {
        case FSCRYPT_POLICY_V1:
            GTEST_LOG_(INFO) << "Detected v1 encryption policy";
            contents_mode = arg.policy.v1.contents_encryption_mode;
            filenames_mode = arg.policy.v1.filenames_encryption_mode;
            flags = arg.policy.v1.flags;

            // Starting with Android 11, FBE must use a strong, non-reversible
            // key derivation function [CDD 9.9.3/C-1-13], and FBE keys must
            // never be never reused for different cryptographic purposes
            // [CDD 9.9.3/C-1-14].  Effectively, these requirements mean that
            // the fscrypt policy version must not be v1.  If this part of the
            // test fails, make sure the device's fstab doesn't contain the "v1"
            // flag in the argument to the fileencryption option.
            if (first_api_level < __ANDROID_API_R__) {
                GTEST_LOG_(INFO) << "Exempt from non-reversible FBE key derivation due to old "
                                    "starting API level";
                // On these old devices we also allow the use of some custom
                // encryption mode numbers which were never supported by the
                // Android common kernel and shouldn't be used on new devices.
                allow_legacy_modes = true;
            } else {
                ADD_FAILURE() << "Device isn't using non-reversible FBE key derivation";
            }
            break;
        case FSCRYPT_POLICY_V2:
            GTEST_LOG_(INFO) << "Detected v2 encryption policy";
            contents_mode = arg.policy.v2.contents_encryption_mode;
            filenames_mode = arg.policy.v2.filenames_encryption_mode;
            flags = arg.policy.v2.flags;
            break;
        default:
            FAIL() << "Unknown encryption policy version: " << arg.policy.version;
    }

    GTEST_LOG_(INFO) << "Contents encryption mode: " << contents_mode;
    GTEST_LOG_(INFO) << "Filenames encryption mode: " << filenames_mode;

    validateEncryptionModes(contents_mode, filenames_mode, allow_legacy_modes);
    validateEncryptionFlags(flags, false);

    validateAdoptableStorageSettings(first_api_level);
}
