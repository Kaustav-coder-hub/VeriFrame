from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional


@dataclass
class MediaIdentity:
    """
    Represents the cryptographic identity of a media asset
    inside VeriFrame.
    """

    media_id: str
    media_hash: str
    algorithm: str
    mime_type: str
    size: int
    operation: str
    created_at: str

    def to_dict(self) -> dict:
        """Convert the media identity into a JSON-compatible dictionary."""
        return {
            "mediaId": self.media_id,
            "hash": self.media_hash,
            "algorithm": self.algorithm,
            "mimeType": self.mime_type,
            "size": self.size,
            "operation": self.operation,
            "createdAt": self.created_at,
        }


def create_media_identity(
    media_id: str,
    media_hash: str,
    mime_type: str,
    size: int,
    operation: str = "ORIGINAL",
) -> MediaIdentity:
    """
    Create a MediaIdentity object for a media asset.
    """

    return MediaIdentity(
        media_id=media_id,
        media_hash=media_hash,
        algorithm="SHA-256",
        mime_type=mime_type,
        size=size,
        operation=operation,
        created_at=datetime.now(timezone.utc).isoformat(),
    )