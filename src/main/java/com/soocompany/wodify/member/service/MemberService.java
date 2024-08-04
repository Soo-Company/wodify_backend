package com.soocompany.wodify.member.service;

import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.box.repository.BoxRepository;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.member.domain.Role;
import com.soocompany.wodify.member.dto.MemberDetResDto;
import com.soocompany.wodify.member.dto.MemberListResDto;
import com.soocompany.wodify.member.dto.MemberSaveReqDto;
import com.soocompany.wodify.member.dto.MemberUpdateDto;
import com.soocompany.wodify.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.security.Security;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;
    private final BoxRepository boxRepository;

    @Autowired
    public MemberService(MemberRepository memberRepository, BoxRepository boxRepository){
        this.memberRepository = memberRepository;
        this.boxRepository = boxRepository;
    }


    // email, delYn을 통해 member 구하기
    public MemberDetResDto memberFindByEmailAndDelYn(String email, String yn){
        Member member = memberRepository.findByEmailAndDelYn(email, yn).orElseThrow(()->new EntityNotFoundException(email+" : member is not found."));
        return member.detFromEntity();

    }

    //회원가입 멤버 체크용
    public MemberDetResDto isMemberExist(String email, String yn){
        Optional<Member> member = memberRepository.findByEmailAndDelYn(email, yn);
        return member.map(Member::detFromEntity).orElse(null);

    }

    public MemberDetResDto memberRegister(MemberSaveReqDto memberSaveReqDto){
        if(memberRepository.findByEmailAndDelYn(memberSaveReqDto.getEmail(), "N").isPresent()){
            log.error("memberRegister() : 이미 존재하는 email 입니다. 회원가입 불가");
            throw  new IllegalArgumentException("이미 존재하는 email 입니다. 회원가입 불가");
        }
        if(memberSaveReqDto.getName()==null)
            throw new IllegalArgumentException("이름 미입력");
        if(memberSaveReqDto.getPhone()==null)
            throw new IllegalArgumentException("전화번호 미입력");
        if(memberSaveReqDto.getRole()==null)
            throw new IllegalArgumentException("역할 미입력");


        Member newMember = memberRepository.save(memberSaveReqDto.toEntity());
        return newMember.detFromEntity();


    }




    public MemberDetResDto memberDetail() {
        String memberId = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(memberId), "N").orElseThrow(()->{
            log.error("memberDetail() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });
        return member.detFromEntity();
    }


    public MemberDetResDto memberUpdate(MemberUpdateDto memberUpdateDto) {
        String memberId = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(memberId), "N").orElseThrow(()->{
            log.error("memberUpdate() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });
        member.memberInfoUpdate(memberUpdateDto);
        return member.detFromEntity();
    }

    public void memberDelete() {
        String memberId = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(memberId), "N").orElseThrow(()->{
            log.error("memberDelete() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });
        member.updateDelYn();
    }


    //코치의 박스 가입/변경 메서드
    //id = member id(코치의 id)
    public MemberDetResDto coachBoxUpdate(String boxCode) {
        //유효한 박스 코드인지 확인하기
        Box box = boxRepository.findByCodeAndDelYn(boxCode, "N").orElseThrow(()->new IllegalArgumentException("memberBoxUpdate() : 박스코드가 유효하지 않습니다."));

        String memberId = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByIdAndDelYn(Long.parseLong(memberId), "N").orElseThrow(()->{
            log.error("coachBoxUpdate() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });

        //이 코치가 대표면 박스 가입/변경 불가
        if(member.getBox()!=null && member.equals(member.getBox().getMember())) {
            throw new IllegalArgumentException("coachBoxUpdate() : 박스의 대표는 다른 박스로 변경 불가합니다.");
        }

        //box 변경
        member.memberBoxUpdate(box);
        return member.detFromEntity();

    }



    //이 서비스를 이용한 모든 사용자 중 유효한 사용자
    public Page<MemberListResDto> memberList(Pageable pageable) {
        Page<Member> members = memberRepository.findAllByDelYn(pageable, "N");
        Page<MemberListResDto> memberListResDtos = members.map(a->a.listFromEntity());
        return memberListResDtos;

    }


    //코치, 대표가 다니는 박스의 회원 리스트
    public Page<MemberListResDto> boxUserList(Pageable pageable) {
        Long memberId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());   //코치나 ceo의 memberId
        Member loginMember = memberRepository.findById(memberId).orElseThrow(()->{
            log.error("nowMemberList() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });

        Box box = loginMember.getBox();
        if(box == null){
            log.error("nowMemberList() : 로그인한 코치, 대표가 가입한 박스가 존재하지 않습니다.");
            throw new EntityNotFoundException("로그인한 코치, 대표가 가입한 박스가 존재하지 않습니다.");
        }

        Page<Member> members = memberRepository.findByBoxAndRoleAndDelYn(pageable, box, Role.USER, "N");
        Page<MemberListResDto> memberListResDtos = members.map(a->a.listFromEntity());
        return memberListResDtos;

    }


    //박스내 코치 리스트
    public Page<MemberListResDto> boxCoachList(Pageable pageable) {
        Member loginMember = memberRepository.findByIdAndDelYn(Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()), "N").orElseThrow(()->{
            log.error("boxCoachList() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });
        Box  box = loginMember.getBox();
        if(box == null || box.getCode() == null){
            log.error("boxCoachList() : 로그인한 대표의 박스가 존재하지 않습니다.");
            throw new EntityNotFoundException("로그인한 대표의 박스가 존재하지 않습니다.");
        }

        Page<Member> members = memberRepository.findByBoxAndRoleAndDelYn(pageable, box, Role.COACH, "N");
        return members.map(a-> a.listFromEntity());
    }

    //박스 회원 탈퇴
    public String userLeaveBox(String userEmail) {
        Member loginMember = memberRepository.findByIdAndDelYn(Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()), "N").orElseThrow(()->{
            log.error("userLeaveBox() : id에 맞는 회원이 존재하지 않습니다.");
            throw new EntityNotFoundException("id에 맞는 회원이 존재하지 않습니다.");
        });
        Box  box = loginMember.getBox();
        if(box == null || box.getCode() == null){
            log.error("userLeaveBox() : 로그인한 대표의 박스가 존재하지 않습니다.");
            throw new EntityNotFoundException("로그인한 대표의 박스가 존재하지 않습니다.");
        }

        //탈퇴할 멤버
        Member leaveUser = memberRepository.findByEmailAndBoxAndDelYn(userEmail, box, "N").orElseThrow(()->{
            log.error("userLeaveBox() : 탈퇴할 회원이 조회되지 않습니다.");
            throw new EntityNotFoundException("탈퇴할 회원이 조회되지 않습니다.");
        });
        leaveUser.memberBoxUpdate(null);


        return "성공적으로 회원을 탈퇴시켰습니다.";
    }
}
