package com.soocompany.wodify.reservation_detail.service;

import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.holding_info.domain.HoldingInfo;
import com.soocompany.wodify.holding_info.repository.HoldingInfoRepository;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.member.domain.Role;
import com.soocompany.wodify.member.repository.MemberRepository;
import com.soocompany.wodify.registration_info.domain.RegistrationInfo;
import com.soocompany.wodify.registration_info.repository.RegistrationInfoRepository;
import com.soocompany.wodify.reservation.dto.ReservationManageEvent;
import com.soocompany.wodify.reservation.dto.ReservationSearchDto;
import com.soocompany.wodify.reservation.service.ReservationManageEventHandler;
import com.soocompany.wodify.reservation.service.ReservationManagementService;
import com.soocompany.wodify.reservation.domain.Reservation;
import com.soocompany.wodify.reservation.repository.ReservationRepository;
import com.soocompany.wodify.reservation_detail.controller.SseController;
import com.soocompany.wodify.reservation_detail.domain.ReservationDetail;
import com.soocompany.wodify.reservation_detail.dto.ReservationDetCreateReqDto;
import com.soocompany.wodify.reservation_detail.dto.ReservationDetailDetResDto;
import com.soocompany.wodify.reservation_detail.repository.ReservationDetailRepository;
import com.soocompany.wodify.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ReservationDetailService {
    private final ReservationDetailRepository reservationDetailRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final RegistrationInfoRepository registrationInfoRepository;
    private final HoldingInfoRepository holdingInfoRepository;
    private final ReservationManagementService reservationManagementService;
    private final ReservationManageEventHandler reservationManageEventHandler;
    private final WaitingService waitingService;
    private final SseController sseController;

    public ReservationDetailDetResDto reservationCreate(ReservationDetCreateReqDto dto, Long memberId) {
        Reservation reservation = reservationRepository.findByIdAndDelYn(dto.getReservationId(), "N").orElseThrow(() -> new EntityNotFoundException("해당 id의 예약이 존재하지 않습니다."));
        Member member = memberRepository.findByIdAndDelYn(memberId, "N").orElseThrow(() -> {
            log.error("reservationCreate() : 해당 id의 회원을 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 id의 회원을 찾을 수 없습니다.");
        });
        Optional<ReservationDetail> reservationDetailOptional = reservationDetailRepository.findByReservationAndMemberAndDelYn(reservation, member, "N");
        if (reservationDetailOptional.isPresent()) {
            log.error("reservationCreate() : 이미 해당 예약을 한 이력이 있습니다.");
            throw new IllegalArgumentException("이미 해당 예약을 한 이력이 있습니다.");
        }

        // 예약을 요청한 회원의 자격 요건 확인 = 등록 정보가 있는지 & 정지 여부
        Box box = member.getBox();
        if (box==null) {
            log.error("reservationCreate() : 박스에 소속되어있지 않은 회원은 예약이 불가합니다.");
            throw new IllegalArgumentException("박스에 소속되어있지 않은 회원은 예약이 불가합니다.");
        }
        List<RegistrationInfo> registrationInfos = registrationInfoRepository.findByMemberAndBoxAndDelYnOrderByRegistrationDateDesc(member, box, "N");
        if (registrationInfos.isEmpty()) {
            log.error("reservationCreate() : 박스에 등록되어있지 않은 회원은 예약이 불가합니다.");
            throw new IllegalArgumentException("박스에 등록되어있지 않은 회원은 예약이 불가합니다.");
        }
        boolean isRegistration = false;
        for (RegistrationInfo registrationInfo : registrationInfos) {
            LocalDate registrationDate = registrationInfo.getRegistrationDate();
            LocalDate endDate = registrationInfo.getEndDate();
            LocalDate reservationDate = reservation.getDate();
            if (!registrationDate.isAfter(reservationDate)&&endDate.isAfter(reservationDate)) {
                isRegistration = true;
            }
        }

//        예약 날짜와 등록 정보 날짜 비교
        if (!isRegistration) {
            log.error("reservationCreate() : 박스 등록기간이 아닌 기간은 예약이 불가합니다.");
            throw new IllegalArgumentException("박스 등록기간이 아닌 기간은 예약이 불가합니다.");
        }
//        정지 상태 확인
        List<HoldingInfo> holdingInfos = holdingInfoRepository.findByMemberAndBoxAndDelYn(member, box, "N");
        for (HoldingInfo holdingInfo : holdingInfos) {
            if (holdingInfo.isOnHold()) {
                log.error("reservationCreate() : 정지 기간 내 예약은 불가합니다.");
                throw new IllegalArgumentException("정지 기간 내 예약은 불가합니다.");
            }
        }

        Long available = reservationManagementService.decreaseAvailable(reservation.getId(), 1);
        log.info(reservation.getId() +".."+ String.valueOf(available)+"명 가능");
        if (available < 0) {
            throw new IllegalStateException("예약 인원이 초과되어 예약이 불가능합니다.");
        }

        ReservationDetail reservationDetail = dto.toEntity(reservation, member);
        ReservationDetail savedDetail = reservationDetailRepository.save(reservationDetail);
        ReservationDetailDetResDto reservationDetailDetResDto = savedDetail.detFromEntity();
        List<Member> coachs = memberRepository.findByBoxAndRoleAndDelYn(box, Role.COACH,"N");
        for (Member coach : coachs) {
            Long coachId = coach.getId();
            reservationDetailDetResDto.setCheck("reservation");
            sseController.publishMessage(reservationDetailDetResDto, String.valueOf(coachId));
        }
        Member boxRepresentative = box.getMember();
        reservationDetailDetResDto.setCheck("reservation");
        try{
            sseController.publishMessage(reservationDetailDetResDto, String.valueOf(boxRepresentative.getId()));
        }catch (Exception e) {
            log.error("알림은 안보내졌지만 예약 성공");
        }
        reservationManageEventHandler.publish(new ReservationManageEvent(reservation.getId(), 1));
        return reservationDetailDetResDto;
    }

    public ReservationDetailDetResDto detail(Long id) {
        ReservationDetail reservationDetail = reservationDetailRepository.findByIdAndDelYn(id, "N").orElseThrow(() -> new EntityNotFoundException("해당 id의 예약상세내역을 찾을 수 없습니다."));
        return reservationDetail.detFromEntity();
    }

    public Page<ReservationDetailDetResDto> listByMember(Long memberId, @PageableDefault(sort = "date",direction = Sort.Direction.DESC) Pageable pageable) {
        Member member = memberRepository.findByIdAndDelYn(memberId, "N").orElseThrow(() -> new EntityNotFoundException("해당 id의 회원을 찾을 수 없습니다."));
        Page<ReservationDetail> details = reservationDetailRepository.findByMemberAndDelYn(member, pageable);
        return details.map(ReservationDetail::detFromEntity);
    }

    public void delete(Long id) {
        ReservationDetail reservationDetail = reservationDetailRepository.findByIdAndDelYn(id, "N").orElseThrow(() -> new EntityNotFoundException("해당 id의 예약이 없습니다."));
        Long memberId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Member member = memberRepository.findByIdAndDelYn(memberId, "N").orElseThrow(() -> {
            log.error("delete() : 해당 id의 회원을 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 id의 회원을 찾을 수 없습니다.");
        });
        if (!reservationDetail.getMember().equals(member)) {
            log.error("delete() : 예약을 삭제할 수 있는 권한이 없습니다.");
            throw new IllegalArgumentException("예약을 삭제할 수 있는 권한이 없습니다.");
        }
        Reservation reservation = reservationDetail.getReservation();
        reservationManagementService.increaseAvailable(reservation.getId(),1);
        reservation.increaseAvailablePeople();
        reservationDetail.updateDelYn();
        reservationDetailRepository.save(reservationDetail);

        // 대기자 처리 로직 예외 처리
        try {
            handleWaitlist(reservation);
        } catch (Exception e) {
            log.error("delete() : 대기자 처리 중 오류가 발생하였으나, 예약 삭제는 성공하였습니다.", e);
        }
    }

    private void handleWaitlist(Reservation reservation) {
        // 대기자 명단에서 첫 번째 사용자에게 예약 기회 제공
        String nextMemberId = waitingService.getNextMember(String.valueOf(reservation.getId()));
        if (nextMemberId!=null) {
            memberRepository.findByIdAndDelYn(Long.valueOf(nextMemberId), "N").orElseThrow(() -> {
                log.error("delete() : 해당 id의 회원을 찾을 수 없습니다.");
                return new EntityNotFoundException("해당 id의 회원을 찾을 수 없습니다.");
            });
            ReservationDetailDetResDto reservationDetailDetResDto = reservationCreate(ReservationDetCreateReqDto.builder().reservationId(reservation.getId()).build(), Long.valueOf(nextMemberId));
            waitingService.removeFromQueue(String.valueOf(reservation.getId()), nextMemberId);
            // 대기자에게 알림
            reservationDetailDetResDto.setCheck("reservation");
            sseController.publishMessage(reservationDetailDetResDto, nextMemberId);
        }
    }

    public Page<ReservationDetailDetResDto> myReservationList(ReservationSearchDto dto, Pageable pageable) {
        Long memberId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Member member = memberRepository.findByIdAndDelYn(memberId, "N").orElseThrow(() -> {
            log.error("reservationCreate() : 해당 id의 회원을 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 id의 회원을 찾을 수 없습니다.");
        });
        Page<ReservationDetail> details;
        if (dto.getStartDate()!=null&&dto.getEndDate()!=null) {
            details = reservationDetailRepository.findAllByMemberAndDateRange(member, dto.getStartDate(),dto.getEndDate(), pageable);
        }else {
            details = reservationDetailRepository.findByMemberAndDelYn(member, pageable);
        }

        return details.map(ReservationDetail::detFromEntity);
    }
}