#define LOG_TAG "oslo_packages_test"

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <string.h>
#include <math.h>

#include <cutils/log.h>

#include <linux/mfd/adnc/iaxxx-odsp.h>
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>
#include <tinyalsa/asoundlib.h>

#define ODSP_NODE "/dev/iaxxx-odsp-celldrv"

#define BUFFER_PACKAGE      "BufferPackage.bin"
#define BUFFER_CONFIG_OSLO_VAL      "BufferConfigValOslo.bin"
#define SENSOR_PACKAGE   "OsloSensorPackage.bin"

#define PLUGIN3_SRC_ID 0x30FF

static struct mixer* open_mixer_ctl()
{
    return mixer_open(0);
}

static void close_mixer_ctl(struct mixer *mixer)
{
    if (mixer) {
        mixer_close(mixer);
    }
}

static int set_mixer_ctl_val(struct mixer *mixer, char *id, int value)
{
    struct mixer_ctl *ctl = NULL;
    int err = 0;

    if ((NULL == mixer) || (NULL == id)) {
        ALOGE("%s: ERROR Null argument passed", __func__);
        err = -EINVAL;
        goto exit;
    }

    ctl = mixer_get_ctl_by_name(mixer, id);
    if (NULL == ctl) {
        ALOGE("%s: ERROR Invalid control name: %s", __func__, id);
        err = -1;
        goto exit;
    }

    if (mixer_ctl_set_value(ctl, 0, value)) {
        ALOGE("%s: ERROR Invalid value for %s", __func__, id);
        err = -1;
        goto exit;
    }

exit:
    return err;
}

static int set_mixer_ctl_string(struct mixer *mixer, char *id, const char *string)
{
    struct mixer_ctl *ctl = NULL;
    int err = 0;

    if ((NULL == mixer) || (NULL == id)) {
        ALOGE("%s: ERROR Null argument passed", __func__);
        err = -EINVAL;
        goto exit;
    }

    ctl = mixer_get_ctl_by_name(mixer, id);
    if (NULL == ctl) {
        ALOGE("%s: ERROR Invalid control name: %s", __func__, id);
        err = -1;
        goto exit;
    }

    if (mixer_ctl_set_enum_by_string(ctl, string)) {
        ALOGE("%s: ERROR Invalid string for %s", __func__, id);
        err = -1;
        goto exit;
    }

exit:
    return err;
}

void usage()
{
    fputs("\
    USAGE -\n\
    -------\n\
    1) Oslo_packages_test 1\n\
    2) Oslo_packages_test 0\n\
    \n\
    1 means load all packages\n\
    0 means unload.\n\
    ", stdout);

    exit(EXIT_FAILURE);
}

