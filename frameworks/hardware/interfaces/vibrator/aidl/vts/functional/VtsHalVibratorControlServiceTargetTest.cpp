/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/frameworks/vibrator/BnVibratorControlService.h>
#include <aidl/android/frameworks/vibrator/BnVibratorController.h>
#include <aidl/android/frameworks/vibrator/IVibratorControlService.h>
#include <aidl/android/frameworks/vibrator/IVibratorController.h>
#include <aidl/android/frameworks/vibrator/ScaleParam.h>
#include <android-base/logging.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>

namespace android {

using ::aidl::android::frameworks::vibrator::BnVibratorController;
using ::aidl::android::frameworks::vibrator::BnVibratorControlService;
using ::aidl::android::frameworks::vibrator::IVibratorController;
using ::aidl::android::frameworks::vibrator::IVibratorControlService;
using ::aidl::android::frameworks::vibrator::ScaleParam;
using ::aidl::android::frameworks::vibrator::VibrationParam;
using ::android::getAidlHalInstanceNames;
using ::android::PrintInstanceNameToString;
using ndk::SpAIBinder;
using ::testing::Eq;
using ::testing::InitGoogleTest;
using ::testing::TestWithParam;
using ::testing::ValuesIn;

VibrationParam generateVibrationParam(int in_typesMask, float scale) {
    ScaleParam scaleParam = ScaleParam();
    scaleParam.typesMask = in_typesMask;
    scaleParam.scale = scale;
    return VibrationParam(scaleParam);
}

std::vector<VibrationParam> generateVibrationParams(int in_typesMask, float scale) {
    return {generateVibrationParam(in_typesMask, scale)};
}

class VibratorController : public BnVibratorController {
   public:
    ~VibratorController() override = default;

    ndk::ScopedAStatus requestVibrationParams(int in_typesMask,
                                              int64_t in_deadlineElapsedRealtimeMillis,
                                              const ::ndk::SpAIBinder& in_requestToken) override {
        if (in_requestToken == nullptr) {
            LOG(INFO) << "Vibrator controller failed to process a request for vibration params";
            return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_ILLEGAL_ARGUMENT));
        }

        LOG(INFO) << "Vibrator controller received a request for vibration params for type: "
                  << in_typesMask << ", with a timeout of: " << in_deadlineElapsedRealtimeMillis;

        std::shared_ptr<IVibratorControlService> service =
            IVibratorControlService::fromBinder(in_requestToken);

        EXPECT_TRUE(service
                        ->onRequestVibrationParamsComplete(
                            in_requestToken, generateVibrationParams(in_typesMask, /* scale= */ 1))
                        .isOk());

        return ndk::ScopedAStatus::ok();
    }
};

class VibratorControlServiceTest : public ::testing::TestWithParam<std::string> {
   public:
    void SetUp() override {
        SpAIBinder binder(AServiceManager_waitForService(GetParam().c_str()));
        service = IVibratorControlService::fromBinder(binder);
        ASSERT_NE(service, nullptr);
    }

    std::shared_ptr<IVibratorControlService> service;
};

TEST_P(VibratorControlServiceTest, RegisterAndUnregisterVibratorControllerTest) {
    std::shared_ptr<IVibratorController> vibratorController =
        ::ndk::SharedRefBase::make<VibratorController>();

    EXPECT_TRUE(service->registerVibratorController(vibratorController).isOk());

    EXPECT_TRUE(service->unregisterVibratorController(vibratorController).isOk());
}

TEST_P(VibratorControlServiceTest, RegisterAndUnregisterMultipleVibratorControllersTest) {
    std::shared_ptr<IVibratorController> firstController =
        ::ndk::SharedRefBase::make<VibratorController>();
    std::shared_ptr<IVibratorController> secondController =
        ::ndk::SharedRefBase::make<VibratorController>();

    EXPECT_TRUE(service->registerVibratorController(firstController).isOk());
    EXPECT_TRUE(service->registerVibratorController(secondController).isOk());

    EXPECT_TRUE(service->unregisterVibratorController(firstController).isOk());
    EXPECT_TRUE(service->unregisterVibratorController(secondController).isOk());
}

