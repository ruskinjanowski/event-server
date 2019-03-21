package com.trader.eventserver;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.knowm.xchange.dto.marketdata.Ticker;

import com.google.gson.Gson;
import com.trader.api.Api;
import com.trader.eventserver.model.OrderBook;
import com.trader.eventserver.model.TradeUpdate;
import com.trader.eventserver.model.Updates;
import com.trader.model.Events;
import com.trader.model.Order;
import com.trader.model.OrderCancelled;
import com.trader.model.Spread;

/**
 * This class processes all data streamed over a Luno Websocket
 * (https://www.luno.com/en/api). The functionality this class provides is to
 * identify generate notifications for spread change events and to listen on
 * specific orders and to generate created, cancelled and complete events for
 * these orders of interest.
 *
 */
public class LunoMarket {

	public static final LunoMarket INSTANCE = new LunoMarket();
	private static final Gson gson = new Gson();

	/**
	 * Orders of interest to generate create, cancelled and complete events for.
	 */
	private Set<String> listenOrders = new HashSet<>();

	/**
	 * All currently open orders.
	 */
	private final Map<String, Order> orders = new HashMap<>();
	/**
	 * Sequence orders were added in. <id, sequence number>
	 */
	private final Map<String, Long> orderSequence = new HashMap<>();
	/**
	 * Orders which are currently being filled - one ask and one bid order. <id,
	 * amount filled in BTC>
	 */
	private final Map<String, Double> fills = new HashMap<>();
	/**
	 * Orders grouped by price.
	 */
	private final TreeMap<Double, Set<Order>> priceAsks = new TreeMap<>();
	private final TreeMap<Double, Set<Order>> priceBids = new TreeMap<>();
	/**
	 * Ids of all ask orders.
	 */
	private final Set<String> asks = new HashSet<>();
	/**
	 * Ids of all bid orders.
	 */
	private final Set<String> bids = new HashSet<>();

	/**
	 * Sequence of message reporrted by Luno
	 */
	long sequence = -1;
	/**
	 * Number of order received by this class.
	 */
	long orderNum = 0;

	Spread latestSpread;

	public LunoMarket() {
		new CheckThread().start();
	}

	public void setOrderBook(String json) {
		setOrderBook(gson.fromJson(json, OrderBook.class));
	}

	public void setOrderBook(OrderBook ob) {
		if (sequence != -1) {
			System.out.println("bdhsbhs");
			System.exit(0);
		}
		sequence = ob.sequence;
		for (Order ask : ob.asks) {
			addOrder(ask, false);
		}
		for (Order bid : ob.bids) {
			addOrder(bid, true);
		}

		latestSpread = new Spread(priceAsks.firstKey(), priceBids.lastKey());
	}

	private void addOrder(Order o, boolean isBid) {
		orders.put(o.id, o);
		orderSequence.put(o.id, orderNum++);
		TreeMap<Double, Set<Order>> prices;
		if (isBid) {
			prices = priceBids;
			bids.add(o.id);
		} else {
			prices = priceAsks;
			asks.add(o.id);
		}

		if (!prices.containsKey(o.price)) {
			prices.put(o.price, new HashSet<>());
		}
		prices.get(o.price).add(o);

		if (listenOrders.contains(o.id)) {
			printVolumeInQueue(o.id);
			EventServerEndpoint.broadcast(new Events(null, null, null, o));
		}
	}

	private TreeMap<Double, Set<Order>> getListFor(String id) {
		TreeMap<Double, Set<Order>> prices;
		if (asks.contains(id)) {
			prices = priceAsks;
		} else if (bids.contains(id)) {
			prices = priceBids;
		} else {
			throw new IllegalStateException();
		}

		return prices;
	}

	private void addUpdates(Updates u) {
		if (u.sequence != sequence + 1) {
			new IllegalStateException().printStackTrace();
			System.exit(0);
		}
		sequence = u.sequence;
		processDeleteUpdates(u);
		processCreateUpdates(u);
		processTradeUpdates(u);

		fireSpreadIfNeeded();
	}

	public synchronized void addUpdates(String json) {
		if (json.equals("\"\"")) {
			return;// keep alive message
		}
		addUpdates(gson.fromJson(json, Updates.class));
	}

	private void processDeleteUpdates(Updates u) {
		if (u.delete_update != null) {
			String id = u.delete_update.order_id;
			if (listenOrders.contains(id)) {
				System.out.println(" - - - - - remove ordndscscd");
				Order o = orders.get(id);
				Double fill = fills.containsKey(id) ? fills.get(id) : 0.0;

				System.out.println("Order deleted: " + o + ", " + fill);

				OrderCancelled oc = new OrderCancelled(o, fill);
				EventServerEndpoint.broadcast(new Events(null, null, oc, null));

			}
			removeOrder(u.delete_update.order_id);
		}
	}

