package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.common.dto.UserDTO;
import com.ivanka.audioeditor.server.model.AppUser;
import com.ivanka.audioeditor.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/find-or-create")
    public ResponseEntity<?> findOrCreate(
            @RequestParam("name") String name,
            @RequestParam("email") String email) {

        if (name == null || name.trim().isEmpty() || email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Name and email cannot be empty");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return ResponseEntity.badRequest().body("Invalid email format");
        }

        AppUser user = userService.findOrCreate(name.trim(), email.trim());
        UserDTO dto = new UserDTO(user.getId(), user.getUserName(), user.getUserEmail());
        return ResponseEntity.ok(dto);
    }

}