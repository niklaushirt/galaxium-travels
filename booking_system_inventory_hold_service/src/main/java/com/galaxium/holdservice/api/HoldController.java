package com.galaxium.holdservice.api;

import com.galaxium.holdservice.domain.Hold;
import com.galaxium.holdservice.service.HoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class HoldController {

    static final String TRAVELER_ID_HEADER = "X-Traveler-Id";

    private final HoldService holdService;

    @PostMapping("/quotes/{quoteId}/holds")
    public ResponseEntity<Hold> createHold(@PathVariable String quoteId,
                                           @RequestHeader(TRAVELER_ID_HEADER) Integer travelerId) {
        log.info("POST /api/v1/quotes/{}/holds - Creating hold", quoteId);
        try {
            Hold hold = holdService.createHold(quoteId, travelerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(hold);
        } catch (IllegalArgumentException e) {
            log.error("Invalid quote ID: {}", quoteId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot create hold: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/holds/{holdId}")
    public ResponseEntity<Hold> getHold(@PathVariable String holdId,
                                        @RequestHeader(TRAVELER_ID_HEADER) Integer travelerId) {
        log.info("GET /api/v1/holds/{} - Retrieving hold", holdId);
        return holdService.getHold(holdId, travelerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/holds/{holdId}/confirm")
    public ResponseEntity<Hold> confirmHold(@PathVariable String holdId,
                                            @RequestHeader(TRAVELER_ID_HEADER) Integer travelerId) {
        log.info("POST /api/v1/holds/{}/confirm - Confirming hold", holdId);
        try {
            Hold hold = holdService.confirmHold(holdId, travelerId);
            return ResponseEntity.ok(hold);
        } catch (IllegalArgumentException e) {
            log.error("Hold not found: {}", holdId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot confirm hold: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/holds/{holdId}/release")
    public ResponseEntity<Hold> releaseHold(@PathVariable String holdId,
                                            @RequestHeader(TRAVELER_ID_HEADER) Integer travelerId) {
        log.info("POST /api/v1/holds/{}/release - Releasing hold", holdId);
        try {
            Hold hold = holdService.releaseHold(holdId, travelerId);
            return ResponseEntity.ok(hold);
        } catch (IllegalArgumentException e) {
            log.error("Hold not found: {}", holdId, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot release hold: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}

// Made with Bob
