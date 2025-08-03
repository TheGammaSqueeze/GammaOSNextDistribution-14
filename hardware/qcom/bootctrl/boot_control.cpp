/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <map>
#include <memory>
#include <list>
#include <string>
#include <vector>
#ifdef __cplusplus
extern "C" {
#endif
#include <errno.h>
#define LOG_TAG "bootcontrolhal"
#include <log/log.h>
#include <hardware/boot_control.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <limits.h>
#include <cutils/properties.h>
#include "gpt-utils.h"

#define BOOTDEV_DIR "/dev/block/bootdevice/by-name"
#define BOOT_IMG_PTN_NAME "boot_"
#define LUN_NAME_END_LOC 14
#define BOOT_SLOT_PROP "ro.boot.slot_suffix"

#define SLOT_ACTIVE 1
#define SLOT_INACTIVE 2
#define UPDATE_SLOT(pentry, guid, slot_state) ({ \
		memcpy(pentry, guid, TYPE_GUID_SIZE); \
		if (slot_state == SLOT_ACTIVE)\
			*(pentry + AB_FLAG_OFFSET) = AB_SLOT_ACTIVE_VAL; \
		else if (slot_state == SLOT_INACTIVE) \
		*(pentry + AB_FLAG_OFFSET)  = (*(pentry + AB_FLAG_OFFSET)& \
			~AB_PARTITION_ATTR_SLOT_ACTIVE); \
		})

using namespace std;
const char *slot_suffix_arr[] = {
	AB_SLOT_A_SUFFIX,
	AB_SLOT_B_SUFFIX,
	NULL};

enum part_attr_type {
	ATTR_SLOT_ACTIVE = 0,
	ATTR_BOOT_SUCCESSFUL,
	ATTR_UNBOOTABLE,
};

enum part_stat_result_type {
	PARTITION_FOUND,
	PARTITION_MISSING,
	PARTITION_STAT_ERROR,
};

void boot_control_init(struct boot_control_module *module)
{
	if (!module) {
		ALOGE("Invalid argument passed to %s", __func__);
		return;
	}
	return;
}

//Get the value of one of the attribute fields for a partition.
static int get_partition_attribute(char *partname,
		enum part_attr_type part_attr)
{
	uint8_t *pentry = NULL;
	uint8_t *attr = NULL;
	if (!partname)
		return -1;
	std::unique_ptr<struct gpt_disk, decltype(&gpt_disk_free)> disk_raii(gpt_disk_alloc(), &gpt_disk_free);
	if (!disk_raii.get()) {
		ALOGE("%s: Failed to alloc disk struct", __func__);
		return -1;
	}
	if (gpt_disk_get_disk_info(partname, disk_raii.get())) {
		ALOGE("%s: Failed to get disk info", __func__);
		return -1;
	}
	pentry = gpt_disk_get_pentry(disk_raii.get(), partname, PRIMARY_GPT);
	if (!pentry) {
		ALOGE("%s: pentry does not exist in disk struct",  __func__);
		return -1;
	}
	attr = pentry + AB_FLAG_OFFSET;
	if (part_attr == ATTR_SLOT_ACTIVE)
		return !!(*attr & AB_PARTITION_ATTR_SLOT_ACTIVE);
	else if (part_attr == ATTR_BOOT_SUCCESSFUL)
		return !!(*attr & AB_PARTITION_ATTR_BOOT_SUCCESSFUL);
	else if (part_attr == ATTR_UNBOOTABLE)
		return !!(*attr & AB_PARTITION_ATTR_UNBOOTABLE);
	return -1;
}

// Stat a block device. First stat using lstat, if successful make sure that
// stat is successful as well. This minimizes the risk of missing selinux
// permissions.
enum part_stat_result_type stat_block_device(const char *dev_path)
{
	struct stat st;
	if (lstat(dev_path, &st)) {
		// Partition could not be found
		return PARTITION_MISSING;
	}
	errno = 0;
	if (stat(dev_path, &st)) {
		// Symbolic link exists, but unable to stat the target.
		// Either the file does not exist (broken symlink) or
		// missing selinux permission on block device
		ALOGE("Unable to stat block device: %s, %s",
			dev_path,
			strerror(errno));
		return PARTITION_STAT_ERROR;
	}
	return PARTITION_FOUND;
}

