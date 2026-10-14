@echo off
pip install -r requirements.txt
pyinstaller --onefile --noconsole --name TW_Market --collect-all winrt --hidden-import uuid main.py
echo dist\TW_Market.exe
