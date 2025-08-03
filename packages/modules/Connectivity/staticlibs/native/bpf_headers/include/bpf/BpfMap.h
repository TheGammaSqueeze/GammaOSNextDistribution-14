/*
 * Copyright (C) 2018 The Android Open Source Project
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

#pragma once

#include <linux/bpf.h>

#include <android/log.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>

#include "BpfSyscallWrappers.h"
#include "bpf/BpfUtils.h"

#include <functional>

namespace android {
namespace bpf {

using base::Result;
using base::unique_fd;
using std::function;

// This is a class wrapper for eBPF maps. The eBPF map is a special in-kernel
// data structure that stores data in <Key, Value> pairs. It can be read/write
// from userspace by passing syscalls with the map file descriptor. This class
// is used to generalize the procedure of interacting with eBPF maps and hide
// the implementation detail from other process. Besides the basic syscalls
// wrapper, it also provides some useful helper functions as well as an iterator
// nested class to iterate the map more easily.
//
// NOTE: A kernel eBPF map may be accessed by both kernel and userspace
// processes at the same time. Or if the map is pinned as a virtual file, it can
// be obtained by multiple eBPF map class object and accessed concurrently.
// Though the map class object and the underlying kernel map are thread safe, it
// is not safe to iterate over a map while another thread or process is deleting
// from it. In this case the iteration can return duplicate entries.
template <class Key, class Value>
class BpfMapRO {
  public:
    BpfMapRO<Key, Value>() {};

    // explicitly force no copy constructor, since it would need to dup the fd
    // (later on, for testing, we still make available a copy assignment operator)
    BpfMapRO<Key, Value>(const BpfMapRO<Key, Value>&) = delete;

  protected:
    void abortOnMismatch(bool writable) const {
        (void) writable;
    }

  public:
    bool isOk(bool writable = false) const {
        if (!mMapFd.ok()) return false;
        if (isAtLeastKernelVersion(4, 14, 0)) {
            int flags = bpfGetFdMapFlags(mMapFd);
            if (flags < 0) return false;
            if (flags & BPF_F_WRONLY) return false;
            if (writable && (flags & BPF_F_RDONLY)) return false;
            if (bpfGetFdKeySize(mMapFd) != sizeof(Key)) return false;
            if (bpfGetFdValueSize(mMapFd) != sizeof(Value)) return false;
        }
        return true;
    }
    explicit BpfMapRO<Key, Value>(const char* pathname) {
        mMapFd.reset(mapRetrieveRO(pathname));
    }

    Result<Key> getFirstKey() const {
        Key firstKey;
        if (getFirstMapKey(mMapFd, &firstKey)) {
            return ErrnoErrorf("BpfMap::getFirstKey() failed");
        }
        return firstKey;
    }

    Result<Key> getNextKey(const Key& key) const {
        Key nextKey;
        if (getNextMapKey(mMapFd, &key, &nextKey)) {
            return ErrnoErrorf("BpfMap::getNextKey() failed");
        }
        return nextKey;
    }

    Result<Value> readValue(const Key key) const {
        Value value;
        if (findMapEntry(mMapFd, &key, &value)) {
            return ErrnoErrorf("BpfMap::readValue() failed");
        }
        return value;
    }

  protected:
    [[clang::reinitializes]] Result<void> init(const char* path, int fd, bool writable) {
        mMapFd.reset(fd);
        if (!mMapFd.ok()) {
            return ErrnoErrorf("Pinned map not accessible or does not exist: ({})", path);
        }
        // Normally we should return an error here instead of calling abort,
        // but this cannot happen at runtime without a massive code bug (K/V type mismatch)
        // and as such it's better to just blow the system up and let the developer fix it.
        // Crashes are much more likely to be noticed than logs and missing functionality.
        abortOnMismatch(writable);
        return {};
    }

  public:
    // Function that tries to get map from a pinned path.
    [[clang::reinitializes]] Result<void> init(const char* path) {
        return init(path, mapRetrieveRO(path), /* writable */ false);
    }

    // Iterate through the map and handle each key retrieved based on the filter
    // without modification of map content.
    Result<void> iterate(
            const function<Result<void>(const Key& key,
                                        const BpfMapRO<Key, Value>& map)>& filter) const;

    // Iterate through the map and get each <key, value> pair, handle each <key,
    // value> pair based on the filter without modification of map content.
    Result<void> iterateWithValue(
            const function<Result<void>(const Key& key, const Value& value,
                                        const BpfMapRO<Key, Value>& map)>& filter) const;

