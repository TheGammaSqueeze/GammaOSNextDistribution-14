/*
 * Copyright (C) 2018 The Android Open Source Project
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
#define LOG_TAG "Cts-NdkBinderTest"

#include <aidl/test_package/BnEmpty.h>
#include <aidl/test_package/BpCompatTest.h>
#include <aidl/test_package/BpTest.h>
#include <aidl/test_package/ByteEnum.h>
#include <aidl/test_package/ExtendableParcelable.h>
#include <aidl/test_package/FixedSize.h>
#include <aidl/test_package/FixedSizeUnion.h>
#include <aidl/test_package/Foo.h>
#include <aidl/test_package/IntEnum.h>
#include <aidl/test_package/LongEnum.h>
#include <aidl/test_package/RegularPolygon.h>
#include <android/binder_ibinder_jni.h>
#include <android/log.h>
#include <android/persistable_bundle_aidl.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <type_traits>

#include "itest_impl.h"
#include "utilities.h"

using ::aidl::android::os::PersistableBundle;
using ::aidl::test_package::Bar;
using ::aidl::test_package::BpTest;
using ::aidl::test_package::ByteEnum;
using ::aidl::test_package::ExtendableParcelable;
using ::aidl::test_package::FixedSize;
using ::aidl::test_package::FixedSizeUnion;
using ::aidl::test_package::Foo;
using ::aidl::test_package::GenericBar;
using ::aidl::test_package::ICompatTest;
using ::aidl::test_package::IntEnum;
using ::aidl::test_package::ITest;
using ::aidl::test_package::LongEnum;
using ::aidl::test_package::MyExt;
using ::aidl::test_package::RegularPolygon;
using ::ndk::ScopedAStatus;
using ::ndk::ScopedFileDescriptor;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;

// This client is built for 32 and 64-bit targets. The size of FixedSize must remain the same.
static_assert(sizeof(FixedSize) == 16);
static_assert(offsetof(FixedSize, a) == 0);
static_assert(offsetof(FixedSize, b) == 8);

static_assert(sizeof(FixedSizeUnion) == 16);  // tag(uint8_t), value(union of {int32_t, long64_t})
static_assert(alignof(FixedSizeUnion) == 8);

static_assert(FixedSizeUnion::fixed_size::value);

class MyEmpty : public ::aidl::test_package::BnEmpty {};
class YourEmpty : public ::aidl::test_package::BnEmpty {};

// AIDL tests which are independent of the service
class NdkBinderTest_AidlLocal : public NdkBinderTest {};

TEST_F(NdkBinderTest_AidlLocal, FromBinder) {
  std::shared_ptr<MyTest> test = SharedRefBase::make<MyTest>();
  SpAIBinder binder = test->asBinder();
  EXPECT_EQ(test, ITest::fromBinder(binder));

  EXPECT_FALSE(test->isRemote());
}

TEST_F(NdkBinderTest_AidlLocal, ConfirmFixedSizeTrue) {
  bool res = std::is_same<FixedSize::fixed_size, std::true_type>::value;
  EXPECT_EQ(res, true);
}

TEST_F(NdkBinderTest_AidlLocal, ConfirmFixedSizeFalse) {
  bool res = std::is_same<RegularPolygon::fixed_size, std::true_type>::value;
  EXPECT_EQ(res, false);
}

struct Params {
  std::shared_ptr<ITest> iface;
  bool shouldBeRemote;
  bool shouldBeWrapped;
  std::string expectedName;
  bool shouldBeOld;
};

#define iface GetParam().iface
#define shouldBeRemote GetParam().shouldBeRemote
#define shouldBeWrapped GetParam().shouldBeWrapped

// AIDL tests which run on each type of service (local C++, local Java, remote C++, remote Java,
// etc..)
class NdkBinderTest_Aidl : public NdkBinderTest,
                           public ::testing::WithParamInterface<Params> {};

TEST_P(NdkBinderTest_Aidl, GotTest) { ASSERT_NE(nullptr, iface); }

TEST_P(NdkBinderTest_Aidl, SanityCheckSource) {
  std::string name;
  ASSERT_OK(iface->GetName(&name));
  EXPECT_EQ(GetParam().expectedName, name);
}

TEST_P(NdkBinderTest_Aidl, Remoteness) {
  ASSERT_EQ(shouldBeRemote, iface->isRemote());
}

TEST_P(NdkBinderTest_Aidl, UseBinder) {
  ASSERT_EQ(STATUS_OK, AIBinder_ping(iface->asBinder().get()));
}

TEST_P(NdkBinderTest_Aidl, GetExtension) {
  SpAIBinder ext;
  ASSERT_EQ(STATUS_OK, AIBinder_getExtension(iface->asBinder().get(), ext.getR()));

  // TODO(b/139325468): add support in Java as well
  if (GetParam().expectedName == "CPP") {
    EXPECT_EQ(STATUS_OK, AIBinder_ping(ext.get()));
  } else {
    ASSERT_EQ(nullptr, ext.get());
  }
}

bool ReadFdToString(int fd, std::string* content) {
  char buf[64];
  ssize_t n;
  while ((n = TEMP_FAILURE_RETRY(read(fd, &buf[0], sizeof(buf)))) > 0) {
    content->append(buf, n);
  }
  return (n == 0) ? true : false;
}

std::string dumpToString(std::shared_ptr<ITest> itest, std::vector<const char*> args) {
  int fd[2] = {-1, -1};
  EXPECT_EQ(0, socketpair(AF_UNIX, SOCK_STREAM, 0, fd));

  EXPECT_OK(itest->dump(fd[0], args.data(), args.size()));
  close(fd[0]);

  std::string ret;
  EXPECT_TRUE(ReadFdToString(fd[1], &ret));

  close(fd[1]);
  return ret;
}

auto getCompatTest(std::shared_ptr<ITest> itest) {
  SpAIBinder binder;
  itest->getICompatTest(&binder);
  return ICompatTest::fromBinder(binder);
}

TEST_P(NdkBinderTest_Aidl, UseDump) {
  std::string name;
  EXPECT_OK(iface->GetName(&name));
  if (name == "JAVA" && !iface->isRemote()) {
    // TODO(b/127361166): GTEST_SKIP is considered a failure, would prefer to use that here
    // TODO(b/127339049): JavaBBinder doesn't implement dump
    return;
  }

  EXPECT_EQ("", dumpToString(iface, {}));
  EXPECT_EQ("", dumpToString(iface, {"", ""}));
  EXPECT_EQ("Hello World!", dumpToString(iface, {"Hello ", "World!"}));
  EXPECT_EQ("ABC", dumpToString(iface, {"A", "B", "C"}));
}

TEST_P(NdkBinderTest_Aidl, Trivial) {
  ASSERT_OK(iface->TestVoidReturn());

  if (shouldBeWrapped) {
    ASSERT_OK(iface->TestOneway());
  } else {
    ASSERT_EQ(STATUS_UNKNOWN_ERROR, AStatus_getStatus(iface->TestOneway().get()));
  }
}

TEST_P(NdkBinderTest_Aidl, CallingInfo) {
  EXPECT_OK(iface->CacheCallingInfoFromOneway());
  int32_t res;

  EXPECT_OK(iface->GiveMeMyCallingPid(&res));
  EXPECT_EQ(getpid(), res);

  EXPECT_OK(iface->GiveMeMyCallingUid(&res));
  EXPECT_EQ(getuid(), res);

  EXPECT_OK(iface->GiveMeMyCallingPidFromOneway(&res));
  if (shouldBeRemote) {
    // PID is hidden from oneway calls
    EXPECT_EQ(0, res);
  } else {
    EXPECT_EQ(getpid(), res);
  }

  EXPECT_OK(iface->GiveMeMyCallingUidFromOneway(&res));
  EXPECT_EQ(getuid(), res);
}

TEST_P(NdkBinderTest_Aidl, ConstantsInInterface) {
  ASSERT_EQ(0, ITest::kZero);
  ASSERT_EQ(1, ITest::kOne);
  ASSERT_EQ(0xffffffff, ITest::kOnes);
  ASSERT_EQ(1, ITest::kByteOne);
  ASSERT_EQ(0xffffffffffffffff, ITest::kLongOnes);
  ASSERT_EQ(std::string(""), ITest::kEmpty);
  ASSERT_EQ(std::string("foo"), ITest::kFoo);
}

TEST_P(NdkBinderTest_Aidl, ConstantsInParcelable) {
  ASSERT_EQ(0, Foo::kZero);
  ASSERT_EQ(1, Foo::kOne);
  ASSERT_EQ(0xffffffff, Foo::kOnes);
  ASSERT_EQ(1, Foo::kByteOne);
  ASSERT_EQ(0xffffffffffffffff, Foo::kLongOnes);
  ASSERT_EQ(std::string(""), Foo::kEmpty);
  ASSERT_EQ(std::string("foo"), Foo::kFoo);
}

TEST_P(NdkBinderTest_Aidl, ConstantsInUnion) {
  ASSERT_EQ(0, SimpleUnion::kZero);
  ASSERT_EQ(1, SimpleUnion::kOne);
  ASSERT_EQ(0xffffffff, SimpleUnion::kOnes);
  ASSERT_EQ(1, SimpleUnion::kByteOne);
  ASSERT_EQ(0xffffffffffffffff, SimpleUnion::kLongOnes);
  ASSERT_EQ(std::string(""), SimpleUnion::kEmpty);
  ASSERT_EQ(std::string("foo"), SimpleUnion::kFoo);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveInt) {
  int32_t out;
  ASSERT_OK(iface->RepeatInt(3, &out));
  EXPECT_EQ(3, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveLong) {
  int64_t out;
  ASSERT_OK(iface->RepeatLong(3, &out));
  EXPECT_EQ(3, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveFloat) {
  float out;
  ASSERT_OK(iface->RepeatFloat(2.0f, &out));
  EXPECT_EQ(2.0f, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveDouble) {
  double out;
  ASSERT_OK(iface->RepeatDouble(3.0, &out));
  EXPECT_EQ(3.0, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveBoolean) {
  bool out;
  ASSERT_OK(iface->RepeatBoolean(true, &out));
  EXPECT_EQ(true, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveChar) {
  char16_t out;
  ASSERT_OK(iface->RepeatChar(L'@', &out));
  EXPECT_EQ(L'@', out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveByte) {
  int8_t out;
  ASSERT_OK(iface->RepeatByte(3, &out));
  EXPECT_EQ(3, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveByteEnum) {
  ByteEnum out;
  ASSERT_OK(iface->RepeatByteEnum(ByteEnum::FOO, &out));
  EXPECT_EQ(ByteEnum::FOO, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveIntEnum) {
  IntEnum out;
  ASSERT_OK(iface->RepeatIntEnum(IntEnum::FOO, &out));
  EXPECT_EQ(IntEnum::FOO, out);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitiveLongEnum) {
  LongEnum out;
  ASSERT_OK(iface->RepeatLongEnum(LongEnum::FOO, &out));
  EXPECT_EQ(LongEnum::FOO, out);
}

TEST_P(NdkBinderTest_Aidl, EnumToString) {
  EXPECT_EQ(toString(ByteEnum::FOO), "FOO");
  EXPECT_EQ(toString(IntEnum::BAR), "BAR");
  EXPECT_EQ(toString(LongEnum::FOO), "FOO");

  EXPECT_EQ(toString(static_cast<IntEnum>(-1)), "-1");
}

TEST_P(NdkBinderTest_Aidl, EnumValues) {
  auto range = ::ndk::enum_range<ByteEnum>();
  auto iter = range.begin();
  EXPECT_EQ(ByteEnum::FOO, *iter++);
  EXPECT_EQ(ByteEnum::BAR, *iter++);
  EXPECT_EQ(range.end(), iter);
}

TEST_P(NdkBinderTest_Aidl, RepeatBinder) {
  SpAIBinder binder = iface->asBinder();
  SpAIBinder ret;

  ASSERT_OK(iface->RepeatBinder(binder, &ret));
  EXPECT_EQ(binder.get(), ret.get());

  if (shouldBeWrapped) {
    ndk::ScopedAStatus status = iface->RepeatBinder(nullptr, &ret);
    ASSERT_EQ(STATUS_UNEXPECTED_NULL, AStatus_getStatus(status.get()));
  } else {
    ASSERT_OK(iface->RepeatBinder(nullptr, &ret));
    EXPECT_EQ(nullptr, ret.get());
  }

  ASSERT_OK(iface->RepeatNullableBinder(binder, &ret));
  EXPECT_EQ(binder.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableBinder(nullptr, &ret));
  EXPECT_EQ(nullptr, ret.get());
}

TEST_P(NdkBinderTest_Aidl, RepeatInterface) {
  std::shared_ptr<IEmpty> empty = SharedRefBase::make<MyEmpty>();

  std::shared_ptr<IEmpty> ret;
  ASSERT_OK(iface->RepeatInterface(empty, &ret));
  EXPECT_EQ(empty.get(), ret.get());

  // b/210547999
  // interface writes are always nullable in AIDL C++ (but reads are not
  // nullable by default). However, the NDK backend follows the Java behavior
  // and always allows interfaces to be nullable (for reads and writes).
  ASSERT_OK(iface->RepeatInterface(nullptr, &ret));
  EXPECT_EQ(nullptr, ret.get());

  ASSERT_OK(iface->RepeatNullableInterface(empty, &ret));
  EXPECT_EQ(empty.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableInterface(nullptr, &ret));
  EXPECT_EQ(nullptr, ret.get());
}

static void checkInOut(const ScopedFileDescriptor& inFd,
                       const ScopedFileDescriptor& outFd) {
  static const std::string kContent = "asdf";

  ASSERT_EQ(static_cast<int>(kContent.size()),
            write(inFd.get(), kContent.data(), kContent.size()));

  std::string out;
  out.resize(kContent.size());
  ASSERT_EQ(static_cast<int>(kContent.size()),
            read(outFd.get(), &out[0], kContent.size()));

  EXPECT_EQ(kContent, out);
}

static void checkFdRepeat(
    const std::shared_ptr<ITest>& test,
    ScopedAStatus (ITest::*repeatFd)(const ScopedFileDescriptor&,
                                     ScopedFileDescriptor*)) {
  int fds[2];

  while (pipe(fds) == -1 && errno == EAGAIN)
    ;

  ScopedFileDescriptor readFd(fds[0]);
  ScopedFileDescriptor writeFd(fds[1]);

  ScopedFileDescriptor readOutFd;
  ASSERT_OK((test.get()->*repeatFd)(readFd, &readOutFd));

  checkInOut(writeFd, readOutFd);
}

TEST_P(NdkBinderTest_Aidl, RepeatFdArray) {
  int fds[2];

  while (pipe(fds) == -1 && errno == EAGAIN)
    ;
  std::vector<ScopedFileDescriptor> sfds;
  sfds.emplace_back(fds[0]);
  sfds.emplace_back(fds[1]);

  std::vector<ScopedFileDescriptor> sfds_out1;
  sfds_out1.resize(sfds.size());
  std::vector<ScopedFileDescriptor> sfds_out2;

  ASSERT_OK((iface->RepeatFdArray(sfds, &sfds_out1, &sfds_out2)));

  // sfds <-> sfds_out1
  checkInOut(sfds[1], sfds_out1[0]);
  checkInOut(sfds_out1[1], sfds[0]);

  // sfds_out1 <-> sfds_out2
  checkInOut(sfds_out1[1], sfds_out2[0]);
  checkInOut(sfds_out2[1], sfds_out1[0]);

  // sfds <-> sfds_out2
  checkInOut(sfds[1], sfds_out2[0]);
  checkInOut(sfds_out2[1], sfds[0]);
}

TEST_P(NdkBinderTest_Aidl, RepeatFd) { checkFdRepeat(iface, &ITest::RepeatFd); }

TEST_P(NdkBinderTest_Aidl, RepeatFdNull) {
  ScopedFileDescriptor fd;
  // FD is different from most types because the standard type used to represent
  // it can also contain a null value (this is why many other types don't have
  // 'null' tests for the non-@nullable Repeat* functions).
  //
  // Even worse, these are default initialized to this value, so it's a pretty
  // common error:
  EXPECT_EQ(fd.get(), -1);
  ScopedFileDescriptor out;

  if (shouldBeWrapped) {
    ASSERT_EQ(STATUS_UNEXPECTED_NULL, AStatus_getStatus(iface->RepeatFd(fd, &out).get()));
  } else {
    // another in/out-process difference
    ASSERT_OK(iface->RepeatFd(fd, &out));
  }
}

TEST_P(NdkBinderTest_Aidl, RepeatNullableFd) {
  checkFdRepeat(iface, &ITest::RepeatNullableFd);

  ScopedFileDescriptor in;
  EXPECT_EQ(-1, in.get());

  ScopedFileDescriptor out;
  ASSERT_OK(iface->RepeatNullableFd(in, &out));

  EXPECT_EQ(-1, out.get());
}

TEST_P(NdkBinderTest_Aidl, RepeatString) {
  std::string res;

  EXPECT_OK(iface->RepeatString("", &res));
  EXPECT_EQ("", res);

  EXPECT_OK(iface->RepeatString("a", &res));
  EXPECT_EQ("a", res);

  EXPECT_OK(iface->RepeatString("say what?", &res));
  EXPECT_EQ("say what?", res);

  std::string stringWithNulls = "asdf";
  stringWithNulls[1] = '\0';

  EXPECT_OK(iface->RepeatString(stringWithNulls, &res));
  EXPECT_EQ(stringWithNulls, res);
}

TEST_P(NdkBinderTest_Aidl, RepeatNullableString) {
  std::optional<std::string> res;

  EXPECT_OK(iface->RepeatNullableString(std::nullopt, &res));
  EXPECT_EQ(std::nullopt, res);

  EXPECT_OK(iface->RepeatNullableString("", &res));
  EXPECT_EQ("", *res);

  EXPECT_OK(iface->RepeatNullableString("a", &res));
  EXPECT_EQ("a", *res);

  EXPECT_OK(iface->RepeatNullableString("say what?", &res));
  EXPECT_EQ("say what?", *res);
}

TEST_P(NdkBinderTest_Aidl, ParcelableOrder) {
  RegularPolygon p1 = {"A", 1, 1.0f};

  // tests on self
  EXPECT_EQ(p1, p1);
  EXPECT_LE(p1, p1);
  EXPECT_GE(p1, p1);
  EXPECT_FALSE(p1 < p1);
  EXPECT_FALSE(p1 > p1);

  RegularPolygon p2 = {"A", 2, 1.0f};
  RegularPolygon p3 = {"B", 1, 1.0f};
  for (const auto& bigger : {p2, p3}) {
    EXPECT_FALSE(p1 == bigger);
    EXPECT_LE(p1, bigger);
    EXPECT_GE(bigger, p1);
    EXPECT_LT(p1, bigger);
    EXPECT_GT(bigger, p1);
  }
}

TEST_P(NdkBinderTest_Aidl, ParcelableDefaults) {
  RegularPolygon polygon;

  EXPECT_EQ("square", polygon.name);
  EXPECT_EQ(4, polygon.numSides);
  EXPECT_EQ(1.0f, polygon.sideLength);
}

TEST_P(NdkBinderTest_Aidl, RepeatPolygon) {
  RegularPolygon defaultPolygon = {"hexagon", 6, 2.0f};
  RegularPolygon outputPolygon;
  ASSERT_OK(iface->RepeatPolygon(defaultPolygon, &outputPolygon));
  EXPECT_EQ(defaultPolygon, outputPolygon);
}

TEST_P(NdkBinderTest_Aidl, RepeatNullNullablePolygon) {
  std::optional<RegularPolygon> defaultPolygon;
  std::optional<RegularPolygon> outputPolygon;
  ASSERT_OK(iface->RepeatNullablePolygon(defaultPolygon, &outputPolygon));
  EXPECT_EQ(defaultPolygon, outputPolygon);
}

TEST_P(NdkBinderTest_Aidl, RepeatPresentNullablePolygon) {
  std::optional<RegularPolygon> defaultPolygon =
      std::optional<RegularPolygon>({"septagon", 7, 3.0f});
  std::optional<RegularPolygon> outputPolygon;
  ASSERT_OK(iface->RepeatNullablePolygon(defaultPolygon, &outputPolygon));
  EXPECT_EQ(defaultPolygon, outputPolygon);
}

TEST_P(NdkBinderTest_Aidl, RepeatDefaultPersistableBundle) {
  PersistableBundle defaultPBundle;
  PersistableBundle outPBundle;
  ASSERT_OK(iface->RepeatPersistableBundle(defaultPBundle, &outPBundle));
  // The == operator checks the underlying pointers which should be different
  EXPECT_NE(defaultPBundle, outPBundle);
  // The deepEquals function checks the contents of the bundle, which should be
  // the same
  EXPECT_TRUE(defaultPBundle.deepEquals(outPBundle));
}

const bool kBoolVal = true;
const int32_t kIntVal = 11111;
const int64_t kLongVal = 12345;
const double kDoubleVal = 54321;
const std::string kStringVal = "cool";
const std::vector<bool> kBoolVVal = {true, false, true};
const std::vector<int32_t> kIntVVal = {1111, -2222, 3333};
const std::vector<int64_t> kLongVVal = {11111, -22222, 33333};
const std::vector<double> kDoubleVVal = {111111, -222222, 333333};
const std::vector<std::string> kStringVVal = {"hello", "monkey", "!"};

TEST_P(NdkBinderTest_Aidl, RepeatTypesPersistableBundle) {
  PersistableBundle inPBundle;
  PersistableBundle outPBundle;
  // put all supported types && verify
  inPBundle.putBoolean("bool", kBoolVal);
  inPBundle.putInt("int", kIntVal);
  inPBundle.putLong("long", kLongVal);
  inPBundle.putDouble("double", kDoubleVal);
  inPBundle.putString("string", kStringVal);
  inPBundle.putBooleanVector("boolv", kBoolVVal);
  inPBundle.putIntVector("intv", kIntVVal);
  inPBundle.putLongVector("longv", kLongVVal);
  inPBundle.putDoubleVector("doublev", kDoubleVVal);
  inPBundle.putStringVector("stringv", kStringVVal);
  PersistableBundle innerBundle;
  innerBundle.putBoolean("bool", kBoolVal);
  innerBundle.putInt("int", kIntVal);
  inPBundle.putPersistableBundle("pbundle", innerBundle);
  bool outBool = false;
  int32_t outInt = 0;
  int64_t outLong = 0;
  double outDouble = 0;
  std::string outString = std::string();
  std::vector<bool> outBoolV = std::vector<bool>();
  std::vector<int32_t> outIntV = std::vector<int32_t>();
  std::vector<int64_t> outLongV = std::vector<int64_t>();
  std::vector<double> outDoubleV = std::vector<double>();
  std::vector<std::string> outStringV = std::vector<std::string>();
  PersistableBundle outInnerBundle;
  EXPECT_TRUE(inPBundle.getBoolean("bool", &outBool));
  EXPECT_EQ(outBool, kBoolVal);
  EXPECT_TRUE(inPBundle.getInt("int", &outInt));
  EXPECT_EQ(outInt, kIntVal);
  EXPECT_TRUE(inPBundle.getLong("long", &outLong));
  EXPECT_EQ(outLong, kLongVal);
  EXPECT_TRUE(inPBundle.getDouble("double", &outDouble));
  EXPECT_EQ(outDouble, kDoubleVal);
  EXPECT_TRUE(inPBundle.getString("string", &outString));
  EXPECT_EQ(outString, kStringVal);
  EXPECT_TRUE(inPBundle.getBooleanVector("boolv", &outBoolV));
  EXPECT_EQ(outBoolV, kBoolVVal);
  EXPECT_TRUE(inPBundle.getIntVector("intv", &outIntV));
  EXPECT_EQ(outIntV, kIntVVal);
  EXPECT_TRUE(inPBundle.getLongVector("longv", &outLongV));
  EXPECT_EQ(outLongV, kLongVVal);
  EXPECT_TRUE(inPBundle.getDoubleVector("doublev", &outDoubleV));
  EXPECT_EQ(outDoubleV, kDoubleVVal);
  EXPECT_TRUE(inPBundle.getStringVector("stringv", &outStringV));
  EXPECT_EQ(outStringV, kStringVVal);
  EXPECT_TRUE(inPBundle.getPersistableBundle("pbundle", &outInnerBundle));
  EXPECT_TRUE(innerBundle.deepEquals(outInnerBundle));

  ASSERT_OK(iface->RepeatPersistableBundle(inPBundle, &outPBundle));

  // verify all supported types make it to/from the service
  outBool = false;
  outInt = 0;
  outLong = 0;
  outDouble = 0;
  outString = std::string();
  outBoolV.clear();
  outIntV.clear();
  outLongV.clear();
  outDoubleV.clear();
  outInnerBundle = PersistableBundle();
  // The == operator checks the underlying pointers which should be different
  EXPECT_NE(inPBundle, outPBundle);
  // The deepEquals function checks the contents of the bundle, which should be
  // the same
  EXPECT_TRUE(inPBundle.deepEquals(outPBundle));
  EXPECT_EQ(inPBundle.size(), outPBundle.size());

  EXPECT_TRUE(outPBundle.getBoolean("bool", &outBool));
  EXPECT_EQ(outBool, kBoolVal);
  EXPECT_TRUE(outPBundle.getInt("int", &outInt));
  EXPECT_EQ(outInt, kIntVal);
  EXPECT_TRUE(outPBundle.getLong("long", &outLong));
  EXPECT_EQ(outLong, kLongVal);
  EXPECT_TRUE(outPBundle.getDouble("double", &outDouble));
  EXPECT_EQ(outDouble, kDoubleVal);
  EXPECT_TRUE(outPBundle.getString("string", &outString));
  EXPECT_EQ(outString, kStringVal);
  EXPECT_TRUE(outPBundle.getBooleanVector("boolv", &outBoolV));
  EXPECT_EQ(outBoolV, kBoolVVal);
  EXPECT_TRUE(outPBundle.getIntVector("intv", &outIntV));
  EXPECT_EQ(outIntV, kIntVVal);
  EXPECT_TRUE(outPBundle.getLongVector("longv", &outLongV));
  EXPECT_EQ(outLongV, kLongVVal);
  EXPECT_TRUE(outPBundle.getDoubleVector("doublev", &outDoubleV));
  EXPECT_EQ(outDoubleV, kDoubleVVal);
  EXPECT_TRUE(outPBundle.getPersistableBundle("pbundle", &outInnerBundle));
  EXPECT_TRUE(innerBundle.deepEquals(outInnerBundle));
}

TEST_P(NdkBinderTest_Aidl, EraseAllPersistableBundle) {
  PersistableBundle pBundle;
  // fill it up, empty it out and verify sizes along the way
  EXPECT_EQ(0, pBundle.size());
  pBundle.putBoolean("bool", kBoolVal);
  pBundle.putInt("int", kIntVal);
  pBundle.putLong("long", kLongVal);
  pBundle.putDouble("double", kDoubleVal);
  pBundle.putString("string", kStringVal);
  EXPECT_GT(pBundle.size(), 0);
  EXPECT_GT(pBundle.erase("bool"), 0);
  EXPECT_GT(pBundle.erase("int"), 0);
  EXPECT_GT(pBundle.erase("long"), 0);
  EXPECT_GT(pBundle.erase("double"), 0);
  EXPECT_GT(pBundle.erase("string"), 0);
  EXPECT_EQ(0, pBundle.size());
}

TEST_P(NdkBinderTest_Aidl, GetBoolKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putBoolean("first", kBoolVal);
  pBundle.putBoolean("second", kBoolVal);
  pBundle.putBoolean("third", kBoolVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getBooleanKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetIntKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putInt("first", kIntVal);
  pBundle.putInt("second", kIntVal);
  pBundle.putInt("third", kIntVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getIntKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetLongKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putLong("first", kLongVal);
  pBundle.putLong("second", kLongVal);
  pBundle.putLong("third", kLongVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getLongKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetDoubleKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putDouble("first", kDoubleVal);
  pBundle.putDouble("second", kDoubleVal);
  pBundle.putDouble("third", kDoubleVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getDoubleKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetStringKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putString("first", kStringVal);
  pBundle.putString("second", kStringVal);
  pBundle.putString("third", kStringVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getStringKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetBooleanVectorKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putBooleanVector("first", kBoolVVal);
  pBundle.putBooleanVector("second", kBoolVVal);
  pBundle.putBooleanVector("third", kBoolVVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getBooleanVectorKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetIntVectorKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putIntVector("first", kIntVVal);
  pBundle.putIntVector("second", kIntVVal);
  pBundle.putIntVector("third", kIntVVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getIntVectorKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetLongVectorKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putLongVector("first", kLongVVal);
  pBundle.putLongVector("second", kLongVVal);
  pBundle.putLongVector("third", kLongVVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getLongVectorKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetDoubleVectorKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putDoubleVector("first", kDoubleVVal);
  pBundle.putDoubleVector("second", kDoubleVVal);
  pBundle.putDoubleVector("third", kDoubleVVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getDoubleVectorKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetStringVectorKeysPersistableBundle) {
  PersistableBundle pBundle;
  pBundle.putStringVector("first", kStringVVal);
  pBundle.putStringVector("second", kStringVVal);
  pBundle.putStringVector("third", kStringVVal);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getStringVectorKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, GetPersistableBundleKeysPersistableBundle) {
  PersistableBundle pBundle;
  PersistableBundle innerBundle;
  pBundle.putPersistableBundle("first", innerBundle);
  pBundle.putPersistableBundle("second", innerBundle);
  pBundle.putPersistableBundle("third", innerBundle);
  EXPECT_EQ(3, pBundle.size());
  std::set<std::string> ret = pBundle.getPersistableBundleKeys();
  EXPECT_EQ(3, ret.size());
  EXPECT_EQ(ret, std::set<std::string>({"first", "second", "third"}));
}

TEST_P(NdkBinderTest_Aidl, InsAndOuts) {
  RegularPolygon defaultPolygon;
  ASSERT_OK(iface->RenamePolygon(&defaultPolygon, "Jerry"));
  EXPECT_EQ("Jerry", defaultPolygon.name);
}

TEST_P(NdkBinderTest_Aidl, NewField) {
  Baz baz;
  baz.d = {"a", "b", "c"};

  Baz outbaz;

  ASSERT_OK(getCompatTest(iface)->repeatBaz(baz, &outbaz));

  if (GetParam().shouldBeOld) {
    EXPECT_EQ(std::nullopt, outbaz.d);
  } else {
    EXPECT_EQ(baz.d, outbaz.d);
  }
}

TEST_P(NdkBinderTest_Aidl, RenameFoo) {
  Foo foo;
  Foo outputFoo;
  ASSERT_OK(iface->renameFoo(&foo, "MYFOO"));

  EXPECT_EQ("MYFOO", foo.a);
}

TEST_P(NdkBinderTest_Aidl, RenameBar) {
  Foo foo;
  Foo outputFoo;
  ASSERT_OK(iface->renameBar(&foo, "MYBAR"));

  EXPECT_EQ("MYBAR", foo.d.a);
}

TEST_P(NdkBinderTest_Aidl, GetLastItem) {
  Foo foo;
  foo.f = 15;
  int retF;
  ASSERT_OK(iface->getF(foo, &retF));
  EXPECT_EQ(15, retF);
}

TEST_P(NdkBinderTest_Aidl, RepeatFoo) {
  Foo foo;
  foo.a = "NEW FOO";
  foo.b = 57;
  foo.d.b = "a";
  foo.e.d = 99;
  foo.shouldBeByteBar = ByteEnum::BAR;
  foo.shouldBeIntBar = IntEnum::BAR;
  foo.shouldBeLongBar = LongEnum::BAR;
  foo.shouldContainTwoByteFoos = {ByteEnum::FOO, ByteEnum::FOO};
  foo.shouldContainTwoIntFoos = {IntEnum::FOO, IntEnum::FOO};
  foo.shouldContainTwoLongFoos = {LongEnum::FOO, LongEnum::FOO};
  foo.u = SimpleUnion::make<SimpleUnion::c>("hello");
  foo.shouldSetBit0AndBit2 = Foo::BIT0 | Foo::BIT2;
  foo.shouldBeConstS1 = SimpleUnion::S1;

  Foo retFoo;

  ASSERT_OK(iface->repeatFoo(foo, &retFoo));

  EXPECT_EQ(foo.a, retFoo.a);
  EXPECT_EQ(foo.b, retFoo.b);
  EXPECT_EQ(foo.d.b, retFoo.d.b);
  EXPECT_EQ(foo.e.d, retFoo.e.d);
  EXPECT_EQ(foo.shouldBeByteBar, retFoo.shouldBeByteBar);
  EXPECT_EQ(foo.shouldBeIntBar, retFoo.shouldBeIntBar);
  EXPECT_EQ(foo.shouldBeLongBar, retFoo.shouldBeLongBar);
  EXPECT_EQ(foo.shouldContainTwoByteFoos, retFoo.shouldContainTwoByteFoos);
  EXPECT_EQ(foo.shouldContainTwoIntFoos, retFoo.shouldContainTwoIntFoos);
  EXPECT_EQ(foo.shouldContainTwoLongFoos, retFoo.shouldContainTwoLongFoos);
  EXPECT_EQ(foo.u, retFoo.u);
  EXPECT_EQ(foo.shouldSetBit0AndBit2, retFoo.shouldSetBit0AndBit2);
  EXPECT_EQ(foo.shouldBeConstS1, retFoo.shouldBeConstS1);
}

TEST_P(NdkBinderTest_Aidl, RepeatGenericBar) {
  GenericBar<int32_t> bar;
  bar.a = 40;
  bar.shouldBeGenericFoo.a = 41;
  bar.shouldBeGenericFoo.b = 42;

  GenericBar<int32_t> retBar;

  ASSERT_OK(iface->repeatGenericBar(bar, &retBar));

  EXPECT_EQ(bar.a, retBar.a);
  EXPECT_EQ(bar.shouldBeGenericFoo.a, retBar.shouldBeGenericFoo.a);
  EXPECT_EQ(bar.shouldBeGenericFoo.b, retBar.shouldBeGenericFoo.b);
}

template <typename T>
using RepeatMethod = ScopedAStatus (ITest::*)(const std::vector<T>&,
                                              std::vector<T>*, std::vector<T>*);

template <typename T>
void testRepeat(const std::shared_ptr<ITest>& i, RepeatMethod<T> repeatMethod,
                std::vector<std::vector<T>> tests) {
  for (const auto& input : tests) {
    std::vector<T> out1;
    out1.resize(input.size());
    std::vector<T> out2;

    ASSERT_OK((i.get()->*repeatMethod)(input, &out1, &out2)) << input.size();
    EXPECT_EQ(input, out1);
    EXPECT_EQ(input, out2);
  }
}

template <typename T>
void testRepeat2List(const std::shared_ptr<ITest>& i, RepeatMethod<T> repeatMethod,
                     std::vector<std::vector<T>> tests) {
  for (const auto& input : tests) {
    std::vector<T> out1;
    std::vector<T> out2;
    std::vector<T> expected;

    expected.insert(expected.end(), input.begin(), input.end());
    expected.insert(expected.end(), input.begin(), input.end());

    ASSERT_OK((i.get()->*repeatMethod)(input, &out1, &out2)) << expected.size();
    EXPECT_EQ(expected, out1);
    EXPECT_EQ(expected, out2);
  }
}

TEST_P(NdkBinderTest_Aidl, Arrays) {
  testRepeat<bool>(iface, &ITest::RepeatBooleanArray,
                   {
                       {},
                       {true},
                       {false, true, false},
                   });
  testRepeat<uint8_t>(iface, &ITest::RepeatByteArray,
                      {
                          {},
                          {1},
                          {1, 2, 3},
                      });
  testRepeat<char16_t>(iface, &ITest::RepeatCharArray,
                       {
                           {},
                           {L'@'},
                           {L'@', L'!', L'A'},
                       });
  testRepeat<int32_t>(iface, &ITest::RepeatIntArray,
                      {
                          {},
                          {1},
                          {1, 2, 3},
                      });
  testRepeat<int64_t>(iface, &ITest::RepeatLongArray,
                      {
                          {},
                          {1},
                          {1, 2, 3},
                      });
  testRepeat<float>(iface, &ITest::RepeatFloatArray,
                    {
                        {},
                        {1.0f},
                        {1.0f, 2.0f, 3.0f},
                    });
  testRepeat<double>(iface, &ITest::RepeatDoubleArray,
                     {
                         {},
                         {1.0},
                         {1.0, 2.0, 3.0},
                     });
  testRepeat<ByteEnum>(iface, &ITest::RepeatByteEnumArray,
                       {
                           {},
                           {ByteEnum::FOO},
                           {ByteEnum::FOO, ByteEnum::BAR},
                       });
  testRepeat<IntEnum>(iface, &ITest::RepeatIntEnumArray,
                      {
                          {},
                          {IntEnum::FOO},
                          {IntEnum::FOO, IntEnum::BAR},
                      });
  testRepeat<LongEnum>(iface, &ITest::RepeatLongEnumArray,
                       {
                           {},
                           {LongEnum::FOO},
                           {LongEnum::FOO, LongEnum::BAR},
                       });
  testRepeat<std::string>(iface, &ITest::RepeatStringArray,
                          {
                              {},
                              {"asdf"},
                              {"", "aoeu", "lol", "brb"},
                          });
  testRepeat<RegularPolygon>(iface, &ITest::RepeatRegularPolygonArray,
                             {
                                 {},
                                 {{"hexagon", 6, 2.0f}},
                                 {{"hexagon", 6, 2.0f}, {"square", 4, 7.0f}, {"pentagon", 5, 4.2f}},
                             });
  std::shared_ptr<IEmpty> my_empty = SharedRefBase::make<MyEmpty>();
  testRepeat<SpAIBinder>(iface, &ITest::RepeatBinderArray,
                         {
                             {},
                             {iface->asBinder()},
                             {iface->asBinder(), my_empty->asBinder()},
                         });

  std::shared_ptr<IEmpty> your_empty = SharedRefBase::make<YourEmpty>();
  testRepeat<std::shared_ptr<IEmpty>>(iface, &ITest::RepeatInterfaceArray,
                                      {
                                          {},
                                          {my_empty},
                                          {my_empty, your_empty},
                                          // Legacy behavior: allow null for non-nullable interface
                                          {my_empty, your_empty, nullptr},
                                      });
}

TEST_P(NdkBinderTest_Aidl, Lists) {
  testRepeat2List<std::string>(iface, &ITest::Repeat2StringList,
                               {
                                   {},
                                   {"asdf"},
                                   {"", "aoeu", "lol", "brb"},
                               });
  testRepeat2List<RegularPolygon>(
      iface, &ITest::Repeat2RegularPolygonList,
      {
          {},
          {{"hexagon", 6, 2.0f}},
          {{"hexagon", 6, 2.0f}, {"square", 4, 7.0f}, {"pentagon", 5, 4.2f}},
      });
}

template <typename T>
using RepeatNullableMethod = ScopedAStatus (ITest::*)(
    const std::optional<std::vector<std::optional<T>>>&,
    std::optional<std::vector<std::optional<T>>>*,
    std::optional<std::vector<std::optional<T>>>*);

template <typename T>
void testRepeat(
    const std::shared_ptr<ITest>& i, RepeatNullableMethod<T> repeatMethod,
    std::vector<std::optional<std::vector<std::optional<T>>>> tests) {
  for (const auto& input : tests) {
    std::optional<std::vector<std::optional<T>>> out1;
    if (input) {
      out1 = std::vector<std::optional<T>>{};
      out1->resize(input->size());
    }
    std::optional<std::vector<std::optional<T>>> out2;

    ASSERT_OK((i.get()->*repeatMethod)(input, &out1, &out2))
        << (input ? input->size() : -1);
    EXPECT_EQ(input, out1);
    EXPECT_EQ(input, out2);
  }
}

template <typename T>
using SingleRepeatNullableMethod = ScopedAStatus (ITest::*)(
    const std::optional<std::vector<T>>&, std::optional<std::vector<T>>*);

template <typename T>
void testRepeat(const std::shared_ptr<ITest>& i,
                SingleRepeatNullableMethod<T> repeatMethod,
                std::vector<std::optional<std::vector<T>>> tests) {
  for (const auto& input : tests) {
    std::optional<std::vector<T>> ret;
    ASSERT_OK((i.get()->*repeatMethod)(input, &ret))
        << (input ? input->size() : -1);
    EXPECT_EQ(input, ret);
  }
}

TEST_P(NdkBinderTest_Aidl, NullableArrays) {
  testRepeat<bool>(iface, &ITest::RepeatNullableBooleanArray,
                   {
                       std::nullopt,
                       {{}},
                       {{true}},
                       {{false, true, false}},
                   });
  testRepeat<uint8_t>(iface, &ITest::RepeatNullableByteArray,
                      {
                          std::nullopt,
                          {{}},
                          {{1}},
                          {{1, 2, 3}},
                      });
  testRepeat<char16_t>(iface, &ITest::RepeatNullableCharArray,
                       {
                           std::nullopt,
                           {{}},
                           {{L'@'}},
                           {{L'@', L'!', L'A'}},
                       });
  testRepeat<int32_t>(iface, &ITest::RepeatNullableIntArray,
                      {
                          std::nullopt,
                          {{}},
                          {{1}},
                          {{1, 2, 3}},
                      });
  testRepeat<int64_t>(iface, &ITest::RepeatNullableLongArray,
                      {
                          std::nullopt,
                          {{}},
                          {{1}},
                          {{1, 2, 3}},
                      });
  testRepeat<float>(iface, &ITest::RepeatNullableFloatArray,
                    {
                        std::nullopt,
                        {{}},
                        {{1.0f}},
                        {{1.0f, 2.0f, 3.0f}},
                    });
  testRepeat<double>(iface, &ITest::RepeatNullableDoubleArray,
                     {
                         std::nullopt,
                         {{}},
                         {{1.0}},
                         {{1.0, 2.0, 3.0}},
                     });
  testRepeat<ByteEnum>(iface, &ITest::RepeatNullableByteEnumArray,
                       {
                           std::nullopt,
                           {{}},
                           {{ByteEnum::FOO}},
                           {{ByteEnum::FOO, ByteEnum::BAR}},
                       });
  testRepeat<IntEnum>(iface, &ITest::RepeatNullableIntEnumArray,
                      {
                          std::nullopt,
                          {{}},
                          {{IntEnum::FOO}},
                          {{IntEnum::FOO, IntEnum::BAR}},
                      });
  testRepeat<LongEnum>(iface, &ITest::RepeatNullableLongEnumArray,
                       {
                           std::nullopt,
                           {{}},
                           {{LongEnum::FOO}},
                           {{LongEnum::FOO, LongEnum::BAR}},
                       });
  testRepeat<std::optional<std::string>>(
      iface, &ITest::RepeatNullableStringArray,
      {
          std::nullopt,
          {{}},
          {{"asdf"}},
          {{std::nullopt}},
          {{"aoeu", "lol", "brb"}},
          {{"", "aoeu", std::nullopt, "brb"}},
      });
  testRepeat<std::string>(iface, &ITest::DoubleRepeatNullableStringArray,
                          {
                              {{}},
                              {{"asdf"}},
                              {{std::nullopt}},
                              {{"aoeu", "lol", "brb"}},
                              {{"", "aoeu", std::nullopt, "brb"}},
                          });
  std::shared_ptr<IEmpty> my_empty = SharedRefBase::make<MyEmpty>();
  testRepeat<SpAIBinder>(iface, &ITest::RepeatNullableBinderArray,
                         {
                             std::nullopt,
                             {{}},
                             {{iface->asBinder()}},
                             {{nullptr}},
                             {{iface->asBinder(), my_empty->asBinder()}},
                             {{iface->asBinder(), nullptr, my_empty->asBinder()}},
                         });
  std::shared_ptr<IEmpty> your_empty = SharedRefBase::make<YourEmpty>();
  testRepeat<std::shared_ptr<IEmpty>>(iface, &ITest::RepeatNullableInterfaceArray,
                                      {
                                          std::nullopt,
                                          {{}},
                                          {{my_empty}},
                                          {{nullptr}},
                                          {{my_empty, your_empty}},
                                          {{my_empty, nullptr, your_empty}},
                                      });
}

class DefaultImpl : public ::aidl::test_package::ICompatTestDefault {
 public:
  ::ndk::ScopedAStatus NewMethodThatReturns10(int32_t* _aidl_return) override {
    *_aidl_return = 100;  // default impl returns different value
    return ::ndk::ScopedAStatus(AStatus_newOk());
  }
};

TEST_P(NdkBinderTest_Aidl, NewMethod) {
  std::shared_ptr<ICompatTest> default_impl = SharedRefBase::make<DefaultImpl>();
  ::aidl::test_package::ICompatTest::setDefaultImpl(default_impl);

  auto compat_test = getCompatTest(iface);
  int32_t res;
  EXPECT_OK(compat_test->NewMethodThatReturns10(&res));
  if (GetParam().shouldBeOld) {
    // Remote was built with version 1 interface which does not have
    // "NewMethodThatReturns10". In this case the default method
    // which returns 100 is called.
    EXPECT_EQ(100, res);
  } else {
    // Remote is built with the current version of the interface.
    // The method returns 10.
    EXPECT_EQ(10, res);
  }
}

TEST_P(NdkBinderTest_Aidl, RepeatStringNullableLater) {
  std::optional<std::string> res;

  std::string name;
  EXPECT_OK(iface->GetName(&name));

  // Java considers every type to be nullable, but this is okay, since it will
  // pass back NullPointerException to the client if it does not handle a null
  // type, similar to how a C++ server would refuse to unparcel a null
  // non-nullable type. Of course, this is not ideal, but the problem runs very
  // deep.
  const bool supports_nullable = !GetParam().shouldBeOld || name == "Java";
  auto compat_test = getCompatTest(iface);
  if (supports_nullable) {
    EXPECT_OK(compat_test->RepeatStringNullableLater(std::nullopt, &res));
    EXPECT_EQ(std::nullopt, res);
  } else {
    ndk::ScopedAStatus status = compat_test->RepeatStringNullableLater(std::nullopt, &res);
    ASSERT_EQ(STATUS_UNEXPECTED_NULL, AStatus_getStatus(status.get()));
  }

  EXPECT_OK(compat_test->RepeatStringNullableLater("", &res));
  EXPECT_EQ("", res);

  EXPECT_OK(compat_test->RepeatStringNullableLater("a", &res));
  EXPECT_EQ("a", res);

  EXPECT_OK(compat_test->RepeatStringNullableLater("say what?", &res));
  EXPECT_EQ("say what?", res);
}

TEST_P(NdkBinderTest_Aidl, GetInterfaceVersion) {
  int32_t res;
  auto compat_test = getCompatTest(iface);
  EXPECT_OK(compat_test->getInterfaceVersion(&res));
  if (GetParam().shouldBeOld) {
    EXPECT_EQ(1, res);
  } else {
    // 3 is the not-yet-frozen version. It may be V2 or V3 depending
    // on the release config. 'next' will be V2.
    EXPECT_TRUE(res > 1);
  }
}

TEST_P(NdkBinderTest_Aidl, GetInterfaceHash) {
  std::string res;
  auto compat_test = getCompatTest(iface);
  EXPECT_OK(compat_test->getInterfaceHash(&res));
  if (GetParam().shouldBeOld) {
    // aidl_api/libbinder_ndk_test_interface/1/.hash
    EXPECT_EQ("b663b681b3e0d66f9b5428c2f23365031b7d4ba0", res);
  } else {
    int32_t version = 0;
    EXPECT_OK(compat_test->getInterfaceVersion(&version));
    if (version == 2) {
      // aidl_api/libbinder_ndk_test_interface/2/.hash
      EXPECT_EQ("2740afaf3b5a0e739c44165c49633a0af87369f2", res);
    } else {
      EXPECT_EQ("notfrozen", res);
    }
  }
}

TEST_P(NdkBinderTest_Aidl, LegacyBinder) {
  SpAIBinder binder;
  iface->getLegacyBinderTest(&binder);
  ASSERT_NE(nullptr, binder.get());

  ASSERT_TRUE(AIBinder_associateClass(binder.get(), kLegacyBinderClass));

  constexpr int32_t kVal = 42;

  ::ndk::ScopedAParcel in;
  ::ndk::ScopedAParcel out;
  ASSERT_EQ(STATUS_OK, AIBinder_prepareTransaction(binder.get(), in.getR()));
  ASSERT_EQ(STATUS_OK, AParcel_writeInt32(in.get(), kVal));
  ASSERT_EQ(STATUS_OK,
            AIBinder_transact(binder.get(), FIRST_CALL_TRANSACTION, in.getR(), out.getR(), 0));

  int32_t output;
  ASSERT_EQ(STATUS_OK, AParcel_readInt32(out.get(), &output));
  EXPECT_EQ(kVal, output);
}

TEST_P(NdkBinderTest_Aidl, ParcelableHolderTest) {
  ExtendableParcelable ep;
  MyExt myext1;
  myext1.a = 42;
  myext1.b = "mystr";
  ep.ext.setParcelable(myext1);
  std::optional<MyExt> myext2;
  ep.ext.getParcelable(&myext2);
  EXPECT_TRUE(myext2);
  EXPECT_EQ(42, myext2->a);
  EXPECT_EQ("mystr", myext2->b);

  AParcel* parcel = AParcel_create();
  ep.writeToParcel(parcel);
  AParcel_setDataPosition(parcel, 0);
  ExtendableParcelable ep2;
  ep2.readFromParcel(parcel);
  std::optional<MyExt> myext3;
  ep2.ext.getParcelable(&myext3);
  EXPECT_TRUE(myext3);
  EXPECT_EQ(42, myext3->a);
  EXPECT_EQ("mystr", myext3->b);
  AParcel_delete(parcel);
}

TEST_P(NdkBinderTest_Aidl, ParcelableHolderCopyTest) {
  ndk::AParcelableHolder ph1{ndk::STABILITY_LOCAL};
  MyExt myext1;
  myext1.a = 42;
  myext1.b = "mystr";
  ph1.setParcelable(myext1);

  ndk::AParcelableHolder ph2{ph1};
  std::optional<MyExt> myext2;
  ph2.getParcelable(&myext2);
  EXPECT_TRUE(myext2);
  EXPECT_EQ(42, myext2->a);
  EXPECT_EQ("mystr", myext2->b);

  std::optional<MyExt> myext3;
  ph1.getParcelable(&myext3);
  EXPECT_TRUE(myext3);
  EXPECT_EQ(42, myext3->a);
  EXPECT_EQ("mystr", myext3->b);
}

TEST_P(NdkBinderTest_Aidl, ParcelableHolderAssignmentWithLocalStabilityTest) {
  ndk::AParcelableHolder ph1{ndk::STABILITY_LOCAL};
  MyExt myext1;
  myext1.a = 42;
  myext1.b = "mystr";
  EXPECT_EQ(STATUS_OK, ph1.setParcelable(myext1));

  ndk::AParcelableHolder ph2{ndk::STABILITY_LOCAL};
  MyExt myext2;
  myext2.a = 0xdb;
  myext2.b = "magic";
  EXPECT_EQ(STATUS_OK, ph2.setParcelable(myext2));

  ph2 = ph1;
  std::optional<MyExt> myext3;
  EXPECT_EQ(STATUS_OK, ph2.getParcelable(&myext3));
  EXPECT_NE(std::nullopt, myext3);
  EXPECT_TRUE(myext3 != myext2);
  EXPECT_TRUE(myext3 == myext1);
}

TEST_P(NdkBinderTest_Aidl, ParcelableHolderAssignmentWithVintfStabilityTest) {
  ndk::AParcelableHolder ph1{ndk::STABILITY_VINTF};
  MyExt myext1;
  myext1.a = 42;
  myext1.b = "mystr";
  // STABILITY_VINTF Pracelable can't set with STABILITY_LOCAL.
  EXPECT_EQ(STATUS_BAD_VALUE, ph1.setParcelable(myext1));

  ndk::AParcelableHolder ph2{ndk::STABILITY_VINTF};
  MyExt myext2;
  myext2.a = 0xbd;
  myext2.b = "cigam";
  EXPECT_EQ(STATUS_BAD_VALUE, ph2.setParcelable(myext2));

  ph2 = ph1;
  std::optional<MyExt> myext3;
  EXPECT_EQ(STATUS_OK, ph2.getParcelable(&myext3));
  EXPECT_EQ(std::nullopt, myext3);
}

TEST_P(NdkBinderTest_Aidl, ParcelableHolderCommunicationTest) {
  ExtendableParcelable ep;
  ep.c = 42L;
  MyExt myext1;
  myext1.a = 42;
  myext1.b = "mystr";
  ep.ext.setParcelable(myext1);

  ExtendableParcelable ep2;
  EXPECT_OK(iface->RepeatExtendableParcelable(ep, &ep2));
  std::optional<MyExt> myext2;
  ep2.ext.getParcelable(&myext2);
  EXPECT_EQ(42L, ep2.c);
  EXPECT_TRUE(myext2);
  EXPECT_EQ(42, myext2->a);
  EXPECT_EQ("mystr", myext2->b);
}

TEST_P(NdkBinderTest_Aidl, EmptyParcelableHolderCommunicationTest) {
  ExtendableParcelable ep;
  ExtendableParcelable ep2;
  ep.c = 42L;
  EXPECT_OK(iface->RepeatExtendableParcelableWithoutExtension(ep, &ep2));

  EXPECT_EQ(42L, ep2.c);
}

std::shared_ptr<ITest> getProxyLocalService() {
  std::shared_ptr<MyTest> test = SharedRefBase::make<MyTest>();
  SpAIBinder binder = test->asBinder();

  // adding an arbitrary class as the extension
  std::shared_ptr<MyTest> ext = SharedRefBase::make<MyTest>();
  SpAIBinder extBinder = ext->asBinder();

  binder_status_t ret = AIBinder_setExtension(binder.get(), extBinder.get());
  if (ret != STATUS_OK) {
    __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, "Could not set local extension");
  }

  // BpTest -> AIBinder -> test
  //
  // Warning: for testing purposes only. This parcels things within the same process for testing
  // purposes. In normal usage, this should just return SharedRefBase::make<MyTest> directly.
  return SharedRefBase::make<BpTest>(binder);
}

std::shared_ptr<ITest> getNdkBinderTestJavaService(const std::string& method) {
  JNIEnv* env = GetEnv();
  if (env == nullptr) {
    __android_log_write(ANDROID_LOG_ERROR, LOG_TAG, "No environment");
    return nullptr;
  }

  jobject object = callStaticJavaMethodForObject(env, "android/binder/cts/NdkBinderTest", method,
                                                 "()Landroid/os/IBinder;");

  SpAIBinder binder = SpAIBinder(AIBinder_fromJavaBinder(env, object));

  return ITest::fromBinder(binder);
}

INSTANTIATE_TEST_CASE_P(LocalProxyToNative, NdkBinderTest_Aidl,
                        ::testing::Values(Params{getProxyLocalService(), false /*shouldBeRemote*/,
                                                 true /*shouldBeWrapped*/, "CPP",
                                                 false /*shouldBeOld*/}));
