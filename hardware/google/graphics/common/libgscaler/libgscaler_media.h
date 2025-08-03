/*
 * Copyright@ Samsung Electronics Co. LTD
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

#ifndef LIBGSCALER_MEDIA_H_
#define LIBGSCALER_MEDIA_H_

/* V4L2_SUBDEV */
#include <linux/v4l2-subdev.h>

#ifdef __GNUC__
#  define __UNUSED__ __attribute__((__unused__))
#else
#  define __UNUSED__
#endif

__BEGIN_DECLS

/*! \ingroup exynos_v4l2 */
int exynos_subdev_open(const char *filename, int oflag, ...);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_get_node_num(const char *devname, int oflag, ...);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_open_devname(const char *devname, int oflag, ...);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_close(int fd);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_enum_frame_size(int fd, struct v4l2_subdev_frame_size_enum *frame_size_enum);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_g_fmt(int fd, struct v4l2_subdev_format *fmt);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_s_fmt(int fd, struct v4l2_subdev_format *fmt);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_g_crop(int fd, struct v4l2_subdev_crop *crop);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_s_crop(int fd, struct v4l2_subdev_crop *crop);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_enum_frame_interval(int fd, struct v4l2_subdev_frame_interval_enum *frame_internval_enum);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_g_frame_interval(int fd, struct v4l2_subdev_frame_interval *frame_internval_enum);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_s_frame_interval(int fd, struct v4l2_subdev_frame_interval *frame_internval_enum);
/*! \ingroup exynos_v4l2 */
int exynos_subdev_enum_mbus_code(int fd, struct v4l2_subdev_mbus_code_enum *mbus_code_enum);
/* MEDIA CONTORLLER */
#include <linux/media.h>

/*! media_link
 * \ingroup exynos_v4l2
 */
struct media_link {
    struct media_pad *source;
    struct media_pad *sink;
    struct media_link *twin;
    __u32 flags;
    __u32 padding[3];
};

/*! media_link
 * \ingroup exynos_v4l2
 */
struct media_pad {
    struct media_entity *entity;
    __u32 index;
    __u32 flags;
    __u32 padding[3];
};

/*! media_link
 * \ingroup exynos_v4l2
 */
struct media_entity {
    struct media_device *media;
    struct media_entity_desc info;
    struct media_pad *pads;
    struct media_link *links;
    unsigned int max_links;
    unsigned int num_links;

    char devname[32];
    int fd;
    __u32 padding[6];
};

/*! media_link
 * \ingroup exynos_v4l2
 */
struct media_device {
    int fd;
    struct media_entity *entities;
    unsigned int entities_count;
    void (*debug_handler)(void *, ...);
    void *debug_priv;
    __u32 padding[6];
};

__END_DECLS

#endif /* LIBGSCALER_MEDIA_H_  */
