package com.github.litermc.miss.mixin.client;

import com.github.litermc.miss.network.URIServerAddress;
import com.github.litermc.miss.network.URIInetSocketAddress;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {
	@Inject(method = "resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
	public void resolveAddress(ServerAddress addr, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
		Optional<ResolvedServerAddress> ret = cir.getReturnValue();
		if (!ret.isPresent()) {
			return;
		}
		ResolvedServerAddress resolved = ret.get();
		URI uri = ((URIServerAddress)((Object)(addr))).getURI();
		URIInetSocketAddress uAddr = new URIInetSocketAddress(uri, resolved.asInetSocketAddress().getAddress());
		cir.setReturnValue(Optional.of(new ResolvedServerAddress() {
			@Override
			public String getHostName() {
				return uri.getHost();
			}

			@Override
			public String getHostIp() {
				return uAddr.getAddress().getHostAddress();
			}

			@Override
			public int getPort() {
				return uAddr.getPort();
			}

			@Override
			public InetSocketAddress asInetSocketAddress() {
				return uAddr;
			}
		}));
	}
}
