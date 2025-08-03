## Frame Info exported from libHEVC

### Introduction
QP and CU type maps for H265 are defined for each 8x8 coding unit.
The QP values defined as unsigned 8-bit numbers can range from <1, 51> and CU type can be
INTER/INTRA/SKIP. HEVC defines them in ihevc_defs.h as PRED_MODE_INTER = 0, PRED_MODE_INTRA = 1
and PRED_MODE_SKIP = 2.
Set the “u4_frame_info_enable” flag to enable encoder/decoder to populate and return the qp values
and CU type data in its output structure ihevcd_cxa_video_decode_op_t via pu1_8x8_blk_qp_map and
pu1_8x8_blk_type_map.

### Mapping to the frame
Within a video sequence, CTUs of a fixed size (16x16, 32x32, or 64x64) can be further divided into
CUs of size as low as 8x8 pixels. A CU’s number of QP entries (each for 8x8 blocks) can range from
1 to 64. A frame with a resolution of WdxHt has a total of (align8(Wd) x align8(Ht)) / 64 entries
for QP and CU type map each. Qp and CU type values for each 8x8 block are stored in raster scan
order. Refer to ihevcd_cxa.h for details.

### Plugin/Application
The encoder/decoder keeps the QP and CU type map as a part of its output handle. The plugins can
access these data through the output structure.
