package com.kosa.fillinv.schedule.repository;

import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
import com.kosa.fillinv.schedule.entity.ScheduleTime;
import com.kosa.fillinv.schedule.repository.ScheduleParticipantRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScheduleTimeSpecifications {

    public static Specification<ScheduleTime> search(
            String keyword,
            Instant from,
            Instant to,
            ScheduleStatus status,
            String mentorId,
            String menteeId,
            ScheduleParticipantRole role
    ) {
        return Specification
                .where(fetchBooking())
                .and(lessonTitleContains(keyword))
                .and(startTimeAfter(from))
                .and(startTimeBefore(to))
                .and(bookingStatusEq(status))
                .and(participantEq(mentorId, menteeId, role));
    }

    public static Specification<ScheduleTime> startTimeAfter(Instant from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    public static Specification<ScheduleTime> startTimeBefore(Instant to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("startTime"), to);
    }

    public static Specification<ScheduleTime> bookingStatusEq(ScheduleStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;

            Join<ScheduleTime, Schedule> booking =
                    root.join("schedule", JoinType.INNER);

            return cb.equal(booking.get("status"), status);
        };
    }

    public static Specification<ScheduleTime> fetchBooking() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("schedule", JoinType.INNER);
                query.distinct(true);
            }
            return null;
        };
    }

    public static Specification<ScheduleTime> participantEq(
            String mentorId,
            String menteeId,
            ScheduleParticipantRole role
    ) {
        return (root, query, cb) -> {

            if (role == null) {
                return null;
            }

            Join<ScheduleTime, Schedule> booking =
                    root.join("schedule", JoinType.INNER);

            return switch (role) {

                case MENTOR -> {
                    if (mentorId == null) {
                        throw new IllegalArgumentException("MENTOR role requires mentorId");
                    }
                    yield cb.equal(booking.get("mentorId"), mentorId);
                }

                case MENTEE -> {
                    if (menteeId == null) {
                        throw new IllegalArgumentException("MENTEE role requires menteeId");
                    }
                    yield cb.equal(booking.get("menteeId"), menteeId);
                }

                case BOTH -> {
                    List<Predicate> predicates = new ArrayList<>();

                    if (mentorId != null) {
                        predicates.add(cb.equal(booking.get("mentorId"), mentorId));
                    }
                    if (menteeId != null) {
                        predicates.add(cb.equal(booking.get("menteeId"), menteeId));
                    }

                    if (predicates.isEmpty()) {
                        throw new IllegalArgumentException("BOTH role requires mentorId or menteeId");
                    }

                    yield cb.or(predicates.toArray(new Predicate[0]));
                }
            };
        };
    }

    public static Specification<ScheduleTime> lessonTitleContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }

            Join<ScheduleTime, Schedule> booking =
                    root.join("schedule", JoinType.INNER);

            return cb.like(
                    cb.lower(booking.get("lessonTitle")),
                    "%" + keyword.toLowerCase() + "%"
            );
        };
    }
}
