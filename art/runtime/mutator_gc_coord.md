Mechanisms for Coordination Between Garbage Collector and Mutator
-----------------------------------------------------------------

Most garbage collection work can proceed concurrently with the client or
mutator Java threads. But in certain places, for example while tracing from
thread stacks, the garbage collector needs to ensure that Java data processed
by the collector is consistent and complete. At these points, the mutators
should not hold references to the heap that are invisible to the garbage
collector. And they should not be modifying the data that is visible to the
collector.

Logically, the collector and mutator share a reader-writer lock on the Java
heap and associated data structures. Mutators hold the lock in reader or shared mode
while running Java code or touching heap-related data structures. The collector
holds the lock in writer or exclusive mode while it needs the heap data
structures to be stable. However, this reader-writer lock has a very customized
implementation that also provides additional facilities, such as the ability
to exclude only a single thread, so that we can specifically examine its heap
references.

In order to ensure consistency of the Java data, the compiler inserts "suspend
points", sometimes also called "safe points" into the code. These allow a thread
to respond to external requests.

Whenever a thread is runnable, i.e. whenever a thread logically holds the
mutator lock in shared mode, it is expected to regularly execute such a suspend
point, and check for pending requests. They are currently implemented by
setting a flag in the thread structure[^1], which is then explicitly tested by the
compiler-generated code.

A thread responds to suspend requests only when it is "runnable", i.e. logically
running Java code. When it runs native code, or is blocked in a kernel call, it
logically releases the mutator lock. When the garbage collector needs mutator
cooperation, and the thread is not runnable, it is assured that the mutator is
not touching Java data, and hence the collector can safely perform the required
action itself, on the mutator thread's behalf.

Normally, when a thread makes a JNI call, it is not considered runnable while
executing native code. This makes the transitions to and from running native JNI
code somewhat expensive (see below). But these transitions are necessary to
ensure that such code, which does not execute "suspend points", and can thus not
cooperate with the GC, doesn't delay GC completion. `@FastNative` and
`@CriticalNative` calls avoid these transitions, instead allowing the thread to
remain "runnable", at the expense of potentially delaying GC operations for the
duration of the call.

Although we say that a thread is "suspended" when it is not running Java code,
it may in fact still be running native code and touching data structures that
are not considered "Java data". This distinction can be a fine line. For
example, a Java thread blocked on a Java monitor will normally be "suspended"
and blocked on a mutex contained in the monitor data structure. But it may wake
up for reasons beyond ARTs control, which will normally result in touching the
mutex. The monitor code must be quite careful to ensure that this does not cause
problems, especially if the ART runtime was shut down in the interim and the
monitor data structure has been reclaimed.

Calls to change thread state
----------------------------

When a thread changes between running Java and native code, it has to
correspondingly change its state between "runnable" and one of several
other states, all of which are considered to be "suspended" for our purposes.
When a Java thread starts to execute native code, and may thus not respond
promptly to suspend requests, it will normally create an object of type
`ScopedThreadSuspension`. `ScopedThreadSuspension`'s constructor changes state to
the "suspended" state given as an argument, logically releasing the mutator lock
and promising to no longer touch Java data structures. It also handles any
pending suspension requests that slid in just before it changed state.

Conversely, `ScopedThreadSuspension`'s destructor waits until the GC has finished
any actions it is currently performing on the thread's behalf and effectively
released the mutator exclusive lock, and then returns to runnable state,
re-acquiring the mutator lock.

Occasionally a thread running native code needs to temporarily again access Java
data structures, performing the above transitions in the opposite order.
`ScopedObjectAccess` is a similar RAII object whose constructor and destructor
perform those transitions in the reverse order from `ScopedThreadSuspension`.

Mutator lock implementation
---------------------------

The mutator lock is not implemented as a conventional mutex. But it plays by the
rules of our normal static thread-safety analysis. Thus a function that is
expected to be called in runnable state, with the ability to access Java data,
should be annotated with `REQUIRES_SHARED(Locks::mutator_lock_)`.

