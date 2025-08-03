/*
 * Copyright (C) 2018 Knowles Electronics
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

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <string.h>
#include "iaxxx_odsp_hw.h"


#define MAX_FILE_PATH_SIZE (512)

enum {
    SETPARAMBLK_TEST_OPTION_FROMFILE,
    SETPARAMBLK_TEST_OPTION_CUSTOM,
    SETPARAMBLK_TEST_OPTION_WITHACK
};


static struct option const long_options[] =
{
    {"setparamblkfromfile", no_argument, NULL, 's'},
    {"customsetparamblk", no_argument, NULL, 'c'},
    {"setparamblkwithack", no_argument, NULL, 'k'},
    {"filename", required_argument, NULL, 'f'},
    {"plugininstanceid", required_argument, NULL, 'i'},
    {"paramblkid", required_argument, NULL, 'p'},
    {"procblockid", required_argument, NULL, 'b'},
    {"help", no_argument, NULL, 'h'},
    {NULL, 0, NULL, 0}
};


static int setparamblk_with_ack_test(struct iaxxx_odsp_hw *odsp_hw,
                                    const uint32_t inst_id,
                                    const uint32_t block_id,
                                    const uint32_t  param_blk_id,
                                    const uint32_t max_retries,
                                    uint32_t* response_data,
                                    const uint32_t response_data_size_in_words,
                                    const char *file)
{
    int ret = -1;
    uint32_t *data = NULL;
    FILE *fp = NULL;
    long file_len, bytes_read;

    fp = fopen(file, "rb");
    if (NULL == fp) {
        fprintf(stderr,"Failed to find the file at the location %s", file);
        goto setparamblk_with_ack_test;
    }

    fseek(fp, 0, SEEK_END);
    file_len = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    data = malloc(file_len);
    if (!data) {
        fprintf(stderr,"Error allocating memory");
        goto setparamblk_with_ack_test;
    }


    bytes_read = fread(data, 1, file_len, fp);
    if (bytes_read != file_len) {
        fprintf(stderr,"Failed to read the complete file");
        fclose(fp);
        goto setparamblk_with_ack_test;
    }
    fclose(fp);

    ret = iaxxx_odsp_plugin_set_parameter_blk_with_ack(odsp_hw, inst_id,
                                                    param_blk_id, block_id, data,
                                                    file_len * sizeof(uint32_t),
                                                    response_data,
                                                    response_data_size_in_words,
                                                    max_retries);

setparamblk_with_ack_test:
    if (data)
        free(data);
    return ret;
}

void usage() {
    fprintf(stdout, "\
    USAGE -\n\
    -------\n\
    1) setparamblk_test -s -f <file_name> -i <plugin-instance-id> -p <param-blk-id> -b <proc-block-id> \n\
    2) setparamblk_test -k -f <file_name> -i <plugin-instance-id> -p <param-blk-id> -b <proc-block-id> -m <max-retries> -r <response-size-in-words> \n\
    3) setparamblk_test -c -f <file_name> -i <plugin-instance-id> -p <param-blk-id> -b <proc-block-id> \n\
    \n\
    In the first form, test setparamblk from a file.\n\
    In the second form, test setparamblk from a file with retry and acknowledgement\n\
    In the second form, test setparamblk from a file for custom configuration of plugin.\n\n");

    exit(0);
}


int main(int argc, char **argv)
{
    struct iaxxx_odsp_hw* odsp_hw = NULL;
    int err = 0;

    int plugin_instance_id = -1;
    int proc_block_id = -1;
    int param_blk_id = -1;
    int max_retries = -1;
    int response_data_size_in_words = -1;
    uint32_t *response_data = NULL;
    char file_path[MAX_FILE_PATH_SIZE];
    int setparamblk_test_option = -1;
    int index = 0;

    /* getopt_long stores the option index here. */
    int option_index = 0;
    int ch;

    file_path[0] = '\0';

    if (argc <= 1) {
        usage();
    }

    while (1) {
        ch = getopt_long(argc, argv, "sckhf:p:i:b:m:r:",
                        long_options, &option_index);

        /* Detect the end of the options. */
        if (ch == -1)
            break;

        switch (ch) {
            case 's':
                setparamblk_test_option = SETPARAMBLK_TEST_OPTION_FROMFILE;
                break;

            case 'c':
                setparamblk_test_option = SETPARAMBLK_TEST_OPTION_CUSTOM;
                break;

            case 'k':
                setparamblk_test_option = SETPARAMBLK_TEST_OPTION_WITHACK;
                break;

            case 'f':
                strcpy(file_path, optarg);
                break;

            case 'p':
                param_blk_id = atoi(optarg);
                break;

            case 'i':
                plugin_instance_id = atoi(optarg);
                break;

            case 'b':
                proc_block_id = atoi(optarg);
                break;

            case 'm':
                max_retries = atoi(optarg);
                break;

            case 'r':
                response_data_size_in_words = atoi(optarg);
                break;

            case 'h':
            default:
                usage();
        }
    }

    if (setparamblk_test_option == -1) {
        fprintf(stderr,"\n No Test Option Parameter! \n");
        usage();
    }

    if (file_path[0] == '\0') {
        fprintf(stderr,"\n No File Name Parameter! \n");
        usage();
    }

    if (plugin_instance_id == -1) {
        fprintf(stderr,"\n No Plugin_instance_id Parameter! \n");
        usage();
    }

    if (param_blk_id == -1) {
        fprintf(stderr,"\n No Param_blk_id Parameter! \n");
        usage();
    }

    if (proc_block_id == -1) {
        fprintf(stderr,"\n No proc_block_id Parameter! \n");
        usage();
    }
    if (setparamblk_test_option == SETPARAMBLK_TEST_OPTION_WITHACK) {

        if (max_retries == -1) {
            fprintf(stderr,"\n No max retries Parameter! \n");
            usage();
        }

        if (response_data_size_in_words <= 0) {
            fprintf(stderr,"\n No or Invalid Response data size Parameter! \n");
            usage();
        }

        response_data = (uint32_t*) malloc(sizeof(uint32_t) * response_data_size_in_words);

        if (!response_data) {
            fprintf(stderr,"\n malloc failed! \n", stdout);
            exit(0);
        }

        memset(response_data, 0, sizeof(uint32_t) * response_data_size_in_words);
    }

    if ((odsp_hw = iaxxx_odsp_init()) == NULL) {
        fprintf(stderr,"\n ODSP Init Failed! \n");
        return 0;
    }

    if (setparamblk_test_option == SETPARAMBLK_TEST_OPTION_FROMFILE) {
        fprintf(stdout, "\n## Setparamblk_test from file.. ##\n");
        err = iaxxx_odsp_plugin_set_parameter_blk_from_file(odsp_hw,
                                                            plugin_instance_id,
                                                            param_blk_id,
                                                            proc_block_id,
                                                            file_path);
    } else if (setparamblk_test_option == SETPARAMBLK_TEST_OPTION_CUSTOM) {
        fprintf(stdout, "\n## Setparamblk_test custom configuration.. ##\n");
        err = iaxxx_odsp_plugin_set_custom_cfg(odsp_hw, plugin_instance_id,
                                            proc_block_id, param_blk_id, 0,
                                            file_path);
    } else if (setparamblk_test_option == SETPARAMBLK_TEST_OPTION_WITHACK) {

        fprintf(stdout, "\n## Setparamblk_test with ack and retry.. ##\n");
        err = setparamblk_with_ack_test(odsp_hw, plugin_instance_id,
                                        proc_block_id, param_blk_id,
                                        max_retries, response_data,
                                        response_data_size_in_words, file_path);
        if (!err) {
            // Print the response data
            fprintf(stdout,"\nResponse Data::");
            for (index = 0; index < response_data_size_in_words; index++) {
                if (!(index % 8))
                    fprintf(stdout, "\n0x%04x: ", index);

                fprintf(stdout, "0x%08x ", response_data[index]);
            }
            fprintf(stdout, "\n");
        }
        free(response_data);
    }

    iaxxx_odsp_deinit(odsp_hw);
    if (err)
        fprintf(stdout, "\n## Setparamblk_test failed!! ##\n\n");
    else
        fprintf(stdout, "\n## Setparamblk_test done. ##\n\n");

    return 0;
}