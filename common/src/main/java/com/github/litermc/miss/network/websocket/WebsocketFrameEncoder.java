package com.github.litermc.miss.network.websocket;

import io.netty.buffer.ByteBuf;

public class WebsocketFrameEncoder {
	public static final int MAX_FRAME_SIZE = 32767;
	public static final int MAX_PAYLOAD_SIZE = MAX_FRAME_SIZE - 16;

	public WebsocketFrameEncoder(ByteBuf target) {
		this.target = target;
	}

	public void encode(int opCode, ByteBuf payload) {
	}

	private void encodeImpl(int opCode, ByteBuf payload) {
		
	}
}
