from dataclasses import dataclass
from typing import Optional

from ai.src.provenance.engine import ProvenanceRecord


VERIFIED = "VERIFIED"
MISMATCH = "MISMATCH"
NOT_FOUND = "NOT_FOUND"


@dataclass
class VerificationResult:
    """
    Result produced by the VeriFrame verification engine.
    """

    status: str
    current_hash: str
    registered_hash: Optional[str] = None
    record: Optional[ProvenanceRecord] = None

    def to_dict(self) -> dict:
        """Convert the result into a JSON-compatible dictionary."""

        return {
            "status": self.status,
            "currentHash": self.current_hash,
            "registeredHash": self.registered_hash,
            "record": (
                self.record.to_dict()
                if self.record is not None
                else None
            ),
        }


class VerificationEngine:
    """
    Verifies media hashes against registered provenance records.
    """

    def verify_hash(
        self,
        current_hash: str,
        registered_hash: str,
        record: Optional[ProvenanceRecord] = None,
    ) -> VerificationResult:
        """
        Compare the submitted hash with an expected registered hash.
        """

        if current_hash == registered_hash:
            return VerificationResult(
                status=VERIFIED,
                current_hash=current_hash,
                registered_hash=registered_hash,
                record=record,
            )

        return VerificationResult(
            status=MISMATCH,
            current_hash=current_hash,
            registered_hash=registered_hash,
            record=record,
        )

    def verify_against_records(
        self,
        current_hash: str,
        records: list[ProvenanceRecord],
    ) -> VerificationResult:
        """
        Search registered provenance records for an exact hash.

        If the hash exists, the media is VERIFIED.
        If no record exists, the result is NOT_FOUND.
        """

        for record in records:
            if record.media_hash == current_hash:
                return VerificationResult(
                    status=VERIFIED,
                    current_hash=current_hash,
                    registered_hash=record.media_hash,
                    record=record,
                )

        return VerificationResult(
            status=NOT_FOUND,
            current_hash=current_hash,
        )