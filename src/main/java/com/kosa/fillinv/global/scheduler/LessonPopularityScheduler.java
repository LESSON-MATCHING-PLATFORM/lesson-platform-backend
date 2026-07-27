package com.kosa.fillinv.global.scheduler;

import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonTemp;
import com.kosa.fillinv.lesson.repository.LessonBulkRepository;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.lesson.repository.LessonTempRepository;
import com.kosa.fillinv.review.dto.ReviewStatsDTO;
import com.kosa.fillinv.review.repository.ReviewRepository;
import com.kosa.fillinv.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LessonPopularityScheduler {

    private static final int BAYESIAN_AVERAGE_WEIGHT = 5; // 베이지안 평균 가중치
    private static final double RECENT_APP_COUNT_WEIGHT = 0.6;
    private static final double REVIEW_COUNT_WEIGHT = 0.2;
    private static final double BAYESIAN_AVG_WEIGHT = 0.2;
    private final LessonRepository lessonRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReviewRepository reviewRepository;
    private final LessonTempRepository lessonTempRepository;
    private final LessonBulkRepository lessonBulkRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 1 * * *")
    public void updateLessonPopularity() {

        // 점수 초기화
        transactionTemplate.executeWithoutResult(status -> resetAllScores());

        // 계산 및 Temp 저장
        transactionTemplate.executeWithoutResult(status -> calculateAndSaveToTemp());

        // Lesson 업데이트
        transactionTemplate.executeWithoutResult(status -> updateLessonsFromTemp());

    }

    // 서버 실행 시 시작 (개발 단계)
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        updateLessonPopularity();
    }

    private void resetAllScores() {
        lessonRepository.resetAllPopularityScores();
    }

    private void calculateAndSaveToTemp() {

        List<Lesson> activeLessons = lessonRepository.findAllByDeletedAtIsNull();
        if (activeLessons.isEmpty()) {
            return;
        }

        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);

        // 최근 7일 신청 조회
        Map<String, Long> recentStatsMap = scheduleRepository.countByLessonIdAndCreatedAtAfter(startDate).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));

        // 리뷰 관련 계산
        List<ReviewStatsDTO> reviewStatsList = reviewRepository.findReviewStatsByLessonId();
        Map<String, ReviewStat> reviewStatsMap = new HashMap<>();

        long totalReviewCount = 0;
        double totalReviewScoreSum = 0.0;

        for (ReviewStatsDTO row : reviewStatsList) {
            String lessonId = row.lessonId();
            Long count = row.count();
            Double avgScore = row.averageScore();

            reviewStatsMap.put(lessonId, new ReviewStat(count, avgScore));

            totalReviewCount += count;
            totalReviewScoreSum += (count * avgScore);
        }

        double C = (totalReviewCount > 0) ? (totalReviewScoreSum / totalReviewCount) : 0.0;

        List<LessonTemp> tempToSave = new ArrayList<>();

        // 인기 점수 계산
        for (Lesson lesson : activeLessons) {
            String lessonId = lesson.getId();

            Long recentAppCount = recentStatsMap.getOrDefault(lessonId, 0L);

            ReviewStat reviewStat = reviewStatsMap.getOrDefault(lessonId, new ReviewStat(0L, 0.0));
            Long v = reviewStat.count;
            Double R = reviewStat.avgScore;

            // 베이지안 평균
            double bayesianAvg = 0.0;
            if (v + BAYESIAN_AVERAGE_WEIGHT > 0) {
                bayesianAvg = ((double) v / (v + BAYESIAN_AVERAGE_WEIGHT)) * R
                        + ((double) BAYESIAN_AVERAGE_WEIGHT / (v + BAYESIAN_AVERAGE_WEIGHT)) * C;
            }

            double finalScore = (recentAppCount * RECENT_APP_COUNT_WEIGHT) + (v * REVIEW_COUNT_WEIGHT)
                    + (bayesianAvg * BAYESIAN_AVG_WEIGHT);
            double roundedScore = Math.round(finalScore * 100.0) / 100.0;

            tempToSave.add(new LessonTemp(lessonId, roundedScore));
        }

        lessonTempRepository.deleteAll();
        lessonBulkRepository.bulkInsertLessonTemp(tempToSave);
    }

    private void updateLessonsFromTemp() {
        List<LessonTemp> temps = lessonTempRepository.findAll();
        if (temps.isEmpty()) {
            return;
        }

        lessonBulkRepository.bulkUpdatePopularity(temps);
    }

    record ReviewStat(Long count, Double avgScore) {
    }
}
