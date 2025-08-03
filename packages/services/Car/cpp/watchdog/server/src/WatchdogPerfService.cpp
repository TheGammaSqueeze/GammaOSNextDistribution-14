/*
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"
#define DEBUG false  // STOPSHIP if true.

#include "WatchdogPerfService.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/util/ProtoOutputStream.h>
#include <log/log.h>
#include <processgroup/sched_policy.h>

#include <pthread.h>

#include <iterator>
#include <vector>

#include <carwatchdog_daemon_dump.proto.h>
#include <health_check_client_info.proto.h>
#include <performance_stats.proto.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::UserState;
using ::android::sp;
using ::android::String16;
using ::android::String8;
using ::android::base::EqualsIgnoreCase;
using ::android::base::Error;
using ::android::base::Join;
using ::android::base::ParseUint;
using ::android::base::Result;
using ::android::base::Split;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::util::ProtoOutputStream;

const int32_t kMaxCachedUnsentResourceStats = 10;
const std::chrono::nanoseconds kPrevUnsentResourceStatsDelayNs = 3s;
// Minimum required collection polling interval between subsequent collections.
const std::chrono::nanoseconds kMinEventInterval = 1s;
const std::chrono::seconds kDefaultSystemEventCollectionInterval = 1s;
const std::chrono::seconds kDefaultPeriodicCollectionInterval = 20s;
const std::chrono::seconds kDefaultPeriodicMonitorInterval = 5s;
const std::chrono::nanoseconds kCustomCollectionInterval = 10s;
const std::chrono::nanoseconds kCustomCollectionDuration = 30min;

constexpr const char* kServiceName = "WatchdogPerfService";
static const std::string kDumpMajorDelimiter = std::string(100, '-') + "\n";  // NOLINT
constexpr const char* kHelpText =
        "\n%s dump options:\n"
        "%s: Starts custom performance data collection. Customize the collection behavior with "
        "the following optional arguments:\n"
        "\t%s <seconds>: Modifies the collection interval. Default behavior is to collect once "
        "every %lld seconds.\n"
        "\t%s <seconds>: Modifies the maximum collection duration. Default behavior is to collect "
        "until %ld minutes before automatically stopping the custom collection and discarding "
        "the collected data.\n"
        "\t%s <package name>,<package name>,...: Comma-separated value containing package names. "
        "When provided, the results are filtered only to the provided package names. Default "
        "behavior is to list the results for the top N packages.\n"
        "%s: Stops custom performance data collection and generates a dump of "
        "the collection report.\n\n"
        "When no options are specified, the car watchdog report contains the performance data "
        "collected during boot-time and over the last few minutes before the report generation.\n";

Result<std::chrono::seconds> parseSecondsFlag(const char** args, uint32_t numArgs, size_t pos) {
    if (numArgs <= pos) {
        return Error() << "Value not provided";
    }
    uint64_t value;
    if (std::string strValue = std::string(args[pos]); !ParseUint(strValue, &value)) {
        return Error() << "Invalid value " << strValue << ", must be an integer";
    }
    return std::chrono::seconds(value);
}

constexpr const char* toString(std::variant<EventType, SwitchMessage> what) {
    return std::visit(
            [&](const auto& v) -> const char* {
                switch (static_cast<int>(v)) {
                    case EventType::INIT:
                        return "INIT";
                    case EventType::TERMINATED:
                        return "TERMINATED";
                    case EventType::BOOT_TIME_COLLECTION:
                        return "BOOT_TIME_COLLECTION";
                    case EventType::PERIODIC_COLLECTION:
                        return "PERIODIC_COLLECTION";
                    case EventType::USER_SWITCH_COLLECTION:
                        return "USER_SWITCH_COLLECTION";
                    case EventType::WAKE_UP_COLLECTION:
                        return "WAKE_UP_COLLECTION";
                    case EventType::CUSTOM_COLLECTION:
                        return "CUSTOM_COLLECTION";
                    case EventType::PERIODIC_MONITOR:
                        return "PERIODIC_MONITOR";
                    case EventType::LAST_EVENT:
                        return "LAST_EVENT";
                    case SwitchMessage::END_BOOTTIME_COLLECTION:
                        return "END_BOOTTIME_COLLECTION";
                    case SwitchMessage::END_USER_SWITCH_COLLECTION:
                        return "END_USER_SWITCH_COLLECTION";
                    case SwitchMessage::END_WAKE_UP_COLLECTION:
                        return "END_WAKE_UP_COLLECTION";
                    case SwitchMessage::END_CUSTOM_COLLECTION:
                        return "END_CUSTOM_COLLECTION";
                    default:
                        return "INVALID_EVENT_OR_SWITCH_MESSAGE";
                }
            },
            what);
}

constexpr int toProtoEventType(EventType eventType) {
    switch (eventType) {
        case EventType::INIT:
            return PerformanceProfilerDump::INIT;
        case EventType::TERMINATED:
            return PerformanceProfilerDump::TERMINATED;
        case EventType::BOOT_TIME_COLLECTION:
            return PerformanceProfilerDump::BOOT_TIME_COLLECTION;
        case EventType::PERIODIC_COLLECTION:
            return PerformanceProfilerDump::PERIODIC_COLLECTION;
        case EventType::USER_SWITCH_COLLECTION:
            return PerformanceProfilerDump::USER_SWITCH_COLLECTION;
        case EventType::WAKE_UP_COLLECTION:
            return PerformanceProfilerDump::WAKE_UP_COLLECTION;
        case EventType::CUSTOM_COLLECTION:
            return PerformanceProfilerDump::CUSTOM_COLLECTION;
        default:
            return PerformanceProfilerDump::EVENT_TYPE_UNSPECIFIED;
    }
}

constexpr const char* toString(SystemState systemState) {
    switch (systemState) {
        case SystemState::NORMAL_MODE:
            return "NORMAL_MODE";
        case SystemState::GARAGE_MODE:
            return "GARAGE_MODE";
        default:
            return "UNKNOWN MODE";
    }
}

bool isEmpty(const ResourceStats& resourceStats) {
    return !resourceStats.resourceUsageStats.has_value() &&
            !resourceStats.resourceOveruseStats.has_value();
}

}  // namespace

std::string WatchdogPerfService::EventMetadata::toString() const {
    std::string buffer;
    const auto intervalInSecs =
            std::chrono::duration_cast<std::chrono::seconds>(pollingIntervalNs).count();
    StringAppendF(&buffer, "Event polling interval: %lld second%s\n", intervalInSecs,
                  ((intervalInSecs > 1) ? "s" : ""));
    if (!filterPackages.empty()) {
        std::vector<std::string> packages(filterPackages.begin(), filterPackages.end());
        StringAppendF(&buffer, "Filtered results to packages: %s\n", Join(packages, ", ").c_str());
    }
    return buffer;
}

Result<void> WatchdogPerfService::registerDataProcessor(sp<DataProcessorInterface> processor) {
    if (processor == nullptr) {
        return Error() << "Must provide a valid data processor";
    }
    if (const auto result = processor->init(); !result.ok()) {
        return Error() << "Failed to initialize " << processor->name().c_str() << ": "
                       << result.error().message();
    }
    Mutex::Autolock lock(mMutex);
    mDataProcessors.push_back(processor);
    if (DEBUG) {
        ALOGD("Successfully registered %s to %s", processor->name().c_str(), kServiceName);
    }
    return {};
}

Result<void> WatchdogPerfService::start() {
    {
        Mutex::Autolock lock(mMutex);
        if (mCurrCollectionEvent != EventType::INIT || mCollectionThread.joinable()) {
            return Error(INVALID_OPERATION) << "Cannot start " << kServiceName << " more than once";
        }
        if (mWatchdogServiceHelper == nullptr) {
            return Error(INVALID_OPERATION) << "No watchdog service helper is registered";
        }
        std::chrono::nanoseconds systemEventCollectionInterval =
                std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::seconds(sysprop::systemEventCollectionInterval().value_or(
                                kDefaultSystemEventCollectionInterval.count())));
        std::chrono::nanoseconds periodicCollectionInterval =
                std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::seconds(sysprop::periodicCollectionInterval().value_or(
                                kDefaultPeriodicCollectionInterval.count())));
        std::chrono::nanoseconds periodicMonitorInterval =
                std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::seconds(sysprop::periodicMonitorInterval().value_or(
                                kDefaultPeriodicMonitorInterval.count())));
        mBoottimeCollection = {
                .eventType = EventType::BOOT_TIME_COLLECTION,
                .pollingIntervalNs = systemEventCollectionInterval,
        };
        mPeriodicCollection = {
                .eventType = EventType::PERIODIC_COLLECTION,
                .pollingIntervalNs = periodicCollectionInterval,
        };
        mUserSwitchCollection = {{
                .eventType = EventType::USER_SWITCH_COLLECTION,
                .pollingIntervalNs = systemEventCollectionInterval,
        }};
        mWakeUpCollection = {
                .eventType = EventType::WAKE_UP_COLLECTION,
                .pollingIntervalNs = systemEventCollectionInterval,
        };
        mPeriodicMonitor = {
                .eventType = EventType::PERIODIC_MONITOR,
                .pollingIntervalNs = periodicMonitorInterval,
        };
        if (mDataProcessors.empty()) {
            ALOGE("Terminating %s: No data processor is registered", kServiceName);
            mCurrCollectionEvent = EventType::TERMINATED;
            return Error() << "No data processor is registered";
        }
        mUidStatsCollector->init();
        mProcStatCollector->init();
        mProcDiskStatsCollector->init();
    }

    mCollectionThread = std::thread([&]() {
        {
            Mutex::Autolock lock(mMutex);
            if (EventType expected = EventType::INIT; mCurrCollectionEvent != expected) {
                ALOGE("Skipping performance data collection as the current collection event "
                      "%s != %s",
                      toString(mCurrCollectionEvent), toString(expected));
                return;
            }
            notifySystemStartUpLocked();
            mCurrCollectionEvent = EventType::BOOT_TIME_COLLECTION;
            mBoottimeCollection.lastPollUptimeNs = mHandlerLooper->now();
            mHandlerLooper->setLooper(Looper::prepare(/*opts=*/0));
            mHandlerLooper->sendMessage(sp<WatchdogPerfService>::fromExisting(this),
                                        EventType::BOOT_TIME_COLLECTION);
        }
        if (set_sched_policy(0, SP_BACKGROUND) != 0) {
            ALOGW("Failed to set background scheduling priority to %s thread", kServiceName);
        }
        if (int result = pthread_setname_np(pthread_self(), "WatchdogPerfSvc"); result != 0) {
            ALOGE("Failed to set %s thread name: %d", kServiceName, result);
        }
        ALOGI("Starting %s performance data collection", toString(mCurrCollectionEvent));
        bool isCollectionActive = true;
        /*
         * Loop until the collection is not active -- performance collection runs on this thread in
         * a handler.
         */
        while (isCollectionActive) {
            mHandlerLooper->pollAll(/*timeoutMillis=*/-1);
            Mutex::Autolock lock(mMutex);
            isCollectionActive = mCurrCollectionEvent != EventType::TERMINATED;
        }
    });
    return {};
}

