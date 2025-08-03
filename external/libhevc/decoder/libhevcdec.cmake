# src files
list(
  APPEND
  LIBHEVCDEC_SRCS
  "${HEVC_ROOT}/decoder/ihevcd_version.c"
  "${HEVC_ROOT}/decoder/ihevcd_api.c"
  "${HEVC_ROOT}/decoder/ihevcd_decode.c"
  "${HEVC_ROOT}/decoder/ihevcd_nal.c"
  "${HEVC_ROOT}/decoder/ihevcd_bitstream.c"
  "${HEVC_ROOT}/decoder/ihevcd_parse_headers.c"
  "${HEVC_ROOT}/decoder/ihevcd_parse_slice_header.c"
  "${HEVC_ROOT}/decoder/ihevcd_parse_slice.c"
  "${HEVC_ROOT}/decoder/ihevcd_parse_residual.c"
  "${HEVC_ROOT}/decoder/ihevcd_cabac.c"
  "${HEVC_ROOT}/decoder/ihevcd_intra_pred_mode_prediction.c"
  "${HEVC_ROOT}/decoder/ihevcd_process_slice.c"
  "${HEVC_ROOT}/decoder/ihevcd_utils.c"
  "${HEVC_ROOT}/decoder/ihevcd_job_queue.c"
  "${HEVC_ROOT}/decoder/ihevcd_ref_list.c"
  "${HEVC_ROOT}/decoder/ihevcd_get_mv.c"
  "${HEVC_ROOT}/decoder/ihevcd_mv_pred.c"
  "${HEVC_ROOT}/decoder/ihevcd_mv_merge.c"
  "${HEVC_ROOT}/decoder/ihevcd_iquant_itrans_recon_ctb.c"
  "${HEVC_ROOT}/decoder/ihevcd_itrans_recon_dc.c"
  "${HEVC_ROOT}/decoder/ihevcd_common_tables.c"
  "${HEVC_ROOT}/decoder/ihevcd_boundary_strength.c"
  "${HEVC_ROOT}/decoder/ihevcd_deblk.c"
  "${HEVC_ROOT}/decoder/ihevcd_inter_pred.c"
  "${HEVC_ROOT}/decoder/ihevcd_sao.c"
  "${HEVC_ROOT}/decoder/ihevcd_ilf_padding.c"
  "${HEVC_ROOT}/decoder/ihevcd_fmt_conv.c")

include_directories(${HEVC_ROOT}/decoder)

# arm/x86 sources
if("${CMAKE_SYSTEM_PROCESSOR}" STREQUAL "aarch64")
  list(
    APPEND
    LIBHEVCDEC_ASMS
    "${HEVC_ROOT}/decoder/arm64/ihevcd_fmt_conv_420sp_to_420p.s"
    "${HEVC_ROOT}/decoder/arm64/ihevcd_fmt_conv_420sp_to_420sp.s"
    "${HEVC_ROOT}/decoder/arm64/ihevcd_fmt_conv_420sp_to_rgba8888.s"
    "${HEVC_ROOT}/decoder/arm/ihevcd_function_selector.c"
    "${HEVC_ROOT}/decoder/arm/ihevcd_function_selector_noneon.c"
    "${HEVC_ROOT}/decoder/arm64/ihevcd_function_selector_av8.c"
    "${HEVC_ROOT}/decoder/arm64/ihevcd_itrans_recon_dc_chroma.s"
    "${HEVC_ROOT}/decoder/arm64/ihevcd_itrans_recon_dc_luma.s")

  include_directories(${HEVC_ROOT}/decoder/arm64)
elseif("${CMAKE_SYSTEM_PROCESSOR}" STREQUAL "aarch32")
  list(
    APPEND
    LIBHEVCDEC_ASMS
    "${HEVC_ROOT}/decoder/arm/ihevcd_fmt_conv_420sp_to_420p.s"
    "${HEVC_ROOT}/decoder/arm/ihevcd_fmt_conv_420sp_to_420sp.s"
    "${HEVC_ROOT}/decoder/arm/ihevcd_fmt_conv_420sp_to_rgba8888.s"
    "${HEVC_ROOT}/decoder/arm/ihevcd_function_selector_a9q.c"
    "${HEVC_ROOT}/decoder/arm/ihevcd_function_selector.c"
    "${HEVC_ROOT}/decoder/arm/ihevcd_function_selector_noneon.c"
    "${HEVC_ROOT}/decoder/arm/ihevcd_itrans_recon_dc_chroma.s"
    "${HEVC_ROOT}/decoder/arm/ihevcd_itrans_recon_dc_luma.s")

  include_directories(${HEVC_ROOT}/decoder/arm)
else()
  list(
    APPEND
    LIBHEVCDEC_SRCS
    "${HEVC_ROOT}/decoder/x86/ihevcd_function_selector.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_function_selector_generic.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_function_selector_ssse3.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_function_selector_sse42.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_fmt_conv_ssse3_intr.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_it_rec_dc_ssse3_intr.c"
    "${HEVC_ROOT}/decoder/x86/ihevcd_it_rec_dc_sse42_intr.c")

  include_directories(${HEVC_ROOT}/decoder/x86)
endif()

add_library(libhevcdec STATIC ${LIBHEVC_COMMON_SRCS} ${LIBHEVC_COMMON_ASMS}
                              ${LIBHEVCDEC_ASMS} ${LIBHEVCDEC_SRCS})
