/* compress_plugin.c
**
** Copyright (c) 2019-2020, The Linux Foundation. All rights reserved.
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
#include <stdint.h>
#include <fcntl.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <poll.h>
#include <dlfcn.h>

#include <sys/ioctl.h>
#include <linux/ioctl.h>
#include <sound/asound.h>
#include "tinycompress/compress_plugin.h"
#include "sound/compress_offload.h"
#include "compress_ops.h"
#include "snd_utils.h"

#define U32_MAX	((uint32_t)~0U)

enum {
	COMPRESS_PLUG_STATE_OPEN,
	COMPRESS_PLUG_STATE_SETUP,
	COMPRESS_PLUG_STATE_PREPARED,
	COMPRESS_PLUG_STATE_PAUSE,
	COMPRESS_PLUG_STATE_RUNNING,
};

struct compress_plug_data {
	unsigned int card;
	unsigned int device;
	unsigned int fd;
	unsigned int flags;

	void *dl_hdl;
	COMPRESS_PLUGIN_OPEN_FN_PTR();

	struct compress_plugin *plugin;
	void *dev_node;
};

static int compress_plug_get_caps(struct compress_plug_data *plug_data,
		struct snd_compr_caps *caps)
{
	struct compress_plugin *plugin = plug_data->plugin;

	return plugin->ops->get_caps(plugin, caps);
}

static int compress_plug_set_params(struct compress_plug_data *plug_data,
		struct snd_compr_params *params)
{
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	if (plugin->state == COMPRESS_PLUG_STATE_RUNNING)
		return plugin->ops->set_params(plugin, params);
	else if (plugin->state != COMPRESS_PLUG_STATE_OPEN &&
		plugin->state != COMPRESS_PLUG_STATE_SETUP)
		return -EBADFD;

	if (params->buffer.fragment_size == 0 ||
	   params->buffer.fragments > U32_MAX / params->buffer.fragment_size ||
	   params->buffer.fragments == 0)
		return -EINVAL;

	rc = plugin->ops->set_params(plugin, params);
	if (!rc)
		plugin->state = COMPRESS_PLUG_STATE_SETUP;

	return rc;
}

static int compress_plug_avail(struct compress_plug_data *plug_data,
		struct snd_compr_avail *avail)
{
	struct compress_plugin *plugin = plug_data->plugin;

	return plugin->ops->avail(plugin, avail);
}

static int compress_plug_tstamp(struct compress_plug_data *plug_data,
		struct snd_compr_tstamp *tstamp)
{
	struct compress_plugin *plugin = plug_data->plugin;

	if (plugin->state != COMPRESS_PLUG_STATE_SETUP)
		return -EBADFD;

	return plugin->ops->tstamp(plugin, tstamp);
}

static int compress_plug_start(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	/* for playback moved to prepare in first write */
	/* for capture: move to prepare state set params */
	 /* TODO: add direction in set params */
	if (plugin->state != COMPRESS_PLUG_STATE_PREPARED)
		return -EBADFD;

	rc = plugin->ops->start(plugin);
	if (!rc)
		plugin->state = COMPRESS_PLUG_STATE_RUNNING;

	return rc;
}

static int compress_plug_stop(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	if (plugin->state == COMPRESS_PLUG_STATE_PREPARED ||
		plugin->state == COMPRESS_PLUG_STATE_SETUP)
		return -EBADFD;

	rc = plugin->ops->stop(plugin);
	if (!rc)
		plugin->state = COMPRESS_PLUG_STATE_SETUP;

	return rc;
}

static int compress_plug_pause(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	rc = plugin->ops->pause(plugin);
	if (!rc)
		plugin->state = COMPRESS_PLUG_STATE_PAUSE;

	return rc;
}

static int compress_plug_resume(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	if (plugin->state != COMPRESS_PLUG_STATE_PAUSE)
		return -EBADFD;

	rc = plugin->ops->resume(plugin);
	if (!rc)
		plugin->state = COMPRESS_PLUG_STATE_RUNNING;

	return rc;
}

static int compress_plug_drain(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;

	/* check if we will allow in pause */
	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	return plugin->ops->drain(plugin);
}

static int compress_plug_partial_drain(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;

	 /* check if we will allow in pause */
	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	return plugin->ops->partial_drain(plugin);
}

static int compress_plug_next_track(struct compress_plug_data *plug_data)
{
	struct compress_plugin *plugin = plug_data->plugin;

	/* transion to next track applied to running stream only */
	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	return plugin->ops->next_track(plugin);
}

static int compress_plug_ioctl(void *data, unsigned int cmd, ...)
{
	struct compress_plug_data *plug_data = data;
	struct compress_plugin *plugin = plug_data->plugin;
	int ret = 0;
	va_list ap;
	void *arg;

	va_start(ap, cmd);
	arg = va_arg(ap, void *);
	va_end(ap);

	switch (cmd) {
	case SNDRV_COMPRESS_IOCTL_VERSION:
		*((int*)arg) = SNDRV_COMPRESS_VERSION;
		break;
	case SNDRV_COMPRESS_GET_CAPS:
		ret = compress_plug_get_caps(plug_data, arg);
		break;
	case SNDRV_COMPRESS_SET_PARAMS:
		ret = compress_plug_set_params(plug_data, arg);
		break;
	case SNDRV_COMPRESS_AVAIL:
		ret = compress_plug_avail(plug_data, arg);
		break;
	case SNDRV_COMPRESS_TSTAMP:
		ret = compress_plug_tstamp(plug_data, arg);
		break;
	case SNDRV_COMPRESS_START:
		ret = compress_plug_start(plug_data);
		break;
	case SNDRV_COMPRESS_STOP:
		ret = compress_plug_stop(plug_data);
		break;
	case SNDRV_COMPRESS_PAUSE:
		ret = compress_plug_pause(plug_data);
		break;
	case SNDRV_COMPRESS_RESUME:
		ret = compress_plug_resume(plug_data);
		break;
	case SNDRV_COMPRESS_DRAIN:
		ret = compress_plug_drain(plug_data);
		break;
	case SNDRV_COMPRESS_PARTIAL_DRAIN:
		ret = compress_plug_partial_drain(plug_data);
		break;
	case SNDRV_COMPRESS_NEXT_TRACK:
		ret = compress_plug_next_track(plug_data);
		break;
	default:
		if (plugin->ops->ioctl)
			ret = plugin->ops->ioctl(plugin, cmd, arg);
		else
			ret = -EINVAL;
		break;
	}

	return ret;
}

