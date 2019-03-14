package trader.events.main;

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
import com.trader.controller.api.Api;

import arbtrader.controller.TraderURIs;
import arbtrader.credentials.AccountDetails;
import arbtrader.credentials.AccountDetails.LunoDetails;
import arbtrader.credentials.EMarketType;
import arbtrader.credentials.TraderFolders;
import arbtrader.credentials.TraderFolders.ProgramName;
import trader.events.server.EventServerEndpoint;
import trader.luno.ws.LunoMarket;
import trader.luno.ws.model.Credentials;

public class Main {

	static Server server;
	public static final EMarketType market = EMarketType.ZAR_BTC;

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
		if (market.equals(EMarketType.ZAR_BTC)) {
			url = "wss://ws.luno.com/api/1/stream/XBTZAR";
		} else if (market.equals(EMarketType.EUR_BTC)) {
			url = "wss://ws.luno.com/api/1/stream/XBTEUR";
		} else if (market.equals(EMarketType.NGN_BTC)) {
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
