package com.soocompany.wodify.wod.service;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.box.repository.BoxRepository;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.member.domain.Role;
import com.soocompany.wodify.member.repository.MemberRepository;
import com.soocompany.wodify.reservation.domain.Reservation;
import com.soocompany.wodify.reservation.repository.ReservationRepository;
import com.soocompany.wodify.wod.domain.Wod;
import com.soocompany.wodify.wod.domain.WodDetail;
import com.soocompany.wodify.wod.dto.*;
import com.soocompany.wodify.wod.repository.WodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WodService {
    private final WodRepository wodRepository;
    private final MemberRepository memberRepository;
    private final BoxRepository boxRepository;
    private final ReservationRepository reservationRepository;

    public Wod wodSave(WodSaveReqDto wodSaveReqDto) {
        String id = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(id), "N").orElseThrow(() -> {
            log.error("wodSave() : 해당 Email 의 멤버를 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 Email 의 멤버를 찾을 수 없습니다.");
        });
        if (member.getRole() == Role.USER) {
            log.error("wodSave() : WOD 생성 권한이 없습니다.");
            throw new IllegalArgumentException("WOD 생성 권한이 없습니다.");
        }
        Box box = member.getBox();
        if (box == null) {
            log.error("wodSave() : 소속된 박스가 없습니다.");
            throw new IllegalArgumentException("소속된 박스가 없습니다.");
        }
        Optional<Wod> savedWod = wodRepository.findByBoxIdAndDateAndDelYn(box.getId(), wodSaveReqDto.getDate(), "N");
        if (savedWod.isPresent()) {
            log.error("wodSave() : 이미 WOD 가 존재합니다.");
            throw new IllegalArgumentException("이미 WOD 가 존재합니다.");
        }
        Wod wod = wodSaveReqDto.toEntity(member, box);
        for (WodDetSaveReqDto wodDetSaveReqDto : wodSaveReqDto.getWodDetSaveReqDtoList())
            wod.getWodDetails().add(wodDetSaveReqDto.toEntity(wod));
        return wodRepository.save(wod);
    }

    public WodResDto wodFind(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        String id = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(id), "N").orElseThrow(() -> {
            log.error("wodFind() : 해당 이메일의 멤버를 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 이메일의 멤버를 찾을 수 없습니다.");
        });
        Box box = boxRepository.findByMemberIdAndDelYn(member.getBox().getId(), "N").orElseThrow(() -> {
            log.error("wodFind() : 해당 ID의 박스를 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 ID의 박스를 찾을 수 없습니다.");
        });
        Wod wod = wodRepository.findByBoxIdAndDateAndDelYn(box.getId(), date, "N").orElseThrow(() -> {
            log.error("wodFind() : 해당 날짜에 등록된 WOD 가 없습니다.");
            return new EntityNotFoundException("해당 날짜에 등록된 WOD 가 없습니다.");
        });
        WodResDto wodResDto = WodResDto.fromEntity(wod);
        for (WodDetail wodDetail : wod.getWodDetails()) {
            wodResDto.getWodDetResDtoList().add(WodDetResDto.fromEntity(wodDetail));
        }
        return wodResDto;
    }

    public Wod wodDelete(WodCancelReqDto wodCancelReqDto) {
        String id = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(id), "N").orElseThrow(() -> {
            log.error("wodDelete() : 해당 이메일의 멤버를 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 이메일의 멤버를 찾을 수 없습니다.");
        });
        Box box = boxRepository.findByMemberIdAndDelYn(member.getBox().getId(), "N").orElseThrow(() -> {
            log.error("wodDelete() : 해당 ID의 박스를 찾을 수 없습니다.");
            return new EntityNotFoundException("해당 ID의 박스를 찾을 수 없습니다.");
        });
        Wod wod = wodRepository.findByBoxIdAndDateAndDelYn(box.getId(), wodCancelReqDto.getDate(), "N").orElseThrow(() -> {
            log.error("wodDelete() : 해당 날짜에 등록된 WOD 가 없습니다.");
            return new EntityNotFoundException("해당 날짜에 등록된 WOD 가 없습니다.");
        });
        if (!wod.getBox().equals(box)) {
            log.error("wodDelete() : 해당 WOD 가 등록된 Box 와 본인의 Box 가 다릅니다.");
            throw new IllegalArgumentException("해당 WOD 가 등록된 Box 와 본인의 Box 가 다릅니다.");
        }
        if (!reservationRepository.findByBoxAndDateAndDelYn(box, wodCancelReqDto.getDate(), "N", Pageable.unpaged()).isEmpty()) {
            log.error("wodDelete() : 해당 와드에 대한 예약이 있습니다.");
            throw new EntityNotFoundException("해당 와드에 대한 예약이 있습니다.");
        }
        return wod.wodDelete();
    }
}
