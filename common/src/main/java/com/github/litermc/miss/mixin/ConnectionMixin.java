package com.github.litermc.miss.mixin;

import com.github.litermc.miss.network.MaybeHTTPForwarder;
import com.github.litermc.miss.network.URIServerAddress;
import com.github.litermc.miss.network.WebsocketForwarder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.util.debugchart.LocalSampleLogger;

import java.net.InetSocketAddress;
import java.net.URI;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Connection.class)
public class ConnectionMixin implements URIServerAddress {
	@Shadow
	@Final
	private PacketFlow receiving;

	@Unique
	private URI uri;

	@Override
	public URI getURI() {
		return this.uri;
	}

	@Inject(method = "connectToServer(Ljava/net/InetSocketAddress;ZLnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;", at = @At("RETURN"))
	private static void connectToServer(InetSocketAddress addr, boolean useNative, LocalSampleLogger logger, CallbackInfoReturnable<Connection> cir) {
		if (!(addr instanceof URIServerAddress uAddr)) {
			return;
		}
		Connection conn = cir.getReturnValue();
		((ConnectionMixin)((Object)(conn))).uri = uAddr.getURI();
	}

	@Inject(method = "channelActive(Lio/netty/channel/ChannelHandlerContext;)V", at = @At("RETURN"))
	private void channelActive(ChannelHandlerContext ctx, CallbackInfo info) {
		Channel channel = ctx.channel();

		switch (this.receiving) {
			case CLIENTBOUND -> {
				WebsocketForwarder forwarder = new WebsocketForwarder();
				channel.pipeline().addAfter("timeout", "websocket_forwarder", forwarder);
			}
			case SERVERBOUND -> {
				MaybeHTTPForwarder forwarder = new MaybeHTTPForwarder();
				channel.pipeline().addAfter("timeout", "maybe_http_forwarder", forwarder);
			}
		}
	}
}