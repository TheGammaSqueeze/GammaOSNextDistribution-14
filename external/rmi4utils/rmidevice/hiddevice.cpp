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
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/select.h>

#include <linux/types.h>
#include <linux/input.h>
#include <linux/hidraw.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/inotify.h>

#include "hiddevice.h"

#define RMI_WRITE_REPORT_ID                 0x9 // Output Report
#define RMI_READ_ADDR_REPORT_ID             0xa // Output Report
#define RMI_READ_DATA_REPORT_ID             0xb // Input Report
#define RMI_ATTN_REPORT_ID                  0xc // Input Report
#define RMI_SET_RMI_MODE_REPORT_ID          0xf // Feature Report
#define RMI_SET_LID_MODE_REPORT_ID          0xe // Feature Report


// Make sure that none of the enums/macros conflict with possible
// kernel definition/names.
enum hid_rmi4_report_type {
  HID_RMI4_REPORT_UNKNOWN = 0,
  HID_RMI4_REPORT_INPUT = 1,
  HID_RMI4_REPORT_OUTPUT = 2,
  HID_RMI4_REPORT_FEATURE = 3,
};

#define HID_RMI4_REPORT_TYPE_INPUT			0x81
#define HID_RMI4_REPORT_TYPE_OUTPUT			0x91
#define HID_RMI4_REPORT_TYPE_FEATURE			0xb1

#define HID_RMI4_REPORT_ID			0
#define HID_RMI4_READ_INPUT_COUNT		1
#define HID_RMI4_READ_INPUT_DATA		2
#define HID_RMI4_READ_OUTPUT_ADDR		2
#define HID_RMI4_READ_OUTPUT_COUNT		4
#define HID_RMI4_WRITE_OUTPUT_COUNT		1
#define HID_RMI4_WRITE_OUTPUT_ADDR		2
#define HID_RMI4_WRITE_OUTPUT_DATA		4
#define HID_RMI4_FEATURE_MODE			1
#define HID_RMI4_ATTN_INTERUPT_SOURCES		1
#define HID_RMI4_ATTN_DATA			2

#define SYNAPTICS_VENDOR_ID			0x06cb

int HIDDevice::Open(const char * filename)
{
	int rc;
	int desc_size;
	std::string hidDeviceName;
	std::string hidDriverName;

	if (!filename)
		return -EINVAL;

	m_fd = open(filename, O_RDWR);
	if (m_fd < 0)
		return -1;

	memset(&m_rptDesc, 0, sizeof(m_rptDesc));
	memset(&m_info, 0, sizeof(m_info));

	rc = ioctl(m_fd, HIDIOCGRDESCSIZE, &desc_size);
	if (rc < 0)
		goto error;
	
	m_rptDesc.size = desc_size;
	rc = ioctl(m_fd, HIDIOCGRDESC, &m_rptDesc);
	if (rc < 0)
		goto error;
	
	rc = ioctl(m_fd, HIDIOCGRAWINFO, &m_info);
	if (rc < 0)
		goto error;

	if (m_info.vendor != SYNAPTICS_VENDOR_ID) {
		errno = -ENODEV;
		rc = -1;
		goto error;
	}

	ParseReportDescriptor();

	m_inputReport = new unsigned char[m_inputReportSize]();
	if (!m_inputReport) {
		errno = -ENOMEM;
		rc = -1;
		goto error;
	}

	m_outputReport = new unsigned char[m_outputReportSize]();
	if (!m_outputReport) {
		errno = -ENOMEM;
		rc = -1;
		goto error;
	}

	m_readData = new unsigned char[m_inputReportSize]();
	if (!m_readData) {
		errno = -ENOMEM;
		rc = -1;
		goto error;
	}

	m_attnData = new unsigned char[m_inputReportSize]();
	if (!m_attnData) {
		errno = -ENOMEM;
		rc = -1;
		goto error;
	}

	m_deviceOpen = true;

	// Determine which mode the device is currently running in based on the current HID driver
	// hid-rmi indicated RMI Mode 1 all others would be Mode 0
	if (LookupHidDeviceName(m_info.bustype, m_info.vendor, m_info.product, hidDeviceName)) {
		if (LookupHidDriverName(hidDeviceName, hidDriverName)) {
			if (hidDriverName == "hid-rmi")
				m_initialMode = HID_RMI4_MODE_ATTN_REPORTS;
		}
	}

	if (m_initialMode != m_mode) {
		rc = SetMode(m_mode);
		if (rc) {
			rc = -1;
			goto error;
		}
	}

	return 0;

error:
	Close();
	return rc;
}

