package com.github.litermc.miss.network.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.security.SecureRandom;
import java.util.Iterator;

public class WebsocketFrameEncoder {
	private static final SecureRandom RANDOM = new SecureRandom();

	public static final int MAX_FRAME_SIZE = 32767;
	public static final int MAX_PAYLOAD_SIZE = MAX_FRAME_SIZE - 16;

	private final ByteBufAllocator allocator;

	public WebsocketFrameEncoder(ByteBufAllocator allocator) {
		this.allocator = allocator;
	}

	public Iterable<ByteBuf> encode(int opCode, boolean doMask, ByteBuf payload) {
		final int chunks = (payload.readableBytes() + MAX_PAYLOAD_SIZE - 1) / MAX_PAYLOAD_SIZE;
		return new IterableIterator<ByteBuf>() {
			private int i = 0;

			@Override
			public boolean hasNext() {
				return this.i < chunks;
			}

			@Override
			public ByteBuf next() {
				int o = this.i == 0 ? opCode : 0;
				this.i++;
				if (this.i == chunks) {
					o |= 0x80;
				}
				return WebsocketFrameEncoder.this.encodeImpl(o, doMask, payload, Math.min(payload.readableBytes(), MAX_PAYLOAD_SIZE));
			}
		};
	}

	private static interface IterableIterator<T> extends Iterable<T>, Iterator<T> {
		@Override
		default Iterator<T> iterator() {
			return this;
		}
	}

	private ByteBuf encodeImpl(int opCode, boolean doMask, ByteBuf payload, int length) {
		ByteBuf buf = this.allocator.heapBuffer(length + 16);
		buf.writeByte(opCode);
		int maskBit = doMask ? 0x80 : 0;
		if (length <= 0x7d) {
			buf.writeByte(maskBit | length);
		} else if (length <= 0xffff) {
			buf.writeByte(maskBit | 0x7e);
			buf.writeShort(length);
		} else {
			buf.writeByte(maskBit | 0x7f);
			buf.writeLong(length);
		}
		if (doMask) {
			byte[] mask = new byte[4];
			RANDOM.nextBytes(mask);
			buf.writeBytes(mask);
			for (int i = 0; i < length; i++) {
				buf.writeByte(payload.getByte(payload.readerIndex() + i) ^ mask[i & 0x3]);
			}
		} else {
			buf.writeBytes(payload, payload.readerIndex(), length);
		}
		payload.readerIndex(payload.readerIndex() + length);
		return buf;
	}
}
