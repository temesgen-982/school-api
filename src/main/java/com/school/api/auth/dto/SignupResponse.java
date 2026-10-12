package com.school.api.auth.dto;

import com.school.api.branch.dto.BranchResponse;
import com.school.api.school.dto.SchoolResponse;
import com.school.api.user.dto.UserResponse;
import java.util.List;

public record SignupResponse(
        UserResponse owner,
        SchoolResponse school,
        List<BranchResponse> branches) {
}
