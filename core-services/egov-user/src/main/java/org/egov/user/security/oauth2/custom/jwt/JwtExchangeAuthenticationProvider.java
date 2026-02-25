package org.egov.user.security.oauth2.custom.jwt;

import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.user.config.*;
import org.egov.user.domain.exception.DuplicateUserNameException;
import org.egov.user.domain.exception.UserNotFoundException;
import org.egov.user.domain.exception.sso.OidcProviderConfigException;
import org.egov.user.domain.exception.sso.SsoMissingParamException;
import org.egov.user.domain.exception.sso.SsoUserMappingException;
import org.egov.user.domain.model.Role;
import org.egov.user.domain.model.SecureUser;
import org.egov.user.domain.model.User;
import org.egov.user.domain.model.enums.UserType;
import org.egov.user.domain.service.UserService;
import org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile;
import org.egov.user.security.oauth2.custom.service.IdpGraphService;
import org.egov.user.security.oauth2.custom.service.impl.NoOpGraphService;
import org.egov.user.utils.ProjectEmployeeStaffUtil;
import org.egov.user.web.contract.auth.OidcValidatedJwt;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static org.springframework.util.StringUtils.isEmpty;

@Slf4j
@Component
public class JwtExchangeAuthenticationProvider implements AuthenticationProvider {

    private final JwtValidationService jwtValidationService;
    private final UserService userService;
    private final MultiStateInstanceUtil centraInstanceUtil;
    private final ProjectEmployeeStaffUtil projectEmployeeStaffUtil;
    private final OidcProviderSupplier oidcProviderSupplier;
    private final List<IdpGraphService> graphServices;
    private final AccessTokenMfaExtractor accessTokenMfaExtractor;
    private final SsoDefaultPasswordResolver ssoDefaultPasswordResolver;
    private final NoOpGraphService noOpGraphService;

    public JwtExchangeAuthenticationProvider(
            JwtValidationService jwtValidationService,
            UserService userService, MultiStateInstanceUtil centraInstanceUtil,
            ProjectEmployeeStaffUtil projectEmployeeStaffUtil,
            OidcProviderSupplier oidcProviderSupplier,
            List<IdpGraphService> graphServices,
            AccessTokenMfaExtractor accessTokenMfaExtractor,
            SsoDefaultPasswordResolver ssoDefaultPasswordResolver,
            NoOpGraphService noOpGraphService) {
        this.jwtValidationService = jwtValidationService;
        this.userService = userService;
        this.centraInstanceUtil = centraInstanceUtil;
        this.projectEmployeeStaffUtil = projectEmployeeStaffUtil;
        this.oidcProviderSupplier = oidcProviderSupplier;
        this.graphServices = graphServices != null ? graphServices : new ArrayList<>();
        this.accessTokenMfaExtractor = requireNonNull(accessTokenMfaExtractor,
                JwtConstants.MFA_EXTRACTOR_REQUIRED_MESSAGE);
        this.ssoDefaultPasswordResolver = ssoDefaultPasswordResolver;
        this.noOpGraphService = noOpGraphService;
    }

