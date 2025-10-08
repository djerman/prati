package rs.atekom.prati.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.google.maps.GeoApiContext;
import pratiBaza.pomocne.Mail;
import pratiBaza.pomocne.Obracuni;
import pratiBaza.servis.AlarmiKorisnikServis;
import pratiBaza.servis.EvidencijaVoznjiServis;
import pratiBaza.servis.GrupeKorisniciServis;
import pratiBaza.servis.GrupeObjektiServis;
import pratiBaza.servis.GrupeServis;
import pratiBaza.servis.JavljanjaMirovanjaServis;
import pratiBaza.servis.JavljanjaPoslednjaServis;
import pratiBaza.servis.JavljanjaServis;
import pratiBaza.servis.KorisniciServis;
import pratiBaza.servis.ObdPoslednjiServis;
import pratiBaza.servis.ObdServis;
import pratiBaza.servis.VozilaServis;
import pratiBaza.servis.ObjektiServis;
import pratiBaza.servis.OrganizacijeServis;
import pratiBaza.servis.PartneriServis;
import pratiBaza.servis.ProcedureServis;
import pratiBaza.servis.ProjektiServis;
import pratiBaza.servis.RacuniRaspodelaServis;
import pratiBaza.servis.RacuniServis;
import pratiBaza.servis.SifreServis;
import pratiBaza.servis.SimServis;
import pratiBaza.servis.SistemAlarmiServis;
import pratiBaza.servis.SistemGorivoServis;
import pratiBaza.servis.SistemOperateriServis;
import pratiBaza.servis.SistemPretplatniciServis;
import pratiBaza.servis.SistemServis;
import pratiBaza.servis.SistemSesijeServis;
import pratiBaza.servis.SistemUredjajiModeliServis;
import pratiBaza.servis.SistemUredjajiProizvodjaciServis;
import pratiBaza.servis.TroskoviServis;
import pratiBaza.servis.UredjajiServis;
import pratiBaza.servis.VozaciDozvoleServis;
import pratiBaza.servis.VozaciLekarskoServis;
import pratiBaza.servis.VozaciLicenceServis;
import pratiBaza.servis.VozaciLicnaServis;
import pratiBaza.servis.VozaciPasosiServis;
import pratiBaza.servis.VozaciServis;
import pratiBaza.servis.VozilaNaloziServis;
import pratiBaza.servis.VozilaOpremaPrijemServis;
import pratiBaza.servis.VozilaOpremaServis;
import pratiBaza.servis.VozilaPrimoPredajeServis;
import pratiBaza.servis.VozilaSaobracajne2Servis;
import pratiBaza.servis.VozilaSaobracajneServis;
import pratiBaza.servis.ObjekatZoneServis;
import pratiBaza.servis.ZoneServis;
import rs.atekom.prati.ApplicationContextProvider;

@WebListener
public class Servis implements ServletContextListener{
	@SuppressWarnings("unused")
	private ApplicationContext context;
	public static AlarmiKorisnikServis alarmKorisnikServis;
	public static EvidencijaVoznjiServis evidencijaServis;
	public static GrupeKorisniciServis grupeKorisnikServis;
	public static GrupeObjektiServis grupeObjekatServis;
	public static GrupeServis grupeServis;
	public static JavljanjaPoslednjaServis javljanjePoslednjeServis;
	public static JavljanjaMirovanjaServis javljanjeMirovanjeServis;
	public static JavljanjaServis javljanjeServis;
	public static KorisniciServis korisnikServis;
	public static ObdServis obdServis;
	public static ObdPoslednjiServis obdPoslednjiServis;
	public static ObjektiServis objekatServis;
	public static OrganizacijeServis organizacijaServis;
	public static PartneriServis partnerServis;
	public static ProjektiServis projektServis;
	public static ProcedureServis proceduraServis;
	public static RacuniServis racunServis;
	public static RacuniRaspodelaServis racunRaspodelaServis;
	public static SifreServis sifraServis;
	public static SimServis simServis;
	public static SistemAlarmiServis sistemAlarmServis;
	public static SistemGorivoServis sistemGorivoServis;
	public static SistemOperateriServis sistemOperaterServis;
	public static SistemPretplatniciServis sistemPretplatnikServis;
	public static SistemServis sistemServis;
	public static SistemSesijeServis sistemSesijaServis;
	public static SistemUredjajiModeliServis sistemUredjajModelServis;
	public static SistemUredjajiProizvodjaciServis sistemUredjajProizvodjacServis;
	public static UredjajiServis uredjajServis;

	public static VozaciServis vozacServis;
	public static VozaciDozvoleServis dozvolaServis;
	public static VozaciLekarskoServis lekarskoServis;
	public static VozaciLicenceServis licencaServis;
	public static VozaciLicnaServis licnaServis;
	public static VozaciPasosiServis pasosServis;

