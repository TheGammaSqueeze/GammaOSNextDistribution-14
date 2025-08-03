/* compress_hw.c
**
** Copyright (c) 2019, The Linux Foundation. All rights reserved.
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions are
** met:
**   * Redistributions of source code must retain the above copyright
**     notice, this list of conditions and the following disclaimer.
**   * Redistributions in binary form must reproduce the above
**     copyright notice, this list of conditions and the following
**     disclaimer in the documentation and/or other materials provided
**     with the distribution.
**   * Neither the name of The Linux Foundation nor the names of its
**     contributors may be used to endorse or promote products derived
**     from this software without specific prior written permission.
**
** THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
** WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
** MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
** ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
** BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
** CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
** SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
** BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
** WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
** OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
** IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <stdarg.h>
#include <string.h>
#include <stdbool.h>
#include <errno.h>
#include <unistd.h>
#include <poll.h>

#include <sys/ioctl.h>
#include <linux/ioctl.h>
#include <sound/asound.h>
#include "tinycompress/tinycompress.h"
#include "compress_ops.h"

struct compress_hw_data {
	unsigned int card;
	unsigned int device;
	unsigned int fd;
};

static int compress_hw_poll(void *data, struct pollfd *fds,
				nfds_t nfds, int timeout)
{
	struct compress_hw_data *hw_data = data;

	fds->fd = hw_data->fd;
	return poll(fds, nfds, timeout);
}

static int compress_hw_write(void *data, const void *buf, size_t size)
{
	struct compress_hw_data *hw_data = data;

	return write(hw_data->fd, buf, size);
}

static int compress_hw_read(void *data, void *buf, size_t size)
{
	struct compress_hw_data *hw_data = data;

	return read(hw_data->fd, buf, size);
}

static int compress_hw_ioctl(void *data, unsigned int cmd, ...)
{
	struct compress_hw_data *hw_data = data;
	va_list ap;
	void *arg;

	va_start(ap, cmd);
	arg = va_arg(ap, void *);
	va_end(ap);

	return ioctl(hw_data->fd, cmd, arg);
}

static void compress_hw_close(void *data)
{
	struct compress_hw_data *hw_data = data;

	if (hw_data->fd > 0)
		close(hw_data->fd);

	free(hw_data);
}

static int compress_hw_open(unsigned int card, unsigned int device,
		unsigned int flags, void **data, __unused void *node)
{
	struct compress_hw_data *hw_data;
	char fn[256];
	int fd;

	hw_data = calloc(1, sizeof(*hw_data));
	if (!hw_data) {
		return -ENOMEM;
	}

	snprintf(fn, sizeof(fn), "/dev/snd/comprC%uD%u", card, device);

	if (flags & COMPRESS_OUT)
		fd = open(fn,  O_RDONLY);
	else
		fd = open(fn, O_WRONLY);

	if (fd < 0) {
		return fd;
	}

	hw_data->card = card;
	hw_data->device = device;
	hw_data->fd = fd;

	*data = hw_data;

	return fd;
}

struct compress_ops compr_hw_ops = {
	.open = compress_hw_open,
	.close = compress_hw_close,
	.ioctl = compress_hw_ioctl,
	.read = compress_hw_read,
	.write = compress_hw_write,
	.poll = compress_hw_poll,
};
