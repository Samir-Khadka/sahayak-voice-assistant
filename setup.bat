@echo off
REM Sahayak — Windows setup. Run once: double-click setup.bat
setlocal
cd /d %~dp0

where python >nul 2>nul || (echo [ERROR] Install Python 3.10+ from python.org first. & pause & exit /b 1)

echo [1/4] Creating virtual environment...
python -m venv .venv
call .venv\Scripts\activate

echo [2/4] Installing packages...
pip install -r requirements.txt
pip install pycaw comtypes pywin32

echo [3/4] Downloading offline English voice (Piper)...
mkdir voices 2>nul
powershell -Command "Invoke-WebRequest -Uri 'https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx' -OutFile 'voices\en_US-lessac-medium.onnx'"
powershell -Command "Invoke-WebRequest -Uri 'https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json' -OutFile 'voices\en_US-lessac-medium.onnx.json'"

echo [4/4] First-run config...
.venv\Scripts\python -c "from sahayak.config import Config; c=Config(); c['piper_voices']={'en':'voices/en_US-lessac-medium.onnx','ne':''}; c.save(); print('token:', c['server_token'])"

echo.
echo Done! Your server token is printed above - enter it in the Android app.
echo   Talk on laptop : .venv\Scripts\python -m sahayak.main
echo   Phone server   : .venv\Scripts\python -m sahayak.main --server
echo   One command    : .venv\Scripts\python -m sahayak.main --text "what time is it"
pause
