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

#include <android-base/file.h>
#include <android-base/mapped_file.h>
#include <benchmark/benchmark.h>
#include <unistd.h>

#include "incfs_support/access.h"
#include "incfs_support/signal_handling.h"
#include "util/map_ptr.h"

static std::unique_ptr<TemporaryFile> makeFile() {
    auto tmp = std::unique_ptr<TemporaryFile>(new TemporaryFile());
    char c = 1;
    write(tmp->fd, &c, sizeof(c));
    return tmp;
}

static std::pair<std::unique_ptr<TemporaryFile>, std::unique_ptr<android::base::MappedFile>>
makeEmptyFileMapping() {
    auto tmp = makeFile();
    // mmap() only works for non-empty files, but it's "ok" to resize it back to empty afterwards
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    ftruncate(tmp->fd, 0);
    return {std::move(tmp), std::move(mapping)};
}

static void TestEmpty(benchmark::State& state) {
    int val = 0;
    for (auto _ : state) {
        benchmark::DoNotOptimize(val += 1);
    }
}
BENCHMARK(TestEmpty);

static void TestSignal(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);

    int val = 0;
    for (auto _ : state) {
        SCOPED_SIGBUS_HANDLER({ break; });
        val += *mapping->data();
    }
}
BENCHMARK(TestSignal);

static void TestRead(benchmark::State& state) {
    auto tmp = makeFile();
    int val = 0;
    for (auto _ : state) {
        char c;
        pread(tmp->fd, &c, sizeof(c), 0);
        val += c;
    }
}
BENCHMARK(TestRead);

static void TestMapPtrRaw(benchmark::State& state) {
    auto tmp = makeFile();
    android::incfs::IncFsFileMap map;
    map.CreateForceVerification(tmp->fd, 0, 1, tmp->path, true);
    int val = 0;
    const uint8_t* prev_block = nullptr;
    for (auto _ : state) {
        auto start = static_cast<const uint8_t*>(map.unsafe_data());
        auto end = start + map.length();
        val += map.Verify(start, end, &prev_block);
    }
}
BENCHMARK(TestMapPtrRaw);

static void TestMapPtr(benchmark::State& state) {
    auto tmp = makeFile();
    android::incfs::IncFsFileMap map;
    map.CreateForceVerification(tmp->fd, 0, 1, tmp->path, true);
    int val = 0;
    for (auto _ : state) {
        val += map.data<char>().verify();
    }
}
BENCHMARK(TestMapPtr);

static void TestAccess(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    int val = 0;
    for (auto _ : state) {
        incfs::access(mapping->data(), [&](auto ptr) { val += *ptr; });
    }
}
BENCHMARK(TestAccess);

static void TestAccessFast(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    int val = 0;
    incfs::access(mapping->data(), [&](auto ptr) {
        for (auto _ : state) {
            val += *ptr;
        }
    });
}
BENCHMARK(TestAccessFast);

static void TestAccessVal(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    int val = 0;
    for (auto _ : state) {
        incfs::access(mapping->data(), [&](auto ptr) { return val += *ptr; });
    }
}
BENCHMARK(TestAccessVal);

static void TestAccessNested(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    int val = 0;
    incfs::access(nullptr, [&](auto) {
        for (auto _ : state) {
            incfs::access(mapping->data(), [&](auto ptr) { val += *ptr; });
        }
    });
}
BENCHMARK(TestAccessNested);

static void TestAccessDoubleNested(benchmark::State& state) {
    auto tmp = makeFile();
    auto mapping = android::base::MappedFile::FromFd(tmp->fd, 0, 1, PROT_READ);
    int val = 0;
    incfs::access(nullptr, [&](auto) {
        incfs::access(nullptr, [&](auto) {
            for (auto _ : state) {
                incfs::access(mapping->data(), [&](auto ptr) { val += *ptr; });
            }
        });
    });
}
BENCHMARK(TestAccessDoubleNested);

static void TestAccessError(benchmark::State& state) {
    auto [tmp, mapping] = makeEmptyFileMapping();
    int val = 0;
    for (auto _ : state) {
        incfs::access(mapping->data(), [&](auto ptr) { val += *ptr; });
    }
}
BENCHMARK(TestAccessError);

BENCHMARK_MAIN();
