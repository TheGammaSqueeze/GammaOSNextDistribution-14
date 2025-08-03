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
#include <chrono>
#include <condition_variable>
#include <filesystem>
#include <mutex>
#include <string>
#include <thread>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <bpf/BpfUtils.h>
#include <gtest/gtest.h>
#include <poll.h>
#include <signal.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/sysinfo.h>
#include <unistd.h>

#include <BpfSyscallWrappers.h>

#include <memevents/memevents.h>
#include <memevents/memevents_test.h>

using namespace ::android::base;
using namespace ::android::bpf::memevents;

using android::bpf::isAtLeastKernelVersion;

namespace fs = std::filesystem;

static const MemEventClient mem_test_client = MemEventClient::TEST_CLIENT;
static const int page_size = getpagesize();
static const bool isBpfRingBufferSupported = isAtLeastKernelVersion(5, 8, 0);
static const std::string bpfRbsPaths[MemEventClient::NR_CLIENTS] = {
        MEM_EVENTS_AMS_RB, MEM_EVENTS_LMKD_RB, MEM_EVENTS_TEST_RB};
static const std::string testBpfSkfilterProgPaths[NR_MEM_EVENTS] = {
        MEM_EVENTS_TEST_OOM_KILL_TP, MEM_EVENTS_TEST_DIRECT_RECLAIM_START_TP,
        MEM_EVENTS_TEST_DIRECT_RECLAIM_END_TP};
static const std::filesystem::path sysrq_trigger_path = "proc/sysrq-trigger";

/*
 * Test suite to test on devices that don't support BPF, kernel <= 5.8.
 * We allow for the listener to iniailize gracefully, but every public API will
 * return false/fail.
 */
class MemEventListenerUnsupportedKernel : public ::testing::Test {
  protected:
    MemEventListener memevent_listener = MemEventListener(mem_test_client);

    static void SetUpTestSuite() {
        if (isBpfRingBufferSupported) {
            GTEST_SKIP()
                    << "BPF ring buffers is supported on this kernel, running alternative tests";
        }
    }

    void TearDown() override { memevent_listener.deregisterAllEvents(); }
};

/*
 * Listener shouldn't fail when initializing on a kernel that doesn't support BPF.
 */
TEST_F(MemEventListenerUnsupportedKernel, initialize_invalid_client) {
    std::unique_ptr<MemEventListener> listener =
            std::make_unique<MemEventListener>(MemEventClient::AMS);
    ASSERT_TRUE(listener) << "Failed to initialize listener on older kernel";
}

/*
 * Register will fail when running on a older kernel, even when we pass a valid event type.
 */
TEST_F(MemEventListenerUnsupportedKernel, fail_to_register) {
    ASSERT_FALSE(memevent_listener.registerEvent(MEM_EVENT_OOM_KILL))
            << "Listener should fail to register valid event type on an unsupported kernel";
    ASSERT_FALSE(memevent_listener.registerEvent(NR_MEM_EVENTS))
            << "Listener should fail to register invalid event type";
}

/*
 * Listen will fail when running on a older kernel.
 * The listen() function always checks first if we are running on an older kernel,
 * therefore we don't need to register for an event before trying to call listen.
 */
TEST_F(MemEventListenerUnsupportedKernel, fail_to_listen) {
    ASSERT_FALSE(memevent_listener.listen()) << "listen() should fail on unsupported kernel";
}

/*
 * Just like the other APIs, deregister will return false immediately on an older
 * kernel.
 */
TEST_F(MemEventListenerUnsupportedKernel, fail_to_unregister_event) {
    ASSERT_FALSE(memevent_listener.deregisterEvent(MEM_EVENT_OOM_KILL))
            << "Listener should fail to deregister valid event type on an older kernel";
    ASSERT_FALSE(memevent_listener.deregisterEvent(NR_MEM_EVENTS))
            << "Listener should fail to deregister invalid event type, regardless of kernel "
               "version";
}

/*
 * The `getMemEvents()` API should fail on an older kernel.
 */
