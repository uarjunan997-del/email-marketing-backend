package com.emailMarketing.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.config.JwtUtil;
import com.emailMarketing.subscription.User;
import com.emailMarketing.subscription.UserRepository;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository; this.passwordEncoder = passwordEncoder; this.jwtUtil = jwtUtil; this.authenticationManager = authenticationManager;
    }

    // Frontend currently sends: email, password, firstName, lastName (no username)
    // username is optional; if absent we derive from email local part and ensure uniqueness.
    public record RegisterRequest(
            String username,
            @NotBlank @Size(min=6, max=255) String password,
            @Email @NotBlank String email,
            String firstName,
            String lastName) {}
    public record AuthResponse(String token, Long id, String username, String email, String firstName, String lastName, java.util.Set<String> roles) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        // derive or validate username
        String desiredUsername = (req.username() == null || req.username().isBlank()) ? deriveUsername(req.email()) : req.username().trim();
        if(desiredUsername.length() < 3) return ResponseEntity.badRequest().body("Username too short");
        if(userRepository.findByEmail(req.email()).isPresent()) return ResponseEntity.badRequest().body("Email taken");
        desiredUsername = ensureUniqueUsername(desiredUsername);

        User u = new User();
        u.setUsername(desiredUsername);
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setEmail(req.email());
        if(req.firstName()!=null) u.setFirstName(req.firstName());
        if(req.lastName()!=null) u.setLastName(req.lastName());
        u.getRoles().add("USER");
        userRepository.save(u);
    return ResponseEntity.ok(new AuthResponse(
        jwtUtil.generateAccessToken(u.getUsername(), u.getRoles()),
        u.getId(),
        u.getUsername(),
        u.getEmail(),
        u.getFirstName(),
        u.getLastName(),
        u.getRoles()
    ));
    }

    private String deriveUsername(String email){
        String local = email.substring(0, email.indexOf('@')).toLowerCase();
        // strip non alphanumeric/underscore
        local = local.replaceAll("[^a-z0-9_]+", "");
        if(local.isBlank()) local = "user";
        return local.length()>32 ? local.substring(0,32) : local;
    }

    private String ensureUniqueUsername(String base){
        if(userRepository.findByUsername(base).isEmpty()) return base;
        int suffix=1; String candidate;
        while(true){
            candidate = base + suffix;
            if(candidate.length()>40) candidate = candidate.substring(0,40);
            if(userRepository.findByUsername(candidate).isEmpty()) return candidate;
            suffix++;
            if(suffix>1000) throw new IllegalStateException("Could not allocate unique username");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        return ResponseEntity.ok(new AuthResponse(
            jwtUtil.generateAccessToken(user.getUsername(), user.getRoles()),
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRoles()
        ));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }
}
