package com.gnanadhan.app.controller;

import com.gnanadhan.app.entity.DBHealth;
import com.gnanadhan.app.service.DBHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DBHealthController {

    private final DBHealthService dbHealthService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/health/db")
    public ResponseEntity<DBHealth> healthDb() {
        DBHealth dbHealth = dbHealthService.checkDbHealth();
        if (dbHealth.isStatus()) {
            return ResponseEntity.ok(dbHealth);
        } else {
            return ResponseEntity.status(503).body(dbHealth);
        }
    }
}