void HIDDevice::ParseReportDescriptor()
{
	bool isVendorSpecific = false;
	bool isReport = false;
	int totalReportSize = 0;
	int reportSize = 0;
	int reportCount = 0;
	enum hid_rmi4_report_type hidReportType = HID_RMI4_REPORT_UNKNOWN;
	bool inCollection = false;

	for (unsigned int i = 0; i < m_rptDesc.size; ++i) {
		if (m_rptDesc.value[i] == 0xc0) {
			inCollection = false;
			isVendorSpecific = false;
			isReport = false;
			continue;
		}

		if (isVendorSpecific) {
			if (m_rptDesc.value[i] == 0x85) {
				if (isReport) {
					// finish up data on the previous report
					totalReportSize = (reportSize * reportCount) >> 3;

					switch (hidReportType) {
						case HID_RMI4_REPORT_INPUT:
							m_inputReportSize = totalReportSize + 1;
							break;
						case HID_RMI4_REPORT_OUTPUT:
							m_outputReportSize = totalReportSize + 1;
							break;
						case HID_RMI4_REPORT_FEATURE:
							m_featureReportSize = totalReportSize + 1;
							break;
						case HID_RMI4_REPORT_UNKNOWN:
						default:
							break;
					}
				}

				// reset values for the new report
				totalReportSize = 0;
				reportSize = 0;
				reportCount = 0;
				hidReportType = HID_RMI4_REPORT_UNKNOWN;

				isReport = true;
			}

			if (isReport) {
				if (m_rptDesc.value[i] == 0x75) {
					if (i + 1 >= m_rptDesc.size)
						return;
					reportSize = m_rptDesc.value[++i];
					continue;
				}

				if (m_rptDesc.value[i] == 0x95) {
					if (i + 1 >= m_rptDesc.size)
						return;
					reportCount = m_rptDesc.value[++i];
					continue;
				}

				if (m_rptDesc.value[i] == RMI_SET_LID_MODE_REPORT_ID) {
					hasVendorDefineLIDMode = true;
				}

				if (m_rptDesc.value[i] == HID_RMI4_REPORT_TYPE_INPUT)
					hidReportType = HID_RMI4_REPORT_INPUT;

				if (m_rptDesc.value[i] == HID_RMI4_REPORT_TYPE_OUTPUT)
					hidReportType = HID_RMI4_REPORT_OUTPUT;

				if (m_rptDesc.value[i] == HID_RMI4_REPORT_TYPE_FEATURE) {
					hidReportType = HID_RMI4_REPORT_FEATURE;
				}
			}
		}

		if (!inCollection) {
			switch (m_rptDesc.value[i]) {
				case 0x00:
				case 0x01:
				case 0x02:
				case 0x03:
				case 0x04:
					inCollection = true;
					break;
				case 0x05:
					inCollection = true;

					if (i + 3 >= m_rptDesc.size)
						break;

					// touchscreens with active pen have a Generic Mouse collection
					// so stop searching if we have already found the touchscreen digitizer
					// usage.
					if (m_deviceType == RMI_DEVICE_TYPE_TOUCHSCREEN)
						break;
				
					if (m_rptDesc.value[i + 1] == 0x01) {
						if (m_rptDesc.value[i + 2] == 0x09 && m_rptDesc.value[i + 3] == 0x02)
							m_deviceType = RMI_DEVICE_TYPE_TOUCHPAD;
					} else if (m_rptDesc.value[i + 1] == 0x0d) {
						if (m_rptDesc.value[i + 2] == 0x09 && m_rptDesc.value[i + 3] == 0x04)
							m_deviceType = RMI_DEVICE_TYPE_TOUCHSCREEN;
						// for Precision Touch Pad
						else if (m_rptDesc.value[i + 2] == 0x09 && m_rptDesc.value[i + 3] == 0x05)
							m_deviceType = RMI_DEVICE_TYPE_TOUCHPAD;
					}
					i += 3;
					break;
				case 0x06:
					inCollection = true;
					if (i + 2 >= m_rptDesc.size)
						break;

					if (m_rptDesc.value[i + 1] == 0x00 && m_rptDesc.value[i + 2] == 0xFF)
						isVendorSpecific = true;
					i += 2;
					break;
				default:
					break;

			}
		}
	}
}

