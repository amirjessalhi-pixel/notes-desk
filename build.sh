#!/usr/bin/env sh
# Compiles, runs the self-check, and packages target/notes-desk.jar. Needs JDK 17+.
set -e
cd "$(dirname "$0")"
rm -rf target
javac --release 17 -d target/classes $(find src/main/java -name '*.java')
javac --release 17 -cp target/classes -d target/test-classes $(find src/test/java -name '*.java')
SEP=:; case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
java -ea -cp "target/classes${SEP}target/test-classes" com.notesdesk.StorageTest
jar --create --file target/notes-desk.jar --main-class com.notesdesk.Main -C target/classes .
echo "Built target/notes-desk.jar  ->  java -jar target/notes-desk.jar"