	private void processCreateUpdates(Updates u) {
		if (u.create_update != null) {
			try {
				addOrder(u.create_update.getOrder(), u.create_update.isBid());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private void processTradeUpdates(Updates u) {
		if (u.trade_updates == null) {
			return;
		}
		for (TradeUpdate tu : u.trade_updates) {
			Order o = orders.get(tu.maker_order_id);

			double fill = fills.containsKey(tu.maker_order_id) ? fills.get(tu.maker_order_id) : 0;
			double newFill = fill + tu.base;
			fills.put(tu.maker_order_id, newFill);

			System.out.println("filled: " + fills.get(tu.maker_order_id) + " for volume: " + o.volume);

			if (btcEquals(newFill, o.volume)) {
				// notify listeners if needed
				if (listenOrders.contains(o.id)) {
					EventServerEndpoint.broadcast(new Events(null, o, null, null));
					System.out.println("Order Filled: " + o);
				}
				removeOrder(o.id);
			}
		}
	}

	private void fireSpreadIfNeeded() {
		double ask = priceAsks.firstEntry().getValue().iterator().next().price;
		double bid = priceBids.lastEntry().getValue().iterator().next().price;

		Spread pc = new Spread(ask, bid);
		if (!pc.equals(latestSpread)) {
			latestSpread = pc;
			System.out.println("Spread changed: ask: " + pc.priceAsk + " bid: " + pc.priceBid);
			EventServerEndpoint.broadcast(new Events(pc, null, null, null));
		}
	}

	private void removeOrder(String id) {
		TreeMap<Double, Set<Order>> prices = getListFor(id);
		Order o = orders.get(id);
		Set<Order> oset = prices.get(o.price);
		if (oset != null) {
			oset.remove(o);
		} else {
			Set<Order> s1 = priceBids.get(o.price);
			Set<Order> s2 = priceAsks.get(o.price);
			System.out.println(s1 + "" + s2);
		}

		if (oset.isEmpty()) {
			prices.remove(o.price);
		}
		orders.remove(id);
		orderSequence.remove(id);
		fills.remove(id);
		bids.remove(id);
		asks.remove(id);
		listenOrders.remove(id);
	}

	private void printVolumeInQueue(String id) {
		if (orders.containsKey(id)) {
			System.out.println("already contains: " + id);
		} else {
			System.out.println("not present: " + id);
			return;
		}
		TreeMap<Double, Set<Order>> list = getListFor(id);
		long thisSeq = orderSequence.get(id);
		double before = 0;
		double after = 0;
		double thisO = 0;
		int countBefore = 0;

		for (Order o : list.get(orders.get(id).price)) {
			long oSeq = orderSequence.get(o.id);
			if (oSeq > thisSeq) {
				after += o.volume;
			} else if (oSeq < thisSeq) {
				before += o.volume;
				countBefore++;
			} else {
				thisO = o.volume;
			}
		}

		System.out.println("---------");
		System.out.println("This: " + thisO + " before: " + before + " (" + countBefore + ") " + " after: " + after);
		System.out.println("---------");
	}

	/**
	 * Add an order for which created, cancelled and complete events will be
	 * generated.
	 * 
	 * @param id the id of the order
	 */
	public void addListenOrder(String id) {
		listenOrders.add(id);
		if (orders.containsKey(id)) {
			System.out.println("already contains: " + id);
			printVolumeInQueue(id);
			EventServerEndpoint.broadcast(new Events(null, null, null, orders.get(id)));
		} else {
			System.out.println("not present: " + id);
		}
	}

	/**
	 * 
	 * @return the current spread on this exchange
	 */
	public Spread getSpread() {
		return latestSpread;
	}

	public static boolean btcEquals(double b1, double b2) {
		boolean eq = Math.abs(b1 - b2) < 0.000001;
		return eq;
	}

	/**
	 * This class periodically monitors the spread and compares it with the spread
	 * reported by the Luno HTTP API to ensure that the LunoMarket class is still
	 * reporting the correct spread.
	 * 
	 *
	 */
	private class CheckThread extends Thread {
		public CheckThread() {
			super("CheckThread");
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(10 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Spread s1 = getSpread();
				Spread apiSpread = null;

				try {
					Ticker ticker = Api.getMarketDataService(Main.market).getTicker(Main.market.pair);
					apiSpread = new Spread(ticker.getAsk().doubleValue(), ticker.getBid().doubleValue());
					sleep(3_000);// in case of network latency
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}

				Spread s2 = getSpread();

				if (s1.equals(s2) && !s1.equals(apiSpread)) {
					// error state
					System.out.println("Error state " + new Date());
					System.out.println(s1);
					System.out.println(apiSpread);

					System.out.println("---asks---");
					for (Order o : priceAsks.firstEntry().getValue()) {
						System.out.println(o);
					}

					System.out.println("---bids---");
					for (Order o : priceBids.lastEntry().getValue()) {
						System.out.println(o);
					}
					System.exit(0);
				} else if (s1.equals(s2)) {
					System.out.println(new Date());
					System.out.println("unknown: " + s1 + " -- " + s2 + "--" + apiSpread);
				} else {

					System.out.println(new Date());
					System.out.println("spread correct: " + s1 + " -- " + apiSpread);
				}
				try {
					Thread.sleep(10 * 60 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