int HIDDevice::Read(unsigned short addr, unsigned char *buf, unsigned short len)
{
	ssize_t count;
	size_t bytesReadPerRequest;
	size_t bytesInDataReport;
	size_t totalBytesRead;
	size_t bytesPerRequest;
	size_t bytesWritten;
	size_t bytesToRequest;
	int reportId;
	int rc;
	struct timeval tv;
	int resendCount = 0;

	tv.tv_sec = 10 / 1000;
	tv.tv_usec = (10 % 1000) * 1000;
	
	if (!m_deviceOpen)
		return -1;

	if (m_hasDebug) {
		fprintf(stdout, "R %02x : ", addr);
	}

	if (m_bytesPerReadRequest)
		bytesPerRequest = m_bytesPerReadRequest;
	else
		bytesPerRequest = len;

	for (totalBytesRead = 0; totalBytesRead < len; totalBytesRead += bytesReadPerRequest) {
Resend:
		if (GetDeviceType() == RMI_DEVICE_TYPE_TOUCHPAD) {
			if (resendCount == 3) {
				fprintf(stderr, "resend count exceed, return as failure\n");
				return -1;
			}
		}
		count = 0;
		if ((len - totalBytesRead) < bytesPerRequest)
			bytesToRequest = len % bytesPerRequest;
		else
			bytesToRequest = bytesPerRequest;

		if (m_outputReportSize < HID_RMI4_READ_OUTPUT_COUNT + 2) {
			return -1;
		}
		m_outputReport[HID_RMI4_REPORT_ID] = RMI_READ_ADDR_REPORT_ID;
		m_outputReport[1] = 0; /* old 1 byte read count */
		m_outputReport[HID_RMI4_READ_OUTPUT_ADDR] = addr & 0xFF;
		m_outputReport[HID_RMI4_READ_OUTPUT_ADDR + 1] = (addr >> 8) & 0xFF;
		m_outputReport[HID_RMI4_READ_OUTPUT_COUNT] = bytesToRequest  & 0xFF;
		m_outputReport[HID_RMI4_READ_OUTPUT_COUNT + 1] = (bytesToRequest >> 8) & 0xFF;

		m_dataBytesRead = 0;

		for (bytesWritten = 0; bytesWritten < m_outputReportSize; bytesWritten += count) {
			m_bCancel = false;
			count = write(m_fd, m_outputReport + bytesWritten,
					m_outputReportSize - bytesWritten);
			if (count < 0) {
				if (errno == EINTR && m_deviceOpen && !m_bCancel)
					continue;
				else
					return count;
			}
			break;
		}

		bytesReadPerRequest = 0;
		while (bytesReadPerRequest < bytesToRequest) {
			if (GetDeviceType() == RMI_DEVICE_TYPE_TOUCHPAD) {
				// Add timeout 10 ms for select() called in GetReport().
				rc = GetReport(&reportId, &tv);
			} else {
				// Touch Screen
				rc = GetReport(&reportId);
			}
			if (rc > 0 && reportId == RMI_READ_DATA_REPORT_ID) {
				if (static_cast<ssize_t>(m_inputReportSize) <
				    std::max(HID_RMI4_READ_INPUT_COUNT,
					     HID_RMI4_READ_INPUT_DATA)){
					return -1;
				}
				bytesInDataReport = m_readData[HID_RMI4_READ_INPUT_COUNT];
				if (bytesInDataReport > bytesToRequest
				    || bytesReadPerRequest + bytesInDataReport > len){
					return -1;
				}
				memcpy(buf + bytesReadPerRequest, &m_readData[HID_RMI4_READ_INPUT_DATA],
					bytesInDataReport);
				bytesReadPerRequest += bytesInDataReport;
				m_dataBytesRead = 0;
				if (GetDeviceType() == RMI_DEVICE_TYPE_TOUCHPAD) {
					// Resend sheme is supported on TP only.
					resendCount = 0;
				}
			} else if (GetDeviceType() == RMI_DEVICE_TYPE_TOUCHPAD) {
				fprintf(stderr, "Some error with GetReport : rc(%d), reportID(0x%x)\n", rc, reportId);
				resendCount += 1;
				goto Resend;
			}
		}
		addr += bytesPerRequest;
	}
	if (m_hasDebug) {
		for (int i=0 ; i<len ; i++) {
			fprintf(stdout, "%02x ", buf[i]);
		}
		fprintf(stdout, "\n");
	}

	return totalBytesRead;
}

