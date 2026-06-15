package com.kkpp.auth.controller;

import com.kkpp.auth.dto.request.UpdateUserProfileRequest;
import com.kkpp.auth.dto.response.UserProfileResponse;
import com.kkpp.auth.service.UserService;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "마이페이지 사용자 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 프로필 조회", description = "JWT 토큰으로 인증된 사용자의 이름, 휴대폰 번호, 주소 정보를 반환합니다.")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthUser AuthUserInfo authUser) {
        return ApiResponse.success(userService.getUserProfile(authUser.userId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "회원정보 수정", description = "JWT 토큰으로 인증된 사용자의 주소 정보(주소, 상세 주소, 우편번호)를 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원정보 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 잘못된 요청 본문"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @AuthUser AuthUserInfo authUser,
            @RequestBody @Valid UpdateUserProfileRequest request
    ) {
        return ApiResponse.success(
                userService.updateUserProfile(authUser.userId(), request),
                "회원정보가 수정되었습니다."
        );
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "JWT 토큰으로 인증된 사용자를 탈퇴 처리(비활성화)합니다. 데이터는 보존되며 재로그인이 차단됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public ApiResponse<Void> withdraw(@AuthUser AuthUserInfo authUser) {
        userService.withdraw(authUser.userId());
        return ApiResponse.success(null, "회원 탈퇴가 완료되었습니다.");
    }
}
