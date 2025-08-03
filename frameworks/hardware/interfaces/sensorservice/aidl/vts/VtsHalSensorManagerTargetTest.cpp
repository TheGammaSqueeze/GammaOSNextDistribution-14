/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "sensor_manager_aidl_hal_test"
#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/frameworks/sensorservice/ISensorManager.h>
#include <aidl/sensors/convert.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android/binder_manager.h>
#include <android/sensor.h>
#include <binder/IServiceManager.h>
#include <cutils/ashmem.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <sys/mman.h>

#include <chrono>
#include <thread>

using aidl::android::frameworks::sensorservice::IDirectReportChannel;
using aidl::android::frameworks::sensorservice::ISensorManager;
using aidl::android::hardware::common::Ashmem;
using aidl::android::hardware::sensors::Event;
using aidl::android::hardware::sensors::ISensors;
using aidl::android::hardware::sensors::SensorInfo;
using aidl::android::hardware::sensors::SensorType;
using ::android::sp;
using ndk::ScopedAStatus;
using ndk::ScopedFileDescriptor;
using ::testing::Contains;

static inline ::testing::AssertionResult isOk(const ScopedAStatus& status) {
    return status.isOk() ? ::testing::AssertionSuccess()
                         : ::testing::AssertionFailure() << status.getDescription();
}

template <typename I, typename F>
static ::testing::AssertionResult isIncreasing(I begin, I end, F getField) {
    typename std::iterator_traits<I>::pointer lastValue = nullptr;
    I iter;
    size_t pos;
    for (iter = begin, pos = 0; iter != end; ++iter, ++pos) {
        if (iter == begin) {
            lastValue = &(*iter);
            continue;
        }
        if (getField(*iter) < getField(*lastValue)) {
            return ::testing::AssertionFailure()
                   << "Not an increasing sequence, pos = " << pos << ", " << getField(*iter)
                   << " < " << getField(*lastValue);
        }
    }
    return ::testing::AssertionSuccess();
}

#define EXPECT_OK(__ret__) EXPECT_TRUE(isOk(__ret__))
#define ASSERT_OK(__ret__) ASSERT_TRUE(isOk(__ret__))

class SensorManagerTest : public ::testing::TestWithParam<std::string> {
   public:
    virtual void SetUp() override {
        manager_ = ISensorManager::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
        ASSERT_NE(manager_, nullptr);
    }

    // Call getSensorList. Filter result based on |pred| if it is provided.
    ndk::ScopedAStatus GetSensorList(std::vector<SensorInfo>* out_info,
                                     const std::function<bool(SensorInfo)>& pred = nullptr) {
        ndk::ScopedAStatus ret = manager_->getSensorList(out_info);
        if (ret.isOk() && pred) {
            out_info->erase(std::remove_if(out_info->begin(), out_info->end(),
                                           [pred](const auto& el) { return !pred(el); }),
                            out_info->end());
        }
        return ret;
    }

    std::shared_ptr<ISensorManager> manager_;
};

using map_region = std::unique_ptr<void, std::function<void(void*)>>;

map_region map(const Ashmem& mem) {
    if (mem.fd.get() == -1) {
        return nullptr;
    }
    size_t size = mem.size;
    void* buf = mmap(nullptr, size, PROT_READ, MAP_SHARED, mem.fd.get(), 0);
    return map_region{buf, [size](void* localBuf) { munmap(localBuf, size); }};
}

TEST_P(SensorManagerTest, List) {
    std::vector<SensorInfo> sensorList;
    auto res = GetSensorList(&sensorList);
    ASSERT_OK(res) << res.getDescription();
}

TEST_P(SensorManagerTest, Ashmem) {
    std::vector<SensorInfo> sensorList;
    auto res = GetSensorList(&sensorList, [](const auto& info) {
        return info.flags & SensorInfo::SENSOR_FLAG_BITS_DIRECT_CHANNEL_ASHMEM;
    });
    ASSERT_OK(res);
    if (sensorList.empty()) {
        GTEST_SKIP() << "DIRECT_CHANNEL_ASHMEM not supported by HAL, skipping";
    }
    auto testOne = [this](int64_t memSize, int64_t intendedSize,
                          void (*callback)(const std::shared_ptr<IDirectReportChannel>&,
                                           const ScopedAStatus&)) {
        auto fd = ashmem_create_region("sensorservice_vts", memSize);
        ASSERT_TRUE(fd != -1);
        Ashmem ashmem = {ScopedFileDescriptor(fd), memSize};
        std::shared_ptr<IDirectReportChannel> chan;
        ScopedAStatus res = manager_->createAshmemDirectChannel(ashmem, intendedSize, &chan);
        callback(chan, res);
    };

    testOne(16, 16, [](const auto& chan, const ScopedAStatus& result) {
        EXPECT_EQ(result.getServiceSpecificError(), ISensorManager::RESULT_BAD_VALUE)
            << "unexpected result when memory size is too small";
        EXPECT_EQ(chan, nullptr);
    });

    testOne(1024, 1024, [](const auto& chan, const ScopedAStatus& result) {
        EXPECT_OK(result);
        EXPECT_NE(chan, nullptr);
    });

    testOne(1024, 2048, [](const auto& chan, const ScopedAStatus& result) {
        EXPECT_EQ(result.getServiceSpecificError(), ISensorManager::RESULT_BAD_VALUE)
            << "unexpected result when intended size is too big";
        EXPECT_EQ(chan, nullptr);
    });

    testOne(1024, 16, [](const auto& chan, const ScopedAStatus& result) {
        EXPECT_EQ(result.getServiceSpecificError(), ISensorManager::RESULT_BAD_VALUE)
            << "unexpected result when intended size is too small";
        EXPECT_EQ(chan, nullptr);
    });
}

