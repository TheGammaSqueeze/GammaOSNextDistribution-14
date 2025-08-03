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

#include <android-base/file.h>
#include <gtest/gtest.h>

#include <fstream>

#include "Hardware.h"

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

using ::testing::Test;

class HwCalTest : public Test {
  protected:
    static constexpr std::array<uint32_t, 2> V_TICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_CLICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_LONG_DEFAULT = {1, 100};

  public:
    void SetUp() override { setenv("CALIBRATION_FILEPATH", mCalFile.path, true); }

  private:
    template <typename T>
    static void pack(std::ostream &stream, const T &value, std::string lpad, std::string rpad) {
        stream << lpad << value << rpad;
    }

    template <typename T, typename std::array<T, 0>::size_type N>
    static void pack(std::ostream &stream, const std::array<T, N> &value, std::string lpad,
                     std::string rpad) {
        for (auto &entry : value) {
            pack(stream, entry, lpad, rpad);
        }
    }

  protected:
    void createHwCal() { mHwCal = std::make_unique<HwCal>(); }

    template <typename T>
    void write(const std::string key, const T &value, std::string lpad = " ",
               std::string rpad = "") {
        std::ofstream calfile{mCalFile.path, std::ios_base::app};
        calfile << key << ":";
        pack(calfile, value, lpad, rpad);
        calfile << std::endl;
    }

    void unlink() { ::unlink(mCalFile.path); }

  protected:
    std::unique_ptr<Vibrator::HwCal> mHwCal;
    TemporaryFile mCalFile;
};

