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

#define LOG_TAG "libPixelUsbOverheat"

#include "include/pixelusb/UsbOverheatEvent.h"

#include <aidl/android/hardware/thermal/IThermal.h>
#include <android/binder_manager.h>
#include <thermalutils/ThermalHidlWrapper.h>
#include <time.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

// Start monitoring the temperature
static volatile bool monitorTemperature;

constexpr int kEpollEvents = 10;
constexpr char kOverheatLock[] = "overheat";
constexpr char kWakeLockPath[] = "/sys/power/wake_lock";
constexpr char kWakeUnlockPath[] = "/sys/power/wake_unlock";

int addEpollFdWakeUp(const unique_fd &epfd, const unique_fd &fd) {
    struct epoll_event event {};
    int ret;

    event.data.fd = fd;
    event.events = EPOLLIN | EPOLLWAKEUP;

    ret = epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &event);
    if (ret)
        ALOGE("epoll_ctl error %d", errno);

    return ret;
}

UsbOverheatEvent::UsbOverheatEvent(const ZoneInfo &monitored_zone,
                                   const std::vector<ZoneInfo> &queried_zones,
                                   const int &monitor_interval_sec)
    : monitored_zone_(monitored_zone),
      queried_zones_(queried_zones),
      monitor_interval_sec_(monitor_interval_sec),
      monitor_() {
    int fd = timerfd_create(CLOCK_BOOTTIME_ALARM, 0);
    if (fd < 0) {
        ALOGE("timerfd_create failed: %d", errno);
    }

    unique_fd timerFd(timerfd_create(CLOCK_BOOTTIME_ALARM, 0));
    if (timerFd == -1) {
        ALOGE("timerFd failed to create %d", errno);
        abort();
    }

    unique_fd epollFd(epoll_create(2));
    if (epollFd == -1) {
        ALOGE("epoll_fd_ failed to create %d", errno);
        abort();
    }

    unique_fd eventFd(eventfd(0, 0));
    if (eventFd == -1) {
        ALOGE("event_fd_ failed to create %d", errno);
        abort();
    }

    if (addEpollFdWakeUp(epollFd, timerFd) == -1) {
        ALOGE("Adding timerFd failed");
        abort();
    }

    if (addEpollFdWakeUp(epollFd, eventFd) == -1) {
        ALOGE("Adding eventFd failed");
        abort();
    }

    epoll_fd_ = std::move(epollFd);
    timer_fd_ = std::move(timerFd);
    event_fd_ = std::move(eventFd);

    monitor_ = unique_ptr<thread>(new thread(this->monitorThread, this));
    registerListener();
}

UsbOverheatEvent::~UsbOverheatEvent() {
    unregisterThermalCallback();
}

static int wakelock_cnt = 0;
static std::mutex wakelock_lock;

static void wakeLockAcquire() {
    lock_guard<mutex> lock(wakelock_lock);

    wakelock_cnt++;
    if (wakelock_cnt == 1) {
        ALOGV("Acquire wakelock");
        if (!WriteStringToFile(kOverheatLock, kWakeLockPath)) {
            ALOGE("Failed to acquire wake lock string");
        }
    }
}

static void wakeLockRelease() {
    lock_guard<mutex> lock(wakelock_lock);

    wakelock_cnt--;
    if (wakelock_cnt == 0) {
        ALOGV("Release wakelock");
        if (!WriteStringToFile(kOverheatLock, kWakeUnlockPath)) {
            ALOGE("Failed to acquire wake lock string");
        }
    }
}

