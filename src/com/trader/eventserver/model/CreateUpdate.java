package com.trader.eventserver.model;

import arbtrader.model.Order;

public class CreateUpdate {
	public final String order_id;
	/**
	 * ASK or BID
	 */
	public final String type;
	public final double price;
	public final double volume;

	public CreateUpdate(String order_id, String type, double price, double volume) {
		super();
		this.order_id = order_id;
		this.type = type;
		this.price = price;
		this.volume = volume;
	}

	public Order getOrder() {
		return new Order(order_id, price, volume);
	}

	public boolean isBid() {
		if ("BID".equals(type)) {
			return true;
		} else if ("ASK".equals(type)) {
			return false;
		} else {
			throw new IllegalStateException();
		}
	}

}
