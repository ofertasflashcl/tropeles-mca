# Colaboración entre agentes

Cuaderno compartido de los agentes que mejoran Tropeles-MCA junto a Benja. Lo ve cualquiera que lea el repo. Puede escribir en él cualquiera que empuje una rama o abra un PR.

- **Claude** (Claude Code): escribió casi todo Tropeles (rondas 1 a 20), integra el trabajo y publica el jar.
- **Sol** (Sol 6.1 / Codex, ChatGPT): autor de la auditoría del 2026-11-03 (hallazgos F01-F26, backlog B01-B12, criterios T01-T40).
- **Benja**: decide qué se hace, prueba en su partida y mergea.

## Archivos

- [`TAREAS.md`](TAREAS.md): quién hace qué y qué archivos tiene reservados.
- [`BITACORA.md`](BITACORA.md): qué hizo cada uno, ideas, preguntas y respuestas.

## Cómo trabajamos

1. Antes de empezar, lee `BITACORA.md` desde tu última entrada y después `TAREAS.md`.
2. Para tomar una tarea, pon tu nombre en su fila. Sus archivos quedan reservados para ti hasta que tu cambio entre a la rama de integración. Si necesitas un archivo reservado, pídelo en la bitácora.
3. La rama de integración es `tropeles/ojo-combate-mineria-1.20.1`. Claude empuja directo ahí. Sol trabaja en ramas `sol/<tema>` y abre un PR hacia ella; Claude lo revisa y Benja lo mergea.
4. El CI "Forge 1.20.1 (Tropeles)" compila, arranca un servidor Forge real y corre los escenarios de `scripts/ci/commands.txt`. Corre en cada push a `tropeles/**` y en cada PR hacia esas ramas. El jar de Benja se publica solo desde la rama de integración.
5. Al cerrar una sesión de trabajo, agrega una entrada al final de `BITACORA.md`. Pon la fecha UTC, quién eres, qué hiciste (commits o PR), qué comprobaste y qué no, qué sigue y tus preguntas para el otro.
6. Compilar o que el NPC diga algo no cuenta como éxito. Di qué se comprobó y cómo.
7. Los commits que solo tocan `colaboracion/` llevan `[skip ci]`. No se hace force-push ni se reescribe historia ajena. Si dos entradas chocan en un merge, se conservan las dos.

## Lo que pidió Benja

- En el mod solo se usa IA gratis, nunca APIs pagadas.
- Entregas rápidas: el jar se publica apenas el CI queda verde, y Benja lo prueba en su partida.
- Escribimos en español.
