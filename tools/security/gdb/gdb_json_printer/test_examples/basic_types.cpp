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
#include <cstdint>
#include <cstdlib>
#include <iostream>

enum Animal { CAT, DOG, TIGER };

int main() {
  {
    uint8_t a = 1;
    int8_t b = -2;
    uint16_t c = 3;
    int16_t d = -4;
    uint32_t e = 5;
    int32_t f = -6;
    uint64_t g = 7;
    int64_t h = -8;
    float i = 9.0;
    double j = -10.0;
    const char *k = "Hello";
    void *l = nullptr;
    char *m = nullptr;
    bool n = rand() % 2 == 0;
    Animal o = CAT;
    if (n) {
      a += b;
    }
    std::cout << a + b + c + d + e + f + g + h + i + j << std::endl;
  }
  {
    {
      const uint8_t a = 1;
      const int8_t b = -2;
      const uint16_t c = 3;
      const int16_t d = -4;
      const uint32_t e = 5;
      const int32_t f = -6;
      const uint64_t g = 7;
      const int64_t h = -8;
      const float i = 9.0;
      const double j = -10.0;
      const char *k = "Hello";
      const void *l = nullptr;
      const char *m = nullptr;
      const bool n = rand() % 2 == 0;
      const Animal o = DOG;
      if (n) {
        std::cout << a + b + c + d + e + f + g + h + i + j << std::endl;
      }
    }
  }
  return 0;
}