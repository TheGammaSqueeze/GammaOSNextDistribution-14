/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "trusty_secure_deletion_secret_storage.h"

#include <inttypes.h>

#include <array>
#include <optional>
#include <vector>

#include <lib/storage/storage.h>
#include <uapi/err.h>

#include <keymaster/logger.h>
#include <keymaster/random_source.h>

namespace keymaster {

namespace {

// Maximum number of attempts to perform a secure storage transaction to read or
// delete a secure deletion secret.  Because the storageproxy may be restarted
// while this code is running, it may be necessary to retry.  But because it's
// unclear exactly what error codes may be returned when the proxy is shut down,
// we conservatively retry all unexpected errors.  To avoid an infinite loop, we
// set a limit on the number of retries (though hitting the limit and returning
// an error will likely break the boot anyway).  Ideally, we should never need
// more than one retry.  We allow three.
constexpr size_t kMaxTries = 3;

// Name of the file to store secrets. The "_1" suffix is to allow for new file
// formats/versions in the future.
constexpr char kSecureDeletionSecretFileName[] = "SecureDeletionSecrets_1";

// Each secret is 16 bytes.
constexpr storage_off_t kSecretSize = 16;

//  The factory reset secret is composed of two secrets, so 32 bytes, and it's
//  stored at offset 0.
constexpr storage_off_t kFactoryResetSecretSize = kSecretSize * 2;
constexpr storage_off_t kFactoryResetSecretPos = 0;
constexpr storage_off_t kFirstSecureDeletionSecretPos =
        kFactoryResetSecretPos + kFactoryResetSecretSize;

// We read secrets in blocks of 32, so 512 bytes.
constexpr storage_off_t kBlockSize = kSecretSize * 32;

// Limit file size to 16 KiB (except for key upgrades, see
// kMaxSecretFileSizeForUpgrades).
constexpr storage_off_t kMaxSecretFileSize = kBlockSize * 32;

// This is a higher file size limit, with the space above kMaxSecretFileSize
// usable only for key IDs that need to be written as part of a key upgrade.
// This is to reduce the probability that keys are degraded as a result of
// upgrading.
constexpr storage_off_t kMaxSecretFileSizeForUpgrades =
        kMaxSecretFileSize + 8 * kBlockSize;

// We set a bit in the first byte of each slot to indicate that the slot is in
// use.  This reduces the maximum entropy of each slot to 127 bits.
constexpr uint8_t kInUseFlag = 0x80;

/**
 * StorageFile represents a secure storage file, and provides operations on it.
 * Use StorageSession::OpenFile to create a StorageFile.
 */
class StorageFile {
public:
    StorageFile(const StorageFile&) = delete;
    StorageFile& operator=(const StorageFile&) = delete;

    StorageFile(StorageFile&& rhs)
            : fileHandle_(std::move(rhs.fileHandle_)),
              fileSize_(rhs.fileSize_) {
        rhs.fileHandle_ = std::nullopt;
        rhs.fileSize_ = 0;
    }

    ~StorageFile() { CloseFile(); }

    /**
     * Close the file.
     *
     * Note that normally it's not necessary to call this, because the dtor
     * will.
     */
    void CloseFile() {
        if (!fileHandle_) {
            return;
        }

        LOG_D("Closing file handle %" PRIu64, *fileHandle_);
        storage_close_file(*fileHandle_);
        fileHandle_ = std::nullopt;
    }

    /**
     * Return size of file.
     */
    storage_off_t size() const { return fileSize_; }

    /**
     * Reads a block of bytes from the file.  On error returns std::nullopt.
     */
    std::optional<Buffer> ReadBlock(storage_off_t readPos,
                                    storage_off_t bytesToRead) const {
        if (!fileHandle_) {
            return std::nullopt;
        }

        Buffer buf(bytesToRead);
        if (buf.buffer_size() < bytesToRead) {
            LOG_E("Error memory allocation failed trying to allocate ReadBlock buffer.");
            return std::nullopt;
        }

        ssize_t bytesRead = storage_read(
                *fileHandle_, readPos, buf.peek_write(), buf.available_write());
        if (bytesRead < 0) {
            LOG_E("Error %zd reading file", bytesRead);
            return std::nullopt;
        } else if (static_cast<size_t>(bytesRead) < bytesToRead) {
            LOG_E("Error attempt to read %" PRIu64
                  " bytes returned only %zd bytes",
                  bytesToRead, bytesRead);
            return std::nullopt;
        }

        if (!buf.advance_write(bytesRead)) {
            LOG_E("Failed to update buffer write position. Code error.");
            return std::nullopt;
        }
        return buf;
    }

