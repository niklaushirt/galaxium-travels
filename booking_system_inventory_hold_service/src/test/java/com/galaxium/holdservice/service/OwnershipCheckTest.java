package com.galaxium.holdservice.service;

import com.galaxium.holdservice.client.PythonBackendClient;
import com.galaxium.holdservice.domain.Hold;
import com.galaxium.holdservice.domain.Quote;
import com.galaxium.holdservice.repository.AuditEventRepository;
import com.galaxium.holdservice.repository.HoldRepository;
import com.galaxium.holdservice.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipCheckTest {

    private static final int OWNER_ID = 1;
    private static final int OTHER_ID = 2;

    @Mock
    private HoldRepository holdRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private PythonBackendClient pythonBackendClient;
    @Mock
    private PricingService pricingService;

    private HoldService holdService;
    private QuoteService quoteService;

    private Quote quote;
    private Hold hold;

    @BeforeEach
    void setUp() {
        holdService = new HoldService(holdRepository, quoteRepository, auditEventRepository, pythonBackendClient);
        quoteService = new QuoteService(quoteRepository, auditEventRepository, pricingService);

        quote = Quote.builder()
                .quoteId("Q-1")
                .travelerId(OWNER_ID)
                .travelerName("Jane Doe")
                .flightId(42)
                .seatClass("ECONOMY")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        hold = Hold.builder()
                .holdId("H-1")
                .quoteId("Q-1")
                .status(Hold.HoldStatus.HELD)
                .reservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
    }

    // --- QuoteService.getQuote ---

    @Test
    void getQuoteReturnsQuoteForOwner() {
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertTrue(quoteService.getQuote("Q-1", OWNER_ID).isPresent());
    }

    @Test
    void getQuoteHidesQuoteFromOtherTraveler() {
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertTrue(quoteService.getQuote("Q-1", OTHER_ID).isEmpty());
    }

    // --- HoldService.createHold ---

    @Test
    void createHoldRejectsQuoteOfOtherTraveler() {
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertThrows(IllegalArgumentException.class, () -> holdService.createHold("Q-1", OTHER_ID));
        verify(holdRepository, never()).save(any());
    }

    // --- HoldService.getHold ---

    @Test
    void getHoldReturnsHoldForOwner() {
        when(holdRepository.findById("H-1")).thenReturn(Optional.of(hold));
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertTrue(holdService.getHold("H-1", OWNER_ID).isPresent());
    }

    @Test
    void getHoldHidesHoldFromOtherTraveler() {
        when(holdRepository.findById("H-1")).thenReturn(Optional.of(hold));
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertTrue(holdService.getHold("H-1", OTHER_ID).isEmpty());
    }

    // --- HoldService.confirmHold ---

    @Test
    void confirmHoldRejectsOtherTravelerWithoutCallingBackend() {
        when(holdRepository.findById("H-1")).thenReturn(Optional.of(hold));
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertThrows(IllegalArgumentException.class, () -> holdService.confirmHold("H-1", OTHER_ID));
        verify(pythonBackendClient, never()).createBookingFromHold(any());
        verify(holdRepository, never()).save(any());
    }

    // --- HoldService.releaseHold ---

    @Test
    void releaseHoldRejectsOtherTravelerWithoutChangingStatus() {
        when(holdRepository.findById("H-1")).thenReturn(Optional.of(hold));
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));

        assertThrows(IllegalArgumentException.class, () -> holdService.releaseHold("H-1", OTHER_ID));
        assertEquals(Hold.HoldStatus.HELD, hold.getStatus());
        verify(holdRepository, never()).save(any());
    }

    @Test
    void releaseHoldSucceedsForOwner() {
        when(holdRepository.findById("H-1")).thenReturn(Optional.of(hold));
        when(quoteRepository.findById("Q-1")).thenReturn(Optional.of(quote));
        when(holdRepository.save(any(Hold.class))).thenAnswer(inv -> inv.getArgument(0));

        Hold released = holdService.releaseHold("H-1", OWNER_ID);

        assertEquals(Hold.HoldStatus.RELEASED, released.getStatus());
    }
}
