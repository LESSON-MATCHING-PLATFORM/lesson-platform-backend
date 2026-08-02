package com.kosa.fillinv.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.repository.CategoryRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.lesson.domain.LessonBuilder;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.LessonType;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.member.repository.MemberRepository;
import com.kosa.fillinv.booking.dto.request.BookingCreateRequest;
import com.kosa.fillinv.booking.entity.BookingCancelReason;
import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingSession;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
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
        "spring.datasource.url=jdbc:h2:mem:booking-command-test;MODE=MySQL;NON_KEYWORDS=MINUTE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
@Transactional
class BookingCommandServiceTest {

    @Autowired
    private BookingCommandService bookingCommandService;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

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
    @DisplayName("멘토링 레슨 Booking 생성 성공")
    void createMentoringBooking() {
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

        BookingCreateRequest request = new BookingCreateRequest(
                lesson.getId(),
                selectedOption.getId(),
                selectedAvailableTime.getId(),
                startTime
        );

        // when
        String bookingId = bookingCommandService.createBooking("mentee-1", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        assertThat(booking.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(booking.getOptionName()).isEqualTo(selectedOption.getName());
        assertThat(booking.getSessions()).hasSize(1);
        assertThat(booking.getLessonType()).isEqualTo(LessonType.MENTORING.name());
        assertThat(booking.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(booking.getPrice()).isEqualTo(selectedOption.getPrice());

        assertThat(booking.getOptionName()).isEqualTo(selectedOption.getName());
        assertThat(booking.getOptionMinute()).isEqualTo(selectedOption.getMinute());

        assertThat(booking.getSessions()).hasSize(1);
        BookingSession time = booking.getSessions().get(0);
        assertThat(time.getStartTime()).isEqualTo(startTime);
        assertThat(time.getEndTime()).isEqualTo(startTime.plus(selectedOption.getMinute(), ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("멘토링 Booking 생성 시 startTime이 없으면 INVALID_ARGUMENT 예외가 발생한다")
    void createMentoringBooking_whenStartTimeIsNull_throwsInvalidArgument() {
        Lesson lesson = new LessonBuilder()
                .lessonType(LessonType.MENTORING)
                .categoryId(categoryId)
                .withDefaultOptions()
                .withDefaultAvailableTimes()
                .build();
        lessonRepository.save(lesson);

        Option selectedOption = lesson.getOptionList().getFirst();
        BookingCreateRequest request = new BookingCreateRequest(
                lesson.getId(),
                selectedOption.getId(),
                null,
                null
        );

        assertThatThrownBy(() -> bookingCommandService.createBooking("mentee-1", request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("원데이 레슨 Booking 생성 성공")
    void createOnedayBooking() {
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

        BookingCreateRequest request = new BookingCreateRequest(
                lesson.getId(),
                null,
                selectedAvailableTime.getId(),
                startTime
        );

        when(stockRepository.decreaseQuantity(anyString())).thenReturn(1);

        // when
        String bookingId = bookingCommandService.createBooking("mentee-2", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        assertThat(booking.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(booking.getOptionName()).isEqualTo(lesson.getOptionList().isEmpty() ? null : lesson.getOptionList().get(0).getName());
        assertThat(booking.getSessions()).hasSize(1);
        assertThat(booking.getLessonType()).isEqualTo(LessonType.ONEDAY.name());
        assertThat(booking.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(booking.getPrice()).isEqualTo(selectedAvailableTime.getPrice());

        assertThat(booking.getOptionName()).isEqualTo(null);
        assertThat(booking.getOptionMinute()).isEqualTo(null);

        BookingSession time = booking.getSessions().get(0);
        assertThat(time.getStartTime()).isEqualTo(selectedAvailableTime.getStartTime());
        assertThat(time.getEndTime()).isEqualTo(selectedAvailableTime.getEndTime());
    }

    @Test
    @DisplayName("스터디 레슨은 AvailableTime 전체가 BookingSession으로 생성된다")
    void createStudyBooking() {
        // given
        Lesson lesson = new LessonBuilder()
                .lessonType(LessonType.STUDY)
                .categoryId(categoryId)
                .withDefaultOptions()
                .withDefaultAvailableTimes()
                .build();
        lessonRepository.save(lesson);

        BookingCreateRequest request = new BookingCreateRequest(
                lesson.getId(),
                null,
                null,
                null
        );

        when(stockRepository.decreaseQuantity(anyString())).thenReturn(1);

        // when
        String bookingId = bookingCommandService.createBooking("mentee-3", request);
        entityManager.flush();
        entityManager.clear();

        // then
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();

        assertThat(booking.getLessonTitle()).isEqualTo(lesson.getTitle());
        assertThat(booking.getOptionName()).isEqualTo(lesson.getOptionList().isEmpty() ? null : lesson.getOptionList().get(0).getName());
        assertThat(booking.getLessonType()).isEqualTo(LessonType.STUDY.name());
        assertThat(booking.getMentorId()).isEqualTo(lesson.getMentorId());
        assertThat(booking.getPrice()).isEqualTo(lesson.getPrice());

        assertThat(booking.getSessions()).hasSize(lesson.getAvailableTimeList().size());
    }

    @Test
    @DisplayName("환불 성공 후 APPROVAL_PENDING 원데이 Booking은 CANCELED로 전이되고 availableTimeId 기준으로 재고가 복구된다")
    void cancelByRefund_onedayApprovalPending_cancelsAndRestoresStock() {
        Booking booking = booking(LessonType.ONEDAY, BookingStatus.APPROVAL_PENDING);
        bookingRepository.save(booking);
        doReturn(1).when(stockRepository).increaseQuantity("available-time-001");

        bookingCommandService.cancelByRefund(booking.getId());
        entityManager.flush();
        entityManager.clear();

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(saved.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(saved.getCanceledAt()).isNotNull();
        verify(stockRepository).increaseQuantity("available-time-001");
    }

    @Test
    @DisplayName("환불 성공 후 APPROVED 스터디 Booking은 CANCELED로 전이되고 lessonId 기준으로 재고가 복구된다")
    void cancelByRefund_studyApproved_cancelsAndRestoresStockByLessonId() {
        Booking booking = booking(LessonType.STUDY, BookingStatus.APPROVED);
        bookingRepository.save(booking);
        doReturn(1).when(stockRepository).increaseQuantity("lesson-001");

        bookingCommandService.cancelByRefund(booking.getId());
        entityManager.flush();
        entityManager.clear();

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(saved.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(saved.getCanceledAt()).isNotNull();
        verify(stockRepository).increaseQuantity("lesson-001");
        verify(stockRepository, never()).increaseQuantity(booking.getId());
    }

    @Test
    @DisplayName("이미 CANCELED인 Booking은 환불 후처리를 다시 수행해도 재고를 복구하지 않는다")
    void cancelByRefund_whenAlreadyCanceled_doesNotRestoreStock() {
        Booking booking = booking(LessonType.ONEDAY, BookingStatus.APPROVAL_PENDING);
        booking.cancel(BookingCancelReason.REFUND_COMPLETED);
        bookingRepository.save(booking);

        bookingCommandService.cancelByRefund(booking.getId());

        verify(stockRepository, never()).increaseQuantity(anyString());
    }

    private Booking booking(LessonType lessonType, BookingStatus status) {
        return Booking.builder()
                .id("booking-" + lessonType.name() + "-" + status.name())
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
