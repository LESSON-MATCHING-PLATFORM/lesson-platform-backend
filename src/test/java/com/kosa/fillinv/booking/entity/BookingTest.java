package com.kosa.fillinv.booking.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    @Test
    void addSession() {
        Booking booking = new Booking();

        BookingSession session = session();

        booking.addSession(session);

        assertThat(session.getBooking()).isEqualTo(booking);
        assertThat(booking.getSessions()).hasSize(1);
    }

    @Test
    void addMultipleSessions() {
        Booking booking = new Booking();

        List<BookingSession> sessions = List.of(
                session(),
                session(),
                session()
        );

        booking.addSessions(sessions);

        assertThat(booking.getSessions()).hasSize(3);
        assertThat(booking.getSessions()).containsAll(sessions);
        sessions.stream().map(BookingSession::getBooking).forEach(s -> assertThat(s).isEqualTo(booking));
    }

    private BookingSession session() {
        Instant startTime = Instant.parse("2026-01-01T00:00:00Z");
        return BookingSession.of(startTime, startTime.plusSeconds(3600), null);
    }
}
