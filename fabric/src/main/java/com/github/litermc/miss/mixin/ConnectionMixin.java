package com.github.litermc.miss.mixin;

import com.github.litermc.miss.Constants;
import com.github.litermc.miss.network.MaybeHTTPForwarder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    
    @Inject(at = @At("RETURN"), method = "channelActive(Lio/netty/channel/ChannelHandlerContext;)V")
    private void init(ChannelHandlerContext ctx, CallbackInfo info) {
        Channel channel = ctx.channel();

        MaybeHTTPForwarder forwarder = new MaybeHTTPForwarder();
        channel.pipeline().addAfter("timeout", "maybe_http_forwarder", forwarder);
    }
}