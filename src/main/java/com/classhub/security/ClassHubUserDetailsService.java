package com.classhub.security;

import com.classhub.user.UserRepository;
import com.classhub.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ClassHubUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ClassHubUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email;
        try {
            email = UserService.normalizeEmail(username);
        } catch (RuntimeException ex) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return userRepository.findByEmail(email)
                .map(ClassHubUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
