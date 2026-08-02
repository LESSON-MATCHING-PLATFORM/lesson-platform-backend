package com.kosa.fillinv.booking.service;

import com.kosa.fillinv.booking.entity.Booking;
import com.kosa.fillinv.booking.entity.BookingCancelReason;
import com.kosa.fillinv.booking.entity.BookingStatus;
import com.kosa.fillinv.booking.repository.BookingRepository;
import com.kosa.fillinv.global.exception.BusinessException;
import com.kosa.fillinv.stock.entity.Stock;
import com.kosa.fillinv.stock.repository.StockRepository;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:booking-cancel-concurrency-test;MODE=MySQL;NON_KEYWORDS=MINUTE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "TOSS_SECRET_KEY=test-secret"
})
class BookingCancelConcurrencyIntegrationTest {

    @Autowired
    private BookingCommandService bookingCommandService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 Booking 환불 취소가 동시에 실행되어도 재고는 한 번만 복구된다")
    void cancelByRefund_whenSameBookingCanceledConcurrently_restoresStockOnlyOnce() throws Exception {
        bookingRepository.save(booking("booking-001", BookingStatus.APPROVAL_PENDING));
        stockRepository.save(stock("stock-001", "available-time-001", 3));

        runWithLockedBooking(
                "booking-001",
                lockedBooking -> {
                    lockedBooking.cancel(BookingCancelReason.REFUND_COMPLETED);
                    stockRepository.increaseQuantity("available-time-001");
                },
                () -> bookingCommandService.cancelByRefund("booking-001")
        );

        Booking savedBooking = bookingRepository.findById("booking-001").orElseThrow();
        Stock savedStock = stockRepository.findById("stock-001").orElseThrow();

        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedBooking.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(savedBooking.getCanceledAt()).isNotNull();
        assertThat(savedStock.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("환불 취소와 멘토 거절이 동시에 실행되어도 재고는 한 번만 복구된다")
    void cancelByRefundAndRejectLesson_whenSameBookingCanceledConcurrently_restoresStockOnlyOnce() throws Exception {
        bookingRepository.save(booking("booking-001", BookingStatus.APPROVAL_PENDING));
        stockRepository.save(stock("stock-001", "available-time-001", 3));

        runWithLockedBooking(
                "booking-001",
                lockedBooking -> {
                    lockedBooking.cancel(BookingCancelReason.REFUND_COMPLETED);
                    stockRepository.increaseQuantity("available-time-001");
                },
                () -> {
                    try {
                        bookingCommandService.rejectLessonByMentor("mentor-001", "booking-001");
                    } catch (BusinessException ignored) {
                    }
                }
        );

        Booking savedBooking = bookingRepository.findById("booking-001").orElseThrow();
        Stock savedStock = stockRepository.findById("stock-001").orElseThrow();

        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedBooking.getCancelReason()).isEqualTo(BookingCancelReason.REFUND_COMPLETED);
        assertThat(savedBooking.getCanceledAt()).isNotNull();
        assertThat(savedStock.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("삭제된 Booking은 취소용 잠금 조회 대상에서 제외한다")
    void findByIdForUpdate_whenBookingDeleted_returnsEmpty() {
        Booking booking = booking("booking-001", BookingStatus.APPROVAL_PENDING);
        booking.delete();
        bookingRepository.save(booking);

        Boolean found = transactionTemplate.execute(status ->
                bookingRepository.findByIdForUpdate("booking-001").isPresent()
        );

        assertThat(found).isFalse();
    }

    private void runWithLockedBooking(
            String bookingId,
            Consumer<Booking> lockedAction,
            Runnable competingAction
    ) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch competingStarted = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        try {
            Future<?> lockingFuture = executorService.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    Booking lockedBooking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
                    lockAcquired.countDown();
                    await(releaseLock);
                    lockedAction.accept(lockedBooking);
                });
            });

            assertThat(lockAcquired.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> competingFuture = executorService.submit(() -> {
                competingStarted.countDown();
                competingAction.run();
            });

            assertThat(competingStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(competingFuture.isDone()).isFalse();

            releaseLock.countDown();

            lockingFuture.get(5, TimeUnit.SECONDS);
            competingFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            executorService.shutdownNow();
            throw e;
        } finally {
            releaseLock.countDown();
            executorService.shutdown();
            assertThat(executorService.awaitTermination(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Booking booking(String bookingId, BookingStatus status) {
        return Booking.builder()
                .id(bookingId)
                .status(status)
                .requestContent("신청합니다")
                .lessonTitle("자바 레슨")
                .lessonType("ONEDAY")
                .lessonDescription("자바를 공부합니다")
                .lessonLocation("온라인")
                .lessonCategoryName("개발")
                .mentorNickname("멘토")
                .price(10000)
                .lessonId("lesson-001")
                .menteeId("mentee-001")
                .mentorId("mentor-001")
                .availableTimeId("available-time-001")
                .build();
    }

    private Stock stock(String stockId, String serviceKey, int quantity) {
        return Stock.builder()
                .id(stockId)
                .serviceKey(serviceKey)
                .quantity(quantity)
                .build();
    }
}
