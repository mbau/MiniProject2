package com.mbau.miniproject2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;

public class ShowMapActivity extends Activity implements LocationListener, OnMapLongClickListener{
	
	//the map
	GoogleMap theMap;

	//location manager
	private LocationManager locMan;

	//user marker
	private Marker userMarker;
	
	//places of interest
	private Marker[] placeMarkers;
	//max
	private final int MAX_PLACES = 20;//most returned from google
	//marker options
	private MarkerOptions[] places;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_show_map);

		//find out if we already have it
		if(theMap==null){
			//get the map
			theMap = ((MapFragment)getFragmentManager().findFragmentById(R.id.the_map)).getMap();
			//check in case map/ Google Play services not available
			if(theMap!=null){
				//ok - proceed
				theMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
				theMap.setOnMapLongClickListener(this);
				//create marker array
				placeMarkers = new Marker[MAX_PLACES];
				//update location
				updatePlaces();
			}

		}
	}
	
	//location listener functions
	
	@Override
	public void onLocationChanged(Location location) {
		Log.v("MyMapActivity", "location changed");
		updatePlaces();
	}
	@Override
	public void onProviderDisabled(String provider){
		Log.v("MyMapActivity", "provider disabled");
	}
	@Override
	public void onProviderEnabled(String provider) {
		Log.v("MyMapActivity", "provider enabled");
	}
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.v("MyMapActivity", "status changed");
	}
	
	/*
	 * update the place markers
	 */
	private void updatePlaces(){
		//get location manager
		locMan = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		//get last location
		Location lastLoc = locMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		double lat = lastLoc.getLatitude();
		double lng = lastLoc.getLongitude();
		//create LatLng
		LatLng lastLatLng = new LatLng(lat, lng);

		//remove any existing marker
		if(userMarker!=null) userMarker.remove();
		//create and set marker properties
		userMarker = theMap.addMarker(new MarkerOptions()
		.position(lastLatLng)
		.title("Current Location")
		.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
		//move to location
		theMap.animateCamera(CameraUpdateFactory.newLatLng(lastLatLng), 1000, null);
		
		String latVal=String.valueOf(lat);
		String lngVal=String.valueOf(lng);
		String url;
		try {
			url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location="
			+URLEncoder.encode(latVal, "UTF-8")
			+","
			+URLEncoder.encode(lngVal, "UTF-8")
			+"&radius="
			+URLEncoder.encode("1000", "UTF-8")
			+"&sensor="
			+URLEncoder.encode("true", "UTF-8")
			+"&types="
			+URLEncoder.encode("restaurant|food|bar|church|stadium|university|school", "UTF-8")
			+"&key="
			+URLEncoder.encode("AIzaSyDdMnQpqT9pr-k6VhwesT1OBAg_qkvflxU", "UTF-8");
			new GetPlaces().execute(url);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 100, this);
	}
	
	private class GetPlaces extends AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... placesURL) {
			//fetch places
			//build result as string
			StringBuilder placesBuilder = new StringBuilder();
			//process search parameter string(s)
			for (String placeSearchURL : placesURL) {
				HttpClient placesClient = new DefaultHttpClient();
				try {
					//try to fetch the data
					
					//HTTP Get receives URL string
					HttpGet placesGet = new HttpGet(placeSearchURL);
					//execute GET with Client - return response
					HttpResponse placesResponse = placesClient.execute(placesGet);
					//check response status
					StatusLine placeSearchStatus = placesResponse.getStatusLine();
					//only carry on if response is OK
					if (placeSearchStatus.getStatusCode() == 200) {
						//get response entity
						HttpEntity placesEntity = placesResponse.getEntity();
						//get input stream setup
						InputStream placesContent = placesEntity.getContent();
						//create reader
						InputStreamReader placesInput = new InputStreamReader(placesContent);
						//use buffered reader to process
						BufferedReader placesReader = new BufferedReader(placesInput);
						//read a line at a time, append to string builder
						String lineIn;
						while ((lineIn = placesReader.readLine()) != null) {
							placesBuilder.append(lineIn);
						}
					}
				}
				catch(Exception e){ 
					e.printStackTrace(); 
				}
			}
			return placesBuilder.toString();
		}
		//process data retrieved from doInBackground
		protected void onPostExecute(String result) {
			//parse place data returned from Google Places
			//remove existing markers
			if(placeMarkers!=null){
				for(int pm=0; pm<placeMarkers.length; pm++){
					if(placeMarkers[pm]!=null)
						placeMarkers[pm].remove();
				}
			}
			try {
				//parse JSON
				
				//create JSONObject, pass string returned from doInBackground
				JSONObject resultObject = new JSONObject(result);
				//get "results" array
				JSONArray placesArray = resultObject.getJSONArray("results");
				//marker options for each place returned
				places = new MarkerOptions[placesArray.length()];
				//loop through places
				for (int p=0; p<placesArray.length(); p++) {
					//parse each place
					//if any values are missing we won't show the marker
					boolean missingValue=false;
					LatLng placeLL=null;
					String placeName="";
					String vicinity="";
					float currIcon = BitmapDescriptorFactory.HUE_ORANGE;
					try{
						//attempt to retrieve place data values
						missingValue=false;
						//get place at this index
						JSONObject placeObject = placesArray.getJSONObject(p);
						//get location section
						JSONObject loc = placeObject.getJSONObject("geometry")
								.getJSONObject("location");
						//read lat lng
						placeLL = new LatLng(Double.valueOf(loc.getString("lat")), 
								Double.valueOf(loc.getString("lng")));	
						//get types
						JSONArray types = placeObject.getJSONArray("types");
						//loop through types
						for(int t=0; t<types.length(); t++){
							//what type is it
							String thisType=types.get(t).toString();
							//check for particular types - set icons
							if(thisType.contains("restaurant")){
								currIcon = BitmapDescriptorFactory.HUE_GREEN;
								break;
							}
							else if(thisType.contains("bar")){
								currIcon = BitmapDescriptorFactory.HUE_GREEN;
								break;
							}
							else if(thisType.contains("food")){
								currIcon = BitmapDescriptorFactory.HUE_GREEN;
								break;
							}
							else if(thisType.contains("church")){
								currIcon = BitmapDescriptorFactory.HUE_VIOLET;
								break;
							}
							else if(thisType.contains("stadium")){
								currIcon = BitmapDescriptorFactory.HUE_YELLOW;
								break;
							}
						}
						//vicinity
						vicinity = placeObject.getString("vicinity");
						//name
						placeName = placeObject.getString("name");
					}
					catch(JSONException jse){
						Log.v("PLACES", "missing value");
						missingValue=true;
						jse.printStackTrace();
					}
					//if values missing we don't display
					if(missingValue)	places[p]=null;
					else
						places[p]=new MarkerOptions()
					.position(placeLL)
					.title(placeName)
					.icon(BitmapDescriptorFactory.defaultMarker(currIcon))
					.snippet(vicinity);
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			if(places!=null && placeMarkers!=null){
				for(int p=0; p<places.length && p<placeMarkers.length; p++){
					//will be null if a value was missing
					if(places[p]!=null)
						placeMarkers[p]=theMap.addMarker(places[p]);
				}
			}
			
		}
	}
	
	@Override
	public void onMapLongClick(LatLng point) {
		theMap.addMarker(new MarkerOptions().position(point).title(point.toString()));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(theMap!=null){
			locMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 100, this);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if(theMap!=null){
			locMan.removeUpdates(this);
		}
	}

}
