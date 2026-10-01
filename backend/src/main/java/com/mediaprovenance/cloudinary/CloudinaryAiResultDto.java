package com.mediaprovenance.cloudinary;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CloudinaryAiResultDto {
    String provider;
    String tagsJson;
    String rawJson;
}