#ifdef BPF_MAP_MAKE_VISIBLE_FOR_TESTING
    const unique_fd& getMap() const { return mMapFd; };

    // Copy assignment operator - due to need for fd duping, should not be used in non-test code.
    BpfMapRO<Key, Value>& operator=(const BpfMapRO<Key, Value>& other) {
        if (this != &other) mMapFd.reset(fcntl(other.mMapFd.get(), F_DUPFD_CLOEXEC, 0));
        return *this;
    }
#else
    BpfMapRO<Key, Value>& operator=(const BpfMapRO<Key, Value>&) = delete;
#endif

    // Move assignment operator
    BpfMapRO<Key, Value>& operator=(BpfMapRO<Key, Value>&& other) noexcept {
        if (this != &other) {
            mMapFd = std::move(other.mMapFd);
            other.reset();
        }
        return *this;
    }

#ifdef BPF_MAP_MAKE_VISIBLE_FOR_TESTING
    // Note that unique_fd.reset() carefully saves and restores the errno,
    // and BpfMap.reset() won't touch the errno if passed in fd is negative either,
    // hence you can do something like BpfMap.reset(systemcall()) and then
    // check BpfMap.isValid() and look at errno and see why systemcall() failed.
    [[clang::reinitializes]] void reset(int fd) {
        mMapFd.reset(fd);
        if (mMapFd.ok()) abortOnMismatch(/* writable */ false);  // false isn't ideal
    }

    // unique_fd has an implicit int conversion defined, which combined with the above
    // reset(int) would result in double ownership of the fd, hence we either need a custom
    // implementation of reset(unique_fd), or to delete it and thus cause compile failures
    // to catch this and prevent it.
    void reset(unique_fd fd) = delete;
#endif

    [[clang::reinitializes]] void reset() {
        mMapFd.reset();
    }

    bool isValid() const { return mMapFd.ok(); }

    Result<bool> isEmpty() const {
        auto key = getFirstKey();
        if (key.ok()) return false;
        if (key.error().code() == ENOENT) return true;
        return key.error();
    }

  protected:
    unique_fd mMapFd;
};

