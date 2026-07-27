package com.kosa.fillinv.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.kosa.fillinv.lesson.domain.LessonBuilder;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.schedule.dto.request.ScheduleCreateRequest;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleTime;
import com.kosa.fillinv.schedule.repository.ScheduleRepository;
import com.kosa.fillinv.stock.repository.StockRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ScheduleCommandServiceTest {

    @Autowired
    private ScheduleCommandService scheduleCommandService;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @MockitoSpyBean
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private StockRepository stockRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        doReturn(Optional.of(Member.builder().id("mentee-1").nickname("멘토").build()))
                .when(memberRepository)
                .findById(anyString());
    }

    @Test
    @DisplayName("멘토링 레슨 스케줄 생성 성공")
    void createMentoringSchedule() {
        // given
        Lesson lesson = new LessonBuilder()
                .lessonType(LessonType.MENTORING)
                .withDefaultOptions()
                .withDefaultAvailableTimes()
                .build();
        lessonRepository.save(lesson);

        Option selectedOption = lesson.getOptionList().getFirst();
        AvailableTime selectedAvailableTime = lesson.getAvailableTimeList().getFirst();
        Instant startTime = selectedAvailableTime.getStartTime();

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                lesson.getId(),
                selectedOption.getId(),
                selectedAvailableTime.getId(),
                startTime
        );

        // when
        String scheduleId = scheduleCommandService.createSchedule("mentee-1", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();

        assertThat(schedule.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(schedule.getOptionName()).isEqualTo(selectedOption.getName());
        assertThat(schedule.getScheduleTimeList()).hasSize(1);
        assertThat(schedule.getLessonType()).isEqualTo(LessonType.MENTORING.name());
        assertThat(schedule.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(schedule.getPrice()).isEqualTo(selectedOption.getPrice());

        assertThat(schedule.getOptionName()).isEqualTo(selectedOption.getName());
        assertThat(schedule.getOptionMinute()).isEqualTo(selectedOption.getMinute());

        assertThat(schedule.getScheduleTimeList()).hasSize(1);
        ScheduleTime time = schedule.getScheduleTimeList().get(0);
        assertThat(time.getStartTime()).isEqualTo(startTime);
        assertThat(time.getEndTime()).isEqualTo(startTime.plus(selectedOption.getMinute(), ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("원데이 레슨 스케줄 생성 성공")
    void createOnedaySchedule() {
        // given
        Lesson lesson = new LessonBuilder()
                .lessonType(LessonType.ONEDAY)
                .withDefaultOptions()
                .withDefaultAvailableTimes()
                .build();
        lessonRepository.save(lesson);

        AvailableTime selectedAvailableTime = lesson.getAvailableTimeList().getFirst();
        Instant startTime = selectedAvailableTime.getStartTime();

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                lesson.getId(),
                null,
                selectedAvailableTime.getId(),
                startTime
        );

        when(stockRepository.decreaseQuantity(anyString())).thenReturn(1);

        // when
        String scheduleId = scheduleCommandService.createSchedule("mentee-2", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();

        assertThat(schedule.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(schedule.getOptionName()).isEqualTo(lesson.getOptionList().isEmpty() ? null : lesson.getOptionList().get(0).getName());
        assertThat(schedule.getScheduleTimeList()).hasSize(1);
        assertThat(schedule.getLessonType()).isEqualTo(LessonType.ONEDAY.name());
        assertThat(schedule.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(schedule.getPrice()).isEqualTo(selectedAvailableTime.getPrice());

        assertThat(schedule.getOptionName()).isEqualTo(null);
        assertThat(schedule.getOptionMinute()).isEqualTo(null);

        ScheduleTime time = schedule.getScheduleTimeList().get(0);
        assertThat(time.getStartTime()).isEqualTo(selectedAvailableTime.getStartTime());
        assertThat(time.getEndTime()).isEqualTo(selectedAvailableTime.getEndTime());
    }

    @Test
    @DisplayName("스터디 레슨은 AvailableTime 전체가 ScheduleTime으로 생성된다")
    void createStudySchedule() {
        // given
        Lesson lesson = new LessonBuilder()
                .lessonType(LessonType.STUDY)
                .withDefaultOptions()
                .withDefaultAvailableTimes()
                .build();
        lessonRepository.save(lesson);

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                lesson.getId(),
                null,
                null,
                null
        );

        when(stockRepository.decreaseQuantity(anyString())).thenReturn(1);

        // when
        String scheduleId = scheduleCommandService.createSchedule("mentee-3", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();

        assertThat(schedule.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(schedule.getOptionName()).isEqualTo(lesson.getOptionList().isEmpty() ? null : lesson.getOptionList().get(0).getName());
        assertThat(schedule.getLessonType()).isEqualTo(LessonType.STUDY.name());
        assertThat(schedule.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(schedule.getPrice()).isEqualTo(lesson.getPrice());

        assertThat(schedule.getScheduleTimeList()).hasSize(lesson.getAvailableTimeList().size());
    }
}