## Health Connect platform tests ##

Include different tests for the platform APIs:

- cts - required for the platform release. Test platform API behaviour, not the implementatation.
  Included to the compatability and mainline tests suites.
- unittests - small tests, test apis implementation. Included to the mainline tests suite.
- PermissionIntegrationTests - integration tests for the permission flow implementation. Require
  signature permission, not included to the compatability and mainline tests suites.
