package rs.cybertrade.prati.objekatzone;

import org.vaadin.dialogs.ConfirmDialog;

import com.vaadin.server.Page;
import com.vaadin.ui.CheckBox;

import pratiBaza.tabele.ObjekatZone;
import pratiBaza.tabele.Objekti;

import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

import rs.cybertrade.prati.view.OpstaForma;
import rs.cybertrade.prati.view.OpstaFormaInterface;
import rs.cybertrade.prati.view.OpstiView;
import rs.cybertrade.prati.view.komponente.ComboObjekti;
import rs.cybertrade.prati.view.komponente.ComboOrganizacije;
import rs.cybertrade.prati.view.komponente.ComboPretplatnici;
import rs.cybertrade.prati.view.komponente.ComboZone;

public class ObjekatZoneForma extends OpstaForma implements OpstaFormaInterface{

	private static final long serialVersionUID = 1L;
	private ObjekatZoneLogika logika;
	private ComboPretplatnici pretplatnici;
	private ComboOrganizacije organizacije;
	private ComboObjekti objekti;
	private ComboZone zone;
	private CheckBox aktivan, ulaz, izlaz, izbrisan;

	public ObjekatZoneForma(ObjekatZoneLogika log) {
		logika = log;
		pretplatnici = new ComboPretplatnici("претплатник", true, true);
		objekti = new ComboObjekti(logika.view.korisnik, "објекти", true, true);
		zone = new ComboZone(logika.view.korisnik, "зоне", true, true);
		aktivan = new CheckBox("активан");
		ulaz = new CheckBox("улаз");
		izlaz = new CheckBox("излаз");
		izbrisan = new CheckBox("избрисан");
		organizacije = new ComboOrganizacije(pretplatnici.getValue(), "организација", true, true);
		sacuvaj.addClickListener(new ClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void buttonClick(ClickEvent event) {
				if(proveraPodataka()) {
					ConfirmDialog.show(logika.view.getUI(), "Провера", "Сачувај унете податке?", "да", "не", new ConfirmDialog.Listener() {
						private static final long serialVersionUID = 1L;
						@Override
						public void onClose(ConfirmDialog dialog) {
							if(dialog.isConfirmed()) {
								logika.sacuvajPodatak(sacuvajPodatak(logika.view.dajIzabraniRed()));
							}
						}
					});
				}else {
					logika.view.pokaziPorukuGreska(OpstiView.OBAVEZNAPOLJA);
				}
			}
		});
		
		otkazi.addClickListener(new ClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void buttonClick(ClickEvent event) {
				logika.otkaziPodatak();
			}
		});
		
		izbrisi.addClickListener(new ClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void buttonClick(ClickEvent event) {
				ConfirmDialog.show(logika.view.getUI(), "Провера", "Избриши изабране податке?", "да", "не", new ConfirmDialog.Listener() {
					private static final long serialVersionUID = 1L;
					@Override
					public void onClose(ConfirmDialog dialog) {
						if(dialog.isConfirmed()) {
							logika.ukloniPodatak();
						}
					}
				});
			}
		});
		
		if(logika.view.isAdmin()) {
			layout.addComponent(pretplatnici);
		}
		layout.addComponent(objekti);
		layout.addComponent(zone);
		layout.addComponent(ulaz);
		layout.addComponent(izlaz);
		layout.addComponent(aktivan);
		if(logika.view.isAdmin() || logika.view.korisnik.getOrganizacija() == null) {
			layout.addComponent(organizacije);
		}
		if(logika.view.isAdmin())  {
			layout.addComponent(izbrisan);
		}
		
		layout.addComponentsAndExpand(expander);
		layout.addComponent(sacuvaj);
		layout.addComponent(otkazi);
		layout.addComponent(izbrisi);
		
		addComponent(layout);
	}
	
	@Override
	public void izmeniPodatak(Object podatak) {
		ocistiPodatak();
		ObjekatZone objekatZona;
		if(podatak == null) {
			objekatZona = new ObjekatZone();
		}else {
			objekatZona = (ObjekatZone)podatak;
			postaviPodatak(objekatZona);
		}
		String scrollScript = "window.document.getElementById('" + getId() + "').scrollTop = 0;";
		Page.getCurrent().getJavaScript().execute(scrollScript);
	}

	@Override
	public Object sacuvajPodatak(Object podatak) {
		ObjekatZone objekatZona;
		if(podatak == null) {
			objekatZona = new ObjekatZone();
		}else {
			objekatZona = (ObjekatZone)podatak;
		}
		objekatZona.setSistemPretplatnici(pretplatnici.getValue());
		objekatZona.setObjekti(objekti.getValue());
		objekatZona.setZone(zone.getValue());
		objekatZona.setUlaz(ulaz.getValue());
		objekatZona.setIzlaz(izlaz.getValue());
		objekatZona.setAktivan(aktivan.getValue());
		objekatZona.setOrganizacija(organizacije.getValue());
		objekatZona.setIzbrisan(izbrisan.getValue());
		return objekatZona;
	}

	@Override
	public void ocistiPodatak() {
		if(logika.view.korisnik.getSistemPretplatnici() != null) {
			pretplatnici.setValue(logika.view.korisnik.getSistemPretplatnici());
		}else {
			pretplatnici.clear();
		}
		objekti.clear();
		zone.clear();
		ulaz.setValue(true);
		izlaz.setValue(true);
		aktivan.setValue(true);
		if(logika.view.korisnik.getOrganizacija() != null) {
			organizacije.setValue(logika.view.korisnik.getOrganizacija());
		}else {
			organizacije.clear();
		}
		izbrisan.setValue(false);
	}

	@Override
	public void postaviPodatak(Object podatak) {
		ObjekatZone objekatZona = (ObjekatZone)podatak;
		if(objekatZona.getId() != null) {
			pretplatnici.setValue(objekatZona.getSistemPretplatnici());
			try {
				objekti.setSelectedItem(objekatZona.getObjekti());
			}catch (Exception e) {
				objekti.setSelectedItem(null);
			}
			try {
				zone.setSelectedItem(objekatZona.getZone());
			}catch (Exception e) {
				zone.setSelectedItem(null);
			}
			ulaz.setValue(objekatZona.isUlaz());
			izlaz.setValue(objekatZona.isIzlaz());
			aktivan.setValue(objekatZona.isAktivan());
			organizacije.setValue(objekatZona.getOrganizacija());
			izbrisan.setValue(objekatZona.isIzbrisan());
		}
		
	}

	@Override
	public boolean proveraPodataka() {
		boolean sveIma = true;
		if(pretplatnici.getValue() == null) {
			sveIma = false;
		}
		if(objekti.getValue() == null) {
			sveIma = false;
		}
		if(zone.getValue() == null) {
			sveIma = false;
		}
		if(!ulaz.getValue() && !izlaz.getValue()) {
			sveIma = false;
		}
		return sveIma;
	}

}
