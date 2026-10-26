#!/usr/bin/env python3
"""Backward-compatible command name: the active fork now uses scoped v0.2.0 checks."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("verify-scoped-sharing.py")), run_name="__main__")