TEST_F(MemEventListenerUnsupportedKernel, fail_to_get_mem_events) {
    std::vector<mem_event_t> mem_events;
    ASSERT_FALSE(memevent_listener.getMemEvents(mem_events))
            << "Fetching memory events should fail on an older kernel";
}

/*
 * The `getRingBufferFd()` API should fail on an older kernel
 */
TEST_F(MemEventListenerUnsupportedKernel, fail_to_get_rb_fd) {
    ASSERT_LT(memevent_listener.getRingBufferFd(), 0)
            << "Fetching bpf-rb file descriptor should fail on an older kernel";
}

/*
 * Test suite verifies that all the BPF programs and ring buffers are loaded.
 */
class MemEventsBpfSetupTest : public ::testing::Test {
  protected:
    static void SetUpTestSuite() {
        if (!isBpfRingBufferSupported) {
            GTEST_SKIP() << "BPF ring buffers not supported in kernels below 5.8";
        }
    }
};

/*
 * Verify that all the ams bpf-programs are loaded.
 */
TEST_F(MemEventsBpfSetupTest, loaded_ams_progs) {
    ASSERT_TRUE(std::filesystem::exists(MEM_EVENTS_AMS_OOM_MARK_VICTIM_TP))
            << "Failed to find ams mark_victim bpf-program";
}

/*
 * Verify that all the lmkd bpf-programs are loaded.
 */
TEST_F(MemEventsBpfSetupTest, loaded_lmkd_progs) {
    ASSERT_TRUE(std::filesystem::exists(MEM_EVENTS_LMKD_VMSCAN_DR_BEGIN_TP))
            << "Failed to find lmkd direct_reclaim_begin bpf-program";
    ASSERT_TRUE(std::filesystem::exists(MEM_EVENTS_LMKD_VMSCAN_DR_END_TP))
            << "Failed to find lmkd direct_reclaim_end bpf-program";
}

/*
 * Verify that all the memevents test bpf-skfilter-programs are loaded.
 */
TEST_F(MemEventsBpfSetupTest, loaded_test_skfilter_progs) {
    for (int i = 0; i < NR_MEM_EVENTS; i++) {
        ASSERT_TRUE(std::filesystem::exists(testBpfSkfilterProgPaths[i]))
                << "Failed to find testing bpf-prog: " << testBpfSkfilterProgPaths[i];
    }
}

/*
 * Verify that all [bpf] ring buffer's are loaded.
 * We expect to have at least 1 ring buffer for each client in `MemEventClient`.
 */
TEST_F(MemEventsBpfSetupTest, loaded_ring_buffers) {
    for (int i = 0; i < MemEventClient::NR_CLIENTS; i++) {
        ASSERT_TRUE(std::filesystem::exists(bpfRbsPaths[i]))
                << "Failed to find bpf ring-buffer: " << bpfRbsPaths[i];
    }
}

class MemEventsListenerTest : public ::testing::Test {
  protected:
    MemEventListener memevent_listener = MemEventListener(mem_test_client);

    static void SetUpTestSuite() {
        if (!isBpfRingBufferSupported) {
            GTEST_SKIP() << "BPF ring buffers not supported in kernels below 5.8";
        }
    }

    void TearDown() override { memevent_listener.deregisterAllEvents(); }
};

/*
 * MemEventListener should fail, through a `std::abort()`, when attempted to initialize
 * with an invalid `MemEventClient`. By passing `MemEventClient::NR_CLIENTS`, and attempting
 * to convert/pass `-1` as a client, we expect the listener initialization to fail.
 */
TEST_F(MemEventsListenerTest, initialize_invalid_client) {
    EXPECT_DEATH(MemEventListener listener(MemEventClient::NR_CLIENTS), "");
    EXPECT_DEATH(MemEventListener listener(static_cast<MemEventClient>(-1)), "");
}

/*
 * MemEventListener should fail when a valid, non-testing, client tries to initialize
 * by passing the optional test flag.
 */
