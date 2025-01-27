package com.github.litermc.miss.network.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Request {
	private final String method;
	private final URI uri;
	private final int major;
	private final int minor;
	private final Map<String, List<String>> header;

	private Request(String method, URI uri, int major, int minor) {
		this.method = method;
		this.uri = uri;
		this.major = major;
		this.minor = minor;
		this.header = new HashMap<>();
	}

	public String getMethod() {
		return this.method;
	}

	public URI getURI() {
		return this.uri;
	}

	public boolean protoAtLeast(int major, int minor) {
		return this.major >= major && this.minor >= minor;
	}

	public String getHeader(String key) {
		List<String> values = this.header.get(key.toUpperCase());
		if (values == null || values.isEmpty()) {
			return null;
		}
		return values.get(0);
	}

	public List<String> getHeaderValues(String key) {
		return this.header.get(key.toUpperCase());
	}

	public static Request startReadRequest(byte[] buf) throws DecodeException {
		String method = null;
		int uriStartIndex = -1;
		URI uri = null;
		String httpProto = null;

		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == ' ' || buf[i] == '\t') {
				if (method == null) {
					method = new String(buf, 0, i, StandardCharsets.US_ASCII).toUpperCase();
				} else if (uri == null) {
					try {
						uri = new URI(new String(buf, uriStartIndex, i - uriStartIndex, StandardCharsets.US_ASCII));
					} catch (URISyntaxException e) {
						throw new DecodeException(e);
					}
				}
			} else if (method != null) {
				if (uriStartIndex == -1) {
					uriStartIndex = i;
				} else if (uri != null) {
					httpProto = new String(buf, i, buf.length - i, StandardCharsets.US_ASCII);
					break;
				}
			}
		}
		if (httpProto == null) {
			throw new DecodeException("Invalid http request");
		}

		if (!httpProto.toUpperCase().startsWith("HTTP/")) {
			throw new DecodeException("Invalid http request: invalid proto");
		}
		httpProto = httpProto.substring("HTTP/".length());
		int dotIndex = httpProto.indexOf('.');
		if (dotIndex == -1) {
			throw new DecodeException("Invalid http request: invalid proto version");
		}
		int major, minor;
		try {
			major = Integer.valueOf(httpProto.substring(0, dotIndex));
		} catch (NumberFormatException e) {
			throw new DecodeException("Invalid http request: invalid proto version", e);
		}
		try {
			minor = Integer.valueOf(httpProto.substring(dotIndex + 1));
		} catch (NumberFormatException e) {
			throw new DecodeException("Invalid http request: invalid proto version", e);
		}

		return new Request(method, uri, major, minor);
	}

	public void parseHeader(byte[] buf) throws DecodeException {
		int lastEnd = 0;
		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == '\r' || buf[i] == '\n') {
				this.parseHeaderLine(buf, lastEnd, i);
				if (buf[i] == '\r' && i + 1 < buf.length && buf[i + 1] == '\n') {
					i++;
				}
				lastEnd = i + 1;
			}
		}
	}

	private void parseHeaderLine(byte[] buf, int start, int end) throws DecodeException {
		for (int i = start; i < end; i++) {
			if (buf[i] == ':') {
				String key = new String(buf, start, i - start).trim();
				String value = new String(buf, i + 1, end - i - 1).trim();
				this.header.computeIfAbsent(key.toUpperCase(), (k) -> new ArrayList<>()).add(value);
				return;
			}
		}
		throw new DecodeException("Invalid http request: invalid header");
	}
}
