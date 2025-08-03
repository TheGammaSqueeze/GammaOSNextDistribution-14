# Automated testing

IntentResolver test code is organized into sub-modules by scope and purpose.

TreeHugger execution is controlled via [TEST_MAPPING](../TEST_MAPPING).

## [Unit Tests](unit)

Instrumentation tests which run on devices or emulators, but are otherwise isolated from the system. Scope of verification is limited to a single component at a time. These tests are extremely fast and should provide the most detailed and granular failure information.

**Use cases**: The first choice for all new code. Fakes and other reusable test code should be placed in [shared](shared).

## [Integration Tests](integration)

Emulator tests which verify operation of the foundational components backed by android platform APIs. These tests are required for coverage because components tested here are replaced with fakes in other test suites.

**Use cases**: Larger tests which require device preparation and setup to test production code using real dependencies. Implement these when verification is needed of interactions with live system services or applications using real data.

## [Activity Tests](activity)

Instrumentation tests which launch target activity code directly in the instrumentation context. These operate mostly production code end to end and provide a blend of UI assertions and verification using injected mocks and fakes.

Originally from `frameworks/base/core/tests`, these cover the widest range of code but are historically the most flaky, brittle and with the least informative failures.

Use Hilt's [@TestInstallIn](https://developer.android.com/training/dependency-injection/hilt-testing) to replace dependencies with alternates as needed. Test modules should be added here, while the fakes and other utilities used in these tests are found in [tests/shared](shared).

**Use cases**: New tests and expansion of existing tests should be considered only as last resort for otherwise untestable code.

## [Shared](shared)

Testing code as a common dependency available to all the above test types.

**Use cases**: Fakes, reusable assertions, or other test setup code. Tests for code here should be placed in [tests/unit](unit).