    /**
     * Write a block of bytes from `data` of size `size` to storage at offset
     * `pos`.  Will not extend the file.
     *
     * Returns false if the write would go past the end of the file, or if an
     * error occurs (all errors are logged).
     */
    bool WriteBlock(storage_off_t pos, const uint8_t* data, size_t size) const {
        if (!fileHandle_) {
            LOG_E("Attempt to write to invalid file handle");
            return false;
        }

        storage_off_t end;
        if (__builtin_add_overflow(pos, size, &end) || end > fileSize_) {
            LOG_E("Attempt to write past EOF");
            return false;
        }

        ssize_t bytesWritten =
                storage_write(*fileHandle_, pos, data, size, 0 /* opflags */);
        if (bytesWritten < 0) {
            LOG_E("Error %zd writing rollback record at offset %" PRIu64,
                  bytesWritten, pos);
            return false;
        } else if (static_cast<size_t>(bytesWritten) < size) {
            LOG_E("Wrote %zd of %zu bytes", bytesWritten, size);
            return false;
        }

        return true;
    }

    /**
     * Resize the file to `newSize` bytes.
     *
     * Returns error code from uapi/err.h.  Errors are logged.
     */
    int Resize(storage_off_t newSize) {
        if (!fileHandle_) {
            LOG_E("Attempt to resize invalid file handle");
            return ERR_NOT_VALID;
        }

        int rc = storage_set_file_size(*fileHandle_, newSize, 0 /* opflags */);
        if (rc) {
            LOG_E("Error %d resizing file from %" PRIu64 " to %" PRIu64, rc,
                  fileSize_, newSize);
            return rc;
        }

        fileSize_ = newSize;
        return NO_ERROR;
    }

private:
    friend class StorageSession;

    StorageFile(file_handle_t fileHandle, storage_off_t fileSize)
            : fileHandle_(fileHandle), fileSize_(fileSize) {}

    std::optional<file_handle_t> fileHandle_;
    storage_off_t fileSize_;
};

/**
 * StorageSession represents a secure storage session, and provides methods to
 * manipulate files within the session, and to finalize a storage transaction.
 */
class StorageSession {
public:
    // Error codes used by DeleteFile.
    enum class Error : uint32_t {
        OK = 0,
        NOT_FOUND = 1,
        UNKNOWN = 2,
    };

    StorageSession(const StorageSession&) = delete;
    StorageSession(StorageSession&& rhs) : session_(rhs.session_) {
        rhs.session_ = STORAGE_INVALID_SESSION;
    }
    ~StorageSession() {
        if (session_ != STORAGE_INVALID_SESSION) {
            LOG_D("Closing storage session %" PRIu32, session_);
            storage_close_session(session_);
        }
    }

    /**
     * Creates a session, connected to the specified port.
     *
     * There is a possibility that the port has not been created when this
     * method is called.  In this case its behavior is determined by the
     * `waitForPort` argument:
     *
     * - if true, the method will block until the port is available and the
     *   connection is completed.
     *
     * - if false, the method will return std::nullopt immediately.
     *
     * If the port exists, this method will block until the connection is
     * completed.
     */
    static std::optional<StorageSession> CreateSession(
            bool waitForPort = true,
            const char* port = STORAGE_CLIENT_TP_PORT) {
        LOG_D("Opening storage session on port %s (wait: %s)", port,
              waitForPort ? "true" : "false");
        // We use connect rather than storage_open_session because the latter
        // always waits for the port.
        long rc = connect(port, waitForPort ? IPC_CONNECT_WAIT_FOR_PORT : 0);
        if (rc < 0) {
            LOG_E("Error %ld opening storage session on port %s.", rc, port);
            return std::nullopt;
        }
        storage_session_t session = static_cast<storage_session_t>(rc);
        LOG_D("Opened storage session %" PRIu32, session);
        return StorageSession(session);
    }

