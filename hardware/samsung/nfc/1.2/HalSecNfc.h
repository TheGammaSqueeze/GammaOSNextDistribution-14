#ifndef __NFC_SEC_HAL__
#define __NFC_SEC_HAL__

#include <android/hardware/nfc/1.2/INfc.h>
#include <android/hardware/nfc/1.2/types.h>
#include <hardware/nfc.h>

using ::android::hardware::nfc::V1_2::NfcConfig;

int nfc_hal_init(void);
void nfc_hal_deinit(void);
int nfc_hal_open(nfc_stack_callback_t* p_cback,
                 nfc_stack_data_callback_t* p_data_cback);
int nfc_hal_write(uint16_t data_len, const uint8_t* p_data);
int nfc_hal_core_initialized(uint8_t* p_core_init_rsp_params);
int nfc_hal_pre_discover();
int nfc_hal_close();
int nfc_hal_control_granted();
int nfc_hal_power_cycle();

int nfc_hal_factory_reset(void);
int nfc_hal_closeForPowerOffCase(void);
void nfc_hal_getVendorConfig(android::hardware::nfc::V1_1::NfcConfig& config);
void nfc_hal_getVendorConfig_1_2(NfcConfig& config);

#endif  // __NFC_SEC_HAL__
