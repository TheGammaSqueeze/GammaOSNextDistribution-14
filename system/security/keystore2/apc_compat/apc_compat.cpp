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

#include "apc_compat.hpp"
#include <android-base/logging.h>
#include <android/hardware/confirmationui/1.0/IConfirmationUI.h>
#include <hwbinder/IBinder.h>

#include <aidl/android/hardware/confirmationui/BnConfirmationResultCallback.h>
#include <aidl/android/hardware/confirmationui/IConfirmationResultCallback.h>
#include <aidl/android/hardware/confirmationui/IConfirmationUI.h>
#include <aidl/android/hardware/confirmationui/UIOption.h>
#include <android/binder_manager.h>

#include <memory>
#include <set>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "keystore2_apc_compat"

namespace keystore2 {

using android::sp;
using android::hardware::hidl_death_recipient;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hardware::Status;
using HidlConfirmationResultCb =
    android::hardware::confirmationui::V1_0::IConfirmationResultCallback;
using HidlConfirmationUI = android::hardware::confirmationui::V1_0::IConfirmationUI;
using android::hardware::confirmationui::V1_0::ResponseCode;
using HidlUIOptions = android::hardware::confirmationui::V1_0::UIOption;

using AidlConfirmationUI = ::aidl::android::hardware::confirmationui::IConfirmationUI;
using AidlBnConfirmationResultCb =
    ::aidl::android::hardware::confirmationui::BnConfirmationResultCallback;
using AidlUIOptions = ::aidl::android::hardware::confirmationui::UIOption;

class CompatSessionCB {
  public:
    void
    finalize(uint32_t responseCode, ApcCompatCallback callback,
             std::optional<std::reference_wrapper<const std::vector<uint8_t>>> dataConfirmed,
             std::optional<std::reference_wrapper<const std::vector<uint8_t>>> confirmationToken) {
        if (callback.result != nullptr) {
            size_t dataConfirmedSize = 0;
            const uint8_t* dataConfirmedPtr = nullptr;
            size_t confirmationTokenSize = 0;
            const uint8_t* confirmationTokenPtr = nullptr;
            if (responseCode == APC_COMPAT_ERROR_OK) {
                if (dataConfirmed) {
                    dataConfirmedPtr = dataConfirmed->get().data();
                    dataConfirmedSize = dataConfirmed->get().size();
                }
                if (confirmationToken) {
                    confirmationTokenPtr = confirmationToken->get().data();
                    confirmationTokenSize = confirmationToken->get().size();
                }
            }
            callback.result(callback.data, responseCode, dataConfirmedPtr, dataConfirmedSize,
                            confirmationTokenPtr, confirmationTokenSize);
        }
    }
};

class ConfuiHidlCompatSession : public HidlConfirmationResultCb,
                                public hidl_death_recipient,
                                public CompatSessionCB {
  public:
    static sp<ConfuiHidlCompatSession> tryGetService() {
        sp<HidlConfirmationUI> service = HidlConfirmationUI::tryGetService();
        if (service) {
            return sp<ConfuiHidlCompatSession>(new ConfuiHidlCompatSession(std::move(service)));
        } else {
            return nullptr;
        }
    }

    uint32_t promptUserConfirmation(ApcCompatCallback callback, const char* prompt_text,
                                    const uint8_t* extra_data, size_t extra_data_size,
                                    const char* locale, ApcCompatUiOptions ui_options) {
        std::string hidl_prompt(prompt_text);
        std::vector<uint8_t> hidl_extra(extra_data, extra_data + extra_data_size);
        std::vector<HidlUIOptions> hidl_ui_options;
        if (ui_options.inverted) {
            hidl_ui_options.push_back(HidlUIOptions::AccessibilityInverted);
        }
        if (ui_options.magnified) {
            hidl_ui_options.push_back(HidlUIOptions::AccessibilityMagnified);
        }
        auto lock = std::lock_guard(callback_lock_);
        if (callback_.result != nullptr) {
            return APC_COMPAT_ERROR_OPERATION_PENDING;
        }
        auto err = service_->linkToDeath(sp(this), 0);
        if (!err.isOk()) {
            LOG(ERROR) << "Communication error: promptUserConfirmation: "
                          "Trying to register death recipient: "
                       << err.description();
            return APC_COMPAT_ERROR_SYSTEM_ERROR;
        }

        auto rc = service_->promptUserConfirmation(sp(this), hidl_prompt, hidl_extra, locale,
                                                   hidl_ui_options);
        if (!rc.isOk()) {
            LOG(ERROR) << "Communication error: promptUserConfirmation: " << rc.description();
        } else if (rc == ResponseCode::OK) {
            callback_ = callback;
        }
        return responseCode2Compat(rc.withDefault(ResponseCode::SystemError));
    }

    void abort() { service_->abort(); }

    void finalize(ResponseCode responseCode, const hidl_vec<uint8_t>& dataConfirmed,
                  const hidl_vec<uint8_t>& confirmationToken) {
        ApcCompatCallback callback;
        {
            auto lock = std::lock_guard(callback_lock_);
            // Calling the callback consumes the callback data structure. We have to make
            // sure that it can only be called once.
            callback = callback_;
            callback_ = {nullptr, nullptr};
            // Unlock the callback_lock_ here. It must never be held while calling the callback.
        }

        if (callback.result != nullptr) {
            service_->unlinkToDeath(sp(this));

            std::vector<uint8_t> data = dataConfirmed;
            std::vector<uint8_t> token = confirmationToken;

            CompatSessionCB::finalize(responseCode2Compat(responseCode), callback, data, token);
        }
    }

    // HidlConfirmationResultCb overrides:
    android::hardware::Return<void> result(ResponseCode responseCode,
                                           const hidl_vec<uint8_t>& dataConfirmed,
                                           const hidl_vec<uint8_t>& confirmationToken) override {
        finalize(responseCode, dataConfirmed, confirmationToken);
        return Status::ok();
    };

    void serviceDied(uint64_t /* cookie */,
                     const ::android::wp<::android::hidl::base::V1_0::IBase>& /* who */) override {
        finalize(ResponseCode::SystemError, {}, {});
    }

    static uint32_t responseCode2Compat(ResponseCode rc) {
        switch (rc) {
        case ResponseCode::OK:
            return APC_COMPAT_ERROR_OK;
        case ResponseCode::Canceled:
            return APC_COMPAT_ERROR_CANCELLED;
        case ResponseCode::Aborted:
            return APC_COMPAT_ERROR_ABORTED;
        case ResponseCode::OperationPending:
            return APC_COMPAT_ERROR_OPERATION_PENDING;
        case ResponseCode::Ignored:
            return APC_COMPAT_ERROR_IGNORED;
        case ResponseCode::SystemError:
        case ResponseCode::Unimplemented:
        case ResponseCode::Unexpected:
        case ResponseCode::UIError:
        case ResponseCode::UIErrorMissingGlyph:
        case ResponseCode::UIErrorMessageTooLong:
        case ResponseCode::UIErrorMalformedUTF8Encoding:
        default:
            return APC_COMPAT_ERROR_SYSTEM_ERROR;
        }
    }

  private:
    ConfuiHidlCompatSession(sp<HidlConfirmationUI> service)
        : service_(service), callback_{nullptr, nullptr} {}
    sp<HidlConfirmationUI> service_;

    // The callback_lock_ protects the callback_ field against concurrent modification.
    // IMPORTANT: It must never be held while calling the call back.
    std::mutex callback_lock_;
    ApcCompatCallback callback_;
};

class ConfuiAidlCompatSession : public AidlBnConfirmationResultCb, public CompatSessionCB {
  public:
    static std::shared_ptr<ConfuiAidlCompatSession> tryGetService() {
        constexpr const char confirmationUIServiceName[] =
            "android.hardware.confirmationui.IConfirmationUI/default";
        if (!AServiceManager_isDeclared(confirmationUIServiceName)) {
            LOG(INFO) << confirmationUIServiceName << " is not declared in VINTF";
            return nullptr;
        }
        std::shared_ptr<AidlConfirmationUI> aidlService = AidlConfirmationUI::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(confirmationUIServiceName)));
        if (aidlService) {
            return ::ndk::SharedRefBase::make<ConfuiAidlCompatSession>(aidlService);
        }

