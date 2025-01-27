package com.github.litermc.miss.network;

import java.net.URI;

public interface URIServerAddress {
	URI getURI();

	default String getScheme() {
		String scheme = getURI().getScheme();
		return scheme == null ? "" : scheme;
	}
}
