/*--------------------------------------------------------------------------
Copyright (c) 2010-2019, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of The Linux Foundation nor
      the names of its contributors may be used to endorse or promote
      products derived from this software without specific prior written
      permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
--------------------------------------------------------------------------*/

#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/eventfd.h>
#include <unistd.h>
#include <fcntl.h>
#include "video_encoder_device_v4l2.h"
#include "omx_video_encoder.h"
#include <media/msm_vidc.h>
#ifdef USE_ION
#include <linux/msm_ion.h>
#endif
#include <math.h>
#include <media/msm_media_info.h>
#include <cutils/properties.h>
#include <media/hardware/HardwareAPI.h>

#ifdef _ANDROID_
#include <media/hardware/HardwareAPI.h>
#include <gralloc_priv.h>
#endif

#include <qdMetaData.h>
#include <color_metadata.h>
#include "PlatformConfig.h"

#define ATRACE_TAG ATRACE_TAG_VIDEO
#include <utils/Trace.h>

#define YUV_STATS_LIBRARY_NAME "libgpustats.so" // UBWC case: use GPU library

#undef ALIGN
#define ALIGN(x, to_align) ((((unsigned long) x) + (to_align - 1)) & ~(to_align - 1))
#define EXTRADATA_IDX(__num_planes) ((__num_planes) ? (__num_planes) - 1 : 0)
#define MAXDPB 16
#define MIN(x,y) (((x) < (y)) ? (x) : (y))
#define MAX(x,y) (((x) > (y)) ? (x) : (y))
#define ROUND(__sz, __align) (((__sz) + ((__align>>1))) & (~(__align-1)))
#define MAX_PROFILE_PARAMS 6
#define HEVC_MAIN_START 0
#define HEVC_MAIN10_START (HEVC_MAIN_START + 13)
#define POLL_TIMEOUT 1000
#define MAX_SUPPORTED_SLICES_PER_FRAME 28 /* Max supported slices with 32 output buffers */

#define SZ_4K 0x1000
#define SZ_1M 0x100000


#define Log2(number, power)  { OMX_U32 temp = number; power = 0; while( (0 == (temp & 0x1)) &&  power < 16) { temp >>=0x1; power++; } }
#define Q16ToFraction(q,num,den) { OMX_U32 power; Log2(q,power);  num = q >> power; den = 0x1 << (16 - power); }

#define BUFFER_LOG_LOC "/data/vendor/media"

#define OMX_VIDEO_LEVEL_UNKNOWN 0

#define VENC_BFRAME_MAX_COUNT       1
#define VENC_BFRAME_MAX_FPS         60
#define VENC_BFRAME_MAX_WIDTH       1920
#define VENC_BFRAME_MAX_HEIGHT      1088

#undef LOG_TAG
#define LOG_TAG "OMX-VENC: venc_dev"

//constructor
venc_dev::venc_dev(class omx_venc *venc_class)
{
    //nothing to do
    int i = 0;
    venc_handle = venc_class;
    etb = ebd = ftb = fbd = 0;
    m_poll_efd = -1;

    struct v4l2_control control;
    for (i = 0; i < MAX_PORT; i++)
        streaming[i] = false;

    stopped = 1;
    paused = false;
    async_thread_created = false;
    async_thread_force_stop = false;
    color_format = 0;
    hw_overload = false;
    mBatchSize = 0;
    deinterlace_enabled = false;
    m_roi_enabled = false;
    pthread_mutex_init(&m_roilock, NULL);
    pthread_mutex_init(&pause_resume_mlock, NULL);
    pthread_cond_init(&pause_resume_cond, NULL);
    memset(&input_extradata_info, 0, sizeof(input_extradata_info));
    memset(&output_extradata_info, 0, sizeof(output_extradata_info));
    memset(&idrperiod, 0, sizeof(idrperiod));
    memset(&multislice, 0, sizeof(multislice));
    memset (&slice_mode, 0 , sizeof(slice_mode));
    memset(&m_sVenc_cfg, 0, sizeof(m_sVenc_cfg));
    memset(&rate_ctrl, 0, sizeof(rate_ctrl));
    memset(&bitrate, 0, sizeof(bitrate));
    memset(&intra_period, 0, sizeof(intra_period));
    memset(&codec_profile, 0, sizeof(codec_profile));
    memset(&set_param, 0, sizeof(set_param));
    memset(&time_inc, 0, sizeof(time_inc));
    memset(&m_sInput_buff_property, 0, sizeof(m_sInput_buff_property));
    memset(&m_sOutput_buff_property, 0, sizeof(m_sOutput_buff_property));
    memset(&session_qp, 0, sizeof(session_qp));
    memset(&session_ipb_qp_values, 0, sizeof(session_ipb_qp_values));
    memset(&entropy, 0, sizeof(entropy));
    memset(&dbkfilter, 0, sizeof(dbkfilter));
    memset(&intra_refresh, 0, sizeof(intra_refresh));
    memset(&hec, 0, sizeof(hec));
    memset(&voptimecfg, 0, sizeof(voptimecfg));
    memset(&capability, 0, sizeof(capability));
    memset(&m_debug,0,sizeof(m_debug));
    is_searchrange_set = false;
    enable_mv_narrow_searchrange = false;
    supported_rc_modes = RC_ALL;
    memset(&vqzip_sei_info, 0, sizeof(vqzip_sei_info));
    memset(&ltrinfo, 0, sizeof(ltrinfo));
    memset(&fd_list, 0, sizeof(fd_list));
    sess_priority.priority = 1;
    operating_rate = 30;
    memset(&color_space, 0x0, sizeof(color_space));
    memset(&temporal_layers_config, 0x0, sizeof(temporal_layers_config));
    client_req_disable_bframe   = false;
    bframe_implicitly_enabled = false;
    client_req_disable_temporal_layers  = false;
    client_req_turbo_mode  = false;
    intra_period.num_pframes = 29;
    intra_period.num_bframes = 0;

    Platform::Config::getInt32(Platform::vidc_enc_log_in,
            (int32_t *)&m_debug.in_buffer_log, 0);
    Platform::Config::getInt32(Platform::vidc_enc_log_out,
            (int32_t *)&m_debug.out_buffer_log, 0);
    Platform::Config::getInt32(Platform::vidc_enc_csc_custom_matrix,
            (int32_t *)&is_csc_custom_matrix_enabled, 0);

    char property_value[PROPERTY_VALUE_MAX] = {0};

    property_get("vendor.vidc.enc.log.in", property_value, "0");
    m_debug.in_buffer_log |= atoi(property_value);

    property_value[0] = '\0';
    property_get("vendor.vidc.enc.log.out", property_value, "0");
    m_debug.out_buffer_log |= atoi(property_value);

    property_get("vendor.vidc.enc.log.extradata", property_value, "0");
    m_debug.extradata_log = atoi(property_value);

#ifdef _UBWC_
    property_get("debug.gralloc.gfx_ubwc_disable", property_value, "0");
    if(!(strncmp(property_value, "1", PROPERTY_VALUE_MAX)) ||
        !(strncmp(property_value, "true", PROPERTY_VALUE_MAX))) {
        is_gralloc_source_ubwc = 0;
    } else {
        is_gralloc_source_ubwc = 1;
    }
#else
    is_gralloc_source_ubwc = 0;
#endif

     property_value[0] = '\0';
     property_get("vendor.vidc.log.loc", property_value, BUFFER_LOG_LOC);
     if (*property_value)
         strlcpy(m_debug.log_loc, property_value, PROPERTY_VALUE_MAX);

    mUseAVTimerTimestamps = false;
    mIsGridset = false;
    mTileDimension = 0;

    profile_level_converter::init();
}

venc_dev::~venc_dev()
{
    if (m_roi_enabled) {
        std::list<roidata>::iterator iter;
        pthread_mutex_lock(&m_roilock);
        for (iter = m_roilist.begin(); iter != m_roilist.end(); iter++) {
            DEBUG_PRINT_HIGH("roidata with timestamp (%lld) should have been removed already",
                iter->timestamp);
            free(iter->info.pRoiMBInfo);
        }
        m_roilist.clear();
        pthread_mutex_unlock(&m_roilock);
    }
    pthread_mutex_destroy(&m_roilock);
}

void* venc_dev::async_venc_message_thread (void *input)
{
    struct venc_msg venc_msg;
    omx_video* omx_venc_base = NULL;
    omx_venc *omx = reinterpret_cast<omx_venc*>(input);
    omx_venc_base = reinterpret_cast<omx_video*>(input);
    OMX_BUFFERHEADERTYPE* omxhdr = NULL;

    prctl(PR_SET_NAME, (unsigned long)"VideoEncCallBackThread", 0, 0, 0);
    struct v4l2_plane plane[VIDEO_MAX_PLANES];
    struct pollfd pfds[2];
    struct v4l2_buffer v4l2_buf;
    struct v4l2_event dqevent;
    struct statistics stats;
    pfds[0].events = POLLIN | POLLRDNORM | POLLOUT | POLLWRNORM | POLLRDBAND | POLLPRI;
    pfds[1].events = POLLIN | POLLERR;
    pfds[0].fd = omx->handle->m_nDriver_fd;
    pfds[1].fd = omx->handle->m_poll_efd;
    int error_code = 0,rc=0;

    memset(&stats, 0, sizeof(statistics));
    memset(&v4l2_buf, 0, sizeof(v4l2_buf));

    while (!omx->handle->async_thread_force_stop) {
        pthread_mutex_lock(&omx->handle->pause_resume_mlock);

        if (omx->handle->paused) {
            venc_msg.msgcode = VEN_MSG_PAUSE;
            venc_msg.statuscode = VEN_S_SUCCESS;

            if (omx->async_message_process(input, &venc_msg) < 0) {
                DEBUG_PRINT_ERROR("ERROR: Failed to process pause msg");
                pthread_mutex_unlock(&omx->handle->pause_resume_mlock);
                break;
            }

            /* Block here until the IL client resumes us again */
            pthread_cond_wait(&omx->handle->pause_resume_cond,
                    &omx->handle->pause_resume_mlock);

            venc_msg.msgcode = VEN_MSG_RESUME;
            venc_msg.statuscode = VEN_S_SUCCESS;

            if (omx->async_message_process(input, &venc_msg) < 0) {
                DEBUG_PRINT_ERROR("ERROR: Failed to process resume msg");
                pthread_mutex_unlock(&omx->handle->pause_resume_mlock);
                break;
            }
            memset(&stats, 0, sizeof(statistics));
        }

        pthread_mutex_unlock(&omx->handle->pause_resume_mlock);

        rc = poll(pfds, 2, POLL_TIMEOUT);

        if (!rc) {
            DEBUG_PRINT_HIGH("Poll timedout, pipeline stalled due to client/firmware ETB: %d, EBD: %d, FTB: %d, FBD: %d",
                    omx->handle->etb, omx->handle->ebd, omx->handle->ftb, omx->handle->fbd);
            continue;
        } else if (rc < 0 && errno != EINTR && errno != EAGAIN) {
            DEBUG_PRINT_ERROR("Error while polling: %d, errno = %d", rc, errno);
            break;
        }

        if ((pfds[1].revents & POLLIN) || (pfds[1].revents & POLLERR)) {
            DEBUG_PRINT_ERROR("async_venc_message_thread interrupted to be exited");
            break;
        }

        if ((pfds[0].revents & POLLIN) || (pfds[0].revents & POLLRDNORM)) {
            v4l2_buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
            v4l2_buf.memory = V4L2_MEMORY_USERPTR;
            v4l2_buf.length = omx->handle->num_output_planes;
            v4l2_buf.m.planes = plane;

            while (!ioctl(pfds[0].fd, VIDIOC_DQBUF, &v4l2_buf)) {
                venc_msg.msgcode=VEN_MSG_OUTPUT_BUFFER_DONE;
                venc_msg.statuscode=VEN_S_SUCCESS;
                omxhdr=omx_venc_base->m_out_mem_ptr+v4l2_buf.index;
                venc_msg.buf.len= v4l2_buf.m.planes->bytesused;
                venc_msg.buf.offset = v4l2_buf.m.planes->data_offset;
                venc_msg.buf.flags = 0;
                venc_msg.buf.ptrbuffer = (OMX_U8 *)omx_venc_base->m_pOutput_pmem[v4l2_buf.index].buffer;
                venc_msg.buf.clientdata=(void*)omxhdr;
                venc_msg.buf.timestamp = (uint64_t) v4l2_buf.timestamp.tv_sec * (uint64_t) 1000000 + (uint64_t) v4l2_buf.timestamp.tv_usec;

                /* TODO: ideally report other types of frames as well
                 * for now it doesn't look like IL client cares about
                 * other types
                 */
                if (v4l2_buf.flags & V4L2_QCOM_BUF_FLAG_IDRFRAME)
                    venc_msg.buf.flags |= QOMX_VIDEO_PictureTypeIDR;

                if (v4l2_buf.flags & V4L2_BUF_FLAG_KEYFRAME)
                    venc_msg.buf.flags |= OMX_BUFFERFLAG_SYNCFRAME;

                if (v4l2_buf.flags & V4L2_BUF_FLAG_PFRAME) {
                    venc_msg.buf.flags |= OMX_VIDEO_PictureTypeP;
                } else if (v4l2_buf.flags & V4L2_BUF_FLAG_BFRAME) {
                    venc_msg.buf.flags |= OMX_VIDEO_PictureTypeB;
                }

                if (v4l2_buf.flags & V4L2_QCOM_BUF_FLAG_CODECCONFIG)
                    venc_msg.buf.flags |= OMX_BUFFERFLAG_CODECCONFIG;

                if (v4l2_buf.flags & V4L2_QCOM_BUF_FLAG_EOS)
                    venc_msg.buf.flags |= OMX_BUFFERFLAG_EOS;

                if (omx->handle->num_output_planes > 1 && v4l2_buf.m.planes->bytesused)
                    venc_msg.buf.flags |= OMX_BUFFERFLAG_EXTRADATA;

                if (omxhdr->nFilledLen)
                    venc_msg.buf.flags |= OMX_BUFFERFLAG_ENDOFFRAME;

                omx->handle->fbd++;
                stats.bytes_generated += venc_msg.buf.len;

                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }
            }
        }

        if ((pfds[0].revents & POLLOUT) || (pfds[0].revents & POLLWRNORM)) {
            v4l2_buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
            v4l2_buf.memory = V4L2_MEMORY_USERPTR;
            v4l2_buf.m.planes = plane;
            v4l2_buf.length = omx->handle->num_input_planes;

            while (!ioctl(pfds[0].fd, VIDIOC_DQBUF, &v4l2_buf)) {
                venc_msg.msgcode=VEN_MSG_INPUT_BUFFER_DONE;
                venc_msg.statuscode=VEN_S_SUCCESS;
                omx->handle->ebd++;

                if (omx->handle->mBatchSize) {
                    int bufIndex = omx->handle->mBatchInfo.retrieveBufferAt(v4l2_buf.index);
                    if (bufIndex < 0) {
                        DEBUG_PRINT_ERROR("Retrieved invalid buffer %d", v4l2_buf.index);
                        break;
                    }
                    if (omx->handle->mBatchInfo.isPending(bufIndex)) {
                        DEBUG_PRINT_LOW(" EBD for %d [v4l2-id=%d].. batch still pending",
                                bufIndex, v4l2_buf.index);
                        //do not return to client yet
                        continue;
                    }
                    v4l2_buf.index = bufIndex;
                }
                if (omx_venc_base->mUseProxyColorFormat && !omx_venc_base->mUsesColorConversion)
                    omxhdr = &omx_venc_base->meta_buffer_hdr[v4l2_buf.index];
                else
                    omxhdr = &omx_venc_base->m_inp_mem_ptr[v4l2_buf.index];

                venc_msg.buf.clientdata=(void*)omxhdr;

                DEBUG_PRINT_LOW("sending EBD %p [id=%d]", omxhdr, v4l2_buf.index);
                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }
            }
        }

        if (pfds[0].revents & POLLPRI) {
            rc = ioctl(pfds[0].fd, VIDIOC_DQEVENT, &dqevent);

            if (dqevent.type == V4L2_EVENT_MSM_VIDC_FLUSH_DONE) {
                venc_msg.msgcode = VEN_MSG_FLUSH_INPUT_DONE;
                venc_msg.statuscode = VEN_S_SUCCESS;

                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }

                venc_msg.msgcode = VEN_MSG_FLUSH_OUPUT_DONE;
                venc_msg.statuscode = VEN_S_SUCCESS;

                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }
            } else if (dqevent.type == V4L2_EVENT_MSM_VIDC_HW_OVERLOAD) {
                DEBUG_PRINT_ERROR("HW Overload received");
                venc_msg.statuscode = VEN_S_EFAIL;
                venc_msg.msgcode = VEN_MSG_HW_OVERLOAD;

                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }
            } else if (dqevent.type == V4L2_EVENT_MSM_VIDC_SYS_ERROR){
                DEBUG_PRINT_ERROR("ERROR: Encoder is in bad state");
                venc_msg.msgcode = VEN_MSG_INDICATION;
                venc_msg.statuscode=VEN_S_EFAIL;

                if (omx->async_message_process(input,&venc_msg) < 0) {
                    DEBUG_PRINT_ERROR("ERROR: Wrong ioctl message");
                    break;
                }
            }
        }

        /* calc avg. fps, bitrate */
        struct timeval tv;
        gettimeofday(&tv,NULL);
        OMX_U64 time_diff = (OMX_U32)((tv.tv_sec * 1000000 + tv.tv_usec) -
                (stats.prev_tv.tv_sec * 1000000 + stats.prev_tv.tv_usec));
        if (time_diff >= 5000000) {
            OMX_U32 num_fbd = omx->handle->fbd - stats.prev_fbd;
            if (stats.prev_tv.tv_sec && num_fbd && time_diff) {
                float framerate = num_fbd * 1000000/(float)time_diff;
                OMX_U32 bitrate = (stats.bytes_generated * 8 / num_fbd) * framerate;
                DEBUG_PRINT_HIGH("stats: avg. fps %0.2f, bitrate %d",
                    framerate, bitrate);
            }
            stats.prev_tv = tv;
            stats.bytes_generated = 0;
            stats.prev_fbd = omx->handle->fbd;
        }

    }

    DEBUG_PRINT_HIGH("omx_venc: Async Thread exit");
    return NULL;
}

static const int event_type[] = {
    V4L2_EVENT_MSM_VIDC_FLUSH_DONE,
    V4L2_EVENT_MSM_VIDC_SYS_ERROR
};

static OMX_ERRORTYPE subscribe_to_events(int fd)
{
    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    struct v4l2_event_subscription sub;
    int array_sz = sizeof(event_type)/sizeof(int);
    int i,rc;
    memset(&sub, 0, sizeof(sub));

    if (fd < 0) {
       DEBUG_PRINT_ERROR("Invalid input: %d", fd);
        return OMX_ErrorBadParameter;
    }

    for (i = 0; i < array_sz; ++i) {
        memset(&sub, 0, sizeof(sub));
        sub.type = event_type[i];
        rc = ioctl(fd, VIDIOC_SUBSCRIBE_EVENT, &sub);

        if (rc) {
           DEBUG_PRINT_ERROR("Failed to subscribe event: 0x%x", sub.type);
            break;
        }
    }

    if (i < array_sz) {
        for (--i; i >=0 ; i--) {
            memset(&sub, 0, sizeof(sub));
            sub.type = event_type[i];
            rc = ioctl(fd, VIDIOC_UNSUBSCRIBE_EVENT, &sub);

            if (rc)
               DEBUG_PRINT_ERROR("Failed to unsubscribe event: 0x%x", sub.type);
        }

        eRet = OMX_ErrorNotImplemented;
    }

    return eRet;
}

bool inline venc_dev::venc_query_cap(struct v4l2_queryctrl &cap) {

    if (ioctl(m_nDriver_fd, VIDIOC_QUERYCTRL, &cap)) {
        DEBUG_PRINT_ERROR("Query caps for id = %u failed", cap.id);
        return false;
    }
    return true;
}

bool inline venc_dev::venc_validate_range(OMX_S32 id, OMX_S32 val) {

    struct v4l2_queryctrl cap;
    memset(&cap, 0, sizeof(struct v4l2_queryctrl));

    cap.id = id;
    if (venc_query_cap(cap)) {
        if (val >= cap.minimum && val <= cap.maximum) {
            return true;
        } else {
            DEBUG_PRINT_ERROR("id = %u, value = %u, min = %u, max = %u",
                cap.id, val, cap.minimum, cap.maximum);
        }
    }
    return false;
}

void venc_dev::get_roi_for_timestamp(struct roidata &roi, OMX_TICKS timestamp)
{
    std::list<roidata>::iterator iter;
    bool found = false;

    memset(&roi, 0, sizeof(struct roidata));
    roi.dirty = false;

    /*
     * look for the roi data which has timestamp nearest and
     * lower than the etb timestamp, we should not take the
     * roi data which has the timestamp greater than etb timestamp.
     */
    pthread_mutex_lock(&m_roilock);
    iter = m_roilist.begin();
    while (iter != m_roilist.end()) {
        if (iter->timestamp <= timestamp) {
            if (found) {
                /* we found roidata in previous iteration already and got another
                 * roidata in this iteration, so we will use this iteration's
                 * roidata and free the previous roidata which is no longer used.
                 */
                DEBUG_PRINT_LOW("freeing unused roidata with timestamp %lld us", roi.timestamp);
                free(roi.info.pRoiMBInfo);
            }
            found = true;
            roi = *iter;
            /* we got roidata so erase the elment in the roi list.
             * after list erase iterator will point to next element
             * so we don't need to increment iter after erase.
             */
            iter = m_roilist.erase(iter);
        } else {
            iter++;
        }
    }
    if (found) {
        DEBUG_PRINT_LOW("found roidata with timestamp %lld us", roi.timestamp);
    }
    pthread_mutex_unlock(&m_roilock);
}

int venc_dev::append_mbi_extradata(void *dst, struct msm_vidc_extradata_header* src)
{
    OMX_QCOM_EXTRADATA_MBINFO *mbi = (OMX_QCOM_EXTRADATA_MBINFO *)dst;

    if (!dst || !src)
        return 0;

    /* TODO: Once Venus 3XX target names are known, nFormat should 2 for those
     * targets, since the payload format will be different */
    mbi->nFormat = 2;
    mbi->nDataSize = src->data_size;
    memcpy(&mbi->data, &src->data, src->data_size);

    return mbi->nDataSize + sizeof(*mbi);
}

inline int get_yuv_size(unsigned long fmt, int width, int height) {
    unsigned int y_stride, uv_stride, y_sclines,
                uv_sclines, y_plane, uv_plane;
    unsigned int y_ubwc_plane = 0, uv_ubwc_plane = 0;
    unsigned size = 0;

    y_stride = VENUS_Y_STRIDE(fmt, width);
    uv_stride = VENUS_UV_STRIDE(fmt, width);
    y_sclines = VENUS_Y_SCANLINES(fmt, height);
    uv_sclines = VENUS_UV_SCANLINES(fmt, height);

    switch (fmt) {
        case COLOR_FMT_NV12:
            y_plane = y_stride * y_sclines;
            uv_plane = uv_stride * uv_sclines;
            size = MSM_MEDIA_ALIGN(y_plane + uv_plane, 4096);
            break;
         default:
            break;
    }
    return size;
}

void venc_dev::append_extradata_mbidata(OMX_OTHER_EXTRADATATYPE *p_extra,
            struct msm_vidc_extradata_header *p_extradata)
{
    OMX_U32 payloadSize = append_mbi_extradata(&p_extra->data, p_extradata);
    p_extra->nSize = ALIGN(sizeof(OMX_OTHER_EXTRADATATYPE) + payloadSize, 4);
    p_extra->nVersion.nVersion = OMX_SPEC_VERSION;
    p_extra->nPortIndex = OMX_DirOutput;
    p_extra->eType = (OMX_EXTRADATATYPE)OMX_ExtraDataVideoEncoderMBInfo;
    p_extra->nDataSize = payloadSize;

}

void venc_dev::append_extradata_ltrinfo(OMX_OTHER_EXTRADATATYPE *p_extra,
            struct msm_vidc_extradata_header *p_extradata)
{
    p_extra->nSize = ALIGN(sizeof(OMX_OTHER_EXTRADATATYPE) + p_extradata->data_size, 4);
    p_extra->nVersion.nVersion = OMX_SPEC_VERSION;
    p_extra->nPortIndex = OMX_DirOutput;
    p_extra->eType = (OMX_EXTRADATATYPE) OMX_ExtraDataVideoLTRInfo;
    p_extra->nDataSize = p_extradata->data_size;
    memcpy(p_extra->data, p_extradata->data, p_extradata->data_size);
}

void venc_dev::append_extradata_none(OMX_OTHER_EXTRADATATYPE *p_extra)
{
    p_extra->nSize = ALIGN(sizeof(OMX_OTHER_EXTRADATATYPE), 4);
    p_extra->nVersion.nVersion = OMX_SPEC_VERSION;
    p_extra->nPortIndex = OMX_DirOutput;
    p_extra->eType = OMX_ExtraDataNone;
    p_extra->nDataSize = 0;
}

bool venc_dev::handle_input_extradata(struct v4l2_buffer buf)
{
    OMX_OTHER_EXTRADATATYPE *p_extra = NULL;
    unsigned int consumed_len = 0, filled_len = 0;
    unsigned int yuv_size = 0, index = 0;
    int enable = 0, i = 0, size = 0;
    unsigned char *pVirt = NULL;
    int height = m_sVenc_cfg.input_height;
    int width = m_sVenc_cfg.input_width;
    OMX_TICKS nTimeStamp = buf.timestamp.tv_sec * 1000000 + buf.timestamp.tv_usec;
    int fd = buf.m.planes[0].reserved[0];
    bool vqzip_sei_found = false;

    if (!EXTRADATA_IDX(num_input_planes)) {
        DEBUG_PRINT_LOW("Input extradata not enabled");
        return true;
    }

    if (!input_extradata_info.uaddr) {
        DEBUG_PRINT_ERROR("Extradata buffers not allocated\n");
        return true;
    }

    DEBUG_PRINT_HIGH("Processing Extradata for Buffer = %lld", nTimeStamp); // Useful for debugging

    if (m_sVenc_cfg.inputformat == V4L2_PIX_FMT_NV12 || m_sVenc_cfg.inputformat == V4L2_PIX_FMT_NV21) {
        size = VENUS_BUFFER_SIZE(COLOR_FMT_NV12, width, height);
        yuv_size = get_yuv_size(COLOR_FMT_NV12, width, height);
        pVirt = (unsigned char *)mmap(NULL, size, PROT_READ|PROT_WRITE,MAP_SHARED, fd, 0);
        if (pVirt == MAP_FAILED) {
            DEBUG_PRINT_ERROR("%s Failed to mmap",__func__);
            return false;
        }
        p_extra = (OMX_OTHER_EXTRADATATYPE *) ((unsigned long)(pVirt + yuv_size + 3)&(~3));
    }

    index = venc_get_index_from_fd(input_extradata_info.m_ion_dev,fd);
    char *p_extradata = input_extradata_info.uaddr + index * input_extradata_info.buffer_size;
    OMX_OTHER_EXTRADATATYPE *data = (struct OMX_OTHER_EXTRADATATYPE *)p_extradata;
    memset((void *)(data), 0, (input_extradata_info.buffer_size)); // clear stale data in current buffer

    while (p_extra && (consumed_len + sizeof(OMX_OTHER_EXTRADATATYPE)) <= (size - yuv_size)
        && (consumed_len + p_extra->nSize) <= (size - yuv_size)
        && (filled_len + sizeof(OMX_OTHER_EXTRADATATYPE) <= input_extradata_info.buffer_size)
        && (filled_len + p_extra->nSize <= input_extradata_info.buffer_size)
        && (p_extra->eType != (OMX_EXTRADATATYPE)MSM_VIDC_EXTRADATA_NONE)) {

        DEBUG_PRINT_LOW("Extradata Type = 0x%x", (OMX_QCOM_EXTRADATATYPE)p_extra->eType);
        switch ((OMX_QCOM_EXTRADATATYPE)p_extra->eType) {
        case OMX_ExtraDataFrameDimension:
        {
            struct msm_vidc_extradata_index *payload;
            OMX_QCOM_EXTRADATA_FRAMEDIMENSION *framedimension_format;
            data->nSize = (sizeof(OMX_OTHER_EXTRADATATYPE) + sizeof(struct msm_vidc_extradata_index) + 3)&(~3);
            data->nVersion.nVersion = OMX_SPEC_VERSION;
            data->nPortIndex = 0;
            data->eType = (OMX_EXTRADATATYPE)MSM_VIDC_EXTRADATA_INDEX;
            data->nDataSize = sizeof(struct msm_vidc_input_crop_payload);
            framedimension_format = (OMX_QCOM_EXTRADATA_FRAMEDIMENSION *)p_extra->data;
            payload = (struct msm_vidc_extradata_index *)(data->data);
            payload->type = (msm_vidc_extradata_type)MSM_VIDC_EXTRADATA_INPUT_CROP;
            payload->input_crop.left = framedimension_format->nDecWidth;
            payload->input_crop.top = framedimension_format->nDecHeight;
            payload->input_crop.width = framedimension_format->nActualWidth;
            payload->input_crop.height = framedimension_format->nActualHeight;
            DEBUG_PRINT_LOW("Height = %d Width = %d Actual Height = %d Actual Width = %d",
                framedimension_format->nDecWidth, framedimension_format->nDecHeight,
                framedimension_format->nActualWidth, framedimension_format->nActualHeight);
            filled_len += data->nSize;
            data = (OMX_OTHER_EXTRADATATYPE *)((char *)data + data->nSize);
            break;
        }
        case OMX_ExtraDataQP:
        {
            OMX_QCOM_EXTRADATA_QP * qp_payload = NULL;
            struct msm_vidc_frame_qp_payload *payload;
            data->nSize = (sizeof(OMX_OTHER_EXTRADATATYPE) + sizeof(struct msm_vidc_frame_qp_payload) + 3)&(~3);
            data->nVersion.nVersion = OMX_SPEC_VERSION;
            data->nPortIndex = 0;
            data->eType = (OMX_EXTRADATATYPE)MSM_VIDC_EXTRADATA_FRAME_QP;
            data->nDataSize = sizeof(struct  msm_vidc_frame_qp_payload);
            qp_payload = (OMX_QCOM_EXTRADATA_QP *)p_extra->data;
            payload = (struct  msm_vidc_frame_qp_payload *)(data->data);
            payload->frame_qp = qp_payload->nQP;
            DEBUG_PRINT_LOW("Frame QP = %d", payload->frame_qp);
            filled_len += data->nSize;
            data = (OMX_OTHER_EXTRADATATYPE *)((char *)data + data->nSize);
            break;
        }
        case OMX_ExtraDataVQZipSEI:
            DEBUG_PRINT_LOW("VQZIP SEI Found ");
            input_extradata_info.vqzip_sei_found = true;
            break;
        case OMX_ExtraDataFrameInfo:
        {
            OMX_QCOM_EXTRADATA_FRAMEINFO *frame_info = NULL;
            frame_info = (OMX_QCOM_EXTRADATA_FRAMEINFO *)(p_extra->data);
            if (frame_info->ePicType == OMX_VIDEO_PictureTypeI) {
                if (venc_set_intra_vop_refresh((OMX_BOOL)true) == false)
                    DEBUG_PRINT_ERROR("%s Error in requesting I Frame ", __func__);
            }
            break;
        }
        default:
            DEBUG_PRINT_HIGH("Unknown Extradata 0x%x", (OMX_QCOM_EXTRADATATYPE)p_extra->eType);
            break;
        }

        consumed_len += p_extra->nSize;
        p_extra = (OMX_OTHER_EXTRADATATYPE *)((char *)p_extra + p_extra->nSize);
    }

      /*
       * Below code is based on these points.
       * 1) As _PQ_ not defined in Napali :
       *     a) Send data to Venus as ROI.
       *     b) ROI enabled : Processed under unlocked context.
       *     c) ROI disabled : Nothing to fill.
       *     d) pq enabled : Not possible.
       * 2) Normal ROI handling.
       *     By this time if client sets next ROI, then we shouldn't process new ROI here.
       */

    struct roidata roi;
    memset(&roi, 0, sizeof(struct roidata));
    roi.dirty = false;
    if (m_roi_enabled) {
        get_roi_for_timestamp(roi, nTimeStamp);
    }

    if (roi.dirty) {
        data->nSize = ALIGN(sizeof(OMX_OTHER_EXTRADATATYPE) +
            sizeof(struct msm_vidc_roi_qp_payload) +
            roi.info.nRoiMBInfoSize - 2 * sizeof(unsigned int), 4);
        if (data->nSize > input_extradata_info.buffer_size  - consumed_len) {
           DEBUG_PRINT_ERROR("Buffer size (%lu) is less than ROI extradata size (%u)",
                             (input_extradata_info.buffer_size - consumed_len) ,data->nSize);
           return false;
        }
        data->nVersion.nVersion = OMX_SPEC_VERSION;
        data->nPortIndex = 0;
        data->eType = (OMX_EXTRADATATYPE)MSM_VIDC_EXTRADATA_ROI_QP;
        data->nDataSize = sizeof(struct msm_vidc_roi_qp_payload);
        struct msm_vidc_roi_qp_payload *roiData =
                (struct msm_vidc_roi_qp_payload *)(data->data);
        roiData->upper_qp_offset = roi.info.nUpperQpOffset;
        roiData->lower_qp_offset = roi.info.nLowerQpOffset;
        roiData->b_roi_info = roi.info.bUseRoiInfo;
        roiData->mbi_info_size = roi.info.nRoiMBInfoSize;
        DEBUG_PRINT_HIGH("Using ROI QP map: Enable = %d", roiData->b_roi_info);
        memcpy(roiData->data, roi.info.pRoiMBInfo, roi.info.nRoiMBInfoSize);
        data = (OMX_OTHER_EXTRADATATYPE *)((char *)data + data->nSize);
        consumed_len += data->nSize;
    }

    if (m_roi_enabled) {
        if (roi.dirty) {
            DEBUG_PRINT_LOW("free roidata with timestamp %lld us", roi.timestamp);
            free(roi.info.pRoiMBInfo);
            roi.dirty = false;
        }
    }

#ifdef _VQZIP_
    if (vqzip_sei_info.enabled && !input_extradata_info.vqzip_sei_found) {
        DEBUG_PRINT_ERROR("VQZIP is enabled, But no VQZIP SEI found. Rejecting the session");
        if (pVirt)
            munmap(pVirt, size);
        return false; //This should be treated as fatal error
    }
    if (vqzip_sei_info.enabled && pVirt) {
        data->nSize = (sizeof(OMX_OTHER_EXTRADATATYPE) +  sizeof(struct VQZipStats) + 3)&(~3);
        data->nVersion.nVersion = OMX_SPEC_VERSION;
        data->nPortIndex = 0;
        data->eType = (OMX_EXTRADATATYPE)MSM_VIDC_EXTRADATA_YUVSTATS_INFO;
        data->nDataSize = sizeof(struct VQZipStats);
        vqzip.fill_stats_data((void*)pVirt, (void*) data->data);
        data = (OMX_OTHER_EXTRADATATYPE *)((char *)data + data->nSize);
    }
#endif
        data->nSize = sizeof(OMX_OTHER_EXTRADATATYPE);
        data->nVersion.nVersion = OMX_SPEC_VERSION;
        data->eType = OMX_ExtraDataNone;
        data->nDataSize = 0;
        data->data[0] = 0;

    if (pVirt)
        munmap(pVirt, size);

    return true;
}

