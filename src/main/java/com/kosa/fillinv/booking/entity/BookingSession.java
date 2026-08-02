package com.kosa.fillinv.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_times")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor
public class BookingSession {

    @Id
    @Column(name = "schedule_time_id", nullable = false)
    private String id;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @ManyToOne
    @Setter
    @JoinColumn(name = "schedule_id", nullable = false)
    private Booking booking;

    public static BookingSession of(Instant startTime, Instant endTime, Booking booking) {
        return new BookingSession(UUID.randomUUID().toString(), startTime, endTime, booking);
    }
}