void WatchdogPerfService::terminate() {
    {
        Mutex::Autolock lock(mMutex);
        if (mCurrCollectionEvent == EventType::TERMINATED) {
            ALOGE("%s was terminated already", kServiceName);
            return;
        }
        ALOGE("Terminating %s as car watchdog is terminating", kServiceName);
        if (mCurrCollectionEvent != EventType::INIT) {
            /*
             * Looper runs only after EventType::INIT has completed so remove looper messages
             * and wake the looper only when the current collection has changed from INIT.
             */
            mHandlerLooper->removeMessages(sp<WatchdogPerfService>::fromExisting(this));
            mHandlerLooper->wake();
        }
        for (const auto& processor : mDataProcessors) {
            processor->terminate();
        }
        mCurrCollectionEvent = EventType::TERMINATED;
        mUnsentResourceStats.clear();
    }
    if (mCollectionThread.joinable()) {
        mCollectionThread.join();
        if (DEBUG) {
            ALOGD("%s collection thread terminated", kServiceName);
        }
    }
}

void WatchdogPerfService::setSystemState(SystemState systemState) {
    Mutex::Autolock lock(mMutex);
    if (mSystemState != systemState) {
        ALOGI("%s switching from %s to %s", kServiceName, toString(mSystemState),
              toString(systemState));
    }
    mSystemState = systemState;
}

