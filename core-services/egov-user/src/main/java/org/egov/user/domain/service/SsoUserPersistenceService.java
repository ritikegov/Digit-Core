package org.egov.user.domain.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.user.domain.exception.sso.IdpPersistenceException;
import org.egov.user.domain.model.User;
import org.egov.user.domain.model.UserIdpDetails;
import org.egov.user.persistence.repository.UserIdpDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SsoUserPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(SsoUserPersistenceService.class);

    private final UserService userService;
    private final UserIdpDetailsRepository userIdpDetailsRepository;

    public SsoUserPersistenceService(UserService userService,
                                     UserIdpDetailsRepository userIdpDetailsRepository) {
        this.userService = userService;
        this.userIdpDetailsRepository = userIdpDetailsRepository;
    }

    /**
     * Updates user and upserts IDP details in a single transaction.
     * Used when user record and IDP details must be persisted atomically (e.g. SSO login with role change or new user).
     */
    @Transactional
    public User updateUserAndUpsertIdpDetails(User user, UserIdpDetails encryptedIdpDetails,
                                              String tenantId, RequestInfo requestInfo) {
        validateIdpPersistenceInput(encryptedIdpDetails, tenantId);
        User updatedUser = userService.updateWithoutOtpValidation(user, requestInfo);
        userIdpDetailsRepository.upsert(encryptedIdpDetails, tenantId);
        return updatedUser;
    }

    /**
     * Upserts IDP details only within a transaction.
     * Used when user record is unchanged but IDP session/MFA details must be persisted (e.g. SSO login, no role change).
     */
    @Transactional
    public void upsertIdpDetailsOnly(UserIdpDetails encryptedIdpDetails, String tenantId) {
        validateIdpPersistenceInput(encryptedIdpDetails, tenantId);
        userIdpDetailsRepository.upsert(encryptedIdpDetails, tenantId);
    }

    private void validateIdpPersistenceInput(UserIdpDetails details, String tenantId) {
        if (details == null) {
            LOG.error("IDP details persistence aborted: details is null for tenantId={}", tenantId);
            throw IdpPersistenceException.invalidInput("details is null");
        }
        if (details.getId() == null) {
            LOG.error("IDP details persistence aborted: details.id is null for tenantId={}", tenantId);
            throw IdpPersistenceException.invalidInput("details.id is null");
        }
        if (tenantId == null) {
            LOG.error("IDP details persistence aborted: tenantId is null for userId={}", details.getId());
            throw IdpPersistenceException.invalidInput("tenantId is null");
        }
    }

    /**
     * Checks if a tokenId has already been used (token replay protection).
     * 
     * @param tokenId the JWT token ID (jti/uti) to check
     * @param tenantId the tenant ID
     * @return true if the tokenId has been used before, false otherwise
     */
    public boolean isTokenReplay(String tokenId, String tenantId) {
        if (tokenId == null || tokenId.isEmpty()) {
            return false;
        }
        
        try {
            return userIdpDetailsRepository.isTokenReplay(tokenId, tenantId);
        } catch (Exception e) {
            LOG.error("Error checking token replay for tokenId={}, tenantId={}", tokenId, tenantId, e);
            // Fail secure: if we can't check, assume it's a replay to be safe
            return true;
        }
    }
}
