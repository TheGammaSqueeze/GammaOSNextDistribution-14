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

#include "CarDisplayProxy.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/bufferqueue/2.0/B2HGraphicBufferProducer.h>
#include <gui/view/Surface.h>
#include <ui/Rotation.h>

#include <cinttypes>

namespace {

using ::aidl::android::frameworks::automotive::display::DisplayDesc;
using ::aidl::android::frameworks::automotive::display::Rotation;
using ::aidl::android::hardware::common::NativeHandle;
using ::android::SurfaceComposerClient;
using ::ndk::ScopedAStatus;

// We're using the highest Z-order.
constexpr int32_t kSurfaceZOrder = 0x7FFFFFFF;

Rotation convert(::android::ui::Rotation uiRotation) {
    switch (uiRotation) {
        case ::android::ui::ROTATION_0:
            return Rotation::ROTATION_0;
        case ::android::ui::ROTATION_90:
            return Rotation::ROTATION_90;
        case ::android::ui::ROTATION_180:
            return Rotation::ROTATION_180;
        case ::android::ui::ROTATION_270:
            return Rotation::ROTATION_270;
    }
}

constexpr size_t kMaxWindowSize = 256;

native_handle_t* convertHalTokenToNativeHandle(const ::android::HalToken& halToken) {
    // Attempts to store halToken in the ints of the native_handle_t after its
    // size.
    auto nhDataByteSize = halToken.size();
    if (nhDataByteSize > kMaxWindowSize) {
        return nullptr;
    }

    auto numInts = ceil(nhDataByteSize / sizeof(int)) + 1;
    native_handle_t* nh = native_handle_create(/* numFds = */ 0, numInts);
    if (nh == nullptr) {
        return nullptr;
    }

    // Stores the size of the token in the first int
    nh->data[0] = nhDataByteSize;
    memcpy(&(nh->data[1]), halToken.data(), nhDataByteSize);
    return nh;
}

}  // namespace

