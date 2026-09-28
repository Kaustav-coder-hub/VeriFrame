package com.mediaprovenance.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.mediaprovenance.common.ApiException;
import com.mediaprovenance.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryGatewayImpl implements CloudinaryGateway {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryGatewayImpl.class);
    private final Cloudinary cloudinary;
    private final AppProperties appProperties;

    public CloudinaryGatewayImpl(Cloudinary cloudinary, AppProperties appProperties) {
        this.cloudinary = cloudinary;
        this.appProperties = appProperties;
    }

    @Override
    public CloudinaryUploadResult upload(byte[] bytes, String filename, String mediaId) {
        validateImageMagicBytes(bytes);
        String publicId = "veriframe_" + UUID.randomUUID().toString().replace("-", "");

        try {
            Map<?, ?> uploadParams = ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "context", "media_id=" + mediaId + "|filename=" + sanitizeFilename(filename)
            );

            Map<?, ?> result = cloudinary.uploader().upload(bytes, uploadParams);
            return extractUploadResult(result, publicId);
        } catch (Exception e) {
            log.error("Failed to upload image to Cloudinary: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CLOUDINARY_UPLOAD_ERROR", "Cloudinary upload failed", e);
        }
    }

    @Override
    public CloudinaryAiResultDto analyzeAi(String publicId) {
        String categorization = appProperties.getCloudinary().getAutoTaggingCategorization();
        if (categorization == null || categorization.isBlank()) {
            categorization = "aws_rek_tagging";
        }

        try {
            Map<?, ?> result = cloudinary.uploader().explicit(publicId, ObjectUtils.asMap(
                    "type", "upload",
                    "categorization", categorization,
                    "auto_tagging", 0.6
            ));

            String tagsJson = extractTagsJson(result);
            String rawJson = result != null ? result.toString() : "{}";
            return CloudinaryAiResultDto.builder()
                    .provider(categorization)
                    .tagsJson(tagsJson)
                    .rawJson(rawJson)
                    .build();
        } catch (Exception e) {
            log.warn("Cloudinary AI tagging failed or add-on is unavailable for asset [{}]: {}. Degrading gracefully.", publicId, e.getMessage());
            return CloudinaryAiResultDto.builder()
                    .provider("NONE")
                    .tagsJson("[]")
                    .rawJson("{\"warning\":\"AI add-on unavailable or error occurred\"}")
                    .build();
        }
    }

    @Override
    public CloudinaryUploadResult transformAndUpload(String sourcePublicId, String operation, String mediaId) {
        // AI transformations can be non-deterministic. We generate the derived image ONCE with an explicit fixed output format (never f_auto),
        // download those exact bytes, hash them, and re-upload as a new canonical asset.
        String fixedFormatUrl;
        if ("BG_REMOVAL".equalsIgnoreCase(operation)) {
            fixedFormatUrl = cloudinary.url().transformation(
                    new Transformation<>().effect("background_removal").fetchFormat("png")
            ).generate(sourcePublicId);
        } else {
            // Default mandatory CROP operation: c_fill, g_auto, 1080x1080, f_jpg
            fixedFormatUrl = cloudinary.url().transformation(
                    new Transformation<>().crop("fill").gravity("auto").width(1080).height(1080).fetchFormat("jpg")
            ).generate(sourcePublicId);
        }

        byte[] derivedBytes = downloadDerivedBytes(fixedFormatUrl);
        String derivedFilename = "transformed_" + operation.toLowerCase() + ".jpg";
        return upload(derivedBytes, derivedFilename, mediaId);
    }

    @Override
    public String buildOptimizedUrl(String publicId) {
        // f_auto and q_auto are for optimized display delivery ONLY. Output can vary by client request headers, so optimized URLs are NEVER hashed.
        return cloudinary.url().transformation(
                new Transformation<>().fetchFormat("auto").quality("auto")
        ).generate(publicId);
    }

    @Override
    public byte[] downloadDerivedBytes(String derivedUrl) {
        try {
            URL url = URI.create(derivedUrl).toURL();
            String host = url.getHost();
            if (host == null || (!host.equalsIgnoreCase("res.cloudinary.com") && !host.endsWith(".cloudinary.com"))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SSRF_GUARD_TRIGGERED", "Download allowed only from official Cloudinary domain");
            }
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download derived bytes from Cloudinary URL [{}]: {}", derivedUrl, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DOWNLOAD_ERROR", "Failed to fetch derived image bytes", e);
        }
    }

    private void validateImageMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Uploaded file is too small or empty");
        }

        boolean isJpeg = (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
        boolean isPng = (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47);
        boolean isGif = (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46);
        boolean isWebP = (bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46 && bytes[3] == (byte) 0x46
                       && bytes[8] == (byte) 0x57 && bytes[9] == (byte) 0x45 && bytes[10] == (byte) 0x42 && bytes[11] == (byte) 0x50);

        if (!isJpeg && !isPng && !isGif && !isWebP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_TYPE", "File must be a valid image (JPEG, PNG, GIF, or WebP)");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.jpg";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private CloudinaryUploadResult extractUploadResult(Map<?, ?> result, String fallbackPublicId) {
        String publicId = result.get("public_id") != null ? result.get("public_id").toString() : fallbackPublicId;
        String assetId = result.get("asset_id") != null ? result.get("asset_id").toString() : publicId;
        String secureUrl = result.get("secure_url") != null ? result.get("secure_url").toString() : "";
        int width = result.get("width") != null ? Integer.parseInt(result.get("width").toString()) : 0;
        int height = result.get("height") != null ? Integer.parseInt(result.get("height").toString()) : 0;
        long bytes = result.get("bytes") != null ? Long.parseLong(result.get("bytes").toString()) : 0L;

        return CloudinaryUploadResult.builder()
                .publicId(publicId)
                .assetId(assetId)
                .secureUrl(secureUrl)
                .width(width)
                .height(height)
                .bytes(bytes)
                .build();
    }

    private String extractTagsJson(Map<?, ?> result) {
        if (result == null || !result.containsKey("info")) {
            return "[]";
        }
        Object info = result.get("info");
        return info != null ? info.toString() : "[]";
    }
}
