package com.mediaprovenance.cloudinary;

public interface CloudinaryGateway {
    CloudinaryUploadResult upload(byte[] bytes, String filename, String mediaId);
    CloudinaryAiResultDto analyzeAi(String publicId);
    CloudinaryUploadResult transformAndUpload(String sourcePublicId, String operation, String mediaId);
    String buildOptimizedUrl(String publicId);
    byte[] downloadDerivedBytes(String derivedUrl);
}