void WatchdogPerfService::onCarWatchdogServiceRegistered() {
    Mutex::Autolock lock(mMutex);
    for (const auto& processor : mDataProcessors) {
        processor->onCarWatchdogServiceRegistered();
    }
    if (mUnsentResourceStats.empty()) {
        return;
    }
    mHandlerLooper->sendMessage(sp<WatchdogPerfService>::fromExisting(this),
                                TaskMessage::SEND_RESOURCE_STATS);
}

Result<void> WatchdogPerfService::onBootFinished() {
    Mutex::Autolock lock(mMutex);
    if (EventType expected = EventType::BOOT_TIME_COLLECTION; mCurrCollectionEvent != expected) {
        /*
         * This case happens when either the WatchdogPerfService has prematurely terminated before
         * boot complete notification is received or multiple boot complete notifications are
         * received. In either case don't return error as this will lead to runtime exception and
         * cause system to boot loop.
         */
        ALOGE("Current performance data collection event %s != %s", toString(mCurrCollectionEvent),
              toString(expected));
        return {};
    }
    mHandlerLooper->sendMessageAtTime(mHandlerLooper->now() + mPostSystemEventDurationNs.count(),
                                      sp<WatchdogPerfService>::fromExisting(this),
                                      SwitchMessage::END_BOOTTIME_COLLECTION);
    if (DEBUG) {
        ALOGD("Boot complete signal received.");
    }
    return {};
}

Result<void> WatchdogPerfService::onUserStateChange(userid_t userId, const UserState& userState) {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::BOOT_TIME_COLLECTION ||
        mCurrCollectionEvent == EventType::CUSTOM_COLLECTION) {
        // Ignoring the user switch events because the boot-time and custom collections take
        // precedence over other collections.
        if (mCurrCollectionEvent == EventType::CUSTOM_COLLECTION) {
            ALOGW("Unable to start %s. Current performance data collection event: %s",
                  toString(EventType::USER_SWITCH_COLLECTION), toString(mCurrCollectionEvent));
        }
        return {};
    }
    switch (static_cast<int>(userState)) {
        case static_cast<int>(UserState::USER_STATE_SWITCHING):
            // TODO(b/243984863): Handle multi-user switching scenario.
            mUserSwitchCollection.from = mUserSwitchCollection.to;
            mUserSwitchCollection.to = userId;
            if (mCurrCollectionEvent != EventType::PERIODIC_COLLECTION &&
                mCurrCollectionEvent != EventType::USER_SWITCH_COLLECTION) {
                ALOGE("Unable to start %s. Current performance data collection event: %s",
                      toString(EventType::USER_SWITCH_COLLECTION), toString(mCurrCollectionEvent));
                return {};
            }
            startUserSwitchCollection();
            ALOGI("Switching to %s (userIds: from = %d, to = %d)", toString(mCurrCollectionEvent),
                  mUserSwitchCollection.from, mUserSwitchCollection.to);
            break;
        case static_cast<int>(UserState::USER_STATE_UNLOCKING):
            if (mCurrCollectionEvent != EventType::PERIODIC_COLLECTION) {
                if (mCurrCollectionEvent != EventType::USER_SWITCH_COLLECTION) {
                    ALOGE("Unable to start %s. Current performance data collection event: %s",
                          toString(EventType::USER_SWITCH_COLLECTION),
                          toString(mCurrCollectionEvent));
                }
                return {};
            }
            if (mUserSwitchCollection.to != userId) {
                return {};
            }
            startUserSwitchCollection();
            ALOGI("Switching to %s (userId: %d)", toString(mCurrCollectionEvent), userId);
            break;
        case static_cast<int>(UserState::USER_STATE_POST_UNLOCKED): {
            if (mCurrCollectionEvent != EventType::USER_SWITCH_COLLECTION) {
                ALOGE("Ignoring USER_STATE_POST_UNLOCKED because no user switch collection in "
                      "progress. Current performance data collection event: %s.",
                      toString(mCurrCollectionEvent));
                return {};
            }
            if (mUserSwitchCollection.to != userId) {
                ALOGE("Ignoring USER_STATE_POST_UNLOCKED signal for user id: %d. "
                      "Current user being switched to: %d",
                      userId, mUserSwitchCollection.to);
                return {};
            }
            auto thiz = sp<WatchdogPerfService>::fromExisting(this);
            mHandlerLooper->removeMessages(thiz, SwitchMessage::END_USER_SWITCH_COLLECTION);
            nsecs_t endUserSwitchCollectionTime =
                    mHandlerLooper->now() + mPostSystemEventDurationNs.count();
            mHandlerLooper->sendMessageAtTime(endUserSwitchCollectionTime, thiz,
                                              SwitchMessage::END_USER_SWITCH_COLLECTION);
            break;
        }
        default:
            ALOGE("Unsupported user state: %d", static_cast<int>(userState));
            return {};
    }
    if (DEBUG) {
        ALOGD("Handled user state change: userId = %d, userState = %d", userId,
              static_cast<int>(userState));
    }
    return {};
}

