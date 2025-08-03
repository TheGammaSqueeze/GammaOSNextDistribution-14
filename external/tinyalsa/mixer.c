/* mixer.c
**
** Copyright 2011, The Android Open Source Project
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions are met:
**     * Redistributions of source code must retain the above copyright
**       notice, this list of conditions and the following disclaimer.
**     * Redistributions in binary form must reproduce the above copyright
**       notice, this list of conditions and the following disclaimer in the
**       documentation and/or other materials provided with the distribution.
**     * Neither the name of The Android Open Source Project nor the names of
**       its contributors may be used to endorse or promote products derived
**       from this software without specific prior written permission.
**
** THIS SOFTWARE IS PROVIDED BY The Android Open Source Project ``AS IS'' AND
** ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
** IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
** ARE DISCLAIMED. IN NO EVENT SHALL The Android Open Source Project BE LIABLE
** FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
** DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
** SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
** CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
** LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
** OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
** DAMAGE.
*/

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <ctype.h>
#include <poll.h>

#include <sys/ioctl.h>

#include <linux/ioctl.h>

#define __force
#define __bitwise
#define __user
#include <sound/asound.h>

#ifndef SNDRV_CTL_ELEM_ID_NAME_MAXLEN
#define SNDRV_CTL_ELEM_ID_NAME_MAXLEN 44
#endif

#include <tinyalsa/asoundlib.h>
#include "mixer_io.h"

struct mixer_ctl {
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_info *info;
    char **ename;
    bool info_retrieved;
};

struct mixer_ctl_group {
    struct snd_ctl_elem_info *elem_info;
    struct mixer_ctl *ctl;
    unsigned int count;
    int event_cnt;

    struct mixer_ops *ops;
    void *data;
};

struct mixer {
    int fd;
    struct snd_ctl_card_info card_info;

    /* hardware/physical mixer control group */
    struct mixer_ctl_group *hw_grp;

    /*
     * Virutal mixer control group.
     * Currently supports one virtual mixer (.so)
     * per card. Could be extended to multiple
     */
    struct mixer_ctl_group *virt_grp;

    unsigned int total_ctl_count;
};

static void mixer_grp_close(struct mixer_ctl_group *grp)
{
    unsigned int n, m;

    if (!grp)
        return;

    if (grp->ctl) {
        for (n = 0; n < grp->count; n++) {
            if (grp->ctl[n].ename) {
                unsigned int max = grp->ctl[n].info->value.enumerated.items;
                for (m = 0; m < max; m++)
                    free(grp->ctl[n].ename[m]);
                free(grp->ctl[n].ename);
            }
        }
        free(grp->ctl);
    }

    if (grp->elem_info)
        free(grp->elem_info);

    free(grp);
}

void mixer_close(struct mixer *mixer)
{
    if (!mixer)
        return;

    if (mixer->fd >= 0 && mixer->hw_grp)
        mixer->hw_grp->ops->close(mixer->hw_grp->data);
    mixer_grp_close(mixer->hw_grp);

    if (mixer->virt_grp)
        mixer->virt_grp->ops->close(mixer->virt_grp->data);
    mixer_grp_close(mixer->virt_grp);

    free(mixer);

    /* TODO: verify frees */
}

