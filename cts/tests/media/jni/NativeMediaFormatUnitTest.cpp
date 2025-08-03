/*
 * Copyright (C) 2021 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMediaFormatUnitTest"
#include <log/log.h>

#include <jni.h>
#include <media/NdkMediaFormat.h>

#include <cinttypes>
#include <map>
#include <string>

static const char story[] = {"What if after you die, God asks you: 'so how was heaven'"};
static const char dragon[] = {"e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6"};

class Rect {
public:
    int left;
    int top;
    int right;
    int bottom;

    Rect(int a, int b, int c, int d) : left{a}, top{b}, right{c}, bottom{d} {};
};

class Buffer {
public:
    char* buffer;
    size_t size;

    explicit Buffer(char* buffer = nullptr, size_t size = 0) : buffer{buffer}, size{size} {};
};

class NativeMediaFormatUnitTest {
private:
    std::map<int32_t, const char*> mInt32KeyValuePairs;
    std::map<int64_t, const char*> mInt64KeyValuePairs;
    std::map<float, const char*> mFloatKeyValuePairs;
    std::map<double, const char*> mDoubleKeyValuePairs;
    std::map<size_t, const char*> mSizeKeyValuePairs;
    std::map<const char*, const char*> mStringKeyValuePairs;
    std::map<Rect*, const char*> mWindowKeyValuePairs;
    std::map<Buffer*, const char*> mBufferKeyValuePairs;

public:
    NativeMediaFormatUnitTest();
    ~NativeMediaFormatUnitTest();

    bool validateFormatInt32(AMediaFormat* fmt, int offset = 0, bool isClear = false);
    bool validateFormatInt64(AMediaFormat* fmt, int offset = 0, bool isClear = false);
    bool validateFormatFloat(AMediaFormat* fmt, float offset = 0.0f, bool isClear = false);
    bool validateFormatDouble(AMediaFormat* fmt, double offset = 0.0, bool isClear = false);
    bool validateFormatSize(AMediaFormat* fmt, size_t offset = 0, bool isClear = false);
    bool validateFormatString(AMediaFormat* fmt, int offset = 0, bool isClear = false);
    bool validateFormatRect(AMediaFormat* fmt, int offset = 0, bool isClear = false);
    bool validateFormatBuffer(AMediaFormat* fmt, int offset = 0, bool isClear = false);
    bool validateFormat(AMediaFormat* fmt, int offset = 0, bool isClear = false);

    void configureFormatInt32(AMediaFormat* fmt, int offset = 0);
    void configureFormatInt64(AMediaFormat* fmt, int offset = 0);
    void configureFormatFloat(AMediaFormat* fmt, float offset = 0.0f);
    void configureFormatDouble(AMediaFormat* fmt, double offset = 0.0);
    void configureFormatSize(AMediaFormat* fmt, size_t offset = 0);
    void configureFormatString(AMediaFormat* fmt, int offset = 0);
    void configureFormatRect(AMediaFormat* fmt, int offset = 0);
    void configureFormatBuffer(AMediaFormat* fmt, int offset = 0);
    void configureFormat(AMediaFormat* fmt, int offset = 0);
};

NativeMediaFormatUnitTest::NativeMediaFormatUnitTest() {
    mInt32KeyValuePairs.insert({118, "elements in periodic table"});
    mInt32KeyValuePairs.insert({5778, "surface temp. of sun in kelvin"});
    mInt32KeyValuePairs.insert({8611, "k2 peak in mts"});
    mInt32KeyValuePairs.insert({72, "heart rate in bpm"});
    mInt64KeyValuePairs.insert({299792458L, "vel. of em wave in free space m/s"});
    mInt64KeyValuePairs.insert({86400L, "number of seconds in a day"});
    mInt64KeyValuePairs.insert({1520200000L, "distance of earth from the sun in km"});
    mInt64KeyValuePairs.insert({39000000L, "forest area of the world km^2"});
    mFloatKeyValuePairs.insert({22.0f / 7.0f, "pi"});
    mFloatKeyValuePairs.insert({3.6f, "not great, not terrible"});
    mFloatKeyValuePairs.insert({15.999f, "atomic weight of oxygen 8"});
    mFloatKeyValuePairs.insert({2.7182f, "Euler's number"});
    mDoubleKeyValuePairs.insert({44.0 / 7, "tau"});
    mDoubleKeyValuePairs.insert({9.80665, "g on earth m/sec^2"});
    mSizeKeyValuePairs.insert({sizeof(int64_t), "size of int64_t"});
    mSizeKeyValuePairs.insert({sizeof(wchar_t), "size of wide char"});
    mSizeKeyValuePairs.insert({sizeof(intptr_t), "size of pointer variable"});
    mSizeKeyValuePairs.insert({sizeof *this, "size of class NativeMediaFormatUnitTest"});
    mStringKeyValuePairs.insert(
            {"Discovered radium and polonium, and made huge contribution to finding treatments "
             "for cancer", "Marie Curie"});
    mStringKeyValuePairs.insert({"Sun rises in the east has zero entropy", "Shannon"});
    mWindowKeyValuePairs.insert({new Rect{12, 15, 12, 21}, "trapezoid"});
    mWindowKeyValuePairs.insert({new Rect{12, 12, 12, 12}, "rhombus"});
    mWindowKeyValuePairs.insert({new Rect{12, 15, 12, 15}, "rectangle"});
    mWindowKeyValuePairs.insert({new Rect{12, 15, 18, 21}, "quadrilateral"});
    mBufferKeyValuePairs.insert({new Buffer(), "empty buffer"});
    size_t sz = strlen(story) + 1;
    auto* quote = new Buffer{new char[sz], sz};
    memcpy(quote->buffer, story, sz);
    mBufferKeyValuePairs.insert({quote, "one line story"});
    sz = strlen(dragon) + 1;
    auto* chess = new Buffer(new char[sz], sz);
    memcpy(chess->buffer, dragon, sz);
    mBufferKeyValuePairs.insert({chess, "sicilian dragon"});
}

NativeMediaFormatUnitTest::~NativeMediaFormatUnitTest() {
    for (auto it : mWindowKeyValuePairs) {
        delete it.first;
    }
    for (auto it : mBufferKeyValuePairs) {
        delete[] it.first->buffer;
        delete it.first;
    }
}

bool NativeMediaFormatUnitTest::validateFormatInt32(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = true;
    int32_t val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mInt32KeyValuePairs) {
        bool result = AMediaFormat_getInt32(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (val != it.first + offset) {
                ALOGE("MediaFormat Value for Key %s is not %d but %d", it.second, it.first + offset,
                      val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getInt32(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatInt64(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = true;
    int64_t val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mInt64KeyValuePairs) {
        bool result = AMediaFormat_getInt64(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (val != it.first + offset) {
                ALOGE("MediaFormat Value for Key %s is not %" PRId64 "but %" PRId64, it.second,
                      it.first + offset, val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getInt64(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatFloat(AMediaFormat* fmt, float offset, bool isClear) {
    bool status = true;
    float val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mFloatKeyValuePairs) {
        bool result = AMediaFormat_getFloat(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (val != it.first + offset) {
                ALOGE("MediaFormat Value for Key %s is not %f but %f", it.second, it.first + offset,
                      val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getFloat(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatDouble(AMediaFormat* fmt, double offset,
                                                     bool isClear) {
    bool status = true;
    double val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mDoubleKeyValuePairs) {
        bool result = AMediaFormat_getDouble(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (val != it.first + offset) {
                ALOGE("MediaFormat Value for Key %s is not %f but %f", it.second, it.first + offset,
                      val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getDouble(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatSize(AMediaFormat* fmt, size_t offset, bool isClear) {
    bool status = true;
    size_t val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mSizeKeyValuePairs) {
        bool result = AMediaFormat_getSize(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (val != it.first + offset) {
                ALOGE("MediaFormat Value for Key %s is not %zu but %zu", it.second,
                      it.first + offset, val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getSize(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatString(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = true;
    const char* val;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mStringKeyValuePairs) {
        bool result = AMediaFormat_getString(fmt, it.second, &val);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            std::string s = it.first + std::to_string(offset);
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (s != val) {
                ALOGE("MediaFormat Value for Key %s is not %s but %s", it.second, s.c_str(), val);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, s.c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, s.c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getString(fmt, "hello world", &val)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatRect(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = true;
    int left, top, right, bottom;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mWindowKeyValuePairs) {
        bool result = AMediaFormat_getRect(fmt, it.second, &left, &top, &right, &bottom);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (left != it.first->left + offset || top != it.first->top + offset ||
                       right != it.first->right + offset || bottom != it.first->bottom + offset) {
                ALOGE("MediaFormat Value for Key %s is not (%d, %d, %d, %d)) but (%d, %d, %d, %d)",
                      it.second, it.first->left, it.first->top, it.first->right, it.first->bottom,
                      left, top, right, bottom);
                status &= false;
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first->left + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first->left + offset).c_str());
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first->top + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first->top + offset).c_str());
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first->right + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first->right + offset).c_str());
                status &= false;
            }
            if (strstr(toString, std::to_string(it.first->bottom + offset).c_str()) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString,
                      std::to_string(it.first->bottom + offset).c_str());
                status &= false;
            }
        }
    }
    if (AMediaFormat_getRect(fmt, "hello world", &left, &top, &right, &bottom)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormatBuffer(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = true;
    void* data;
    size_t size;
    const char* toString = AMediaFormat_toString(fmt);
    for (auto it : mBufferKeyValuePairs) {
        bool result = AMediaFormat_getBuffer(fmt, it.second, &data, &size);
        if (isClear) {
            if (result) {
                ALOGE("MediaFormat is not expected to contain Key %s", it.second);
                status &= false;
            }
        } else {
            if (!result) {
                ALOGE("MediaFormat doesn't contain key %s", it.second);
                status &= false;
            } else if (size != (offset == 0 ? it.first->size : it.first->size / 2)) {
                ALOGE("MediaFormat Value for Key %s is not %zu but %zu", it.second,
                      (offset == 0 ? it.first->size : it.first->size / 2), size);
                status &= false;
            } else {
                if (it.first->buffer != nullptr &&
                    memcmp(data, it.first->buffer + it.first->size - size, size) != 0) {
                    ALOGE("MediaFormat Value for Key %s is not %s but %s {%zu}", it.second,
                          it.first->buffer + it.first->size - size, (char*)data, size);
                    status &= false;
                }
            }
            if (strstr(toString, it.second) == nullptr) {
                ALOGE("AMediaFormat_toString() of fmt %s doesn't contains %s", toString, it.second);
                status &= false;
            }
        }
    }
    if (AMediaFormat_getBuffer(fmt, "hello world", &data, &size)) {
        ALOGE("MediaFormat has value for key 'hello world' ");
        status &= false;
    }
    return status;
}

bool NativeMediaFormatUnitTest::validateFormat(AMediaFormat* fmt, int offset, bool isClear) {
    bool status = validateFormatInt32(fmt, offset, isClear);
    status &= validateFormatInt64(fmt, offset, isClear);
    status &= validateFormatFloat(fmt, offset, isClear);
    status &= validateFormatDouble(fmt, offset, isClear);
    status &= validateFormatSize(fmt, offset, isClear);
    status &= validateFormatString(fmt, offset, isClear);
    status &= validateFormatRect(fmt, offset, isClear);
    status &= validateFormatBuffer(fmt, offset, isClear);
    return status;
}

void NativeMediaFormatUnitTest::configureFormatInt32(AMediaFormat* fmt, int offset) {
    for (auto it : mInt32KeyValuePairs) {
        AMediaFormat_setInt32(fmt, it.second, it.first + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatInt64(AMediaFormat* fmt, int offset) {
    for (auto it : mInt64KeyValuePairs) {
        AMediaFormat_setInt64(fmt, it.second, it.first + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatFloat(AMediaFormat* fmt, float offset) {
    for (auto it : mFloatKeyValuePairs) {
        AMediaFormat_setFloat(fmt, it.second, it.first + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatDouble(AMediaFormat* fmt, double offset) {
    for (auto it : mDoubleKeyValuePairs) {
        AMediaFormat_setDouble(fmt, it.second, it.first + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatSize(AMediaFormat* fmt, size_t offset) {
    for (auto it : mSizeKeyValuePairs) {
        AMediaFormat_setSize(fmt, it.second, it.first + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatString(AMediaFormat* fmt, int offset) {
    for (auto it : mStringKeyValuePairs) {
        std::string s1 = it.first + std::to_string(offset);
        AMediaFormat_setString(fmt, it.second, s1.c_str());
    }
}

void NativeMediaFormatUnitTest::configureFormatRect(AMediaFormat* fmt, int offset) {
    for (auto it : mWindowKeyValuePairs) {
        AMediaFormat_setRect(fmt, it.second, it.first->left + offset, it.first->top + offset,
                             it.first->right + offset, it.first->bottom + offset);
    }
}

void NativeMediaFormatUnitTest::configureFormatBuffer(AMediaFormat* fmt, int offset) {
    for (auto it : mBufferKeyValuePairs) {
        int sz = offset == 0 ? it.first->size : it.first->size / 2;
        AMediaFormat_setBuffer(fmt, it.second, it.first->buffer + it.first->size - sz, sz);
    }
}

void NativeMediaFormatUnitTest::configureFormat(AMediaFormat* fmt, int offset) {
    configureFormatInt32(fmt, offset);
    configureFormatInt64(fmt, offset);
    configureFormatFloat(fmt, offset);
    configureFormatDouble(fmt, offset);
    configureFormatSize(fmt, offset);
    configureFormatString(fmt, offset);
    configureFormatRect(fmt, offset);
    configureFormatBuffer(fmt, offset);
}

// 1. configure format with default values and validate the same
// 2. copy configured format to an empty format and validate the copied format
// 3. overwrite copied format with default + offset values and validate the updated format
// 4. overwrite updated format with default values using AMediaFormat_copy API and validate the same
// 5. clear mediaformat and validate if keys are not present
static bool testMediaFormatAllNative() {
    auto* nmf = new NativeMediaFormatUnitTest();
    AMediaFormat* fmtOrig = AMediaFormat_new();
    AMediaFormat* fmtDup = AMediaFormat_new();
    const int offset = 123;

    nmf->configureFormat(fmtOrig);
    bool status = nmf->validateFormat(fmtOrig);

    AMediaFormat_copy(fmtDup, fmtOrig);
    status &= nmf->validateFormat(fmtDup);

    nmf->configureFormat(fmtDup, offset);
    status &= nmf->validateFormat(fmtDup, offset);

    AMediaFormat_copy(fmtDup, fmtOrig);
    status &= nmf->validateFormat(fmtDup);

    AMediaFormat_clear(fmtDup);
    status &= nmf->validateFormat(fmtDup, offset, true);

    AMediaFormat_delete(fmtOrig);
    AMediaFormat_delete(fmtDup);
    delete nmf;

    return status;
}

// 1. configure format with default values and validate the same
// 2. copy configured format to an empty format and validate the copied format
// 3. overwrite copied format with default + offset values and validate the updated format
// 4. overwrite updated format with default values using AMediaFormat_copy API and validate the same
#define testMediaFormatfuncNative(func)                            \
    static bool testMediaFormat##func##Native() {                  \
        auto* nmf = new NativeMediaFormatUnitTest();               \
        AMediaFormat* fmtOrig = AMediaFormat_new();                \
        AMediaFormat* fmtDup = AMediaFormat_new();                 \
        const int offset = 12345;                                  \
                                                                   \
        nmf->configureFormat##func(fmtOrig);                       \
        bool status = nmf->validateFormat##func(fmtOrig);          \
                                                                   \
        AMediaFormat_copy(fmtDup, fmtOrig);                        \
        status &= nmf->validateFormat##func(fmtDup);               \
                                                                   \
        nmf->configureFormat##func(fmtDup, offset);                \
        status &= nmf->validateFormat##func(fmtDup, offset);       \
                                                                   \
        AMediaFormat_copy(fmtDup, fmtOrig);                        \
        status &= nmf->validateFormat##func(fmtDup);               \
                                                                   \
        AMediaFormat_clear(fmtDup);                                \
        status &= nmf->validateFormat##func(fmtDup, offset, true); \
        AMediaFormat_delete(fmtOrig);                              \
        AMediaFormat_delete(fmtDup);                               \
        delete nmf;                                                \
        return status;                                             \
    }

testMediaFormatfuncNative(Int32)

testMediaFormatfuncNative(Int64)

testMediaFormatfuncNative(Float)

testMediaFormatfuncNative(Double)

testMediaFormatfuncNative(Size)

testMediaFormatfuncNative(String)

testMediaFormatfuncNative(Rect)

testMediaFormatfuncNative(Buffer)

#define nativeTestMediaFormatfunc(func)                                \
    static jboolean nativeTestMediaFormat##func(JNIEnv*, jobject) {    \
        return static_cast<jboolean>(testMediaFormat##func##Native()); \
    }

//@ApiTest(apis = {"AMediaFormat_getInt32", "AMediaFormat_setInt32",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Int32)

//@ApiTest(apis = {"AMediaFormat_getInt64", "AMediaFormat_setInt64",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Int64)

//@ApiTest(apis = {"AMediaFormat_getFloat", "AMediaFormat_setFloat",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Float)

//@ApiTest(apis = {"AMediaFormat_getDouble", "AMediaFormat_setDouble",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Double)

//@ApiTest(apis = {"AMediaFormat_getSize", "AMediaFormat_setSize",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Size)

//@ApiTest(apis = {"AMediaFormat_getString", "AMediaFormat_setString",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(String)

//@ApiTest(apis = {"AMediaFormat_getRect", "AMediaFormat_setRect",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Rect)

//@ApiTest(apis = {"AMediaFormat_getBuffer", "AMediaFormat_setBuffer",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(Buffer)

//@ApiTest(apis = {"AMediaFormat_getInt32", "AMediaFormat_setInt32",
//                 "AMediaFormat_getInt64", "AMediaFormat_setInt64",
//                 "AMediaFormat_getFloat", "AMediaFormat_setFloat",
//                 "AMediaFormat_getDouble", "AMediaFormat_setDouble",
//                 "AMediaFormat_getString", "AMediaFormat_setString",
//                 "AMediaFormat_getSize", "AMediaFormat_setSize",
//                 "AMediaFormat_getRect", "AMediaFormat_setRect",
//                 "AMediaFormat_getBuffer", "AMediaFormat_setBuffer",
//                 "AMediaFormat_copy", "AMediaFormat_clear", "AMediaFormat_toString",
//                 "AMediaFormat_new", "AMediaFormat_delete"})
nativeTestMediaFormatfunc(All)

int registerAndroidMediaV2CtsMediaFormatUnitTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestMediaFormatInt32", "()Z", (void*)nativeTestMediaFormatInt32},
            {"nativeTestMediaFormatInt64", "()Z", (void*)nativeTestMediaFormatInt64},
            {"nativeTestMediaFormatFloat", "()Z", (void*)nativeTestMediaFormatFloat},
            {"nativeTestMediaFormatDouble", "()Z", (void*)nativeTestMediaFormatDouble},
            {"nativeTestMediaFormatSize", "()Z", (void*)nativeTestMediaFormatSize},
            {"nativeTestMediaFormatString", "()Z", (void*)nativeTestMediaFormatString},
            {"nativeTestMediaFormatRect", "()Z", (void*)nativeTestMediaFormatRect},
            {"nativeTestMediaFormatBuffer", "()Z", (void*)nativeTestMediaFormatBuffer},
            {"nativeTestMediaFormatAll", "()Z", (void*)nativeTestMediaFormatAll},
    };
    jclass c = env->FindClass("android/mediav2/cts/MediaFormatUnitTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMediaFormatUnitTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
