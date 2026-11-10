@echo off
REM Compile Your_Own_AI -> out\
if not exist out mkdir out
javac -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\yourownai\Main.java
if errorlevel 1 (echo Build FAILED & exit /b 1)
echo Build OK
