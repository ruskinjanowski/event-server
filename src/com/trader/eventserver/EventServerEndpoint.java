package com.trader.eventserver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.Gson;
import com.trader.model.Events;
import com.trader.model.OrderListenRequest;

@ServerEndpoint(value = "/events", decoders = MessageDecoder.class, encoders = MessageEncoder.class)
public class EventServerEndpoint {

	private static Logger logger = Logger.getLogger(EventServerEndpoint.class.getName());

	private static final Gson gson = new Gson();

	/** Session ID and session */
	private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

	/** Session ID and last alive time */
	private static final Map<String, Long> aliveTimes = new ConcurrentHashMap<>();

	private static final KeepAliveTimer keepAliveTimer = new KeepAliveTimer();
	static {
		keepAliveTimer.start();
	}

	@OnOpen
	public void onOpen(Session session) {
		logger.info("Connected ... " + session.getId());
		sessions.put(session.getId(), session);
		aliveTimes.put(session.getId(), System.currentTimeMillis());

		// send spread

		Events e = new Events(LunoMarket.INSTANCE.getSpread(), null, null, null);
		broadcast(e);
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		logger.info(String.format("Session %s closed because of %s", session.getId(), closeReason));
		sessions.remove(session.getId());
		aliveTimes.remove(session.getId());
	}

	@OnMessage
	public String onMessage(String message, Session session) {

		if ("client ka".equals(message)) {
			aliveTimes.put(session.getId(), System.currentTimeMillis());
			return null;
		}

		try {
			OrderListenRequest olr = gson.fromJson(message, OrderListenRequest.class);
			LunoMarket.INSTANCE.addListenOrder(olr.id);
			System.out.println("jhbjddv");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static synchronized void broadcast(Events msg) {
		String sent = gson.toJson(msg);
		for (Session s : sessions.values()) {
			try {
				s.getBasicRemote().sendObject(sent);
			} catch (IOException | EncodeException e) {
				e.printStackTrace();
			}
		}
	}

	private static class KeepAliveTimer extends Thread {
		public KeepAliveTimer() {
			super("KeepAliveTimer");
		}

		@Override
		public void run() {
			while (true) {
				for (Session s : sessions.values()) {
					try {
						if (System.currentTimeMillis() - aliveTimes.get(s.getId()) < 2 * 60 * 1000) {
							s.getBasicRemote().sendText("ka");
						} else {
							System.out.println("Disconnecting session due to keep alive: " + s);
							s.close();
							sessions.remove(s.getId());
							aliveTimes.remove(s.getId());
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