int HIDDevice::Write(unsigned short addr, const unsigned char *buf, unsigned short len)
{
	ssize_t count;

	if (!m_deviceOpen)
		return -1;

	if (static_cast<ssize_t>(m_outputReportSize) <
	    HID_RMI4_WRITE_OUTPUT_DATA + len)
		return -1;
	m_outputReport[HID_RMI4_REPORT_ID] = RMI_WRITE_REPORT_ID;
	m_outputReport[HID_RMI4_WRITE_OUTPUT_COUNT] = len;
	m_outputReport[HID_RMI4_WRITE_OUTPUT_ADDR] = addr & 0xFF;
	m_outputReport[HID_RMI4_WRITE_OUTPUT_ADDR + 1] = (addr >> 8) & 0xFF;
	memcpy(&m_outputReport[HID_RMI4_WRITE_OUTPUT_DATA], buf, len);

	if (m_hasDebug) {
		fprintf(stdout, "W %02x : ", addr);
		for (int i=0 ; i<len ; i++) {
			fprintf(stdout, "%02x ", buf[i]);
		}
		fprintf(stdout, "\n");
	}

	for (;;) {
		m_bCancel = false;
		count = write(m_fd, m_outputReport, m_outputReportSize);
		if (count < 0) {
			if (errno == EINTR && m_deviceOpen && !m_bCancel)
				continue;
			else
				return count;
		}
		return len;
	}
}

int HIDDevice::SetMode(int mode)
{
	int rc;
	char buf[2];
	
	if (!m_deviceOpen)
		return -1;

	buf[0] = RMI_SET_RMI_MODE_REPORT_ID;
	buf[1] = mode;
	rc = ioctl(m_fd, HIDIOCSFEATURE(2), buf);
	if (rc < 0) {
		perror("HIDIOCSFEATURE");
		return rc;
	}

	return 0;
}

int HIDDevice::ToggleInterruptMask(bool enable)
{
	int rc;
	char buf[2];

	if (GetDeviceType() != RMI_DEVICE_TYPE_TOUCHPAD) {
		fprintf(stdout, "Not TP, skip toggle interrupts mask\n");
		return 0;
	}

	// We can have information to see whether it exists this feature report currentlt.
	// However, it might have no action even we set this feature with specific value.
	// Need FW team's help to query more information about the existence of functions.
	if (!hasVendorDefineLIDMode) {
		if (m_hasDebug) {
			fprintf(stdout, "no LID mode feature, return\n");
		}
		return 0;
	}
	
	if (!m_deviceOpen)
		return -1;

	buf[0] = RMI_SET_LID_MODE_REPORT_ID;
	if (enable) {
		buf[1] = 0;
	} else {
		buf[1] = 8;
	}
	rc = ioctl(m_fd, HIDIOCSFEATURE(2), buf);
	if (rc < 0) {
		perror("HIDIOCSFEATURE");
		return rc;
	}
	Sleep(10);
	return 0;
}

void HIDDevice::Close()
{
	RMIDevice::Close();

	if (!m_deviceOpen)
		return;

	if (m_initialMode != m_mode)
		SetMode(m_initialMode);

	m_deviceOpen = false;
	close(m_fd);
	m_fd = -1;

	delete[] m_inputReport;
	m_inputReport = NULL;
	delete[] m_outputReport;
	m_outputReport = NULL;
	delete[] m_readData;
	m_readData = NULL;
	delete[] m_attnData;
	m_attnData = NULL;
}

int HIDDevice::WaitForAttention(struct timeval * timeout, unsigned int source_mask)
{
	return GetAttentionReport(timeout, source_mask, NULL, NULL);
}

int HIDDevice::GetAttentionReport(struct timeval * timeout, unsigned int source_mask,
					unsigned char *buf, unsigned int *len)
{
	int rc = 0;
	int reportId;

	// Assume the Linux implementation of select with timeout set to the
	// time remaining.
	while (!timeout || (timeout->tv_sec != 0 || timeout->tv_usec != 0)) {
		rc = GetReport(&reportId, timeout);
		if (rc > 0) {
			if (reportId == RMI_ATTN_REPORT_ID) {
				// If a valid buffer is passed in then copy the data from
				// the attention report into it. If the buffer is
				// too small simply set *len to 0 to indicate nothing
				// was copied. Some callers won't care about the contents
				// of the report so failing to copy the data should not return
				// an error.
				if (buf && len) {
					if (*len >= m_inputReportSize) {
						*len = m_inputReportSize;
						memcpy(buf, m_attnData, *len);
					} else {
						*len = 0;
					}
				}

				if (m_inputReportSize < HID_RMI4_ATTN_INTERUPT_SOURCES + 1)
					return -1;

				if (source_mask & m_attnData[HID_RMI4_ATTN_INTERUPT_SOURCES])
					return rc;
			}
		} else {
			return rc;
		}
	}

	return rc;
}

