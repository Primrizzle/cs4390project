@echo off

rem Compile Java files
javac p01Server.java
javac p01Client.java

rem Start the server
start java p01Server

rem Wait for a few seconds to ensure the server is running
timeout /t 5 /nobreak

rem Start the client
start java p01Client

rem Wait for the user to finish interacting with the client
echo Press any key to stop the server...
pause >nul

rem Terminate the server process
taskkill /f /im java.exe /fi "WindowTitle eq p01Server"

rem Cleanup - Delete compiled Java files
del *.class

echo Server and client demo completed.
pause
