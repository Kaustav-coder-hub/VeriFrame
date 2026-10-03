package com.mediaprovenance.media;

import com.mediaprovenance.verification.VerificationResponse;
import com.mediaprovenance.verification.VerificationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private final MediaService mediaService;
    private final VerificationService verificationService;

    public MediaController(MediaService mediaService, VerificationService verificationService) {
        this.mediaService = mediaService;
        this.verificationService = verificationService;
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadResponse> upload(
            @RequestPart("file") MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        MediaUploadResponse response = mediaService.upload(fileBytes, originalFilename);
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping(value = "/media/{mediaId}/transform", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MediaVersionDto> transform(
            @PathVariable UUID mediaId,
            @RequestBody TransformRequest request) {

        MediaVersionDto result = mediaService.transform(mediaId, request.getOperation(), request.getSourceVersionId());
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<MediaPassportDto> getPassport(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(mediaService.getPassport(mediaId));
    }

    @GetMapping("/media/{mediaId}/provenance")
    public ResponseEntity<List<ProvenanceDto>> getProvenance(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(mediaService.getProvenance(mediaId));
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> verify(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mediaId", required = false) UUID mediaId) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        byte[] fileBytes = file.getBytes();
        VerificationResponse result = verificationService.verify(fileBytes, mediaId);

        HttpStatus status = switch (result.getStatus()) {
            case "VERIFIED" -> HttpStatus.OK;
            case "MISMATCH" -> HttpStatus.OK;
            case "NOT_FOUND" -> HttpStatus.OK;
            case "PENDING" -> HttpStatus.CONFLICT;
            case "PROVENANCE_FAILED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.OK;
        };

        return ResponseEntity.status(status).body(result);
    }
}
