### CHRE Simulation Test Framework

#### Background

Simulation tests are written for the CHRE linux (i.e. simulation) platform, and
can be useful in validating higher level CHRE behavior. By "higher level", we
mean:

* More coverage than a module-level unit test.
* But smaller in scope compared to a full end-to-end integration test.

You can think of a simulation test as treating the core CHRE framework as a
black box, and is able to validate its output.

#### Running the tests

You can run simulation tests through `atest`:

```
atest --host chre_simulation_tests
```

#### How to write a test

The simulation test framework encourages writing self contained tests as follow:

```cpp
// Use the same unique prefix for all the tests in a single file
TEST_F(TestBase, <PrefixedTestName>) {
  // 1. Create tests event to trigger code in the Nanoapp context.
  CREATE_CHRE_TEST_EVENT(MY_TEST_EVENT, 0);

  // 2. Create a test Nanpoapp by inheriting TestNanoapp.
  class App : public TestNanoapp {
    void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
      switch (eventType) {
        // 3. Handle system events.
        case CHRE_EVENT_WIFI_ASYNC_RESULT: {
          // ...
          // 4. Send event back to the test.
          TestEventQueueSingleton::get()->pushEvent(
            CHRE_EVENT_WIFI_ASYNC_RESULT)
          break;
        }

        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            // 5. Handle test events to execute code in the context the Nanoapp.
            case MY_TEST_EVENT:
              // ...
              break;
          }
        }
      }
    }
  };

  // 6. Load the app and add initial expectations.
  uint64_t appId = loadNanoapp(MakeUnique<App>());;
  EXPECT_TRUE(...);

  // 7. Send test events to the Nanoapp to execute some actions and add
  //    expectations about the result.
  sendEventToNanoapp(appId, MY_TEST_EVENT);
  waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT);
  EXPECT_TRUE(...);

  // 8. Optionally unload the Nanoapp
  unloadNanoapp(appId);
}
```

##### Test app (#2, #6, #8)

Inherit from `TestNanoapp` to create a test nanoapp.

If you need to customize any of the nanoapp `name`, `id`, `version`, or `perms`,
you will need to add a constructor calling the `TestNanoapp` constructor with that info, i.e.:

```
class App: public TestNanoapp {
  public:
    explicit App(TestNanoappInfo info): TestNanoapp(info) {}

  // ...
};
```

The nanoapp entry points are implemented as methods of the class:

- `start`,
- `handleEvent`,
- `end`.

##### Test events (#1)

The test events are local to a single test and created using the
`CREATE_CHRE_TEST_EVENT(name, id)` macro. The id must be unique in a single
test and in the range [0, 0xfff].

##### System event (#3)

Add code to `handleEvent` to handle the system events you are interested in for
the test:

```cpp
void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
  switch (eventType) {
    case CHRE_EVENT_WIFI_ASYNC_RESULT: {
      // ...
      break;
    }
  }
}
```

The handler would typically send an event back to the nanoapp, see the next
section for more details.

##### Send event from the nanoapp (#4)

You can send an event from the nanoapp (typically inside `handleEvent`):

```cpp
// Sending a system event.
TestEventQueueSingleton::get()->pushEvent(CHRE_EVENT_WIFI_ASYNC_RESULT);

// Sending a test event.
TestEventQueueSingleton::get()->pushEvent(MY_TEST_EVENT);
```

Use `waitForEvent` to wait for an event in your test code:

```cpp
// Wait for a system event.
waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT);

// Wait for a test event.
waitForEvent(MY_TEST_EVENT);
```

Waiting for an event as described above is sufficient to express a boolean
expectation. For example the status of an event:

```cpp
void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
  switch (eventType) {
    case CHRE_EVENT_WIFI_ASYNC_RESULT: {
      auto *event = static_cast<const chreAsyncResult *>(eventData);
      if (event->success) {
        TestEventQueueSingleton::get()->pushEvent(
            CHRE_EVENT_WIFI_ASYNC_RESULT);
      }
      break;
    }
  }
}
```

With the above snippet `waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT)` will timeout
if the nanoapp did not receive a successful status.

Sometimes you want to attach additional data alongside the event. Simply pass
the data as the second argument to pushEvent:

```cpp
  void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
    switch (eventType) {
      case CHRE_EVENT_WIFI_ASYNC_RESULT: {
        auto *event = static_cast<const chreAsyncResult *>(eventData);
        if (event->success) {
          TestEventQueueSingleton::get()->pushEvent(
              CHRE_EVENT_WIFI_ASYNC_RESULT,
              *(static_cast<const uint32_t *>(event->cookie)));
        }
        break;
      }
    }
  }
```

The data must be trivially copyable (a scalar or a struct of scalar are safe).

Use the second argument of `waitForEvent` to retrieve the data in your test
code:

```cpp
uint32_t cookie;
waitForEvent(CHRE_EVENT_WIFI_ASYNC_RESULT, &cookie);
EXPECT_EQ(cookie, ...);
```

##### Send event to the nanoapp (#5)

To execute the code in the nanoapp context, you will need to create a test
event and send it to the nanoapp as follow:

```cpp
CREATE_CHRE_TEST_EVENT(MY_TEST_EVENT, 0);

// ...

sendEventToNanoapp(appId, MY_TEST_EVENT);
```

The code to be executed in the context of the nanoapp should be added to its
`handleEvent` function:

```cpp
void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
  switch (eventType) {
    // Test event are received with a CHRE_EVENT_TEST_EVENT type.
    case CHRE_EVENT_TEST_EVENT: {
      auto event = static_cast<const TestEvent *>(eventData);
      switch (event->type) {
        // Create a case for each of the test events.
        case MY_TEST_EVENT:
          // Code running in the context of the nanoapp.
          break;
      }
    }
  }
}
```

It is possible to send data alongside a test event:

```cpp
bool enable = true;
sendEventToNanoapp(appId, MY_TEST_EVENT, &enable);
```

The data should be a scalar type or a struct of scalars. Be careful not to send
a pointer to a memory block that might be released before the data is consumed
in `handleEvent`. This would result in a use after free error and flaky tests.

The `handleEvent` function receives a copy of the data in the `data` field of
the `TestEvent`:

```cpp
void handleEvent(uint32_t, uint16_t eventType, const void *eventData) override {
  switch (eventType) {
    // Test event are received with a CHRE_EVENT_TEST_EVENT type.
    case CHRE_EVENT_TEST_EVENT: {
      auto event = static_cast<const TestEvent *>(eventData);
      switch (event->type) {
        // Create a case for each of the test events.
        case MY_TEST_EVENT:
          chreFunctionTakingABool(*(bool*(event->data)));
          break;
      }
    }
  }
}
```
