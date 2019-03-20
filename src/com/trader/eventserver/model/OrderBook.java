package com.trader.eventserver.model;

import com.trader.model.Order;

public class OrderBook {

	public final long sequence;
	public final Order[] asks;
	public final Order[] bids;
	public final long timestamp;

	public OrderBook(long sequence, Order[] asks, Order[] bids, long timestamp) {
		super();
		this.sequence = sequence;
		this.asks = asks;
		this.bids = bids;
		this.timestamp = timestamp;
	}

}
