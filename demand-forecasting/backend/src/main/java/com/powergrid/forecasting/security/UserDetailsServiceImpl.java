package com.powergrid.forecasting.security;

import com.powergrid.forecasting.repository.PowerGridUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final PowerGridUserRepository userRepository;

    public UserDetailsServiceImpl(PowerGridUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        String principal = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        if (principal.isBlank()) {
            throw new UsernameNotFoundException("POWERGRID user not found");
        }

        return userRepository.findByUsernameIgnoreCase(principal)
                .or(() -> userRepository.findByEmailIgnoreCase(principal))
                .orElseThrow(() -> new UsernameNotFoundException("POWERGRID user not found: " + principal));
    }
}
