package com.classhub.health;

import com.classhub.common.api.ApiResponse;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadyController {

    private static final Logger log = LoggerFactory.getLogger(ReadyController.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public ReadyController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return ResponseEntity.ok(ApiResponse.of(Map.of("status", "READY")));
            }
        } catch (Exception ex) {
            log.warn("Readiness check failed");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.of(Map.of("status", "NOT_READY")));
    }
}
