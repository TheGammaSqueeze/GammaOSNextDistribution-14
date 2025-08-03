/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sstream>
#include <string>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cutils/properties.h>
#include <gtest/gtest.h>
#include <liblmkd_utils.h>
#include <log/log_properties.h>
#include <private/android_filesystem_config.h>

using namespace android::base;

#ifndef __NR_process_mrelease
#define __NR_process_mrelease 448
#endif

#define INKERNEL_MINFREE_PATH "/sys/module/lowmemorykiller/parameters/minfree"

#define LMKD_LOGCAT_MARKER "lowmemorykiller"
#define LMKD_KILL_TEMPLATE "Kill \'[^']*\' \\\(%d\\)"
#define LMKD_REAP_TEMPLATE "Process %d was reaped"
#define LMKD_REAP_FAIL_TEMPLATE "process_mrelease %d failed"

#define LMKD_KILL_LINE_START LMKD_LOGCAT_MARKER ": Kill"
#define LMKD_REAP_LINE_START LMKD_LOGCAT_MARKER ": Process"
#define LMKD_REAP_TIME_TEMPLATE LMKD_LOGCAT_MARKER ": Process %d was reaped in %ldms"
#define LMKD_REAP_MRELESE_ERR_MARKER ": process_mrelease"
#define LMKD_REAP_NO_PROCESS_TEMPLATE ": process_mrelease %d failed: No such process"

#define ONE_MB (1 << 20)

// Test constant parameters
#define OOM_ADJ_MAX 1000
#define ALLOC_STEP (5 * ONE_MB)
#define ALLOC_DELAY 200

// used to create ptr aliasing and prevent compiler optimizing the access
static volatile void* gptr;

class LmkdTest : public ::testing::Test {
  public:
    virtual void SetUp() {
        // test requirements
        if (getuid() != static_cast<unsigned>(AID_ROOT)) {
            GTEST_SKIP() << "Must be root, skipping test";
        }

        if (!__android_log_is_debuggable()) {
            GTEST_SKIP() << "Must be userdebug build, skipping test";
        }

        if (!access(INKERNEL_MINFREE_PATH, W_OK)) {
            GTEST_SKIP() << "Must not have kernel lowmemorykiller driver,"
                         << " skipping test";
        }

        // should be able to turn on lmkd debug information
        if (!property_get_bool("ro.lmk.debug", true)) {
            GTEST_SKIP() << "Can't run with ro.lmk.debug property set to 'false', skipping test";
        }

        // setup lmkd connection
        ASSERT_FALSE((sock = lmkd_connect()) < 0)
                << "Failed to connect to lmkd process, err=" << strerror(errno);

        // enable ro.lmk.debug if not already enabled
        if (!property_get_bool("ro.lmk.debug", false)) {
            EXPECT_EQ(property_set("ro.lmk.debug", "true"), 0);
            EXPECT_EQ(lmkd_update_props(sock), UPDATE_PROPS_SUCCESS)
                    << "Failed to reinitialize lmkd";
        }

        uid = getuid();
    }

    virtual void TearDown() {
        // drop lmkd connection
        close(sock);
    }

    void SetupChild(pid_t pid, int oomadj) {
        struct lmk_procprio params;

        params.pid = pid;
        params.uid = uid;
        params.oomadj = oomadj;
        params.ptype = PROC_TYPE_APP;
        ASSERT_FALSE(lmkd_register_proc(sock, &params) < 0)
                << "Failed to communicate with lmkd, err=" << strerror(errno);
        GTEST_LOG_(INFO) << "Target process " << pid << " launched";
        if (property_get_bool("ro.config.low_ram", false)) {
            ASSERT_FALSE(create_memcg(uid, pid) != 0)
                    << "Target process " << pid << " failed to create a cgroup";
        }
    }

    static std::string ExecCommand(const std::string& command) {
        FILE* fp = popen(command.c_str(), "r");
        std::string content;
        ReadFdToString(fileno(fp), &content);
        pclose(fp);
        return content;
    }

    static std::string ReadLogcat(const std::string& tag, const std::string& regex) {
        std::string cmd = "logcat -d -b all";
        if (!tag.empty()) {
            cmd += " -s \"" + tag + "\"";
        }
        if (!regex.empty()) {
            cmd += " -e \"" + regex + "\"";
        }
        return ExecCommand(cmd);
    }

