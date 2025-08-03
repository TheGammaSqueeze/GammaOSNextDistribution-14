# GTest Unit Tests

[TOC]

## GTest Unit Tests

[GTest](https://github.com/google/googletest) is a Google developed open source
unit testing framework for C++ and C code. As the majority of GD code is writeen
in C++, GTest provide the first layer of defence against bugs from the
implementation level. Used in combination with
[GMock](https://github.com/google/googlemock) developers can easily isolate
classes and functions from their code to conduct unit testing.

*   [GTest Primer](https://github.com/google/googletest/blob/master/googletest/docs/primer.md)
*   [GMock for Dummies](https://github.com/google/googletest/blob/master/googlemock/docs/for_dummies.md)

### Test Binary

All Gd unit test classes are compiled into a single binary
[bluetooth_test_gd](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/Android.bp).

### Test Sources Definitions

*   Tests should live in the same directory as the source code
*   Mocks should live in the same directory as the source header so that it can
    be shared among multiple tests
*   Tests should not modify global states that would affect other tests, so that
    all tests could be executed using the same binary
*   Each module can define a filegroup() that includes all test sources. This
    filegroup is then included in a single cc_test() target that produce a
    single test binary
    [bluetooth_test_gd](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/Android.bp).
    A single test binary simplifies the configuration effort needed for
    compilation, presubmit and postsubmit execution, and so on.

### How to run tests

#### Use `atest`

[ATest](https://source.android.com/compatibility/tests/development/atest) is an
Android tool that allows a developers to run multiple modes of tests from the
same `atest` command, including Java Instrumentation Tests, C/C++ GTests,
CTS/GTS tests, etc. To use `atest` with GD, simplying sync your Android tree,
run `source build/envsetup.sh` and `lunch` to a desired target. Then

*   To run tests on device, the following command will automatically build,
    push, and execute tests on a connected Android device

    ```shell
    atest bluetooth_test_gd
    ```

*   To run tests on host, the following command will automatically build and run
    tests on your host machine

    ```shell
    atest --host bluetooth_test_gd
    ```

*   To run a single test case, use `<test_binary>:<test_class>#<test_method>`
    format, such as

    ```shell
    atest --host bluetooth_test_gd:AclManagerTest#invoke_registered_callback_connection_complete_success
    ```

    See `atest --help` for more documentation on how to use atest to run various
    tests

#### Run it yourself (Not receommended unless really needed)

Sometimes, you may want to execute the test binary directly because you want to
attach a debugger or you want to avoid the test boostrap delay in `atest`. You
can do it with the following steps

1.  Sync Android tree, run `build/envsetup` and `lunch` desired target, `cd`
    into Android checkout root directory

1.  Make bluetooth_test_gd binary

    ```shell
    m -j40 bluetooth_test_gd
    ```

1.  Run the test on host {value=3}

    ```shell
    $ANDROID_HOST_OUT/nativetest64/bluetooth_test_gd/bluetooth_test_gd
    ```

1.  Run the test on device {value=4}

    Push test to device

    ```shell
    adb push $ANDROID_PRODUCT_OUT/testcases/bluetooth_test_gd/arm64/bluetooth_test_gd /data/nativetest64/bluetooth_test_gd
    ```

    Run test using ADB

    ```shell
    adb shell /data/nativetest64/bluetooth_test_gd
    ```

1.  Run test with filter (Works the same way for device based test) {value=5}

    ```shell
    $ANDROID_HOST_OUT/nativetest64/bluetooth_test_gd/bluetooth_test_gd --gtest_filter=AclManagerTest.invoke_registered_callback_connection_complete_success*
    ```

    Note: the '*' wildcard is very important

1.  Get command line help {value=6}

    ```shell
    $ANDROID_HOST_OUT/nativetest64/bluetooth_test_gd/bluetooth_test_gd --help
    ```

### Example: L2capClassicFixedChannelImplTest

Note: All paths are relative to
[packages/modules/Bluetooth/system/gd](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd)

#### Source code:

*   [l2cap/classic/internal/fixed_channel_impl.h](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/l2cap/classic/internal/fixed_channel_impl.h)

```c++
#pragma once

#include "common/bidi_queue.h"
#include "l2cap/cid.h"
#include "l2cap/classic/fixed_channel.h"
#include "l2cap/internal/channel_impl.h"
#include "l2cap/l2cap_packets.h"
#include "os/handler.h"
#include "os/log.h"

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

class Link;

class FixedChannelImpl : public l2cap::internal::ChannelImpl {
 public:
  FixedChannelImpl(Cid cid, Link* link, os::Handler* l2cap_handler);
  virtual ~FixedChannelImpl() = default;
  hci::Address GetDevice() const;
  virtual void RegisterOnCloseCallback(os::Handler* user_handler, FixedChannel::OnCloseCallback on_close_callback);
  virtual void Acquire();
  virtual void Release();
  virtual bool IsAcquired() const;
  virtual void OnClosed(hci::ErrorCode status);
  virtual std::string ToString();
  common::BidiQueueEnd<packet::BasePacketBuilder, packet::PacketView<packet::kLittleEndian>>* GetQueueUpEnd();
  common::BidiQueueEnd<packet::PacketView<packet::kLittleEndian>, packet::BasePacketBuilder>* GetQueueDownEnd();
  Cid GetCid() const;
  Cid GetRemoteCid() const;
 private:
  // private fields omitted in doc ...
};

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
```

*   [packages/modules/Bluetooth/system/gd/l2cap/classic/internal/fixed_channel_impl.cc](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/l2cap/classic/internal/fixed_channel_impl.cc)

#### Mocks for dependencies' unit tests

*   [l2cap/classic/internal/fixed_channel_impl_mock.h](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/l2cap/classic/internal/fixed_channel_impl_mock.h)

```c++
#pragma once

#include "l2cap/classic/internal/fixed_channel_impl.h"

#include <gmock/gmock.h>

// Unit test interfaces
namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {
namespace testing {

class MockFixedChannelImpl : public FixedChannelImpl {
 public:
  MockFixedChannelImpl(Cid cid, Link* link, os::Handler* l2cap_handler) : FixedChannelImpl(cid, link, l2cap_handler) {}
  MOCK_METHOD(void, RegisterOnCloseCallback,
              (os::Handler * user_handler, FixedChannel::OnCloseCallback on_close_callback), (override));
  MOCK_METHOD(void, Acquire, (), (override));
  MOCK_METHOD(void, Release, (), (override));
  MOCK_METHOD(bool, IsAcquired, (), (override, const));
  MOCK_METHOD(void, OnClosed, (hci::ErrorCode status), (override));
};

}  // namespace testing
}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
```

#### Tests

*   [l2cap/classic/internal/fixed_channel_impl_test.cc](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/master/gd/l2cap/classic/internal/fixed_channel_impl_test.cc)

```c++
#include "l2cap/classic/internal/fixed_channel_impl.h"

#include "common/testing/bind_test_util.h"
#include "l2cap/cid.h"
#include "l2cap/classic/internal/link_mock.h"
#include "l2cap/internal/parameter_provider_mock.h"
#include "os/handler.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace bluetooth {
namespace l2cap {
namespace classic {
namespace internal {

using l2cap::internal::testing::MockParameterProvider;
using ::testing::_;
using testing::MockLink;
using ::testing::Return;

class L2capClassicFixedChannelImplTest : public ::testing::Test {
 public:
  static void SyncHandler(os::Handler* handler) {
    std::promise<void> promise;
    auto future = promise.get_future();
    handler->Post(common::BindOnce(&std::promise<void>::set_value, common::Unretained(&promise)));
    future.wait_for(std::chrono::seconds(1));
  }

 protected:
  void SetUp() override {
    thread_ = new os::Thread("test_thread", os::Thread::Priority::NORMAL);
    l2cap_handler_ = new os::Handler(thread_);
  }

  void TearDown() override {
    l2cap_handler_->Clear();
    delete l2cap_handler_;
    delete thread_;
  }

  os::Thread* thread_ = nullptr;
  os::Handler* l2cap_handler_ = nullptr;
};

TEST_F(L2capClassicFixedChannelImplTest, get_device) {
  MockParameterProvider mock_parameter_provider;
  EXPECT_CALL(mock_parameter_provider, GetClassicLinkIdleDisconnectTimeout())
      .WillRepeatedly(Return(std::chrono::seconds(5)));
  testing::MockClassicAclConnection* mock_acl_connection = new testing::MockClassicAclConnection();
  EXPECT_CALL(*mock_acl_connection, GetAddress()).Times(1);
  EXPECT_CALL(*mock_acl_connection, RegisterCallbacks(_, l2cap_handler_)).Times(1);
  EXPECT_CALL(*mock_acl_connection, UnregisterCallbacks(_)).Times(1);
  MockLink mock_classic_link(l2cap_handler_, &mock_parameter_provider,
                             std::unique_ptr<testing::MockClassicAclConnection>(mock_acl_connection));
  hci::AddressWithType device{hci::Address{{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                              hci::AddressType::PUBLIC_IDENTITY_ADDRESS};
  EXPECT_CALL(mock_classic_link, GetDevice()).WillRepeatedly(Return(device));
  FixedChannelImpl fixed_channel_impl(kSmpBrCid, &mock_classic_link, l2cap_handler_);
  EXPECT_EQ(device.GetAddress(), fixed_channel_impl.GetDevice());
}

// Other test cases omitted in doc ...

}  // namespace internal
}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth
```
