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

#include <aidl/android/hardware/vibrator/BnVibratorCallback.h>
#include <android-base/logging.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <linux/input.h>
#include <linux/uinput.h>

#include <future>

#include "Stats.h"
#include "Vibrator.h"
#include "mocks.h"
#include "types.h"
#include "utils.h"

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

using ::testing::_;
using ::testing::AnyNumber;
using ::testing::Assign;
using ::testing::AtLeast;
using ::testing::AtMost;
using ::testing::Combine;
using ::testing::DoAll;
using ::testing::DoDefault;
using ::testing::Exactly;
using ::testing::Expectation;
using ::testing::ExpectationSet;
using ::testing::Ge;
using ::testing::Mock;
using ::testing::MockFunction;
using ::testing::Range;
using ::testing::Return;
using ::testing::Sequence;
using ::testing::SetArgPointee;
using ::testing::SetArgReferee;
using ::testing::Test;
using ::testing::TestParamInfo;
using ::testing::ValuesIn;
using ::testing::WithParamInterface;

// Forward Declarations

static EffectQueue Queue(const QueueEffect &effect);
static EffectQueue Queue(const QueueDelay &delay);
template <typename T, typename U, typename... Args>
static EffectQueue Queue(const T &first, const U &second, Args... rest);

static EffectLevel Level(float intensity, float levelLow, float levelHigh);
static EffectScale Scale(float intensity, float levelLow, float levelHigh);

// Constants With Arbitrary Values

static constexpr uint32_t CAL_VERSION = 2;
static constexpr std::array<EffectLevel, 2> V_TICK_DEFAULT = {1, 100};
static constexpr std::array<EffectLevel, 2> V_CLICK_DEFAULT{1, 100};
static constexpr std::array<EffectLevel, 2> V_LONG_DEFAULT{1, 100};
static constexpr std::array<EffectDuration, 14> EFFECT_DURATIONS{
        0, 100, 12, 1000, 300, 133, 150, 500, 100, 5, 12, 1000, 1000, 1000};

// Constants With Prescribed Values

static const std::map<Effect, EffectIndex> EFFECT_INDEX{
        {Effect::CLICK, 2},
        {Effect::TICK, 2},
        {Effect::HEAVY_CLICK, 2},
        {Effect::TEXTURE_TICK, 9},
};
static constexpr uint32_t MIN_ON_OFF_INTERVAL_US = 8500;
static constexpr uint8_t VOLTAGE_SCALE_MAX = 100;
static constexpr int8_t MAX_COLD_START_LATENCY_MS = 6;  // I2C Transaction + DSP Return-From-Standby
static constexpr auto POLLING_TIMEOUT = 50;  // POLLING_TIMEOUT < ASYNC_COMPLETION_TIMEOUT
enum WaveformIndex : uint16_t {
    /* Physical waveform */
    WAVEFORM_LONG_VIBRATION_EFFECT_INDEX = 0,
    WAVEFORM_RESERVED_INDEX_1 = 1,
    WAVEFORM_CLICK_INDEX = 2,
    WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX = 3,
    WAVEFORM_THUD_INDEX = 4,
    WAVEFORM_SPIN_INDEX = 5,
    WAVEFORM_QUICK_RISE_INDEX = 6,
    WAVEFORM_SLOW_RISE_INDEX = 7,
    WAVEFORM_QUICK_FALL_INDEX = 8,
    WAVEFORM_LIGHT_TICK_INDEX = 9,
    WAVEFORM_LOW_TICK_INDEX = 10,
    WAVEFORM_RESERVED_MFG_1,
    WAVEFORM_RESERVED_MFG_2,
    WAVEFORM_RESERVED_MFG_3,
    WAVEFORM_MAX_PHYSICAL_INDEX,
    /* OWT waveform */
    WAVEFORM_COMPOSE = WAVEFORM_MAX_PHYSICAL_INDEX,
    WAVEFORM_PWLE,
    /*
     * Refer to <linux/input.h>, the WAVEFORM_MAX_INDEX must not exceed 96.
     * #define FF_GAIN          0x60  // 96 in decimal
     * #define FF_MAX_EFFECTS   FF_GAIN
     */
    WAVEFORM_MAX_INDEX,
};

static const EffectScale ON_GLOBAL_SCALE{levelToScale(V_LONG_DEFAULT[1])};
static const EffectIndex ON_EFFECT_INDEX{0};

