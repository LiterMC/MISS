package com.github.litermc.miss.network;

import com.github.litermc.miss.Constants;
import com.github.litermc.miss.network.http.DecodeException;
import com.github.litermc.miss.network.http.Request;
import com.github.litermc.miss.network.websocket.WebsocketFrameDecoder;
import com.github.litermc.miss.network.websocket.WebsocketFrameEncoder;
import com.github.litermc.miss.network.websocket.WebsocketUtil;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MaybeHTTPForwarder {
	private static final byte[] CRLF = new byte[]{'\r', '\n'};

	private Status status = Status.NONE;
	private Request request = null;
	private ByteBuf headerCache = null;
	private ByteBuf inputBuf = null;
	private WebsocketFrameDecoder frameDecoder = null;

	public MaybeHTTPForwarder() {
	}

	private void initConnection(ChannelHandlerContext ctx, ByteBuf input) throws Exception {
		if (this.headerCache == null) {
			this.headerCache = ctx.alloc().heapBuffer(256);
		}
		this.headerCache.writeBytes(input, input.readerIndex(), input.readableBytes());
		int endIndex = -2;
		endIndex = readLine(this.headerCache);
		if (endIndex == -2 || (endIndex == -1 && this.headerCache.readableBytes() > 256)) {
			this.status = Status.PASSTHROUGH;
			ctx.fireChannelRead(this.headerCache);
			this.headerCache = null;
			return;
		}
		if (endIndex >= 0) {
			this.status = Status.HANDSHAKING;
			Constants.LOG.info("Switching to websocket handshaking state for {}", ctx.channel().remoteAddress());
			byte[] buf = new byte[endIndex];
			this.headerCache.getBytes(0, buf);
			try {
				this.request = Request.startReadRequest(buf);
			} catch (DecodeException e) {
				e.printStackTrace();
				this.status = Status.PASSTHROUGH;
				ctx.fireChannelRead(this.headerCache);
				this.headerCache = null;
				return;
			}
			this.onHandshakeMessage(ctx, Unpooled.EMPTY_BUFFER);
		}
	}

	private void onHandshakeMessage(ChannelHandlerContext ctx, ByteBuf input) throws Exception {
		this.headerCache.writeBytes(input, input.readerIndex(), input.readableBytes());
		byte[] header = getHeaderByteArray(this.headerCache);
		if (header == null) {
			return;
		}
		try {
			this.request.header().parseFrom(header);
		} catch (DecodeException e) {
			ByteBuf reply = ctx.alloc().heapBuffer(256);
			writeResponse(reply, 400, "Cannot parse request header".getBytes(StandardCharsets.UTF_8));
			ctx.write(reply);
			ctx.flush();
			ctx.channel().close();
			throw e;
		}
		input = this.headerCache;
		this.headerCache = null;

		if (!this.request.protoAtLeast(1, 1)) {
			ByteBuf reply = ctx.alloc().heapBuffer(256);
			writeResponse(reply, 400, "Protocol too low, require at least 1.1".getBytes(StandardCharsets.UTF_8));
			ctx.write(reply);
			ctx.flush();
			ctx.channel().close();
			return;
		}

		if (!this.request.getHeader("Connection").equalsIgnoreCase("upgrade") || !this.request.getHeader("Upgrade").equalsIgnoreCase("websocket")) {
			ByteBuf reply = ctx.alloc().heapBuffer(256);
			writeResponse(reply, 200, "Pong".getBytes(StandardCharsets.UTF_8));
			ctx.write(reply);
			ctx.flush();
			ctx.channel().close();
			return;
		}

		int wsVersion = Integer.valueOf(this.request.getHeader("Sec-WebSocket-Version"));
		String key = this.request.getHeader("Sec-WebSocket-Key");

		if (wsVersion != 13) {
			ByteBuf reply = ctx.alloc().heapBuffer(256);
			writeResponse(reply, 400, ("Unsupported websocket version " + wsVersion).getBytes(StandardCharsets.UTF_8));
			ctx.write(reply);
			ctx.flush();
			ctx.channel().close();
			return;
		}

		String acceptHeader = WebsocketUtil.calculateAccept(key);

		ByteBuf reply = ctx.alloc().heapBuffer(256);
		reply.writeCharSequence("HTTP/1.1 101", StandardCharsets.US_ASCII);
		reply.writeBytes(CRLF);
		reply.writeCharSequence("Connection: upgrade", StandardCharsets.US_ASCII);
		reply.writeBytes(CRLF);
		reply.writeCharSequence("Upgrade: websocket", StandardCharsets.US_ASCII);
		reply.writeBytes(CRLF);
		reply.writeCharSequence("Sec-WebSocket-Accept: " + acceptHeader, StandardCharsets.US_ASCII);
		reply.writeBytes(CRLF);
		reply.writeBytes(CRLF);
		ctx.write(reply);
		ctx.flush();
		this.status = Status.HTTP;
		this.onHTTPMessage(ctx, input);
	}

	private void onHTTPMessage(ChannelHandlerContext ctx, ByteBuf input) throws Exception {
		if (this.inputBuf == null) {
			this.inputBuf = ctx.alloc().heapBuffer(256);
		}
		this.inputBuf.writeBytes(input);
		boolean successed;
		do {
			successed = false;
			if (this.frameDecoder == null) {
				this.frameDecoder = new WebsocketFrameDecoder(ctx.alloc().heapBuffer(2048), true);
			}
			if (this.frameDecoder.decode(this.inputBuf)) {
				this.onMessage(ctx, this.frameDecoder);
				this.frameDecoder = null;
				successed = true;
			}
			this.inputBuf.discardReadBytes();
		} while (successed && this.inputBuf.readableBytes() > 0);
	}

	private void onMessage(ChannelHandlerContext ctx, WebsocketFrameDecoder frame) {
		ByteBuf payload = frame.getPayload();
		switch (frame.getOpCode()) {
			case 0x1 -> {
				// Ignore text frame
			}
			case 0x2 -> {
				// Forward binary frame as game packet
				ctx.fireChannelRead(payload);
			}
			case 0x8 -> {
				// disconnect frame
				this.sendMessage(ctx, 0x8, Unpooled.EMPTY_BUFFER);
				ctx.flush();
				ctx.channel().close();
			}
			case 0x9 -> {
				// ping frame
				this.sendMessage(ctx, 0xa, payload);
				ctx.flush();
			}
			case 0xa -> {
				// pong frame
			}
		}
	}

	public void sendMessage(ChannelHandlerContext ctx, int opCode, ByteBuf payload) {
		this.sendMessage(ctx, opCode, payload, null);
	}

	public void sendMessage(ChannelHandlerContext ctx, int opCode, ByteBuf payload, ChannelPromise promise) {
		WebsocketFrameEncoder encoder = new WebsocketFrameEncoder(ctx.alloc());
		List<ByteBuf> chunks = encoder.encode(opCode, false, payload);
		if (promise == null) {
			chunks.forEach(ctx::write);
			return;
		}
		AtomicInteger promiseCount = new AtomicInteger(chunks.size());
		for (ByteBuf buf : chunks) {
			final ChannelPromise p = ctx.channel().newPromise();
			p.addListener(future -> {
				try {
					p.sync();
				} catch (Exception e) {
					promise.tryFailure(e);
				}
				if (promiseCount.decrementAndGet() == 0) {
					promise.trySuccess();
				}
			});
			ctx.write(buf, p);
		}
	}

	public Decoder getDecoder() {
		return this.new Decoder();
	}

	public Encoder getEncoder() {
		return this.new Encoder();
	}

	private class Decoder extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (!(msg instanceof ByteBuf input)) {
				ctx.fireChannelRead(msg);
				return;
			}
			try {
				switch (MaybeHTTPForwarder.this.status) {
				case NONE -> MaybeHTTPForwarder.this.initConnection(ctx, input);
				case HANDSHAKING -> MaybeHTTPForwarder.this.onHandshakeMessage(ctx, input);
				case HTTP -> MaybeHTTPForwarder.this.onHTTPMessage(ctx, input);
				case PASSTHROUGH -> ctx.fireChannelRead(input);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private class Encoder extends ChannelOutboundHandlerAdapter {
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			if (MaybeHTTPForwarder.this.status == Status.PASSTHROUGH || !(msg instanceof ByteBuf input)) {
				ctx.write(msg, promise);
				return;
			}
			MaybeHTTPForwarder.this.sendMessage(ctx, 0x2, input, promise);
		}
	}

	private static void writeResponse(ByteBuf buffer, int status, byte[] body) {
		buffer.writeCharSequence("HTTP/1.1", StandardCharsets.US_ASCII);
		buffer.writeByte(' ');
		buffer.writeCharSequence(String.valueOf(status), StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Content-Type: text/plain", StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Content-Length: " + body.length, StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeBytes(CRLF);
		buffer.writeBytes(body);
	}

	private static int readLine(ByteBuf buf) throws DecodeException {
		int maxLength = buf.readerIndex() + 256;
		for (int i = buf.readerIndex(); i < buf.writerIndex(); i++) {
			if (i >= maxLength) {
				throw new DecodeException("Request URI too large");
			}
			byte b = buf.getByte(i);
			if (b == '\r' || b == '\n') {
				int j = i + 1;
				if (b == '\r' && buf.getByte(i + 1) == '\n') {
					j++;
				}
				buf.readerIndex(j);
				return i;
			}
			if (b <= 0x09) {
				return -2;
			}
		}
		return -1;
	}

	public static byte[] getHeaderByteArray(ByteBuf buffer) throws DecodeException {
		int lastLine = -1;
		int maxHeader = buffer.readerIndex() + 2048;
		for (int i = buffer.readerIndex(); i < buffer.writerIndex(); i++) {
			if (i > maxHeader) {
				throw new DecodeException("Request header too large");
			}
			byte b = buffer.getByte(i);
			if (b <= 0x09) {
				throw new NotASCIIException();
			}
			boolean isCR = b == '\r';
			if (isCR || b == '\n') {
				if (isCR && i + 1 < buffer.writerIndex() && buffer.getByte(i + 1) == '\n') {
					lastLine++;
					i++;
				}
				if (lastLine == i) {
					byte[] buf = new byte[i - 1 - buffer.readerIndex()];
					buffer.readBytes(buf);
					buffer.readerIndex(i + 1);
					return buf;
				}
				lastLine = i + 1;
			}
		}
		return null;
	}

	private static class NotASCIIException extends DecodeException {
		NotASCIIException() {
			super("Not ascii");
		}
	}

	private enum Status {
		NONE, HANDSHAKING, HTTP, PASSTHROUGH;
	}
}
