

#pragma once

#include <cstdint>

#include "test/headless/log.h"

namespace bluetooth {
namespace test {
namespace headless {

template <typename T>
void dump(const T* data, std::string comment = std::string("dump")) {
  const uint8_t* p = reinterpret_cast<const uint8_t*>(data);
  for (size_t i = 0; i < sizeof(T); i++, p++) {
    LOG_CONSOLE("  %s  %p:0x%02x", comment.c_str(), p, *p);
  }
}

}  // namespace headless
}  // namespace test
}  // namespace bluetooth