int main(int argc, char *argv[])
{
    FILE *odsp_node = NULL;
    struct iaxxx_pkg_mgmt_info pkg_info;
    struct iaxxx_plugin_info pi;
    struct iaxxx_plugin_create_cfg pcc;
    struct iaxxx_set_event se;
    struct iaxxx_evt_info ei;

    uint32_t buffer_id;
    uint32_t sensor_pkg_id;
    int param;
    int err = 0;

    struct mixer *mixer = NULL;

    if (argc <= 1 || argc > 2) {
        usage();
    }

    param = strtol(argv[1], NULL, 0);
    if (param > 1 || param < 0) {
        usage();
    }

    /* Open Host Driver File node */
    if((odsp_node = fopen(ODSP_NODE, "rw")) == NULL) {
        printf("file %s open for write error: %s\n", ODSP_NODE,
                strerror(errno));
        ALOGE("file %s open for write error: %s\n", ODSP_NODE,
                strerror(errno));
        err = -EIO;
        goto exit;
    }

    mixer = open_mixer_ctl();
    if (NULL == mixer) {
        ALOGE("%s: ERROR: Failed to open the mixer control", __func__);
        err = -EINVAL;
        goto exit;
    }

    if (param == 1) {
        /* Load all pacakge */

        /* Sensor Plugin */
        strcpy(pkg_info.pkg_name, SENSOR_PACKAGE);
        pkg_info.pkg_id = 0;
        pkg_info.proc_id = 0;
        err = ioctl(fileno(odsp_node), ODSP_LOAD_PACKAGE, (unsigned long)&pkg_info);
        if (err) {
            printf("%s: ERROR: SENSOR ODSP_LOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: SENSOR ODSP_LOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }
        printf("%s: SENSOR ODSP_LOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        ALOGD("%s: SENSOR ODSP_LOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        sensor_pkg_id = pkg_info.proc_id;

        /* Buffer Plugin */
        strcpy(pkg_info.pkg_name, BUFFER_PACKAGE);
        pkg_info.pkg_id = 4;
        pkg_info.proc_id = 0;
        err = ioctl(fileno(odsp_node), ODSP_LOAD_PACKAGE, (unsigned long)&pkg_info);
        if (err) {
            printf("%s: ERROR: Buffer ODSP_LOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: Buffer ODSP_LOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }
        printf("%s: Buffer ODSP_LOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        ALOGD("%s: Buffer ODSP_LOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        buffer_id = pkg_info.proc_id;

        /* if buffer plugin Loaded */
        strcpy(pcc.file_name, BUFFER_CONFIG_OSLO_VAL);
        pcc.inst_id = 2;
        pcc.block_id = 1;
        pcc.cfg_size = 12;
        pcc.cfg_val = 0;

        err = ioctl(fileno(odsp_node), ODSP_PLG_SET_CREATE_CFG, (unsigned long)&pcc);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_SET_CREATE_CFG IOCTL failed to set create config %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_SET_CREATE_CFG IOCTL failed to set create config %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /* Create Buffer plugin */
        pi.plg_idx  = 0;
        pi.pkg_id = buffer_id;
        pi.block_id = 1;
        pi.inst_id  = 2;
        pi.priority = 1;
        err = ioctl(fileno(odsp_node), ODSP_PLG_CREATE, (unsigned long)&pi);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_CREATE IOCTL failed to create Buffer Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_CREATE IOCTL failed to create Buffer Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /* Create placeholder sensor plugin */
        pi.plg_idx  = 0;
        pi.pkg_id = sensor_pkg_id;
        pi.block_id = 1;
        pi.inst_id  = 3;
        pi.priority = 1;
        err = ioctl(fileno(odsp_node), ODSP_PLG_CREATE, (unsigned long)&pi);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_CREATE IOCTL failed to create sensor Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_CREATE IOCTL failed to create sensor Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /* Set the events and params */
        se.inst_id           = 3;
        se.event_enable_mask = 0x7;
        se.block_id          = 1;
        err = ioctl(fileno(odsp_node), ODSP_PLG_SET_EVENT, (unsigned long)&se);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_SET_EVENT IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_SET_EVENT IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        printf("Registering for 3 sensor mode switch events\n");
        ALOGD("Registering for 3 sensor mode switch events\n");

        /* Subscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 0;    // 0 - Mode switch to presence mode
        ei.dst_id       = IAXXX_SYSID_SCRIPT_MGR;
        ei.dst_opaque   = 0x1201;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_SUBSCRIBE, (unsigned long)&ei);
        if (err) {
            printf("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* Subscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 1;    // 1 - Mode switch to detected mode.
        ei.dst_id       = IAXXX_SYSID_SCRIPT_MGR;
        ei.dst_opaque   = 0x1202;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_SUBSCRIBE, (unsigned long)&ei);
        if (err) {
            printf("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* Subscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 2;    // 2 - Mode switch to max mode
        ei.dst_id       = IAXXX_SYSID_HOST;  // update this to HOST_1 for Customer
        ei.dst_opaque   = 0;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_SUBSCRIBE, (unsigned long)&ei);
        if (err) {
            printf("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* setup routing */
        set_mixer_ctl_val(mixer, "sensor0 En", 1);

        set_mixer_ctl_string(mixer, "Plgin2Ip Ep0 Conf", "SensorOut0");
        set_mixer_ctl_string(mixer, "Plgin3Ip Ep0 Conf", "plugin2Out0");

        set_mixer_ctl_val(mixer, "Plgin2Blk1En", 1);
        set_mixer_ctl_val(mixer, "Plgin3Blk1En", 1);
    } else {
        /* disconnect the route */
        set_mixer_ctl_val(mixer, "Plgin3Blk1En", 0);
        set_mixer_ctl_val(mixer, "Plgin2Blk1En", 0);
        set_mixer_ctl_val(mixer, "sensor0 En", 0);

        /* Unload all pacakge */
        printf("UnSubscribe 3 sensor mode switch events\n");
        ALOGD("UnSubscribe 3 sensor mode switch events\n");

        /* UnSubscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 2;    // 2 - Mode switch to max mode
        ei.dst_id       = IAXXX_SYSID_HOST;  // update this to HOST_1 for Customer
        ei.dst_opaque   = 0;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_UNSUBSCRIBE, (unsigned long)&ei);
        if (err) {
            printf("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* UnSubscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 1;    // 1 - Mode switch to detected mode.
        ei.dst_id       = IAXXX_SYSID_SCRIPT_MGR;
        ei.dst_opaque   = 0x1202;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_UNSUBSCRIBE, (unsigned long)&ei);
        if (err) {
            printf("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* UnSubscribe for events */
        ei.src_id       = PLUGIN3_SRC_ID;
        ei.event_id     = 0;    // 0 - Mode switch to presence mode
        ei.dst_id       = IAXXX_SYSID_SCRIPT_MGR;
        ei.dst_opaque   = 0x1201;
        err = ioctl(fileno(odsp_node), ODSP_EVENT_UNSUBSCRIBE, (unsigned long)&ei);
        if (-1 == err) {
            printf("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d) IOCTL failed with error %d(%s)\n",
                        __func__, ei.event_id, ei.src_id, errno, strerror(errno));
            goto exit;
        }

        /* Set the events and params */
        se.inst_id           = 3;
        se.event_enable_mask = 0x0;
        se.block_id          = 1;
        err = ioctl(fileno(odsp_node), ODSP_PLG_SET_EVENT, (unsigned long)&se);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_SET_EVENT IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_SET_EVENT IOCTL failed with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /* destroy placeholder sensor plugin */
        pi.block_id = 1;
        pi.inst_id  = 3;
        err = ioctl(fileno(odsp_node), ODSP_PLG_DESTROY, (unsigned long)&pi);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_DESTROY IOCTL failed to create sensor Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_DESTROY IOCTL failed to create sensor Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /* destroy Buffer plugin */
        pi.block_id = 1;
        pi.inst_id  = 2;
        err = ioctl(fileno(odsp_node), ODSP_PLG_DESTROY, (unsigned long)&pi);
        if (err) {
            printf("%s: ERROR: ODSP_PLG_DESTROY IOCTL failed to create Buffer Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: ODSP_PLG_DESTROY IOCTL failed to create Buffer Plugin with error %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        /*Unload Buffer Plugin */

        pkg_info.pkg_id = 4;
        pkg_info.proc_id = 4;
        err = ioctl(fileno(odsp_node), ODSP_UNLOAD_PACKAGE, (unsigned long)&pkg_info);
        if (err) {
            printf("%s: ERROR: SENSOR ODSP_UNLOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: SENSOR ODSP_UNLOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        printf("%s: SENSOR ODSP_UNLOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        ALOGD("%s: SENSOR ODSP_UNLOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);

        /*Unload Sensor Plugin */
        pkg_info.pkg_id = 0;
        pkg_info.proc_id = 4;
        err = ioctl(fileno(odsp_node), ODSP_UNLOAD_PACKAGE, (unsigned long)&pkg_info);
        if (err) {
            printf("%s: ERROR: Buffer ODSP_UNLOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            ALOGE("%s: ERROR: Buffer ODSP_UNLOAD_PACKAGE failed %d(%s)\n", __func__, errno, strerror(errno));
            goto exit;
        }

        printf("%s: Buffer ODSP_UNLOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
        ALOGD("%s: Buffer ODSP_UNLOAD_PACKAGE %x\n", __func__, pkg_info.proc_id);
    }
exit:
    if (odsp_node)
        fclose(odsp_node);
    if (mixer)
        close_mixer_ctl(mixer);
    return err;
}
