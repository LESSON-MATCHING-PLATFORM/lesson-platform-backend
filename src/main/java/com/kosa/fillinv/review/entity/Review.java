package com.kosa.fillinv.review.entity;

import com.kosa.fillinv.global.entity.BaseEntity;
import com.kosa.fillinv.member.entity.Member;
import com.kosa.fillinv.booking.entity.Booking;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reviews")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @Getter
    @Column(name = "review_id")
    private String id;

    @Getter
    @Column(name = "score", nullable = false)
    private Integer score;

    @Getter
    @Column(name = "content", nullable = false)
    private String content;

    @Getter
    @Column(name = "writer_id", nullable = false)
    private String writerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", insertable = false, updatable = false)
    private Member writer;

    @Getter
    @Column(name = "lesson_id", nullable = false)
    private String lessonId;

    @Getter
    @Column(name = "schedule_id", nullable = false, unique = true)
    private String scheduleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
    private Booking booking;
}
