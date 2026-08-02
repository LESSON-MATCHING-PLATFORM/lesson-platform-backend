package com.kosa.fillinv.booking.entity;

import com.kosa.fillinv.global.entity.BaseEntity;
import com.kosa.fillinv.global.exception.ResourceException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "schedules")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @Id
    @Column(name = "schedule_id", nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "request_content")
    private String requestContent;

    /* ===== Lesson Snapshot ===== */
    @Column(name = "lesson_title", nullable = false)
    private String lessonTitle;

    @Column(name = "lesson_type", nullable = false)
    private String lessonType;

    @Column(name = "lesson_description", nullable = false)
    private String lessonDescription;

    @Column(name = "lesson_location", nullable = false)
    private String lessonLocation;

    @Column(name = "lesson_category_name", nullable = false)
    private String lessonCategoryName;

    @Column(name = "mentor_nickname", nullable = false)
    private String mentorNickname;

    /* ===== Option Snapshot ===== */
    /* 옵션은 MENTORING 레슨만 사용 */
    @Column(name = "option_name")
    private String optionName;

    @Column(name = "option_minute")
    private Integer optionMinute;

    @Column(name = "price")
    private Integer price;

    /* 외부 테이블 키 */
    @Column(name = "lesson_id", nullable = false)
    private String lessonId;

    @Column(name = "mentee_id", nullable = false)
    private String menteeId;

    @Column(name = "lesson_mentor_id", nullable = false)
    private String mentorId;

    // MENTORING 레슨에서 사용
    @Column(name = "option_id")
    private String optionId;

    // ONEDAY 레슨에서 사용
    @Column(name = "available_time_id")
    private String availableTimeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private BookingCancelReason cancelReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    // STUDY 레슨은 여러 회차를 가질 수 있기 때문에 List 사용
    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingSession> sessions = new ArrayList<>();

    public void addSession(BookingSession session) {
        session.setBooking(this);
        this.sessions.add(session);
    }

    public void addSessions(List<BookingSession> sessions) {
        sessions.forEach(this::addSession);
    }

    // Booking 상태 변경 메서드
    public void updateStatus(BookingStatus bookingStatus) {
        this.status = bookingStatus;
    }

    public boolean cancel(BookingCancelReason reason) {
        if (status == BookingStatus.CANCELED) {
            return false;
        }

        this.status = BookingStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = Instant.now();
        return true;
    }

    public void markPaymentCompleted() {
        if (status != BookingStatus.PAYMENT_PENDING) return;
        this.status = BookingStatus.APPROVAL_PENDING;
    }

    public void validateMentor(String mentorId) {
        if (mentorId == null || !mentorId.equals(this.mentorId)) {
            throw new ResourceException.AccessDenied("Booking에 참여하는 멘토만 접근가능합니다.");
        }
    }

    public void validateParticipant(String memberId) {
        if (memberId == null || (!memberId.equals(this.menteeId) && !memberId.equals(this.mentorId))) {
            throw new ResourceException.AccessDenied("Booking에 참여하는 멘토 또는 멘티만 접근가능합니다.");
        }
    }

    public void validateMentee(String memberId) {
        if (memberId == null || !memberId.equals(this.menteeId)) {
            throw new ResourceException.AccessDenied("Booking에 참여하는 멘티만 접근가능합니다.");
        }
    }

    public String getRole(String memberId) {
        if (memberId == null || memberId.isBlank()) return "NONE";

        if (memberId.equals(this.menteeId)) {
            return "MENTEE";
        } else if (memberId.equals(this.mentorId)) {
            return "MENTOR";
        } else {
            return "NONE";
        }
    }
}