static const std::map<EffectTuple, EffectScale> EFFECT_SCALE{
        {{Effect::TICK, EffectStrength::LIGHT},
         Scale(0.5f * 0.5f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::TICK, EffectStrength::MEDIUM},
         Scale(0.5f * 0.7f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::TICK, EffectStrength::STRONG},
         Scale(0.5f * 1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::CLICK, EffectStrength::LIGHT},
         Scale(0.7f * 0.5f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::CLICK, EffectStrength::MEDIUM},
         Scale(0.7f * 0.7f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::CLICK, EffectStrength::STRONG},
         Scale(0.7f * 1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::HEAVY_CLICK, EffectStrength::LIGHT},
         Scale(1.0f * 0.5f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::HEAVY_CLICK, EffectStrength::MEDIUM},
         Scale(1.0f * 0.7f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::HEAVY_CLICK, EffectStrength::STRONG},
         Scale(1.0f * 1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
        {{Effect::TEXTURE_TICK, EffectStrength::LIGHT},
         Scale(0.5f * 0.5f, V_TICK_DEFAULT[0], V_TICK_DEFAULT[1])},
        {{Effect::TEXTURE_TICK, EffectStrength::MEDIUM},
         Scale(0.5f * 0.7f, V_TICK_DEFAULT[0], V_TICK_DEFAULT[1])},
        {{Effect::TEXTURE_TICK, EffectStrength::STRONG},
         Scale(0.5f * 1.0f, V_TICK_DEFAULT[0], V_TICK_DEFAULT[1])},
};

static const std::map<EffectTuple, EffectQueue> EFFECT_QUEUE{
        {{Effect::DOUBLE_CLICK, EffectStrength::LIGHT},
         Queue(QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(0.7f * 0.5f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
               100,
               QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(1.0f * 0.5f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])})},
        {{Effect::DOUBLE_CLICK, EffectStrength::MEDIUM},
         Queue(QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(0.7f * 0.7f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
               100,
               QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(1.0f * 0.7f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])})},
        {{Effect::DOUBLE_CLICK, EffectStrength::STRONG},
         Queue(QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(0.7f * 1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])},
               100,
               QueueEffect{EFFECT_INDEX.at(Effect::CLICK),
                           Level(1.0f * 1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])})},
};

EffectQueue Queue(const QueueEffect &effect) {
    auto index = std::get<0>(effect);
    auto level = std::get<1>(effect);
    auto string = std::to_string(index) + "." + std::to_string(level);
    auto duration = EFFECT_DURATIONS[index];
    return {string, duration};
}

EffectQueue Queue(const QueueDelay &delay) {
    auto string = std::to_string(delay);
    return {string, delay};
}

template <typename T, typename U, typename... Args>
EffectQueue Queue(const T &first, const U &second, Args... rest) {
    auto head = Queue(first);
    auto tail = Queue(second, rest...);
    auto string = std::get<0>(head) + "," + std::get<0>(tail);
    auto duration = std::get<1>(head) + std::get<1>(tail);
    return {string, duration};
}

static EffectLevel Level(float intensity, float levelLow, float levelHigh) {
    return std::lround(intensity * (levelHigh - levelLow)) + levelLow;
}

static EffectScale Scale(float intensity, float levelLow, float levelHigh) {
    return levelToScale(Level(intensity, levelLow, levelHigh));
}

class VibratorTest : public Test {
  public:
    void SetUp() override {
        setenv("INPUT_EVENT_NAME", "CS40L26TestSuite", true);
        std::unique_ptr<MockApi> mockapi;
        std::unique_ptr<MockCal> mockcal;
        std::unique_ptr<MockStats> mockstats;

        createMock(&mockapi, &mockcal, &mockstats);
        createVibrator(std::move(mockapi), std::move(mockcal), std::move(mockstats));
    }

    void TearDown() override { deleteVibrator(); }

