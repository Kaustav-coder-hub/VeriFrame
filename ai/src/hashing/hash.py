import hashlib
from pathlib import Path


def sha256_file(file_path: str) -> str:
    """
    Calculate the SHA-256 hash of a file.

    The hash is calculated from the exact bytes of the file,
    making it suitable for VeriFrame media identity.
    """

    path = Path(file_path)

    if not path.is_file():
        raise FileNotFoundError(f"File not found: {file_path}")

    sha256 = hashlib.sha256()

    with path.open("rb") as file:
        while chunk := file.read(8192):
            sha256.update(chunk)

    return sha256.hexdigest()