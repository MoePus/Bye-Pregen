# Vendored library manifest

`libs/` holds the one compile-time input the 26.3 build resolves locally.
Everything else comes from a repository: the project build scripts use
`maven.modrinth` coordinates (C2ME, Lithium, Sodium, Architectury, Chunky,
Tectonic, ...), and C2ME additionally gets extracted into
`common/build/c2me-compile-jars` by the `extractC2meCompileJars` task.

The jar itself is gitignored (`libs/*`, with `!libs/MANIFEST.md`); this file is
the record of what it is and who consumes it. The 26.2-era set of vendored C2ME
1.21.1 jars and support libraries is not part of this worktree: each project
build script declares its own dependencies.

Status definitions:

- `build-input`: referenced directly by a project build script.

| File | Status | Purpose |
| --- | --- | --- |
| `voxy-0.2.15-beta+1.21.1-neoforge.jar` | build-input | NeoForge build from `https://github.com/m3t4f1v3/voxy` commit `b4746ab9`; `common/build.gradle` compiles the Voxy world-conversion compatibility mixin against it, because Voxy has no 26.3 build yet. |
