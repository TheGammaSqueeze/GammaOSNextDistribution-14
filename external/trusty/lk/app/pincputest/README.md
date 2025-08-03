`pincputest` shall verify that a peer thread being pinned to a new cpu is
rescheduled properly; particular attention shall be given to situations
where the thread can be rescheduled right away.

In order to cover all cases, the test shall ensure that the peer thread
rescheduling happens:
1. in any possible state of the peer thread (running, ready, blocked, sleeping),
2. whether the newly pinned cpu is the current cpu or another cpu
3. whether the thread from which the peer thread is pinned is a higher
   or lower priority than the peer thread
4. and finally whether the involved threads are standard or real-time threads.

Note: the real-time threads are not collaboratively time-sliced with other threads
on their current cpu. This implies that a real-time thread will have to become
BLOCKED or SLEEPING before it can be interrupted.

In order to be able to set the peer thread in a given state, we need to use
a control thread with same or higher priority than the peer thread.
This control thread will be the `unittest thread`. The thread responsible
for pinning the `peer` thread is called the `main` thread.

Below sections illustrates the interactions between the `unittest` thread, the `main` thread and the `peer` thread to reach the expected `peer` thread state in the various
cases described above.

## Testing `thread_set_pinned_cpu` on a `[RUNNING]` peer

```mermaid

sequenceDiagram
    participant U as unitest thread (priority peer+1)
    participant M as main thread (priority peer+/-1)
    participant P as peer thread (priority peer)
    Note over U, P: Testing thread_set_pinned_cpu on a [RUNNING] peer
    Note over M: main [BLOCKED]
    Note over P: peer [BLOCKED]
    U->>U:thread_set_pinned_cpu(get_current_thread(), p)
    U->>P:thread_set_pinned_cpu(peer.thread, p)
    U->>M:thread_set_pinned_cpu(peer.thread, p+1 or p+2)
    Note over M: main is pinned to another or same cpu than peer will be<br/> allowing to test cases where<br/> 1) either current cpu needs to be rescheduled<br/> or 2) another cpu than current
    U->>U: spin_lock(&peer.lock)
    U->>+P: event_signal(&peer.ev_req)
    P->>P: spin_lock(&peer.lock)
    Note over P: peer [READY] until unittest in [BLOCKED]
    U->>+M: event_signal(&main.ev_req)
    U->>+U: event_wait(&main.ev_resp)
    Note over U: unittest [BLOCKED]
    U-->>P: peer thread scheduled on cpu p
    Note over P: peer [RUNNING]
    M->>M: loop until peer is [RUNNING]
    M->>P: thread_set_pinned_cpu(p+1 or p+2)
    M-->>P: peer thread scheduled on p+1 or p+2
    Note over P: peer in [RUNNING] on the spin_lock
    M->>U: event_signal(&main.ev_resp)
    M-->>U: unitest unblocked by main
    Note over U: unittest [RUNNING]
    U->>U: spin_unlock(&peer.lock)
    U-->>P: peer is unlocked by unittest
    Note over P: peer is [RUNNING] unlocked from spin
    U->>U: wait(&peer.ev_resp)
    Note over U: unittest [BLOCKED]
    P->>P: set actual_cpu
    P->>U: event_signal(&peer.ev_resp)
    P->>P: ev_wait(peer.ev_req)
    P-->>U: unittest unblocked by peer
    Note over P: peer [BLOCKED]
    M->>M: ev_wait(peer.ev_req)
    Note over M: main [BLOCKED]
    U->>U: check actual_cpu == p+1 or p+2
```

## Testing `thread_set_pinned_cpu` on a `[READY]` peer

```mermaid

sequenceDiagram
    participant U as unitest thread (priority peer+1)
    participant M as main thread (priority peer+/-1)
    participant P as peer thread (priority peer)
    Note over U, P: Testing thread_set_pinned_cpu on a [READY] peer
    Note over M: main [BLOCKED]
    Note over P: peer [BLOCKED]
    U->>U:thread_set_pinned_cpu(get_current_thread(), p)
    U->>P:thread_set_pinned_cpu(peer.thread, p)
    U->>M:thread_set_pinned_cpu(peer.thread, p+1 or p+2)
    Note over M: main is pinned to another or same cpu than peer will be<br/> allowing to test cases where<br/> 1) either current cpu needs to be rescheduled<br/> or 2) another cpu than current
    U->>U: spin_lock(&peer.lock)
    U->>+P: event_signal(&peer.ev_req)
    P->>P: spin_lock(&peer.lock)
    Note over P: peer [READY] until unittest in [BLOCKED]
    U->>+M: event_signal(&main.ev_req)
    U->>+U: event_wait(&main.ev_resp)
    Note over U: unittest [BLOCKED]
    U-->>P: peer thread scheduled on cpu p
    Note over P: peer [RUNNING]
    M->>M: spin_lock(&main.lock)
    M->>-U: event_signal(&main.ev_resp)
    M->>M: loop until peer is [READY]
    Note over U: unittest [RUNNING]
    U-->>P: peer thread scheduled out of cpu p
    Note over P: peer [READY]
    U->>U: spin_lock(&main.lock)
    Note over U: unittest [RUNNING] in busy loop due to locked lock
    M->>P: thread_set_pinned_cpu(p+1)
    Note over P: peer thread still spin locked in [READY]
    M->>M: spin_unlock(&main.lock)
    M-->>U: unittest unlocked
    U->>P: spin_unlock(&peer.lock)
    Note over P: peer thread [RUNNING] but still locked on spin_lock
    U->>U: event_wait(&peer_ev_resp)
    U-->>P: peer thread unlocked
    Note over P: peer thread [RUNNING]
    M-->>P: peer thread scheduled on p+1
    P->>P: set actual_cpu
    P->>U: event_signal(&peer.ev_resp)
    P->>P: ev_wait(peer.ev_req)
    Note over P: peer [BLOCKED]

    M->>M: ev_wait(peer.ev_req)
    Note over M: main [BLOCKED]
    U->>U: check actual_cpu == p+1

```

