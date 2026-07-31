package com.kosa.fillinv.lesson.service.client;

import com.kosa.fillinv.lesson.service.dto.BookedTimeVO;
import com.kosa.fillinv.lesson.service.dto.LessonCountVO;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultBookingClient implements BookingClient {

    private final BookingRepository bookingRepository;

    @Override
    public Map<String, Integer> countByLessonIdInAndStatusIn(
            Collection<String> lessonIds,
            Collection<BookingStatus> statuses
    ) {
        return bookingRepository.countByLessonIdInAndStatusIn(lessonIds, statuses)
                .stream()
                .collect(
                        Collectors.toMap(LessonCountVO::lessonId, vo -> vo.count().intValue())
                );
    }

    @Override
    public Integer countByLessonIdAndStatusIn(String lessonId, Collection<BookingStatus> statuses) {
        Long count = bookingRepository.countByLessonIdAndStatusIn(lessonId, statuses);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public List<BookedTimeVO> getBookedTimes(String lessonId, Collection<BookingStatus> statuses, Instant since) {
        return bookingRepository.findBookedTimesByLessonIdAndStatusInAndStartTimeAfter(lessonId, statuses, since);
    }
}
