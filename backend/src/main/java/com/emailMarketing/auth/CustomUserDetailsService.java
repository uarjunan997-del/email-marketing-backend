package com.emailMarketing.auth;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import com.emailMarketing.subscription.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    public CustomUserDetailsService(UserRepository userRepository){this.userRepository=userRepository;}
    @Override
    public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var u = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    // Map stored roles directly (they should be simple names without ROLE_ prefix)
    String[] roles = u.getRoles().isEmpty() ? new String[]{"USER"} : u.getRoles().stream().map(String::toUpperCase).collect(Collectors.toSet()).toArray(new String[0]);
    return User.withUsername(u.getUsername()).password(u.getPassword()).roles(roles).build();
    }
}
