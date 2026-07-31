package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.repository.CategoryRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.global.response.ErrorCode;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.AvailableTimeRepository;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.lesson.repository.OptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BookingLessonReader {

    private final LessonRepository lessonRepository;
    private final OptionRepository optionRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final CategoryRepository categoryRepository;

    Lesson getLesson(String lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LESSON_NOT_FOUND));
    }

    Option getOption(String optionId) {
        return optionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPTION_NOT_FOUND));
    }

    AvailableTime getAvailableTime(String availableTimeId) {
        return availableTimeRepository.findById(availableTimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVAILABLE_TIME_NOT_FOUND));
    }

    List<AvailableTime> getAvailableTimesByLessonId(String lessonId) {
        return availableTimeRepository.findAllByLessonId(lessonId);
    }

    Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
