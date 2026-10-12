package com.campus.complaints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RestController
@RequestMapping("/api/health")
public class DatabaseHealthController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthController.class);

    private final DataSource dataSource;

    public DatabaseHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/database")
    public ResponseEntity<DatabaseHealthResponse> databaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(new DatabaseHealthResponse("UP"));
            }
        } catch (SQLException exception) {
            logger.warn("Database health check failed");
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new DatabaseHealthResponse("UNAVAILABLE"));
    }
}
