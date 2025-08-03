/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef _UAPI__M2M1SHOT_H_
#define _UAPI__M2M1SHOT_H_
#include <linux/ioctl.h>
#include <linux/types.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#include <linux/videodev2.h>
#define M2M1SHOT_MAX_PLANES 3
struct m2m1shot_rect {
 __s16 left;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __s16 top;
 __u16 width;
 __u16 height;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct m2m1shot_pix_format {
 __u32 fmt;
 __u32 width;
 __u32 height;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 struct v4l2_rect crop;
};
enum m2m1shot_buffer_type {
 M2M1SHOT_BUFFER_NONE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 M2M1SHOT_BUFFER_DMABUF,
 M2M1SHOT_BUFFER_USERPTR,
};
struct m2m1shot_buffer_plane {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 union {
 __s32 fd;
 unsigned long userptr;
 };
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 size_t len;
};
struct m2m1shot_buffer {
 struct m2m1shot_buffer_plane plane[M2M1SHOT_MAX_PLANES];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u8 type;
 __u8 num_planes;
};
#define M2M1SHOT_OP_FLIP_VIRT (1 << 0)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define M2M1SHOT_OP_FLIP_HORI (1 << 1)
#define M2M1SHOT_OP_CSC_WIDE (1 << 8)
#define M2M1SHOT_OP_CSC_NARROW (1 << 9)
#define M2M1SHOT_OP_CSC_601 (1 << 10)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define M2M1SHOT_OP_CSC_709 (1 << 11)
#define M2M1SHOT_OP_PREMULTIPLIED_ALPHA (1 << 16)
#define M2M1SHOT_OP_DITHERING (1 << 17)
struct m2m1shot_operation {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __s16 quality_level;
 __s16 rotate;
 __u32 op;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct m2m1shot {
 struct m2m1shot_pix_format fmt_out;
 struct m2m1shot_pix_format fmt_cap;
 struct m2m1shot_buffer buf_out;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 struct m2m1shot_buffer buf_cap;
 struct m2m1shot_operation op;
 unsigned long reserved[2];
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct m2m1shot_custom_data {
 unsigned int cmd;
 unsigned long arg;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define M2M1SHOT_IOC_PROCESS _IOWR('M', 0, struct m2m1shot)
#define M2M1SHOT_IOC_CUSTOM _IOWR('M', 16, struct m2m1shot_custom_data)
#endif
