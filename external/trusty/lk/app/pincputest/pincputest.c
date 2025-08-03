/*
 * Copyright (c) 2021, Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#define LOCAL_TRACE (0)

#include <kernel/debug.h>
#include <kernel/event.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <lk/trace.h>
#include <stdbool.h>
#include <stdio.h>

#define PINCPU_TEST_CPU_COUNT 4

/*
 * Test verifying cpu pinning on any thread state
 * Make sure cpu pinning can be dynamically set on
 * a running, ready, blocked or sleeping thread.
 */

/**
 * struct pincputest_thread_ctx_main - main thread context structure
 * @test_state:               Current test state
 * @thread:                   `main` thread's thread structure
 * @ev_req:                   Request event sent by the unittest thread to
 *                            the main thread in order to start a new test
 * @ev_resp:                  Response event sent by the main thread to
 *                            the unittest thread when the test case is
 *                            complete
 * @runningstate_lock:        Spin_lock used to ensure the unittest thread
 *                            waits in running state. This is used to keep
 *                            the peer thread in ready state.
 * @cpu_expected:             Cpu on which the main thread is pinned to and
 *                            shall be running on when setting the peer
 *                            thread's pinned cpu.
 * @actual_pinned_cpu:        Peer thread's pinned cpu as seen by the main
 *                            thread when the thread_set_pinned_cpu is complete
 */
struct pincputest_thread_ctx_main {
    void* test_state;
    thread_t* thread;
    event_t ev_req;
    event_t ev_resp;
    spin_lock_t runningstate_lock;
    int cpu_expected;
    int actual_pinned_cpu;
};

/**
 * struct pincputest_thread_ctx_peer - peer thread context structure
 * @test_state:               Current test state
 * @thread:                   `peer` thread's thread structure
 * @ev_req:                   Request event sent by the main thread to
 *                            the peer thread.
 * @ev_resp:                  Response event sent by the peer thread to
 *                            the main thread
 * @blockingstate_mutex:      Mutex used to set the peer thread
 *                            in blocking state
 * @blockingstate_event:      Event used to set the peer thread
 *                            in blocking state, as an additional option
 *                            to the mutex.
 * @runningstate_lock:        Spin_lock used to set the peer thread
 *                            in running state.
 * @cpu_expected:             Cpu on which the peer thread is pinned to.
 * @cpu_actual:               Cpu on which the peer thread is running
 *                            after handling the request event
 *                            from the main thread
 */
struct pincputest_thread_ctx_peer {
    void* test_state;
    thread_t* thread;
    event_t ev_req;
    event_t ev_resp;
    mutex_t blockingstate_mutex;
    event_t blockingstate_event;
    spin_lock_t runningstate_lock;
    int cpu_expected;
    int cpu_actual;
};

/**
 * struct pincputest_t - test state structure for the pincputest
 * @is_rt_main:               If true, main thread is real-time
 * @is_rt_peer:               If true, peer thread is real-time
 * @pinned_is_current:        If true, main thread sets the peer
 *                            thread's pinned cpu to the current cpu
 * @priority_main:            Priority of the main thread
 * @priority_peer:            Priority of the peer thread
 * @expected_state_peer:      Expected thread state
 *                            (running/ready/blocked/sleeping)
 *                            for the peer thread to be in when
 *                            its cpu pinning is updated
 * @blockingstate_use_mutex:  If true, the peer thread blocks on a mutex,
 *                            If false, it blocks on an event.
 * @ctx_main:                 main thread context
 * @ctx_peer:                 peer thread context
 * The pincputest consists of a `main` thread setting cpu pinning
 * on a `peer` thread. The test covers main and peer threads
 * being standard or realtime threads and having different relative priorities.
 */
typedef struct {
    bool is_rt_main;
    bool is_rt_peer;
    bool pinned_is_current;
    int priority_main;
    int priority_peer;
    enum thread_state expected_state_peer;
    bool blockingstate_use_mutex;
    struct pincputest_thread_ctx_main ctx_main;
    struct pincputest_thread_ctx_peer ctx_peer;
} pincputest_t;

static const char* thread_state_to_str(enum thread_state state) {
    switch (state) {
    case THREAD_SUSPENDED:
        return "Suspended";
    case THREAD_READY:
        return "Ready";
    case THREAD_RUNNING:
        return "Running";
    case THREAD_BLOCKED:
        return "Blocked";
    case THREAD_SLEEPING:
        return "Sleeping";
    case THREAD_DEATH:
        return "Death";
    default:
        return "Unknown";
    }
}

