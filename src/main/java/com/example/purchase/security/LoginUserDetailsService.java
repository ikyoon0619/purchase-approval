package com.example.purchase.security;

import com.example.purchase.org.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 폼에서 받은 아이디로 사용자를 찾아 준다. 비밀번호 비교는 Spring Security가 한다 */
@Service
@RequiredArgsConstructor
public class LoginUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) {
        return memberRepository.findByLoginIdWithDepartment(loginId)
                .map(LoginUser::new)
                .orElseThrow(() -> new UsernameNotFoundException(loginId));
    }
}
