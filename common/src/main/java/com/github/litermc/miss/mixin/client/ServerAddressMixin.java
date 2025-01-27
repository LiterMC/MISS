package com.github.litermc.miss.mixin.client;

import com.github.litermc.miss.network.MaybeHTTPForwarder;
import com.github.litermc.miss.network.URIServerAddress;
import com.github.litermc.miss.network.WebsocketForwarder;
import com.google.common.net.HostAndPort;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.net.URI;
import java.net.URISyntaxException;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements URIServerAddress {
	private static final ServerAddress INVALID = new ServerAddress("server.invalid", 25565);

	@Shadow
	@Final
	private HostAndPort hostAndPort;

	@Unique
	private URI uri;

	@ModifyArg(method = "<init>(Ljava/lang/String;I)V", at = @At(value = "INVOKE", target = "Lcom/google/common/net/HostAndPort;fromParts(Ljava/lang/String;I)Lcom/google/common/net/HostAndPort;", remap = false), index = 0)
	private static String initHostAndPort(String host) {
		URI u;
		try {
			u = new URI(host);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return host;
		}
		if (u.getHost() != null) {
			host = u.getHost();
		}
		return host;
	}

	@Inject(method = "<init>(Ljava/lang/String;I)V", at = @At("RETURN"))
	public void ServerAddress(String host, int port, CallbackInfo ci) {
		try {
			URI u = new URI(host);
			// Host only string will be parsed as path
			String path = u.getRawPath();
			if (u.getHost() != null) {
				host = u.getHost();
				if (u.getPort() != -1) {
					port = u.getPort();
				}
			} else if (path != null) {
				host = path;
				path = null;
			}
			this.uri = new URI(u.getScheme(), u.getRawUserInfo(), host, port, path, u.getRawQuery(), u.getRawFragment());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return;
		}
	}

	@Override
	public URI getURI() {
		if (this.uri == null) {
			try {
				this.uri = new URI(null, null, this.hostAndPort.getHost(), this.hostAndPort.getPort(), "", null, null);
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
		return this.uri;
	}

	@Overwrite
	public static ServerAddress parseString(String str) {
		if (str == null) {
			return INVALID;
		}
		URI uri;
		try {
			uri = new URI(str);
		} catch (URISyntaxException e) {
			try {
				HostAndPort addr = HostAndPort.fromString(str).withDefaultPort(25565);
				if (addr.getHost().isEmpty()) {
					return INVALID;
				}
				return new ServerAddress(addr.getHost(), addr.getPort());
			} catch (IllegalArgumentException e2) {
				return INVALID;
			}
		}
		// Host only string will be parsed as path
		String host = null;
		int port = -1;
		String path = uri.getRawPath();
		if (uri.getHost() != null) {
			host = uri.getHost();
			if (uri.getPort() != -1) {
				port = uri.getPort();
			}
		} else if (path != null) {
			host = path;
			path = null;
		}
		if (host == null) {
			return INVALID;
		}
		if (port == -1) {
			String s = uri.getScheme();
			port = s == null ? 25565 : switch (s.toUpperCase()) {
			case "WS" -> 80;
			case "WSS" -> 443;
			default -> 25565;
			};
		}
		ServerAddress addr = new ServerAddress(host, port);
		((ServerAddressMixin)((Object)(addr))).uri = uri;
		return addr;
	}

	@Overwrite
	public static boolean isValidAddress(String str) {
		try {
			URI u = new URI(str);
			if (u.getHost() != null || u.getRawPath() != null) {
				return true;
			}
		} catch (URISyntaxException e) {
			try {
				HostAndPort addr = HostAndPort.fromString(str);
				if (!addr.getHost().isEmpty()) {
					return true;
				}
			} catch (IllegalArgumentException e2) {
			}
		}
		return false;
	}

	@Overwrite
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		return other instanceof URIServerAddress addr && this.getURI().equals(addr.getURI());
	}

	@Overwrite
	public int hashCode() {
		return this.getURI().hashCode();
	}
}
