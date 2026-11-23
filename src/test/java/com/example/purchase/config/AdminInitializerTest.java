package com.example.purchase.config;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.MemberRepository;
import com.example.purchase.org.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 운영 첫 실행(사용자 0명)에 admin 하나만 만드는지 */
class AdminInitializerTest {

    MemberRepository members = mock(MemberRepository.class);
    DepartmentRepository departments = mock(DepartmentRepository.class);
    PositionRepository positions = mock(PositionRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AdminInitializer initializer = new AdminInitializer(members, departments, positions, encoder);

    @BeforeEach
    void setUp() {
        when(departments.findByCode("D000")).thenReturn(Optional.empty());
        when(departments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positions.findAllByOrderByRankOrderAsc()).thenReturn(List.of());
        when(positions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void 사용자가_없고_비밀번호가_있으면_admin을_만든다() {
        when(members.count()).thenReturn(0L);
        ReflectionTestUtils.setField(initializer, "initialPassword", "first-admin-pw");

        initializer.run(null);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(members).save(saved.capture());
        assertThat(saved.getValue().getLoginId()).isEqualTo("admin");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("first-admin-pw", saved.getValue().getPassword())).isTrue(); // 평문 저장 아님
        verify(departments).save(any(Department.class));
        verify(positions).save(any(Position.class));
    }

    @Test
    void 이미_사용자가_있으면_아무것도_안_한다() {
        when(members.count()).thenReturn(40L);
        ReflectionTestUtils.setField(initializer, "initialPassword", "first-admin-pw");

        initializer.run(null);

        verify(members, never()).save(any());
    }

    @Test
    void 비밀번호를_안_정했으면_경고만_하고_만들지_않는다() {
        when(members.count()).thenReturn(0L);
        ReflectionTestUtils.setField(initializer, "initialPassword", "");

        initializer.run(null);

        verify(members, never()).save(any());
    }

    @Test
    void 비밀번호가_8자_미만이면_시작을_막는다() {
        when(members.count()).thenReturn(0L);
        ReflectionTestUtils.setField(initializer, "initialPassword", "1234");

        assertThatThrownBy(() -> initializer.run(null)).hasMessageContaining("8자 이상");
        verify(members, never()).save(any());
    }
}
