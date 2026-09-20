@echo off
setlocal

rem ---------------------------------------------------------------------------
rem  Esa's Money Tracker - build and install helper
rem
rem    run.bat            build the debug APK, install it, and launch it
rem    run.bat build      build only, do not touch the phone
rem    run.bat install    install the APK that is already built, and launch it
rem    run.bat release    bump the version and build a signed release APK
rem    run.bat keystore   create the signing key, once, for the life of the app
rem    run.bat clean      delete build output
rem
rem  "run.bat" and "run.bat install" want a phone plugged in with USB debugging
rem  on - the test phone. "run.bat release" never touches a phone: it produces a
rem  file in dist\ that any phone can install as an ordinary download.
rem
rem  Installing over an existing copy keeps your recorded data, because the
rem  package name and the signing key do not change between builds.
rem ---------------------------------------------------------------------------

set "PROJ=%~dp0"
pushd "%PROJ%" || goto :fail

set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=all"

rem --- Java ------------------------------------------------------------------
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [X] No JDK found at "%JAVA_HOME%".
  echo     Set JAVA_HOME to a JDK 17+ install, or to Android Studio's bundled jbr folder.
  goto :fail
)

rem --- Workaround for this machine's AF_UNIX problem -------------------------
rem  The JDK cannot create socket files under AppData\Local here, which makes
rem  every Gradle build die with "Unable to establish loopback connection".
rem  Point it somewhere else. The path must contain no spaces. See BUILDING.md.
set "UDS_DIR=%USERPROFILE%\.gradle\uds"
if not exist "%UDS_DIR%" mkdir "%UDS_DIR%"
for %%I in ("%UDS_DIR%") do set "UDS_SHORT=%%~sI"
set "_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=%UDS_SHORT:\=/%"

rem --- adb -------------------------------------------------------------------
set "ADB=adb"
where adb >nul 2>&1 || set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=C:\Android\platform-tools\adb.exe"

set "APK=app\build\outputs\apk\debug\app-debug.apk"
set "PKG=com.esa.moneytracker"

if /i "%ACTION%"=="clean"    goto :clean
if /i "%ACTION%"=="build"    goto :build
if /i "%ACTION%"=="release"  goto :release
if /i "%ACTION%"=="keystore" goto :keystore
if /i "%ACTION%"=="install"  goto :install
if /i "%ACTION%"=="all"      goto :build
echo [X] Unknown action "%ACTION%". Use: build ^| install ^| release ^| keystore ^| clean
goto :fail

:clean
echo == Cleaning ==
call "%PROJ%gradlew.bat" clean || goto :fail
echo [OK] Build output removed.
goto :done

rem ---------------------------------------------------------------------------
rem  Creating the signing key. Done once, and then never again for the life of
rem  the app: every future update has to be signed with this same key or the
rem  phone will not accept it as an update.
rem ---------------------------------------------------------------------------
:keystore
set "KEYDIR=%USERPROFILE%\keys"
set "KEYFILE=%KEYDIR%\esa-money-tracker.jks"
set "ALIAS=esa-money-tracker"

if exist "%KEYFILE%" (
  echo [!] A key already exists at "%KEYFILE%".
  echo     Creating a second one would make every phone refuse the update.
  echo     Delete that file only if you are certain nothing was signed with it.
  goto :done
)
if not exist "%KEYDIR%" mkdir "%KEYDIR%"

echo == Creating the signing key ==
echo.
echo  It will ask for a password twice, and then for a key password
echo  ^(press Enter to reuse the same one^). Nothing is echoed as you type.
echo.
"%JAVA_HOME%\bin\keytool" -genkeypair -v ^
  -keystore "%KEYFILE%" ^
  -alias "%ALIAS%" ^
  -keyalg RSA -keysize 4096 -validity 10950 ^
  -dname "CN=Esa Money Tracker, O=Esa, C=ID"
if errorlevel 1 goto :fail

