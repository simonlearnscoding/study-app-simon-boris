package com.example.user;

import com.example.jwt.CustomUserDetails;
import com.example.task.Task;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/myself")
    public ResponseEntity<Object> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserDetails customUserDetails) {
            return ResponseEntity.ok(customUserDetails.getUser()); // ✅ actual User entity
        }

        throw new RuntimeException("Unexpected principal type: " + principal.getClass().getName());
    }

    @GetMapping("/")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}/tasks")
    public List<Task> getTasksByUser(@PathVariable Long userId) {
        return userService.getTasksByUser(userId);
    }

    @PostMapping("/{userId}/tasks")
    public Task createUserTask(@PathVariable Long userId, @RequestBody CreateTaskRequest request) {
        return userService.createTaskForUser(userId, request.title(), request.description());
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id).orElse(null);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    public record CreateUserRequest(String name, String email) {}

    public record CreateTaskRequest(String title, String description) {}
}