int HIDDevice::GetReport(int *reportId, struct timeval * timeout)
{
	ssize_t count = 0;
	fd_set fds;
	int rc;

	if (!m_deviceOpen)
		return -1;

	if (m_inputReportSize < HID_RMI4_REPORT_ID + 1)
		return -1;

	for (;;) {
		FD_ZERO(&fds);
		FD_SET(m_fd, &fds);

		rc = select(m_fd + 1, &fds, NULL, NULL, timeout);
		if (rc == 0) {
			return -ETIMEDOUT;
		} else if (rc < 0) {
			if (errno == EINTR && m_deviceOpen && !m_bCancel)
				continue;
			else
				return rc;
		} else if (rc > 0 && FD_ISSET(m_fd, &fds)) {
			size_t offset = 0;
			for (;;) {
				m_bCancel = false;
				count = read(m_fd, m_inputReport + offset, m_inputReportSize - offset);
				if (count < 0) {
					if (errno == EINTR && m_deviceOpen && !m_bCancel)
						continue;
					else
						return count;
				}
				offset += count;
				if (offset == m_inputReportSize)
					break;
			}
			count = offset;
		}
		break;
	}

	if (reportId)
		*reportId = m_inputReport[HID_RMI4_REPORT_ID];

	if (m_inputReport[HID_RMI4_REPORT_ID] == RMI_ATTN_REPORT_ID) {
		if (static_cast<ssize_t>(m_inputReportSize) < count)
			return -1;
		memcpy(m_attnData, m_inputReport, count);
	} else if (m_inputReport[HID_RMI4_REPORT_ID] == RMI_READ_DATA_REPORT_ID) {
		if (static_cast<ssize_t>(m_inputReportSize) < count)
			return -1;
		memcpy(m_readData, m_inputReport, count);
		m_dataBytesRead = count;
	}
	return 1;
}

void HIDDevice::PrintReport(const unsigned char *report)
{
	int i;
	int len = 0;
	const unsigned char * data;
	int addr = 0;

	switch (report[HID_RMI4_REPORT_ID]) {
		case RMI_WRITE_REPORT_ID:
			len = report[HID_RMI4_WRITE_OUTPUT_COUNT];
			data = &report[HID_RMI4_WRITE_OUTPUT_DATA];
			addr = (report[HID_RMI4_WRITE_OUTPUT_ADDR] & 0xFF)
				| ((report[HID_RMI4_WRITE_OUTPUT_ADDR + 1] & 0xFF) << 8);
			fprintf(stdout, "Write Report:\n");
			fprintf(stdout, "Address = 0x%02X\n", addr);
			fprintf(stdout, "Length = 0x%02X\n", len);
			break;
		case RMI_READ_ADDR_REPORT_ID:
			addr = (report[HID_RMI4_READ_OUTPUT_ADDR] & 0xFF)
				| ((report[HID_RMI4_READ_OUTPUT_ADDR + 1] & 0xFF) << 8);
			len = (report[HID_RMI4_READ_OUTPUT_COUNT] & 0xFF)
				| ((report[HID_RMI4_READ_OUTPUT_COUNT + 1] & 0xFF) << 8);
			fprintf(stdout, "Read Request (Output Report):\n");
			fprintf(stdout, "Address = 0x%02X\n", addr);
			fprintf(stdout, "Length = 0x%02X\n", len);
			return;
			break;
		case RMI_READ_DATA_REPORT_ID:
			len = report[HID_RMI4_READ_INPUT_COUNT];
			data = &report[HID_RMI4_READ_INPUT_DATA];
			fprintf(stdout, "Read Data Report:\n");
			fprintf(stdout, "Length = 0x%02X\n", len);
			break;
		case RMI_ATTN_REPORT_ID:
			fprintf(stdout, "Attention Report:\n");
			len = 28;
			data = &report[HID_RMI4_ATTN_DATA];
			fprintf(stdout, "Interrupt Sources: 0x%02X\n", 
				report[HID_RMI4_ATTN_INTERUPT_SOURCES]);
			break;
		default:
			fprintf(stderr, "Unknown Report: ID 0x%02x\n", report[HID_RMI4_REPORT_ID]);
			return;
	}

	fprintf(stdout, "Data:\n");
	for (i = 0; i < len; ++i) {
		fprintf(stdout, "0x%02X ", data[i]);
		if (i % 8 == 7) {
			fprintf(stdout, "\n");
		}
	}
	fprintf(stdout, "\n\n");
}