	public static VozilaServis voziloServis;
	public static VozilaNaloziServis nalogServis;
	public static VozilaOpremaServis opremaServis;
	public static VozilaOpremaPrijemServis opremaPrijemServis;
	public static VozilaPrimoPredajeServis primoPredajaServis;
	public static TroskoviServis trosakServis;
	public static VozilaSaobracajneServis saobracajnaServis;
	public static VozilaSaobracajne2Servis saobracajna2Servis;

	public static ObjekatZoneServis zonaObjekatServis;
	public static ZoneServis zonaServis;

	public static String apiGoogle;
	public static GeoApiContext gContext;
	public static NominatimClient nClient;
	public static NominatimReverseGeocodingJAPI nominatim;
	public static Obracuni obracun;
	public static Mail posta;

	private ExecutorService serverExecutor;
	private Future<?> neonFuture, nyitechFuture, genekoFuture, ruptelaFuture;

	private OpstiServer ruptela;
	private OpstiServer neon;
	private OpstiServer geneko;
	private NyitechServer nyitech;
	private Thread neonServer, nyitechServer, genekoServer, ruptelaServer;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		context = new ClassPathXmlApplicationContext("applicationContext.xml");
		alarmKorisnikServis = ApplicationContextProvider.getApplicationContext().getBean("alarmKorisnikServis", AlarmiKorisnikServis.class);
		evidencijaServis = ApplicationContextProvider.getApplicationContext().getBean("evidencijaServis", EvidencijaVoznjiServis.class);
		grupeKorisnikServis = ApplicationContextProvider.getApplicationContext().getBean("grupaKorisnikServis", GrupeKorisniciServis.class);
		grupeObjekatServis = ApplicationContextProvider.getApplicationContext().getBean("grupaObjekatServis", GrupeObjektiServis.class);
		grupeServis = ApplicationContextProvider.getApplicationContext().getBean("grupaServis", GrupeServis.class);
		javljanjePoslednjeServis = ApplicationContextProvider.getApplicationContext().getBean("javljanjePoslednjeServis", JavljanjaPoslednjaServis.class);
		javljanjeMirovanjeServis = ApplicationContextProvider.getApplicationContext().getBean("javljanjeMirovanjeServis", JavljanjaMirovanjaServis.class);
		javljanjeServis = ApplicationContextProvider.getApplicationContext().getBean("javljanjeServis", JavljanjaServis.class);
		korisnikServis = ApplicationContextProvider.getApplicationContext().getBean("korisnikServis", KorisniciServis.class);
		obdServis = ApplicationContextProvider.getApplicationContext().getBean("obdServis", ObdServis.class);
		obdPoslednjiServis = ApplicationContextProvider.getApplicationContext().getBean("obdPoslednjiServis", ObdPoslednjiServis.class);
		objekatServis =ApplicationContextProvider.getApplicationContext().getBean("objekatServis", ObjektiServis.class);
		organizacijaServis = ApplicationContextProvider.getApplicationContext().getBean("organizacijaServis", OrganizacijeServis.class);
		partnerServis = ApplicationContextProvider.getApplicationContext().getBean("partnerServis", PartneriServis.class);
		projektServis = ApplicationContextProvider.getApplicationContext().getBean("projektServis", ProjektiServis.class);
		proceduraServis = ApplicationContextProvider.getApplicationContext().getBean("proceduraServis", ProcedureServis.class);
		racunServis = ApplicationContextProvider.getApplicationContext().getBean("racunServis", RacuniServis.class);
		racunRaspodelaServis = ApplicationContextProvider.getApplicationContext().getBean("racunRaspodelaServis", RacuniRaspodelaServis.class);
		sifraServis = ApplicationContextProvider.getApplicationContext().getBean("sifraServis", SifreServis.class);
		simServis = ApplicationContextProvider.getApplicationContext().getBean("simServis", SimServis.class);
		sistemAlarmServis = ApplicationContextProvider.getApplicationContext().getBean("sistemAlarmServis", SistemAlarmiServis.class);
		sistemGorivoServis = ApplicationContextProvider.getApplicationContext().getBean("sistemGorivoServis", SistemGorivoServis.class);
		sistemOperaterServis = ApplicationContextProvider.getApplicationContext().getBean("sistemOperaterServis", SistemOperateriServis.class);
		sistemPretplatnikServis = ApplicationContextProvider.getApplicationContext().getBean("sistemPretplatnikServis", SistemPretplatniciServis.class);
		sistemServis = ApplicationContextProvider.getApplicationContext().getBean("sistemServis", SistemServis.class);
		sistemSesijaServis = ApplicationContextProvider.getApplicationContext().getBean("sistemSesijaServis", SistemSesijeServis.class);
		sistemUredjajModelServis = ApplicationContextProvider.getApplicationContext().getBean("sistemUredjajModelServis", SistemUredjajiModeliServis.class);
		sistemUredjajProizvodjacServis = ApplicationContextProvider.getApplicationContext().getBean("sistemUredjajProizvodjacServis", SistemUredjajiProizvodjaciServis.class);
		uredjajServis = ApplicationContextProvider.getApplicationContext().getBean("uredjajServis", UredjajiServis.class);