static int mixer_grp_open(struct mixer_ctl_group **ctl_grp,
                          struct mixer_ops *ops,
                          void *data, int *num_ctls_in_grp)
{
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_list elist;
    struct snd_ctl_elem_id *eid = NULL;
    unsigned int n;
    int ret;

    grp = calloc(1, sizeof(*grp));
    if (!grp)
        return -ENOMEM;

    memset(&elist, 0, sizeof(elist));
    ret = ops->ioctl(data, SNDRV_CTL_IOCTL_ELEM_LIST, &elist);
    if (ret < 0)
        goto err_get_elem_list;

    grp->ctl = calloc(elist.count, sizeof(struct mixer_ctl));
    grp->elem_info = calloc(elist.count, sizeof(struct snd_ctl_elem_info));
    if (!grp->ctl || !grp->elem_info) {
        ret = -ENOMEM;
        goto err_ctl_alloc;
    }

    eid = calloc(elist.count, sizeof(*eid));
    if (!eid) {
        ret = -ENOMEM;
        goto err_ctl_alloc;
    }

    grp->count = elist.count;
    elist.space = grp->count;
    elist.pids = eid;
    ret = ops->ioctl(data, SNDRV_CTL_IOCTL_ELEM_LIST, &elist);
    if (ret < 0)
        goto err_ctl_alloc;

    for (n = 0; n < grp->count; n++) {
        struct mixer_ctl *ctl = grp->ctl + n;

        ctl->grp = grp;
        ctl->info = grp->elem_info + n;
        ctl->info->id.numid = eid[n].numid;
        strncpy((char *)ctl->info->id.name, (char *)eid[n].name,
                SNDRV_CTL_ELEM_ID_NAME_MAXLEN);
        ctl->info->id.name[SNDRV_CTL_ELEM_ID_NAME_MAXLEN - 1] = 0;
    }

    grp->data = data;
    grp->ops = ops;
    *ctl_grp = grp;
    *num_ctls_in_grp = grp->count;

    free(eid);
    return 0;

err_ctl_alloc:

    free(eid);
    free(grp->elem_info);
    free(grp->ctl);

err_get_elem_list:

    free(grp);
    return ret;

}

static int mixer_do_hw_open(struct mixer *mixer, unsigned int card)
{
    struct mixer_ops *ops;
    void *data;
    int fd, ret, num_grp_ctls = 0;

    mixer->fd = -1;
    fd = mixer_hw_open(card, &data, &ops);
    if (fd < 0)
        return fd;

    ret = ops->ioctl(data, SNDRV_CTL_IOCTL_CARD_INFO, &mixer->card_info);
    if (ret < 0)
        goto err_card_info;

    ret = mixer_grp_open(&mixer->hw_grp, ops, data, &num_grp_ctls);
    if (ret < 0)
        goto err_card_info;

    mixer->total_ctl_count += num_grp_ctls;

    mixer->fd = fd;
    return 0;

err_card_info:
    ops->close(data);
    return ret;

}

static int mixer_do_plugin_open(struct mixer *mixer, unsigned int card,
                int is_hw_open_failed)
{
    struct mixer_ops *ops;
    void *data;
    int ret, num_grp_ctls = 0;

    ret = mixer_plugin_open(card, &data, &ops);
    if (ret < 0)
        return ret;

    /* Get card_info if hw_open failed */
    if (is_hw_open_failed) {
        ret = ops->ioctl(data, SNDRV_CTL_IOCTL_CARD_INFO, &mixer->card_info);
        if (ret < 0)
            goto err_card_info;
    }

    ret = mixer_grp_open(&mixer->virt_grp, ops, data, &num_grp_ctls);
    if (ret < 0)
        goto err_card_info;

    mixer->total_ctl_count += num_grp_ctls;
    return 0;

err_card_info:
    ops->close(data);
    return ret;

}

struct mixer *mixer_open(unsigned int card)
{
    struct mixer *mixer = NULL;
    int h_status, v_status;

    mixer = calloc(1, sizeof(*mixer));
    if (!mixer)
        goto fail;

    /* open hardware node first */
    h_status = mixer_do_hw_open(mixer, card);

    /*
     * open the virtual node even if hw_open fails
     * since hw_open is expected to fail for virtual cards
     * for which kernel does not register mixer node
     */
    //TODO: open virtual node only if mixer is defined under snd-card-def
    v_status = mixer_do_plugin_open(mixer, card, h_status);

    /* Fail mixer_open if both hw and plugin nodes cannot be opened */
    if (h_status < 0 && v_status < 0)
        goto fail;

    return mixer;

fail:
    if (mixer)
        mixer_close(mixer);

    return 0;
}