INSTANTIATE_TEST_CASE_P(LocalNativeFromJava, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getLocalNativeService"),
                            false /*shouldBeRemote*/, false /*shouldBeWrapped*/, "CPP",
                            false /*shouldBeOld*/}));
INSTANTIATE_TEST_CASE_P(LocalJava, NdkBinderTest_Aidl,
                        ::testing::Values(Params{getNdkBinderTestJavaService("getLocalJavaService"),
                                                 false /*shouldBeRemote*/, true /*shouldBeWrapped*/,
                                                 "JAVA", false /*shouldBeOld*/}));
INSTANTIATE_TEST_CASE_P(RemoteNative, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getRemoteNativeService"),
                            true /*shouldBeRemote*/, true /*shouldBeWrapped*/, "CPP",
                            false /*shouldBeOld*/}));
INSTANTIATE_TEST_CASE_P(RemoteJava, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getRemoteJavaService"),
                            true /*shouldBeRemote*/, true /*shouldBeWrapped*/, "JAVA",
                            false /*shouldBeOld*/}));

INSTANTIATE_TEST_CASE_P(RemoteNativeOld, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getRemoteOldNativeService"),
                            true /*shouldBeRemote*/, true /*shouldBeWrapped*/, "CPP",
                            true /*shouldBeOld*/}));
