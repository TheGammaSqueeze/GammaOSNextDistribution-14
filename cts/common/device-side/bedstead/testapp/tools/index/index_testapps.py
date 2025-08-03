#  Copyright (C) 2021 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
from pathlib import Path
import subprocess
import queue
from src.library.main.proto.testapp_protos_pb2 import TestAppIndex, AndroidApp, UsesSdk,\
    Permission, Activity, IntentFilter, Service, Metadata, Receiver

ELEMENT = "E"
ATTRIBUTE = "A"

def main():
    args_parser = argparse.ArgumentParser(description='Generate index for test apps')
    args_parser.add_argument('--directory', help='Directory containing test apps')
    args_parser.add_argument('--aapt2', help='The path to aapt2')
    args = args_parser.parse_args()

    pathlist = Path(args.directory).rglob('*.apk')
    file_names = [p.name for p in pathlist]

    index = TestAppIndex()

    for file_name in file_names:
        aapt2_command = [
            args.aapt2, 'd', 'xmltree', '--file', 'AndroidManifest.xml', args.directory + "/" + file_name]
        index.apps.append(parse(str(subprocess.check_output(aapt2_command)), file_name))

    with open(args.directory + "/index.txt", "wb") as fd:
        fd.write(index.SerializeToString())

class XmlTreeLine:
    """ A single line taken from the aapt2 xmltree output. """

    def __init__(self, line, children):
        self.line = line
        self.children = children

    def __str__(self):
        return str(self.line) + "{" + ", ".join([str(s) for s in self.children]) + "}"

class Element:
    """ An XML element. """

    def __init__(self, name, attributes, children):
        self.name = name
        self.attributes = attributes
        self.children = children

    def __str__(self):
        return "Element(" + self.name +  " " + str(self.attributes) + ")"

def parse_lines(manifest_content):
    return parse_line(manifest_content, 0)[1]

def parse_line(manifest_content, ptr, incoming_indentation = -1):
    line = manifest_content[ptr]
    line_without_indentation = line.lstrip(" ")
    indentation_size = len(line) - len(line_without_indentation)

    if (indentation_size <= incoming_indentation):
        return ptr, None

    ptr += 1
    children = []

    while (ptr < len(manifest_content)):
        ptr, next_child = parse_line(manifest_content, ptr, indentation_size)
        if next_child:
            children.append(next_child)
        else:
            break

    return ptr, XmlTreeLine(line_without_indentation, children)

def augment(element):
    """ Convert a XmlTreeLine and descendants into an Element with descendants. """
    name = None
    if element.line:
        name = element.line[3:].split(" ", 1)[0]
    attributes = {}
    children = []

    children_to_process = queue.Queue()
    for c in element.children:
        children_to_process.put(c)

    while not children_to_process.empty():
        c = children_to_process.get()
        if c.line.startswith("E"):
            # Is an element
            children.append(augment(c))
        elif c.line.startswith("A"):
            # Is an attribute
            attribute_name = c.line[3:].split("=", 1)[0]
            if ":" in attribute_name:
                attribute_name = attribute_name.rsplit(":", 1)[1]
            attribute_name = attribute_name.split("(", 1)[0]
            attribute_value = c.line.split("=", 1)[1].split(" (Raw", 1)[0]
            if attribute_value[0] == '"':
                attribute_value = attribute_value[1:-1]
            attributes[attribute_name] = attribute_value

            # Children of the attribute are actually children of the element itself
            for child in c.children:
                children_to_process.put(child)
        else:
            raise Exception("Unknown line type for line: " + c.line)

    return Element(name, attributes, children)

def parse(manifest_content, file_name):
    manifest_content = manifest_content.split("\\n")
    # strip namespaces as not important for our uses
    # Also strip the last line which is a quotation mark because of the way it's imported
    manifest_content = [m for m in manifest_content if not "N: " in m][:-1]

    simple_root = parse_lines(manifest_content)
    root = augment(simple_root)

    android_app = AndroidApp()
    android_app.apk_name = file_name
    android_app.package_name = root.attributes["package"]
    android_app.sharedUserId = root.attributes.get("sharedUserId", "")

    parse_uses_sdk(root, android_app)
    parse_permissions(root, android_app)

    application_element = find_single_element(root.children, "application")
    android_app.test_only = application_element.attributes.get("testOnly", "false") == "true"
    android_app.label = application_element.attributes.get("label", "")
    android_app.cross_profile = application_element.attributes.get("crossProfile", "false") == "true"

    parse_activities(application_element, android_app)
    parse_services(application_element, android_app)
    parse_metadata(application_element, android_app)
    parse_receiver(application_element, android_app)

    return android_app

