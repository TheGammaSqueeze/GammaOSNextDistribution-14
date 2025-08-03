//===-- size_map_verify_unit_tests.cpp ------------------------------------===//
//
// Part of the LLVM Project, under the Apache License v2.0 with LLVM Exceptions.
// See https://llvm.org/LICENSE.txt for license information.
// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
//
//===----------------------------------------------------------------------===//

#include "libsize_map_verify.h"
#include "tests/scudo_unit_test.h"

namespace scudo {

class SmokeConfigTest {
  // This test is the base test config.
public:
  static constexpr u32 Classes[] = {
      32,   48,   64,    80,    96,    112,   144,   176,   192,   224,   288,
      352,  448,  592,   800,   1104,  1648,  2096,  2576,  3120,  4112,  4624,
      7120, 8720, 11664, 14224, 16400, 18448, 23056, 29456, 33296, 65552,
  };
  static const u32 MinSizeLog = 4;
  static const u32 MidSizeLog = 6;
  static const u32 MaxSizeLog = 16;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_smoke_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<SmokeConfigTest>(NumBitsMessage));
  EXPECT_EQ("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_smoke_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<SmokeConfigTest>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 80\n",
            verifySizeMessage);
}
class SizeIncreaseConfigPass {
  // Test shows that when every size and Min/Mid/Max changes that
  // NumBits remains the same.
  // Demonstrating NumBits changes based on how close the sizes
  // are to each other.
public:
  static constexpr u32 Classes[] = {
      80,    144,   208,   272,   336,   400,    528,    656,
      720,   848,   1104,  1360,  1744,  2320,   3152,   4368,
      6544,  8336,  10256, 12432, 16400, 18448,  28432,  34832,
      46608, 56848, 65552, 73744, 92176, 117776, 133136, 262160,
  };
  static const u32 MinSizeLog = 6;
  static const u32 MidSizeLog = 8;
  static const u32 MaxSizeLog = 18;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_size_increase_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<SizeIncreaseConfigPass>(NumBitsMessage));
  EXPECT_EQ("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_size_increase_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<SizeIncreaseConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 272\n",
            verifySizeMessage);
}
class MaxSizeConfigPass {
  // This config uses the largest sizes permitted in size_class_map
  // showing that NumBits does not need to increase due to sizes being
  // too large and also shows the limit for MaxSizeLog.
  // Primary allocator works up to 524304.
public:
  static constexpr u32 Classes[] = {
      144,   272,    400,    528,    656,    784,    1040,   1296,
      1424,  1680,   2192,   2704,   3472,   4624,   6288,   8720,
      13072, 16656,  20496,  24848,  32784,  36880,  56848,  69648,
      93200, 113680, 131088, 147472, 184336, 235536, 266256, 524304,
  };
  static const u32 MinSizeLog = 7;
  static const u32 MidSizeLog = 9;
  static const u32 MaxSizeLog = 19;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_max_size_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<MaxSizeConfigPass>(NumBitsMessage));
  EXPECT_EQ("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_max_size_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<MaxSizeConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 528\n",
            verifySizeMessage);
}
class SizeDecreaseConfigFail {
  // The NumBits decreasing causes a failure:
  // NumBits not large enough to notice bit difference between numbers.
  // NumBits cannot be increased due to MidSizeLog - 1 being the limit.
public:
  static constexpr u32 Classes[] = {
      24,   32,   40,   48,   56,   64,   80,    96,    104,   120,   152,
      184,  232,  304,  408,  560,  832,  1056,  1296,  1568,  2064,  2320,
      3568, 4368, 5840, 7120, 8208, 9232, 11536, 14736, 16656, 32784,
  };
  static const u32 MinSizeLog = 3;
  static const u32 MidSizeLog = 5;
  static const u32 MaxSizeLog = 15;
  static const u32 NumBits = 6;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_size_decrease_config) {
  std::string NumBitsMessage;
  EXPECT_FALSE(generateNumBits<SizeDecreaseConfigFail>(NumBitsMessage));
  EXPECT_NE("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_size_decrease_config) {
  std::string verifySizeMessage;
  EXPECT_FALSE(verifySizeClass<SizeDecreaseConfigFail>(verifySizeMessage));
  EXPECT_EQ(
      "MidSizeLog non-szTable formula is used until: 48\n\nNumBits not "
      "large enough to distinguish between values. \nHard max NumBits - 1 "
      "cannot exceed MidSizeLog.\nIf NumBits is at max then increase "
      "Min/Mid/Max sizelogs and increase the sizes accordingly.\n\n\n",
      verifySizeMessage);
}
class MidSizeLog10ConfigPass {
  // Expands the use of the non-table formula to 1040.
  // Shows how to expand the non-table formula by increasing MidSizeLog and
  // by ensuring an equal step between sizes up to MidSizeLog.
  // Shows the tool's ability to use a larger MidSizeLog and a smaller szTable.
  // Demonstrates how many sizes are needed to increase the MidSizeLog.
public:
  static constexpr u32 Classes[] = {
      32,    48,    64,    80,    96,   112,  128,  144,   160,   176,   192,
      208,   224,   240,   256,   272,  288,  304,  320,   336,   352,   368,
      384,   400,   416,   432,   448,  464,  480,  496,   512,   528,   544,
      560,   576,   592,   608,   624,  640,  656,  672,   688,   704,   720,
      736,   752,   768,   784,   800,  816,  832,  848,   864,   880,   896,
      912,   928,   944,   960,   976,  992,  1008, 1024,  1040,  1104,  1648,
      2096,  2576,  3120,  4112,  4624, 7120, 8720, 11664, 14224, 16400, 18448,
      23056, 29456, 33296, 65552,
  };
  static const u32 MinSizeLog = 4;
  static const u32 MidSizeLog = 10;
  static const u32 MaxSizeLog = 16;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_midsizelog_10_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<MidSizeLog10ConfigPass>(NumBitsMessage));
  EXPECT_EQ("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_midsizelog_10_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<MidSizeLog10ConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 1040\n",
            verifySizeMessage);
}
class NumBitsIncreaseConfigPass {
  // Demonstrates when to increase NumBits and how to do it.
  // Ensure NumBits - 1 <= MidSizeLog, with an equal step until MidSizeLog.
  // Increasing NumBits allows more bits to be checked when analyzing sizes
  // NumBits 8 checks 7 bits from the most-significant-bit-index.
  // Here NumBits 8 is needed for the sizes 288 and 290.
  // Shows NumBits increases szTable's flexibility for new sizes.
  // Another condition to remember:
  // Sizes cannot be just 1 larger than previous size.
public:
  static constexpr u32 Classes[] = {
      32,    48,    64,    80,    96,    112,   128,   144,  176,
      192,   224,   288,   290,   352,   448,   592,   800,  1104,
      1648,  2096,  2576,  3120,  4112,  4624,  7120,  8720, 11664,
      14224, 16400, 18448, 23056, 29456, 33296, 65552,
  };
  static const u32 MinSizeLog = 4;
  static const u32 MidSizeLog = 7;
  static const u32 MaxSizeLog = 16;
  static const u32 NumBits = 8;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_numbits_increase_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<NumBitsIncreaseConfigPass>(NumBitsMessage));
  EXPECT_EQ("NumBits = 8\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_numbits_increase_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<NumBitsIncreaseConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 144\n",
            verifySizeMessage);
}
class MidEqualMaxConfigPass {
  // The equality of MidSizeLog and MaxSizeLog shows how the szTable
  // does not need to be used, which makes NumBits obselete.
  // The test shows that the formula can be used for every size.
public:
  static constexpr u32 Classes[] = {
      32,  48,  64,  80,  96,  112, 128, 144, 160, 176,  192,  208,  224,
      240, 256, 272, 288, 304, 320, 336, 352, 368, 384,  400,  416,  432,
      448, 464, 480, 496, 512, 528, 544, 560, 576, 592,  608,  624,  640,
      656, 672, 688, 704, 720, 736, 752, 768, 784, 800,  816,  832,  848,
      864, 880, 896, 912, 928, 944, 960, 976, 992, 1008, 1024, 1040,
  };
  static const u32 MinSizeLog = 4;
  static const u32 MidSizeLog = 10;
  static const u32 MaxSizeLog = 10;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 16;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_mid_equal_max_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<MidEqualMaxConfigPass>(NumBitsMessage));
  EXPECT_EQ(
      "MidSizeLog = MaxSizeLog, NumBits not used for these sizes. Only uses "
      "the formula without szTable.\n",
      NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_mid_equal_max_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<MidEqualMaxConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 1040\nMidSizeLog = "
            "MaxSizeLog, szTable and NumBits are not used at all.\n",
            verifySizeMessage);
}
class SizeDeltaConfigPass {
  // Test shows that when changing SizeDelta, min and max have to
  // change to match.
  // Every size needs to change by whatever the SizeDelta changed by.
  // Sizes need to be added to make Mid match.
public:
  static constexpr u32 Classes[] = {
      16,   24,    32,    40,    48,    56,    64,    72,    88,
      104,  136,   168,   184,   216,   280,   344,   440,   584,
      792,  1096,  1640,  2088,  2568,  3112,  4104,  4616,  7112,
      8712, 11656, 14216, 16392, 18440, 23048, 29448, 33288, 65544,
  };
  static const u32 MinSizeLog = 3;
  static const u32 MidSizeLog = 6;
  static const u32 MaxSizeLog = 16;
  static const u32 NumBits = 7;

  static const u32 SizeDelta = 8;

  static const u32 MaxNumCachedHint = 13;
  static const u32 MaxBytesCachedLog = 13;
};
TEST(ScudoToolSizeMapVerifyTest, generate_size_delta_config) {
  std::string NumBitsMessage;
  EXPECT_TRUE(generateNumBits<SizeDeltaConfigPass>(NumBitsMessage));
  EXPECT_EQ("NumBits = 7\n", NumBitsMessage);
}
TEST(ScudoToolSizeMapVerifyTest, verify_size_delta_config) {
  std::string verifySizeMessage;
  EXPECT_TRUE(verifySizeClass<SizeDeltaConfigPass>(verifySizeMessage));
  EXPECT_EQ("MidSizeLog non-szTable formula is used until: 72\n",
            verifySizeMessage);
}
} // namespace scudo