  protected:
    void createMock(std::unique_ptr<MockApi> *mockapi, std::unique_ptr<MockCal> *mockcal,
                    std::unique_ptr<MockStats> *mockstats) {
        *mockapi = std::make_unique<MockApi>();
        *mockcal = std::make_unique<MockCal>();
        *mockstats = std::make_unique<MockStats>();

        mMockApi = mockapi->get();
        mMockCal = mockcal->get();
        mMockStats = mockstats->get();

        ON_CALL(*mMockApi, destructor()).WillByDefault(Assign(&mMockApi, nullptr));

        ON_CALL(*mMockApi, initFF()).WillByDefault(Return(false));
        ON_CALL(*mMockApi, setFFGain(_)).WillByDefault(Return(true));
        ON_CALL(*mMockApi, setFFEffect(_, _)).WillByDefault(Return(true));
        ON_CALL(*mMockApi, setFFPlay(_, _)).WillByDefault(Return(true));
        ON_CALL(*mMockApi, pollVibeState(_, _)).WillByDefault(Return(true));
        ON_CALL(*mMockApi, uploadOwtEffect(_, _, _, _, _)).WillByDefault(Return(true));
        ON_CALL(*mMockApi, eraseOwtEffect(_, _)).WillByDefault(Return(true));

        ON_CALL(*mMockApi, getOwtFreeSpace(_))
                .WillByDefault(DoAll(SetArgPointee<0>(11504), Return(true)));

        ON_CALL(*mMockCal, destructor()).WillByDefault(Assign(&mMockCal, nullptr));

        ON_CALL(*mMockCal, getVersion(_))
                .WillByDefault(DoAll(SetArgPointee<0>(CAL_VERSION), Return(true)));

        ON_CALL(*mMockCal, getTickVolLevels(_))
                .WillByDefault(DoAll(SetArgPointee<0>(V_TICK_DEFAULT), Return(true)));
        ON_CALL(*mMockCal, getClickVolLevels(_))
                .WillByDefault(DoAll(SetArgPointee<0>(V_CLICK_DEFAULT), Return(true)));
        ON_CALL(*mMockCal, getLongVolLevels(_))
                .WillByDefault(DoAll(SetArgPointee<0>(V_LONG_DEFAULT), Return(true)));

        ON_CALL(*mMockStats, destructor()).WillByDefault(Assign(&mMockStats, nullptr));
        ON_CALL(*mMockStats, logPrimitive(_)).WillByDefault(Return(true));
        ON_CALL(*mMockStats, logWaveform(_, _)).WillByDefault(Return(true));
        ON_CALL(*mMockStats, logLatencyStart(_)).WillByDefault(Return(true));
        ON_CALL(*mMockStats, logLatencyEnd()).WillByDefault(Return(true));

        relaxMock(false);
    }

    void createVibrator(std::unique_ptr<MockApi> mockapi, std::unique_ptr<MockCal> mockcal,
                        std::unique_ptr<MockStats> mockstats, bool relaxed = true) {
        if (relaxed) {
            relaxMock(true);
        }
        mVibrator = ndk::SharedRefBase::make<Vibrator>(std::move(mockapi), std::move(mockcal),
                                                       std::move(mockstats));
        if (relaxed) {
            relaxMock(false);
        }
    }

    void deleteVibrator(bool relaxed = true) {
        if (relaxed) {
            relaxMock(true);
        }
        mVibrator.reset();
    }