static void pincputest_param_to_string(const void* param) {
    const void* const* params = param;
    TRACEF("[main:%s(%d), peer:%s(%d)]\n", *(bool*)params[0] ? "rt" : "std",
           *(int*)params[2], *(bool*)params[1] ? "rt" : "std",
           *(int*)params[3]);
}

/**
 * pincputest_unittest_thread() is the controller test function
 * invoked from the unittest thread.
 * It will ensure that the peer thread is in a given state
 * before the main thread invoked thread_set_pinned_cpu on the peer thread
 */
static void pincputest_unittest_thread(pincputest_t* _state) {
    struct pincputest_thread_ctx_main* ctx_main = &_state->ctx_main;
    struct pincputest_thread_ctx_peer* ctx_peer = &_state->ctx_peer;
    spin_lock_saved_state_t lock_state_main;
    spin_lock_saved_state_t lock_state_peer;
    ctx_main->test_state = _state;
    ctx_peer->test_state = _state;
    int cpu_peer = -1;           /* current cpu for the peer thread */
    ctx_peer->cpu_expected = -1; /* new target/pinned cpu for the peer thread */
    ctx_main->cpu_expected = -1; /* target cpu for the main thread */

    /*
     * set current thread's priority just higher from peer:
     * this thread (the unittest thread) handles the
     * necessary locking / unlocking for the peer thread
     * to reach the desired state.
     * So the unittest thread shall be either same priority
     * than peer if non real-time (with time slicing
     * / collaborative multi-threading), or higher priority
     * in case peer is a real-time thead.
     * To make things simpler, we set its priority higher
     * than the peer thread
     */
    thread_set_priority(_state->priority_peer + 1);

    for (int c = 0; c <= PINCPU_TEST_CPU_COUNT; c++) {
        /* reset actual_pinned_cpu:
         * [-1..PINCPU_TEST_CPU_COUNT-1] are all valid values
         * PINCPU_TEST_CPU_COUNT can thus be used as the reset value
         */
        ctx_main->actual_pinned_cpu = PINCPU_TEST_CPU_COUNT;
        cpu_peer = ctx_peer->cpu_actual == -1 ? 0 : ctx_peer->cpu_actual;
        if (thread_pinned_cpu(ctx_peer->thread) == -1) {
            /* this case can only happen at the beginning of the test */
            DEBUG_ASSERT(c == 0);
            thread_set_pinned_cpu(ctx_peer->thread, cpu_peer);
        }
        DEBUG_ASSERT(thread_pinned_cpu(ctx_peer->thread)== cpu_peer);

        /*
         * define the new target pinned cpu
         * for both peer and main threads
         */
        ctx_peer->cpu_expected = (cpu_peer + 1) % PINCPU_TEST_CPU_COUNT;
        if (_state->pinned_is_current) {
            ctx_main->cpu_expected = ctx_peer->cpu_expected;
        } else {
            ctx_main->cpu_expected = (cpu_peer + 2) % PINCPU_TEST_CPU_COUNT;
        }
        thread_set_pinned_cpu(ctx_main->thread, ctx_main->cpu_expected);
        if (c == PINCPU_TEST_CPU_COUNT) {
            /* for the last round, unpin peer thread */
            ctx_peer->cpu_expected = -1;
        }

        /*
         * `unittest` thread shall be pinned to same cpu
         * as `peer` thread when peer expected state is
         * ready, blocked or sleeping.
         * However if the expected state is running, the
         * unittest thread shall be pinned to another cpu
         * (another than current and new pinned target cpu)
         */
        if (_state->expected_state_peer == THREAD_RUNNING) {
            thread_set_pinned_cpu(get_current_thread(),
                                  (cpu_peer + 3) % PINCPU_TEST_CPU_COUNT);
        } else {
            thread_set_pinned_cpu(get_current_thread(), cpu_peer);
        }
        /*
         * In unit-test environment, ensure all cpus are idle before
         * starting the test. We actually want the cpu on which to pin the
         * peer thread to be idle when thread_set_pinned_cpu is invoked, as
         * this is the most complex scheduling state.
         */
        thread_sleep_ns(100000000); /* wait 100ms for tgt cpu to be idle */

        /* start the test by prepping
         * the locking states for the peer thread
         */
        if (_state->expected_state_peer == THREAD_BLOCKED &&
            _state->blockingstate_use_mutex) {
            mutex_acquire(&ctx_peer->blockingstate_mutex);
        }

        /*
         * notify the peer thread of a new test:
         * the peer thread will take the right
         * action to reach its expected state
         * before the main thread invokes
         * thread_set_pinned_cpu()
         */
        LTRACEF("ev_req sent to peer (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        event_signal(&ctx_peer->ev_req, true);

        /*
         * then notify the main thread
         * to invoke thread_set_pinned_cpu
         */
        LTRACEF("ev_req sent to main (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        event_signal(&ctx_main->ev_req, true);

        /* now wait for the main thread to invoke
         * the cpu pinning on the peer thread
         * if main thread is not supposed to be
         * preempted by the peer thread, simply
         * wait for the response event.
         * however if main thread is preempted while
         * right away (due to a higher priority peer thread)
         * peer thread needs to be unlocked as soon
         * as peer thread is pinned to new cpu and main thread
         * is in ready state
         */
        bool main_preempted = false;
        if ((ctx_peer->cpu_expected > -1) &&
            (_state->expected_state_peer == THREAD_RUNNING)) {
            if (_state->priority_main < _state->priority_peer) {
                main_preempted = true;
            }
        }
        if (main_preempted) {
            // thread_sleep_ns(1000000000);  // sleep 1sec
            volatile int* peer_cpu_ptr = &ctx_peer->cpu_actual;
            int peer_cpu;
            int busy_loop_cnt = 0;
            do {
                spin_lock_irqsave(&ctx_peer->runningstate_lock,
                                  lock_state_peer);
                peer_cpu = *peer_cpu_ptr;
                spin_unlock_irqrestore(&ctx_peer->runningstate_lock,
                                       lock_state_peer);
                if (peer_cpu != ctx_peer->cpu_expected &&
                    ++busy_loop_cnt % 10000 == 0) {
                    LTRACEF("%s: thread %s, actual_cpu [%d] != expected_cpu [%d], keep waiting...\n",
                            __func__, ctx_peer->thread->name, peer_cpu,
                            ctx_peer->cpu_expected);
                }
            } while (peer_cpu != ctx_peer->cpu_expected);

            LTRACEF("%s: thread %s, curr_cpu [%d] == expected_cpu [%d]!\n",
                    __func__, ctx_peer->thread->name, peer_cpu,
                    ctx_peer->cpu_expected);
        } else if (_state->expected_state_peer == THREAD_READY) {
            /*
             * for peer thread to be in READY state, the higher priority
             * unittest thread shall be busy on the same cpu as the peer thread
             * until the main thread is done with the pinned cpu request.
             * loop until the peer thread pinning completes.
             */
            volatile int* peer_pinned_cpu_ptr = &ctx_main->actual_pinned_cpu;
            int peer_cpu;
            // int busy_loop_cnt = 0;
            do {
                spin_lock_irqsave(&ctx_main->runningstate_lock,
                                  lock_state_main);
                peer_cpu = *peer_pinned_cpu_ptr;
                spin_unlock_irqrestore(&ctx_main->runningstate_lock,
                                       lock_state_main);
                /* note: do not add LTRACEF statement as the thread will
                 * become BLOCKED and the expected peer thread state
                 * will not be gguaranted!!
                 */
            } while (peer_cpu != ctx_peer->cpu_expected);
            LTRACEF("ev_resp from main waiting...\n");
            event_wait(&ctx_main->ev_resp);
            LTRACEF("ev_resp from main received!\n");
        } else {
            LTRACEF("ev_resp from main waiting...\n");
            event_wait(&ctx_main->ev_resp);
            LTRACEF("ev_resp from main received!\n");
        }
        /* unblock the peer thread for it
         * to complete the test
         * (report its actual cpu)
         */
        if (_state->expected_state_peer == THREAD_BLOCKED &&
            _state->blockingstate_use_mutex) {
            mutex_release(&ctx_peer->blockingstate_mutex);
        } else if (_state->expected_state_peer == THREAD_BLOCKED &&
                   !_state->blockingstate_use_mutex) {
            event_signal(&ctx_peer->blockingstate_event, true);
        }
        event_wait(&ctx_peer->ev_resp);
        if (main_preempted) {
            event_wait(&ctx_main->ev_resp);
        }
        thread_sleep_ns(100000000); /* wait 100ms for tgt cpu to be idle */
        LTRACEF("%s[%s] / %s[%s]\n", ctx_main->thread->name,
                thread_state_to_str(ctx_main->thread->state),
                ctx_peer->thread->name,
                thread_state_to_str(ctx_peer->thread->state));
        DEBUG_ASSERT(ctx_main->thread->state == THREAD_BLOCKED);
        DEBUG_ASSERT(ctx_peer->thread->state == THREAD_BLOCKED);
        if (ctx_peer->cpu_expected > -1) {
            ASSERT_EQ(ctx_peer->cpu_expected, ctx_peer->cpu_actual);
        } else {
            ASSERT_GT(ctx_peer->cpu_actual, -1);
        }
        LTRACEF("%s: cpu expected (%d) actual (%d)\n**********************\n",
                __func__, ctx_peer->cpu_expected, ctx_peer->cpu_actual);
    }
test_abort:
    thread_set_priority(HIGH_PRIORITY);
    thread_set_pinned_cpu(get_current_thread(), -1);
    ctx_main->test_state = NULL;
    ctx_peer->test_state = NULL;
}

static int pincputest_main_thread(void* _state) {
    pincputest_t* state = _state;
    struct pincputest_thread_ctx_main* ctx = &state->ctx_main;
    struct pincputest_thread_ctx_peer* ctx_peer = &state->ctx_peer;
    spin_lock_saved_state_t lock_state_main;
    while (1) {
        LTRACEF("ev_req waiting in main (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        event_wait(&ctx->ev_req);
        LTRACEF("ev_req received in main (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        DEBUG_ASSERT(_state);
        if (state->expected_state_peer == THREAD_DEATH) {
            /* exiting */
            LTRACEF("main thread exiting...\n");
            event_signal(&ctx->ev_resp, true);
            return 0;
        }
        thread_set_priority(state->priority_main);
        while (_state &&
               ctx_peer->thread->state != state->expected_state_peer) {
            thread_sleep_ns(10000000);  // sleep 10ms
            if (ctx_peer->thread->state != state->expected_state_peer) {
                LTRACEF("%s: thread %s, state [%s] != expected [%s], keep waiting...\n",
                        __func__, ctx_peer->thread->name,
                        thread_state_to_str(ctx_peer->thread->state),
                        thread_state_to_str(state->expected_state_peer));
            }
        }
        DEBUG_ASSERT(_state);
        if (ctx->cpu_expected > -1) {
            ASSERT_EQ(thread_curr_cpu(get_current_thread()), ctx->cpu_expected);
        }
        if (ctx_peer->cpu_expected == -1) {
            thread_set_pinned_cpu(get_current_thread(), -1);
        }
        thread_set_pinned_cpu(ctx_peer->thread, ctx_peer->cpu_expected);
        spin_lock_irqsave(&ctx->runningstate_lock, lock_state_main);
        ctx->actual_pinned_cpu = thread_pinned_cpu(ctx_peer->thread);
        spin_unlock_irqrestore(&ctx->runningstate_lock, lock_state_main);
        event_signal(&ctx->ev_resp, true);
        LTRACEF("ev_resp sent...\n");
        DEBUG_ASSERT(_state);
    test_abort:;
        thread_set_priority(HIGH_PRIORITY);
    }
    return 0;
}

static int pincputest_peer_thread(void* _state) {
    pincputest_t* state = _state;
    struct pincputest_thread_ctx_peer* ctx = &state->ctx_peer;
    spin_lock_saved_state_t lock_state_peer;
    int pinned_cpu;
    int curr_cpu;
    bool done;
    while (1) {
        LTRACEF("ev_req waiting in peer (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        event_wait(&ctx->ev_req);
        LTRACEF("ev_req received in peer (pinned_cpu=%d curr_cpu=%d)\n",
                thread_pinned_cpu(get_current_thread()), arch_curr_cpu_num());
        DEBUG_ASSERT(_state);
        thread_set_priority(state->priority_peer);
        switch (state->expected_state_peer) {
        case THREAD_RUNNING:
        case THREAD_READY:
            /* start busy loop */
            done = false;
            do {
                spin_lock_irqsave(&ctx->runningstate_lock, lock_state_peer);
                pinned_cpu = thread_pinned_cpu(get_current_thread());
                curr_cpu = thread_curr_cpu(get_current_thread());
                spin_unlock_irqrestore(&ctx->runningstate_lock,
                                       lock_state_peer);

                if (ctx->cpu_expected > -1) {
                    if (curr_cpu == ctx->cpu_expected) {
                        done = true;
                    }
                } else {
                    if (pinned_cpu == -1) {
                        done = true;
                    }
                }
            } while (!done);
            LTRACEF("%s: thread %s, curr_cpu [%d] == expected_cpu [%d]!\n",
                    __func__, ctx->thread->name, curr_cpu, ctx->cpu_expected);
            break;
        case THREAD_BLOCKED:
            /* go to BLOCKED state */
            if (state->blockingstate_use_mutex) {
                mutex_acquire(&ctx->blockingstate_mutex);
                mutex_release(&ctx->blockingstate_mutex);
            } else {
                event_wait(&ctx->blockingstate_event);
            }
            break;
        case THREAD_SLEEPING:
            /* go to SLEEPING state for 1 sec */
            thread_sleep_ns(1000000000);
            break;
        case THREAD_DEATH:
            /* exiting */
            LTRACEF("peer thread exiting...\n");
            event_signal(&ctx->ev_resp, true);
            return 0;
        default:
            event_signal(&ctx->ev_resp, true);
            return -1;
        }
        spin_lock_irqsave(&ctx->runningstate_lock, lock_state_peer);
        ctx->cpu_actual = thread_curr_cpu(get_current_thread());
        spin_unlock_irqrestore(&ctx->runningstate_lock, lock_state_peer);
        if (ctx->cpu_expected > -1) {
            LTRACEF("PinCpuWhenThreadState%s [%s] cpu expected (%d) actual (%d)\n",
                    thread_state_to_str(state->expected_state_peer),
                    ctx->cpu_expected == ctx->cpu_actual ? "PASSED" : "FAILED",
                    ctx->cpu_expected, ctx->cpu_actual);
        }
        event_signal(&ctx->ev_resp, true);
        thread_set_priority(HIGH_PRIORITY);
    }
    return 0;
}

static void pincputest_init_threads(pincputest_t* _state) {
    char name[24];

    /* init peer thread */
    const char* peer_name = "pincputest-peer-";
    struct pincputest_thread_ctx_peer* ctx_peer = &_state->ctx_peer;
    strlcpy(name, peer_name, sizeof(name));
    strlcat(name, _state->is_rt_peer ? "rt" : "std", sizeof(name));
    event_init(&ctx_peer->ev_req, false, EVENT_FLAG_AUTOUNSIGNAL);
    event_init(&ctx_peer->ev_resp, false, EVENT_FLAG_AUTOUNSIGNAL);
    spin_lock_init(&ctx_peer->runningstate_lock);
    mutex_init(&ctx_peer->blockingstate_mutex);
    event_init(&ctx_peer->blockingstate_event, false, EVENT_FLAG_AUTOUNSIGNAL);
    ctx_peer->thread =
            thread_create(name, pincputest_peer_thread, (void*)_state,
                          HIGH_PRIORITY, DEFAULT_STACK_SIZE);
    if (_state->is_rt_peer) {
        thread_set_real_time(ctx_peer->thread);
    }
    thread_set_pinned_cpu(ctx_peer->thread, 0);
    ctx_peer->cpu_actual = -1;

    /* init main thread */
    struct pincputest_thread_ctx_main* ctx_main = &_state->ctx_main;
    const char* main_name = "pincputest-main-";
    strlcpy(name, main_name, sizeof(name));
    strlcat(name, _state->is_rt_main ? "rt" : "std", sizeof(name));
    event_init(&ctx_main->ev_req, false, EVENT_FLAG_AUTOUNSIGNAL);
    event_init(&ctx_main->ev_resp, false, EVENT_FLAG_AUTOUNSIGNAL);
    spin_lock_init(&ctx_main->runningstate_lock);
    ctx_main->thread =
            thread_create(name, pincputest_main_thread, (void*)_state,
                          HIGH_PRIORITY, DEFAULT_STACK_SIZE);
    if (_state->is_rt_main) {
        thread_set_real_time(ctx_main->thread);
    }
    thread_resume(ctx_peer->thread);
    thread_resume(ctx_main->thread);
}

TEST_F_SETUP(pincputest) {
    const void* const* params = GetParam();
    // pincputest_param_to_string(params);
    const bool* is_rt_main = params[0];
    const bool* is_rt_peer = params[1];
    const bool* pinned_is_current = params[2];
    const int* priority_main = params[3];
    const int* priority_peer = params[4];
    _state->is_rt_main = *is_rt_main;
    _state->is_rt_peer = *is_rt_peer;
    _state->pinned_is_current = *pinned_is_current;
    _state->priority_main = *priority_main;
    _state->priority_peer = *priority_peer;
    pincputest_init_threads(_state);
}

TEST_F_TEARDOWN(pincputest) {
    int ret;
    struct pincputest_thread_ctx_main* ctx_main = &_state->ctx_main;
    struct pincputest_thread_ctx_peer* ctx_peer = &_state->ctx_peer;

    _state->expected_state_peer = THREAD_DEATH;
    /* exiting main thread */
    event_signal(&ctx_main->ev_req, true);
    event_wait(&ctx_main->ev_resp);
    thread_join(_state->ctx_main.thread, &ret, INFINITE_TIME);

    /* exiting peer thread */
    event_signal(&ctx_peer->ev_req, true);
    event_wait(&ctx_peer->ev_resp);
    thread_join(_state->ctx_peer.thread, &ret, INFINITE_TIME);

    event_destroy(&ctx_main->ev_req);
    event_destroy(&ctx_main->ev_resp);
    event_destroy(&ctx_peer->ev_req);
    event_destroy(&ctx_peer->ev_resp);
    mutex_destroy(&ctx_peer->blockingstate_mutex);
    event_destroy(&ctx_peer->blockingstate_event);
}

TEST_P(pincputest, PinCpuWhenThreadStateRunning) {
    LTRACEF("PinCpuWhenThreadStateRunning\n");
    _state->expected_state_peer = THREAD_RUNNING;
    _state->blockingstate_use_mutex = false;
    pincputest_unittest_thread(_state);
    ASSERT_EQ(HasFailure(), 0);
test_abort:;
}

TEST_P(pincputest, PinCpuWhenThreadStateReady) {
    LTRACEF("PinCpuWhenThreadStateReady\n");
    _state->expected_state_peer = THREAD_READY;
    _state->blockingstate_use_mutex = false;
    pincputest_unittest_thread(_state);
    ASSERT_EQ(HasFailure(), 0);
test_abort:;
}

TEST_P(pincputest, PinCpuWhenThreadStateSleeping) {
    LTRACEF("PinCpuWhenThreadStateSleeping\n");
    _state->expected_state_peer = THREAD_SLEEPING;
    _state->blockingstate_use_mutex = false;
    pincputest_unittest_thread(_state);
    ASSERT_EQ(HasFailure(), 0);
test_abort:;
}

TEST_P(pincputest, PinCpuWhenThreadStateBlockingOnMutex) {
    LTRACEF("PinCpuWhenThreadStateBlockingOnMutex\n");
    _state->expected_state_peer = THREAD_BLOCKED;
    _state->blockingstate_use_mutex = true;
    pincputest_unittest_thread(_state);
    ASSERT_EQ(HasFailure(), 0);
test_abort:;
}

TEST_P(pincputest, PinCpuWhenThreadStateBlockingOnEvent) {
    LTRACEF("PinCpuWhenThreadStateBlockingOnEvent\n");
    _state->expected_state_peer = THREAD_BLOCKED;
    _state->blockingstate_use_mutex = false;
    pincputest_unittest_thread(_state);
    ASSERT_EQ(HasFailure(), 0);
test_abort:;
}

INSTANTIATE_TEST_SUITE_P(
        standard_threads,
        pincputest,
        testing_Combine(/* is_main_rt: main thread is standard */
                        testing_Values(0),
                        /* is_main_rt: peer thread is standard */
                        testing_Values(0),
                        /* pinned_is_current: peer thread pinned to current */
                        testing_Bool(),
                        /* main thread priority */
                        testing_Values(HIGH_PRIORITY),
                        /* peer thread priority */
                        testing_Values(HIGH_PRIORITY,
                                       HIGH_PRIORITY + 2,
                                       HIGH_PRIORITY - 2)));

INSTANTIATE_TEST_SUITE_P(
        current_is_realtime,
        pincputest,
        testing_Combine(/* is_main_rt: main thread is standard or real-time */
                        testing_Values(1),
                        /* is_main_rt: peer thread is real-time */
                        testing_Values(0),
                        /* pinned_is_current: peer thread pinned to current */
                        testing_Values(1),  // testing_Bool(),
                        /* main thread priority */
                        testing_Values(HIGH_PRIORITY),
                        /* peer thread priority */
                        testing_Values(HIGH_PRIORITY,
                                       HIGH_PRIORITY + 2,
                                       HIGH_PRIORITY - 2)));

PORT_TEST(pincputest, "com.android.kernel.pincputest");