There is an explicit `mutator_lock_` object, of type `MutatorMutex`. `MutatorMutex` is
seemingly a minor refinement of `ReaderWriterMutex`, but it is used entirely
differently. It is acquired explicitly by clients that need to hold it
exclusively, and in a small number of cases, it is acquired in shared mode, e.g.
via `SharedTryLock()`, or by the GC itself. However, more commonly
`MutatorMutex::TransitionFromSuspendedToRunnable()`, is used to logically acquire
the mutator mutex, e.g. as part of `ScopedObjectAccess` construction.

`TransitionFromSuspendedToRunnable()` does not physically acquire the
`ReaderWriterMutex` in shared mode. Thus any thread acquiring the lock in exclusive mode
must, in addition, explicitly arrange for mutator threads to be suspended via the
thread suspension mechanism, and then make them runnable again on release.

Logically the mutator lock is held in shared/reader mode if ***either*** the
underlying reader-writer lock is held in shared mode, ***or*** if a mutator is in
runnable state. These two ways of holding the mutator mutex are ***not***
equivalent: In particular, we rely on the garbage collector never actually
entering a "runnable" state while active (see below). However, it often runs with
the explicit mutator mutex in shared mode, thus blocking others from acquiring it
in exclusive mode.

Suspension and checkpoint API
-----------------------------

Suspend point checks enable three kinds of communication with mutator threads:

**Checkpoints**
: Checkpoint requests are used to get a thread to perform an action
on our behalf. `RequestCheckpoint()` asks a specific thread to execute the closure
supplied as an argument at its leisure. `RequestSynchronousCheckpoint()` in
addition waits for the thread to complete running the closure, and handles
suspended threads by running the closure on their behalf. In addition to these
functions provided by `Thread`, `ThreadList` provides the `RunCheckpoint()` function
that runs a checkpoint function on behalf of each thread, either by using
`RequestCheckpoint()` to run it inside a running thread, or by ensuring that a
suspended thread stays suspended, and then running the function on its behalf.
`RunCheckpoint()` does not wait for completion of the function calls triggered by
the resulting `RequestCheckpoint()` invocations.

**Empty checkpoints**
: ThreadList provides `RunEmptyCheckpoint()`, which waits until
all threads have either passed a suspend point, or have been suspended. This
ensures that no thread is still executing Java code inside the same
suspend-point-delimited code interval it was executing before the call. For
example, a read-barrier started before a `RunEmptyCheckpoint()` call will have
finished before the call returns.

**Thread suspension**
: ThreadList provides a number of `SuspendThread...()` calls and
a `SuspendAll()` call to suspend one or all threads until they are resumed by
`Resume()` or `ResumeAll()`. The `Suspend...` calls guarantee that the target
thread(s) are suspended (again, only in the sense of not running Java code)
when the call returns.

Deadlock freedom
----------------

It is easy to deadlock while attempting to run checkpoints, or suspending
threads. In particular, we need to avoid situations in which we cannot suspend
a thread because it is blocked, directly, or indirectly, on the GC completing
its task. Deadlocks are avoided as follows:

**Mutator lock ordering**
The mutator lock participates in the normal ART lock ordering hierarchy, as though it
were a regular lock. See `base/locks.h` for the hierarchy. In particular, only
locks at or below level `kPostMutatorTopLockLevel` may be acquired after
acquiring the mutator lock, e.g. inside the scope of a `ScopedObjectAccess`.
Similarly only locks at level strictly above `kMutatatorLock` may be held while
acquiring the mutator lock, e.g. either by starting a `ScopedObjectAccess`, or
ending a `ScopedThreadSuspension`.

This ensures that code that uses purely mutexes and threads state changes cannot
deadlock: Since we always wait on a lower-level lock, the holder of the
lowest-level lock can always progress. An attempt to initiate a checkpoint or to
suspend another thread must also be treated as an acquisition of the mutator
lock: A thread that is waiting for a lock before it can respond to the request
is itself holding the mutator lock, and can only be blocked on lower-level
locks. And acquisition of those can never depend on acquiring the mutator
lock.

