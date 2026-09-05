# Building

## Toolchain

| Piece | Version |
| --- | --- |
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.5.1 (via the wrapper) |
| Kotlin | AGP 9 built-in Kotlin |
| JDK | 21 (Android Studio's bundled JBR) |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |

AGP 9 removed the DSL that the standalone `org.jetbrains.kotlin.android` plugin
relies on, so this project uses **AGP's built-in Kotlin support** instead: there
is no `kotlin-android` plugin in `app/build.gradle.kts`. The Compose,
kotlinx.serialization and KSP plugins are applied on top of it as normal.

### The easy way

`run.bat` wraps the build, applies the machine workaround described below, and
installs over the existing app without wiping your recorded data:

```
run.bat            build, install onto the connected phone, and launch
run.bat build      build only, do not touch the phone
run.bat install    install an already-built APK
run.bat release    bump the version and build a signed release APK
run.bat keystore   create the signing key, once
run.bat clean      delete build output
```

`run.bat` and `run.bat install` need a phone with USB debugging on — the test
phone. `run.bat release` never touches a phone: it writes a file to `dist\` that
any phone installs as an ordinary download. See [Releasing](#releasing).

### Or by hand

```bash
./gradlew :app:assembleDebug
```

Done this way you have to set the workaround yourself (see below); `run.bat`
sets it for you.

## Releasing

A release APK is how the app gets onto a phone that will never have USB
debugging turned on. Two things have to be true for the phone to treat it as an
**update** rather than a stranger, and to leave the recorded transactions alone:

- **The signing key never changes.** A different key means Android refuses the
  install outright — the only way through is uninstalling, which deletes the
  data. This is the one thing in the project that cannot be recreated if it is
  lost.
- **`versionCode` goes up every time.** It lives in `version.properties` and
  `run.bat release` increments it, so it is not something to remember.

### Choosing the key, once

```
run.bat keystore
```

That writes `%USERPROFILE%\keys\esa-money-tracker.jks`, asks for a password, and
prints the four lines to put in `keystore.properties` (see
`keystore.properties.example`). Neither the key nor that file is ever committed.

A **new** key is the tidy choice, but it is not the key the copy already on the
phone was signed with, so moving to it costs one migration: export a backup from
*Data & cadangan*, uninstall, install the new APK, import the backup.

To avoid that migration entirely, sign with the key the installed copy already
carries — the debug key this machine generated. Copy it out of Android Studio's
reach first, because Android Studio regenerates `.android/debug.keystore`
without asking if the file ever goes missing:

```powershell
Copy-Item "$env:USERPROFILE\.android\debug.keystore" "$env:USERPROFILE\keys\esa-money-tracker.jks"
```

then point `keystore.properties` at the copy with `storePassword=android`,
`keyAlias=androiddebugkey`, `keyPassword=android`. Same key, so every phone
already holding a debug build accepts the release APK as a plain update. The
password being public matters only to whoever can already read the file on this
PC.

Either way: **back the `.jks` file up**. It is worth more than the source code,
which can be rebuilt.

### Building one

```
run.bat release
```

Bumps `versionCode`, builds, and leaves `dist\esa-money-tracker-<version>.apk`.
Without `keystore.properties` the build still succeeds but the APK comes out
unsigned and the script says so — an unsigned APK is refused by every phone.

Edit `versionName` in `version.properties` when a release deserves a name;
nothing else in that file is meant to be edited by hand.

To check what a built APK actually claims:

```powershell
& "C:/Android/build-tools/37.0.0/apksigner.bat" verify --print-certs -v dist/esa-money-tracker-1.1.apk
& "C:/Android/build-tools/37.0.0/aapt2.exe" dump badging dist/esa-money-tracker-1.1.apk
```

The first prints the certificate the APK is signed with — compare its SHA-256
digest between two releases and they must match. The second prints the package
name and `versionCode`.

### Publishing it

The APK goes out as a GitHub release, which is what
[Obtainium](https://github.com/ImranR98/Obtainium) on the phone watches.

In the browser: **Releases > Draft a new release** on
`mahesaJenar01/esa-money-tracker`, tag it `v<versionName>`, drag
`dist\esa-money-tracker-<version>.apk` into the assets box, publish. Obtainium
notices within its next check and offers the update.

With the GitHub CLI installed (`winget install GitHub.cli`, then `gh auth login`)
the same thing is one command:

```powershell
gh release create v1.1 dist/esa-money-tracker-1.1.apk --title "1.1" --notes "Bank per rekening, tanggal transaksi bisa diatur, riwayat dirapikan."
```

The tag has to be new each time, so bump `versionName` in `version.properties`
when a release is worth its own name.

## One-time fix needed on this machine

On this PC, the JDK cannot create an `AF_UNIX` socket anywhere under
`%LOCALAPPDATA%` (`C:\Users\<you>\AppData\Local`), which is where it puts them by
default. Java's `Selector.open()` is built on such a socket, so **every** Gradle
build — from the terminal and from Android Studio — dies immediately with:

```
java.io.IOException: Unable to establish loopback connection
```

This is not specific to this project; it affects every Java and Gradle build on
the machine. It is usually caused by antivirus software or Windows' Controlled
Folder Access blocking socket files in that directory.

The fix is to point the JDK at a directory outside `AppData\Local`. Set this once
as a user environment variable (PowerShell, no admin rights needed):

```powershell
[Environment]::SetEnvironmentVariable('_JAVA_OPTIONS', '-Djdk.net.unixdomain.tmpdir=C:/Users/MAHESA~1/.gradle/uds', 'User')
```

Then create the directory and restart Android Studio and any open terminals:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.gradle\uds"
```

Notes:

- The path must contain **no spaces** — some tools split JVM argument strings on
  whitespace, so `C:\Users\Mahesa Jenar\...` breaks. The `MAHESA~1` 8.3 short
  name above is the same folder without the space.
- Putting the flag in `gradle.properties` under `org.gradle.jvmargs` does **not**
  work: Gradle strips unrecognised `-D` flags out of the daemon's real JVM
  arguments and applies them after startup, which is too late for this one.
- To verify the fix:

  ```powershell
  & "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -e 'java.nio.channels.Selector.open()'
  ```

  or simply run `./gradlew --version`.

If the machine's antivirus is later reconfigured to allow socket files under
`AppData\Local`, the environment variable can be removed.
