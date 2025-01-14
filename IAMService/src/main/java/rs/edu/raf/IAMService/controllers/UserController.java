package rs.edu.raf.IAMService.controllers;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import rs.edu.raf.IAMService.data.dto.*;
import rs.edu.raf.IAMService.data.entites.User;
import rs.edu.raf.IAMService.data.enums.RoleType;
import rs.edu.raf.IAMService.exceptions.EmailTakenException;
import rs.edu.raf.IAMService.exceptions.MissingRoleException;
import rs.edu.raf.IAMService.jwtUtils.JwtUtil;
import rs.edu.raf.IAMService.services.UserService;
import rs.edu.raf.IAMService.utils.ChangedPasswordTokenUtil;
import rs.edu.raf.IAMService.utils.SubmitLimiter;
import rs.edu.raf.IAMService.validator.PasswordValidator;

import java.util.Optional;

import rs.edu.raf.IAMService.data.dto.CorporateClientDto;
import rs.edu.raf.IAMService.data.dto.PrivateClientDto;

@RestController
@CrossOrigin
@RequestMapping(
        value = "/api/users",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
)
public class UserController {
    private final HttpServletRequest request;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SubmitLimiter submitLimiter;
    private final ChangedPasswordTokenUtil changedPasswordTokenUtil;
    private final PasswordValidator passwordValidator;

