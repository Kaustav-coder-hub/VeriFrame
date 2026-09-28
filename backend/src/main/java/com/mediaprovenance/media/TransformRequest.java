package com.mediaprovenance.media;

import lombok.Data;
import java.util.UUID;

@Data
public class TransformRequest {
    private String operation; // CROP | BG_REMOVAL
    private UUID sourceVersionId; // optional — defaults to latest version
}