Result<void> WatchdogPerfService::startUserSwitchCollection() {
    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    mHandlerLooper->removeMessages(thiz);
    mUserSwitchCollection.lastPollUptimeNs = mHandlerLooper->now();
    // End |EventType::USER_SWITCH_COLLECTION| after a timeout because the user switch end
    // signal won't be received within a few seconds when the switch is blocked due to a
    // keyguard event. Otherwise, polling beyond a few seconds will lead to unnecessary data
    // collection.
    mHandlerLooper->sendMessageAtTime(mHandlerLooper->now() + mUserSwitchTimeoutNs.count(), thiz,
                                      SwitchMessage::END_USER_SWITCH_COLLECTION);
    mCurrCollectionEvent = EventType::USER_SWITCH_COLLECTION;
    mHandlerLooper->sendMessage(thiz, EventType::USER_SWITCH_COLLECTION);
    return {};
}

Result<void> WatchdogPerfService::onSuspendExit() {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::CUSTOM_COLLECTION) {
        // Ignoring the suspend exit event because the custom collection takes
        // precedence over other collections.
        ALOGE("Unable to start %s. Current performance data collection event: %s",
              toString(EventType::WAKE_UP_COLLECTION), toString(mCurrCollectionEvent));
        return {};
    }
    if (mCurrCollectionEvent == EventType::WAKE_UP_COLLECTION) {
        ALOGE("The current performance data collection event is already %s",
              toString(EventType::WAKE_UP_COLLECTION));
        return {};
    }
    notifySystemStartUpLocked();
    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    mHandlerLooper->removeMessages(thiz);
    nsecs_t now = mHandlerLooper->now();
    mWakeUpCollection.lastPollUptimeNs = now;
    mHandlerLooper->sendMessageAtTime(now + mWakeUpDurationNs.count(), thiz,
                                      SwitchMessage::END_WAKE_UP_COLLECTION);
    mCurrCollectionEvent = EventType::WAKE_UP_COLLECTION;
    mHandlerLooper->sendMessage(thiz, EventType::WAKE_UP_COLLECTION);
    ALOGI("Switching to %s", toString(mCurrCollectionEvent));
    return {};
}

Result<void> WatchdogPerfService::onShutdownEnter() {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::CUSTOM_COLLECTION) {
        ALOGI("Unable to switch to %s during shutdown enter. Current performance data collection "
              "event: %s",
              toString(EventType::PERIODIC_COLLECTION), toString(mCurrCollectionEvent));
        return {};
    }
    switchToPeriodicLocked(/*startNow=*/true);
    return {};
}

Result<void> WatchdogPerfService::onCustomCollection(int fd, const char** args, uint32_t numArgs) {
    if (numArgs == 0) {
        return Error(BAD_VALUE) << "No custom collection dump arguments";
    }

    if (EqualsIgnoreCase(args[0], kStartCustomCollectionFlag)) {
        if (numArgs > 7) {
            return Error(BAD_VALUE) << "Number of arguments to start custom performance data "
                                    << "collection cannot exceed 7";
        }
        std::chrono::nanoseconds interval = kCustomCollectionInterval;
        std::chrono::nanoseconds maxDuration = kCustomCollectionDuration;
        std::unordered_set<std::string> filterPackages;
        for (uint32_t i = 1; i < numArgs; ++i) {
            if (EqualsIgnoreCase(args[i], kIntervalFlag)) {
                const auto& result = parseSecondsFlag(args, numArgs, i + 1);
                if (!result.ok()) {
                    return Error(BAD_VALUE)
                            << "Failed to parse " << kIntervalFlag << ": " << result.error();
                }
                interval = std::chrono::duration_cast<std::chrono::nanoseconds>(*result);
                ++i;
                continue;
            }
            if (EqualsIgnoreCase(args[i], kMaxDurationFlag)) {
                const auto& result = parseSecondsFlag(args, numArgs, i + 1);
                if (!result.ok()) {
                    return Error(BAD_VALUE)
                            << "Failed to parse " << kMaxDurationFlag << ": " << result.error();
                }
                maxDuration = std::chrono::duration_cast<std::chrono::nanoseconds>(*result);
                ++i;
                continue;
            }
            if (EqualsIgnoreCase(args[i], kFilterPackagesFlag)) {
                if (numArgs < i + 1) {
                    return Error(BAD_VALUE)
                            << "Must provide value for '" << kFilterPackagesFlag << "' flag";
                }
                std::vector<std::string> packages = Split(std::string(args[i + 1]), ",");
                std::copy(packages.begin(), packages.end(),
                          std::inserter(filterPackages, filterPackages.end()));
                ++i;
                continue;
            }
            return Error(BAD_VALUE) << "Unknown flag " << args[i]
                                    << " provided to start custom performance data collection";
        }
        if (const auto& result = startCustomCollection(interval, maxDuration, filterPackages);
            !result.ok()) {
            return result;
        }
        return {};
    }
    if (EqualsIgnoreCase(args[0], kEndCustomCollectionFlag)) {
        if (numArgs != 1) {
            ALOGW("Number of arguments to stop custom performance data collection cannot exceed 1. "
                  "Stopping the data collection.");
            WriteStringToFd("Number of arguments to stop custom performance data collection "
                            "cannot exceed 1. Stopping the data collection.",
                            fd);
        }
        return endCustomCollection(fd);
    }
    return Error(BAD_VALUE) << "Custom perf collection dump arguments start neither with "
                            << kStartCustomCollectionFlag << " nor with "
                            << kEndCustomCollectionFlag << " flags";
}

