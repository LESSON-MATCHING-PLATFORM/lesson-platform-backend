# MENTORING 시간 점유와 해제 정책

## 목적

MENTORING은 ONEDAY/STUDY와 달리 별도 재고를 차감하지 않는다. 사용자가 선택한 시작 시각과 옵션 시간을 기준으로 `BookingSession`을 만들고, Booking 상태를 통해 멘토의 시간이 점유되었는지 판단한다.

이 문서는 MENTORING 시간 점유와 해제 기준을 명시해 예약 생성, 취소, 환불 흐름에서 같은 정책을 공유하기 위한 기준이다.

## 현재 정책

MENTORING 시간 점유는 별도 hold 테이블이 아니라 Booking 상태에서 파생한다.

점유 상태:

- `PAYMENT_PENDING`
- `APPROVAL_PENDING`
- `APPROVED`

해제 상태:

- `CANCELED`

즉, MENTORING Booking이 `CANCELED`로 전이되면 해당 Booking의 `BookingSession`은 더 이상 점유 시간으로 보지 않는다.

## 취소 흐름

MENTORING 취소 흐름은 ONEDAY/STUDY의 재고 복구와 다르다.

- ONEDAY는 `availableTimeId` 기준으로 stock을 복구한다.
- STUDY는 `lessonId` 기준으로 stock을 복구한다.
- MENTORING은 stock을 복구하지 않는다.

MENTORING의 시간 해제는 `BookingStockService.restore()`가 아니라 `Booking.cancel()`에 따른 `CANCELED` 상태 전이로 표현된다. bookedTimes 조회와 중복 신청 방지 로직은 공통 정책인 `MentoringOccupancyPolicy.occupiedStatuses()`에 포함된 상태만 점유로 해석한다.

## 중복 신청 방지와의 관계

MENTORING 중복 신청 방지는 같은 `mentorId`의 활성 MENTORING Booking 중 요청 시간과 겹치는 세션이 있는지 확인한다.

겹침 조건:

```text
existing.startTime < requestedEnd
AND existing.endTime > requestedStart
```

경계 시간이 맞닿는 경우, 예를 들어 기존 예약 종료 시각과 새 예약 시작 시각이 같은 경우는 중복으로 보지 않는다.

## 이번 범위에서 제외한 것

현재 정책은 비즈니스 규칙과 상태 기반 점유 해석을 명확히 하는 데 초점을 둔다. 거의 동시에 들어온 두 요청이 모두 overlap 조회를 통과하는 race condition까지 완전히 막는 것은 별도 동시성 방어 백로그로 분리한다.

후속 검토 대상:

- mentor 기준 pessimistic lock
- 명시적 mentor time hold 모델
- time bucket 기반 DB 제약
- transaction isolation 조정
