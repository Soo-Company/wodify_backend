package com.soocompany.wodify.wod.controller;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.soocompany.wodify.common.dto.CommonResDto;
import com.soocompany.wodify.wod.domain.Wod;
import com.soocompany.wodify.wod.dto.WodCancelReqDto;
import com.soocompany.wodify.wod.dto.WodResDto;
import com.soocompany.wodify.wod.dto.WodSaveReqDto;
import com.soocompany.wodify.wod.service.WodService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
@RestController
@RequiredArgsConstructor
@RequestMapping("/wod")
public class WodController {
    private final WodService wodService;
    @PreAuthorize("hasRole('COACH')")
    @PostMapping("/save")
    public ResponseEntity<?> wodSave(@RequestBody WodSaveReqDto wodSaveReqDto) {
        HttpStatus code = HttpStatus.CREATED;
        String msg = "WOD 를 등록했습니다.";
        Long savedId = wodService.wodSave(wodSaveReqDto).getId();
        CommonResDto commonResDto = new CommonResDto(code, msg, savedId);
        return new ResponseEntity<>(commonResDto, HttpStatus.CREATED);
    }

    @GetMapping("/find")
    public ResponseEntity<?> wodFind(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {

        HttpStatus code = HttpStatus.OK;
        String msg = "WOD 를 찾았습니다.";
        WodResDto wodResDto = wodService.wodFind(date);
        CommonResDto commonResDto = new CommonResDto(code, msg, wodResDto);
        return new ResponseEntity<>(commonResDto, code);
    }

    @PreAuthorize("hasRole('COACH')")
    @PatchMapping("/delete")
    public ResponseEntity<?> wodDelete(@RequestBody WodCancelReqDto wodCancelReqDto) {
        String msg = "WOD 를 삭제했습니다.";
        HttpStatus code = HttpStatus.OK;
        Wod wod = wodService.wodDelete(wodCancelReqDto);
        return new ResponseEntity<>(new CommonResDto(code, msg, wod.getId()), code);
    }
}