template <class Key, class Value>
Result<void> BpfMapRO<Key, Value>::iterate(
        const function<Result<void>(const Key& key,
                                    const BpfMapRO<Key, Value>& map)>& filter) const {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<void> status = filter(curKey.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
Result<void> BpfMapRO<Key, Value>::iterateWithValue(
        const function<Result<void>(const Key& key, const Value& value,
                                    const BpfMapRO<Key, Value>& map)>& filter) const {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<Value> curValue = readValue(curKey.value());
        if (!curValue.ok()) return curValue.error();
        Result<void> status = filter(curKey.value(), curValue.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
class BpfMap : public BpfMapRO<Key, Value> {
  protected:
    using BpfMapRO<Key, Value>::mMapFd;
    using BpfMapRO<Key, Value>::abortOnMismatch;

  public:
    using BpfMapRO<Key, Value>::getFirstKey;
    using BpfMapRO<Key, Value>::getNextKey;
    using BpfMapRO<Key, Value>::readValue;

    BpfMap<Key, Value>() {};

    explicit BpfMap<Key, Value>(const char* pathname) {
        mMapFd.reset(mapRetrieveRW(pathname));
        abortOnMismatch(/* writable */ true);
    }

    // Function that tries to get map from a pinned path.
    [[clang::reinitializes]] Result<void> init(const char* path) {
        return BpfMapRO<Key,Value>::init(path, mapRetrieveRW(path), /* writable */ true);
    }

    Result<void> writeValue(const Key& key, const Value& value, uint64_t flags) {
        if (writeToMapEntry(mMapFd, &key, &value, flags)) {
            return ErrnoErrorf("BpfMap::writeValue() failed");
        }
        return {};
    }

    Result<void> deleteValue(const Key& key) {
        if (deleteMapEntry(mMapFd, &key)) {
            return ErrnoErrorf("BpfMap::deleteValue() failed");
        }
        return {};
    }

    Result<void> clear() {
        while (true) {
            auto key = getFirstKey();
            if (!key.ok()) {
                if (key.error().code() == ENOENT) return {};  // empty: success
                return key.error();                           // Anything else is an error
            }
            auto res = deleteValue(key.value());
            if (!res.ok()) {
                // Someone else could have deleted the key, so ignore ENOENT
                if (res.error().code() == ENOENT) continue;
                ALOGE("Failed to delete data %s", strerror(res.error().code()));
                return res.error();
            }
        }
    }

#ifdef BPF_MAP_MAKE_VISIBLE_FOR_TESTING
    [[clang::reinitializes]] Result<void> resetMap(bpf_map_type map_type,
                                                   uint32_t max_entries,
                                                   uint32_t map_flags = 0) {
        if (map_flags & BPF_F_WRONLY) abort();
        if (map_flags & BPF_F_RDONLY) abort();
        mMapFd.reset(createMap(map_type, sizeof(Key), sizeof(Value), max_entries,
                               map_flags));
        if (!mMapFd.ok()) return ErrnoErrorf("BpfMap::resetMap() failed");
        abortOnMismatch(/* writable */ true);
        return {};
    }
#endif

    // Iterate through the map and handle each key retrieved based on the filter
    // without modification of map content.
    Result<void> iterate(
            const function<Result<void>(const Key& key,
                                        const BpfMap<Key, Value>& map)>& filter) const;

    // Iterate through the map and get each <key, value> pair, handle each <key,
    // value> pair based on the filter without modification of map content.
    Result<void> iterateWithValue(
            const function<Result<void>(const Key& key, const Value& value,
                                        const BpfMap<Key, Value>& map)>& filter) const;

    // Iterate through the map and handle each key retrieved based on the filter
    Result<void> iterate(
            const function<Result<void>(const Key& key,
                                        BpfMap<Key, Value>& map)>& filter);

    // Iterate through the map and get each <key, value> pair, handle each <key,
    // value> pair based on the filter.
    Result<void> iterateWithValue(
            const function<Result<void>(const Key& key, const Value& value,
                                        BpfMap<Key, Value>& map)>& filter);

};

template <class Key, class Value>
Result<void> BpfMap<Key, Value>::iterate(
        const function<Result<void>(const Key& key,
                                    const BpfMap<Key, Value>& map)>& filter) const {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<void> status = filter(curKey.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
Result<void> BpfMap<Key, Value>::iterateWithValue(
        const function<Result<void>(const Key& key, const Value& value,
                                    const BpfMap<Key, Value>& map)>& filter) const {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<Value> curValue = readValue(curKey.value());
        if (!curValue.ok()) return curValue.error();
        Result<void> status = filter(curKey.value(), curValue.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
Result<void> BpfMap<Key, Value>::iterate(
        const function<Result<void>(const Key& key,
                                    BpfMap<Key, Value>& map)>& filter) {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<void> status = filter(curKey.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

template <class Key, class Value>
Result<void> BpfMap<Key, Value>::iterateWithValue(
        const function<Result<void>(const Key& key, const Value& value,
                                    BpfMap<Key, Value>& map)>& filter) {
    Result<Key> curKey = getFirstKey();
    while (curKey.ok()) {
        const Result<Key>& nextKey = getNextKey(curKey.value());
        Result<Value> curValue = readValue(curKey.value());
        if (!curValue.ok()) return curValue.error();
        Result<void> status = filter(curKey.value(), curValue.value(), *this);
        if (!status.ok()) return status;
        curKey = nextKey;
    }
    if (curKey.error().code() == ENOENT) return {};
    return curKey.error();
}

}  // namespace bpf
}  // namespace android
