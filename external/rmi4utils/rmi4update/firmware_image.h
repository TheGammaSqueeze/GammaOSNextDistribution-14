/*
 * Copyright (C) 2012 - 2014 Andrew Duggan
 * Copyright (C) 2012 - 2014 Synaptics Inc
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

#ifndef _FIRMWAREIMAGE_H_
#define _FIRMWAREIMAGE_H_

#include "rmidevice.h"
#include "updateutil.h"

#define RMI_IMG_CHECKSUM_OFFSET			0
#define RMI_IMG_IO_OFFSET			0x06
#define RMI_IMG_BOOTLOADER_VERSION_OFFSET	0x07
#define RMI_IMG_IMAGE_SIZE_OFFSET		0x08
#define RMI_IMG_CONFIG_SIZE_OFFSET		0x0C
#define RMI_IMG_PACKAGE_ID_OFFSET		0x1A
#define RMI_IMG_FW_BUILD_ID_OFFSET		0x50

#define RMI_IMG_PRODUCT_ID_OFFSET		0x10
#define RMI_IMG_PRODUCT_INFO_OFFSET		0x1E

#define RMI_IMG_FW_OFFSET			0x100

#define RMI_IMG_LOCKDOWN_V2_OFFSET		0xD0
#define RMI_IMG_LOCKDOWN_V2_SIZE		0x30

#define RMI_IMG_LOCKDOWN_V3_OFFSET		0xC0
#define RMI_IMG_LOCKDOWN_V3_SIZE		0x40

#define RMI_IMG_LOCKDOWN_V5_OFFSET		0xB0
#define RMI_IMG_LOCKDOWN_V5_SIZE		0x50

// Leon add for BL_V7
#define RMI_IMG_V10_CNTR_ADDR_OFFSET		0x0C
#define RMI_IMG_V10_SIGNATURE_VERSION_NUMBER 0x11
#define RMI_IMG_V10_SIGNATURE_LENGTH_OFFSET 0x8
#define RMI_IMG_V10_SIGNATURE_LENGTH_SIZE 4

struct container_descriptor {
	unsigned char content_checksum[4];
	unsigned char container_id[2];
	unsigned char minor_version;
	unsigned char major_version;
	unsigned char signature_size[4];
	unsigned char container_option_flags[4];
	unsigned char content_options_length[4];
	unsigned char content_options_address[4];
	unsigned char content_length[4];
	unsigned char content_address[4];
};

enum container_id {
	TOP_LEVEL_CONTAINER = 0,
	UI_CONTAINER,
	UI_CONFIG_CONTAINER,
	BL_CONTAINER,
	BL_IMAGE_CONTAINER,
	BL_CONFIG_CONTAINER,
	BL_LOCKDOWN_INFO_CONTAINER,
	PERMANENT_CONFIG_CONTAINER,
	GUEST_CODE_CONTAINER,
	BL_PROTOCOL_DESCRIPTOR_CONTAINER,
	UI_PROTOCOL_DESCRIPTOR_CONTAINER,
	RMI_SELF_DISCOVERY_CONTAINER,
	RMI_PAGE_CONTENT_CONTAINER,
	GENERAL_INFORMATION_CONTAINER,
	DEVICE_CONFIG_CONTAINER,
	FLASH_CONFIG_CONTAINER,
	GUEST_SERIALIZATION_CONTAINER,
	GLOBAL_PARAMETERS_CONTAINER,
	CORE_CODE_CONTAINER,
	CORE_CONFIG_CONTAINER,
	DISPLAY_CONFIG_CONTAINER,
	EXTERNAL_TOUCH_AFE_CONFIG_CONTAINER,
	UTILITY_CONTAINER,
	UTILITY_PARAMETER_CONTAINER,
	// Reserved : 24 ~ 26
	// V10 above
	FIXED_LOCATION_DATA_CONTAINER = 27,
};

enum signature_BLv7 {
	BLv7_CORE_CODE = 0,
	BLv7_CORE_CONFIG,
	BLv7_FLASH_CONFIG,
	BLv7_FLD,
	BLv7_MAX
};

struct signature_info {
	bool bExisted;
	unsigned short size;
};
// BL_V7 end

class FirmwareImage
{
public:
	FirmwareImage() : m_firmwareBuildID(0), m_packageID(0), m_firmwareData(NULL), m_configData(NULL), m_lockdownData(NULL),
				m_memBlock(NULL), m_hasSignature(false), m_fldData(NULL), m_fldSize(0), m_globalparaData(NULL), m_globalparaSize(0),
				m_firmwareVersion(0), m_hasFirmwareVersion(false)
	{}
	int Initialize(const char * filename);
	int VerifyImageMatchesDevice(unsigned long deviceFirmwareSize,
					unsigned long deviceConfigSize);
	unsigned char * GetFirmwareData() { return m_firmwareData; }
	unsigned char * GetConfigData() { return m_configData; }
	unsigned char * GetFlashConfigData() { return m_flashConfigData; }
	unsigned char * GetLockdownData() { return m_lockdownData; }
	unsigned char * GetFLDData() { return m_fldData; }
	unsigned char * GetGlobalParametersData() { return m_globalparaData; }
	unsigned long GetFirmwareSize() { return m_firmwareSize; }
	unsigned long GetConfigSize() { return m_configSize; }
	unsigned long GetFlashConfigSize() { return m_flashConfigSize; }
	unsigned long GetLockdownSize() { return m_lockdownSize; }
	unsigned long GetFirmwareID() { return m_firmwareBuildID; }
	unsigned long GetFLDSize() { return m_fldSize; }
	unsigned long GetGlobalParametersSize() { return m_globalparaSize; }
	unsigned short GetFirmwareVersion() { return m_firmwareVersion; }
	signature_info *GetSignatureInfo() { return m_signatureInfo; }
	int VerifyImageProductID(char* deviceProductID);
	bool IsImageHasFirmwareVersion() { return m_hasFirmwareVersion; }

	bool HasIO() { return m_io; }
	~FirmwareImage();

private:
	unsigned long Checksum(unsigned short * data, unsigned long len);
	void PrintHeaderInfo();
	void ParseHierarchicalImg();	// BL_V7

private:
	unsigned long m_checksum;
	unsigned long m_firmwareSize;
	unsigned long m_configSize;
	unsigned long m_flashConfigSize;
	unsigned long m_lockdownSize;
	long m_imageSize;
	unsigned long m_firmwareBuildID;
	unsigned short m_packageID;
	unsigned char m_bootloaderVersion;
	unsigned char m_io;
	char m_productID[RMI_PRODUCT_ID_LENGTH + 1];
	unsigned short m_productInfo;

	unsigned char * m_firmwareData;
	unsigned char * m_configData;
	unsigned char * m_flashConfigData;
	unsigned char * m_lockdownData;
	unsigned char * m_memBlock;
	unsigned long m_cntrAddr;	// BL_V7
	bool m_hasSignature;
	unsigned char * m_fldData;
	unsigned long m_fldSize;
	unsigned char * m_globalparaData;
	unsigned long m_globalparaSize;
	unsigned short m_firmwareVersion;
	bool m_hasFirmwareVersion;

	signature_info m_signatureInfo[BLv7_MAX];
};

#endif // _FIRMWAREIMAGE_H_
