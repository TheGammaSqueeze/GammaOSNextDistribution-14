//===-- size_map_gen.cpp --------------------------------------------------===//
//
// Part of the LLVM Project, under the Apache License v2.0 with LLVM Exceptions.
// See https://llvm.org/LICENSE.txt for license information.
// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
//
//===----------------------------------------------------------------------===//

#include "allocator_config.h"
#include "libsize_map_verify.h"
#include "size_class_map.h"
#include <iostream>

int main() {
  bool fullyPassed = true;
  std::string NumBitsMessage;
  std::string verifySizeMessage;
  std::string optimizeMessage;
  std::string dumpMessage;

  fullyPassed = fullyPassed &&
                scudo::generateNumBits<scudo::AndroidNormalSizeClassConfig>(
                    NumBitsMessage);
  fullyPassed = fullyPassed &&
                scudo::verifySizeClass<scudo::AndroidNormalSizeClassConfig>(
                    verifySizeMessage);
  scudo::optimizeMidSizeLog<scudo::AndroidNormalSizeClassConfig>(
      optimizeMessage);
  scudo::dumpszTableInfo<scudo::AndroidNormalSizeClassConfig>(dumpMessage);

  if (!NumBitsMessage.empty()) {
    std::cout << "NumBits Calculator:" << std::endl;
    std::cout << NumBitsMessage << std::endl;
  }
  if (!verifySizeMessage.empty()) {
    std::cout << "Sizes Verification:" << std::endl;
    std::cout << verifySizeMessage << std::endl;
  }
  if (!verifySizeMessage.empty()) {
    std::cout << "Optimizations:" << std::endl;
    std::cout << optimizeMessage << std::endl;
  }
  if (!verifySizeMessage.empty()) {
    std::cout << "szTable Dump:" << std::endl;
    std::cout << dumpMessage << std::endl;
  }

  if (fullyPassed == true)
    std::cout << "All Parameters Passed.\n\n";
  else
    std::cout << "Errors Detected. Check NumBits Calculator or Size "
                 "Verification\n\n";

  scudo::validateMap<scudo::AndroidNormalSizeClassMap>();

  return fullyPassed ? 0 : 1;
}
