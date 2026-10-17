// Copyright 2020-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <algorithm>
#include <chrono>
#include <future>
#include <thread>

#include <android/sensor.h>

#include "common/assert.h"
#include "common/gammaos_tweak.h"
#include "common/logging/log.h"
#include "common/thread.h"
#include "common/vector_math.h"
#include "jni/ndk_motion.h"

namespace InputManager {

namespace {
using Common::Vec3;
}

class NDKMotion final : public Input::MotionDevice {
    std::chrono::microseconds update_period;
    NDKMotionFactory* owner_factory;

    ASensorManager* sensor_manager = nullptr;
    ALooper* looper = nullptr;
    ASensorEventQueue* event_queue;

    // EnableSensors()/DisableSensors() can be invoked from the UI thread
    // (pauseEmulation/unPauseEmulation) or from the emulation thread (the
    // RunLoop error handling path), while Update() is invoked from inside the core.
    // The underlying ASensorEventQueue is not safe for concurrent
    // use from multiple threads, so without this lock, concurrent access can
    // cause queue corruption and crashes.
    mutable std::mutex sensor_mutex;

    mutable std::atomic<Vec3<float>> acceleration{};
    mutable std::atomic<Vec3<float>> rotation{};
    static_assert(decltype(acceleration)::is_always_lock_free, "vectors are not lock free");
    std::thread poll_thread;
    std::atomic<bool> stop_polling = false;

    static Vec3<float> TransformAxes(Vec3<float> in) {
        // 3DS   Y+            Phone     Z+
        // on    |             laying    |
        // table |             in        |
        //       |_______ X-   portrait  |_______ X+
        //      /              mode     /
        //     /                       /
        //    Z-                      Y-
        Vec3<float> out;
        out.y = in.z;
        // rotations are 90 degrees counter-clockwise from portrait
        switch (screen_rotation) {
        case 0:
            out.x = -in.x;
            out.z = in.y;
            break;
        case 1:
            out.x = in.y;
            out.z = in.x;
            break;
        case 2:
            out.x = in.x;
            out.z = -in.y;
            break;
        case 3:
            out.x = -in.y;
            out.z = -in.x;
            break;
        default:
            UNREACHABLE();
        }
        return out;
    }

    void Construct() {
        sensor_manager = ASensorManager_getInstanceForPackage("org.citra.citra_emu");
        looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
        if (!sensor_manager || !looper) {
            LOG_CRITICAL(Input, "Could not retrieve sensor manager");
            return;
        }
        event_queue = ASensorManager_createEventQueue(sensor_manager, looper, 0, nullptr, nullptr);
        if (!event_queue) {
            LOG_ERROR(Input, "Could not create sensor event queue");
            return;
        }

        EnableSensors();
    }

    void Destruct() {
        ASensorManager_destroyEventQueue(sensor_manager, event_queue);
    }

    void Update() const {
        std::lock_guard lock{sensor_mutex};
        ALooper_pollOnce(0, nullptr, nullptr, nullptr);
        ASensorEvent event{};
        std::optional<Vec3<float>> new_accel{}, new_rot{};
        while (ASensorEventQueue_getEvents(event_queue, &event, 1) > 0) {
            if (event.type == ASENSOR_TYPE_ACCELEROMETER) {
                new_accel.emplace(event.vector.x, event.vector.y, event.vector.z);
            } else if (event.type == ASENSOR_TYPE_GYROSCOPE) {
                new_rot.emplace(event.vector.x, event.vector.y, event.vector.z);
            }
            // occasionally the queue has ASENSOR_TYPE_ADDITIONAL_INFO events
            // but so far there is no reason to handle them
        }
        if (new_accel) {
            // convert from m/(s^2) to g and invert
            acceleration = TransformAxes(*new_accel) / -ASENSOR_STANDARD_GRAVITY;
        }
        if (new_rot) {
            // convert from rad/s to deg/s
            rotation = TransformAxes(*new_rot) * 180.0f / static_cast<float>(M_PI);
        }
    }

public:
    NDKMotion(std::chrono::microseconds update_period_, NDKMotionFactory* owner_factory_,
              bool asynchronous = false)
        : update_period(update_period_), owner_factory(owner_factory_) {
        if (asynchronous) {
            // The sensor queue is created on the polling thread (its looper); wait for it so
            // the factory's EnableSensors/DisableSensors never see a queue still being built.
            std::promise<void> constructed;
            std::future<void> ready = constructed.get_future();
            poll_thread = std::thread([this, &constructed] {
                Common::SetCurrentThreadName("MotionPoll");
                Construct();
                constructed.set_value();
                auto start = std::chrono::high_resolution_clock::now();
                while (!stop_polling) {
                    Update();
                    std::this_thread::sleep_until(start += update_period);
                }
                Destruct();
            });
            ready.wait();
        } else {
            Construct();
        }
    }

