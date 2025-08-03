# Service Entitlement

Service entitlement library exports the APIs for querying the service status, based on GSMA TS.43
spec.

## How to debug

###  Log TAG to filter:
* ServiceEntitlement


###  Enable logging the PII data:
Grand the permission:

```shell
adb root
```

Enable by system property:

```shell
adb shell setprop dbg.se.pii_loggable true
```
NOTE Debug option only available on devices which is built as userdebug.