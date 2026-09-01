@echo off
REM Keeps the BoodschapGemak API alive. Restarts it if it exits - which
REM also covers MySQL not being ready yet at logon, since the server
REM refuses to start without a database and we simply try again.
cd /d "%~dp0.."
:loop
echo [%date% %time%] starting server >> server.log
node src\index.js >> server.log 2>&1
echo [%date% %time%] server exited, retrying in 10s >> server.log
ping -n 11 127.0.0.1 > nul
goto loop
