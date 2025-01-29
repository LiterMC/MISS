package com.github.litermc.miss.mixin.client;

import com.github.litermc.miss.network.URIServerAddress;
import com.google.common.net.HostAndPort;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.net.URI;
import java.net.URISyntaxException;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements URIServerAddress {
	@Shadow
	@Final
	// @Mutable
	private HostAndPort hostAndPort;

	@Unique
	private URI uri;

	// Just parse host here to prevent HostAndPort throws exception
	@Redirect(method = "<init>(Ljava/lang/String;I)V", at = @At(value = "INVOKE", target = "Lcom/google/common/net/HostAndPort;fromParts(Ljava/lang/String;I)Lcom/google/common/net/HostAndPort;", remap = false))
	private static HostAndPort ServerAddress$init$HostAndPort$fromParts(String host, int port) {
		URI uri = parseHostAndPortAsURI(host, port);
		if (uri == null) {
			return HostAndPort.fromParts(host, port);
		}
		return HostAndPort.fromParts(uri.getHost(), uri.getPort());
	}

	@Inject(method = "<init>(Ljava/lang/String;I)V", at = @At("RETURN"))
	public void ServerAddress$init(String host, int port, CallbackInfo ci) {
		this.uri = parseHostAndPortAsURI(host, port);
		// No need to reassign here since it's already patched by the Redirect above.
		// if (this.uri != null) {
		// 	this.hostAndPort = HostAndPort.fromParts(this.uri.getHost(), this.uri.getPort());
		// }
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
			// A valid address may has a null host.
			// For example: "localhost:25565" will parse "localhost:" as the scheme and "25565" as the scheme specific part
			// Therefore, we fall back to vanilla parser
			return;
		}
		// We will parse the URI string in mixins
		ServerAddress addr = new ServerAddress(str, -1);
		cir.setReturnValue(addr);
	}

	@Inject(method = "isValidAddress(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
	private static void isValidAddress(String str, CallbackInfoReturnable<Boolean> cir) {
		try {
			URI u = new URI(str);
			if (u.getHost() != null) {
				String s = u.getScheme();
				if (s != null) {
					boolean ok = switch (s.toUpperCase()) {
					case "WS", "WSS" -> true;
					case "TCP", "UDP" -> true;
					default -> false;
					};
					cir.setReturnValue(ok);
					return;
				}
			}
		} catch (URISyntaxException e) {
			// Fall back to vanilla parser
			return;
		}
	}

	// TODO: try not use Overwrite but Inject
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

	private static URI parseHostAndPortAsURI(String host, int port) {
		URI uri;
		try {
			uri = new URI(host);
		} catch (URISyntaxException e) {
			return null;
		}
		String path = uri.getRawPath();
		String h = uri.getHost();
		if (h != null) {
			host = h;
			int p = uri.getPort();
			if (p != -1) {
				port = p;
			} else {
				String s = uri.getScheme();
				if (s != null) {
					port = switch (s.toUpperCase()) {
					case "WS" -> 80;
					case "WSS" -> 443;
					default -> 25565;
					};
				}
			}
		} else if (path != null) {
			// Host only string will be parsed as path
			// E.g. "localhost" will have a null scheme, a null host, an invalid port, but path will be "localhost"
			host = path;
			if (port < 0) {
				port = 25565;
			}
			path = null;
		} else {
			// A valid address will never have both null host and a null path at this moment.
			// Because the format of "localhost:25565" should be parsed by vanilla parser
			throw new IllegalArgumentException("This unparseable URI should never be passed to here: " + host);
		}
		try {
			return new URI(uri.getScheme(), uri.getRawUserInfo(), host, port, path, uri.getRawQuery(), uri.getRawFragment());
		} catch (URISyntaxException e) {
			return null;
		}
	}
}
