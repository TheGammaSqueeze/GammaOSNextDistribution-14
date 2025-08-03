"""Exception classes for qemu runner."""


class RunnerError(Exception):
    """Contains all kinds of errors .run() will intentionally throw."""


class RunnerGenericError(RunnerError):
    """Generic runner error message."""

    def __init__(self, msg):
        super(RunnerGenericError, self).__init__()
        self.msg = msg

    def __str__(self):
        return "Runner failed: %s" % self.msg


class ConfigError(RunnerError):
    """Invalid configuration."""

    def __init__(self, msg):
        super(ConfigError, self).__init__()
        self.msg = msg

    def __str__(self):
        return "Invalid configuration: %s" % self.msg


class AdbFailure(RunnerError):
    """An adb invocation failed."""

    def __init__(self, adb_args, code):
        super(AdbFailure, self).__init__(self)
        self.adb_args = adb_args
        self.code = code

    def __str__(self):
        return "'adb %s' failed with %d" % (" ".join(self.adb_args), self.code)


class Timeout(RunnerError):
    """A step timed out."""

    def __init__(self, step, timeout):
        super(Timeout, self).__init__(self)
        self.step = step
        self.timeout = timeout

    def __str__(self):
        return "%s timed out (%d s)" % (self.step, self.timeout)
