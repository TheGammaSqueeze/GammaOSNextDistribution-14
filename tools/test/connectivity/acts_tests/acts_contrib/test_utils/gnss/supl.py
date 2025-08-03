import os
import tempfile
from xml.etree import ElementTree


def set_supl_over_wifi_state(ad, turn_on):
    """Enable / Disable supl over wifi features

    Modify the gps xml file: /vendor/etc/gnss/gps.xml
    Args:
        ad: AndroidDevice object
        turn_on: (bool) True -> enable / False -> disable
    """
    ad.adb.remount()
    folder = tempfile.mkdtemp()
    xml_path_on_host = os.path.join(folder, "gps.xml")
    xml_path_on_device = "/vendor/etc/gnss/gps.xml"
    ad.pull_files(xml_path_on_device, xml_path_on_host)

    # register namespance to aviod adding ns0 into xml attributes
    ElementTree.register_namespace("", "http://www.glpals.com/")
    xml_tree = ElementTree.parse(xml_path_on_host)
    root = xml_tree.getroot()
    for node in root:
        if "hal" in node.tag:
            if turn_on:
                _enable_supl_over_wifi(ad, node)
            else:
                _disable_supl_over_wifi(ad, node)
    xml_tree.write(xml_path_on_host, xml_declaration=True, encoding="utf-8", method="xml")
    ad.push_system_file(xml_path_on_host, xml_path_on_device)


def _enable_supl_over_wifi(ad, node):
    """Enable supl over wifi
    Detail setting:
        <hal
            SuplDummyCellInfo="true"
            SuplUseApn="false"
            SuplUseApnNI="true"
            SuplUseFwCellInfo="false"
        />
    Args:
        ad: AndroidDevice object
        node: ElementTree node
    """
    ad.log.info("Enable SUPL over wifi")
    attributes = {"SuplDummyCellInfo": "true", "SuplUseApn": "false", "SuplUseApnNI": "true",
                  "SuplUseFwCellInfo": "false"}
    for key, value in attributes.items():
        node.set(key, value)


def _disable_supl_over_wifi(ad, node):
    """Disable supl over wifi
    Detail setting:
        <hal
            SuplUseApn="true"
        />
    Remove following setting
        SuplDummyCellInfo="true"
        SuplUseApnNI="true"
        SuplUseFwCellInfo="false"
    Args:
        ad: AndroidDevice object
        node: ElementTree node
    """
    ad.log.info("Disable SUPL over wifi")
    for attri in ["SuplDummyCellInfo", "SuplUseApnNI", "SuplUseFwCellInfo"]:
        node.attrib.pop(attri, None)
    node.set("SuplUseApn", "true")
