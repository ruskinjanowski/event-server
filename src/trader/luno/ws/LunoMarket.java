package trader.luno.ws;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.knowm.xchange.dto.marketdata.Ticker;

import com.google.gson.Gson;
import com.trader.controller.api.Api;

import arbtrader.model.Events;
import arbtrader.model.Order;
import arbtrader.model.OrderCancelled;
import arbtrader.model.SpreadChanged;
import trader.events.main.Main;
import trader.events.server.EventServerEndpoint;
import trader.luno.ws.model.OrderBook;
import trader.luno.ws.model.TradeUpdate;
import trader.luno.ws.model.Updates;

/**
 * Class to keep track of the state of all orders for a given market on Luno.
 * The purpose of this class is to fire price change events as soon as there are
 * no more open orders at either the current buying or current selling price.
 *
 */
public class LunoMarket {

	public static final LunoMarket INSTANCE = new LunoMarket();
	private static final Gson gson = new Gson();

	private Set<String> listenOrders = new HashSet<>();

	/**
	 * All orders.
	 */
	final Map<String, Order> orders = new HashMap<>();
	/**
	 * Sequence orders were added in. Used to keep track
	 */
	final Map<String, Long> orderSequence = new HashMap<>();
	/**
	 * Orders which are currently being filled - one ask and one bid order. <id,
	 * amount filled in BTC>
	 */
	final Map<String, Double> fills = new HashMap<>();
	/**
	 * Orders grouped by price.
	 */
	final TreeMap<Double, Set<Order>> priceAsks = new TreeMap<>();
	final TreeMap<Double, Set<Order>> priceBids = new TreeMap<>();
	/**
	 * Ids of all ask orders.
	 */
	final Set<String> asks = new HashSet<>();
	/**
	 * Ids of all bid orders.
	 */
	final Set<String> bids = new HashSet<>();

	long sequence = -1;
	long orderNum = 0;

	SpreadChanged latestSpread;

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

		latestSpread = new SpreadChanged(priceAsks.firstKey(), priceBids.lastKey());
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

	public void addUpdates(Updates u) {
		if (u.sequence != sequence + 1) {
			new IllegalStateException().printStackTrace();
			System.exit(0);
		}
		sequence = u.sequence;
		processDeleteUpdates(u);
		processCreateUpdates(u);
		processTradeUpdates(u);

		fireSpreadChangedIfNeeded();
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

			// while (priceBids.lastEntry().getValue().isEmpty()) {
			// priceBids.remove(priceBids.lastKey());
			// }
			// while (priceAsks.firstEntry().getValue().isEmpty()) {
			// priceAsks.remove(priceAsks.firstKey());
			// }
			// sanity check
			if (bids.contains(tu.maker_order_id)) {

				double bid = priceBids.lastKey();
				if (bid != o.price) {
					throw new IllegalStateException();
				}
			} else if (asks.contains(tu.maker_order_id)) {

				double ask = priceAsks.firstKey();
				if (ask != o.price) {
					throw new IllegalStateException();
				}
			} else {
				throw new IllegalStateException();
			}

			double fill = fills.containsKey(tu.maker_order_id) ? fills.get(tu.maker_order_id) : 0;
			double newFill = fill + tu.base;
			fills.put(tu.maker_order_id, newFill);

			System.out.println("filled: " + fills.get(tu.maker_order_id) + " for volume: " + o.volume);
			boolean typeBid = bids.contains(o.id);

			if (btcEquals(newFill, o.volume)) {

				// while (priceBids.lastEntry().getValue().isEmpty()) {
				// priceBids.remove(priceBids.lastKey());
				// }
				// while (priceAsks.firstEntry().getValue().isEmpty()) {
				// priceAsks.remove(priceAsks.firstKey());
				// }

				// order filled
				System.out.println("compledted: " + o + " " + typeBid);

				// notify listeners if needed
				if (listenOrders.contains(o.id)) {

					EventServerEndpoint.broadcast(new Events(null, o, null, null));
					System.out.println("Order Filled: " + o);
				}
				removeOrder(o.id);
			}
		}
	}

	private void fireSpreadChangedIfNeeded() {

		// while (priceBids.lastEntry().getValue().isEmpty()) {
		// priceBids.remove(priceBids.lastKey());
		// }
		// while (priceAsks.firstEntry().getValue().isEmpty()) {
		// priceAsks.remove(priceAsks.firstKey());
		// }

		double ask = priceAsks.firstEntry().getValue().iterator().next().price;
		double bid = priceBids.lastEntry().getValue().iterator().next().price;

		SpreadChanged pc = new SpreadChanged(ask, bid);
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

	public SpreadChanged getSpread() {
		return latestSpread;
	}

	public static boolean btcEquals(double b1, double b2) {
		boolean eq = Math.abs(b1 - b2) < 0.000001;
		return eq;
	}

	class CheckThread extends Thread {
		public CheckThread() {
			super("CheckThread");
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(10 * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				SpreadChanged s1 = getSpread();
				SpreadChanged ind = null;

				try {
					Ticker ticker = Api.getMarketDataService(Main.market).getTicker(Main.market.pair);
					ind = new SpreadChanged(ticker.getAsk().doubleValue(), ticker.getBid().doubleValue());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				SpreadChanged s2 = getSpread();

				if (s1.equals(s2) && !s1.equals(ind)) {
					// error state
					System.out.println("Error state");
					System.out.println(s1);
					System.out.println(ind);

					double ask = priceAsks.firstEntry().getValue().iterator().next().price;
					double bid = priceBids.lastEntry().getValue().iterator().next().price;

					System.out.println("---asks---");
					for (Order o : priceAsks.firstEntry().getValue()) {
						System.out.println(o);
					}

					System.out.println("---bids---");
					for (Order o : priceBids.lastEntry().getValue()) {
						System.out.println(o);
					}
					System.exit(0);
				} else {
					System.out.println("spread correct: " + s1 + " -- " + ind);
				}
				try {
					Thread.sleep(10 * 60 * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}
}
