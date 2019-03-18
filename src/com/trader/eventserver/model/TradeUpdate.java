package com.trader.eventserver.model;

public class TradeUpdate {
	public final double base;
	public final double counter;
	public final String maker_order_id;
	public final String taker_order_id;
	public final String order_id;

	public TradeUpdate(double base, double counter, String maker_order_id, String taker_order_id, String order_id) {
		super();
		this.base = base;
		this.counter = counter;
		this.maker_order_id = maker_order_id;
		this.taker_order_id = taker_order_id;
		this.order_id = order_id;
	}

}
