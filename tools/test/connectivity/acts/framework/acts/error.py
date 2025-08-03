"""This class is where error information will be stored.
"""

from acts.signals import TestError


class ActsError(TestError):
    """Base Acts Error"""
    def __init__(self, *args, **kwargs):
        class_name = self.__class__.__name__
        self.error_doc = self.__class__.__doc__
        self.error_code = getattr(ActsErrorCode, class_name,
                                  ActsErrorCode.UNKNOWN)
        extras = dict(**kwargs, error_doc=self.error_doc,
                      error_code=self.error_code)
        details = args[0] if len(args) > 0 else ''
        super().__init__(details, extras)


class ActsErrorCode:
    # Framework Errors 0-999

    UNKNOWN = 0

    # This error code is used to implement unittests for this class.
    ActsError = 100
    AndroidDeviceError = 101

    # Controllers Errors 1000-3999

    Sl4aStartError = 1001
    Sl4aApiError = 1002
    Sl4aConnectionError = 1003
    Sl4aProtocolError = 1004
    Sl4aNotInstalledError = 1005
    Sl4aRpcTimeoutError = 1006

    # Util Errors 4000-9999

    FastbootError = 9000
    AdbError = 9001
    AdbCommandError = 9002
