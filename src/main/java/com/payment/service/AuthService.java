package com.payment.service;

import com.payment.domain.Merchant;
import com.payment.repository.MerchantRepository;
import com.payment.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    public AuthService(MerchantRepository merchantRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuditService auditService) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
    }

    @Transactional
    public Merchant register(String name, String email, String password) {
        if (merchantRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setEmail(email);
        merchant.setPasswordHash(passwordEncoder.encode(password));
        merchant = merchantRepository.save(merchant);
        log.info("Merchant registered: id={}, email={}", merchant.getId(), merchant.getEmail());
        auditService.log(null, "MERCHANT_REGISTERED", "merchantId=" + merchant.getId() + ", email=" + email);
        return merchant;
    }

    public String login(String email, String password) {
        Merchant merchant = merchantRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(password, merchant.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        String token = jwtTokenProvider.createToken(merchant.getId(), merchant.getEmail());
        log.info("Merchant logged in: id={}, email={}", merchant.getId(), merchant.getEmail());
        auditService.log(merchant.getId(), "MERCHANT_LOGIN", "email=" + email);
        return token;
    }
}