    @Autowired
    public UserController(UserService userService, ChangedPasswordTokenUtil changedPasswordTokenUtil, PasswordEncoder passwordEncoder, SubmitLimiter submitLimiter, PasswordValidator passwordValidator, HttpServletRequest request, JwtUtil jwtUtil) {
        this.userService = userService;
        this.changedPasswordTokenUtil = changedPasswordTokenUtil;
        this.passwordEncoder = passwordEncoder;
        this.submitLimiter = submitLimiter;
        this.passwordValidator = passwordValidator;
        this.request = request;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<?> createEmployee(@RequestBody EmployeeDto employeeDto) {
        try {
            EmployeeDto newEmployeeDto = userService.createEmployee(employeeDto);
            return ResponseEntity.ok(newEmployeeDto.getId());
        } catch (EmailTakenException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (MissingRoleException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }


    @PostMapping(path = "/changePassword/{email}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<PasswordChangeTokenDto> InitiatesChangePassword(@PathVariable String email) {

        if (!submitLimiter.allowRequest(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        int port = this.request.getServerPort();
        String baseURL = "http://localhost:" + port + "/api/users/changePasswordSubmit/";
        PasswordChangeTokenDto passwordChangeTokenDto =
                changedPasswordTokenUtil.generateToken(userService.findByEmail(email.toLowerCase()), baseURL);
        userService.sendToQueue(email, passwordChangeTokenDto.getUrlLink());
        return ResponseEntity.ok().body(passwordChangeTokenDto);
    }


    @PutMapping(path = "/changePasswordSubmit/{token} ", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> changePasswordSubmit(String newPassword, PasswordChangeTokenDto passwordChangeTokenDto) {
        String tokenWithoutBearer = request.getHeader("authorization").replace("Bearer ", "");
        Claims extractedToken = jwtUtil.extractAllClaims(tokenWithoutBearer);
        Optional<User> userOptional = userService.findUserByEmail(passwordChangeTokenDto.getEmail());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Korisnik sa emailom: " + passwordChangeTokenDto.getEmail() + " ne postoji ili nije pronadjen");
        }

        User user = userOptional.get();
        if (!extractedToken.get("email").toString().equalsIgnoreCase(passwordChangeTokenDto.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Nemate autorizaciju da promenite mail: " + passwordChangeTokenDto.getEmail());
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Korisnik vec koristi tu sifru");
        }

        if (!passwordValidator.isValid(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pogresan format lozinke");
        }

        if (changedPasswordTokenUtil.isTokenValid(passwordChangeTokenDto)) {
            user.setPassword(passwordEncoder.encode(newPassword));
            userService.updateEntity(user);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(401).body("Token za mail: " + passwordChangeTokenDto.getEmail() + " nije vise validan");

    }

    @PostMapping("/public/private-client")
    public PrivateClientDto createPrivateClient(@RequestBody PrivateClientDto clientDto) {
        return userService.createPrivateClient(clientDto);
    }

    @PostMapping("/public/corporate-client")
    public CorporateClientDto createCorporateClient(@RequestBody CorporateClientDto clientDto) {
        return userService.createCorporateClient(clientDto);
    }

    @PatchMapping("/public/{clientId}/activate")
    public Long activateClient(@PathVariable String clientId,
                               @RequestBody ClientActivationDto dto) {
        return userService.activateClient(clientId, dto.getPassword());
    }

    @Operation(
            summary = "Find user by email",
            description = "Finds a user by their email address."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found, returns userDto"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping(path = "/findByEmail/{email}", consumes = MediaType.ALL_VALUE)
    //  @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE', 'USER')")
    public ResponseEntity<?> findByEmail(@PathVariable
                                         @Parameter(description = "Email address of the user to be found", required = true)
                                         String email) {
        UserDto userDto = userService.findByEmail(email);
        if (userDto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(userDto);
    }

    @Operation(summary = "Find User by ID", description = "Returns a user by their ID.")
    @ApiResponse(responseCode = "200", description = "User found, returns userDto", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping(path = "/findById/{id}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> findById(@PathVariable Long id) {
        UserDto userDto = userService.findById(id);
        if (userDto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(userDto);
    }

    @Operation(summary = "Delete User by Email", description = "Deletes a user by their email.")
    @ApiResponse(responseCode = "200", description = "User deleted successfully return deleted UserDto")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @DeleteMapping(path = "/delete/{email}", consumes = MediaType.ALL_VALUE)
    @PreAuthorize(value = "hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE', 'ROLE_USER')")
    @Transactional
    public ResponseEntity<?> deleteUserByEmail(@PathVariable String email) {
        Claims claims = getClaims(request);
        if (claims == null) {
            return ResponseEntity.status(401).build();
        }

        RoleType roleType = RoleType.valueOf((String) claims.get("role"));

        if (roleType.equals(RoleType.USER)) {
            if (!email.equals(claims.get("email"))) {
                return ResponseEntity.status(403).build();
            }
        }
        UserDto userDto = userService.findByEmail(email);
        if (roleType.equals(RoleType.EMPLOYEE)) {
            if (!userDto.getRole().equals(RoleType.USER) && !email.equals(claims.get("email"))) {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(userService.deleteUserByEmail(email));
    }

    @PutMapping(path = "/updateEmployee", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(value = "hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE')")
    public ResponseEntity<?> updateEmployee(@RequestBody EmployeeDto employeeDto) {
        return updateUser(employeeDto);
    }

    @PreAuthorize(value = "hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE','ROLE_USER')")
    @PutMapping(path = "/updateCorporateClient", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCorporateClient(@RequestBody CorporateClientDto corporateClientDto) {
        return updateUser(corporateClientDto);
    }

    @PutMapping(path = "/updatePrivateClient", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(value = "hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE','ROLE_USER')")
    public ResponseEntity<?> updatePrivateClient(@RequestBody PrivateClientDto privateClientDto) {
        return updateUser(privateClientDto);
    }

    @GetMapping(path = "/findAll", consumes = MediaType.ALL_VALUE)
    @PreAuthorize(value = "hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE')")
    public ResponseEntity<?> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    public ResponseEntity<?> updateUser(UserDto userDto) {
        Claims claims = getClaims(request);
        if (claims == null) {
            return ResponseEntity.status(401).build();
        }
        RoleType roleType = RoleType.valueOf((String) claims.get("role"));
        UserDto userDtoFromDB = userService.findByEmail(userDto.getEmail());
        if (roleType.equals(RoleType.USER)) {
            if (userDto.getEmail().equals(claims.get("email"))) {
                if (validationCheck(userDto, userDtoFromDB)) {
                    return ResponseEntity.ok(userService.updateUser(userDto));
                }
                return ResponseEntity.status(401).build();

            } else return ResponseEntity.status(403).build();
        }
        if (roleType.equals(RoleType.EMPLOYEE)) {
            if ((userDto.getRole().equals(RoleType.USER) || userDto.getEmail().equals(claims.get("email"))) && validationCheck(userDto, userDtoFromDB)) {
                return ResponseEntity.ok(userService.updateUser(userDto));
            } else return ResponseEntity.status(403).build();
        }

        if (roleType.equals(RoleType.ADMIN)) {
            if (validationCheck(userDto, userDtoFromDB)) {
                UserDto ud = userService.updateUser(userDto);
                return ResponseEntity.ok(ud);
            } else {
                return ResponseEntity.status(403).build();
            }
        }
        return ResponseEntity.ok(userService.updateUser(userDto));
    }

    public Claims getClaims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isEmpty()) {
            authHeader = request.getHeader("authorization");
            if (authHeader == null || authHeader.isEmpty()) {
                return null;
            }
        }
        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractAllClaims(token);
    }

    public boolean validationCheck(UserDto userDto, UserDto userDtoFromDB) {
        if (userDto.getEmail().equalsIgnoreCase(userDtoFromDB.getEmail()) && userDto.getRole().equals(userDtoFromDB.getRole()) && userDto.getPermissions().equals(userDtoFromDB.getPermissions()) && userDto.getId().equals(userDtoFromDB.getId()) && userDto.getUsername().equals(userDtoFromDB.getUsername())) {
            if (userDto instanceof CorporateClientDto && userDtoFromDB instanceof CorporateClientDto) {
                if (((CorporateClientDto) userDto).getPrimaryAccountNumber().equals(((CorporateClientDto) userDtoFromDB).getPrimaryAccountNumber())) {
                    return true;
                }
            }
            if (userDto instanceof PrivateClientDto && userDtoFromDB instanceof PrivateClientDto) {
                if (((PrivateClientDto) userDto).getPrimaryAccountNumber().equals(((PrivateClientDto) userDtoFromDB).getPrimaryAccountNumber())) {
                    return true;
                }
            }
            return true;
        }
        return false;

    }


    @PutMapping(path = "/activateEmployee/{id}")
    @Secured("ADMIN")
    public ResponseEntity<Boolean> ActivateEmployee(@PathVariable int id) {

        try{
            userService.employeeActivation(id);
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Boolean.FALSE);
        }

        return ResponseEntity.status(HttpStatus.OK).body(Boolean.TRUE);
    }
    @PutMapping(path = "/deactivateEmployee/{id}")
    @Secured("ADMIN")
    public ResponseEntity<Boolean> DeactivateEmployee(@PathVariable int id) {

        try{
            userService.employeeDeactivation(id);
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Boolean.FALSE);
        }

        return ResponseEntity.status(HttpStatus.OK).body(Boolean.TRUE);
    }

}


