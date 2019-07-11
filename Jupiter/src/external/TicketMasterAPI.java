package external;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;

public class TicketMasterAPI {
	
	private static final String URL = "https://app.ticketmaster.com/discovery/v2/events.json";
	private static final String DEFAULT_KEYWORD = ""; // no restriction
	private static final String API_KEY = "a8RYDj0gyAKYguE8IKApcBM0aQcuOCTP";
	
	public List<Item> search(double lat, double lon, String keyWord) {
		if (keyWord == null) {
			keyWord = DEFAULT_KEYWORD;
		}
		
		try {
			// use UTF-8 to encode keyword
			keyWord = URLEncoder.encode(keyWord, "UTF-8"); // "Jack Feng" => "Jack%20Feng"
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// use Geo location
		String geoHash = GeoHash.encodeGeohash(lat, lon, 8);
		// apikey=abcde&geoPoint=xyz123&keyword=&radius=50
		String query = String.format("apikey=%s&geoPoint=%s&keyWord=%s&radius=%s", API_KEY, geoHash, keyWord, 50);
		
		// "https://app.ticketmaster.com/discovery/v2/events.json?apikey=qqPuP6n3ivMUoT9fPgLepkRMreBcbrjV&latlong=37,-120&keyword=event&radius=50"
		//String query = String.format("apikey=%s&latlong=%s,%s&keyWord=%s&radius=%s", API_KEY, lat, lon, keyWord, 50);
		String url = URL + "?" + query;
		
		try {
			HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
			connection.setRequestMethod("GET");
			
			int responseCode = connection.getResponseCode(); // first time request
			System.out.println("Sending request to url: " + url);
			System.out.println("Response code: " + responseCode);
			
			// return empty JSONArray if responseCode is not 200
			if (responseCode != 200) {
				return new ArrayList<>();
			}
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream())); // maybe second request
			String line;
			StringBuilder response = new StringBuilder();
			// read line by line
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();
			JSONObject obj = new JSONObject(response.toString());
			// search for _embedded key, if it does exist, return all the embedded JSONArray events
			if (!obj.isNull("_embedded")) {
				JSONObject embedded = obj.getJSONObject("_embedded");	// find _embedded object
				return getItemList(embedded.getJSONArray("events"));	// return events array
			}
		}
		catch (IOException | JSONException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	// Convert JSONArray to a list of item object
	private List<Item> getItemList(JSONArray events) throws JSONException {
		
		List<Item> itemList = new ArrayList<>();
		for (int i = 0; i < events.length(); i++) {
			JSONObject event = events.getJSONObject(i);
			
			ItemBuilder builder = new ItemBuilder();
			if (!event.isNull("id")) {
				builder.setItemId(event.getString("id"));
			}
			if (!event.isNull("name")) {
				builder.setName(event.getString("name"));
			}
			if (!event.isNull("url")) {
				builder.setUrl(event.getString("url"));
			}
			if (!event.isNull("distance")) {
				builder.setDistance(event.getDouble("distance"));
			}
			builder.setAddress(getAddress(event))
			.setCategories(getCategories(event))
			.setImageUrl(getImageUrl(event));
			
			itemList.add(builder.build());
		}
		return itemList;
	}
	
	// Helper methods getAddress
	private String getAddress(JSONObject event) throws JSONException {
		if (!event.isNull("_embedded")) {
			JSONObject embedded = event.getJSONObject("_embedded");
			if (!embedded.isNull("venues")) {
				JSONArray venues = embedded.getJSONArray("venues");
				for (int i = 0; i < venues.length(); i++) {
					JSONObject venue = venues.getJSONObject(i);
					StringBuilder builder = new StringBuilder();
					if (!venue.isNull("address")) {
						JSONObject address = venue.getJSONObject("address");
						if (!address.isNull("line1")) {
							builder.append(address.getString("line1"));
						}
						if (!address.isNull("line2")) {
							builder.append(",");
							builder.append(address.getString("line2"));
						}
						if (!address.isNull("line3")) {
							builder.append(",");
							builder.append(address.getString("line3"));
						}
					}
					if (!venue.isNull("city")) {
						JSONObject city = venue.getJSONObject("city");
						builder.append(",");
						builder.append(city.getString("name"));
					}
					String result = builder.toString();
					if (!result.isEmpty()) {
						return result;
					}
				}
			}
		}
		// if _embedded is null, then return empty string
		return "";
	}
	
	// Helper methods getImageUrl
	private String getImageUrl(JSONObject event) throws JSONException {
		if (!event.isNull("images")) {
			JSONArray images = event.getJSONArray("images");
			for (int i = 0; i < images.length(); i++) {
				JSONObject image = images.getJSONObject(i);
				if (!image.isNull("url")) {
					return image.getString("url");
				}
			}
		}
		return "";
	}
	
	// Helper methods getCategories
	private Set<String> getCategories(JSONObject event) throws JSONException {
		
		Set<String> categories = new HashSet<>();
		if (!event.isNull("classifications")) {
			JSONArray classifications = event.getJSONArray("classifications");
			for (int i = 0; i < classifications.length(); i++) {
				JSONObject classification = classifications.getJSONObject(i);
				if (!classification.isNull("segment")) {
					JSONObject segment = classification.getJSONObject("segment");
					if (!segment.isNull("name")) {
						categories.add(segment.getString("name"));
					}
				}
			}
		}
		return categories;
	}
	
	private void queryAPI(double lat, double lon) {
		List<Item> events = search(lat, lon, null);
		
		for (Item event : events) {
			System.out.println(event.toJSONObject());
		}
	}
	
	public static void main(String[] args) {
		TicketMasterAPI tmApi = new TicketMasterAPI();
		// Mountain View, CA
		tmApi.queryAPI(37.38, -122.08);
		// London, UK
		//tmApi.queryAPI(51.503364, -0.12);
		// Houston, TX
		//tmApi.queryAPI(29.682684, -95.295410);
		// Waltham, MA
		//tmApi.queryAPI(42.376171, -71.238991);
	}
}
