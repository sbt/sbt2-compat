# sbt2-compat

`sbt2-compat` is an [sbt](https://www.scala-sbt.org/) plugin that provides a unified API for sbt plugins that need to cross-build for both sbt 1.x and sbt 2.x.

The idea comes from [sbt-compat](https://github.com/dwijnand/sbt-compat), which backported parts of sbt 1's public API on top of sbt 0.13. This plugin does the same thing for the sbt 1 -> sbt 2 transition: it exposes (a subset of) the sbt 2 API using sbt 1 primitives, so that plugin authors can write shared source code that compiles against both sbt versions without maintaining their own per-version compat layers.

## Motivation

sbt 2 introduces significant API changes compared to sbt 1. The official migration guide documents the [PluginCompat technique](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html#the-plugincompat-technique) -- a pattern where each cross-built plugin maintains a shim object with version-specific source directories (`src/main/scala-2.12/` and `src/main/scala-3/`) to bridge the differences.

Today, every cross-built plugin implements this pattern independently with its own `Compat.scala` or `PluginCompat.scala` files -- duplicated, ad-hoc, and often subtly different. `sbt2-compat` extracts the **common** compat code into a shared library so plugin authors don't have to reinvent it.

## Setup

Add this plugin to your sbt plugin's `build.sbt` (**not** `project/plugins.sbt`):

```scala
addSbtPlugin("com.github.sbt" % "sbt2-compat" % "<version>")
```

Your plugin must be cross-built for both sbt 1 and sbt 2. The standard pattern is:

```scala
crossScalaVersions := Seq("3.7.3", "2.12.20")

(pluginCrossBuild / sbtVersion) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.5.8"
    case _      => "2.0.0-RC7"
  }
}
```

## Usage

In your plugin's shared source files, import the compat API:

```scala
import sbtcompat.PluginCompat._
```

Then use the provided types and methods. The compat layer supplies the right implementation per sbt version automatically.

### Type aliases

| Alias | sbt 1 (`scala-2.12`) | sbt 2 (`scala-3`) |
|---|---|---|
| `FileRef` | `java.io.File` | `xsbti.HashedVirtualFileRef` |
| `Out` | `java.io.File` | `xsbti.VirtualFile` |
| `ArtifactPath` | `java.io.File` | `xsbti.VirtualFileRef` |

### File conversions

All conversion methods take an implicit/using `FileConverter` parameter. On sbt 1 they are identity/trivial; on sbt 2 they delegate to the converter.

| Method | Description |
|---|---|
| `toFile(a: Attributed[FileRef])` | Extract `java.io.File` from attributed classpath entry |
| `toFile(ref: FileRef)` | Convert `FileRef` to `java.io.File` |
| `toNioPath(a)` / `toNioPath(ref)` | Convert to `java.nio.file.Path` |
| `toOutput(x: File)` | Convert `File` to `Out` |
| `toFileRef(x: File)` | Convert `File` to `FileRef` |
| `toArtifactPath(f: File)` | Convert `File` to `ArtifactPath` |
| `artifactPathToFile(ref)` | Convert `ArtifactPath` to `File` |
| `toNioPaths(cp)` / `toFiles(cp)` | Batch classpath conversions |
| `toFileRefsMapping(mappings)` | Convert `Seq[(File, String)]` to `Seq[(FileRef, String)]` |
| `toAttributedFiles(files)` | Wrap `Seq[File]` into `Seq[Attributed[FileRef]]` |

The `FileConverter` must be in implicit scope. The recommended pattern in task bodies:

```scala
implicit val conv: xsbti.FileConverter = fileConverter.value
```

This works on both Scala 2.12 (`implicit`) and Scala 3 (`using`) because `implicit val` is compatible with both resolution mechanisms.

### Def.uncached

In sbt 2, all tasks are cached by default; `Def.uncached(value)` opts out. In sbt 1, there is no caching. The compat layer provides an implicit enrichment on sbt 1 (`Def.uncached` as a no-op) and relies on the native method on sbt 2:

```scala
import sbtcompat.PluginCompat._

lazy val myTask = taskKey[Unit]("example")
myTask := Def.uncached {
  // task body
}
```

### ModuleID / Artifact serialization

| Member | sbt 1 | sbt 2 |
|---|---|---|
| `moduleIDStr` | `Keys.moduleID.key` (typed `AttributeKey`) | `Keys.moduleIDStr` (string key) |
| `artifactStr` | `Keys.artifact.key` (typed `AttributeKey`) | `Keys.artifactStr` (string key) |
| `parseModuleIDStrAttribute(x)` | identity | JSON parse |
| `moduleIDToStr(m)` | identity | JSON write |
| `parseArtifactStrAttribute(x)` | identity | JSON parse |
| `artifactToStr(art)` | identity | JSON write |

### Credentials

| Method | sbt 1 | sbt 2 |
|---|---|---|
| `toDirectCredentials(c)` | `Credentials.toDirect(c)` | `IvyCredentials.toDirect(c)` |
| `credentialForHost(cs, host)` | `Credentials.forHost(cs, host)` | `IvyCredentials.forHost(cs, host)` |

### ScopedKey / Settings

| Method | Description |
|---|---|
| `createScopedKey(settingKey, projRef)` | Create a `ScopedKey` from a `SettingKey` and `ProjectRef` |
| `setSetting(data, scopedKey, value)` | Set a value in sbt's settings map (different API on sbt 1 vs sbt 2) |

### Attributed file helpers

For storing/retrieving `File` values in sbt's `Attributed` metadata (which uses typed `AttributeKey` on sbt 1 and `StringAttributeKey` on sbt 2):

| Method | Description |
|---|---|
| `attributedPutFile(a, key, value)` | Store a `File` in `Attributed` metadata |
| `attributedGetFile(a, key)` | Retrieve a `File` from `Attributed` metadata |
| `attributedPutFiles(a, key, value)` | Store `Seq[File]` in `Attributed` metadata |
| `attributedGetFiles(a, key)` | Retrieve `Seq[File]` from `Attributed` metadata |
| `attributedPutValue(a, key, value)` | Store a generic value in `Attributed` metadata |

## Known caveats

### FileRefOps ambiguity

`sbt2-compat` provides `FileRefOps` which adds `.name()` to `java.io.File` (needed for sbt 1 compatibility with sbt 2's `HashedVirtualFileRef.name()`). However, sbt already provides `fileToRichFile` which also adds `.name()`. When both wildcard imports are present (`import sbt._` + `import sbtcompat.PluginCompat._`), Scala 2.12 may report an ambiguity error. **Fix:** use specific imports from `sbt2-compat` instead of a wildcard, excluding `FileRefOps`, in files where `.name()` is called on `File` values.

### artifactStr / moduleIDStr ambiguity on Scala 3

On sbt 2 (Scala 3), `sbt.Keys` natively defines `artifactStr` and `moduleIDStr`. If a file has both `import sbt.Keys.*` and `import sbtcompat.PluginCompat.*`, the compiler reports "Reference is ambiguous". **Fix:** use an aliased import and qualify the ambiguous names:

```scala
import sbtcompat.{PluginCompat => SbtCompat}
import SbtCompat.{FileRef, toFile, parseModuleIDStrAttribute, ...}
// For ambiguous names: SbtCompat.artifactStr, SbtCompat.moduleIDStr
```

### DefOps absent on Scala 3

`sbt2-compat`'s Scala 3 source does not define `DefOps` because `Def.uncached` is native on sbt 2. Do not include `DefOps` in specific imports -- it will fail on Scala 3. Wildcard imports handle this gracefully.

### sbt 2 disk cache and empty jars

sbt 2.0.0-RC7 aggressively caches compilation results. When the disk cache hits, class files may not be written to the local `classes/` directory, causing `packageBin` to produce empty jars when using `publishLocal`. Workaround: invalidate the cache by making a trivial source change, or delete the `target/` directory and restart sbt.

## Design

The plugin follows the same pattern as [sbt-compat](https://github.com/dwijnand/sbt-compat) (0.13 -> 1):

- The **sbt 1 tree** (`src/main/scala-2.12/`) is the workhorse. It defines type aliases, implicit enrichments, and standalone functions that bridge the sbt 1 API to look like sbt 2.
- The **sbt 2 tree** (`src/main/scala-3/`) is a minimal stub. It defines type aliases and standalone functions, but omits anything sbt 2 already provides natively.

**The implicit enrichment rule:** An implicit class in the sbt 1 file is justified only if it eliminates that method entirely from the sbt 2 file -- i.e., sbt 2 already has the method natively. If both files would need the method regardless, use a plain standalone function in both.

**What is NOT included:** Plugin-specific compat code that bridges API differences unique to a single plugin (e.g., plugin-specific task key types, caching implementations, annotation differences) must remain in the plugin's own compat files. Only generic, reusable compat code belongs in `sbt2-compat`.

## Source layout

```
build.sbt
project/build.properties                        # sbt 2.0.0-RC7
src/main/scala-2.12/sbtcompat/PluginCompat.scala # sbt 1 — workhorse
src/main/scala-3/sbtcompat/PluginCompat.scala    # sbt 2 — stub
```

The two source trees are selected automatically by sbt based on `scalaBinaryVersion`: `scala-2.12/` when building for Scala 2.12 (sbt 1), `scala-3/` when building for Scala 3 (sbt 2).

## Building

```bash
sbt +compile        # cross-compile for both sbt versions
sbt +publishLocal   # publish locally for both sbt versions
```

## Development cycle

`sbt2-compat` evolves iteratively by porting real-world sbt plugins from sbt 1 to sbt 2. Each ported plugin validates the existing API surface and may reveal missing compat methods that need to be added.

### The porting loop

The development process for each new plugin follows this cycle:

1. **Clone** the target plugin's upstream repo.
2. **Baseline** -- compile and test the plugin as-is (before any changes) to establish a known-good state, using the same commands as CI.
3. **Publish `sbt2-compat` locally** -- `sbt +publishLocal` from this repo.
4. **Analyze** -- compare the plugin's compat files (`Compat.scala`, `PluginCompat.scala`) against `sbt2-compat`'s API. Identify which members overlap (can be replaced) and which are plugin-specific (must stay local).
5. **Instrument** -- add the `sbt2-compat` dependency, remove overlapping members from the plugin's compat files, add `import sbtcompat.PluginCompat._` to shared source files, adapt call sites as needed.
6. **Verify** -- run the same compile and test commands as the baseline. All tests must pass identically.
7. **Collect gaps** -- if the plugin needs compat methods that `sbt2-compat` doesn't have yet, add them to the compat plugin and repeat from step 3.
8. **Repeat** with the next plugin.

This loop ensures the compat API is driven by real usage across multiple plugins rather than speculative design.
