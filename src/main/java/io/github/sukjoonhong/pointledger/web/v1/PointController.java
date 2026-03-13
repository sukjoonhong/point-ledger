package io.github.sukjoonhong.pointledger.web.v1;

import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.PointMessagePublisher;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.local.LocalPointQueueManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointMessagePublisher messagePublisher;

    @PostMapping("/enqueue")
    public ResponseEntity<Void> enqueueEvent(@RequestBody PointCommand command) {
        messagePublisher.publish(command);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}