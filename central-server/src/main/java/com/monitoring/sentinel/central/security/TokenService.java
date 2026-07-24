package com.monitoring.sentinel.central.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A unique token per server (architecture doc, section 4.3), unlimited lifetime in V1,
 * manually revocable. The plain-text token is never stored: if the database leaks, an
 * attacker shouldn't be able to impersonate an agent. The token has enough entropy
 * (32 random bytes) that a plain hash (SHA-256, no per-entry salt) stays safe, unlike a
 * low-entropy user password.
 */
@Service
public class TokenService {

	private static final int TOKEN_BYTES = 32;
	private final SecureRandom secureRandom = new SecureRandom();

	public String generateRawToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public boolean matches(String rawToken, String storedHash) {
		if (rawToken == null || storedHash == null) {
			return false;
		}
		return MessageDigest.isEqual(
				hash(rawToken).getBytes(StandardCharsets.UTF_8),
				storedHash.getBytes(StandardCharsets.UTF_8));
	}
}