static bool mixer_ctl_get_elem_info(struct mixer_ctl* ctl)
{
    struct mixer_ctl_group *grp;
    unsigned int i;

    if (!ctl || !ctl->grp)
        return false;

    grp = ctl->grp;
    if (!ctl->info_retrieved) {
        if (grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_INFO,
                                                  ctl->info) < 0)
            return false;
        ctl->info_retrieved = true;
    }

    if (ctl->info->type != SNDRV_CTL_ELEM_TYPE_ENUMERATED || ctl->ename)
        return true;

    struct snd_ctl_elem_info tmp;
    char** enames = calloc(ctl->info->value.enumerated.items, sizeof(char*));
    if (!enames)
        return false;

    for (i = 0; i < ctl->info->value.enumerated.items; i++) {
        memset(&tmp, 0, sizeof(tmp));
        tmp.id.numid = ctl->info->id.numid;
        tmp.value.enumerated.item = i;
        if (grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_INFO, &tmp) < 0)
            goto fail;
        enames[i] = strdup(tmp.value.enumerated.name);
        if (!enames[i])
            goto fail;
    }
    ctl->ename = enames;
    return true;

fail:
    free(enames);
    return false;
}

const char *mixer_get_name(struct mixer *mixer)
{
    return (const char *)mixer->card_info.name;
}

unsigned int mixer_get_num_ctls(struct mixer *mixer)
{
    if (!mixer)
        return 0;

    return mixer->total_ctl_count;
}

static int mixer_grp_get_count(struct mixer_ctl_group *grp)
{
    if (!grp)
        return 0;

    return grp->count;
}

struct mixer_ctl *mixer_get_ctl(struct mixer *mixer, unsigned int id)
{
    struct mixer_ctl *ctl;
    unsigned int hw_ctl_count, virt_ctl_count;

    if (!mixer || (id >= mixer->total_ctl_count))
        return NULL;

    hw_ctl_count = mixer_grp_get_count(mixer->hw_grp);
    virt_ctl_count = mixer_grp_get_count(mixer->virt_grp);

    if (id < hw_ctl_count)
        ctl = mixer->hw_grp->ctl + id;
    else if ((id - hw_ctl_count) < virt_ctl_count)
        ctl = mixer->virt_grp->ctl + (id - hw_ctl_count);
    else
        return NULL;

    if (!mixer_ctl_get_elem_info(ctl))
        return NULL;

    return ctl;
}

struct mixer_ctl *mixer_get_ctl_by_name(struct mixer *mixer, const char *name)
{
    struct mixer_ctl_group *grp;
    unsigned int n;
    int hw_ctl_count;

    if (!mixer || !name)
        return NULL;

    hw_ctl_count = mixer_grp_get_count(mixer->hw_grp);
    if (mixer->hw_grp) {
        grp = mixer->hw_grp;

        for (n = 0; n < grp->count; n++)
            if (!strcmp(name, (char*) grp->elem_info[n].id.name))
                return mixer_get_ctl(mixer, n);
    }

    if (mixer->virt_grp) {
        grp = mixer->virt_grp;

        for (n = 0; n < grp->count; n++)
            if (!strcmp(name, (char*) grp->elem_info[n].id.name))
                return mixer_get_ctl(mixer, n + hw_ctl_count);
    }

    return NULL;
}

void mixer_ctl_update(struct mixer_ctl *ctl)
{
    struct mixer_ctl_group *grp;

    if (!ctl || !ctl->grp)
        return;

    grp = ctl->grp;
    grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_INFO, ctl->info);
}

const char *mixer_ctl_get_name(struct mixer_ctl *ctl)
{
    if (!ctl)
        return NULL;

    return (const char *)ctl->info->id.name;
}

