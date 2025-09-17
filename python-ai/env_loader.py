import os
from pathlib import Path
from dotenv import load_dotenv


def load_env():
    """
    Load environment variables from .env files with sensible precedence.

    Search order (earlier can be overridden by later ones):
    1. <workspace>/.env
    2. <backend>/.env
    3. <python-ai>/.env
    4. <workspace>/.env.development (or .env.dev)
    5. <backend>/.env.development (or .env.dev)
    6. <python-ai>/.env.development (or .env.dev)
    7. <workspace>/.env.local
    8. <backend>/.env.local
    9. <python-ai>/.env.local

    Later files override earlier ones to let local dev override shared values.
    """
    here = Path(__file__).resolve().parent  # python-ai
    backend = here.parent  # backend folder
    workspace = backend.parent  # repo root

    candidates = [
        workspace / ".env",
        backend / ".env",
        here / ".env",
        workspace / ".env.development",
        workspace / ".env.dev",
        backend / ".env.development",
        backend / ".env.dev",
        here / ".env.development",
        here / ".env.dev",
        workspace / ".env.local",
        backend / ".env.local",
        here / ".env.local",
    ]

    loaded_any = False
    for p in candidates:
        if p.exists():
            # Allow later files to override earlier ones
            load_dotenv(p, override=True)
            loaded_any = True
    return loaded_any
