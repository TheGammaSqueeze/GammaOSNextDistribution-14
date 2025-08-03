Feature details
---------------

This feature is to track power consumption by NFCC at different states.
Currently it will track STANDBY, ULPDET and ACTIVE state. It will count number
of times in milliseconds spent in each of specified state and number of times
each state is entered since boot.

Feature enable
--------------
This feature can be enabled by setting POWER_STATE_FEATURE to true.
There are two ways we can set this variables.
  - This can be integrated into build by adding below line to device-nfc.mk
POWER_STATE_FEATURE ?= true
  - We can set these variables during make command
make -j8 POWER_STATE_FEATURE=true.

Config file change
------------------
We can specify refresh time for power data from config file /vendor/etc/libnfc-nxp.conf.
Higher the value set for the config below, lesser would be the overhead of additional
power consumption to collect the stats.

NXP_SYSTEM_POWER_TRACE_POLL_DURATION_SEC=30

Integration Guide
------------------
Below code snippet shows how to integrate Nfc power stats with PowerStats HAL.

Create PowerStats HAL instance
'std::shared_ptr<PowerStats> p = ndk::SharedRefBase::make<PowerStats>();'

Create PixelStateResidencyDataProvider instance
'auto pixelSdp = std::make_unique<PixelStateResidencyDataProvider>();'

Add "Nfc" Entity with 3 states namely STANDBY, ULPDET, ACTIVE
'pixelSdp->addEntity("Nfc", {{0, "STANDBY"}, {1, "ULPDET"}, {2, "ACTIVE"}});'

Start PixelStateResidencyDataProvider. This will register binder to service manager.
'pixelSdp->start();'

Add PixelStateResidencyDataProvider to PowerStats HAL
'p->addStateResidencyDataProvider(std::move(pixelSdp));'

Register PowerStats HAL with service manager
'const std::string instance = std::string() + PowerStats::descriptor + "/default";'
'binder_status_t status = AServiceManager_addService(p->asBinder().get(), instance.c_str());'
