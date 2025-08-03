#!/usr/bin/env python3
#"""Transform errors to expected failures."""

from collections.abc import Sequence
import os
import re
import sys

class TransformExpectedFailures:
    """Automate transforming error log to expected failure."""
    def transform(self, errors: str, expected_types: str | None = None) -> None:
        """Transform erro log to expected failure format.
        
        It split the error log into multiple lines each line contain one error
        based on the expexted types and remove the Error part. then remove the
        white space after ':' and strip the error line.
        """
        if expected_types == None:
            expected_types = "extra_field|missing_method|extra_class|missing_annotation"

        result_list = self._transform(self._extract_error_logs(errors, expected_types))
        print(",\n".join(result_list))

    def _extract_error_logs(self, errors: str, expected_types: str) -> list[str]:
        return re.findall(
                    f"\\b(?:{expected_types})\\b:.+?(?=Error)",
                    errors,
                )

    def _transform(self, error_logs : list[str]) -> list[str]:
        result = [self._transform_single_message(error_log) for error_log in error_logs]
        result = sorted(set(result))
        return result

    def _transform_single_message(self, error_log: str) -> str:
        error_log = error_log.strip()
        index = error_log.find(":") + 1
        return '"' + error_log[:index] + error_log[index + 1 :] + '"'


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] == "":
        print('The errors report is required')
        sys.exit(1)
    if len(sys.argv) > 3:
        print('Invalid number of arguments, please specify the path to the file and optionally the expected failures types annotation separated by |')
        sys.exit(1)
    if len(sys.argv) == 2:
        TransformExpectedFailures().transform(sys.argv[1])
    else:
        TransformExpectedFailures().transform(sys.argv[1], sys.argv[2])