bool venc_dev::handle_output_extradata(void *buffer, int index)
{
    OMX_BUFFERHEADERTYPE *p_bufhdr = (OMX_BUFFERHEADERTYPE *) buffer;
    OMX_OTHER_EXTRADATATYPE *p_extra = NULL;
    OMX_OTHER_EXTRADATATYPE *p_clientextra = NULL;

    if(venc_handle->m_client_output_extradata_mem_ptr && venc_handle->m_sExtraData
        && venc_handle->m_client_out_extradata_info.getSize() >=
        output_extradata_info.buffer_size) {
        p_clientextra = (OMX_OTHER_EXTRADATATYPE * )
            ((venc_handle->m_client_output_extradata_mem_ptr + index) ->pBuffer);
    }
    if (p_clientextra == NULL) {
        DEBUG_PRINT_ERROR("Client Extradata buffers not allocated\n");
    }

    if (!output_extradata_info.uaddr) {
        DEBUG_PRINT_ERROR("Extradata buffers not allocated\n");
        return false;
    }

    p_extra = (OMX_OTHER_EXTRADATATYPE *)ALIGN(p_bufhdr->pBuffer +
                p_bufhdr->nOffset + p_bufhdr->nFilledLen, 4);

    if (output_extradata_info.buffer_size >
            p_bufhdr->nAllocLen - ALIGN(p_bufhdr->nOffset + p_bufhdr->nFilledLen, 4)) {
        DEBUG_PRINT_ERROR("Insufficient buffer size for extradata");
        p_extra = NULL;
        return false;
    } else if (sizeof(msm_vidc_extradata_header) != sizeof(OMX_OTHER_EXTRADATATYPE)) {
        /* A lot of the code below assumes this condition, so error out if it's not met */
        DEBUG_PRINT_ERROR("Extradata ABI mismatch");
        return false;
    }

    struct msm_vidc_extradata_header *p_extradata = NULL;
    do {
        p_extradata = (struct msm_vidc_extradata_header *) (p_extradata ?
            ((char *)p_extradata) + p_extradata->size :
            output_extradata_info.uaddr + index * output_extradata_info.buffer_size);

        switch (p_extradata->type) {
            case MSM_VIDC_EXTRADATA_METADATA_MBI:
            {
                append_extradata_mbidata(p_extra, p_extradata);
                if(p_clientextra) {
                     append_extradata_mbidata(p_clientextra, p_extradata);
                }
                DEBUG_PRINT_LOW("MBI Extradata = 0x%x", *((OMX_U32 *)p_extra->data));
                break;
            }
            case MSM_VIDC_EXTRADATA_METADATA_LTR:
            {
                append_extradata_ltrinfo(p_extra, p_extradata);
                if(p_clientextra) {
                    append_extradata_ltrinfo(p_clientextra, p_extradata);
                }
                DEBUG_PRINT_LOW("LTRInfo Extradata = 0x%x", *((OMX_U32 *)p_extra->data));
                break;
            }
            case MSM_VIDC_EXTRADATA_NONE:
                append_extradata_none(p_extra);
                if(p_clientextra) {
                    append_extradata_none(p_clientextra);
                }
                break;
            default:
                /* No idea what this stuff is, just skip over it */
                DEBUG_PRINT_HIGH("Found an unrecognised extradata (%x) ignoring it",
                        p_extradata->type);
                continue;
        }

        p_extra = (OMX_OTHER_EXTRADATATYPE *)(((char *)p_extra) + p_extra->nSize);
        if(p_clientextra) {
            p_clientextra = (OMX_OTHER_EXTRADATATYPE *)(((char *)p_clientextra) + p_clientextra->nSize);
        }
    } while (p_extradata->type != MSM_VIDC_EXTRADATA_NONE);

    /* Just for debugging: Traverse the list of extra datas  and spit it out onto log */
    p_extra = (OMX_OTHER_EXTRADATATYPE *)ALIGN(p_bufhdr->pBuffer +
                p_bufhdr->nOffset + p_bufhdr->nFilledLen, 4);
    while(p_extra->eType != OMX_ExtraDataNone)
    {
        DEBUG_PRINT_LOW("[%p/%u] found extradata type %x of size %u (%u) at %p",
                p_bufhdr->pBuffer, (unsigned int)p_bufhdr->nFilledLen, p_extra->eType,
                (unsigned int)p_extra->nSize, (unsigned int)p_extra->nDataSize, p_extra);

        p_extra = (OMX_OTHER_EXTRADATATYPE *) (((OMX_U8 *) p_extra) +
                p_extra->nSize);
    }

    return true;
}

int venc_dev::venc_set_format(int format)
{
    int rc = true;

    if (format) {
        color_format = format;

        switch (color_format) {
        case NV12_128m:
            return venc_set_color_format((OMX_COLOR_FORMATTYPE)QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m);
        case NV12_UBWC:
            return venc_set_color_format((OMX_COLOR_FORMATTYPE)QOMX_COLOR_FORMATYUV420PackedSemiPlanar32mCompressed);
        default:
            return false;
        }

    } else {
        color_format = 0;
        rc = false;
    }

    return rc;
}

OMX_ERRORTYPE venc_dev::venc_get_supported_profile_level(OMX_VIDEO_PARAM_PROFILELEVELTYPE *profileLevelType)
{
    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    struct v4l2_queryctrl profile_cap, level_cap;
    int v4l2_profile;
    int avc_profiles[5] = { QOMX_VIDEO_AVCProfileConstrainedBaseline,
                            QOMX_VIDEO_AVCProfileBaseline,
                            QOMX_VIDEO_AVCProfileMain,
                            QOMX_VIDEO_AVCProfileConstrainedHigh,
                            QOMX_VIDEO_AVCProfileHigh };
    int hevc_profiles[3] = { OMX_VIDEO_HEVCProfileMain,
                             OMX_VIDEO_HEVCProfileMain10HDR10,
                             OMX_VIDEO_HEVCProfileMainStill };

    if (!profileLevelType)
        return OMX_ErrorBadParameter;

    memset(&level_cap, 0, sizeof(struct v4l2_queryctrl));
    memset(&profile_cap, 0, sizeof(struct v4l2_queryctrl));

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        level_cap.id = V4L2_CID_MPEG_VIDEO_H264_LEVEL;
        profile_cap.id = V4L2_CID_MPEG_VIDEO_H264_PROFILE;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        level_cap.id = V4L2_CID_MPEG_VIDC_VIDEO_VP8_PROFILE_LEVEL;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        level_cap.id = V4L2_CID_MPEG_VIDC_VIDEO_HEVC_TIER_LEVEL;
        profile_cap.id = V4L2_CID_MPEG_VIDC_VIDEO_HEVC_PROFILE;
    } else {
        DEBUG_PRINT_ERROR("get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported Invalid codec");
        return OMX_ErrorInvalidComponent;
    }

    if (profile_cap.id) {
        if(!venc_query_cap(profile_cap)) {
            DEBUG_PRINT_ERROR("Getting capabilities for profile failed");
            return OMX_ErrorHardware;
        }
    }

    if (level_cap.id) {
        if(!venc_query_cap(level_cap)) {
            DEBUG_PRINT_ERROR("Getting capabilities for level failed");
            return OMX_ErrorHardware;
        }
    }

    /* Get the corresponding omx level from v4l2 level */
    if (!profile_level_converter::convert_v4l2_level_to_omx(m_sVenc_cfg.codectype, level_cap.maximum, (int *)&profileLevelType->eLevel)) {
        DEBUG_PRINT_ERROR("Invalid level, cannot find corresponding v4l2 level : %d ", level_cap.maximum);
        return OMX_ErrorHardware;
    }

    /* For given profile index get corresponding profile that needs to be supported */
    if (profileLevelType->nPortIndex != 1) {
        DEBUG_PRINT_ERROR("get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported should be queried on output port only %u",
                            (unsigned int)profileLevelType->nPortIndex);
        return OMX_ErrorBadPortIndex;
    }

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        if (profileLevelType->nProfileIndex < (sizeof(avc_profiles)/sizeof(int))) {
            profileLevelType->eProfile = avc_profiles[profileLevelType->nProfileIndex];
        } else {
            DEBUG_PRINT_LOW("AVC: get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported nProfileIndex ret NoMore %u",
                    (unsigned int)profileLevelType->nProfileIndex);
            return OMX_ErrorNoMore;
        }
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        if (profileLevelType->nProfileIndex == 0) {
            profileLevelType->eProfile = OMX_VIDEO_VP8ProfileMain;
        } else {
            DEBUG_PRINT_LOW("VP8: get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported nProfileIndex ret NoMore %u",
                    (unsigned int)profileLevelType->nProfileIndex);
            return OMX_ErrorNoMore;
        }
        /* Driver has no notion of VP8 profile and there is only one profile supported. Hence return here */
        return OMX_ErrorNone;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        if (profileLevelType->nProfileIndex < (sizeof(hevc_profiles)/sizeof(int))) {
            profileLevelType->eProfile =  hevc_profiles[profileLevelType->nProfileIndex];
        } else {
            DEBUG_PRINT_LOW("HEVC: get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported nProfileIndex ret NoMore %u",
                    (unsigned int)profileLevelType->nProfileIndex);
            return OMX_ErrorNoMore;
        }
    }

    /* Check if the profile is supported by driver or not  */
    /* During query caps of profile driver sends a mask of */
    /* of all v4l2 profiles supported(in the flags field)  */
    if (!profile_level_converter::convert_omx_profile_to_v4l2(m_sVenc_cfg.codectype, profileLevelType->eProfile, &v4l2_profile)) {
        DEBUG_PRINT_ERROR("Invalid profile, cannot find corresponding omx profile");
        return OMX_ErrorHardware;
    }

    DEBUG_PRINT_INFO("v4l2 profile : %d flags : %d ", v4l2_profile, profile_cap.flags);
    if(!((profile_cap.flags >> v4l2_profile) & 0x1)) {
        DEBUG_PRINT_ERROR("%s: Invalid index corresponding profile not supported : %d ",__FUNCTION__, profileLevelType->eProfile);
        eRet = OMX_ErrorNoMore;
    }

    DEBUG_PRINT_LOW("get_parameter: OMX_IndexParamVideoProfileLevelQuerySupported for Input port returned Profile:%u, Level:%u",
            (unsigned int)profileLevelType->eProfile, (unsigned int)profileLevelType->eLevel);
    return eRet;
}

bool venc_dev::venc_get_supported_color_format(unsigned index, OMX_U32 *colorFormat) {
#ifdef _UBWC_
    //we support following formats
    //index 0 - Compressed (UBWC) Venus flavour of YUV420SP
    //index 1 - Venus flavour of YUV420SP
    //index 2 - Compressed (UBWC) TP10 (10bit packed)
    //index 3 - Compressed (UBWC) Venus flavour of RGBA8888
    //index 4 - Venus flavour of RGBA8888
    //index 5 - opaque which internally maps to YUV420SP.
    //index 6 - vannilla YUV420SP
    //this can be extended in the future
    int supportedFormats[] = {
        [0] = QOMX_COLOR_FORMATYUV420PackedSemiPlanar32mCompressed,
        [1] = QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m,
        [2] = QOMX_COLOR_FormatYVU420SemiPlanar,
        [3] = QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m10bitCompressed,
        [4] = QOMX_COLOR_Format32bitRGBA8888Compressed,
        [5] = QOMX_COLOR_Format32bitRGBA8888,
        [6] = QOMX_COLOR_FormatAndroidOpaque,
        [7] = OMX_COLOR_FormatYUV420SemiPlanar,
    };
#else
    //we support two formats
    //index 0 - Venus flavour of YUV420SP
    //index 1 - opaque which internally maps to YUV420SP.
    //index 2 - vannilla YUV420SP
    //this can be extended in the future
    int supportedFormats[] = {
        [0] = QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m,
        [1] = QOMX_COLOR_FormatYVU420SemiPlanar,
        [2] = QOMX_COLOR_FormatAndroidOpaque,
        [3] = OMX_COLOR_FormatYUV420SemiPlanar,
    };
#endif
    if (index > (sizeof(supportedFormats)/sizeof(*supportedFormats) - 1))
        return false;
    *colorFormat = supportedFormats[index];
    return true;
}

OMX_ERRORTYPE venc_dev::allocate_extradata(struct extradata_buffer_info *extradata_info, int flags)
{
    if (extradata_info->allocated) {
        DEBUG_PRINT_HIGH("2nd allocation return for port = %d",extradata_info->port_index);
        return OMX_ErrorNone;
    }

#ifdef USE_ION

    if (extradata_info->buffer_size) {
        if (extradata_info->ion.ion_alloc_data.handle) {
            munmap((void *)extradata_info->uaddr, extradata_info->size);
            close(extradata_info->ion.fd_ion_data.fd);
            venc_handle->free_ion_memory(&extradata_info->ion);
        }

        extradata_info->size = (extradata_info->size + 4095) & (~4095);

        extradata_info->ion.ion_device_fd = venc_handle->alloc_map_ion_memory(
                extradata_info->size,
                &extradata_info->ion.ion_alloc_data,
                &extradata_info->ion.fd_ion_data, flags);


        if (extradata_info->ion.ion_device_fd < 0) {
            DEBUG_PRINT_ERROR("Failed to alloc extradata memory\n");
            return OMX_ErrorInsufficientResources;
        }

        extradata_info->uaddr = (char *)mmap(NULL,
                extradata_info->size,
                PROT_READ|PROT_WRITE, MAP_SHARED,
                extradata_info->ion.fd_ion_data.fd , 0);

        if (extradata_info->uaddr == MAP_FAILED) {
            DEBUG_PRINT_ERROR("Failed to map extradata memory\n");
            close(extradata_info->ion.fd_ion_data.fd);
            venc_handle->free_ion_memory(&extradata_info->ion);
            return OMX_ErrorInsufficientResources;
        }
        extradata_info->m_ion_dev = open("/dev/ion", O_RDONLY);
    }

#endif
    extradata_info->allocated = OMX_TRUE;
    return OMX_ErrorNone;
}

void venc_dev::free_extradata(struct extradata_buffer_info *extradata_info)
{
#ifdef USE_ION

    if (extradata_info == NULL) {
        return;
    }

    if (extradata_info->uaddr) {
        munmap((void *)extradata_info->uaddr, extradata_info->size);
        extradata_info->uaddr = NULL;
        close(extradata_info->ion.fd_ion_data.fd);
        venc_handle->free_ion_memory(&extradata_info->ion);
    }

    if (extradata_info->m_ion_dev)
        close(extradata_info->m_ion_dev);

    memset(extradata_info, 0, sizeof(*extradata_info));
    extradata_info->ion.fd_ion_data.fd = -1;
    extradata_info->allocated = OMX_FALSE;

#endif // USE_ION
}

void venc_dev::free_extradata_all()
{
    free_extradata(&output_extradata_info);
    free_extradata(&input_extradata_info);
}

bool venc_dev::venc_get_output_log_flag()
{
    return (m_debug.out_buffer_log == 1);
}

int venc_dev::venc_output_log_buffers(const char *buffer_addr, int buffer_len, uint64_t timestamp)
{
    if (venc_handle->is_secure_session()) {
        DEBUG_PRINT_ERROR("logging secure output buffers is not allowed!");
        return -1;
    }

    if (!m_debug.outfile) {
        int size = 0;
        if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
           size = snprintf(m_debug.outfile_name, PROPERTY_VALUE_MAX, "%s/output_enc_%lu_%lu_%p.264",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        } else if(m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
           size = snprintf(m_debug.outfile_name, PROPERTY_VALUE_MAX, "%s/output_enc_%ld_%ld_%p.265",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        } else if(m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
           size = snprintf(m_debug.outfile_name, PROPERTY_VALUE_MAX, "%s/output_enc_%lu_%lu_%p.ivf",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        }
        if ((size > PROPERTY_VALUE_MAX) && (size < 0)) {
             DEBUG_PRINT_ERROR("Failed to open output file: %s for logging size:%d",
                                m_debug.outfile_name, size);
        }
        m_debug.outfile = fopen(m_debug.outfile_name, "ab");
        if (!m_debug.outfile) {
            DEBUG_PRINT_ERROR("Failed to open output file: %s for logging errno:%d",
                               m_debug.outfile_name, errno);
            m_debug.outfile_name[0] = '\0';
            return -1;
        }
        if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
            int fps = m_sVenc_cfg.fps_num / m_sVenc_cfg.fps_den;
            IvfFileHeader ivfFileHeader(false, m_sVenc_cfg.input_width,
                                        m_sVenc_cfg.input_height, fps, 1, 0);
            fwrite(&ivfFileHeader, sizeof(ivfFileHeader), 1, m_debug.outfile);
        }
    }
    if (m_debug.outfile && buffer_len) {
        if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
            IvfFrameHeader ivfFrameHeader(buffer_len, timestamp);
            fwrite(&ivfFrameHeader, sizeof(ivfFrameHeader), 1, m_debug.outfile);
        }
        DEBUG_PRINT_LOW("%s buffer_len:%d", __func__, buffer_len);
        fwrite(buffer_addr, buffer_len, 1, m_debug.outfile);
    }
    return 0;
}

int venc_dev::venc_extradata_log_buffers(char *buffer_addr)
{
    if (!m_debug.extradatafile && m_debug.extradata_log) {
        int size = 0;
        if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
           size = snprintf(m_debug.extradatafile_name, PROPERTY_VALUE_MAX, "%s/extradata_enc_%lu_%lu_%p.bin",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        } else if(m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
           size = snprintf(m_debug.extradatafile_name, PROPERTY_VALUE_MAX, "%s/extradata_enc_%lu_%lu_%p.bin",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        } else if(m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
           size = snprintf(m_debug.extradatafile_name, PROPERTY_VALUE_MAX, "%s/extradata_enc_%lu_%lu_%p.bin",
                           m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        }
        if ((size > PROPERTY_VALUE_MAX) && (size < 0)) {
             DEBUG_PRINT_ERROR("Failed to open extradata file: %s for logging size:%d",
                                m_debug.extradatafile_name, size);
        }

        m_debug.extradatafile = fopen(m_debug.extradatafile_name, "ab");
        if (!m_debug.extradatafile) {
            DEBUG_PRINT_ERROR("Failed to open extradata file: %s for logging errno:%d",
                               m_debug.extradatafile_name, errno);
            m_debug.extradatafile_name[0] = '\0';
            return -1;
        }
    }

    if (m_debug.extradatafile && buffer_addr) {
        OMX_OTHER_EXTRADATATYPE *p_extra = NULL;
        do {
            p_extra = (OMX_OTHER_EXTRADATATYPE *)(!p_extra ? buffer_addr :
                    ((char *)p_extra) + p_extra->nSize);
            fwrite(p_extra, p_extra->nSize, 1, m_debug.extradatafile);
        } while (p_extra->eType != OMX_ExtraDataNone);
    }
    return 0;
}

int venc_dev::venc_input_log_buffers(OMX_BUFFERHEADERTYPE *pbuffer, int fd, int plane_offset,
        unsigned long inputformat) {
    if (venc_handle->is_secure_session()) {
        DEBUG_PRINT_ERROR("logging secure input buffers is not allowed!");
        return -1;
    }

    if (!m_debug.infile) {
        int size = snprintf(m_debug.infile_name, PROPERTY_VALUE_MAX, "%s/input_enc_%lu_%lu_%p.yuv",
                            m_debug.log_loc, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height, this);
        if ((size > PROPERTY_VALUE_MAX) && (size < 0)) {
             DEBUG_PRINT_ERROR("Failed to open output file: %s for logging size:%d",
                                m_debug.infile_name, size);
        }
        m_debug.infile = fopen (m_debug.infile_name, "ab");
        if (!m_debug.infile) {
            DEBUG_PRINT_HIGH("Failed to open input file: %s for logging", m_debug.infile_name);
            m_debug.infile_name[0] = '\0';
            return -1;
        }
    }

    if (m_debug.infile && pbuffer && pbuffer->nFilledLen) {
        int stride, scanlines;
        int color_format;
        unsigned long i, msize;
        unsigned char *pvirt = NULL, *ptemp = NULL;
        unsigned char *temp = (unsigned char *)pbuffer->pBuffer;

        switch (inputformat) {
            case V4L2_PIX_FMT_NV12:
                color_format = COLOR_FMT_NV12;
                break;
            case V4L2_PIX_FMT_NV12_UBWC:
                color_format = COLOR_FMT_NV12_UBWC;
                break;
            case V4L2_PIX_FMT_RGB32:
                color_format = COLOR_FMT_RGBA8888;
                break;
            case V4L2_PIX_FMT_RGBA8888_UBWC:
                color_format = COLOR_FMT_RGBA8888_UBWC;
                break;
            default:
                color_format = COLOR_FMT_NV12;
                DEBUG_PRINT_LOW("Default format NV12 is set for logging [%lu]", inputformat);
                break;
        }

        msize = VENUS_BUFFER_SIZE(color_format, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height);
        const unsigned int extra_size = VENUS_EXTRADATA_SIZE(m_sVenc_cfg.input_width, m_sVenc_cfg.input_height);

        if (metadatamode == 1) {
            pvirt= (unsigned char *)mmap(NULL, msize, PROT_READ|PROT_WRITE,MAP_SHARED, fd, plane_offset);
            if (pvirt == MAP_FAILED) {
                DEBUG_PRINT_ERROR("%s mmap failed", __func__);
                return -1;
            }
            ptemp = pvirt;
        } else {
            ptemp = temp;
        }

        if (color_format == COLOR_FMT_NV12) {
            stride = VENUS_Y_STRIDE(color_format, m_sVenc_cfg.input_width);
            scanlines = VENUS_Y_SCANLINES(color_format, m_sVenc_cfg.input_height);

            for (i = 0; i < m_sVenc_cfg.input_height; i++) {
                fwrite(ptemp, m_sVenc_cfg.input_width, 1, m_debug.infile);
                ptemp += stride;
            }
            if (metadatamode == 1) {
                ptemp = pvirt + (stride * scanlines);
            } else {
                ptemp = (unsigned char *)pbuffer->pBuffer + (stride * scanlines);
            }
            for (i = 0; i < m_sVenc_cfg.input_height/2; i++) {
                fwrite(ptemp, m_sVenc_cfg.input_width, 1, m_debug.infile);
                ptemp += stride;
            }
        } else if (color_format == COLOR_FMT_RGBA8888) {
            stride = VENUS_RGB_STRIDE(color_format, m_sVenc_cfg.input_width);
            scanlines = VENUS_RGB_SCANLINES(color_format, m_sVenc_cfg.input_height);

            for (i = 0; i < m_sVenc_cfg.input_height; i++) {
                fwrite(ptemp, m_sVenc_cfg.input_width * 4, 1, m_debug.infile);
                ptemp += stride;
            }
        } else if (color_format == COLOR_FMT_NV12_UBWC || color_format == COLOR_FMT_RGBA8888_UBWC) {
            if (color_format == COLOR_FMT_NV12_UBWC) {
                msize -= 2 * extra_size;
            }
            fwrite(ptemp, msize, 1, m_debug.infile);
        }

        if (metadatamode == 1 && pvirt) {
            munmap(pvirt, msize);
        }
    }

    return 0;
}

bool venc_dev::venc_open(OMX_U32 codec)
{
    int r, minqp = 0, maxqp = 127;
    unsigned int alignment = 0,buffer_size = 0, temp =0;
    struct v4l2_control control;
    OMX_STRING device_name = (OMX_STRING)"/dev/video33";
    char property_value[PROPERTY_VALUE_MAX] = {0};
    char platform_name[PROPERTY_VALUE_MAX] = {0};
    FILE *soc_file = NULL;
    char buffer[10];

    property_get("ro.board.platform", platform_name, "0");

    if (!strncmp(platform_name, "msm8610", 7)) {
        device_name = (OMX_STRING)"/dev/video/q6_enc";
        supported_rc_modes = (RC_ALL & ~RC_CBR_CFR);
    }
    m_nDriver_fd = open (device_name, O_RDWR);
    if ((int)m_nDriver_fd < 0) {
        DEBUG_PRINT_ERROR("ERROR: Omx_venc::Comp Init Returning failure");
        return false;
    }
    m_poll_efd = eventfd(0, 0);
    if (m_poll_efd < 0) {
        DEBUG_PRINT_ERROR("Failed to open event fd(%s)", strerror(errno));
        return false;
    }
    DEBUG_PRINT_LOW("m_nDriver_fd = %u", (unsigned int)m_nDriver_fd);

    // set the basic configuration of the video encoder driver
    m_sVenc_cfg.input_width = OMX_CORE_QCIF_WIDTH;
    m_sVenc_cfg.input_height= OMX_CORE_QCIF_HEIGHT;
    m_sVenc_cfg.dvs_width = OMX_CORE_QCIF_WIDTH;
    m_sVenc_cfg.dvs_height = OMX_CORE_QCIF_HEIGHT;
    m_sVenc_cfg.fps_num = 30;
    m_sVenc_cfg.fps_den = 1;
    m_sVenc_cfg.targetbitrate = 64000;
    m_sVenc_cfg.inputformat= V4L2_DEFAULT_OUTPUT_COLOR_FMT;
    m_rotation.rotation = 0;
    m_codec = codec;
    downscalar_enabled = OMX_FALSE;

    if (codec == OMX_VIDEO_CodingAVC) {
        m_sVenc_cfg.codectype = V4L2_PIX_FMT_H264;
        codec_profile.profile = V4L2_MPEG_VIDEO_H264_PROFILE_BASELINE;
        profile_level.level = V4L2_MPEG_VIDEO_H264_LEVEL_1_0;
        idrperiod.idrperiod = 1;
        minqp = 0;
        maxqp = 51;
    } else if (codec == OMX_VIDEO_CodingVP8) {
        m_sVenc_cfg.codectype = V4L2_PIX_FMT_VP8;
        codec_profile.profile = V4L2_MPEG_VIDC_VIDEO_VP8_UNUSED;
        profile_level.level = V4L2_MPEG_VIDC_VIDEO_VP8_VERSION_0;
        minqp = 0;
        maxqp = 127;
    } else if (codec == OMX_VIDEO_CodingHEVC || codec == OMX_VIDEO_CodingImageHEIC) {
        m_sVenc_cfg.codectype = V4L2_PIX_FMT_HEVC;
        idrperiod.idrperiod = 1;
        minqp = 0;
        maxqp = 51;
        if (codec == OMX_VIDEO_CodingImageHEIC)
            codec_profile.profile = V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN_STILL_PIC;
        else
            codec_profile.profile = V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN;
        profile_level.level = V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_1;
    } else if (codec == QOMX_VIDEO_CodingTME) {
        m_sVenc_cfg.codectype = V4L2_PIX_FMT_TME;
    }
    session_ipb_qp_values.min_i_qp = minqp;
    session_ipb_qp_values.max_i_qp = maxqp;
    session_ipb_qp_values.min_p_qp = minqp;
    session_ipb_qp_values.max_p_qp = maxqp;
    session_ipb_qp_values.min_b_qp = minqp;
    session_ipb_qp_values.max_b_qp = maxqp;

    int ret;
    ret = subscribe_to_events(m_nDriver_fd);

    if (ret) {
        DEBUG_PRINT_ERROR("Subscribe Event Failed");
        return false;
    }

    struct v4l2_fmtdesc fdesc;
    struct v4l2_format fmt;
    struct v4l2_requestbuffers bufreq;
    struct v4l2_capability cap;

    ret = ioctl(m_nDriver_fd, VIDIOC_QUERYCAP, &cap);

    if (ret) {
        DEBUG_PRINT_ERROR("Failed to query capabilities");
    } else {
        DEBUG_PRINT_LOW("Capabilities: driver_name = %s, card = %s, bus_info = %s,"
                " version = %d, capabilities = %x", cap.driver, cap.card,
                cap.bus_info, cap.version, cap.capabilities);
    }

    ret=0;
    fdesc.type=V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    fdesc.index=0;

    while (ioctl(m_nDriver_fd, VIDIOC_ENUM_FMT, &fdesc) == 0) {
        DEBUG_PRINT_LOW("fmt: description: %s, fmt: %x, flags = %x", fdesc.description,
                fdesc.pixelformat, fdesc.flags);
        fdesc.index++;
    }

    fdesc.type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    fdesc.index=0;

    while (ioctl(m_nDriver_fd, VIDIOC_ENUM_FMT, &fdesc) == 0) {
        DEBUG_PRINT_LOW("fmt: description: %s, fmt: %x, flags = %x", fdesc.description,
                fdesc.pixelformat, fdesc.flags);
        fdesc.index++;
    }

    if (venc_handle->is_secure_session()) {
        m_sOutput_buff_property.alignment = SZ_1M;
        m_sInput_buff_property.alignment  = SZ_1M;
    } else {
        m_sOutput_buff_property.alignment = SZ_4K;
        m_sInput_buff_property.alignment  = SZ_4K;
    }

    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
    fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
    fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;

    /*TODO: Return values not handled properly in this function anywhere.
     * Need to handle those.*/
    ret = ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt);

    if (ret) {
        DEBUG_PRINT_ERROR("Failed to set format on capture port");
        return false;
    }

    m_sOutput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
    fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
    fmt.fmt.pix_mp.pixelformat = V4L2_DEFAULT_OUTPUT_COLOR_FMT;
    fmt.fmt.pix_mp.colorspace = V4L2_COLORSPACE_470_SYSTEM_BG;

    ret = ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt);
    m_sInput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

    bufreq.memory = V4L2_MEMORY_USERPTR;
    bufreq.count = 2;

    bufreq.type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    ret = ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq);
    m_sInput_buff_property.mincount = m_sInput_buff_property.actualcount = bufreq.count;

    bufreq.type=V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    bufreq.count = 2;
    ret = ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq);
    m_sOutput_buff_property.mincount = m_sOutput_buff_property.actualcount = bufreq.count;

    if(venc_handle->is_secure_session()) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_SECURE;
        control.value = 1;
        DEBUG_PRINT_HIGH("ioctl: open secure device");
        ret=ioctl(m_nDriver_fd, VIDIOC_S_CTRL,&control);
        if (ret) {
            DEBUG_PRINT_ERROR("ioctl: open secure dev fail, rc %d", ret);
            return false;
        }
    }

    resume_in_stopped = 0;
    metadatamode = 0;

    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_TME) {
        control.id = V4L2_CID_MPEG_VIDEO_HEADER_MODE;
        control.value = V4L2_MPEG_VIDEO_HEADER_MODE_SEPARATE;

        DEBUG_PRINT_LOW("Calling IOCTL to disable seq_hdr in sync_frame id=%d, val=%d", control.id, control.value);

        if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control))
            DEBUG_PRINT_ERROR("Failed to set control");
    }

    struct v4l2_frmsizeenum frmsize;

    //Get the hardware capabilities
    memset((void *)&frmsize,0,sizeof(frmsize));
    frmsize.index = 0;
    frmsize.pixel_format = m_sVenc_cfg.codectype;
    ret = ioctl(m_nDriver_fd, VIDIOC_ENUM_FRAMESIZES, &frmsize);

    if (ret || frmsize.type != V4L2_FRMSIZE_TYPE_STEPWISE) {
        DEBUG_PRINT_ERROR("Failed to get framesizes");
        return false;
    }

    if (frmsize.type == V4L2_FRMSIZE_TYPE_STEPWISE) {
        capability.min_width = frmsize.stepwise.min_width;
        capability.max_width = frmsize.stepwise.max_width;
        capability.min_height = frmsize.stepwise.min_height;
        capability.max_height = frmsize.stepwise.max_height;
    }

    //Initialize non-default parameters
    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES;
        control.value = 0x7fffffff;
        if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control))
            DEBUG_PRINT_ERROR("Failed to set V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAME\n");
    }


    /* Enable Low power mode by default for better power */

    input_extradata_info.port_index = OUTPUT_PORT;
    output_extradata_info.port_index = CAPTURE_PORT;

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_TME) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_TME_PAYLOAD_VERSION;
        ret = ioctl(m_nDriver_fd, VIDIOC_G_CTRL,&control);

        if (ret) {
            DEBUG_PRINT_ERROR("Failed to read TME version");
            return false;
        }
        venc_handle->tme_payload_version = control.value;
        DEBUG_PRINT_HIGH("TME version is 0x%x", control.value);
    }
    return true;
}

static OMX_ERRORTYPE unsubscribe_to_events(int fd)
{
    OMX_ERRORTYPE eRet = OMX_ErrorNone;
    struct v4l2_event_subscription sub;
    int array_sz = sizeof(event_type)/sizeof(int);
    int i,rc;

    if (fd < 0) {
       DEBUG_PRINT_ERROR("Invalid input: %d", fd);
        return OMX_ErrorBadParameter;
    }

    for (i = 0; i < array_sz; ++i) {
        memset(&sub, 0, sizeof(sub));
        sub.type = event_type[i];
        rc = ioctl(fd, VIDIOC_UNSUBSCRIBE_EVENT, &sub);

        if (rc) {
           DEBUG_PRINT_ERROR("Failed to unsubscribe event: 0x%x", sub.type);
            break;
        }
    }

    return eRet;
}

void venc_dev::venc_close()
{
    DEBUG_PRINT_LOW("venc_close: fd = %u", (unsigned int)m_nDriver_fd);

    if ((int)m_nDriver_fd >= 0) {
        DEBUG_PRINT_HIGH("venc_close E");

        if(eventfd_write(m_poll_efd, 1)) {
            DEBUG_PRINT_ERROR("eventfd_write failed for fd: %d, errno = %d, force stop async_thread", m_poll_efd, errno);
            async_thread_force_stop = true;
        }

        if (async_thread_created)
            pthread_join(m_tid,NULL);

        if (venc_handle->msg_thread_created) {
            venc_handle->msg_thread_created = false;
            venc_handle->msg_thread_stop = true;
            post_message(venc_handle, omx_video::OMX_COMPONENT_CLOSE_MSG);
            DEBUG_PRINT_HIGH("omx_video: Waiting on Msg Thread exit");
            pthread_join(venc_handle->msg_thread_id, NULL);
        }
        DEBUG_PRINT_HIGH("venc_close X");
        unsubscribe_to_events(m_nDriver_fd);
        close(m_poll_efd);
        close(m_nDriver_fd);
        m_nDriver_fd = -1;
    }

#ifdef _VQZIP_
    vqzip.deinit();
#endif

    if (m_debug.infile) {
        fclose(m_debug.infile);
        m_debug.infile = NULL;
    }

    if (m_debug.outfile) {
        fclose(m_debug.outfile);
        m_debug.outfile = NULL;
    }

    if (m_debug.extradatafile) {
        fclose(m_debug.extradatafile);
        m_debug.extradatafile = NULL;
    }
}

bool venc_dev::venc_set_buf_req(OMX_U32 *min_buff_count,
        OMX_U32 *actual_buff_count,
        OMX_U32 *buff_size,
        OMX_U32 port)
{
    (void)min_buff_count, (void)buff_size;
    unsigned long temp_count = 0;

    if (port == 0) {
        if (*actual_buff_count > m_sInput_buff_property.mincount) {
            temp_count = m_sInput_buff_property.actualcount;
            m_sInput_buff_property.actualcount = *actual_buff_count;
            DEBUG_PRINT_LOW("I/P Count set to %u", (unsigned int)*actual_buff_count);
        }
    } else {
        if (*actual_buff_count > m_sOutput_buff_property.mincount) {
            temp_count = m_sOutput_buff_property.actualcount;
            m_sOutput_buff_property.actualcount = *actual_buff_count;
            DEBUG_PRINT_LOW("O/P Count set to %u", (unsigned int)*actual_buff_count);
        }
    }

    return true;

}

bool venc_dev::venc_loaded_start()
{
    return true;
}

bool venc_dev::venc_loaded_stop()
{
    return true;
}

bool venc_dev::venc_loaded_start_done()
{
    return true;
}

bool venc_dev::venc_loaded_stop_done()
{
    return true;
}

bool venc_dev::venc_get_seq_hdr(void *buffer,
        unsigned buffer_size, unsigned *header_len)
{
    (void) buffer, (void) buffer_size, (void) header_len;
    return true;
}

