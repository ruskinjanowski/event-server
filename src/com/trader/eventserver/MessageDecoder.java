package com.trader.eventserver;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

import com.google.gson.Gson;

import arbtrader.model.OrderListenRequest;

public class MessageDecoder implements Decoder.Text<OrderListenRequest> {

	private static Gson gson = new Gson();

	@Override
	public OrderListenRequest decode(String s) throws DecodeException {
		return gson.fromJson(s, OrderListenRequest.class);
	}

	@Override
	public boolean willDecode(String s) {
		return (s != null);
	}

	@Override
	public void init(EndpointConfig endpointConfig) {
		// Custom initialization logic
	}

	@Override
	public void destroy() {
		// Close resources
	}
}