TEST_F(MemEventsListenerTest, initialize_valid_client_with_test_flag) {
    for (int i = 0; i < MemEventClient::TEST_CLIENT; i++) {
        const MemEventClient valid_client = static_cast<MemEventClient>(i);
        EXPECT_DEATH(MemEventListener listener(valid_client, true), "")
                << "Only test client is allowed to set the test flag to true";
    }
}

/*
 * MemEventListener should NOT fail when initializing for all valid `MemEventClient`.
 * We considered a `MemEventClient` valid if its between 0 and MemEventClient::NR_CLIENTS.
 */
TEST_F(MemEventsListenerTest, initialize_valid_clients) {
    std::unique_ptr<MemEventListener> listener;
    for (int i = 0; i < MemEventClient::NR_CLIENTS; i++) {
        const MemEventClient client = static_cast<MemEventClient>(i);
        listener = std::make_unique<MemEventListener>(client);
        ASSERT_TRUE(listener) << "MemEventListener failed to initialize with valid client value: "
                              << client;
    }
}

/*
 * MemEventClient base client should equal to AMS client.
 */
TEST_F(MemEventsListenerTest, base_client_equal_ams_client) {
    ASSERT_EQ(static_cast<int>(MemEventClient::BASE), static_cast<int>(MemEventClient::AMS))
            << "Base client should be equal to AMS client";
}

/*
 * Validate `registerEvent()` fails with values >= `NR_MEM_EVENTS`.
 */
TEST_F(MemEventsListenerTest, register_event_invalid_values) {
    ASSERT_FALSE(memevent_listener.registerEvent(NR_MEM_EVENTS));
    ASSERT_FALSE(memevent_listener.registerEvent(NR_MEM_EVENTS + 1));
    ASSERT_FALSE(memevent_listener.registerEvent(-1));
}

/*
 * Validate that `registerEvent()` always returns true when we try registering
 * the same [valid] event/value.
 */
TEST_F(MemEventsListenerTest, register_event_repeated_event) {
    const int event_type = MEM_EVENT_OOM_KILL;
    ASSERT_TRUE(memevent_listener.registerEvent(event_type));
    ASSERT_TRUE(memevent_listener.registerEvent(event_type));
    ASSERT_TRUE(memevent_listener.registerEvent(event_type));
}

/*
 * Validate that `registerEvent()` is able to register all the `MEM_EVENT_*` values
 * from `bpf_types.h`.
 */
TEST_F(MemEventsListenerTest, register_event_valid_values) {
    for (unsigned int i = 0; i < NR_MEM_EVENTS; i++)
        ASSERT_TRUE(memevent_listener.registerEvent(i)) << "Failed to register event: " << i;
}

/*
 * `listen()` should return false when no events have been registered.
 */
TEST_F(MemEventsListenerTest, listen_no_registered_events) {
    ASSERT_FALSE(memevent_listener.listen());
}

/*
 * Validate `deregisterEvent()` fails with values >= `NR_MEM_EVENTS`.
 * Exactly like `register_event_invalid_values` test.
 */
TEST_F(MemEventsListenerTest, deregister_event_invalid_values) {
    ASSERT_FALSE(memevent_listener.deregisterEvent(NR_MEM_EVENTS));
    ASSERT_FALSE(memevent_listener.deregisterEvent(NR_MEM_EVENTS + 1));
    ASSERT_FALSE(memevent_listener.deregisterEvent(-1));
}

/*
 * Validate that `deregisterEvent()` always returns true when we try
 * deregistering the same [valid] event/value.
 */
TEST_F(MemEventsListenerTest, deregister_repeated_event) {
    const int event_type = MEM_EVENT_DIRECT_RECLAIM_BEGIN;
    ASSERT_TRUE(memevent_listener.registerEvent(event_type));
    ASSERT_TRUE(memevent_listener.deregisterEvent(event_type));
    ASSERT_TRUE(memevent_listener.deregisterEvent(event_type));
}

/*
 * Verify that the `deregisterEvent()` will return true
 * when we deregister a non-registered, valid, event.
 */
