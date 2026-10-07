package com.school.api.branch;

import com.school.api.branch.dto.BranchCreateRequest;
import com.school.api.branch.dto.BranchPatchRequest;
import com.school.api.branch.dto.BranchResponse;
import com.school.api.common.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schools/{schoolId}/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(
            @PathVariable UUID schoolId,
            @Valid @RequestBody BranchCreateRequest request,
            HttpServletRequest httpRequest) {
        BranchResponse response = branchService.create(schoolId, request);
        URI location = URI.create(httpRequest.getRequestURI() + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<BranchResponse> list(
            @PathVariable UUID schoolId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return branchService.list(schoolId, pageable);
    }

    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable UUID schoolId, @PathVariable UUID id) {
        return branchService.getById(schoolId, id);
    }

    @PatchMapping("/{id}")
    public BranchResponse patch(
            @PathVariable UUID schoolId,
            @PathVariable UUID id,
            @Valid @RequestBody BranchPatchRequest request) {
        return branchService.patch(schoolId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID schoolId, @PathVariable UUID id) {
        branchService.deactivate(schoolId, id);
    }
}