    /**
     * Open a file.  Returns std::nullopt on failure (after logging error code).
     */
    std::optional<StorageFile> OpenFile(
            const char* fileName,
            uint32_t flags = STORAGE_FILE_OPEN_CREATE) const {
        file_handle_t fileHandle;
        int err = storage_open_file(session_, &fileHandle, fileName, flags,
                                    0 /* opflags */);
        if (err) {
            LOG_E("Error %d opening file %s", err, fileName);
            return std::nullopt;
        }
        LOG_D("Opened file %s with handle %" PRIu64, fileName, fileHandle);

        storage_off_t fileSize;
        err = storage_get_file_size(fileHandle, &fileSize);
        if (err) {
            LOG_E("Error %d reading size of file %s", err, fileName);
            storage_close_file(fileHandle);
            return std::nullopt;
        }
        return StorageFile(fileHandle, fileSize);
    }

    /**
     * Delete a file.
     *
     * Returns Error::OK on success, Error::NOT_FOUND, if the file did not
     * exist, Error::UNKNOWN in any other case.
     */
    Error DeleteFile(const char* fileName) const {
        int rc = storage_delete_file(session_, fileName, 0 /* opflags */);
        if (rc < 0) {
            LOG_E("Error (%d) deleting file %s", rc, fileName);
            if (rc == ERR_NOT_FOUND) {
                return Error::NOT_FOUND;
            } else {
                return Error::UNKNOWN;
            }
        }
        return Error::OK;
    }

    /**
     * Ends current transaction (any uncommitted changes in this session),
     * either committing or aborting (rolling back) the changes, as specified by
     * `commit`.
     *
     * Note failing to commit the transaction will result in it being lost.
     *
     * Returns true on success, false on failure.
     */
    bool EndTransaction(bool commit) {
        int rc = storage_end_transaction(session_, commit);
        if (rc < 0) {
            LOG_E("Error (%d) committing transaction", rc);
            return false;
        }
        return true;
    }

private:
    StorageSession(storage_session_t session) : session_(session) {}

    storage_session_t session_;
};

/**
 * Zeros each secret from position `begin` to position `end`.
 */
bool zero_entries(const StorageFile& file,
                  storage_off_t begin,
                  storage_off_t end) {
    if (begin % kSecretSize != 0) {
        LOG_S("zero_entries called with invalid offset %" PRIu64, begin);
        return false;
    }

    for (storage_off_t pos = begin; pos < end; pos += kSecretSize) {
        uint8_t zero_buf[kSecretSize] = {0x00, 0x00, 0x00, 0x00,  //
                                         0x00, 0x00, 0x00, 0x00,  //
                                         0x00, 0x00, 0x00, 0x00,  //
                                         0x00, 0x00, 0x00, 0x00};
        if (!file.WriteBlock(pos, zero_buf, sizeof(zero_buf))) {
            LOG_E("Failed to zero secret at offset %" PRIu64, pos);
            return false;
        }
    }

    return true;
}

/**
 * Finds an empty slot in file.  Returns empty on error, 0 on failure to
 * find an empty slot and a valid (>0) slot number otherwise.
 */
std::optional<uint32_t /* keySlot */> find_empty_slot(const StorageFile& file,
                                                      bool isUpgrade) {
    storage_off_t end =
            std::min(file.size(), isUpgrade ? kMaxSecretFileSizeForUpgrades
                                            : kMaxSecretFileSize);

    uint32_t retval = 0;
    for (storage_off_t filePos = 0; filePos < end; filePos += kBlockSize) {
        std::optional<Buffer> block = file.ReadBlock(filePos, kBlockSize);
        if (!block) {
            LOG_E("Failed to read block of secrets");
            return std::nullopt;
        }

        size_t blockPos =
                filePos == kFactoryResetSecretPos ? kFactoryResetSecretSize : 0;
        for (; blockPos < block->available_read(); blockPos += kSecretSize) {
            uint8_t first_byte = *(block->begin() + blockPos);
            if ((first_byte & kInUseFlag) == 0 && retval == 0) {
                static_assert(kBlockSize % kSecretSize == 0 &&
                              kFactoryResetSecretSize % kSecretSize == 0);
                retval = static_cast<uint32_t>((filePos + blockPos) /
                                               kSecretSize);
            }
        }
    }

    return retval;
}

// Helper class that calls the provided function (probably a lambda) on
// destruction if not disarmed.
template <typename F>
class OnExit {
public:
    OnExit(F f) : f_(f) {}
    ~OnExit() {
        if (!disarmed_) {
            f_();
        }
    }

