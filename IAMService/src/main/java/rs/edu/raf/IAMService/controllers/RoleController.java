package rs.edu.raf.IAMService.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.edu.raf.IAMService.data.dto.RoleDto;
import rs.edu.raf.IAMService.services.RoleService;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping(value = "/api/roles")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE')")
    @GetMapping("/all")
    public ResponseEntity getAllRoles() {
        try {
            List<RoleDto> roleDtos = roleService.getAllRoles();
            return ResponseEntity.ok(roleDtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Roles retrieval failed.");
        }
    }
}
