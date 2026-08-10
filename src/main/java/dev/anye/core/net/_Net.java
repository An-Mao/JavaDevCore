package dev.anye.core.net;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Logger;

public class _Net {
	public static final Logger logger = Logger.getLogger(_Net.class.getName());

	private _Net() {
	}

	public static String urlEncode(String value) {
		try {
			String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
			encoded = encoded.replace("+", "%20");
			return encoded;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String httpEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	public static String sendGetData(String url) {
		return sendData(url, "GET", null, "");
	}

	public static String sendData(String url, String type, HashMap<String, String> dataHead, String dataSend) {
		try {
			URL apiUrl = URI.create(url).toURL();
			HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
			connection.setRequestMethod(type);
			if (type.equals("POST")) {
				dataHead.forEach(connection::setRequestProperty);
				connection.setDoOutput(true);
				try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
					outputStream.write(dataSend.getBytes(StandardCharsets.UTF_8));
				}
			}
			StringBuilder response = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String responseLine;
				while ((responseLine = reader.readLine()) != null) {
					response.append(responseLine);
				}
			}
			connection.disconnect();
			return response.toString();
		} catch (Exception e) {
			logger.severe(e.getMessage());
			return "";
		}
	}

}