bool venc_dev::venc_get_dimensions(OMX_U32 portIndex, OMX_U32 *w, OMX_U32 *h) {
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = portIndex == PORT_INDEX_OUT ? V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE :
            V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;

    if (ioctl(m_nDriver_fd, VIDIOC_G_FMT, &fmt)) {
        DEBUG_PRINT_ERROR("Failed to get format on %s port",
                portIndex == PORT_INDEX_OUT ? "capture" : "output");
        return false;
    }
    *w = fmt.fmt.pix_mp.width;
    *h = fmt.fmt.pix_mp.height;
    return true;
}

bool venc_dev::venc_get_buf_req(OMX_U32 *min_buff_count,
        OMX_U32 *actual_buff_count,
        OMX_U32 *buff_size,
        OMX_U32 port)
{
    struct v4l2_format fmt;
    unsigned int buf_size = 0, extra_data_size = 0, client_extra_data_size = 0;
    int ret;
    int extra_idx = 0;
    struct v4l2_control control;
    unsigned int minCount = 0;

    memset(&control, 0, sizeof(control));
    memset(&fmt, 0, sizeof(fmt));

    if (port == 0) {
        fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
        fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.inputformat;
        fmt.fmt.pix_mp.colorspace = V4L2_COLORSPACE_470_SYSTEM_BG;
        ret = ioctl(m_nDriver_fd, VIDIOC_G_FMT, &fmt);
        if (ret) {
            DEBUG_PRINT_ERROR("set format failed, type %d, wxh %dx%d, format %#x, colorspace %d\n",
                fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                fmt.fmt.pix_mp.pixelformat, fmt.fmt.pix_mp.colorspace);
            return false;
        }
        m_sInput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

        control.id = V4L2_CID_MIN_BUFFERS_FOR_OUTPUT;
        ret = ioctl(m_nDriver_fd,  VIDIOC_G_CTRL, &control);
        if (ret || (unsigned int)control.value > MAX_V4L2_BUFS) {
            DEBUG_PRINT_ERROR("Driver returned invalid data, port = %d ret = %d Count = %d",
                port, ret, (unsigned int)control.value);
            return false;
        }

        // Increase buffer-header count for metadata-mode on input port
        // to improve buffering and reduce bottlenecks in clients
        if (metadatamode) {
            DEBUG_PRINT_LOW("FW returned buffer count = %d , overwriting with 9",
                control.value);
            minCount = 9;
        }

        if (m_sVenc_cfg.input_height * m_sVenc_cfg.input_width >= 3840*2160) {
            DEBUG_PRINT_LOW("Increasing buffer count = %d to 11", minCount);
            minCount = 11;
        }

        // Request MAX_V4L2_BUFS from V4L2 in batch mode.
        // Keep the original count for the client
        if (metadatamode && mBatchSize) {
            minCount = MAX_V4L2_BUFS;
            DEBUG_PRINT_LOW("Set buffer count = %d as metadata mode and batchmode enabled", minCount);
        }

        minCount = MAX((unsigned int)control.value, minCount);
        m_sInput_buff_property.mincount = minCount;

        fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
        fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.inputformat;
        ret = ioctl(m_nDriver_fd, VIDIOC_G_FMT, &fmt);
        if (ret) {
            DEBUG_PRINT_ERROR("get format failed, type %d, wxh %dx%d, format %#x\n",
                fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                fmt.fmt.pix_mp.pixelformat);
            return false;
        }
        m_sInput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

        if (m_sInput_buff_property.actualcount < m_sInput_buff_property.mincount)
            m_sInput_buff_property.actualcount = m_sInput_buff_property.mincount;

        *min_buff_count = m_sInput_buff_property.mincount;
        *actual_buff_count = m_sInput_buff_property.actualcount;
#ifdef USE_ION
        // For ION memory allocations of the allocated buffer size
        // must be 4k aligned, hence aligning the input buffer
        // size to 4k.
        m_sInput_buff_property.datasize = ALIGN(m_sInput_buff_property.datasize, SZ_4K);
#endif
        *buff_size = m_sInput_buff_property.datasize;
        num_input_planes = fmt.fmt.pix_mp.num_planes;
        extra_idx = EXTRADATA_IDX(num_input_planes);

        if (extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
            extra_data_size =  fmt.fmt.pix_mp.plane_fmt[extra_idx].sizeimage;
        } else if (extra_idx >= VIDEO_MAX_PLANES) {
            DEBUG_PRINT_ERROR("Extradata index is more than allowed: %d\n", extra_idx);
            return false;
        }
        input_extradata_info.buffer_size =  ALIGN(extra_data_size, SZ_4K);
        input_extradata_info.count = MAX_V4L2_BUFS;
        input_extradata_info.size = input_extradata_info.buffer_size * input_extradata_info.count;

    } else {
        unsigned int extra_idx = 0;
        memset(&fmt, 0, sizeof(fmt));
        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
        fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;

        ret = ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt);
        if (ret) {
            DEBUG_PRINT_ERROR("set format failed, type %d, wxh %dx%d, format %#x\n",
                fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                fmt.fmt.pix_mp.pixelformat);
            return false;
        }

        m_sOutput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;
        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
        fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;

        ret = ioctl(m_nDriver_fd, VIDIOC_G_FMT, &fmt);
        if (ret) {
            DEBUG_PRINT_ERROR("get format failed, type %d, wxh %dx%d, format %#x\n",
                fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                fmt.fmt.pix_mp.pixelformat);
            return false;
        }
        m_sOutput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

        control.id = V4L2_CID_MIN_BUFFERS_FOR_CAPTURE;

        ret = ioctl(m_nDriver_fd,  VIDIOC_G_CTRL, &control);
        if (ret || (unsigned int)control.value > MAX_V4L2_BUFS) {
            DEBUG_PRINT_ERROR("Driver returned invalid data port = %d ret = %d Count = %d",
                port, ret, (unsigned int)control.value);
            return false;
        }
        minCount = control.value;

        if (mBatchSize) {
            // If we're in batch mode, we'd like to end up in a situation where
            // driver is able to own mBatchSize buffers and we'd also own atleast
            // mBatchSize buffers
            minCount = MAX((unsigned int)control.value, mBatchSize) + mBatchSize;
            DEBUG_PRINT_LOW("set min count %d as mBatchSize %d", minCount, mBatchSize);
        }
        m_sOutput_buff_property.mincount = minCount;

        if (m_sOutput_buff_property.actualcount < m_sOutput_buff_property.mincount)
            m_sOutput_buff_property.actualcount = m_sOutput_buff_property.mincount;

        *min_buff_count = m_sOutput_buff_property.mincount;
        *actual_buff_count = m_sOutput_buff_property.actualcount;
        *buff_size = m_sOutput_buff_property.datasize;
        num_output_planes = fmt.fmt.pix_mp.num_planes;
        extra_idx = EXTRADATA_IDX(num_output_planes);

        if (extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
            extra_data_size =  fmt.fmt.pix_mp.plane_fmt[extra_idx].sizeimage;
        } else if (extra_idx >= VIDEO_MAX_PLANES) {
            DEBUG_PRINT_ERROR("Extradata index is more than allowed: %d", extra_idx);
            return false;
        }

        output_extradata_info.buffer_size = extra_data_size;
        output_extradata_info.count = m_sOutput_buff_property.actualcount;
        output_extradata_info.size = output_extradata_info.buffer_size * output_extradata_info.count;
        venc_handle->m_client_out_extradata_info.set_extradata_info(extra_data_size,m_sOutput_buff_property.actualcount);
    }

    DEBUG_PRINT_HIGH("venc_get_buf_req: port %d, wxh %dx%d, format %#x, driver min count %d, "
        "updated min count %d, act count %d, size %d, num planes %d",
        port, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height, fmt.fmt.pix_mp.pixelformat,
        control.value, *min_buff_count, *actual_buff_count, *buff_size, fmt.fmt.pix_mp.num_planes);

    return true;
}

bool venc_dev::venc_set_param(void *paramData, OMX_INDEXTYPE index)
{
    DEBUG_PRINT_LOW("venc_set_param index 0x%x", index);
    struct v4l2_format fmt;
    struct v4l2_requestbuffers bufreq;
    int ret;
    bool isCBR;

    switch ((int)index) {
        case OMX_IndexParamPortDefinition:
            {
                OMX_PARAM_PORTDEFINITIONTYPE *portDefn;
                portDefn = (OMX_PARAM_PORTDEFINITIONTYPE *) paramData;

                if (portDefn->nPortIndex == PORT_INDEX_IN) {
                    if (!venc_set_encode_framerate(portDefn->format.video.xFramerate)) {
                        return false;
                    }

                    if (enable_mv_narrow_searchrange &&
                        (m_sVenc_cfg.input_width * m_sVenc_cfg.input_height) >=
                        (OMX_CORE_1080P_WIDTH * OMX_CORE_1080P_HEIGHT)) {
                        if (venc_set_searchrange() == false) {
                            DEBUG_PRINT_ERROR("ERROR: Failed to set search range");
                        }
                    }

                    unsigned long inputformat = venc_get_color_format(portDefn->format.video.eColorFormat);

                    if (m_sVenc_cfg.input_height != portDefn->format.video.nFrameHeight ||
                            m_sVenc_cfg.input_width != portDefn->format.video.nFrameWidth ||
                            m_sInput_buff_property.actualcount != portDefn->nBufferCountActual ||
                            m_sVenc_cfg.inputformat != inputformat) {

                        DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamPortDefinition: port: %u, WxH %lux%lu --> %ux%u, count %lu --> %u, format %#lx --> %#lx",
                            portDefn->nPortIndex, m_sVenc_cfg.input_width, m_sVenc_cfg.input_height,
                            portDefn->format.video.nFrameWidth, portDefn->format.video.nFrameHeight,
                            m_sInput_buff_property.actualcount, portDefn->nBufferCountActual,
                            m_sVenc_cfg.inputformat, inputformat);

                        if (portDefn->nBufferCountActual < m_sInput_buff_property.mincount) {
                            DEBUG_PRINT_LOW("Actual count %u is less than driver mincount %lu on port %u",
                                portDefn->nBufferCountActual, m_sInput_buff_property.mincount, portDefn->nPortIndex);
                            return false;
                        }

                        m_sVenc_cfg.input_height = portDefn->format.video.nFrameHeight;
                        m_sVenc_cfg.input_width = portDefn->format.video.nFrameWidth;
                        m_sVenc_cfg.inputformat = inputformat;
                        m_sInput_buff_property.actualcount = portDefn->nBufferCountActual;

                        memset(&fmt, 0, sizeof(fmt));
                        fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
                        fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
                        fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
                        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.inputformat;
                        fmt.fmt.pix_mp.colorspace = V4L2_COLORSPACE_470_SYSTEM_BG;
                        if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
                            DEBUG_PRINT_ERROR("set format failed, type %d, wxh %dx%d, pixelformat %#x, colorspace %#x",
                                 fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                                 fmt.fmt.pix_mp.pixelformat, fmt.fmt.pix_mp.colorspace);
                            hw_overload = errno == EBUSY;
                            return false;
                        }
                        m_sInput_buff_property.datasize=fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

                        bufreq.memory = V4L2_MEMORY_USERPTR;
                        bufreq.count = portDefn->nBufferCountActual;
                        bufreq.type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
                        if (ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
                            DEBUG_PRINT_ERROR("reqbufs failed, type %d, count %d", bufreq.type, bufreq.count);
                            return false;
                        }

                        if (num_input_planes > 1)
                            input_extradata_info.count = m_sInput_buff_property.actualcount + 1;

                        if (!downscalar_enabled) {
                            if (m_rotation.rotation == 90 || m_rotation.rotation == 270) {
                                m_sVenc_cfg.dvs_height = portDefn->format.video.nFrameWidth;
                                m_sVenc_cfg.dvs_width = portDefn->format.video.nFrameHeight;
                            } else {
                                m_sVenc_cfg.dvs_height = portDefn->format.video.nFrameHeight;
                                m_sVenc_cfg.dvs_width = portDefn->format.video.nFrameWidth;
                            }
                        }
                        memset(&fmt, 0, sizeof(fmt));
                        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
                        fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
                        fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
                        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;

                        if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
                            DEBUG_PRINT_ERROR("VIDIOC_S_FMT CAPTURE_MPLANE Failed");
                            hw_overload = errno == EBUSY;
                            return false;
                        }

                        m_sOutput_buff_property.datasize = fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

                    } else {
                        DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamPortDefinition: parameters not changed on port %d",
                            portDefn->nPortIndex);
                    }
                } else if (portDefn->nPortIndex == PORT_INDEX_OUT) {
                    unsigned long codectype = venc_get_codectype(portDefn->format.video.eCompressionFormat);

                    //Don't worry about width/height if downscalar is enabled.
                    if (((m_sVenc_cfg.dvs_height != portDefn->format.video.nFrameHeight ||
                            m_sVenc_cfg.dvs_width != portDefn->format.video.nFrameWidth) && !downscalar_enabled) ||
                            m_sOutput_buff_property.actualcount != portDefn->nBufferCountActual ||
                            m_sVenc_cfg.codectype != codectype) {

                        if (portDefn->nBufferCountActual < m_sOutput_buff_property.mincount) {
                            DEBUG_PRINT_LOW("Actual count %u is less than driver mincount %lu on port %u",
                                portDefn->nBufferCountActual, m_sOutput_buff_property.mincount, portDefn->nPortIndex);
                            return false;
                        }

                        //If downscalar is enabled. Correct width/height is populated no need to replace with port def width/height
                        if (!downscalar_enabled) {
                            DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamPortDefinition: port: %u, WxH %lux%lu --> %ux%u, count %lu --> %u, format %#lx --> %#lx",
                                            portDefn->nPortIndex, m_sVenc_cfg.dvs_width, m_sVenc_cfg.dvs_height,
                                            portDefn->format.video.nFrameWidth, portDefn->format.video.nFrameHeight,
                                            m_sOutput_buff_property.actualcount, portDefn->nBufferCountActual,
                                            m_sVenc_cfg.codectype, codectype);
                            m_sVenc_cfg.dvs_height = portDefn->format.video.nFrameHeight;
                            m_sVenc_cfg.dvs_width = portDefn->format.video.nFrameWidth;
                        }

                        m_sVenc_cfg.codectype = codectype;
                        m_sOutput_buff_property.actualcount = portDefn->nBufferCountActual;

                        memset(&fmt, 0, sizeof(fmt));
                        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
                        fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
                        fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
                        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;
                        if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
                            DEBUG_PRINT_ERROR("set format failed, type %d, wxh %dx%d, pixelformat %#x",
                                 fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                                 fmt.fmt.pix_mp.pixelformat);
                            hw_overload = errno == EBUSY;
                            return false;
                        }
                        m_sOutput_buff_property.datasize = fmt.fmt.pix_mp.plane_fmt[0].sizeimage;

                        if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_TME) {
                            if (!venc_set_target_bitrate(portDefn->format.video.nBitrate)) {
                                return false;
                            }
                        }

                        bufreq.memory = V4L2_MEMORY_USERPTR;
                        bufreq.count = portDefn->nBufferCountActual;
                        bufreq.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
                        if (ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
                            DEBUG_PRINT_ERROR("reqbufs failed, type %d, count %d", bufreq.type, bufreq.count);
                            return false;
                        }

                        if (num_output_planes > 1)
                            output_extradata_info.count = m_sOutput_buff_property.actualcount;

                        //reset rotation as output port dimension is changed
                        m_rotation.rotation = 0;
                        venc_handle->m_sConfigFrameRotation.nRotation = 0;
                    } else {
                        DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamPortDefinition: parameters not changed on port %d",
                            portDefn->nPortIndex);
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index (%d) for OMX_IndexParamPortDefinition", portDefn->nPortIndex);
                }
            }
            break;
        case OMX_IndexParamVideoPortFormat:
            {
                OMX_VIDEO_PARAM_PORTFORMATTYPE *portFmt;
                portFmt =(OMX_VIDEO_PARAM_PORTFORMATTYPE *)paramData;
                DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamVideoPortFormat");

                if (portFmt->nPortIndex == (OMX_U32) PORT_INDEX_IN) {
                    if (!venc_set_color_format(portFmt->eColorFormat)) {
                        return false;
                    }
                } else if (portFmt->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (!venc_set_encode_framerate(portFmt->xFramerate)) {
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoPortFormat");
                }
                    break;
            }
        case OMX_IndexParamVideoBitrate:
            {
                OMX_VIDEO_PARAM_BITRATETYPE* pParam;
                pParam = (OMX_VIDEO_PARAM_BITRATETYPE*)paramData;
                DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamVideoBitrate");

                if (pParam->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (!venc_set_ratectrl_cfg(pParam->eControlRate)) {
                        DEBUG_PRINT_ERROR("ERROR: Rate Control setting failed");
                        return false;
                    }

                    if (!venc_set_target_bitrate(pParam->nTargetBitrate)) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Target Bit Rate / Quality Factor failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoBitrate");
                }

                break;
            }
        case OMX_IndexParamVideoAvc:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoAvc");
                OMX_VIDEO_PARAM_AVCTYPE* pParam = (OMX_VIDEO_PARAM_AVCTYPE*)paramData;
                OMX_U32 bFrames = 0;

                if (pParam->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    DEBUG_PRINT_LOW("pParam->eProfile :%d ,pParam->eLevel %d",
                            pParam->eProfile,pParam->eLevel);

                    if (!venc_set_profile (pParam->eProfile)) {
                        DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating Profile %d",
                                pParam->eProfile);
                        return false;
                    } else {
                        if ((pParam->eProfile != OMX_VIDEO_AVCProfileBaseline) &&
                            (pParam->eProfile != (OMX_VIDEO_AVCPROFILETYPE) QOMX_VIDEO_AVCProfileConstrainedBaseline)) {
                            if (pParam->nBFrames) {
                                bFrames = pParam->nBFrames;
                            }
                        } else {
                            if (pParam->nBFrames) {
                                DEBUG_PRINT_ERROR("Warning: B frames not supported");
                                bFrames = 0;
                            }
                        }
                    }

                    if(!venc_set_level(OMX_VIDEO_LEVEL_UNKNOWN)) {
                        DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating level to unknown");
                        return false;
                    }

                    if (!venc_set_intra_period (pParam->nPFrames, bFrames)) {
                        DEBUG_PRINT_ERROR("ERROR: Request for setting intra period failed");
                        return false;
                    }

                    if (!venc_set_entropy_config (pParam->bEntropyCodingCABAC, pParam->nCabacInitIdc)) {
                        DEBUG_PRINT_ERROR("ERROR: Request for setting Entropy failed");
                        return false;
                    }

                    if (!venc_set_inloop_filter (pParam->eLoopFilterMode)) {
                        DEBUG_PRINT_ERROR("ERROR: Request for setting Inloop filter failed");
                        return false;
                    }

                    if (!venc_set_multislice_cfg(V4L2_MPEG_VIDEO_MULTI_SICE_MODE_MAX_MB, pParam->nSliceHeaderSpacing)) {
                        DEBUG_PRINT_ERROR("WARNING: Unsuccessful in updating slice_config");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoAvc");
                }

                //TBD, lot of other variables to be updated, yet to decide
                break;
            }
        case (OMX_INDEXTYPE)OMX_IndexParamVideoVp8:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoVp8");
                OMX_VIDEO_PARAM_VP8TYPE* pParam = (OMX_VIDEO_PARAM_VP8TYPE*)paramData;

                //TODO: Set VP8 level/profile currently based on driver change
                if (!venc_set_profile (pParam->eProfile)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating Profile %d",
                            pParam->eProfile);
                    return false;
                }
                if (!venc_set_level (OMX_VIDEO_LEVEL_UNKNOWN)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating level to unknown");
                    return false;
                }
                if(venc_set_vpx_error_resilience(pParam->bErrorResilientMode) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Failed to set vpx error resilience");
                    return false;
                }
                break;
            }
            case (OMX_INDEXTYPE)OMX_IndexParamVideoHevc:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoHevc");
                OMX_VIDEO_PARAM_HEVCTYPE* pParam = (OMX_VIDEO_PARAM_HEVCTYPE*)paramData;

                if (!venc_set_profile (pParam->eProfile)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating Profile %d",
                                        pParam->eProfile);
                    return false;
                }
                if (!venc_set_level (OMX_VIDEO_LEVEL_UNKNOWN)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating level to unknown");
                    return false;
                }
                if (!venc_set_inloop_filter(OMX_VIDEO_AVCLoopFilterEnable))
                    DEBUG_PRINT_HIGH("WARN: Request for setting Inloop filter failed for HEVC encoder");

                OMX_U32 fps = m_sVenc_cfg.fps_den ? m_sVenc_cfg.fps_num / m_sVenc_cfg.fps_den : 30;
                OMX_U32 nPFrames = pParam->nKeyFrameInterval > 0 ? pParam->nKeyFrameInterval - 1 : fps - 1;
                if (!venc_set_intra_period (nPFrames, 0 /* nBFrames */)) {
                    DEBUG_PRINT_ERROR("ERROR: Request for setting intra period failed");
                    return false;
                }
                break;
            }
            case (OMX_INDEXTYPE)OMX_IndexParamVideoTme:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoTme");
                QOMX_VIDEO_PARAM_TMETYPE * pParam = (QOMX_VIDEO_PARAM_TMETYPE*)paramData;

                if (!venc_set_profile(pParam->eProfile)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating Profile %d",
                                        pParam->eProfile);
                    return false;
                }
                if (!venc_set_level(pParam->eLevel)) {
                    DEBUG_PRINT_ERROR("ERROR: Unsuccessful in updating level %d",
                                      pParam->eLevel);
                    return false;
                }
                break;
            }
            case (OMX_INDEXTYPE)OMX_IndexParamVideoAndroidImageGrid:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_IndexParamVideoAndroidImageGrid");
                OMX_VIDEO_PARAM_ANDROID_IMAGEGRIDTYPE* pParam =
                       (OMX_VIDEO_PARAM_ANDROID_IMAGEGRIDTYPE*)paramData;

                if (m_codec != OMX_VIDEO_CodingImageHEIC) {
                    DEBUG_PRINT_ERROR("OMX_IndexParamVideoAndroidImageGrid is only set for HEIC Encode (HW tiling)");
                    return true;
                }

                if (!venc_set_tile_dimension(pParam->nTileWidth)) {
                    DEBUG_PRINT_ERROR("ERROR: Failed to set tile dimension %d",
                                        pParam->nTileWidth);
                    return false;
                }
                break;
            }
        case OMX_IndexParamVideoIntraRefresh:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoIntraRefresh");
                OMX_VIDEO_PARAM_INTRAREFRESHTYPE *intra_refresh_param =
                    (OMX_VIDEO_PARAM_INTRAREFRESHTYPE *)paramData;

                if (intra_refresh_param->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    intra_refresh.irmode     = intra_refresh_param->eRefreshMode;
                    intra_refresh.mbcount    = intra_refresh_param->nCirMBs;
                    intra_refresh.framecount = 0;
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoIntraRefresh");
                }

                break;
            }
        case OMX_IndexParamVideoErrorCorrection:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoErrorCorrection");
                OMX_VIDEO_PARAM_ERRORCORRECTIONTYPE *error_resilience =
                    (OMX_VIDEO_PARAM_ERRORCORRECTIONTYPE *)paramData;

                if (error_resilience->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (venc_set_error_resilience(error_resilience) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Error Correction failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoErrorCorrection");
                }

                break;
            }
         case OMX_QcomIndexParamVideoSliceSpacing:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_QcomIndexParamVideoSliceSpacing");
                QOMX_VIDEO_PARAM_SLICE_SPACING_TYPE *slice_spacing =
                    (QOMX_VIDEO_PARAM_SLICE_SPACING_TYPE*)paramData;

                if (slice_spacing->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (!venc_set_multislice_cfg(slice_spacing->eSliceMode, slice_spacing->nSliceSize)) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Slice Spacing failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_QcomIndexParamVideoSliceSpacing");
                }

                break;
            }
        case OMX_IndexParamVideoProfileLevelCurrent:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoProfileLevelCurrent");
                OMX_VIDEO_PARAM_PROFILELEVELTYPE *profile_level =
                    (OMX_VIDEO_PARAM_PROFILELEVELTYPE *)paramData;

                if (profile_level->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (!venc_set_profile(profile_level->eProfile)) {
                        DEBUG_PRINT_ERROR("WARNING: Unsuccessful in updating Profile");
                        return false;
                    }
                    if (!venc_set_level(profile_level->eLevel)) {
                        DEBUG_PRINT_ERROR("WARNING: Unsuccessful in updating level");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoProfileLevelCurrent");
                }

                break;
            }
        case OMX_IndexParamVideoQuantization:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_IndexParamVideoQuantization");
                OMX_VIDEO_PARAM_QUANTIZATIONTYPE *session_qp =
                    (OMX_VIDEO_PARAM_QUANTIZATIONTYPE *)paramData;
                if (session_qp->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (venc_set_qp(session_qp->nQpI,
                                session_qp->nQpP,
                                session_qp->nQpB,
                                ENABLE_I_QP | ENABLE_P_QP | ENABLE_B_QP) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Session QP failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexParamVideoQuantization");
                }

                break;
            }
        case QOMX_IndexParamVideoInitialQp:
            {
                DEBUG_PRINT_LOW("venc_set_param:QOMX_IndexParamVideoInitialQp");
                QOMX_EXTNINDEX_VIDEO_INITIALQP *initial_qp =
                    (QOMX_EXTNINDEX_VIDEO_INITIALQP *)paramData;
                if (initial_qp->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (venc_set_qp(initial_qp->nQpI,
                                initial_qp->nQpP,
                                initial_qp->nQpB,
                                initial_qp->bEnableInitQp) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Initial QP failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for QOMX_IndexParamVideoInitialQp");
                }

                break;
            }
        case OMX_QcomIndexParamVideoIPBQPRange:
            {
                DEBUG_PRINT_LOW("venc_set_param:OMX_QcomIndexParamVideoIPBQPRange");
                OMX_QCOM_VIDEO_PARAM_IPB_QPRANGETYPE *session_qp_range =
                    (OMX_QCOM_VIDEO_PARAM_IPB_QPRANGETYPE *)paramData;
                if(session_qp_range->nPortIndex == (OMX_U32)PORT_INDEX_OUT) {
                    if ( venc_set_session_qp_range (session_qp_range) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting QP range failed");
                        return false;
                    }
                }

                break;
            }
        case OMX_QcomIndexEnableSliceDeliveryMode:
            {
                QOMX_EXTNINDEX_PARAMTYPE* pParam =
                    (QOMX_EXTNINDEX_PARAMTYPE*)paramData;

                if (pParam->nPortIndex == PORT_INDEX_OUT) {
                    if (venc_set_slice_delivery_mode(pParam->bEnable) == false) {
                        DEBUG_PRINT_ERROR("Setting slice delivery mode failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("OMX_QcomIndexEnableSliceDeliveryMode "
                            "called on wrong port(%u)", (unsigned int)pParam->nPortIndex);
                    return false;
                }

                break;
            }
        case OMX_ExtraDataFrameDimension:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_ExtraDataFrameDimension");
                OMX_BOOL extra_data = *(OMX_BOOL *)(paramData);

                if (venc_set_extradata(OMX_ExtraDataFrameDimension, extra_data) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_ExtraDataFrameDimension failed");
                    return false;
                }

                extradata = true;
                break;
            }
        case OMX_ExtraDataVideoEncoderSliceInfo:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_ExtraDataVideoEncoderSliceInfo");
                OMX_BOOL extra_data = *(OMX_BOOL *)(paramData);

                if (venc_set_extradata(OMX_ExtraDataVideoEncoderSliceInfo, extra_data) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_ExtraDataVideoEncoderSliceInfo failed");
                    return false;
                }

                extradata = true;
                break;
            }
        case OMX_ExtraDataVideoEncoderMBInfo:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_ExtraDataVideoEncoderMBInfo");
                OMX_BOOL extra_data =  *(OMX_BOOL *)(paramData);

                if (venc_set_extradata(OMX_ExtraDataVideoEncoderMBInfo, extra_data) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_ExtraDataVideoEncoderMBInfo failed");
                    return false;
                }

                extradata = true;
                break;
            }
        case OMX_ExtraDataVideoLTRInfo:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_ExtraDataVideoLTRInfo");
                OMX_BOOL extra_data =  *(OMX_BOOL *)(paramData);
                if (venc_set_extradata(OMX_ExtraDataVideoLTRInfo, extra_data) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_ExtraDataVideoLTRInfo failed");
                    return false;
                }
                extradata = true;
                break;
            }
        case OMX_QcomIndexParamSequenceHeaderWithIDR:
            {
                PrependSPSPPSToIDRFramesParams * pParam =
                    (PrependSPSPPSToIDRFramesParams *)paramData;

                DEBUG_PRINT_LOW("set inband sps/pps: %d", pParam->bEnable);
                if(venc_set_inband_video_header(pParam->bEnable) == false) {
                    DEBUG_PRINT_ERROR("ERROR: set inband sps/pps failed");
                    return false;
                }

                break;
            }
        case OMX_QcomIndexParamAUDelimiter:
            {
                OMX_QCOM_VIDEO_CONFIG_AUD * pParam =
                    (OMX_QCOM_VIDEO_CONFIG_AUD *)paramData;

                DEBUG_PRINT_LOW("set AU Delimiters: %d", pParam->bEnable);
                if(venc_set_au_delimiter(pParam->bEnable) == false) {
                    DEBUG_PRINT_ERROR("ERROR: set AU delimiter failed");
                    return false;
                }

                break;
            }
        case OMX_QcomIndexConfigH264EntropyCodingCabac:
            {
                QOMX_VIDEO_H264ENTROPYCODINGTYPE * pParam =
                    (QOMX_VIDEO_H264ENTROPYCODINGTYPE *)paramData;

                DEBUG_PRINT_LOW("set Entropy info : %d", pParam->bCabac);
                if(venc_set_entropy_config (pParam->bCabac, 0) == false) {
                    DEBUG_PRINT_ERROR("ERROR: set Entropy failed");
                    return false;
                }

                break;
            }
        case OMX_QcomIndexParamH264VUITimingInfo:
            {
                OMX_QCOM_VIDEO_PARAM_VUI_TIMING_INFO *pParam =
                        (OMX_QCOM_VIDEO_PARAM_VUI_TIMING_INFO *)paramData;
                DEBUG_PRINT_LOW("Set VUI timing info: %d", pParam->bEnable);
                if(venc_set_vui_timing_info(pParam->bEnable) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Failed to set vui timing info to %d", pParam->bEnable);
                    return false;
                } else {
                    vui_timing_info.enabled = (unsigned int) pParam->bEnable;
                }
                break;
            }
        case OMX_QTIIndexParamVQZIPSEIType:
            {
                OMX_QTI_VIDEO_PARAM_VQZIP_SEI_TYPE*pParam =
                        (OMX_QTI_VIDEO_PARAM_VQZIP_SEI_TYPE *)paramData;
                DEBUG_PRINT_LOW("Enable VQZIP SEI: %d", pParam->bEnable);
                if(venc_set_vqzip_sei_type(pParam->bEnable) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Failed to set VQZIP SEI type %d", pParam->bEnable);
                    return false;
                }
                break;
            }
        case OMX_QcomIndexParamPeakBitrate:
            {
                OMX_QCOM_VIDEO_PARAM_PEAK_BITRATE *pParam =
                        (OMX_QCOM_VIDEO_PARAM_PEAK_BITRATE *)paramData;
                DEBUG_PRINT_LOW("Set peak bitrate: %u", (unsigned int)pParam->nPeakBitrate);
                if(venc_set_peak_bitrate(pParam->nPeakBitrate) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Failed to set peak bitrate to %u", (unsigned int)pParam->nPeakBitrate);
                    return false;
                } else {
                    peak_bitrate.peakbitrate = (unsigned int) pParam->nPeakBitrate;
                }
                break;
            }
       case OMX_QcomIndexParamSetMVSearchrange:
            {
               DEBUG_PRINT_LOW("venc_set_config: OMX_QcomIndexParamSetMVSearchrange");
               is_searchrange_set = true;
               if (!venc_set_searchrange()) {
                   DEBUG_PRINT_ERROR("ERROR: Failed to set search range");
                   return false;
               }
            }
            break;
        case OMX_QcomIndexParamVideoLTRCount:
            {
                DEBUG_PRINT_LOW("venc_set_param: OMX_QcomIndexParamVideoLTRCount");
                OMX_QCOM_VIDEO_PARAM_LTRCOUNT_TYPE* pParam =
                        (OMX_QCOM_VIDEO_PARAM_LTRCOUNT_TYPE*)paramData;
                if (pParam->nCount > 0) {
                    if (venc_set_ltrmode(1, pParam->nCount) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Enable LTR mode failed");
                        return false;
                    }
                } else {
                    if (venc_set_ltrmode(0, 0) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Disable LTR mode failed");
                        return false;
                    }
                }
                break;
            }
        case OMX_QcomIndexParamVideoHybridHierpMode:
            {
                QOMX_EXTNINDEX_VIDEO_HYBRID_HP_MODE* pParam =
                        (QOMX_EXTNINDEX_VIDEO_HYBRID_HP_MODE*)paramData;
                OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE temporalParams;
                OMX_U32 i = 0, cumulativeBitrate = 0, cumulativeRatio = 0;
                OMX_QCOM_VIDEO_PARAM_IPB_QPRANGETYPE qp_range;

                memset(&temporalParams, 0, sizeof(temporalParams));
                temporalParams.nPLayerCountActual   = pParam->nHpLayers;
                temporalParams.ePattern             = OMX_VIDEO_AndroidTemporalLayeringPatternAndroid;
                temporalParams.nBLayerCountActual   = 0;
                temporalParams.nLayerCountMax       = pParam->nHpLayers;
                temporalParams.nBLayerCountMax      = 0;

                if (!venc_convert_abs2cum_bitrate(pParam, temporalParams)) {
                    DEBUG_PRINT_ERROR("Failed to convert Hybrid param to internal struct");
                    return false;
                }

                if (!venc_validate_temporal_extn(temporalParams)) {
                    DEBUG_PRINT_ERROR("Invalid settings for Hybrid HP");
                    return false;
                }

                venc_copy_temporal_settings(temporalParams);

                if (!venc_set_intra_period(pParam->nKeyFrameInterval, 0)) {
                   DEBUG_PRINT_ERROR("Failed to set Intraperiod: %d", pParam->nKeyFrameInterval);
                   return false;
                }

                temporal_layers_config.nKeyFrameInterval = intra_period.num_pframes;

                qp_range.minIQP = pParam->nMinQuantizer;
                qp_range.maxIQP = pParam->nMaxQuantizer;
                qp_range.minPQP = pParam->nMinQuantizer;
                qp_range.maxPQP = pParam->nMaxQuantizer;
                qp_range.minBQP = pParam->nMinQuantizer;
                qp_range.maxBQP = pParam->nMaxQuantizer;
                if(!venc_set_session_qp_range (&qp_range)) {
                    DEBUG_PRINT_ERROR("ERROR: Setting QP Range for hybridHP [%u %u] failed",
                        (unsigned int)pParam->nMinQuantizer, (unsigned int)pParam->nMaxQuantizer);
                    return false;
                }
                temporal_layers_config.nMinQuantizer = pParam->nMinQuantizer;
                temporal_layers_config.nMaxQuantizer = pParam->nMaxQuantizer;
                break;
            }
        case OMX_QcomIndexParamBatchSize:
            {
                OMX_PARAM_U32TYPE* pParam =
                    (OMX_PARAM_U32TYPE*)paramData;

                if (pParam->nPortIndex == PORT_INDEX_OUT) {
                    DEBUG_PRINT_ERROR("For the moment, client-driven batching not supported"
                            " on output port");
                    return false;
                }

                if (!venc_set_batch_size(pParam->nU32)) {
                     DEBUG_PRINT_ERROR("Failed setting batch size as %d", pParam->nU32);
                     return false;
                }
                break;
            }
        case OMX_QcomIndexParamVencAspectRatio:
            {
                if (!venc_set_aspectratio(paramData)) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_QcomIndexParamVencAspectRatio failed");
                    return false;
                }
                break;
            }
        case OMX_QTIIndexParamVideoEnableRoiInfo:
            {
                struct v4l2_control control;
                OMX_QTI_VIDEO_PARAM_ENABLE_ROIINFO *pParam =
                    (OMX_QTI_VIDEO_PARAM_ENABLE_ROIINFO *)paramData;
                if (pParam->bEnableRoiInfo == OMX_FALSE) {
                    DEBUG_PRINT_INFO("OMX_QTIIndexParamVideoEnableRoiInfo: bEnableRoiInfo is false");
                    break;
                }
                if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264 &&
                        m_sVenc_cfg.codectype != V4L2_PIX_FMT_HEVC) {
                    DEBUG_PRINT_ERROR("OMX_QTIIndexParamVideoEnableRoiInfo is not supported for %lu codec", m_sVenc_cfg.codectype);
                    return false;
                }
                control.id = V4L2_CID_MPEG_VIDC_VIDEO_EXTRADATA;
                control.value = V4L2_MPEG_VIDC_EXTRADATA_ROI_QP;
                DEBUG_PRINT_LOW("Setting param OMX_QTIIndexParamVideoEnableRoiInfo");
                if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_QTIIndexParamVideoEnableRoiInfo failed");
                    return false;
                }
                m_roi_enabled = true;
                break;
            }
        case OMX_QTIIndexParamLowLatencyMode:
            {
                QOMX_EXTNINDEX_VIDEO_LOW_LATENCY_MODE *pParam =
                    (QOMX_EXTNINDEX_VIDEO_LOW_LATENCY_MODE*)paramData;

                if (!venc_set_lowlatency_mode(pParam->bEnableLowLatencyMode)) {
                     DEBUG_PRINT_ERROR("Setting low latency mode failed");
                     return false;
                }
                break;
            }
        case OMX_IndexParamAndroidVideoTemporalLayering:
            {
                OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE hierData;
                memcpy(&hierData, paramData, sizeof(hierData));

                if (!venc_validate_temporal_extn(hierData)) {
                    DEBUG_PRINT_ERROR("set_param: Failed to validate temporal settings");
                    return false;
                }

                venc_copy_temporal_settings(hierData);

                break;
            }
        case OMX_QTIIndexParamDisablePQ:
            {
                QOMX_DISABLETYPE * pParam = (QOMX_DISABLETYPE *)paramData;
                DEBUG_PRINT_LOW("venc_set_param: OMX_QTIIndexParamDisablePQ: %d", pParam->bDisable);
                break;
            }
        case OMX_QTIIndexParamIframeSizeType:
            {
                QOMX_VIDEO_IFRAMESIZE* pParam =
                    (QOMX_VIDEO_IFRAMESIZE *)paramData;
                isCBR = rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CBR_VFR ||
                    rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CBR_CFR;
                if (!isCBR) {
                    DEBUG_PRINT_ERROR("venc_set_param: OMX_QTIIndexParamIframeSizeType not allowed for this configuration isCBR(%d)",
                        isCBR);
                    return false;
                }
                if (!venc_set_iframesize_type(pParam->eType)) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_QTIIndexParamIframeSizeType failed");
                    return false;
                }
                break;
            }
        case OMX_QTIIndexParamEnableAVTimerTimestamps:
            {
                QOMX_ENABLETYPE *pParam = (QOMX_ENABLETYPE *)paramData;
                mUseAVTimerTimestamps = pParam->bEnable == OMX_TRUE;
                DEBUG_PRINT_INFO("AVTimer timestamps enabled");
                break;
            }
        case OMX_QcomIndexParamVideoDownScalar:
            {
                QOMX_INDEXDOWNSCALAR *pParam = (QOMX_INDEXDOWNSCALAR *)paramData;
                downscalar_enabled = pParam->bEnable;

                DEBUG_PRINT_INFO("Downscalar settings: Enabled : %d Width : %u Height %u",
                    pParam->bEnable, pParam->nOutputWidth, pParam->nOutputHeight);
                if (downscalar_enabled) {
                    m_sVenc_cfg.dvs_width = pParam->nOutputWidth;
                    m_sVenc_cfg.dvs_height = pParam->nOutputHeight;

                    memset(&fmt, 0, sizeof(fmt));
                    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
                    fmt.fmt.pix_mp.width = pParam->nOutputWidth;
                    fmt.fmt.pix_mp.height = pParam->nOutputHeight;
                    fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;
                    if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
                        DEBUG_PRINT_ERROR("Failed to set format on capture port");
                        return false;
                    }
                    m_sOutput_buff_property.datasize = fmt.fmt.pix_mp.plane_fmt[0].sizeimage;
                }
                break;
            }
        case OMX_QTIIndexParamColorSpaceConversion:
            {
                QOMX_ENABLETYPE *pParam = (QOMX_ENABLETYPE *)paramData;
                csc_enable = pParam->bEnable;
                DEBUG_PRINT_INFO("CSC settings: Enabled : %d ", pParam->bEnable);
                break;
            }
        default:
            DEBUG_PRINT_ERROR("ERROR: Unsupported parameter in venc_set_param: %u",
                    index);
            break;
    }

    return true;
}

