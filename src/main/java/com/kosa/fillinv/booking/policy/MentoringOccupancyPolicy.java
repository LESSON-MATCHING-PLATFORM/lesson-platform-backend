package com.kosa.fillinv.booking.policy;

import com.kosa.fillinv.booking.entity.BookingStatus;
import java.util.Set;

public final class MentoringOccupancyPolicy {

    private static final Set<BookingStatus> OCCUPIED_STATUSES = Set.of(
            BookingStatus.PAYMENT_PENDING,
            BookingStatus.APPROVAL_PENDING,
            BookingStatus.APPROVED
    );

    private MentoringOccupancyPolicy() {
    }

    public static Set<BookingStatus> occupiedStatuses() {
        return OCCUPIED_STATUSES;
    }
}
