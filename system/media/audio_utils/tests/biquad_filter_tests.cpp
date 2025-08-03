/*
 * Copyright 2020 The Android Open Source Project
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

#include <array>
#include <random>
#include <vector>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <audio_utils/BiquadFilter.h>

using ::testing::Pointwise;
using ::testing::FloatNear;
using namespace android::audio_utils;

/************************************************************************************
 * Reference data, must not change.
 * The reference output data is from running in matlab or octave y = filter(b, a, x), where
 *     b = [2.0 3.0 4.0]
 *     a = [1.0 0.2 0.3]
 *     x = [-0.1 -0.2 -0.3 -0.4 -0.5 0.1 0.2 0.3 0.4 0.5]
 *     filter(b, a, x)
 *
 * The output y = [-0.2 -0.66 -1.4080 -2.0204 -2.5735 -1.7792 -0.1721 2.1682 2.1180 2.3259].
 * The reference data construct the input and output as 2D array so that it can be
 * use to practice calling BiquadFilter::process multiple times.
 ************************************************************************************/
constexpr size_t FRAME_COUNT = 5;
constexpr size_t PERIOD = 2;
constexpr float INPUT[PERIOD][FRAME_COUNT] = {
        {-0.1f, -0.2f, -0.3f, -0.4f, -0.5f},
        {0.1f, 0.2f, 0.3f, 0.4f, 0.5f}};
// COEFS in order of [ b0 b1 b2 a1 a2 ], normalized form where a0 = 1.
constexpr std::array<float, kBiquadNumCoefs> COEFS = {
        2.0f, 3.0f, 4.0f, 0.2f, 0.3f };
constexpr float OUTPUT[PERIOD][FRAME_COUNT] = {
        {-0.2f, -0.66f, -1.4080f, -2.0204f, -2.5735f},
        {-1.7792f, -0.1721f, 2.1682f, 2.1180f, 2.3259f}};
constexpr float EPS = 1e-4f;

template <typename S, typename D>
static void populateBuffer(const S *singleChannelBuffer, size_t frameCount,
        size_t channelCount, size_t zeroChannels, D *buffer) {
    const size_t stride = channelCount + zeroChannels;
    for (size_t i = 0; i < frameCount; ++i) {
        size_t j = 0;
        for (; j < channelCount; ++j) {
            buffer[i * stride + j] = singleChannelBuffer[i];
        }
        for (; j < stride; ++j) {
            buffer[i * stride + j] = D{};
        }
    }
}

template <typename D>
static void randomBuffer(D *buffer, size_t frameCount, size_t channelCount) {
    static std::minstd_rand gen(42);
    constexpr float amplitude = 1.0f;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (size_t i = 0; i < frameCount * channelCount; ++i) {
        buffer[i] = dis(gen);
    }
}

template <typename D>
static std::array<D, 5> randomFilter() {
    static std::minstd_rand gen(42);
    constexpr float amplitude = 0.9f;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    const D p1 = (D)dis(gen);
    const D p2 = (D)dis(gen);
    return {(D)dis(gen), (D)dis(gen), (D)dis(gen), -(p1 + p2), p1 * p2};
}

template <typename D>
static std::array<D, 5> randomUnstableFilter() {
    static std::minstd_rand gen(42);
    constexpr float amplitude = 3.;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    // symmetric in p1 and p2.
    const D p1 = (D)dis(gen);
    D p2;
    while (true) {
        p2 = (D)dis(gen);
        if (fabs(p2) > 1.1) break;
    }
    return {(D)dis(gen), (D)dis(gen), (D)dis(gen), -(p1 + p2), p1 * p2};
}

// The BiquadFilterTest is parameterized on channel count.
class BiquadFilterTest : public ::testing::TestWithParam<size_t> {
protected:
    template <typename ConstOptions, typename T>
    static void testProcess(size_t zeroChannels = 0) {
        const size_t channelCount = static_cast<size_t>(GetParam());
        const size_t stride = channelCount + zeroChannels;
        const size_t sampleCount = FRAME_COUNT * stride;
        T inputBuffer[PERIOD][sampleCount];
        T outputBuffer[sampleCount];
        T expectedOutputBuffer[PERIOD][sampleCount];
        for (size_t i = 0; i < PERIOD; ++i) {
            populateBuffer(INPUT[i], FRAME_COUNT, channelCount, zeroChannels, inputBuffer[i]);
            populateBuffer(
                    OUTPUT[i], FRAME_COUNT, channelCount, zeroChannels, expectedOutputBuffer[i]);
        }
        BiquadFilter<T, true /* SAME_COEF_PER_CHANNEL */, ConstOptions>
            filter(channelCount, COEFS);

        for (size_t i = 0; i < PERIOD; ++i) {
            filter.process(outputBuffer, inputBuffer[i], FRAME_COUNT, stride);
            EXPECT_THAT(std::vector<float>(outputBuffer, outputBuffer + sampleCount),
                        Pointwise(FloatNear(EPS), std::vector<float>(
                                expectedOutputBuffer[i], expectedOutputBuffer[i] + sampleCount)));
        }

        // After clear, the previous delays should be cleared.
        filter.clear();
        filter.process(outputBuffer, inputBuffer[0], FRAME_COUNT, stride);
        EXPECT_THAT(std::vector<float>(outputBuffer, outputBuffer + sampleCount),
                    Pointwise(FloatNear(EPS), std::vector<float>(
                            expectedOutputBuffer[0], expectedOutputBuffer[0] + sampleCount)));
    }
};

