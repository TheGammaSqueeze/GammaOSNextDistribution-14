/* snd_utils.h
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

#ifndef __SND_CARD_UTILS_H__
#define __SND_CARD_UTILS_H__

#include <dlfcn.h>

struct snd_node {
    void *card_node;
    void *dev_node;
    void *dl_hdl;

    void* (*get_card) (unsigned int card);
    void (*put_card) (void *card);
    void* (*get_node) (void *card, unsigned int id,
                      int type);
    int (*get_int) (void *node, const char *prop, int *val);
    int (*get_str) (void *node, const char *prop, char **val);
};

enum {
    NODE_PCM,
    NODE_MIXER,
};

enum snd_node_type {
    SND_NODE_TYPE_HW = 0,
    SND_NODE_TYPE_PLUGIN,
    SND_NODE_TYPE_INVALID,
};

struct snd_node *snd_utils_get_dev_node(unsigned int card,
        unsigned int device, int dev_type);

void snd_utils_put_dev_node(struct snd_node *node);

enum snd_node_type snd_utils_get_node_type(struct snd_node *node);

int snd_utils_get_int(struct snd_node *node, const char *prop, int *val);

int snd_utils_get_str(struct snd_node *node, const char *prop, char **val);

#endif /* end of __SND_CARD_UTILS_H__ */
