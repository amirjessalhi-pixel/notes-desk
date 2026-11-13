@echo off
rem Compiles, runs the self-check, and packages target\notes-desk.jar. Needs JDK 17+.
setlocal
cd /d "%~dp0"
if exist target rmdir /s /q target
dir /s /b src\main\java\*.java > "%TEMP%\nd-main.txt"
dir /s /b src\test\java\*.java > "%TEMP%\nd-test.txt"
javac --release 17 -d target\classes @"%TEMP%\nd-main.txt" || exit /b 1
javac --release 17 -cp target\classes -d target\test-classes @"%TEMP%\nd-test.txt" || exit /b 1
java -ea -cp target\classes;target\test-classes com.notesdesk.StorageTest || exit /b 1
jar --create --file target\notes-desk.jar --main-class com.notesdesk.Main -C target\classes . || exit /b 1
echo Built target\notes-desk.jar  -^>  java -jar target\notes-desk.jar
