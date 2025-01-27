package com.github.litermc.miss.network.websocket;

public class WebsocketDecodeException extends Exception {
	public WebsocketDecodeException(String message) {
		super(message);
	}

	public WebsocketDecodeException(String message, Throwable cause) {
		super(message, cause);
	}

	public WebsocketDecodeException(Throwable cause) {
		super(cause);
	}
}
