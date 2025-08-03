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

class Foo;

class Bar {
public:
  uint16_t b = 2;
  Foo *foo = nullptr;
};

class Foo {
public:
  uint8_t a = 1;
  Bar *bar = nullptr;
};

int main() {
  {
    Foo *foo = new Foo();
    Bar *bar = new Bar();
    foo->bar = bar;
    bar->foo = foo;
    delete foo;
    delete bar;
  }
  return 0;
}