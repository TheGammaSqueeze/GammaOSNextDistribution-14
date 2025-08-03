/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef AVB_MESSAGES_H_
#define AVB_MESSAGES_H_

#include <stdint.h>
#include <uapi/err.h>

#include <UniquePtr.h>

// Message serialization objects for communicating with Android
// Verified Boot app.
namespace avb {

// error codes for AVB protocol
enum class AvbError : uint32_t {
    kNone = 0,      // All OK
    kInvalid = 1,   // Invalid input, e.g. slot error is not valid.
    kInternal = 2,  // Error occurred during an operation in Trusty
};

// Abstract base class of all AVB messages.
class AvbMessage {
public:
    // Returns serialized size in bytes of the current state of the
    // object.
    virtual uint32_t GetSerializedSize() const = 0;

    // Converts the object into its serialized representation. Returns
    // number of bytes serialized. |payload| points to the start of
    // the the message buffer, |end| points past the end of the message
    // buffer.
    virtual uint32_t Serialize(uint8_t* payload, const uint8_t* end) const = 0;

    // Inflates the object from its serial representation. |payload| points to
    // start of the message buffer, |end| points past the end of the message
    // buffer. Returns a Trusty error.
    virtual int Deserialize(const uint8_t* payload, const uint8_t* end) = 0;

    void set_error(AvbError error) { error_ = error; }
    AvbError get_error() const { return error_; }

private:
    AvbError error_ = AvbError::kNone;
};

class RollbackIndexRequest : public AvbMessage {
public:
    RollbackIndexRequest() {}
    RollbackIndexRequest(uint32_t slot, uint64_t value)
            : value_(value), slot_(slot) {}

    uint32_t GetSerializedSize() const override;
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override;
    int Deserialize(const uint8_t* payload, const uint8_t* end) override;

    void set_slot(uint32_t slot) { slot_ = slot; }
    void set_value(uint64_t value) { value_ = value; }
    uint64_t get_value() const { return value_; }
    uint32_t get_slot() const { return slot_; }

private:
    uint64_t value_ = 0;  // Value to write to rollback index.
    uint32_t slot_ = 0;   // Slot number of requested rollback index.
};

class RollbackIndexResponse : public AvbMessage {
public:
    RollbackIndexResponse() {}
    RollbackIndexResponse(uint64_t value) : value_(value) {}

    uint32_t GetSerializedSize() const override;
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override;
    int Deserialize(const uint8_t* payload, const uint8_t* end) override;

    void set_value(uint64_t value) { value_ = value; }
    uint64_t get_value() { return value_; }

private:
    uint64_t value_ = 0;  // Value of requested rollback index.
};

class EmptyMessage : public AvbMessage {
public:
    EmptyMessage() {}

    uint32_t GetSerializedSize() const override { return 0; }
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override {
        return 0;
    }
    int Deserialize(const uint8_t* payload, const uint8_t* end) override {
        return NO_ERROR;
    }
};

class GetVersionRequest : public EmptyMessage {};

class GetVersionResponse : public AvbMessage {
public:
    GetVersionResponse() {}
    GetVersionResponse(uint32_t version) : version_(version) {}

    uint32_t GetSerializedSize() const override;
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override;
    int Deserialize(const uint8_t* payload, const uint8_t* end) override;

    void set_version(uint32_t version) { version_ = version; }
    uint32_t get_version() { return version_; }

private:
    uint32_t version_ = 0;
};

class PermanentAttributesMessage : public AvbMessage {
public:
    PermanentAttributesMessage() {}

    uint32_t GetSerializedSize() const override;
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override;
    int Deserialize(const uint8_t* payload, const uint8_t* end) override;

    uint32_t get_attributes_size() const { return attributes_size_; }
    uint8_t* get_attributes_buf() const { return attributes_.get(); }
    int set_attributes_buf(const uint8_t* buf, const uint32_t size) {
        return Deserialize(buf, buf + size);
    }

private:
    UniquePtr<uint8_t[]> attributes_;
    uint32_t attributes_size_ = 0;
};

class WritePermanentAttributesRequest : public PermanentAttributesMessage {};
class WritePermanentAttributesResponse : public EmptyMessage {};

class ReadPermanentAttributesRequest : public EmptyMessage {};
class ReadPermanentAttributesResponse : public PermanentAttributesMessage {};

class LockStateMessage : public AvbMessage {
public:
    LockStateMessage() {}

    uint32_t GetSerializedSize() const override;
    uint32_t Serialize(uint8_t* payload, const uint8_t* end) const override;
    int Deserialize(const uint8_t* payload, const uint8_t* end) override;

    uint8_t get_lock_state() const { return lock_state_; }
    void set_lock_state(uint8_t lock_state) { lock_state_ = lock_state; }

private:
    uint8_t lock_state_ = 0;
};

class ReadLockStateRequest : public EmptyMessage {};
class ReadLockStateResponse : public LockStateMessage {};

class WriteLockStateRequest : public LockStateMessage {};
class WriteLockStateResponse : public EmptyMessage {};

}  // namespace avb

#endif  // AVB_MESSAGES_H_
