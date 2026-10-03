from ai.src.provenance.engine import ProvenanceEngine
from ai.src.verification.verifier import (
    VerificationEngine,
    VERIFIED,
    MISMATCH,
    NOT_FOUND,
)


def test_verified_hash():
    verifier = VerificationEngine()

    result = verifier.verify_hash(
        current_hash="HASH_A",
        registered_hash="HASH_A",
    )

    assert result.status == VERIFIED
    assert result.current_hash == "HASH_A"
    assert result.registered_hash == "HASH_A"


def test_mismatched_hash():
    verifier = VerificationEngine()

    result = verifier.verify_hash(
        current_hash="HASH_X",
        registered_hash="HASH_A",
    )

    assert result.status == MISMATCH
    assert result.current_hash == "HASH_X"
    assert result.registered_hash == "HASH_A"


def test_verified_against_provenance():
    provenance = ProvenanceEngine()

    original = provenance.register_original("HASH_A")

    verifier = VerificationEngine()

    result = verifier.verify_against_records(
        current_hash="HASH_A",
        records=provenance.get_records(),
    )

    assert result.status == VERIFIED
    assert result.record == original


def test_not_found():
    provenance = ProvenanceEngine()

    provenance.register_original("HASH_A")

    verifier = VerificationEngine()

    result = verifier.verify_against_records(
        current_hash="HASH_X",
        records=provenance.get_records(),
    )

    assert result.status == NOT_FOUND
    assert result.current_hash == "HASH_X"
    assert result.record is None


def test_verify_derived_version():
    provenance = ProvenanceEngine()

    provenance.register_original("HASH_A")

    cropped = provenance.register_derivative(
        media_hash="HASH_B",
        previous_hash="HASH_A",
        operation="CROP",
    )

    verifier = VerificationEngine()

    result = verifier.verify_against_records(
        current_hash="HASH_B",
        records=provenance.get_records(),
    )


    assert result.status == VERIFIED
    assert result.record == cropped
    assert result.record is not None

    assert result.record.previous_hash == "HASH_A"
    assert result.record.operation == "CROP"