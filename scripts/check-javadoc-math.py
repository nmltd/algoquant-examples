#!/usr/bin/env python3
"""Check actual offline MathJax SVG rendering with Chrome, without running library tests."""
import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


class MathElements(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rendered = 0
        self.errors = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'mjx-container':
            self.rendered += 1
        if tag == 'mjx-merror' or attrs.get('data-mml-node') == 'merror':
            self.errors.append(attrs.get('data-mjx-error', 'MathJax equation error'))


def verify(root):
    root = Path(root).resolve()
    chrome = shutil.which('google-chrome') or shutil.which('chromium')
    if not chrome:
        raise RuntimeError('Chrome or Chromium is required to verify mathematical rendering')
    pages = [page for page in root.rglob('*.html')
             if re.search(r'\\\[|\\\(', page.read_text(encoding='utf-8'))]
    if not pages:
        raise ValueError('No LaTeX pages found; the check must exercise actual equations')
    total = 0
    for page in sorted(pages):
        with tempfile.TemporaryDirectory(prefix='javadoc-math-') as profile:
            command = [chrome, '--headless', '--no-sandbox', '--disable-gpu',
                       '--allow-file-access-from-files', '--disable-background-networking',
                       '--host-resolver-rules=MAP * ~NOTFOUND', '--user-data-dir=' + profile,
                       '--virtual-time-budget=10000', '--dump-dom', page.as_uri()]
            result = subprocess.run(command, check=True, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=60, encoding='utf-8')
        parsed = MathElements()
        parsed.feed(result.stdout)
        if parsed.errors or not parsed.rendered:
            raise ValueError('%s: %s; rendered=%d' %
                             (page.relative_to(root), parsed.errors, parsed.rendered))
        total += parsed.rendered
        print('%s: %d rendered equations' % (page.relative_to(root), parsed.rendered))
    print('Verified offline SVG rendering on %d pages (%d equations)' % (len(pages), total))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory')
    verify(parser.parse_args().directory)
