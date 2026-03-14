package io.github.sukjoonhong.pointledger.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointAlertService {
    private final Logger logger = LoggerFactory.getLogger(PointAlertService.class);

    void alert(String message) {
        logger.error(message);
    }
}
