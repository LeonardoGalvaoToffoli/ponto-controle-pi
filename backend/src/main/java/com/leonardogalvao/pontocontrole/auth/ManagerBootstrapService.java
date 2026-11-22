package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserAccountRepository;
import com.leonardogalvao.pontocontrole.users.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ManagerBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerBootstrapService.class);

    private final UserAccountRepository userAccountRepository;
    private final ManagerBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ManagerBootstrapService(
            UserAccountRepository userAccountRepository,
            ManagerBootstrapProperties properties,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void bootstrap() {
        if (userAccountRepository.findByRole(UserRole.GERENTE).isPresent()) {
            LOGGER.info("manager_bootstrap status=preserved");
            return;
        }

        String email = CredentialPolicy.normalizeEmail(properties.email());
        if (!CredentialPolicy.isValidEmail(email)) {
            throw new IllegalStateException("MANAGER_EMAIL deve conter um email válido de até 254 caracteres");
        }
        if (!CredentialPolicy.isValidPassword(properties.password())) {
            throw new IllegalStateException("MANAGER_PASSWORD deve ter entre 12 e 72 bytes em UTF-8");
        }

        UserAccount manager = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(properties.password()),
                UserRole.GERENTE,
                false,
                clock.instant());
        try {
            userAccountRepository.saveAndFlush(manager);
            LOGGER.info("manager_bootstrap status=created userId={}", manager.getId());
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                    "Não foi possível criar o gerente inicial; verifique a unicidade da configuração",
                    exception);
        }
    }
}
