package rs.edu.raf.IAMService.data.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import rs.edu.raf.IAMService.data.enums.PermissionType;
import rs.edu.raf.IAMService.data.enums.RoleType;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class EmployeeDto extends UserDto {

    private String name;
    private String surname;
    private String gender;
    private String position;
    private String department;

    public EmployeeDto(
            Long id,
            Date dateOfBirth,
            String email,
            String username,
            String phone,
            String address,
            List<PermissionType> permissions,
            String name,
            String surname,
            String gender,
            String position,
            String department,
            boolean active
    ) {
        super(id, dateOfBirth, email, username, phone, address, RoleType.EMPLOYEE, permissions, active);
        this.name = name;
        this.surname = surname;
        this.gender = gender;
        this.position = position;
        this.department = department;
    }
}
