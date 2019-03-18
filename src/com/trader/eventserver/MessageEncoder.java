package com.trader.eventserver;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

import com.google.gson.Gson;

import arbtrader.model.SpreadChanged;

public class MessageEncoder implements Encoder.Text<SpreadChanged> {

	private static Gson gson = new Gson();

	@Override
	public String encode(SpreadChanged message) throws EncodeException {
		return gson.toJson(message);
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