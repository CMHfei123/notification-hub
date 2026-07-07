@echo off

chcp 65001 >nul 2>&1

setlocal enabledelayedexpansion



title NotificationHub Server

cd /d %~dp0

echo Working directory: %CD%



set PYTHON=D:\anaconda\python.exe

if not exist "%PYTHON%" (

    where python >nul 2>&1

    if errorlevel 1 (

        echo [ERROR] Python not found.

        pause

        exit /b 1

    )

    set PYTHON=python

)

%PYTHON% --version



echo Installing deps...

"%PYTHON%" -m pip install -r %~dp0requirements.txt -q



echo.

echo ====== Starting server ======

echo URL: https://localhost:8856

echo Login: admin / admin123

echo.

"%PYTHON%" %~dp0main.py

if errorlevel 1 (

    echo [ERROR] Exit code %errorlevel%

    pause

)

endlocal