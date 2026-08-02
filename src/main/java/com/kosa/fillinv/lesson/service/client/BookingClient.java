package com.kosa.fillinv.lesson.service.client;

import com.kosa.fillinv.lesson.service.dto.BookedTimeVO;
import com.kosa.fillinv.booking.entity.BookingStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BookingClient {
    Map<String, Integer> countByLessonIdInAndStatusIn(Collection<String> lessonIds,
            Collection<BookingStatus> statuses);

    Integer countByLessonIdAndStatusIn(String lessonId, Collection<BookingStatus> statuses);

    List<BookedTimeVO> getBookedTimes(String lessonId, Collection<BookingStatus> statuses, Instant since);
}