  private:
    void relaxMock(bool relax) {
        auto times = relax ? AnyNumber() : Exactly(0);

        Mock::VerifyAndClearExpectations(mMockApi);
        Mock::VerifyAndClearExpectations(mMockCal);
        Mock::VerifyAndClearExpectations(mMockStats);

        EXPECT_CALL(*mMockApi, destructor()).Times(times);
        EXPECT_CALL(*mMockApi, setF0(_)).Times(times);
        EXPECT_CALL(*mMockApi, setF0Offset(_)).Times(times);
        EXPECT_CALL(*mMockApi, setRedc(_)).Times(times);
        EXPECT_CALL(*mMockApi, setQ(_)).Times(times);
        EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).Times(times);
        EXPECT_CALL(*mMockApi, getOwtFreeSpace(_)).Times(times);
        EXPECT_CALL(*mMockApi, setF0CompEnable(_)).Times(times);
        EXPECT_CALL(*mMockApi, setRedcCompEnable(_)).Times(times);
        EXPECT_CALL(*mMockApi, pollVibeState(_, _)).Times(times);
        EXPECT_CALL(*mMockApi, initFF()).Times(times);
        EXPECT_CALL(*mMockApi, setFFGain(_)).Times(times);
        EXPECT_CALL(*mMockApi, setFFEffect(_, _)).Times(times);
        EXPECT_CALL(*mMockApi, setFFPlay(_, _)).Times(times);
        EXPECT_CALL(*mMockApi, uploadOwtEffect(_, _, _, _, _)).Times(times);
        EXPECT_CALL(*mMockApi, eraseOwtEffect(_, _)).Times(times);
        EXPECT_CALL(*mMockApi, setMinOnOffInterval(_)).Times(times);
        EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).Times(times);
        EXPECT_CALL(*mMockApi, setHapticPcmAmp(_, _, _, _)).Times(times);
        EXPECT_CALL(*mMockApi, isPassthroughI2sHapticSupported()).Times(times);
        EXPECT_CALL(*mMockApi, enableDbc()).Times(times);

        EXPECT_CALL(*mMockApi, debug(_)).Times(times);

        EXPECT_CALL(*mMockCal, destructor()).Times(times);
        EXPECT_CALL(*mMockCal, getF0(_)).Times(times);
        EXPECT_CALL(*mMockCal, getRedc(_)).Times(times);
        EXPECT_CALL(*mMockCal, getQ(_)).Times(times);
        EXPECT_CALL(*mMockCal, getTickVolLevels(_)).Times(times);
        EXPECT_CALL(*mMockCal, getClickVolLevels(_)).Times(times);
        EXPECT_CALL(*mMockCal, getLongVolLevels(_)).Times(times);
        EXPECT_CALL(*mMockCal, isChirpEnabled()).Times(times);
        EXPECT_CALL(*mMockCal, getLongFrequencyShift(_)).Times(times);
        EXPECT_CALL(*mMockCal, isF0CompEnabled()).Times(times);
        EXPECT_CALL(*mMockCal, isRedcCompEnabled()).Times(times);
        EXPECT_CALL(*mMockCal, debug(_)).Times(times);

        EXPECT_CALL(*mMockStats, destructor()).Times(times);
        EXPECT_CALL(*mMockStats, logPrimitive(_)).Times(times);
        EXPECT_CALL(*mMockStats, logWaveform(_, _)).Times(times);
        EXPECT_CALL(*mMockStats, logLatencyStart(_)).Times(times);
        EXPECT_CALL(*mMockStats, logLatencyEnd()).Times(times);
    }

  protected:
    MockApi *mMockApi;
    MockCal *mMockCal;
    MockStats *mMockStats;
    std::shared_ptr<IVibrator> mVibrator;
    uint32_t mEffectIndex;
};

