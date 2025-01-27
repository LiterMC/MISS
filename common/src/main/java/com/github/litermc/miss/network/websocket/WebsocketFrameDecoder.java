package com.github.litermc.miss.network.websocket;

import io.netty.buffer.ByteBuf;

public class WebsocketFrameDecoder {
	private boolean finished = false;
	private byte opCode = -1;
	private boolean masking = false;
	private int payloadNeededLength = -1;
	private int maskIndex = -1;
	private final byte[] mask = new byte[4];
	private final ByteBuf payload;
	private final boolean requireMask;

	public WebsocketFrameDecoder(ByteBuf target, boolean requireMask) {
		this.payload = target;
		this.requireMask = requireMask;
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
				short code = buf.readUnsignedByte();
				if ((code & 0x70) != 0) {
					throw new WebsocketDecodeException("Unexpected use of reserved bits");
				}
				this.opCode = (byte)(code & 0x0f);
				this.finished = (code & 0x80) == 0x80;
				this.payloadNeededLength = -1;
			} else if (this.payloadNeededLength == 0) {
				if (this.finished) {
					throw new IllegalStateException("Decode is already finished");
				}
				short code = buf.readUnsignedByte();
				if ((code & 0x70) != 0) {
					throw new WebsocketDecodeException("Unexpected use of reserved bits");
				}
				if ((code & 0x0f) != 0) {
					throw new WebsocketDecodeException("Unexpected non-continuous frame");
				}
				this.finished = (code & 0x80) == 0x80;
				this.payloadNeededLength = -1;
			}
			if (this.payloadNeededLength == -1) {
				if (buf.readableBytes() == 0) {
					return false;
				}
				short b = buf.getUnsignedByte(buf.readerIndex());
				this.masking = (b & 0x80) == 0x80;
				if (this.masking != this.requireMask) {
					throw new WebsocketDecodeException("Wrong payload masking status, require " + this.requireMask);
				}
				this.maskIndex = -1;
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
			if (this.masking && this.maskIndex == -1) {
				if (buf.readableBytes() < 4) {
					return false;
				}
				buf.readBytes(this.mask);
				this.maskIndex = 0;
			}
			if (this.payloadNeededLength > 0) {
				int readable = Math.min(buf.readableBytes(), this.payloadNeededLength);
				if (this.masking) {
					for (int i = 0; i < readable; i++) {
						this.payload.writeByte(buf.readByte() ^ this.mask[this.maskIndex++ & 0x3]);
						this.payloadNeededLength--;
					}
				} else {
					this.payload.writeBytes(buf, readable);
					this.payloadNeededLength -= readable;
				}
			}
			if (this.payloadNeededLength == 0) {
				if (this.finished) {
					return true;
				}
			}
		}
		return false;
	}
}
