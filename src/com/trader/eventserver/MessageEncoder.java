package com.trader.eventserver;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

import com.google.gson.Gson;
import com.trader.model.Spread;

public class MessageEncoder implements Encoder.Text<Spread> {

	private static Gson gson = new Gson();

	@Override
	public String encode(Spread message) throws EncodeException {
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