**Checkpoints**
Running a checkpoint in a thread requires suspending that thread for the
duration of the checkpoint, or running the checkpoint on the threads behalf
while that thread is blocked from executing Java code. In the former case, the
checkpoint code is run from `CheckSuspend`, which requires the mutator lock,
so checkpoint code may only acquire mutexes at or below level
`kPostMutatorTopLockLevel`. But that is not sufficient.

No matter whether the checkpoint is run in the target thread, or on its behalf,
the target thread is effectively suspended and prevented from running Java code.
However the target may hold arbitrary Java monitors, which it can no longer
release. This may also prevent higher level mutexes from getting released.  Thus
checkpoint code should only acquire mutexes at level `kPostMonitorLock` or
below.


**Waiting**
This becomes much more problematic when we wait for something other than a lock.
Waiting for something that may depend on the GC, while holding the mutator lock,
can potentially lead to deadlock, since it will prevent the waiting thread from
participating in GC checkpoints. Waiting while holding a lower-level lock like
`thread_list_lock_` is similarly unsafe in general, since a runnable thread may
not respond to checkpoints until it acquires `thread_list_lock_`. In general,
waiting for a condition variable while holding an unrelated lock is problematic,
and these are specific instances of that general problem.

We do currently provide `WaitHoldingLocks`, and it is sometimes used with
low-level locks held. But such code must somehow ensure that such waits
eventually terminate without deadlock.

One common use of WaitHoldingLocks is to wait for weak reference processing.
Special rules apply to avoid deadlocks in this case: Such waits must start after
weak reference processing is disabled; the GC may not issue further nonempty
checkpoints or suspend requests until weak reference processing has been
reenabled, and threads have been notified. Thus the waiting thread's inability
to respond to nonempty checkpoints and suspend requests cannot directly block
the GC. Non-GC checkpoint or suspend requests that target a thread waiting on
reference processing will block until reference processing completes.

Consider a case in which thread W1 waits on reference processing, while holding
a low-level mutex M. Thread W2 holds the mutator lock and waits on M. We avoid a
situation in which the GC needs to suspend or checkpoint W2 by briefly stopping
the world to disable weak reference access. During the stop-the-world phase, W1
cannot yet be waiting for weak-reference access.  Thus there is no danger of
deadlock while entering this phase. After this phase, there is no need for W2 to
suspend or execute a nonempty checkpoint. If we replaced the stop-the-world
phase by a checkpoint, W2 could receive the checkpoint request too late, and be
unable to respond.

Empty checkpoints can continue to occur during reference processing.  Reference
processing wait loops explicitly handle empty checkpoints, and an empty
checkpoint request notifies the condition variable used to wait for reference
processing, after acquiring `reference_processor_lock_`.  This means that empty
checkpoints do not preclude client threads from being in the middle of an
operation that involves a weak reference access, while nonempty checkpoints do.

**Suspending the GC**
Under unusual conditions, the GC can run on any thread. This means that when
thread *A* suspends thread *B* for some other reason, Thread *B* might be
running the garbage collector and conceivably thus cause it to block.  This
would be very deadlock prone. If Thread *A* allocates while Thread *B* is
suspended in the GC, and the allocation requires the GC's help to complete, we
deadlock.

Thus we ensure that the GC, together with anything else that can block GCs,
cannot be blocked for thread suspension requests. This is accomplished by
ensuring that it always appears to be in a suspended thread state. Since we
only check for suspend requests when entering the runnable state, suspend
requests go unnoticed until the GC completes. It may physically acquire and
release the actual `mutator_lock_` in either shared or exclusive mode.

Thread Suspension Mechanics
---------------------------

Thread suspension is initiated by a registered thread, except that, for testing
purposes, `SuspendAll` may be invoked with `self == nullptr`.  We never suspend
the initiating thread, explicitly exclusing it from `SuspendAll()`, and failing
`SuspendThreadBy...()` requests to that effect.

