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
#include <memory>

class Bar {
public:
  uint16_t b = 2;
};

class Foo {
public:
  uint8_t a = 1;
  Bar *bar = nullptr;
};

int main() {
  int16_t a[5] = {1, 2, 3, 4, 5};
  int16_t* b = &a[2];
  Foo foo[3];
  Bar bar[3];
  foo[0].bar = &bar[0];
  foo[1].bar = &bar[1];
  foo[2].bar = &bar[2];
  Foo& foo1 = foo[0];
  
  return 0;
}