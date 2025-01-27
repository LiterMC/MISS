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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements URIServerAddress {
	private static final ServerAddress INVALID = new ServerAddress("server.invalid", 25565);

	@Shadow
	@Final
	private HostAndPort hostAndPort;

	@Unique
	private URI uri;

	@ModifyArgs(method = "<init>(Ljava/lang/String;I)V", at = @At(value = "INVOKE", target = "Lcom/google/common/net/HostAndPort;fromParts(Ljava/lang/String;I)Lcom/google/common/net/HostAndPort;", remap = false))
	private static void initHostAndPort(Args args) {
		URI u;
		try {
			u = new URI(args.<String>get(0));
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return;
		}
		if (u.getHost() != null) {
			args.set(0, u.getHost());
			if (u.getPort() != -1) {
				args.set(1, u.getPort());
			}
		}
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
		if (uri.getHost() == null || uri.getPort() == -1) {
			return INVALID;
		}
		ServerAddress addr = new ServerAddress(uri.getHost(), uri.getPort());
		((ServerAddressMixin)((Object)(addr))).uri = uri;
		return addr;
	}

	@Overwrite
	public static boolean isValidAddress(String str) {
		try {
			URI u = new URI(str);
			if (u.getHost() != null && u.getPort() != -1) {
				return true;
			}
		} catch (URISyntaxException e) {
			return false;
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
