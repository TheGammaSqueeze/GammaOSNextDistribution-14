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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <time.h>
#include <string>
#include <sstream>
#include <time.h>

#include "hiddevice.h"
#include "rmi4update.h"

#define VERSION_MAJOR		1
#define VERSION_MINOR		3
#define VERSION_SUBMINOR	12

#define RMI4UPDATE_GETOPTS	"hfd:t:pclvm"

bool needDebugMessage; 

void printHelp(const char *prog_name)
{
	fprintf(stdout, "Usage: %s [OPTIONS] FIRMWAREFILE\n", prog_name);
	fprintf(stdout, "\t-h, --help\t\tPrint this message\n");
	fprintf(stdout, "\t-f, --force\t\tForce updating firmware even it the image provided is older\n\t\t\t\tthen the current firmware on the device.\n");
	fprintf(stdout, "\t-d, --device\t\thidraw device file associated with the device being updated.\n");
	fprintf(stdout, "\t-p, --fw-props\t\tPrint the firmware properties.\n");
	fprintf(stdout, "\t-c, --config-id\t\tPrint the config id.\n");
	fprintf(stdout, "\t-l, --lockdown\t\tPerform lockdown.\n");
	fprintf(stdout, "\t-v, --version\t\tPrint version number.\n");
	fprintf(stdout, "\t-t, --device-type\tFilter by device type [touchpad or touchscreen].\n");
}

void printVersion()
{
	fprintf(stdout, "rmi4update version %d.%d.%d\n",
		VERSION_MAJOR, VERSION_MINOR, VERSION_SUBMINOR);
}

int GetFirmwareProps(const char * deviceFile, std::string &props, bool configid)
{
	HIDDevice rmidevice;
	int rc = UPDATE_SUCCESS;
	std::stringstream ss;

	rc = rmidevice.Open(deviceFile);
	if (rc)
		return rc;
	
	if (needDebugMessage)
		rmidevice.m_hasDebug = true;

	// Clear all interrupts before parsing to avoid unexpected interrupts.
	rmidevice.ToggleInterruptMask(false);

	rmidevice.ScanPDT(0x1);
	rmidevice.QueryBasicProperties();

	// Restore the interrupts
	rmidevice.ToggleInterruptMask(true);

	if (configid) {
		ss << std::hex << rmidevice.GetConfigID();
	} else {
		ss << rmidevice.GetFirmwareVersionMajor() << "."
			<< rmidevice.GetFirmwareVersionMinor() << "."
			<< rmidevice.GetFirmwareID();

		if (rmidevice.InBootloader())
			ss << " bootloader";
	}

	props = ss.str();

	return rc;
}

int main(int argc, char **argv)
{
	int rc;
	FirmwareImage image;
	int opt;
	int index;
	char *deviceName = NULL;
	const char *firmwareName = NULL;
	bool force = false;
	static struct option long_options[] = {
		{"help", 0, NULL, 'h'},
		{"force", 0, NULL, 'f'},
		{"device", 1, NULL, 'd'},
		{"fw-props", 0, NULL, 'p'},
		{"config-id", 0, NULL, 'c'},
		{"lockdown", 0, NULL, 'l'},
		{"version", 0, NULL, 'v'},
		{"device-type", 1, NULL, 't'},
		{0, 0, 0, 0},
	};
	bool printFirmwareProps = false;
	bool printConfigid = false;
	bool performLockdown = false;
	needDebugMessage = false;
	HIDDevice device;
	enum RMIDeviceType deviceType = RMI_DEVICE_TYPE_ANY;

	while ((opt = getopt_long(argc, argv, RMI4UPDATE_GETOPTS, long_options, &index)) != -1) {
		switch (opt) {
			case 'h':
				printHelp(argv[0]);
				return 0;
			case 'f':
				force = true;
				break;
			case 'd':
				deviceName = optarg;
				break;
			case 'p':
				printFirmwareProps = true;
				break;
			case 'c':
				printFirmwareProps = true;
				printConfigid = true;
				break;
			case 'l':
				performLockdown = true;
				break;
			case 't':
				if (!strcasecmp((const char *)optarg, "touchpad"))
					deviceType = RMI_DEVICE_TYPE_TOUCHPAD;
				else if (!strcasecmp((const char *)optarg, "touchscreen"))
					deviceType = RMI_DEVICE_TYPE_TOUCHSCREEN;
				break;
			case 'v':
				printVersion();
				return 0;
			case 'm':
				needDebugMessage = true;
				break;
			default:
				break;

		}
	}

	if (printFirmwareProps) {
		std::string props;
		
		if (!deviceName) {
			fprintf(stderr, "Specifiy which device to query\n");
			return 1;
		}
		rc = GetFirmwareProps(deviceName, props, printConfigid);
		if (rc) {
			fprintf(stderr, "Failed to read properties from device: %s\n", update_err_to_string(rc));
			return 1;
		}
		fprintf(stdout, "%s\n", props.c_str());
		return 0;
	}

	if (optind < argc) {
		firmwareName = argv[optind];
	} else {
		printHelp(argv[0]);
		return -1;
	}

	rc = image.Initialize(firmwareName);
	if (rc != UPDATE_SUCCESS) {
		fprintf(stderr, "Failed to initialize the firmware image: %s\n", update_err_to_string(rc));
		return 1;
	}

	if (deviceName) {
		 rc = device.Open(deviceName);
		 if (rc) {
			fprintf(stderr, "%s: failed to initialize rmi device (%d): %s\n", argv[0], errno,
				strerror(errno));
			return 1;
		}
	} else {
		if (!device.FindDevice(deviceType))
			return 1;
	}

	if (needDebugMessage) {
		device.m_hasDebug = true;
	}

	RMI4Update update(device, image);
	rc = update.UpdateFirmware(force, performLockdown);

	if (rc != UPDATE_SUCCESS)
	{
		device.Reset();
		return 1;
	}

	return 0;
}