    /**
     * Authenticates a user using JWT exchange flow with SSO integration.
     *
     * <p>This method handles the complete authentication lifecycle for SSO users:
     * <ol>
     *   <li>Validates JWT token and extracts tenant ID, user type, and provider information</li>
     *   <li>Validates required parameters (tenant ID, user type)</li>
     *   <li>Looks up existing user by issuer, external user ID, and tenant</li>
     *   <li>If user exists: updates user information with latest JWT claims and MFA details</li>
     *   <li>If user not found: creates new HRMS user with employee/project staff mapping</li>
     *   <li>Applies MFA details from access token and provider-specific graph service</li>
     *   <li>Sets creator ID from OID digits if available</li>
     *   <li>Validates account status (active, not locked)</li>
     *   <li>Unlocks account if eligible based on failed login attempts</li>
     *   <li>Resets failed login attempts and returns authenticated user</li>
     * </ol>
     *
     * <p>Handles both existing user updates and new user creation scenarios with proper
     * exception handling for missing users, duplicate users, inactive accounts, and locked accounts.
     *
     * @param authentication the JwtExchangeAuthenticationToken containing JWT token, optional auth token, and tenant ID
     * @return UsernamePasswordAuthenticationToken with authenticated SecureUser and role-based authorities
     * @throws SsoMissingParamException if tenant ID or user type is missing/invalid
     * @throws SsoUserMappingException if user is inactive, account is permanently locked, or duplicate user conflict
     * @throws OidcProviderConfigException if provider configuration is not found
     * @throws UserNotFoundException if user lookup fails (handled internally for new user creation)
     * @throws DuplicateUserNameException if user creation results in duplicate username (fatal error)
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        String authToken = null;
        String tenantId = null;
        if (authentication instanceof JwtExchangeAuthenticationToken) {
            authToken = ((JwtExchangeAuthenticationToken) authentication).getAuthToken();
            tenantId = ((JwtExchangeAuthenticationToken) authentication).getTenantId();
        }

        OidcValidatedJwt jwt = jwtValidationService.validate(token, tenantId);

        String userType = jwt.getUserType();
        if (centraInstanceUtil.getIsEnvironmentCentralInstance()) {
            MDC.put(UserServiceConstants.TENANTID_MDC_STRING, tenantId);
        }

        if (isEmpty(tenantId)) {
            throw SsoMissingParamException.tenantIdMissing();
        }
        if (isEmpty(userType) || isNull(UserType.fromValue(userType))) {
            throw SsoMissingParamException.userTypeMissing();
        }

        AuthProperties.Provider provider = getProviderById(jwt.getProviderId(), tenantId);
        assertIssuerMatchesProvider(jwt, provider);
        AccessTokenMfaDetails mfaDetails = accessTokenMfaExtractor.extract(authToken);

        User user;
        RequestInfo requestInfo;
        try {
            user = userService.getUniqueUser(jwt.getIssuer(), jwt.getExternalUserId(), tenantId,
                    UserType.fromValue(userType));
            requestInfo = getRequestInfo(user);
            User userForUpdate = createUserForSsoUpdate(user, jwt);
            applyMfaDetailsToUser(userForUpdate, mfaDetails);
            resolveGraphService(provider).enrichUserWithMfaDetails(userForUpdate, provider, jwt.getOid());
            if (jwt.getOid() != null) {
                String oidDigits = jwt.getOid().replaceAll("\\D", "");
                if (!oidDigits.isEmpty()) {
                    long creatorId = Long.parseLong(oidDigits.substring(0, Math.min(oidDigits.length(), 10)));
                    userForUpdate.setCreatedBy(creatorId);
                    userForUpdate.setLastModifiedBy(creatorId);
                    userForUpdate.setLoggedInUserId(creatorId);
                }
            }
            requestInfo.getUserInfo().setId(userForUpdate.getId());
            user = userService.updateWithoutOtpValidation(userForUpdate, requestInfo);
        } catch (UserNotFoundException e) {
            log.info("User not found for oid: {}, creating new user", jwt.getOid(), e);
            requestInfo = getRequestInfo(jwt.getRoles(), jwt.getUserType(), jwt.getOid());

            String password = ssoDefaultPasswordResolver.generatePassword();
            org.egov.user.domain.model.hrms.User userToCreate = convertJwtToUser(jwt, provider);
            userToCreate.setPassword(password);
            Optional<EmployeeCreationProfile> profileOpt = resolveGraphService(provider)
                    .getEmployeeCreationProfile(provider, jwt.getOid());
            String employeeType = profileOpt.map(EmployeeCreationProfile::getEmployeeType).filter(StringUtils::hasText)
                    .orElse(OidcConfigConstants.DEFAULT_EMPLOYEE_TYPE);
            String designation = profileOpt.map(EmployeeCreationProfile::getDesignation).filter(StringUtils::hasText)
                    .orElse(OidcConfigConstants.DEFAULT_DESIGNATION_ID);
            String department = profileOpt.map(EmployeeCreationProfile::getDepartment).filter(StringUtils::hasText)
                    .orElse(OidcConfigConstants.DEFAULT_DEPARTMENT_CODE);
            org.egov.user.domain.model.hrms.User hrmsUser = projectEmployeeStaffUtil.createEmployeeAndProjectStaff(
                    userToCreate,
                    employeeType,
                    designation,
                    department,
                    provider.getDefaultEmployeeStatus(),
                    tenantId,
                    jwt.getOid(),
                    requestInfo);
            String userUuid = hrmsUser.getUserServiceUuid();
            log.info("Created HRMS user and staff mapping for user service uuid: {}", userUuid);
            User createdUser = convertHrmsUserToUser(hrmsUser);
            createdUser.setPassword(password);
            createdUser.setTenantId(tenantId);
            createdUser.setIdpIssuer(jwt.getIssuer());
            createdUser.setIdpSubject(jwt.getSubject());
            createdUser.setAuthProvider(jwt.getProviderId());
            createdUser.setJwtToken(jwt.getRawToken());
            createdUser.setIdpTokenExp(jwt.getExpirationTime());
            createdUser.setLastSsoLoginAt(jwt.getIssuanceTime());
            createdUser.setActive(Boolean.TRUE);
            createdUser.setEmailId(jwt.getPreferredUsername());
            if (jwt.getOid() != null) {
                String oidDigits = jwt.getOid().replaceAll("\\D", "");
                if (!oidDigits.isEmpty()) {
                    long creatorId = Long.parseLong(oidDigits.substring(0, Math.min(oidDigits.length(), 10)));
                    createdUser.setCreatedBy(creatorId);
                    createdUser.setLastModifiedBy(creatorId);
                }
            }
            applyMfaDetailsToUser(createdUser, mfaDetails);
            resolveGraphService(provider).enrichUserWithMfaDetails(createdUser, provider, jwt.getOid());

            requestInfo.getUserInfo().setId(createdUser.getId());
            requestInfo.getUserInfo().setUuid(createdUser.getUuid());
            user = userService.updateWithoutOtpValidation(createdUser, requestInfo);
        } catch (DuplicateUserNameException e) {
            log.error("Fatal: duplicate user conflict for oid: {}", jwt.getOid(), e);
            throw SsoUserMappingException.duplicateUser(e);
        }

        if (user.getActive() == null || !user.getActive()) {
            throw SsoUserMappingException.userInactive();
        }

        if (user.getAccountLocked() != null && user.getAccountLocked()) {
            if (userService.isAccountUnlockAble(user)) {
                user = unlockAccount(user, requestInfo);
            } else {
                throw SsoUserMappingException.accountLocked();
            }
        }

        List<GrantedAuthority> grantedAuths = new ArrayList<>();
        grantedAuths.add(new SimpleGrantedAuthority(provider.getRolePrefix() + user.getType()));
        final SecureUser secureUser = new SecureUser(getUser(user));
        userService.resetFailedLoginAttempts(user);
        return new UsernamePasswordAuthenticationToken(secureUser, user.getPassword(), grantedAuths);
    }

    /**
     * Resolves the graph/MFA enrichment service for the given provider.
     * Returns the first implementation that supports the provider, or a no-op if none match.
     */
    private IdpGraphService resolveGraphService(AuthProperties.Provider provider) {
        return graphServices.stream()
                .filter(s -> s.supports(provider))
                .findFirst()
                .orElse(noOpGraphService);
    }

