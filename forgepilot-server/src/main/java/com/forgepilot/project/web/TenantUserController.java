package com.forgepilot.project.web;

import com.forgepilot.project.service.MemberAdminService;
import com.forgepilot.project.service.MemberAdminService.CreateTenantUserRequest;
import com.forgepilot.project.service.MemberAdminService.TenantUserView;
import com.forgepilot.system.security.AuthContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/users")
public class TenantUserController {

    private final MemberAdminService memberAdminService;

    public TenantUserController(MemberAdminService memberAdminService) {
        this.memberAdminService = memberAdminService;
    }

    @GetMapping
    public List<TenantUserView> list() {
        return memberAdminService.listTenantUsers(AuthContext.require());
    }

    @PostMapping
    public TenantUserView create(@RequestBody CreateTenantUserRequest request) {
        return memberAdminService.createTenantUser(AuthContext.require(), request);
    }
}
