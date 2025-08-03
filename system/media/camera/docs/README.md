# Camera Metadata XML
## Introduction
This is a set of scripts to manipulate the camera metadata in an XML form.

## Generated Files
Many files can be generated from XML, such as the documentation (html/pdf),
C code, Java code, and even XML itself (as a round-trip validity check).

## Dependencies
* Python 2.7.x+
* Beautiful Soup 4+ - HTML/XML parser, used to parse `metadata_definitions.xml`
* Mako 0.7+         - Template engine, needed to do file generation.
* Markdown 2.1+     - Plain text to HTML converter, for docs formatting.
* Tidy              - Cleans up the XML/HTML files.
* XML Lint          - Validates XML against XSD schema.

## Quick Setup (Debian Rodete):
NOTE: Debian (and most Linux distros) no longer package Python 2.
      Python 3 dependencies are listed below.
```
sudo apt install python3-mako \
                 python3-bs4 \
                 python3-markdown \
                 tidy \
                 libxml2-utils
```

## Quick Usage:
1. Modify or add to `metadata_definition.xml`
2. Execute `metadata-generate`
3. Run `m ds-docs` to make sure the javadoc is correctly generated
4. Commit and Upload the repos listed at the end of `metadata-generate`
