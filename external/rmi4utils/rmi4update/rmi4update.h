/*
 * Copyright (C) 2014 Andrew Duggan
 * Copyright (C) 2014 Synaptics Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
#ifndef _RMI4UPDATE_H_
#define _RMI4UPDATE_H_

#include "rmidevice.h"
#include "firmware_image.h"

#define RMI_BOOTLOADER_ID_SIZE		2

// leon add
enum v7_status {
	SUCCESS = 0x00,
	DEVICE_NOT_IN_BOOTLOADER_MODE,
	INVALID_PARTITION,
	INVALID_COMMAND,
	INVALID_BLOCK_OFFSET,
	INVALID_TRANSFER,
	NOT_ERASED,
	FLASH_PROGRAMMING_KEY_INCORRECT,
	BAD_PARTITION_TABLE,
	CHECKSUM_FAILED,
	WRITE_PROTECTION = 0x0E,
	FLASH_HARDWARE_FAILURE = 0x1f,
};

enum v7_partition_id {
	NONE_PARTITION = 0x00,
	BOOTLOADER_PARTITION = 0x01,
	DEVICE_CONFIG_PARTITION,
	FLASH_CONFIG_PARTITION,
	MANUFACTURING_BLOCK_PARTITION,
	GUEST_SERIALIZATION_PARTITION,
	GLOBAL_PARAMETERS_PARTITION,
	CORE_CODE_PARTITION,
	CORE_CONFIG_PARTITION,
	GUEST_CODE_PARTITION,
	DISPLAY_CONFIG_PARTITION,
	EXTERNAL_TOUCH_AFE_CONFIG_PARTITION,
	UTILITY_PARAMETER_PARTITION,
	FIXED_LOCATION_DATA_PARTITION = 0x0E,
};

enum v7_flash_command {
	CMD_V7_IDLE = 0x00,
	CMD_V7_ENTER_BL,
	CMD_V7_READ,
	CMD_V7_WRITE,
	CMD_V7_ERASE,
	CMD_V7_ERASE_AP,
	CMD_V7_SENSOR_ID,
	CMD_V7_SIGNATURE,
};

enum bl_version {
	BL_V5 = 5,
	BL_V6 = 6,
	BL_V7 = 7,
	BL_V8 = 8,
	BL_V10 = 10,
};

struct f34_v7_query_0 {
	union {
		struct {
			unsigned char subpacket_1_size:3;
			unsigned char has_config_id:1;
			unsigned char f34_query0_b4:1;
			unsigned char has_thqa:1;
			unsigned char f34_query0_b6__7:2;
		} __attribute__((packed));;
		unsigned char data[1];
	};
};

struct f34_v7_query_1_7 {
	union {
		struct {
			/* query 1 */
			unsigned char bl_minor_revision;
			unsigned char bl_major_revision;

			/* query 2 */
			unsigned char bl_fw_id_7_0;
			unsigned char bl_fw_id_15_8;
			unsigned char bl_fw_id_23_16;
			unsigned char bl_fw_id_31_24;

			/* query 3 */
			unsigned char minimum_write_size;
			unsigned char block_size_7_0;
			unsigned char block_size_15_8;
			unsigned char flash_page_size_7_0;
			unsigned char flash_page_size_15_8;

			/* query 4 */
			unsigned char adjustable_partition_area_size_7_0;
			unsigned char adjustable_partition_area_size_15_8;

			/* query 5 */
			unsigned char flash_config_length_7_0;
			unsigned char flash_config_length_15_8;

			/* query 6 */
			unsigned char payload_length_7_0;
			unsigned char payload_length_15_8;

			/* query 7 */
			unsigned char f34_query7_b0:1;
			unsigned char has_bootloader:1;
			unsigned char has_device_config:1;
			unsigned char has_flash_config:1;
			unsigned char has_manufacturing_block:1;
			unsigned char has_guest_serialization:1;
			unsigned char has_global_parameters:1;
			unsigned char has_core_code:1;
			unsigned char has_core_config:1;
			unsigned char has_guest_code:1;
			unsigned char has_display_config:1;
			unsigned char f34_query7_b11_13:3;
			unsigned char has_fld:1;
			unsigned char f34_query7_b15:1;
			unsigned char f34_query7_b16__23;
			unsigned char f34_query7_b24__31;
		} __attribute__((packed));;
		unsigned char data[21];
	};
};

