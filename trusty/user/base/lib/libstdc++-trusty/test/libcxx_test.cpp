/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <errno.h>

#include <algorithm>
#include <iostream>
#include <memory>
#include <string>
#include <type_traits>
#include <vector>

#if defined(TRUSTY_USERSPACE)
#include <trusty_unittest.h>
#else
#include <lib/unittest/unittest.h>
#include <lk/trusty_unittest.h>
#endif

int global_count;

#define CHECK_ERRNO(e)       \
    do {                     \
        ASSERT_EQ(e, errno); \
        errno = 0;           \
    } while (0)
#define CLEAR_ERRNO() \
    do {              \
        errno = 0;    \
    } while (0)

typedef struct libcxx {
} libcxx_t;

TEST_F_SETUP(libcxx) {
    /* Isolate the tests. */
    CLEAR_ERRNO();
    global_count = 0;
}

TEST_F_TEARDOWN(libcxx) {
    /* errno should have been checked and cleared if the test sets errno. */
    CHECK_ERRNO(0);
    ASSERT_EQ(0, global_count);

test_abort:
    global_count = 0;
}

class Stub {};

TEST_F(libcxx, new_and_delete) {
    Stub* tmp = new Stub();
    ASSERT_NE(nullptr, tmp);
    delete tmp;
test_abort:;
}

/*
 * NOTE currently -fno-threadsafe-statics is suppressing threadsafe statics.
 * When this is no longer the case, this test will link against __cxa_guard_*.
 * This test is mainly checking that static initializers are executed only once
 * and that all required ABI functions are provided. Thread safety is outside
 * the scope of this test.
 */
static Stub* static_stub_getter() {
    static Stub* d = new Stub();
    return d;
}

TEST_F(libcxx, safe_static) {
    ASSERT_NE(nullptr, static_stub_getter());
    ASSERT_EQ(static_stub_getter(), static_stub_getter());

test_abort:;
}

/*
 * Inspecting the generated code, it appears this variable can be optimized out
 * if it is not declared volatile.
 */
volatile bool did_init;

class GlobalSetter {
public:
    GlobalSetter() { did_init = true; }
};

GlobalSetter setter;

TEST_F(libcxx, global_constructor) {
    /* Did a global constructor run? */
    ASSERT_EQ(true, did_init);
test_abort:;
}

class Counter {
public:
    Counter() { global_count++; }

    Counter(const Counter& other) { global_count++; }

    ~Counter() { global_count--; }
};

TEST_F(libcxx, unique_ptr) {
    ASSERT_EQ(0, global_count);
    {
        std::unique_ptr<Counter> u(new Counter());
        ASSERT_EQ(1, global_count);
    }
    ASSERT_EQ(0, global_count);
test_abort:;
}

TEST_F(libcxx, unique_ptr_move) {
    Counter* p = new Counter();
    std::unique_ptr<Counter> a(p);
    std::unique_ptr<Counter> b;

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(p, a.get());
    ASSERT_EQ(nullptr, b.get());

    b = std::move(a);

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(nullptr, a.get());
    ASSERT_EQ(p, b.get());

    b.reset();
    ASSERT_EQ(0, global_count);
    ASSERT_EQ(nullptr, b.get());

test_abort:;
}

TEST_F(libcxx, shared_ptr) {
    std::shared_ptr<Counter> a;
    std::shared_ptr<Counter> b;
    ASSERT_EQ(0, global_count);
    a.reset(new Counter());
    ASSERT_EQ(1, global_count);
    b = a;
    ASSERT_EQ(1, global_count);
    ASSERT_NE(nullptr, a.get());
    ASSERT_EQ(a.get(), b.get());
    a.reset();
    ASSERT_EQ(1, global_count);
    b.reset();
    ASSERT_EQ(0, global_count);

test_abort:;
}

TEST_F(libcxx, shared_ptr_move) {
    Counter* p = new Counter();
    std::shared_ptr<Counter> a(p);
    std::shared_ptr<Counter> b;

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(p, a.get());
    ASSERT_EQ(nullptr, b.get());

    b = std::move(a);

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(nullptr, a.get());
    ASSERT_EQ(p, b.get());

    b.reset();
    ASSERT_EQ(0, global_count);
    ASSERT_EQ(nullptr, b.get());

test_abort:;
}

TEST_F(libcxx, weak_ptr) {
    std::weak_ptr<Counter> w;
    ASSERT_EQ(0, global_count);
    {
        std::shared_ptr<Counter> s(new Counter());
        w = s;
        ASSERT_EQ(1, global_count);
        ASSERT_EQ(1, w.use_count());
        {
            auto t = w.lock();
            ASSERT_EQ(1, global_count);
            ASSERT_EQ(2, w.use_count());
            ASSERT_EQ(s.get(), t.get());
        }
        ASSERT_EQ(1, global_count);
        ASSERT_EQ(1, w.use_count());
    }
    ASSERT_EQ(0, global_count);
    ASSERT_EQ(0, w.use_count());

test_abort:;
}

