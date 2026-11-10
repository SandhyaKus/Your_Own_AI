@echo off
REM Build and start the server. Run from the project root.
call build.bat || exit /b 1
java -cp out yourownai.Main