    ~NDKMotion() {
        // Unregister first so the factory can never dereference this object
        // again once we start tearing it down. This blocks until any
        // EnableSensors()/DisableSensors() call already running through the
        // factory has finished, so it's safe to destroy event_queue after.
        if (owner_factory) {
            owner_factory->Unregister(this);
        }

        if (std::thread::id{} == poll_thread.get_id()) {
            Destruct();
        } else {
            stop_polling = true;
            poll_thread.join();
        }
    }

    std::tuple<Vec3<float>, Vec3<float>> GetStatus() const override {
        if (std::thread::id{} == poll_thread.get_id()) {
            Update();
        }
        return {acceleration, rotation};
    }

    void EnableSensors() {
        std::lock_guard lock{sensor_mutex};
        const auto init_sensor = [this](int sensor_type) {
            ASensorRef sensor = ASensorManager_getDefaultSensor(sensor_manager, sensor_type);
            if (!sensor) {
                LOG_ERROR(Input, "Could not find sensor of type {}", sensor_type);
                return;
            }
            int error = ASensorEventQueue_registerSensor(
                event_queue, sensor,
                std::max(ASensor_getMinDelay(sensor), static_cast<int>(update_period.count())), 0);
            if (error < 0)
                LOG_ERROR(Input, "Registering sensor returned error code {}", error);
        };

        LOG_TRACE(Input, "Enabling sensors..");
        init_sensor(ASENSOR_TYPE_ACCELEROMETER);
        init_sensor(ASENSOR_TYPE_GYROSCOPE);
    }

    void DisableSensors() {
        std::lock_guard lock{sensor_mutex};
        const auto disable_sensor = [this](int sensor_type) {
            ASensorRef sensor = ASensorManager_getDefaultSensor(sensor_manager, sensor_type);
            if (!sensor) {
                LOG_ERROR(Input, "Could not find sensor of type {}", sensor_type);
                return;
            }
            int error = ASensorEventQueue_disableSensor(event_queue, sensor);
            if (error < 0)
                LOG_ERROR(Input, "Disabling sensor returned error code {}", error);
        };

        LOG_TRACE(Input, "Disabling sensors..");
        disable_sensor(ASENSOR_TYPE_ACCELEROMETER);
        disable_sensor(ASENSOR_TYPE_GYROSCOPE);
    }
};

std::unique_ptr<Input::MotionDevice> NDKMotionFactory::Create(const Common::ParamPackage& params) {
    std::chrono::milliseconds update_period{params.Get("update_period", 4)};
    // GammaOS: poll the sensors on their own thread every update period instead of on the
    // emulation thread at every HID read (an epoll and a socket read each time, about 1 percent
    // of the emulation thread in 3D Land), and at the HID's own rate (below). persist.gammaos.azahar_nano.motion_thread = 1 / 0
    // forces it on / off; default Mali only.
    static const bool poll_thread =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.motion_thread");
    // The 3DS HID samples the accelerometer at 104 Hz and the gyroscope at 101 Hz: with the
    // polling thread the sensors are asked for 100 Hz (10 ms) instead of their fastest rate, so
    // the sensor service (a real-time thread) wakes half as often.
    if (poll_thread) {
        update_period = std::max(update_period, std::chrono::milliseconds{10});
    }
    std::unique_ptr<NDKMotion> ndk_motion =
        std::make_unique<NDKMotion>(update_period, this, poll_thread);
    {
        std::lock_guard lock{device_mutex};
        ndk_motion_device = ndk_motion.get();
    }
    return std::move(ndk_motion);
}

void NDKMotionFactory::EnableSensors() {
    std::lock_guard lock{device_mutex};
    if (ndk_motion_device) {
        ndk_motion_device->EnableSensors();
    }
}

void NDKMotionFactory::DisableSensors() {
    std::lock_guard lock{device_mutex};
    if (ndk_motion_device) {
        ndk_motion_device->DisableSensors();
    }
}

void NDKMotionFactory::Unregister(NDKMotion* device) {
    std::lock_guard lock{device_mutex};
    if (ndk_motion_device == device) {
        ndk_motion_device = nullptr;
    }
}

} // namespace InputManager
