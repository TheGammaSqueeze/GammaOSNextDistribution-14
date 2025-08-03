/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "aidl/GpuCapacityNode.h"

using testing::Invoke;
using testing::Return, testing::_, testing::Eq, testing::StrEq, testing::NiceMock;

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

struct MockFdInterface : FdInterface {
    MOCK_METHOD(int, open, (const char *, int), (const, final));
    MOCK_METHOD(int, write, (int, const char *, size_t), (const, final));
    MOCK_METHOD(ssize_t, read, (int, void *, size_t), (const, final));
    MOCK_METHOD(off_t, lseek, (int, off_t, int), (const, final));
    MOCK_METHOD(int, close, (int), (const, final));
};

struct FdInterfaceWrapper : FdInterface {
    FdInterfaceWrapper(std::shared_ptr<FdInterface> const &wrapped) : wrapped_(wrapped) {}

    int open(const char *path, int flags) const final { return wrapped_->open(path, flags); }

    int write(int fd, const char *data, size_t count) const final {
        return wrapped_->write(fd, data, count);
    }
    ssize_t read(int fd, void *data, size_t count) const final {
        return wrapped_->read(fd, data, count);
    }
    off_t lseek(int fd, off_t offset, int whence) const final {
        return wrapped_->lseek(fd, offset, whence);
    }

    int close(int fd) const final { return wrapped_->close(fd); }
    std::shared_ptr<FdInterface> const wrapped_;
};

struct GpuCapacityNodeTest : ::testing::Test {
    GpuCapacityNodeTest() : mock_fd_interface(std::make_shared<NiceMock<MockFdInterface>>()) {}
    std::shared_ptr<MockFdInterface> mock_fd_interface;
    std::string const path = "/path/example";
    std::string const headroom_path = "/path/example/capacity_headroom";
    std::string const freq_path = "/path/example/cur_freq";
    int const fake_fd = 33;
    int const another_fake_fd = 34;
    int const invalid_fake_fd = -33;
    Cycles const capacity{11503};
    std::string const capacity_str = "11503";
};

TEST_F(GpuCapacityNodeTest, OpensCorrectNode) {
    EXPECT_CALL(*mock_fd_interface, close(fake_fd)).Times(1).WillOnce(Return(0));
    EXPECT_CALL(*mock_fd_interface, close(another_fake_fd)).Times(1).WillOnce(Return(0));
    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
}

TEST_F(GpuCapacityNodeTest, OpensCorrectNodeHelper) {
    EXPECT_CALL(*mock_fd_interface, open(StrEq(headroom_path), O_RDWR | O_CLOEXEC | O_NONBLOCK))
            .Times(1)
            .WillOnce(Return(fake_fd));
    EXPECT_CALL(*mock_fd_interface, open(StrEq(freq_path), O_RDONLY | O_CLOEXEC | O_NONBLOCK))
            .Times(1)
            .WillOnce(Return(another_fake_fd));
    EXPECT_CALL(*mock_fd_interface, close(another_fake_fd)).Times(1).WillOnce(Return(0));
    EXPECT_CALL(*mock_fd_interface, close(fake_fd)).Times(1).WillOnce(Return(0));
    auto const node = GpuCapacityNode::init_gpu_capacity_node(
            std::make_unique<FdInterfaceWrapper>(mock_fd_interface), path);
}

TEST_F(GpuCapacityNodeTest, node_open_helper_failure_one) {
    EXPECT_CALL(*mock_fd_interface, open(_, _)).Times(1).WillOnce(Return(invalid_fake_fd));
    EXPECT_CALL(*mock_fd_interface, close(0)).Times(0);
    auto const node = GpuCapacityNode::init_gpu_capacity_node(
            std::make_unique<FdInterfaceWrapper>(mock_fd_interface), path);
    EXPECT_THAT(node, testing::Eq(nullptr));
}

TEST_F(GpuCapacityNodeTest, node_open_helper_failure_two) {
    testing::Sequence seq;
    EXPECT_CALL(*mock_fd_interface, open(_, _)).InSequence(seq).WillOnce(Return(fake_fd));
    EXPECT_CALL(*mock_fd_interface, open(_, _)).InSequence(seq).WillOnce(Return(invalid_fake_fd));
    EXPECT_CALL(*mock_fd_interface, close(fake_fd)).Times(1);
    auto const node = GpuCapacityNode::init_gpu_capacity_node(
            std::make_unique<FdInterfaceWrapper>(mock_fd_interface), path);
    EXPECT_THAT(node, testing::Eq(nullptr));
}

TEST_F(GpuCapacityNodeTest, writes_correct_value_to_node) {
    EXPECT_CALL(*mock_fd_interface, write(fake_fd, StrEq(capacity_str), capacity_str.size()))
            .Times(1);
    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    EXPECT_THAT(capacity_node.set_gpu_capacity(capacity), Eq(true));
}

