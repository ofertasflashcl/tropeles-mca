#!/usr/bin/env python3
"""Verify terminal server results against an explicit command/result manifest."""
import re
import sys
from collections import Counter
from pathlib import Path


def verify(log, commands, manifest):
    rows = [line.split() for line in manifest.splitlines()
            if line.strip() and not line.lstrip().startswith('#')]
    if not rows or any(len(row) != 2 for row in rows):
        raise ValueError('Manifiesto vacío o mal formado')
    expected = [row[1] for row in rows]
    if len(expected) != len(set(expected)):
        raise ValueError('Resultados duplicados en el manifiesto')
    requested = []
    summaries = 0
    for line in commands.splitlines():
        parts = line.strip().split()
        if not parts or parts[0].startswith('#') or parts[0] != 'mca-prueba':
            continue
        if len(parts) != 2:
            raise ValueError('Comando mca-prueba mal formado')
        if parts[1] == 'resumen':
            summaries += 1
        else:
            if summaries:
                raise ValueError('Hay pruebas después del resumen')
            requested.append(parts[1])
    if summaries != 1 or len(requested) != len(set(requested)):
        raise ValueError('Debe haber un resumen y comandos únicos')
    if set(requested) != {row[0] for row in rows}:
        raise ValueError('commands.txt y el manifiesto no describen la misma suite')
    if 'PRUEBA FALLA' in log:
        raise ValueError('El servidor reportó PRUEBA FALLA')
    results = re.findall(r'PRUEBA (OK|FALLA) ([\w-]+):', log)
    counts = Counter(name for status, name in results)
    if counts != Counter(expected):
        raise ValueError(f'Resultados ausentes, inesperados o repetidos: esperado={sorted(expected)}, recibido={dict(counts)}')
    summaries = list(re.finditer(r'PRUEBA RESUMEN (\d+)/(\d+) \{([^}\n]*)\}', log))
    if len(summaries) != 1:
        raise ValueError('Falta un resumen completo único')
    summary = summaries[0]
    if summary.group(1, 2) != (str(len(expected)), str(len(expected))):
        raise ValueError('El total del resumen no coincide con el manifiesto')
    entries = [item.strip().split('=') for item in summary.group(3).split(',')]
    if any(len(item) != 2 or item[1] != 'OK' for item in entries):
        raise ValueError('El resumen contiene resultados no aprobados')
    if Counter(item[0] for item in entries) != Counter(expected):
        raise ValueError('El contenido del resumen no coincide con el manifiesto')
    if re.search(r'PRUEBA (?:OK|FALLA) ', log[summary.end():]):
        raise ValueError('Se reportaron resultados después del resumen')
    return len(expected)


if __name__ == '__main__':
    try:
        if len(sys.argv) != 4:
            raise ValueError('Uso: verify-checks.py latest.log commands.txt expected-checks.tsv')
        total = verify(*(Path(path).read_text(encoding='utf-8', errors='replace') for path in sys.argv[1:]))
        print(f'Suite exacta verificada: {total}/{total}')
    except (ValueError, OSError) as error:
        print(f'Validación de suite FALLA: {error}', file=sys.stderr)
        sys.exit(1)