        return nullptr;
    }

    class DeathRecipientCookie {
      public:
        DeathRecipientCookie(std::weak_ptr<ConfuiAidlCompatSession> session)
            : mAidlSession(session) {}
        DeathRecipientCookie() = delete;
        std::weak_ptr<ConfuiAidlCompatSession> mAidlSession;
    };

    uint32_t promptUserConfirmation(ApcCompatCallback callback, const char* prompt_text,
                                    const uint8_t* extra_data, size_t extra_data_size,
                                    const char* locale, ApcCompatUiOptions ui_options) {
        std::vector<uint8_t> aidl_prompt(prompt_text, prompt_text + strlen(prompt_text));
        std::vector<uint8_t> aidl_extra(extra_data, extra_data + extra_data_size);
        std::vector<AidlUIOptions> aidl_ui_options;
        if (ui_options.inverted) {
            aidl_ui_options.push_back(AidlUIOptions::ACCESSIBILITY_INVERTED);
        }
        if (ui_options.magnified) {
            aidl_ui_options.push_back(AidlUIOptions::ACCESSIBILITY_MAGNIFIED);
        }
        auto lock = std::lock_guard(callback_lock_);
        if (callback_.result != nullptr) {
            return APC_COMPAT_ERROR_OPERATION_PENDING;
        }

        if (!aidlService_) {
            return APC_COMPAT_ERROR_SYSTEM_ERROR;
        }

        {
            auto cookieLock = std::lock_guard(deathRecipientCookie_lock_);
            void* cookie = new DeathRecipientCookie(this->ref<ConfuiAidlCompatSession>());
            auto linkRet = AIBinder_linkToDeath(aidlService_->asBinder().get(),
                                                death_recipient_.get(), cookie);
            if (linkRet != STATUS_OK) {
                LOG(ERROR) << "Communication error: promptUserConfirmation: "
                              "Trying to register death recipient: ";
                delete static_cast<DeathRecipientCookie*>(cookie);
                return APC_COMPAT_ERROR_SYSTEM_ERROR;
            }
            deathRecipientCookie_.insert(cookie);
        }

        auto rc = aidlService_->promptUserConfirmation(ref<ConfuiAidlCompatSession>(), aidl_prompt,
                                                       aidl_extra, locale, aidl_ui_options);
        int ret = getReturnCode(rc);
        if (ret == AidlConfirmationUI::OK) {
            callback_ = callback;
        } else {
            LOG(ERROR) << "Communication error: promptUserConfirmation: " << rc.getDescription();
        }
        return responseCode2Compat(ret);
    }

    void abort() {
        if (aidlService_) {
            aidlService_->abort();
        }
    }

    void
    finalize(int32_t responseCode,
             std::optional<std::reference_wrapper<const std::vector<uint8_t>>> dataConfirmed,
             std::optional<std::reference_wrapper<const std::vector<uint8_t>>> confirmationToken) {
        ApcCompatCallback callback;
        {
            auto lock = std::lock_guard(callback_lock_);
            // Calling the callback consumes the callback data structure. We have to make
            // sure that it can only be called once.
            callback = callback_;
            callback_ = {nullptr, nullptr};
            // Unlock the callback_lock_ here. It must never be held while calling the callback.
        }

        if (callback.result != nullptr) {
            if (aidlService_) {
                // unlink all of the registered death recipients in case there
                // were multiple calls to promptUserConfirmation before a call
                // to finalize
                std::set<void*> cookiesToUnlink;
                {
                    auto cookieLock = std::lock_guard(deathRecipientCookie_lock_);
                    cookiesToUnlink = deathRecipientCookie_;
                    deathRecipientCookie_.clear();
                }

                // Unlink these outside of the lock
                for (void* cookie : cookiesToUnlink) {
                    AIBinder_unlinkToDeath(aidlService_->asBinder().get(), death_recipient_.get(),
                                           cookie);
                }
            }
            CompatSessionCB::finalize(responseCode2Compat(responseCode), callback, dataConfirmed,
                                      confirmationToken);
        }
    }

    // AidlBnConfirmationResultCb overrides:
    ::ndk::ScopedAStatus result(int32_t responseCode, const std::vector<uint8_t>& dataConfirmed,
                                const std::vector<uint8_t>& confirmationToken) override {
        finalize(responseCode, dataConfirmed, confirmationToken);
        return ::ndk::ScopedAStatus::ok();
    };

    void serviceDied() {
        aidlService_.reset();
        aidlService_ = nullptr;
        {
            std::lock_guard lock(deathRecipientCookie_lock_);
            deathRecipientCookie_.clear();
        }
        finalize(AidlConfirmationUI::SYSTEM_ERROR, {}, {});
    }

    void serviceUnlinked(void* cookie) {
        {
            std::lock_guard lock(deathRecipientCookie_lock_);
            deathRecipientCookie_.erase(cookie);
        }
    }

    static void binderDiedCallbackAidl(void* ptr) {
        auto aidlSessionCookie = static_cast<ConfuiAidlCompatSession::DeathRecipientCookie*>(ptr);
        if (aidlSessionCookie == nullptr) {
            LOG(ERROR) << __func__ << ": Null cookie for binderDiedCallbackAidl when HAL died!";
            return;
        } else if (auto aidlSession = aidlSessionCookie->mAidlSession.lock();
                   aidlSession != nullptr) {
            LOG(WARNING) << __func__ << " : Notififying ConfuiAidlCompatSession Service died.";
            aidlSession->serviceDied();
        } else {
            LOG(ERROR) << __func__
                       << " : ConfuiAidlCompatSession Service died but object is no longer around "
                          "to be able to notify.";
        }
    }

    static void binderUnlinkedCallbackAidl(void* ptr) {
        auto aidlSessionCookie = static_cast<ConfuiAidlCompatSession::DeathRecipientCookie*>(ptr);
        if (aidlSessionCookie == nullptr) {
            LOG(ERROR) << __func__ << ": Null cookie!";
            return;
        } else if (auto aidlSession = aidlSessionCookie->mAidlSession.lock();
                   aidlSession != nullptr) {
            aidlSession->serviceUnlinked(ptr);
        }
        delete aidlSessionCookie;
    }

    int getReturnCode(const ::ndk::ScopedAStatus& result) {
        if (result.isOk()) return AidlConfirmationUI::OK;

        if (result.getExceptionCode() == EX_SERVICE_SPECIFIC) {
            return static_cast<int>(result.getServiceSpecificError());
        }
        return result.getStatus();
    }

    uint32_t responseCode2Compat(int32_t rc) {
        switch (rc) {
        case AidlConfirmationUI::OK:
            return APC_COMPAT_ERROR_OK;
        case AidlConfirmationUI::CANCELED:
            return APC_COMPAT_ERROR_CANCELLED;
        case AidlConfirmationUI::ABORTED:
            return APC_COMPAT_ERROR_ABORTED;
        case AidlConfirmationUI::OPERATION_PENDING:
            return APC_COMPAT_ERROR_OPERATION_PENDING;
        case AidlConfirmationUI::IGNORED:
            return APC_COMPAT_ERROR_IGNORED;
        case AidlConfirmationUI::SYSTEM_ERROR:
        case AidlConfirmationUI::UNIMPLEMENTED:
        case AidlConfirmationUI::UNEXPECTED:
        case AidlConfirmationUI::UI_ERROR:
        case AidlConfirmationUI::UI_ERROR_MISSING_GLYPH:
        case AidlConfirmationUI::UI_ERROR_MESSAGE_TOO_LONG:
        case AidlConfirmationUI::UI_ERROR_MALFORMED_UTF8ENCODING:
        default:
            return APC_COMPAT_ERROR_SYSTEM_ERROR;
        }
    }

    ConfuiAidlCompatSession(std::shared_ptr<AidlConfirmationUI> service)
        : aidlService_(service), callback_{nullptr, nullptr} {
        death_recipient_ = ::ndk::ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(binderDiedCallbackAidl));
        AIBinder_DeathRecipient_setOnUnlinked(death_recipient_.get(), binderUnlinkedCallbackAidl);
    }

    virtual ~ConfuiAidlCompatSession() = default;
    ConfuiAidlCompatSession(const ConfuiAidlCompatSession&) = delete;
    ConfuiAidlCompatSession& operator=(const ConfuiAidlCompatSession&) = delete;

  private:
    std::shared_ptr<AidlConfirmationUI> aidlService_;
    std::mutex deathRecipientCookie_lock_;
    std::set<void*> deathRecipientCookie_;

    // The callback_lock_ protects the callback_ field against concurrent modification.
    // IMPORTANT: It must never be held while calling the call back.
    std::mutex callback_lock_;
    ApcCompatCallback callback_;

    ::ndk::ScopedAIBinder_DeathRecipient death_recipient_;
};

