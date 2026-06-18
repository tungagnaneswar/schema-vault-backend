package com.gnanadhan.app.service;

import com.gnanadhan.app.entity.DBHealth;
import com.gnanadhan.app.repository.DBHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DBHealthService {

    private final DBHealthRepository dbHealthRepository;

    public DBHealth checkDbHealth() {
        boolean isUp = dbHealthRepository.checkDbConnection();
        if (isUp) {
            return new DBHealth(true, "Database connection is healthy");
        } else {
            return new DBHealth(false, "Database connection failed");
        }
    }
}
