package com.github.litermc.miss.network.http;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Response {
	private final int major;
	private final int minor;
	private final int status;
	private final String statusText;
	private final Header header;

	private Response(int major, int minor, int status, String statusText, Header header) {
		this.major = major;
		this.minor = minor;
		this.status = status;
		this.statusText = statusText;
		this.header = header;
	}

	public boolean protoAtLeast(int major, int minor) {
		return this.major >= major && this.minor >= minor;
	}

	public boolean protoEquals(int major, int minor) {
		return this.major == major && this.minor == minor;
	}

	public int getStatus() {
		return this.status;
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

	public static Response startReadResponse(byte[] buf) throws DecodeException {
		String httpProto = null;
		int major = -1, minor = -1;
		int statusStartIndex = -1;
		int status = -1;
		String statusText = null;

		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == ' ' || buf[i] == '\t') {
				if (httpProto == null) {
					httpProto = new String(buf, 0, i, StandardCharsets.US_ASCII).toUpperCase();
					if (!httpProto.toUpperCase().startsWith("HTTP/")) {
						throw new DecodeException("Invalid http response: invalid proto");
					}
					httpProto = httpProto.substring("HTTP/".length());
					int dotIndex = httpProto.indexOf('.');
					if (dotIndex == -1) {
						throw new DecodeException("Invalid http response: invalid proto version");
					}
					try {
						major = Integer.valueOf(httpProto.substring(0, dotIndex));
					} catch (NumberFormatException e) {
						throw new DecodeException("Invalid http response: invalid proto version", e);
					}
					try {
						minor = Integer.valueOf(httpProto.substring(dotIndex + 1));
					} catch (NumberFormatException e) {
						throw new DecodeException("Invalid http response: invalid proto version", e);
					}
				} else if (status == -1) {
					try {
						status = Integer.valueOf(new String(buf, statusStartIndex, i - statusStartIndex, StandardCharsets.US_ASCII));
					} catch (NumberFormatException e) {
						throw new DecodeException(e);
					}
				}
			} else if (httpProto != null) {
				if (statusStartIndex == -1) {
					statusStartIndex = i;
				} else if (status != -1) {
					statusText = new String(buf, i, buf.length - i, StandardCharsets.US_ASCII);
					break;
				}
			}
		}
		if (httpProto == null || statusStartIndex == -1) {
			throw new DecodeException("Invalid http response");
		}
		if (status == -1) {
			try {
				status = Integer.valueOf(new String(buf, statusStartIndex, buf.length - statusStartIndex, StandardCharsets.US_ASCII));
			} catch (NumberFormatException e) {
				throw new DecodeException(e);
			}
		}

		return new Response(major, minor, status, statusText, new Header());
	}
}