//Set a particular attribute for all the partitions in a
//slot
static int update_slot_attribute(const char *slot,
		enum part_attr_type ab_attr)
{
	unsigned int i = 0;
	char buf[PATH_MAX];
	uint8_t *pentry = NULL;
	uint8_t *pentry_bak = NULL;
	uint8_t *attr = NULL;
	uint8_t *attr_bak = NULL;
	std::unique_ptr<struct gpt_disk, decltype(&gpt_disk_free)> disk_raii(nullptr, &gpt_disk_free);
	char partName[MAX_GPT_NAME_SIZE + 1] = {0};
	const char ptn_list[][MAX_GPT_NAME_SIZE] = { AB_PTN_LIST };
	int slot_name_valid = 0;
	if (!slot) {
		ALOGE("%s: Invalid argument", __func__);
		return -1;
	}
	for (i = 0; slot_suffix_arr[i] != NULL; i++)
	{
		if (!strncmp(slot, slot_suffix_arr[i],
					strlen(slot_suffix_arr[i])))
				slot_name_valid = 1;
	}
	if (!slot_name_valid) {
		ALOGE("%s: Invalid slot name", __func__);
		return -1;
	}
	for (i=0; i < ARRAY_SIZE(ptn_list); i++) {
		memset(buf, '\0', sizeof(buf));
		//Check if A/B versions of this ptn exist
		snprintf(buf, sizeof(buf) - 1,
                                        "%s/%s%s",
                                        BOOT_DEV_DIR,
                                        ptn_list[i],
					AB_SLOT_A_SUFFIX
					);
		enum part_stat_result_type stat_result = stat_block_device(buf);
		if (stat_result == PARTITION_MISSING) {
			//partition does not have _a version
			continue;
		} else if (stat_result == PARTITION_STAT_ERROR) {
			return -1;
		}
		memset(buf, '\0', sizeof(buf));
		snprintf(buf, sizeof(buf) - 1,
                                        "%s/%s%s",
                                        BOOT_DEV_DIR,
                                        ptn_list[i],
					AB_SLOT_B_SUFFIX
					);
		stat_result = stat_block_device(buf);
		if (stat_result == PARTITION_MISSING) {
			//partition does not have _b version
			continue;
		} else if (stat_result == PARTITION_STAT_ERROR) {
			return -1;
		}
		memset(partName, '\0', sizeof(partName));
		snprintf(partName,
				sizeof(partName) - 1,
				"%s%s",
				ptn_list[i],
				slot);
		disk_raii = std::unique_ptr<struct gpt_disk, decltype(&gpt_disk_free)>(
			gpt_disk_alloc(), &gpt_disk_free);
		if (!disk_raii.get()) {
			ALOGE("%s: Failed to alloc disk struct",
					__func__);
			return -1;
		}
		if (gpt_disk_get_disk_info(partName, disk_raii.get()) != 0) {
			ALOGE("%s: Failed to get disk info for %s",
					__func__,
					partName);
			return -1;
		}
		pentry = gpt_disk_get_pentry(disk_raii.get(), partName, PRIMARY_GPT);
		pentry_bak = gpt_disk_get_pentry(disk_raii.get(), partName, SECONDARY_GPT);
		if (!pentry || !pentry_bak) {
			ALOGE("%s: Failed to get pentry/pentry_bak for %s",
					__func__,
					partName);
			return -1;
		}
		attr = pentry + AB_FLAG_OFFSET;
		attr_bak = pentry_bak + AB_FLAG_OFFSET;
		if (ab_attr == ATTR_BOOT_SUCCESSFUL) {
			*attr = (*attr) | AB_PARTITION_ATTR_BOOT_SUCCESSFUL;
			*attr_bak = (*attr_bak) |
				AB_PARTITION_ATTR_BOOT_SUCCESSFUL;
		} else if (ab_attr == ATTR_UNBOOTABLE) {
			*attr = (*attr) | AB_PARTITION_ATTR_UNBOOTABLE;
			*attr_bak = (*attr_bak) | AB_PARTITION_ATTR_UNBOOTABLE;
		} else if (ab_attr == ATTR_SLOT_ACTIVE) {
			*attr = (*attr) | AB_PARTITION_ATTR_SLOT_ACTIVE;
			*attr_bak = (*attr) | AB_PARTITION_ATTR_SLOT_ACTIVE;
		} else {
			ALOGE("%s: Unrecognized attr", __func__);
			return -1;
		}
		if (gpt_disk_update_crc(disk_raii.get())) {
			ALOGE("%s: Failed to update crc for %s",
					__func__,
					partName);
			return -1;
		}
		if (gpt_disk_commit(disk_raii.get())) {
			ALOGE("%s: Failed to write back entry for %s",
					__func__,
					partName);
			return -1;
		}
	}
	// Successful
	return 0;
}

