package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.PolicyMapper;
import com.codeops.vault.dto.request.CreateBindingRequest;
import com.codeops.vault.dto.request.CreatePolicyRequest;
import com.codeops.vault.dto.request.UpdatePolicyRequest;
import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.AccessPolicy;
import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.BindingType;
import com.codeops.vault.entity.enums.PolicyPermission;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.AccessPolicyRepository;
import com.codeops.vault.repository.PolicyBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing Vault access policies, bindings, and permission evaluation.
 *
 * <p>Implements a deny-overrides-allow access control model with wildcard
 * path matching. Policies define what permissions are granted or denied
 * for secret paths matching a glob pattern. Bindings attach policies to
 * users, teams, or services.</p>
 *
 * <h3>Evaluation Semantics</h3>
 * <ol>
 *   <li>Collect all active policies bound to the requesting entity</li>
 *   <li>Filter to policies whose pathPattern matches the secret path</li>
 *   <li>If ANY matching deny policy includes the permission → DENY</li>
 *   <li>If ANY matching allow policy includes the permission → ALLOW</li>
 *   <li>Default: DENY (no matching allow policy)</li>
 * </ol>
 *
 * <h3>Path Matching</h3>
 * <p>Patterns use glob-style matching:</p>
 * <ul>
 *   <li>{@code *} matches any sequence of non-separator characters within a single path segment</li>
 *   <li>Exact paths match exactly</li>
 *   <li>Trailing {@code /*} matches all direct children</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final AccessPolicyRepository policyRepository;
    private final PolicyBindingRepository bindingRepository;
    private final PolicyMapper policyMapper;

    // ─── Policy CRUD ────────────────────────────────────────

    /**
     * Creates a new access policy.
     *
     * @param request The creation request.
     * @param teamId  Team ID from JWT.
     * @param userId  User ID from JWT.
     * @return AccessPolicyResponse.
     * @throws ValidationException if a policy with this name already exists for the team.
     */
    @Transactional
    public AccessPolicyResponse createPolicy(CreatePolicyRequest request, UUID teamId, UUID userId) {
        if (policyRepository.existsByTeamIdAndName(teamId, request.name())) {
            throw new ValidationException("Policy already exists with name: " + request.name());
        }

        AccessPolicy policy = AccessPolicy.builder()
                .teamId(teamId)
                .name(request.name())
                .description(request.description())
                .pathPattern(request.pathPattern())
                .permissions(permissionsToString(request.permissions()))
                .isDenyPolicy(request.isDenyPolicy())
                .isActive(true)
                .createdByUserId(userId)
                .build();

        policy = policyRepository.save(policy);
        log.info("Created policy '{}' for team {}", request.name(), teamId);
        return policyMapper.toResponse(policy, 0);
    }

    /**
     * Gets a policy by ID.
     *
     * @param policyId The policy ID.
     * @return AccessPolicyResponse with binding count.
     * @throws NotFoundException if the policy does not exist.
     */
    @Transactional(readOnly = true)
    public AccessPolicyResponse getPolicyById(UUID policyId) {
        AccessPolicy policy = findPolicyById(policyId);
        int bindingCount = (int) bindingRepository.countByPolicyId(policyId);
        return policyMapper.toResponse(policy, bindingCount);
    }

    /**
     * Lists policies for a team with optional active filter.
     *
     * @param teamId     Team ID.
     * @param activeOnly Whether to return only active policies.
     * @param pageable   Pagination.
     * @return Paginated list of AccessPolicyResponse.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccessPolicyResponse> listPolicies(UUID teamId, boolean activeOnly,
                                                            Pageable pageable) {
        Page<AccessPolicy> page;
        if (activeOnly) {
            page = policyRepository.findByTeamIdAndIsActiveTrue(teamId, pageable);
        } else {
            page = policyRepository.findByTeamId(teamId, pageable);
        }

        List<AccessPolicyResponse> responses = page.getContent().stream()
                .map(p -> policyMapper.toResponse(p, (int) bindingRepository.countByPolicyId(p.getId())))
                .toList();

        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Updates a policy. Only non-null fields in the request are applied.
     *
     * @param policyId The policy ID.
     * @param request  Update request with optional fields.
     * @return Updated AccessPolicyResponse.
     * @throws NotFoundException   if the policy does not exist.
     * @throws ValidationException if name change conflicts with existing policy.
     */
    @Transactional
    public AccessPolicyResponse updatePolicy(UUID policyId, UpdatePolicyRequest request) {
        AccessPolicy policy = findPolicyById(policyId);

        if (request.name() != null && !request.name().isBlank()) {
            if (!request.name().equals(policy.getName())
                    && policyRepository.existsByTeamIdAndName(policy.getTeamId(), request.name())) {
                throw new ValidationException("Policy already exists with name: " + request.name());
            }
            policy.setName(request.name());
        }
        if (request.description() != null) {
            policy.setDescription(request.description());
        }
        if (request.pathPattern() != null && !request.pathPattern().isBlank()) {
            policy.setPathPattern(request.pathPattern());
        }
        if (request.permissions() != null && !request.permissions().isEmpty()) {
            policy.setPermissions(permissionsToString(request.permissions()));
        }
        if (request.isDenyPolicy() != null) {
            policy.setIsDenyPolicy(request.isDenyPolicy());
        }
        if (request.isActive() != null) {
            policy.setIsActive(request.isActive());
        }

        policy = policyRepository.save(policy);
        int bindingCount = (int) bindingRepository.countByPolicyId(policyId);
        log.info("Updated policy '{}'", policy.getName());
        return policyMapper.toResponse(policy, bindingCount);
    }

    /**
     * Deletes a policy and all its bindings.
     *
     * @param policyId The policy ID.
     * @throws NotFoundException if the policy does not exist.
     */
    @Transactional
    public void deletePolicy(UUID policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new NotFoundException("AccessPolicy", policyId);
        }
        bindingRepository.deleteByPolicyId(policyId);
        policyRepository.deleteById(policyId);
        log.info("Deleted policy {} and all bindings", policyId);
    }

    // ─── Binding CRUD ───────────────────────────────────────

    /**
     * Creates a binding between a policy and a target (user/team/service).
     *
     * @param request The binding request.
     * @param userId  User ID from JWT (who created the binding).
     * @return PolicyBindingResponse.
     * @throws NotFoundException   if the policy does not exist.
     * @throws ValidationException if this exact binding already exists.
     */
    @Transactional
    public PolicyBindingResponse createBinding(CreateBindingRequest request, UUID userId) {
        AccessPolicy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new NotFoundException("AccessPolicy", request.policyId()));

        if (bindingRepository.existsByPolicyIdAndBindingTypeAndBindingTargetId(
                request.policyId(), request.bindingType(), request.bindingTargetId())) {
            throw new ValidationException("Binding already exists for this policy, type, and target");
        }

        PolicyBinding binding = PolicyBinding.builder()
                .policy(policy)
                .bindingType(request.bindingType())
                .bindingTargetId(request.bindingTargetId())
                .createdByUserId(userId)
                .build();

        binding = bindingRepository.save(binding);
        log.info("Created binding for policy '{}' ({} -> {})",
                policy.getName(), request.bindingType(), request.bindingTargetId());
        return policyMapper.toBindingResponse(binding, policy.getName());
    }

    /**
     * Lists all bindings for a policy.
     *
     * @param policyId The policy ID.
     * @return List of PolicyBindingResponse.
     * @throws NotFoundException if the policy does not exist.
     */
    @Transactional(readOnly = true)
    public List<PolicyBindingResponse> listBindingsForPolicy(UUID policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new NotFoundException("AccessPolicy", policyId);
        }

        List<PolicyBinding> bindings = bindingRepository.findByPolicyId(policyId);
        return bindings.stream()
                .map(b -> policyMapper.toBindingResponse(b, b.getPolicy().getName()))
                .toList();
    }

    /**
     * Lists all policy bindings that apply to a specific target.
     *
     * @param bindingType The binding type (USER, TEAM, SERVICE).
     * @param targetId    The target ID.
     * @return List of PolicyBindingResponse.
     */
    @Transactional(readOnly = true)
    public List<PolicyBindingResponse> listBindingsForTarget(BindingType bindingType, UUID targetId) {
        List<PolicyBinding> bindings = bindingRepository.findByBindingTypeAndBindingTargetId(
                bindingType, targetId);
        return bindings.stream()
                .map(b -> policyMapper.toBindingResponse(b, b.getPolicy().getName()))
                .toList();
    }

    /**
     * Deletes a specific binding.
     *
     * @param bindingId The binding ID.
     * @throws NotFoundException if the binding does not exist.
     */
    @Transactional
    public void deleteBinding(UUID bindingId) {
        if (!bindingRepository.existsById(bindingId)) {
            throw new NotFoundException("PolicyBinding", bindingId);
        }
        bindingRepository.deleteById(bindingId);
        log.info("Deleted binding {}", bindingId);
    }

    // ─── Access Evaluation ──────────────────────────────────

    /**
     * Evaluates whether a user has a specific permission on a secret path.
     *
     * <p>This is the core access control method. It evaluates all applicable
     * policies (user-level and team-level bindings) and applies
     * deny-overrides-allow semantics.</p>
     *
     * <p>Evaluation steps:</p>
     * <ol>
     *   <li>Find all active bindings for the user (USER type) and team (TEAM type)</li>
     *   <li>Collect the associated active policies</li>
     *   <li>Filter to policies whose pathPattern matches the requested path</li>
     *   <li>Check deny policies first — if any deny policy includes the permission, return DENIED</li>
     *   <li>Check allow policies — if any allow policy includes the permission, return ALLOWED</li>
     *   <li>Default: DENIED</li>
     * </ol>
     *
     * @param userId     The requesting user's ID.
     * @param teamId     The requesting user's team ID.
     * @param secretPath The secret path being accessed.
     * @param permission The permission being requested.
     * @return An AccessDecision indicating ALLOWED or DENIED with reason.
     */
    @Transactional(readOnly = true)
    public AccessDecision evaluateAccess(UUID userId, UUID teamId, String secretPath,
                                          PolicyPermission permission) {
        List<PolicyBinding> userBindings = bindingRepository.findActiveBindingsForTarget(
                teamId, BindingType.USER, userId);
        List<PolicyBinding> teamBindings = bindingRepository.findActiveBindingsForTarget(
                teamId, BindingType.TEAM, teamId);

        List<AccessPolicy> policies = collectPoliciesFromBindings(userBindings, teamBindings);
        return evaluatePolicies(policies, secretPath, permission);
    }

    /**
     * Evaluates access for a service identity (from MCP or inter-service calls).
     *
     * @param serviceId  The requesting service's ID (from Registry).
     * @param teamId     The team context.
     * @param secretPath The secret path being accessed.
     * @param permission The permission being requested.
     * @return AccessDecision.
     */
    @Transactional(readOnly = true)
    public AccessDecision evaluateServiceAccess(UUID serviceId, UUID teamId, String secretPath,
                                                 PolicyPermission permission) {
        List<PolicyBinding> serviceBindings = bindingRepository.findActiveBindingsForTarget(
                teamId, BindingType.SERVICE, serviceId);
        List<PolicyBinding> teamBindings = bindingRepository.findActiveBindingsForTarget(
                teamId, BindingType.TEAM, teamId);

        List<AccessPolicy> policies = collectPoliciesFromBindings(serviceBindings, teamBindings);
        return evaluatePolicies(policies, secretPath, permission);
    }

    // ─── Path Matching ──────────────────────────────────────

    /**
     * Tests whether a secret path matches a policy's glob pattern.
     *
     * <p>Matching rules:</p>
     * <ul>
     *   <li>Exact match: {@code /services/app/db} matches {@code /services/app/db}</li>
     *   <li>Wildcard segment: {@code /services/app/*} matches {@code /services/app/password}
     *       but NOT {@code /services/app/db/password}</li>
     *   <li>Pattern {@code /services/*} matches {@code /services/talent-app} but NOT
     *       {@code /services/talent-app/db}</li>
     *   <li>Root wildcard: {@code /*} matches any single-segment path</li>
     * </ul>
     *
     * <p>This method is package-private for testability.</p>
     *
     * @param secretPath  The actual secret path.
     * @param pathPattern The glob pattern from the policy.
     * @return true if the path matches the pattern.
     */
    boolean matchesPath(String secretPath, String pathPattern) {
        if (secretPath == null || secretPath.isBlank()
                || pathPattern == null || pathPattern.isBlank()) {
            return false;
        }

        String normalizedPath = normalizePath(secretPath);
        String normalizedPattern = normalizePath(pathPattern);

        String[] pathSegments = normalizedPath.split("/", -1);
        String[] patternSegments = normalizedPattern.split("/", -1);

        if (pathSegments.length != patternSegments.length) {
            return false;
        }

        for (int i = 0; i < pathSegments.length; i++) {
            if (patternSegments[i].equals("*")) {
                continue;
            }
            if (!pathSegments[i].equals(patternSegments[i])) {
                return false;
            }
        }

        return true;
    }

    // ─── Statistics ─────────────────────────────────────────

    /**
     * Gets policy counts for a team.
     *
     * @param teamId Team ID.
     * @return Map with keys "total", "active", "deny".
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getPolicyCounts(UUID teamId) {
        List<AccessPolicy> policies = policyRepository.findByTeamId(teamId);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", (long) policies.size());
        counts.put("active", policies.stream().filter(AccessPolicy::getIsActive).count());
        counts.put("deny", policies.stream().filter(AccessPolicy::getIsDenyPolicy).count());
        return counts;
    }

    // ─── Private Helpers ────────────────────────────────────

    /**
     * Finds a policy by ID or throws NotFoundException.
     *
     * @param policyId The policy ID.
     * @return The found AccessPolicy entity.
     * @throws NotFoundException if the policy does not exist.
     */
    private AccessPolicy findPolicyById(UUID policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("AccessPolicy", policyId));
    }

    /**
     * Converts a list of PolicyPermission enums to a comma-separated string.
     *
     * @param permissions The permission list.
     * @return Comma-separated string (e.g., "READ,WRITE,LIST").
     */
    private String permissionsToString(List<PolicyPermission> permissions) {
        return permissions.stream()
                .map(PolicyPermission::name)
                .collect(Collectors.joining(","));
    }

    /**
     * Parses a comma-separated permissions string into a list of PolicyPermission enums.
     *
     * @param permissions The comma-separated string.
     * @return List of PolicyPermission values.
     */
    private List<PolicyPermission> parsePermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .map(PolicyPermission::valueOf)
                .toList();
    }

    /**
     * Collects all policies from multiple binding lists, combining them into a single list.
     *
     * @param bindingLists The binding lists to collect policies from.
     * @return Combined list of AccessPolicy entities.
     */
    @SafeVarargs
    private List<AccessPolicy> collectPoliciesFromBindings(List<PolicyBinding>... bindingLists) {
        return Stream.of(bindingLists)
                .flatMap(List::stream)
                .map(PolicyBinding::getPolicy)
                .toList();
    }

    /**
     * Evaluates a list of policies against a secret path and permission.
     *
     * <p>Implements deny-overrides-allow semantics:</p>
     * <ol>
     *   <li>Filter to policies whose pathPattern matches the path</li>
     *   <li>If any matching deny policy includes the permission → DENIED</li>
     *   <li>If any matching allow policy includes the permission → ALLOWED</li>
     *   <li>Default → DENIED</li>
     * </ol>
     *
     * @param policies   The policies to evaluate.
     * @param secretPath The secret path being accessed.
     * @param permission The permission being requested.
     * @return AccessDecision.
     */
    private AccessDecision evaluatePolicies(List<AccessPolicy> policies, String secretPath,
                                             PolicyPermission permission) {
        List<AccessPolicy> matchingPolicies = policies.stream()
                .filter(p -> matchesPath(secretPath, p.getPathPattern()))
                .toList();

        for (AccessPolicy policy : matchingPolicies) {
            if (policy.getIsDenyPolicy()) {
                List<PolicyPermission> perms = parsePermissions(policy.getPermissions());
                if (perms.contains(permission)) {
                    log.debug("Access DENIED by policy '{}' for path '{}', permission {}",
                            policy.getName(), secretPath, permission);
                    return AccessDecision.denied(policy.getId(), policy.getName());
                }
            }
        }

        for (AccessPolicy policy : matchingPolicies) {
            if (!policy.getIsDenyPolicy()) {
                List<PolicyPermission> perms = parsePermissions(policy.getPermissions());
                if (perms.contains(permission)) {
                    log.debug("Access ALLOWED by policy '{}' for path '{}', permission {}",
                            policy.getName(), secretPath, permission);
                    return AccessDecision.allowed(policy.getId(), policy.getName());
                }
            }
        }

        log.debug("Access DENIED (default) for path '{}', permission {}", secretPath, permission);
        return AccessDecision.defaultDenied();
    }

    /**
     * Normalizes a path by removing trailing slashes.
     *
     * @param path The path to normalize.
     * @return Normalized path without trailing slash.
     */
    private String normalizePath(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