TEST_F(VibratorTest, Constructor) {
    std::unique_ptr<MockApi> mockapi;
    std::unique_ptr<MockCal> mockcal;
    std::unique_ptr<MockStats> mockstats;
    int min_val = 0xC8000;
    int max_val = 0x7FC000;
    std::string f0Val = std::to_string(std::rand() % (max_val - min_val + 1) + min_val);
    std::string redcVal = std::to_string(std::rand() % (max_val - min_val + 1) + min_val);
    std::string qVal = std::to_string(std::rand() % (max_val - min_val + 1) + min_val);
    uint32_t calVer;
    uint32_t supportedPrimitivesBits = 0x0;
    Expectation volGet;
    Sequence f0Seq, redcSeq, qSeq, supportedPrimitivesSeq;

    EXPECT_CALL(*mMockApi, destructor()).WillOnce(DoDefault());
    EXPECT_CALL(*mMockCal, destructor()).WillOnce(DoDefault());
    EXPECT_CALL(*mMockStats, destructor()).WillOnce(DoDefault());

    deleteVibrator(false);

    createMock(&mockapi, &mockcal, &mockstats);

    EXPECT_CALL(*mMockCal, getF0(_))
            .InSequence(f0Seq)
            .WillOnce(DoAll(SetArgReferee<0>(f0Val), Return(true)));
    EXPECT_CALL(*mMockApi, setF0(f0Val)).InSequence(f0Seq).WillOnce(Return(true));

    EXPECT_CALL(*mMockCal, getRedc(_))
            .InSequence(redcSeq)
            .WillOnce(DoAll(SetArgReferee<0>(redcVal), Return(true)));
    EXPECT_CALL(*mMockApi, setRedc(redcVal)).InSequence(redcSeq).WillOnce(Return(true));

    EXPECT_CALL(*mMockCal, getQ(_))
            .InSequence(qSeq)
            .WillOnce(DoAll(SetArgReferee<0>(qVal), Return(true)));
    EXPECT_CALL(*mMockApi, setQ(qVal)).InSequence(qSeq).WillOnce(Return(true));

    EXPECT_CALL(*mMockCal, getLongFrequencyShift(_)).WillOnce(Return(true));

    mMockCal->getVersion(&calVer);
    if (calVer == 2) {
        volGet = EXPECT_CALL(*mMockCal, getTickVolLevels(_)).WillOnce(DoDefault());
        volGet = EXPECT_CALL(*mMockCal, getClickVolLevels(_)).WillOnce(DoDefault());
        volGet = EXPECT_CALL(*mMockCal, getLongVolLevels(_)).WillOnce(DoDefault());
    }

    EXPECT_CALL(*mMockCal, isF0CompEnabled()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, setF0CompEnable(true)).WillOnce(Return(true));
    EXPECT_CALL(*mMockCal, isRedcCompEnabled()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, setRedcCompEnable(true)).WillOnce(Return(true));

    EXPECT_CALL(*mMockApi, isPassthroughI2sHapticSupported()).WillOnce(Return(false));
    EXPECT_CALL(*mMockCal, isChirpEnabled()).WillOnce(Return(true));
    EXPECT_CALL(*mMockCal, getSupportedPrimitives(_))
            .InSequence(supportedPrimitivesSeq)
            .WillOnce(DoAll(SetArgPointee<0>(supportedPrimitivesBits), Return(true)));

    EXPECT_CALL(*mMockApi, setMinOnOffInterval(MIN_ON_OFF_INTERVAL_US)).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getContextScale()).WillRepeatedly(Return(0));
    EXPECT_CALL(*mMockApi, getContextEnable()).WillRepeatedly(Return(false));
    EXPECT_CALL(*mMockApi, getContextSettlingTime()).WillRepeatedly(Return(0));
    EXPECT_CALL(*mMockApi, getContextCooldownTime()).WillRepeatedly(Return(0));
    EXPECT_CALL(*mMockApi, getContextFadeEnable()).WillRepeatedly(Return(false));
    EXPECT_CALL(*mMockApi, enableDbc()).WillOnce(Return(true));
    createVibrator(std::move(mockapi), std::move(mockcal), std::move(mockstats), false);
}

TEST_F(VibratorTest, on) {
    Sequence s1, s2;
    uint16_t duration = std::rand() + 1;

    EXPECT_CALL(*mMockStats, logLatencyStart(kWaveformEffectLatency))
            .InSequence(s1, s2)
            .WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE)).InSequence(s1).WillOnce(DoDefault());
    EXPECT_CALL(*mMockStats, logWaveform(_, _)).InSequence(s1).WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, setFFEffect(_, duration + MAX_COLD_START_LATENCY_MS))
            .InSequence(s2)
            .WillOnce(DoDefault());
    EXPECT_CALL(*mMockStats, logLatencyEnd()).InSequence(s1, s2).WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, setFFPlay(ON_EFFECT_INDEX, true))
            .InSequence(s1, s2)
            .WillOnce(DoDefault());
    EXPECT_TRUE(mVibrator->on(duration, nullptr).isOk());
}

TEST_F(VibratorTest, off) {
    Sequence s1;
    EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE)).InSequence(s1).WillOnce(DoDefault());
    EXPECT_TRUE(mVibrator->off().isOk());
}

TEST_F(VibratorTest, supportsAmplitudeControl_supported) {
    int32_t capabilities;
    EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->getCapabilities(&capabilities).isOk());
    EXPECT_GT(capabilities & IVibrator::CAP_AMPLITUDE_CONTROL, 0);
}

TEST_F(VibratorTest, supportsExternalAmplitudeControl_unsupported) {
    int32_t capabilities;
    EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->getCapabilities(&capabilities).isOk());
    EXPECT_EQ(capabilities & IVibrator::CAP_EXTERNAL_AMPLITUDE_CONTROL, 0);
}

TEST_F(VibratorTest, setAmplitude_supported) {
    EffectAmplitude amplitude = static_cast<float>(std::rand()) / RAND_MAX ?: 1.0f;

    EXPECT_CALL(*mMockApi, setFFGain(amplitudeToScale(amplitude))).WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->setAmplitude(amplitude).isOk());
}