TEST_F(MemEventsListenerTest, deregister_unregistered_event) {
    ASSERT_TRUE(memevent_listener.deregisterEvent(MEM_EVENT_DIRECT_RECLAIM_END));
}

/*
 * Validate that the `deregisterAllEvents()` closes all the registered
 * events.
 */
TEST_F(MemEventsListenerTest, deregister_all_events) {
    ASSERT_TRUE(memevent_listener.registerEvent(MEM_EVENT_OOM_KILL));
    ASSERT_TRUE(memevent_listener.registerEvent(MEM_EVENT_DIRECT_RECLAIM_BEGIN));
    memevent_listener.deregisterAllEvents();
    ASSERT_FALSE(memevent_listener.listen())
            << "Expected to fail since we are not registered to any events";
}

/*
 * Validating that `MEM_EVENT_BASE` is equal to `MEM_EVENT_OOM_KILL`.
 */
TEST_F(MemEventsListenerTest, base_and_oom_events_are_equal) {
    ASSERT_EQ(MEM_EVENT_OOM_KILL, MEM_EVENT_BASE)
            << "MEM_EVENT_BASE should be equal to MEM_EVENT_OOM_KILL";
}

/*
 * Validate that `getRingBufferFd()` returns a valid file descriptor.
 */
TEST_F(MemEventsListenerTest, get_client_rb_fd) {
    ASSERT_GE(memevent_listener.getRingBufferFd(), 0)
            << "Failed to get a valid bpf-rb file descriptor";
}

class MemEventsListenerBpf : public ::testing::Test {
  private:
    android::base::unique_fd mProgram;

    void setUpProgram(unsigned int event_type) {
        ASSERT_TRUE(event_type < NR_MEM_EVENTS) << "Invalid event type provided";

        int bpf_fd = android::bpf::retrieveProgram(testBpfSkfilterProgPaths[event_type].c_str());
        ASSERT_NE(bpf_fd, -1) << "Retrieve bpf program failed with prog path: "
                              << testBpfSkfilterProgPaths[event_type];
        mProgram.reset(bpf_fd);

        ASSERT_GE(mProgram.get(), 0)
                << testBpfSkfilterProgPaths[event_type] << " was either not found or inaccessible.";
    }

    /*
     * Always call this after `setUpProgram()`, in order to make sure that the
     * correct `mProgram` was set.
     */
    void RunProgram(unsigned int event_type) {
        errno = 0;
        switch (event_type) {
            case MEM_EVENT_OOM_KILL:
                struct mark_victim_args mark_victim_fake_args;
                android::bpf::runProgram(mProgram, &mark_victim_fake_args,
                                         sizeof(mark_victim_fake_args));
                break;
            case MEM_EVENT_DIRECT_RECLAIM_BEGIN:
                struct direct_reclaim_begin_args dr_begin_fake_args;
                android::bpf::runProgram(mProgram, &dr_begin_fake_args, sizeof(dr_begin_fake_args));
                break;
            case MEM_EVENT_DIRECT_RECLAIM_END:
                struct direct_reclaim_end_args dr_end_fake_args;
                android::bpf::runProgram(mProgram, &dr_end_fake_args, sizeof(dr_end_fake_args));
                break;
            default:
                FAIL() << "Invalid event type provided";
        }
        EXPECT_EQ(errno, 0);
    }

  protected:
    MemEventListener mem_listener = MemEventListener(mem_test_client);

    static void SetUpTestSuite() {
        if (!isAtLeastKernelVersion(5, 8, 0)) {
            GTEST_SKIP() << "BPF ring buffers not supported below 5.8";
        }
    }

    void TearDown() override { mem_listener.deregisterAllEvents(); }

    /*
     * Helper function to insert mocked data into the testing [bpf] ring buffer.
     * This will trigger the `listen()` if its registered to the given `event_type`.
     */
    void setMockDataInRb(mem_event_type_t event_type) {
        setUpProgram(event_type);
        RunProgram(event_type);
    }

