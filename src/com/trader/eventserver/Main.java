package com.trader.eventserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;

import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.trader.api.AccountDetails;
import com.trader.api.AccountDetails.LunoDetails;
import com.trader.api.Api;
import com.trader.definitions.TraderFolders;
import com.trader.definitions.TraderFolders.ProgramName;
import com.trader.definitions.TraderURIs;
import com.trader.eventserver.model.Credentials;
import com.trader.model.MarketType;

public class Main {

	static Server server;
	public static final MarketType market = MarketType.EUR_BTC;

	public static void main(String[] args) throws IOException, WebSocketException {

		Api.createApis(ProgramName.EventSever);
		runServer();
		streamLunoData();
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("Please press a key to stop the program.");
		reader.readLine();
		server.stop();

	}

	public static void streamLunoData() throws WebSocketException, IOException {

		File propsFile = new File(TraderFolders.getConfig(ProgramName.EventSever), "accounts.properties");
		AccountDetails login = new AccountDetails(propsFile);
		LunoDetails ld = login.getLunoDetails(market);
		Credentials creds = new Credentials(ld.key, ld.secret);

		String url;// wss://ws.luno.com/api/1/stream/ETHXBT
		if (market.equals(MarketType.ZAR_BTC)) {
			url = "wss://ws.luno.com/api/1/stream/XBTZAR";
		} else if (market.equals(MarketType.EUR_BTC)) {
			url = "wss://ws.luno.com/api/1/stream/XBTEUR";
		} else if (market.equals(MarketType.NGN_BTC)) {
			url = "wss://ws.luno.com/api/1/stream/XBTNGN";
		} else {
			throw new IllegalStateException();
		}

		new WebSocketFactory().createSocket(url).addListener(new WebSocketAdapter() {
			boolean firstMessage = true;

			@Override
			public void onTextMessage(WebSocket ws, String message) {
				try {
					if (firstMessage) {
						LunoMarket.INSTANCE.setOrderBook(message);
						firstMessage = false;
					} else {
						LunoMarket.INSTANCE.addUpdates(message);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
				super.onConnected(websocket, headers);
				System.out.println("Connection successful.");
			}
		}).connect().sendText(new Gson().toJson(creds));
	}

	public static void runServer() {
		int port = TraderURIs.getPortForMarket(market);
		server = new Server("localhost", port, "/websockets/" + market.pair.base + market.pair.counter,
				EventServerEndpoint.class);

		try {
			server.start();
		} catch (DeploymentException e) {
			e.printStackTrace();
		}

	}
}
