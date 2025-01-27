package com.github.litermc.miss.network.websocket;

import io.netty.buffer.ByteBuf;

public class WebsocketFrameDecoder {
	private boolean finished = false;
	private byte opCode = -1;
	private int payloadNeededLength = -1;
	private boolean maskReady = false;
	private final byte[] mask = new byte[4];
	private final ByteBuf payload;

	public WebsocketFrameDecoder(ByteBuf target) {
		this.payload = target;
	}

	public byte getOpCode() {
		return this.opCode;
	}

	public ByteBuf getPayload() {
		return this.payload;
	}

	public boolean decode(ByteBuf buf) throws WebsocketDecodeException {
		while (buf.readableBytes() > 0) {
			if (this.opCode == -1) {
				short b = buf.readUnsignedByte();
				this.opCode = (byte)(b & 0x0f);
				this.finished = (b & 0x80) == 0x80;
				this.payloadNeededLength = -1;
			} else if (this.payloadNeededLength == 0) {
				if (this.finished) {
					throw new IllegalStateException("Decode is already finished");
				}
				short b = buf.readUnsignedByte();
				if ((b & 0x0f) != 0) {
					throw new WebsocketDecodeException("Unexpected non-continuous frame");
				}
				this.finished = (b & 0x80) == 0x80;
				this.payloadNeededLength = -1;
			}
			if (this.payloadNeededLength == -1) {
				short b = buf.getUnsignedByte(buf.readerIndex());
				if ((b & 0x80) == 0) {
					throw new WebsocketDecodeException("mask bit must be 1");
				}
				this.maskReady = false;
				int length = b & 0x7f;
				if (length <= 0x7d) {
					buf.readByte();
					this.payloadNeededLength = length;
				} else if (length == 0x7e) {
					if (buf.readableBytes() < 3) {
						return false;
					}
					buf.readByte();
					this.payloadNeededLength = buf.readUnsignedShort();
				} else if (length == 0x7f) {
					if (buf.readableBytes() < 3) {
						return false;
					}
					buf.readByte();
					long leng = buf.readLong();
					if (leng < 0 || leng > Integer.MAX_VALUE) {
						throw new WebsocketDecodeException("Frame is too large");
					}
					this.payloadNeededLength = (int)(leng);
				}
			}
			if (!this.maskReady) {
				if (buf.readableBytes() < 4) {
					return false;
				}
				buf.readBytes(this.mask);
				this.maskReady = true;
			}
			if (this.payloadNeededLength > 0) {
				int readable = Math.min(buf.readableBytes(), this.payloadNeededLength);
				for (int i = 0; i < readable; i++) {
					this.payload.writeByte(buf.readByte() ^ this.mask[i & 0x3]);
					this.payloadNeededLength--;
				}
			}
			if (this.finished && this.payloadNeededLength == 0) {
				return true;
			}
		}
		return false;
	}
}
