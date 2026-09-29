package com.mediaprovenance.cloudinary;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CloudinaryUploadResult {
    String publicId;
    String assetId;
    String secureUrl;
    byte[] derivedBytes;
    int width;
    int height;
    long bytes;
}