    static size_t ConsumeMemory(size_t total_size, size_t step_size, size_t step_delay) {
        volatile void* ptr;
        size_t allocated_size = 0;

        while (allocated_size < total_size) {
            ptr = mmap(NULL, step_size, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, 0, 0);
            if (ptr != MAP_FAILED) {
                // create ptr aliasing to prevent compiler optimizing the access
                gptr = ptr;
                // make data non-zero
                memset((void*)ptr, (int)(allocated_size + 1), step_size);
                allocated_size += step_size;
            }
            usleep(step_delay);
        }
        return allocated_size;
    }

    static bool ParseProcSize(const std::string& line, long& rss, long& swap) {
        size_t pos = line.find("to free");
        if (pos == std::string::npos) {
            return false;
        }
        return sscanf(line.c_str() + pos, "to free %ldkB rss, %ldkB swap", &rss, &swap) == 2;
    }

    static bool ParseReapTime(const std::string& line, pid_t pid, long& reap_time) {
        int reap_pid;
        return sscanf(line.c_str(), LMKD_REAP_TIME_TEMPLATE, &reap_pid, &reap_time) == 2 &&
               reap_pid == pid;
    }

    static bool ParseReapNoProcess(const std::string& line, pid_t pid) {
        int reap_pid;
        return sscanf(line.c_str(), LMKD_REAP_NO_PROCESS_TEMPLATE, &reap_pid) == 1 &&
               reap_pid == pid;
    }

  private:
    int sock;
    uid_t uid;
};

TEST_F(LmkdTest, TargetReaping) {
    // test specific requirements
    if (syscall(__NR_process_mrelease, -1, 0) && errno == ENOSYS) {
        GTEST_SKIP() << "Must support process_mrelease syscall, skipping test";
    }

    // for a child to act as a target process
    pid_t pid = fork();
    ASSERT_FALSE(pid < 0) << "Failed to spawn a child process, err=" << strerror(errno);
    if (pid != 0) {
        // parent
        waitpid(pid, NULL, 0);
    } else {
        // child
        SetupChild(getpid(), OOM_ADJ_MAX);
        // allocate memory until killed
        ConsumeMemory((size_t)-1, ALLOC_STEP, ALLOC_DELAY);
        // should not reach here, child should be killed by OOM
        FAIL() << "Target process " << pid << " was not killed";
    }

    std::string regex = StringPrintf("((" LMKD_KILL_TEMPLATE ")|(" LMKD_REAP_TEMPLATE
                                     ")|(" LMKD_REAP_FAIL_TEMPLATE "))",
                                     pid, pid, pid);
    std::string logcat_out = ReadLogcat(LMKD_LOGCAT_MARKER ":I", regex);

    // find kill report
    size_t line_start = logcat_out.find(LMKD_KILL_LINE_START);
    ASSERT_TRUE(line_start != std::string::npos) << "Kill report is not found";
    size_t line_end = logcat_out.find('\n', line_start);
    std::string line = logcat_out.substr(
            line_start, line_end == std::string::npos ? std::string::npos : line_end - line_start);
    long rss, swap;
    ASSERT_TRUE(ParseProcSize(line, rss, swap)) << "Kill report format is invalid";

    // find reap duration report
    line_start = logcat_out.find(LMKD_REAP_LINE_START);
    if (line_start == std::string::npos) {
        // Target might have exited before reaping started
        line_start = logcat_out.find(LMKD_REAP_MRELESE_ERR_MARKER);

        ASSERT_TRUE(line_start != std::string::npos) << "Reaping time report is not found";

        line_end = logcat_out.find('\n', line_start);
        line = logcat_out.substr(line_start, line_end == std::string::npos ? std::string::npos
                                                                           : line_end - line_start);
        ASSERT_TRUE(ParseReapNoProcess(line, pid)) << "Failed to reap the target " << pid;
        return;
    }
    line_end = logcat_out.find('\n', line_start);
    line = logcat_out.substr(
            line_start, line_end == std::string::npos ? std::string::npos : line_end - line_start);
    long reap_time;
    ASSERT_TRUE(ParseReapTime(line, pid, reap_time) && reap_time >= 0)
            << "Reaping time report format is invalid";

    // occasionally the reaping happens quickly enough that it's reported as 0ms
    if (reap_time > 0) {
        double reclaim_speed = ((double)rss + swap) / reap_time;
        GTEST_LOG_(INFO) << "Reclaim speed " << reclaim_speed << "kB/ms (" << rss << "kB rss + "
                         << swap << "kB swap) / " << reap_time << "ms";
   }
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    InitLogging(argv, StderrLogger);
    return RUN_ALL_TESTS();
}