enum mixer_ctl_type mixer_ctl_get_type(struct mixer_ctl *ctl)
{
    if (!ctl)
        return MIXER_CTL_TYPE_UNKNOWN;

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:    return MIXER_CTL_TYPE_BOOL;
    case SNDRV_CTL_ELEM_TYPE_INTEGER:    return MIXER_CTL_TYPE_INT;
    case SNDRV_CTL_ELEM_TYPE_ENUMERATED: return MIXER_CTL_TYPE_ENUM;
    case SNDRV_CTL_ELEM_TYPE_BYTES:      return MIXER_CTL_TYPE_BYTE;
    case SNDRV_CTL_ELEM_TYPE_IEC958:     return MIXER_CTL_TYPE_IEC958;
    case SNDRV_CTL_ELEM_TYPE_INTEGER64:  return MIXER_CTL_TYPE_INT64;
    default:                             return MIXER_CTL_TYPE_UNKNOWN;
    };
}

const char *mixer_ctl_get_type_string(struct mixer_ctl *ctl)
{
    if (!ctl)
        return "";

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:    return "BOOL";
    case SNDRV_CTL_ELEM_TYPE_INTEGER:    return "INT";
    case SNDRV_CTL_ELEM_TYPE_ENUMERATED: return "ENUM";
    case SNDRV_CTL_ELEM_TYPE_BYTES:      return "BYTE";
    case SNDRV_CTL_ELEM_TYPE_IEC958:     return "IEC958";
    case SNDRV_CTL_ELEM_TYPE_INTEGER64:  return "INT64";
    default:                             return "Unknown";
    };
}

unsigned int mixer_ctl_get_num_values(struct mixer_ctl *ctl)
{
    if (!ctl)
        return 0;

    return ctl->info->count;
}

static int percent_to_int(struct snd_ctl_elem_info *ei, int percent)
{
    int range;

    if (percent > 100)
        percent = 100;
    else if (percent < 0)
        percent = 0;

    range = (ei->value.integer.max - ei->value.integer.min);

    return ei->value.integer.min + (range * percent) / 100;
}

static int int_to_percent(struct snd_ctl_elem_info *ei, int value)
{
    int range = (ei->value.integer.max - ei->value.integer.min);

    if (range == 0)
        return 0;

    return ((value - ei->value.integer.min) / range) * 100;
}

int mixer_ctl_get_percent(struct mixer_ctl *ctl, unsigned int id)
{
    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_INTEGER))
        return -EINVAL;

    return int_to_percent(ctl->info, mixer_ctl_get_value(ctl, id));
}

int mixer_ctl_set_percent(struct mixer_ctl *ctl, unsigned int id, int percent)
{
    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_INTEGER))
        return -EINVAL;

    return mixer_ctl_set_value(ctl, id, percent_to_int(ctl->info, percent));
}

int mixer_ctl_get_value(struct mixer_ctl *ctl, unsigned int id)
{
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_value ev;
    int ret;

    if (!ctl || (id >= ctl->info->count) || !ctl->grp)
        return -EINVAL;

    grp = ctl->grp;
    memset(&ev, 0, sizeof(ev));
    ev.id.numid = ctl->info->id.numid;
    ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_READ, &ev);
    if (ret < 0)
        return ret;

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:
        return !!ev.value.integer.value[id];

    case SNDRV_CTL_ELEM_TYPE_INTEGER:
        return ev.value.integer.value[id];

    case SNDRV_CTL_ELEM_TYPE_ENUMERATED:
        return ev.value.enumerated.item[id];

    case SNDRV_CTL_ELEM_TYPE_BYTES:
        return ev.value.bytes.data[id];

    default:
        return -EINVAL;
    }

    return 0;
}

int mixer_ctl_is_access_tlv_rw(struct mixer_ctl *ctl)
{
    return (ctl->info->access & SNDRV_CTL_ELEM_ACCESS_TLV_READWRITE);
}

