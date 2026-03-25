package com.rentalops.issuereports.api;

import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskRequest;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskResponse;
import com.rentalops.issuereports.api.dto.CreateIssueReportRequest;
import com.rentalops.issuereports.api.dto.CreateIssueReportResponse;
import com.rentalops.issuereports.api.dto.DismissIssueReportResponse;
import com.rentalops.issuereports.api.dto.IssueReportDetailResponse;
import com.rentalops.issuereports.api.dto.IssueReportListItemResponse;
import com.rentalops.issuereports.application.IssueReportApplicationService;
import com.rentalops.issuereports.application.IssueReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes issue report endpoints.
 *
 * <p>This controller is intentionally thin — all business rules live in the service layer.
 * The controller only binds HTTP input, delegates to the service, and returns the result.
 */
@RestController
@RequestMapping("/issue-reports")
@Tag(name = "Issue Reports", description = "Issue report management within the authenticated tenant")
public class IssueReportController {

    private final IssueReportApplicationService issueReportApplicationService;
    private final IssueReportQueryService issueReportQueryService;

    public IssueReportController(IssueReportApplicationService issueReportApplicationService,
                                 IssueReportQueryService issueReportQueryService) {
        this.issueReportApplicationService = issueReportApplicationService;
        this.issueReportQueryService = issueReportQueryService;
    }

    @Operation(summary = "List issue reports", description = "Returns all issue reports for the authenticated tenant. Admin only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = IssueReportListItemResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public List<IssueReportListItemResponse> listIssueReports() {
        return issueReportQueryService.listIssueReports();
    }

    @Operation(summary = "Create issue report", description = "Operator creates a new field issue report. Status is always OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Issue report created",
                    content = @Content(schema = @Schema(implementation = CreateIssueReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not an operator",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Property not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateIssueReportResponse createIssueReport(@Valid @RequestBody CreateIssueReportRequest request) {
        return issueReportApplicationService.createIssueReport(request);
    }

    @Operation(summary = "Get issue report detail", description = "Returns full detail for a single issue report. Admin only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detail returned",
                    content = @Content(schema = @Schema(implementation = IssueReportDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public IssueReportDetailResponse getIssueReportDetail(
            @Parameter(description = "Issue report UUID") @PathVariable UUID id) {
        return issueReportQueryService.getIssueReportDetail(id);
    }

    @Operation(summary = "Convert issue report to task",
            description = "Admin converts an OPEN issue report into a task. Property is inherited from the report.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Converted — returns both updated report and new task",
                    content = @Content(schema = @Schema(implementation = ConvertIssueReportToTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Issue report not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Issue report already reviewed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/convert-to-task")
    public ConvertIssueReportToTaskResponse convertToTask(
            @Parameter(description = "Issue report UUID") @PathVariable UUID id,
            @Valid @RequestBody ConvertIssueReportToTaskRequest request) {
        return issueReportApplicationService.convertToTask(id, request);
    }

    @Operation(summary = "Dismiss issue report",
            description = "Admin archives an OPEN issue report as DISMISSED without creating a task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dismissed",
                    content = @Content(schema = @Schema(implementation = DismissIssueReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Issue report not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Issue report already reviewed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/dismiss")
    public DismissIssueReportResponse dismiss(
            @Parameter(description = "Issue report UUID") @PathVariable UUID id) {
        return issueReportApplicationService.dismiss(id);
    }
}