Result<void> WatchdogPerfService::onDump(int fd) const {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::TERMINATED) {
        ALOGW("%s not active. Dumping cached data", kServiceName);
        if (!WriteStringToFd(StringPrintf("%s not active. Dumping cached data.", kServiceName),
                             fd)) {
            return Error(FAILED_TRANSACTION) << "Failed to write " << kServiceName << " status";
        }
    }

    if (const auto& result = dumpCollectorsStatusLocked(fd); !result.ok()) {
        return Error(FAILED_TRANSACTION) << result.error();
    }

    if (!WriteStringToFd(StringPrintf("\n%s%s report:\n%sBoot-time collection information:\n%s\n",
                                      kDumpMajorDelimiter.c_str(), kServiceName,
                                      kDumpMajorDelimiter.c_str(), std::string(33, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mBoottimeCollection.toString(), fd) ||
        !WriteStringToFd(StringPrintf("\nWake-up collection information:\n%s\n",
                                      std::string(31, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mWakeUpCollection.toString(), fd) ||
        !WriteStringToFd(StringPrintf("\nUser-switch collection information:\n%s\n",
                                      std::string(35, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mUserSwitchCollection.toString(), fd) ||
        !WriteStringToFd(StringPrintf("\nPeriodic collection information:\n%s\n",
                                      std::string(32, '=').c_str()),
                         fd) ||
        !WriteStringToFd(mPeriodicCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION)
                << "Failed to dump the boot-time and periodic collection reports.";
    }

    for (const auto& processor : mDataProcessors) {
        if (const auto result = processor->onDump(fd); !result.ok()) {
            return result;
        }
    }

    WriteStringToFd(kDumpMajorDelimiter, fd);
    return {};
}

Result<void> WatchdogPerfService::onDumpProto(ProtoOutputStream& outProto) const {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::TERMINATED) {
        ALOGW("%s not active. Dumping cached data", kServiceName);
    }

    uint64_t performanceProfilerDumpToken =
            outProto.start(CarWatchdogDaemonDump::PERFORMANCE_PROFILER_DUMP);

    outProto.write(PerformanceProfilerDump::CURRENT_EVENT, toProtoEventType(mCurrCollectionEvent));

    DataProcessorInterface::CollectionIntervals collectionIntervals =
            {.mBoottimeIntervalMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     mBoottimeCollection.pollingIntervalNs),
             .mPeriodicIntervalMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     mPeriodicCollection.pollingIntervalNs),
             .mUserSwitchIntervalMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     mUserSwitchCollection.pollingIntervalNs),
             .mWakeUpIntervalMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     mWakeUpCollection.pollingIntervalNs),
             .mCustomIntervalMillis = std::chrono::duration_cast<std::chrono::milliseconds>(
                     mCustomCollection.pollingIntervalNs)};

    // Populate Performance Stats
    for (const auto& processor : mDataProcessors) {
        processor->onDumpProto(collectionIntervals, outProto);
    }

    outProto.end(performanceProfilerDumpToken);

    return {};
}

bool WatchdogPerfService::dumpHelpText(int fd) const {
    return WriteStringToFd(StringPrintf(kHelpText, kServiceName, kStartCustomCollectionFlag,
                                        kIntervalFlag,
                                        std::chrono::duration_cast<std::chrono::seconds>(
                                                kCustomCollectionInterval)
                                                .count(),
                                        kMaxDurationFlag,
                                        std::chrono::duration_cast<std::chrono::minutes>(
                                                kCustomCollectionDuration)
                                                .count(),
                                        kFilterPackagesFlag, kEndCustomCollectionFlag),
                           fd);
}

Result<void> WatchdogPerfService::dumpCollectorsStatusLocked(int fd) const {
    if (!mUidStatsCollector->enabled() &&
        !WriteStringToFd(StringPrintf("UidStatsCollector failed to access proc and I/O files"),
                         fd)) {
        return Error() << "Failed to write UidStatsCollector status";
    }
    if (!mProcStatCollector->enabled() &&
        !WriteStringToFd(StringPrintf("ProcStat collector failed to access the file %s",
                                      mProcStatCollector->filePath().c_str()),
                         fd)) {
        return Error() << "Failed to write ProcStat collector status";
    }
    return {};
}

Result<void> WatchdogPerfService::startCustomCollection(
        std::chrono::nanoseconds interval, std::chrono::nanoseconds maxDuration,
        const std::unordered_set<std::string>& filterPackages) {
    if (interval < kMinEventInterval || maxDuration < kMinEventInterval) {
        return Error(INVALID_OPERATION)
                << "Collection polling interval and maximum duration must be >= "
                << std::chrono::duration_cast<std::chrono::milliseconds>(kMinEventInterval).count()
                << " milliseconds";
    }
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == EventType::CUSTOM_COLLECTION) {
        return Error(INVALID_OPERATION) << "Cannot start custom collection more than once";
    }
    nsecs_t now = mHandlerLooper->now();
    mCustomCollection = {
            .eventType = EventType::CUSTOM_COLLECTION,
            .pollingIntervalNs = interval,
            .lastPollUptimeNs = now,
            .filterPackages = filterPackages,
    };

    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    mHandlerLooper->removeMessages(thiz);
    mHandlerLooper->sendMessageAtTime(now + maxDuration.count(), thiz,
                                      SwitchMessage::END_CUSTOM_COLLECTION);
    mCurrCollectionEvent = EventType::CUSTOM_COLLECTION;
    mHandlerLooper->sendMessage(thiz, EventType::CUSTOM_COLLECTION);
    ALOGI("Starting %s performance data collection", toString(mCurrCollectionEvent));
    return {};
}

