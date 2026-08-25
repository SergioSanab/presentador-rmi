@echo off
rem Uso: ejecutar-servidor.bat [ipDeEstaMaquina]
cd /d "%~dp0"
if "%~1"=="" (
  java -cp clases servidor.MainServidor diapositivas 1099
) else (
  java -Djava.rmi.server.hostname=%~1 -cp clases servidor.MainServidor diapositivas 1099
)
