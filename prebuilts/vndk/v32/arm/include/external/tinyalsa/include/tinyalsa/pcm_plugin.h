/* pcm_plugin.h
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

#ifndef __PCM_PLUGIN_H__
#define __PCM_PLUGIN_H__

#include <poll.h>

#define PCM_PLUGIN_OPEN_FN(name)                    \
    int name##_open(struct pcm_plugin **plugin,     \
                    unsigned int card,              \
                    unsigned int device,            \
                    int mode)

#define PCM_PLUGIN_OPEN_FN_PTR()                        \
    int (*plugin_open_fn) (struct pcm_plugin **plugin,  \
                           unsigned int card,           \
                           unsigned int device,         \
                           int mode);

struct pcm_plugin;

struct pcm_plugin_ops {
    int (*close) (struct pcm_plugin *plugin);
    int (*hw_params) (struct pcm_plugin *plugin,
                      struct snd_pcm_hw_params *params);
    int (*sw_params) (struct pcm_plugin *plugin,
                      struct snd_pcm_sw_params *params);
    int (*sync_ptr) (struct pcm_plugin *plugin,
                     struct snd_pcm_sync_ptr *sync_ptr);
    int (*writei_frames) (struct pcm_plugin *plugin,
                          struct snd_xferi *x);
    int (*readi_frames) (struct pcm_plugin *plugin,
                         struct snd_xferi *x);
    int (*ttstamp) (struct pcm_plugin *plugin,
                    int *tstamp);
    int (*prepare) (struct pcm_plugin *plugin);
    int (*start) (struct pcm_plugin *plugin);
    int (*drop) (struct pcm_plugin *plugin);
    int (*ioctl) (struct pcm_plugin *plugin,
                  int cmd, void *arg);
    void* (*mmap) (struct pcm_plugin *plugin, void *addr, size_t length, int prot,
                       int flags, off_t offset);
    int (*munmap) (struct pcm_plugin *plugin, void *addr, size_t length);
    int (*poll) (struct pcm_plugin *plugin, struct pollfd *pfd, nfds_t nfds,
        int timeout);
};

struct pcm_plugin_min_max {
    unsigned int min;
    unsigned int max;
};

struct pcm_plugin_hw_constraints {
    uint64_t access;
    /* As of this implementation ALSA supports 52 formats */
    uint64_t format;
    struct pcm_plugin_min_max bit_width;
    struct pcm_plugin_min_max channels;
    struct pcm_plugin_min_max rate;
    struct pcm_plugin_min_max periods;
    struct pcm_plugin_min_max period_bytes;
};

struct pcm_plugin {
    unsigned int card;

    struct pcm_plugin_ops *ops;
    struct pcm_plugin_hw_constraints *constraints;

    void *node;
    int mode;
    void *priv;

    unsigned int state;
};

#endif /* end of __PCM_PLUGIN_H__ */
