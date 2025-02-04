package rs.atekom.prati.mape;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.vaadin.event.LayoutEvents.LayoutClickEvent;
import com.vaadin.event.LayoutEvents.LayoutClickListener;
import com.vaadin.event.UIEvents.PollEvent;
import com.vaadin.event.UIEvents.PollListener;
import com.vaadin.shared.Registration;
import com.vaadin.tapio.googlemaps.GoogleMap;
import com.vaadin.tapio.googlemaps.client.LatLon;
import com.vaadin.tapio.googlemaps.client.events.MarkerClickListener;
import com.vaadin.tapio.googlemaps.client.overlays.GoogleMapInfoWindow;
import com.vaadin.tapio.googlemaps.client.overlays.GoogleMapMarker;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.VerticalLayout;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.JavljanjaPoslednja;
import rs.atekom.prati.Prati;
import rs.atekom.prati.server.Servis;

public class Gmap extends GoogleMap{

	private static final long serialVersionUID = 1L;
	private static GoogleMapInfoWindow window;
	private GeoApiContext gContext;
	private LatLon pozicijaM;
	private LatLng pozicija;
	private String podaciMarker;
	public ArrayList<Double> lat;
    public ArrayList<Double> lon;
    private GMarker kliknutiMarker;
    private String[] datumMarker;
    public Ikonica ikonica;
    private String apiKey;
    private GoogleMapMarker klikMarker;
    private MarkerClickListener markerListener;
    public Registration pollListener;//za uklanjanje listenera
    
	public Gmap(String apKey, String clientId, String language) {
		super(apKey, clientId, language);
		setSizeFull();
		ikonica = new Ikonica();
		apiKey = apKey;
		lat = new ArrayList<Double>();
		lon = new ArrayList<Double>();
		
		markerListener = new MarkerClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void markerClicked(GoogleMapMarker clickedMarker) {
				prikaziGMarkerPodatke(clickedMarker);
			}
		};
		
