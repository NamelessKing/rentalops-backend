package com.rentalops.tasks.api;

import com.rentalops.tasks.api.dto.CreateTaskRequest;
import com.rentalops.tasks.api.dto.MyTaskItemResponse;
import com.rentalops.tasks.api.dto.TaskClaimResponse;
import com.rentalops.tasks.api.dto.TaskDetailResponse;
import com.rentalops.tasks.api.dto.TaskListItemResponse;
import com.rentalops.tasks.api.dto.TaskPoolItemResponse;
import com.rentalops.tasks.application.TaskApplicationService;
import com.rentalops.tasks.application.TaskQueryService;
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
 * Exposes task management endpoints.
 *
 * <p>This controller is intentionally thin — all business rules live in the service layer.
 * The controller only binds HTTP input, delegates to the service, and returns the result.
 */
@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks", description = "Task management within the authenticated tenant")
public class TaskController {

    private final TaskApplicationService taskApplicationService;
    private final TaskQueryService taskQueryService;

    public TaskController(TaskApplicationService taskApplicationService,
                          TaskQueryService taskQueryService) {
        this.taskApplicationService = taskApplicationService;
        this.taskQueryService = taskQueryService;
    }

    @Operation(summary = "List all tasks", description = "Returns all tasks in the authenticated tenant. Admin only.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task list returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskListItemResponse.class)))
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
    @GetMapping
    public List<TaskListItemResponse> listAdminTasks() {
        return taskQueryService.listAdminTasks();
    }

    @Operation(summary = "Get task detail", description = "Returns full task detail. Admin sees any task; operator sees only their own assigned task.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task detail returned",
                    content = @Content(schema = @Schema(implementation = TaskDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Operator is not the assignee",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public TaskDetailResponse getTaskDetail(
            @Parameter(description = "Task UUID") @PathVariable UUID id) {
        return taskQueryService.getTaskDetail(id);
    }

    @Operation(summary = "Create task", description = "Creates a task in POOL or DIRECT_ASSIGNMENT mode. Admin only.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Task created",
                    content = @Content(schema = @Schema(implementation = TaskDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or invalid enum value",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Property or assignee not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Assignee is not an operator",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDetailResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskApplicationService.createTask(request);
    }

    @Operation(summary = "List pool tasks", description = "Returns PENDING pool tasks compatible with the operator's specialization category.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Pool task list returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskPoolItemResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an operator",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/pool")
    public List<TaskPoolItemResponse> listPoolTasks() {
        return taskQueryService.listPoolTasks();
    }

    @Operation(summary = "List my tasks", description = "Returns all tasks assigned to the authenticated operator.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task list returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MyTaskItemResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an operator",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/my")
    public List<MyTaskItemResponse> listMyTasks() {
        return taskQueryService.listMyTasks();
    }

    @Operation(summary = "Claim a pool task",
               description = "Operator claims a PENDING pool task. Returns the updated task with ASSIGNED status.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Task claimed successfully",
                    content = @Content(schema = @Schema(implementation = TaskClaimResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an operator",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Task already claimed or not available",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/claim")
    public TaskClaimResponse claimTask(
            @Parameter(description = "Task UUID") @PathVariable UUID id) {
        return taskApplicationService.claimTask(id);
    }

    @Operation(summary = "Start a task",
               description = "Transitions the task from ASSIGNED to IN_PROGRESS. Only the assignee can do this.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task started"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not the task assignee",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startTask(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        taskApplicationService.startTask(id);
    }

    @Operation(summary = "Complete a task",
               description = "Transitions the task from IN_PROGRESS to COMPLETED. Only the assignee can do this.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task completed"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not the task assignee",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeTask(@Parameter(description = "Task UUID") @PathVariable UUID id) {
        taskApplicationService.completeTask(id);
    }
}
