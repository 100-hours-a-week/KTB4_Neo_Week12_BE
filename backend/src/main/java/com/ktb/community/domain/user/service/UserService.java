package com.ktb.community.domain.user.service;

import com.ktb.community.domain.user.dto.LoginRequestDto;
import com.ktb.community.domain.user.dto.LoginResponseDto;
import com.ktb.community.domain.user.dto.RefreshTokenResponseDto;
import com.ktb.community.domain.user.dto.PasswordUpdateRequestDto;
import com.ktb.community.domain.user.dto.SignUpRequestDto;
import com.ktb.community.domain.user.dto.SignUpResponseDto;
import com.ktb.community.domain.user.dto.UserResponseDto;
import com.ktb.community.domain.user.dto.UserUpdateRequestDto;
import com.ktb.community.domain.user.entity.RefreshToken;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.domain.user.repository.RefreshTokenRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.security.jwt.JwtTokenProvider;
import com.ktb.community.domain.upload.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ImageUploadService imageUploadService;

    private static final int REFRESH_TOKEN_VALIDITY_DAY = 7;

    public SignUpResponseDto signup(SignUpRequestDto request) {

        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())
                || userRepository.existsByNicknameAndDeletedFalse(request.getNickname())) {
            throw new ApiException(ErrorCode.USER_ALREADY_EXISTS);
        }

        if(!request.getPassword().equals(request.getPasswordCheck())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        String profileImage = imageUploadService.reserveVerifiedSignupImage(
                request.getProfileUploadToken()
        );
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                                imageUploadService.consumeSignupImage(request.getProfileUploadToken());
                            } else {
                                imageUploadService.releaseSignupImage(request.getProfileUploadToken());
                            }
                        }
                    }
            );
        }

        User user = new User(
                request.getEmail(),
                encodedPassword,
                request.getNickname(),
                profileImage
        );

        User savedUser = userRepository.save(user);
        if (!synchronizedTransaction) {
            imageUploadService.consumeSignupImage(request.getProfileUploadToken());
        }

        return new SignUpResponseDto(savedUser.getUserId());

    }


    public LoginResponseDto login(LoginRequestDto request) {

        User user = userRepository.findByEmailAndDeletedFalse(request.getEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        RefreshToken savedRefreshToken = refreshTokenRepository.findByUser(user)
                .orElse(null);

        LocalDateTime expiryDate = LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAY);

        if (savedRefreshToken == null) {
            refreshTokenRepository.save(new RefreshToken(user, refreshToken, expiryDate));
        } else {
            savedRefreshToken.updateToken(refreshToken, expiryDate);
        }

        return new LoginResponseDto(user.getUserId(), accessToken, refreshToken);
    }

    public void logout(Long authenticatedUserId) {
        refreshTokenRepository.deleteByUserId(authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getMyPage(Long authenticatedUserId, Long userId) {
        User loginUser = getActiveUser(authenticatedUserId);

        validateUserOwner(loginUser, userId);

        return new UserResponseDto(loginUser.getUserId(), loginUser.getNickname(), loginUser.getEmail(), loginUser.getProfileImage());
    }

    public void updateUser(Long authenticatedUserId, Long userId, UserUpdateRequestDto request) {
        User loginUser = getActiveUser(authenticatedUserId);

        validateUserOwner(loginUser, userId);

        loginUser.update(request.getNickname(), request.getProfileImage());
    }

    public void updatePassword(Long authenticatedUserId, Long userId, PasswordUpdateRequestDto request) {
        User loginUser = getActiveUser(authenticatedUserId);

        validateUserOwner(loginUser, userId);

        if(!request.getPassword().equals(request.getPasswordCheck())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }

        if(!passwordEncoder.matches(request.getCurPassword(), loginUser.getPassword())) {
            throw new ApiException(ErrorCode.INVALID_PASSWORD);
        }

        String encodedNewPassword = passwordEncoder.encode(request.getPassword());
        loginUser.updatePassword(encodedNewPassword);
    }

    public void deleteUser(Long authenticatedUserId, Long userId) {
        User loginUser = getActiveUser(authenticatedUserId);

        validateUserOwner(loginUser, userId);

        refreshTokenRepository.deleteByUser(loginUser);
        loginUser.delete();
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_USER));

        return user;
    }

    private void validateUserOwner(User loginUser, Long targetUserId) {
        if (!loginUser.getUserId().equals(targetUserId)) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

    public RefreshTokenResponseDto refreshAccessToken(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new ApiException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        try {
            jwtTokenProvider.validateRefreshTokenOrThrow(refreshTokenValue);
        } catch (ApiException e) {
            refreshTokenRepository.delete(refreshToken);
            throw e;
        }

        User user = refreshToken.getUser();

        if (user.isDeleted()) {
            refreshTokenRepository.delete(refreshToken);
            throw new ApiException(ErrorCode.UNAUTHORIZED_USER);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(user);

        return new RefreshTokenResponseDto(newAccessToken);
    }
}