namespace aidl::android::frameworks::automotive::display::implementation {

ScopedAStatus CarDisplayProxy::getDisplayIdList(std::vector<int64_t>* _aidl_return) {
    std::vector<::android::PhysicalDisplayId> displayIds =
            SurfaceComposerClient::getPhysicalDisplayIds();
    std::for_each(displayIds.begin(), displayIds.end(),
                  [_aidl_return](const ::android::PhysicalDisplayId& id) {
                      _aidl_return->push_back(id.value);
                  });

    return ScopedAStatus::ok();
}

ScopedAStatus CarDisplayProxy::getDisplayInfo(int64_t id, DisplayDesc* _aidl_return) {
    ::android::ui::DisplayMode displayMode;
    ::android::ui::DisplayState displayState;
    if (!getDisplayInfoFromSurfaceComposerClient(id, &displayMode, &displayState)) {
        LOG(ERROR) << "Invalid display id = " << id;
        return ScopedAStatus::fromStatus(STATUS_BAD_VALUE);
    }

    DisplayDesc desc = {
            .width = displayMode.resolution.width,
            .height = displayMode.resolution.height,
            .layer = displayState.layerStack.id,
            .orientation = convert(displayState.orientation),
    };
    *_aidl_return = std::move(desc);
    return ScopedAStatus::ok();
}

::android::sp<::android::IBinder> CarDisplayProxy::getDisplayInfoFromSurfaceComposerClient(
        int64_t id, ::android::ui::DisplayMode* displayMode,
        ::android::ui::DisplayState* displayState) {
    ::android::sp<::android::IBinder> displayToken;
    std::optional<::android::PhysicalDisplayId> displayId =
            ::android::DisplayId::fromValue<::android::PhysicalDisplayId>(id);
    if (!displayId) {
        LOG(ERROR) << "Failed to get a valid display name";
        return nullptr;
    }

    displayToken = SurfaceComposerClient::getPhysicalDisplayToken(*displayId);
    if (!displayToken) {
        LOG(ERROR) << "Failed to get a valid display token";
        return nullptr;
    }

    ::android::status_t status =
            SurfaceComposerClient::getActiveDisplayMode(displayToken, displayMode);
    if (status != ::android::NO_ERROR) {
        LOG(WARNING) << "Failed to read current mode of the display " << id;
    }

    status = SurfaceComposerClient::getDisplayState(displayToken, displayState);
    if (status != ::android::NO_ERROR) {
        LOG(WARNING) << "Failed to read current state of the display " << id;
    }

    return std::move(displayToken);
}

ScopedAStatus CarDisplayProxy::getHGraphicBufferProducer(int64_t id, NativeHandle* _aidl_return) {
    ::android::sp<::android::IBinder> displayToken;
    ::android::sp<::android::SurfaceControl> surfaceControl;

    if (auto status = getDisplayRecord(id, &displayToken, &surfaceControl); !status.isOk()) {
        return status;
    }

    // SurfaceControl::getSurface() is guaranteed to be non-null.
    auto targetSurface = surfaceControl->getSurface();
    auto igbp = targetSurface->getIGraphicBufferProducer();
    auto hgbp =
            new ::android::hardware::graphics::bufferqueue::V2_0::utils::B2HGraphicBufferProducer(
                    igbp);

    ::android::HalToken halToken;
    if (!::android::createHalToken(hgbp, &halToken)) {
        LOG(ERROR) << "Failed to create a hal token";
        return ScopedAStatus::fromStatus(STATUS_BAD_VALUE);
    }

    native_handle_t* handle = convertHalTokenToNativeHandle(halToken);
    auto scope_guard = ::android::base::make_scope_guard([handle]() {
        native_handle_close(handle);
        native_handle_delete(handle);
    });
    if (handle == nullptr) {
        LOG(ERROR) << "Failed to create a handle, errno = " << strerror(errno);
        return ScopedAStatus::fromStatus(STATUS_BAD_VALUE);
    }

    *_aidl_return = std::move(::android::dupToAidl(handle));
    return ScopedAStatus::ok();
}

ScopedAStatus CarDisplayProxy::getSurface(int64_t id, android::view::Surface* _aidl_return) {
    if (_aidl_return == nullptr) {
        LOG(ERROR) << __FUNCTION__
                   << " is called with an invalid aidl::android::view::Surface object.";
        return ScopedAStatus::fromStatus(STATUS_BAD_VALUE);
    }

    auto it = mSurfaceList.find(id);
    if (it != mSurfaceList.end() && ::android::Surface::isValid(mSurfaceList[id])) {
        LOG(DEBUG) << __FUNCTION__ << " reuses an existing surface for a display " << id;
        _aidl_return->reset(mSurfaceList[id].get());
        return ScopedAStatus::ok();
    }

    ::android::sp<::android::IBinder> displayToken;
    ::android::sp<::android::SurfaceControl> surfaceControl;
    if (auto status = getDisplayRecord(id, &displayToken, &surfaceControl); !status.isOk()) {
        return status;
    }

    // Retrieve a Surface associated with a target display.
    ::android::sp<::android::Surface> targetSurface = surfaceControl->getSurface();
    if (!targetSurface || !::android::Surface::isValid(targetSurface)) {
        LOG(ERROR) << "Created Surface is invalid, " << targetSurface.get();
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    // Bookkeep a retrieved Surface object and pass it to the client as a format
    // of ::aidl::android::view::Surface, which is alias to
    // ::aidl::android::hardware::NativeWindow.
    mSurfaceList.insert_or_assign(id, targetSurface);
    _aidl_return->reset(targetSurface.get());
    return ScopedAStatus::ok();
}

ScopedAStatus CarDisplayProxy::hideWindow(int64_t id) {
    auto it = mDisplays.find(id);
    if (it == mDisplays.end()) {
        LOG(DEBUG) << __FUNCTION__ << ": Invalid display id, " << id;
        return ScopedAStatus::ok();
    }

    auto status = SurfaceComposerClient::Transaction{}.hide(it->second.surfaceControl).apply();
    if (status != ::android::NO_ERROR) {
        LOG(DEBUG) << __FUNCTION__
                   << ": Failed to hide a surface, status = " << ::android::statusToString(status);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus CarDisplayProxy::showWindow(int64_t id) {
    auto it = mDisplays.find(id);
    if (it == mDisplays.end()) {
        LOG(ERROR) << __FUNCTION__ << ": Invalid display id, " << id;
        return ScopedAStatus::fromStatus(STATUS_BAD_VALUE);
    }

    const auto& displayToken = it->second.token;
    const auto& surfaceControl = it->second.surfaceControl;
    ::android::ui::DisplayState displayState;
    ::android::status_t status =
            SurfaceComposerClient::getDisplayState(displayToken, &displayState);
    if (status != ::android::NO_ERROR) {
        LOG(ERROR) << "Failed to read current state of the display " << id
                   << ", status = " << ::android::statusToString(status);
        return ScopedAStatus::fromStatus(status);
    }

    SurfaceComposerClient::Transaction t;
    t.setDisplayLayerStack(displayToken, displayState.layerStack);
    t.setLayerStack(surfaceControl, displayState.layerStack);

    status = t.setLayer(surfaceControl, kSurfaceZOrder).show(surfaceControl).apply();
    if (status != ::android::NO_ERROR) {
        LOG(ERROR) << "Failed to set a layer";
        return ScopedAStatus::fromStatus(status);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus CarDisplayProxy::getDisplayRecord(
        int64_t id, ::android::sp<::android::IBinder>* outDisplayToken,
        ::android::sp<::android::SurfaceControl>* outSurfaceControl) {
    auto it = mDisplays.find(id);
    if (it != mDisplays.end()) {
        *outDisplayToken = mDisplays[id].token;
        *outSurfaceControl = mDisplays[id].surfaceControl;
        return ScopedAStatus::ok();
    }

    ::android::sp<::android::IBinder> displayToken;
    ::android::sp<::android::SurfaceControl> surfaceControl;
    ::android::ui::DisplayMode displayMode;
    ::android::ui::DisplayState displayState;
    displayToken = getDisplayInfoFromSurfaceComposerClient(id, &displayMode, &displayState);
    if (!displayToken) {
        LOG(ERROR) << "Failed to read display information from SurfaceComposerClient.";
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    auto displayWidth = displayMode.resolution.getWidth();
    auto displayHeight = displayMode.resolution.getHeight();
    if ((displayState.orientation != ::android::ui::ROTATION_0) &&
        (displayState.orientation != ::android::ui::ROTATION_180)) {
        std::swap(displayWidth, displayHeight);
    }

    ::android::sp<SurfaceComposerClient> client = new SurfaceComposerClient();
    ::android::status_t status = client->initCheck();
    if (status != ::android::NO_ERROR) {
        LOG(ERROR) << "SurfaceComposerClient::initCheck() fails, error = "
                   << ::android::statusToString(status);
        return ScopedAStatus::fromStatus(status);
    }

    surfaceControl =
            client->createSurface(::android::String8::format("CarDisplayProxy::%" PRIx64, id),
                                  displayWidth, displayHeight, ::android::PIXEL_FORMAT_RGBX_8888,
                                  ::android::ISurfaceComposerClient::eOpaque);
    if (!surfaceControl || !surfaceControl->isValid()) {
        LOG(ERROR) << "Failed to create a SurfaceControl";
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    *outDisplayToken = displayToken;
    *outSurfaceControl = surfaceControl;

    DisplayRecord rec = {displayToken, surfaceControl};
    mDisplays.insert_or_assign(id, std::move(rec));

    return ScopedAStatus::ok();
}

}  // namespace aidl::android::frameworks::automotive::display::implementation