TEST_F(HwCalTest, f0_measured) {
    uint32_t randInput = std::rand();
    std::string expect = std::to_string(randInput);
    std::string actual = std::to_string(~randInput);

    write("f0_measured", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getF0(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, f0_missing) {
    std::string actual;

    createHwCal();

    EXPECT_FALSE(mHwCal->getF0(&actual));
}

TEST_F(HwCalTest, redc_measured) {
    uint32_t randInput = std::rand();
    std::string expect = std::to_string(randInput);
    std::string actual = std::to_string(~randInput);

    write("redc_measured", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getRedc(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, redc_missing) {
    std::string actual;

    createHwCal();

    EXPECT_FALSE(mHwCal->getRedc(&actual));
}

TEST_F(HwCalTest, q_measured) {
    uint32_t randInput = std::rand();
    std::string expect = std::to_string(randInput);
    std::string actual = std::to_string(~randInput);

    write("q_measured", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getQ(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, q_missing) {
    std::string actual;

    createHwCal();

    EXPECT_FALSE(mHwCal->getQ(&actual));
}

TEST_F(HwCalTest, v_levels) {
    std::array<uint32_t, 2> expect;
    std::array<uint32_t, 2> actual;

    // voltage for tick effects
    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });

    write("v_tick", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getTickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    // voltage for click effects
    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });

    write("v_click", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getClickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    // voltage for long effects
    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });

    write("v_long", expect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getLongVolLevels(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, v_missing) {
    std::array<uint32_t, 2> expect = V_TICK_DEFAULT;
    std::array<uint32_t, 2> actual;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    createHwCal();

    EXPECT_TRUE(mHwCal->getTickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_CLICK_DEFAULT;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    createHwCal();

    EXPECT_TRUE(mHwCal->getClickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_LONG_DEFAULT;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    createHwCal();

    EXPECT_TRUE(mHwCal->getLongVolLevels(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, v_short) {
    std::array<uint32_t, 2> expect = V_TICK_DEFAULT;
    std::array<uint32_t, 2> actual;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    write("v_tick", std::array<uint32_t, expect.size() - 1>());
    write("v_click", std::array<uint32_t, expect.size() - 1>());
    write("v_long", std::array<uint32_t, expect.size() - 1>());

    createHwCal();

    EXPECT_TRUE(mHwCal->getTickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_CLICK_DEFAULT;
    EXPECT_TRUE(mHwCal->getClickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_LONG_DEFAULT;
    EXPECT_TRUE(mHwCal->getLongVolLevels(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, v_long) {
    std::array<uint32_t, 2> expect = V_TICK_DEFAULT;
    std::array<uint32_t, 2> actual;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    write("v_tick", std::array<uint32_t, expect.size() + 1>());
    write("v_click", std::array<uint32_t, expect.size() + 1>());
    write("v_long", std::array<uint32_t, expect.size() + 1>());

    createHwCal();

    EXPECT_TRUE(mHwCal->getTickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_CLICK_DEFAULT;
    EXPECT_TRUE(mHwCal->getClickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_LONG_DEFAULT;
    EXPECT_TRUE(mHwCal->getLongVolLevels(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, v_nofile) {
    std::array<uint32_t, 2> expect = V_TICK_DEFAULT;
    std::array<uint32_t, 2> actual;

    std::transform(expect.begin(), expect.end(), actual.begin(), [](uint32_t &e) { return ~e; });

    write("v_tick", actual);
    write("v_click", actual);
    write("v_long", actual);
    unlink();

    createHwCal();

    EXPECT_TRUE(mHwCal->getTickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_CLICK_DEFAULT;
    EXPECT_TRUE(mHwCal->getClickVolLevels(&actual));
    EXPECT_EQ(expect, actual);

    expect = V_LONG_DEFAULT;
    EXPECT_TRUE(mHwCal->getLongVolLevels(&actual));
    EXPECT_EQ(expect, actual);
}

TEST_F(HwCalTest, multiple) {
    uint32_t randInput = std::rand();
    std::string f0Expect = std::to_string(randInput);
    std::string f0Actual = std::to_string(~randInput);
    randInput = std::rand();
    std::string redcExpect = std::to_string(randInput);
    std::string redcActual = std::to_string(~randInput);
    randInput = std::rand();
    std::string qExpect = std::to_string(randInput);
    std::string qActual = std::to_string(~randInput);
    std::array<uint32_t, 2> volTickExpect, volClickExpect, volLongExpect;
    std::array<uint32_t, 2> volActual;

    std::transform(volTickExpect.begin(), volTickExpect.end(), volActual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });

    write("f0_measured", f0Expect);
    write("redc_measured", redcExpect);
    write("q_measured", qExpect);
    write("v_tick", volTickExpect);
    std::transform(volClickExpect.begin(), volClickExpect.end(), volActual.begin(),
                   [](uint32_t &e) {
                       e = std::rand();
                       return ~e;
                   });
    write("v_click", volClickExpect);
    std::transform(volLongExpect.begin(), volLongExpect.end(), volActual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });
    write("v_long", volLongExpect);

    createHwCal();

    EXPECT_TRUE(mHwCal->getF0(&f0Actual));
    EXPECT_EQ(f0Expect, f0Actual);
    EXPECT_TRUE(mHwCal->getRedc(&redcActual));
    EXPECT_EQ(redcExpect, redcActual);
    EXPECT_TRUE(mHwCal->getQ(&qActual));
    EXPECT_EQ(qExpect, qActual);
    EXPECT_TRUE(mHwCal->getTickVolLevels(&volActual));
    EXPECT_EQ(volTickExpect, volActual);
    EXPECT_TRUE(mHwCal->getClickVolLevels(&volActual));
    EXPECT_EQ(volClickExpect, volActual);
    EXPECT_TRUE(mHwCal->getLongVolLevels(&volActual));
    EXPECT_EQ(volLongExpect, volActual);
}

TEST_F(HwCalTest, trimming) {
    uint32_t randInput = std::rand();
    std::string f0Expect = std::to_string(randInput);
    std::string f0Actual = std::to_string(~randInput);
    randInput = std::rand();
    std::string redcExpect = std::to_string(randInput);
    std::string redcActual = std::to_string(randInput);
    randInput = std::rand();
    std::string qExpect = std::to_string(randInput);
    std::string qActual = std::to_string(randInput);
    std::array<uint32_t, 2> volTickExpect, volClickExpect, volLongExpect;
    std::array<uint32_t, 2> volActual;

    std::transform(volTickExpect.begin(), volTickExpect.end(), volActual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });

    write("f0_measured", f0Expect, " \t", "\t ");
    write("redc_measured", redcExpect, " \t", "\t ");
    write("q_measured", qExpect, " \t", "\t ");
    write("v_tick", volTickExpect, " \t", "\t ");
    std::transform(volClickExpect.begin(), volClickExpect.end(), volActual.begin(),
                   [](uint32_t &e) {
                       e = std::rand();
                       return ~e;
                   });
    write("v_click", volClickExpect, " \t", "\t ");
    std::transform(volLongExpect.begin(), volLongExpect.end(), volActual.begin(), [](uint32_t &e) {
        e = std::rand();
        return ~e;
    });
    write("v_long", volLongExpect, " \t", "\t ");

    createHwCal();

    EXPECT_TRUE(mHwCal->getF0(&f0Actual));
    EXPECT_EQ(f0Expect, f0Actual);
    EXPECT_TRUE(mHwCal->getRedc(&redcActual));
    EXPECT_EQ(redcExpect, redcActual);
    EXPECT_TRUE(mHwCal->getQ(&qActual));
    EXPECT_EQ(qExpect, qActual);
    EXPECT_TRUE(mHwCal->getTickVolLevels(&volActual));
    EXPECT_EQ(volTickExpect, volActual);
    EXPECT_TRUE(mHwCal->getClickVolLevels(&volActual));
    EXPECT_EQ(volClickExpect, volActual);
    EXPECT_TRUE(mHwCal->getLongVolLevels(&volActual));
    EXPECT_EQ(volLongExpect, volActual);
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