unsigned get_number_slots(struct boot_control_module *module)
{
	struct dirent *de = NULL;
	DIR *dir_bootdev = NULL;
	unsigned slot_count = 0;
	if (!module) {
		ALOGE("%s: Invalid argument", __func__);
		return 0;
	}
	dir_bootdev = opendir(BOOTDEV_DIR);
	if (!dir_bootdev) {
		ALOGE("%s: Failed to open bootdev dir (%s)",
				__func__,
				strerror(errno));
		return 0;
	}
	while ((de = readdir(dir_bootdev))) {
		if (de->d_name[0] == '.')
			continue;
		static_assert(AB_SLOT_A_SUFFIX[0] == '_', "Breaking change to slot A suffix");
		static_assert(AB_SLOT_B_SUFFIX[0] == '_', "Breaking change to slot B suffix");
		if (!strncmp(de->d_name, BOOT_IMG_PTN_NAME,
					strlen(BOOT_IMG_PTN_NAME)))
			slot_count++;
	}
	closedir(dir_bootdev);
	return slot_count;
}

unsigned get_current_slot(struct boot_control_module *module)
{
	//The HAL spec requires that we return a number between
	//0 to num_slots - 1. If something went wrong just return
	//return the default slot (0).

	uint32_t num_slots = 0;
	char bootSlotProp[PROPERTY_VALUE_MAX] = {'\0'};
	unsigned i = 0;
	if (!module) {
		ALOGE("%s: Invalid argument", __func__);
		return 0;
	}
	num_slots = get_number_slots(module);
	if (num_slots <= 1) {
		//Slot 0 is the only slot around.
		return 0;
	}
	property_get(BOOT_SLOT_PROP, bootSlotProp, "N/A");
	if (!strncmp(bootSlotProp, "N/A", strlen("N/A"))) {
		ALOGE("%s: Unable to read boot slot property",
				__func__);
		return 0;
	}
	//Iterate through a list of partitons named as boot+suffix
	//and see which one is currently active.
	for (i = 0; slot_suffix_arr[i] != NULL ; i++) {
		if (!strncmp(bootSlotProp,
					slot_suffix_arr[i],
					strlen(slot_suffix_arr[i])))
				return i;
	}
	return 0;
}

static int boot_control_check_slot_sanity(struct boot_control_module *module,
		unsigned slot)
{
	if (!module)
		return -1;
	uint32_t num_slots = get_number_slots(module);
	if ((num_slots < 1) || (slot > num_slots - 1)) {
		ALOGE("Invalid slot number");
		return -1;
	}
	return 0;

}

int mark_boot_successful(struct boot_control_module *module)
{
	unsigned cur_slot = 0;
	int retval = 0;
	if (!module) {
		ALOGE("%s: Invalid argument", __func__);
		retval = -1;
	}
	if (retval == 0) {
		cur_slot = get_current_slot(module);
		if (update_slot_attribute(slot_suffix_arr[cur_slot],
					   ATTR_BOOT_SUCCESSFUL)) {
		    retval = -1;
		}
	}
	if (retval == -1)
	    ALOGE("%s: Failed to mark boot successful", __func__);
	return retval;
}

const char *get_suffix(struct boot_control_module *module, unsigned slot)
{
	if (boot_control_check_slot_sanity(module, slot) != 0)
		return NULL;
	else
		return slot_suffix_arr[slot];
}


//Return a gpt disk structure representing the disk that holds
//partition.
static struct gpt_disk* boot_ctl_get_disk_info(char *partition)
{
	struct gpt_disk *disk = NULL;
	if (!partition)
		return NULL;
	disk = gpt_disk_alloc();
	if (!disk) {
		ALOGE("%s: Failed to alloc disk",
				__func__);
		return NULL;
	}
	if (gpt_disk_get_disk_info(partition, disk)) {
		ALOGE("failed to get disk info for %s",
				partition);
		gpt_disk_free(disk);
		return NULL;
	}
	return disk;
}