TEST_F(VibratorTest, supportsExternalControl_supported) {
    int32_t capabilities;
    EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->getCapabilities(&capabilities).isOk());
    EXPECT_GT(capabilities & IVibrator::CAP_EXTERNAL_CONTROL, 0);
}

TEST_F(VibratorTest, supportsExternalControl_unsupported) {
    int32_t capabilities;
    EXPECT_CALL(*mMockApi, hasOwtFreeSpace()).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).WillOnce(Return(false));

    EXPECT_TRUE(mVibrator->getCapabilities(&capabilities).isOk());
    EXPECT_EQ(capabilities & IVibrator::CAP_EXTERNAL_CONTROL, 0);
}

TEST_F(VibratorTest, setExternalControl_enable) {
    Sequence s1, s2;
    EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE)).InSequence(s1).WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).InSequence(s2).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, setHapticPcmAmp(_, true, _, _))
            .InSequence(s1, s2)
            .WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->setExternalControl(true).isOk());
}

TEST_F(VibratorTest, setExternalControl_disable) {
    Sequence s1, s2, s3, s4;

    // The default mIsUnderExternalControl is false, so it needs to turn on the External Control
    // to make mIsUnderExternalControl become true.
    EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE))
            .InSequence(s1)
            .InSequence(s1)
            .WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, getHapticAlsaDevice(_, _)).InSequence(s2).WillOnce(Return(true));
    EXPECT_CALL(*mMockApi, setHapticPcmAmp(_, true, _, _)).InSequence(s3).WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->setExternalControl(true).isOk());

    EXPECT_CALL(*mMockApi, setFFGain(levelToScale(VOLTAGE_SCALE_MAX)))
            .InSequence(s4)
            .WillOnce(DoDefault());
    EXPECT_CALL(*mMockApi, setHapticPcmAmp(_, false, _, _))
            .InSequence(s1, s2, s3, s4)
            .WillOnce(Return(true));

    EXPECT_TRUE(mVibrator->setExternalControl(false).isOk());
}

class EffectsTest : public VibratorTest, public WithParamInterface<EffectTuple> {
  public:
    static auto PrintParam(const TestParamInfo<ParamType> &info) {
        auto param = info.param;
        auto effect = std::get<0>(param);
        auto strength = std::get<1>(param);
        return toString(effect) + "_" + toString(strength);
    }
};

TEST_P(EffectsTest, perform) {
    auto param = GetParam();
    auto effect = std::get<0>(param);
    auto strength = std::get<1>(param);
    auto scale = EFFECT_SCALE.find(param);
    auto queue = EFFECT_QUEUE.find(param);
    EffectDuration duration;
    auto callback = ndk::SharedRefBase::make<MockVibratorCallback>();
    std::promise<void> promise;
    std::future<void> future{promise.get_future()};
    auto complete = [&promise] {
        promise.set_value();
        return ndk::ScopedAStatus::ok();
    };
    bool composeEffect;

    ExpectationSet eSetup;
    Expectation eActivate, ePollHaptics, ePollStop, eEraseDone;

    eSetup +=
            EXPECT_CALL(*mMockStats, logLatencyStart(kPrebakedEffectLatency)).WillOnce(DoDefault());

    if (scale != EFFECT_SCALE.end()) {
        EffectIndex index = EFFECT_INDEX.at(effect);
        duration = EFFECT_DURATIONS[index];

        eSetup += EXPECT_CALL(*mMockApi, setFFGain(levelToScale(scale->second)))
                          .WillOnce(DoDefault());
        eSetup += EXPECT_CALL(*mMockStats, logLatencyEnd()).WillOnce(DoDefault());
        eActivate =
                EXPECT_CALL(*mMockApi, setFFPlay(index, true)).After(eSetup).WillOnce(DoDefault());
    } else if (queue != EFFECT_QUEUE.end()) {
        duration = std::get<1>(queue->second);
        eSetup += EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE))
                          .After(eSetup)
                          .WillOnce(DoDefault());
        eSetup += EXPECT_CALL(*mMockApi, getOwtFreeSpace(_)).WillOnce(DoDefault());
        eSetup += EXPECT_CALL(*mMockApi, uploadOwtEffect(_, _, _, _, _))
                          .After(eSetup)
                          .WillOnce(DoDefault());
        eSetup += EXPECT_CALL(*mMockStats, logLatencyEnd()).After(eSetup).WillOnce(DoDefault());
        eActivate = EXPECT_CALL(*mMockApi, setFFPlay(WAVEFORM_COMPOSE, true))
                            .After(eSetup)
                            .WillOnce(DoDefault());
        composeEffect = true;
    } else {
        duration = 0;
    }

    if (duration) {
        ePollHaptics = EXPECT_CALL(*mMockApi, pollVibeState(1, POLLING_TIMEOUT))
                               .After(eActivate)
                               .WillOnce(DoDefault());
        ePollStop = EXPECT_CALL(*mMockApi, pollVibeState(0, -1))
                            .After(ePollHaptics)
                            .WillOnce(DoDefault());
        if (composeEffect) {
            eEraseDone = EXPECT_CALL(*mMockApi, eraseOwtEffect(_, _))
                                 .After(ePollStop)
                                 .WillOnce(DoDefault());
            EXPECT_CALL(*callback, onComplete()).After(eEraseDone).WillOnce(complete);
        } else {
            EXPECT_CALL(*callback, onComplete()).After(ePollStop).WillOnce(complete);
        }
    }

    int32_t lengthMs;
    ndk::ScopedAStatus status = mVibrator->perform(effect, strength, callback, &lengthMs);
    if (status.isOk()) {
        EXPECT_LE(duration, lengthMs);
    } else {
        EXPECT_EQ(EX_UNSUPPORTED_OPERATION, status.getExceptionCode());
        EXPECT_EQ(0, lengthMs);
    }

    if (duration) {
        EXPECT_EQ(future.wait_for(std::chrono::milliseconds(100)), std::future_status::ready);
    }
}

