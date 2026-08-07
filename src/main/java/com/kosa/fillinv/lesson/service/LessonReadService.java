package com.kosa.fillinv.lesson.service;

import com.kosa.fillinv.category.dto.CategoryResponseDto;
import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.service.CategoryService;
import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.lesson.controller.dto.LessonSearchRequest;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.service.client.*;
import com.kosa.fillinv.lesson.service.dto.*;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.policy.MentoringOccupancyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kosa.fillinv.lesson.error.LessonError.*;

@Component
@RequiredArgsConstructor
public class LessonReadService {

    private final LessonService lessonService;

    private final ReviewClient reviewClient;

    private final ProfileClient profileClient;

    private final StockClient stockClient;

    private final CategoryService categoryService;

    private final BookingClient bookingClient;

    private static final Set<BookingStatus> PARTICIPATED_STATUSES = Set.of(BookingStatus.APPROVED, BookingStatus.COMPLETED);

    public Page<LessonThumbnail> search() {
        return search(LessonSearchRequest.empty());
    }

    public Page<LessonThumbnail> search(LessonSearchRequest request) {
        LessonSearchCondition condition = resolveCondition(request);
        return searchByCondition(condition);
    }

    public Page<LessonThumbnail> searchOwnedBy(
            LessonSearchRequest request,
            String mentorId
    ) {
        validateMentorId(mentorId);

        LessonSearchCondition condition =
                resolveCondition(request).ownBy(mentorId);

        return searchByCondition(condition);
    }

    private LessonSearchCondition resolveCondition(
            LessonSearchRequest request
    ) {
        String categoryPath = resolveCategoryPath(request.categoryId());
        return request.toCondition(categoryPath);
    }

    private String resolveCategoryPath(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryService.getCategoryById(categoryId).getCategoryPath();
    }

    private void validateMentorId(String mentorId) {
        if (mentorId == null) {
            throw new ResourceException.InvalidArgument(MENTOR_ID_REQUIRED);
        }
    }

    private Page<LessonThumbnail> searchByCondition(
            LessonSearchCondition condition
    ) {
        Page<LessonDTO> lessonPage = lessonService.searchLesson(condition);
        return assembleLessonThumbnail(lessonPage);
    }

    public LessonDetailResult detail(LessonDetailCommand request) {

        LessonDTO lessonDTO = lessonService.readLessonById(request.lessonId())
                .orElseThrow(() -> new ResourceException.NotFound(LESSON_NOT_FOUND_MESSAGE_FORMAT(request.lessonId())));

        MentorSummaryDTO mentorSummaryDTO = profileClient.readMentorById(lessonDTO.mentorId());

        Category category = categoryService.getCategoryById(lessonDTO.categoryId());

        Set<String> keys = Set.of();
        if (lessonDTO.lessonType() == LessonType.STUDY) {
            keys = Set.of(lessonDTO.id());
        } else if (lessonDTO.lessonType() == LessonType.ONEDAY) {
            keys = lessonDTO.availableTimeDTOList().stream()
                    .map(AvailableTimeDTO::id)
                    .collect(Collectors.toSet());
        }

        Map<String, Integer> stockMap = keys.isEmpty() ?
                new HashMap<>() :
                new HashMap<>(stockClient.getStock(keys));
        for (String key : keys) {
            // Stock에서 조회되지 않은 남은 좌석 수가 있다면 Exception 대신 0으로 초기화
            stockMap.putIfAbsent(key, 0);
        }

        Integer lessonRemainSeats = null;
        Map<String, Integer> availableTimeRemainSeats = null;

        if (lessonDTO.lessonType() == LessonType.STUDY) {
            lessonRemainSeats = stockMap.get(lessonDTO.id());
        } else if (lessonDTO.lessonType() == LessonType.ONEDAY) {
            availableTimeRemainSeats = stockMap;
        }

        Integer menteeCount = bookingClient.countByLessonIdAndStatusIn(
                request.lessonId(),
                PARTICIPATED_STATUSES
        );

        List<BookedTimeVO> bookedTimes = null;
        if (lessonDTO.lessonType() == LessonType.MENTORING) {
            bookedTimes = bookingClient.getBookedTimes(
                    request.lessonId(),
                    MentoringOccupancyPolicy.occupiedStatuses(),
                    Instant.now().minus(7, ChronoUnit.DAYS) // 모든 schedule_time을 가져오지 않게
            );
        }

        return LessonDetailResult.of(
                mentorSummaryDTO,
                lessonDTO,
                lessonRemainSeats,
                availableTimeRemainSeats,
                category.getName(),
                menteeCount == null ? 0 : menteeCount,
                bookedTimes
        );
    }

    private Page<LessonThumbnail> assembleLessonThumbnail(
            Page<LessonDTO> lessonPage
    ) {
        if (lessonPage.isEmpty()) {
            return Page.empty(lessonPage.getPageable());
        }

        Map<Long, CategoryResponseDto> allCategoriesMap = categoryService.getAllCategoriesMap();

        Set<String> mentorIds = lessonPage.stream()
                .map(LessonDTO::mentorId)
                .collect(Collectors.toSet());
        Set<String> lessonIds = lessonPage.stream()
                .map(LessonDTO::id)
                .collect(Collectors.toSet());

        Map<String, MentorSummaryDTO> mentorMap =
                profileClient.getMentors(mentorIds);

        Map<String, Float> averageRating =
                reviewClient.getAverageRating(lessonIds);

        Map<String, Integer> menteeCountMap = bookingClient.countByLessonIdInAndStatusIn(
                lessonIds,
                PARTICIPATED_STATUSES
        );

        return lessonPage.map(lesson -> {
            MentorSummaryDTO mentor = mentorMap.get(lesson.mentorId());
            Float rating = averageRating.get(lesson.id());
            Integer menteeCount = menteeCountMap.getOrDefault(lesson.id(), 0);
            return LessonThumbnail.of(
                    lesson,
                    mentor,
                    rating,
                    allCategoriesMap.get(lesson.categoryId()) == null ? null : allCategoriesMap.get(lesson.categoryId()).name(),
                    menteeCount
            );
        });
    }
}