Result<void> WatchdogPerfService::endCustomCollection(int fd) {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent != EventType::CUSTOM_COLLECTION) {
        return Error(INVALID_OPERATION) << "No custom collection is running";
    }

    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    mHandlerLooper->removeMessages(thiz);
    mHandlerLooper->sendMessage(thiz, SwitchMessage::END_CUSTOM_COLLECTION);

    if (const auto result = dumpCollectorsStatusLocked(fd); !result.ok()) {
        return Error(FAILED_TRANSACTION) << result.error();
    }

    if (!WriteStringToFd(StringPrintf("%sPerformance data report for custom collection:\n%s",
                                      kDumpMajorDelimiter.c_str(), kDumpMajorDelimiter.c_str()),
                         fd) ||
        !WriteStringToFd(mCustomCollection.toString(), fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to write custom collection report.";
    }

    for (const auto& processor : mDataProcessors) {
        if (const auto result = processor->onCustomCollectionDump(fd); !result.ok()) {
            return Error(FAILED_TRANSACTION)
                    << processor->name() << " failed on " << toString(mCurrCollectionEvent)
                    << " collection: " << result.error();
        }
    }

    if (DEBUG) {
        ALOGD("Custom event finished");
    }
    WriteStringToFd(kDumpMajorDelimiter, fd);
    return {};
}

void WatchdogPerfService::switchToPeriodicLocked(bool startNow) {
    if (mCurrCollectionEvent == EventType::PERIODIC_COLLECTION) {
        ALOGW("The current performance data collection event is already %s",
              toString(mCurrCollectionEvent));
        return;
    }
    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    mHandlerLooper->removeMessages(thiz);
    mCurrCollectionEvent = EventType::PERIODIC_COLLECTION;
    mPeriodicCollection.lastPollUptimeNs = mHandlerLooper->now();
    if (startNow) {
        mHandlerLooper->sendMessage(thiz, EventType::PERIODIC_COLLECTION);
    } else {
        mPeriodicCollection.lastPollUptimeNs += mPeriodicCollection.pollingIntervalNs.count();
        mHandlerLooper->sendMessageAtTime(mPeriodicCollection.lastPollUptimeNs, thiz,
                                          EventType::PERIODIC_COLLECTION);
    }
    mPeriodicMonitor.lastPollUptimeNs =
            mHandlerLooper->now() + mPeriodicMonitor.pollingIntervalNs.count();
    mHandlerLooper->sendMessageAtTime(mPeriodicMonitor.lastPollUptimeNs, thiz,
                                      EventType::PERIODIC_MONITOR);
    ALOGI("Switching to %s and %s", toString(mCurrCollectionEvent),
          toString(EventType::PERIODIC_MONITOR));
}

void WatchdogPerfService::handleMessage(const Message& message) {
    Result<void> result;

    switch (message.what) {
        case static_cast<int>(EventType::BOOT_TIME_COLLECTION):
            result = processCollectionEvent(&mBoottimeCollection);
            break;
        case static_cast<int>(SwitchMessage::END_BOOTTIME_COLLECTION):
            mHandlerLooper->removeMessages(sp<WatchdogPerfService>::fromExisting(this));
            if (result = processCollectionEvent(&mBoottimeCollection); result.ok()) {
                Mutex::Autolock lock(mMutex);
                switchToPeriodicLocked(/*startNow=*/false);
            }
            break;
        case static_cast<int>(EventType::PERIODIC_COLLECTION):
            result = processCollectionEvent(&mPeriodicCollection);
            break;
        case static_cast<int>(EventType::USER_SWITCH_COLLECTION):
            result = processCollectionEvent(&mUserSwitchCollection);
            break;
        case static_cast<int>(EventType::WAKE_UP_COLLECTION):
            result = processCollectionEvent(&mWakeUpCollection);
            break;
        case static_cast<int>(SwitchMessage::END_USER_SWITCH_COLLECTION):
        case static_cast<int>(SwitchMessage::END_WAKE_UP_COLLECTION): {
            mHandlerLooper->removeMessages(sp<WatchdogPerfService>::fromExisting(this));
            EventMetadata* eventMetadata =
                    message.what == static_cast<int>(SwitchMessage::END_USER_SWITCH_COLLECTION)
                    ? &mUserSwitchCollection
                    : &mWakeUpCollection;
            if (result = processCollectionEvent(eventMetadata); result.ok()) {
                Mutex::Autolock lock(mMutex);
                switchToPeriodicLocked(/*startNow=*/false);
            }
            break;
        }
        case static_cast<int>(EventType::CUSTOM_COLLECTION):
            result = processCollectionEvent(&mCustomCollection);
            break;
        case static_cast<int>(EventType::PERIODIC_MONITOR):
            result = processMonitorEvent(&mPeriodicMonitor);
            break;
        case static_cast<int>(SwitchMessage::END_CUSTOM_COLLECTION): {
            Mutex::Autolock lock(mMutex);
            if (EventType expected = EventType::CUSTOM_COLLECTION;
                mCurrCollectionEvent != expected) {
                ALOGW("Skipping END_CUSTOM_COLLECTION message as the current collection %s != %s",
                      toString(mCurrCollectionEvent), toString(expected));
                return;
            }
            mCustomCollection = {};
            for (const auto& processor : mDataProcessors) {
                /*
                 * Clear custom collection cache on the data processors when the custom collection
                 * ends.
                 */
                processor->onCustomCollectionDump(-1);
            }
            switchToPeriodicLocked(/*startNow=*/true);
            return;
        }
        case static_cast<int>(TaskMessage::SEND_RESOURCE_STATS):
            result = sendResourceStats();
            break;
        default:
            result = Error() << "Unknown message: " << message.what;
    }

    if (!result.ok()) {
        Mutex::Autolock lock(mMutex);
        ALOGE("Terminating %s: %s", kServiceName, result.error().message().c_str());
        /*
         * DO NOT CALL terminate() as it tries to join the collection thread but this code is
         * executed on the collection thread. Thus it will result in a deadlock.
         */
        mCurrCollectionEvent = EventType::TERMINATED;
        mHandlerLooper->removeMessages(sp<WatchdogPerfService>::fromExisting(this));
        mHandlerLooper->wake();
    }
}

