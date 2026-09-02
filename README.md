# Tolgee IntelliJ Plugin

Tolgee localization platform integration for JetBrains IDEs (IntelliJ IDEA,
WebStorm, PyCharm, GoLand, RustRover, RubyMine, PhpStorm, Android Studio, etc.).

## Overview

The plugin connects an IDE project to a [Tolgee](https://tolgee.io) project and
provides:

- **Tool window** — a `Tolgee` panel on the right side bar with a toolbar
  (`+` Add, `−` Unlink, `↑` Push, `↓` Pull, `↻` Refresh) and a tree that shows
  the linked Tolgee project on top and the local translations directory below.
  Double-click a file to open it. Right-click the tree for **Edit Connection…**.
- **Local-first key cache** — code completion reads from the on-disk translation
  files, not from the Tolgee server. Push and Pull refresh the cache
  automatically; Refresh does the same manual re-scan without any network call.
- **Code completion** — autocomplete translation key names inside Tolgee call
  sites in JavaScript, TypeScript, JSX, TSX, Java, Kotlin, and Android
  `strings.xml`. When a key is parametrized (ICU MessageFormat), the plugin
  inserts a parameter argument shaped for the host language
  (`{ name: name }` for JS, `params={{ name: name }}` for the `<T>` component,
  `mapOf("name" to name)` for Kotlin, `Map.of("name", name)` for Java) and
  pre-selects the first placeholder value so you can start typing immediately.

## How to

### 1. Add a connection

Open the **Tolgee** tool window (right side bar) and click **+**. Fill in:

- **Instance URL** (default `https://app.tolgee.io`)
- **API key** — either a Project API Key (`tgpak_...`) bound to a single Tolgee
  project, or a Personal Access Token (`tgpat_...`) that works across all
  projects you can see. Stored in the IDE's secure password store; only the URL
  goes into `.idea/tolgee.xml`.
- Click **Load projects**, pick one from the dropdown.
- **Pull translations after creating the connection** (checked by default) —
  runs Pull immediately after saving, so code completion is ready right away.

Under **Advanced**:

- **Translations path** — directory under the project root that holds the
  translation files (default `.tolgee`). Created automatically on save if
  missing.
- **Namespaces** — checkbox list. `<All namespaces>` (default) or pick a
  specific subset to push/pull. When the project has 0 or 1 namespace the
  list collapses to a single disabled row.
- **Languages** — checkbox list. `<All languages>` (default) or pick a
  specific subset. Widening the selection on **Edit Connection…** triggers an
  automatic Pull; narrowing it prompts you to delete files that fall outside
  the new filter.

Editing an existing connection (right-click the tree → **Edit Connection…**)
opens the same dialog without the pull checkbox.

### 2. On-disk layout

Files live under `<translationsPath>`:

```
<translationsPath>/<lang>.json          # keys with no namespace
<translationsPath>/<namespace>/<lang>.json   # namespaced keys
```

Each file is a **flat JSON** object: `{"key.name": "translation string"}`. Only
this shape is read or written — nested objects aren't parsed.

### 3. Pull / Push / Refresh

- **Pull** (↓) — asks Tolgee for every key, groups by `(namespace, language)`,
  and writes the resulting `.json` files. Filters by the Namespaces and
  Languages you set on the connection. After writing, the key cache re-reads
  from disk.
- **Push** (↑) — walks the translations directory one level deep (root + one
  subdirectory for namespaces), uploads each `<lang>.json` via the
  `single-step-import` endpoint with the correct `namespace` and
  `forceMode=OVERRIDE`. New keys are created automatically. After uploading,
  the key cache re-reads from disk.
- **Refresh** (↻) — re-reads the translations directory into the in-memory key
  cache. No network call. Use it after editing the JSON files by hand (or after
  a teammate's Pull).

### 4. Code completion

Completion fires automatically inside these positions once the cache is
populated:

| Language                                | Recognized positions                                                                                        |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| JS / TS / JSX / TSX                     | `t('...')`, `tolgee.t('...')`, `tolgee.translate('...')`, `<T keyName="..." />`                             |
| Java                                    | `*.translate("...")`, `*.t("...")`, `*.getTranslation("...")`, `*.getMessage("...")` — first string arg     |
| Kotlin                                  | `translate("...")`, `t("...")`, `getTranslation("...")`, `getMessage("...")` — first string arg             |
| Android XML (`res/values*/strings.xml`) | `<string name="…">` inside a `<resources>` root                                                             |

For parametrized keys, the plugin extracts ICU parameters from the translation
text (and derives `count` for plurals), then inserts an argument map after the
key string and pre-selects the first placeholder value so you can start typing
the real argument immediately.

## Reference

### Modules

| Path                | Purpose                                                                     |
| ------------------- | --------------------------------------------------------------------------- |
| `api/`              | OkHttp + kotlinx-serialization REST client and DTOs                         |
| `settings/`         | Application-level Tolgee URL + API key (stored via the IDE password safe)   |
| `project/`          | Project-level link (`TolgeeProjectLink`) and file-backed key cache          |
| `toolwindow/`       | Tool window factory, tree panel, Add/Edit/Unlink dialogs and actions        |
| `push/`, `pull/`    | Push / Pull `AnAction`s and background tasks                                |
| `completion/`       | Language-specific completion contributors and ICU parameter extraction      |
| `util/`             | Filesystem helpers and icons                                                |

### API endpoints hit

| Trigger                                            | Endpoints                                                                                     |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| **Add dialog → Load projects**                     | `GET /v2/api-keys/current` → then `GET /v2/projects/{boundId}` (PAK) or `GET /v2/projects` (PAT) |
| **Pull**                                           | `GET /v2/projects/{id}/translations` (paged, `size=200`)                                      |
| **Push**                                           | `POST /v2/projects/{id}/single-step-import` per file (multipart, `format=JSON_ICU`)           |
| **Refresh**                                        | *(none — reads local files)*                                                                  |

All requests send `X-Api-Key: <apiKey>`.

### Build

```bash
./gradlew buildPlugin           # produces build/distributions/*.zip
./gradlew runIde                # launches IntelliJ IDEA Ultimate sandbox with the plugin
./gradlew runIdeAndroidStudio   # same, but against a local Android Studio install
./gradlew verifyPlugin          # plugin verifier against bundled IDE versions
```

`runIdeAndroidStudio` is registered via `intellijPlatformTesting.runIde.register`
and points at `/Users/<you>/Applications/Android Studio.app/Contents`. It also
sets `executable` to Studio's bundled JBR and prepends `nio-fs.jar` to the boot
classpath so `MultiRoutingFileSystemProvider` resolves at JVM startup — required
by the vmoptions that Studio ships with. Edit the path in `build.gradle.kts` if
your install is elsewhere.

### Compatibility

`since-build = 251` (IntelliJ Platform 2025.1), no `until-build`. Optional
dependencies on the bundled `JavaScript`, `com.intellij.java`, and
`org.jetbrains.kotlin` plugins gate the language-specific completion
contributors — running in an IDE that lacks one of those is harmless, that
contributor just isn't loaded. XML completion is registered directly (XML PSI is
part of the base platform, always available).

### Build target

The Gradle build resolves against **IntelliJ IDEA Ultimate** because the
JavaScript plugin (`Lang/JavaScript and TypeScript`) is bundled only with
Ultimate. The produced plugin still installs and runs on Community, PyCharm
Community, Android Studio, and other IntelliJ-based IDEs — completion in JS/TS
just won't load there because the JS plugin isn't present. The dependencies in
`plugin.xml` are all `optional`, so the plugin loads regardless.

If you don't have an Ultimate license to use locally, set
`-PintelliJPlatformType=IU` on a CI machine; the IntelliJ Platform Gradle plugin
downloads Ultimate automatically — no license is required for *building* against
it, only for *running* it interactively.
