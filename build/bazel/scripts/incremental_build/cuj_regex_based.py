import re
import random
from pathlib import Path
from typing import Callable, Iterable

from cuj import CujGroup, CujStep, de_src


class RegexModify(CujGroup):
    """
    A pair of CujSteps, where the fist modifies the file and the
    second reverts it
    Attributes:
        file: the file to be edited and reverted
        pattern: the string that will be replaced, only the FIRST occurrence will be replaced
        replace: a function that generates the replacement string
        modify_type: types of modification
    """

    def __init__(
        self, file: Path, pattern: str, replacer: Callable[[], str], modify_type: str
    ):
        super().__init__(f"{modify_type} {de_src(file)}")
        if not file.exists():
            raise RuntimeError(f"{file} does not exist")
        self.file = file
        self.pattern = pattern
        self.replacer = replacer

    def get_steps(self) -> Iterable[CujStep]:
        original_text: str

        def modify():
            nonlocal original_text
            original_text = self.file.read_text()
            modified_text = re.sub(
                self.pattern,
                self.replacer(),
                original_text,
                count=1,
                flags=re.MULTILINE,
            )
            self.file.write_text(modified_text)

        def revert():
            self.file.write_text(original_text)

        return [CujStep("", modify), CujStep("revert", revert)]


def modify_private_method(file: Path) -> CujGroup:
    pattern = r"(private static boolean.*{)"

    def replacement():
        return r'\1 Log.d("Placeholder", "Placeholder{}");'.format(
            random.randint(0, 10000000)
        )

    modify_type = "modify_private_method"
    return RegexModify(file, pattern, replacement, modify_type)


def add_private_field(file: Path) -> CujGroup:
    class_name = file.name.removesuffix('.java')
    pattern = fr"(\bclass {class_name} [^{{]*{{)"

    def replacement():
        return f"\\1\nprivate static final int FOO = {random.randint(0, 10_000_000)};\n"

    modify_type = "add_private_field"
    return RegexModify(file, pattern, replacement, modify_type)


def add_public_api(file: Path) -> CujGroup:
    class_name = file.name.removesuffix('.java')
    pattern = fr"(\bclass {class_name} [^{{]*{{)"

    def replacement():
        return f"\\1\n@android.annotation.SuppressLint(\"UnflaggedApi\")\npublic static final int BAZ = {random.randint(0, 10_000_000)};\n"

    modify_type = "add_public_api"
    return RegexModify(file, pattern, replacement, modify_type)


def modify_resource(file: Path) -> CujGroup:
    pattern = r">0<"

    def replacement():
        return r">" + str(random.randint(0, 10000000)) + r"<"

    modify_type = "modify_resource"
    return RegexModify(file, pattern, replacement, modify_type)


def add_resource(file: Path) -> CujGroup:
    pattern = r"</resources>"

    def replacement():
        return (
            r'    <integer name="foo">'
            + str(random.randint(0, 10000000))
            + r"</integer>\n</resources>"
        )

    modify_type = "add_resource"
    return RegexModify(file, pattern, replacement, modify_type)
