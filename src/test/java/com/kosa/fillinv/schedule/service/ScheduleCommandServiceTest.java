package com.kosa.fillinv.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.repository.CategoryRepository;
import com.kosa.fillinv.lesson.domain.LessonBuilder;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.schedule.dto.request.ScheduleCreateRequest;
import com.kosa.fillinv.schedule.entity.BookingCancelReason;
import com.kosa.fillinv.schedule.entity.Schedule;
import com.kosa.fillinv.schedule.entity.ScheduleStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:schedule-command-test;MODE=MySQL;NON_KEYWORDS=MINUTE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
@Transactional
class ScheduleCommandServiceTest {

    @Autowired
    private ScheduleCommandService scheduleCommandService;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @MockitoSpyBean
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private StockRepository stockRepository;

    @Autowired
    private EntityManager entityManager;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = categoryRepository.save(new Category(null, "개발", null, "개발")).getId();

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
                .categoryId(categoryId)
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
                .categoryId(categoryId)
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
                .categoryId(categoryId)
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

    @Test
    @DisplayName("환불 성공 후 APPROVAL_PENDING 원데이 Booking은 CANCELED로 전이되고 availableTimeId 기준으로 재고가 복구된다")
    void cancelByRefund_onedayApprovalPending_cancelsAndRestoresStock() {
        Schedule schedule = schedule(LessonType.ONEDAY, ScheduleStatus.APPROVAL_PENDING);
        scheduleRepository.save(schedule);
        doReturn(1).when(stockRepository).increaseQuantity("available-time-001");

        scheduleCommandService.cancelByRefund(schedule.getId());
        entityManager.flush();
        entityManager.clear();

        Schedule saved = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ScheduleStatus.CANCELED);
        assertThat(saved.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(saved.getCanceledAt()).isNotNull();
        verify(stockRepository).increaseQuantity("available-time-001");
    }

    @Test
    @DisplayName("환불 성공 후 APPROVED 스터디 Booking은 CANCELED로 전이되고 lessonId 기준으로 재고가 복구된다")
    void cancelByRefund_studyApproved_cancelsAndRestoresStockByLessonId() {
        Schedule schedule = schedule(LessonType.STUDY, ScheduleStatus.APPROVED);
        scheduleRepository.save(schedule);
        doReturn(1).when(stockRepository).increaseQuantity("lesson-001");

        scheduleCommandService.cancelByRefund(schedule.getId());
        entityManager.flush();
        entityManager.clear();

        Schedule saved = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ScheduleStatus.CANCELED);
        assertThat(saved.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(saved.getCanceledAt()).isNotNull();
        verify(stockRepository).increaseQuantity("lesson-001");
        verify(stockRepository, never()).increaseQuantity(schedule.getId());
    }

    @Test
    @DisplayName("이미 CANCELED인 Booking은 환불 후처리를 다시 수행해도 재고를 복구하지 않는다")
    void cancelByRefund_whenAlreadyCanceled_doesNotRestoreStock() {
        Schedule schedule = schedule(LessonType.ONEDAY, ScheduleStatus.APPROVAL_PENDING);
        schedule.cancel(BookingCancelReason.REFUND_COMPLETED);
        scheduleRepository.save(schedule);

        scheduleCommandService.cancelByRefund(schedule.getId());

        verify(stockRepository, never()).increaseQuantity(anyString());
    }

    private Schedule schedule(LessonType lessonType, ScheduleStatus status) {
        return Schedule.builder()
                .id("schedule-" + lessonType.name() + "-" + status.name())
                .status(status)
                .requestContent("신청합니다")
                .lessonTitle("자바 레슨")
                .lessonType(lessonType.name())
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(10000)
                .lessonId("lesson-001")
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .optionId("option-001")
                .availableTimeId("available-time-001")
                .build();
    }
}
