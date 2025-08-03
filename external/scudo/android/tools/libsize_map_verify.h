//===-- libsize_map_verify.h -------------------------------------*- C++-*-===//
//
// Part of the LLVM Project, under the Apache License v2.0 with LLVM Exceptions.
// See https://llvm.org/LICENSE.txt for license information.
// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
//
//===----------------------------------------------------------------------===//

#ifndef SCUDO_LIBSIZE_MAP_VERIFY_H_
#define SCUDO_LIBSIZE_MAP_VERIFY_H_

#include "common.h"
#include "size_class_map.h"
#include <string>
#include <vector>

namespace scudo {
typedef u8 szTableT;

// Returns the index of each size class.
// Attempting to find the smallest size that fits within each size class.
// Example:
// If a size class is 8 then 4,5,6,7,8 return the index of size 8
// but 9 would return the index of the next size class, 16.
u8 computeClassId(uptr Size, u32 ClassesSize, u32 Classes[]) {
  for (uptr i = 0; i != ClassesSize; ++i)
    if (Size <= Classes[i])
      return static_cast<u8>(i + 1);
  return static_cast<u8>(-1);
}
// Function returns a vector that contains the classIds for all the sizes.
// Needed to check if the NumBits generated assigns the indexes to
// classIds correctly.
std::vector<u8> szTableCreate(u32 NumBits, u32 MidSizeLog, u32 MaxSizeLog,
                              u32 SizeDelta, u32 ClassesSize, u32 Classes[]) {
  std::vector<u8> Tab((MaxSizeLog - MidSizeLog) << NumBits);
  // Pos starts at the MidSize, which ignores the sizes not used in szTable.
  // Inc uses NumBits - 1 to determine the starting incrementing value.
  // Tab gets the classId of each size based on computeClassId.
  uptr Pos = 1 << MidSizeLog;
  uptr Inc = 1 << (MidSizeLog - NumBits);
  for (uptr i = 0; i != Tab.size(); ++i) {
    Pos += Inc;
    if ((Pos & (Pos - 1)) == 0)
      Inc *= 2;
    Tab[i] = computeClassId(Pos + SizeDelta, ClassesSize, Classes);
  }
  return Tab;
};

// Function returns the index of the first value greater than MidSizeLog.
template <typename Config> uptr findMidSizeIndex() {
  const u32 len = sizeof(Config::Classes) / sizeof(Config::Classes[0]);
  u32 largerMid = 0;
  for (uptr i = 0; i < len; ++i) {
    if (Config::Classes[i] > (1 << Config::MidSizeLog) + Config::SizeDelta) {
      largerMid = i;
      break;
    }
  }
  return largerMid;
}

// Calculates the minimum NumBits that can be used for the given sizes and
// Min/Mid/Max. Smaller NumBits creates a szTable nearly half the size and
// quickens navigation of the table. The sizes smaller than MidSizeLog do not
// use NumBits or szTable, instead using a formula. This method is faster but
// requires sizes to have the exact same spacing of 2^MinSizeLog; therefore,
// having an efficient NumBits allows for the table to be more flexible than the
// formula while still moving at a reasonable speed.
template <typename Config> bool generateNumBits(std::string &manipMessage) {
  // In size_class_map S is used, so it is used for consistency.
  u32 S = Config::NumBits - 1;
  const u32 len = sizeof(Config::Classes) / sizeof(Config::Classes[0]);
  // This is used to display the NumBits calculated
  u32 minNumBits = S;
  // largerMid equals the index of the first value greater than MidSizeLog.
  // These sizes are the only ones used with NumBits, smaller sizes are
  // ignored.
  const u32 largerMid = findMidSizeIndex<Config>();
  if (largerMid == 0) {
    manipMessage +=
        "MidSizeLog = MaxSizeLog, NumBits not used for these sizes. "
        "Only uses the formula without szTable.\n";
    return true;
  }

  // Create Classes array that can be inputed into functions and referenced.
  u32 ClassesFunc[len];
  for (uptr i = 0; i < len; ++i)
    ClassesFunc[i] = Config::Classes[i];
  // Create smaller Classes array that can be manipulated to calculate NumBits.
  u32 ClassesManip[len - largerMid];
  for (uptr i = 0; i < len - largerMid; ++i)
    ClassesManip[i] = ClassesFunc[i + largerMid] - Config::SizeDelta;

  u32 holdIndex[len - largerMid];
  bool failed = false;
  // Starting at intial S, it decreases to find the smallest working S
  // for the current config.
  for (; S > 0; --S) {
    // For each size it calls the on the algorithm which retuns an index.
    for (uptr i = 0; i < len - largerMid; ++i)
      holdIndex[i] = scaledLog2(ClassesManip[i] - 1, Config::MidSizeLog, S);

    // Vector that holds classIds for sizes that is navigated using indexes
    // stored in holdIndex.
    std::vector szTableT =
        szTableCreate(S, Config::MidSizeLog, Config::MaxSizeLog,
                      Config::SizeDelta, len, ClassesFunc);

    // Checks that each index in holdIndex should refer to a unique classId,
    // therefore a unique size a duplicate means that the calculated index
    // for two different sizes refers to the same classId.
    for (uptr i = 1; i < len - largerMid; ++i) {
      if (szTableT[holdIndex[i]] == szTableT[holdIndex[i - 1]]) {
        failed = true;
        break;
      }
    }
    if (failed == true)
      break;
  }
  // Setting minNumBits to the last working NumBits and Numbits = S + 1.
  minNumBits = S + 2;
  // Adds a check to ensure NumBits calculated is not too large.
  if (minNumBits - 1 > Config::MidSizeLog) {
    manipMessage +=
        "Calculated Numbits too large. The max size for NumBits is: "
        "NumBits - 1 = MidSizeLog.\n"
        "NumBits = " +
        std::to_string(minNumBits) + "\n";
    return false;
  }
  manipMessage += "NumBits = " + std::to_string(minNumBits) + "\n";
  return true;
}

// Verify the sizes and variables entered are functional.
// If not, gives a brief explaination of the error.
template <typename Config> bool verifySizeClass(std::string &manipMessage) {
  const u32 len = sizeof(Config::Classes) / sizeof(Config::Classes[0]);
  u32 ClassesFunc[len];
  for (uptr i = 0; i < len; ++i)
    ClassesFunc[i] = Config::Classes[i];

  // Verify smallest size = MinSizeLog and largest size = MaxSizeLog.
  // Log base 2 of (smallest size - SizeDelta) and
  // Log base 2 of (largest size - SizeDelta).
  const u32 MinSize = (1 << Config::MinSizeLog);
  const u32 MaxSize = (1 << Config::MaxSizeLog);
  if (ClassesFunc[0] - Config::SizeDelta != MinSize) {
    manipMessage += "MinSizeLog + SizeDelta not equal to the smallest size. " +
                    std::to_string(MinSize) +
                    " != " + std::to_string(ClassesFunc[0]) + "\n\n";
    return false;
  }
  if (ClassesFunc[len - 1] - Config::SizeDelta != MaxSize) {
    manipMessage += "Largest size (" + std::to_string(ClassesFunc[len - 1]) +
                    ") - SizeDelta (" + std::to_string(Config::SizeDelta) +
                    ") != MaxSize (" + std::to_string(MaxSize) + ")\n\n";
    return false;
  }
  // Verify MidSizeLog is greater than MinSizeLog.
  const u32 MidSize = (1 << Config::MidSizeLog);
  if (MidSize <= MinSize) {
    manipMessage +=
        "MidSizeLog needs to be greater than MinSizeLog\n"
        "If the MidSizeLog is equal to MinSizeLog then the szTable will be "
        "used for every size.\nMin size = " +
        std::to_string(MinSize) + "\tMid size = " + std::to_string(MidSize) +
        "\n\n";
    return false;
  }

  // Displays why MidSizeLog is not working.
  for (uptr i = 1; i < len; ++i) {
    // If the step ends prior to MidSize, the step needs extending.
    if (ClassesFunc[i] - ClassesFunc[i - 1] != 1 << Config::MinSizeLog &&
        ClassesFunc[i - 1] - Config::SizeDelta < MidSize) {
      manipMessage +=
          "MidSizeLog non-table formula can be used until: " +
          std::to_string(ClassesFunc[i - 1]) +
          "\n\nCurrently stops at: " + std::to_string(MidSize) +
          "\nFor size_map to work, formula must work for a number >= "
          "the current MidSize.\nMidSizeLog is either too large or their "
          "is not an equal step between desired sizes."
          "\nThe step between sizes should equal 2^MinSizeLog.\n\n";
      return false;
    } else if (ClassesFunc[i] - ClassesFunc[i - 1] != 1 << Config::MinSizeLog ||
               MidSize == MaxSize) {
      manipMessage += "MidSizeLog non-szTable formula is used until: " +
                      std::to_string(MidSize + Config::SizeDelta) + "\n";
      break;
    }
  }
  // Verifying if the MidSizeLog and MaxSizeLog.
  if (MidSize == MaxSize) {
    manipMessage +=
        "MidSizeLog = MaxSizeLog, szTable and NumBits are not used at "
        "all.\n";
    return true;
  }

  // Recreates NumBits arrays/vectors to verify the NumBits.
  // Explained in generateNumBits.
  u32 S = Config::NumBits - 1;
  std::vector szTableT =
      szTableCreate(S, Config::MidSizeLog, Config::MaxSizeLog,
                    Config::SizeDelta, len, ClassesFunc);
  const u32 largerMid = findMidSizeIndex<Config>();
  u32 ClassesManip[len - largerMid];
  for (uptr i = 0; i < len - largerMid; ++i)
    ClassesManip[i] = Config::Classes[i + largerMid] - Config::SizeDelta;
  u32 holdIndex[len - largerMid];
  for (uptr i = 0; i < len - largerMid; ++i)
    holdIndex[i] = scaledLog2(ClassesManip[i] - 1, Config::MidSizeLog, S);

  for (uptr i = 1; i < len - largerMid; ++i) {
    if (szTableT[holdIndex[i]] == szTableT[holdIndex[i - 1]]) {
      manipMessage +=
          "\nNumBits not large enough to distinguish between values. "
          "\nHard max NumBits - 1 cannot exceed MidSizeLog.\n"
          "If NumBits is at max then increase Min/Mid/Max sizelogs and "
          "increase the sizes accordingly.\n\n\n";
      return false;
    }
  }
  return true;
}

// Display to what size MidSizeLog will work with and most efficient numbers.
// MidSizeLog uses a formula, not a table.
template <typename Config> void optimizeMidSizeLog(std::string &manipMessage) {
  const u32 len = sizeof(Config::Classes) / sizeof(Config::Classes[0]);
  u32 ClassesFunc[len];
  for (uptr i = 0; i < len; ++i)
    ClassesFunc[i] = Config::Classes[i];

  const u32 MaxSize = (1 << Config::MaxSizeLog);
  const u32 MidSize = (1 << Config::MidSizeLog);
  for (uptr i = 1; i < len; ++i) {
    if (ClassesFunc[i] - ClassesFunc[i - 1] == 1 << Config::MinSizeLog)
      continue;
    manipMessage +=
        "MidSizeLog non-table formula can be used until: " +
        std::to_string(ClassesFunc[i - 1]) +
        "\nCurrently stops at: " + std::to_string(MidSize + Config::SizeDelta) +
        "\n";
    if (MidSize == ClassesFunc[i - 1] - Config::SizeDelta) {
      manipMessage +=
          "MidSizeLog is used efficiently and fully for current config\n";
    } else {
      manipMessage +=
          "For size_map to work, formula must work for a number "
          ">= the current MidSize.\nMax efficiency is achieved if they "
          "are equal.\n";
      if (ClassesFunc[i - 1] - Config::SizeDelta > MidSize) {
        manipMessage +=
            "In order to match numbers, increase MidSizeLog.\nEnsure "
            "each size up to the new MidSize has an equal step between "
            "each size.\nThe step equals 2^MinSizeLog.\n";
      } else if (ClassesFunc[i - 1] - Config::SizeDelta < MidSize) {
        manipMessage +=
            "MidSizeLog is either too large or their is not an equal "
            "step between desired sizes.\nThe step between sizes "
            "should equal 2^MinSizeLog.\n";
      }
    }
    break;
  }
  if (ClassesFunc[len - 1] - Config::SizeDelta == MidSize) {
    manipMessage +=
        "MidSizeLog non-table formula can be used until: " +
        std::to_string(ClassesFunc[len - 1]) +
        "\nCurrently stops at: " + std::to_string(MidSize) +
        "\n"
        "MidSizeLog is used efficiently and fully for current config\n";
  }
}

// Dumps the size of szTable in elements and bits.
template <typename Config> bool dumpszTableInfo(std::string &manipMessage) {
  u32 S = Config::NumBits - 1;
  const u32 len = sizeof(Config::Classes) / sizeof(Config::Classes[0]);
  u32 minNumBits = S;
  const u32 largerMid = findMidSizeIndex<Config>();
  bool failed = false;

  if (largerMid == 0) {
    manipMessage += "Does not use NumBits. MidSizeLog = MaxsizeLog.";
    return true;
  }
  u32 ClassesFunc[len];
  for (uptr i = 0; i < len; ++i)
    ClassesFunc[i] = Config::Classes[i];
  std::vector szTableT =
      szTableCreate(S, Config::MidSizeLog, Config::MaxSizeLog,
                    Config::SizeDelta, len, ClassesFunc);
  manipMessage +=
      "szTable Number of Elements: " + std::to_string(szTableT.size()) +
      "\nSize of szTable in Bits: " +
      std::to_string(szTableT.size() * sizeof(u8)) + "\n";
  return true;
}
} // namespace scudo

#endif // SCUDO_LIBSIZE_MAP_VERIFY_H_