TEST_F(libcxx, weak_ptr_move) {
    std::shared_ptr<Counter> s(new Counter());
    std::weak_ptr<Counter> a(s);
    std::weak_ptr<Counter> b;

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(1, a.use_count());
    ASSERT_EQ(0, b.use_count());

    b = std::move(a);

    ASSERT_EQ(1, global_count);
    ASSERT_EQ(0, a.use_count());
    ASSERT_EQ(1, b.use_count());

    s.reset();

    ASSERT_EQ(0, global_count);
    ASSERT_EQ(0, b.use_count());

test_abort:;
}

// TODO test framework does not compare anything that can't be cast to long.
TEST_F(libcxx, string_append) {
    std::string a("abcdefghijklmnopqrstuvwxyz!!!");
    std::string b("abcdefghijklmnopqrstuvwxyz");
    ASSERT_NE(0, strcmp(a.c_str(), b.c_str()));
    b += "!!!";
    ASSERT_EQ(0, strcmp(a.c_str(), b.c_str()));

test_abort:;
}

TEST_F(libcxx, string_move) {
    std::string a("foo");
    std::string b;
    ASSERT_EQ(0, strcmp(a.c_str(), "foo"));
    ASSERT_NE(0, strcmp(b.c_str(), "foo"));

    b = std::move(a);

    ASSERT_NE(0, strcmp(a.c_str(), "foo"));
    ASSERT_EQ(0, strcmp(b.c_str(), "foo"));

test_abort:;
}

TEST_F(libcxx, to_string) {
    ASSERT_EQ(0, strcmp(std::to_string(123).c_str(), "123"));

test_abort:;
}

TEST_F(libcxx, vector) {
    const int limit = 20;
    std::vector<int> v = {1, 2, 3, 4, 5, 6, 7};
    for (int i = 8; i <= limit; ++i) {
        v.push_back(i);
    }
    int sum = 0;
    for (auto it = v.begin(); it != v.end(); ++it) {
        sum += *it;
    }
    ASSERT_EQ(limit * (limit + 1) / 2, sum);

test_abort:;
}

TEST_F(libcxx, vector_move) {
    std::vector<Counter> a(3);
    std::vector<Counter> b;

    EXPECT_EQ(3, global_count);
    EXPECT_EQ(3U, a.size());
    EXPECT_EQ(0U, b.size());

    b = std::move(a);

    // Note: can't say much about the state of "a".
    EXPECT_EQ(3U, b.size());

    a = {};
    b = {};

    EXPECT_EQ(0, global_count);

test_abort:;
}

// libcxx's headers "extern template" common parameterizations of std::sort.
// These parameterizations must be explicitly instantiated inside libcxx or
// else there will be a link error.
TEST_F(libcxx, vector_sort) {
    std::vector<int> v = {2, 3, 1};
    std::sort(v.begin(), v.end());

    EXPECT_EQ(1, v[0]);
    EXPECT_EQ(2, v[1]);
    EXPECT_EQ(3, v[2]);

test_abort:;
}

// Make sure a simple use of cout can compile and run.
TEST_F(libcxx, iostream_smoke_test) {
    std::cout << "Hello, world. " << 123 << "!" << std::endl;
}

class Parent {};

class Child : Parent {};

class Stranger {};

// Do we have full C++17 support?
TEST_F(libcxx, is_base_of_v) {
    // Extra parentheses needed because templates create lexical ambiguity for
    // preprocessor.
    EXPECT_EQ(true, (std::is_base_of_v<Parent, Parent>));
    EXPECT_EQ(true, (std::is_base_of_v<Child, Child>));
    EXPECT_EQ(true, (std::is_base_of_v<Stranger, Stranger>));
    EXPECT_EQ(true, (std::is_base_of_v<Parent, Child>));
    EXPECT_EQ(false, (std::is_base_of_v<Child, Parent>));
    EXPECT_EQ(false, (std::is_base_of_v<Parent, Stranger>));
    EXPECT_EQ(false, (std::is_base_of_v<Stranger, Parent>));
    EXPECT_EQ(false, (std::is_base_of_v<Child, Stranger>));
    EXPECT_EQ(false, (std::is_base_of_v<Stranger, Child>));
}

#if defined(TRUSTY_USERSPACE)
PORT_TEST(libcxx, "com.android.libcxxtest");
#else
PORT_TEST(libcxx, "com.android.kernel.libcxxtest");
#endif
