package rs.atekom.prati.view.ruta;

import java.util.ArrayList;
import java.util.List;
import com.github.appreciated.app.layout.annotations.MenuCaption;
import com.github.appreciated.app.layout.annotations.MenuIcon;
import com.github.appreciated.app.layout.annotations.NavigatorViewName;
import com.google.maps.DirectionsApi;
import com.google.maps.GeocodingApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.TravelMode;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.tapio.googlemaps.client.LatLon;
import com.vaadin.tapio.googlemaps.client.overlays.GoogleMapPolyline;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import pratiBaza.tabele.Objekti;
import rs.atekom.prati.Prati;
import rs.atekom.prati.mape.Gmap;
import rs.atekom.prati.server.Servis;
import rs.atekom.prati.view.OpstiView;
import rs.atekom.prati.view.OpstiViewInterface;

@NavigatorViewName("ruta") // an empty view name will also be the default view
@MenuCaption("Рута")
@MenuIcon(VaadinIcons.LOCATION_ARROW)
public class RutaView extends OpstiView implements OpstiViewInterface{

	private static final long serialVersionUID = 1L;
	public final String VIEW_NAME = "ruta";
	private RutaLogika viewLogika;
	private RutaForma forma;
	private GoogleMapPolyline polyline;

	public RutaView() {
		viewLogika = new RutaLogika(this);
		forma = new RutaForma(viewLogika);
		forma.removeStyleName("visible");
		forma.setEnabled(false);
		
		buildToolbar();
		buildPretraga();
		filter.setEnabled(false);
		filter.setVisible(false);
		
		String apKey = "";
		String apiPretplatnik = korisnik.getSistemPretplatnici() == null ? "": korisnik.getSistemPretplatnici().getApiKey();
		if(!apiPretplatnik.isEmpty() && !apiPretplatnik.equals("") && apiPretplatnik != null) {
			 apKey = apiPretplatnik;
		}else {
			apKey = Servis.apiGoogle;
		}
		
		dodaj.addClickListener(new ClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void buttonClick(ClickEvent event) {
				viewLogika.noviPodatak();
			}
		});
		
		mapa = new Gmap(apKey, null, "serbian");
		
		barGrid.addComponent(topLayout);
		barGrid.addComponent(mapa);
		barGrid.setExpandRatio(mapa, 1);
		
		addComponent(barGrid);
		addComponent(forma);
		
		viewLogika.init();
	}
	
	@Override
	public void buildTable() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enter(ViewChangeEvent event) {
		viewLogika.enter(event.getParameters());
	}
	
	@Override
	public void ocistiIzbor() {
		mapa.removeAllComponents();
		mapa.removePolyline(polyline);
		mapa.centriraj();
	}

	@Override
	public void izaberiRed(Object red) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object dajIzabraniRed() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void izmeniPodatak(Object podatak) {
		ocistiIzbor();
		if(podatak != null) {
			forma.addStyleName("visible");
			forma.setEnabled(true);
		}else {
			forma.removeStyleName("visible");
			forma.setEnabled(false);
		}
		forma.izmeniPodatak(podatak);
	}

	@Override
	public void ukloniPodatak() {
		ocistiIzbor();
	}

	@Override
	public void updateTable() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void osveziFilter() {
		// TODO Auto-generated method stub
		
	}

	public void prikaziRutu(Objekti objekat, LatLng pozicija, String tackaPrva, String tackaDruga, String odrediste) {
		ocistiIzbor();
		ArrayList<LatLon> zaRutu = new ArrayList<LatLon>();
		List<LatLng> latLng = new ArrayList<LatLng>();
		
		try {
			GeocodingResult[] adresaTrazena = GeocodingApi.reverseGeocode(Servis.ensureGContext(), pozicija).await();
			String adresaPocetna = adresaTrazena[0].formattedAddress;
			DirectionsResult result = new DirectionsResult();
			result = DirectionsApi.newRequest(Servis.ensureGContext())
			        .origin(adresaPocetna)
			        .destination(odrediste)
			        .language("serbian")
			        .mode(TravelMode.DRIVING)
			        .optimizeWaypoints(true)
			        .waypoints(tackaPrva, tackaDruga)
			        .await();
			if(result.routes.length>0) {
				latLng = result.routes[0].overviewPolyline.decodePath();
				for(LatLng latitudaLong: latLng){
					zaRutu.add(new LatLon(latitudaLong.lat,latitudaLong.lng));
				}
				polyline = new GoogleMapPolyline(zaRutu,  "#0066ff", 0.6, 6);
				mapa.addMarker("Почетак", new LatLon( zaRutu.get(0).getLat(), zaRutu.get(0).getLon()) , false, "VAADIN/pocetak.png");
				mapa.addMarker("Крај", new LatLon( zaRutu.get(zaRutu.size()-1).getLat(), zaRutu.get(zaRutu.size()-1).getLon()) , false, "VAADIN/kraj.png");
				mapa.addPolyline(polyline);
				
				LatLon ne = new LatLon(result.routes[0].bounds.northeast.lat, result.routes[0].bounds.northeast.lng);
				LatLon sw = new LatLon(result.routes[0].bounds.southwest.lat, result.routes[0].bounds.southwest.lng);
				mapa.fitToBounds(ne, sw);	
				
				long rastojanje	= 0;
				long vreme = 0;
				for(int i = 0; i < result.routes[0].legs.length; i++){
					rastojanje = rastojanje + result.routes[0].legs[i].distance.inMeters;
					vreme = vreme + result.routes[0].legs[0].duration.inSeconds;
					}
				double ukupnoRastojanje = rastojanje / 1000;
				int sati = (int)vreme / 3600;
				int min = (int)(vreme % 3600)/60;
				String poruka = objekat.getOznaka() + " - " + odrediste + " - " + ukupnoRastojanje + "km, " + sati + "ч " + min + "мин";
				Prati.getCurrent().showNotification(new Notification(poruka, Notification.Type.HUMANIZED_MESSAGE));
				}else {
					Prati.getCurrent().showNotification(new Notification("Проверите параметре руте",Notification.Type.HUMANIZED_MESSAGE));
					}
		} catch (Exception e) {
			e.printStackTrace();
			Prati.getCurrent().showNotification(new Notification("Проверите параметре руте",Notification.Type.HUMANIZED_MESSAGE));
		}
		
	}

	@Override
	public void dodajFilter() {
		// TODO Auto-generated method stub
		
	}
}
