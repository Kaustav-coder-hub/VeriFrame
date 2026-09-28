package com.mediaprovenance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TestDummyController {

    @PostMapping("/media/test")
    public ResponseEntity<Map<String, String>> testWrite() {
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @GetMapping("/media/test")
    public ResponseEntity<Map<String, String>> testRead() {
        return ResponseEntity.ok(Map.of("status", "read_ok"));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> testVerify() {
        return ResponseEntity.ok(Map.of("status", "verify_ok"));
    }
}