bool venc_dev::venc_set_config(void *configData, OMX_INDEXTYPE index)
{

    DEBUG_PRINT_LOW("Inside venc_set_config");

    switch ((int)index) {
        case OMX_IndexConfigVideoBitrate:
            {
                OMX_VIDEO_CONFIG_BITRATETYPE *bit_rate = (OMX_VIDEO_CONFIG_BITRATETYPE *)
                    configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_IndexConfigVideoBitrate");

                if (bit_rate->nPortIndex == (OMX_U32)PORT_INDEX_OUT) {
                    if (venc_set_target_bitrate(bit_rate->nEncodeBitrate) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Target Bit rate failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexConfigVideoBitrate");
                }

                break;
            }
        case OMX_IndexConfigVideoFramerate:
            {
                OMX_CONFIG_FRAMERATETYPE *frame_rate = (OMX_CONFIG_FRAMERATETYPE *)
                    configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_IndexConfigVideoFramerate");

                if (frame_rate->nPortIndex == (OMX_U32)PORT_INDEX_OUT) {
                    if (venc_set_encode_framerate(frame_rate->xEncodeFramerate) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Encode Framerate failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexConfigVideoFramerate");
                }

                break;
            }
        case QOMX_IndexConfigVideoIntraperiod:
            {
                DEBUG_PRINT_LOW("venc_set_param:QOMX_IndexConfigVideoIntraperiod");
                QOMX_VIDEO_INTRAPERIODTYPE *intraperiod =
                    (QOMX_VIDEO_INTRAPERIODTYPE *)configData;

                if (intraperiod->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    if (venc_set_intra_period(intraperiod->nPFrames, intraperiod->nBFrames) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Request for setting intra period failed");
                        return false;
                    }

                    if (venc_set_idr_period(intraperiod->nIDRPeriod) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting idr period failed");
                        return false;
                    }
                }
                client_req_disable_bframe = (intraperiod->nBFrames == 0) ? true : false;

                break;
            }
        case OMX_IndexConfigVideoIntraVOPRefresh:
            {
                OMX_CONFIG_INTRAREFRESHVOPTYPE *intra_vop_refresh = (OMX_CONFIG_INTRAREFRESHVOPTYPE *)
                    configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_IndexConfigVideoIntraVOPRefresh");

                if (intra_vop_refresh->nPortIndex == (OMX_U32)PORT_INDEX_OUT) {
                    if (venc_set_intra_vop_refresh(intra_vop_refresh->IntraRefreshVOP) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting Encode Framerate failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexConfigVideoFramerate");
                }

                break;
            }
        case OMX_IndexConfigCommonMirror:
            {
                OMX_CONFIG_MIRRORTYPE *mirror = (OMX_CONFIG_MIRRORTYPE*) configData;
                DEBUG_PRINT_LOW("venc_set_param: OMX_IndexConfigCommonMirror");

                if (venc_set_mirror(mirror->eMirror) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting OMX_IndexConfigCommonMirror failed");
                    return false;
                }
                break;
            }
        case OMX_IndexConfigCommonRotate:
            {
                OMX_CONFIG_ROTATIONTYPE *config_rotation =
                    reinterpret_cast<OMX_CONFIG_ROTATIONTYPE*>(configData);
                OMX_U32 nFrameWidth;
                if (!config_rotation) {
                   return false;
                }
                if (true == deinterlace_enabled) {
                    DEBUG_PRINT_ERROR("ERROR: Rotation is not supported with deinterlacing");
                    return false;
                }
                if(venc_set_vpe_rotation(config_rotation->nRotation) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Dimension Change for Rotation failed");
                    return false;
                }

                break;
            }
        case OMX_IndexConfigVideoAVCIntraPeriod:
            {
                OMX_VIDEO_CONFIG_AVCINTRAPERIOD *avc_iperiod = (OMX_VIDEO_CONFIG_AVCINTRAPERIOD*) configData;
                DEBUG_PRINT_LOW("venc_set_param: OMX_IndexConfigVideoAVCIntraPeriod");

                if (venc_set_intra_period(avc_iperiod->nPFrames, intra_period.num_bframes) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting intra period failed");
                    return false;
                }
                if (venc_set_idr_period(avc_iperiod->nIDRPeriod) == false) {
                    DEBUG_PRINT_ERROR("ERROR: Setting idr period failed");
                    return false;
                }
                break;
            }
        case OMX_IndexConfigVideoVp8ReferenceFrame:
            {
                OMX_VIDEO_VP8REFERENCEFRAMETYPE* vp8refframe = (OMX_VIDEO_VP8REFERENCEFRAMETYPE*) configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_IndexConfigVideoVp8ReferenceFrame");
                if ((vp8refframe->nPortIndex == (OMX_U32)PORT_INDEX_IN) &&
                        (vp8refframe->bUseGoldenFrame)) {
                    if(venc_set_useltr(0x1) == false) {
                        DEBUG_PRINT_ERROR("ERROR: use goldenframe failed");
                        return false;
                    }
                } else if((vp8refframe->nPortIndex == (OMX_U32)PORT_INDEX_IN) &&
                        (vp8refframe->bGoldenFrameRefresh)) {
                    if(venc_set_markltr(0x1) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Setting goldenframe failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexConfigVideoVp8ReferenceFrame");
                }
                break;
            }
        case OMX_QcomIndexConfigVideoLTRUse:
            {
                OMX_QCOM_VIDEO_CONFIG_LTRUSE_TYPE* pParam = (OMX_QCOM_VIDEO_CONFIG_LTRUSE_TYPE*)configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_QcomIndexConfigVideoLTRUse");
                if (pParam->nPortIndex == (OMX_U32)PORT_INDEX_IN) {
                    if (venc_set_useltr(pParam->nID) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Use LTR failed");
                        return false;
                    }
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_QcomIndexConfigVideoLTRUse");
                }
                break;
            }
        case OMX_QcomIndexConfigVideoLTRMark:
            {
                OMX_QCOM_VIDEO_CONFIG_LTRMARK_TYPE* pParam = (OMX_QCOM_VIDEO_CONFIG_LTRMARK_TYPE*)configData;
                DEBUG_PRINT_LOW("venc_set_config: OMX_QcomIndexConfigVideoLTRMark");
                if (pParam->nPortIndex == (OMX_U32)PORT_INDEX_IN) {
                    if (venc_set_markltr(pParam->nID) == false) {
                        DEBUG_PRINT_ERROR("ERROR: Mark LTR failed");
                        return false;
                    }
                }  else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_QcomIndexConfigVideoLTRMark");
                }
                break;
            }
        case OMX_IndexConfigAndroidVideoTemporalLayering:
            {
                OMX_VIDEO_CONFIG_ANDROID_TEMPORALLAYERINGTYPE *pParam =
                    (OMX_VIDEO_CONFIG_ANDROID_TEMPORALLAYERINGTYPE *) configData;
                OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE temporalParams;

                memset(&temporalParams, 0x0, sizeof(temporalParams));
                temporalParams.nPLayerCountActual      = pParam->nPLayerCountActual;
                temporalParams.bBitrateRatiosSpecified = pParam->bBitrateRatiosSpecified;
                temporalParams.ePattern                = pParam->ePattern;
                temporalParams.nLayerCountMax          = temporal_layers_config.nMaxLayers;

                if (temporalParams.bBitrateRatiosSpecified == OMX_TRUE) {
                    for (OMX_U32 i = 0; i < temporalParams.nPLayerCountActual; ++i) {
                        temporalParams.nBitrateRatios[i] = pParam->nBitrateRatios[i];
                    }
                }

                if (!venc_validate_temporal_extn(temporalParams)) {
                    DEBUG_PRINT_ERROR("set_param: Failed to validate temporal settings");
                    return false;
                }

                venc_copy_temporal_settings(temporalParams);

                // Set temporal layers with these settings
                if (!venc_reconfigure_temporal_settings()) {
                    DEBUG_PRINT_ERROR("Reconfiguring temporal settings failed");
                    return false;
                }

                break;
            }
        case OMX_QcomIndexConfigBaseLayerId:
            {
                OMX_SKYPE_VIDEO_CONFIG_BASELAYERPID* pParam =
                    (OMX_SKYPE_VIDEO_CONFIG_BASELAYERPID*) configData;
                if (venc_set_baselayerid(pParam->nPID) == false) {
                    DEBUG_PRINT_ERROR("Failed to set OMX_QcomIndexConfigBaseLayerId failed");
                    return false;
                }
                break;
            }
        case OMX_IndexParamAndroidVideoTemporalLayering:
            {
                DEBUG_PRINT_ERROR("TemporalLayer: Changing layer-configuration dynamically is not supported!");
                return false;
            }
        case OMX_QcomIndexConfigQp:
            {
                OMX_QCOM_VIDEO_CONFIG_QP* pParam =
                    (OMX_QCOM_VIDEO_CONFIG_QP*) configData;
                DEBUG_PRINT_LOW("Set_config: nQP %d", pParam->nQP);
                if (venc_set_qp(pParam->nQP,
                                pParam->nQP,
                                pParam->nQP,
                                ENABLE_I_QP | ENABLE_P_QP | ENABLE_B_QP ) == false) {
                    DEBUG_PRINT_ERROR("Failed to set OMX_QcomIndexConfigQp failed");
                    return false;
                }
                break;
            }
        case OMX_IndexConfigPriority:
            {
                OMX_PARAM_U32TYPE *priority = (OMX_PARAM_U32TYPE *)configData;
                DEBUG_PRINT_LOW("Set_config: priority %d",priority->nU32);
                if (!venc_set_priority(priority->nU32)) {
                    DEBUG_PRINT_ERROR("Failed to set priority");
                    return false;
                }
                break;
            }
        case OMX_IndexConfigOperatingRate:
            {
                OMX_PARAM_U32TYPE *rate = (OMX_PARAM_U32TYPE *)configData;
                DEBUG_PRINT_LOW("Set_config: operating rate %u", rate->nU32);
                if (!venc_set_operatingrate(rate->nU32)) {
                    DEBUG_PRINT_ERROR("Failed to set operating rate");
                    return false;
                }
                break;
            }
        case OMX_IndexConfigAndroidIntraRefresh:
            {
                OMX_VIDEO_CONFIG_ANDROID_INTRAREFRESHTYPE *intra_refresh_period = (OMX_VIDEO_CONFIG_ANDROID_INTRAREFRESHTYPE *)configData;
                DEBUG_PRINT_LOW("OMX_IndexConfigAndroidIntraRefresh : num frames = %d", intra_refresh_period->nRefreshPeriod);

                if (intra_refresh_period->nPortIndex == (OMX_U32) PORT_INDEX_OUT) {
                    intra_refresh.framecount = intra_refresh_period->nRefreshPeriod;
                    intra_refresh.irmode     = OMX_VIDEO_IntraRefreshMax;
                    intra_refresh.mbcount    = 0;
                } else {
                    DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_IndexConfigVideoIntraRefreshType");
                }
                break;
            }
        case OMX_QTIIndexConfigVideoBlurResolution:
        {
             OMX_QTI_VIDEO_CONFIG_BLURINFO *blur = (OMX_QTI_VIDEO_CONFIG_BLURINFO *)configData;
             if (blur->nPortIndex == (OMX_U32)PORT_INDEX_IN) {
                 DEBUG_PRINT_LOW("Set_config: blur resolution: %d", blur->eTargetResol);
                 if(!venc_set_blur_resolution(blur)) {
                    DEBUG_PRINT_ERROR("Failed to set Blur Resolution");
                    return false;
                 }
             } else {
                  DEBUG_PRINT_ERROR("ERROR: Invalid Port Index for OMX_QTIIndexConfigVideoBlurResolution");
                  return false;
             }
             break;
        }
        case OMX_QcomIndexConfigH264Transform8x8:
        {
            OMX_CONFIG_BOOLEANTYPE *pEnable = (OMX_CONFIG_BOOLEANTYPE *) configData;
            DEBUG_PRINT_LOW("venc_set_config: OMX_QcomIndexConfigH264Transform8x8");
            if (venc_h264_transform_8x8(pEnable->bEnabled) == false) {
                DEBUG_PRINT_ERROR("Failed to set OMX_QcomIndexConfigH264Transform8x8");
                return false;
            }
            break;
        }
        case OMX_QTIIndexConfigDescribeColorAspects:
            {
                DescribeColorAspectsParams *params = (DescribeColorAspectsParams *)configData;

                OMX_U32 color_space = MSM_VIDC_BT601_6_625;
                OMX_U32 full_range = 0;
                OMX_U32 matrix_coeffs = MSM_VIDC_MATRIX_601_6_625;
                OMX_U32 transfer_chars = MSM_VIDC_TRANSFER_601_6_625;

                switch((ColorAspects::Primaries)(params->sAspects.mPrimaries)) {
                    case ColorAspects::PrimariesBT709_5:
                        color_space = MSM_VIDC_BT709_5;
                        break;
                    case ColorAspects::PrimariesBT470_6M:
                        color_space = MSM_VIDC_BT470_6_M;
                        break;
                    case ColorAspects::PrimariesBT601_6_625:
                        color_space = MSM_VIDC_BT601_6_625;
                        break;
                    case ColorAspects::PrimariesBT601_6_525:
                        color_space = MSM_VIDC_BT601_6_525;
                        break;
                    case ColorAspects::PrimariesGenericFilm:
                        color_space = MSM_VIDC_GENERIC_FILM;
                        break;
                    case ColorAspects::PrimariesBT2020:
                        color_space = MSM_VIDC_BT2020;
                        break;
                    default:
                        color_space = MSM_VIDC_BT601_6_625;
                        //params->sAspects.mPrimaries = ColorAspects::PrimariesBT601_6_625;
                        break;
                }
                switch((ColorAspects::Range)params->sAspects.mRange) {
                    case ColorAspects::RangeFull:
                        full_range = 1;
                        break;
                    case ColorAspects::RangeLimited:
                        full_range = 0;
                        break;
                    default:
                        break;
                }
                switch((ColorAspects::Transfer)params->sAspects.mTransfer) {
                    case ColorAspects::TransferSMPTE170M:
                        transfer_chars = MSM_VIDC_TRANSFER_601_6_525;
                        break;
                    case ColorAspects::TransferUnspecified:
                        transfer_chars = MSM_VIDC_TRANSFER_UNSPECIFIED;
                        break;
                    case ColorAspects::TransferGamma22:
                        transfer_chars = MSM_VIDC_TRANSFER_BT_470_6_M;
                        break;
                    case ColorAspects::TransferGamma28:
                        transfer_chars = MSM_VIDC_TRANSFER_BT_470_6_BG;
                        break;
                    case ColorAspects::TransferSMPTE240M:
                        transfer_chars = MSM_VIDC_TRANSFER_SMPTE_240M;
                        break;
                    case ColorAspects::TransferLinear:
                        transfer_chars = MSM_VIDC_TRANSFER_LINEAR;
                        break;
                    case ColorAspects::TransferXvYCC:
                        transfer_chars = MSM_VIDC_TRANSFER_IEC_61966;
                        break;
                    case ColorAspects::TransferBT1361:
                        transfer_chars = MSM_VIDC_TRANSFER_BT_1361;
                        break;
                    case ColorAspects::TransferSRGB:
                        transfer_chars = MSM_VIDC_TRANSFER_SRGB;
                        break;
                    default:
                        //params->sAspects.mTransfer = ColorAspects::TransferSMPTE170M;
                        transfer_chars = MSM_VIDC_TRANSFER_601_6_625;
                        break;
                }
                switch((ColorAspects::MatrixCoeffs)params->sAspects.mMatrixCoeffs) {
                    case ColorAspects::MatrixUnspecified:
                        matrix_coeffs = MSM_VIDC_MATRIX_UNSPECIFIED;
                        break;
                    case ColorAspects::MatrixBT709_5:
                        matrix_coeffs = MSM_VIDC_MATRIX_BT_709_5;
                        break;
                    case ColorAspects::MatrixBT470_6M:
                        matrix_coeffs = MSM_VIDC_MATRIX_FCC_47;
                        break;
                    case ColorAspects::MatrixBT601_6:
                        matrix_coeffs = MSM_VIDC_MATRIX_601_6_525;
                        break;
                    case ColorAspects::MatrixSMPTE240M:
                        matrix_coeffs = MSM_VIDC_MATRIX_SMPTE_240M;
                        break;
                    case ColorAspects::MatrixBT2020:
                        matrix_coeffs = MSM_VIDC_MATRIX_BT_2020;
                        break;
                    case ColorAspects::MatrixBT2020Constant:
                        matrix_coeffs = MSM_VIDC_MATRIX_BT_2020_CONST;
                        break;
                    default:
                        //params->sAspects.mMatrixCoeffs = ColorAspects::MatrixBT601_6;
                        matrix_coeffs = MSM_VIDC_MATRIX_601_6_625;
                        break;
                }
                if (!venc_set_colorspace(color_space, full_range,
                            transfer_chars, matrix_coeffs)) {

                    DEBUG_PRINT_ERROR("Failed to set operating rate");
                    return false;
                }
                break;
            }
        case OMX_QTIIndexConfigVideoRoiInfo:
        {
            if(!venc_set_roi_qp_info((OMX_QTI_VIDEO_CONFIG_ROIINFO *)configData)) {
                DEBUG_PRINT_ERROR("Failed to set ROI QP info");
                return false;
            }
            break;
        }
        default:
            DEBUG_PRINT_ERROR("Unsupported config index = %u", index);
            break;
    }

    return true;
}

bool venc_dev::venc_handle_empty_eos_buffer( void)
{
    struct v4l2_encoder_cmd enc;
    int rc = 0;

    if (!streaming[OUTPUT_PORT]) {
        enum v4l2_buf_type buf_type;
        struct v4l2_control control;
        int ret = 0;

        /* If first ETB is an EOS with 0 filled length there is a limitation */
        /* for which we need to combine sequence header with 1st frame       */
        control.id = V4L2_CID_MPEG_VIDEO_HEADER_MODE;
        control.value = V4L2_MPEG_VIDEO_HEADER_MODE_JOINED_WITH_1ST_FRAME;
        DEBUG_PRINT_LOW("Combining sequence header with 1st frame ");
        ret = ioctl(m_nDriver_fd,  VIDIOC_S_CTRL, &control);
        if (ret) {
            DEBUG_PRINT_ERROR("Failed to set header mode");
            return false;
        }

        buf_type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;

        DEBUG_PRINT_HIGH("Calling streamon before issuing stop command for EOS");
        ret = ioctl(m_nDriver_fd, VIDIOC_STREAMON, &buf_type);
        if (ret) {
            DEBUG_PRINT_ERROR("Failed to call streamon");
            if (errno == EBUSY) {
                hw_overload = true;
            }
            return false;
        } else {
            streaming[OUTPUT_PORT] = true;
        }
    }

    memset(&enc, 0, sizeof(enc));
    enc.cmd = V4L2_ENC_CMD_STOP;
    DEBUG_PRINT_LOW("Sending : Encoder STOP comamnd");
    rc = ioctl(m_nDriver_fd, VIDIOC_ENCODER_CMD, &enc);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed : Encoder STOP comamnd");
        return false;
    }
    return true;
}

unsigned venc_dev::venc_stop( void)
{
    struct venc_msg venc_msg;
    struct v4l2_requestbuffers bufreq;
    int rc = 0, ret = 0;

    if (!stopped) {
        enum v4l2_buf_type cap_type;

        if (streaming[OUTPUT_PORT]) {
            cap_type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
            rc = ioctl(m_nDriver_fd, VIDIOC_STREAMOFF, &cap_type);

            if (rc) {
                DEBUG_PRINT_ERROR("Failed to call streamoff on driver: capability: %d, %d",
                        cap_type, rc);
            } else
                streaming[OUTPUT_PORT] = false;

            DEBUG_PRINT_LOW("Releasing registered buffers from driver on o/p port");
            bufreq.memory = V4L2_MEMORY_USERPTR;
            bufreq.count = 0;
            bufreq.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
            ret = ioctl(m_nDriver_fd, VIDIOC_REQBUFS, &bufreq);

            if (ret) {
                DEBUG_PRINT_ERROR("ERROR: VIDIOC_REQBUFS OUTPUT MPLANE Failed");
                return false;
            }
        }

        if (!rc && streaming[CAPTURE_PORT]) {
            cap_type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
            rc = ioctl(m_nDriver_fd, VIDIOC_STREAMOFF, &cap_type);

            if (rc) {
                DEBUG_PRINT_ERROR("Failed to call streamoff on driver: capability: %d, %d",
                        cap_type, rc);
            } else
                streaming[CAPTURE_PORT] = false;

            DEBUG_PRINT_LOW("Releasing registered buffers from driver on capture port");
            bufreq.memory = V4L2_MEMORY_USERPTR;
            bufreq.count = 0;
            bufreq.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
            ret = ioctl(m_nDriver_fd, VIDIOC_REQBUFS, &bufreq);

            if (ret) {
                DEBUG_PRINT_ERROR("ERROR: VIDIOC_REQBUFS CAPTURE MPLANE Failed");
                return false;
            }
        }

        if (!rc && !ret) {
            venc_stop_done();
            stopped = 1;
            /*set flag to re-configure when started again*/
            resume_in_stopped = 1;
        }
    }

    return rc;
}

bool venc_dev::is_streamon_done(OMX_U32 port)
{
    return streaming[port];
}

unsigned venc_dev::venc_pause(void)
{
    pthread_mutex_lock(&pause_resume_mlock);
    paused = true;
    pthread_mutex_unlock(&pause_resume_mlock);
    return 0;
}

unsigned venc_dev::venc_resume(void)
{
    pthread_mutex_lock(&pause_resume_mlock);
    paused = false;
    pthread_mutex_unlock(&pause_resume_mlock);

    return pthread_cond_signal(&pause_resume_cond);
}

unsigned venc_dev::venc_start_done(void)
{
    struct venc_msg venc_msg;
    venc_msg.msgcode = VEN_MSG_START;
    venc_msg.statuscode = VEN_S_SUCCESS;
    venc_handle->async_message_process(venc_handle,&venc_msg);
    return 0;
}

unsigned venc_dev::venc_stop_done(void)
{
    struct venc_msg venc_msg;
    free_extradata_all();
    venc_msg.msgcode=VEN_MSG_STOP;
    venc_msg.statuscode=VEN_S_SUCCESS;
    venc_handle->async_message_process(venc_handle,&venc_msg);
    return 0;
}

unsigned venc_dev::venc_set_message_thread_id(pthread_t tid)
{
    async_thread_created = true;
    m_tid=tid;
    return 0;
}

bool venc_dev::venc_set_vqzip_defaults()
{
    struct v4l2_control control;
    int rc = 0, num_mbs_per_frame;

    num_mbs_per_frame = m_sVenc_cfg.input_height * m_sVenc_cfg.input_width;

    switch (num_mbs_per_frame) {
    case OMX_CORE_720P_WIDTH  * OMX_CORE_720P_HEIGHT:
    case OMX_CORE_1080P_WIDTH * OMX_CORE_1080P_HEIGHT:
    case OMX_CORE_4KUHD_WIDTH * OMX_CORE_4KUHD_HEIGHT:
    case OMX_CORE_4KDCI_WIDTH * OMX_CORE_4KDCI_HEIGHT:
        break;
    default:
        DEBUG_PRINT_ERROR("VQZIP is not supported for this resoultion : %lu X %lu",
            m_sVenc_cfg.input_width, m_sVenc_cfg.input_height);
        return false;
    }

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL;
    control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_OFF;
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc)
        DEBUG_PRINT_ERROR("Failed to set Rate Control OFF for VQZIP");
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES;
    control.value = INT_MAX;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc)
        DEBUG_PRINT_ERROR("Failed to set P frame period for VQZIP");

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES;
    control.value = 0;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc)
        DEBUG_PRINT_ERROR("Failed to set B frame period for VQZIP");

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_IDR_PERIOD;
    control.value = 1;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc)
        DEBUG_PRINT_ERROR("Failed to set IDR period for VQZIP");

    return true;
}

unsigned venc_dev::venc_start(void)
{
    enum v4l2_buf_type buf_type;
    int ret, r;
    struct v4l2_control control;

    memset(&control, 0, sizeof(control));

    if (vqzip_sei_info.enabled && !venc_set_vqzip_defaults())
        return 1;

    if (!venc_set_priority(sess_priority.priority)) {
        DEBUG_PRINT_ERROR("Failed to set priority");
        return 1;
    }
    // Client can set intra refresh period in terms of frames.
    // This requires reconfiguration on port redefinition as
    // mbcount for IR depends on encode resolution.
    if (!venc_reconfigure_intra_refresh_period()) {
        DEBUG_PRINT_ERROR("Reconfiguring intra refresh period failed");
        return 1;
    }

    if (!venc_reconfigure_intra_period()) {
        DEBUG_PRINT_ERROR("Reconfiguring intra period failed");
        return 1;
    }

    // re-configure the temporal layers as RC-mode and key-frame interval
    // might have changed since the client last configured the layers.
    if (!venc_reconfigure_temporal_settings()) {
        DEBUG_PRINT_ERROR("Reconfiguring temporal settings failed");
        return 1;
    }

    // Note HP settings could change GOP structure
    if (!venc_set_intra_period(intra_period.num_pframes, intra_period.num_bframes)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to set nPframes/nBframes");
        return OMX_ErrorUndefined;
    }

    // If Hybrid HP is enable, LTR needs to be reset
    if (!venc_reconfigure_ltrmode()) {
        DEBUG_PRINT_ERROR("Reconfiguring LTR mode failed");
        return 1;
    }

    venc_config_print();

    /* set buffercount before start */
    venc_reconfig_reqbufs();
    resume_in_stopped = 0;

    /* Check if slice_delivery mode is enabled & max slices is sufficient for encoding complete frame */
    if (slice_mode.enable && multislice.mslice_size &&
            (m_sVenc_cfg.dvs_width *  m_sVenc_cfg.dvs_height)/(256 * multislice.mslice_size) >= MAX_SUPPORTED_SLICES_PER_FRAME) {
        DEBUG_PRINT_ERROR("slice_mode: %lu, max slices (%lu) should be less than (%d)", slice_mode.enable,
                (m_sVenc_cfg.dvs_width *  m_sVenc_cfg.dvs_height)/(256 * multislice.mslice_size),
                MAX_SUPPORTED_SLICES_PER_FRAME);
        return 1;
    }

    if(m_codec == OMX_VIDEO_CodingImageHEIC && mIsGridset) {
        struct v4l2_format fmt;
        memset(&fmt, 0, sizeof(fmt));
        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        fmt.fmt.pix_mp.height = mTileDimension;
        fmt.fmt.pix_mp.width = mTileDimension;
        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;
        DEBUG_PRINT_INFO("set format type %d, wxh %dx%d, pixelformat %#x",
                 fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                 fmt.fmt.pix_mp.pixelformat);
        if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
            DEBUG_PRINT_ERROR("set format failed, type %d, wxh %dx%d, pixelformat %#x",
                 fmt.type, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                 fmt.fmt.pix_mp.pixelformat);
            hw_overload = errno == EBUSY;
            return false;
        }


    }

    buf_type=V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    DEBUG_PRINT_LOW("send_command_proxy(): Idle-->Executing");
    ret=ioctl(m_nDriver_fd, VIDIOC_STREAMON,&buf_type);

    if (ret)
        return 1;

    streaming[CAPTURE_PORT] = true;

    stopped = 0;
    return 0;
}

inline const char* hiermode_string(int val)
{
    switch(val)
    {
    case HIER_NONE:
        return "No Hier";
    case HIER_P:
        return "Hier-P";
    case HIER_B:
        return "Hier-B";
    case HIER_P_HYBRID:
        return "Hybrid Hier-P";
    default:
        return "No hier";
    }
}

inline const char* bitrate_type_string(int val)
{
    switch(val)
    {
    case V4L2_CID_MPEG_VIDC_VIDEO_VENC_BITRATE_DISABLE:
        return "CUMULATIVE";
    case V4L2_CID_MPEG_VIDC_VIDEO_VENC_BITRATE_ENABLE:
        return "LAYER WISE";
    default:
        return "Unknown Bitrate Type";
    }
}

static const char *codec_as_string(unsigned long codec) {
    switch (codec) {
    case V4L2_PIX_FMT_H264:
        return "H264";
    case V4L2_PIX_FMT_HEVC:
        return "HEVC";
    case V4L2_PIX_FMT_VP8:
        return "VP8";
    default:
        return "UNKOWN";
    }
}