// Print protocol specific device information
void HIDDevice::PrintDeviceInfo()
{
	enum RMIDeviceType deviceType = GetDeviceType();

	fprintf(stdout, "HID device info:\nBus: %s Vendor: 0x%04x Product: 0x%04x\n",
		m_info.bustype == BUS_I2C ? "I2C" : "USB", m_info.vendor, m_info.product);
	fprintf(stdout, "Report sizes: input: %ld output: %ld\n", (unsigned long)m_inputReportSize,
		(unsigned long)m_outputReportSize);
	if (deviceType)
		fprintf(stdout, "device type: %s\n", deviceType == RMI_DEVICE_TYPE_TOUCHSCREEN ?
			"touchscreen" : "touchpad");
}

bool WriteDeviceNameToFile(const char * file, const char * str)
{
	int fd;
	ssize_t size;

	fd = open(file, O_WRONLY);
	if (fd < 0)
		return false;

	for (;;) {
		size = write(fd, str, strlen(str));
		if (size < 0) {
			if (errno == EINTR)
				continue;

			return false;
		}
		break;
	}

	return close(fd) == 0 && size == static_cast<ssize_t>(strlen(str));
}
static const char * const absval[6] = { "Value", "Min  ", "Max  ", "Fuzz ", "Flat ", "Resolution "};
#define KEY_MAX			0x2ff
#define EV_MAX			0x1f
#define BITS_PER_LONG (sizeof(long) * 8)
#define NBITS(x) ((((x)-1)/BITS_PER_LONG)+1)
#define OFF(x)  ((x)%BITS_PER_LONG)
#define BIT(x)  (1UL<<OFF(x))
#define LONG(x) ((x)/BITS_PER_LONG)
#define test_bit(bit, array)	((array[LONG(bit)] >> OFF(bit)) & 1)
#define DEV_INPUT_EVENT "/dev/input"
#define EVENT_DEV_NAME "event"
/**
 * Filter for the AutoDevProbe scandir on /dev/input.
 *
 * @param dir The current directory entry provided by scandir.
 *
 * @return Non-zero if the given directory entry starts with "event", or zero
 * otherwise.
 */
static int is_event_device(const struct dirent *dir) {
	return strncmp(EVENT_DEV_NAME, dir->d_name, 5) == 0;
}

