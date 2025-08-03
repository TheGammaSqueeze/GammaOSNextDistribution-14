[AID_VENDOR_NXP_STRONGBOX]
value:2901

[AID_VENDOR_NXP_WEAVER]
value:2902

[AID_VENDOR_NXP_AUTHSECRET]
value:2903

[vendor/bin/hw/android.hardware.security.keymint-service.strongbox.nxp]
mode: 0755
user: AID_VENDOR_NXP_STRONGBOX
group: AID_SYSTEM
caps: SYS_ADMIN SYS_NICE WAKE_ALARM

[vendor/bin/hw/android.hardware.weaver-service.nxp]
mode: 0755
user: AID_VENDOR_NXP_WEAVER
group: AID_SYSTEM
caps: SYS_ADMIN SYS_NICE WAKE_ALARM

[vendor/bin/hw/android.hardware.authsecret-service.nxp]
mode: 0755
user: AID_VENDOR_NXP_AUTHSECRET
group: AID_SYSTEM
caps: SYS_ADMIN SYS_NICE WAKE_ALARM