static int compress_plug_poll(void *data, struct pollfd *fds,
				nfds_t nfds, int timeout)
{
	struct compress_plug_data *plug_data = data;
	struct compress_plugin *plugin = plug_data->plugin;

	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	return plugin->ops->poll(plugin, fds, nfds, timeout);
}


static int compress_plug_read(void *data, void *buf, size_t size)
{
	struct compress_plug_data *plug_data = data;
	struct compress_plugin *plugin = plug_data->plugin;

	if (plugin->state != COMPRESS_PLUG_STATE_RUNNING &&
		plugin->state != COMPRESS_PLUG_STATE_SETUP)
		return -EBADFD;

	return plugin->ops->read(plugin, buf, size);
}

static int compress_plug_write(void *data, const void *buf, size_t size)
{
	struct compress_plug_data *plug_data = data;
	struct compress_plugin *plugin = plug_data->plugin;
	int rc;

	if (plugin->state != COMPRESS_PLUG_STATE_SETUP &&
	    plugin->state != COMPRESS_PLUG_STATE_PREPARED &&
	    plugin->state != COMPRESS_PLUG_STATE_RUNNING)
		return -EBADFD;

	rc = plugin->ops->write(plugin, buf, size);
	if ((rc > 0) && (plugin->state == COMPRESS_PLUG_STATE_SETUP))
		plugin->state = COMPRESS_PLUG_STATE_PREPARED;

	return rc;
}

static void compress_plug_close(void *data)
{
	struct compress_plug_data *plug_data = data;
	struct compress_plugin *plugin = plug_data->plugin;

	plugin->ops->close(plugin);
	dlclose(plug_data->dl_hdl);

	free(plug_data);
}

static int compress_plug_open(unsigned int card, unsigned int device,
			unsigned int flags, void **data, void *node)
{
	struct compress_plug_data *plug_data;
	void *dl_hdl;
	int rc = 0;
	char *so_name, *open_fn, token[80], *name, *token_saveptr;

	plug_data = calloc(1, sizeof(*plug_data));
	if (!plug_data) {
		return -ENOMEM;
	}

	rc = snd_utils_get_str(node, "so-name", &so_name);
	if (rc) {
		fprintf(stderr, "%s: failed to get plugin lib name\n",
				__func__);
		goto err_get_lib;
	}

	dl_hdl = dlopen(so_name, RTLD_NOW);
	if (!dl_hdl) {
		fprintf(stderr, "%s: unable to open %s, error: %s\n",
					__func__, so_name, dlerror());
		goto err_dl_open;
	} else {
		fprintf(stderr, "%s: dlopen successful for %s\n",
					__func__, so_name);
	}

	sscanf(so_name, "lib%s", token);
	token_saveptr = token;
	name = strtok_r(token, ".", &token_saveptr);
	if (!name) {
		fprintf(stderr, "%s: invalid library name\n", __func__);
		goto err_open_fn;
	}
	const size_t open_fn_size = strlen(name) + strlen("_open") + 1;
	open_fn = calloc(1, open_fn_size);
	if (!open_fn) {
		rc = -ENOMEM;
		goto err_open_fn;
	}

	strlcpy(open_fn, name, open_fn_size);
	strlcat(open_fn, "_open", open_fn_size);

	plug_data->plugin_open_fn = dlsym(dl_hdl, open_fn);
	if (!plug_data->plugin_open_fn) {
		fprintf(stderr, "%s: dlsym to open fn failed, err = '%s'\n",
				__func__, dlerror());
		goto err_dlsym;
	}

	rc = plug_data->plugin_open_fn(&plug_data->plugin,
					card, device, flags);
	if (rc) {
		fprintf(stderr, "%s: failed to open plugin\n", __func__);
		goto err_dlsym;
	}

	/* Call snd-card-def to get card and compress nodes */
	/* Check how to manage fd for plugin */

	plug_data->dl_hdl = dl_hdl;
	plug_data->card = card;
	plug_data->device = device;
	plug_data->dev_node = node;
	plug_data->flags = flags;

	*data = plug_data;

	plug_data->plugin->state = COMPRESS_PLUG_STATE_OPEN;

	return 0;

err_dlsym:
	free(open_fn);
err_open_fn:
	dlclose(dl_hdl);
err_get_lib:
err_dl_open:
	free(plug_data);

	return rc;
}

struct compress_ops compr_plug_ops = {
	.open = compress_plug_open,
	.close = compress_plug_close,
	.ioctl = compress_plug_ioctl,
	.read = compress_plug_read,
	.write = compress_plug_write,
	.poll = compress_plug_poll,
};
