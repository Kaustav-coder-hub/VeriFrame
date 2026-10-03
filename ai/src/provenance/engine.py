from dataclasses import dataclass
from typing import Optional


@dataclass
class ProvenanceRecord:
    """
    Represents one step in a media's provenance history.
    """

    media_hash: str
    previous_hash: Optional[str]
    operation: str

    def to_dict(self) -> dict:
        """Convert the provenance record to a JSON-compatible dictionary."""
        return {
            "mediaHash": self.media_hash,
            "previousHash": self.previous_hash,
            "operation": self.operation,
        }


class ProvenanceEngine:
    """
    Creates and manages the provenance relationship between
    different versions of a media asset.
    """

    def __init__(self):
        self.records = []

    def register_original(self, media_hash: str) -> ProvenanceRecord:
        """
        Register the original media version.
        """

        record = ProvenanceRecord(
            media_hash=media_hash,
            previous_hash=None,
            operation="ORIGINAL",
        )

        self.records.append(record)

        return record

    def register_derivative(
        self,
        media_hash: str,
        previous_hash: str,
        operation: str,
    ) -> ProvenanceRecord:
        """
        Register a transformed/derived media version.
        """

        record = ProvenanceRecord(
            media_hash=media_hash,
            previous_hash=previous_hash,
            operation=operation,
        )

        self.records.append(record)

        return record

    def get_records(self) -> list[ProvenanceRecord]:
        """Return all provenance records in creation order."""
        return self.records.copy()