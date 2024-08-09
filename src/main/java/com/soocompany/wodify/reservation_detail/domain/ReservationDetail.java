package com.soocompany.wodify.reservation_detail.domain;

import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.common.domain.BaseEntity;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.record.domain.Record;
import com.soocompany.wodify.reservation.domain.Reservation;
import com.soocompany.wodify.reservation_detail.dto.ReservationDetailDetResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Optional;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToOne
    @JoinColumn(name = "record_id")
    private Record record;

    public ReservationDetailDetResDto detFromEntity() {

        return ReservationDetailDetResDto.builder()
                .id(this.id)
                .date(reservation.getDate())
                .time(reservation.getTime())
                .coachName(reservation.getCoach().getName())
                .wodId(reservation.getWod().getId())
                .recordId(Optional.ofNullable(record).map(Record::getId).orElse(null))
                .recordSnF(Optional.ofNullable(record).map(Record::getSnf).orElse(null))
                .build();
    }

    public void updateRecord(Record record) {
        this.record = record;
    }
}