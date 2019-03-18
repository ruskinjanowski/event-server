package com.trader.eventserver.model;

public class Updates {
	public final long sequence;
	public final TradeUpdate[] trade_updates;// :null, // array of 0 or more trade updates
	public final CreateUpdate create_update; // null or 1 create update
	public final DeleteUpdate delete_update; // null or 1 delete update
	public final long timestamp;

	public Updates(long sequence, TradeUpdate[] trade_updates, CreateUpdate create_update, DeleteUpdate delete_update,
			long timestamp) {
		super();
		this.sequence = sequence;
		this.trade_updates = trade_updates;
		this.create_update = create_update;
		this.delete_update = delete_update;
		this.timestamp = timestamp;
	}

}
