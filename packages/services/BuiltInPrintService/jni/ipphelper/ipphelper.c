/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#include "lib_wprint.h"
#include "cups.h"
#include "http-private.h"
#include "ipphelper.h"
#include "wprint_debug.h"

#include "ipp_print.h"
#include "../plugins/media.h"

#define TAG "ipphelper"
#define IPP_JOB_UNKNOWN ((ipp_jstate_t)(-1))

const char *resource_extensions_arr[] = {
        DEFAULT_IPP_URI_RESOURCE, "/"
};

/*
 * Get the IPP version of the given printer
 */
static status_t determine_ipp_version(char *, http_t *);

/*
 * Tests IPP versions and sets it to the latest working version
 */
static status_t test_and_set_ipp_version(char *, http_t *, int, int);

/*
 * Parses supported IPP versions from the IPP response and copies them into ippVersions
 */
static void parse_IPPVersions(ipp_t *response, ipp_version_supported_t *ippVersions);

/*
 * Parses printer URIs from the IPP response and copies them into capabilities
 */
static void parse_printerUris(ipp_t *response, printer_capabilities_t *capabilities);

static inline const char* strnstr(const char* s, const char* needle, size_t len) {
    if (len <= 0) return NULL;

    const char c = *needle++;
    const size_t needleLen = strlen(needle);
    do {
        do {
            if (len <= (ssize_t)needleLen) return NULL;
            --len;
        } while (*s++ != c);
    } while (memcmp(s, needle, needleLen) != 0);
    s--;
    return s;
}

/*
 * Known media sizes.
 *
 * A note on rounding: In some cases the Android-specified width (in mils) is rounded down.
 * This causes artifacts in libjpeg-turbo when rendering to the correct width, so in these
 * cases we override with a rounded-up value.
 */
struct MediaSizeTableElement SupportedMediaSizes[SUPPORTED_MEDIA_SIZE_COUNT] = {
        { US_LETTER, "LETTER", 8500, 11000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_letter_8.5x11in",
          215900, 279400 },
        { US_LEGAL, "LEGAL", 8500, 14000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_legal_8.5x14in",
          215900, 355600 },
        { LEDGER, "LEDGER", 11000, 17000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_ledger_11x17in",
          279400, 431800 },
        { INDEX_CARD_5X7, "5X7", 5000, 7000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_5x7_5x7in",
          127000, 177800 },

        // Android system uses width of 11690
        { ISO_A3, "A3", 11694, 16540, 297, 420, "iso_a3_297x420mm",
          297000, 420000 },

        // Android system uses width of 8267
        { ISO_A4, "A4", 8268, 11692, 210, 297, "iso_a4_210x297mm",
          210000, 297000 },
        { ISO_A5, "A5", 5830, 8270, 148, 210, "iso_a5_148x210mm",
          148000, 210000 },

        // Android system uses width of 10118
        { JIS_B4, "JIS B4", 10119, 14331, 257, 364, "jis_b4_257x364mm",
          257000, 364000 },

        // Android system uses width of 7165
        { JIS_B5, "JIS B5", 7167, 10118, 182, 257, "jis_b5_182x257mm",
          182000, 257000 },
        { US_GOVERNMENT_LETTER, "8x10", 8000, 10000, UNKNOWN_VALUE, UNKNOWN_VALUE,
          "na_govt-letter_8x10in",203200, 254000 },
        { INDEX_CARD_4X6, "4x6", 4000, 6000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_index-4x6_4x6in",
          101600, 152400 },
        { JPN_HAGAKI_PC, "JPOST", 3940, 5830, 100, 148, "jpn_hagaki_100x148mm",
          100000, 148000 },
        { PHOTO_89X119, "89X119", 3504, 4685, 89, 119, "om_dsc-photo_89x119mm",
          89000, 119000 },
        { CARD_54X86, "54X86", 2126, 3386, 54, 86, "om_card_54x86mm",
          54000,  86000 },
        { OE_PHOTO_L, "L", 3500, 5000, UNKNOWN_VALUE, UNKNOWN_VALUE, "oe_photo-l_3.5x5in",
          88900, 127000 },

          // Large formats
        { ISO_AND_JIS_A0, "A0", 33110, 46810, 841, 1189, "iso_a0_841x1189mm",
          841000, 1189000},
        { ISO_AND_JIS_A1, "A1", 23390, 33110, 594, 841, "iso_a1_594x841mm",
          594000, 841000},
        { ISO_AND_JIS_A2, "A2", 16540, 23390, 420, 594, "iso_a2_420x594mm",
          420000, 594000},
        { ARCH_A, "9X12", 9000, 12000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_arch-a_9x12in",
          228600, 304800},
        { ARCH_B, "12X18", 12000, 18000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_arch-b_12x18in",
          304800, 457200},
        { ARCH_C, "18x24", 18000, 24000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_arch-c_18x24in",
          457200, 609600},
        { ARCH_D, "24x36", 24000, 36000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_arch-d_24x36in",
          609600, 914400},
        { ARCH_E, "36x48", 36000, 48000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_arch-e_36x48in",
          914400, 1219200},
        { ARCH_E1, "30x42", 30000, 42000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_wide-format_30x42in",
          762000, 1066800},
        { C_SIZE, "AnsiC", 17000, 22000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_c_17x22in",
          431800, 558800},
        { D_SIZE, "AnsiD", 22000, 34000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_d_22x34in",
          558800, 863600},
        { E_SIZE, "AnsiE", 34000, 44000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_e_34x44in",
          863600, 1117600},
        { F_SIZE, "AnsiF", 28000, 40000, UNKNOWN_VALUE, UNKNOWN_VALUE, "asme_f_28x40in",
          711200, 1016000},
        { SUPER_B, "SuperB", 13000, 19000, UNKNOWN_VALUE, UNKNOWN_VALUE, "na_super-b_13x19in",
          330200, 482600}
};

typedef struct {
    double Lower;
    double Upper;
} media_dimension_mm_t;

static const char *__request_ipp_version[] = {"ipp-versions-supported"};

static int __ipp_version_major = 2;
static int __ipp_version_minor = 0;

status_t set_ipp_version(ipp_t *op_to_set, char *printer_uri, http_t *http,
        ipp_version_state use_existing_version) {
    LOGD("set_ipp_version(): Enter %d", use_existing_version);
    if (op_to_set == NULL) {
        return ERROR;
    }
    switch (use_existing_version) {
        case NEW_REQUEST_SEQUENCE:
            __ipp_version_major = 2;
            __ipp_version_minor = 0;
            break;
        case IPP_VERSION_RESOLVED:
            break;
        case IPP_VERSION_UNSUPPORTED:
            if (determine_ipp_version(printer_uri, http) != 0) {
                return ERROR;
            }
            break;
    }
    ippSetVersion(op_to_set, __ipp_version_major, __ipp_version_minor);
    LOGD("set_ipp_version(): Done");
    return OK;
}

static status_t determine_ipp_version(char *printer_uri, http_t *http) {
    LOGD("determine_ipp_version(): Enter printer_uri =  %s", printer_uri);

    if (http == NULL) {
        LOGE("determine_ipp_version(): http is NULL cannot continue");
        return ERROR;
    }
    if ((test_and_set_ipp_version(printer_uri, http, 1, 1) == OK)
            || (test_and_set_ipp_version(printer_uri, http, 1, 0) == OK)
            || (test_and_set_ipp_version(printer_uri, http, 2, 0) == OK)) {
        LOGD("successfully set ipp version.");
    } else {
        LOGD("could not get ipp version using any known ipp version.");
        return ERROR;
    }
    return OK;
}

static status_t test_and_set_ipp_version(char *printer_uri, http_t *http, int major, int minor) {
    status_t return_value = ERROR;
    int service_unavailable_retry_count = 0;
    int bad_request_retry_count = 0;
    ipp_t *request = NULL;
    ipp_t *response;
    ipp_version_supported_t ippVersions;
    char http_resource[1024];
    int op = IPP_GET_PRINTER_ATTRIBUTES;

    LOGD("test_and_set_ipp_version(): Enter %d - %d", major, minor);
    memset(&ippVersions, 0, sizeof(ipp_version_supported_t));
    getResourceFromURI(printer_uri, http_resource, 1024);
    do {
        request = ippNewRequest(op);
        ippSetVersion(request, major, minor);
        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL, printer_uri);
        ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
                sizeof(__request_ipp_version) / sizeof(__request_ipp_version[0]),
                NULL, __request_ipp_version);
        if ((response = cupsDoRequest(http, request, http_resource)) == NULL) {
            ipp_status_t ipp_status = cupsLastError();
            LOGD("test_and_set_ipp_version:  response is null:  ipp_status %d %s",
                    ipp_status, ippErrorString(ipp_status));
            if (ipp_status == IPP_INTERNAL_ERROR) {
                LOGE("test_and_set_ipp_version: 1280 received, bailing...");
                break;
            } else if ((ipp_status == IPP_SERVICE_UNAVAILABLE) &&
                    (service_unavailable_retry_count < IPP_SERVICE_ERROR_MAX_RETRIES)) {
                LOGE("test_and_set_ipp_version: 1282 received, retrying %d of %d",
                        service_unavailable_retry_count, IPP_SERVICE_ERROR_MAX_RETRIES);
                service_unavailable_retry_count++;
                continue;
            } else if (ipp_status == IPP_BAD_REQUEST) {
                LOGE("test_and_set_ipp_version: IPP_Status of IPP_BAD_REQUEST "
                        "received. retry (%d) of (%d)", bad_request_retry_count,
                        IPP_BAD_REQUEST_MAX_RETRIES);
                if (bad_request_retry_count > IPP_BAD_REQUEST_MAX_RETRIES) {
                    break;
                }
                bad_request_retry_count++;
                continue;
            } else if (ipp_status == IPP_NOT_FOUND) {
                LOGE("test_and_set_ipp_version: IPP_Status of IPP_NOT_FOUND received");
                break;
            }
            return_value = ERROR;
        } else {
            ipp_status_t ipp_status = cupsLastError();
            LOGD("ipp CUPS last ERROR: %d, %s", ipp_status, ippErrorString(ipp_status));
            if (ipp_status == IPP_BAD_REQUEST) {
                LOGD("IPP_Status of IPP_BAD_REQUEST received. retry (%d) of (%d)",
                        bad_request_retry_count, IPP_BAD_REQUEST_MAX_RETRIES);
                if (bad_request_retry_count > IPP_BAD_REQUEST_MAX_RETRIES) {
                    break;
                }
                bad_request_retry_count++;
                ippDelete(response);
                continue;
            }

            parse_IPPVersions(response, &ippVersions);
            if (ippVersions.supportsIpp20) {
                __ipp_version_major = 2;
                __ipp_version_minor = 0;
                return_value = OK;
                LOGD("test_and_set_ipp_version(): ipp version set to %d,%d",
                        __ipp_version_major, __ipp_version_minor);
            } else if (ippVersions.supportsIpp11) {
                __ipp_version_major = 1;
                __ipp_version_minor = 1;
                return_value = OK;
                LOGD("test_and_set_ipp_version(): ipp version set to %d,%d",
                        __ipp_version_major, __ipp_version_minor);
            } else if (ippVersions.supportsIpp10) {
                __ipp_version_major = 1;
                __ipp_version_minor = 0;
                return_value = OK;
                LOGD("test_and_set_ipp_version(): ipp version set to %d,%d",
                        __ipp_version_major, __ipp_version_minor);
            } else {
                LOGD("test_and_set_ipp_version: ipp version not found");
                return_value = ERROR;
            }
        }
        if (response != NULL) ippDelete(response);
        break;
    } while (1);
    return return_value;
}