struct StateSpaceOptions {
    template <typename T, typename F>
    using FilterType = BiquadStateSpace<T, F>;
};

struct StateSpaceChannelOptimizedOptions {
    template <typename T, typename F>
    using FilterType = BiquadStateSpace<T, F, true /* SEPARATE_CHANNEL_OPTIMIZATION */>;
};

struct Direct2TransposeOptions {
    template <typename T, typename F>
    using FilterType = BiquadDirect2Transpose<T, F>;
};

TEST_P(BiquadFilterTest, ConstructAndProcessSSFilterFloat) {
    testProcess<StateSpaceOptions, float>();
}

TEST_P(BiquadFilterTest, ConstructAndProcessSSFilterDouble) {
    testProcess<StateSpaceOptions, double>();
}

TEST_P(BiquadFilterTest, ConstructAndProcessSSFilterFloatZero3) {
    testProcess<StateSpaceOptions, float>(3 /* zeroChannels */);
}

TEST_P(BiquadFilterTest, ConstructAndProcessSSFilterDoubleZero5) {
    testProcess<StateSpaceOptions, double>(5 /* zeroChannels */);
}

TEST_P(BiquadFilterTest, ConstructAndProcessSSChanelOptimizedFilterFloat) {
    testProcess<StateSpaceChannelOptimizedOptions, float>();
}

TEST_P(BiquadFilterTest, ConstructAndProcessSSChannelOptimizedFilterDouble) {
    testProcess<StateSpaceChannelOptimizedOptions, double>();
}

TEST_P(BiquadFilterTest, ConstructAndProcessDT2FilterFloat) {
    testProcess<Direct2TransposeOptions, float>();
}

TEST_P(BiquadFilterTest, ConstructAndProcessDT2FilterDouble) {
    testProcess<Direct2TransposeOptions, double>();
}

INSTANTIATE_TEST_CASE_P(
        CstrAndRunBiquadFilter,
        BiquadFilterTest,
        ::testing::Values(1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24)
        );

// Test the experimental 1D mode.
TEST(BiquadBasicTest, OneDee) {
    using D = float;
    constexpr size_t TEST_LENGTH = 1024;
    constexpr size_t FILTERS = 3;
    std::vector<D> reference(TEST_LENGTH);
    randomBuffer(reference.data(), TEST_LENGTH, 1 /* channelCount */);

    BiquadFilter<D, true> parallel(FILTERS, COEFS);
    std::vector<std::unique_ptr<BiquadFilter<D>>> biquads(FILTERS);
    for (auto& biquad : biquads) {
        biquad.reset(new BiquadFilter<D>(1, COEFS));
    }

    auto test1 = reference;
    parallel.process1D(test1.data(), TEST_LENGTH);

    auto test2 = reference;
    for (auto& biquad : biquads) {
        biquad->process(test2.data(), test2.data(), TEST_LENGTH);
    }
    EXPECT_THAT(test1, Pointwise(FloatNear(EPS), test2));
}

// The BiquadBasicTest is parameterized on floating point type (float or double).
template <typename D>
class BiquadBasicTest : public ::testing::Test {
protected:

