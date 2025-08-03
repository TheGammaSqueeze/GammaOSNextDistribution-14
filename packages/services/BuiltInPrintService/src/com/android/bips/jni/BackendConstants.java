/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2014-2106 Mopria Alliance, Inc.
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
package com.android.bips.jni;

import android.view.Gravity;

public class BackendConstants {
    public static final String RESOLUTION_300_DPI = "resolution-300-dpi";
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String WPRINT_APPLICATION_ID = "Android";
    public static final String WPRINT_LIBRARY_PREFIX = "wfds";

    public static final int STATUS_OK = 0;

    public static final String PRINT_DOCUMENT_CATEGORY__DOCUMENT = "Doc";
    public static final String PRINT_DOCUMENT_CATEGORY__PHOTO = "Photo";

    public static final String ALIGNMENT = "alignment";

    /** Center horizontally based on the orientation */
    public static final int ALIGN_CENTER_HORIZONTAL_ON_ORIENTATION =
            Gravity.HORIZONTAL_GRAVITY_MASK;

    public static final int ALIGN_CENTER_HORIZONTAL = Gravity.CENTER_HORIZONTAL;
    public static final int ALIGN_CENTER_VERTICIAL = Gravity.CENTER_VERTICAL;

    /** Center horizontally & vertically */
    public static final int ALIGN_CENTER = Gravity.CENTER;

    public static final String JOB_STATE_QUEUED = "print-job-queued";
    public static final String JOB_STATE_RUNNING = "print-job-running";
    public static final String JOB_STATE_BLOCKED = "print-job-blocked";
    public static final String JOB_STATE_DONE = "print-job-complete";
    public static final String JOB_STATE_OTHER = "print-job-unknown";

    public static final String JOB_DONE_OK = "job-success";
    public static final String JOB_DONE_ERROR = "job-failed";
    public static final String JOB_DONE_CANCELLED = "job-cancelled";
    public static final String JOB_DONE_CORRUPT = "job-corrupt";
    public static final String JOB_DONE_OTHER = "job-result-unknown";
    public static final String JOB_DONE_AUTHENTICATION_CANCELED = "job-authentication-canceled";
    public static final String JOB_DONE_SIDES_UNSUPPORTED = "job-sides-unsupported";
    public static final String JOB_DONE_BAD_CERTIFICATE = "bad-certificate";
    public static final String JOB_DONE_ACCOUNT_INFO_NEEDED = "client-error-account-info-needed";
    public static final String JOB_DONE_ACCOUNT_CLOSED = "client-error-account-closed";
    public static final String JOB_DONE_ACCOUNT_LIMIT_REACHED =
            "client-error-account-limit-reached";
    public static final String JOB_DONE_AUTHORIZATION_FAILED =
            "client-error-account-authorization-failed";