bool HIDDevice::CheckABSEvent()
{
	int fd=-1;
	unsigned int type;
	int abs[6] = {0};
	struct dirent **namelist;
	int i, ndev;

    char input_event_name[PATH_MAX];
	unsigned long bit[EV_MAX][NBITS(KEY_MAX)];


#ifdef __BIONIC__
	// Android's libc doesn't have the GNU versionsort extension.
	ndev = scandir(DEV_INPUT_EVENT, &namelist, is_event_device, alphasort);
#else
	ndev = scandir(DEV_INPUT_EVENT, &namelist, is_event_device, versionsort);
#endif
	if (ndev <= 0)
		return false;
	for (i = 0; i < ndev; i++)
	{
		char fname[64];
		int fd = -1;
		char name[256] = "???";

		snprintf(fname, sizeof(fname),
			 "%s/%s", DEV_INPUT_EVENT, namelist[i]->d_name);
		fd = open(fname, O_RDONLY);
		if (fd < 0)
			continue;
		ioctl(fd, EVIOCGNAME(sizeof(name)), name);
		//fprintf(stderr, "%s:	%s\n", fname, name);
		close(fd);

		if(strstr(name, m_transportDeviceName.c_str()+4))
		{
			snprintf(input_event_name, sizeof(fname), "%s", fname);
		}
		free(namelist[i]);
	}
	
	if ((fd = open(input_event_name, O_RDONLY)) < 0) {
		if (errno == EACCES && getuid() != 0)
			fprintf(stderr, "No access right \n");
	}
	memset(bit, 0, sizeof(bit));
	ioctl(fd, EVIOCGBIT(0, EV_MAX), bit[0]);
	for (type = 0; type < EV_MAX; type++) {
		if (test_bit(type, bit[0]) && type == EV_ABS) {
			ioctl(fd, EVIOCGBIT(type, KEY_MAX), bit[type]);
			if (test_bit(ABS_X, bit[type])) {
				ioctl(fd, EVIOCGABS(ABS_X), abs);
				if(abs[2] == 0) //maximum
				{
					Sleep(1000);
					return false;
				}
			}
		}
	}
	return true;
}
void HIDDevice::RebindDriver()
{
	int bus = m_info.bustype;
	int vendor = m_info.vendor;
	int product = m_info.product;
	std::string hidDeviceName;
	std::string bindFile;
	std::string unbindFile;
	std::string hidrawFile;
	int notifyFd;
	int wd;
	int rc;
	Close();

	notifyFd = inotify_init();
	if (notifyFd < 0) {
		fprintf(stderr, "Failed to initialize inotify\n");
		return;
	}

	wd = inotify_add_watch(notifyFd, "/dev", IN_CREATE);
	if (wd < 0) {
		fprintf(stderr, "Failed to add watcher for /dev\n");
		return;
	}

	if (m_transportDeviceName == "") {
		if (!LookupHidDeviceName(bus, vendor, product, hidDeviceName)) {
			fprintf(stderr, "Failed to find HID device name for the specified device: bus (0x%x) vendor: (0x%x) product: (0x%x)\n",
				bus, vendor, product);
			return;
		}

		if (!FindTransportDevice(bus, hidDeviceName, m_transportDeviceName, m_driverPath)) {
			fprintf(stderr, "Failed to find the transport device / driver for %s\n", hidDeviceName.c_str());
			return;
		}

	}
 
	bindFile = m_driverPath + "bind";
	unbindFile = m_driverPath + "unbind";

	Sleep(500);
	if (!WriteDeviceNameToFile(unbindFile.c_str(), m_transportDeviceName.c_str())) {
		fprintf(stderr, "Failed to unbind HID device %s: %s\n",
			m_transportDeviceName.c_str(), strerror(errno));
		return;
	}
	Sleep(500);
	if (!WriteDeviceNameToFile(bindFile.c_str(), m_transportDeviceName.c_str())) {
		fprintf(stderr, "Failed to bind HID device %s: %s\n",
			m_transportDeviceName.c_str(), strerror(errno));
		return;
	}

	if (WaitForHidRawDevice(notifyFd, hidrawFile)) {
		rc = Open(hidrawFile.c_str());
		if (rc)
			fprintf(stderr, "Failed to open device (%s) during rebind: %d: errno: %s (%d)\n",
					hidrawFile.c_str(), rc, strerror(errno), errno);
	}
}

bool HIDDevice::FindTransportDevice(uint32_t bus, std::string & hidDeviceName,
			std::string & transportDeviceName, std::string & driverPath)
{
	std::string devicePrefix = "/sys/bus/";
	std::string devicePath;
	struct dirent * devicesDirEntry;
	DIR * devicesDir;
	struct dirent * devDirEntry;
	DIR * devDir;
	bool deviceFound = false;
	ssize_t sz;

	if (bus == BUS_I2C) {
		devicePrefix += "i2c/";
		// The i2c driver module installed on system is vary (i2c_hid, i2c_hid_acpi, i2c_hid_of),
		// so we will assign driver path until we get device name later.
	} else {
		devicePrefix += "usb/";
		driverPath = devicePrefix + "drivers/usbhid/";
	}
	devicePath = devicePrefix + "devices/";

	devicesDir = opendir(devicePath.c_str());
	if (!devicesDir)
		return false;

	while((devicesDirEntry = readdir(devicesDir)) != NULL) {
		if (devicesDirEntry->d_type != DT_LNK)
			continue;

		char buf[PATH_MAX];

		sz = readlinkat(dirfd(devicesDir), devicesDirEntry->d_name, buf, PATH_MAX);
		if (sz < 0)
			continue;

		buf[sz] = 0;

		std::string fullLinkPath = devicePath + buf;
		devDir = opendir(fullLinkPath.c_str());
		if (!devDir) {
			fprintf(stdout, "opendir failed\n");
			continue;
		}

		while ((devDirEntry = readdir(devDir)) != NULL) {
			if (!strcmp(devDirEntry->d_name, hidDeviceName.c_str())) {
				transportDeviceName = devicesDirEntry->d_name;
				deviceFound = true;
				break;
			}
		}
		closedir(devDir);

		if (deviceFound) {
			if (bus == BUS_I2C) {
				std::fstream ueventfile;
				std::string ueventfilepath = fullLinkPath + "/uevent";
				std::string uevent;
				std::string modulename;
				ueventfile.open(ueventfilepath.c_str(), std::ios::in);
				if(ueventfile.is_open()) {
					getline(ueventfile, uevent);
					modulename = uevent.substr(uevent.find("=") + 1, std::string::npos);
					driverPath = devicePrefix + "drivers/";
					driverPath += modulename;
					driverPath += "/";
				}
				ueventfile.close();
			}
			break;
		}
			
	}
	closedir(devicesDir);

	return deviceFound;
}

