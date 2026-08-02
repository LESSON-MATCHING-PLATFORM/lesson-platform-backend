package com.kosa.fillinv.review.repository;

import com.kosa.fillinv.review.dto.LessonAvgScore;
import com.kosa.fillinv.review.dto.MyReviewVO;
import com.kosa.fillinv.review.dto.ReviewStatsDTO;
import com.kosa.fillinv.review.dto.ReviewWithNicknameVO;
import com.kosa.fillinv.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {

    @Query("SELECT AVG(r.score) FROM Review r WHERE r.lessonId = :lessonId")
    Double findAverageScoreByLessonId(@Param("lessonId") String lessonId);

    @Query("SELECT new com.kosa.fillinv.review.dto.LessonAvgScore(r.lessonId, AVG(r.score)) FROM Review r WHERE r.lessonId IN :lessonIds GROUP BY r.lessonId")
    List<LessonAvgScore> findAverageScoreByLessonIds(@Param("lessonIds") Collection<String> lessonIds);

    @EntityGraph(attributePaths = {"writer"})
    @Query("SELECT new com.kosa.fillinv.review.dto.ReviewWithNicknameVO(r, r.writer.nickname) " +
            "FROM Review r " +
            "WHERE r.lessonId = :lessonId")
    Page<ReviewWithNicknameVO> findReviewsWithNicknameByLessonId(@Param("lessonId") String lessonId, Pageable pageable);

    @Query("SELECT new com.kosa.fillinv.review.dto.MyReviewVO(" +
            "r, s.lessonTitle, s.optionName, s.createdAt, m.nickname) " +
            "FROM Review r " +
            "JOIN r.booking s " +
            "JOIN Lesson l ON s.lessonId = l.id " +
            "JOIN Member m ON l.mentorId = m.id " +
            "WHERE r.writerId = :writerId")
    Page<MyReviewVO> findByWriterId(@Param("writerId") String writerId, Pageable pageable);

    boolean existsByScheduleId(String scheduleId);

    @Query("SELECT r.lessonId, COUNT(r), AVG(r.score) FROM Review r JOIN Lesson l ON r.lessonId = l.id WHERE r.deletedAt IS NULL AND l.deletedAt IS NULL GROUP BY r.lessonId")
    List<ReviewStatsDTO> findReviewStatsByLessonId();

}