    public static final String BLOCKED_REASON__OFFLINE = "device-offline";
    public static final String BLOCKED_REASON__BUSY = "device-busy";
    public static final String BLOCKED_REASON__CANCELLED = "print-job-cancelled";
    public static final String BLOCKED_REASON__OUT_OF_PAPER = "input-media-supply-empty";
    public static final String BLOCKED_REASON__OUT_OF_INK = "marker-ink-empty";
    public static final String BLOCKED_REASON__OUT_OF_TONER = "marker-toner-empty";
    public static final String BLOCKED_REASON__JAMMED = "jam";
    public static final String BLOCKED_REASON__DOOR_OPEN = "cover-open";
    public static final String BLOCKED_REASON__SERVICE_REQUEST = "service-request";
    public static final String BLOCKED_REASON__LOW_ON_INK = "marker-ink-almost-empty";
    public static final String BLOCKED_REASON__LOW_ON_TONER = "marker-toner-almost-empty";
    public static final String BLOCKED_REASON__REALLY_LOW_ON_INK = "marker-ink-really-low";
    public static final String BLOCKED_REASON__PAUSED = "paused";
    public static final String BLOCKED_REASON__INPUT_CANNOT_FEED_SIZE_SELECTED =
            "input-cannot-feed-size-selected";
    public static final String BLOCKED_REASON__INTERLOCK_ERROR = "interlock";
    public static final String BLOCKED_REASON__ALERT_REMOVAL_OF_BINARY_CHANGE_ENTRY =
            "alert-removal-of-binary-change-entry";
    public static final String BLOCKED_REASON__CONFIGURATION_CHANGED = "configuration-change";
    public static final String BLOCKED_REASON__CONNECTING_TO_DEVICE = "connecting-to-device";
    public static final String BLOCKED_REASON__DEACTIVATED = "deactivated";
    public static final String BLOCKED_REASON__DEVELOPER_ERROR = "developer";
    public static final String BLOCKED_REASON__HOLD_NEW_JOBS = "hold-new-jobs";
    public static final String BLOCKED_REASON__OPC_LIFE_OVER = "opc-life-over";
    public static final String BLOCKED_REASON__TIMED_OUT = "timed-out";
    public static final String BLOCKED_REASON__STOPPED = "stopped";
    public static final String BLOCKED_REASON__PRINTER_NMS_RESET = "printer-nms-reset";
    public static final String BLOCKED_REASON__PRINTER_MANUAL_RESET = "printer-manual-reset";
    public static final String BLOCKED_REASON__SPOOL_AREA_FULL = "spool-area-full";
    public static final String BLOCKED_REASON__SHUTDOWN = "shutdown";
    public static final String BLOCKED_REASON__OUTPUT_MAILBOX_SELECT_FAILURE =
            "output-mailbox-select-failure";
    public static final String BLOCKED_REASON__OUTPUT_TRAY_MISSING = "output-tray-missing";
    public static final String BLOCKED_REASON__BANDER_ERROR = "bander-error";
    public static final String BLOCKED_REASON__POWER_ERROR = "power-error";
    public static final String BLOCKED_REASON__BINDER_ERROR = "binder-error";
    public static final String BLOCKED_REASON__CLEANER_ERROR = "cleaner-error";
    public static final String BLOCKED_REASON__DIE_CUTTER_ERROR = "die-cutter-error";
    public static final String BLOCKED_REASON__FOLDER_ERROR = "folder-error";
    public static final String BLOCKED_REASON__IMPRINTER_ERROR = "imprinter-error";
    public static final String BLOCKED_REASON__INPUT_TRAY_ERROR = "input-tray-error";
    public static final String BLOCKED_REASON__INSERTER_ERROR = "inserter-error";
    public static final String BLOCKED_REASON__INTERPRETER_ERROR = "interpereter-error";
    public static final String BLOCKED_REASON__MAKE_ENVELOPE_ERROR = "make-envelope-error";
    public static final String BLOCKED_REASON__MARKER_ERROR = "maker-error";
    public static final String BLOCKED_REASON__MEDIA_ERROR = "media-error";
    public static final String BLOCKED_REASON__PERFORATER_ERROR = "perforater-error";
    public static final String BLOCKED_REASON__PUNCHER_ERROR = "puncher-error";
    public static final String BLOCKED_REASON__SEPARATION_CUTTER_ERROR = "sepration-cutter-error";
    public static final String BLOCKED_REASON__SHEET_ROTATOR_ERROR = "sheet-rotator-error";
    public static final String BLOCKED_REASON__SLITTER_ERROR = "slitter-error";
    public static final String BLOCKED_REASON__STACKER_ERROR = "stacker-error";
    public static final String BLOCKED_REASON__STAPLER_ERROR = "stapler-error";
    public static final String BLOCKED_REASON__STITCHER_ERROR = "stitcher-error";
    public static final String BLOCKED_REASON__SUBUNIT_ERROR = "subunit-error";
    public static final String BLOCKED_REASON__TRIMMER_ERROR = "trimmer-error";
    public static final String BLOCKED_REASON__WRAPPER_ERROR = "wrapper-error";
    public static final String BLOCKED_REASON__CLIENT_ERROR = "client-error";
    public static final String BLOCKED_REASON__SERVER_ERROR = "server-error";
    public static final String BLOCKED_REASON__WAITING = "waiting";
    public static final String BLOCKED_REASON__WAITING_FOR_PIN = "waiting-for-pin";
    public static final String BLOCKED_REASON__WAITING_FOR_FINISHINGS_CONFIRMATION =
            "waiting-for-finishings-confirmation";
    public static final String BLOCKED_REASON__WAITING_FOR_TWO_SIDED_CONFIRMATION =
            "waiting-for-two-sided-confirmation";
    public static final String BLOCKED_REASON__WAITING_FOR_AUTHENTICATION_CONFIRMATION =
            "waiting-for-authentication-confirmation";
    public static final String BLOCKED_REASON__WAITING_FOR_ACCOUNTING_CONFIRMATION =
            "waiting-for-accounting-confirmation";
    public static final String BLOCKED_REASON__WAITING_FOR_PRINT_POLICY_CONFIRMATION =
            "waiting-for-print-policy-confirmation";
    public static final String BLOCKED_REASON__BAD_CERTIFICATE = "bad-certificate";
    public static final String BLOCKED_REASON__UNKNOWN = "unknown";

    public static final String JOB_FAIL_REASON__ABORTED_BY_SYSTEM =
            "failed-aborted-by-system";
    public static final String JOB_FAIL_REASON__UNSUPPORTED_COMPRESSION =
            "failed-unsupported-compression";
    public static final String JOB_FAIL_REASON__COMPRESSION_ERROR = "failed-compression-error";
    public static final String JOB_FAIL_REASON__UNSUPPORTED_DOCUMENT_FORMAT =
            "failed-unsupported-document-format";
    public static final String JOB_FAIL_REASON__DOCUMENT_FORMAT_ERROR =
            "failed-document-format-error";
    public static final String JOB_FAIL_REASON__SERVICE_OFFLINE =
            "failed-service-off-line";
    public static final String JOB_FAIL_REASON__DOCUMENT_PASSWORD_ERROR =
            "failed-document-password-error";
    public static final String JOB_FAIL_REASON__DOCUMENT_PERMISSION_ERROR =
            "failed-document-permission-error";
    public static final String JOB_FAIL_REASON__DOCUMENT_SECURITY_ERROR =
            "failed-document-security-error";
    public static final String JOB_FAIL_REASON__DOCUMENT_UNPRINTABLE_ERROR =
            "failed-document-unprintable-error";
    public static final String JOB_FAIL_REASON__DOCUMENT_ACCESS_ERROR =
            "failed-document-access-error";
    public static final String JOB_FAIL_REASON__SUBMISSION_INTERRUPTED =
            "failed-submission-interrupted";

    public static final String EVENT_JOB_ATTEMPTED = "job_attempted";
    public static final String EVENT_JOB_COMPLETED = "job_completed";
    public static final String PARAM_JOB_ID = "job_id";
    public static final String PARAM_JOB_PAGES = "job_pages";
    public static final String PARAM_SOURCE_PATH = "source_path";
    public static final String PARAM_DATE_TIME = "date_time";
    public static final String PARAM_USER_ID = "user_id";
    public static final String PARAM_LOCATION = "location";
    public static final String PARAM_RESULT = "result";
    public static final String PARAM_ERROR_MESSAGES = "error_messages";
    public static final String PARAM_ELAPSED_TIME_ALL = "elapsed_time_all";
}