    // Multichannel biquad test where each channel has different filter coefficients.
    static void testDifferentFiltersPerChannel() {
        constexpr size_t FILTERS = 3;
        constexpr size_t TEST_LENGTH = 1024;
        std::vector<D> reference(TEST_LENGTH * FILTERS);
        randomBuffer(reference.data(), TEST_LENGTH, FILTERS);

        std::array<std::array<D, 5>, FILTERS> filters;
        for (auto &filter : filters) {
            filter = randomFilter<D>();
        }

        BiquadFilter<D, false> multichannel(FILTERS);
        std::vector<std::unique_ptr<BiquadFilter<D>>> biquads(FILTERS);
        for (size_t i = 0; i < filters.size(); ++i) {
            ASSERT_TRUE(multichannel.setCoefficients(filters[i], i));
            biquads[i].reset(new BiquadFilter<D>(1 /* channels */, filters[i]));
        }

        // Single multichannel Biquad with different filters per channel.
        auto test1 = reference;
        multichannel.process(test1.data(), test1.data(), TEST_LENGTH);

        // Multiple different single channel Biquads applied to the test data, with a stride.
        auto test2 = reference;
        for (size_t i = 0; i < biquads.size(); ++i) {
            biquads[i]->process(test2.data() + i, test2.data() + i, TEST_LENGTH, FILTERS);
        }

        // Must be equivalent.
        EXPECT_THAT(test1, Pointwise(FloatNear(EPS), test2));
    }

    // Test zero fill with coefficients all zero.
    static void testZeroFill() {
        constexpr size_t TEST_LENGTH = 1024;

        // Randomize input and output.
        std::vector<D> reference(TEST_LENGTH);
        randomBuffer(reference.data(), TEST_LENGTH, 1);
        std::vector<D> output(TEST_LENGTH);
        randomBuffer(output.data(), TEST_LENGTH, 1);

        // Single channel Biquad
        BiquadFilter<D> bqf(1 /* channelCount */, {} /* coefs */);


        bqf.process(output.data(), reference.data(), TEST_LENGTH);

        // Result is zero.
        const std::vector<D> zero(TEST_LENGTH);
        ASSERT_EQ(zero, output);
        ASSERT_NE(zero, reference);
    }

    // Stability check
    static void testStability() {
        BiquadFilter<D> bqf(1 /* channels */);
        constexpr size_t TRIALS = 1000;
        for (size_t i = 0; i < TRIALS; ++i) {
            ASSERT_TRUE(bqf.setCoefficients(randomFilter<D>()));
            ASSERT_FALSE(bqf.setCoefficients(randomUnstableFilter<D>()));
        }
    }

    // Constructor, assignment equivalence check
    static void testEquivalence() {
        for (size_t channelCount = 1; channelCount < 3; ++channelCount) {
            BiquadFilter<D> bqf1(channelCount);
            BiquadFilter<D> bqf2(channelCount);
            ASSERT_TRUE(bqf1.setCoefficients(randomFilter<D>()));
            ASSERT_FALSE(bqf2.setCoefficients(randomUnstableFilter<D>()));
            ASSERT_NE(bqf1, bqf2); // one is stable one isn't, can't be the same.
            constexpr size_t TRIALS = 10;  // try a few different filters, just to be sure.
            for (size_t i = 0; i < TRIALS; ++i) {
                ASSERT_TRUE(bqf1.setCoefficients(randomFilter<D>()));
                // Copy construction/assignment is equivalent.
                const auto bqf3 = bqf1;
                ASSERT_EQ(bqf1, bqf3);
                const auto bqf4(bqf1);
                ASSERT_EQ(bqf1, bqf4);

                BiquadFilter<D> bqf5(channelCount);
                bqf5.setCoefficients(bqf1.getCoefficients());
                ASSERT_EQ(bqf1, bqf5);
            }
        }
    }

    // Test that 6 coefficient definition reduces to same 5 coefficient definition
    static void testCoefReductionEquivalence() {
        std::array<D, 5> coef5 = randomFilter<D>();
        // The 6 coefficient version has a0.
        // This should be a power of 2 to be exact for IEEE binary float
        for (size_t shift = 0; shift < 4; ++shift) {
            const D a0 = 1 << shift;
            std::array<D, 6> coef6 = { coef5[0] * a0, coef5[1] * a0, coef5[2] * a0,
                a0, coef5[3] * a0, coef5[4] * a0
            };
            for (size_t channelCount = 1; channelCount < 2; ++channelCount) {
                BiquadFilter<D> bqf1(channelCount, coef5);
                BiquadFilter<D> bqf2(channelCount, coef6);
                ASSERT_EQ(bqf1, bqf2);
            }
        }
    }
};

using FloatTypes = ::testing::Types<float, double>;
TYPED_TEST_CASE(BiquadBasicTest, FloatTypes);

TYPED_TEST(BiquadBasicTest, DifferentFiltersPerChannel) {
    this->testDifferentFiltersPerChannel();
}

TYPED_TEST(BiquadBasicTest, ZeroFill) {
    this->testZeroFill();
}

TYPED_TEST(BiquadBasicTest, Stability) {
    this->testStability();
}

TYPED_TEST(BiquadBasicTest, Equivalence) {
    this->testEquivalence();
}

TYPED_TEST(BiquadBasicTest, CoefReductionEquivalence) {
    this->testCoefReductionEquivalence();
}