		addMarkerClickListener(markerListener);
		centriraj();
	}
	
	public void dodavanjeMarkera() {
		clearMarkers();
		lat.clear();
		lon.clear();
		if(!Prati.getCurrent().objekti.isEmpty()){
			for(JavljanjaPoslednja javljanje: Prati.getCurrent().poslednjaJavljanja.getSelectedItems()){
			    if(javljanje != null){
					GoogleMapMarker gMarker = new GoogleMapMarker(podesiCaption(javljanje), new LatLon(javljanje.getLat(), javljanje.getLon()), false);
					gMarker.setAnimationEnabled(false);
					gMarker.setIconUrl(ikonica.icon(javljanje));
					lat.add(javljanje.getLat());
					lon.add(javljanje.getLon());
					addMarker(gMarker);
				}
			 }
		}
		if(Prati.getCurrent().centriranje)
			centriraj();
	}
	
	public void ukloniMarkere() {
		clearMarkers();
		centriraj();
	}
    
    public void osvezavanja(){
		Prati.getCurrent().setPollInterval(10000);
		Prati.getCurrent().osvezavanjeMarkera = new PollListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void poll(PollEvent event) {
				dodavanjeMarkera();
				}
		};
		pollListener = Prati.getCurrent().addPollListener(Prati.getCurrent().osvezavanjeMarkera);
	}
    
    public void centriraj(){
    	if(lat.size() > 0 && !lat.isEmpty()){
    		if(lat.size() == 1){
    			setCenter(new LatLon((Double)lat.get(0), (Double)lon.get(0)));
    		}else if(lat.size() > 1){
    			fitToBounds(new LatLon(Collections.max(lat), Collections.max(lon)), new LatLon(Collections.min(lat),Collections.min(lon)));
    		}else{
    			dodajPraznuMapu();
    		}
    	}else {
    		dodajPraznuMapu();
    	}
    }
    
    /*prazna mapa**/
    public void dodajPraznuMapu(){
    	clearMarkers();
    	setCenter(new LatLon(44.751802, 19.703187));
        setZoom(8);
    }
    
    
    public MarkerClickListener markerInfo = new MarkerClickListener() {
    	private static final long serialVersionUID = 1L;
		@Override
		public void markerClicked(GoogleMapMarker clickedMarker) {
			prikaziMarkerPodatke(clickedMarker);
		}
	};
	
	public MarkerClickListener klikMarkerInfo = new MarkerClickListener() {
		private static final long serialVersionUID = 1L;
		@Override
		public void markerClicked(GoogleMapMarker clickedMarker) {
			prikaziMarkerPoziciju(clickedMarker);
		}
	};
	
	public void prikaziMarkerPoziciju(GoogleMapMarker clickedMarker) {
		try {
			klikMarker = clickedMarker;
			if(window != null) {
				if(isInfoWindowOpen(window)) {
					closeInfoWindow(window);
					}
	            }
			VerticalLayout infoSadrzaj = new VerticalLayout();
			infoSadrzaj.addComponent(new Label("дужина: " + clickedMarker.getPosition().getLon()));
			infoSadrzaj.addComponent(new Label("ширина: " + clickedMarker.getPosition().getLat()));
            window = new GoogleMapInfoWindow("", klikMarker);
            setInfoWindowContents(window, infoSadrzaj);				
            if (clickedMarker.equals(clickedMarker)) {
            	openInfoWindow(window);
            	} 
			}catch (Exception e) {
				System.out.println("marker greška....");
			}
	}
	
	public String podesiCaption(JavljanjaPoslednja javljanjePoslednje) {
		String datumVreme = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(javljanjePoslednje.getDatumVreme());
		String[] datum = datumVreme.split(" ");
		String brzina = "брзина: " + javljanjePoslednje.getBrzina() + "км/ч";
		String oznaka = javljanjePoslednje.getObjekti().getOznaka();
		//setCaption(String.join("\n", oznaka + " ", brzina + " ", datum[1] + " ", datum[0]));
		return String.join("\n", oznaka + "; ", brzina + "; ", datum[1] + "; ", datum[0]);
	}
	
	public String  podesiCaption(Javljanja javljanjePoslednje) {
		String datumVreme = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(javljanjePoslednje.getDatumVreme());
		String[] datum = datumVreme.split(" ");
		String brzina = "брзина: " + javljanjePoslednje.getBrzina() + "км/ч";
		String oznaka = javljanjePoslednje.getObjekti().getOznaka();
		//setCaption(String.join("\n", oznaka + " ", brzina + " ", datum[1] + " ", datum[0]));
		return String.join("\n", oznaka + "; ", brzina + "; ", datum[1] + "; ", datum[0]);
	}
	
	public void prikaziGMarkerPodatke(GoogleMapMarker clickedMarker){
		try{
			klikMarker = (GoogleMapMarker) clickedMarker;
		    if(window != null){
		    	closeInfoWindow(window);
		    	//if(isInfoWindowOpen(window))	
		    	}
		    VerticalLayout infoSadrzaj = new VerticalLayout();
		    infoSadrzaj.setMargin(false);
		    infoSadrzaj.setSpacing(false);
		    String[] caption = klikMarker.getCaption().split(";");
		    for(int i = 0; i < caption.length; i++) {
		    	infoSadrzaj.addComponent(new Label(caption[i]));
		    }

		    window = new GoogleMapInfoWindow("", (GoogleMapMarker) klikMarker);
		    setInfoWindowContents(window, infoSadrzaj);				
		    if (clickedMarker.equals(clickedMarker)) {
		    	openInfoWindow(window);
		    	} 
		    }catch(Exception e){
	        	System.out.println("marker: " + e.getMessage());
		    }
		}
	
	public void prikaziMarkerPodatke(GoogleMapMarker clickedMarker){
		try{
			kliknutiMarker = (GMarker) clickedMarker;
		    if(window != null){
		    	if(isInfoWindowOpen(window))
		    		closeInfoWindow(window);
		    	}
		    VerticalLayout infoSadrzaj = new VerticalLayout();
		    //String prviRed = kliknutiMarker.getOznaka();
		    infoSadrzaj.addComponent(new Label(kliknutiMarker.getOznaka()));
		    if(kliknutiMarker.getRegistracija() != null ){
		    	infoSadrzaj.addComponent(new Label(kliknutiMarker.getRegistracija()));
		    	}
		    if(kliknutiMarker.getModel() != null){
		    	infoSadrzaj.addComponent(new Label(kliknutiMarker.getModel()));
		    	}
		    datumMarker = kliknutiMarker.getDatumVreme().split(" ");
		    infoSadrzaj.addComponent(new Label("брзина: " + kliknutiMarker.getBrzina() + "км/ч"));
		    if(kliknutiMarker.getUkupnokm() != null && kliknutiMarker.getUkupnokm() != 0){
		    	infoSadrzaj.addComponent(new Label("км: " + kliknutiMarker.getUkupnokm().toString()));
		    	}
		    if(kliknutiMarker.getUkupnoGorivo() != null && kliknutiMarker.getUkupnoGorivo() != 0.00){
		    	infoSadrzaj.addComponent(new Label("лит: " + kliknutiMarker.getUkupnoGorivo().toString()));
		    	}
		    infoSadrzaj.addComponent((new Label(datumMarker[1])));
		    infoSadrzaj.addComponent(new Label(datumMarker[0]));

		    infoSadrzaj.addLayoutClickListener(new LayoutClickListener() {
		    	private static final long serialVersionUID = 1L;
		    	public void layoutClick(LayoutClickEvent event) {
		    		String adresa = "";
		    		if(apiKey == null || apiKey.isEmpty()){
		    			apiKey = Servis.apiGoogle; //"AIzaSyDD6DzSebIUn6n2zEUm6f00tWnytTqfjy0";
		    			}
		    		gContext = new GeoApiContext().setApiKey(apiKey);
		    		pozicijaM = kliknutiMarker.getPosition();
		    		pozicija = new LatLng(pozicijaM.getLat(), pozicijaM.getLon());
		    		podaciMarker = kliknutiMarker.getOznaka();
		    		try {
		    			GeocodingResult[] adresaTrazena= GeocodingApi.reverseGeocode(gContext, pozicija).await();
		    			adresa = adresaTrazena[0].formattedAddress;
		    			Prati.getCurrent().showNotification(new Notification( podaciMarker + ": " + adresa, Notification.Type.HUMANIZED_MESSAGE));
		    			} catch (Exception e) {
		    				e.printStackTrace();
		    				}
		    		}
		    	});
		    window = new GoogleMapInfoWindow("", (GoogleMapMarker) kliknutiMarker);
		    setInfoWindowContents(window, infoSadrzaj);				
		    if (clickedMarker.equals(clickedMarker)) {
		    	openInfoWindow(window);
		    	} 
		    }catch(Exception e){
	        	
		    }
		}
	}