    /*
     * Test that the `listen()` returns true.
     * We setup some mocked event data into the testing [bpf] ring-buffer, to make
     * sure the `listen()` is triggered.
     */
    void testListenEvent(unsigned int event_type) {
        ASSERT_TRUE(event_type < NR_MEM_EVENTS) << "Invalid event type provided";

        setMockDataInRb(event_type);

        ASSERT_TRUE(mem_listener.listen(5000));  // 5 second timeout
    }
};

/*
 * Validate that `listen()` is triggered when we the bpf-rb receives
 * a OOM event.
 */
TEST_F(MemEventsListenerBpf, listener_bpf_oom_kill) {
    const mem_event_type_t event_type = MEM_EVENT_OOM_KILL;

    ASSERT_TRUE(mem_listener.registerEvent(event_type));
    testListenEvent(event_type);

    std::vector<mem_event_t> mem_events;
    ASSERT_TRUE(mem_listener.getMemEvents(mem_events)) << "Failed fetching events";
    ASSERT_FALSE(mem_events.empty()) << "Expected for mem_events to have at least 1 mocked event";
    ASSERT_EQ(mem_events[0].type, event_type) << "Didn't receive a OOM event";

    /*
     * This values are set inside the testing prog `memevents_test.h`. These values can't be passed
     * from the test to the bpf-prog.
     */
    ASSERT_EQ(mem_events[0].event_data.oom_kill.pid, mocked_oom_event.event_data.oom_kill.pid)
            << "Didn't receive expected PID";
    ASSERT_EQ(mem_events[0].event_data.oom_kill.uid, mocked_oom_event.event_data.oom_kill.uid)
            << "Didn't receive expected UID";
    ASSERT_EQ(mem_events[0].event_data.oom_kill.oom_score_adj,
              mocked_oom_event.event_data.oom_kill.oom_score_adj)
            << "Didn't receive expected OOM score";
    ASSERT_EQ(strcmp(mem_events[0].event_data.oom_kill.process_name,
                     mocked_oom_event.event_data.oom_kill.process_name),
              0)
            << "Didn't receive expected process name";
}

/*
 * Validate that `listen()` is triggered when we the bpf-rb receives
 * a direct reclain start event.
 */
TEST_F(MemEventsListenerBpf, listener_bpf_direct_reclaim_begin) {
    const mem_event_type_t event_type = MEM_EVENT_DIRECT_RECLAIM_BEGIN;

    ASSERT_TRUE(mem_listener.registerEvent(event_type));
    testListenEvent(event_type);

    std::vector<mem_event_t> mem_events;
    ASSERT_TRUE(mem_listener.getMemEvents(mem_events)) << "Failed fetching events";
    ASSERT_FALSE(mem_events.empty()) << "Expected for mem_events to have at least 1 mocked event";
    ASSERT_EQ(mem_events[0].type, event_type) << "Didn't receive a direct reclaim begin event";
}

/*
 * Validate that `listen()` is triggered when we the bpf-rb receives
 * a direct reclain end event.
 */
TEST_F(MemEventsListenerBpf, listener_bpf_direct_reclaim_end) {
    const mem_event_type_t event_type = MEM_EVENT_DIRECT_RECLAIM_END;

    ASSERT_TRUE(mem_listener.registerEvent(event_type));
    testListenEvent(event_type);

    std::vector<mem_event_t> mem_events;
    ASSERT_TRUE(mem_listener.getMemEvents(mem_events)) << "Failed fetching events";
    ASSERT_FALSE(mem_events.empty()) << "Expected for mem_events to have at least 1 mocked event";
    ASSERT_EQ(mem_events[0].type, event_type) << "Didn't receive a direct reclaim end event";
}

/*
 * `listen()` should timeout, and return false, when a memory event that
 * we are not registered for is triggered.
 */
TEST_F(MemEventsListenerBpf, no_register_events_listen_fails) {
    const mem_event_type_t event_type = MEM_EVENT_DIRECT_RECLAIM_END;
    setMockDataInRb(event_type);
    ASSERT_FALSE(mem_listener.listen(5000));  // 5 second timeout
}