void venc_dev::venc_config_print()
{

    DEBUG_PRINT_HIGH("ENC_CONFIG: Codec: %s, Profile %ld, level : %ld",
            codec_as_string(m_sVenc_cfg.codectype), codec_profile.profile, profile_level.level);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Input Width: %ld, Height:%ld, Fps: %ld",
            m_sVenc_cfg.input_width, m_sVenc_cfg.input_height,
            m_sVenc_cfg.fps_num/m_sVenc_cfg.fps_den);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Output Width: %ld, Height:%ld, Fps: %ld",
            m_sVenc_cfg.dvs_width, m_sVenc_cfg.dvs_height,
            m_sVenc_cfg.fps_num/m_sVenc_cfg.fps_den);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Color Space: Primaries = %u, Range = %u, Transfer Chars = %u, Matrix Coeffs = %u",
            color_space.primaries, color_space.range, color_space.transfer_chars, color_space.matrix_coeffs);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Bitrate: %ld, RC: %ld, P - Frames : %ld, B - Frames = %ld",
            bitrate.target_bitrate, rate_ctrl.rcmode, intra_period.num_pframes, intra_period.num_bframes);

    DEBUG_PRINT_HIGH("ENC_CONFIG: qpI: %ld, qpP: %ld, qpb: %ld enableqp : %ld",
            session_qp.iframeqp, session_qp.pframeqp, session_qp.bframeqp, session_qp.enableqp);

    DEBUG_PRINT_HIGH("ENC_CONFIG: minIQP: %lu, maxIQP: %lu minPQP : %lu maxPQP : %lu minBQP : %lu maxBQP : %lu",
            session_ipb_qp_values.min_i_qp, session_ipb_qp_values.max_i_qp,
            session_ipb_qp_values.min_p_qp, session_ipb_qp_values.max_p_qp,
            session_ipb_qp_values.min_b_qp, session_ipb_qp_values.max_b_qp);

    DEBUG_PRINT_HIGH("ENC_CONFIG: VOP_Resolution: %ld, Slice-Mode: %ld, Slize_Size: %ld",
            voptimecfg.voptime_resolution, multislice.mslice_mode,
            multislice.mslice_size);

    DEBUG_PRINT_HIGH("ENC_CONFIG: EntropyMode: %d, CabacModel: %ld",
            entropy.longentropysel, entropy.cabacmodel);

    DEBUG_PRINT_HIGH("ENC_CONFIG: DB-Mode: %ld, alpha: %ld, Beta: %ld",
            dbkfilter.db_mode, dbkfilter.slicealpha_offset,
            dbkfilter.slicebeta_offset);

    DEBUG_PRINT_HIGH("ENC_CONFIG: HEC: %ld, IDR Period: %ld",
            hec.header_extension, idrperiod.idrperiod);

    DEBUG_PRINT_HIGH("ENC_CONFIG: LTR Enabled: %d, Count: %d",
            ltrinfo.enabled, ltrinfo.count);

    if (temporal_layers_config.nPLayers) {
        DEBUG_PRINT_HIGH("ENC_CONFIG: Temporal layers: P-layers: %u, B-layers: %u, Adjusted I-frame-interval: %lu",
                temporal_layers_config.nPLayers, temporal_layers_config.nBLayers,
                intra_period.num_pframes + intra_period.num_bframes + 1);

        for (OMX_U32 l = 0; temporal_layers_config.bIsBitrateRatioValid
                && (l < temporal_layers_config.nPLayers + temporal_layers_config.nBLayers); ++l) {
            DEBUG_PRINT_HIGH("ENC_CONFIG: Temporal layers: layer[%d] bitrate %% = %u%%",
                    l, temporal_layers_config.nTemporalLayerBitrateFraction[l]);
        }
    } else {

        DEBUG_PRINT_HIGH("ENC_CONFIG: Hier layers: %d, Hier Mode: %s VPX_ErrorResilience: %d",
                temporal_layers_config.nPLayers, hiermode_string(temporal_layers_config.hier_mode), vpx_err_resilience.enable);

        DEBUG_PRINT_HIGH("ENC_CONFIG: Hier params: Frame Interval : %d, MinQP: %d, Max_QP: %d",
                temporal_layers_config.nKeyFrameInterval, temporal_layers_config.nMinQuantizer, temporal_layers_config.nMaxQuantizer);

        DEBUG_PRINT_HIGH("ENC_CONFIG: Hybrid_HP PARAMS: Layer0: %d, Layer1: %d, Later2: %d, Layer3: %d, Layer4: %d, Layer5: %d",
                temporal_layers_config.nTemporalLayerBitrateRatio[0], temporal_layers_config.nTemporalLayerBitrateRatio[1],
                temporal_layers_config.nTemporalLayerBitrateRatio[2], temporal_layers_config.nTemporalLayerBitrateRatio[3],
                temporal_layers_config.nTemporalLayerBitrateRatio[4], temporal_layers_config.nTemporalLayerBitrateRatio[5]);
    }

    DEBUG_PRINT_HIGH("ENC_CONFIG: VUI timing info enabled: %d", vui_timing_info.enabled);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Peak bitrate: %d", peak_bitrate.peakbitrate);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Session Priority: %s", sess_priority.priority ? "NonRealTime" : "RealTime");

    DEBUG_PRINT_HIGH("ENC_CONFIG: ROI : %u", m_roi_enabled);

    DEBUG_PRINT_HIGH("ENC_CONFIG: Operating Rate: %u", operating_rate);
}

bool venc_dev::venc_reconfig_reqbufs()
{
    struct v4l2_requestbuffers bufreq;

    DEBUG_PRINT_HIGH("venc_reconfig_reqbufs: output_mplane %lu, capture_mplane %lu",
        m_sInput_buff_property.actualcount, m_sOutput_buff_property.actualcount);

    bufreq.memory = V4L2_MEMORY_USERPTR;
    bufreq.count = m_sInput_buff_property.actualcount;
    bufreq.type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    if(ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
        DEBUG_PRINT_ERROR("VIDIOC_REQBUFS: OUTPUT_MPLANE (count %d) failed", bufreq.count);
        return false;
    }

    bufreq.memory = V4L2_MEMORY_USERPTR;
    bufreq.count = m_sOutput_buff_property.actualcount;
    bufreq.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    if(ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
        DEBUG_PRINT_ERROR("VIDIOC_REQBUFS: CAPTURE_MPLANE (count %d) failed", bufreq.count);
        return false;
    }
    return true;
}

unsigned venc_dev::venc_flush( unsigned port)
{
    struct v4l2_encoder_cmd enc;
    DEBUG_PRINT_LOW("in %s", __func__);

    unsigned int cookie = 0;
    for (unsigned int i = 0; i < (sizeof(fd_list)/sizeof(fd_list[0])); i++) {
        cookie = fd_list[i];
        if (cookie != 0) {
            if (!ioctl(input_extradata_info.m_ion_dev, ION_IOC_FREE, &cookie)) {
                DEBUG_PRINT_HIGH("Freed handle = %u", cookie);
            }
            fd_list[i] = 0;
        }
    }

    enc.cmd = V4L2_QCOM_CMD_FLUSH;
    enc.flags = V4L2_QCOM_CMD_FLUSH_OUTPUT | V4L2_QCOM_CMD_FLUSH_CAPTURE;

    if (ioctl(m_nDriver_fd, VIDIOC_ENCODER_CMD, &enc)) {
        DEBUG_PRINT_ERROR("Flush Port (%d) Failed ", port);
        return -1;
    }

    return 0;
}

//allocating I/P memory from pmem and register with the device
bool venc_dev::allocate_extradata(unsigned port)
{
    int rc = 0;
    unsigned int extra_idx = 0;

    // PORT_INDEX_IN = 0
    // PORT_INDEX_OUT = 1
    struct port_info_s {
        int num_planes;
        struct extradata_buffer_info *extradata_info;
        int flag;
    }port_info[2] = {
        {
            .num_planes = num_input_planes,
            .extradata_info = &input_extradata_info,
            .flag = ION_FLAG_CACHED
        },
        {
            .num_planes = num_output_planes,
            .extradata_info = &output_extradata_info,
            .flag = 0
        }
    };

    if (port != PORT_INDEX_IN && port != PORT_INDEX_OUT) {
        DEBUG_PRINT_ERROR("ERROR: venc_use_buf:Invalid Port Index ");
        return false;
    }

    extra_idx = EXTRADATA_IDX(port_info[port].num_planes);
    if ((port_info[port].num_planes > 1) && (extra_idx)) {
        rc = allocate_extradata(port_info[port].extradata_info,
                                port_info[port].flag);
        if (rc) {
            DEBUG_PRINT_ERROR("Failed to allocate extradata: %d\n", rc);
            return false;
        }
    }

    return true;
}

bool venc_dev::venc_free_buf(void *buf_addr, unsigned port)
{
    struct pmem *pmem_tmp;
    struct venc_bufferpayload dev_buffer;

    memset(&dev_buffer, 0, sizeof(dev_buffer));
    pmem_tmp = (struct pmem *)buf_addr;

    if (port == PORT_INDEX_IN) {
        dev_buffer.pbuffer = (OMX_U8 *)pmem_tmp->buffer;
        dev_buffer.fd  = pmem_tmp->fd;
        dev_buffer.maped_size = pmem_tmp->size;
        dev_buffer.sz = pmem_tmp->size;
        dev_buffer.offset = pmem_tmp->offset;
        DEBUG_PRINT_LOW("venc_free_buf:pbuffer = %p,fd = %x, offset = %d, maped_size = %d", \
                dev_buffer.pbuffer, \
                dev_buffer.fd, \
                dev_buffer.offset, \
                dev_buffer.maped_size);

    } else if (port == PORT_INDEX_OUT) {
        dev_buffer.pbuffer = (OMX_U8 *)pmem_tmp->buffer;
        dev_buffer.fd  = pmem_tmp->fd;
        dev_buffer.sz = pmem_tmp->size;
        dev_buffer.maped_size = pmem_tmp->size;
        dev_buffer.offset = pmem_tmp->offset;

        DEBUG_PRINT_LOW("venc_free_buf:pbuffer = %p,fd = %x, offset = %d, maped_size = %d", \
                dev_buffer.pbuffer, \
                dev_buffer.fd, \
                dev_buffer.offset, \
                dev_buffer.maped_size);
    } else {
        DEBUG_PRINT_ERROR("ERROR: venc_free_buf:Invalid Port Index ");
        return false;
    }

    return true;
}

bool venc_dev::venc_color_align(OMX_BUFFERHEADERTYPE *buffer,
        OMX_U32 width, OMX_U32 height)
{
    OMX_U32 y_stride = VENUS_Y_STRIDE(COLOR_FMT_NV12, width),
            y_scanlines = VENUS_Y_SCANLINES(COLOR_FMT_NV12, height),
            uv_stride = VENUS_UV_STRIDE(COLOR_FMT_NV12, width),
            uv_scanlines = VENUS_UV_SCANLINES(COLOR_FMT_NV12, height),
            src_chroma_offset = width * height;

    if (buffer->nAllocLen >= VENUS_BUFFER_SIZE(COLOR_FMT_NV12, width, height)) {
        OMX_U8* src_buf = buffer->pBuffer, *dst_buf = buffer->pBuffer;
        //Do chroma first, so that we can convert it in-place
        src_buf += width * height;
        dst_buf += y_stride * y_scanlines;
        for (int line = height / 2 - 1; line >= 0; --line) {
            /* Align the length to 16 for better memove performance. */
            memmove(dst_buf + line * uv_stride,
                    src_buf + line * width,
                    ALIGN(width, 16));
        }

        dst_buf = src_buf = buffer->pBuffer;
        //Copy the Y next
        for (int line = height - 1; line > 0; --line) {
            /* Align the length to 16 for better memove performance. */
            memmove(dst_buf + line * y_stride,
                    src_buf + line * width,
                    ALIGN(width, 16));
        }
        /* Inform driver to do cache flush on total buffer */
        buffer->nFilledLen = buffer->nAllocLen;
    } else {
        DEBUG_PRINT_ERROR("Failed to align Chroma. from %u to %u : \
                Insufficient bufferLen=%u v/s Required=%u",
                (unsigned int)(width*height), (unsigned int)src_chroma_offset, (unsigned int)buffer->nAllocLen,
                VENUS_BUFFER_SIZE(COLOR_FMT_NV12, width, height));
        return false;
    }

    return true;
}

bool venc_dev::venc_get_vui_timing_info(OMX_U32 *enabled)
{
    if (!enabled) {
        DEBUG_PRINT_ERROR("Null pointer error");
        return false;
    } else {
        *enabled = vui_timing_info.enabled;
        return true;
    }
}

bool venc_dev::venc_get_vqzip_sei_info(OMX_U32 *enabled)
{
    if (!enabled) {
        DEBUG_PRINT_ERROR("Null pointer error");
        return false;
    } else {
        *enabled = vqzip_sei_info.enabled;
        return true;
    }
}

bool venc_dev::venc_get_peak_bitrate(OMX_U32 *peakbitrate)
{
    if (!peakbitrate) {
        DEBUG_PRINT_ERROR("Null pointer error");
        return false;
    } else {
        *peakbitrate = peak_bitrate.peakbitrate;
        return true;
    }
}

bool venc_dev::venc_get_batch_size(OMX_U32 *size)
{
    if (!size) {
        DEBUG_PRINT_ERROR("Null pointer error");
        return false;
    } else {
        *size = mBatchSize;
        return true;
    }
}


bool venc_dev::venc_empty_buf(void *buffer, void *pmem_data_buf, unsigned index, unsigned fd)
{
    struct pmem *temp_buffer;
    struct v4l2_buffer buf;
    struct v4l2_requestbuffers bufreq;
    struct v4l2_plane plane[VIDEO_MAX_PLANES];
    int rc = 0, extra_idx;
    struct OMX_BUFFERHEADERTYPE *bufhdr;
    LEGACY_CAM_METADATA_TYPE * meta_buf = NULL;
    temp_buffer = (struct pmem *)buffer;

    memset (&buf, 0, sizeof(buf));
    memset (&plane, 0, sizeof(plane));

    if (buffer == NULL) {
        DEBUG_PRINT_ERROR("ERROR: venc_etb: buffer is NULL");
        return false;
    }

    bufhdr = (OMX_BUFFERHEADERTYPE *)buffer;
    bufreq.memory = V4L2_MEMORY_USERPTR;
    bufreq.count = m_sInput_buff_property.actualcount;
    bufreq.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;

    DEBUG_PRINT_LOW("Input buffer length %u, Timestamp = %lld", (unsigned int)bufhdr->nFilledLen, bufhdr->nTimeStamp);

    if (pmem_data_buf) {
        DEBUG_PRINT_LOW("\n Internal PMEM addr for i/p Heap UseBuf: %p", pmem_data_buf);
        plane[0].m.userptr = (unsigned long)pmem_data_buf;
        plane[0].data_offset = bufhdr->nOffset;
        plane[0].length = bufhdr->nAllocLen;
        plane[0].bytesused = bufhdr->nFilledLen;
    } else {
        // --------------------------------------------------------------------------------------
        // [Usage]             [metadatamode] [Type]        [color_format] [Where is buffer info]
        // ---------------------------------------------------------------------------------------
        // Camera-2              1            CameraSource   0              meta-handle
        // Camera-3              1            GrallocSource  0              gralloc-private-handle
        // surface encode (RBG)  1            GrallocSource  1              bufhdr (color-converted)
        // CPU (Eg: MediaCodec)  0            --             0              bufhdr
        // ---------------------------------------------------------------------------------------
        if (metadatamode) {
            plane[0].m.userptr = index;
            meta_buf = (LEGACY_CAM_METADATA_TYPE *)bufhdr->pBuffer;

            if (!meta_buf) {
                if (!bufhdr->nFilledLen) {
                    if (bufhdr->nFlags & OMX_BUFFERFLAG_EOS) {
                        DEBUG_PRINT_ERROR("venc_empty_buf: Zero length EOS buffers are not valid");
                        DEBUG_PRINT_ERROR("Use this function instead : venc_handle_empty_eos_buffer");
                        return false;
                    }
                    DEBUG_PRINT_ERROR("venc_empty_buf: Zero length buffers are not valid");
                    return false;
                }
            } else if (!color_format) {

                if (meta_buf->buffer_type == LEGACY_CAM_SOURCE) {
                    native_handle_t *hnd = (native_handle_t*)meta_buf->meta_handle;
                    if (!hnd) {
                        DEBUG_PRINT_ERROR("ERROR: venc_etb: handle is NULL");
                        return false;
                    }
                    int usage = 0;
                    usage = MetaBufferUtil::getIntAt(hnd, 0, MetaBufferUtil::INT_USAGE);
                    usage = usage > 0 ? usage : 0;

                    if (!streaming[OUTPUT_PORT] && !(m_sVenc_cfg.inputformat == V4L2_PIX_FMT_RGB32 ||
                        m_sVenc_cfg.inputformat == V4L2_PIX_FMT_RGBA8888_UBWC)) {

                        unsigned int is_csc_enabled = 0;
                        struct v4l2_format fmt;
                        OMX_COLOR_FORMATTYPE color_format = (OMX_COLOR_FORMATTYPE)QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m;

                        color_format = (OMX_COLOR_FORMATTYPE)MetaBufferUtil::getIntAt(hnd, 0, MetaBufferUtil::INT_COLORFORMAT);

                        memset(&fmt, 0, sizeof(fmt));
                        if (usage & private_handle_t::PRIV_FLAGS_ITU_R_709 ||
                                usage & private_handle_t::PRIV_FLAGS_ITU_R_601) {
                            DEBUG_PRINT_ERROR("Camera buffer color format is not 601_FR.");
                            DEBUG_PRINT_ERROR(" This leads to unknown color space");
                        }
                        if (usage & private_handle_t::PRIV_FLAGS_ITU_R_601_FR) {
                            if (is_csc_enabled) {
                                struct v4l2_control control;
                                control.id = V4L2_CID_MPEG_VIDC_VIDEO_VPE_CSC;
                                control.value = V4L2_CID_MPEG_VIDC_VIDEO_VPE_CSC_ENABLE;
                                if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
                                    DEBUG_PRINT_ERROR("venc_empty_buf: Failed to set VPE CSC for 601_to_709");
                                } else {
                                    DEBUG_PRINT_INFO("venc_empty_buf: Will convert 601-FR to 709");
                                    fmt.fmt.pix_mp.colorspace = V4L2_COLORSPACE_REC709;
                                    venc_set_colorspace(MSM_VIDC_BT709_5, 0,
                                            MSM_VIDC_TRANSFER_BT709_5, MSM_VIDC_MATRIX_BT_709_5);
                                }
                            } else {
                                venc_set_colorspace(MSM_VIDC_BT601_6_525, 1,
                                        MSM_VIDC_TRANSFER_601_6_525, MSM_VIDC_MATRIX_601_6_525);
                                fmt.fmt.pix_mp.colorspace = V4L2_COLORSPACE_470_SYSTEM_BG;
                            }
                        }
                        fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
                        m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12;
                        fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
                        fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
                        if (usage & private_handle_t::PRIV_FLAGS_UBWC_ALIGNED) {
                            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12_UBWC;
                        }

                        if (color_format > 0 && !venc_set_color_format(color_format)) {
                            DEBUG_PRINT_ERROR("Failed setting color format in Camerasource %lx", m_sVenc_cfg.inputformat);
                            return false;
                        }

                        if(ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
                            DEBUG_PRINT_ERROR("VIDIOC_REQBUFS OUTPUT_MPLANE Failed");
                            return false;
                        }
                    }

                    // Setting batch mode is sticky. We do not expect camera to change
                    // between batch and normal modes at runtime.
                    if (mBatchSize) {
                        if ((unsigned int)MetaBufferUtil::getBatchSize(hnd) != mBatchSize) {
                            DEBUG_PRINT_ERROR("Don't support dynamic batch sizes (changed from %d->%d)",
                                    mBatchSize, MetaBufferUtil::getBatchSize(hnd));
                            return false;
                        }

                        return venc_empty_batch ((OMX_BUFFERHEADERTYPE*)buffer, index);
                    }

                    int offset = MetaBufferUtil::getIntAt(hnd, 0, MetaBufferUtil::INT_OFFSET);
                    int length = MetaBufferUtil::getIntAt(hnd, 0, MetaBufferUtil::INT_SIZE);
                    if (offset < 0 || length < 0) {
                        DEBUG_PRINT_ERROR("Invalid meta buffer handle!");
                        return false;
                    }
                    plane[0].data_offset = offset;
                    plane[0].length = length;
                    plane[0].bytesused = length;
                    DEBUG_PRINT_LOW("venc_empty_buf: camera buf: fd = %d filled %d of %d flag 0x%x format 0x%lx",
                            fd, plane[0].bytesused, plane[0].length, buf.flags, m_sVenc_cfg.inputformat);
                } else if (meta_buf->buffer_type == kMetadataBufferTypeGrallocSource) {
                    VideoGrallocMetadata *meta_buf = (VideoGrallocMetadata *)bufhdr->pBuffer;
                    private_handle_t *handle = (private_handle_t *)meta_buf->pHandle;

                    if (!handle) {
                        DEBUG_PRINT_ERROR("%s : handle is null!", __FUNCTION__);
                        return false;
                    }

                    if (mUseAVTimerTimestamps) {
                        uint64_t avTimerTimestampNs = bufhdr->nTimeStamp * 1000;
                        if (getMetaData(handle, GET_VT_TIMESTAMP, &avTimerTimestampNs) == 0
                                && avTimerTimestampNs > 0) {
                            bufhdr->nTimeStamp = avTimerTimestampNs / 1000;
                            DEBUG_PRINT_LOW("AVTimer TS : %llu us", (unsigned long long)bufhdr->nTimeStamp);
                        }
                    }

                    if (!streaming[OUTPUT_PORT]) {
                        ColorMetaData colorData= {};
                        // Moment of truth... actual colorspace is known here..
                        if (getMetaData(handle, GET_COLOR_METADATA, &colorData) == 0) {
                            DEBUG_PRINT_INFO("ENC_CONFIG: gralloc Color MetaData colorPrimaries=%d colorRange=%d "
                                             "transfer=%d matrixcoefficients=%d",
                                             colorData.colorPrimaries, colorData.range,
                                             colorData.transfer, colorData.matrixCoefficients);
                        }

                        struct v4l2_format fmt;
                        memset(&fmt, 0, sizeof(fmt));
                        fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;

                        bool isUBWC = (handle->flags & private_handle_t::PRIV_FLAGS_UBWC_ALIGNED) && is_gralloc_source_ubwc;
                        if (handle->format == HAL_PIXEL_FORMAT_NV12_ENCODEABLE) {
                            m_sVenc_cfg.inputformat = isUBWC ? V4L2_PIX_FMT_NV12_UBWC : V4L2_PIX_FMT_NV12;
                            DEBUG_PRINT_INFO("ENC_CONFIG: Input Color = NV12 %s", isUBWC ? "UBWC" : "Linear");
                        } else if (handle->format == HAL_PIXEL_FORMAT_RGBA_8888) {
                            // In case of RGB, conversion to YUV is handled within encoder.
                            // Disregard the Colorspace in gralloc-handle in case of RGB and use
                            //   [a] 601 for non-UBWC case : C2D output is (apparently) 601-LR
                            //   [b] 601 for UBWC case     : Venus can convert to 601-LR or FR. use LR for now.
                            //set colormetadata corresponding to ITU_R_601;
                            colorData.colorPrimaries =  ColorPrimaries_BT601_6_525;
                            colorData.range = Range_Limited;
                            colorData.transfer = Transfer_SMPTE_170M;
                            colorData.matrixCoefficients = MatrixCoEff_BT601_6_525;
                            m_sVenc_cfg.inputformat = isUBWC ? V4L2_PIX_FMT_RGBA8888_UBWC : V4L2_PIX_FMT_RGB32;
                            DEBUG_PRINT_INFO("ENC_CONFIG: Input Color = RGBA8888 %s", isUBWC ? "UBWC" : "Linear");
                        } else if (handle->format == QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m) {
                            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12;
                            DEBUG_PRINT_INFO("ENC_CONFIG: Input Color = NV12 Linear");
                        } else if (handle->format == HAL_PIXEL_FORMAT_YCbCr_420_TP10_UBWC) {
                            if ((m_codec == OMX_VIDEO_CodingHEVC) &&
                                 (codec_profile.profile == V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN10)) {
                                m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12_TP10_UBWC;
                                DEBUG_PRINT_INFO("ENC_CONFIG: Input Color = TP10UBWC");
                            } else {
                                DEBUG_PRINT_ERROR("ENC_CONFIG: TP10UBWC colorformat not supported for this codec and profile");
                                return false;
                            }

                            if(colorData.masteringDisplayInfo.colorVolumeSEIEnabled ||
                               colorData.contentLightLevel.lightLevelSEIEnabled) {
                                if (!venc_set_hdr_info(colorData.masteringDisplayInfo, colorData.contentLightLevel)) {
                                    DEBUG_PRINT_ERROR("HDR10-PQ Info Setting failed");
                                    return false;
                                } else {
                                    DEBUG_PRINT_INFO("Encoding in HDR10-PQ mode");
                                }
                            } else {
                                DEBUG_PRINT_INFO("Encoding in HLG mode");
                            }
                        }

                        DEBUG_PRINT_INFO("color_space.primaries %d colorData.colorPrimaries %d, is_csc_custom_matrix_enabled=%d",
                                         color_space.primaries, colorData.colorPrimaries, is_csc_custom_matrix_enabled);

                        if (csc_enable) {
                            struct v4l2_control control;

                            /* Set Camera Color Space. When we set CSC, this will be passed to
                               fimrware as the InputPrimaries */
                            venc_set_colorspace(colorData.colorPrimaries, colorData.range,
                                                colorData.transfer, colorData.matrixCoefficients);

                            control.id = V4L2_CID_MPEG_VIDC_VIDEO_VPE_CSC;
                            control.value = V4L2_CID_MPEG_VIDC_VIDEO_VPE_CSC_ENABLE;
                            if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
                                DEBUG_PRINT_ERROR("venc_empty_buf: Failed to set VPE CSC");
                            }
                            else {
                                if (is_csc_custom_matrix_enabled) {
                                    control.id = V4L2_CID_MPEG_VIDC_VIDEO_VPE_CSC_CUSTOM_MATRIX;
                                    control.value = 1;
                                    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
                                        DEBUG_PRINT_ERROR("venc_empty_buf: Failed to enable VPE CSC custom matrix");
                                    } else {
                                        DEBUG_PRINT_INFO("venc_empty_buf: Enabled VPE CSC custom matrix");
                                    }
                                }
                                /* Change Colorspace to 709*/
                                colorData.colorPrimaries =  ColorPrimaries_BT709_5;
                                colorData.range = Range_Limited;
                                colorData.transfer = Transfer_sRGB;
                                colorData.matrixCoefficients = MatrixCoEff_BT709_5;
                            }
                        }

                        /* Enum values from gralloc ColorMetaData matches with the driver values
                           as it is standard compliant */
                        venc_set_colorspace(colorData.colorPrimaries, colorData.range,
                                            colorData.transfer, colorData.matrixCoefficients);

                        fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.inputformat;
                        fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
                        fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;
                        if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
                            DEBUG_PRINT_ERROR("Failed setting color format in Grallocsource %lx", m_sVenc_cfg.inputformat);
                            return false;
                        }
                        if(ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
                            DEBUG_PRINT_ERROR("VIDIOC_REQBUFS OUTPUT_MPLANE Failed");
                            return false;
                        }
                    }

                    struct UBWCStats cam_ubwc_stats[2];
                    unsigned long long int cr = 1 << 16;

                    if (getMetaData(handle, GET_UBWC_CR_STATS_INFO, (void *)cam_ubwc_stats) == 0) {
                        if (cam_ubwc_stats[0].bDataValid) {
                            switch (cam_ubwc_stats[0].version) {
                            case UBWC_2_0:
                                {
                                    unsigned long long int sum = 0, weighted_sum = 0;

                                    DEBUG_PRINT_HIGH("Field 0 : 32 Tile = %d 64 Tile = %d 96 Tile = %d "
                                       "128 Tile = %d 160 Tile = %d 192 Tile = %d 256 Tile = %d\n",
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile32,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile64,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile96,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile128,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile160,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile192,
                                       cam_ubwc_stats[0].ubwc_stats.nCRStatsTile256);

                                    weighted_sum =
                                        32  * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile32 +
                                        64  * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile64 +
                                        96  * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile96 +
                                        128 * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile128 +
                                        160 * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile160 +
                                        192 * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile192 +
                                        256 * cam_ubwc_stats[0].ubwc_stats.nCRStatsTile256;

                                    sum =
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile32 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile64 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile96 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile128 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile160 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile192 +
                                        cam_ubwc_stats[0].ubwc_stats.nCRStatsTile256;

                                    cr = (weighted_sum && sum) ?
                                        ((256 * sum) << 16) / weighted_sum : cr;
                                }
                                break;
                            default:
                                break;
                            }
                        }
                    }

                    fd = handle->fd;
                    plane[0].data_offset = 0;
                    plane[0].length = handle->size;
                    plane[0].bytesused = handle->size;
                    plane[0].reserved[2] = (unsigned long int)cr;
                    DEBUG_PRINT_LOW("venc_empty_buf: Opaque camera buf: fd = %d "
                                ": filled %d of %d format 0x%lx CR = %d", fd, plane[0].bytesused,
                                plane[0].length, m_sVenc_cfg.inputformat, plane[0].reserved[2]);
                }
            } else {
                // color_format == 1 ==> RGBA to YUV Color-converted buffer
                // Buffers color-converted via C2D have 601-Limited color
                if (!streaming[OUTPUT_PORT]) {
                    DEBUG_PRINT_HIGH("Setting colorspace 601-L for Color-converted buffer");
                    venc_set_colorspace(MSM_VIDC_BT601_6_625, 0 /*range-limited*/,
                            MSM_VIDC_TRANSFER_601_6_525, MSM_VIDC_MATRIX_601_6_525);
                }
                plane[0].m.userptr = (unsigned long) bufhdr->pBuffer;
                plane[0].data_offset = bufhdr->nOffset;
                plane[0].length = bufhdr->nAllocLen;
                plane[0].bytesused = bufhdr->nFilledLen;
                DEBUG_PRINT_LOW("venc_empty_buf: Opaque non-camera buf: fd = %d filled %d of %d",
                        fd, plane[0].bytesused, plane[0].length);
            }
        } else {
            plane[0].m.userptr = (unsigned long) bufhdr->pBuffer;
            plane[0].data_offset = bufhdr->nOffset;
            plane[0].length = bufhdr->nAllocLen;
            plane[0].bytesused = bufhdr->nFilledLen;
            DEBUG_PRINT_LOW("venc_empty_buf: non-camera buf: fd = %d filled %d of %d",
                    fd, plane[0].bytesused, plane[0].length);
        }
    }

    if (!streaming[OUTPUT_PORT] &&
        (m_sVenc_cfg.inputformat != V4L2_PIX_FMT_NV12_TP10_UBWC &&
         m_sVenc_cfg.inputformat != V4L2_PIX_FMT_NV12_P010_UBWC &&
         m_sVenc_cfg.inputformat != V4L2_PIX_FMT_NV12_UBWC)) {
        if (bframe_implicitly_enabled) {
            DEBUG_PRINT_HIGH("Disabling implicitly enabled B-frames");
            intra_period.num_pframes = nPframes_cache;
            if (!_venc_set_intra_period(intra_period.num_pframes, 0)) {
                DEBUG_PRINT_ERROR("Failed to set nPframes/nBframes");
                return OMX_ErrorUndefined;
            }
        }
    }

    extra_idx = EXTRADATA_IDX(num_input_planes);

    if (extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
        int extradata_index = venc_get_index_from_fd(input_extradata_info.m_ion_dev,fd);
        if (extradata_index < 0 ) {
                DEBUG_PRINT_ERROR("Extradata index calculation went wrong for fd = %d", fd);
                return false;
            }

        plane[extra_idx].bytesused = 0;
        plane[extra_idx].length = input_extradata_info.size;
        plane[extra_idx].m.userptr = (unsigned long) (input_extradata_info.uaddr + extradata_index * input_extradata_info.buffer_size);
#ifdef USE_ION
        plane[extra_idx].reserved[0] = input_extradata_info.ion.fd_ion_data.fd;
#endif
        plane[extra_idx].reserved[1] = input_extradata_info.buffer_size * extradata_index;
        plane[extra_idx].reserved[2] = input_extradata_info.size;
        plane[extra_idx].data_offset = 0;
    } else if (extra_idx >= VIDEO_MAX_PLANES) {
        DEBUG_PRINT_ERROR("Extradata index higher than expected: %d\n", extra_idx);
        return false;
    }

    buf.index = index;
    buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    buf.memory = V4L2_MEMORY_USERPTR;
    plane[0].reserved[0] = fd;
    plane[0].reserved[1] = 0;
    buf.m.planes = plane;
    buf.length = num_input_planes;
    buf.timestamp.tv_sec = bufhdr->nTimeStamp / 1000000;
    buf.timestamp.tv_usec = (bufhdr->nTimeStamp % 1000000);

    if (!handle_input_extradata(buf)) {
        DEBUG_PRINT_ERROR("%s Failed to handle input extradata", __func__);
        return false;
    }
    VIDC_TRACE_INT_LOW("ETB-TS", bufhdr->nTimeStamp / 1000);

    if (bufhdr->nFlags & OMX_BUFFERFLAG_EOS)
        buf.flags |= V4L2_QCOM_BUF_FLAG_EOS;

    if (!plane[0].bytesused) {
        if (buf.flags & V4L2_QCOM_BUF_FLAG_EOS) {
            DEBUG_PRINT_ERROR("venc_empty_buf: Zero length EOS buffers are not valid");
            DEBUG_PRINT_ERROR("Use this function instead : venc_handle_empty_eos_buffer");
            return false;
        }
        DEBUG_PRINT_ERROR("venc_empty_buf: Zero length buffers are not valid");
        return false;
    }

    if (m_debug.in_buffer_log) {
        venc_input_log_buffers(bufhdr, fd, plane[0].data_offset, m_sVenc_cfg.inputformat);
    }
    if (m_debug.extradata_log && extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
        DEBUG_PRINT_ERROR("Extradata Addr 0x%llx, Buffer Addr = 0x%x", (OMX_U64)input_extradata_info.uaddr, (unsigned int)plane[extra_idx].m.userptr);
        venc_extradata_log_buffers((char *)plane[extra_idx].m.userptr);
    }
    rc = ioctl(m_nDriver_fd, VIDIOC_QBUF, &buf);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to qbuf (etb) to driver");
        return false;
    }

    etb++;

    if (!streaming[OUTPUT_PORT]) {
        enum v4l2_buf_type buf_type;
        buf_type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        int ret;

        if (!downscalar_enabled) {
            OMX_U32 inp_width = 0, inp_height = 0, out_width = 0, out_height = 0;

            if (!venc_get_dimensions(PORT_INDEX_IN, &inp_width, &inp_height)) {
                return false;
            }

            if (!venc_get_dimensions(PORT_INDEX_OUT, &out_width, &out_height)) {
                return false;
            }

            // Tiling in HEIC requires output WxH to be Tile size; difference is permitted
            if (!(m_codec == OMX_VIDEO_CodingImageHEIC) &&
                inp_width * inp_height != out_width * out_height) {
                DEBUG_PRINT_ERROR("Downscalar is disabled and input/output dimenstions don't match");
                DEBUG_PRINT_ERROR("Input WxH : %dx%d Output WxH : %dx%d",inp_width, inp_height, out_width, out_height);
                return false;
            }
        }

        ret = ioctl(m_nDriver_fd, VIDIOC_STREAMON, &buf_type);

        if (ret) {
            DEBUG_PRINT_ERROR("Failed to call streamon");
            if (errno == EBUSY) {
                hw_overload = true;
            }
            return false;
        } else {
            streaming[OUTPUT_PORT] = true;
        }
    }

    return true;
}