class ApcCompatSession {
  public:
    static ApcCompatServiceHandle getApcCompatSession() {
        auto aidlCompatSession = ConfuiAidlCompatSession::tryGetService();
        if (aidlCompatSession) {
            return new ApcCompatSession(std::move(aidlCompatSession), nullptr);
        }

        sp<ConfuiHidlCompatSession> hidlCompatSession = ConfuiHidlCompatSession::tryGetService();
        if (hidlCompatSession) {
            return new ApcCompatSession(nullptr, std::move(hidlCompatSession));
        }

        LOG(ERROR) << "ConfirmationUI: Not found Service";
        return nullptr;
    }

    uint32_t promptUserConfirmation(ApcCompatCallback callback, const char* prompt_text,
                                    const uint8_t* extra_data, size_t extra_data_size,
                                    char const* locale, ApcCompatUiOptions ui_options) {
        if (aidlCompatSession_) {
            return aidlCompatSession_->promptUserConfirmation(callback, prompt_text, extra_data,
                                                              extra_data_size, locale, ui_options);
        } else {
            return hidlCompatSession_->promptUserConfirmation(callback, prompt_text, extra_data,
                                                              extra_data_size, locale, ui_options);
        }
    }

    void abortUserConfirmation() {
        if (aidlCompatSession_) {
            return aidlCompatSession_->abort();
        } else {
            return hidlCompatSession_->abort();
        }
    }