//The argument here is a vector of partition names(including the slot suffix)
//that lie on a single disk
static int boot_ctl_set_active_slot_for_partitions(vector<string> part_list,
		unsigned slot)
{
	char buf[PATH_MAX] = {0};
	std::unique_ptr<struct gpt_disk, decltype(&gpt_disk_free)> disk_raii(nullptr, &gpt_disk_free);
	char slotA[MAX_GPT_NAME_SIZE + 1] = {0};
	char slotB[MAX_GPT_NAME_SIZE + 1] = {0};
	char active_guid[TYPE_GUID_SIZE + 1] = {0};
	char inactive_guid[TYPE_GUID_SIZE + 1] = {0};
	//Pointer to the partition entry of current 'A' partition
	uint8_t *pentryA = NULL;
	uint8_t *pentryA_bak = NULL;
	//Pointer to partition entry of current 'B' partition
	uint8_t *pentryB = NULL;
	uint8_t *pentryB_bak = NULL;
	vector<string>::iterator partition_iterator;

	for (partition_iterator = part_list.begin();
			partition_iterator != part_list.end();
			partition_iterator++) {
		//Chop off the slot suffix from the partition name to
		//make the string easier to work with.
		string prefix = *partition_iterator;
		if (prefix.size() < (strlen(AB_SLOT_A_SUFFIX) + 1)) {
			ALOGE("Invalid partition name: %s", prefix.c_str());
			return -1;
		}
		prefix.resize(prefix.size() - strlen(AB_SLOT_A_SUFFIX));
		//Check if A/B versions of this ptn exist
		snprintf(buf, sizeof(buf) - 1, "%s/%s%s", BOOT_DEV_DIR,
				prefix.c_str(),
				AB_SLOT_A_SUFFIX);
		enum part_stat_result_type stat_result = stat_block_device(buf);
		if (stat_result == PARTITION_MISSING) {
			//partition does not have _a version
			continue;
		} else if (stat_result == PARTITION_STAT_ERROR) {
			return -1;
		}
		memset(buf, '\0', sizeof(buf));
		snprintf(buf, sizeof(buf) - 1, "%s/%s%s", BOOT_DEV_DIR,
				prefix.c_str(),
				AB_SLOT_B_SUFFIX);
		stat_result = stat_block_device(buf);
		if (stat_result == PARTITION_MISSING) {
			//partition does not have _b version
			continue;
		} else if (stat_result == PARTITION_STAT_ERROR) {
			return -1;
		}
		memset(slotA, 0, sizeof(slotA));
		memset(slotB, 0, sizeof(slotA));
		snprintf(slotA, sizeof(slotA) - 1, "%s%s", prefix.c_str(),
				AB_SLOT_A_SUFFIX);
		snprintf(slotB, sizeof(slotB) - 1,"%s%s", prefix.c_str(),
				AB_SLOT_B_SUFFIX);
		//Get the disk containing the partitions that were passed in.
		//All partitions passed in must lie on the same disk.
		if (!disk_raii.get()) {
			disk_raii = std::unique_ptr<struct gpt_disk, decltype(&gpt_disk_free)>(
				boot_ctl_get_disk_info(slotA), &gpt_disk_free);
			if (!disk_raii.get()) {
				return -1;
			}
		}
		//Get partition entry for slot A & B from the primary
		//and backup tables.
		pentryA = gpt_disk_get_pentry(disk_raii.get(), slotA, PRIMARY_GPT);
		pentryA_bak = gpt_disk_get_pentry(disk_raii.get(), slotA, SECONDARY_GPT);
		pentryB = gpt_disk_get_pentry(disk_raii.get(), slotB, PRIMARY_GPT);
		pentryB_bak = gpt_disk_get_pentry(disk_raii.get(), slotB, SECONDARY_GPT);
		if ( !pentryA || !pentryA_bak || !pentryB || !pentryB_bak) {
			//None of these should be NULL since we have already
			//checked for A & B versions earlier.
			ALOGE("Slot pentries for %s not found.",
					prefix.c_str());
			return -1;
		}
		memset(active_guid, '\0', sizeof(active_guid));
		memset(inactive_guid, '\0', sizeof(inactive_guid));
		if (get_partition_attribute(slotA, ATTR_SLOT_ACTIVE) == 1) {
			//A is the current active slot
			memcpy((void*)active_guid, (const void*)pentryA,
					TYPE_GUID_SIZE);
			memcpy((void*)inactive_guid,(const void*)pentryB,
					TYPE_GUID_SIZE);
		} else if (get_partition_attribute(slotB,
					ATTR_SLOT_ACTIVE) == 1) {
			//B is the current active slot
			memcpy((void*)active_guid, (const void*)pentryB,
					TYPE_GUID_SIZE);
			memcpy((void*)inactive_guid, (const void*)pentryA,
					TYPE_GUID_SIZE);
		} else {
			ALOGE("Both A & B are inactive..Aborting");
			return -1;
		}
		if (!strncmp(slot_suffix_arr[slot], AB_SLOT_A_SUFFIX,
					strlen(AB_SLOT_A_SUFFIX))){
			//Mark A as active in primary table
			UPDATE_SLOT(pentryA, active_guid, SLOT_ACTIVE);
			//Mark A as active in backup table
			UPDATE_SLOT(pentryA_bak, active_guid, SLOT_ACTIVE);
			//Mark B as inactive in primary table
			UPDATE_SLOT(pentryB, inactive_guid, SLOT_INACTIVE);
			//Mark B as inactive in backup table
			UPDATE_SLOT(pentryB_bak, inactive_guid, SLOT_INACTIVE);
		} else if (!strncmp(slot_suffix_arr[slot], AB_SLOT_B_SUFFIX,
					strlen(AB_SLOT_B_SUFFIX))){
			//Mark B as active in primary table
			UPDATE_SLOT(pentryB, active_guid, SLOT_ACTIVE);
			//Mark B as active in backup table
			UPDATE_SLOT(pentryB_bak, active_guid, SLOT_ACTIVE);
			//Mark A as inavtive in primary table
			UPDATE_SLOT(pentryA, inactive_guid, SLOT_INACTIVE);
			//Mark A as inactive in backup table
			UPDATE_SLOT(pentryA_bak, inactive_guid, SLOT_INACTIVE);
		} else {
			//Something has gone terribly terribly wrong
			ALOGE("%s: Unknown slot suffix!", __func__);
			return -1;
		}
		if (disk_raii.get()) {
			if (gpt_disk_update_crc(disk_raii.get()) != 0) {
				ALOGE("%s: Failed to update gpt_disk crc",
						__func__);
				return -1;
			}
		}
	}
	//write updated content to disk
	if (disk_raii.get()) {
		if (gpt_disk_commit(disk_raii.get())) {
			ALOGE("Failed to commit disk entry");
			return -1;
		}
	}

	// Successful
	return 0;
}