const std::vector<Effect> kEffects{ndk::enum_range<Effect>().begin(),
                                   ndk::enum_range<Effect>().end()};
const std::vector<EffectStrength> kEffectStrengths{ndk::enum_range<EffectStrength>().begin(),
                                                   ndk::enum_range<EffectStrength>().end()};

INSTANTIATE_TEST_CASE_P(VibratorTests, EffectsTest,
                        Combine(ValuesIn(kEffects.begin(), kEffects.end()),
                                ValuesIn(kEffectStrengths.begin(), kEffectStrengths.end())),
                        EffectsTest::PrintParam);

struct PrimitiveParam {
    CompositePrimitive primitive;
    EffectIndex index;
};

class PrimitiveTest : public VibratorTest, public WithParamInterface<PrimitiveParam> {
  public:
    static auto PrintParam(const TestParamInfo<ParamType> &info) {
        return toString(info.param.primitive);
    }
};

const std::vector<PrimitiveParam> kPrimitiveParams = {
        {CompositePrimitive::CLICK, 2},      {CompositePrimitive::THUD, 4},
        {CompositePrimitive::SPIN, 5},       {CompositePrimitive::QUICK_RISE, 6},
        {CompositePrimitive::SLOW_RISE, 7},  {CompositePrimitive::QUICK_FALL, 8},
        {CompositePrimitive::LIGHT_TICK, 9}, {CompositePrimitive::LOW_TICK, 10},
};

TEST_P(PrimitiveTest, getPrimitiveDuration) {
    auto param = GetParam();
    auto primitive = param.primitive;
    auto index = param.index;
    int32_t duration;

    EXPECT_EQ(EX_NONE, mVibrator->getPrimitiveDuration(primitive, &duration).getExceptionCode());
    EXPECT_EQ(EFFECT_DURATIONS[index], duration);
}

INSTANTIATE_TEST_CASE_P(VibratorTests, PrimitiveTest,
                        ValuesIn(kPrimitiveParams.begin(), kPrimitiveParams.end()),
                        PrimitiveTest::PrintParam);

struct ComposeParam {
    std::string name;
    std::vector<CompositeEffect> composite;
    EffectQueue queue;
};

class ComposeTest : public VibratorTest, public WithParamInterface<ComposeParam> {
  public:
    static auto PrintParam(const TestParamInfo<ParamType> &info) { return info.param.name; }
};

