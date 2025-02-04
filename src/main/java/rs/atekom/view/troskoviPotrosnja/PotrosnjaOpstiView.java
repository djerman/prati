package rs.atekom.view.troskoviPotrosnja;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.event.selection.SelectionEvent;
import com.vaadin.event.selection.SelectionListener;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.renderers.DateRenderer;
import pratiBaza.tabele.Troskovi;
import rs.atekom.prati.server.Servis;
import rs.atekom.prati.view.Opsti;
import rs.atekom.prati.view.OpstiViewInterface;
import rs.atekom.prati.view.vozila.zbirni.ZbirniRacuniView;

public class PotrosnjaOpstiView extends Opsti implements OpstiViewInterface{

	private static final long serialVersionUID = 1L;
	public final String VIEW_NAME = "potrosnja";
	public Grid<Troskovi> tabela;
	public ListDataProvider<Troskovi> dataProvider;
	public SerializablePredicate<Troskovi> filterPredicate;
	public ArrayList<Troskovi> pocetno, lista;
	public PotrosnjaLogika viewLogika;
	public PotrosnjaForma forma;
	public Troskovi izabrani;
	public ZbirniRacuniView zbirni;
	
	public PotrosnjaOpstiView(boolean dodajFormu, ZbirniRacuniView zbirni) {
		if(zbirni != null) {
			this.zbirni = zbirni;
		}
		viewLogika = new PotrosnjaLogika(this);
		forma = new PotrosnjaForma(viewLogika);
		forma.removeStyleName("visible");
		forma.setEnabled(false);
		
		buildToolbar();
		buildTable();
		
		tabela.addSelectionListener(new SelectionListener<Troskovi>() {
			private static final long serialVersionUID = 1L;
			@Override
			public void selectionChange(SelectionEvent<Troskovi> event) {
				if(event.getFirstSelectedItem().isPresent()) {
					izabrani = event.getFirstSelectedItem().get();
				}else {
					izabrani = null;
				}
				viewLogika.redIzabran(izabrani);
			}
		});
		
		dodaj.addClickListener(new ClickListener() {
			private static final long serialVersionUID = 1L;
			@Override
			public void buttonClick(ClickEvent event) {
				viewLogika.noviPodatak();
			}
		});
		
		barGrid.addComponent(topLayout);
		barGrid.addComponent(tabela);
		barGrid.setExpandRatio(tabela, 1);
		
		addComponent(barGrid);
		if(dodajFormu) {
			addComponent(forma);
		}
		
		viewLogika.init();
	}
	
	@Override
	public void buildTable() {
		tabela = new Grid<Troskovi>();
		pocetno = new ArrayList<Troskovi>();
		updateTable();
		tabela.setSizeFull();
		tabela.setStyleName("list");
		tabela.setSelectionMode(SelectionMode.SINGLE);
		
		if(isSistem()) {
			tabela.addColumn(troskovi -> troskovi.getSistemPretplatnici() == null ? "" : troskovi.getSistemPretplatnici().getNaziv()).setCaption("претплатник");
		}
		tabela.addColumn(Troskovi::getDatumVreme, new DateRenderer(DANFORMAT)).setCaption("датум").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(troskovi -> troskovi.getPartner() == null ? "" : troskovi.getPartner().getNaziv()).setCaption("партнер");
		tabela.addColumn(troskovi -> troskovi.getObjekti() == null ? "" : troskovi.getObjekti().getOznaka()).setCaption("објекат");
		tabela.addColumn(troskovi -> troskovi.getSistemGoriva() == null ? "" : troskovi.getSistemGoriva().getNaziv()).setCaption("врста горива").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getKolicina).setCaption("количина").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getCena).setCaption("цена").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getPdvProcenat).setCaption("ПДВ %").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getPdvIznos).setCaption("ПДВ").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getUkupno).setCaption("укупно").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getOpis).setCaption("опис").setStyleGenerator(objekti -> "v-align-right");
		if(isSistem() || (korisnik.isAdmin() && korisnik.getOrganizacija() == null)) {
			tabela.addColumn(troskovi -> troskovi.getObjekti() == null ? "" : troskovi.getObjekti().getOrganizacija() == null ? "" : troskovi.getObjekti().getOrganizacija().getNaziv()).setCaption("организација");
		}
		if(isSistem()) {
			tabela.addComponentColumn(troskovi -> {CheckBox chb = new CheckBox(); if(troskovi.isIzbrisan()) {chb.setValue(true);} return chb;}).setCaption("избрисан").setStyleGenerator(objekti -> "v-align-right");
		}
		tabela.addColumn(Troskovi::getIzmenjeno, new DateRenderer(DANSATFORMAT)).setCaption("измењено").setStyleGenerator(objekti -> "v-align-right");
		tabela.addColumn(Troskovi::getKreirano, new DateRenderer(DANSATFORMAT)).setCaption("креирано").setStyleGenerator(objekti -> "v-align-right");
	}

	@Override
	public void ocistiIzbor() {
		tabela.getSelectionModel().deselectAll();
	}

	@Override
	public void izaberiRed(Object red) {
		tabela.getSelectionModel().select((Troskovi)red);
	}

	@Override
	public Object dajIzabraniRed() {
		try {
			return tabela.getSelectionModel().getFirstSelectedItem().get();
		}catch (NoSuchElementException e) {
			return null;
		}
	}

	@Override
	public void izmeniPodatak(Object podatak) {
		Troskovi trosak = (Troskovi)podatak;
		if(trosak != null) {
			forma.addStyleName("visible");
			forma.setEnabled(true);
		}else {
			forma.removeStyleName("visible");
			forma.setEnabled(false);
		}
		forma.izmeniPodatak(trosak);
	}

	@Override
	public void ukloniPodatak() {
		if(izabrani != null) {
			if(!izabrani.isIzbrisan()) {
				Servis.trosakServis.izbrisiTrosak(izabrani);
				pokaziPorukuUspesno("одржавање избрисано");
			}else {
				pokaziPorukuGreska("одржавање већ избрисано!");
			}
		}
	}

	@Override
	public void updateTable() {
		filter.clear();
		lista = Servis.trosakServis.nadjiSvuPotrosnju(korisnik);
		if(lista != null) {
			tabela.setItems(lista);
		}else {
			tabela.setItems(pocetno);
		}
		dodajFilter();
	}

	@Override
	public void osveziFilter() {
		dataProvider.setFilter(filterPredicate);
		dataProvider.refreshAll();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void dodajFilter() {
		dataProvider = (ListDataProvider<Troskovi>)tabela.getDataProvider();
		filterPredicate = new SerializablePredicate<Troskovi>() {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean test(Troskovi t) {
				return ((t.getSistemPretplatnici() == null ? "" : t.getSistemPretplatnici().getNaziv()).toLowerCase().contains(filter.getValue().toLowerCase()) ||
						(t.getObjekti() == null ? "" : t.getObjekti().getOznaka()).toLowerCase().contains(filter.getValue().toLowerCase()) ||
						(t.getPartner() == null ? "" : t.getPartner().getNaziv()).toLowerCase().contains(filter.getValue().toLowerCase()));
			}
		};
		filter.addValueChangeListener(e -> {osveziFilter();});
	}

}