		vozacServis = ApplicationContextProvider.getApplicationContext().getBean("vozacServis", VozaciServis.class);
		dozvolaServis = ApplicationContextProvider.getApplicationContext().getBean("vozacDozvolaServis", VozaciDozvoleServis.class);
		lekarskoServis = ApplicationContextProvider.getApplicationContext().getBean("vozacLekarskoServis", VozaciLekarskoServis.class);
		licencaServis = ApplicationContextProvider.getApplicationContext().getBean("vozacLicencaServis", VozaciLicenceServis.class);
		licnaServis = ApplicationContextProvider.getApplicationContext().getBean("vozacLicnaServis", VozaciLicnaServis.class);
		pasosServis = ApplicationContextProvider.getApplicationContext().getBean("vozacPasosServis", VozaciPasosiServis.class);

		voziloServis = ApplicationContextProvider.getApplicationContext().getBean("voziloServis", VozilaServis.class);
		nalogServis = ApplicationContextProvider.getApplicationContext().getBean("voziloNalogServis", VozilaNaloziServis.class);
		opremaServis = ApplicationContextProvider.getApplicationContext().getBean("voziloOpremaServis", VozilaOpremaServis.class);
		opremaPrijemServis = ApplicationContextProvider.getApplicationContext().getBean("voziloOpremaPrijemServis", VozilaOpremaPrijemServis.class);
		primoPredajaServis = ApplicationContextProvider.getApplicationContext().getBean("voziloPrimoPredajaServis", VozilaPrimoPredajeServis.class);
		trosakServis = ApplicationContextProvider.getApplicationContext().getBean("trosakServis", TroskoviServis.class);
		saobracajnaServis = ApplicationContextProvider.getApplicationContext().getBean("saobracajnaServis", VozilaSaobracajneServis.class);
		saobracajna2Servis = ApplicationContextProvider.getApplicationContext().getBean("saobracajna2Servis", VozilaSaobracajne2Servis.class);

		zonaObjekatServis = ApplicationContextProvider.getApplicationContext().getBean("zonaObjekatServis", ObjekatZoneServis.class);
		zonaServis = ApplicationContextProvider.getApplicationContext().getBean("zonaServis", ZoneServis.class);

		apiGoogle = sistemServis.vratiSistem().getApi();
		gContext = new GeoApiContext().setApiKey(apiGoogle);
		nClient = new NominatimClient(sistemServis.vratiSistem().getEmailVlasnika(), sistemServis.vratiSistem().getNominatimAdresa());
		nominatim = new NominatimReverseGeocodingJAPI(sistemServis.vratiSistem().getNominatimAdresa());
		obracun = new Obracuni();
		posta = new Mail(sistemServis.vratiSistem().getEmailServer(), String.valueOf(sistemServis.vratiSistem().getEmailServerPort()), 
				sistemServis.vratiSistem().getEmailKorisnik(), sistemServis.vratiSistem().getEmailLozinka());

		try {
			// === START of server startup (updated) ===
			// NOTE: original code created Threads directly for each server.
			// To make startup non-invasive and reversible, we keep the server instances
			// but submit them to a dedicated ExecutorService. This allows easier control,
			// graceful shutdown and limits the number of raw Threads created.
			// Original Thread creation is left commented below for quick rollback.
			neon = new OpstiServer(9000, 100);//76 aktivnih 2021-01-08
			nyitech = new NyitechServer(9010, 20);//7 aktivnih 2021-01-08
			geneko = new OpstiServer(9030, 20);//12 aktivnih 2021-01-08
			ruptela = new OpstiServer(9040, 200);//58 aktivnih 2021-01-08

			//neonServer = new Thread(neon);
			//nyitechServer = new Thread(nyitech);
			//genekoServer = new Thread(geneko);
			//ruptelaServer = new Thread(ruptela);

			//neonServer.start();
			//nyitechServer.start();
			//genekoServer.start();
			//ruptelaServer.start();
			// Create single executor to host the server runnables (one thread per server instance)
			serverExecutor = Executors.newFixedThreadPool(4, Executors.defaultThreadFactory());
			// Submit server runnables (OpstiServer / NyitechServer implement Runnable)
			neonFuture = serverExecutor.submit(neon);
			nyitechFuture = serverExecutor.submit(nyitech);
			genekoFuture = serverExecutor.submit(geneko);
			ruptelaFuture = serverExecutor.submit(ruptela);
			// === END of server startup (updated) ===
		} catch (Throwable e) {
			System.out.println("error starting servers " + e.getMessage());
			return;
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		serverExecutor.shutdown();
		try {
			if (!serverExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
				serverExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		context = null;
		if(neonServer != null) {
			neon.stop();
		}
		if(nyitechServer != null) {
			nyitech.stop();
		}
		if(genekoServer != null) {
			geneko.stop();
		}
		if(ruptelaServer != null) {
			ruptela.stop();
		}
	}

}