/*
 * `getMemEvents()` should return an empty list, when a memory event that
 * we are not registered for, is triggered.
 */
TEST_F(MemEventsListenerBpf, getMemEvents_no_register_events) {
    const mem_event_type_t event_type = MEM_EVENT_OOM_KILL;
    setMockDataInRb(event_type);

    std::vector<mem_event_t> mem_events;
    ASSERT_TRUE(mem_listener.getMemEvents(mem_events)) << "Failed fetching events";
    ASSERT_TRUE(mem_events.empty());
}

/*
 * Verify that the listener receives a notification when:
 * 1. We start listening
 * 2. Memory event is added in the bpf ring-buffer
 * 3. Listening is notified of the new event.
 */
TEST_F(MemEventsListenerBpf, listen_then_create_event) {
    const mem_event_type_t event_type = MEM_EVENT_DIRECT_RECLAIM_BEGIN;
    std::mutex mtx;
    std::condition_variable cv;
    bool didReceiveEvent = false;

    ASSERT_TRUE(mem_listener.registerEvent(event_type));

    std::thread t([&] {
        bool listen_result = mem_listener.listen(10000);
        std::lock_guard lk(mtx);
        didReceiveEvent = listen_result;
        cv.notify_one();
    });

    setMockDataInRb(event_type);

    std::unique_lock lk(mtx);
    cv.wait_for(lk, std::chrono::seconds(10), [&] { return didReceiveEvent; });
    ASSERT_TRUE(didReceiveEvent) << "Listen never received a memory event notification";
    t.join();
}

/*
 * Similarly to `listen_then_create_event`, but instead of using
 * `listen()`, we want to poll from `getRingBufferFd()` value.
 */
TEST_F(MemEventsListenerBpf, getRb_poll_and_create_event) {
    const mem_event_type_t event_type = MEM_EVENT_DIRECT_RECLAIM_BEGIN;
    std::mutex mtx;
    std::condition_variable cv;
    bool didReceiveEvent = false;

    ASSERT_TRUE(mem_listener.registerEvent(event_type));

    int rb_fd = mem_listener.getRingBufferFd();
    ASSERT_GE(rb_fd, 0) << "Received invalid file descriptor";

    std::thread t([&] {
        struct pollfd pfd = {
                .fd = rb_fd,
                .events = POLLIN,
        };
        int poll_result = poll(&pfd, 1, 10000);
        std::lock_guard lk(mtx);
        didReceiveEvent = poll_result > 0;
        cv.notify_one();
    });

    setMockDataInRb(event_type);

    std::unique_lock lk(mtx);
    cv.wait_for(lk, std::chrono::seconds(10), [&] { return didReceiveEvent; });
    ASSERT_TRUE(didReceiveEvent) << "Poll never received a memory event notification";
    t.join();
}

class MemoryPressureTest : public ::testing::Test {
  public:
    static void SetUpTestSuite() {
        if (!isAtLeastKernelVersion(5, 8, 0))
            GTEST_SKIP() << "BPF ring buffers not supported below 5.8";

        if (!std::filesystem::exists(sysrq_trigger_path))
            GTEST_SKIP() << "sysrq-trigger is required to wake up the OOM killer";

        ASSERT_TRUE(std::filesystem::exists(MEM_EVENTS_TEST_OOM_MARK_VICTIM_TP))
                << "Failed to find test bpf program: " << MEM_EVENTS_TEST_OOM_MARK_VICTIM_TP;
    }

  protected:
    MemEventListener mem_listener = MemEventListener(mem_test_client, true);

    void TearDown() override { mem_listener.deregisterAllEvents(); }