    void closeUserConfirmationService() {
        // Closing the handle implicitly aborts an ongoing sessions.
        // Note that a resulting callback is still safely conducted, because we only delete a
        // StrongPointer below. libhwbinder still owns another StrongPointer to this session.
        abortUserConfirmation();
    }

    ApcCompatSession(std::shared_ptr<ConfuiAidlCompatSession> aidlCompatSession,
                     sp<ConfuiHidlCompatSession> hidlCompatSession)
        : aidlCompatSession_(aidlCompatSession), hidlCompatSession_(hidlCompatSession) {}

  private:
    std::shared_ptr<ConfuiAidlCompatSession> aidlCompatSession_;
    sp<ConfuiHidlCompatSession> hidlCompatSession_;
};
}  // namespace keystore2

using namespace keystore2;

ApcCompatServiceHandle tryGetUserConfirmationService() {
    return reinterpret_cast<ApcCompatServiceHandle>(ApcCompatSession::getApcCompatSession());
}

uint32_t promptUserConfirmation(ApcCompatServiceHandle handle, ApcCompatCallback callback,
                                const char* prompt_text, const uint8_t* extra_data,
                                size_t extra_data_size, char const* locale,
                                ApcCompatUiOptions ui_options) {
    auto session = reinterpret_cast<ApcCompatSession*>(handle);
    return session->promptUserConfirmation(callback, prompt_text, extra_data, extra_data_size,
                                           locale, ui_options);
}

void abortUserConfirmation(ApcCompatServiceHandle handle) {
    auto session = reinterpret_cast<ApcCompatSession*>(handle);
    session->abortUserConfirmation();
}

void closeUserConfirmationService(ApcCompatServiceHandle handle) {
    auto session = reinterpret_cast<ApcCompatSession*>(handle);
    session->closeUserConfirmationService();
    delete reinterpret_cast<ApcCompatSession*>(handle);
}

const ApcCompatServiceHandle INVALID_SERVICE_HANDLE = nullptr;