Result<void> WatchdogPerfService::processCollectionEvent(
        WatchdogPerfService::EventMetadata* metadata) {
    Mutex::Autolock lock(mMutex);
    /*
     * Messages sent to the looper are intrinsically racy such that a message from the previous
     * collection event may land in the looper after the current collection has already begun. Thus
     * verify the current collection event before starting the collection.
     */
    if (mCurrCollectionEvent != metadata->eventType) {
        ALOGW("Skipping %s event on collection event %s", toString(metadata->eventType),
              toString(mCurrCollectionEvent));
        return {};
    }
    if (DEBUG) {
        ALOGD("Processing %s collection event", toString(metadata->eventType));
    }
    if (metadata->pollingIntervalNs < kMinEventInterval) {
        return Error()
                << "Collection polling interval of "
                << std::chrono::duration_cast<std::chrono::seconds>(metadata->pollingIntervalNs)
                           .count()
                << " seconds for " << toString(metadata->eventType)
                << " collection cannot be less than "
                << std::chrono::duration_cast<std::chrono::seconds>(kMinEventInterval).count()
                << " seconds";
    }
    if (const auto result = collectLocked(metadata); !result.ok()) {
        return Error() << toString(metadata->eventType) << " collection failed: " << result.error();
    }
    metadata->lastPollUptimeNs += metadata->pollingIntervalNs.count();
    mHandlerLooper->sendMessageAtTime(metadata->lastPollUptimeNs,
                                      sp<WatchdogPerfService>::fromExisting(this),
                                      metadata->eventType);
    return {};
}

Result<void> WatchdogPerfService::collectLocked(WatchdogPerfService::EventMetadata* metadata) {
    if (!mUidStatsCollector->enabled() && !mProcStatCollector->enabled()) {
        return Error() << "No collectors enabled";
    }

    auto now = std::chrono::time_point_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now());
    int64_t timeSinceBootMillis = kGetElapsedTimeSinceBootMillisFunc();

    if (mUidStatsCollector->enabled()) {
        if (const auto result = mUidStatsCollector->collect(); !result.ok()) {
            return Error() << "Failed to collect per-uid proc and I/O stats: " << result.error();
        }
    }

    if (mProcStatCollector->enabled()) {
        if (const auto result = mProcStatCollector->collect(); !result.ok()) {
            return Error() << "Failed to collect proc stats: " << result.error();
        }
    }

    ResourceStats resourceStats = {};

    for (const auto& processor : mDataProcessors) {
        Result<void> result;
        switch (mCurrCollectionEvent) {
            case EventType::BOOT_TIME_COLLECTION:
                result = processor->onBoottimeCollection(now, mUidStatsCollector,
                                                         mProcStatCollector, &resourceStats);
                break;
            case EventType::PERIODIC_COLLECTION:
                result = processor->onPeriodicCollection(now, mSystemState, mUidStatsCollector,
                                                         mProcStatCollector, &resourceStats);
                break;
            case EventType::USER_SWITCH_COLLECTION: {
                WatchdogPerfService::UserSwitchEventMetadata* userSwitchMetadata =
                        static_cast<WatchdogPerfService::UserSwitchEventMetadata*>(metadata);
                result = processor->onUserSwitchCollection(now, userSwitchMetadata->from,
                                                           userSwitchMetadata->to,
                                                           mUidStatsCollector, mProcStatCollector);
                break;
            }
            case EventType::WAKE_UP_COLLECTION:
                result = processor->onWakeUpCollection(now, mUidStatsCollector, mProcStatCollector);
                break;
            case EventType::CUSTOM_COLLECTION:
                result = processor->onCustomCollection(now, mSystemState, metadata->filterPackages,
                                                       mUidStatsCollector, mProcStatCollector,
                                                       &resourceStats);
                break;
            default:
                result = Error() << "Invalid collection event " << toString(mCurrCollectionEvent);
        }
        if (!result.ok()) {
            return Error() << processor->name() << " failed on " << toString(mCurrCollectionEvent)
                           << " collection: " << result.error();
        }
    }

    if (!isEmpty(resourceStats)) {
        if (resourceStats.resourceUsageStats.has_value()) {
            resourceStats.resourceUsageStats->durationInMillis =
                    timeSinceBootMillis - mLastCollectionTimeMillis;
        }
        cacheUnsentResourceStatsLocked(std::move(resourceStats));
    }

    mLastCollectionTimeMillis = timeSinceBootMillis;

    if (mUnsentResourceStats.empty() || !mWatchdogServiceHelper->isServiceConnected()) {
        if (DEBUG && !mWatchdogServiceHelper->isServiceConnected()) {
            ALOGD("Cannot send resource stats since CarWatchdogService not connected.");
        }
        return {};
    }

    // Send message to send resource stats
    mHandlerLooper->sendMessage(sp<WatchdogPerfService>::fromExisting(this),
                                TaskMessage::SEND_RESOURCE_STATS);

    return {};
}

