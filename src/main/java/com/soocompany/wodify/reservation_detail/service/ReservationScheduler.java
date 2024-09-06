package com.soocompany.wodify.reservation_detail.service;

import com.soocompany.wodify.reservation.domain.Reservation;
import com.soocompany.wodify.reservation.repository.ReservationRepository;
import com.soocompany.wodify.reservation_detail.controller.SseController;
import com.soocompany.wodify.reservation_detail.domain.ReservationDetail;
import com.soocompany.wodify.reservation_detail.dto.ReservationDetailDetResDto;
import com.soocompany.wodify.reservation_detail.repository.ReservationDetailRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j

public class ReservationScheduler {

    private final SseController sseController;
    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;

    @Qualifier("schedulerLockTemplate2")
    private final RedisTemplate<String, Object> schedulreRedisTemplate;

    public ReservationScheduler(SseController sseController, ReservationRepository reservationRepository, ReservationDetailRepository reservationDetailRepository,
                                @Qualifier("schedulerLockTemplate2") RedisTemplate<String, Object> schedulreRedisTemplate) {
        this.sseController = sseController;
        this.reservationRepository = reservationRepository;
        this.reservationDetailRepository = reservationDetailRepository;
        this.schedulreRedisTemplate = schedulreRedisTemplate;
    }

    @SchedulerLock(name = "cron_lock_reservation", lockAtLeastFor = "20s", lockAtMostFor = "50s")
    @Scheduled(cron="0 0/1 * * * *") // test
//    @Scheduled(cron="0 0/5 * * * *")  // test
    @Transactional
    public void alarmReservation(){
//        String lockKey = "reservationLock";
//        Boolean isLocked = schedulreRedisTemplate.opsForValue().setIfAbsent(lockKey, "true", Duration.ofSeconds(90)); // 60초간 락 유지
//        if(Boolean.TRUE.equals(isLocked)){
//            try {
                log.info("예약 알림 : 현재 서버에서 스케쥴러 실행중");
                LocalDate date = LocalDate.now();
                List<Reservation> reservationList = reservationRepository.findAllByDateAndDelYn(date, "N");
                for(Reservation reservation : reservationList){
                    List<ReservationDetail> reservationDetails = reservationDetailRepository.findAllByReservationAndDelYn(reservation, "N");
                    for(ReservationDetail reservationDetail : reservationDetails){
                        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                        if(reservationDetail.getReservation().getTime().minusHours(1).format(dateTimeFormatter).equals(LocalTime.now().plusHours(9).format(dateTimeFormatter))){
                            ReservationDetailDetResDto dto = reservationDetail.detFromEntity();
                            dto.setCheck("reservationDetail");
                            String memberId = String.valueOf(reservationDetail.getMember().getId());
                            sseController.publishReservationMessage(dto,memberId);
                            log.info("예약 알림 : 잘됩니다.");
                        }
                    }
                }
//            }finally {
//                schedulreRedisTemplate.delete(lockKey);
//            }
//        }else {
//            log.info("예약 알림 : 다른 서버에서 스케쥴러 실행중");
//        }
    }
}
