#!/usr/bin/python3
import os
import xml.etree.ElementTree as ElementTree

from urllib import request

# See (https://developer.android.com/studio/build/dependencies#gmaven-access)
GMAVEN_BASE_URL = 'https://maven.google.com'

URL_SEP = '/'

class GMavenArtifact(object):
    # A map from group:library to the latest available version
    key_versions_map = {}

    def __init__(self, artifact_glob):
        try:
            (group, library, version, ext) = artifact_glob.split(':')
        except ValueError:
            raise ValueError(f'Error in {artifact_glob} expected: group:library:version:ext')

        if not group or not library or not version or not ext:
            raise ValueError(f'Error in {artifact_glob} expected: group:library:version:ext')

        self.group = group
        self.group_path = group.replace('.', '/')
        self.library = library
        self.key = f'{group}:{library}'
        self.version = version
        self.ext = ext

    def get_pom_file_url(self):
        return maven_path_for_artifact(
            GMAVEN_BASE_URL, self.group_path, self.library, self.version, 'pom', URL_SEP)

    def get_artifact_url(self):
        return maven_path_for_artifact(
            GMAVEN_BASE_URL, self.group_path, self.library, self.version, self.ext, URL_SEP)

    def get_latest_version(self):
        latest_version = GMavenArtifact.key_versions_map[self.key] \
            if self.key in GMavenArtifact.key_versions_map else None

        if not latest_version:
            print(f'Fetching latest version for {self.key}')
            group_index_url = f'{GMAVEN_BASE_URL}/{self.group_path}/group-index.xml'
            tree = ElementTree.parse(request.urlopen(group_index_url))
            root = tree.getroot()
            libraries = root.findall('./*[@versions]')
            for library in libraries:
                key = f'{root.tag}:{library.tag}'
                GMavenArtifact.key_versions_map[key] = library.get('versions').split(',')[-1]
            latest_version = GMavenArtifact.key_versions_map[self.key]
        return latest_version


class MavenLibraryInfo:
    def __init__(self, key, group_id, artifact_id, version, artifact_dir, repo_dir, file):
        self.key = key
        self.group_id = group_id
        self.artifact_id = artifact_id
        self.version = version
        self.dir = artifact_dir
        self.repo_dir = repo_dir
        self.file = file


def maven_path_for_artifact(base, group, library, version, ext, pathsep=os.pathsep):
    return pathsep.join([base, group, library, version, f'{library}-{version}.{ext}'])
