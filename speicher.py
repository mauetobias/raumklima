"""Kleiner verschlüsselter Speicher zwischen den Läufen.

Liegt im Ordner speicher/, den der Workflow über den GitHub-Actions-Cache weiterreicht.
Alles ist mit TOKEN_SCHLUESSEL verschlüsselt, der nur in den Secrets steht.
"""
import os
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken

ORDNER = Path(os.environ.get("SPEICHER", "speicher"))


def _fernet():
    return Fernet(os.environ["TOKEN_SCHLUESSEL"].strip().encode())


def lesen(name):
    datei = ORDNER / f"{name}.enc"
    if not datei.exists():
        return None
    try:
        return _fernet().decrypt(datei.read_bytes()).decode()
    except InvalidToken:
        return None


def schreiben(name, text):
    ORDNER.mkdir(exist_ok=True)
    (ORDNER / f"{name}.enc").write_bytes(_fernet().encrypt(text.encode()))
