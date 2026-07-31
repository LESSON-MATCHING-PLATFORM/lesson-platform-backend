package com.kosa.fillinv.review.service;

import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.review.dto.*;
import com.kosa.fillinv.review.entity.Review;
import com.kosa.fillinv.review.exception.ReviewException;
import com.kosa.fillinv.review.repository.ReviewRepository;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.exception.BookingException;
import com.kosa.fillinv.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public LessonReviewListResponseDTO getReviewListByLesson(String lessonId, Pageable pageable) {
        Double averageScore = reviewRepository.findAverageScoreByLessonId(lessonId);
        Page<LessonReviewResponseDTO> reviews = reviewRepository
                .findReviewsWithNicknameByLessonId(lessonId, pageable)
                .map(LessonReviewResponseDTO::from);

        return LessonReviewListResponseDTO.of(averageScore, reviews.getTotalElements(), reviews);
    }

    @Transactional(readOnly = true)
    public Page<MyReviewResponseDTO> getMyReviews(String memberId, Pageable pageable) {
        return reviewRepository.findByWriterId(memberId, pageable)
                .map(MyReviewResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<UnwrittenReviewResponseDTO> getUnwrittenReviews(String memberId, Pageable pageable) {
        return bookingRepository.findUnwrittenReviews(memberId, pageable)
                .map(UnwrittenReviewResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Double> getAverageScoreByLessonIds(Set<String> lessonIds) {
        List<LessonAvgScore> averageScoresByLessonIds = reviewRepository.findAverageScoreByLessonIds(lessonIds);

        return averageScoresByLessonIds.stream().collect(
                Collectors.toMap(
                        LessonAvgScore::lessonId,
                        LessonAvgScore::averageScore));
    }

    @Transactional
    public ReviewCreateResponseDTO createReview(String memberId, ReviewRequestDTO requestDTO) {
        Booking booking = bookingRepository.findById(requestDTO.scheduleId())
                .orElseThrow(BookingException.BookingNotFound::new);

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ReviewException(ErrorCode.REVIEW_NOT_ALLOWED);
        }

        if (!booking.getMenteeId().equals(memberId)) {
            throw new ReviewException(ErrorCode.ACCESS_DENIED);
        }

        if (reviewRepository.existsByScheduleId(booking.getId())) {
            throw new ReviewException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .id(UUID.randomUUID().toString())
                .score(requestDTO.score())
                .content(requestDTO.content())
                .writerId(memberId)
                .lessonId(booking.getLessonId())
                .scheduleId(booking.getId())
                .build();

        reviewRepository.save(review);

        return ReviewCreateResponseDTO.from(review.getId());
    }
}
