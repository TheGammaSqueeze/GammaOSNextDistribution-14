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
  {
    Foo *foo = new Foo();
    Bar *bar = new Bar();
    foo->bar = bar;
    delete foo;
    delete bar;
  }
  {
    std::unique_ptr<Foo> foo = std::make_unique<Foo>();
    std::unique_ptr<Bar> bar = std::make_unique<Bar>();
    foo->bar = bar.get();
    foo->bar->b++;
  }
  {
    std::shared_ptr<Foo> foo = std::make_shared<Foo>();
    std::shared_ptr<Bar> bar = std::make_shared<Bar>();
    foo->bar = bar.get();
    foo->bar->b++;
  }
  return 0;
}