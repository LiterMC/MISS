package com.github.litermc.miss.mixin;

import com.github.litermc.miss.Constants;
import com.github.litermc.miss.network.MaybeHTTPForwarder;
import com.github.litermc.miss.network.WebsocketForwarder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
	@Shadow
	@Final
	private PacketFlow receiving;

	@Inject(at = @At("RETURN"), method = "channelActive(Lio/netty/channel/ChannelHandlerContext;)V")
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