Result<void> WatchdogPerfService::processMonitorEvent(
        WatchdogPerfService::EventMetadata* metadata) {
    if (metadata->eventType != static_cast<int>(EventType::PERIODIC_MONITOR)) {
        return Error() << "Invalid monitor event " << toString(metadata->eventType);
    }
    if (DEBUG) {
        ALOGD("Processing %s monitor event", toString(metadata->eventType));
    }
    if (metadata->pollingIntervalNs < kMinEventInterval) {
        return Error()
                << "Monitor polling interval of "
                << std::chrono::duration_cast<std::chrono::seconds>(metadata->pollingIntervalNs)
                           .count()
                << " seconds for " << toString(metadata->eventType) << " event cannot be less than "
                << std::chrono::duration_cast<std::chrono::seconds>(kMinEventInterval).count()
                << " seconds";
    }
    Mutex::Autolock lock(mMutex);
    if (!mProcDiskStatsCollector->enabled()) {
        return Error() << "Cannot access proc disk stats for monitoring";
    }
    time_t now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    if (const auto result = mProcDiskStatsCollector->collect(); !result.ok()) {
        return Error() << "Failed to collect disk stats: " << result.error();
    }
    auto* currCollectionMetadata = getCurrentCollectionMetadataLocked();
    if (currCollectionMetadata == nullptr) {
        return Error() << "No metadata available for current collection event: "
                       << toString(mCurrCollectionEvent);
    }
    bool requestedCollection = false;
    auto thiz = sp<WatchdogPerfService>::fromExisting(this);
    const auto requestCollection = [&]() mutable {
        if (requestedCollection) {
            return;
        }
        const nsecs_t prevUptimeNs = currCollectionMetadata->lastPollUptimeNs -
                currCollectionMetadata->pollingIntervalNs.count();
        nsecs_t uptimeNs = mHandlerLooper->now();
        if (const auto delta = std::abs(uptimeNs - prevUptimeNs);
            delta < kMinEventInterval.count()) {
            return;
        }
        currCollectionMetadata->lastPollUptimeNs = uptimeNs;
        mHandlerLooper->removeMessages(thiz, currCollectionMetadata->eventType);
        mHandlerLooper->sendMessage(thiz, currCollectionMetadata->eventType);
        requestedCollection = true;
    };
    for (const auto& processor : mDataProcessors) {
        if (const auto result =
                    processor->onPeriodicMonitor(now, mProcDiskStatsCollector, requestCollection);
            !result.ok()) {
            return Error() << processor->name() << " failed on " << toString(metadata->eventType)
                           << ": " << result.error();
        }
    }
    metadata->lastPollUptimeNs += metadata->pollingIntervalNs.count();
    if (metadata->lastPollUptimeNs == currCollectionMetadata->lastPollUptimeNs) {
        /*
         * If the |PERIODIC_MONITOR| and  *_COLLECTION events overlap, skip the |PERIODIC_MONITOR|
         * event.
         */
        metadata->lastPollUptimeNs += metadata->pollingIntervalNs.count();
    }
    mHandlerLooper->sendMessageAtTime(metadata->lastPollUptimeNs, thiz, metadata->eventType);
    return {};
}

Result<void> WatchdogPerfService::sendResourceStats() {
    std::vector<ResourceStats> unsentResourceStats = {};
    {
        Mutex::Autolock lock(mMutex);
        nsecs_t now = mHandlerLooper->now();
        for (auto it = mUnsentResourceStats.begin(); it != mUnsentResourceStats.end();) {
            if (now - std::get<nsecs_t>(*it) >= kPrevUnsentResourceStatsMaxDurationNs.count()) {
                // Drop the expired stats
                it = mUnsentResourceStats.erase(it);
                continue;
            }
            unsentResourceStats.push_back(std::get<ResourceStats>(*it));
            ++it;
        }
    }
    if (unsentResourceStats.empty()) {
        return {};
    }
    if (auto status = mWatchdogServiceHelper->onLatestResourceStats(unsentResourceStats);
        !status.isOk()) {
        ALOGW("Failed to push the unsent resource stats to watchdog service: %s",
              status.getDescription().c_str());
        return {};
    }
    Mutex::Autolock lock(mMutex);
    mUnsentResourceStats.clear();
    if (DEBUG) {
        ALOGD("Pushed latest resource usage and I/O overuse stats to watchdog service");
    }
    return {};
}

Result<void> WatchdogPerfService::notifySystemStartUpLocked() {
    for (const auto& processor : mDataProcessors) {
        if (const auto result = processor->onSystemStartup(); !result.ok()) {
            ALOGE("%s failed to process system startup event", processor->name().c_str());
            return Error() << processor->name() << " failed to process system startup event";
        }
    }
    return {};
}

void WatchdogPerfService::cacheUnsentResourceStatsLocked(ResourceStats resourceStats) {
    mUnsentResourceStats.push_back(
            std::make_tuple(mHandlerLooper->now(), std::move(resourceStats)));
    if (mUnsentResourceStats.size() > kMaxCachedUnsentResourceStats) {
        mUnsentResourceStats.erase(mUnsentResourceStats.begin());
    }
}

WatchdogPerfService::EventMetadata* WatchdogPerfService::getCurrentCollectionMetadataLocked() {
    switch (mCurrCollectionEvent) {
        case EventType::BOOT_TIME_COLLECTION:
            return &mBoottimeCollection;
        case EventType::PERIODIC_COLLECTION:
            return &mPeriodicCollection;
        case EventType::USER_SWITCH_COLLECTION:
            return &mUserSwitchCollection;
        case EventType::WAKE_UP_COLLECTION:
            return &mWakeUpCollection;
        case EventType::CUSTOM_COLLECTION:
            return &mCustomCollection;
        default:
            return nullptr;
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