## Testing `thread_set_pinned_cpu` on a `[BLOCKED]` peer

```mermaid

sequenceDiagram
    participant U as unitest thread (priority peer+1)
    participant M as main thread (priority peer+/-1)
    participant P as peer thread (priority peer)
    Note over U, P: Testing thread_set_pinned_cpu on a [BLOCKED] peer
    Note over M: main [BLOCKED]
    Note over P: peer [BLOCKED]
    U->>U:thread_set_pinned_cpu(get_current_thread(), p)
    U->>P:thread_set_pinned_cpu(peer.thread, p)
    U->>M:thread_set_pinned_cpu(peer.thread, p+1 or p+2)
    Note over M: main is pinned to another or same cpu than peer will be<br/> allowing to test cases where<br/> 1) either current cpu needs to be rescheduled<br/> or 2) another cpu than current
    U->>+P: event_signal(&peer.ev_req)
    P->>P: event_wait(&peer.blocked_ev)
    Note over P: peer [BLOCKED]
    U->>+M: event_signal(&main.ev_req)
    U->>+U: event_wait(&main.ev_resp)
    Note over U: unittest [BLOCKED]
    M->>M: loop until peer is [BLOCKED]
    M->>P: thread_set_pinned_cpu(p+1 or p+2)
    M->>U: event_signal(&main.ev_resp)
    M-->>U: unittest unblocked by main
    Note over U: unittest [RUNNING]
    U->>P: event_signal(&peer.blocked_ev)
    U-->>P: peer unblocked by unittest
    M-->>P: peer thread scheduled on p+1 or p+2
    Note over P: peer [RUNNING]
    P->>P: set actual_cpu
    P->>U: event_signal(&peer.ev_resp)
    P->>P: ev_wait(peer.ev_req)
    Note over P: peer [BLOCKED]
    M->>M: ev_wait(peer.ev_req)
    Note over M: main [BLOCKED]
    U->>U: wait(&peer.ev_resp)
    U->>U: check actual_cpu == p+1 or p+2

```

## Testing `thread_set_pinned_cpu` on a `[SLEEPING]` peer

```mermaid

sequenceDiagram
    participant U as unitest thread (priority peer+1)
    participant M as main thread (priority peer+/-1)
    participant P as peer thread (priority peer)
    Note over U, P: Testing thread_set_pinned_cpu on a [SLEEPING] peer
    Note over M: main [BLOCKED]
    Note over P: peer [BLOCKED]
    U->>U:thread_set_pinned_cpu(get_current_thread(), p)
    U->>P:thread_set_pinned_cpu(peer.thread, p)
    U->>M:thread_set_pinned_cpu(peer.thread, p+1 or p+2)
    Note over M: main is pinned to another or same cpu than peer will be<br/> allowing to test cases where<br/> 1) either current cpu needs to be rescheduled<br/> or 2) another cpu than current
    U->>+P: event_signal(&peer.ev_req)
    P->>P: thread_sleep_ns(100ms)
    Note over P: peer [SLEEPING]
    U->>+M: event_signal(&main.ev_req)
    U->>+U: event_wait(&main.ev_resp)
    Note over U: unittest [BLOCKED]
    M->>M: loop until peer is [SLEEPING]
    M->>P: thread_set_pinned_cpu(p+1 or p+2)
    M->>U: event_signal(&main.ev_resp)
    M-->>U: unittest unblocked by main
    Note over U: unittest [RUNNING]
    M->>M: ev_wait(peer.ev_req)
    Note over M: main [BLOCKED]
    U->>U: wait(&peer.ev_resp)
    Note over U: unittest [BLOCKING]
    P-->>P: timer interrupt: waking-up peer
    M-->>P: peer thread scheduled on p+1 or p+2
    Note over P: peer [RUNNING]
    P->>P: set actual_cpu
    P->>U: event_signal(&peer.ev_resp)
    P->>P: ev_wait(peer.ev_req)
    Note over P: peer [BLOCKED]
    P-->>U: unittest unblocked by peer
    U->>U: check actual_cpu == p+1 or p+2
```
