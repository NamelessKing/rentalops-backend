package com.rentalops.dashboard.api;

import com.rentalops.dashboard.api.dto.AdminDashboardResponse;
import com.rentalops.dashboard.application.DashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the admin dashboard summary endpoint.
 *
 * <p>Intentionally thin: the controller binds the HTTP route and delegates
 * all query logic and authorization to {@link DashboardQueryService}.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Aggregated admin dashboard data for the authenticated tenant")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @Operation(
            summary = "Admin summary",
            description = "Returns property, operator, task and issue report counts for the authenticated Admin's tenant."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Summary returned",
                    content = @Content(schema = @Schema(implementation = AdminDashboardResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/admin-summary")
    public AdminDashboardResponse getAdminSummary() {
        return dashboardQueryService.getAdminSummary();
    }
}

