/* compress_plugin.h
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

#ifndef __COMPRESS_PLUGIN_H__
#define __COMPRESS_PLUGIN_H__

#include "sound/compress_params.h"
#include "sound/compress_offload.h"

#define COMPRESS_PLUGIN_OPEN_FN(name)                    \
	int name##_open(struct compress_plugin **plugin,     \
			unsigned int card,                   \
			unsigned int device,                  \
			unsigned int flags)

#define COMPRESS_PLUGIN_OPEN_FN_PTR()                        \
	int (*plugin_open_fn) (struct compress_plugin **plugin,  \
				unsigned int card,                \
				unsigned int device,               \
				unsigned int flags);

struct compress_plugin;

struct compress_plugin_ops {
	void (*close) (struct compress_plugin *plugin);
	int (*get_caps) (struct compress_plugin *plugin,
					 struct snd_compr_caps *caps);
	int (*set_params) (struct compress_plugin *plugin,
					   struct snd_compr_params *params);
	int (*avail) (struct compress_plugin *plugin,
				  struct snd_compr_avail *avail);
	int (*tstamp) (struct compress_plugin *plugin,
					 struct snd_compr_tstamp *tstamp);
	int (*write) (struct compress_plugin *plugin,
					 const void *buf, size_t size);
	int (*read) (struct compress_plugin *plugin,
					 void *buf, size_t size);
	int (*start) (struct compress_plugin *plugin);
	int (*stop) (struct compress_plugin *plugin);
	int (*pause) (struct compress_plugin *plugin);
	int (*resume) (struct compress_plugin *plugin);
	int (*drain) (struct compress_plugin *plugin);
	int (*partial_drain) (struct compress_plugin *plugin);
	int (*next_track) (struct compress_plugin *plugin);
	int (*ioctl) (struct compress_plugin *plugin, int cmd, ...);
	int (*poll) (struct compress_plugin *plugin,
			struct pollfd *fds, nfds_t nfds, int timeout);
};

struct compress_plugin {
	unsigned int card;

	struct compress_plugin_ops *ops;

	void *node;
	int mode;
	void *priv;

	unsigned int state;
};

#endif /* end of __COMPRESS_PLUGIN_H__ */