set "KEYFILE_FWD=%KEYFILE:\=/%"
echo.
echo [OK] Key written to "%KEYFILE%"
echo.
echo  Two things left, both one-off:
echo.
echo  1. Create the file  %PROJ%keystore.properties  containing:
echo.
echo         storeFile=%KEYFILE_FWD%
echo         storePassword=^<the password you just chose^>
echo         keyAlias=%ALIAS%
echo         keyPassword=^<the key password, same one if you pressed Enter^>
echo.
echo  2. Back up "%KEYFILE%" somewhere you will still have it in five years.
echo     Losing it means no phone can ever update the app again without
echo     uninstalling it first, which deletes your recorded transactions.
goto :done

rem ---------------------------------------------------------------------------
rem  Building a release. The file in dist\ is named after versionName AND
rem  versionCode, so two releases can never land on the same name and quietly
rem  overwrite each other. versionCode has to go up every time: the phone
rem  compares that number and refuses anything that is not higher than what it
rem  already has. The bump is written before the build, because Gradle reads it
rem  out of version.properties, and put back if the build or the copy fails, so
rem  a broken build does not burn a version.
rem ---------------------------------------------------------------------------
:release
rem  The one line to change when this script is copied to another app.
set "APPNAME=esa-money-tracker"
rem  How many builds dist\ keeps. After a signed release the older ones are
rem  deleted, so there is always something to fall back to if the new one is bad.
set "KEEP=3"

if not exist "%PROJ%version.properties" (
  echo [X] version.properties is missing.
  goto :fail
)

for /f "usebackq eol=# tokens=1,2 delims==" %%A in ("%PROJ%version.properties") do (
  if /i "%%A"=="versionCode" set "VCODE=%%B"
  if /i "%%A"=="versionName" set "VNAME=%%B"
)
if not defined VCODE (
  echo [X] version.properties has no versionCode.
  goto :fail
)
set "OLDCODE=%VCODE%"
set /a VCODE=VCODE+1

set "OUT=dist\%APPNAME%-%VNAME%-%VCODE%.apk"
if exist "%PROJ%%OUT%" (
  echo [X] %OUT% already exists, and this script never overwrites a release.
  echo     Nothing was built: version.properties still says versionCode %OLDCODE%.
  echo     Move that file out of dist\ if it is stale, then run this again.
  goto :fail
)

call :writeversion %VCODE%

echo == Building release %VNAME% ^(versionCode %VCODE%^) ==
if not exist "%PROJ%keystore.properties" (
  echo.
  echo [!] No keystore.properties, so this APK will come out unsigned and no
  echo     phone will install it. Run "run.bat keystore" first.
  echo.
)

call "%PROJ%gradlew.bat" :app:assembleRelease || goto :unbump

set "RAPK=app\build\outputs\apk\release\app-release.apk"
set "SIGNED=yes"
if not exist "%RAPK%" (
  set "RAPK=app\build\outputs\apk\release\app-release-unsigned.apk"
  set "SIGNED=no"
)
if not exist "%RAPK%" (
  echo [X] Build reported success but no release APK was produced.
  goto :unbump
)

if not exist "%PROJ%dist" mkdir "%PROJ%dist"
if exist "%PROJ%%OUT%" (
  echo [X] %OUT% appeared while the build was running. Refusing to overwrite it.
  goto :unbump
)
copy /y "%RAPK%" "%PROJ%%OUT%" >nul || goto :unbump

echo.
if "%SIGNED%"=="no" (
  echo [!] UNSIGNED: %OUT%
  echo     A phone will refuse this file. Run "run.bat keystore", write
  echo     keystore.properties, and build again.
  goto :done
)
echo [OK] %OUT%
echo      version %VNAME%, versionCode %VCODE%
echo      %PROJ%%OUT%
echo.
echo      Publish it so Obtainium sees the update:
echo.
echo          gh release create v%VNAME% "%OUT%" --title "v%VNAME%" --notes "%APPNAME% %VNAME% (build %VCODE%)"
echo.
echo      Put this file where the phone can reach it, open it there, and tap
echo      install. It installs over the existing app and keeps every recorded
echo      transaction. See README.md, "Updating the app on your phone".
echo.
echo      dist\ keeps the %KEEP% most recent builds, so there is something to go
echo      back to if this one turns out bad. Anything older than that goes:
set "NTH=0"
for /f "usebackq delims=" %%F in (`dir /b /a-d /o-d "%PROJ%dist\%APPNAME%-*.apk" 2^>nul`) do call :prune "%%F"
goto :done

