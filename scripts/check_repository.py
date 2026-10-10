#!/usr/bin/env python3
"""Deterministic locale, source configuration and documentation consistency checks."""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
locales = {lang: json.loads((root / f'hub-server/src/main/resources/public/locales/{lang}.json').read_text(encoding='utf-8')) for lang in ('en','de','fa')}
keys = set(locales['en'])
for lang, data in locales.items():
    assert set(data) == keys, f'Locale keys differ: {lang}'
    assert all(isinstance(value, str) and value.strip() for value in data.values()), f'Empty label: {lang}'
for path in root.rglob('*.json'):
    if not {'build', 'target', '.git'}.intersection(path.relative_to(root).parts):
        json.loads(path.read_text(encoding='utf-8'))
for path in root.rglob('pom.xml'):
    ET.parse(path)
names = ('user-guide','architecture','api','connectors','security','deployment','testing','operations','provider-parity','commercial-readiness','research')
for lang in locales:
    for name in names:
        path = root / f'docs/{lang}/{name}.md'
        assert path.exists() and path.stat().st_size > 400, f'Missing documentation: {path}'
for path in list(root.glob('*.md')) + list((root/'docs').rglob('*.md')):
    text = path.read_text(encoding='utf-8')
    assert text.count('```') % 2 == 0, f'Unbalanced code fences: {path}'
    for link in re.findall(r'\]\(([^)]+)\)', text):
        if '://' in link or link.startswith(('#','mailto:')):
            continue
        dest = link.split('#',1)[0]
        if dest:
            assert (path.parent/dest).exists(), f'Broken local link in {path}: {link}'
print(f'Repository checks passed: {len(keys)} locale keys x 3 languages; 33 required guides; JSON/XML/Markdown links.')