unsigned get_active_boot_slot(struct boot_control_module *module)
{
	if (!module) {
		ALOGE("%s: Invalid argument", __func__);
		// The HAL spec requires that we return a number between
		// 0 to num_slots - 1. Since something went wrong here we
		// are just going to return the default slot.
		return 0;
	}

	uint32_t num_slots = get_number_slots(module);
	if (num_slots <= 1) {
		//Slot 0 is the only slot around.
		return 0;
	}

	for (uint32_t i = 0; i < num_slots; i++) {
		char bootPartition[MAX_GPT_NAME_SIZE + 1] = {0};
		snprintf(bootPartition, sizeof(bootPartition) - 1, "boot%s",
		         slot_suffix_arr[i]);
		if (get_partition_attribute(bootPartition, ATTR_SLOT_ACTIVE) == 1) {
			return i;
		}
	}

	ALOGE("%s: Failed to find the active boot slot", __func__);
	return 0;
}

int set_active_boot_slot(struct boot_control_module *module, unsigned slot)
{
	map<string, vector<string>> ptn_map;
	vector<string> ptn_vec;
	const char ptn_list[][MAX_GPT_NAME_SIZE] = { AB_PTN_LIST };
	uint32_t i;
	int rc = -1;
	int is_ufs = gpt_utils_is_ufs_device();
	map<string, vector<string>>::iterator map_iter;

	if (boot_control_check_slot_sanity(module, slot)) {
		ALOGE("%s: Bad arguments", __func__);
		return -1;
	}
	//The partition list just contains prefixes(without the _a/_b) of the
	//partitions that support A/B. In order to get the layout we need the
	//actual names. To do this we append the slot suffix to every member
	//in the list.
	for (i = 0; i < ARRAY_SIZE(ptn_list); i++) {
		//XBL is handled differrently for ufs devices so ignore it
		if (is_ufs && !strncmp(ptn_list[i], PTN_XBL, strlen(PTN_XBL)))
				continue;
		//The partition list will be the list of _a partitions
		string cur_ptn = ptn_list[i];
		cur_ptn.append(AB_SLOT_A_SUFFIX);
		ptn_vec.push_back(cur_ptn);

	}
	//The partition map gives us info in the following format:
	// [path_to_block_device_1]--><partitions on device 1>
	// [path_to_block_device_2]--><partitions on device 2>
	// ...
	// ...
	// eg:
	// [/dev/block/sdb]---><system, boot, rpm, tz,....>
	if (gpt_utils_get_partition_map(ptn_vec, ptn_map)) {
		ALOGE("%s: Failed to get partition map",
				__func__);
		return -1;
	}
	for (map_iter = ptn_map.begin(); map_iter != ptn_map.end(); map_iter++){
		if (map_iter->second.size() < 1)
			continue;
		if (boot_ctl_set_active_slot_for_partitions(map_iter->second, slot)) {
			ALOGE("%s: Failed to set active slot for partitions ", __func__);;
			return -1;
		}
	}
	if (is_ufs) {
		if (!strncmp(slot_suffix_arr[slot], AB_SLOT_A_SUFFIX,
					strlen(AB_SLOT_A_SUFFIX))){
			//Set xbl_a as the boot lun
			rc = gpt_utils_set_xbl_boot_partition(NORMAL_BOOT);
		} else if (!strncmp(slot_suffix_arr[slot], AB_SLOT_B_SUFFIX,
					strlen(AB_SLOT_B_SUFFIX))){
			//Set xbl_b as the boot lun
			rc = gpt_utils_set_xbl_boot_partition(BACKUP_BOOT);
		} else {
			//Something has gone terribly terribly wrong
			ALOGE("%s: Unknown slot suffix!", __func__);
			return -1;
		}
		if (rc) {
			ALOGE("%s: Failed to switch xbl boot partition",
					__func__);
			return -1;
		}
	}
	return 0;
}