TEST_P(VibratorControlServiceTest, SetAndClearVibrationParamsTest) {
    std::shared_ptr<IVibratorController> vibratorController =
        ::ndk::SharedRefBase::make<VibratorController>();

    EXPECT_TRUE(service->registerVibratorController(vibratorController).isOk());

    // Set empty params
    EXPECT_TRUE(service->setVibrationParams({}, vibratorController->getDefaultImpl()).isOk());

    // Set single param
    EXPECT_TRUE(
        service
            ->setVibrationParams({generateVibrationParam(ScaleParam::TYPE_ALARM, /* scale= */ 1)},
                                 vibratorController->getDefaultImpl())
            .isOk());

    // Set multiple params
    EXPECT_TRUE(service
                    ->setVibrationParams(
                        {generateVibrationParam(ScaleParam::TYPE_ALARM, /* scale= */ 1),
                         generateVibrationParam(ScaleParam::TYPE_RINGTONE, /* scale= */ 1)},
                        vibratorController->getDefaultImpl())
                    .isOk());

    // Clear params
    EXPECT_TRUE(
        service->clearVibrationParams(ScaleParam::TYPE_ALARM, vibratorController->getDefaultImpl())
            .isOk());

    EXPECT_TRUE(service->unregisterVibratorController(vibratorController).isOk());
}

TEST_P(VibratorControlServiceTest, UnregisteredControllerSetAndClearVibrationParamsTest) {
    std::shared_ptr<IVibratorController> vibratorController =
        ::ndk::SharedRefBase::make<VibratorController>();

    EXPECT_TRUE(
        service
            ->setVibrationParams(generateVibrationParams(ScaleParam::TYPE_MEDIA, /* scale= */ 1),
                                 vibratorController->getDefaultImpl())
            .isOk());

    EXPECT_TRUE(
        service->clearVibrationParams(ScaleParam::TYPE_MEDIA, vibratorController->getDefaultImpl())
            .isOk());
}

TEST_P(VibratorControlServiceTest, UnexpectedRequestTokenOnRequestVibrationParamsCompleteTest) {
    std::shared_ptr<IVibratorController> vibratorController =
        ::ndk::SharedRefBase::make<VibratorController>();

    auto unexpectedRequestToken = service->asBinder();

    // Empty params
    EXPECT_TRUE(service->onRequestVibrationParamsComplete(unexpectedRequestToken, {}).isOk());

    // Single param
    EXPECT_TRUE(service
                    ->onRequestVibrationParamsComplete(
                        unexpectedRequestToken,
                        generateVibrationParams(ScaleParam::TYPE_ALARM, /* scale= */ 1))
                    .isOk());

    // Multiple params
    EXPECT_TRUE(service
                    ->onRequestVibrationParamsComplete(
                        unexpectedRequestToken,
                        {generateVibrationParam(ScaleParam::TYPE_ALARM, /* scale= */ 1),
                         generateVibrationParam(ScaleParam::TYPE_RINGTONE, /* scale= */ 1)})
                    .isOk());

    EXPECT_TRUE(service->unregisterVibratorController(vibratorController).isOk());
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(VibratorControlServiceTest);

INSTANTIATE_TEST_SUITE_P(
    PerInstance, VibratorControlServiceTest,
    testing::ValuesIn(getAidlHalInstanceNames(IVibratorControlService::descriptor)),
    PrintInstanceNameToString);

int main(int argc, char** argv) {
    InitGoogleTest(&argc, argv);
    ABinderProcess_setThreadPoolMaxThreadCount(/* numThreads= */ 1);
    ABinderProcess_startThreadPool();
    return RUN_ALL_TESTS();
}
}  // namespace android