ipp_status_t get_PrinterState(http_t *http, char *printer_uri,
        printer_state_dyn_t *printer_state_dyn, ipp_pstate_t *printer_state) {
    LOGD("get_PrinterState(): Enter");

    // Requested printer attributes
    static const char *pattrs[] = {"printer-make-and-model", "printer-state",
            "printer-state-message", "printer-state-reasons"};

    ipp_t *request = NULL;
    ipp_t *response = NULL;
    ipp_status_t ipp_status = IPP_OK;
    int op = IPP_GET_PRINTER_ATTRIBUTES;
    char http_resource[1024];
    getResourceFromURI(printer_uri, http_resource, 1024);

    if (printer_state_dyn == NULL) {
        LOGE("get_PrinterState(): printer_state_dyn is null");
        return ipp_status;
    }

    if (printer_state) {
        *printer_state = IPP_PRINTER_STOPPED;
    } else {
        LOGE("get_PrinterState(): printer_state is null");
    }
    request = ippNewRequest(op);

    ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL, printer_uri);

    ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
            sizeof(pattrs) / sizeof(pattrs[0]), NULL, pattrs);

    if ((response = ipp_doCupsRequest(http, request, http_resource, printer_uri)) == NULL) {
        ipp_status = cupsLastError();
        LOGE("get_PrinterState(): response is null: ipp_status %d", ipp_status);
        printer_state_dyn->printer_status = PRINT_STATUS_UNABLE_TO_CONNECT;
        printer_state_dyn->printer_reasons[0] = PRINT_STATUS_UNABLE_TO_CONNECT;
    } else {
        ipp_status = cupsLastError();
        LOGD("ipp CUPS last ERROR: %d, %s", ipp_status, ippErrorString(ipp_status));
        get_PrinterStateReason(response, printer_state, printer_state_dyn);
        LOGD("get_PrinterState(): printer_state_dyn->printer_status: %d",
                printer_state_dyn->printer_status);
    }
    LOGD("get_PrinterState(): exit http->fd %d, ipp_status %d, printer_state %d", http->fd,
            ipp_status, printer_state_dyn->printer_status);

    ippDelete(request);
    ippDelete(response);
    return ipp_status;
}

void get_PrinterStateReason(ipp_t *response, ipp_pstate_t *printer_state,
        printer_state_dyn_t *printer_state_dyn) {
    LOGD("get_PrinterStateReason(): Enter");
    ipp_attribute_t *attrptr;
    int reason_idx = 0;
    int idx = 0;
    ipp_pstate_t printer_ippstate = IPP_PRINTER_IDLE;

    if ((attrptr = ippFindAttribute(response, "printer-state", IPP_TAG_ENUM)) == NULL) {
        LOGE("get_PrinterStateReason printer-state null");
        printer_state_dyn->printer_status = PRINT_STATUS_UNABLE_TO_CONNECT;
        printer_state_dyn->printer_reasons[0] = PRINT_STATUS_UNABLE_TO_CONNECT;
    } else {
        printer_ippstate = (ipp_pstate_t) ippGetInteger(attrptr, 0);
        *printer_state = printer_ippstate;

        LOGD("get_PrinterStateReason printer-state: %d", printer_ippstate);
        // set the printer_status; they may be modified based on the status reasons below.
        switch (printer_ippstate) {
            case IPP_PRINTER_IDLE:
                printer_state_dyn->printer_status = PRINT_STATUS_IDLE;
                break;
            case IPP_PRINTER_PROCESSING:
                printer_state_dyn->printer_status = PRINT_STATUS_PRINTING;
                break;
            case IPP_PRINTER_STOPPED:
                printer_state_dyn->printer_status = PRINT_STATUS_SVC_REQUEST;
                break;
        }
    }

    if ((attrptr = ippFindAttribute(response, "printer-state-reasons", IPP_TAG_KEYWORD)) == NULL) {
        LOGE(" get_PrinterStateReason printer-state reason null");
        printer_state_dyn->printer_status = PRINT_STATUS_UNABLE_TO_CONNECT;
        printer_state_dyn->printer_reasons[0] = PRINT_STATUS_UNABLE_TO_CONNECT;
    } else {
        for (idx = 0; idx < ippGetCount(attrptr); idx++) {
            // Per RFC2911 any of these can have -error, -warning, or -report appended to end
            LOGD("get_PrinterStateReason printer-state-reason: %s",
                    ippGetString(attrptr, idx, NULL));
            if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_NONE,
                    strlen(IPP_PRNT_STATE_NONE)) == 0) {
                switch (printer_ippstate) {
                    case IPP_PRINTER_IDLE:
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_IDLE;
                        break;
                    case IPP_PRINTER_PROCESSING:
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_PRINTING;
                        break;
                    case IPP_PRINTER_STOPPED:
                        // should this be PRINT_STATUS_SVC_REQUEST
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_UNKNOWN;
                        break;
                }
            } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_SPOOL_FULL,
                    strlen(IPP_PRNT_STATE_SPOOL_FULL)) == 0) {
                switch (printer_ippstate) {
                    case IPP_PRINTER_IDLE:
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_UNKNOWN;
                        break;
                    case IPP_PRINTER_PROCESSING:
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_PRINTING;
                        break;
                    case IPP_PRINTER_STOPPED:
                        // should this be PRINT_STATUS_SVC_REQUEST
                        printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_UNKNOWN;
                        break;
                }
            } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_MARKER_SUPPLY_LOW,
                    strlen(IPP_PRNT_STATE_MARKER_SUPPLY_LOW)) == 0) {
                printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_LOW_ON_INK;
            } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_TONER_LOW,
                    strlen(IPP_PRNT_STATE_TONER_LOW)) == 0) {
                printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_LOW_ON_TONER;
            } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_OTHER_WARN,
                    strlen(IPP_PRNT_STATE_OTHER_WARN)) == 0) {
                printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_UNKNOWN;
            } else {
                // check blocking cases
                if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_MEDIA_NEEDED,
                        strlen(IPP_PRNT_STATE_MEDIA_NEEDED)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_OUT_OF_PAPER;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_MEDIA_EMPTY,
                        strlen(IPP_PRNT_STATE_MEDIA_EMPTY)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_OUT_OF_PAPER;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_TONER_EMPTY,
                        strlen(IPP_PRNT_STATE_TONER_EMPTY)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_OUT_OF_TONER;
                } else if (strncmp(ippGetString(attrptr, idx, NULL),
                        IPP_PRNT_STATE_MARKER_SUPPLY_EMPTY,
                        strlen(IPP_PRNT_STATE_MARKER_SUPPLY_EMPTY)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_OUT_OF_INK;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_DOOR_OPEN,
                        strlen(IPP_PRNT_STATE_DOOR_OPEN)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_DOOR_OPEN;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_COVER_OPEN,
                        strlen(IPP_PRNT_STATE_COVER_OPEN)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_DOOR_OPEN;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_MEDIA_JAM,
                        strlen(IPP_PRNT_STATE_MEDIA_JAM)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_JAMMED;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_SHUTDOWN,
                        strlen(IPP_PRNT_SHUTDOWN)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_SHUTTING_DOWN;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_STATE_OTHER_ERR,
                        strlen(IPP_PRNT_STATE_OTHER_ERR)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_SVC_REQUEST;
                } else if (strncmp(ippGetString(attrptr, idx, NULL), IPP_PRNT_PAUSED,
                        strlen(IPP_PRNT_PAUSED)) == 0) {
                    printer_state_dyn->printer_reasons[reason_idx++] = PRINT_STATUS_UNKNOWN;
                }
            }
        }  // end of reasons loop
    }
}

void set_jobStateDyn(ipp_t *response,
                    ipp_jstate_t *job_state,
                    job_state_dyn_t *job_state_dyn) {
    ipp_attribute_t *attr;
    if ((attr = ippFindAttribute(response, "job-state", IPP_TAG_ENUM)) == NULL) {
        LOGE("  job-state null");
    } else {
        *job_state = (ipp_jstate_t) ippGetInteger(attr, 0);
    }

    switch (*job_state) {
        case IPP_JOB_PENDING:
            job_state_dyn->job_state = IPP_JOB_STATE_PENDING;
            break;
        case IPP_JOB_HELD:
            job_state_dyn->job_state = IPP_JOB_STATE_PENDING_HELD;
            break;
        case IPP_JOB_PROCESSING:
            job_state_dyn->job_state = IPP_JOB_STATE_PROCESSING;
            break;
        case IPP_JOB_STOPPED:
            job_state_dyn->job_state = IPP_JOB_STATE_PROCESSING_STOPPED;
            break;
        case IPP_JOB_CANCELED:
            job_state_dyn->job_state = IPP_JOB_STATE_CANCELED;
            break;
        case IPP_JOB_ABORTED:
            job_state_dyn->job_state = IPP_JOB_STATE_ABORTED;
            break;
        case IPP_JOB_COMPLETED:
            job_state_dyn->job_state = IPP_JOB_STATE_COMPLETED;
            break;
        default:
            if (*job_state == IPP_JOB_UNKNOWN) {
                job_state_dyn->job_state = IPP_JOB_STATE_UNABLE_TO_CONNECT;
            }
            break;
    }
}