    /**
     * Helper function that will force the OOM killer to claim a [random]
     * victim. Note that there is no deterministic way to ensure what process
     * will be claimed by the OOM killer.
     *
     * We utilize [sysrq]
     * (https://www.kernel.org/doc/html/v4.10/admin-guide/sysrq.html)
     * to help us attempt to wake up the out-of-memory killer.
     *
     * @return true if we were able to trigger an OOM event, false otherwise.
     */
    bool triggerOom() {
        const std::filesystem::path process_oom_path = "proc/self/oom_score_adj";

        // Make sure that we don't kill the parent process
        if (!android::base::WriteStringToFile("-999", process_oom_path)) {
            LOG(ERROR) << "Failed writing oom score adj for parent process";
            return false;
        }

        int pid = fork();
        if (pid < 0) {
            LOG(ERROR) << "Failed to fork";
            return false;
        }
        if (pid == 0) {
            /*
             * We want to make sure that the OOM killer claims our child
             * process, this way we ensure we don't kill anything critical
             * (including this test).
             */
            if (!android::base::WriteStringToFile("1000", process_oom_path)) {
                LOG(ERROR) << "Failed writing oom score adj for child process";
                return false;
            }

            struct sysinfo info;
            if (sysinfo(&info) != 0) {
                LOG(ERROR) << "Failed to get sysinfo";
                return false;
            }
            size_t length = info.freeram / 2;

            // Allocate memory
            void* addr =
                    mmap(NULL, length, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
            if (addr == MAP_FAILED) {
                LOG(ERROR) << "Failed creating mmap";
                return false;
            }

            // Fault pages
            srand(67);
            for (int i = 0; i < length; i += page_size) memset((char*)addr + i, (char)rand(), 1);

            // Use sysrq-trigger to attempt waking up the OOM killer
            if (!android::base::WriteStringToFile("f", sysrq_trigger_path)) {
                LOG(ERROR) << "Failed calling sysrq to trigger OOM killer";
                return false;
            }
            sleep(10);  // Give some time in for sysrq to wake up the OOM killer
        } else {
            /*
             * Wait for child process to finish, this will prevent scenario where the `listen()`
             * is called by the parent, but the child hasn't even been scheduled to run yet.
             */
            wait(NULL);
            if (!mem_listener.listen(2000)) {
                LOG(ERROR) << "Failed to receive a memory event";
                return false;
            }
        }
        return true;
    }

    /*
     * This wrapper function exists to facilitate the use of ASSERT, with
     * non-void helper functions, that want to use `ReadFileToString()`.
     * We can only assert on void functions.
     */
    void fileToString(const std::string& file_path, std::string* content) {
        ASSERT_TRUE(android::base::ReadFileToString(file_path, content))
                << "Failed to read file: " << file_path;
    }

    /*
     * Check if the current device supports the new oom/mark_victim tracepoints.
     * The original oom/mark_victim tracepoint only supports the `pid` field, while
     * the newer version supports: pid, uid, and comm.
     */
    bool isUpdatedMarkVictimTpSupported() {
        const std::string path_mark_victim_format =
                "/sys/kernel/tracing/events/oom/mark_victim/format";
        std::string mark_victim_format_content;
        fileToString(path_mark_victim_format, &mark_victim_format_content);

        /*
         * We check for `uid` only since the format file has several occurrences
         * of words with `comm` in them.
         */
        return mark_victim_format_content.find("uid") != std::string::npos;
    }
};

/**
 * End-to-end test for listening, and consuming, out-of-memory (OOM) events.
 *
 * We don't perform a listen here since the `triggerOom()` already does
 * that for us.
 */
TEST_F(MemoryPressureTest, oom_e2e_flow) {
    if (!isUpdatedMarkVictimTpSupported())
        GTEST_SKIP() << "New oom/mark_victim fields not supported";

    ASSERT_TRUE(mem_listener.registerEvent(MEM_EVENT_OOM_KILL))
            << "Failed registering OOM events as an event of interest";

    ASSERT_TRUE(triggerOom()) << "Failed to trigger OOM killer";

    std::vector<mem_event_t> oom_events;
    ASSERT_TRUE(mem_listener.getMemEvents(oom_events)) << "Failed to fetch memory oom events";
    ASSERT_FALSE(oom_events.empty()) << "We expect at least 1 OOM event";
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ::android::base::InitLogging(argv, android::base::StderrLogger);
    return RUN_ALL_TESTS();
}
