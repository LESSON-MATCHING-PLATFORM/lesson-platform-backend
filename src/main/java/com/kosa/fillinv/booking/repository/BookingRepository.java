package com.kosa.fillinv.booking.repository;

import com.kosa.fillinv.lesson.service.dto.BookedTimeVO;
import com.kosa.fillinv.lesson.service.dto.LessonCountVO;
import com.kosa.fillinv.review.dto.UnwrittenReviewVO;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {


    // 레슨별 Booking 목록 조회
    Page<Booking> findByLessonId(String lessonId, Pageable pageable);

    // 상태 일치 Booking 찾기
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    @Query("SELECT new com.kosa.fillinv.review.dto.UnwrittenReviewVO(s.id, s.lessonTitle, s.lessonId, s.optionName, s.createdAt, m.nickname) " +
            "FROM Booking s " +
            "JOIN Lesson l ON s.lessonId = l.id " +
            "JOIN Member m ON l.mentorId = m.id " +
            "WHERE s.menteeId = :menteeId " +
            "AND s.status = com.kosa.fillinv.booking.entity.BookingStatus.COMPLETED " +
            "AND NOT EXISTS (SELECT r FROM Review r WHERE r.scheduleId = s.id)")
    Page<UnwrittenReviewVO> findUnwrittenReviews(@Param("menteeId") String menteeId, Pageable pageable);

    // 멘티 Booking 조회 (Batch Fetch Size가 N+1 문제를 알아서 최적화)
    Page<Booking> findByMenteeId(String memberId, Pageable pageable);

    // 멘토 Booking 조회 (Batch Fetch Size가 N+1 문제를 알아서 최적화)
    Page<Booking> findByMentorId(String memberId, Pageable pageable);

    // 멤버(멘토 또는 멘티) 관련 Booking을 필터링하여 조회 (시작 시간으로 오름차순 정렬)
    @Query("SELECT s FROM Booking s " +
            "LEFT JOIN s.sessions st " +
            "WHERE (s.mentorId = :memberId OR s.menteeId = :memberId) " + // Booking의 멘토나 멘티가 로그인한 사람의 경우를 찾기
            "AND (:title IS NULL OR s.lessonTitle LIKE %:title%) " + // 제목 필터
            "AND (:start IS NULL OR st.startTime >= :start) " + // 시작 지점 조건
            "AND (:end IS NULL OR st.startTime < :end) " + // 종료 지점 조건
            "AND (:status IS NULL OR s.status = :status)")
    // 기본 정렬 (과거 조회의 경우 Pageable에서 DESC)
    Page<Booking> findAllByMemberIdWithFilter(
            @Param("memberId") String memberId,
            @Param("title") String title,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    BookingStatus status(BookingStatus status);

    @Query("SELECT new com.kosa.fillinv.lesson.service.dto.LessonCountVO(s.lessonId, COUNT(s)) " +
            "FROM Booking s " +
            "WHERE s.lessonId IN :lessonIds AND s.status IN :statuses " +
            "GROUP BY s.lessonId")
    List<LessonCountVO> countByLessonIdInAndStatusIn(@Param("lessonIds") Collection<String> lessonIds, @Param("statuses") Collection<BookingStatus> statuses);

    Long countByLessonIdAndStatusIn(String lessonId, Collection<BookingStatus> statuses);

    @Query("SELECT s.lessonId, COUNT(s) FROM Booking s JOIN Lesson l ON s.lessonId = l.id WHERE s.createdAt >= :startDate AND s.deletedAt IS NULL AND l.deletedAt IS NULL GROUP BY s.lessonId")
    List<Object[]> countByLessonIdAndCreatedAtAfter(@Param("startDate") Instant startDate);

    @Query("SELECT new com.kosa.fillinv.lesson.service.dto.BookedTimeVO(st.startTime, st.endTime) " +
            "FROM Booking s " +
            "JOIN s.sessions st " +
            "WHERE s.lessonId = :lessonId AND s.status IN :statuses AND st.startTime >= :since")
    List<BookedTimeVO> findBookedTimesByLessonIdAndStatusInAndStartTimeAfter(@Param("lessonId") String lessonId, @Param("statuses") Collection<BookingStatus> statuses, @Param("since") Instant since);
}
