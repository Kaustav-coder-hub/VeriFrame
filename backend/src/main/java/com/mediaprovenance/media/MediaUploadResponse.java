package com.mediaprovenance.media;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class MediaUploadResponse {
    UUID mediaId;
    MediaVersionDto originalVersion;
    Object ai;
    Object provenance;
    boolean duplicate;
}