rem ---------------------------------------------------------------------------
rem  The build or the copy failed, so the release this run claimed a number for
rem  does not exist. Put the old versionCode back: otherwise every failed
rem  attempt would eat a number.
rem ---------------------------------------------------------------------------
:unbump
call :writeversion %OLDCODE%
echo.
echo [!] versionCode put back to %OLDCODE%; no release file was produced.
goto :fail

:build
echo == Building debug APK ==
call "%PROJ%gradlew.bat" :app:assembleDebug || goto :fail
if not exist "%APK%" (
  echo [X] Build reported success but %APK% is missing.
  goto :fail
)
echo [OK] Built %APK%
if /i "%ACTION%"=="build" goto :done

:install
if not exist "%APK%" (
  echo [X] %APK% not found. Run "run.bat build" first.
  goto :fail
)
if not exist "%ADB%" (
  echo [X] adb not found. Install Android SDK platform-tools, or add adb to PATH.
  goto :fail
)

echo.
echo == Connected devices ==
"%ADB%" devices
echo.

echo == Installing ==
rem -r reinstalls over the existing app and keeps its data.
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo [X] Install failed.
  echo     If it mentions INSTALL_FAILED_UPDATE_INCOMPATIBLE, a copy signed with a
  echo     different key is already installed. Remove it first:
  echo         "%ADB%" uninstall %PKG%
  echo     That also deletes the recorded transactions on the phone.
  goto :fail
)

echo.
echo == Launching ==
"%ADB%" shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo [OK] Esa's Money Tracker is installed and running.
goto :done

:fail
popd 2>nul
echo.
echo Build or install failed. See the output above.
endlocal
exit /b 1

:done
popd 2>nul
endlocal
exit /b 0

rem ---------------------------------------------------------------------------
rem  Rewrites version.properties. The versionCode to store is the argument;
rem  versionName comes from VNAME. Used to bump the number before a release
rem  build, and to put the old number back when that build fails.
rem ---------------------------------------------------------------------------
:writeversion
> "%PROJ%version.properties" (
  echo # The version the next release APK is built with.
  echo #
  echo # versionCode is what Android compares when installing over an existing copy: it
  echo # must go up every time, or the phone refuses the update. "run.bat release"
  echo # bumps it for you, so this file is not something to edit by hand.
  echo #
  echo # versionName is the human label. Change it whenever a release is worth a name.
  echo versionCode=%~1
  echo versionName=%VNAME%
)
goto :eof

rem ---------------------------------------------------------------------------
rem  Deletes one file from dist\, unless it is among the KEEP most recent. The
rem  caller walks dist\ newest first and calls this once per file; NTH counts how
rem  far down that list we are. It is a subroutine rather than the body of the
rem  loop because a counter cannot be read back inside the loop that sets it.
rem
rem  "Newest" is the file's timestamp (dir /o-d), which follows the order the
rem  releases were built, because copy keeps the time the APK was built. Only
rem  files this script named itself are ever listed, so nothing else you put in
rem  dist\ is touched.
rem ---------------------------------------------------------------------------
:prune
set /a NTH+=1
if %NTH% LEQ %KEEP% goto :eof
del "%PROJ%dist\%~1" >nul 2>&1
if exist "%PROJ%dist\%~1" (
  echo          [!] could not delete dist\%~1 - is it open somewhere?
) else (
  echo          deleted dist\%~1
)
goto :eof