void *UsbOverheatEvent::monitorThread(void *param) {
    auto *overheatEvent = reinterpret_cast<UsbOverheatEvent *>(param);
    struct epoll_event events[kEpollEvents];
    struct itimerspec delay = itimerspec();

    while (true) {
        uint64_t fired;
        float temperature = 0;
        string status;

        for (vector<ZoneInfo>::size_type i = 0; i < overheatEvent->queried_zones_.size(); i++) {
            if (overheatEvent->getCurrentTemperature(overheatEvent->queried_zones_[i].name_,
                                                     &temperature)) {
                if (i == 0)
                    overheatEvent->max_overheat_temp_ =
                            max(temperature, overheatEvent->max_overheat_temp_);
                status.append(overheatEvent->queried_zones_[i].name_);
                status.append(":");
                status.append(std::to_string(temperature));
                status.append(" ");
            }
        }
        ALOGW("%s", status.c_str());

        delay.it_value.tv_sec = monitorTemperature ? overheatEvent->monitor_interval_sec_ : 0;
        int ret = timerfd_settime(overheatEvent->timer_fd_, 0, &delay, NULL);
        if (ret < 0) {
            ALOGE("timerfd_settime failed. err:%d tv_sec:%ld", errno, delay.it_value.tv_sec);
            continue;
        }

        wakeLockRelease();
        int nrEvents = epoll_wait(overheatEvent->epoll_fd_, events, kEpollEvents, -1);
        wakeLockAcquire();
        if (nrEvents <= 0) {
            ALOGE("nrEvents negative skipping");
            continue;
        }

        for (int i = 0; i < nrEvents; i++) {
            ALOGV("event=%u on fd=%d\n", events[i].events, events[i].data.fd);

            if (events[i].data.fd == overheatEvent->timer_fd_) {
                ALOGI("Wake up caused by timer fd");
                int numRead = read(overheatEvent->timer_fd_, &fired, sizeof(fired));
                if (numRead != sizeof(fired)) {
                    ALOGV("numRead incorrect");
                }
                if (fired != 1) {
                    ALOGV("Fired not set to 1");
                }
            } else {
                ALOGI("Wake up caused by event fd");
                read(overheatEvent->event_fd_, &fired, sizeof(fired));
            }
        }
    }
}

bool UsbOverheatEvent::registerListener() {
    ALOGV("UsbOverheatEvent::registerListener");
    const std::lock_guard<std::mutex> lock(thermal_hal_mutex_);
    if (connectAidlThermalService()) {
        return true;
    }

    sp<IServiceManager> sm = IServiceManager::getService();
    if (sm == NULL) {
        ALOGE("Hardware service manager is not running");
        return false;
    }
    Return<bool> result = sm->registerForNotifications(IThermal::descriptor, "", this);
    if (result.isOk()) {
        ALOGI("Thermal HIDL service connected!");
        return true;
    }
    ALOGE("Failed to register for hardware service manager notifications: %s",
          result.description().c_str());
    return false;
}

bool UsbOverheatEvent::connectAidlThermalService() {
    const std::string thermal_instance_name =
            std::string(::aidl::android::hardware::thermal::IThermal::descriptor) + "/default";
    if (AServiceManager_isDeclared(thermal_instance_name.c_str())) {
        auto thermal_aidl_service = ::aidl::android::hardware::thermal::IThermal::fromBinder(
                ndk::SpAIBinder(AServiceManager_waitForService(thermal_instance_name.c_str())));
        if (thermal_aidl_service) {
            thermal_service_ = sp<::aidl::android::hardware::thermal::ThermalHidlWrapper>::make(
                    thermal_aidl_service);
            if (thermal_aidl_death_recipient_.get() == nullptr) {
                thermal_aidl_death_recipient_ = ndk::ScopedAIBinder_DeathRecipient(
                        AIBinder_DeathRecipient_new(onThermalAidlBinderDied));
            }
            auto linked = AIBinder_linkToDeath(thermal_aidl_service->asBinder().get(),
                                               thermal_aidl_death_recipient_.get(), this);
            if (linked != STATUS_OK) {
                ALOGE("Failed to register AIDL thermal death recipient");
            }
            is_thermal_callback_registered_ = registerThermalCallback("AIDL");
            return is_thermal_callback_registered_;
        } else {
            ALOGE("Unable to get Thermal AIDL service");
        }
    } else {
        ALOGW("Thermal AIDL service is not declared");
    }
    return false;
}