bool HIDDevice::LookupHidDeviceName(uint32_t bus, int16_t vendorId, int16_t productId, std::string & deviceName)
{
	bool ret = false;
	struct dirent * devDirEntry;
	DIR * devDir;
	char devicePrefix[15];

	snprintf(devicePrefix, 15, "%04X:%04X:%04X", bus, (vendorId & 0xFFFF), (productId & 0xFFFF));

	devDir = opendir("/sys/bus/hid/devices");
	if (!devDir)
		return false;

	while ((devDirEntry = readdir(devDir)) != NULL) {
		if (!strncmp(devDirEntry->d_name, devicePrefix, 14)) {
			deviceName = devDirEntry->d_name;
			ret = true;
			break;
		}
	}
	closedir(devDir);

	return ret;
}

bool HIDDevice::LookupHidDriverName(std::string &deviceName, std::string &driverName)
{
	bool ret = false;
	ssize_t sz;
	char link[PATH_MAX];
	std::string driverLink = "/sys/bus/hid/devices/" + deviceName + "/driver";

	sz = readlink(driverLink.c_str(), link, PATH_MAX);
	if (sz == -1)
		return ret;

	link[sz] = 0;

	driverName = std::string(StripPath(link, PATH_MAX));

	return true;
}

bool HIDDevice::WaitForHidRawDevice(int notifyFd, std::string & hidrawFile)
{
	struct timeval timeout;
	fd_set fds;
	int rc;
	ssize_t eventBytesRead;
	int eventBytesAvailable;
	size_t sz;
	char link[PATH_MAX];
	std::string transportDeviceName;
	std::string driverPath;
	std::string hidDeviceName;
	int offset = 0;

	for (;;) {
		FD_ZERO(&fds);
		FD_SET(notifyFd, &fds);

		timeout.tv_sec = 20;
		timeout.tv_usec = 0;

		rc = select(notifyFd + 1, &fds, NULL, NULL, &timeout);
		if (rc < 0) {
			if (errno == -EINTR)
				continue;

			return false;
		}

		if (rc == 0) {
			return false;
		}

		if (FD_ISSET(notifyFd, &fds)) {
			struct inotify_event * event;

			rc = ioctl(notifyFd, FIONREAD, &eventBytesAvailable);
			if (rc < 0) {
				continue;
			}

			char buf[eventBytesAvailable];

			eventBytesRead = read(notifyFd, buf, eventBytesAvailable);
			if (eventBytesRead < 0) {
				continue;
			}

			while (offset < eventBytesRead) {
				event = (struct inotify_event *)&buf[offset];

				if (!strncmp(event->name, "hidraw", 6)) {
					std::string classPath = std::string("/sys/class/hidraw/")
												+ event->name + "/device";
					sz = readlink(classPath.c_str(), link, PATH_MAX);
					link[sz] = 0;

					hidDeviceName = std::string(link).substr(9, 19);

					if (!FindTransportDevice(m_info.bustype, hidDeviceName, transportDeviceName, driverPath)) {
						fprintf(stderr, "Failed to find the transport device / driver for %s\n", hidDeviceName.c_str());
						continue;
					}

					if (transportDeviceName == m_transportDeviceName) {
						hidrawFile = std::string("/dev/") + event->name;
						return true;
					}
				}

				offset += sizeof(struct inotify_event) + event->len;
			}
		}
	}
}

bool HIDDevice::FindDevice(enum RMIDeviceType type)
{
	DIR * devDir;
	struct dirent * devDirEntry;
	char deviceFile[PATH_MAX];
	bool found = false;
	int rc;
	devDir = opendir("/dev");
	if (!devDir)
		return -1;

	while ((devDirEntry = readdir(devDir)) != NULL) {
		if (strstr(devDirEntry->d_name, "hidraw")) {
			snprintf(deviceFile, PATH_MAX, "/dev/%s", devDirEntry->d_name);
			fprintf(stdout, "Got device : /dev/%s\n", devDirEntry->d_name);
			rc = Open(deviceFile);
			if (rc != 0) {
				continue;
			} else if (type != RMI_DEVICE_TYPE_ANY && GetDeviceType() != type) {
				Close();
				continue;
			} else {
				found = true;
				break;
			}
		}
	}
	closedir(devDir);
	
	return found;
}