void parse_jobStateReasons(ipp_t *response,
                          job_state_dyn_t *job_state_dyn) {
    ipp_attribute_t *attr;
    if ((attr = ippFindAttribute(response, "job-state-reasons", IPP_TAG_KEYWORD)) == NULL) {
        job_state_dyn->job_state = IPP_JOB_STATE_UNABLE_TO_CONNECT;
        job_state_dyn->job_state_reasons[0] = IPP_JOB_STATE_REASON_UNABLE_TO_CONNECT;
    } else {
        int reasons_idx = 0;
        for (int i = 0; i < ippGetCount(attr); i++) {
            const char *text = ippGetString(attr, i, NULL);
            LOGD("get_JobStatus: ipp job-state-reason(%d) : %s", i, text);
            if (strcmp(text, "job-canceled-by-user") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_JOB_CANCELED_BY_USER;
            } else if (strcmp(text, "job-canceled-at-device") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_JOB_CANCELED_AT_DEVICE;
            } else if (strcmp(text, "aborted-by-system") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_ABORTED_BY_SYSTEM;
            } else if (strcmp(text, "unsupported-compression") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_UNSUPPORTED_COMPRESSION;
            } else if (strcmp(text, "compression-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_COMPRESSION_ERROR;
            } else if (strcmp(text, "unsupported-document-format") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_UNSUPPORTED_DOCUMENT_FORMAT;
            } else if (strcmp(text, "document-format-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_FORMAT_ERROR;
            } else if (strcmp(text, "service-off-line") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_SERVICE_OFFLINE;
            } else if (strcmp(text, "document-password-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_PASSWORD_ERROR;
            } else if (strcmp(text, "document-permission-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_PERMISSION_ERROR;
            } else if (strcmp(text, "document-security-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_SECURITY_ERROR;
            } else if (strcmp(text, "document-unprintable-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_UNPRINTABLE_ERROR;
            } else if (strcmp(text, "document-access-error") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_DOCUMENT_ACCESS_ERROR;
            } else if (strcmp(text, "submission-interrupted") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_SUBMISSION_INTERRUPTED;
            } else if (strcmp(text, "account-authorization-failed") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_AUTHORIZATION_FAILED;
            } else if (strcmp(text, "account-closed") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_ACCOUNT_CLOSED;
            } else if (strcmp(text, "account-info-needed") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_ACCOUNT_INFO_NEEDED;
            } else if (strcmp(text, "account-limit-reached") == 0) {
                job_state_dyn->job_state_reasons[reasons_idx++] =
                        IPP_JOB_STATE_REASON_ACCOUNT_LIMIT_REACHED;
            }
        }
    }
}

ipp_status_t get_JobStatus(http_t *http,
              char *printer_uri,        /* I - URI buffer */
              int job_id,
              job_state_dyn_t *job_state_dyn,
              ipp_jstate_t *job_state,
              const char *requesting_user) {
    LOGD("get_JobStatus(): Enter");
    static const char *const jattrs[] =
            {            /* Job attributes we want */
                    "job-id",
                    "job-printer-uri",
                    "job-name",
                    "job-state",
                    "job-state-reasons"
            };
    int service_unavailable_retry_count = 0;
    int bad_request_retry_count = 0;
    int op = IPP_GET_JOB_ATTRIBUTES;
    ipp_t *request;  /* IPP request object */
    ipp_t *response; /* IPP response object */
    ipp_attribute_t *attr;     /* Current IPP attribute */
    ipp_attribute_t *attrptr;        /* Attribute pointer */
    ipp_status_t ipp_status = IPP_OK;        /* Status of IPP request */
    ipp_version_state ipp_version_supported = IPP_VERSION_RESOLVED;
    char http_resource[1024];
    getResourceFromURI(printer_uri, http_resource, 1024);

    if (job_state_dyn == NULL) {
        LOGE("get_JobStatus():   ERROR:  get_JobStatus entry: job_state_dyn is null");
        return ipp_status;
    }

    if (job_state != NULL) {
        *job_state = IPP_JOB_UNKNOWN;
    } else {
        LOGE("  get_JobStatus: job_state is null");
        // return error...
        return ipp_status;
    }

    LOGD(" get_JobStatus IPP_GET_JOB_ATTRIBUTES  http->fd %d", http->fd);
    do {
        ipp_status = IPP_OK; // reset ipp_status

        request = ippNewRequest(op);
        if (set_ipp_version(request, printer_uri, http, ipp_version_supported) != 0) {
            LOGE("get_JobStatus(): set_ipp_version!=0, version not set");
            ipp_status = IPP_VERSION_NOT_SUPPORTED;
            ippDelete(request);
            break;
        }

        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL, printer_uri);
        ippAddInteger(request, IPP_TAG_OPERATION, IPP_TAG_INTEGER, "job-id", job_id);
        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME, "requesting-user-name",
                     NULL, requesting_user);
        ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
                      sizeof(jattrs) / sizeof(jattrs[0]), NULL, jattrs);

        for (attrptr = ippFirstAttribute(request); attrptr; attrptr = ippNextAttribute(request)) {
            print_attr(attrptr);
        }

        if ((response = cupsDoRequest(http, request, http_resource)) == NULL) {
            job_state_dyn->job_state = IPP_JOB_STATE_UNABLE_TO_CONNECT;
            job_state_dyn->job_state_reasons[0] = IPP_JOB_STATE_REASON_UNABLE_TO_CONNECT;
            ipp_status = cupsLastError();
            LOGE("  get_JobStatus:  response is null:  ipp_status %d", ipp_status);
            if (ipp_status == IPP_INTERNAL_ERROR) {
                LOGE("get_JobStatus: 1280 received, bailing...");
                break;
            }
            if (ipp_status == IPP_SERVICE_UNAVAILABLE &&
                (service_unavailable_retry_count < IPP_SERVICE_ERROR_MAX_RETRIES)) {
                LOGE("1282 received, retrying %d of %d", service_unavailable_retry_count,
                     IPP_SERVICE_ERROR_MAX_RETRIES);
                service_unavailable_retry_count++;
                continue;
            }
            if (ipp_status == IPP_BAD_REQUEST) {
                LOGE("IPP_Status of IPP_BAD_REQUEST received. retry (%d) of (%d)",
                     bad_request_retry_count, IPP_BAD_REQUEST_MAX_RETRIES);
                bad_request_retry_count++;
                continue;
            }
            if (ipp_status == IPP_NOT_FOUND) {
                LOGE("IPP_Status of IPP_NOT_FOUND received. Switching resource path.");
                if (tryNextResourceExtension(printer_uri)) {
                    getResourceFromURI(printer_uri, http_resource, 1024);
                    continue;
                } else {
                    LOGE("No more resource paths to try");
                    break;
                }
            }
        } else {
            ipp_status = cupsLastError();

            LOGD("ipp CUPS last ERROR: %d, %s", ipp_status, ippErrorString(ipp_status));
            if (ipp_status == IPP_BAD_REQUEST) {
                LOGE("IPP_Status of IPP_BAD_REQUEST received. retry (%d) of (%d)",
                     bad_request_retry_count, IPP_BAD_REQUEST_MAX_RETRIES);
                bad_request_retry_count++;
                ippDelete(response);
                continue;
            }
            if (ipp_status == IPP_VERSION_NOT_SUPPORTED) {
                ipp_version_supported = IPP_VERSION_UNSUPPORTED;
                ippDelete(response);
                continue;
            }
            LOGD("  get_JobStatus:  response!=null:  ipp_status %d", ipp_status);
            for (attrptr = ippFirstAttribute(response);
                    attrptr;
                    attrptr = ippNextAttribute(response))
                print_attr(attrptr);
        }

        set_jobStateDyn(response, job_state, job_state_dyn);
        parse_jobStateReasons(response, job_state_dyn);

        if (response != NULL) ippDelete(response);

        break;
    } while (bad_request_retry_count < IPP_BAD_REQUEST_MAX_RETRIES &&
             service_unavailable_retry_count < IPP_SERVICE_ERROR_MAX_RETRIES);

    LOGD("  get_JobStatus exit  ipp_status %d, job_state %d", ipp_status, *job_state);

    return ipp_status;
}

static void print_col(ipp_t *col) {
    int i;
    ipp_attribute_t *attr;

    LOGD("{");
    for (attr = ippFirstAttribute(col); attr; attr = ippNextAttribute(col)) {
        switch (ippGetValueTag(attr)) {
            case IPP_TAG_INTEGER:
            case IPP_TAG_ENUM:
                for (i = 0; i < ippGetCount(attr); i++) {
                    LOGD("  %s(%s%s)= %d ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)), ippGetInteger(attr, i));
                }
                break;
            case IPP_TAG_BOOLEAN:
                for (i = 0; i < ippGetCount(attr); i++) {
                    if (ippGetBoolean(attr, i)) {
                        LOGD("  %s(%s%s)= true ", ippGetName(attr),
                                ippGetCount(attr) > 1 ? "1setOf " : "",
                                ippTagString(ippGetValueTag(attr)));
                    } else {
                        LOGD("  %s(%s%s)= false ", ippGetName(attr),
                                ippGetCount(attr) > 1 ? "1setOf " : "",
                                ippTagString(ippGetValueTag(attr)));
                    }
                }
                break;
            case IPP_TAG_NOVALUE:
                LOGD("  %s(%s%s)= novalue", ippGetName(attr),
                        ippGetCount(attr) > 1 ? "1setOf " : "", ippTagString(ippGetValueTag(attr)));
                break;
            case IPP_TAG_RANGE:
                for (i = 0; i < ippGetCount(attr); i++) {
                    int lower, upper;
                    lower = ippGetRange(attr, i, &upper);
                    LOGD("  %s(%s%s)= %d-%d ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)), lower, upper);
                }
                break;
            case IPP_TAG_RESOLUTION:
                for (i = 0; i < ippGetCount(attr); i++) {
                    ipp_res_t units;
                    int xres, yres;
                    xres = ippGetResolution(attr, i, &yres, &units);
                    LOGD("  %s(%s%s)= %dx%d%s ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)), xres, yres,
                            units == IPP_RES_PER_INCH ? "dpi" : "dpc");
                }
                break;
            case IPP_TAG_STRING:
            case IPP_TAG_TEXT:
            case IPP_TAG_NAME:
            case IPP_TAG_KEYWORD:
            case IPP_TAG_CHARSET:
            case IPP_TAG_URI:
            case IPP_TAG_MIMETYPE:
            case IPP_TAG_LANGUAGE:
                for (i = 0; i < ippGetCount(attr); i++) {
                    LOGD("  %s(%s%s)= \"%s\" ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)), ippGetString(attr, i, NULL));
                }
                break;
            case IPP_TAG_TEXTLANG:
            case IPP_TAG_NAMELANG:
                for (i = 0; i < ippGetCount(attr); i++) {
                    const char *charset;
                    const char *text;
                    text = ippGetString(attr, i, &charset);
                    LOGD("  %s(%s%s)= \"%s\",%s ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)), text, charset);
                }
                break;
            case IPP_TAG_BEGIN_COLLECTION:
                for (i = 0; i < ippGetCount(attr); i++) {
                    print_col(ippGetCollection(attr, i));
                }
                break;
            default:
                break;
        }
    }
    LOGD("}");
}

void print_attr(ipp_attribute_t *attr) {
    int i;

    if (ippGetName(attr) == NULL) {
        return;
    }

    switch (ippGetValueTag(attr)) {
        case IPP_TAG_INTEGER:
        case IPP_TAG_ENUM:
            for (i = 0; i < ippGetCount(attr); i++) {
                LOGD("%s (%s%s) = %d ", ippGetName(attr), ippGetCount(attr) > 1 ? "1setOf " : "",
                        ippTagString(ippGetValueTag(attr)), ippGetInteger(attr, i));
            }
            break;
        case IPP_TAG_BOOLEAN:
            for (i = 0; i < ippGetCount(attr); i++) {
                if (ippGetBoolean(attr, i)) {
                    LOGD("%s (%s%s) = true ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)));
                } else {
                    LOGD("%s (%s%s) = false ", ippGetName(attr),
                            ippGetCount(attr) > 1 ? "1setOf " : "",
                            ippTagString(ippGetValueTag(attr)));
                }
            }
            break;
        case IPP_TAG_NOVALUE:
            LOGD("%s (%s%s) = novalue", ippGetName(attr),
                    ippGetCount(attr) > 1 ? "1setOf " : "", ippTagString(ippGetValueTag(attr)));
            break;
        case IPP_TAG_RANGE:
            for (i = 0; i < ippGetCount(attr); i++) {
                int lower, upper;
                lower = ippGetRange(attr, i, &upper);
                LOGD("%s (%s%s) = %d-%d ", ippGetName(attr), ippGetCount(attr) > 1 ? "1setOf " : "",
                        ippTagString(ippGetValueTag(attr)), lower, upper);
            }
            break;
        case IPP_TAG_RESOLUTION:
            for (i = 0; i < ippGetCount(attr); i++) {
                ipp_res_t units;
                int xres, yres;
                xres = ippGetResolution(attr, i, &yres, &units);
                LOGD("%s (%s%s) = %dx%d%s ", ippGetName(attr),
                        ippGetCount(attr) > 1 ? "1setOf " : "",
                        ippTagString(ippGetValueTag(attr)), xres, yres,
                        units == IPP_RES_PER_INCH ? "dpi" : "dpc");
            }
            break;
        case IPP_TAG_STRING:
        case IPP_TAG_TEXT:
        case IPP_TAG_NAME:
        case IPP_TAG_KEYWORD:
        case IPP_TAG_CHARSET:
        case IPP_TAG_URI:
        case IPP_TAG_MIMETYPE:
        case IPP_TAG_LANGUAGE:
            for (i = 0; i < ippGetCount(attr); i++) {
                LOGD("%s (%s%s) = \"%s\" ", ippGetName(attr),
                        ippGetCount(attr) > 1 ? "1setOf " : "",
                        ippTagString(ippGetValueTag(attr)), ippGetString(attr, i, NULL));
            }
            break;
        case IPP_TAG_TEXTLANG:
        case IPP_TAG_NAMELANG:
            for (i = 0; i < ippGetCount(attr); i++) {
                const char *charset;
                const char *text;
                text = ippGetString(attr, i, &charset);
                LOGD("%s (%s%s) = \"%s\",%s ", ippGetName(attr),
                        ippGetCount(attr) > 1 ? "1setOf " : "",
                        ippTagString(ippGetValueTag(attr)), text, charset);
            }
            break;

        case IPP_TAG_BEGIN_COLLECTION:
            for (i = 0; i < ippGetCount(attr); i++) {
                LOGD("%s (%s%s): IPP_TAG_BEGIN_COLLECTION", ippGetName(attr),
                        ippGetCount(attr) > 1 ? "1setOf " : "", ippTagString(ippGetValueTag(attr)));
                print_col(ippGetCollection(attr, i));
            }
            LOGD("%s (%s%s): IPP_TAG_END_COLLECTION", ippGetName(attr),
                    ippGetCount(attr) > 1 ? "1setOf " : "", ippTagString(ippGetValueTag(attr)));
            break;

        default:
            break;
    }
}

void parse_IPPVersions(ipp_t *response, ipp_version_supported_t *ippVersions) {
    int i;
    ipp_attribute_t *attrptr;
    char ipp10[] = "1.0";
    char ipp11[] = "1.1";
    char ipp20[] = "2.0";
    LOGD(" Entered IPPVersions");
    if (ippVersions != NULL) {
        memset(ippVersions, 0, sizeof(ipp_version_supported_t));
        LOGD(" in get_supportedIPPVersions");
        attrptr = ippFindAttribute(response, "ipp-versions-supported", IPP_TAG_KEYWORD);
        if (attrptr != NULL) {
            LOGD(" in get_supportedIPPVersions: %d", ippGetCount(attrptr));
            for (i = 0; i < ippGetCount(attrptr); i++) {
                if (strcmp(ipp10, ippGetString(attrptr, i, NULL)) == 0) {
                    ippVersions->supportsIpp10 = 1;
                } else if (strcmp(ipp11, ippGetString(attrptr, i, NULL)) == 0) {
                    ippVersions->supportsIpp11 = 1;
                } else if (strcmp(ipp20, ippGetString(attrptr, i, NULL)) == 0) {
                    ippVersions->supportsIpp20 = 1;
                } else {
                    LOGD("found another ipp version. %s", ippGetString(attrptr, i, NULL));
                }
            }
        }
    }
}

const char *mapDFMediaToIPPKeyword(media_size_t media_size) {
    int i;
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (SupportedMediaSizes[i].media_size == (media_size_t) media_size) {
            return (SupportedMediaSizes[i].PWGName);
        }
    }
    return (SupportedMediaSizes[0].PWGName);
}

int ipp_find_media_size(const char *ipp_media_keyword, media_size_t *media_size) {
    int i;
    LOGD("ipp_find_media_size entry is %s", ipp_media_keyword);
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (strcmp(SupportedMediaSizes[i].PWGName, ipp_media_keyword) == 0) {
            LOGD(" mediaArraySize: match string  %s  PT_size: %d",
                    SupportedMediaSizes[i].PWGName, SupportedMediaSizes[i].media_size);
            break;
        }
    }
    if (i < SUPPORTED_MEDIA_SIZE_COUNT) {
        *media_size = SupportedMediaSizes[i].media_size;
        return i;
    } else {
        return -1;
    }
    return -1;
}

static int getMediaSizeFromTag(const char *media_size_tag, media_size_t *media_size) {
    int i;
    LOGD("find media size from tag %s", media_size_tag);
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (strcmp(SupportedMediaSizes[i].PWGName, media_size_tag) == 0) {
            LOGD("Media size match: pwg name %s", SupportedMediaSizes[i].PWGName);
            *media_size = SupportedMediaSizes[i].media_size;
            return i;
        }
    }
    return -1;
}

static int getMediaSizeNameFromDimens(
        unsigned int mediaColReadyWidth,
        unsigned int mediaColReadyHeight,
        media_size_t *media_size) {
    int i;
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (SupportedMediaSizes[i].WidthInMicrometers / 10 == mediaColReadyWidth
            && SupportedMediaSizes[i].HeightInMicrometers / 10 == mediaColReadyHeight) {
            *media_size = SupportedMediaSizes[i].media_size;
            return i;
        }
    }
    return -1;
}

static void addMediaIfNotDuplicate(
        int idx,
        int *sizes_idx,
        media_supported_t *media_supported,
        media_size_t media_size) {
    if (idx >= 0) {
        // Check if we've already added this media size to the supported list
        bool isDuplicate = false;
        for (int j = 0; j < (*sizes_idx); j++) {
            if (media_supported->idxKeywordTranTable[j] == idx) {
                isDuplicate = true;
                break;
            }
        }
        // Only add if it hasn't been added yet
        if (!isDuplicate) {
            media_supported->media_size[(*sizes_idx)] = media_size;
            media_supported->idxKeywordTranTable[(*sizes_idx)] = idx;
            (*sizes_idx)++;
        }
    }
}

static void addRollSupportedSizes(
        unsigned int minWidth,
        unsigned int maxWidth,
        unsigned int minHeight,
        unsigned int maxHeight,
        media_supported_t *media_supported,
        int *sizesIdx) {
    // If a supported media size fits on the roll size, add it to the list
    for (int i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (SupportedMediaSizes[i].WidthInMicrometers / 10 >= minWidth
            && SupportedMediaSizes[i].WidthInMicrometers / 10 <= maxWidth
            && SupportedMediaSizes[i].HeightInMicrometers / 10 >= minHeight
            && SupportedMediaSizes[i].HeightInMicrometers / 10 <= maxHeight) {
            addMediaIfNotDuplicate(i, sizesIdx, media_supported, SupportedMediaSizes[i].media_size);
        }
    }
}

void parse_getMediaSupported(
        ipp_t *response,
        media_supported_t *media_supported,
        printer_capabilities_t *capabilities) {
    int i;
    int sizes_idx = 0;
    bool roll_supported = false;
    LOGD(" Entered getMediaSupported");

    media_size_t media_sizeTemp;
    int idx = 0;

    // Check for media-col-ready first
    ipp_attribute_t *attrptr;
    if ((attrptr =
        ippFindAttribute(response, "media-col-ready", IPP_TAG_BEGIN_COLLECTION)) != NULL) {
        LOGD("media-col-ready found");
        for (i = 0; i < ippGetCount(attrptr); i++) {
            ipp_t *collection = ippGetCollection(attrptr, i);
            ipp_attribute_t *attrptr2;
            media_ready_set_t mediaReadySet = {};
            int minHeight = 0, maxHeight = 0;
            int minWidth = 0, maxWidth = 0;
            for (attrptr2 = ippFirstAttribute(collection);
                 (attrptr2 != NULL);
                 attrptr2 = ippNextAttribute(collection)) {
                if (strcmp("media-size", ippGetName(attrptr2)) == 0) {
                    ipp_t *collection_sec = ippGetCollection(attrptr2, 0);
                    ipp_attribute_t *attrptr3;
                    for (attrptr3 = ippFirstAttribute(collection_sec);
                         (attrptr3 != NULL);
                         attrptr3 = ippNextAttribute(collection_sec)) {
                        if (strcmp("x-dimension", ippGetName(attrptr3)) == 0) {
                            if (ippGetValueTag(attrptr3) == IPP_TAG_RANGE) {
                                minWidth = ippGetRange(attrptr3, 0, &maxWidth);
                                mediaReadySet.x_dimension = minWidth;
                            } else if (ippGetValueTag(attrptr3) == IPP_TAG_INTEGER) {
                                maxWidth = ippGetInteger(attrptr3, 0);
                                mediaReadySet.x_dimension = maxWidth;
                            }
                        } else if (strcmp("y-dimension", ippGetName(attrptr3)) == 0) {
                            if (ippGetValueTag(attrptr3) == IPP_TAG_RANGE) {
                                minHeight = ippGetRange(attrptr3, 0, &maxHeight);
                                mediaReadySet.y_dimension = minHeight;
                            } else if (ippGetValueTag(attrptr3) == IPP_TAG_INTEGER) {
                                mediaReadySet.y_dimension = ippGetInteger(attrptr3, 0);
                            }
                        }
                    }
                } else if (strcmp("media-source", ippGetName(attrptr2)) == 0) {
                    memset(mediaReadySet.media_tray_tag, 0, MAX_STRING + 1);
                    strncpy(mediaReadySet.media_tray_tag,
                            ippGetString(attrptr2, 0, NULL), MAX_STRING);
                }
            }
            if (minHeight > 0 && maxHeight > 0
                && strstr(mediaReadySet.media_tray_tag, "roll") != NULL) {
                roll_supported = true;
                // If the source is a roll, add supported sizes that would fit on the roll
                addRollSupportedSizes(minWidth, maxWidth, minHeight, maxHeight,
                                      media_supported, &sizes_idx);
            } else {
                // Get the media size name from x and y dimensions
                idx = getMediaSizeNameFromDimens(mediaReadySet.x_dimension,
                                                 mediaReadySet.y_dimension,
                                                 &media_sizeTemp);
                addMediaIfNotDuplicate(idx, &sizes_idx, media_supported, media_sizeTemp);
            }
        }
    }
    // Check media-ready if no supported media was found in media-col-ready
    if (sizes_idx == 0
        && (attrptr = ippFindAttribute(response, "media-ready", IPP_TAG_KEYWORD)) != NULL) {
        LOGD("media-ready found");
        for (i = 0; i < ippGetCount(attrptr); i++) {
            char media_size_tag[MAX_STRING + 1];
            memset(media_size_tag, 0, MAX_STRING + 1);
            strncpy(media_size_tag,
                    ippGetString(attrptr, i, NULL), MAX_STRING);
            idx = getMediaSizeFromTag(media_size_tag, &media_sizeTemp);
            addMediaIfNotDuplicate(idx, &sizes_idx, media_supported, media_sizeTemp);
        }
    }

    // Set media ready size as default if we found any
    if (sizes_idx > 0) {
        strlcpy(capabilities->mediaDefault, mapDFMediaToIPPKeyword(media_supported->media_size[0]),
                    sizeof(capabilities->mediaDefault));
    }

    // Append media-supported. media is de-duplicated later in java
    if ((attrptr = ippFindAttribute(response, "media-supported", IPP_TAG_KEYWORD)) != NULL) {
        LOGD("media-supported  found; number of values %d", ippGetCount(attrptr));
        for (i = 0; i < ippGetCount(attrptr); i++) {
            idx = ipp_find_media_size(ippGetString(attrptr, i, NULL), &media_sizeTemp);

            // Modified since anytime the find media size returned 0 it could either mean
            // NOT found or na_letter.
            if (idx >= 0) {
                media_supported->media_size[sizes_idx] = media_sizeTemp;
                media_supported->idxKeywordTranTable[sizes_idx] = idx;
                sizes_idx++;
            }
        }
    }
    // Get common media which is supported by media-col-ready(roll) and media-supported
    if (roll_supported) {
        int supported_by_roll_and_media = 0;
        for (int j = 0; j <= PAGE_STATUS_MAX - 1 &&
                        media_supported->media_size[j] != 0; j++) {
            for (int k = j + 1; k <= PAGE_STATUS_MAX - 1 &&
                                media_supported->media_size[k] != 0; k++) {
                if (media_supported->media_size[j] != 0 &&
                    media_supported->media_size[j] == media_supported->media_size[k]) {
                    media_supported->media_size[supported_by_roll_and_media] =
                            media_supported->media_size[j];
                    media_supported->idxKeywordTranTable[supported_by_roll_and_media] =
                            media_supported->idxKeywordTranTable[j];
                    supported_by_roll_and_media += 1;
                    break;
                }
            }
        }

        for (int j = supported_by_roll_and_media; j <= PAGE_STATUS_MAX - 1 &&
                                                  media_supported->media_size[j] != 0; j++) {
            media_supported->media_size[j] = 0;
            media_supported->idxKeywordTranTable[j] = -1;
        }
    }
    if (sizes_idx == 0) {
        LOGD("No supported media found");
    }
}

static void get_supportedPrinterResolutions(ipp_attribute_t *attrptr,
        printer_capabilities_t *capabilities) {
    int idx = 0;
    int i;
    for (i = 0; i < ippGetCount(attrptr); i++) {
        ipp_res_t units;
        int xres, yres;
        xres = ippGetResolution(attrptr, i, &yres, &units);
        if (units == IPP_RES_PER_INCH) {
            if ((idx < MAX_RESOLUTIONS_SUPPORTED) && (xres == yres)) {
                capabilities->supportedResolutions[idx] = xres;
                idx++;
            }
        }
    }
    capabilities->numSupportedResolutions = idx;
}

void getResourceFromURI(const char *uri, char *resource, int resourcelen) {
    char scheme[1024];
    char username[1024];
    char host[1024];
    int port;
    httpSeparateURI(0, uri, scheme, 1024, username, 1024, host, 1024, &port, resource, resourcelen);
}

/*
 * Add a new media type to a printer's collection of supported media types
 */
static void addMediaType(printer_capabilities_t *capabilities, media_type_t mediaType) {
    int index;
    for (index = 0; index < capabilities->numSupportedMediaTypes; index++) {
        // Skip if already present
        if (capabilities->supportedMediaTypes[index] == mediaType) return;
    }

    // Add if not found and not too many
    if (capabilities->numSupportedMediaTypes < MAX_MEDIA_TYPES_SUPPORTED) {
        capabilities->supportedMediaTypes[capabilities->numSupportedMediaTypes++] = mediaType;
    } else {
        LOGI("Hit MAX_MEDIA_TYPES_SUPPORTED while adding %d", mediaType);
    }
}

void parse_printerAttributes(ipp_t *response, printer_capabilities_t *capabilities) {
    int i, j;
    ipp_attribute_t *attrptr;

    LOGD("Entered parse_printerAttributes");

    media_supported_t media_supported;
    for (i = 0; i <= PAGE_STATUS_MAX - 1; i++) {
        media_supported.media_size[i] = 0;
        media_supported.idxKeywordTranTable[i] = -1;
    }
    parse_getMediaSupported(response, &media_supported, capabilities);

    parse_printerUris(response, capabilities);

    LOGD("Media Supported: ");
    int idx = 0;
    capabilities->numSupportedMediaTypes = 0;
    for (i = 0; i <= PAGE_STATUS_MAX - 1; i++) {
        if (media_supported.media_size[i] != 0 && media_supported.idxKeywordTranTable[i] >= 0) {
            capabilities->supportedMediaSizes[capabilities->numSupportedMediaSizes++] =
                    media_supported.media_size[i];
            idx = media_supported.idxKeywordTranTable[i];
            LOGD(" i %d, \tPT_Size: %d  \tidx %d \tKeyword: %s", i, media_supported.media_size[i],
                    idx, SupportedMediaSizes[idx].PWGName);
        }
    }

    if ((attrptr = ippFindAttribute(response, "printer-dns-sd-name", IPP_TAG_NAME)) != NULL) {
        strlcpy(capabilities->name, ippGetString(attrptr, 0, NULL), sizeof(capabilities->name));
    }

    if (!capabilities->name[0]) {
        if ((attrptr = ippFindAttribute(response, "printer-info", IPP_TAG_TEXT)) != NULL) {
            strlcpy(capabilities->name, ippGetString(attrptr, 0, NULL), sizeof(capabilities->name));
        }
    }

    if (!capabilities->name[0]) {
        if ((attrptr = ippFindAttribute(response, "printer-name", IPP_TAG_TEXT)) != NULL) {
            strlcpy(capabilities->name, ippGetString(attrptr, 0, NULL), sizeof(capabilities->name));
        }
    }

    if ((attrptr = ippFindAttribute(response, "printer-make-and-model", IPP_TAG_TEXT)) != NULL) {
        strlcpy(capabilities->make, ippGetString(attrptr, 0, NULL), sizeof(capabilities->make));
    }

    if ((attrptr = ippFindAttribute(response, "printer-uuid", IPP_TAG_URI)) != NULL) {
        strlcpy(capabilities->uuid, ippGetString(attrptr, 0, NULL), sizeof(capabilities->uuid));
    }

    if ((attrptr = ippFindAttribute(response, "printer-location", IPP_TAG_TEXT)) != NULL) {
        strlcpy(capabilities->location, ippGetString(attrptr, 0, NULL),
                sizeof(capabilities->location));
    }

    if ((attrptr = ippFindAttribute(response, "media-default", IPP_TAG_KEYWORD)) != NULL
         && strlen(capabilities->mediaDefault) <= 0) {
        strlcpy(capabilities->mediaDefault, ippGetString(attrptr, 0, NULL),
                sizeof(capabilities->mediaDefault));
    }

    if ((attrptr = ippFindAttribute(response, "color-supported", IPP_TAG_BOOLEAN)) != NULL) {
        if (ippGetBoolean(attrptr, 0)) {
            capabilities->color = 1;
        }
    }
    if ((attrptr = ippFindAttribute(response, "copies-supported", IPP_TAG_RANGE)) != NULL) {
        int upper = 0;
        for (i = 0; i < ippGetCount(attrptr); i++) {
            ippGetRange(attrptr, i, &upper);
        }
        if (upper > 1) {
            capabilities->canCopy = 1;
        }
    }
    if ((attrptr = ippFindAttribute(response, "print-color-mode-supported", IPP_TAG_KEYWORD)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcmp("color", ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->color = 1;
            }
        }
    }
    if ((attrptr = ippFindAttribute(response, "print-quality-supported", IPP_TAG_ENUM)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr) && capabilities->numSupportedQuality
                < MAX_QUALITY_SUPPORTED; i++) {
            LOGD("print-quality-supported: %d", ippGetInteger(attrptr, i));
            capabilities->supportedQuality[capabilities->numSupportedQuality++] =
                ippGetInteger(attrptr, i);
        }
    }

    char imagePCLm[] = "application/PCLm";
    char imagePWG[] = "image/pwg-raster";
    char imagePDF[] = "image/pdf";
    char applicationPDF[] = "application/pdf";

    if ((attrptr = ippFindAttribute(response, "document-format-supported", IPP_TAG_MIMETYPE))
            != NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcmp(imagePDF, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->canPrintPDF = 1;
            } else if (strcmp(applicationPDF, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->canPrintPDF = 1;
            } else if (strcmp(imagePCLm, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->canPrintPCLm = 1;
            } else if (strcmp(applicationPDF, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->canPrintPDF = 1;
            } else if (strcmp(imagePWG, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->canPrintPWG = 1;
            }
        }
    }

    if ((attrptr = ippFindAttribute(response, "sides-supported", IPP_TAG_KEYWORD)) != NULL) {
        capabilities->sidesSupported = 1;
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcmp(IPP_SIDES_TWO_SIDED_SHORT_EDGE, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->duplex = 1;
            } else if (strcmp(IPP_SIDES_TWO_SIDED_LONG_EDGE, ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->duplex = 1;
            }
        }
    }

    // Look up supported media types
    capabilities->numSupportedMediaTypes = 0;
    if (((attrptr = ippFindAttribute(response, "media-type-supported", IPP_TAG_KEYWORD)) != NULL)
            || ((attrptr = ippFindAttribute(response, "media-type-supported", IPP_TAG_NAME))
                    != NULL)) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcasestr(ippGetString(attrptr, i, NULL), "photographic-glossy")) {
                addMediaType(capabilities, MEDIA_PHOTO_GLOSSY);
            } else if (strcasestr(ippGetString(attrptr, i, NULL), "photo")) {
                addMediaType(capabilities, MEDIA_PHOTO);
            } else if (strcasestr(ippGetString(attrptr, i, NULL), "stationery")) {
                addMediaType(capabilities, MEDIA_PLAIN);
            } else if (strcasestr(ippGetString(attrptr, i, NULL), "auto")) {
                addMediaType(capabilities, MEDIA_AUTO);
            }
        }
    }

    if (capabilities->numSupportedMediaTypes == 0) {
        // If no recognized media types were found, fall back to all 3 just in case
        addMediaType(capabilities, MEDIA_PLAIN);
        addMediaType(capabilities, MEDIA_PHOTO);
        addMediaType(capabilities, MEDIA_PHOTO_GLOSSY);
    }

    capabilities->numSupportedResolutions = 0;
    // only appears that SMM supports the pclm-source-resolution-supported attribute
    // if that is not present, use the printer-resolution-supported attribute to determine
    // if 300DPI is supported
    if ((attrptr = ippFindAttribute(response, "pclm-source-resolution-supported",
            IPP_TAG_RESOLUTION)) != NULL) {
        get_supportedPrinterResolutions(attrptr, capabilities);
    } else if ((attrptr = ippFindAttribute(response, "printer-resolution-supported",
            IPP_TAG_RESOLUTION)) != NULL) {
        get_supportedPrinterResolutions(attrptr, capabilities);
    }

    char ipp10[] = "1.0";
    char ipp11[] = "1.1";
    char ipp20[] = "2.0";

    if ((attrptr = ippFindAttribute(response, "ipp-versions-supported", IPP_TAG_KEYWORD)) != NULL) {
        unsigned char supportsIpp20 = 0;
        unsigned char supportsIpp11 = 0;
        unsigned char supportsIpp10 = 0;

        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcmp(ipp10, ippGetString(attrptr, i, NULL)) == 0) {
                supportsIpp10 = 1;
            } else if (strcmp(ipp11, ippGetString(attrptr, i, NULL)) == 0) {
                supportsIpp11 = 1;
            } else if (strcmp(ipp20, ippGetString(attrptr, i, NULL)) == 0) {
                supportsIpp20 = 1;
            } else {
                LOGD("found another ipp version. %s", ippGetString(attrptr, i, NULL));
            }
            if (supportsIpp20) {
                capabilities->ippVersionMajor = 2;
                capabilities->ippVersionMinor = 0;
            } else if (supportsIpp11) {
                capabilities->ippVersionMajor = 1;
                capabilities->ippVersionMinor = 1;
            } else if (supportsIpp10) {
                capabilities->ippVersionMajor = 1;
                capabilities->ippVersionMinor = 0;
            } else {
                // default to 1.0
                capabilities->ippVersionMajor = 1;
                capabilities->ippVersionMinor = 0;
            }
        }
    }

    char epcl10[] = "1.0";
    if ((attrptr = ippFindAttribute(response, "epcl-version-supported", IPP_TAG_KEYWORD)) != NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            LOGD("setting epcl_ipp_version (KEYWORD) %s", ippGetString(attrptr, i, NULL));

            // substring match because different devices implemented spec differently
            if (strstr(ippGetString(attrptr, i, NULL), epcl10) != NULL) {
                LOGD("setting epcl_ipp_version = 1");
                capabilities->ePclIppVersion = 1;
            }
        }
    }

    if ((attrptr = ippFindAttribute(response, "epcl-version-supported", IPP_TAG_TEXT)) != NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            LOGD("setting epcl_ipp_verion (TEXT) %s", ippGetString(attrptr, i, NULL));

            // substring match because different devices implemented spec differently
            if (strstr(ippGetString(attrptr, i, NULL), epcl10) != NULL) {
                LOGD("setting epcl_ipp_verion = 1");
                capabilities->ePclIppVersion = 1;
            }
        }
    }

    if ((attrptr = ippFindAttribute(response, "media-col-default", IPP_TAG_BEGIN_COLLECTION)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            LOGD("Gathering margins supported");

            ipp_t *collection = ippGetCollection(attrptr, i);

            for (j = 0, attrptr = ippFirstAttribute(collection);
                    (j < 4) && (attrptr != NULL); attrptr = ippNextAttribute(collection)) {
                if (strcmp("media-top-margin", ippGetName(attrptr)) == 0) {
                    capabilities->printerTopMargin = ippGetInteger(attrptr, 0);
                } else if (strcmp("media-bottom-margin", ippGetName(attrptr)) == 0) {
                    capabilities->printerBottomMargin = ippGetInteger(attrptr, 0);
                } else if (strcmp("media-left-margin", ippGetName(attrptr)) == 0) {
                    capabilities->printerLeftMargin = ippGetInteger(attrptr, 0);
                } else if (strcmp("media-right-margin", ippGetName(attrptr)) == 0) {
                    capabilities->printerRightMargin = ippGetInteger(attrptr, 0);
                }
            }
        }
    }

    if ((attrptr = ippFindAttribute(response, "media-size-name", IPP_TAG_KEYWORD)) != NULL) {
        capabilities->isMediaSizeNameSupported = true;
    } else {
        capabilities->isMediaSizeNameSupported = false;
    }

    // is strip length supported? if so, stored in capabilities
    if ((attrptr = ippFindAttribute(response, "pclm-strip-height-preferred",
            IPP_TAG_INTEGER)) != NULL) {
        LOGD("pclm-strip-height-preferred=%d", ippGetInteger(attrptr, 0));

        // if the strip height is 0, the device wants us to send the entire page in one band
        // (according to ePCL spec). Since our code doesn't currently support generating an entire
        // page in one band, set the strip height to the default value every device *should* support
        // also, for some reason our code crashes when it attempts to generate strips at 512 or
        // above. Therefore, limiting the upper bound strip height to 256
        if (ippGetInteger(attrptr, 0) == 0 || ippGetInteger(attrptr, 0) > 256) {
            capabilities->stripHeight = STRIPE_HEIGHT;
        } else {
            capabilities->stripHeight = ippGetInteger(attrptr, 0);
        }
    } else {
        capabilities->stripHeight = STRIPE_HEIGHT;
    }

    // what is the preferred compression method - jpeg, flate, rle
    if ((attrptr = ippFindAttribute(response, "pclm-compression-method-preferred",
            IPP_TAG_KEYWORD)) != NULL) {
        LOGD("pclm-compression-method-preferred=%s", ippGetString(attrptr, 0, NULL));
    }

    // is device able to rotate back page for duplex jobs? (assume PCLM and PWG are similar)
    capabilities->canRotateDuplexBackPage = 0;
    if ((attrptr = ippFindAttribute(response, "pclm-raster-back-side", IPP_TAG_KEYWORD)) == NULL) {
        attrptr = ippFindAttribute(response, "pwg-raster-document-sheet-back", IPP_TAG_KEYWORD);
    }
    if (attrptr != NULL && strcmp(ippGetString(attrptr, 0, NULL), "rotated") != 0) {
        LOGD("Device can rotate back page for duplex jobs.");
        capabilities->canRotateDuplexBackPage = 1;
    }

    // look for full-bleed supported by looking for 0 on all margins
    bool topsupported = false, bottomsupported = false, rightsupported = false,
            leftsupported = false;
    if ((attrptr = ippFindAttribute(response, "media-top-margin-supported", IPP_TAG_INTEGER)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (ippGetInteger(attrptr, i) == 0) {
                LOGD("Top Margin Supported");
                topsupported = true;
                break;
            }
        }
    }
    if ((attrptr = ippFindAttribute(response, "media-bottom-margin-supported", IPP_TAG_INTEGER)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (ippGetInteger(attrptr, i) == 0) {
                LOGD("Bottom Margin Supported");
                bottomsupported = true;
                break;
            }
        }
    }
    if ((attrptr = ippFindAttribute(response, "media-right-margin-supported", IPP_TAG_INTEGER)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (ippGetInteger(attrptr, i) == 0) {
                LOGD("Right Margin Supported");
                rightsupported = true;
                break;
            }
        }
    }
    if ((attrptr = ippFindAttribute(response, "media-left-margin-supported", IPP_TAG_INTEGER)) !=
            NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (ippGetInteger(attrptr, i) == 0) {
                LOGD("Left Margin Supported");
                leftsupported = true;
                break;
            }
        }
    }

    if (topsupported && bottomsupported && rightsupported && leftsupported) {
        LOGD("full-bleed is supported");
        capabilities->borderless = 1;
    } else {
        LOGD("full-bleed is NOT supported");
    }

    if ((attrptr = ippFindAttribute(response, "printer-device-id", IPP_TAG_TEXT)) != NULL) {
        if (strstr(ippGetString(attrptr, 0, NULL), "PCL3GUI") != NULL) {
            capabilities->inkjet = 1;
        }
    } else if (capabilities->borderless == 1) {
        capabilities->inkjet = 1;
    }

    // determine if device prints pages face-down
    capabilities->faceDownTray = 1;
    if ((attrptr = ippFindAttribute(response, "output-bin-supported", IPP_TAG_KEYWORD)) != NULL) {
        if (strstr(ippGetString(attrptr, 0, NULL), "face-up") != NULL) {
            capabilities->faceDownTray = 0;
        }
    }
    if ((attrptr = ippFindAttribute(response, "printer-output-tray", IPP_TAG_STRING)) != NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            int length = 0;
            const char *tray_str = ippGetOctetString(attrptr, i, &length);
            if (length > 0 && strnstr(tray_str, "faceUp", (size_t)length) != NULL) {
                capabilities->faceDownTray = 0;
            }
        }
    }

    // Determine supported document format details
    if ((attrptr = ippFindAttribute(response, "document-format-details-supported",
            IPP_TAG_KEYWORD)) != NULL) {
        for (i = 0; i < ippGetCount(attrptr); i++) {
            if (strcmp("document-source-application-name", ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->docSourceAppName = 1;
            } else if (
                    strcmp("document-source-application-version", ippGetString(attrptr, i, NULL)) ==
                            0) {
                capabilities->docSourceAppVersion = 1;
            } else if (strcmp("document-source-os-name", ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->docSourceOsName = 1;
            } else if (strcmp("document-source-os-version", ippGetString(attrptr, i, NULL)) == 0) {
                capabilities->docSourceOsVersion = 1;
            }
        }
    }

    // Determine types of print-scaling supported
    capabilities->print_scalings_supported_count = 0;
    if ((attrptr = ippFindAttribute(response, "print-scaling-supported", IPP_TAG_KEYWORD)) != NULL) {
        for (i = 0; i < ippGetCount(attrptr) && i < MAX_PRINT_SCALING_COUNT; i++) {
            capabilities->print_scalings_supported_count++;
            strlcpy(capabilities->print_scalings_supported[i], ippGetString(attrptr, i, NULL),
                    sizeof(capabilities->print_scalings_supported[i]));
        }
    } else {
        LOGD("print-scaling-supported not found");
    }

    memset(capabilities->print_scaling_default, '\0', sizeof(capabilities->print_scaling_default));
    if ((attrptr = ippFindAttribute(response, "print-scaling-default", IPP_TAG_KEYWORD)) != NULL) {
        strlcpy(capabilities->print_scaling_default, ippGetString(attrptr, 0, NULL),
                sizeof(capabilities->print_scaling_default));
    } else {
        LOGD("print-scaling-default not found");
    }

    if ((attrptr = ippFindAttribute(response, "job-pages-per-set-supported",
            IPP_TAG_BOOLEAN)) != NULL && ippGetBoolean(attrptr, 0)) {
        capabilities->jobPagesPerSetSupported = 1;
    }

    debuglist_printerCapabilities(capabilities);
}

// Used in parse_printerUris
#define MAX_URIS 10
typedef struct {
    const char *uri;
    int valid;
} parsed_uri_t;

static void parse_printerUris(ipp_t *response, printer_capabilities_t *capabilities) {
    ipp_attribute_t *attrptr;
    int i;
    parsed_uri_t uris[MAX_URIS] = {0};

    if ((attrptr = ippFindAttribute(response, "printer-uri-supported", IPP_TAG_URI)) != NULL) {
        for (i = 0; i < MIN(ippGetCount(attrptr), MAX_URIS); i++) {
            uris[i].uri = ippGetString(attrptr, i, NULL);
            uris[i].valid = true;
        }
    }

    // If authentication is required by any URI, mark it invalid
    if ((attrptr = ippFindAttribute(response, "uri-authentication-supported", IPP_TAG_KEYWORD))
            != NULL) {
        for (i = 0; i < MIN(ippGetCount(attrptr), MAX_URIS); i++) {
            // Allow "none" and "requesting-user-name" only
            if (strcmp("none", ippGetString(attrptr, i, NULL)) != 0 &&
                    strcmp("requesting-user-name", ippGetString(attrptr, i, NULL)) != 0) {
                LOGD("parse_printerUris %s invalid because auth=%s", uris[i].uri,
                        ippGetString(attrptr, i, NULL));
                uris[i].valid = false;
            }
        }
    }

    // Find a valid URI and copy it into place.
    for (i = 0; i < MAX_URIS; i++) {
        // Copy if the URI is valid and we haven't yet discovered ipps
        if (uris[i].valid && strncmp(capabilities->printerUri, "ipps://", 7) != 0) {
            LOGD("parse_printerUris found %s", uris[i].uri);
            strlcpy(capabilities->printerUri, uris[i].uri, sizeof(capabilities->printerUri));
        }
    }
}

void debuglist_printerCapabilities(printer_capabilities_t *capabilities) {
    LOGD("printer make: %s", capabilities->make);
    LOGD("printer default media: %s", capabilities->mediaDefault);
    LOGD("canPrintPDF: %d", capabilities->canPrintPDF);
    LOGD("duplex: %d", capabilities->duplex);
    LOGD("canRotateDuplexBackPage: %d", capabilities->canRotateDuplexBackPage);
    LOGD("color: %d", capabilities->color);
    LOGD("canCopy: %d", capabilities->canCopy);
    LOGD("ippVersionMajor: %d", capabilities->ippVersionMajor);
    LOGD("ippVersionMinor: %d", capabilities->ippVersionMinor);
    LOGD("strip height: %d", capabilities->stripHeight);
    LOGD("faceDownTray: %d", capabilities->faceDownTray);
    for (int i = 0; i < capabilities->print_scalings_supported_count; i++) {
        LOGD("print-scaling-supported (%d): %s", i, capabilities->print_scalings_supported[i]);
    }
    LOGD("print_scaling_default: %s",capabilities->print_scaling_default);
    LOGD("jobPagesPerSetSupported: %d", capabilities->jobPagesPerSetSupported);
}

void debuglist_printerStatus(printer_state_dyn_t *printer_state_dyn) {
    const char *decoded = "unknown";
    if (printer_state_dyn->printer_status == PRINT_STATUS_INITIALIZING) {
        decoded = "Initializing";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_SHUTTING_DOWN) {
        decoded = "Shutting Down";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_UNABLE_TO_CONNECT) {
        decoded = "Unable To Connect";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_UNKNOWN) {
        decoded = "Unknown";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_OFFLINE) {
        decoded = "Offline";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_IDLE) {
        decoded = "Idle";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_PRINTING) {
        decoded = "Printing";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_OUT_OF_PAPER) {
        decoded = "Out Of Paper";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_OUT_OF_INK) {
        decoded = "Out Of Ink";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_JAMMED) {
        decoded = "Jammed";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_DOOR_OPEN) {
        decoded = "Door Open";
    } else if (printer_state_dyn->printer_status == PRINT_STATUS_SVC_REQUEST) {
        decoded = "Service Request";
    }
    LOGD("printer status: %d (%s)", printer_state_dyn->printer_status, decoded);

    int idx = 0;
    for (idx = 0; idx < (PRINT_STATUS_MAX_STATE + 1); idx++) {
        if (PRINT_STATUS_MAX_STATE != printer_state_dyn->printer_reasons[idx]) {
            LOGD("printer_reasons (%d): %d", idx, printer_state_dyn->printer_reasons[idx]);
        }
    }
}

/*
 * Handle server certificate information.
 */
static int ipp_server_cert_cb(http_t *http, void *tls, cups_array_t *certs, void *user_data) {
    wprint_connect_info_t *connect_info = (wprint_connect_info_t *)user_data;
    int error = 0;
    if (connect_info->validate_certificate) {
        http_credential_t *credential = cupsArrayFirst(certs);
        if (credential) {
            LOGD("ipp_server_cert_cb: validate_certificate (len=%zu)", credential->datalen);
            error = connect_info->validate_certificate(connect_info, credential->data,
                                                       credential->datalen);
        }
    }
    return error;
}

http_t *ipp_cups_connect(const wprint_connect_info_t *connect_info, char *printer_uri,
        unsigned int uriLength) {
    const char *uri_path;
    http_t *curl_http = NULL;

    cupsSetServerCertCB(ipp_server_cert_cb, (void *)connect_info);

    if ((connect_info->uri_path == NULL) || (strlen(connect_info->uri_path) == 0)) {
        uri_path = DEFAULT_IPP_URI_RESOURCE;
    } else {
        uri_path = connect_info->uri_path;
    }

    int ippPortNumber = ((connect_info->port_num == IPP_PORT) ? ippPort() : connect_info->port_num);

    if (strstr(connect_info->uri_scheme,IPPS_PREFIX) != NULL) {
        curl_http = httpConnect2(connect_info->printer_addr, ippPortNumber, NULL, AF_UNSPEC,
                HTTP_ENCRYPTION_ALWAYS, 1, HTTP_TIMEOUT_MILLIS, NULL);

        // If ALWAYS doesn't work, fall back to REQUIRED
        if (curl_http == NULL) {
            curl_http = httpConnect2(connect_info->printer_addr, ippPortNumber, NULL, AF_UNSPEC,
                    HTTP_ENCRYPTION_REQUIRED, 1, HTTP_TIMEOUT_MILLIS, NULL);
        }
    } else {
        curl_http = httpConnect2(connect_info->printer_addr, ippPortNumber, NULL, AF_UNSPEC,
                HTTP_ENCRYPTION_IF_REQUESTED, 1, HTTP_TIMEOUT_MILLIS, NULL);
    }

    httpSetTimeout(curl_http, (double)connect_info->timeout / 1000, NULL, 0);
    httpAssembleURIf(HTTP_URI_CODING_ALL, printer_uri, uriLength, connect_info->uri_scheme, NULL,
            connect_info->printer_addr, ippPortNumber, "%s", uri_path);

    if (curl_http == NULL) {
        LOGD("ipp_cups_connect failed addr=%s port=%d", connect_info->printer_addr, ippPortNumber);
    }

    cupsSetServerCertCB(NULL, NULL);
    return curl_http;
}

/*
 * Send a request using cupsSendRequest(). Loop if we get NULL or CONTINUE. Does not delete
 * the request.
 */
static ipp_t *ippSendRequest(http_t *http, ipp_t *request, char *resource) {
    ipp_t *response = NULL;
    http_status_t result;
    bool retry;

    do {
        retry = false;
        result = cupsSendRequest(http, request, resource, ippLength(request));
        if (result != HTTP_ERROR) {
            response = cupsGetResponse(http, resource);
            result = httpGetStatus(http);
        }

        if (result == HTTP_CONTINUE && response == NULL) {
            // We need to retry when this happens.
            LOGD("ippSendRequest: (Continue with NULL response) Retry");
            retry = true;
        } else if (result == HTTP_ERROR || result >= HTTP_BAD_REQUEST) {
            break;
        }

        if (http->state != HTTP_WAITING) {
            httpFlush(http);
        }
    } while (retry);

    return response;
}

/*
 * Call ippDoCupsIORequest, repeating if a failure occurs based on failure conditions, and
 * returning the response (or NULL if it failed).
 *
 * Does not free the request, and the caller must call ippDelete to free any valid response.
 */
ipp_t *ipp_doCupsRequest(http_t *http, ipp_t *request, char *http_resource, char *printer_uri) {
    ipp_status_t ipp_status;
    ipp_t *response = NULL;
    int service_unavailable_retry_count = 0;
    int bad_request_retry_count = 0;
    int internal_error_retry_count = 0;
    ipp_version_state ipp_version_supported = IPP_VERSION_RESOLVED;

    // Fail if any of these parameters are NULL
    if (http == NULL || request == NULL || http_resource == NULL || printer_uri == NULL) {
        return NULL;
    }

    do {
        // Give up immediately if wprint is done.
        if (!wprintIsRunning()) return NULL;

        // This is a no-op until we hit the error IPP_VERSION_NOT_SUPPORTED and retry.
        if (set_ipp_version(request, printer_uri, http, ipp_version_supported) != 0) {
            // We tried to find the correct IPP version by doing a series of get attribute
            // requests but they all failed... we give up.
            LOGE("ipp_doCupsRequest: set_ipp_version!=0, version not set");
            break;
        }

        response = ippSendRequest(http, request, http_resource);
        if (response == NULL) {
            ipp_status = cupsLastError();
            if (ipp_status == IPP_INTERNAL_ERROR || ipp_status == HTTP_ERROR) {
                internal_error_retry_count++;
                if (internal_error_retry_count > IPP_INTERNAL_ERROR_MAX_RETRIES) {
                    break;
                }

                LOGE("ipp_doCupsRequest: %s %d received, retry %d of %d",
                        printer_uri, ipp_status, internal_error_retry_count,
                        IPP_INTERNAL_ERROR_MAX_RETRIES);
                continue;
            } else if (ipp_status == IPP_SERVICE_UNAVAILABLE) {
                service_unavailable_retry_count++;
                if (service_unavailable_retry_count > IPP_SERVICE_ERROR_MAX_RETRIES) {
                    break;
                }

                LOGE("ipp_doCupsRequest: %s IPP_SERVICE_UNAVAILABLE received, retrying %d of %d",
                        printer_uri, service_unavailable_retry_count,
                        IPP_SERVICE_ERROR_MAX_RETRIES);
                continue;
            } else if (ipp_status == IPP_BAD_REQUEST) {
                bad_request_retry_count++;
                if (bad_request_retry_count > IPP_BAD_REQUEST_MAX_RETRIES) {
                    break;
                }

                LOGD("ipp_doCupsRequest: %s IPP_BAD_REQUEST received. retry (%d) of (%d)",
                        printer_uri, bad_request_retry_count, IPP_BAD_REQUEST_MAX_RETRIES);
                continue;
            } else if (ipp_status == IPP_NOT_FOUND) {
                LOGE("ipp_doCupsRequest: %s IPP_NOT_FOUND received.", printer_uri);
                break;
            }
        } else {
            ipp_status = cupsLastError();
            if (ipp_status == IPP_BAD_REQUEST) {
                bad_request_retry_count++;
                LOGE("ipp_doCupsRequest: %s IPP_BAD_REQUEST received. retry (%d) of (%d)",
                        printer_uri, bad_request_retry_count, IPP_BAD_REQUEST_MAX_RETRIES);
                if (bad_request_retry_count > IPP_BAD_REQUEST_MAX_RETRIES) {
                    break;
                }

                ippDelete(response);
                response = NULL;
                continue;
            } else if (ipp_status == IPP_VERSION_NOT_SUPPORTED) {
                ipp_version_supported = IPP_VERSION_UNSUPPORTED;
                ippDelete(response);
                response = NULL;
                continue;
            }
        }
        break;
    } while (1);

    return response;
}

int getJobId(http_t *http,
              char *http_resource,
              char *printer_uri,        /* I - URI buffer */
              job_state_dyn_t *job_state_dyn,
              const char *requesting_user) {
    int job_id = -1;
    // Requested print job attributes
    static const char *jattrs[] = {"job-id"};
    ipp_t *request = NULL;  /* IPP request object */
    ipp_t *response = NULL; /* IPP response object */

    request = ippNewRequest(IPP_GET_JOBS);

    if (request != NULL) {
        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_URI, "printer-uri", NULL, printer_uri);
        ippAddBoolean(request, IPP_TAG_OPERATION, "my-jobs", 1);
        ippAddString(request, IPP_TAG_OPERATION, IPP_TAG_NAME, "requesting-user-name",
                     NULL, requesting_user);
        ippAddStrings(request, IPP_TAG_OPERATION, IPP_TAG_KEYWORD, "requested-attributes",
                      sizeof(jattrs) / sizeof(jattrs[0]), NULL, jattrs);

        if ((response = ipp_doCupsRequest(http, request, http_resource, printer_uri)) == NULL) {
            job_state_dyn->job_state = IPP_JOB_STATE_UNABLE_TO_CONNECT;
            job_state_dyn->job_state_reasons[0] = IPP_JOB_STATE_REASON_UNABLE_TO_CONNECT;
        } else {
            ipp_attribute_t *attr = ippFindAttribute(response, "job-id", IPP_TAG_INTEGER);
            if (attr != NULL) job_id = ippGetInteger(attr, 0);
        }
    }

    if (request != NULL) ippDelete(request);
    if (response != NULL) ippDelete(response);

    LOGD("getJobId() returning job-id: %d", job_id);
    return job_id;
}

int tryNextResourceExtension(char *printer_uri) {
    char scheme[1024];
    char username[1024];
    char host[1024];
    char resource[1024];
    int port;

    httpSeparateURI(0, printer_uri, scheme, 1024, username, 1024, host, 1024,
                    &port, resource, 1024);

    int index;
    for (index = 0; index < ARRAY_SIZE(resource_extensions_arr); index++) {
        if (strcmp(resource_extensions_arr[index], resource) == 0) {
            break;
        }
    }
    if (index >= (ARRAY_SIZE(resource_extensions_arr) - 1)) {
        return 0;
    } else {
        httpAssembleURIf(HTTP_URI_CODING_ALL, printer_uri, 1024, scheme, NULL,
                         host, port, "%s", resource_extensions_arr[index + 1]);
        LOGD("next resource %s", printer_uri);
        return 1;
    }
}
