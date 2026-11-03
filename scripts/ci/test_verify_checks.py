"""Regression tests for missing, overwritten and falsely complete server suites."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('verify_checks', Path(__file__).with_name('verify-checks.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class VerifyChecksTest(unittest.TestCase):
    commands = 'mca-prueba ojo\nmca-prueba ia-red\nmca-prueba resumen\n'
    manifest = 'ojo ojo-piedra\nojo ojo-vidrio\nia-red ia-red\n'
    good = ('PRUEBA OK ojo-piedra: visto\nPRUEBA OK ojo-vidrio: visto\n'
            'PRUEBA OK ia-red: movimiento observado\n'
            'PRUEBA RESUMEN 3/3 {ojo-piedra=OK, ojo-vidrio=OK, ia-red=OK}\n')

    def test_complete_suite(self):
        self.assertEqual(3, module.verify(self.good, self.commands, self.manifest))

    def test_rejects_false_success(self):
        logs = {
            'missing': self.good.replace('PRUEBA OK ia-red: movimiento observado\n', ''),
            'duplicate': 'PRUEBA OK ia-red: repetido\n' + self.good,
            'unexpected': 'PRUEBA OK desconocido: extra\n' + self.good,
            'overwritten_failure': 'PRUEBA FALLA ia-red: falló antes\n' + self.good,
            'wrong_total': self.good.replace('3/3', '2/2'),
            'no_summary': self.good.split('PRUEBA RESUMEN')[0],
            'duplicate_summary': self.good + self.good.splitlines()[-1],
            'pending': self.good.replace('ia-red=OK', 'ia-red=PENDIENTE'),
            'wrong_summary_case': self.good.replace('ia-red=OK', 'otro=OK'),
            'late_result': self.good + 'PRUEBA OK ia-red: tarde\n',
            'truncated_summary': self.good.replace('ia-red=OK}', 'ia-red=OK'),
        }
        for name, log in logs.items():
            with self.subTest(name=name), self.assertRaises(ValueError):
                module.verify(log, self.commands, self.manifest)

    def test_rejects_inconsistent_inputs(self):
        for commands, manifest in [
            (self.commands.replace('mca-prueba ia-red\n', ''), self.manifest),
            (self.commands + 'mca-prueba ia-red\n', self.manifest),
            (self.commands.replace('mca-prueba resumen\n', ''), self.manifest),
            (self.commands, self.manifest + 'otro ia-red\n'),
            (self.commands, ''),
        ]:
            with self.subTest(commands=commands, manifest=manifest), self.assertRaises(ValueError):
                module.verify(self.good, commands, manifest)


if __name__ == '__main__':
    unittest.main()
