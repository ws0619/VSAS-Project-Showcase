package au.edu.sydney.a2t0303.service;

/**
 * The details a new user types in when registering.
 * Kept together so the service method does not need six separate parameters.
 */
public record RegistrationRequest(
        String username,
        String password,
        String fullName,
        String email,
        String phone,
        String idKey
) {}
