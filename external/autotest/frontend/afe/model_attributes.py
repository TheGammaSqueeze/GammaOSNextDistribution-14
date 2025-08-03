import common
from autotest_lib.client.common_lib import autotest_enum


# common enums for Job attributes
RebootBefore = autotest_enum.AutotestEnum('Never', 'If dirty', 'Always')
RebootAfter = autotest_enum.AutotestEnum('Never', 'If all tests passed',
                                         'Always')
