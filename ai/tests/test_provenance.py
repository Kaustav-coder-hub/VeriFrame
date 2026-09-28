from ai.src.provenance.models import create_media_identity
from ai.src.provenance.engine import ProvenanceEngine


def test_create_original_media_identity():
    media = create_media_identity(
        media_id="VF-001",
        media_hash="50d315066d23ce6e8696dae79412108084cb68071ee608d441104a922a4c5dbd",
        mime_type="image/jpeg",
        size=245631,
    )

    assert media.media_id == "VF-001"
    assert media.media_hash == "50d315066d23ce6e8696dae79412108084cb68071ee608d441104a922a4c5dbd"
    assert media.algorithm == "SHA-256"
    assert media.mime_type == "image/jpeg"
    assert media.size == 245631
    assert media.operation == "ORIGINAL"
    assert media.created_at is not None


def test_register_original():
    engine = ProvenanceEngine()

    record = engine.register_original("HASH_A")

    assert record.media_hash == "HASH_A"
    assert record.previous_hash is None
    assert record.operation == "ORIGINAL"


def test_register_derivative():
    engine = ProvenanceEngine()

    engine.register_original("HASH_A")

    record = engine.register_derivative(
        media_hash="HASH_B",
        previous_hash="HASH_A",
        operation="CROP",
    )

    assert record.media_hash == "HASH_B"
    assert record.previous_hash == "HASH_A"
    assert record.operation == "CROP"


def test_provenance_chain():
    engine = ProvenanceEngine()

    original = engine.register_original("HASH_A")

    cropped = engine.register_derivative(
        media_hash="HASH_B",
        previous_hash=original.media_hash,
        operation="CROP",
    )

    enhanced = engine.register_derivative(
        media_hash="HASH_C",
        previous_hash=cropped.media_hash,
        operation="ENHANCE",
    )

    records = engine.get_records()

    assert len(records) == 3

    assert records[0].media_hash == "HASH_A"
    assert records[0].previous_hash is None
    assert records[0].operation == "ORIGINAL"

    assert records[1].media_hash == "HASH_B"
    assert records[1].previous_hash == "HASH_A"
    assert records[1].operation == "CROP"

    assert records[2].media_hash == "HASH_C"
    assert records[2].previous_hash == "HASH_B"
    assert records[2].operation == "ENHANCE"