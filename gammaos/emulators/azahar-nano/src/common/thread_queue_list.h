// Copyright 2014-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <algorithm>
#include <array>
#include <bit>
#include <deque>
#include <tuple>
#include <boost/serialization/deque.hpp>
#include <boost/serialization/split_member.hpp>
#include "common/common_types.h"

namespace Common {

template <class T, unsigned int N>
struct ThreadQueueList {
    // TODO(yuriks): If performance proves to be a problem, the std::deques can be replaced with
    //               (dynamically resizable) circular buffers to remove their overhead when
    //               inserting and popping.

    using Priority = unsigned int;

    // Number of priority levels. (Valid levels are [0..NUM_QUEUES).)
    static constexpr Priority NUM_QUEUES = N;
    static_assert(N <= 64, "the non-empty mask has one bit per level");

    ThreadQueueList() {
        first = nullptr;
    }

    // Only for debugging, returns priority level.
    [[nodiscard]] Priority contains(const T& uid) const {
        for (Priority i = 0; i < NUM_QUEUES; ++i) {
            const Queue& cur = queues[i];
            if (std::find(cur.data.cbegin(), cur.data.cend(), uid) != cur.data.cend()) {
                return i;
            }
        }

        return -1;
    }

    // GammaOS: the first non-empty level comes from a bit mask instead of a walk over the
    // linked levels' deques (the scheduler asks on every run loop pass, 20000 times a second
    // in Kirby: Planet Robobot). The linked list stays for the save state format; the mask
    // covers exactly the levels the walk would visit (linked ones) that hold threads, and the
    // links are in ascending level order, so the lowest set bit is the walk's answer.
    [[nodiscard]] T get_first() const {
        if (nonempty == 0) {
            return T();
        }
        return queues[std::countr_zero(nonempty)].data.front();
    }

    std::pair<Priority, T> pop_first() {
        if (nonempty == 0) {
            return {Priority(), T()};
        }
        return pop_level(static_cast<Priority>(std::countr_zero(nonempty)));
    }

    std::pair<Priority, T> pop_first_better(Priority priority) {
        if (nonempty == 0) {
            return {Priority(), T()};
        }
        const auto level = static_cast<Priority>(std::countr_zero(nonempty));
        if (level >= priority) {
            return {Priority(), T()};
        }
        return pop_level(level);
    }

    void push_front(Priority priority, const T& thread_id) {
        Queue* cur = &queues[priority];
        cur->data.push_front(thread_id);
        update_mask(priority);
    }

    void push_back(Priority priority, const T& thread_id) {
        Queue* cur = &queues[priority];
        cur->data.push_back(thread_id);
        update_mask(priority);
    }

    void move(const T& thread_id, Priority old_priority, Priority new_priority) {
        remove(old_priority, thread_id);
        prepare(new_priority);
        push_back(new_priority, thread_id);
    }

    void remove(Priority priority, const T& thread_id) {
        Queue* const cur = &queues[priority];
        const auto iter = std::remove(cur->data.begin(), cur->data.end(), thread_id);
        cur->data.erase(iter, cur->data.end());
        update_mask(priority);
    }

    void rotate(Priority priority) {
        Queue* cur = &queues[priority];

        if (cur->data.size() > 1) {
            cur->data.push_back(std::move(cur->data.front()));
            cur->data.pop_front();
        }
    }

    void clear() {
        queues.fill(Queue());
        first = nullptr;
        nonempty = 0;
    }

    [[nodiscard]] bool empty(Priority priority) const {
        const Queue* cur = &queues[priority];
        return cur->data.empty();
    }

    void prepare(Priority priority) {
        Queue* cur = &queues[priority];
        if (cur->next_nonempty == UnlinkedTag()) {
            link(priority);
            update_mask(priority);
        }
    }

private:
    struct Queue {
        // Points to the next active priority, skipping over ones that have never been used.
        Queue* next_nonempty = UnlinkedTag();
        // Double-ended queue of threads in this priority level
        std::deque<T> data;
    };

    /// Special tag used to mark priority levels that have never been used.
    static Queue* UnlinkedTag() {
        return reinterpret_cast<Queue*>(1);
    }

    void update_mask(Priority priority) {
        const Queue& q = queues[priority];
        const u64 bit = u64{1} << priority;
        if (q.next_nonempty != UnlinkedTag() && !q.data.empty()) {
            nonempty |= bit;
        } else {
            nonempty &= ~bit;
        }
    }

    std::pair<Priority, T> pop_level(Priority level) {
        Queue& q = queues[level];
        auto tmp = std::move(q.data.front());
        q.data.pop_front();
        update_mask(level);
        return {level, tmp};
    }

    void link(Priority priority) {
        Queue* cur = &queues[priority];

        for (int i = priority - 1; i >= 0; --i) {
            if (queues[i].next_nonempty != UnlinkedTag()) {
                cur->next_nonempty = queues[i].next_nonempty;
                queues[i].next_nonempty = cur;
                return;
            }
        }

        cur->next_nonempty = first;
        first = cur;
    }

    // The first queue that's ever been used.
    Queue* first;
    // GammaOS: bit p set when level p is linked and holds threads (not saved: rebuilt on load).
    u64 nonempty = 0;
    // The priority level queues of thread ids.
    std::array<Queue, NUM_QUEUES> queues;

    s64 ToIndex(const Queue* q) const {
        if (q == nullptr) {
            return -2;
        } else if (q == UnlinkedTag()) {
            return -1;
        } else {
            return q - queues.data();
        }
    }

    Queue* ToPointer(s64 idx) {
        if (idx == -1) {
            return UnlinkedTag();
        } else if (idx < 0) {
            return nullptr;
        } else {
            return &queues[idx];
        }
    }

    friend class boost::serialization::access;
    template <class Archive>
    void save(Archive& ar, const unsigned int file_version) const {
        const s64 idx = ToIndex(first);
        ar << idx;
        for (std::size_t i = 0; i < NUM_QUEUES; i++) {
            const s64 idx1 = ToIndex(queues[i].next_nonempty);
            ar << idx1;
            ar << queues[i].data;
        }
    }

    template <class Archive>
    void load(Archive& ar, const unsigned int file_version) {
        s64 idx;
        ar >> idx;
        first = ToPointer(idx);
        for (std::size_t i = 0; i < NUM_QUEUES; i++) {
            ar >> idx;
            queues[i].next_nonempty = ToPointer(idx);
            ar >> queues[i].data;
        }
        nonempty = 0;
        for (Priority i = 0; i < NUM_QUEUES; i++) {
            update_mask(i);
        }
    }

    BOOST_SERIALIZATION_SPLIT_MEMBER()
};

} // namespace Common
