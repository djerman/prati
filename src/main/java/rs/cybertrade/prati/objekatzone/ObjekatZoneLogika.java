package rs.cybertrade.prati.objekatzone;

import java.util.ArrayList;

import com.vaadin.server.Page;

import pratiBaza.tabele.ObjekatZone;
import rs.cybertrade.prati.Prati;
import rs.cybertrade.prati.server.Servis;
import rs.cybertrade.prati.view.LogikaInterface;

public class ObjekatZoneLogika implements LogikaInterface{

	public ObjekatZoneView view;
	
	public ObjekatZoneLogika(ObjekatZoneView objekatZoneView) {
		view = objekatZoneView;
	}
	@Override
	public void init() {
		izmeniPodatak(null);
		view.postaviNoviOmoguceno(true);
	}

	@Override
	public void otkaziPodatak() {
		setFragmentParametar("");
		view.ocistiIzbor();
		view.izmeniPodatak(null);
	}

	@Override
	public void setFragmentParametar(String objectId) {
		String fragmentParametar;
		if(objectId == null || objectId.isEmpty()) {
			fragmentParametar = "";
		}else {
			fragmentParametar = objectId;
		}
		Page page = Prati.getCurrent().getPage();
		page.setUriFragment("!" + view.VIEW_NAME + "/" + fragmentParametar, false);
	}

	@Override
	public void enter(String objectId) {
		if(objectId != null && !objectId.isEmpty()) {
			if(objectId.equals("new")) {
				noviPodatak();
			}
		}else {
			try {
				int id = Integer.parseInt(objectId);
				ObjekatZone objekatZona = Servis.zonaObjekatServis.nadjiObjekatZoniPoId(id);
				view.izaberiRed(objekatZona);
			}catch (Exception e) {
				// TODO: handle exception
			}
		}
	}

	@Override
	public void sacuvajPodatak(Object podatak) {
		ObjekatZone objekatZona = (ObjekatZone)podatak;
		view.ocistiIzbor();
		view.izmeniPodatak(null);
		setFragmentParametar("");
		if(objekatZona.getId() != null) {
			Servis.zonaObjekatServis.izmeniZonaObjekat(objekatZona);
			view.pokaziPorukuUspesno("објекат зона измењен");
		}else {
			try {
				ArrayList<ObjekatZone> lista = new ArrayList<ObjekatZone>();
				lista = Servis.zonaObjekatServis.nadjiZoneObjektePoObjektu(objekatZona.getObjekti());
				boolean ima = false;
				if(lista.size() < 10) {
					for(ObjekatZone objZon : lista) {
						if(objZon.getObjekti().getId().equals(objekatZona.getObjekti().getId())) {
							ima = true;
						}
					}
					if(!ima) {
						Servis.zonaObjekatServis.unesiZonaObjekat(objekatZona);
						view.pokaziPorukuUspesno("објекат zona сачуван");
					}else {
						view.pokaziPorukuGreska("ова комбинација објекта и зоен већ постоји, молим промените!");
					}
				}else{
					view.pokaziPorukuGreska("дозвољено је највише 10 комбинација по објекту!");
				}
			}catch (Exception e) {
				view.pokaziPorukuGreska("објекат зона због грешке није сачуван!");
			}
		}
		view.updateTable();
	}

	@Override
	public void izmeniPodatak(Object podatak) {
		ObjekatZone objekatZona = (ObjekatZone)podatak;
		if(objekatZona == null) {
			setFragmentParametar("");
		}else {
			setFragmentParametar(objekatZona.getId() + "");
		}
		view.izmeniPodatak(objekatZona);
	}

	@Override
	public void noviPodatak() {
		view.ocistiIzbor();
		setFragmentParametar("new");
		view.izmeniPodatak(new ObjekatZone());
	}

	@Override
	public void ukloniPodatak() {
		view.ukloniPodatak();
		view.ocistiIzbor();
		view.izmeniPodatak(null);
		view.updateTable();
		setFragmentParametar("");
	}

	@Override
	public void redIzabran(Object podatak) {
		view.izmeniPodatak((ObjekatZone)podatak);
	}

}
