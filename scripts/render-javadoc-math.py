#!/usr/bin/env python3
"""Add pinned, self-hosted MathJax SVG rendering to generated Javadoc and its JAR."""
import argparse
import hashlib
from pathlib import Path
import posixpath
import urllib.request
import zipfile

VERSION = '3.2.2'
ASSETS = {
    'tex-svg.js': 'd4295dc33744836935c1399feece5159577b34c5c8ffb9f1c6324cd82e03a882',
    'LICENSE': 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30',
}
CONFIG = r'''// Keep currency dollar signs literal. SVG output requires no external fonts.
window.MathJax = {
  tex: {inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]'], ['$$', '$$']]},
  svg: {fontCache: 'local'},
  options: {skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']}
};
'''
MARKER = '<!-- AlgoQuant mathematical notation -->'


def render(directory, archive):
    directory = Path(directory)
    if not (directory / 'index.html').is_file():
        raise ValueError('Expected a generated Javadoc directory containing index.html')
    assets = directory / 'mathjax'
    assets.mkdir(exist_ok=True)
    for name, digest in ASSETS.items():
        target = assets / name
        # Reuse verified downloads when rebuilding locally; never accept unverified bytes.
        data = target.read_bytes() if target.exists() else None
        if data is None or hashlib.sha256(data).hexdigest() != digest:
            remote = 'es5/tex-svg.js' if name == 'tex-svg.js' else name
            url = 'https://cdn.jsdelivr.net/npm/mathjax@%s/%s' % (VERSION, remote)
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('MathJax checksum mismatch: ' + name)
        target.write_bytes(data)
    (assets / 'config.js').write_text(CONFIG, encoding='utf-8')
    count = 0
    for page in sorted(directory.rglob('*.html')):
        text = page.read_text(encoding='utf-8')
        if MARKER in text:
            continue
        if '</head>' not in text:
            continue
        relative = posixpath.relpath('mathjax', page.relative_to(directory).parent.as_posix())
        scripts = ('%s\n<script defer src="%s/config.js"></script>\n'
                   '<script defer src="%s/tex-svg.js"></script>\n') % (MARKER, relative, relative)
        page.write_text(text.replace('</head>', scripts + '</head>', 1), encoding='utf-8')
        count += 1
    # Rebuild rather than append ZIP entries, preserving Maven's metadata and avoiding duplicates.
    archive = Path(archive)
    temporary = archive.with_name(archive.name + '.tmp')
    if not archive.is_file():
        raise ValueError('Expected Maven-generated Javadoc JAR: ' + str(archive))
    with zipfile.ZipFile(archive) as original, zipfile.ZipFile(
            temporary, 'w', zipfile.ZIP_DEFLATED) as output:
        for info in original.infolist():
            if not (directory / info.filename).is_file():
                output.writestr(info, original.read(info.filename))
        for path in sorted(directory.rglob('*')):
            if path.is_file():
                output.write(path, path.relative_to(directory).as_posix())
    temporary.replace(archive)
    print('MathJax %s: prepared %d HTML pages and updated %s' % (VERSION, count, archive))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory')
    parser.add_argument('archive')
    args = parser.parse_args()
    render(args.directory, args.archive)