int mixer_ctl_get_array(struct mixer_ctl *ctl, void *array, size_t count)
{
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_value ev;
    int ret = 0;
    size_t size;
    void *source;
    size_t total_count;

    if ((!ctl) || !count || !array || !ctl->grp)
        return -EINVAL;

    grp = ctl->grp;
    total_count = ctl->info->count;

    if ((ctl->info->type == SNDRV_CTL_ELEM_TYPE_BYTES) &&
        mixer_ctl_is_access_tlv_rw(ctl)) {
            /* Additional two words is for the TLV header */
            total_count += TLV_HEADER_SIZE;
    }

    if (count > total_count)
        return -EINVAL;

    memset(&ev, 0, sizeof(ev));
    ev.id.numid = ctl->info->id.numid;

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:
    case SNDRV_CTL_ELEM_TYPE_INTEGER:
        ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_READ, &ev);
        if (ret < 0)
            return ret;
        size = sizeof(ev.value.integer.value[0]);
        source = ev.value.integer.value;
        break;

    case SNDRV_CTL_ELEM_TYPE_BYTES:
        /* check if this is new bytes TLV */
        if (mixer_ctl_is_access_tlv_rw(ctl)) {
            struct snd_ctl_tlv *tlv;
            int ret;

            if (count > SIZE_MAX - sizeof(*tlv))
                return -EINVAL;
            tlv = calloc(1, sizeof(*tlv) + count);
            if (!tlv)
                return -ENOMEM;
            tlv->numid = ctl->info->id.numid;
            tlv->length = count;
            ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_TLV_READ, tlv);

            source = tlv->tlv;
            memcpy(array, source, count);

            free(tlv);

            return ret;
        } else {
            ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_READ, &ev);
            if (ret < 0)
                return ret;
            size = sizeof(ev.value.bytes.data[0]);
            source = ev.value.bytes.data;
            break;
        }

    case SNDRV_CTL_ELEM_TYPE_IEC958:
        ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_READ, &ev);
        if (ret < 0)
            return ret;
        size = sizeof(ev.value.iec958);
        source = &ev.value.iec958;
        break;

    default:
        return -EINVAL;
    }

    memcpy(array, source, size * count);

    return 0;
}

int mixer_ctl_set_value(struct mixer_ctl *ctl, unsigned int id, int value)
{
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_value ev;
    int ret;

    if (!ctl || (id >= ctl->info->count) || !ctl->grp)
        return -EINVAL;

    grp = ctl->grp;
    memset(&ev, 0, sizeof(ev));
    ev.id.numid = ctl->info->id.numid;
    ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_READ, &ev);
    if (ret < 0)
        return ret;

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:
        ev.value.integer.value[id] = !!value;
        break;

    case SNDRV_CTL_ELEM_TYPE_INTEGER:
        ev.value.integer.value[id] = value;
        break;

    case SNDRV_CTL_ELEM_TYPE_ENUMERATED:
        ev.value.enumerated.item[id] = value;
        break;

    case SNDRV_CTL_ELEM_TYPE_BYTES:
        ev.value.bytes.data[id] = value;
        break;

    default:
        return -EINVAL;
    }

    return grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_WRITE, &ev);
}

int mixer_ctl_set_array(struct mixer_ctl *ctl, const void *array, size_t count)
{
    struct mixer_ctl_group *grp;
    struct snd_ctl_elem_value ev;
    size_t size;
    void *dest;
    size_t total_count;

    if ((!ctl) || !count || !array || !ctl->grp)
        return -EINVAL;

    grp = ctl->grp;
    total_count = ctl->info->count;

    if ((ctl->info->type == SNDRV_CTL_ELEM_TYPE_BYTES) &&
        mixer_ctl_is_access_tlv_rw(ctl)) {
            /* Additional two words is for the TLV header */
            total_count += TLV_HEADER_SIZE;
    }

    if (count > total_count)
        return -EINVAL;

    memset(&ev, 0, sizeof(ev));
    ev.id.numid = ctl->info->id.numid;

    switch (ctl->info->type) {
    case SNDRV_CTL_ELEM_TYPE_BOOLEAN:
    case SNDRV_CTL_ELEM_TYPE_INTEGER:
        size = sizeof(ev.value.integer.value[0]);
        dest = ev.value.integer.value;
        break;

    case SNDRV_CTL_ELEM_TYPE_BYTES:
        /* check if this is new bytes TLV */
        if (mixer_ctl_is_access_tlv_rw(ctl)) {
            struct snd_ctl_tlv *tlv;
            int ret = 0;
            if (count > SIZE_MAX - sizeof(*tlv))
                return -EINVAL;
            tlv = calloc(1, sizeof(*tlv) + count);
            if (!tlv)
                return -ENOMEM;
            tlv->numid = ctl->info->id.numid;
            tlv->length = count;
            memcpy(tlv->tlv, array, count);

            ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_TLV_WRITE, tlv);
            free(tlv);

            return ret;
        } else {
            size = sizeof(ev.value.bytes.data[0]);
            dest = ev.value.bytes.data;
        }
        break;

    case SNDRV_CTL_ELEM_TYPE_IEC958:
        size = sizeof(ev.value.iec958);
        dest = &ev.value.iec958;
        break;

    default:
        return -EINVAL;
    }

    memcpy(dest, array, size * count);

    return grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_WRITE, &ev);
}