def parse_uses_sdk(root, android_app):
    uses_sdk_element = find_single_element(root.children, "uses-sdk")
    if uses_sdk_element:
        if "minSdkVersion" in uses_sdk_element.attributes:
            try:
                android_app.uses_sdk.minSdkVersion = int(uses_sdk_element.attributes["minSdkVersion"])
            except ValueError:
                pass
        if "maxSdkVersion" in uses_sdk_element.attributes:
            try:
                android_app.uses_sdk.maxSdkVersion = int(uses_sdk_element.attributes["maxSdkVersion"])
            except ValueError:
                pass
        if "targetSdkVersion" in uses_sdk_element.attributes:
            try:
                android_app.uses_sdk.targetSdkVersion = int(uses_sdk_element.attributes["targetSdkVersion"])
            except ValueError:
                pass

def parse_permissions(root, android_app):
    for permission_element in find_elements(root.children, "uses-permission"):
        permission = Permission()
        permission.name = permission_element.attributes["name"]
        android_app.permissions.append(permission)

def parse_activities(application_element, android_app):
    for activity_element in find_elements(application_element.children, "activity"):
        activity = Activity()

        activity.name = activity_element.attributes["name"]
        if activity.name.startswith("androidx"):
            continue # Special case: androidx adds non-logging activities

        activity.exported = activity_element.attributes.get("exported", "false") == "true"
        activity.permission = activity_element.attributes.get("permission", "")

        parse_intent_filters(activity_element, activity)
        android_app.activities.append(activity)

def parse_intent_filters(element, parent):
    for intent_filter_element in find_elements(element.children, "intent-filter"):
        intent_filter = IntentFilter()

        parse_intent_filter_actions(intent_filter_element, intent_filter)
        parse_intent_filter_category(intent_filter_element, intent_filter)
        parent.intent_filters.append(intent_filter)

def parse_intent_filter_actions(intent_filter_element, intent_filter):
    for action_element in find_elements(intent_filter_element.children, "action"):
        action = action_element.attributes["name"]
        intent_filter.actions.append(action)

def parse_intent_filter_category(intent_filter_element, intent_filter):
    for category_element in find_elements(intent_filter_element.children, "category"):
        category = category_element.attributes["name"]
        intent_filter.categories.append(category)

def parse_services(application_element, android_app):
    for service_element in find_elements(application_element.children, "service"):
        service = Service()
        service.name = service_element.attributes["name"]
        parse_intent_filters(service_element, service)
        android_app.services.append(service)

def parse_metadata(application_element, android_app):
    for meta_data_element in find_elements(application_element.children, "meta-data"):
        metadata = Metadata()
        metadata.name = meta_data_element.attributes["name"]

        if "value" in meta_data_element.attributes:
            # This forces every value into a string
            metadata.value = meta_data_element.attributes["value"]

        android_app.metadata.append(metadata)

def parse_receiver(application_element, android_app):
    for receiver_element in find_elements(application_element.children, "receiver"):
        parse_metadata(receiver_element, android_app)

        receiver = Receiver()
        receiver.name = receiver_element.attributes["name"]
        receiver.permission = receiver_element.attributes.get("permission", "")
        receiver.exported = receiver_element.attributes.get("exported", "false") == "true"
        parse_metadata(receiver_element, receiver)
        parse_intent_filters(receiver_element, receiver)
        android_app.receivers.append(receiver)

def find_single_element(element_collection, element_name):
    for e in element_collection:
        if e.name == element_name:
            return e

def find_elements(element_collection, element_name):
    return [e for e in element_collection if e.name == element_name]

if __name__ == "__main__":
    main()