int set_slot_as_unbootable(struct boot_control_module *module, unsigned slot)
{
	int retval = 0;
	if (boot_control_check_slot_sanity(module, slot) != 0) {
		ALOGE("%s: Argument check failed", __func__);
		retval = -1;
	}
	if (retval == 0 && update_slot_attribute(slot_suffix_arr[slot],
				ATTR_UNBOOTABLE)) {
		retval = -1;
	}
	if (retval != 0)
		ALOGE("%s: Failed to mark slot unbootable", __func__);
	return retval;
}

int is_slot_bootable(struct boot_control_module *module, unsigned slot)
{
	int attr = 0;
	char bootPartition[MAX_GPT_NAME_SIZE + 1] = {0};

	if (boot_control_check_slot_sanity(module, slot) != 0) {
		ALOGE("%s: Argument check failed", __func__);
		return -1;
	}
	snprintf(bootPartition,
			sizeof(bootPartition) - 1, "boot%s",
			slot_suffix_arr[slot]);
	attr = get_partition_attribute(bootPartition, ATTR_UNBOOTABLE);
	if (attr >= 0)
		return !attr;
	return -1;
}

int is_slot_marked_successful(struct boot_control_module *module, unsigned slot)
{
	int attr = 0;
	char bootPartition[MAX_GPT_NAME_SIZE + 1] = {0};

	if (boot_control_check_slot_sanity(module, slot) != 0) {
		ALOGE("%s: Argument check failed", __func__);
		return -1;
	}
	snprintf(bootPartition,
			sizeof(bootPartition) - 1,
			"boot%s", slot_suffix_arr[slot]);
	attr = get_partition_attribute(bootPartition, ATTR_BOOT_SUCCESSFUL);
	if (attr >= 0)
		return attr;
	return -1;
}

static hw_module_methods_t boot_control_module_methods = {
	.open = NULL,
};

boot_control_module_t HAL_MODULE_INFO_SYM = {
	.common = {
		.tag = HARDWARE_MODULE_TAG,
		.module_api_version = 1,
		.hal_api_version = 0,
		.id = BOOT_CONTROL_HARDWARE_MODULE_ID,
		.name = "Boot control HAL",
		.author = "Code Aurora Forum",
		.methods = &boot_control_module_methods,
	},
	.init = boot_control_init,
	.getNumberSlots = get_number_slots,
	.getCurrentSlot = get_current_slot,
	.markBootSuccessful = mark_boot_successful,
	.getActiveBootSlot = get_active_boot_slot,
	.setActiveBootSlot = set_active_boot_slot,
	.setSlotAsUnbootable = set_slot_as_unbootable,
	.isSlotBootable = is_slot_bootable,
	.getSuffix = get_suffix,
	.isSlotMarkedSuccessful = is_slot_marked_successful,
};
#ifdef __cplusplus
}
#endif
