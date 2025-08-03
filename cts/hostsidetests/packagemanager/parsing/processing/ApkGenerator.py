"""
Parses @AndroidManifestXml annotations to pre-generate APKs for tests

Assumes a Kotlin triple quoted string literal which is prepended by a custom annotation. See the
AndroidManifestXml annotation for more information.
"""

import subprocess
import sys
import os

ANNOTATION = "@AndroidManifestXml"
TRIPLE_QUOTE = "\"\"\""
PACKAGE_NAME_PREFIX = "android.content.pm.parsing.cts.generated"
GENERATED_APK_PACKAGE_NAMES_FILE = "GeneratedApkPackageNames.txt"
ANDROID_NAMESPACE = "xmlns:android=\"http://schemas.android.com/apk/res/android\""

def java_string_hashcode(string):
    """
        Simulates Java's hashCode so that APKs can be looked up at runtime by using the manifest
        hashCode as the file name.
    """
    hash = 0
    for char in string:
        hash = int((((31 * hash + ord(char)) ^ 0x80000000) & 0xFFFFFFFF) - 0x80000000)
    return str(abs(hash))

aapt2 = sys.argv[1]
frameworkRes = sys.argv[2]
apkSigner = sys.argv[3]
keyStore = sys.argv[4]
genDir = sys.argv[5]
inputFiles = sys.argv[6:]

tempDir = f"{genDir}/temp"
outDir = f"{genDir}/out"

os.makedirs(tempDir, exist_ok=True)
os.makedirs(outDir, exist_ok=True)

packageNamesOutput = open(f"{outDir}/{GENERATED_APK_PACKAGE_NAMES_FILE}", "w")

usedHashCodes = {}

for inputFile in inputFiles:
    text = open(inputFile, "r").read()

    annotationIndex = 0
    while True:
        try:
            annotationIndex = text.index(ANNOTATION, annotationIndex)
            if annotationIndex < 0:
                break
        except:
            break

        annotationIndex += len(ANNOTATION)
        startIndex = text.index(TRIPLE_QUOTE, annotationIndex)
        if startIndex < 0:
            continue

        endIndex = text.index(TRIPLE_QUOTE, startIndex + len(TRIPLE_QUOTE))
        if endIndex < 0:
            continue

        string = text[startIndex + len(TRIPLE_QUOTE): endIndex]
        hashCode = java_string_hashcode(string)

        if hashCode in usedHashCodes:
            if usedHashCodes[hashCode] != string:
                sys.exit(f"Manifest XML with hash code {hashCode} already used: {string}")
        usedHashCodes[hashCode] = string

        if ANDROID_NAMESPACE not in string:
            string = string.replace("<manifest", f"<manifest {ANDROID_NAMESPACE}\n", 1)

        packageName = PACKAGE_NAME_PREFIX + hashCode
        string = string.replace(">", f"\npackage=\"{packageName}\"\n>", 1)
        string = string.replace("<application", "<application\nandroid:hasCode=\"false\"\n")

        outputPath = f"{tempDir}/{hashCode}.xml"
        outputFile = open(outputPath, "w")
        outputFile.write(string)
        outputFile.close()

        packageNamesOutput.write(packageName)
        packageNamesOutput.write("\n")

        apkPath = f"{outDir}/{hashCode}.apk"

        subprocess.run([
            aapt2, "link",
            "-I", frameworkRes,
            "--manifest", outputPath,
            "--warn-manifest-validation",
            "--rename-manifest-package", packageName,
            "-o", apkPath
        ], check = True)

        subprocess.run([
            apkSigner, "sign",
            "--ks", keyStore,
            "--ks-pass", "pass:password",
            apkPath
        ], check = True)

        # apksigner will generate an idsig file, but it's not useful for the test, so get rid of it
        idsigPath = f"{outDir}/{hashCode}.idsig"
        if os.path.isfile(idsigPath):
            os.remove(idsigPath)