    void disarm() { disarmed_ = true; }

private:
    F f_;
    bool disarmed_ = false;
};

}  // namespace

bool TrustySecureDeletionSecretStorage::LoadOrCreateFactoryResetSecret(
        bool wait_for_port) const {
    if (factory_reset_secret_) {
        // Already read.
        return true;
    }

    LOG_D("Trying to open a session to read factory reset secret");
    std::optional<StorageSession> session =
            StorageSession::CreateSession(wait_for_port);
    if (!session) {
        return false;
    }

    LOG_D("Trying to open secure secrets file");
    std::optional<StorageFile> file =
            session->OpenFile(kSecureDeletionSecretFileName);
    if (!file) {
        // This shouldn't be possible, unless maybe the session just went away?
        LOG_E("Can't open secure secrets file.");
        return false;
    }

    if (file->size() > 0) {
        LOG_D("Opened non-empty secure secrets file.");
        std::optional<Buffer> block = file->ReadBlock(kFactoryResetSecretPos,
                                                      kFactoryResetSecretSize);
        if (!block) {
            LOG_E("Failed to read factory reset secret");
            return false;
        }

        LOG_D("Read factory-reset secret, size %zu", block->available_read());
        factory_reset_secret_ = std::move(block);
        return true;
    }

    // The file was just created.  Need to create the factory reset secret.
    LOG_I("Created new secure secrets file, size %" PRIu64, file->size());
    if (file->Resize(kBlockSize) != NO_ERROR) {
        LOG_E("Failed to grow new file from 0 to %" PRIu64 " bytes",
              kBlockSize);
        return false;
    }
    LOG_D("Resized secure secrets file to size %" PRIu64, file->size());

    static_assert(kBlockSize >= kFactoryResetSecretSize);
    Buffer buf(kFactoryResetSecretSize);
    keymaster_error_t error =
            random_.GenerateRandom(buf.peek_write(), buf.available_write());
    if (error != KM_ERROR_OK || !buf.advance_write(kFactoryResetSecretSize)) {
        LOG_E("Failed to generate %" PRIu64
              " random bytes for factory reset secret",
              kFactoryResetSecretSize);
        return false;
    }

    if (!file->WriteBlock(kFactoryResetSecretPos, buf.peek_read(),
                          buf.available_read())) {
        LOG_E("Failed to write factory reset secret");
        return false;
    }
    LOG_D("Wrote new factory reset secret.");

    if (!zero_entries(*file, kFirstSecureDeletionSecretPos /* begin */,
                      kBlockSize /* end */)) {
        LOG_E("Failed to zero secure deletion secret entries in first block");
        return false;
    }
    LOG_D("Zeroed secrets.");

    if (!session->EndTransaction(true /* commit */)) {
        LOG_E("Failed to commit transaction creating secure secrets file");
        return false;
    }
    LOG_D("Committed new secrets file.");

    LOG_I("Got factory reset secret of size %zu", buf.buffer_size());
    factory_reset_secret_ = std::move(buf);
    return true;
}

std::optional<SecureDeletionData>
TrustySecureDeletionSecretStorage::CreateDataForNewKey(bool secure_deletion,
                                                       bool is_upgrade) const {
    if (!LoadOrCreateFactoryResetSecret(false /* wait_for_port */) ||
        !factory_reset_secret_) {
        // Unable to get factory reset secret from secure storage.
        LOG_I("Unable to get factory reset secret");
        return std::nullopt;
    }

    SecureDeletionData retval;
    retval.factory_reset_secret.Reinitialize(*factory_reset_secret_);

    if (!secure_deletion) {
        LOG_D("Secure deletion not requested.");
        return retval;
    }

    retval.secure_deletion_secret.reserve(kSecretSize);
    if (retval.secure_deletion_secret.buffer_size() == 0) {
        return std::nullopt;
    }

    keymaster_error_t error = random_.GenerateRandom(
            retval.secure_deletion_secret.peek_write(),
            retval.secure_deletion_secret.available_write());
    if (error != KM_ERROR_OK) {
        // Ths really shouldn't be possible.  Perhaps we should abort()?
        LOG_E("Failed to create secure deletion secret");
        return std::nullopt;
    }
    retval.secure_deletion_secret.peek_write()[0] |= kInUseFlag;
    retval.secure_deletion_secret.advance_write(
            retval.secure_deletion_secret.available_write());

    auto sds_cleanup = OnExit([&]() { retval.secure_deletion_secret.Clear(); });

    std::optional<StorageSession> session =
            StorageSession::CreateSession();  // Will block

    if (!session) {
        LOG_E("Failed to open session in CreateDateForNewKey");
        return retval;
    }
    LOG_D("Opened session to store secure deletion secret.");

    std::optional<StorageFile> file =
            session->OpenFile(kSecureDeletionSecretFileName);
    if (!file) {
        LOG_E("Failed to open file in CreateDateForNewKey");
        return retval;
    }
    LOG_D("Opened file to store secure deletion secret.");

    std::optional<uint32_t> keySlot = find_empty_slot(*file, is_upgrade);
    if (!keySlot) {
        LOG_E("Error while searching for key slot");
        return retval;
    }

    if (*keySlot == 0) {
        bool can_resize =
                file->size() < kMaxSecretFileSize ||
                (is_upgrade && file->size() < kMaxSecretFileSizeForUpgrades);

        if (!can_resize) {
            LOG_E("Didn't find a slot and can't grow the file larger than %" PRIu64,
                  file->size());
            return retval;
        }

        storage_off_t old_size = file->size();
        LOG_D("Attempting to resize file from %" PRIu64 " to %" PRIu64,
              file->size(), file->size() + kBlockSize);
        int rc = file->Resize(old_size + kBlockSize);
        if (rc != NO_ERROR) {
            LOG_E("Failed (%d) to grow file to make room for a key slot", rc);
            return retval;
        }
        LOG_D("Resized file to %" PRIu64, file->size());

        if (!zero_entries(*file, old_size, file->size())) {
            LOG_E("Error zeroing space in extended file");
            return retval;
        }

        keySlot = old_size / kSecretSize;
    }

    LOG_D("Writing new deletion secret to key slot %u", *keySlot);
    if (!file->WriteBlock(*keySlot * kSecretSize,
                          retval.secure_deletion_secret.peek_read(),
                          retval.secure_deletion_secret.available_read())) {
        LOG_E("Failed to write new deletion secret to key slot %u", *keySlot);
        return retval;
    }

    if (!session->EndTransaction(true /* commit */)) {
        LOG_E("Failed to commit transaction writing new deletion secret to slot %u",
              *keySlot);
        return retval;
    }
    LOG_D("Committed new secret.");

    sds_cleanup.disarm();  // Secure deletion secret written; no need to wipe.
    retval.key_slot = *keySlot;
    return retval;
}

SecureDeletionData TrustySecureDeletionSecretStorage::GetDataForKey(
        const uint32_t key_slot) const {
    for (size_t tries = 0; tries < kMaxTries && !factory_reset_secret_;
         ++tries) {
        LoadOrCreateFactoryResetSecret(true /* waitForPort */);
    }
    if (!factory_reset_secret_) {
        return SecureDeletionData{};
    }

    SecureDeletionData retval;
    retval.key_slot = key_slot;
    retval.factory_reset_secret.Reinitialize(*factory_reset_secret_);

    bool secureDeletionSecretRequested = (key_slot != 0);
    if (!secureDeletionSecretRequested) {
        LOG_D("Secure deletion not requested.");
        return retval;
    }

    LOG_D("Need to read secure deletion secret from slot %u", retval.key_slot);

    for (size_t tries = 0; tries < kMaxTries; ++tries) {
        std::optional<StorageSession> session =
                StorageSession::CreateSession();  // Will block
        if (!session) {
            LOG_E("Failed to open session to get secure deletion data.");
            continue;
        }

        std::optional<StorageFile> file =
                session->OpenFile(kSecureDeletionSecretFileName);
        if (!file) {
            LOG_E("Failed to open file to get secure deletion data.");
            continue;
        }

        storage_off_t keySlotBegin = retval.key_slot * kSecretSize;
        storage_off_t keySlotEnd = keySlotBegin + kSecretSize;
        if (keySlotEnd > file->size()) {
            LOG_E("Invalid key slot %u would read past end of file of size %" PRIu64,
                  retval.key_slot, file->size());
            return retval;  // Empty secure_deletion_secret, key decryption will
                            // fail.
        }

        std::optional<Buffer> secret =
                file->ReadBlock(retval.key_slot * kSecretSize, kSecretSize);
        if (!secret) {
            LOG_E("Failed to read secret from slot %u", retval.key_slot);
            continue;
        }

        LOG_D("Read secure deletion secret, size: %zu",
              secret->available_read());
        retval.secure_deletion_secret = std::move(*secret);
        break;
    }

    return retval;
}

void TrustySecureDeletionSecretStorage::DeleteKey(uint32_t key_slot) const {
    if (key_slot == 0) {
        LOG_D("key_slot == 0, nothing to delete");
        return;
    }

    for (;;) {
        std::optional<StorageSession> session =
                StorageSession::CreateSession();  // Will block
        if (!session) {
            LOG_E("Failed to open session to retrieve secure deletion data.");
            continue;
        }

        std::optional<StorageFile> file =
                session->OpenFile(kSecureDeletionSecretFileName);
        if (!file) {
            LOG_E("Failed to open file to retrieve secure deletion data.");
            continue;
        }

        storage_off_t key_slot_begin = key_slot * kSecretSize;
        storage_off_t key_slot_end = key_slot_begin + kSecretSize;
        if (key_slot_begin <
                    kFactoryResetSecretPos + kFactoryResetSecretSize  //
            || key_slot_end > file->size()) {
            LOG_E("Attempted to delete invalid key slot %u", key_slot);
            return;
        }

        if (!zero_entries(*file, key_slot_begin, key_slot_end)) {
            continue;
        }
        LOG_D("Deleted secure key slot %u, zeroing %" PRIu64 " to %" PRIu64,
              key_slot, key_slot_begin, key_slot_end);

        if (!session->EndTransaction(true /* commit */)) {
            LOG_E("Failed to commit transaction deleting key at slot %u",
                  key_slot);
            continue;
        }
        LOG_D("Committed deletion");

        return;
    }
}

void TrustySecureDeletionSecretStorage::DeleteAllKeys() const {
    for (;;) {
        std::optional<StorageSession> session =
                StorageSession::CreateSession();  // Will block
        if (!session) {
            LOG_E("Failed to open session to delete secrets file.");
            continue;
        }
        LOG_D("Opened session to delete secrets file.");

        auto error = session->DeleteFile(kSecureDeletionSecretFileName);
        if (error == StorageSession::Error::OK) {
            LOG_D("Deleted secrets file");

            if (!session->EndTransaction(true /* commit */)) {
                LOG_E("Failed to commit deletion of secrets file.");
            }
            LOG_D("Committed deletion of secrets file.");
        } else if (error == StorageSession::Error::NOT_FOUND) {
            // File does not exist, may as well abandon the session.
            LOG_D("No secrets file existed.");
        } else {
            // Assuming transient error. Log and retry.
            LOG_E("Failed to delete secrets file");
            continue;
        }

        // Success
        factory_reset_secret_ = {};
        return;
    }
}

}  // namespace keymaster
