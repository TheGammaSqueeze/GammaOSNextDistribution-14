CTS tests for `CompanionDeviceManager` are split into 2 modules:
`CtsCompanionDeviceManagerCoreTestCases` (a.k.a. "Core Tests") and
`CtsCompanionDeviceManagerUiAutomationTestCases` (a.k.a. "UiAutomation Tests").

The core difference between the two test modules is that `CtsCompanionDeviceManager_Core_TestCases`
does NOT use `UiAutomation` which makes it:
- faster
- suitable for to run on NFFs
- less prone to flakiness
- better suitable to run in `PRESUBMIT`.

`CtsCompanionDeviceManager_UiAutomation_TestCases`, on the other hand, uses `UiAutomation` in order
to test CDM flows end-to-end and is (at least for now) designed to run only on the mobile
form-factor and requires a discoverable BT device nearby.
