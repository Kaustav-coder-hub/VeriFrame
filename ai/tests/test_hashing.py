import hashlib

from ai.src.hashing.hash import sha256_file


def test_sha256_file(tmp_path):
    test_file = tmp_path / "test.txt"
    content = b"VeriFrame"
    test_file.write_bytes(content)

    expected = hashlib.sha256(content).hexdigest()

    assert sha256_file(str(test_file)) == expected