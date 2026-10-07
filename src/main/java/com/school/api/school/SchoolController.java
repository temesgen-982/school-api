package com.school.api.school;

import com.school.api.common.web.ApiPaths;
import com.school.api.common.web.PageResponse;
import com.school.api.school.dto.SchoolCreateRequest;
import com.school.api.school.dto.SchoolPatchRequest;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping(ApiPaths.V1 + "/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PostMapping
    public ResponseEntity<SchoolResponse> create(
            @Valid @RequestBody SchoolCreateRequest request,
            HttpServletRequest httpRequest) {
        SchoolResponse response = schoolService.create(request);
        URI location = URI.create(httpRequest.getRequestURI() + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<SchoolResponse> list(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return schoolService.list(pageable);
    }

    @GetMapping("/{id}")
    public SchoolResponse get(@PathVariable UUID id) {
        return schoolService.getById(id);
    }

    @PatchMapping("/{id}")
    public SchoolResponse patch(@PathVariable UUID id, @Valid @RequestBody SchoolPatchRequest request) {
        return schoolService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, Authentication authentication) {
        schoolService.deactivate(id, CurrentUser.idOf(authentication));
    }
}