TEST_F(GpuCapacityNodeTest, writes_failure) {
    EXPECT_CALL(*mock_fd_interface, write(_, _, _)).Times(1).WillOnce(Return(-12));
    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    EXPECT_THAT(capacity_node.set_gpu_capacity(capacity), Eq(false));
}

TEST_F(GpuCapacityNodeTest, reads_freq_correctly) {
    static constexpr auto value = "100";
    testing::Sequence seq;
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .InSequence(seq)
            .WillOnce(Invoke([&](auto, void *buf, size_t len) {
                strncpy(static_cast<char *>(buf), value, len);
                return 3;
            }));
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .InSequence(seq)
            .WillOnce(Return(0));
    EXPECT_CALL(*mock_fd_interface, lseek(another_fake_fd, 0, SEEK_SET))
            .InSequence(seq)
            .WillOnce(Return(0));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    ASSERT_TRUE(frequency);
    EXPECT_THAT(*frequency, Eq(Frequency(100000)));
}

TEST_F(GpuCapacityNodeTest, reads_freq_correctly_partial) {
    static constexpr auto value = "100";
    int i = 0;
    testing::Sequence seq;
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(4)
            .WillRepeatedly(Invoke([&](auto, void *buf, size_t) {
                if (i >= 3) {
                    return 0;
                }
                auto c = reinterpret_cast<char *>(buf);
                *c = value[i++];
                return 1;
            }));
    EXPECT_CALL(*mock_fd_interface, lseek(another_fake_fd, 0, SEEK_SET)).WillOnce(Return(0));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    ASSERT_TRUE(frequency);
    EXPECT_THAT(*frequency, Eq(Frequency(100000)));
}

TEST_F(GpuCapacityNodeTest, reads_freq_correctly_full) {
    testing::Sequence seq;
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(1)
            .WillRepeatedly(Invoke([&](auto, void *buf, size_t size) {
                auto c = reinterpret_cast<char *>(buf);
                for (auto i = 0u; i < size; i++) {
                    c[i] = i < 3 ? '1' : '\0';
                }
                return size;
            }));
    EXPECT_CALL(*mock_fd_interface, lseek(another_fake_fd, 0, SEEK_SET)).WillOnce(Return(0));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    ASSERT_TRUE(frequency);
    EXPECT_THAT(*frequency, Eq(Frequency(111000)));
}

TEST_F(GpuCapacityNodeTest, read_failure) {
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _)).Times(1).WillOnce(Return(-1));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    EXPECT_FALSE(frequency);
}

TEST_F(GpuCapacityNodeTest, lseek_failure) {
    testing::Sequence seq;
    EXPECT_CALL(*mock_fd_interface, read(_, _, _)).InSequence(seq).WillOnce(Return(7));
    EXPECT_CALL(*mock_fd_interface, read(_, _, _)).InSequence(seq).WillOnce(Return(0));
    EXPECT_CALL(*mock_fd_interface, lseek(_, _, _)).InSequence(seq).WillOnce(Return(-1));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    EXPECT_FALSE(frequency);
}

TEST_F(GpuCapacityNodeTest, truncates_positive_floats) {
    static constexpr auto value = "1068.2";
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(2)
            .WillOnce(Invoke([&](auto, void *buf, size_t len) {
                strncpy(static_cast<char *>(buf), value, len);
                return 6;
            }))
            .WillOnce(Return(0));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    auto const frequency = capacity_node.gpu_frequency();
    ASSERT_TRUE(frequency);
    EXPECT_THAT(*frequency, Eq(Frequency(1068000)));
}

TEST_F(GpuCapacityNodeTest, nonsense_returned_from_frequency) {
    static constexpr auto value = "zappyzapzoo";
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(1)
            .WillOnce(Invoke([&](auto, void *buf, size_t len) {
                strncpy(static_cast<char *>(buf), value, len);
                return 0;
            }));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    EXPECT_FALSE(capacity_node.gpu_frequency());
}

TEST_F(GpuCapacityNodeTest, nonsense_returned_from_frequency2) {
    static constexpr auto value = "-1068";
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(1)
            .WillOnce(Invoke([&](auto, void *buf, size_t len) {
                strncpy(static_cast<char *>(buf), value, len);
                return 0;
            }));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    EXPECT_FALSE(capacity_node.gpu_frequency());
}

TEST_F(GpuCapacityNodeTest, nonsense_returned_from_frequency4) {
    static constexpr auto value = "0";
    EXPECT_CALL(*mock_fd_interface, read(another_fake_fd, _, _))
            .Times(1)
            .WillOnce(Invoke([&](auto, void *buf, size_t len) {
                strncpy(static_cast<char *>(buf), value, len);
                return 0;
            }));

    GpuCapacityNode capacity_node(std::make_unique<FdInterfaceWrapper>(mock_fd_interface), fake_fd,
                                  another_fake_fd, path);
    EXPECT_FALSE(capacity_node.gpu_frequency());
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
