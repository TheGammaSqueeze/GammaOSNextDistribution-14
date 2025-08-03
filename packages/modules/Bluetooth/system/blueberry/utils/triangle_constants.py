"""Constants for Triangle testing."""

# Internal import

FLAG_TYPE = phenotype_utils.FlagTypes

# Waiting time to trigger connection switching.
WAITING_TIME_SEC = 60

NEARBY_PACKAGE = 'com.google.android.gms.nearby'
WEARABLE_PACKAGE = 'com.google.android.gms.wearable'

SET_SCREEN_OFF_TIMEOUT_HALF_HOUR = (
    'settings put system screen_off_timeout 1800000')

# Phenotype flags
CONNECTION_SWITCHING_FLAGS = (
    {
        'name': 'FastPairFeature__enable_triangle_audio_switch',
        'type': FLAG_TYPE.BOOLEAN,
        'value': 'true'
    },
    {
        'name': 'FastPairFeature__enable_wearable_service',
        'type': FLAG_TYPE.BOOLEAN,
        'value': 'true'
    },
    {
        'name': 'fast_pair_enable_api_for_wear_os',
        'type': FLAG_TYPE.BOOLEAN,
        'value': 'true'
    },
    {
        'name': 'FastPairFeature__enable_task_scheduler_service',
        'type': FLAG_TYPE.BOOLEAN,
        'value': 'true'
    },
    {
        'name': 'fast_pair_manual_connect_affect_duration_millis',
        'type': FLAG_TYPE.LONG,
        'value': '60000'
    }
)

PHONE_PHENOTYPE_FLAGS = {
    NEARBY_PACKAGE:
        (
            {
                'name': 'fast_pair_half_sheet_wear_os',
                'type': FLAG_TYPE.BOOLEAN,
                'value': 'true'
            },
            {
                'name': 'default_debug_mode_enabled',
                'type': FLAG_TYPE.BOOLEAN,
                'value': 'true'
            }
        ) + CONNECTION_SWITCHING_FLAGS
    ,
    WEARABLE_PACKAGE:
        (
            {
                'name': 'enable_fast_pair_account_key_processing_for_phone',
                'type': FLAG_TYPE.BOOLEAN,
                'value': 'true'
            },
        )
}

WATCH_PHENOTYPE_FLAGS = {
    NEARBY_PACKAGE: (
        {
            'name': 'DiscovererFeature__support_wear_os',
            'type': FLAG_TYPE.BOOLEAN,
            'value': 'true'
        },
        {
            'name': 'fast_pair_enable_wear_os_fastpair_seeker',
            'type': FLAG_TYPE.BOOLEAN,
            'value': 'true'
        },
        {
            'name': 'default_device_notification_enabled',
            'type': FLAG_TYPE.BOOLEAN,
            'value': 'true'
        },
        {
            'name': 'fast_pair_enable_wearable_peripheral_api',
            'type': FLAG_TYPE.BOOLEAN,
            'value': 'true'
        },
        {
            'name': 'fast_pair_footprints_access_strategy',
            'type': FLAG_TYPE.STRING,
            'value': 'geller'
        }
        ) + CONNECTION_SWITCHING_FLAGS
}
