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

#include <inttypes.h>
#include <linux/bpf.h>
#include <test/mock_bpf_helpers.h>
#include <map>
#include <unordered_map>
#include <vector>

struct ByteArrayHash {
    std::size_t operator()(std::vector<uint8_t> const& bytes) const {
        size_t result = 0;
        for (size_t i = 0; i < bytes.size(); i++) {
            result = (result * 31) ^ bytes[i];
        }
        return result;
    }
};

typedef std::unordered_map<std::vector<uint8_t>, std::vector<uint8_t>, ByteArrayHash> byteArrayMap;

struct mock_bpf_map {
    uint32_t type;
    size_t key_size;
    size_t value_size;

    // Per-CPU hash map.  Cross-CPU maps have just one key-value pair, the key being 0.
    std::map<uint32_t, byteArrayMap> map;
};

static uint64_t gKtimeNs;
static uint32_t gSmpProcessorId;
static uint32_t gUid;
static uint32_t gPidTgid;

uint64_t bpf_ktime_get_ns() {
    return gKtimeNs;
}

void mock_bpf_set_ktime_ns(uint64_t time_ns) {
    gKtimeNs = time_ns;
}

void mock_bpf_set_smp_processor_id(uint32_t cpu) {
    gSmpProcessorId = cpu;
}

uint64_t bpf_get_smp_processor_id() {
    return gSmpProcessorId;
}

void mock_bpf_set_current_uid_gid(uint32_t uid) {
    gUid = uid;
}

uint64_t bpf_get_current_uid_gid() {
    return gUid;
}

void mock_bpf_set_current_pid_tgid(uint64_t pid_tgid) {
    gPidTgid = pid_tgid;
}

uint64_t bpf_get_current_pid_tgid() {
    return gPidTgid;
}

mock_bpf_map_t mock_bpf_map_create(uint32_t key_size, uint32_t value_size, uint32_t type) {
    mock_bpf_map* map = new mock_bpf_map();
    map->type = type;
    map->key_size = key_size;
    map->value_size = value_size;
    return map;
}

static byteArrayMap& getCurrentMap(mock_bpf_map* map) {
    if (map->type == BPF_MAP_TYPE_PERCPU_HASH || map->type == BPF_MAP_TYPE_PERCPU_ARRAY) {
        return map->map[gSmpProcessorId];
    } else {
        return map->map[0];
    }
}

void* mock_bpf_lookup_elem(mock_bpf_map_t mock_map, void* key) {
    mock_bpf_map* map = (mock_bpf_map*)mock_map;
    std::vector<uint8_t> keyVector(map->key_size);
    memcpy(keyVector.data(), key, map->key_size);
    byteArrayMap& currentMap = getCurrentMap(map);
    if (currentMap.find(keyVector) == currentMap.end()) {
        return NULL;
    }
    return currentMap[keyVector].data();
}

int mock_bpf_update_elem(mock_bpf_map_t mock_map, void* key, void* value, uint64_t flags) {
    mock_bpf_map* map = (mock_bpf_map*)mock_map;
    std::vector<uint8_t> keyVector(map->key_size);
    memcpy(keyVector.data(), key, map->key_size);
    std::vector<uint8_t> value_vector(map->value_size);
    memcpy(value_vector.data(), value, map->value_size);

    byteArrayMap& currentMap = getCurrentMap(map);
    if (flags & BPF_EXIST) {
        if (currentMap.find(keyVector) == currentMap.end()) {
            return 0;
        }
    } else if (flags & BPF_NOEXIST) {
        if (currentMap.find(keyVector) != currentMap.end()) {
            return 0;
        }
    }
    currentMap[keyVector] = value_vector;
    return 1;
}

int mock_bpf_delete_elem(mock_bpf_map_t mock_map, void* key) {
    mock_bpf_map* map = (mock_bpf_map*)mock_map;
    std::vector<uint8_t> keyVector(map->key_size);
    memcpy(keyVector.data(), key, map->key_size);

    byteArrayMap& currentMap = getCurrentMap(map);
    return currentMap.erase(keyVector);
}
