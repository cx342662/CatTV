import argparse
import hashlib
import json
import os
import plistlib
import struct
import zipfile
from pathlib import Path


def verify(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        plists = [name for name in names if name.startswith('Payload/') and name.count('/') == 2 and name.endswith('.app/Info.plist')]
        if len(plists) != 1:
            raise ValueError('IPA must contain exactly one Payload/*.app/Info.plist')
        plist = plistlib.loads(archive.read(plists[0]))
        root = plists[0].removesuffix('Info.plist')
        binary = archive.read(root + plist['CFBundleExecutable'])
        magic, cpu = struct.unpack_from('<II', binary)
        if magic != 0xFEEDFACF or cpu != 0x0100000C:
            raise ValueError('Expected a 64-bit ARM device Mach-O executable')
        commands = struct.unpack_from('<I', binary, 16)[0]
        offset = 32
        platform = None
        for _ in range(commands):
            command, size = struct.unpack_from('<II', binary, offset)
            if size < 8 or offset + size > len(binary):
                raise ValueError('Invalid Mach-O command')
            if command == 0x32:
                platform = struct.unpack_from('<I', binary, offset + 8)[0]
            offset += size
        if platform != 2:
            raise ValueError('Mach-O is not built for the iOS device platform')
        unwanted = [name for name in names if name.startswith(root) and ('.xctest/' in name or '/fixtures/' in name or name.endswith('/test.mp4') or name.endswith('/config.json'))]
        if unwanted:
            raise ValueError('Test fixtures were packaged: ' + ', '.join(unwanted))
        if plist.get('CFBundleIdentifier') != 'com.cattv.ios':
            raise ValueError('Unexpected bundle identifier')
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        return {'sha256': digest, 'sourceCommit': os.environ.get('GITHUB_SHA'), 'bundleIdentifier': plist['CFBundleIdentifier'], 'version': plist['CFBundleShortVersionString'], 'build': plist['CFBundleVersion'], 'minimumOSVersion': plist.get('MinimumOSVersion'), 'architecture': 'arm64', 'platform': 'iOS device', 'testFixturesBundled': False, 'installationVerified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('ipa', type=Path)
    parser.add_argument('--output', type=Path)
    arguments = parser.parse_args()
    result = verify(arguments.ipa)
    report = json.dumps(result, indent=2) + '\n'
    if arguments.output:
        arguments.output.write_text(report, encoding='utf-8')
    print(report, end='')
