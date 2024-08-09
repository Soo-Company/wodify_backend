package com.soocompany.wodify.reservation_detail.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetailDetResDto {

    private Long id;
    private LocalDate date;
    private LocalTime time;
    private String coachName;
    private Long wodId;
    private Long recordId;
    private String recordSnF;

}
