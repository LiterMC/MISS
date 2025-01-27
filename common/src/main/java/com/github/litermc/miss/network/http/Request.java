package com.github.litermc.miss.network.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Request {
	private final String method;
	private final URI uri;
	private final int major;
	private final int minor;
	private final Header header;

	private Request(String method, URI uri, int major, int minor) {
		this.method = method;
		this.uri = uri;
		this.major = major;
		this.minor = minor;
		this.header = new Header();
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

	public Header header() {
		return this.header;
	}

	public String getHeader(String key) {
		return this.header.get(key);
	}

	public List<String> getHeaderValues(String key) {
		return this.header.getValues(key);
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
}
