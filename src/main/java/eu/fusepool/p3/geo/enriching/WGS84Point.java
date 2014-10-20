package eu.fusepool.p3.geo.enriching;

public class WGS84Point {

	private double latitude;
	private double longitude;
	private String uriName;
	
	public String getUriName(){
	    return uriName;
	}
	
	public void setUri(String uriName){
	    this.uriName = uriName;
	}
	
	
	public double getLat() {
		return latitude;
	}
	
	public void setLat(double lat) {
		this.latitude = lat;
	}
	
	public double getLong() {
		return longitude;
	}
	
	public void setLong(double longitude) {
		this.longitude = longitude;
	}
	
		
}
