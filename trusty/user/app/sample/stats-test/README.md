# Metrics Test

The metrics test consists at testing aidl endpoint establishment
and interaction between a vendorAtom Relayer (Metrics TA) in Trusty
and vendorAtom Consumer in Normal World.
The Consumer is expected to advertise itself to the relayer by sharing
its iStats facet via the iStatsSetter interface (AIDL Callback pattern).
Once the Consumer iStats facet is initialized, the Relayer can relay
atoms to the Consumer asynchronously, as soon as receiving them
from any Trusty TA.

We are going to support testing in two modes:

1- Consumer as a Trusty TA first
    Allows to test the Relayer as well as the atoms serialization
    library (see `trusty/interfaces/atoms`).
    Unfortunately asynchronous callback is not supported for Secure World
    initiators due to the single-threaded limitation in Trusty user-space.
    So for this test, we use the synchronous callback approach
    (refer to the sequence diagram in section 1.)

2- Consumer as a Normal-World Daemon
    Allows to test the desired architecture, with Consumer in Normal World
    as the asynchronous callback initiator.
    (refer to the sequence diagram in section 2.)

## 1. Consumer as a Trusty TA


```mermaid
sequenceDiagram
    participant test as Stats PORT_TEST<br/>(Secure World)
    participant consumer as Consumer TA<br/>(Secure World)
    participant relayer as Relayer TA<br/>(Secure World)
    test->>consumer: share memory buffer
    note over relayer: implements IStats as relayer
    test->>+relayer: IStats:recordVendorAtom(atom)
    relayer->>relayer: store IStats object
    relayer-->>-test: return
    test ->> test: wait until shared mem is updated
    note over test, relayer: on Trusty, binder async callbacks are not yet supported<br/>(single-thread limitation)
    note over test, relayer: Hence the consumer needs to regularly call setInterface for receiving atoms
    loop every TIME_OUT_INTERVAL_MS
    note over relayer: implements IStatsSetter
    consumer->>+relayer: IStatsSetter::setInterface(this)
    relayer->>relayer: atom = stored IStats object
    note over consumer: implements IStats as final consumer
    relayer->>+consumer: IStats:recordVendorAtom(atom)
    consumer ->> consumer: write atom to shared mem
    consumer -->> test: shared mem updated
    consumer -->>-relayer: return rc
    relayer -->>- consumer: return
    end
    test ->> test: verify shared mem == atom
```



### Build & Test


```
$ ./trusty/vendor/google/aosp/scripts/build.py qemu-generic-arm64-test-debug --skip-test
$ ./build-root/build-qemu-generic-arm64-test-debug/run --verbose --headless --boot-test "com.android.trusty.stats.test"
```

### Debug Crash Backtrace


```
$ export A2L=$PWD/prebuilts/clang/host/linux-x86/llvm-binutils-stable/llvm-addr2line
$ $A2L -e build-root/build-qemu-generic-arm64-test-debug/user_tasks/trusty/user/app/sample/stats-test/stats-test.syms.elf <address>
```

## 2. Consumer as a Normal-World Daemon


```mermaid
sequenceDiagram
    participant test as GTest<br/>parent thread<br/>(Normal World)
    participant consumer as Consumer<br/>forked thread<br/>(Normal World)
    participant relayer as Relayer TA<br/>(Secure World)
    participant port_test as Stats PORT_TEST<br/>(Secure World)
    critical Initialisation process
    test->>consumer: fork thread and share atom buffer
    note over relayer: implements IStatsSetter
    consumer->>+relayer: IStatsSetter::setInterface(this)
    relayer->>relayer: atom = store IStats object
    relayer -->>- consumer: return
    option unit test process
    test ->> test: wait for shared mem to be updated

    port_test->>port_test: create an atom from canned data
    note over relayer: implements IStats as relayer
    port_test->>+relayer: IStats:recordVendorAtom(atom)
    relayer->>relayer: use IStats object obtained during initialisation
    relayer->>+consumer: IStats:recordVendorAtom(atom)
    consumer ->> consumer: write atom to shared mem
    consumer -->>- relayer: return rc
    relayer -->>- port_test: return
    test ->> test: verify shared mem holds the canned data
    end

```


Implementation notes:

* The Trusty Relayer from section 1. is updated to also
expose an IStatsSetter port to the Normal World, store the Normal World IStats
callback facet, and use it asynchronously upon receiving atoms from
the Stats PORT_TEST.

* The Trusty Stats PORT_TEST is reused as is.

* The test sequence consists in starting the Normal-World GTest first, then
  the Stats PORT_TEST. Both unit tests should provide a PASS verdict for the
  test to succeed.
