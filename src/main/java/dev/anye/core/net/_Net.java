package dev.anye.core.net;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class _Net {
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
        // 使用 UTF-8 字符集进行编码
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    public static String sendGetData(String url){
        //_Log.debug("Get: "+url);
        return sendData(url,"GET",null,"");
    }
    public static String sendData(String url, String type, HashMap<String, String> dataHead, String dataSend){
        try {
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod(type);
            if (type.equals("POST")){
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
                //System.out.println("Response: " + response);
            }
            connection.disconnect();
            return response.toString();
        } catch (Exception e) {
            //_Log.error(e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

}
