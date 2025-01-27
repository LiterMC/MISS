package com.github.litermc.miss.network.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Header {
	private final Map<String, List<String>> map;

	public Header() {
		this.map = new HashMap<>();
	}

	public Header(Map<String, List<String>> map) {
		this.map = new HashMap<>();
		for (Map.Entry<String, List<String>> e : map.entrySet()) {
			this.map.put(e.getKey().toUpperCase(), e.getValue());
		}
	}

	public String get(String key) {
		List<String> values = this.map.get(key.toUpperCase());
		if (values == null || values.isEmpty()) {
			return "";
		}
		return values.get(0);
	}

	public List<String> getValues(String key) {
		return this.map.get(key.toUpperCase());
	}

	public void put(String key, String value) {
		this.map.computeIfAbsent(key.toUpperCase(), (k) -> new ArrayList<>()).add(value);
	}

	public void remove(String key) {
		this.map.remove(key.toUpperCase());
	}

	public void parseFrom(byte[] buf) throws DecodeException {
		int lastEnd = 0;
		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == '\r' || buf[i] == '\n') {
				this.parseLine(buf, lastEnd, i);
				if (buf[i] == '\r' && i + 1 < buf.length && buf[i + 1] == '\n') {
					i++;
				}
				lastEnd = i + 1;
			}
		}
	}

	private void parseLine(byte[] buf, int start, int end) throws DecodeException {
		for (int i = start; i < end; i++) {
			if (buf[i] == ':') {
				String key = new String(buf, start, i - start).trim();
				String value = new String(buf, i + 1, end - i - 1).trim();
				this.put(key, value);
				return;
			}
		}
		throw new DecodeException("Invalid http request: invalid header");
	}
}
