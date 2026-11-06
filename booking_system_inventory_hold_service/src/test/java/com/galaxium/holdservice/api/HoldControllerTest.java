package com.galaxium.holdservice.api;

import com.galaxium.holdservice.domain.Hold;
import com.galaxium.holdservice.service.HoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HoldController.class)
class HoldControllerTest {

    private static final String TRAVELER_HEADER = "X-Traveler-Id";
    private static final int OWNER_ID = 1;
    private static final int OTHER_ID = 2;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HoldService holdService;

    private Hold buildHeld() {
        return Hold.builder()
                .holdId("H-2025-000001")
                .quoteId("Q-2025-000001")
                .status(Hold.HoldStatus.HELD)
                .reservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Hold buildConfirmed() {
        return Hold.builder()
                .holdId("H-2025-000001")
                .quoteId("Q-2025-000001")
                .status(Hold.HoldStatus.CONFIRMED)
                .reservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .externalBookingReference("BK-999")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Hold buildReleased() {
        return Hold.builder()
                .holdId("H-2025-000001")
                .quoteId("Q-2025-000001")
                .status(Hold.HoldStatus.RELEASED)
                .reservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // --- POST /api/v1/quotes/{quoteId}/holds ---

    @Test
    void testPostHoldValid() throws Exception {
        when(holdService.createHold("Q-2025-000001", OWNER_ID)).thenReturn(buildHeld());

        mockMvc.perform(post("/api/v1/quotes/Q-2025-000001/holds").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value("H-2025-000001"))
                .andExpect(jsonPath("$.status").value("HELD"));
    }

    @Test
    void testPostHoldQuoteNotFound() throws Exception {
        when(holdService.createHold("Q-MISSING", OWNER_ID))
                .thenThrow(new IllegalArgumentException("Quote not found: Q-MISSING"));

        mockMvc.perform(post("/api/v1/quotes/Q-MISSING/holds").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPostHoldQuoteOwnedByOtherTraveler() throws Exception {
        when(holdService.createHold("Q-2025-000001", OTHER_ID))
                .thenThrow(new IllegalArgumentException("Quote not found: Q-2025-000001"));

        mockMvc.perform(post("/api/v1/quotes/Q-2025-000001/holds").header(TRAVELER_HEADER, OTHER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPostHoldExpiredQuote() throws Exception {
        when(holdService.createHold("Q-EXPIRED", OWNER_ID))
                .thenThrow(new IllegalStateException("Quote has expired"));

        mockMvc.perform(post("/api/v1/quotes/Q-EXPIRED/holds").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPostHoldMissingTravelerHeader() throws Exception {
        mockMvc.perform(post("/api/v1/quotes/Q-2025-000001/holds"))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/v1/holds/{holdId} ---

    @Test
    void testGetHoldExists() throws Exception {
        when(holdService.getHold("H-2025-000001", OWNER_ID)).thenReturn(Optional.of(buildHeld()));

        mockMvc.perform(get("/api/v1/holds/H-2025-000001").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value("H-2025-000001"))
                .andExpect(jsonPath("$.status").value("HELD"));
    }

    @Test
    void testGetHoldNotFound() throws Exception {
        when(holdService.getHold("H-MISSING", OWNER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/holds/H-MISSING").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHoldOwnedByOtherTraveler() throws Exception {
        when(holdService.getHold("H-2025-000001", OTHER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/holds/H-2025-000001").header(TRAVELER_HEADER, OTHER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHoldMissingTravelerHeader() throws Exception {
        mockMvc.perform(get("/api/v1/holds/H-2025-000001"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/holds/{holdId}/confirm ---

    @Test
    void testPostConfirmHoldValid() throws Exception {
        when(holdService.confirmHold("H-2025-000001", OWNER_ID)).thenReturn(buildConfirmed());

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/confirm").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.externalBookingReference").value("BK-999"));
    }

    @Test
    void testPostConfirmHoldNotFound() throws Exception {
        when(holdService.confirmHold("H-MISSING", OWNER_ID))
                .thenThrow(new IllegalArgumentException("Hold not found: H-MISSING"));

        mockMvc.perform(post("/api/v1/holds/H-MISSING/confirm").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPostConfirmHoldOwnedByOtherTraveler() throws Exception {
        when(holdService.confirmHold("H-2025-000001", OTHER_ID))
                .thenThrow(new IllegalArgumentException("Hold not found: H-2025-000001"));

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/confirm").header(TRAVELER_HEADER, OTHER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPostConfirmHoldInvalidStatus() throws Exception {
        when(holdService.confirmHold("H-2025-000001", OWNER_ID))
                .thenThrow(new IllegalStateException("Hold is not in HELD status: RELEASED"));

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/confirm").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/holds/{holdId}/release ---

    @Test
    void testPostReleaseHoldValid() throws Exception {
        when(holdService.releaseHold("H-2025-000001", OWNER_ID)).thenReturn(buildReleased());

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/release").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void testPostReleaseHoldOwnedByOtherTraveler() throws Exception {
        when(holdService.releaseHold("H-2025-000001", OTHER_ID))
                .thenThrow(new IllegalArgumentException("Hold not found: H-2025-000001"));

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/release").header(TRAVELER_HEADER, OTHER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPostReleaseHoldInvalidStatus() throws Exception {
        when(holdService.releaseHold("H-2025-000001", OWNER_ID))
                .thenThrow(new IllegalStateException("Hold cannot be released, current status: CONFIRMED"));

        mockMvc.perform(post("/api/v1/holds/H-2025-000001/release").header(TRAVELER_HEADER, OWNER_ID))
                .andExpect(status().isBadRequest());
    }
}
