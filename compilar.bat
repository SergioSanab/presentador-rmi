@echo off
cd /d "%~dp0"
if exist clases rmdir /s /q clases
mkdir clases
javac -encoding UTF-8 -d clases src\comun\*.java src\servidor\*.java src\cliente\*.java src\pruebas\*.java
if errorlevel 1 (
  echo.
  echo *** FALLO LA COMPILACION ***
  pause
  exit /b 1
)
echo Compilado en .\clases
pause