bool venc_dev::venc_empty_batch(OMX_BUFFERHEADERTYPE *bufhdr, unsigned index)
{
    struct v4l2_buffer buf;
    struct v4l2_plane plane[VIDEO_MAX_PLANES];
    int rc = 0, extra_idx, numBufs;
    struct v4l2_control control;
    LEGACY_CAM_METADATA_TYPE * meta_buf = NULL;
    native_handle_t *hnd = NULL;

    if (bufhdr == NULL) {
        DEBUG_PRINT_ERROR("ERROR: %s: buffer is NULL", __func__);
        return false;
    }

    bool status = true;
    if (metadatamode) {
        plane[0].m.userptr = index;
        meta_buf = (LEGACY_CAM_METADATA_TYPE *)bufhdr->pBuffer;

        if (!color_format) {
            if (meta_buf->buffer_type == LEGACY_CAM_SOURCE) {
                hnd = (native_handle_t*)meta_buf->meta_handle;
                if (!hnd) {
                    DEBUG_PRINT_ERROR("venc_empty_batch: invalid handle !");
                    return false;
                } else if (MetaBufferUtil::getBatchSize(hnd) > kMaxBuffersInBatch) {
                    DEBUG_PRINT_ERROR("venc_empty_batch: Too many buffers (%d) in batch. "
                            "Max = %d", MetaBufferUtil::getBatchSize(hnd), kMaxBuffersInBatch);
                    status = false;
                }
                DEBUG_PRINT_LOW("venc_empty_batch: Batch of %d bufs", MetaBufferUtil::getBatchSize(hnd));
            } else {
                DEBUG_PRINT_ERROR("Batch supported for CameraSource buffers only !");
                status = false;
            }
        } else {
            DEBUG_PRINT_ERROR("Batch supported for Camera buffers only !");
            status = false;
        }
    } else {
        DEBUG_PRINT_ERROR("Batch supported for metabuffer mode only !");
        status = false;
    }

    if (status) {
        OMX_TICKS bufTimeStamp = 0ll;
        int numBufs = MetaBufferUtil::getBatchSize(hnd);
        int v4l2Ids[kMaxBuffersInBatch] = {-1};
        for (int i = 0; i < numBufs; ++i) {
            v4l2Ids[i] = mBatchInfo.registerBuffer(index);
            if (v4l2Ids[i] < 0) {
                DEBUG_PRINT_ERROR("Failed to register buffer");
                // TODO: cleanup the map and purge all slots of current index
                status = false;
                break;
            }
        }
        for (int i = 0; i < numBufs; ++i) {
            int v4l2Id = v4l2Ids[i];
            int usage = 0;

            memset(&buf, 0, sizeof(buf));
            memset(&plane, 0, sizeof(plane));

            DEBUG_PRINT_LOW("Batch: registering %d as %d", index, v4l2Id);
            buf.index = (unsigned)v4l2Id;
            buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
            buf.memory = V4L2_MEMORY_USERPTR;
            plane[0].reserved[0] = MetaBufferUtil::getFdAt(hnd, i);
            plane[0].reserved[1] = 0;
            plane[0].data_offset = MetaBufferUtil::getIntAt(hnd, i, MetaBufferUtil::INT_OFFSET);
            plane[0].m.userptr = (unsigned long)meta_buf;
            plane[0].length = plane[0].bytesused = MetaBufferUtil::getIntAt(hnd, i, MetaBufferUtil::INT_SIZE);
            buf.m.planes = plane;
            buf.length = num_input_planes;

            usage = MetaBufferUtil::getIntAt(hnd, i, MetaBufferUtil::INT_USAGE);
            usage = usage > 0 ? usage : 0;

            extra_idx = EXTRADATA_IDX(num_input_planes);

            if (extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
                int fd = plane[0].reserved[0];
                int extradata_index = venc_get_index_from_fd(input_extradata_info.m_ion_dev, fd);
                if (extradata_index < 0) {
                    DEBUG_PRINT_ERROR("Extradata index calculation went wrong for fd = %d", fd);
                    return false;
                }

                plane[extra_idx].bytesused = 0;
                plane[extra_idx].length = input_extradata_info.size;
                plane[extra_idx].m.userptr = (unsigned long) (input_extradata_info.uaddr + extradata_index * input_extradata_info.buffer_size);
                plane[extra_idx].reserved[0] = input_extradata_info.ion.fd_ion_data.fd;
                plane[extra_idx].reserved[1] = input_extradata_info.buffer_size * extradata_index;
                plane[extra_idx].reserved[2] = input_extradata_info.size;
                plane[extra_idx].data_offset = 0;
            } else if (extra_idx >= VIDEO_MAX_PLANES) {
                DEBUG_PRINT_ERROR("Extradata index higher than expected: %d\n", extra_idx);
                return false;
            }

            if (bufhdr->nFlags & OMX_BUFFERFLAG_EOS)
                buf.flags |= V4L2_QCOM_BUF_FLAG_EOS;
            if (i != numBufs - 1) {
                buf.flags |= V4L2_MSM_BUF_FLAG_DEFER;
                DEBUG_PRINT_LOW("for buffer %d (etb #%d) in batch of %d, marking as defer",
                        i, etb + 1, numBufs);
            }

            // timestamp differences from camera are in nano-seconds
            bufTimeStamp = bufhdr->nTimeStamp + MetaBufferUtil::getIntAt(hnd, i, MetaBufferUtil::INT_TIMESTAMP) / 1000;

            DEBUG_PRINT_LOW(" Q Batch [%d of %d] : buf=%p fd=%d len=%d TS=%lld",
                i, numBufs, bufhdr, plane[0].reserved[0], plane[0].length, bufTimeStamp);
            buf.timestamp.tv_sec = bufTimeStamp / 1000000;
            buf.timestamp.tv_usec = (bufTimeStamp % 1000000);

            if (!handle_input_extradata(buf)) {
                DEBUG_PRINT_ERROR("%s Failed to handle input extradata", __func__);
                return false;
            }
            VIDC_TRACE_INT_LOW("ETB-TS", bufTimeStamp / 1000);

            rc = ioctl(m_nDriver_fd, VIDIOC_QBUF, &buf);
            if (rc) {
                DEBUG_PRINT_ERROR("%s: Failed to qbuf (etb) to driver", __func__);
                return false;
            }

            etb++;
        }
    }

    if (status && !streaming[OUTPUT_PORT]) {
        enum v4l2_buf_type buf_type;
        buf_type=V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        int ret;

        if (!downscalar_enabled) {
            OMX_U32 inp_width = 0, inp_height = 0, out_width = 0, out_height = 0;

            if (!venc_get_dimensions(PORT_INDEX_IN, &inp_width, &inp_height)) {
                return false;
            }

            if (!venc_get_dimensions(PORT_INDEX_OUT, &out_width, &out_height)) {
                return false;
            }

            if (inp_width * inp_height != out_width * out_height) {
                DEBUG_PRINT_ERROR("Downscalar is disabled and input/output dimenstions don't match");
                DEBUG_PRINT_ERROR("Input WxH : %dx%d Output WxH : %dx%d",inp_width, inp_height, out_width, out_height);
                return false;
            }
        }

        ret = ioctl(m_nDriver_fd, VIDIOC_STREAMON, &buf_type);
        if (ret) {
            DEBUG_PRINT_ERROR("Failed to call streamon");
            if (errno == EBUSY) {
                hw_overload = true;
            }
            status = false;
        } else {
            streaming[OUTPUT_PORT] = true;
        }
    }

    return status;
}

bool venc_dev::venc_fill_buf(void *buffer, void *pmem_data_buf,unsigned index,unsigned fd)
{
    struct pmem *temp_buffer = NULL;
    struct venc_buffer  frameinfo;
    struct v4l2_buffer buf;
    struct v4l2_plane plane[VIDEO_MAX_PLANES];
    int rc = 0;
    unsigned int extra_idx;
    struct OMX_BUFFERHEADERTYPE *bufhdr;

    if (buffer == NULL)
        return false;

    bufhdr = (OMX_BUFFERHEADERTYPE *)buffer;

    if (pmem_data_buf) {
        DEBUG_PRINT_LOW("Internal PMEM addr for o/p Heap UseBuf: %p", pmem_data_buf);
        plane[0].m.userptr = (unsigned long)pmem_data_buf;
    } else {
        DEBUG_PRINT_LOW("Shared PMEM addr for o/p PMEM UseBuf/AllocateBuf: %p", bufhdr->pBuffer);
        plane[0].m.userptr = (unsigned long)bufhdr->pBuffer;
    }

    memset(&buf, 0, sizeof(buf));
    memset(&plane, 0, sizeof(plane));

    buf.index = index;
    buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    buf.memory = V4L2_MEMORY_USERPTR;
    plane[0].length = bufhdr->nAllocLen;
    plane[0].bytesused = bufhdr->nFilledLen;
    plane[0].reserved[0] = fd;
    plane[0].reserved[1] = 0;
    plane[0].data_offset = bufhdr->nOffset;
    buf.m.planes = plane;
    buf.length = num_output_planes;
    buf.flags = 0;

    if (venc_handle->is_secure_session()) {
        if (venc_handle->allocate_native_handle) {
            native_handle_t *handle_t = (native_handle_t *)(bufhdr->pBuffer);
            plane[0].length = handle_t->data[3];
        } else {
            output_metabuffer *meta_buf = (output_metabuffer *)(bufhdr->pBuffer);
            native_handle_t *handle_t = meta_buf->nh;
            plane[0].length = handle_t->data[3];
        }
    }

    if (mBatchSize) {
        // Should always mark first buffer as DEFER, since 0 % anything is 0, just offset by 1
        // This results in the first batch being of size mBatchSize + 1, but thats good because
        // we need an extra FTB for the codec specific data.

        if (!ftb || ftb % mBatchSize) {
            buf.flags |= V4L2_MSM_BUF_FLAG_DEFER;
            DEBUG_PRINT_LOW("for ftb buffer %d marking as defer", ftb + 1);
        }
    }

    extra_idx = EXTRADATA_IDX(num_output_planes);

    if (extra_idx && (extra_idx < VIDEO_MAX_PLANES)) {
        plane[extra_idx].bytesused = 0;
        plane[extra_idx].length = output_extradata_info.buffer_size;
        plane[extra_idx].m.userptr = (unsigned long) (output_extradata_info.uaddr + index * output_extradata_info.buffer_size);
#ifdef USE_ION
        plane[extra_idx].reserved[0] = output_extradata_info.ion.fd_ion_data.fd;
#endif
        plane[extra_idx].reserved[1] = output_extradata_info.buffer_size * index;
        plane[extra_idx].data_offset = 0;
    } else if (extra_idx >= VIDEO_MAX_PLANES) {
        DEBUG_PRINT_ERROR("Extradata index higher than expected: %d", extra_idx);
        return false;
    }

    rc = ioctl(m_nDriver_fd, VIDIOC_QBUF, &buf);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to qbuf (ftb) to driver");
        return false;
    }

    ftb++;
    return true;
}

bool venc_dev::venc_set_inband_video_header(OMX_BOOL enable)
{
    struct v4l2_control control;

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_TME) {
        return false;
    }

    control.id = V4L2_CID_MPEG_VIDEO_HEADER_MODE;
    if(enable) {
        control.value = V4L2_MPEG_VIDEO_HEADER_MODE_JOINED_WITH_1ST_FRAME;
    } else {
        control.value = V4L2_MPEG_VIDEO_HEADER_MODE_SEPARATE;
    }

    DEBUG_PRINT_HIGH("Set inband sps/pps: %d", enable);
    if(ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control) < 0) {
        DEBUG_PRINT_ERROR("Request for inband sps/pps failed");
        return false;
    }
    return true;
}

bool venc_dev::venc_set_au_delimiter(OMX_BOOL enable)
{
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_AU_DELIMITER;
    if(enable) {
        control.value = V4L2_MPEG_VIDC_VIDEO_AU_DELIMITER_ENABLED;
    } else {
        control.value = V4L2_MPEG_VIDC_VIDEO_AU_DELIMITER_DISABLED;
    }

    DEBUG_PRINT_HIGH("Set AU delimiters: %d", enable);
    if(ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control) < 0) {
        DEBUG_PRINT_ERROR("Request for AU delimiters failed");
        return false;
    }
    return true;
}

int venc_dev::venc_get_index_from_fd(OMX_U32 ion_fd, OMX_U32 buffer_fd)
{
    unsigned int cookie = buffer_fd;
    struct ion_fd_data fdData;

    memset(&fdData, 0, sizeof(fdData));
    fdData.fd = buffer_fd;
    if (ion_fd && !ioctl(ion_fd, ION_IOC_IMPORT, &fdData)) {
        cookie = fdData.handle;
        DEBUG_PRINT_HIGH("FD = %u imported handle = %u", fdData.fd, fdData.handle);
    }

    for (unsigned int i = 0; i < (sizeof(fd_list)/sizeof(fd_list[0])); i++) {
        if (fd_list[i] == cookie) {
            DEBUG_PRINT_HIGH("FD is present at index = %d", i);
            if (ion_fd && !ioctl(ion_fd, ION_IOC_FREE, &fdData.handle)) {
                DEBUG_PRINT_HIGH("freed handle = %u", cookie);
            }
            return i;
        }
    }

    for (unsigned int i = 0; i < (sizeof(fd_list)/sizeof(fd_list[0])); i++)
        if (fd_list[i] == 0) {
            DEBUG_PRINT_HIGH("FD added at index = %d", i);
            fd_list[i] = cookie;
            return i;
        }
    return -EINVAL;
}

bool venc_dev::venc_set_vqzip_sei_type(OMX_BOOL enable)
{
    struct v4l2_control sei_control = {0,0}, yuvstats_control = {0,0};

    DEBUG_PRINT_HIGH("Set VQZIP SEI: %d", enable);
    sei_control.id = V4L2_CID_MPEG_VIDC_VIDEO_VQZIP_SEI;
    yuvstats_control.id = V4L2_CID_MPEG_VIDC_VIDEO_EXTRADATA;

    if(enable) {
        sei_control.value = V4L2_CID_MPEG_VIDC_VIDEO_VQZIP_SEI_ENABLE;
        yuvstats_control.value = V4L2_MPEG_VIDC_EXTRADATA_YUV_STATS;
    } else {
        sei_control.value = V4L2_CID_MPEG_VIDC_VIDEO_VQZIP_SEI_DISABLE;
    }

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &sei_control) < 0) {
        DEBUG_PRINT_HIGH("Non-Fatal: Request to set SEI failed");
    }

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &yuvstats_control) < 0) {
        DEBUG_PRINT_HIGH("Non-Fatal: Request to set YUVSTATS failed");
    }
#ifdef _VQZIP_
    vqzip.pConfig.nWidth = ALIGN(m_sVenc_cfg.input_width, 16);
    vqzip.pConfig.nHeight = ALIGN(m_sVenc_cfg.input_height, 16);
    vqzip.init();
    vqzip_sei_info.enabled = true;
#endif

    return true;
}

bool venc_dev::venc_set_extradata(OMX_U32 extra_data, OMX_BOOL enable)
{
    struct v4l2_control control;

    DEBUG_PRINT_HIGH("venc_set_extradata:: %x", (int) extra_data);

    if (enable == OMX_FALSE) {
        /* No easy way to turn off extradata to the driver
         * at the moment */
        return false;
    }

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_EXTRADATA;
    switch (extra_data) {
        case OMX_ExtraDataVideoEncoderSliceInfo:
            control.value = V4L2_MPEG_VIDC_EXTRADATA_MULTISLICE_INFO;
            break;
        case OMX_ExtraDataVideoEncoderMBInfo:
            control.value = V4L2_MPEG_VIDC_EXTRADATA_METADATA_MBI;
            break;
        case OMX_ExtraDataVideoLTRInfo:
            control.value = V4L2_MPEG_VIDC_EXTRADATA_LTR;
            break;
        case OMX_ExtraDataFrameDimension:
            control.value = V4L2_MPEG_VIDC_EXTRADATA_INPUT_CROP;
            break;
        default:
            DEBUG_PRINT_ERROR("Unrecognized extradata index 0x%x", (unsigned int)extra_data);
            return false;
    }

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("ERROR: Request for setting extradata (%x) failed %d",
                (unsigned int)extra_data, errno);
        return false;
    }

    return true;
}

bool venc_dev::venc_set_slice_delivery_mode(OMX_U32 enable)
{
    struct v4l2_control control;

    if (enable) {
        control.id = V4L2_CID_MPEG_VIDEO_MULTI_SLICE_DELIVERY_MODE;
        control.value = 1;
        DEBUG_PRINT_LOW("Set slice_delivery_mode: %d", control.value);

        if (multislice.mslice_mode == V4L2_MPEG_VIDEO_MULTI_SICE_MODE_MAX_MB && m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
            if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
                DEBUG_PRINT_ERROR("Request for setting slice delivery mode failed");
                return false;
            } else {
                DEBUG_PRINT_LOW("Successfully set Slice delivery mode id: %d, value=%d", control.id, control.value);
                slice_mode.enable = 1;
            }
        } else {
            DEBUG_PRINT_ERROR("Failed to set slice delivery mode, slice_mode [%lu] "
                    "is not MB BASED or [%lu] is not H264 codec ", multislice.mslice_mode,
                    m_sVenc_cfg.codectype);
        }
    } else {
        DEBUG_PRINT_ERROR("Slice_DELIVERY_MODE not enabled");
    }

    return true;
}

bool venc_dev::venc_set_colorspace(OMX_U32 primaries, OMX_U32 range,
    OMX_U32 transfer_chars, OMX_U32 matrix_coeffs)
{
    int rc;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("Setting color space : Primaries = %d, Range = %d, Trans = %d, Matrix = %d",
        primaries, range, transfer_chars, matrix_coeffs);

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_COLOR_SPACE;
    control.value = primaries;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control : V4L2_CID_MPEG_VIDC_VIDEO_COLOR_SPACE");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    color_space.primaries = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_FULL_RANGE;
    control.value = range;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control : V4L2_CID_MPEG_VIDC_VIDEO_FULL_RANGE");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    color_space.range = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_TRANSFER_CHARS;
    control.value = transfer_chars;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control : V4L2_CID_MPEG_VIDC_VIDEO_TRANSFER_CHARS");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    color_space.transfer_chars = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_MATRIX_COEFFS;
    control.value = matrix_coeffs;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control : V4L2_CID_MPEG_VIDC_VIDEO_MATRIX_COEFFS");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    color_space.matrix_coeffs = control.value;

    return true;
}

bool venc_dev::venc_set_qp(OMX_U32 i_frame_qp, OMX_U32 p_frame_qp,OMX_U32 b_frame_qp, OMX_U32 enable)
{
    int rc;
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_I_FRAME_QP;
    control.value = i_frame_qp;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    session_qp.iframeqp = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_P_FRAME_QP;
    control.value = p_frame_qp;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    session_qp.pframeqp = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_B_FRAME_QP;
    control.value = b_frame_qp;
    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    session_qp.bframeqp = control.value;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_QP_MASK;
    control.value = enable;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    session_qp.enableqp = control.value;

    return true;
}

bool venc_dev::venc_set_session_qp_range(OMX_QCOM_VIDEO_PARAM_IPB_QPRANGETYPE* qp_range)
{
    int rc;
    struct v4l2_ext_control ctrl[7];
    struct v4l2_ext_controls controls;

    ctrl[0].id = V4L2_CID_MPEG_VIDC_VIDEO_LAYER_ID;
    ctrl[0].value = MSM_VIDC_ALL_LAYER_ID;

    ctrl[1].id = V4L2_CID_MPEG_VIDC_VIDEO_I_FRAME_QP_MIN;
    ctrl[1].value = qp_range->minIQP;

    ctrl[2].id = V4L2_CID_MPEG_VIDC_VIDEO_I_FRAME_QP_MAX;
    ctrl[2].value = qp_range->maxIQP;

    ctrl[3].id = V4L2_CID_MPEG_VIDC_VIDEO_P_FRAME_QP_MIN;
    ctrl[3].value = qp_range->minPQP;

    ctrl[4].id = V4L2_CID_MPEG_VIDC_VIDEO_P_FRAME_QP_MAX;
    ctrl[4].value = qp_range->maxPQP;

    ctrl[5].id = V4L2_CID_MPEG_VIDC_VIDEO_B_FRAME_QP_MIN;
    ctrl[5].value = qp_range->minBQP;

    ctrl[6].id = V4L2_CID_MPEG_VIDC_VIDEO_B_FRAME_QP_MAX;
    ctrl[6].value = qp_range->maxBQP;

    controls.count = 7;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    if(ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls)) {
        DEBUG_PRINT_ERROR("Failed to set QP range");
            return false;
    }

    session_ipb_qp_values.min_i_qp = qp_range->minIQP;
    session_ipb_qp_values.max_i_qp = qp_range->maxIQP;
    session_ipb_qp_values.min_p_qp = qp_range->minPQP;
    session_ipb_qp_values.max_p_qp = qp_range->maxPQP;
    session_ipb_qp_values.min_b_qp = qp_range->minBQP;
    session_ipb_qp_values.max_b_qp = qp_range->maxBQP;
    return true;
}

bool venc_dev::venc_set_profile(OMX_U32 eProfile)
{
    int rc;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_set_profile:: eProfile = %u",
            (unsigned int)eProfile);

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        control.id = V4L2_CID_MPEG_VIDEO_H264_PROFILE;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        //In driver VP8 profile is hardcoded. No need to set anything from here
        return true;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_HEVC_PROFILE;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_TME) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_TME_PROFILE;
    } else {
        DEBUG_PRINT_ERROR("Wrong CODEC");
        return false;
    }

    if (!profile_level_converter::convert_omx_profile_to_v4l2(m_sVenc_cfg.codectype, eProfile, &control.value)) {
        DEBUG_PRINT_ERROR("Cannot find v4l2 profile for OMX profile : %d Codec : %lu ",
                          eProfile, m_sVenc_cfg.codectype);
        return false;
    }

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    codec_profile.profile = control.value;
    return true;
}

bool venc_dev::venc_set_level(OMX_U32 eLevel)
{
    int rc;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_set_level:: eLevel = %u",
                    (unsigned int)eLevel);

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        control.id = V4L2_CID_MPEG_VIDEO_H264_LEVEL;
        control.value = V4L2_MPEG_VIDEO_H264_LEVEL_UNKNOWN;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_VP8_PROFILE_LEVEL;
        control.value = V4L2_MPEG_VIDC_VIDEO_VP8_UNUSED;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_HEVC_TIER_LEVEL;
        control.value = V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_UNKNOWN;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_TME) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_TME_LEVEL;
        control.value = V4L2_MPEG_VIDC_VIDEO_TME_LEVEL_INTEGER;
    } else {
        DEBUG_PRINT_ERROR("Wrong CODEC");
        return false;
    }

    /* If OMX_VIDEO_LEVEL_UNKNOWN then set default values assigned above  */
    if (eLevel != OMX_VIDEO_LEVEL_UNKNOWN) {
        if (!profile_level_converter::convert_omx_level_to_v4l2(m_sVenc_cfg.codectype, eLevel, &control.value)) {
            DEBUG_PRINT_LOW("Warning: Cannot find v4l2 level for OMX level : %d Codec : %lu Setting unknown level",
                              eLevel, m_sVenc_cfg.codectype);
        }
    }

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    profile_level.level = control.value;

    return true;
}

bool venc_dev::venc_set_voptiming_cfg( OMX_U32 TimeIncRes)
{

    struct venc_voptimingcfg vop_timing_cfg;

    DEBUG_PRINT_LOW("venc_set_voptiming_cfg: TimeRes = %u",
            (unsigned int)TimeIncRes);

    vop_timing_cfg.voptime_resolution = TimeIncRes;

    voptimecfg.voptime_resolution = vop_timing_cfg.voptime_resolution;
    return true;
}

bool venc_dev::venc_reconfigure_intra_refresh_period() {

    DEBUG_PRINT_LOW("venc_reconfigure_intra_refresh_period");
    if (intra_refresh.framecount) {
        OMX_U32 mb_size = 16;
        // Firmware will re-calculate mbcount if codec is HEVC.
        OMX_U32 num_mbs_per_frame = (ALIGN(m_sVenc_cfg.dvs_height, mb_size)/mb_size) * (ALIGN(m_sVenc_cfg.dvs_width, mb_size)/mb_size);
        OMX_U32 num_intra_refresh_mbs = 0;
        num_intra_refresh_mbs = ceil(num_mbs_per_frame / intra_refresh.framecount);

        intra_refresh.irmode     = OMX_VIDEO_IntraRefreshRandom;
        intra_refresh.mbcount    = num_intra_refresh_mbs;
    }

    if (venc_set_intra_refresh() == false) {
        DEBUG_PRINT_ERROR("ERROR: Setting Intra refresh failed");
        return false;
    }

    return true;
}

bool venc_dev::venc_reconfigure_intra_period()
{
    int  rc;
    bool isValidCodec        = false;
    bool isValidResolution   = false;
    bool isValidFps          = false;
    bool isValidOpRate       = false;
    bool isValidLayerCount   = false;
    bool enableBframes       = false;
    bool isValidLtrSetting   = false;
    bool isValidRcMode       = false;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_reconfigure_intra_period");

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264 &&
        ((codec_profile.profile == V4L2_MPEG_VIDEO_H264_PROFILE_MAIN) ||
         (codec_profile.profile == V4L2_MPEG_VIDEO_H264_PROFILE_HIGH))) {
        isValidCodec = true;
    }

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC &&
        ((codec_profile.profile == V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN) ||
         (codec_profile.profile == V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN10) ||
         (codec_profile.profile == V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN_STILL_PIC)) &&
         (m_codec != OMX_VIDEO_CodingImageHEIC)) {
        isValidCodec = true;
    }

    if ((m_sVenc_cfg.input_width <= VENC_BFRAME_MAX_WIDTH && m_sVenc_cfg.input_height <= VENC_BFRAME_MAX_HEIGHT) ||
        (m_sVenc_cfg.input_width <= VENC_BFRAME_MAX_HEIGHT && m_sVenc_cfg.input_height <= VENC_BFRAME_MAX_WIDTH)) {
        isValidResolution = true;
    }

    if ((m_sVenc_cfg.fps_num / m_sVenc_cfg.fps_den) <= VENC_BFRAME_MAX_FPS) {
        isValidFps = true;
    }

    if (operating_rate <= VENC_BFRAME_MAX_FPS) {
        isValidOpRate = true;
    }

    if (temporal_layers_config.nPLayers <= 1) {
        isValidLayerCount = true;
    }

    if ((rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_CFR) ||
        (rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_VFR) ||
        (rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_MBR_CFR) ||
        (rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_MBR_VFR)) {
            isValidRcMode = true;
    }

    isValidLtrSetting = ltrinfo.enabled ? false : true;

    enableBframes = isValidResolution   &&
                    isValidFps          &&
                    isValidOpRate       &&
                    isValidLayerCount   &&
                    isValidLtrSetting   &&
                    isValidRcMode       &&
                    isValidCodec        &&
                    !client_req_disable_bframe;

    DEBUG_PRINT_LOW("B-frame enablement = %u; Conditions for Resolution = %u, FPS = %u,"
                     " Operating rate = %u, Layer condition = %u,"
                     " LTR = %u, RC = %u Codec/Profile = %u Client request to disable = %u\n",
                     enableBframes, isValidResolution, isValidFps, isValidOpRate,
                     isValidLayerCount, isValidLtrSetting, isValidRcMode, isValidCodec, client_req_disable_bframe);

    if (enableBframes && intra_period.num_bframes == 0) {
        intra_period.num_bframes = VENC_BFRAME_MAX_COUNT;
        nPframes_cache = intra_period.num_pframes;
        intra_period.num_pframes = intra_period.num_pframes / (1 + intra_period.num_bframes);
        bframe_implicitly_enabled = true;
    } else if (!enableBframes && intra_period.num_bframes > 0) {
        intra_period.num_pframes = intra_period.num_pframes + (intra_period.num_pframes * intra_period.num_bframes);
        intra_period.num_bframes = 0;
    }

    if (!venc_calibrate_gop())
    {
        DEBUG_PRINT_ERROR("Invalid settings, Hybrid HP enabled with LTR OR Hier-pLayers OR bframes");
        return false;
    }

    control.id    = V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES;
    control.value = intra_period.num_pframes;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES value=%d", control.value);

    control.id    = V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES;
    control.value = intra_period.num_bframes;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES value=%lu", intra_period.num_bframes);

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264 ||
        m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        /*
         * This call is to ensure default idr period is set if client
         * did not set it using index OMX_IndexConfigVideoAVCIntraPeriod.
         */
        if (venc_set_idr_period(idrperiod.idrperiod) == false) {
            DEBUG_PRINT_ERROR("ERROR: Setting idr period failed");
            return false;
        }
    }

    return true;
}

bool venc_dev::venc_set_tile_dimension(OMX_U32 nTileDimension)
{
    int rc;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_set_tile_dimension: nTileDimension = %u", (unsigned int)nTileDimension);

    control.id = V4L2_CID_MPEG_VIDC_IMG_GRID_DIMENSION;
    control.value = nTileDimension;
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    mIsGridset = true;
    mTileDimension = nTileDimension;

    return true;
}

bool venc_dev::venc_set_intra_period(OMX_U32 nPFrames, OMX_U32 nBFrames)
{
    DEBUG_PRINT_LOW("venc_set_intra_period: nPFrames = %u, nBFrames: %u", (unsigned int)nPFrames, (unsigned int)nBFrames);

    if ((streaming[OUTPUT_PORT] || streaming[CAPTURE_PORT]) && (intra_period.num_bframes != nBFrames)) {
        DEBUG_PRINT_ERROR("Invalid settings, Cannot change B frame count dynamically");
        return false;
    }
    return _venc_set_intra_period(nPFrames, nBFrames);
}

bool venc_dev::_venc_set_intra_period(OMX_U32 nPFrames, OMX_U32 nBFrames)
{
    int rc;
    struct v4l2_control control;
    char property_value[PROPERTY_VALUE_MAX] = {0};

    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264 &&
        m_sVenc_cfg.codectype != V4L2_PIX_FMT_HEVC &&
        m_codec == OMX_VIDEO_CodingImageHEIC) {
        nBFrames = 0;
    }

    if ((codec_profile.profile != V4L2_MPEG_VIDEO_MPEG4_PROFILE_ADVANCED_SIMPLE) &&
        (codec_profile.profile != V4L2_MPEG_VIDEO_H264_PROFILE_MAIN)             &&
        (codec_profile.profile != V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN)        &&
        (codec_profile.profile != V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN10)      &&
        (codec_profile.profile != V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN_STILL_PIC) &&
        (codec_profile.profile != V4L2_MPEG_VIDEO_H264_PROFILE_HIGH)) {
        nBFrames = 0;
    }

    if (temporal_layers_config.nPLayers > 1 && nBFrames) {
        DEBUG_PRINT_ERROR("Invalid settings, bframes cannot be enabled with HP. Resetting it to 0");
        nBFrames = 0;
    }

    if (!venc_validate_range(V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES, nBFrames) || (nBFrames > VENC_BFRAME_MAX_COUNT)) {
        DEBUG_PRINT_ERROR("Invalid settings, hardware doesn't support %u bframes", nBFrames);
        return false;
    }

    intra_period.num_pframes = nPFrames;
    intra_period.num_bframes = nBFrames;

    if (!venc_calibrate_gop())
    {
        DEBUG_PRINT_ERROR("Invalid settings, Hybrid HP enabled with LTR OR Hier-pLayers OR bframes");
        return false;
    }

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_NUM_P_FRAMES;
    control.value = intra_period.num_pframes;
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_NUM_B_FRAMES;
    control.value = intra_period.num_bframes;
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    return true;
}

bool venc_dev::venc_set_idr_period(OMX_U32 nIDRPeriod)
{
    int rc = 0;
    struct v4l2_control control;

    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264 &&
        m_sVenc_cfg.codectype != V4L2_PIX_FMT_HEVC) {
        // don't return error if idr period is zero even though invalid codectype
        if (!nIDRPeriod)
            return true;

        DEBUG_PRINT_ERROR("venc_set_idr_period: invalid codedtype (%#lx)",
            m_sVenc_cfg.codectype);
        return false;
    }

    DEBUG_PRINT_LOW("venc_set_idr_period: nIDRPeriod: %u", (unsigned int)nIDRPeriod);
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_IDR_PERIOD;
    control.value = nIDRPeriod;
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }
    idrperiod.idrperiod = nIDRPeriod;

    return true;
}

bool venc_dev::venc_set_entropy_config(OMX_BOOL enable, OMX_U32 i_cabac_level)
{
    int rc = 0;
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_set_entropy_config: CABAC = %u level: %u", enable, (unsigned int)i_cabac_level);

    if (enable && (codec_profile.profile != V4L2_MPEG_VIDEO_H264_PROFILE_BASELINE) &&
            (codec_profile.profile != V4L2_MPEG_VIDEO_H264_PROFILE_CONSTRAINED_BASELINE)) {

        control.value = V4L2_MPEG_VIDEO_H264_ENTROPY_MODE_CABAC;
        control.id = V4L2_CID_MPEG_VIDEO_H264_ENTROPY_MODE;

        DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
        rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

        if (rc) {
            DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
            return false;
        }

        DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
        entropy.longentropysel = control.value;

        if (i_cabac_level == 0) {
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_H264_CABAC_MODEL_0;
        } else if (i_cabac_level == 1) {
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_H264_CABAC_MODEL_1;
        } else if (i_cabac_level == 2) {
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_H264_CABAC_MODEL_2;
        }

        control.id = V4L2_CID_MPEG_VIDC_VIDEO_H264_CABAC_MODEL;
        //control.value = entropy_cfg.cabacmodel;
        DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
        rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

        if (rc) {
            DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
            return false;
        }

        DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
        entropy.cabacmodel=control.value;
    } else if (!enable) {
        control.value =  V4L2_MPEG_VIDEO_H264_ENTROPY_MODE_CAVLC;
        control.id = V4L2_CID_MPEG_VIDEO_H264_ENTROPY_MODE;
        DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
        rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

        if (rc) {
            DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
            return false;
        }

        DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
        entropy.longentropysel=control.value;
    } else {
        DEBUG_PRINT_ERROR("Invalid Entropy mode for Baseline Profile");
        return false;
    }

    return true;
}

bool venc_dev::venc_set_multislice_cfg(OMX_U32 nSlicemode, OMX_U32 nSlicesize)
{
    int rc;
    int slice_id = 0;
    struct v4l2_control control;
    bool status = true;

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H263 || nSlicesize == 0) {
        nSlicemode = V4L2_MPEG_VIDEO_MULTI_SLICE_MODE_SINGLE;
        nSlicesize = 0;
    }

    if (nSlicemode == V4L2_MPEG_VIDEO_MULTI_SICE_MODE_MAX_MB) {
        if (!venc_validate_range(V4L2_CID_MPEG_VIDEO_MULTI_SLICE_MAX_MB, nSlicesize)) {
            DEBUG_PRINT_ERROR("Invalid settings, hardware doesn't support %u as slicesize", nSlicesize);
            return false;
        }
        slice_id = V4L2_CID_MPEG_VIDEO_MULTI_SLICE_MAX_MB;

    } else if (nSlicemode == V4L2_MPEG_VIDEO_MULTI_SICE_MODE_MAX_BYTES) {
        if (!venc_validate_range(V4L2_CID_MPEG_VIDEO_MULTI_SLICE_MAX_BYTES, nSlicesize)) {
            DEBUG_PRINT_ERROR("Invalid settings, hardware doesn't support %u as slicesize", nSlicesize);
            return false;
        }
        slice_id = V4L2_CID_MPEG_VIDEO_MULTI_SLICE_MAX_BYTES;

    } else if (nSlicesize) {
        DEBUG_PRINT_ERROR("Invalid settings, unexpected slicemode = %u and slice size = %u", nSlicemode, nSlicesize);
        return false;
    }

    control.id    = V4L2_CID_MPEG_VIDEO_MULTI_SLICE_MODE;
    control.value = nSlicemode;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    if (nSlicemode == V4L2_MPEG_VIDEO_MULTI_SLICE_MODE_SINGLE) {
        return status;
    }

    control.id    = slice_id;
    control.value = nSlicesize;

    DEBUG_PRINT_LOW("Calling SLICE_MB IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    multislice.mslice_mode = nSlicemode;
    multislice.mslice_size = nSlicesize;

    return status;
}

bool venc_dev::venc_set_intra_refresh()
{
    bool status = true;
    int rc;
    struct v4l2_control control_mode, control_mbs;
    control_mode.id   = V4L2_CID_MPEG_VIDC_VIDEO_INTRA_REFRESH_MODE;
    control_mbs.id    = V4L2_CID_MPEG_VIDC_VIDEO_IR_MBS;
    control_mbs.value = 0;
    // There is no disabled mode.  Disabled mode is indicated by a 0 count.
    if (intra_refresh.irmode == OMX_VIDEO_IntraRefreshMax || intra_refresh.mbcount == 0) {
        control_mode.value = V4L2_CID_MPEG_VIDC_VIDEO_INTRA_REFRESH_NONE;
        return status;
    } else if (intra_refresh.irmode == OMX_VIDEO_IntraRefreshCyclic) {
        control_mode.value = V4L2_CID_MPEG_VIDC_VIDEO_INTRA_REFRESH_CYCLIC;
        control_mbs.value  = intra_refresh.mbcount;
    } else if (intra_refresh.irmode == OMX_VIDEO_IntraRefreshRandom) {
        control_mode.value = V4L2_CID_MPEG_VIDC_VIDEO_INTRA_REFRESH_RANDOM;
        control_mbs.value  = intra_refresh.mbcount;
    } else {
        DEBUG_PRINT_ERROR("ERROR: Invalid IntraRefresh Parameters:"
                " mb mode:%lu", intra_refresh.irmode);
        return false;
    }

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%u, val=%d", control_mode.id, control_mode.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control_mode);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control_mode.id, control_mode.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control_mode.id, control_mode.value);

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%u, val=%d", control_mbs.id, control_mbs.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control_mbs);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control_mbs.id, control_mbs.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control_mbs.id, control_mbs.value);

    intra_refresh.irmode  = control_mode.value;
    intra_refresh.mbcount = control_mbs.value;

    return status;
}

