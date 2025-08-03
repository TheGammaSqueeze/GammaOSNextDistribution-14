#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <string.h>
#include "iaxxx_odsp_hw.h"


static struct option const long_options[] =
{
 {"getpluginstatusinfo", no_argument, NULL, 's'},
 {"getpluginendpointstatus", no_argument, NULL, 'e'},
 {"plugininstanceid", required_argument, NULL, 'i'},
 {"endpointindex", required_argument, NULL, 'n'},
 {"endpointdirection", required_argument, NULL, 'd'},
 {"blockid", required_argument, NULL, 'b'},
 {"help", no_argument, NULL, 'h'},
 {NULL, 0, NULL, 0}
};


void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) getpluginstatus_test -s -i <plugin-instance-id> \n\
    get status info from plugin.\n\
    2) getpluginstatus_test -e -i <plugin-instance-id> -n <endpoint-index>"\
    " -d <direction>\n\
    get endpoint status info from plugin.\n\
    3) getpluginstatus_test -r -b <processor-block-id>\n\
    read if any error occurred in any of the plugins\n\n", stdout);

    exit(0);
}


int main(int argc, char **argv)
{
    struct iaxxx_odsp_hw* odsp_hw= NULL;
    int err = 0;

    int plugin_instance_id = -1;
    int test_option =  -1;
    int enpoint_index = -1;
    int enpoint_dir = -1;
    int block_id = -1;
    uint32_t error_code;
    uint8_t  error_instance;

    /* getopt_long stores the option index here. */
    int option_index = 0;
    int ch;

    struct iaxxx_plugin_status_data plugin_status_data;
    struct iaxxx_plugin_endpoint_status_data plugin_ep_status_data;

    if (argc <= 1) {
        usage();
    }

    while(1) {
        ch = getopt_long (argc, argv, "sei:n:d:rb:",
                         long_options, &option_index);

        /* Detect the end of the options. */
        if (ch == -1)
          break;

        switch (ch) {

            case 's':
                test_option = 1;
                break;

            case 'e':
                test_option = 2;
                break;

            case 'r':
                test_option = 3;
                break;

            case 'i':
                plugin_instance_id = atoi(optarg);
                break;

            case 'n':
                enpoint_index = atoi(optarg);
                break;

            case 'd':
                enpoint_dir = atoi(optarg);
                break;

            case 'b':
                block_id = atoi(optarg);
                break;

            case 'h':
            default:
                usage();
        }
    }

    if(test_option == -1) {
        fprintf(stderr,"\n No Test Option Parameter! \n");
        usage();
    }

    if((odsp_hw = iaxxx_odsp_init()) == NULL) {
        fprintf(stderr,"\n ODSP Init Failed! \n");
        return 0;
    }

    if(test_option == 1) {
        if(plugin_instance_id == -1) {
            fprintf(stderr,"\n No Plugin_instance_id Parameter! \n");
            usage();
        }

        if((odsp_hw = iaxxx_odsp_init()) == NULL) {
            fprintf(stderr,"\n ODSP Init Failed! \n");
            return 0;
        }
        err = iaxxx_odsp_plugin_get_status_info(odsp_hw,
                            plugin_instance_id, &plugin_status_data);

        iaxxx_odsp_deinit(odsp_hw);
        if(!err)
            fprintf(stdout, "\n Plugin status:: "
                "\n block_id:%u create_status:%u enable_status:%u"
                "\n process_count:%u process_err_count:%u private_memsize:%u"
                "\n in_frames_consumed:%u out_frames_produced:%u "
                "\n frame_notification_mode:%u state_management_mode:%u ",
                plugin_status_data.block_id,
                plugin_status_data.create_status,
                plugin_status_data.enable_status,
                plugin_status_data.process_count,
                plugin_status_data.process_err_count,
                plugin_status_data.private_memsize,
                plugin_status_data.in_frames_consumed,
                plugin_status_data.out_frames_produced,
                plugin_status_data.frame_notification_mode,
                plugin_status_data.state_management_mode);

    }
    else
    if(test_option == 2) {
        if(plugin_instance_id == -1) {
            fprintf(stderr,"\n No Plugin_instance_id Parameter! \n");
            usage();
        }

        if(enpoint_index == -1) {
            fprintf(stderr,"\n No Endpoint Index Parameter! \n");
            usage();
        }
        if(enpoint_dir == -1) {
            fprintf(stderr,"\n No Endpoint Direction Parameter! \n");
            usage();
        }

        if((odsp_hw = iaxxx_odsp_init()) == NULL) {
            fprintf(stderr,"\n ODSP Init Failed! \n");
            return 0;
        }
        err = iaxxx_odsp_plugin_get_endpoint_status(odsp_hw,
                            plugin_instance_id, enpoint_index,
                            enpoint_dir, &plugin_ep_status_data);
        iaxxx_odsp_deinit(odsp_hw);

        if(!err) {
            fprintf(stdout, "\nstatus:%u frame_status:%d endpoint_status:%d",
                        plugin_ep_status_data.status,
                        plugin_ep_status_data.frame_status,
                        plugin_ep_status_data.endpoint_status);

            fprintf(stdout, "\nusage:%d mandatory:%d counter:%u",
                        plugin_ep_status_data.usage,
                        plugin_ep_status_data.mandatory,
                        plugin_ep_status_data.counter);

            if(enpoint_dir)
                fprintf(stdout,
                    "\nop_encoding:%d op_sample_rate:%d op_frame_length:%u",
                    plugin_ep_status_data.op_encoding,
                    plugin_ep_status_data.op_sample_rate,
                    plugin_ep_status_data.op_frame_length);
        }
    }
    else {
        if(block_id == -1) {
            fprintf(stderr,"\n No block_id Parameter! \n");
            usage();
        }
        if((odsp_hw = iaxxx_odsp_init()) == NULL) {
            fprintf(stderr,"\n ODSP Init Failed! \n");
            return 0;
        }

        err = iaxxx_odsp_plugin_read_error(odsp_hw,
                block_id, &error_code, &error_instance);

        iaxxx_odsp_deinit(odsp_hw);

        if(!err) {
            fprintf(stdout, "\nerror_code:%u", error_code);
            if(error_code)
        	    fprintf(stdout, "\ninstance_error_occured:%d",
                   error_instance);
        }
    }

    if(err)
        fprintf(stdout, "\n\n## plugin_status_test failed!! ##\n\n");
    else
        fprintf(stdout, "\n\n## plugin_status_test done. ##\n\n");

    return 0;
}

