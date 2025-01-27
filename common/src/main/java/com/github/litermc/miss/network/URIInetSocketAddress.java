package com.github.litermc.miss.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

public class URIInetSocketAddress extends InetSocketAddress implements URIServerAddress {
	private final URI uri;

	public URIInetSocketAddress(URI uri, InetAddress ip) {
		super(ip, uri.getPort());
		this.uri = uri;
	}

	@Override
	public URI getURI() {
		return this.uri;
	}
}
