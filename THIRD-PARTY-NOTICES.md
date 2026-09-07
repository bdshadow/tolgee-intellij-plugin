# Third-Party Notices

This plugin bundles the following third-party dependencies. All are licensed under
the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). A
copy of that license is included alongside this file as `LICENSE`.

The plugin also depends on the IntelliJ Platform SDK at runtime; that SDK is
provided by the host IDE and is not redistributed in this bundle.

| Component | Version | Copyright | License |
| --- | --- | --- | --- |
| kotlin-stdlib | 2.0.21 | © JetBrains s.r.o. | Apache-2.0 |
| kotlin-stdlib-jdk7 | 1.9.10 | © JetBrains s.r.o. | Apache-2.0 |
| kotlin-stdlib-jdk8 | 1.9.10 | © JetBrains s.r.o. | Apache-2.0 |
| kotlinx-serialization-core-jvm | 1.7.3 | © JetBrains s.r.o. | Apache-2.0 |
| kotlinx-serialization-json-jvm | 1.7.3 | © JetBrains s.r.o. | Apache-2.0 |
| jetbrains-annotations | 13.0 | © JetBrains s.r.o. | Apache-2.0 |
| okhttp | 4.12.0 | © Square, Inc. | Apache-2.0 |
| okio-jvm | 3.6.0 | © Square, Inc. | Apache-2.0 |

## Bundled Brand Assets

The following image files ship inside the plugin ZIP:

- `META-INF/pluginIcon.svg`
- `icons/tolgee.svg`
- `icons/tolgee_dark.svg`

These icons were adapted from the favicon shipped with
[tolgee-platform](https://github.com/tolgee/tolgee-platform), which is licensed
under Apache License, Version 2.0. Apache-2.0 covers reproduction and
redistribution of the source; it does not grant trademark rights (see §6). See
the Trademark Notice below. Each derived SVG also carries an in-file XML
comment naming its upstream source, per Apache-2.0 §4(b).

The upstream repository was checked for a top-level `NOTICE` file (Apache-2.0
§4(d)) at the paths `NOTICE`, `NOTICE.txt`, `NOTICE.md`, `webapp/NOTICE`, and
`webapp/public/NOTICE`. None exist on `main` at the time of this review, so
there is no NOTICE-file content to reproduce for the icon derivations. Re-verify
if the icons are re-pulled from a later commit.

## Upstream NOTICE Preservation (Apache-2.0 §4(d))

The bundled JARs were checked for `META-INF/NOTICE` (or top-level `NOTICE`)
files that require verbatim preservation. Only one such notice was found — in
`okhttp-4.12.0.jar`, at `okhttp3/internal/publicsuffix/NOTICE`, reproduced
below verbatim:

```
Note that publicsuffixes.gz is compiled from The Public Suffix List:
https://publicsuffix.org/list/public_suffix_list.dat

It is subject to the terms of the Mozilla Public License, v. 2.0:
https://mozilla.org/MPL/2.0/
```

No NOTICE files were found in `kotlin-stdlib`, `kotlin-stdlib-jdk7`,
`kotlin-stdlib-jdk8`, `kotlinx-serialization-core-jvm`,
`kotlinx-serialization-json-jvm`, `jetbrains-annotations`, or `okio-jvm` as of
their bundled versions. Re-verify when the version pins move.

## Trademark Notice

"Tolgee" is a trademark of Tolgee s.r.o. This plugin is not affiliated with or
endorsed by Tolgee s.r.o.; the name identifies the localization platform the
plugin integrates with. All rights in the Tolgee name and logo remain with
their respective owner.
