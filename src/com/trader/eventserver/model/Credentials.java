package com.trader.eventserver.model;

public class Credentials {
	public final String api_key_id;
	public final String api_key_secret;

	public Credentials(String api_key_id, String api_key_secret) {
		super();
		this.api_key_id = api_key_id;
		this.api_key_secret = api_key_secret;
	}
}