bool venc_dev::venc_set_error_resilience(OMX_VIDEO_PARAM_ERRORCORRECTIONTYPE* error_resilience)
{
    bool status = true;
    struct venc_headerextension hec_cfg;
    struct venc_multiclicecfg multislice_cfg;
    int rc;
    OMX_U32 resynchMarkerSpacingBytes = 0;
    struct v4l2_control control;

    memset(&control, 0, sizeof(control));

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_MPEG4) {
        if (error_resilience->bEnableHEC) {
            hec_cfg.header_extension = 1;
        } else {
            hec_cfg.header_extension = 0;
        }

        hec.header_extension = error_resilience->bEnableHEC;
    }

    if (error_resilience->bEnableRVLC) {
        DEBUG_PRINT_ERROR("RVLC is not Supported");
        return false;
    }

    if (( m_sVenc_cfg.codectype != V4L2_PIX_FMT_H263) &&
            (error_resilience->bEnableDataPartitioning)) {
        DEBUG_PRINT_ERROR("DataPartioning are not Supported for MPEG4/H264");
        return false;
    }

    if (error_resilience->nResynchMarkerSpacing) {
        resynchMarkerSpacingBytes = error_resilience->nResynchMarkerSpacing;
        resynchMarkerSpacingBytes = ALIGN(resynchMarkerSpacingBytes, 8) >> 3;
    }

    status = venc_set_multislice_cfg(V4L2_MPEG_VIDEO_MULTI_SICE_MODE_MAX_BYTES, resynchMarkerSpacingBytes);

    return status;
}

bool venc_dev::venc_set_inloop_filter(OMX_VIDEO_AVCLOOPFILTERTYPE loopfilter)
{
    int rc;
    struct v4l2_control control;
    control.id=V4L2_CID_MPEG_VIDEO_H264_LOOP_FILTER_MODE;
    control.value=0;

    if (loopfilter == OMX_VIDEO_AVCLoopFilterEnable) {
        control.value=V4L2_MPEG_VIDEO_H264_LOOP_FILTER_MODE_ENABLED;
    } else if (loopfilter == OMX_VIDEO_AVCLoopFilterDisable) {
        control.value=V4L2_MPEG_VIDEO_H264_LOOP_FILTER_MODE_DISABLED;
    } else if (loopfilter == OMX_VIDEO_AVCLoopFilterDisableSliceBoundary) {
        control.value=V4L2_MPEG_VIDEO_H264_LOOP_FILTER_MODE_DISABLED_AT_SLICE_BOUNDARY;
    }

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    dbkfilter.db_mode=control.value;

    control.id=V4L2_CID_MPEG_VIDEO_H264_LOOP_FILTER_ALPHA;
    control.value=0;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    control.id=V4L2_CID_MPEG_VIDEO_H264_LOOP_FILTER_BETA;
    control.value=0;
    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);


    dbkfilter.slicealpha_offset = dbkfilter.slicebeta_offset = 0;
    return true;
}

bool venc_dev::venc_set_target_bitrate(OMX_U32 nTargetBitrate)
{
    DEBUG_PRINT_LOW("venc_set_target_bitrate: bitrate = %u",
            (unsigned int)nTargetBitrate);
    struct v4l2_control control;
    int rc = 0;

    if (vqzip_sei_info.enabled) {
        DEBUG_PRINT_HIGH("For VQZIP 1.0, Bitrate setting is not supported");
        return true;
    }

    if (rate_ctrl.rcmode == V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CQ)
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_FRAME_QUALITY;
    else
        control.id = V4L2_CID_MPEG_VIDEO_BITRATE;
    control.value = nTargetBitrate;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set control, id %#x, value %d", control.id, control.value);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    if (control.id == V4L2_CID_MPEG_VIDC_VIDEO_FRAME_QUALITY)
        return true;

    m_sVenc_cfg.targetbitrate = control.value;
    bitrate.target_bitrate = control.value;

    // Configure layer-wise bitrate if temporal layers are enabled and layer-wise distribution
    //  has been specified
    if (temporal_layers_config.bIsBitrateRatioValid && temporal_layers_config.nPLayers) {
        OMX_U32 layerBitrates[OMX_VIDEO_MAX_HP_LAYERS] = {0},
                numLayers = temporal_layers_config.nPLayers + temporal_layers_config.nBLayers;

        DEBUG_PRINT_LOW("TemporalLayer: configuring layerwise bitrate");
        for (OMX_U32 i = 0; i < numLayers; ++i) {
            layerBitrates[i] =
                    (temporal_layers_config.nTemporalLayerBitrateFraction[i] * bitrate.target_bitrate) / 100;
            DEBUG_PRINT_LOW("TemporalLayer: layer[%u] ratio=%u%% bitrate=%u(of %ld)",
                    i, temporal_layers_config.nTemporalLayerBitrateFraction[i],
                    layerBitrates[i], bitrate.target_bitrate);
        }
        if (!venc_set_layer_bitrates((OMX_U32 *)layerBitrates, numLayers)) {
            return false;
        }
    }

    return true;
}

bool venc_dev::venc_set_encode_framerate(OMX_U32 encode_framerate)
{
    struct v4l2_streamparm parm;
    int rc = 0;
    struct venc_framerate frame_rate_cfg;
    Q16ToFraction(encode_framerate,frame_rate_cfg.fps_numerator,frame_rate_cfg.fps_denominator);
    parm.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    parm.parm.output.timeperframe.numerator = frame_rate_cfg.fps_denominator;
    parm.parm.output.timeperframe.denominator = frame_rate_cfg.fps_numerator;

    if (vqzip_sei_info.enabled) {
        DEBUG_PRINT_HIGH("For VQZIP 1.0, Framerate setting is not supported");
        return true;
    }


    if (frame_rate_cfg.fps_numerator > 0)
        rc = ioctl(m_nDriver_fd, VIDIOC_S_PARM, &parm);

    if (rc) {
        DEBUG_PRINT_ERROR("ERROR: Request for setting framerate failed");
        return false;
    }

    m_sVenc_cfg.fps_den = frame_rate_cfg.fps_denominator;
    m_sVenc_cfg.fps_num = frame_rate_cfg.fps_numerator;

    return true;
}

unsigned long venc_dev::venc_get_codectype(OMX_VIDEO_CODINGTYPE eCompressionFormat)
{
    unsigned long codectype = V4L2_PIX_FMT_H264;

    switch ((int)eCompressionFormat) {
    case OMX_VIDEO_CodingAVC:
        codectype = V4L2_PIX_FMT_H264;
        break;
    case OMX_VIDEO_CodingVP8:
        codectype = V4L2_PIX_FMT_VP8;
        break;
    case OMX_VIDEO_CodingVP9:
        codectype = V4L2_PIX_FMT_VP9;
        break;
    case OMX_VIDEO_CodingImageHEIC:
    case OMX_VIDEO_CodingHEVC:
        codectype = V4L2_PIX_FMT_HEVC;
        break;
    case QOMX_VIDEO_CodingTME:
        codectype = V4L2_PIX_FMT_TME;
        break;
    default:
        DEBUG_PRINT_ERROR("Unsupported eCompressionFormat %#x", eCompressionFormat);
        codectype = V4L2_PIX_FMT_H264;
        break;
    }

    return codectype;
}

unsigned long venc_dev::venc_get_color_format(OMX_COLOR_FORMATTYPE eColorFormat)
{
    unsigned long format = V4L2_DEFAULT_OUTPUT_COLOR_FMT;

    switch ((int)eColorFormat) {
    case OMX_COLOR_FormatYUV420SemiPlanar:
    case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m:
        format = V4L2_PIX_FMT_NV12;
        break;
    case QOMX_COLOR_FormatYVU420SemiPlanar:
        format = V4L2_PIX_FMT_NV21;
        break;
    case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32mCompressed:
        format = V4L2_PIX_FMT_NV12_UBWC;
        break;
    case QOMX_COLOR_Format32bitRGBA8888:
        format = V4L2_PIX_FMT_RGB32;
        break;
    case QOMX_COLOR_Format32bitRGBA8888Compressed:
        format = V4L2_PIX_FMT_RGBA8888_UBWC;
        break;
    case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m10bitCompressed:
        format = V4L2_PIX_FMT_NV12_TP10_UBWC;
        break;
    default:
        DEBUG_PRINT_ERROR("Unsupported eColorFormat %#x", eColorFormat);
        format = V4L2_DEFAULT_OUTPUT_COLOR_FMT;
        break;
    }

    return format;
}

bool venc_dev::venc_set_color_format(OMX_COLOR_FORMATTYPE color_format)
{
    struct v4l2_format fmt;
    int color_space = 0;
    DEBUG_PRINT_LOW("venc_set_color_format: color_format = %u ", color_format);

    switch ((int)color_format) {
        case OMX_COLOR_FormatYUV420SemiPlanar:
        case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12;
            color_space = V4L2_COLORSPACE_470_SYSTEM_BG;
            break;
        case QOMX_COLOR_FormatYVU420SemiPlanar:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV21;
            color_space = V4L2_COLORSPACE_470_SYSTEM_BG;
            break;
        case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32mCompressed:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12_UBWC;
            color_space = V4L2_COLORSPACE_470_SYSTEM_BG;
            break;
        case QOMX_COLOR_Format32bitRGBA8888:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_RGB32;
            break;
        case QOMX_COLOR_Format32bitRGBA8888Compressed:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_RGBA8888_UBWC;
            break;
        case QOMX_COLOR_FORMATYUV420PackedSemiPlanar32m10bitCompressed:
            m_sVenc_cfg.inputformat = V4L2_PIX_FMT_NV12_TP10_UBWC;
            break;
        default:
            DEBUG_PRINT_HIGH("WARNING: Unsupported Color format [%d]", color_format);
            m_sVenc_cfg.inputformat = V4L2_DEFAULT_OUTPUT_COLOR_FMT;
            color_space = V4L2_COLORSPACE_470_SYSTEM_BG;
            DEBUG_PRINT_HIGH("Default color format NV12 UBWC is set");
            break;
    }

    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.inputformat;
    fmt.fmt.pix_mp.colorspace = color_space;
    fmt.fmt.pix_mp.height = m_sVenc_cfg.input_height;
    fmt.fmt.pix_mp.width = m_sVenc_cfg.input_width;

    if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
        DEBUG_PRINT_ERROR("Failed setting color format %x", color_format);
        return false;
    }

    return true;
}

bool venc_dev::venc_set_intra_vop_refresh(OMX_BOOL intra_vop_refresh)
{
    DEBUG_PRINT_LOW("venc_set_intra_vop_refresh: intra_vop = %uc", intra_vop_refresh);

    if (intra_vop_refresh == OMX_TRUE) {
        struct v4l2_control control;
        int rc;
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_REQUEST_IFRAME;
        control.value = 1;

        rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
        if (rc) {
            DEBUG_PRINT_ERROR("Failed to set Intra Frame Request control");
            return false;
        }
        DEBUG_PRINT_HIGH("Success IOCTL set control for id=%x, value=%d", control.id, control.value);
    } else {
        DEBUG_PRINT_ERROR("ERROR: VOP Refresh is False, no effect");
    }

    return true;
}

bool venc_dev::venc_calibrate_gop()
{
    int ratio, sub_gop_size, gop_size, nPframes, nBframes, nLayers;
    int num_sub_gops_in_a_gop;
    nPframes = intra_period.num_pframes;
    nBframes = intra_period.num_bframes;
    nLayers = temporal_layers_config.nPLayers + temporal_layers_config.nBLayers;

    if (!nPframes && nLayers) {
        DEBUG_PRINT_ERROR("nPframes should be non-zero when nLayers are present\n");
        return false;
    }

    if (nBframes && !nPframes) {
        DEBUG_PRINT_ERROR("nPframes should be non-zero when nBframes is non-zero\n");
        return false;
    }

    if (nLayers > 1) { /*Multi-layer encoding*/
        sub_gop_size = 1 << (nLayers - 1);
        /* Actual GOP definition is nPframes + nBframes + 1 but for the sake of
         * below calculations we are ignoring +1 . Ignoring +1 in below
         * calculations is not a mistake but intentional.
         */
        gop_size = MAX(sub_gop_size, ROUND(nPframes + (nPframes * nBframes), sub_gop_size));
        num_sub_gops_in_a_gop = gop_size/sub_gop_size;
        if (nBframes) { /*Hier-B case*/
        /*
            * Frame Type--> I  B  B  B  P  B  B  B  P  I  B  B  P ...
            * Layer -->     0  2  1  2  0  2  1  2  0  0  2  1  2 ...
            * nPframes = 2, nBframes = 3, nLayers = 3
            *
            * Intention is to keep the intraperiod as close as possible to what is desired
            * by the client while adjusting nPframes and nBframes to meet other constraints.
            * eg1: Input by client: nPframes =  9, nBframes = 14, nLayers = 2
            *    Output of this fn: nPframes = 12, nBframes = 12, nLayers = 2
            *
            * eg2: Input by client: nPframes = 9, nBframes = 4, nLayers = 2
            *    Output of this fn: nPframes = 7, nBframes = 7, nLayers = 2
            */
            nPframes = num_sub_gops_in_a_gop;
            nBframes = sub_gop_size - 1;
        } else { /*Hier-P case*/
            /*
            * Frame Type--> I  P  P  P  P  P  P  P  I  P  P  P  P ...
            * Layer-->      0  2  1  2  0  2  1  2  0  2  1  2  0 ...
            * nPframes =  7, nBframes = 0, nLayers = 3
            *
            * Intention is to keep the intraperiod as close as possible to what is desired
            * by the client while adjusting nPframes and nBframes to meet other constraints.
            * eg1: Input by client: nPframes = 9, nBframes = 0, nLayers = 3
            *    Output of this fn: nPframes = 7, nBframes = 0, nLayers = 3
            *
            * eg2: Input by client: nPframes = 10, nBframes = 0, nLayers = 3
            *     Output of this fn:nPframes = 12, nBframes = 0, nLayers = 3
            */
            nPframes = gop_size - 1;
        }
    } else { /*Single-layer encoding*/
            /*
            * No special handling needed for single layer
            */
    }

    DEBUG_PRINT_LOW("P/B Frames changed from: %ld/%ld to %d/%d",
        intra_period.num_pframes, intra_period.num_bframes, nPframes, nBframes);
    intra_period.num_pframes = nPframes;
    intra_period.num_bframes = nBframes;
    return true;
}

bool venc_dev::venc_set_bitrate_type(OMX_U32 type)
{
    struct v4l2_control control;
    int rc = 0;
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_VENC_BITRATE_TYPE;
    control.value = type;
    DEBUG_PRINT_LOW("Set Bitrate type to %s for %d \n", bitrate_type_string(type), type);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Request to set Bitrate type to %s failed",
            bitrate_type_string(type));
        return false;
    }
    return true;
}

bool venc_dev::venc_set_layer_bitrates(OMX_U32 *layerBitrate, OMX_U32 numLayers)
{
    DEBUG_PRINT_LOW("venc_set_layer_bitrates");
    struct v4l2_ext_control ctrl[2];
    struct v4l2_ext_controls controls;
    int rc = 0;
    OMX_U32 i;

    if (!venc_set_bitrate_type(V4L2_CID_MPEG_VIDC_VIDEO_VENC_BITRATE_ENABLE)) {
        DEBUG_PRINT_ERROR("Failed to set layerwise bitrate type %d", rc);
        return false;
    }

    for (OMX_U32 i = 0; i < numLayers && i < OMX_VIDEO_ANDROID_MAXTEMPORALLAYERS; ++i) {
        if (!layerBitrate[i]) {
            DEBUG_PRINT_ERROR("Invalid bitrate settings for layer %d", i);
            return false;
        } else {
            ctrl[0].id = V4L2_CID_MPEG_VIDC_VIDEO_LAYER_ID;
            ctrl[0].value = i;
            ctrl[1].id = V4L2_CID_MPEG_VIDC_VENC_PARAM_LAYER_BITRATE;
            ctrl[1].value = layerBitrate[i];

            controls.count = 2;
            controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
            controls.controls = ctrl;

            rc = ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls);
            if (rc) {
                DEBUG_PRINT_ERROR("Failed to set layerwise bitrate %d", rc);
                return false;
            }

            DEBUG_PRINT_LOW("Layerwise bitrate configured successfully for layer : %u bitrate : %u ",i, layerBitrate[i]);
        }
    }
    return true;
}

bool venc_dev::venc_set_ltrmode(OMX_U32 enable, OMX_U32 count)
{
    DEBUG_PRINT_LOW("venc_set_ltrmode: enable = %u", (unsigned int)enable);
    struct v4l2_ext_control ctrl[2];
    struct v4l2_ext_controls controls;
    int rc;

    if (enable && temporal_layers_config.hier_mode == HIER_P_HYBRID) {
        DEBUG_PRINT_ERROR("Invalid settings, LTR is being enabled with HybridHP");
        return false;
    }

    ctrl[0].id = V4L2_CID_MPEG_VIDC_VIDEO_LTRMODE;
    if (enable)
        ctrl[0].value = V4L2_MPEG_VIDC_VIDEO_LTR_MODE_MANUAL;
    else
        ctrl[0].value = V4L2_MPEG_VIDC_VIDEO_LTR_MODE_DISABLE;

    ctrl[1].id = V4L2_CID_MPEG_VIDC_VIDEO_LTRCOUNT;
    if (enable && count > 0)
        ctrl[1].value = count;
    else if (enable)
        ctrl[1].value = 1;
    else
        ctrl[1].value = 0;

    controls.count = 2;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%x, val=%d id=%x, val=%d",
                    controls.controls[0].id, controls.controls[0].value,
                    controls.controls[1].id, controls.controls[1].value);

    rc = ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set ltrmode %d", rc);
        return false;
    }
    ltrinfo.enabled = enable;
    ltrinfo.count = count;

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, val=%d id=%x, val=%d",
                    controls.controls[0].id, controls.controls[0].value,
                    controls.controls[1].id, controls.controls[1].value);
    return true;
}

bool venc_dev::venc_set_useltr(OMX_U32 frameIdx)
{
    DEBUG_PRINT_LOW("venc_use_goldenframe");
    int rc = true;
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_USELTRFRAME;
    control.value = frameIdx;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set use_ltr %d", rc);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, val=%d",
                    control.id, control.value);
    return true;
}

bool venc_dev::venc_set_markltr(OMX_U32 frameIdx)
{
    DEBUG_PRINT_LOW("venc_set_goldenframe");
    int rc = true;
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_MARKLTRFRAME;
    control.value = frameIdx;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set ltrmode %d", rc);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, val=%d",
                    control.id, control.value);
    return true;
}

bool venc_dev::venc_set_mirror(OMX_U32 mirror)
{
    DEBUG_PRINT_LOW("venc_set_mirror");
    int rc = true;
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_FLIP;
    control.value = mirror;

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set mirror %d", rc);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, val=%d",
                    control.id, control.value);
    return true;
}

bool venc_dev::venc_set_vpe_rotation(OMX_S32 rotation_angle)
{
    DEBUG_PRINT_LOW("venc_set_vpe_rotation: rotation angle = %d", (int)rotation_angle);
    struct v4l2_control control;
    int rc;
    struct v4l2_format fmt;
    struct v4l2_requestbuffers bufreq;
    bool flip_dimensions = false;

    if ((OMX_S32)m_rotation.rotation == rotation_angle) {
        DEBUG_PRINT_HIGH("venc_set_vpe_rotation: rotation (%d) not changed", rotation_angle);
        return true;
    }

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_ROTATION;
    if (rotation_angle == 0) {
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_ROTATION_NONE;
        if (m_rotation.rotation == 90 || m_rotation.rotation == 270)
            flip_dimensions = true;
    } else if (rotation_angle == 90) {
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_ROTATION_90;
        if (m_rotation.rotation == 0 || m_rotation.rotation == 180)
            flip_dimensions = true;
    } else if (rotation_angle == 180) {
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_ROTATION_180;
        if (m_rotation.rotation == 90 || m_rotation.rotation == 270)
            flip_dimensions = true;
    } else if (rotation_angle == 270) {
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_ROTATION_270;
        if (m_rotation.rotation == 0 || m_rotation.rotation == 180)
            flip_dimensions = true;
    } else {
        DEBUG_PRINT_ERROR("Failed to find valid rotation angle");
        return false;
    }

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%x, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_HIGH("Failed to set VPE Rotation control");
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, value=%d", control.id, control.value);

    /* successfully set rotation_angle, save it */
    m_rotation.rotation = rotation_angle;

    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    if (flip_dimensions) {
        OMX_U32 nWidth = m_sVenc_cfg.dvs_height;
        OMX_U32 nHeight = m_sVenc_cfg.dvs_width;
        m_sVenc_cfg.dvs_height = nHeight;
        m_sVenc_cfg.dvs_width = nWidth;
        DEBUG_PRINT_LOW("Rotation (%u) Flipping wxh to %lux%lu",
                rotation_angle, m_sVenc_cfg.dvs_width, m_sVenc_cfg.dvs_height);
    }

    fmt.fmt.pix_mp.height = m_sVenc_cfg.dvs_height;
    fmt.fmt.pix_mp.width = m_sVenc_cfg.dvs_width;
    fmt.fmt.pix_mp.pixelformat = m_sVenc_cfg.codectype;
    if (ioctl(m_nDriver_fd, VIDIOC_S_FMT, &fmt)) {
        DEBUG_PRINT_ERROR("Failed to set format on capture port");
        return false;
    }

    m_sOutput_buff_property.datasize = fmt.fmt.pix_mp.plane_fmt[0].sizeimage;
    bufreq.memory = V4L2_MEMORY_USERPTR;
    bufreq.count = m_sOutput_buff_property.actualcount;
    bufreq.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    if (ioctl(m_nDriver_fd,VIDIOC_REQBUFS, &bufreq)) {
        DEBUG_PRINT_ERROR("ERROR: Request for o/p buffer count failed for rotation");
            return false;
    }
    if (bufreq.count >= m_sOutput_buff_property.mincount)
        m_sOutput_buff_property.actualcount = m_sOutput_buff_property.mincount = bufreq.count;

    return true;
}

bool venc_dev::venc_set_searchrange()
{
    DEBUG_PRINT_LOW("venc_set_searchrange");
    struct v4l2_control control;
    struct v4l2_ext_control ctrl[6];
    struct v4l2_ext_controls controls;
    int rc;

    if ((m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) ||
               (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8)) {
        ctrl[0].id = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_X_RANGE;
        ctrl[0].value = 16;
        ctrl[1].id = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_Y_RANGE;
        ctrl[1].value = 4;
        ctrl[2].id = V4L2_CID_MPEG_VIDC_VIDEO_PFRAME_X_RANGE;
        ctrl[2].value = 16;
        ctrl[3].id = V4L2_CID_MPEG_VIDC_VIDEO_PFRAME_Y_RANGE;
        ctrl[3].value = 4;
        ctrl[4].id = V4L2_CID_MPEG_VIDC_VIDEO_BFRAME_X_RANGE;
        ctrl[4].value = 12;
        ctrl[5].id = V4L2_CID_MPEG_VIDC_VIDEO_BFRAME_Y_RANGE;
        ctrl[5].value = 4;
    } else {
        DEBUG_PRINT_ERROR("Invalid codec type");
        return false;
    }
    controls.count = 6;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    DEBUG_PRINT_LOW(" Calling IOCTL set control for"
        "id=%x, val=%d id=%x, val=%d"
        "id=%x, val=%d id=%x, val=%d"
        "id=%x, val=%d id=%x, val=%d",
        controls.controls[0].id, controls.controls[0].value,
        controls.controls[1].id, controls.controls[1].value,
        controls.controls[2].id, controls.controls[2].value,
        controls.controls[3].id, controls.controls[3].value,
        controls.controls[4].id, controls.controls[4].value,
        controls.controls[5].id, controls.controls[5].value);

    rc = ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set search range %d", rc);
        return false;
    }
    return true;
}

bool venc_dev::venc_set_ratectrl_cfg(OMX_VIDEO_CONTROLRATETYPE eControlRate)
{
    bool status = true;
    struct v4l2_control control;
    int rc = 0;
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL;

    switch ((OMX_U32)eControlRate) {
        case OMX_Video_ControlRateDisable:
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_OFF;
            break;
        case OMX_Video_ControlRateVariableSkipFrames:
            (supported_rc_modes & RC_VBR_VFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_VFR :
                status = false;
            break;
        case OMX_Video_ControlRateVariable:
            (supported_rc_modes & RC_VBR_CFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_CFR :
                status = false;
            break;
        case OMX_Video_ControlRateConstantSkipFrames:
            (supported_rc_modes & RC_CBR_VFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CBR_VFR :
                status = false;
            break;
        case OMX_Video_ControlRateConstant:
            (supported_rc_modes & RC_CBR_CFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CBR_CFR :
                status = false;
            break;
        case QOMX_Video_ControlRateMaxBitrate:
            (supported_rc_modes & RC_MBR_CFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_MBR_CFR:
                status = false;
            break;
        case QOMX_Video_ControlRateMaxBitrateSkipFrames:
            (supported_rc_modes & RC_MBR_VFR) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_MBR_VFR:
                status = false;
            break;
        case OMX_Video_ControlRateConstantQuality:
            (supported_rc_modes & RC_CQ) ?
                control.value = V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CQ:
                status = false;
            break;
        default:
            status = false;
            break;
    }

    if (status) {

        DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
        rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

        if (rc) {
            DEBUG_PRINT_ERROR("Failed to set control");
            return false;
        }

        DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

        rate_ctrl.rcmode = control.value;
    }

#ifdef _VQZIP_
    if (eControlRate == OMX_Video_ControlRateVariable && (supported_rc_modes & RC_VBR_CFR)
            && m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        /* Enable VQZIP SEI by default for camcorder RC modes */

        control.id = V4L2_CID_MPEG_VIDC_VIDEO_VQZIP_SEI;
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_VQZIP_SEI_ENABLE;
        DEBUG_PRINT_HIGH("Set VQZIP SEI:");
        if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control) < 0) {
            DEBUG_PRINT_HIGH("Non-Fatal: Request to set VQZIP failed");
        }
    }
#endif

    return status;
}

bool venc_dev::venc_set_aspectratio(void *nSar)
{
    int rc;
    struct v4l2_control control;
    struct v4l2_ext_control ctrl[2];
    struct v4l2_ext_controls controls;
    QOMX_EXTNINDEX_VIDEO_VENC_SAR *sar;

    sar = (QOMX_EXTNINDEX_VIDEO_VENC_SAR *) nSar;

    ctrl[0].id = V4L2_CID_MPEG_VIDC_VENC_PARAM_SAR_WIDTH;
    ctrl[0].value = sar->nSARWidth;
    ctrl[1].id = V4L2_CID_MPEG_VIDC_VENC_PARAM_SAR_HEIGHT;
    ctrl[1].value = sar->nSARHeight;

    controls.count = 2;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%x val=%d, id=%x val=%d",
                    controls.controls[0].id, controls.controls[0].value,
                    controls.controls[1].id, controls.controls[1].value);

    rc = ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set SAR %d", rc);
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x val=%d, id=%x val=%d",
                    controls.controls[0].id, controls.controls[0].value,
                    controls.controls[1].id, controls.controls[1].value);
    return true;
}

bool venc_dev::venc_set_lowlatency_mode(OMX_BOOL enable)
{
    int rc = 0;
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_LOWLATENCY_MODE;
    if (enable)
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_LOWLATENCY_ENABLE;
    else
        control.value = V4L2_CID_MPEG_VIDC_VIDEO_LOWLATENCY_DISABLE;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%x, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set lowlatency control");
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, value=%d", control.id, control.value);

    return true;
}

bool venc_dev::venc_set_iframesize_type(QOMX_VIDEO_IFRAMESIZE_TYPE type)
{
    struct v4l2_control control;
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_TYPE;

    switch (type) {
        case QOMX_IFRAMESIZE_DEFAULT:
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_DEFAULT;
            break;
        case QOMX_IFRAMESIZE_MEDIUM:
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_MEDIUM;
            break;
        case QOMX_IFRAMESIZE_HUGE:
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_HUGE;
            break;
        case QOMX_IFRAMESIZE_UNLIMITED:
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_UNLIMITED;
            break;
        default:
            DEBUG_PRINT_INFO("Unknown Iframe Size found setting it to default");
            control.value = V4L2_CID_MPEG_VIDC_VIDEO_IFRAME_SIZE_DEFAULT;
    }

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("Failed to set iframe size hint");
        return false;
    }

    return true;
}

bool venc_dev::venc_set_baselayerid(OMX_U32 baseid)
{
    struct v4l2_control control;
    if (temporal_layers_config.hier_mode == HIER_P) {
        control.id = V4L2_CID_MPEG_VIDC_VIDEO_BASELAYER_ID;
        control.value = baseid;
        DEBUG_PRINT_LOW("Going to set V4L2_CID_MPEG_VIDC_VIDEO_BASELAYER_ID");
        if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
            DEBUG_PRINT_ERROR("Failed to set V4L2_CID_MPEG_VIDC_VIDEO_BASELAYER_ID");
            return false;
        }
        return true;
    } else {
        DEBUG_PRINT_ERROR("Invalid mode set for V4L2_CID_MPEG_VIDC_VIDEO_BASELAYER_ID: %d",
                temporal_layers_config.hier_mode);
        return false;
    }
}

bool venc_dev::venc_set_vui_timing_info(OMX_BOOL enable)
{
    struct v4l2_control control;
    int rc = 0;
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_VUI_TIMING_INFO;

    if (enable)
        control.value = V4L2_MPEG_VIDC_VIDEO_VUI_TIMING_INFO_ENABLED;
    else
        control.value = V4L2_MPEG_VIDC_VIDEO_VUI_TIMING_INFO_DISABLED;

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%x, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set VUI timing info control");
        return false;
    }
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%x, value=%d", control.id, control.value);
    return true;
}

bool venc_dev::venc_set_peak_bitrate(OMX_U32 nPeakBitrate)
{
    struct v4l2_control control;
    int rc = 0;
    control.id = V4L2_CID_MPEG_VIDEO_BITRATE_PEAK;
    control.value = nPeakBitrate;

    DEBUG_PRINT_LOW("venc_set_peak_bitrate: bitrate = %u", (unsigned int)nPeakBitrate);

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);
    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);

    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set peak bitrate control");
        return false;
    }

    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);

    return true;
}

bool venc_dev::venc_set_vpx_error_resilience(OMX_BOOL enable)
{
    struct v4l2_control control;
    int rc = 0;
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_VPX_ERROR_RESILIENCE;

    if (enable)
        control.value = 1;
    else
        control.value = 0;

    DEBUG_PRINT_LOW("venc_set_vpx_error_resilience: %d", control.value);

    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%d", control.id, control.value);

    rc = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (rc) {
        DEBUG_PRINT_ERROR("Failed to set VPX Error Resilience");
        return false;
    }
    vpx_err_resilience.enable = 1;
    DEBUG_PRINT_LOW("Success IOCTL set control for id=%d, value=%d", control.id, control.value);
    return true;
}

bool venc_dev::venc_set_priority(OMX_U32 priority) {
    struct v4l2_control control;

    DEBUG_PRINT_LOW("venc_set_priority: %s", priority ? "NonRealTime" : "RealTime");
    control.id = V4L2_CID_MPEG_VIDC_VIDEO_PRIORITY;
    if (priority == 0)
        control.value = V4L2_MPEG_VIDC_VIDEO_PRIORITY_REALTIME_ENABLE;
    else
        control.value = V4L2_MPEG_VIDC_VIDEO_PRIORITY_REALTIME_DISABLE;

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("Failed to set V4L2_MPEG_VIDC_VIDEO_PRIORITY_REALTIME_%s",
                priority == 0 ? "ENABLE" : "DISABLE");
        return false;
    }
    return true;
}

bool venc_dev::venc_set_operatingrate(OMX_U32 rate) {
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_OPERATING_RATE;
    control.value = rate;

    DEBUG_PRINT_LOW("venc_set_operating_rate: %u fps", rate >> 16);
    DEBUG_PRINT_LOW("Calling IOCTL set control for id=%d, val=%u", control.id, control.value);

    if(ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        hw_overload = errno == EBUSY;
        DEBUG_PRINT_ERROR("Failed to set operating rate %d fps (%s)",
                rate >> 16, hw_overload ? "HW overload" : strerror(errno));
        return false;
    }
    if (rate == QOMX_VIDEO_HIGH_PERF_OPERATING_MODE) {
        DEBUG_PRINT_LOW("Turbo mode requested");
        client_req_turbo_mode = true;
    } else {
        operating_rate = rate >> 16;
        client_req_turbo_mode = false;
        DEBUG_PRINT_LOW("Operating Rate Set = %d fps",  rate >> 16);
    }

    return true;
}

bool venc_dev::venc_set_roi_qp_info(OMX_QTI_VIDEO_CONFIG_ROIINFO *roiInfo)
{
    struct roidata roi;

    if (!m_roi_enabled) {
        DEBUG_PRINT_ERROR("ROI info not enabled");
        return false;
    }

    if (!roiInfo) {
        DEBUG_PRINT_ERROR("No ROI info present");
        return false;
    }
    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264 &&
    m_sVenc_cfg.codectype != V4L2_PIX_FMT_HEVC) {
        DEBUG_PRINT_ERROR("OMX_QTIIndexConfigVideoRoiInfo is not supported for %d codec", (OMX_U32) m_sVenc_cfg.codectype);
        return false;
    }

    DEBUG_PRINT_HIGH("ROI QP info received");
    memset(&roi, 0, sizeof(struct roidata));

    roi.info.nUpperQpOffset = roiInfo->nUpperQpOffset;
    roi.info.nLowerQpOffset = roiInfo->nLowerQpOffset;
    roi.info.bUseRoiInfo = roiInfo->bUseRoiInfo;
    roi.info.nRoiMBInfoSize = roiInfo->nRoiMBInfoSize;

    roi.info.pRoiMBInfo = malloc(roi.info.nRoiMBInfoSize);
    if (!roi.info.pRoiMBInfo) {
        DEBUG_PRINT_ERROR("venc_set_roi_qp_info: malloc failed.");
        return false;
    }
    memcpy(roi.info.pRoiMBInfo, roiInfo->pRoiMBInfo, roiInfo->nRoiMBInfoSize);
    /*
     * set the timestamp equal to previous etb timestamp + 1
     * to know this roi data arrived after previous etb
     */
    if (venc_handle->m_etb_count)
        roi.timestamp = venc_handle->m_etb_timestamp + 1;
    else
        roi.timestamp = 0;

    roi.dirty = true;

    pthread_mutex_lock(&m_roilock);
    DEBUG_PRINT_LOW("list add roidata with timestamp %lld us.", roi.timestamp);
    m_roilist.push_back(roi);
    pthread_mutex_unlock(&m_roilock);

    return true;
}

bool venc_dev::venc_set_blur_resolution(OMX_QTI_VIDEO_CONFIG_BLURINFO *blurInfo)
{
    struct v4l2_ext_control ctrl[2];
    struct v4l2_ext_controls controls;

    int blur_width = 0, blur_height = 0;

    switch (blurInfo->eTargetResol) {
        case BLUR_RESOL_DISABLED:
            blur_width = 0;
            blur_height = 0;
            break;
        case BLUR_RESOL_240:
            blur_width = 426;
            blur_height = 240;
            break;
        case BLUR_RESOL_480:
            blur_width = 854;
            blur_height = 480;
            break;
        case BLUR_RESOL_720:
            blur_width = 1280;
            blur_height = 720;
            break;
        case BLUR_RESOL_1080:
            blur_width = 1920;
            blur_height = 1080;
            break;
        default:
            DEBUG_PRINT_ERROR("Blur resolution not recognized");
            return false;
    }

    ctrl[0].id = V4L2_CID_MPEG_VIDC_VIDEO_BLUR_WIDTH;
    ctrl[0].value = blur_width;

    ctrl[1].id = V4L2_CID_MPEG_VIDC_VIDEO_BLUR_HEIGHT;
    ctrl[1].value = blur_height;

    controls.count = 2;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    if(ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls)) {
        DEBUG_PRINT_ERROR("Failed to set blur resoltion");
        return false;
    }
    DEBUG_PRINT_LOW("Blur resolution set = %d x %d", blur_width, blur_height);
    return true;

}