TEST_P(ComposeTest, compose) {
    auto param = GetParam();
    auto composite = param.composite;
    auto queue = std::get<0>(param.queue);
    ExpectationSet eSetup;
    Expectation eActivate, ePollHaptics, ePollStop, eEraseDone;
    auto callback = ndk::SharedRefBase::make<MockVibratorCallback>();
    std::promise<void> promise;
    std::future<void> future{promise.get_future()};
    auto complete = [&promise] {
        promise.set_value();
        return ndk::ScopedAStatus::ok();
    };

    eSetup += EXPECT_CALL(*mMockStats, logLatencyStart(kCompositionEffectLatency))
                      .WillOnce(DoDefault());
    for (auto &primitive : composite) {
        eSetup += EXPECT_CALL(*mMockStats, logPrimitive(_)).After(eSetup).WillOnce(DoDefault());
    }
    eSetup +=
            EXPECT_CALL(*mMockApi, setFFGain(ON_GLOBAL_SCALE)).After(eSetup).WillOnce(DoDefault());
    eSetup += EXPECT_CALL(*mMockApi, getOwtFreeSpace(_)).WillOnce(DoDefault());
    eSetup += EXPECT_CALL(*mMockApi, uploadOwtEffect(_, _, _, _, _))
                      .After(eSetup)
                      .WillOnce(DoDefault());
    eSetup += EXPECT_CALL(*mMockStats, logLatencyEnd()).WillOnce(DoDefault());
    eActivate = EXPECT_CALL(*mMockApi, setFFPlay(WAVEFORM_COMPOSE, true))
                        .After(eSetup)
                        .WillOnce(DoDefault());

    ePollHaptics = EXPECT_CALL(*mMockApi, pollVibeState(1, POLLING_TIMEOUT))
                           .After(eActivate)
                           .WillOnce(DoDefault());
    ePollStop =
            EXPECT_CALL(*mMockApi, pollVibeState(0, -1)).After(ePollHaptics).WillOnce(DoDefault());
    eEraseDone =
            EXPECT_CALL(*mMockApi, eraseOwtEffect(_, _)).After(ePollStop).WillOnce(DoDefault());
    EXPECT_CALL(*callback, onComplete()).After(eEraseDone).WillOnce(complete);

    EXPECT_EQ(EX_NONE, mVibrator->compose(composite, callback).getExceptionCode());

    EXPECT_EQ(future.wait_for(std::chrono::milliseconds(100)), std::future_status::ready);
}

const std::vector<ComposeParam> kComposeParams = {
        {"click",
         {{0, CompositePrimitive::CLICK, 1.0f}},
         Queue(QueueEffect(2, Level(1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 0)},
        {"thud",
         {{1, CompositePrimitive::THUD, 0.8f}},
         Queue(1, QueueEffect(4, Level(0.8f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 0)},
        {"spin",
         {{2, CompositePrimitive::SPIN, 0.6f}},
         Queue(2, QueueEffect(5, Level(0.6f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 0)},
        {"quick_rise",
         {{3, CompositePrimitive::QUICK_RISE, 0.4f}},
         Queue(3, QueueEffect(6, Level(0.4f, V_LONG_DEFAULT[0], V_LONG_DEFAULT[1])), 0)},
        {"slow_rise",
         {{4, CompositePrimitive::SLOW_RISE, 0.0f}},
         Queue(4, QueueEffect(7, Level(0.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 0)},
        {"quick_fall",
         {{5, CompositePrimitive::QUICK_FALL, 1.0f}},
         Queue(5, QueueEffect(8, Level(1.0f, V_LONG_DEFAULT[0], V_LONG_DEFAULT[1])), 0)},
        {"pop",
         {{6, CompositePrimitive::SLOW_RISE, 1.0f}, {50, CompositePrimitive::THUD, 1.0f}},
         Queue(6, QueueEffect(7, Level(1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 50,
               QueueEffect(4, Level(1.0f, V_CLICK_DEFAULT[0], V_CLICK_DEFAULT[1])), 0)},
        {"snap",
         {{7, CompositePrimitive::QUICK_RISE, 1.0f}, {0, CompositePrimitive::QUICK_FALL, 1.0f}},
         Queue(7, QueueEffect(6, Level(1.0f, V_LONG_DEFAULT[0], V_LONG_DEFAULT[1])),
               QueueEffect(8, Level(1.0f, V_LONG_DEFAULT[0], V_LONG_DEFAULT[1])), 0)},
};

INSTANTIATE_TEST_CASE_P(VibratorTests, ComposeTest,
                        ValuesIn(kComposeParams.begin(), kComposeParams.end()),
                        ComposeTest::PrintParam);
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
