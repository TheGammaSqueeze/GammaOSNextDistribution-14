#!/usr/bin/python3

import os


def has_match(line):
    """check if file current line matches py3_strs.

    Args:
            line: Current line to check.

    return:
            Boolean True or False.
    """
    py3_strs = [
            "#!/usr/bin/python3", "#!/usr/bin/env python3",
            "# lint as: python2, python3", "# lint as: python3"
    ]
    for match in py3_strs:
        if match in line:
            return True
    return False


def need_to_skip(fullname):
    """check if this file or folder that needs to be skipped from skip_strs.

    Args:
            fullname: Current file or folder name.

    return:
            Boolean True or False.
    """
    skip_strs = ["__init__.py", "autotest_lib", "common.py", "site_tests"]
    for match in skip_strs:
        if match in fullname:
            return True
    return False


def list_files_to_txt(upper_dir, file, suffix, nums_line_to_check):
    """List results to .txt file by check all target files.
    under the folder and subfolder.

    Args:
            upper_dir: The folder path need to check. The default.
                    is the ipper path of this script.
            file: output .txt file. The default is Python2MigrationTarget.txt.
            suffix: File extensions that need to be checked.
            nums_line_to_check: The number of rows to check.

    return:
            All file names and paths that meet the standard.
    """
    exts = suffix.split(" ")
    files = os.listdir(upper_dir)
    for filename in files:
        fullname = os.path.join(upper_dir, filename)
        if need_to_skip(fullname):
            continue
        if os.path.isdir(fullname):
            list_files_to_txt(fullname, file, suffix, nums_line_to_check)
        else:
            for ext in exts:
                if filename.endswith(ext):
                    filename = fullname
                    with open(filename, "r") as f:
                        for i in range(nums_line_to_check):
                            line = str(f.readline().strip()).lower()
                            if has_match(line):
                                tail = filename.split("third_party")[-1]
                                file.write("%s, 3\n" % tail)
                            else:
                                tail = filename.split("third_party")[-1]
                                file.write("%s, 2\n" % tail)
                            break


def main():
    """This is main function"""
    upper_dir = os.path.abspath(
            os.path.join(os.path.dirname("__file__"), os.path.pardir))
    outfile = "Python2MigrationTarget.txt"
    suffix = ".py"
    nums_line_to_check = 20
    file = open(outfile, "w")
    if not file:
        print("cannot open the file %s " % outfile)
    list_files_to_txt(upper_dir, file, suffix, nums_line_to_check)
    file.close()


if __name__ == "__main__":

    main()
