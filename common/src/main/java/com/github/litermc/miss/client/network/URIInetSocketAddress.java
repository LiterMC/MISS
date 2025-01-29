package com.github.litermc.miss.client.network;

import com.github.litermc.miss.network.URIServerAddress;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

public class URIInetSocketAddress extends InetSocketAddress implements URIServerAddress {
	private final URI uri;

	public URIInetSocketAddress(InetSocketAddress addr, URI uri) {
		super(addr.getAddress(), addr.getPort());
		this.uri = uri;
	}

	@Override
	public URI getURI() {
		return this.uri;
	}
}
