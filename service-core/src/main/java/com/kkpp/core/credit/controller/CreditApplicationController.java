package com.kkpp.core.credit.controller;

import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.core.credit.dto.request.CropRequest;
import com.kkpp.core.credit.dto.request.InsuranceRequest;
import com.kkpp.core.credit.dto.request.LandRequest;
import com.kkpp.core.credit.dto.response.InsuranceResponse;
import com.kkpp.core.credit.dto.response.StartSessionResponse;
import com.kkpp.core.credit.dto.response.SubmitResponse;
import com.kkpp.core.credit.service.CreditApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "한도 심사 신청")
@RestController
@RequestMapping("/api/v1/credit")
@RequiredArgsConstructor
public class CreditApplicationController {

    private final CreditApplicationService creditApplicationService;

    @PostMapping("/session/start")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<StartSessionResponse> startSession(@Parameter(hidden = true) @AuthUser AuthUserInfo authUser) {
        return ApiResponse.success(creditApplicationService.startSession(authUser.userId()));
    }

    @PostMapping("/land")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> saveLand(@Parameter(hidden = true) @AuthUser AuthUserInfo authUser,
                                      @Valid @RequestBody LandRequest request) {
        creditApplicationService.saveLand(authUser.userId(), request);
        return ApiResponse.success(null, "농지 정보가 임시 저장되었습니다.");
    }

    @Operation(summary = "재배 작물 정보 임시 저장", description = "재배 작물 코드를 입력받아 Redis 심사 세션에 임시 저장합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재배 작물 정보 임시 저장 성공",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "data": null,
                                      "message": "재배 작물 정보가 임시 저장되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "세션 ID 누락 또는 지원하지 않는 작물 코드",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "SES-001", value = """
                                            {
                                              "status": "ERROR",
                                              "errorCode": "SES-001",
                                              "message": "세션 ID가 누락되었습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "CRP-001", value = """
                                            {
                                              "status": "ERROR",
                                              "errorCode": "CRP-001",
                                              "message": "지원하지 않는 작물 코드입니다."
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "유효하지 않은 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-002",
                                      "message": "유효하지 않은 세션입니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "만료된 심사 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-003",
                                      "message": "만료된 심사 세션입니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/crop")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> saveCrop(@Parameter(hidden = true) @AuthUser AuthUserInfo authUser,
                                      @Valid @RequestBody CropRequest request) {
        creditApplicationService.saveCrop(authUser.userId(), request);
        return ApiResponse.success(null, "재배 작물 정보가 임시 저장되었습니다.");
    }

    @Operation(summary = "보험 가입 여부 저장 및 필요 서류 목록 조회", description = "보험 가입 여부를 Redis 심사 세션에 저장하고 다음 단계에서 필요한 서류 목록을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "보험 가입 여부 저장 및 필요 서류 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "hasInsurance=true", value = """
                                            {
                                              "status": "SUCCESS",
                                              "data": {
                                                "requiredDocuments": [
                                                  {
                                                    "documentCode": "AGRI_MANAGEMENT_REGISTRATION",
                                                    "documentName": "농업 경영체 등록 확인서",
                                                    "isRequired": true
                                                  },
                                                  {
                                                    "documentCode": "CROP_DISASTER_INSURANCE",
                                                    "documentName": "농작물 재해보험 가입 증명서",
                                                    "isRequired": true
                                                  }
                                                ]
                                              },
                                              "message": "보험 가입 여부 저장 및 필요 서류 목록이 조회되었습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "hasInsurance=false", value = """
                                            {
                                              "status": "SUCCESS",
                                              "data": {
                                                "requiredDocuments": [
                                                  {
                                                    "documentCode": "AGRI_MANAGEMENT_REGISTRATION",
                                                    "documentName": "농업 경영체 등록 확인서",
                                                    "isRequired": true
                                                  },
                                                  {
                                                    "documentCode": "CROP_DISASTER_INSURANCE",
                                                    "documentName": "농작물 재해보험 가입 증명서",
                                                    "isRequired": false
                                                  }
                                                ]
                                              },
                                              "message": "보험 가입 여부 저장 및 필요 서류 목록이 조회되었습니다."
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "세션 ID 누락",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-001",
                                      "message": "세션 ID가 누락되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "유효하지 않은 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-002",
                                      "message": "유효하지 않은 세션입니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "만료된 심사 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-003",
                                      "message": "만료된 심사 세션입니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/insurance")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<InsuranceResponse> saveInsurance(@Parameter(hidden = true) @AuthUser AuthUserInfo authUser,
                                                        @Valid @RequestBody InsuranceRequest request) {
        InsuranceResponse response = creditApplicationService.saveInsurance(authUser.userId(), request);
        return ApiResponse.success(response, "보험 가입 여부 저장 및 필요 서류 목록이 조회되었습니다.");
    }

    @Operation(summary = "한도 심사 최종 접수", description = "세션에 누적된 농지, 작물, 보험 정보와 제출 서류 파일을 처리하여 한도 심사를 접수합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "한도 심사 신청 완료",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "data": {
                                        "applicationId": "app_1029384756",
                                        "status": "UNDER_REVIEW",
                                        "estimatedCompletion": "1~3일"
                                      },
                                      "message": "한도 심사 신청이 완료되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "세션 ID 누락 또는 필수 서류 누락",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "SES-001", value = """
                                            {
                                              "status": "ERROR",
                                              "errorCode": "SES-001",
                                              "message": "세션 ID가 누락되었습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "DOC-001", value = """
                                            {
                                              "status": "ERROR",
                                              "errorCode": "DOC-001",
                                              "message": "필수 서류가 누락되었습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "APP-002", value = """
                                            {
                                              "status": "ERROR",
                                              "errorCode": "APP-002",
                                              "message": "신청 단계 정보가 누락되었습니다."
                                            }
                                            """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "유효하지 않은 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-002",
                                      "message": "유효하지 않은 세션입니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "진행 중인 심사 존재",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "APP-001",
                                      "message": "이미 접수된 심사 내역이 존재합니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "만료된 심사 세션",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "SES-003",
                                      "message": "만료된 심사 세션입니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "파일 크기 제한 초과",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "DOC-002",
                                      "message": "파일 크기가 제한을 초과했습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "415",
                    description = "지원하지 않는 파일 형식",
                    content = @Content(
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ERROR",
                                      "errorCode": "DOC-003",
                                      "message": "지원하지 않는 파일 형식입니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubmitResponse> submit(
            @Parameter(hidden = true) @AuthUser AuthUserInfo authUser,
            @Parameter(description = "심사 세션 ID", example = "sess_8f9e1a2b3c4d")
            @RequestParam(required = false) String sessionId,
            @Parameter(description = "서류 파일. 필드명 예: files[AGRI_MANAGEMENT_REGISTRATION], files[CROP_DISASTER_INSURANCE]")
            @RequestParam Map<String, MultipartFile> files
    ) {
        SubmitResponse response = creditApplicationService.submit(authUser.userId(), sessionId, files);
        return ApiResponse.success(response, "한도 심사 신청이 완료되었습니다.");
    }
}
