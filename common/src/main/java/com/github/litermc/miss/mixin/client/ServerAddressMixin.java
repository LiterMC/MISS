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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	@Inject(method = "parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;", at = @At("HEAD"), cancellable = true)
	private static void parseString(String str, CallbackInfoReturnable<ServerAddress> cir) {
		if (str == null) {
			return;
		}
		URI uri;
		try {
			uri = new URI(str);
		} catch (URISyntaxException e) {
			// Fall back to vanilla parser
			return;
		}
		if (uri.getHost() == null) {
			// Fall back to vanilla parser
			return;
		}
		int port = uri.getPort();
		if (port == -1) {
			String s = uri.getScheme();
			port = s == null ? 25565 : switch (s.toUpperCase()) {
			case "WS" -> 80;
			case "WSS" -> 443;
			default -> 25565;
			};
		}
		ServerAddress addr = new ServerAddress(uri.getHost(), port);
		((ServerAddressMixin)((Object)(addr))).uri = uri;
		cir.setReturnValue(addr);
	}

	@Inject(method = "isValidAddress(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
	private static void isValidAddress(String str, CallbackInfoReturnable<Boolean> cir) {
		try {
			URI u = new URI(str);
			if (u.getHost() != null) {
				String s = u.getScheme();
				boolean ok = s == null ? true : switch (s.toUpperCase()) {
				case "WS", "WSS" -> true;
				case "TCP", "UDP" -> true;
				default -> false;
				};
				cir.setReturnValue(ok);
				return;
			}
		} catch (URISyntaxException e) {
			// Fall back to vanilla parser
			return;
		}
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