struct partition_tbl
{
	union {
		struct {
			unsigned short partition_id;
			unsigned short partition_len;
			unsigned short partition_addr;
			unsigned short partition_prop;
		} __attribute__((packed));;
		unsigned char data[8];
	};
};
// leon end

class RMI4Update
{
public:
	RMI4Update(RMIDevice & device, FirmwareImage & firmwareImage) : m_device(device), 
			m_firmwareImage(firmwareImage), m_writeBlockWithCmd(true)
	{
		m_IsErased = false;
		m_hasCoreCode = false;
		m_hasCoreConfig = false;
		m_hasFlashConfig = false;
		m_hasFLD = false;
		m_hasGlobalParameters = false;
	}
	int UpdateFirmware(bool force = false, bool performLockdown = false);

private:
	int DisableNonessentialInterupts();
	int FindUpdateFunctions();
	int ReadFlashConfig();
	int rmi4update_poll();
	int ReadF34QueriesV7();
	int ReadF34Queries();
	int ReadF34Controls();
	int WriteBootloaderID();
	int EnterFlashProgrammingV7();
	int EraseFirmwareV7();
	int EraseFlashConfigV10();
	int EraseCoreCodeV10();
	int WriteFirmwareV7();
	int WriteCoreConfigV7();
	int WriteFlashConfigV7();
	int WriteFLDV7();
	int WriteGlobalParametersV7();
	int EnterFlashProgramming();
	int WriteBlocks(unsigned char *block, unsigned short count, unsigned char cmd);
	int WaitForIdle(int timeout_ms, bool readF34OnSucess = true);
	int GetFirmwareSize() { return m_blockSize * m_fwBlockCount; }
	int GetConfigSize() { return m_blockSize * m_configBlockCount; }
	int WriteSignatureV7(enum signature_BLv7 signature_partition, unsigned char* data, int offset);
	bool IsBLv87();
	int ReadMSL();

private:
	RMIDevice & m_device;
	FirmwareImage & m_firmwareImage;

	RMIFunction m_f01;
	RMIFunction m_f34;

	unsigned char m_deviceStatus;
	unsigned char m_bootloaderID[RMI_BOOTLOADER_ID_SIZE];
	bool m_writeBlockWithCmd;

	/* F34 Controls */
	unsigned char m_f34Command;
	unsigned char m_f34Status;
	bool m_programEnabled;

	/* F34 Query */
	bool m_hasNewRegmap;
	bool m_unlocked;
	bool m_hasConfigID;
	unsigned short m_blockSize;
	unsigned short m_fwBlockCount;
	unsigned short m_configBlockCount;
	/* for BL_V7 */
	unsigned short m_flashConfigLength;
	unsigned short m_payloadLength;
	unsigned short m_guestBlockCount;
	struct partition_tbl *m_partitionCore;
	struct partition_tbl *m_partitionConfig;
	struct partition_tbl *m_partitionGuest;
	unsigned char m_flashStatus;
	unsigned char m_flashCmd;
	unsigned char m_inBLmode;
	unsigned long m_buildID;
	unsigned char *m_guestData;
	bool m_hasCoreCode;
	bool m_hasCoreConfig;
	bool m_hasFlashConfig;
	bool m_hasGlobalParameters;
	/* BL_V7 end */

	/* BL v8.7 */
	unsigned short m_MSL;
	/* BL v8.7 end */

	/* for BL V10 */
	bool m_hasFLD;
	/* BL_V7 end */

	unsigned short m_f34StatusAddr;
	enum bl_version m_blVersion;

	bool m_IsErased;
};

#endif // _RMI4UPDATE_H_
