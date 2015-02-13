package eu.fusepool.p3.geo.enriching;

import java.util.Date;

public class WGS84Point {

	private double latitude;
	private double longitude;
	private String uriName;
	private String startDate;
	private String endDate;
	
	public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

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
