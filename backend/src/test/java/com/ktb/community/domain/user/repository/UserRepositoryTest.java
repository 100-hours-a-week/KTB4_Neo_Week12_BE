package com.ktb.community.domain.user.repository;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("회원 가입 성공 시, 해당 유저 정보가 DB에 저장되고, 이때 생성 시간이 자동으로 저장된다.")
    void signupUser_restoreUserInfo() {
        // given
        User user = new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "profile-image"
        );

        // when
        User savedUser = userRepository.saveAndFlush(user);

        // then
        assertThat(savedUser.getUserId()).isNotNull();

        Optional<User> foundUser =  userRepository.findById(savedUser.getUserId());

        assertThat(foundUser.isPresent()).isTrue();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundUser.get().getPassword()).isEqualTo("encoded-password");
        assertThat(foundUser.get().getNickname()).isEqualTo("neo");
        assertThat(foundUser.get().getProfileImage()).isEqualTo("profile-image");

        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("저장된 이메일로 유저 정보 조회할 수 있다.")
    void findByEmail_returnsMatchingUser() {
        // given
        User user1 = new User(
                "neo@example.com",
                "neo-encoded-password",
                "neo",
                "neo-profile-image"
        );

        User user2 = new User (
                "ryan@example.com",
                "ryan-encoded-password",
                "ryan",
                "ryan-profile-iamge"
        );

        userRepository.save(user1);
        userRepository.save(user2);

        // when
        Optional<User> foundUser = userRepository.findByEmail("ryan@example.com");

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("ryan@example.com");
        assertThat(foundUser.get().getNickname()).isEqualTo("ryan");
        assertThat(foundUser.get().getUserId()).isEqualTo(user2.getUserId());
    }

    @Test
    @DisplayName("저장된 이메일이면 true를 반환한다")
    void existsByEmail_existingEmail_returnsTrue() {
        // given
        userRepository.save(new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        ));

        // when & then
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
    }

    @Test
    @DisplayName("저장된 닉네임이면 true를 반환한다")
    void existsByNickname_existingNickname_returnsTrue() {
        // given
        userRepository.save(new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        ));

        // when & then
        assertThat(userRepository.existsByNickname("neo")).isTrue();
        assertThat(userRepository.existsByNickname("unknown")).isFalse();
    }

    @Test
    @DisplayName("삭제되지 않은 이메일로 유저 정보를 조회할 수 있다")
    void findByEmailAndDeletedFalse_returnsActiveUser() {
        // given
        User deletedUser = new User(
                "test@example.com",
                "deleted-encoded-password",
                "deleted-neo",
                "/deleted-profile.png"
        );
        deletedUser.delete();

        User activeUser = new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        );

        userRepository.save(deletedUser);
        userRepository.save(activeUser);

        // when
        Optional<User> foundUser = userRepository.findByEmailAndDeletedFalse("test@example.com");

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUserId()).isEqualTo(activeUser.getUserId());
        assertThat(foundUser.get().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("삭제된 유저의 이메일은 활성 이메일 중복 검사에서 제외된다")
    void existsByEmailAndDeletedFalse_ignoresDeletedUser() {
        // given
        User deletedUser = new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        );
        deletedUser.delete();
        userRepository.save(deletedUser);

        // when & then
        assertThat(userRepository.existsByEmailAndDeletedFalse("test@example.com")).isFalse();

        userRepository.save(new User(
                "test@example.com",
                "new-encoded-password",
                "new-neo",
                "/new-profile.png"
        ));

        assertThat(userRepository.existsByEmailAndDeletedFalse("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("삭제된 유저의 닉네임은 활성 닉네임 중복 검사에서 제외된다")
    void existsByNicknameAndDeletedFalse_ignoresDeletedUser() {
        // given
        User deletedUser = new User(
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        );
        deletedUser.delete();
        userRepository.save(deletedUser);

        // when & then
        assertThat(userRepository.existsByNicknameAndDeletedFalse("neo")).isFalse();

        userRepository.save(new User(
                "new@example.com",
                "new-encoded-password",
                "neo",
                "/new-profile.png"
        ));

        assertThat(userRepository.existsByNicknameAndDeletedFalse("neo")).isTrue();
    }

}
