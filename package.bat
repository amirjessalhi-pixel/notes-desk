@echo off
rem Builds a standalone NotesDesk.exe (dist\NotesDesk) with a trimmed, bundled Java runtime via jlink + jpackage.
setlocal
cd /d "%~dp0"
call build.bat || exit /b 1
if exist target\runtime rmdir /s /q target\runtime
jlink --add-modules java.base,java.desktop --strip-debug --no-header-files --no-man-pages --output target\runtime || exit /b 1
if exist target\input rmdir /s /q target\input
mkdir target\input
copy target\notes-desk.jar target\input\ >nul
if exist dist rmdir /s /q dist
jpackage --type app-image --input target\input --dest dist --name NotesDesk --main-jar notes-desk.jar --main-class com.notesdesk.Main --runtime-image target\runtime || exit /b 1
echo Packaged dist\NotesDesk\NotesDesk.exe