    /**
     * Gets the provider configuration by provider ID and tenant ID.
     *
     * <p>This method searches through all available OIDC providers to find a matching
     * configuration based on both provider ID and tenant ID. The match requires exact
     * equality for both fields with proper null checks and string trimming.
     *
     * <p>The search is performed as a stream operation that filters providers by:
     * <ul>
     *   <li>Non-null provider ID that matches the given providerId (trimmed)</li>
     *   <li>Non-null tenant ID that exactly matches the given tenantId</li>
     * </ul>
     *
     * @param providerId the provider ID to look up (must not be null)
     * @param tenantId the tenant ID to look up (must not be null)
     * @return the matching provider configuration
     * @throws OidcProviderConfigException if no provider is found with the given provider ID and tenant ID combination
     */
    private AuthProperties.Provider getProviderById(String providerId, String tenantId) {
        return oidcProviderSupplier.getProviders().stream()
                .filter(p -> p.getId() != null && p.getId().trim().equals(providerId) && p.getTenantId() !=null && p.getTenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> OidcProviderConfigException.providerNotFound(providerId));
    }

    /**
     * Ensures the JWT issuer matches the provider's issuer URI or one of its aliases.
     * Rejects the token if there is no match (null-safe).
     *
     * @param jwt      the validated JWT
     * @param provider the resolved provider configuration
     * @throws OidcProviderConfigException if the token issuer does not match the provider
     */
    private void assertIssuerMatchesProvider(OidcValidatedJwt jwt, AuthProperties.Provider provider) {
        String tokenIssuer = jwt.getIssuer();
        if (!StringUtils.hasText(tokenIssuer)) {
            throw OidcProviderConfigException.issuerMismatch(String.valueOf(tokenIssuer), provider.getId());
        }
        String issuerUri = provider.getIssuerUri();
        if (StringUtils.hasText(issuerUri) && tokenIssuer.trim().equals(issuerUri.trim())) {
            return;
        }
        List<String> aliases = provider.getIssuerAliases();
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias) && tokenIssuer.trim().equals(alias.trim())) {
                    return;
                }
            }
        }
        throw OidcProviderConfigException.issuerMismatch(tokenIssuer, provider.getId());
    }

    /**
     * Converts an HRMS user object to a domain User object.
     * Maps roles from HRMS format to domain format.
     *
     * @param user the HRMS user object to convert
     * @return converted domain User object
     */
    private User convertHrmsUserToUser(org.egov.user.domain.model.hrms.@Valid @NotNull User user) {
        Set<Role> domainRoles = new HashSet<>();
        if (user.getRoles() != null) {
            for (org.egov.user.domain.model.hrms.Role role : user.getRoles()) {
                Role domainRole = new Role();
                domainRole.setTenantId(user.getTenantId());
                domainRole.setCode(role.getCode());
                domainRoles.add(domainRole);
            }
        }
        return User.builder()
                .uuid(user.getUserServiceUuid())
                .id(user.getId())
                .name(user.getName())
                .username(user.getUserName())
                .emailId(user.getEmailId())
                .mobileNumber(user.getMobileNumber())
                .mobileValidationMandatory(false)
                .roles(domainRoles)
                .loggedInUserId(user.getId())
                .build();
    }

    /**
     * Checks if this authentication provider supports the given authentication type.
     *
     * @param auth the authentication class to check
     * @return true if the authentication is a JwtExchangeAuthenticationToken, false otherwise
     */
    @Override
    public boolean supports(Class<?> auth) {
        return JwtExchangeAuthenticationToken.class.isAssignableFrom(auth);
    }

    /**
     * Creates a RequestInfo object from a User domain object.
     * Converts domain roles to contract roles.
     *
     * @param user the user domain object
     * @return RequestInfo with user information
     */
    private RequestInfo getRequestInfo(User user) {
        Set<Role> domain_roles = user.getRoles();
        List<org.egov.common.contract.request.Role> contract_roles = new ArrayList<>();
        for (org.egov.user.domain.model.Role role : domain_roles) {
            contract_roles.add(
                    org.egov.common.contract.request.Role.builder().code(role.getCode()).name(role.getName()).build());
        }

        org.egov.common.contract.request.User userInfo = org.egov.common.contract.request.User.builder()
                .uuid(user.getUuid())
                .type(user.getType() != null ? user.getType().name() : null).roles(contract_roles).build();
        return RequestInfo.builder().userInfo(userInfo).build();
    }

    /**
     * Creates a RequestInfo object from role codes, user type, and user UUID.
     * Used when creating a new user before the user exists in the system.
     *
     * @param roles set of role codes
     * @param userType the user type string
     * @param userUuid the user UUID
     * @return RequestInfo with user information
     */
    private RequestInfo getRequestInfo(Set<String> roles, String userType, String userUuid) {
        List<org.egov.common.contract.request.Role> contract_roles = new ArrayList<>();
        for (String role : roles) {
            contract_roles.add(org.egov.common.contract.request.Role.builder().code(role).name(role).build());
        }

        org.egov.common.contract.request.User userInfo = org.egov.common.contract.request.User.builder().uuid(userUuid)
                .type(userType).roles(contract_roles).id(97L).build();
        return RequestInfo.builder().userInfo(userInfo).build();
    }

    /**
     * Converts a validated JWT to an HRMS User object.
     * Extracts user information from JWT claims and generates username if configured.
     *
     * @param jwt the validated JWT containing user claims
     * @param provider the OIDC provider configuration
     * @return HRMS User object ready for creation
     */
    private org.egov.user.domain.model.hrms.User convertJwtToUser(OidcValidatedJwt jwt, AuthProperties.Provider provider) {
        List<org.egov.user.domain.model.hrms.Role> roles = new ArrayList<>();
        if (!CollectionUtils.isEmpty(jwt.getRoles())) {
            for (String role : jwt.getRoles()) {
                org.egov.user.domain.model.hrms.Role domainRole = new org.egov.user.domain.model.hrms.Role();
                domainRole.setCode(role);
                domainRole.setName(role);
                domainRole.setTenantId(jwt.getTenantId());
                roles.add(domainRole);
            }
        }

        String username = jwt.getPreferredUsername();

        return org.egov.user.domain.model.hrms.User.builder()
                .uuid(jwt.getExternalUserId())
                .emailId(jwt.getEmail())
                .active(true)
                .accountLocked(false)
                .tenantId(jwt.getTenantId())
                .type(jwt.getUserType())
                .roles(roles)
                .userName(username)
                .name(jwt.getName())
                .dob(provider.getDefaultDob())
                .createdBy(jwt.getOid())
                .build();
    }


    /**
     * Converts a domain User object to a contract User object for API responses.
     *
     * @param user the domain User object
     * @return contract User object with user information
     */
    private org.egov.user.web.contract.auth.User getUser(User user) {
        org.egov.user.web.contract.auth.User authUser = org.egov.user.web.contract.auth.User.builder().id(user.getId())
                .userName(user.getUsername()).uuid(user.getUuid())
                .name(user.getName()).mobileNumber(user.getMobileNumber()).emailId(user.getEmailId())
                .locale(user.getLocale()).active(user.getActive()).type(user.getType().name())
                .roles(toAuthRole(user.getRoles())).tenantId(user.getTenantId())
                .build();

        if (user.getPermanentAddress() != null)
            authUser.setPermanentCity(user.getPermanentAddress().getCity());

        return authUser;
    }

    /**
     * Converts domain Role objects to contract Role objects.
     *
     * @param domainRoles set of domain Role objects
     * @return set of contract Role objects
     */
    private Set<org.egov.user.web.contract.auth.Role> toAuthRole(Set<org.egov.user.domain.model.Role> domainRoles) {
        if (domainRoles == null)
            return new HashSet<>();
        return domainRoles.stream().map(org.egov.user.web.contract.auth.Role::new).collect(Collectors.toSet());
    }

    /**
     * Unlock account and disable existing failed login attempts for the user
     *
     * @param user to be unlocked
     * @return Updated user
     */
    private User unlockAccount(User user, RequestInfo requestInfo) {
        User userToBeUpdated = user.toBuilder()
                .accountLocked(false)
                .password(null)
                .build();

        User updatedUser = userService.updateWithoutOtpValidation(userToBeUpdated, requestInfo);
        userService.resetFailedLoginAttempts(userToBeUpdated);

        return updatedUser;
    }

    /**
     * Creates a User object for SSO update from existing user and JWT claims.
     * Updates user information from JWT while preserving existing user data.
     *
     * @param user the existing user object
     * @param jwt the validated JWT with updated claims
     * @return User object ready for update
     */
    private User createUserForSsoUpdate(User user, OidcValidatedJwt jwt) {
        User copy = new User();
        BeanUtils.copyProperties(user, copy);

        copy.setIdpTokenExp(jwt.getExpirationTime());
        copy.setLastSsoLoginAt(jwt.getIssuanceTime());
        copy.setAuthProvider(jwt.getProviderId());
        copy.setJwtToken(jwt.getRawToken());
        copy.setIdpSubject(jwt.getSubject());
        copy.setIdpIssuer(jwt.getIssuer());
        copy.setName(jwt.getName());
        copy.setEmailId(jwt.getPreferredUsername());
        copy.setRoles(toDomainRoles(jwt.getRoles(), user.getTenantId()));
        copy.setPassword(null);
        copy.setMobileNumber(null);
        copy.setUsername(null);

        return copy;
    }

    /**
     * Converts a set of role code strings to domain Role objects.
     *
     * @param roles set of role code strings
     * @param tenantId the tenant ID for the roles
     * @return set of domain Role objects
     */
    private Set<Role> toDomainRoles(Set<String> roles, String tenantId) {
        if (CollectionUtils.isEmpty(roles)) {
            return new HashSet<>();
        }
        return roles.stream()
                .map(roleCode -> Role.builder()
                        .code(roleCode)
                        .tenantId(tenantId)
                        .build())
                .collect(Collectors.toSet());
    }

    /**
     * Applies MFA details from access token to user object (only non-null fields).
     * Used before the single update so MFA is persisted in one call.
     *
     * @param user the user object to update
     * @param mfa the MFA details extracted from access token
     */
    private void applyMfaDetailsToUser(User user, AccessTokenMfaDetails mfa) {
        if (user == null || mfa == null)
            return;
        if (mfa.getMfaEnabled() != null)
            user.setMfaEnabled(mfa.getMfaEnabled());
        if (mfa.getMfaPhoneLast4() != null)
            user.setMfaPhoneLast4(mfa.getMfaPhoneLast4());
        if (mfa.getMfaDeviceName() != null)
            user.setMfaDeviceName(mfa.getMfaDeviceName());
        if (mfa.getMfaDetails() != null)
            user.setMfaDetails(mfa.getMfaDetails());
        if (mfa.getMfaRegisteredOn() != null)
            user.setMfaRegisteredOn(mfa.getMfaRegisteredOn());
    }

}