void UsbOverheatEvent::unregisterThermalCallback() {
    const std::lock_guard<std::mutex> lock(thermal_hal_mutex_);
    if (is_thermal_callback_registered_) {
        ThermalStatus returnStatus;
        auto ret = thermal_service_->unregisterThermalChangedCallback(
                this, [&returnStatus](ThermalStatus status) { returnStatus = status; });
        if (!ret.isOk() || returnStatus.code != ThermalStatusCode::SUCCESS) {
            ALOGE("Failed to unregister thermal callback");
        }
    }
}

void UsbOverheatEvent::wakeupMonitor() {
    // <flag> value does not have any significance here
    uint64_t flag = 100;

    unsigned long ret = TEMP_FAILURE_RETRY(write(event_fd_, &flag, sizeof(flag)));
    if (ret < 0) {
        ALOGE("Error writing eventfd errno=%d", errno);
    }
}

bool UsbOverheatEvent::startRecording() {
    ALOGI("Start recording. monitorTemperature:%d", monitorTemperature ? 1 : 0);
    // Bail out if temperature was being monitored previously
    if (monitorTemperature)
        return true;

    wakeLockAcquire();
    monitorTemperature = true;
    wakeupMonitor();
    return true;
}

bool UsbOverheatEvent::stopRecording() {
    ALOGI("Stop recording. monitorTemperature:%d", monitorTemperature ? 1 : 0);
    // Bail out if temperature was not being monitored previously
    if (!monitorTemperature)
        return true;

    wakeLockRelease();
    monitorTemperature = false;
    wakeupMonitor();

    return true;
}

bool UsbOverheatEvent::getCurrentTemperature(const string &name, float *temp) {
    ThermalStatus thermal_status;
    hidl_vec<Temperature> thermal_temperatures;
    const std::lock_guard<std::mutex> lock(thermal_hal_mutex_);
    if (thermal_service_ == NULL)
        return false;

    auto ret = thermal_service_->getCurrentTemperatures(
            false, TemperatureType::USB_PORT,
            [&](ThermalStatus status, hidl_vec<Temperature> temperatures) {
                thermal_status = status;
                thermal_temperatures = temperatures;
            });

    if (ret.isOk() && thermal_status.code == ThermalStatusCode::SUCCESS) {
        for (auto temperature : thermal_temperatures) {
            if (temperature.name == name) {
                *temp = temperature.value;
                return true;
            }
        }
    }
    return false;
}

float UsbOverheatEvent::getMaxOverheatTemperature() {
    return max_overheat_temp_;
}

Return<void> UsbOverheatEvent::onRegistration(const hidl_string & /*fully_qualified_name*/,
                                              const hidl_string & /*instance_name*/,
                                              bool /*pre_existing*/) {
    ThermalStatus thermal_status;
    const std::lock_guard<std::mutex> lock(thermal_hal_mutex_);
    thermal_service_ = IThermal::getService();
    if (thermal_service_ == NULL) {
        ALOGE("Unable to get Themal HIDL Service");
        return Void();
    }
    is_thermal_callback_registered_ = registerThermalCallback("HIDL");
    return Void();
}

bool UsbOverheatEvent::registerThermalCallback(const string &service_str) {
    ThermalStatus thermal_status;
    auto ret = thermal_service_->registerThermalChangedCallback(
            this, true, monitored_zone_.type_,
            [&](ThermalStatus status) { thermal_status = status; });

    if (!ret.isOk() || thermal_status.code != ThermalStatusCode::SUCCESS) {
        ALOGE("Failed to register thermal changed callback using %s client", service_str.c_str());
        return false;
    }
    return true;
}

Return<void> UsbOverheatEvent::notifyThrottling(const Temperature &temperature) {
    ALOGV("notifyThrottling '%s' T=%2.2f throttlingStatus=%d", temperature.name.c_str(),
          temperature.value, temperature.throttlingStatus);
    if (temperature.type == monitored_zone_.type_) {
        if (temperature.throttlingStatus >= monitored_zone_.severity_) {
            startRecording();
        } else {
            stopRecording();
        }
    }
    return Void();
}

ZoneInfo::ZoneInfo(const TemperatureType &type, const string &name,
                   const ThrottlingSeverity &severity)
    : type_(type), name_(name), severity_(severity) {}
}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
