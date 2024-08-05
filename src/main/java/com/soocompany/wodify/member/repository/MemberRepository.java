package com.soocompany.wodify.member.repository;

import com.soocompany.wodify.box.domain.Box;
import com.soocompany.wodify.member.domain.Member;
import com.soocompany.wodify.member.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmailAndDelYn(String email, String delYn);

    Page<Member> findAll(Pageable pageable);

    Page<Member> findAllByDelYn(Pageable pageable, String delYn);

    Optional<Member> findByIdAndDelYn(Long id, String delYn);


    Page<Member> findAllByBoxAndRoleAndDelYn(Pageable pageable, Box box, Role role, String delYn);
    List<Member> findByBoxAndRoleAndDelYn(Box box, Role role, String delYn);

    Optional<Member> findByEmailAndBoxAndDelYn(String email, Box box, String delYn);

    List<Member> findByBoxAndDelYn(Box box, String n);
}