static std::vector<Event> parseEvents(uint8_t* buf, size_t memSize) {
    using android::hardware::sensors::implementation::convertFromSensorEvent;
    size_t offset = 0;
    int64_t lastCounter = -1;
    std::vector<Event> events;
    Event event;

    while (offset + (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH <= memSize) {
        uint8_t* start = buf + offset;
        int64_t atomicCounter = *reinterpret_cast<uint32_t*>(
            start + (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_ATOMIC_COUNTER);
        if (atomicCounter <= lastCounter) {
            break;
        }
        int32_t size = *reinterpret_cast<int32_t*>(
            start + (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_FIELD);
        if (size != (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH) {
            // unknown error, events parsed may be wrong, remove all
            events.clear();
            break;
        }

        convertFromSensorEvent(*reinterpret_cast<const sensors_event_t*>(start), &event);
        events.push_back(event);
        lastCounter = atomicCounter;
        offset += (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH;
    }
    return events;
}

TEST_P(SensorManagerTest, GetDefaultAccelerometer) {
    std::vector<SensorInfo> sensorList;
    auto res = GetSensorList(
        &sensorList, [](const auto& info) { return info.type == SensorType::ACCELEROMETER; });
    ASSERT_OK(res);

    SensorInfo info;
    res = manager_->getDefaultSensor(SensorType::ACCELEROMETER, &info);
    if (sensorList.empty()) {
        ASSERT_EQ(ISensorManager::RESULT_NOT_EXIST, res.getServiceSpecificError());
    } else {
        ASSERT_OK(res);
        ASSERT_THAT(sensorList, Contains(info));
    }
}

TEST_P(SensorManagerTest, Accelerometer) {
    using std::literals::chrono_literals::operator""ms;

    std::vector<SensorInfo> sensorList;
    auto res = GetSensorList(&sensorList, [](const auto& info) {
        if (info.type != SensorType::ACCELEROMETER) return false;
        if (!(info.flags & SensorInfo::SENSOR_FLAG_BITS_DIRECT_CHANNEL_ASHMEM)) return false;
        int maxLevel = (info.flags & SensorInfo::SENSOR_FLAG_BITS_MASK_DIRECT_REPORT) >>
                       SensorInfo::SENSOR_FLAG_SHIFT_DIRECT_REPORT;
        return maxLevel >= static_cast<int>(ISensors::RateLevel::FAST);
    });
    ASSERT_OK(res);

    if (sensorList.empty()) {
        GTEST_SKIP()
            << "No accelerometer sensor that supports DIRECT_CHANNEL_ASHMEM and fast report "
            << "rate, skipping";
    }

    for (const auto& info : sensorList) {
        int32_t handle = info.sensorHandle;
        const size_t memSize = (size_t)ISensors::DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH * 300;
        auto fd = ashmem_create_region("sensorservice_vts", memSize);
        ASSERT_TRUE(fd != -1);
        Ashmem mem = {ScopedFileDescriptor(fd), memSize};
        map_region buf = map(mem);
        ASSERT_NE(buf, nullptr);
        std::shared_ptr<IDirectReportChannel> chan;
        auto res = manager_->createAshmemDirectChannel(mem, memSize, &chan);
        ASSERT_OK(res);
        ASSERT_NE(chan, nullptr);

        int32_t token = 0;
        ASSERT_OK(chan->configure(handle, ISensors::RateLevel::FAST, &token));
        ASSERT_GT(token, 0);
        std::this_thread::sleep_for(500ms);
        int32_t zeroToken = 0;
        ASSERT_OK(chan->configure(handle, ISensors::RateLevel::STOP, &zeroToken));
        ASSERT_OK(res);
        ASSERT_EQ(zeroToken, 0);

        auto events = parseEvents(static_cast<uint8_t*>(buf.get()), memSize);

        EXPECT_TRUE(isIncreasing(events.begin(), events.end(), [](const auto& event) {
            return event.timestamp;
        })) << "timestamp is not monotonically increasing";
        for (const auto& event : events) {
            EXPECT_EQ(token, event.sensorHandle)
                << "configure token and sensor handle don't match.";
        }
    }
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(SensorManagerTest);
INSTANTIATE_TEST_SUITE_P(
    PerInstance, SensorManagerTest,
    testing::ValuesIn(android::getAidlHalInstanceNames(ISensorManager::descriptor)),
    android::PrintInstanceNameToString);
