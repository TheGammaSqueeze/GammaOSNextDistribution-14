/* Copyright (C) 2019 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <inttypes.h>
#include <linux/dma-buf.h>
#include <poll.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/types.h>

#include <filesystem>
#include <fstream>
#include <string>
#include <unordered_map>
#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <ion/ion.h>
#include <unistd.h>

#include <dmabufinfo/dmabuf_sysfs_stats.h>
#include <dmabufinfo/dmabufinfo.h>

using namespace ::android::dmabufinfo;
using namespace ::android::base;

namespace fs = std::filesystem;

#define MAX_HEAP_NAME 32
#define ION_HEAP_ANY_MASK (0x7fffffff)

struct ion_heap_data {
    char name[MAX_HEAP_NAME];
    __u32 type;
    __u32 heap_id;
    __u32 reserved0;
    __u32 reserved1;
    __u32 reserved2;
};

#ifndef DMA_BUF_SET_NAME
#define DMA_BUF_SET_NAME _IOW(DMA_BUF_BASE, 5, const char*)
#endif

class fd_sharer {
  public:
    fd_sharer();
    ~fd_sharer() { kill(); }

    bool ok() const { return child_pid > 0; }
    bool sendfd(int fd);
    bool kill();
    pid_t pid() const { return child_pid; }

  private:
    unique_fd parent_fd, child_fd;
    pid_t child_pid;

    void run();
};

fd_sharer::fd_sharer() : parent_fd{}, child_fd{}, child_pid{-1} {
    bool sp_ok = android::base::Socketpair(SOCK_STREAM, &parent_fd, &child_fd);
    if (!sp_ok) return;

    child_pid = fork();
    if (child_pid < 0) return;

    if (child_pid == 0) run();
}

bool fd_sharer::kill() {
    int err = ::kill(child_pid, SIGKILL);
    if (err < 0) return false;

    return ::waitpid(child_pid, nullptr, 0) == child_pid;
}

void fd_sharer::run() {
    while (true) {
        int fd;
        char unused = 0;

        iovec iov{};
        iov.iov_base = &unused;
        iov.iov_len = sizeof(unused);

        msghdr msg{};
        msg.msg_iov = &iov;
        msg.msg_iovlen = 1;

        char cmsg_buf[CMSG_SPACE(sizeof(fd))];
        msg.msg_control = cmsg_buf;
        msg.msg_controllen = sizeof(cmsg_buf);

        cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        cmsg->cmsg_len = CMSG_LEN(sizeof(fd));

        ssize_t s = TEMP_FAILURE_RETRY(recvmsg(child_fd, &msg, 0));
        if (s == -1) break;

        s = TEMP_FAILURE_RETRY(write(child_fd, &unused, sizeof(unused)));
        if (s == -1) break;
    }
}

bool fd_sharer::sendfd(int fd) {
    char unused = 0;

    iovec iov{};
    iov.iov_base = &unused;
    iov.iov_len = sizeof(unused);

    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    char cmsg_buf[CMSG_SPACE(sizeof(fd))];
    msg.msg_control = cmsg_buf;
    msg.msg_controllen = sizeof(cmsg_buf);

    cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(fd));

    int* fd_buf = reinterpret_cast<int*>(CMSG_DATA(cmsg));
    *fd_buf = fd;

    ssize_t s = TEMP_FAILURE_RETRY(sendmsg(parent_fd, &msg, 0));
    if (s == -1) return false;

    // The target process installs the fd into its fd table during recvmsg().
    // So if we return now, there's a brief window between sendfd() finishing
    // and libmemoryinfo actually seeing that the buffer has been shared.  This
    // window is just large enough to break tests.
    //
    // To work around this, wait for the target process to respond with a dummy
    // byte, with a timeout of 1 s.
    pollfd p{};
    p.fd = parent_fd;
    p.events = POLL_IN;
    int ready = poll(&p, 1, 1000);
    if (ready != 1) return false;

    s = TEMP_FAILURE_RETRY(read(parent_fd, &unused, sizeof(unused)));
    if (s == -1) return false;

    return true;
}

#define EXPECT_ONE_BUF_EQ(_bufptr, _name, _fdrefs, _maprefs, _expname, _count, _size) \
    do {                                                                              \
        EXPECT_EQ(_bufptr->name(), _name);                                            \
        EXPECT_EQ(_bufptr->fdrefs().size(), _fdrefs);                                 \
        EXPECT_EQ(_bufptr->maprefs().size(), _maprefs);                               \
        EXPECT_EQ(_bufptr->exporter(), _expname);                                     \
        EXPECT_EQ(_bufptr->count(), _count);                                          \
        EXPECT_EQ(_bufptr->size(), _size);                                            \
    } while (0)

#define EXPECT_PID_IN_FDREFS(_bufptr, _pid, _expect)                         \
    do {                                                                     \
        const std::unordered_map<pid_t, int>& _fdrefs = _bufptr->fdrefs();   \
        auto _ref = _fdrefs.find(_pid);                                      \
        EXPECT_EQ((_ref != _fdrefs.end()), _expect);                         \
    } while (0)

#define EXPECT_PID_IN_MAPREFS(_bufptr, _pid, _expect)                        \
    do {                                                                     \
        const std::unordered_map<pid_t, int>& _maprefs = _bufptr->maprefs(); \
        auto _ref = _maprefs.find(_pid);                                     \
        EXPECT_EQ((_ref != _maprefs.end()), _expect);                        \
    } while (0)

class DmaBufSysfsStatsParser : public ::testing::Test {
  public:
    virtual void SetUp() {
        fs::current_path(fs::temp_directory_path());
        buffer_stats_path = fs::current_path() / "buffers";
        ASSERT_TRUE(fs::create_directory(buffer_stats_path));
    }
    virtual void TearDown() { fs::remove_all(buffer_stats_path); }

    std::filesystem::path buffer_stats_path;
};

TEST_F(DmaBufSysfsStatsParser, TestReadDmaBufSysfsStats) {
    using android::base::StringPrintf;

    for (unsigned int inode_number = 74831; inode_number < 74841; inode_number++) {
        auto buffer_path = buffer_stats_path / StringPrintf("%u", inode_number);
        ASSERT_TRUE(fs::create_directories(buffer_path));

        auto buffer_size_path = buffer_path / "size";
        const std::string buffer_size = "4096";
        ASSERT_TRUE(android::base::WriteStringToFile(buffer_size, buffer_size_path));

        auto exp_name_path = buffer_path / "exporter_name";
        const std::string exp_name = "system";
        ASSERT_TRUE(android::base::WriteStringToFile(exp_name, exp_name_path));
    }

    DmabufSysfsStats stats;
    ASSERT_TRUE(GetDmabufSysfsStats(&stats, buffer_stats_path.c_str()));

    auto buffer_stats = stats.buffer_stats();
    ASSERT_EQ(buffer_stats.size(), 10UL);

    auto buf_info = buffer_stats[0];
    EXPECT_EQ(buf_info.inode, 74831UL);
    EXPECT_EQ(buf_info.exp_name, "system");
    EXPECT_EQ(buf_info.size, 4096UL);

    auto exporter_stats = stats.exporter_info();
    ASSERT_EQ(exporter_stats.size(), 1UL);
    auto exp_info = exporter_stats.find("system");
    ASSERT_TRUE(exp_info != exporter_stats.end());
    EXPECT_EQ(exp_info->second.size, 40960UL);
    EXPECT_EQ(exp_info->second.buffer_count, 10UL);

    auto total_size = stats.total_size();
    EXPECT_EQ(total_size, 40960UL);

    auto total_count = stats.total_count();
    EXPECT_EQ(total_count, 10UL);

    uint64_t total_exported;
    EXPECT_TRUE(GetDmabufTotalExportedKb(&total_exported, buffer_stats_path.c_str()));
    EXPECT_EQ(total_exported, 40UL);
}

class DmaBufProcessStatsTest : public ::testing::Test {
  public:
    virtual void SetUp() {
        fs::current_path(fs::temp_directory_path());
        dmabuf_sysfs_path = fs::current_path() / "buffers";
        procfs_path = fs::current_path() / "proc";
        ASSERT_TRUE(fs::create_directory(dmabuf_sysfs_path));
        ASSERT_TRUE(fs::create_directory(procfs_path));
        pid_path = procfs_path / android::base::StringPrintf("%d", pid);
        ASSERT_TRUE(fs::create_directories(pid_path));
        pid_fdinfo_path = pid_path / "fdinfo";
        ASSERT_TRUE(fs::create_directories(pid_fdinfo_path));
    }
    virtual void TearDown() {
        fs::remove_all(dmabuf_sysfs_path);
        fs::remove_all(procfs_path);
    }

    void AddFdInfo(unsigned int inode, unsigned int size, bool is_dmabuf) {
        std::string dmabuf_fdinfo = android::base::StringPrintf(
                "size:\t%u\ncount:\t1\nexp_name:\t%s\n", size, exporter.c_str());
        std::string fdinfo =
                android::base::StringPrintf("pos:\t21\nflags:\t0032\nmnt_id:\t02\nino:\t%u\n%s",
                                            inode, (is_dmabuf) ? dmabuf_fdinfo.c_str() : "");

        auto fdinfo_file_path = pid_fdinfo_path / android::base::StringPrintf("%d", fd++);
        ASSERT_TRUE(android::base::WriteStringToFile(fdinfo, fdinfo_file_path));
    }

    void AddSysfsDmaBufStats(unsigned int inode, unsigned int size, unsigned int mmap_count) {
        auto buffer_path = dmabuf_sysfs_path / android::base::StringPrintf("%u", inode);
        ASSERT_TRUE(fs::create_directory(buffer_path));

        auto size_path = buffer_path / "size";
        ASSERT_TRUE(android::base::WriteStringToFile(android::base::StringPrintf("%u", size),
                                                     size_path));

        auto mmap_count_path = buffer_path / "mmap_count";
        ASSERT_TRUE(android::base::WriteStringToFile(
                android::base::StringPrintf("%u", mmap_count), mmap_count_path));

        auto exporter_path = buffer_path / "exporter_name";
        ASSERT_TRUE(android::base::WriteStringToFile(exporter, exporter_path));
    }

    std::string CreateMapEntry(unsigned int inode, unsigned int size, bool is_dmabuf) {
        return android::base::StringPrintf("0000000000-%010x rw-s 00000000 00:08 %u %s", size,
                                           inode, (is_dmabuf) ? "/dmabuf:" : "/not/dmabuf/");
    }

    void AddMapEntries(std::vector<std::string> entries) {
        std::string maps_content = android::base::Join(entries, '\n');

        auto maps_file_path = pid_path / "maps";
        ASSERT_TRUE(android::base::WriteStringToFile(maps_content, maps_file_path));
    }

    std::filesystem::path dmabuf_sysfs_path;
    std::filesystem::path procfs_path;
    std::filesystem::path pid_path;
    std::filesystem::path pid_fdinfo_path;
    std::string exporter = "system_heap";
    int pid = 10;
    int fd = 0;
};

TEST_F(DmaBufProcessStatsTest, TestReadDmaBufInfo) {
    AddFdInfo(1, 1024, false);
    AddFdInfo(2, 2048, true);  // Dmabuf 1

    std::vector<std::string> map_entries;
    map_entries.emplace_back(CreateMapEntry(3, 1024, false));
    map_entries.emplace_back(CreateMapEntry(4, 1024, true));  // Dmabuf 2
    AddMapEntries(map_entries);

    AddSysfsDmaBufStats(2, 2048, 4);  // Dmabuf 1
    AddSysfsDmaBufStats(4, 1024, 1);  // Dmabuf 2

    std::vector<DmaBuffer> dmabufs;
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs, true, procfs_path, dmabuf_sysfs_path));

    ASSERT_EQ(dmabufs.size(), 2u);

    auto dmabuf1 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 2; });
    ASSERT_NE(dmabuf1, dmabufs.end());
    ASSERT_EQ(dmabuf1->size(), 2048u);
    ASSERT_EQ(dmabuf1->fdrefs().size(), 1u);
    ASSERT_EQ(dmabuf1->maprefs().size(), 0u);
    ASSERT_EQ(dmabuf1->total_refs(), 1u);
    ASSERT_EQ(dmabuf1->exporter(), exporter);

    auto dmabuf2 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 4; });
    ASSERT_NE(dmabuf2, dmabufs.end());
    ASSERT_EQ(dmabuf2->size(), 1024u);
    ASSERT_EQ(dmabuf2->fdrefs().size(), 0u);
    ASSERT_EQ(dmabuf2->maprefs().size(), 1u);
    ASSERT_EQ(dmabuf2->total_refs(), 1u);
    ASSERT_EQ(dmabuf2->exporter(), exporter);
}

TEST_F(DmaBufProcessStatsTest, TestReadDmaBufFdRefs) {
    AddFdInfo(1, 1024, false);
    AddFdInfo(2, 2048, true);  // Dmabuf 1
    AddFdInfo(2, 2048, true);  // Dmabuf 1
    AddFdInfo(3, 1024, true);  // Dmabuf 2

    std::vector<DmaBuffer> dmabufs;
    ASSERT_TRUE(ReadDmaBufFdRefs(pid, &dmabufs, procfs_path));
    ASSERT_EQ(dmabufs.size(), 2u);

    const auto& dmabuf1 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                       [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 2; });

    ASSERT_EQ(dmabuf1->size(), 2048u);
    ASSERT_EQ(dmabuf1->fdrefs().size(), 1u);  // Only one process has FDs to this buffer
    ASSERT_EQ(dmabuf1->maprefs().size(), 0u);
    ASSERT_EQ(dmabuf1->total_refs(), 2u);
    ASSERT_EQ(dmabuf1->exporter(), exporter);

    // Verify process has 2 FDs to this buffer
    ASSERT_NE(dmabuf1, dmabufs.end());
    const auto& fdrefs1 = dmabuf1->fdrefs();
    const auto& pid_fdrefs1 = fdrefs1.find(pid);
    ASSERT_NE(pid_fdrefs1, fdrefs1.end());
    ASSERT_EQ(pid_fdrefs1->second, 2);

    const auto& dmabuf2 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                       [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 3; });
    ASSERT_EQ(dmabuf2->size(), 1024u);
    ASSERT_EQ(dmabuf2->fdrefs().size(), 1u);  // Only one process has FDs to this buffer
    ASSERT_EQ(dmabuf2->maprefs().size(), 0u);
    ASSERT_EQ(dmabuf2->total_refs(), 1u);
    ASSERT_EQ(dmabuf2->exporter(), exporter);

    // Verify process only has 1 FD to this buffer
    ASSERT_NE(dmabuf2, dmabufs.end());
    const auto& fdrefs2 = dmabuf2->fdrefs();
    const auto& pid_fdrefs2 = fdrefs2.find(pid);
    ASSERT_NE(pid_fdrefs2, fdrefs2.end());
    ASSERT_EQ(pid_fdrefs2->second, 1);
}

TEST_F(DmaBufProcessStatsTest, TestReadDmaBufMapRefs) {
    std::vector<std::string> map_entries;
    map_entries.emplace_back(CreateMapEntry(1, 1024, false));
    map_entries.emplace_back(CreateMapEntry(2, 1024, true));  // Dmabuf 1
    map_entries.emplace_back(CreateMapEntry(2, 1024, true));  // Dmabuf 1
    map_entries.emplace_back(CreateMapEntry(3, 2048, true));  // Dmabuf 2
    AddMapEntries(map_entries);

    AddSysfsDmaBufStats(2, 1024, 2);  // Dmabuf 1
    AddSysfsDmaBufStats(3, 2048, 1);  // Dmabuf 2

    std::vector<DmaBuffer> dmabufs;
    ASSERT_TRUE(ReadDmaBufMapRefs(pid, &dmabufs, procfs_path, dmabuf_sysfs_path));
    ASSERT_EQ(dmabufs.size(), 2u);

    const auto& dmabuf1 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                       [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 2; });

    ASSERT_EQ(dmabuf1->size(), 1024u);
    ASSERT_EQ(dmabuf1->fdrefs().size(), 0u);
    ASSERT_EQ(dmabuf1->maprefs().size(), 1u);  // Only one process mapped this buffer
    ASSERT_EQ(dmabuf1->total_refs(), 2u);
    ASSERT_EQ(dmabuf1->exporter(), exporter);

    // Verify process mapped this buffer twice
    ASSERT_NE(dmabuf1, dmabufs.end());
    const auto& maprefs1 = dmabuf1->maprefs();
    const auto& pid_maprefs1 = maprefs1.find(pid);
    ASSERT_NE(pid_maprefs1, maprefs1.end());
    ASSERT_EQ(pid_maprefs1->second, 2);

    const auto& dmabuf2 = std::find_if(dmabufs.begin(), dmabufs.end(),
                                       [](const DmaBuffer& dmabuf) { return dmabuf.inode() == 3; });
    ASSERT_EQ(dmabuf2->size(), 2048u);
    ASSERT_EQ(dmabuf2->fdrefs().size(), 0u);
    ASSERT_EQ(dmabuf2->maprefs().size(), 1u);  // Only one process mapped this buffer
    ASSERT_EQ(dmabuf2->total_refs(), 1u);
    ASSERT_EQ(dmabuf2->exporter(), exporter);

    // Verify process mapped this buffer only once
    ASSERT_NE(dmabuf2, dmabufs.end());
    const auto& maprefs2 = dmabuf2->maprefs();
    const auto& pid_maprefs2 = maprefs2.find(pid);
    ASSERT_NE(pid_maprefs2, maprefs2.end());
    ASSERT_EQ(pid_maprefs2->second, 1);
}

class DmaBufTester : public ::testing::Test {
  public:
    DmaBufTester() : ion_fd(ion_open()), ion_heap_mask(get_ion_heap_mask()) {}

    ~DmaBufTester() {
        if (ion_fd >= 0) {
            ion_close(ion_fd);
        }
    }

    bool is_valid() { return (ion_fd >= 0 && ion_heap_mask > 0); }

    bool is_using_dmabuf_heaps() {
        // We can verify that a device is running on dmabuf-heaps by checking that
        // the `dev/ion` is missing, while `dev/dma_heap` is present.
        // https://source.android.com/docs/core/architecture/kernel/dma-buf-heaps
        return !fs::is_directory("/dev/ion") && fs::is_directory("/dev/dma_heap");
    }

    unique_fd allocate(uint64_t size, const std::string& name) {
        int fd;
        int err = ion_alloc_fd(ion_fd, size, 0, ion_heap_mask, 0, &fd);
        if (err < 0) {
            printf("Failed ion_alloc_fd, return value: %d\n", err);
            return unique_fd{};
        }

        if (!name.empty()) {
            if (ioctl(fd, DMA_BUF_SET_NAME, name.c_str()) == -1) {
                printf("Failed ioctl(DMA_BUF_SET_NAME): %s\n", strerror(errno));
                close(fd);
                return unique_fd{};
            }
        }

        return unique_fd{fd};
    }

    void readAndCheckDmaBuffer(std::vector<DmaBuffer>* dmabufs, pid_t pid, const std::string name,
                               size_t fdrefs_size, size_t maprefs_size, const std::string exporter,
                               size_t refcount, uint64_t buf_size, bool expectFdrefs,
                               bool expectMapRefs) {
        EXPECT_TRUE(ReadDmaBufInfo(pid, dmabufs));
        EXPECT_EQ(dmabufs->size(), 1UL);
        EXPECT_ONE_BUF_EQ(dmabufs->begin(), name, fdrefs_size, maprefs_size, exporter, refcount,
                          buf_size);
        // Make sure the buffer has the right pid too.
        EXPECT_PID_IN_FDREFS(dmabufs->begin(), pid, expectFdrefs);
        EXPECT_PID_IN_MAPREFS(dmabufs->begin(), pid, expectMapRefs);
    }

    bool checkPidRef(DmaBuffer& dmabuf, pid_t pid, int expectFdrefs) {
        int fdrefs = dmabuf.fdrefs().find(pid)->second;
        return fdrefs == expectFdrefs;
    }

  private:
    int get_ion_heap_mask() {
        if (ion_fd < 0) {
            return 0;
        }

        if (ion_is_legacy(ion_fd)) {
            // Since ION is still in staging, we've seen that the heap mask ids are also
            // changed across kernels for some reason. So, here we basically ask for a buffer
            // from _any_ heap.
            return ION_HEAP_ANY_MASK;
        }

        int cnt;
        int err = ion_query_heap_cnt(ion_fd, &cnt);
        if (err < 0) {
            return err;
        }

        std::vector<ion_heap_data> heaps;
        heaps.resize(cnt);
        err = ion_query_get_heaps(ion_fd, cnt, &heaps[0]);
        if (err < 0) {
            return err;
        }

        unsigned int ret = 0;
        for (auto& it : heaps) {
            if (!strcmp(it.name, "ion_system_heap")) {
                ret |= (1 << it.heap_id);
            }
        }

        return ret;
    }

    int ion_fd;
    const int ion_heap_mask;
};

TEST_F(DmaBufTester, TestFdRef) {
    // Test if a dma buffer is found while the corresponding file descriptor
    // is open

    if (is_using_dmabuf_heaps()) {
        GTEST_SKIP();
    }

    ASSERT_TRUE(is_valid());
    pid_t pid = getpid();
    std::vector<DmaBuffer> dmabufs;
    {
        // Allocate one buffer and make sure the library can see it
        unique_fd buf = allocate(4096, "dmabuftester-4k");
        ASSERT_GT(buf, 0) << "Allocated buffer is invalid";
        ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));

        EXPECT_EQ(dmabufs.size(), 1UL);
        EXPECT_ONE_BUF_EQ(dmabufs.begin(), "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL);

        // Make sure the buffer has the right pid too.
        EXPECT_PID_IN_FDREFS(dmabufs.begin(), pid, true);
    }

    // Now make sure the buffer has disappeared
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));
    EXPECT_TRUE(dmabufs.empty());
}

TEST_F(DmaBufTester, TestMapRef) {
    // Test to make sure we can find a buffer if the fd is closed but the buffer
    // is mapped

    if (is_using_dmabuf_heaps()) {
        GTEST_SKIP();
    }

    ASSERT_TRUE(is_valid());
    pid_t pid = getpid();
    std::vector<DmaBuffer> dmabufs;
    {
        // Allocate one buffer and make sure the library can see it
        unique_fd buf = allocate(4096, "dmabuftester-4k");
        ASSERT_GT(buf, 0) << "Allocated buffer is invalid";
        auto ptr = mmap(0, 4096, PROT_READ, MAP_SHARED, buf, 0);
        ASSERT_NE(ptr, MAP_FAILED);
        ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));

        EXPECT_EQ(dmabufs.size(), 1UL);
        EXPECT_ONE_BUF_EQ(dmabufs.begin(), "dmabuftester-4k", 1UL, 1UL, "ion", 2UL, 4096ULL);

        // Make sure the buffer has the right pid too.
        EXPECT_PID_IN_FDREFS(dmabufs.begin(), pid, true);
        EXPECT_PID_IN_MAPREFS(dmabufs.begin(), pid, true);

        // close the file descriptor and re-read the stats
        buf.reset(-1);
        ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));

        EXPECT_EQ(dmabufs.size(), 1UL);
        EXPECT_ONE_BUF_EQ(dmabufs.begin(), "<unknown>", 0UL, 1UL, "<unknown>", 0UL, 4096ULL);

        EXPECT_PID_IN_FDREFS(dmabufs.begin(), pid, false);
        EXPECT_PID_IN_MAPREFS(dmabufs.begin(), pid, true);

        // unmap the bufer and lose all references
        munmap(ptr, 4096);
    }

    // Now make sure the buffer has disappeared
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));
    EXPECT_TRUE(dmabufs.empty());
}

TEST_F(DmaBufTester, TestSharedfd) {
    // Each time a shared buffer is received over a socket, the remote process
    // will take an extra reference on it.

    if (is_using_dmabuf_heaps()) {
        GTEST_SKIP();
    }

    ASSERT_TRUE(is_valid());

    pid_t pid = getpid();
    std::vector<DmaBuffer> dmabufs;
    {
        fd_sharer sharer{};
        ASSERT_TRUE(sharer.ok());
        // Allocate one buffer and make sure the library can see it
        unique_fd buf = allocate(4096, "dmabuftester-4k");
        ASSERT_GT(buf, 0) << "Allocated buffer is invalid";
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);

        ASSERT_TRUE(sharer.sendfd(buf));
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 2UL, 4096ULL, true,
                              false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], pid, 1));
        readAndCheckDmaBuffer(&dmabufs, sharer.pid(), "dmabuftester-4k", 1UL, 0UL, "ion", 2UL,
                              4096ULL, true, false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], sharer.pid(), 1));

        ASSERT_TRUE(sharer.sendfd(buf));
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 3UL, 4096ULL, true,
                              false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], pid, 1));
        readAndCheckDmaBuffer(&dmabufs, sharer.pid(), "dmabuftester-4k", 1UL, 0UL, "ion", 3UL,
                              4096ULL, true, false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], sharer.pid(), 2));

        ASSERT_TRUE(sharer.kill());
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);
    }

    // Now make sure the buffer has disappeared
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));
    EXPECT_TRUE(dmabufs.empty());
}

TEST_F(DmaBufTester, DupFdTest) {
    // dup()ing an fd will make this process take an extra reference on the
    // shared buffer.

    if (is_using_dmabuf_heaps()) {
        GTEST_SKIP();
    }

    ASSERT_TRUE(is_valid());

    pid_t pid = getpid();
    std::vector<DmaBuffer> dmabufs;
    {
        // Allocate one buffer and make sure the library can see it
        unique_fd buf = allocate(4096, "dmabuftester-4k");
        ASSERT_GT(buf, 0) << "Allocated buffer is invalid";
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);

        unique_fd buf2{dup(buf)};
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 2UL, 4096ULL, true,
                              false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], pid, 2));

        close(buf2.release());
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);
        EXPECT_TRUE(checkPidRef(dmabufs[0], pid, 1));
    }

    // Now make sure the buffer has disappeared
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));
    EXPECT_TRUE(dmabufs.empty());
}

TEST_F(DmaBufTester, ForkTest) {
    // fork()ing a child will cause the child to automatically take a reference
    // on any existing shared buffers.

    if (is_using_dmabuf_heaps()) {
        GTEST_SKIP();
    }

    ASSERT_TRUE(is_valid());

    pid_t pid = getpid();
    std::vector<DmaBuffer> dmabufs;
    {
        // Allocate one buffer and make sure the library can see it
        unique_fd buf = allocate(4096, "dmabuftester-4k");
        ASSERT_GT(buf, 0) << "Allocated buffer is invalid";
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);
        fd_sharer sharer{};
        ASSERT_TRUE(sharer.ok());
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 2UL, 4096ULL, true,
                              false);
        readAndCheckDmaBuffer(&dmabufs, sharer.pid(), "dmabuftester-4k", 1UL, 0UL, "ion", 2UL,
                              4096ULL, true, false);
        ASSERT_TRUE(sharer.kill());
        readAndCheckDmaBuffer(&dmabufs, pid, "dmabuftester-4k", 1UL, 0UL, "ion", 1UL, 4096ULL, true,
                              false);
    }

    // Now make sure the buffer has disappeared
    ASSERT_TRUE(ReadDmaBufInfo(pid, &dmabufs));
    EXPECT_TRUE(dmabufs.empty());
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ::android::base::InitLogging(argv, android::base::StderrLogger);
    return RUN_ALL_TESTS();
}