The suspend calls invoke `IncrementSuspendCount()` to increment the thread
suspend count for each thread. That adds a "suspend barrier" (atomic counter) to
the per-thread list of such counters to decrement. It normally sets the
`kSuspendRequest` ("should enter safepoint handler") and `kActiveSuspendBarrier`
("need to notify us when suspended") flags.

After setting these two flags, we check whether the thread is suspended and
`kSuspendRequest` is still set. Since the thread is already suspended, it cannot
be expected to respond to "pass the suspend barrier" (decrement the atomic
counter) in a timely fashion.  Hence we do so on its behalf. This decrements
the "barrier" and removes it from the thread's list of barriers to decrement,
and clears `kActiveSuspendBarrier`. `kSuspendRequest` remains to ensure the
thread doesn't prematurely return to runnable state.

If `SuspendAllInternal()` does not immediately see a suspended state, then it is up
to the target thread to decrement the suspend barrier.
`TransitionFromRunnableToSuspended()` calls
`TransitionToSuspendedAndRunCheckpoints()`, which changes the thread state
and returns. `TransitionFromRunnableToSuspended()` then calls
`CheckActiveSuspendBarriers()` to check for the `kActiveSuspendBarrier` flag
and decrement the suspend barrier if set.

The `suspend_count_lock_` is not consistently held in the target thread
during this process.  Thus correctness in resolving the race between a
suspension-requesting thread and a target thread voluntarily suspending relies
on first requesting suspension, and then checking whether the target is
already suspended, The detailed correctness argument is given in a comment
inside `SuspendAllInternal()`. This also ensures that the barrier cannot be
decremented after the stack frame holding the barrier goes away.

This relies on the fact that the two stores in the two threads to the state and
kActiveSuspendBarrier flag are ordered with respect to the later loads. That's
guaranteed, since they are all stored in a single `atomic<>`. Thus even relaxed
accesses are OK.

The actual suspend barrier representation still varies between `SuspendAll()`
and `SuspendThreadBy...()`.  The former relies on the fact that only one such
barrier can be in use at a time, while the latter maintains a linked list of
active suspend barriers for each target thread, relying on the fact that each
one can appear on the list of only one thread, and we can thus use list nodes
allocated in the stack frames of requesting threads.

**Avoiding suspension cycles**

Any thread can issue a `SuspendThreadByPeer()`, `SuspendThreadByThreadId()` or
`SuspendAll()` request. But if Thread A increments Thread B's suspend count
while Thread B increments Thread A's suspend count, and they then both suspend
during a subsequent thread transition, we're deadlocked.

For single-thread suspension requests, we refuse to initiate
a suspend request from a registered thread that is also being asked to suspend
(i.e. the suspend count is nonzero).  Instead the requestor waits for that
condition to change.  This means that we cannot create a cycle in which each
thread has asked to suspend the next one, and thus no thread can progress.  The
required atomicity of the requestor suspend count check with setting the suspend
count of the target(s) target is ensured by holding `suspend_count_lock_`.

For `SuspendAll()`, we enforce a requirement that at most one `SuspendAll()`
request is running at one time. We also set the `kSuspensionImmune` thread flag
to prevent a single thread suspension of a thread currently between
`SuspendAll()` and `ResumeAll()` calls. Thus once a `SuspendAll()` call starts,
it will complete before it can be affected by suspension requests from other
threads.

[^1]: In the most recent versions of ART, compiler-generated code loads through
    the address at `tlsPtr_.suspend_trigger`. A thread suspension is requested
    by setting this to null, triggering a `SIGSEGV`, causing that thread to
    check for GC cooperation requests. The older mechanism instead sets an
    appropriate `ThreadFlag` entry to request suspension or a checkpoint. Note
    that the actual checkpoint function value is set, along with the flag, while
    holding `suspend_count_lock_`. If the target thread notices that a
    checkpoint is requested, it then acquires the `suspend_count_lock_` to read
    the checkpoint function.