int mixer_ctl_get_range_min(struct mixer_ctl *ctl)
{
    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_INTEGER))
        return -EINVAL;

    return ctl->info->value.integer.min;
}

int mixer_ctl_get_range_max(struct mixer_ctl *ctl)
{
    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_INTEGER))
        return -EINVAL;

    return ctl->info->value.integer.max;
}

unsigned int mixer_ctl_get_num_enums(struct mixer_ctl *ctl)
{
    if (!ctl)
        return 0;

    return ctl->info->value.enumerated.items;
}

const char *mixer_ctl_get_enum_string(struct mixer_ctl *ctl,
                                      unsigned int enum_id)
{
    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_ENUMERATED) ||
        (enum_id >= ctl->info->value.enumerated.items))
        return NULL;

    return (const char *)ctl->ename[enum_id];
}

int mixer_ctl_set_enum_by_string(struct mixer_ctl *ctl, const char *string)
{
    struct mixer_ctl_group *grp;
    unsigned int i, num_enums;
    struct snd_ctl_elem_value ev;
    int ret;

    if (!ctl || (ctl->info->type != SNDRV_CTL_ELEM_TYPE_ENUMERATED) || !ctl->grp)
        return -EINVAL;

    grp = ctl->grp;
    num_enums = ctl->info->value.enumerated.items;
    for (i = 0; i < num_enums; i++) {
        if (!strcmp(string, ctl->ename[i])) {
            memset(&ev, 0, sizeof(ev));
            ev.value.enumerated.item[0] = i;
            ev.id.numid = ctl->info->id.numid;
            ret = grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_ELEM_WRITE, &ev);
            if (ret < 0)
                return ret;
            return 0;
        }
    }

    return -EINVAL;
}

/** Subscribes for the mixer events.
 * @param mixer A mixer handle.
 * @param subscribe value indicating subscribe or unsubscribe for events
 * @returns On success, zero.
 *  On failure, non-zero.
 * @ingroup libtinyalsa-mixer
 */
int mixer_subscribe_events(struct mixer *mixer, int subscribe)
{
    struct mixer_ctl_group *grp;

    if (mixer->hw_grp) {
        grp = mixer->hw_grp;
        if (!subscribe)
            grp->event_cnt = 0;
        if (grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_SUBSCRIBE_EVENTS, &subscribe) < 0)
            return -1;
    }

    if (mixer->virt_grp) {
        grp = mixer->virt_grp;
        if (!subscribe)
            grp->event_cnt = 0;
        if (grp->ops->ioctl(grp->data, SNDRV_CTL_IOCTL_SUBSCRIBE_EVENTS, &subscribe) < 0)
            return -1;
    }
    return 0;
}

/** Wait for mixer events.
 * @param mixer A mixer handle.
 * @param timeout timout value
 * @returns On success, 1.
 *  On failure, -errno.
 *  On timeout, 0
 * @ingroup libtinyalsa-mixer
 */
