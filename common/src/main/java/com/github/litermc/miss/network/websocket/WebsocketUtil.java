package com.github.litermc.miss.network.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class WebsocketUtil {
	private static final SecureRandom RANDOM = new SecureRandom();

	private WebsocketUtil() {}

	public static String generateKey() {
		byte[] buf = new byte[8];
		RANDOM.nextBytes(buf);
		return Base64.getEncoder().encodeToString(buf);
	}

	public static String calculateAccept(String key) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		md.update(key.getBytes(StandardCharsets.US_ASCII));
		md.update(/* magic string */ "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII));
		return Base64.getEncoder().encodeToString(md.digest());
	}
}
