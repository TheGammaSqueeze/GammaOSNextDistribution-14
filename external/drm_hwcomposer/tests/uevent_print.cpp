// SPDX-License-Identifier: Apache-2.0

#include <iostream>

#include "utils/UEvent.h"

int main() {
  auto uevent = android::UEvent::CreateInstance();
  if (!uevent) {
    std::cout << "Can't initialize UEvent class" << std::endl;
    return -ENODEV;
  }

  int number = 0;
  for (;;) {
    auto msg = uevent->ReadNext();
    if (!msg) {
      continue;
    }

    std::cout << "New event #" << number++ << std::endl
              << *msg << std::endl
              << std::endl;
  }
}
