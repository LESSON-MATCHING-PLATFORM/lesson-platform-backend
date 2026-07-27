package com.kosa.fillinv.lesson.domain;

import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.entity.Option;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LessonBuilder {
    private Long idx;
    private String id;
    private String title;
    private LessonType lessonType;
    private String thumbnailImage;
    private String description;
    private String location;
    private String mentorId;
    private Long categoryId;
    private String categoryPath;
    private Instant closeAt;
    private Integer price;
    private Integer seats;
    private List<Option> options;
    private List<AvailableTime> availableTimes;

    public LessonBuilder() {
        this(1L);
    }

    public LessonBuilder(Long idx) {
        this.idx = idx;
        this.id = "lesson-" + idx;
        this.title = "lesson-title-" + idx;
        this.lessonType = LessonType.MENTORING;
        this.thumbnailImage = "thumbnailImage-" + idx;
        this.description = "description-" + idx;
        this.location = "location";
        this.mentorId = "mentorId";
        this.categoryId = 1L;
        this.categoryPath = "categoryPath";
        this.closeAt = Instant.now().plus(Duration.ofDays(30));
        this.price = 10000;
        this.seats = 10;
        this.options = new ArrayList<>();
        this.availableTimes = new ArrayList<>();
    }

    public LessonBuilder next() {
        return new LessonBuilder(idx++);
    }

    public LessonBuilder id(String id) {
        this.id = id;
        return this;
    }

    public LessonBuilder lessonType(LessonType lessonType) {
        this.lessonType = lessonType;
        return this;
    }

    public LessonBuilder title(String title) {
        this.title = title;
        return this;
    }

    public LessonBuilder thumbnailImage(String thumbnailImage) {
        this.thumbnailImage = thumbnailImage;
        return this;
    }

    public LessonBuilder description(String description) {
        this.description = description;
        return this;
    }

    public LessonBuilder mentorId(String mentorId) {
        this.mentorId = mentorId;
        return this;
    }

    public LessonBuilder location(String location) {
        this.location = location;
        return this;
    }

    public LessonBuilder categoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public LessonBuilder categoryPath(String categoryPath) {
        this.categoryPath = categoryPath;
        return this;
    }

    public LessonBuilder closeAt(Instant closeAt) {
        this.closeAt = closeAt;
        return this;
    }

    public LessonBuilder price(Integer price) {
        this.price = price;
        return this;
    }

    public LessonBuilder seat(Integer seat) {
        this.seats = seat;
        return this;
    }

    public LessonBuilder withDefaultOptions() {
        if (this.lessonType == LessonType.ONEDAY ||  this.lessonType == LessonType.STUDY) {
            return this;
        }
        this.options = List.of(
                new Option("optionId1", "option1", 30, 1000, null),
                new Option("optionId2", "option2", 60, 2000, null),
                new Option("optionId3", "option3", 90, 3000, null)
        );
        return this;
    }

    public LessonBuilder withOptions(List<Option> options) {
        if (this.lessonType == LessonType.ONEDAY ||  this.lessonType == LessonType.STUDY) {
            return this;
        }
        this.options = options;
        return this;
    }

    public LessonBuilder withDefaultAvailableTimes() {
        Instant start1 = Instant.now();
        Instant start2 = Instant.now().plus(Duration.ofDays(1));
        Instant start3 = Instant.now().plus(Duration.ofDays(2));

        this.availableTimes = List.of(
                new AvailableTime( "avaailableTime1", null, start1, start1.plus(Duration.ofHours(2)), 10000, seats),
                new AvailableTime( "avaailableTime2", null, start2, start2.plus(Duration.ofHours(2)), 10000, seats),
                new AvailableTime( "avaailableTime3", null, start3, start3.plus(Duration.ofHours(2)), 10000, seats)
        );
        return this;
    }

    public LessonBuilder withAvailableTimes(List<AvailableTime> availableTimes) {
        this.availableTimes = availableTimes;
        return this;
    }

    public Lesson build() {
        Lesson lesson = Lesson.builder()
                .id(id)
                .title(title)
                .lessonType(lessonType)
                .thumbnailImage(thumbnailImage)
                .description(description)
                .location(location)
                .mentorId(mentorId)
                .categoryId(categoryId)
                .categoryPath(categoryPath)
                .closeAt(closeAt)
                .price(price)
                .seats(seats)
                .build();

        lesson.addOption(options);
        lesson.addAvailableTime(availableTimes);

        return lesson;
    }

}
