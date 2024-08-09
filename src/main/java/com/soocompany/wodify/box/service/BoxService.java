package com.soocompany.wodify.box.service;

import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.box.dto.BoxDetailResDto;
import com.soocompany.wodify.box.dto.BoxListResDto;
import com.soocompany.wodify.box.dto.BoxSaveReqDto;
import com.soocompany.wodify.box.dto.BoxUpdateReqDto;
import com.soocompany.wodify.box.repository.BoxRepository;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class BoxService {

    private final BoxRepository boxRepository;
    private final MemberRepository memberRepository;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;


    @Autowired
    public BoxService(BoxRepository boxRepository, MemberRepository memberRepository, S3Client s3Client) {
        this.boxRepository = boxRepository;
        this.memberRepository = memberRepository;
        this.s3Client = s3Client;
    }


    public BoxSaveReqDto boxCreate(BoxSaveReqDto dto) {
        // 현재 로그인한 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String memberId = authentication.getName();

        // 대표 ID가 유일한지 확인
        Optional<Box> existingBoxByMember = boxRepository.findByMemberIdAndDelYn(Long.parseLong(memberId), "N").stream().findFirst();
        if (existingBoxByMember.isPresent()) {
            String errorMessage = "id가 " + memberId + "인 Member가 이미 다른 Box를 가지고 있습니다";
            log.error("boxCreate() : " + errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        Member member = memberRepository.getReferenceById(Long.parseLong(memberId));

        //맴버에 박스 넣어주기
        Member newMember = memberRepository.findByEmailAndDelYn(member.getEmail(), "N")
                .orElseThrow(() -> new EntityNotFoundException("Test Ceo Initial Data Loader Exception"));

        // MultipartFile에서 파일 저장 경로 얻기 및 AWS S3에 업로드
        MultipartFile logoFile = dto.getLogoPath();
        String s3Path = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            try {
                byte[] bytes = logoFile.getBytes();
                String fileName = UUID.randomUUID() + "_" + logoFile.getOriginalFilename();
                Path path = Paths.get("C:/Users/rnjsc/Desktop/tmp/", fileName);

                // Local PC에 임시 저장
                Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                // AWS S3에 업로드
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(fileName)
                        .build();
                PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest, RequestBody.fromFile(path));
                s3Path = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(fileName)).toExternalForm();
            } catch (IOException e) {
                throw new RuntimeException("이미지 저장 실패", e);
            }
        }

        Box box = boxRepository.save(dto.toEntity(member));
        if (s3Path != null) {
            box.updateLogo(s3Path);
            boxRepository.save(box);
        }

        newMember.memberBoxUpdate(box);
        memberRepository.save(newMember);

        return BoxSaveReqDto.fromEntity(box);
    }


    public BoxSaveReqDto boxUpdate(Long id, BoxUpdateReqDto dto){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String memberId = authentication.getName();

        Box box = boxRepository.findByIdAndDelYn(id, "N")
                .orElseThrow(() -> {
                    String errorMessage = "id가 " + id + "인 Box를 찾을 수 없거나 이미 삭제되었습니다";
                    log.error("boxUpdate() : " + errorMessage);
                    throw new IllegalArgumentException(errorMessage);
                });

        if (!box.getMember().getId().toString().equals(memberId)) {
            String errorMessage = "현재 사용자가 해당 Box를 수정할 권한이 없습니다.";
            log.error("boxUpdate() : " + errorMessage);
            throw new SecurityException(errorMessage);
        }

        // 파일 저장 로직 처리 및 AWS S3에 업로드
        MultipartFile logoFile = dto.getLogo();
        if (logoFile != null && !logoFile.isEmpty()) {
            byte[] bytes = null;
            try {
                bytes = logoFile.getBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String fileName = UUID.randomUUID() + "_" + logoFile.getOriginalFilename();
            Path path = Paths.get("C:/Users/rnjsc/Desktop/tmp/", fileName);

            // Local PC에 임시 저장
            try {
                Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // AWS S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();
            PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest, RequestBody.fromFile(path));
            String s3Path = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(fileName)).toExternalForm();
            box.updateLogo(s3Path);
        }

        // 로고 업데이트를 제외한 다른 필드만 업데이트
        box.updateDetails(dto.getName(),
                box.getLogo(),
                dto.getOperatingHours(),
                dto.getFee(),
                dto.getIntro(),
                dto.getAddress()
        );

        Box updatedBox = boxRepository.save(box);
        return BoxSaveReqDto.fromEntity(updatedBox);
    }



    //박스 폐업 -> 대표, 코치, 회원의 box 정보 null변경
    public void boxDelete() {
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()), "N").orElseThrow(() -> {
            log.error("boxDelete() : id에 맞는 member가 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 member가 존재하지 않습니다.");
        });

        Box box = member.getBox();
        if (box == null) {
            log.error("boxDelete() : 대표의 box가 존재하지 않습니다.");
            throw new EntityNotFoundException("대표의 box가 존재하지 않습니다.");
        }
        // Box 삭제
        box.updateDelYn();
        //박스에 연관된 코치, 회원 리스트
        List<Member> boxMemberList = memberRepository.findByBoxAndDelYn(box, "N");
        for(Member m: boxMemberList){
            m.memberBoxUpdate(null);
        }

            }
        }


//    public Page<BoxListResDto> boxList(Pageable pageable) {
//        Page<Box> boxPage = boxRepository.findAllByDelYn("N", pageable);
//        return boxPage.map(box -> BoxListResDto.builder()
//                .name(box.getName())
//                .logo(box.getLogo())
//                .operatingHours(box.getOperatingHours())
//                .address(box.getAddress())
//                .build());
//    }


//    public BoxDetailResDto boxDetail(Long id) {
//        Box box = boxRepository.findByIdAndDelYn(id, "N")
//                .orElseThrow(() -> {
//                    String errorMessage = "id가 " + id + "인 Box를 찾을 수 없거나 이미 삭제되었습니다";
//                    log.error("boxDetail() : " + errorMessage);
//                    throw  new IllegalArgumentException(errorMessage);
//                });
//
//        return BoxDetailResDto.builder()
//                .name(box.getName())
//                .logo(box.getLogo())
//                .operatingHours(box.getOperatingHours())
//                .fee(box.getFee())
//                .address(box.getAddress())
//                .build();
//    }


