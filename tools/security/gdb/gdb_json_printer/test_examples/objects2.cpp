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

class Foo {
public:
  uint8_t a = 1;
  virtual int get() = 0;
};

class Foo1 : public Foo {
  uint16_t b = 2;
  virtual int get() override { return b; }
};

class Foo2 : public Foo {
public:
  uint32_t c = 3;
  uint32_t cc = 4;
  virtual int get() override { return c; }
};

class Foo3 : public Foo1, public Foo2 {
  uint32_t d = 4;
  virtual int get() override { return d; }
};

class Foo4 : virtual public Foo {
  uint16_t b = 2;
  virtual int get() override { return b; }
};

class Foo5 : virtual public Foo {
public:
  uint32_t c = 3;
  uint32_t cc = 4;
  virtual int get() override { return c; }
};

class Foo6 : public Foo4, public Foo5 {
  uint32_t d = 4;
  virtual int get() override { return d; }
};

class Bar {
public:
  uint8_t e = 5;
};

class Bar1 : virtual public Bar {
public:
  uint8_t f = 6;
};

int main() {
  {
    Foo *x = new Foo1();
    Foo *y = new Foo2();
    Foo1 *z = new Foo3();
    Foo *w = z;
    Bar *a = new Bar1();
    Foo2 b;
    Foo &c = *w;
    Foo1 d = *z;
    b.c += 1;
    Foo *e = new Foo6();
    Foo4 *f = dynamic_cast<Foo6 *>(e);

    delete a;
    delete x;
    delete y;
    delete z;
  }
  return 0;
}