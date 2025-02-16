package com.github.litermc.miss.client.network;

import com.github.litermc.miss.Constants;
import com.github.litermc.miss.network.URIServerAddress;
import com.github.litermc.miss.network.http.DecodeException;
import com.github.litermc.miss.network.http.Response;
import com.github.litermc.miss.network.websocket.WebsocketFrameDecoder;
import com.github.litermc.miss.network.websocket.WebsocketFrameEncoder;
import com.github.litermc.miss.network.websocket.WebsocketUtil;
import com.google.common.net.HostAndPort;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraft.network.Connection;

import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WebsocketForwarder {
	private static final byte[] CRLF = new byte[]{'\r', '\n'};
	private static final SslContextBuilder SSL_CLIENT_BUILDER = SslContextBuilder.forClient();

	private URI targetURI = null;
	private Status status = Status.NONE;
	private String websocketAccepting = null;
	private Response response = null;
	private ByteBuf headerCache = null;
	private ByteBuf inputBuf = null;
	private WebsocketFrameDecoder frameDecoder = null;
	private Connection connection = null;

	public WebsocketForwarder() {
	}

	private void onHandshakeMessage(ChannelHandlerContext ctx, ByteBuf input) throws Exception {
		if (this.headerCache == null) {
			this.headerCache = ctx.alloc().heapBuffer(256);
		}
		this.headerCache.writeBytes(input, input.readerIndex(), input.readableBytes());
		if (this.response == null) {
			int endIndex = -2;
			endIndex = readLine(this.headerCache);
			if (endIndex == -2) {
				this.headerCache = null;
				throw new DecodeException("Not http response");
			}
			if (endIndex == -1 && this.headerCache.readableBytes() > 256) {
				this.headerCache = null;
				throw new DecodeException("Response head too large");
			}
			if (endIndex >= 0) {
				byte[] buf = new byte[endIndex];
				this.headerCache.getBytes(0, buf);
				this.headerCache.discardReadBytes();
				this.response = Response.startReadResponse(buf);
			}
		} else {
			byte[] header = getHeaderByteArray(this.headerCache);
			if (header == null) {
				return;
			}
			this.response.header().parseFrom(header);
			if (!this.response.protoEquals(1, 1)) {
				throw new DecodeException("Response is not HTTP/1.1");
			}
			if (this.response.getStatus() != 101) {
				throw new DecodeException("Response " + this.response.getStatus());
			}
			if (!this.response.getHeader("Connection").equalsIgnoreCase("upgrade") || !this.response.getHeader("Upgrade").equalsIgnoreCase("websocket")) {
				throw new DecodeException("Target server/proxy does not support websocket");
			}
			String accept = this.response.getHeader("Sec-WebSocket-Accept");
			if (!accept.equals(websocketAccepting)) {
				throw new DecodeException("Websocket accept key is incorrect");
			}
			this.status = Status.WEBSOCKET;
			input = this.headerCache;
			this.headerCache = null;
			this.onHTTPMessage(ctx, input);
		}
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
				this.frameDecoder = new WebsocketFrameDecoder(ctx.alloc().heapBuffer(2048), false);
			}
			if (this.frameDecoder.decode(this.inputBuf)) {
				this.onMessage(ctx, this.frameDecoder);
				this.frameDecoder = null;
				successed = true;
			}
			this.inputBuf.discardReadBytes();
		} while (successed && this.inputBuf.readableBytes() > 0);
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
			switch (WebsocketForwarder.this.status) {
			case NONE -> ctx.fireChannelRead(input);
			case HANDSHAKING -> WebsocketForwarder.this.onHandshakeMessage(ctx, input);
			case WEBSOCKET -> WebsocketForwarder.this.onHTTPMessage(ctx, input);
			case PASSTHROUGH -> ctx.fireChannelRead(msg);
			}
		}
	}

	private class Encoder extends ChannelOutboundHandlerAdapter {
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			if (WebsocketForwarder.this.status == Status.PASSTHROUGH || !(msg instanceof ByteBuf input)) {
				ctx.write(msg, promise);
				return;
			}
			if (WebsocketForwarder.this.status == Status.NONE) {
				WebsocketForwarder.this.startHandshake(ctx);
				if (WebsocketForwarder.this.status == Status.PASSTHROUGH) {
					ctx.write(msg, promise);
					return;
				}
			}
			WebsocketForwarder.this.sendMessage(ctx, 0x2, input, promise);
		}
	}

	private void startHandshake(ChannelHandlerContext ctx) throws Exception {
		SocketAddress remoteAddress = ctx.channel().remoteAddress();
		this.connection = (Connection)(ctx.pipeline().get("packet_handler"));
		this.targetURI = ((URIServerAddress)(this.connection)).getURI();
		if (this.targetURI == null && remoteAddress instanceof URIServerAddress uAddr) {
			this.targetURI = uAddr.getURI();
		}
		if (this.targetURI == null) {
			this.status = Status.PASSTHROUGH;
			return;
		}
		String scheme = this.targetURI.getScheme();
		if (scheme == null) {
			this.status = Status.PASSTHROUGH;
			return;
		}
		boolean useTLS = false;
		switch (scheme.toUpperCase()) {
		case "WSS":
			useTLS = true;
		case "WS":
			break;
		default:
			this.status = Status.PASSTHROUGH;
			return;
		}

		Constants.LOG.info("Switching to websocket handshake, secure = {}", useTLS);
		this.status = Status.HANDSHAKING;

		if (useTLS) {
			SslContext sslCtx = SSL_CLIENT_BUILDER.build();
			ctx.pipeline().addAfter("timeout", "ssl", sslCtx.newHandler(ctx.alloc()));
		}

		String websocketKey = WebsocketUtil.generateKey();
		this.websocketAccepting = WebsocketUtil.calculateAccept(websocketKey);
		ByteBuf buffer = ctx.alloc().heapBuffer(256);
		URI pathURI;
		try {
			String path = this.targetURI.getRawPath();
			if (path == null || path.isEmpty()) {
				path = "/";
			}
			pathURI = new URI(null, null, null, -1, path, this.targetURI.getRawQuery(), null);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HostAndPort hostPort = HostAndPort.fromParts(this.targetURI.getHost(), this.targetURI.getPort());
		buffer.writeCharSequence("GET " + pathURI.toString() + " HTTP/1.1", StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Host: " + hostPort.toString(), StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Connection: upgrade", StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Upgrade: websocket", StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Sec-WebSocket-Version: 13", StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeCharSequence("Sec-WebSocket-Key: " + websocketKey, StandardCharsets.US_ASCII);
		buffer.writeBytes(CRLF);
		buffer.writeBytes(CRLF);
		ctx.write(buffer);
		ctx.flush();
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
		List<ByteBuf> chunks = encoder.encode(opCode, true, payload);
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
		NONE, HANDSHAKING, WEBSOCKET, PASSTHROUGH;
	}
}
