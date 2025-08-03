/*
 * Copyright (C) 2014 Satoshi Noguchi
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
#include <stdlib.h>
#include <signal.h>

#include "hiddevice.h"
#include "f54test.h"
#include "display.h"

#define F54TEST_GETOPTS	"hd:r:cnt:"

static bool stopRequested;

void printHelp(const char *prog_name)
{
	fprintf(stdout, "Usage: %s [OPTIONS]\n", prog_name);
	fprintf(stdout, "\t-h, --help\tPrint this message\n");
	fprintf(stdout, "\t-d, --device\thidraw device file associated with the device being tested.\n");
	fprintf(stdout, "\t-r, --report_type\tReport type.\n");
	fprintf(stdout, "\t-c, --continuous\tContinuous mode.\n");
	fprintf(stdout, "\t-n, --no_reset\tDo not reset after the report.\n");
	fprintf(stdout, "\t-t, --device-type\t\t\tFilter by device type [touchpad or touchscreen].\n");
}

int RunF54Test(RMIDevice & rmidevice, f54_report_types reportType, bool continuousMode, bool noReset)
{
	int rc;
	Display * display;

	if (continuousMode)
	{
		display = new AnsiConsole();
	}
	else
	{
		display = new Display();
	}

	display->Clear();

	F54Test f54Test(rmidevice, *display);

	rc = f54Test.Prepare(reportType);
	if (rc)
		return rc;

	stopRequested = false;

	do {
		rc = f54Test.Run();
	}
	while (continuousMode && !stopRequested);

	if (!noReset)
		rmidevice.Reset();

	delete display;

	return rc;
}

void SignalHandler(int p_signame)
{
	stopRequested = true;
}

int main(int argc, char **argv)
{
	int rc = 0;
	int opt;
	int index;
	char *deviceName = NULL;
	static struct option long_options[] = {
		{"help", 0, NULL, 'h'},
		{"device", 1, NULL, 'd'},
		{"report_type", 1, NULL, 'r'},
		{"continuous", 0, NULL, 'c'},
		{"no_reset", 0, NULL, 'n'},
		{"device-type", 1, NULL, 't'},
		{0, 0, 0, 0},
	};
	f54_report_types reportType = F54_16BIT_IMAGE;
	bool continuousMode = false;
	bool noReset = false;
	HIDDevice device;
	enum RMIDeviceType deviceType = RMI_DEVICE_TYPE_ANY;

	while ((opt = getopt_long(argc, argv, F54TEST_GETOPTS, long_options, &index)) != -1) {
		switch (opt) {
			case 'h':
				printHelp(argv[0]);
				return 0;
			case 'd':
				deviceName = optarg;
				break;
			case 'r':
				reportType = (f54_report_types)strtol(optarg, NULL, 0);
				break;
			case 'c':
				continuousMode = true;
				break;
			case 'n':
				noReset = true;
				break;
			case 't':
				if (!strcasecmp(optarg, "touchpad"))
					deviceType = RMI_DEVICE_TYPE_TOUCHPAD;
				else if (!strcasecmp(optarg, "touchscreen"))
					deviceType = RMI_DEVICE_TYPE_TOUCHSCREEN;
				break;
			default:
				break;

		}
	}

	if (continuousMode)
	{
		signal(SIGHUP, SignalHandler);
		signal(SIGINT, SignalHandler);
		signal(SIGTERM, SignalHandler);
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

	return RunF54Test(device, reportType, continuousMode, noReset);
}