int mixer_wait_event(struct mixer *mixer, int timeout)
{
    struct pollfd pfd[2];
    struct mixer_ctl_group *grp;
    int count = 0, num_fds = 0, i;

    memset(pfd, 0, sizeof(struct pollfd) * 2);

    if (mixer->fd >= 0)
        num_fds++;

    if (mixer->virt_grp)
        num_fds++;


    /* TODO wait events for virt fd */
    if (mixer->fd >= 0) {
        pfd[count].fd = mixer->fd;
        pfd[count].events = POLLIN | POLLOUT | POLLERR | POLLNVAL;
        count++;
    }

    if (mixer->virt_grp) {
        grp = mixer->virt_grp;
        if (!grp->ops->get_poll_fd(grp->data, pfd, count)) {
            pfd[count].events = POLLIN | POLLERR | POLLNVAL;
            count++;
        }
    }

    if (!count)
        return 0;

    for (;;) {
        int err;
        err = poll(pfd, count, timeout);
        if (err < 0)
            return -errno;
        if (!err)
            return 0;
        for (i = 0; i < count; i++) {
            if (pfd[i].revents & (POLLERR | POLLNVAL))
                return -EIO;
            if (pfd[i].revents & (POLLIN | POLLOUT)) {
                if ((i == 0) && mixer->fd >= 0) {
                    grp = mixer->hw_grp;
                    grp->event_cnt++;
                } else {
                    grp = mixer->virt_grp;
                    grp->event_cnt++;
                }
                return 1;
            }
        }
    }
}

/** Consume a mixer event.
 * If mixer_subscribe_events has been called,
 * mixer_wait_event will identify when a control value has changed.
 * This function will clear a single event from the mixer so that
 * further events can be alerted.
 *
 * @param mixer A mixer handle.
 * @returns 0 on success.  -errno on failure.
 * @ingroup libtinyalsa-mixer
 */
int mixer_consume_event(struct mixer *mixer)
{
    struct snd_ctl_event ev;
    struct mixer_ctl_group *grp;
    ssize_t count = 0;

    // Exporting the actual event would require exposing snd_ctl_event
    // via the header file, and all associated structs.
    // The events generally tell you exactly which value changed,
    // but reading values you're interested isn't hard and simplifies
    // the interface greatly.
    if (mixer->hw_grp) {
        grp = mixer->hw_grp;
        if (grp->event_cnt) {
            grp->event_cnt--;
            count = grp->ops->read_event(grp->data, &ev, sizeof(ev));
            return (count >= 0) ? 0 : -errno;
        }
    }

    if (mixer->virt_grp) {
        grp = mixer->virt_grp;
        if (grp->event_cnt) {
            grp->event_cnt--;
            count += grp->ops->read_event(grp->data, &ev, sizeof(ev));
            return (count >= 0) ? 0 : -errno;
        }
    }
    return 0;
}

/** Read a mixer event.
 * If mixer_subscribe_events has been called,
 * mixer_wait_event will identify when a control value has changed.
 * This function will read and clear a single event from the mixer
 * so that further events can be alerted.
 *
 * @param mixer A mixer handle.
 * @param ev snd_ctl_event pointer where event needs to be read
 * @returns 0 on success.  -errno on failure.
 * @ingroup libtinyalsa-mixer
 */
int mixer_read_event(struct mixer *mixer, struct ctl_event *ev)
{
    struct mixer_ctl_group *grp;
    ssize_t count = 0;

    if (mixer->hw_grp) {
        grp = mixer->hw_grp;
        if (grp->event_cnt) {
            grp->event_cnt--;
            count = grp->ops->read_event(grp->data, (struct snd_ctl_event *)ev, sizeof(*ev));
            return (count >= 0) ? 0 : -errno;
        }
    }

    if (mixer->virt_grp) {
        grp = mixer->virt_grp;
        if (grp->event_cnt) {
            grp->event_cnt--;
            count = grp->ops->read_event(grp->data, (struct snd_ctl_event *)ev, sizeof(*ev));
            return (count >= 0) ? 0 : -errno;
        }
    }
    return 0;
}