bool venc_dev::venc_h264_transform_8x8(OMX_BOOL enable)
{
    struct v4l2_control control;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_H264_TRANSFORM_8x8;
    if (enable)
        control.value = V4L2_MPEG_VIDC_VIDEO_H264_TRANSFORM_8x8_ENABLE;
    else
        control.value = V4L2_MPEG_VIDC_VIDEO_H264_TRANSFORM_8x8_DISABLE;

    DEBUG_PRINT_LOW("Set h264_transform_8x8 mode: %d", control.value);
    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("set control: H264 transform 8x8 failed");
        return false;
    }

    return true;
}

bool venc_dev::venc_get_temporal_layer_caps(OMX_U32 *nMaxLayers,
        OMX_U32 *nMaxBLayers, OMX_VIDEO_ANDROID_TEMPORALLAYERINGPATTERNTYPE *eSupportedPattern) {
    struct v4l2_queryctrl query_ctrl;

    if(m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC || m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        *eSupportedPattern = OMX_VIDEO_AndroidTemporalLayeringPatternAndroid;
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        *eSupportedPattern = OMX_VIDEO_AndroidTemporalLayeringPatternWebRTC;
    } else {
        *eSupportedPattern = OMX_VIDEO_AndroidTemporalLayeringPatternNone;
    }

    if(venc_check_for_hybrid_hp(*eSupportedPattern)) {
        query_ctrl.id = V4L2_CID_MPEG_VIDC_VIDEO_HYBRID_HIERP_MODE;
    } else {
        query_ctrl.id = V4L2_CID_MPEG_VIDC_VIDEO_HIER_P_NUM_LAYERS;
    }

    DEBUG_PRINT_LOW("TemporalLayer: Querying P layer caps");
    if (ioctl(m_nDriver_fd, VIDIOC_QUERYCTRL, &query_ctrl)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Query control P layer caps failed");
        return false;
    }

    //Return +1 as driver works on num max enhancement layers and OMX on num layers
    *nMaxLayers = query_ctrl.maximum + 1;

    query_ctrl.id = V4L2_CID_MPEG_VIDC_VIDEO_HIER_B_NUM_LAYERS;
    DEBUG_PRINT_LOW("TemporalLayer: Querying B layer caps");
    if (ioctl(m_nDriver_fd, VIDIOC_QUERYCTRL, &query_ctrl)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Query control B layer caps faile");
        return false;
    }

    *nMaxBLayers = query_ctrl.maximum;
    return true;
}

bool venc_dev::venc_check_for_hybrid_hp(OMX_VIDEO_ANDROID_TEMPORALLAYERINGPATTERNTYPE ePattern) {
    //Hybrid HP is only for H264 and CFR
    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264) {
        DEBUG_PRINT_LOW("TemporalLayer: Hybrid HierP is not supported for non H264");
        return false;
    }

    if (ePattern != OMX_VIDEO_AndroidTemporalLayeringPatternAndroid) {
        DEBUG_PRINT_LOW("TemporalLayer: The pattern must be Android for Hybrid HP");
        return false;
    }

    if (rate_ctrl.rcmode != V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_CFR &&
        rate_ctrl.rcmode != V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_CBR_CFR &&
        rate_ctrl.rcmode != V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_MBR_CFR) {
        DEBUG_PRINT_LOW("TemporalLayer: RC must be CFR for Hybrid");
        return false;
    }

    return true;
}

bool venc_dev::venc_check_for_hierp(OMX_VIDEO_ANDROID_TEMPORALLAYERINGPATTERNTYPE ePattern) {

    if (ePattern != OMX_VIDEO_AndroidTemporalLayeringPatternAndroid) {
        DEBUG_PRINT_LOW("TemporalLayer: Incorrect Pattern for H264/HEVC");
        return false;
    }

    return true;
}

int venc_dev::venc_find_hier_type(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    enum hier_type hier;

    if (venc_check_for_hybrid_hp(temporal_settings.ePattern)) {
        hier = HIER_P_HYBRID;
    } else if (venc_check_for_hierp(temporal_settings.ePattern)) {
        hier = HIER_P;
    } else {
        hier = HIER_NONE;
    }

    if (!venc_validate_temporal_extn(temporal_settings)) {
        hier = HIER_NONE;
    }

    return hier;
}

OMX_ERRORTYPE venc_dev::venc_set_hp(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    struct v4l2_control control;
    OMX_U32 maxLayerCount = 0;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_MAX_HIERP_LAYERS;
    control.value = temporal_settings.nLayerCountMax - 1;

    DEBUG_PRINT_LOW("TemporalLayer: Setting HP with max layers: %u num layers : %u",
                    temporal_settings.nLayerCountMax - 1,
                    temporal_settings.nPLayerCountActual - 1);

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to set max HP layers to %u", control.value);
        return OMX_ErrorUnsupportedSetting;
    }

    maxLayerCount = control.value + 1;

    control.id = V4L2_CID_MPEG_VIDC_VIDEO_HIER_P_NUM_LAYERS;
    control.value = temporal_settings.nPLayerCountActual - 1;
    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to set hybrid hierp/hierp NumLayers : %u", control.value);
        return OMX_ErrorUnsupportedSetting;
    }

    temporal_layers_config.ePattern     = temporal_settings.ePattern;
    temporal_layers_config.hier_mode    = HIER_P;
    temporal_layers_config.nPLayers     = control.value + 1;
    temporal_layers_config.nMaxLayers   = maxLayerCount;
    temporal_layers_config.nBLayers     = 0;
    temporal_layers_config.nMaxBLayers  = 0;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE venc_dev::venc_set_hhp(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    struct v4l2_control control;

    control.id      = V4L2_CID_MPEG_VIDC_VIDEO_HYBRID_HIERP_MODE;
    control.value   = temporal_settings.nPLayerCountActual - 1;

    DEBUG_PRINT_LOW("TemporalLayer: Setting HybridHP with num layers : %u", control.value);

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to set hybrid HP. Try HierP");
        return OMX_ErrorUnsupportedSetting;
    }

    temporal_layers_config.ePattern     = temporal_settings.ePattern;
    temporal_layers_config.hier_mode    = HIER_P_HYBRID;
    temporal_layers_config.nPLayers     = control.value + 1;
    temporal_layers_config.nMaxLayers   = control.value + 1;
    temporal_layers_config.nBLayers     = 0;
    temporal_layers_config.nMaxBLayers  = 0;

    if (ltrinfo.enabled) {
        if (!venc_set_ltrmode(0, 0)) {
            DEBUG_PRINT_ERROR("TemporalLayer: Failed to disable LTR when HybridHP is enabled");
            return OMX_ErrorUndefined;
        }
    }

    return OMX_ErrorNone;
}

OMX_ERRORTYPE venc_dev::venc_disable_hhp() {

    struct v4l2_control control;

    control.id      = V4L2_CID_MPEG_VIDC_VIDEO_HYBRID_HIERP_MODE;
    control.value   = 0;

    DEBUG_PRINT_LOW("TemporalLayer: Disabling HybridHP");

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to disable hybrid HP");
        return OMX_ErrorUnsupportedSetting;
    }

    return OMX_ErrorNone;
}

OMX_ERRORTYPE venc_dev::venc_disable_hp() {

    struct v4l2_control control;

    control.id      = V4L2_CID_MPEG_VIDC_VIDEO_MAX_HIERP_LAYERS;
    control.value   = 0;

    DEBUG_PRINT_LOW("TemporalLayer: Disabling HP");

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to reset max HP layers to %u", control.value);
        return OMX_ErrorUnsupportedSetting;
    }

    control.id      = V4L2_CID_MPEG_VIDC_VIDEO_HIER_P_NUM_LAYERS;
    control.value   = 0;

    if (ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to reset HP layers to %u", control.value);
        return OMX_ErrorUnsupportedSetting;
    }

    return OMX_ErrorNone;
}

OMX_ERRORTYPE venc_dev::venc_set_bitrate_ratio(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    OMX_U32 layerBitrates[OMX_VIDEO_MAX_HP_LAYERS] = {0};
    OMX_U32 numLayers = temporal_settings.nPLayerCountActual + temporal_settings.nBLayerCountActual;

    if (temporal_settings.bBitrateRatiosSpecified == OMX_FALSE) {
        DEBUG_PRINT_LOW("TemporalLayer: layerwise bitrate ratio not specified. Will use cumulative");
        if (!venc_set_bitrate_type(V4L2_CID_MPEG_VIDC_VIDEO_VENC_BITRATE_DISABLE)) {
            return OMX_ErrorUnsupportedSetting;
        }
        return OMX_ErrorNone;
    }

    DEBUG_PRINT_LOW("TemporalLayer: layerwise bitrate ratio specified");

    for (OMX_U32 i = 0; i < numLayers; ++i) {
        OMX_U32 previousLayersAccumulatedBitrateRatio = (i == 0) ? 0 : temporal_settings.nBitrateRatios[i-1];
        OMX_U32 currentLayerBitrateRatio = temporal_settings.nBitrateRatios[i] - previousLayersAccumulatedBitrateRatio;
        if (previousLayersAccumulatedBitrateRatio > temporal_settings.nBitrateRatios[i]) {
            DEBUG_PRINT_ERROR("TemporalLayer: invalid bitrate ratio for layer %d.. Will fallback to cumulative", i);
            return OMX_ErrorBadParameter;
        } else {
            layerBitrates[i] = (currentLayerBitrateRatio * bitrate.target_bitrate) / 100;
            temporal_layers_config.nTemporalLayerBitrateRatio[i] = temporal_settings.nBitrateRatios[i];
            temporal_layers_config.nTemporalLayerBitrateFraction[i] = currentLayerBitrateRatio;
            DEBUG_PRINT_LOW("TemporalLayer: layer[%u] ratio=%u%% bitrate=%u(of %ld)",
                    i, currentLayerBitrateRatio, layerBitrates[i], bitrate.target_bitrate);
        }
    }

    temporal_layers_config.bIsBitrateRatioValid = OMX_TRUE;

    // Setting layerwise bitrate makes sense only if target bitrate is configured, else defer until later
    if (bitrate.target_bitrate > 0) {
        if (!venc_set_layer_bitrates((OMX_U32 *)layerBitrates, numLayers)) {
            DEBUG_PRINT_ERROR("TemporalLayer: Failed to set layer bitrate");
            return OMX_ErrorUnsupportedSetting;
        }
    } else {
        DEBUG_PRINT_HIGH("TemporalLayer: Defer setting layerwise bitrate since target bitrate is not yet set");
    }

    return OMX_ErrorNone;
}

bool venc_dev::venc_convert_abs2cum_bitrate(QOMX_EXTNINDEX_VIDEO_HYBRID_HP_MODE *pHybrid,
                OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    OMX_U32 cumulativeBitrate   = 0;
    OMX_U32 cumulativeRatio     = 0;
    OMX_U32 i = 0;

    if (pHybrid == NULL) {
        DEBUG_PRINT_ERROR("TemporalLayer: Invalid arg %s", __func__);
        return false;
    }

    DEBUG_PRINT_LOW("TemporalLayer: Converting layered bitrate to cumulative bitrate in percentage");

    for (i = 0; i < temporal_settings.nPLayerCountActual; i++) {
        cumulativeBitrate += pHybrid->nTemporalLayerBitrateRatio[i];
    }

    if (cumulativeBitrate == 0) {
        temporal_settings.bBitrateRatiosSpecified = OMX_FALSE;
        return true;
    }

    DEBUG_PRINT_LOW("TemporalLayer: Cumulative bitrate is: %u", cumulativeBitrate);
    for (i = 0; i < temporal_settings.nPLayerCountActual; i++) {
        temporal_settings.nBitrateRatios[i] = ((pHybrid->nTemporalLayerBitrateRatio[i] * 100)/cumulativeBitrate) + cumulativeRatio;
        cumulativeRatio = temporal_settings.nBitrateRatios[i];
        DEBUG_PRINT_LOW("TemporalLayer: Layer %u bitrate %u percent %u", i,
            pHybrid->nTemporalLayerBitrateRatio[i], temporal_settings.nBitrateRatios[i]);
    }
    temporal_settings.bBitrateRatiosSpecified = OMX_TRUE;

    return true;
}

bool venc_dev::venc_validate_temporal_settings() {

    bool fps_30_plus = false;
    bool fps_60_plus = false;
    bool res_1080p_plus = false;

    fps_30_plus = (((m_sVenc_cfg.fps_num / m_sVenc_cfg.fps_den) > 30) || ((operating_rate) > 30));
    fps_60_plus = (((m_sVenc_cfg.fps_num / m_sVenc_cfg.fps_den) > 60) || ((operating_rate) > 60));
    res_1080p_plus = ((m_sVenc_cfg.input_width * m_sVenc_cfg.input_height / 256) > (1920 * 1088 / 256));

    if (res_1080p_plus == false && fps_60_plus == false) {
        DEBUG_PRINT_HIGH("TemporalLayer: Hier layers cannot be enabled for res <= 1080p & fps <= 60");
        return false;
    }

    if (res_1080p_plus == true && fps_30_plus == false) {
        DEBUG_PRINT_HIGH("TemporalLayer: Hier layers cannot be enabled for res > 1080p & fps <= 30");
        return false;
    }

    if (intra_period.num_bframes > 0) {
        DEBUG_PRINT_HIGH("TemporalLayer: Invalid B-frame settings for Hier layers");
        return false;
    }

    if (rate_ctrl.rcmode != V4L2_CID_MPEG_VIDC_VIDEO_RATE_CONTROL_VBR_CFR) {
        DEBUG_PRINT_HIGH("TemporalLayer: Hier layers cannot be enabled when RC is not VBR_CFR");
        return false;
    }

    return true;
}

bool venc_dev::venc_validate_temporal_extn(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    if (m_sVenc_cfg.codectype != V4L2_PIX_FMT_H264 && m_sVenc_cfg.codectype != V4L2_PIX_FMT_HEVC &&
        temporal_settings.nPLayerCountActual > 1) {
        DEBUG_PRINT_LOW("TemporalLayer: Layer encoding supported for H264 & HEVC");
        return false;
    }

    if (!venc_validate_range(V4L2_CID_MPEG_VIDC_VIDEO_HIER_B_NUM_LAYERS, temporal_settings.nBLayerCountMax - 1) &&
        (temporal_settings.nBLayerCountMax > 0)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Invalid settings, hardware doesn't"
                    " support %u HB layers", temporal_settings.nBLayerCountMax);
        return false;
    }

    if (!venc_validate_range(V4L2_CID_MPEG_VIDC_VIDEO_HIER_P_NUM_LAYERS, temporal_settings.nLayerCountMax - 1) &&
        !venc_validate_range(V4L2_CID_MPEG_VIDC_VIDEO_HYBRID_HIERP_MODE, temporal_settings.nLayerCountMax - 1)) {
        DEBUG_PRINT_ERROR("TemporalLayer: Invalid settings, hardware doesn't"
                    " support %u temporal layers", temporal_settings.nLayerCountMax);
        return false;
    }

    if (temporal_settings.nPLayerCountActual > temporal_settings.nLayerCountMax) {
        DEBUG_PRINT_HIGH("TemporalLayer: Invalid num of max layers");
        return false;
    }

    if (temporal_settings.ePattern != OMX_VIDEO_AndroidTemporalLayeringPatternAndroid) {
        DEBUG_PRINT_LOW("TemporalLayer: The pattern must be Android for Hybrid HP");
        return false;
    }

    if ((streaming[CAPTURE_PORT] == true || streaming[OUTPUT_PORT] == true) && (temporal_layers_config.hier_mode == HIER_P_HYBRID)) {
        DEBUG_PRINT_HIGH("TemporalLayer: Cannot change Hybrid HP settings during streaming");
        return false;
    }

    if ((streaming[CAPTURE_PORT] == true || streaming[OUTPUT_PORT] == true) &&
        ((temporal_settings.nPLayerCountActual > temporal_layers_config.nMaxLayers) ||
         (temporal_settings.nLayerCountMax    != temporal_layers_config.nMaxLayers))) {
        DEBUG_PRINT_HIGH("TemporalLayer: Invalid value for Layer count during streaming");
        return false;
    }

    return true;
}

OMX_ERRORTYPE venc_dev::venc_set_temporal_settings(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporal_settings) {

    if (venc_find_hier_type(temporal_settings) == HIER_P) {

        if (venc_disable_hhp() != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        if (venc_set_hp(temporal_settings) != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        if (venc_set_bitrate_ratio(temporal_settings) != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }


    } else if (venc_find_hier_type(temporal_settings) == HIER_P_HYBRID) {

        if (venc_disable_hp() != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        if (venc_set_hhp(temporal_settings) != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        if (venc_set_bitrate_ratio(temporal_settings) != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }

    } else {

        if (venc_disable_hhp() != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        if (venc_disable_hp() != OMX_ErrorNone) {
            return OMX_ErrorUnsupportedSetting;
        }
        memset(&temporal_layers_config, 0x0, sizeof(temporal_layers_config));
    }

    return OMX_ErrorNone;
}

void venc_dev::venc_copy_temporal_settings(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE &temporalParams) {

    OMX_U32 previousLayersAccumulatedBitrateRatio = 0;
    memset(&temporal_layers_config, 0x0, sizeof(temporal_layers_config));

    temporal_layers_config.nMaxLayers           = temporalParams.nLayerCountMax ;
    temporal_layers_config.nMaxBLayers          = temporalParams.nBLayerCountMax ;
    temporal_layers_config.ePattern             = temporalParams.ePattern;
    temporal_layers_config.nPLayers             = temporalParams.nPLayerCountActual;
    temporal_layers_config.nBLayers             = temporalParams.nBLayerCountActual;
    temporal_layers_config.bIsBitrateRatioValid = temporalParams.bBitrateRatiosSpecified;

    if (temporalParams.bBitrateRatiosSpecified == OMX_TRUE) {
        for (OMX_U32 i = 0; i < temporalParams.nPLayerCountActual + temporalParams.nBLayerCountActual; ++i) {
            previousLayersAccumulatedBitrateRatio = (i == 0) ? 0 : temporalParams.nBitrateRatios[i-1];
            temporal_layers_config.nTemporalLayerBitrateRatio[i] = temporalParams.nBitrateRatios[i];
            temporal_layers_config.nTemporalLayerBitrateFraction[i] =
                    temporalParams.nBitrateRatios[i] - previousLayersAccumulatedBitrateRatio;
        }
    }

    if (temporalParams.nPLayerCountActual <= 1 ||
        temporalParams.ePattern == OMX_VIDEO_AndroidTemporalLayeringPatternNone) {
        client_req_disable_temporal_layers = true;
    } else {
        client_req_disable_temporal_layers = false;
    }
}

bool venc_dev::venc_reconfigure_temporal_settings() {

    OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE temporalParams;
    memset(&temporalParams, 0x0, sizeof(OMX_VIDEO_PARAM_ANDROID_TEMPORALLAYERINGTYPE));

    DEBUG_PRINT_HIGH("TemporalLayer: Reconfigure temporal layer settings");
    // If client hasn't disabled temporal layers and layer count is set to zero
    // enable two temporal layers by default only if it satisfies certain conditions
    if (client_req_disable_temporal_layers == false && temporal_layers_config.nPLayers <= 1) {

        if (!venc_validate_temporal_settings()) {
            DEBUG_PRINT_HIGH("TemporalLayer: Cannot enable temporal layers by default");
            return true;
        }

        temporal_layers_config.nMaxLayers           = 2;
        temporal_layers_config.nMaxBLayers          = 0;
        temporal_layers_config.ePattern             = OMX_VIDEO_AndroidTemporalLayeringPatternAndroid;
        temporal_layers_config.nPLayers             = 2;
        temporal_layers_config.nBLayers             = 0;
        temporal_layers_config.bIsBitrateRatioValid = OMX_FALSE;
        DEBUG_PRINT_HIGH("TemporalLayer: Enabling temporal layers = 2 by default");
    }

    temporalParams.nLayerCountMax           = temporal_layers_config.nMaxLayers;
    temporalParams.nBLayerCountMax          = temporal_layers_config.nMaxBLayers;
    temporalParams.ePattern                 = temporal_layers_config.ePattern;
    temporalParams.nPLayerCountActual       = temporal_layers_config.nPLayers;
    temporalParams.nBLayerCountActual       = temporal_layers_config.nBLayers;
    temporalParams.bBitrateRatiosSpecified  = temporal_layers_config.bIsBitrateRatioValid;

    if (temporal_layers_config.bIsBitrateRatioValid == OMX_TRUE) {
        for (OMX_U32 i = 0; i < temporal_layers_config.nPLayers + temporal_layers_config.nBLayers; ++i) {
            temporalParams.nBitrateRatios[i] =
                    temporal_layers_config.nTemporalLayerBitrateRatio[i];
        }
    }

    if (venc_set_temporal_settings(temporalParams) != OMX_ErrorNone) {
        DEBUG_PRINT_ERROR("TemporalLayer: Failed to set temporal settings");
        return false;
    }

    return true;
}

bool venc_dev::venc_reconfigure_ltrmode() {

    if ((ltrinfo.enabled == true) && (temporal_layers_config.hier_mode == HIER_P_HYBRID) &&
            (temporal_layers_config.nPLayers > 1)) {

        if (!venc_set_ltrmode(0, 0)) {
            DEBUG_PRINT_ERROR("Failed to disable LTR when HybridHP is enabled");
            return false;
        }
    }
    return true;
}

bool venc_dev::venc_get_hevc_profile(OMX_U32* profile)
{
    if (profile == nullptr) return false;

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        if(profile_level_converter::convert_v4l2_profile_to_omx(V4L2_PIX_FMT_HEVC, codec_profile.profile, (int*)profile)) {
            return true;
        } else return false;
    } else return false;
}
bool venc_dev::venc_get_profile_level(OMX_U32 *eProfile,OMX_U32 *eLevel)
{
    bool status = true;

    if (eProfile == NULL || eLevel == NULL) {
        return false;
    }

    if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_H264) {
        switch (codec_profile.profile) {
            case V4L2_MPEG_VIDEO_H264_PROFILE_BASELINE:
                *eProfile = OMX_VIDEO_AVCProfileBaseline;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_CONSTRAINED_BASELINE:
                *eProfile = QOMX_VIDEO_AVCProfileConstrainedBaseline;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_CONSTRAINED_HIGH:
                *eProfile = QOMX_VIDEO_AVCProfileConstrainedHigh;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_MAIN:
                *eProfile = OMX_VIDEO_AVCProfileMain;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_HIGH:
                *eProfile = OMX_VIDEO_AVCProfileHigh;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_EXTENDED:
                *eProfile = OMX_VIDEO_AVCProfileExtended;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_10:
                *eProfile = OMX_VIDEO_AVCProfileHigh10;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_422:
                *eProfile = OMX_VIDEO_AVCProfileHigh422;
                break;
            case V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_444_PREDICTIVE:
                *eProfile = OMX_VIDEO_AVCProfileHigh444;
                break;
            default:
                *eProfile = OMX_VIDEO_AVCProfileMax;
                status = false;
                break;
        }

        if (!status) {
            return status;
        }

        switch (profile_level.level) {
            case V4L2_MPEG_VIDEO_H264_LEVEL_1_0:
                *eLevel = OMX_VIDEO_AVCLevel1;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_1B:
                *eLevel = OMX_VIDEO_AVCLevel1b;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_1_1:
                *eLevel = OMX_VIDEO_AVCLevel11;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_1_2:
                *eLevel = OMX_VIDEO_AVCLevel12;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_1_3:
                *eLevel = OMX_VIDEO_AVCLevel13;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_2_0:
                *eLevel = OMX_VIDEO_AVCLevel2;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_2_1:
                *eLevel = OMX_VIDEO_AVCLevel21;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_2_2:
                *eLevel = OMX_VIDEO_AVCLevel22;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_3_0:
                *eLevel = OMX_VIDEO_AVCLevel3;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_3_1:
                *eLevel = OMX_VIDEO_AVCLevel31;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_3_2:
                *eLevel = OMX_VIDEO_AVCLevel32;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_4_0:
                *eLevel = OMX_VIDEO_AVCLevel4;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_4_1:
                *eLevel = OMX_VIDEO_AVCLevel41;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_4_2:
                *eLevel = OMX_VIDEO_AVCLevel42;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_5_0:
                *eLevel = OMX_VIDEO_AVCLevel5;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_5_1:
                *eLevel = OMX_VIDEO_AVCLevel51;
                break;
            case V4L2_MPEG_VIDEO_H264_LEVEL_5_2:
                *eLevel = OMX_VIDEO_AVCLevel52;
                break;
            default :
                *eLevel = OMX_VIDEO_AVCLevelMax;
                status = false;
                break;
        }
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_VP8) {
        switch (codec_profile.profile) {
            case V4L2_MPEG_VIDC_VIDEO_VP8_UNUSED:
                *eProfile = OMX_VIDEO_VP8ProfileMain;
                break;
            default:
                *eProfile = OMX_VIDEO_VP8ProfileMax;
                status = false;
                break;
        }
        if (!status) {
            return status;
        }

        switch (profile_level.level) {
            case V4L2_MPEG_VIDC_VIDEO_VP8_VERSION_0:
                *eLevel = OMX_VIDEO_VP8Level_Version0;
                break;
            case V4L2_MPEG_VIDC_VIDEO_VP8_VERSION_1:
                *eLevel = OMX_VIDEO_VP8Level_Version1;
                break;
            default:
                *eLevel = OMX_VIDEO_VP8LevelMax;
                status = false;
                break;
        }
    } else if (m_sVenc_cfg.codectype == V4L2_PIX_FMT_HEVC) {
        switch (codec_profile.profile) {
            case V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN:
                *eProfile = OMX_VIDEO_HEVCProfileMain;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN10:
                *eProfile = OMX_VIDEO_HEVCProfileMain10HDR10;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_PROFILE_MAIN_STILL_PIC:
                *eProfile = OMX_VIDEO_HEVCProfileMainStill;
                break;
            default:
                *eProfile = OMX_VIDEO_HEVCProfileMax;
                status = false;
                break;
        }
        if (!status) {
            return status;
        }

        switch (profile_level.level) {
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel1;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel1;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_2:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel2;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_2:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel2;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_2_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel21;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_2_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel21;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_3:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel3;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_3:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel3;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_3_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel31;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_3_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel31;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_4:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel4;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_4:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel4;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_4_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel41;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_4_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel41;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_5:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel5;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_5:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel5;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_5_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel51;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_5_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel51;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_5_2:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel52;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_5_2:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel52;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_6:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel6;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_6:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel6;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_6_1:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel61;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_HIGH_TIER_LEVEL_6_1:
                *eLevel = OMX_VIDEO_HEVCHighTierLevel61;
                break;
            case V4L2_MPEG_VIDC_VIDEO_HEVC_LEVEL_MAIN_TIER_LEVEL_6_2:
                *eLevel = OMX_VIDEO_HEVCMainTierLevel62;
                break;
            default:
                *eLevel = OMX_VIDEO_HEVCLevelMax;
                status = false;
                break;
        }
    }

    return status;
}

#ifdef _ANDROID_ICS_
bool venc_dev::venc_set_meta_mode(bool mode)
{
    metadatamode = mode;
    return true;
}
#endif

bool venc_dev::venc_is_video_session_supported(unsigned long width,
        unsigned long height)
{
    if ((width * height < capability.min_width *  capability.min_height) ||
            (width * height > capability.max_width *  capability.max_height)) {
        DEBUG_PRINT_ERROR(
                "Unsupported video resolution WxH = (%lu)x(%lu) supported range = min (%d)x(%d) - max (%d)x(%d)",
                width, height, capability.min_width, capability.min_height,
                capability.max_width, capability.max_height);
        return false;
    }

    DEBUG_PRINT_LOW("video session supported");
    return true;
}

bool venc_dev::venc_set_batch_size(OMX_U32 batchSize)
{
    struct v4l2_control control;
    int ret;

    control.id = V4L2_CID_VIDC_QBUF_MODE;
    control.value = batchSize ? V4L2_VIDC_QBUF_BATCHED : V4L2_VIDC_QBUF_STANDARD;

    ret = ioctl(m_nDriver_fd, VIDIOC_S_CTRL, &control);
    if (ret) {
        DEBUG_PRINT_ERROR("Failed to set batching mode: %d", ret);
        return false;
    }

    mBatchSize = batchSize;
    DEBUG_PRINT_HIGH("Using batch size of %d", mBatchSize);
    return true;
}

venc_dev::BatchInfo::BatchInfo()
    : mNumPending(0) {
    pthread_mutex_init(&mLock, NULL);
    for (int i = 0; i < kMaxBufs; ++i) {
        mBufMap[i] = kBufIDFree;
    }
}

int venc_dev::BatchInfo::registerBuffer(int bufferId) {
    pthread_mutex_lock(&mLock);
    int availId = 0;
    for( ; availId < kMaxBufs && mBufMap[availId] != kBufIDFree; ++availId);
    if (availId >= kMaxBufs) {
        DEBUG_PRINT_ERROR("Failed to find free entry !");
        pthread_mutex_unlock(&mLock);
        return -1;
    }
    mBufMap[availId] = bufferId;
    mNumPending++;
    pthread_mutex_unlock(&mLock);
    return availId;
}

int venc_dev::BatchInfo::retrieveBufferAt(int v4l2Id) {
    pthread_mutex_lock(&mLock);
    if (v4l2Id >= kMaxBufs || v4l2Id < 0) {
        DEBUG_PRINT_ERROR("Batch: invalid index %d", v4l2Id);
        pthread_mutex_unlock(&mLock);
        return -1;
    }
    if (mBufMap[v4l2Id] == kBufIDFree) {
        DEBUG_PRINT_ERROR("Batch: buffer @ %d was not registered !", v4l2Id);
        pthread_mutex_unlock(&mLock);
        return -1;
    }
    int bufferId = mBufMap[v4l2Id];
    mBufMap[v4l2Id] = kBufIDFree;
    mNumPending--;
    pthread_mutex_unlock(&mLock);
    return bufferId;
}

bool venc_dev::BatchInfo::isPending(int bufferId) {
    pthread_mutex_lock(&mLock);
    int existsId = 0;
    for(; existsId < kMaxBufs && mBufMap[existsId] != bufferId; ++existsId);
    pthread_mutex_unlock(&mLock);
    return existsId < kMaxBufs;
}

bool venc_dev::venc_set_hdr_info(const MasteringDisplay& mastering_disp_info,
                            const ContentLightLevel& content_light_level_info)
{
    struct v4l2_ext_control ctrl[13];
    struct v4l2_ext_controls controls;
    const unsigned int RGB_PRIMARY_TABLE[] = {
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_00,
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_01,
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_10,
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_11,
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_20,
        V4L2_CID_MPEG_VIDC_VENC_RGB_PRIMARY_21,
    };

    memset(&controls, 0, sizeof(controls));
    memset(ctrl, 0, sizeof(ctrl));

    controls.count = 13;
    controls.ctrl_class = V4L2_CTRL_CLASS_MPEG;
    controls.controls = ctrl;

    ctrl[0].id = V4L2_CID_MPEG_VIDC_VENC_HDR_INFO;
    ctrl[0].value = V4L2_MPEG_VIDC_VENC_HDR_INFO_ENABLED;

    /* ctrl[1] - ctrl[6] */
    for (int i = 0; i < 3; i++) {
        int first_idx = 2*i+1;
        int second_idx = 2*i+2;
        ctrl[first_idx].id = RGB_PRIMARY_TABLE[first_idx-1];
        ctrl[first_idx].value = mastering_disp_info.primaries.rgbPrimaries[i][0];

        ctrl[second_idx].id = RGB_PRIMARY_TABLE[second_idx-1];
        ctrl[second_idx].value = mastering_disp_info.primaries.rgbPrimaries[i][1];
    }

    ctrl[7].id = V4L2_CID_MPEG_VIDC_VENC_WHITEPOINT_X;
    ctrl[7].value = mastering_disp_info.primaries.whitePoint[0];

    ctrl[8].id = V4L2_CID_MPEG_VIDC_VENC_WHITEPOINT_Y;
    ctrl[8].value = mastering_disp_info.primaries.whitePoint[1];

    ctrl[9].id = V4L2_CID_MPEG_VIDC_VENC_MAX_DISP_LUM;
    ctrl[9].value = mastering_disp_info.maxDisplayLuminance;

    ctrl[10].id = V4L2_CID_MPEG_VIDC_VENC_MIN_DISP_LUM;
    ctrl[10].value = mastering_disp_info.minDisplayLuminance;

    ctrl[11].id = V4L2_CID_MPEG_VIDC_VENC_MAX_CLL;
    ctrl[11].value = content_light_level_info.maxContentLightLevel;

    ctrl[12].id = V4L2_CID_MPEG_VIDC_VENC_MAX_FLL;
    ctrl[12].value = content_light_level_info.minPicAverageLightLevel;

    if (ioctl(m_nDriver_fd, VIDIOC_S_EXT_CTRLS, &controls)) {
        DEBUG_PRINT_ERROR("VIDIOC_S_EXT_CTRLS failed for HDR Info");
        return false;
    }

    return true;
}

#ifdef _VQZIP_
venc_dev::venc_dev_vqzip::venc_dev_vqzip()
{
    mLibHandle = NULL;
    pthread_mutex_init(&lock, NULL);
}

bool venc_dev::venc_dev_vqzip::init()
{
    bool status = true;
    if (mLibHandle) {
        DEBUG_PRINT_ERROR("VQZIP init called twice");
        status = false;
    }
    if (status) {
        mLibHandle = dlopen("libvqzip.so", RTLD_NOW);
        if (mLibHandle) {
            mVQZIPInit = (vqzip_init_t)
                dlsym(mLibHandle,"VQZipInit");
            mVQZIPDeInit = (vqzip_deinit_t)
                dlsym(mLibHandle,"VQZipDeInit");
            mVQZIPComputeStats = (vqzip_compute_stats_t)
                dlsym(mLibHandle,"VQZipComputeStats");
            if (!mVQZIPInit || !mVQZIPDeInit || !mVQZIPComputeStats)
                status = false;
        } else {
            DEBUG_PRINT_ERROR("FATAL ERROR: could not dlopen libvqzip.so: %s", dlerror());
            status = false;
        }
        if (status) {
            mVQZIPHandle = mVQZIPInit();
        }
    }
    if (!status && mLibHandle) {
        dlclose(mLibHandle);
        mLibHandle = NULL;
        mVQZIPHandle = NULL;
        mVQZIPInit = NULL;
        mVQZIPDeInit = NULL;
        mVQZIPComputeStats = NULL;
    }
    return status;
}

int venc_dev::venc_dev_vqzip::fill_stats_data(void* pBuf, void* extraData)
{
    VQZipStatus result;
    VQZipStats *pStats = (VQZipStats *)extraData;
    pConfig.pSEIPayload = NULL;
    unsigned long size;

    if (!pBuf || !pStats || !mVQZIPHandle) {
        DEBUG_PRINT_ERROR("Invalid data passed to stats function");
    }
    result = mVQZIPComputeStats(mVQZIPHandle, (void* )pBuf, &pConfig, pStats);
    return result;
}

void venc_dev::venc_dev_vqzip::deinit()
{
    if (mLibHandle) {
        pthread_mutex_lock(&lock);
        dlclose(mLibHandle);
        mVQZIPDeInit(mVQZIPHandle);
        mLibHandle = NULL;
        mVQZIPHandle = NULL;
        mVQZIPInit = NULL;
        mVQZIPDeInit = NULL;
        mVQZIPComputeStats = NULL;
        pthread_mutex_unlock(&lock);
    }
}

venc_dev::venc_dev_vqzip::~venc_dev_vqzip()
{
    DEBUG_PRINT_HIGH("Destroy C2D instance");
    if (mLibHandle) {
        dlclose(mLibHandle);
    }
    mLibHandle = NULL;
    pthread_mutex_destroy(&lock);
}
#endif
