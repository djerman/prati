package rs.atekom.prati.server;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.atekom.prati.server.lifecycle.ServerManager;

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
	private static GeoApiContext gContext;
	private static NominatimClient nClient;
	private static NominatimReverseGeocodingJAPI nominatim;
	public static Obracuni obracun;
	public static Mail posta;

	// ═══════════════════════════════════════════════════════════
	// НОВИ ПРИСТУП - ServerManager
	// ═══════════════════════════════════════════════════════════

	private static final Logger logger = LoggerFactory.getLogger(Servis.class);

	/**
	 * Централизовани менаџер за све серверске инстанце.
	 * Управља покретањем, заустављањем и праћењем статуса.
	 */
	private ServerManager serverManager;

	/**
	 * Серверске инстанце - чувамо референце да бисмо их регистровали
	 */
	private OpstiServer neonServer;
	private NyitechServer nyitechServer;
	private OpstiServer genekoServer;
	private OpstiServer ruptelaServer;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
	    logger.info("═══════════════════════════════════════════════════════════");
	    logger.info("  ПОКРЕТАЊЕ АПЛИКАЦИЈЕ - Prati GPS Tracking System");
	    logger.info("═══════════════════════════════════════════════════════════");
	    
	    try {
	        // ───────────────────────────────────────────────────────
	        // ШАГ 1: Иницијализација Spring контекста
	        // ───────────────────────────────────────────────────────
	        logger.info("Учитавање Spring контекста...");
	        context = new ClassPathXmlApplicationContext("applicationContext.xml");
	        
	        // ───────────────────────────────────────────────────────
	        // ШАГ 2: Инјектовање свих сервиса (остаје исто као раније)
	        // ───────────────────────────────────────────────────────
	        logger.info("Иницијализација сервиса...");
	        
	        // ОВДЕ ОСТАЈЕ СВА ПОСТОЈЕЋА ЛОГИКА ЗА СЕРВИСЕ
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
	        
	        logger.info("Сервиси учитани");
	        
	        // ───────────────────────────────────────────────────────
	        // ШАГ 3: Иницијализација помоћних компоненти
	        // ───────────────────────────────────────────────────────
	        logger.info("Конфигурација спољних сервиса...");
	        
	        apiGoogle = sistemServis.vratiSistem().getApi();
	        //gContext = new GeoApiContext().setApiKey(apiGoogle);
	        //nClient = new NominatimClient(sistemServis.vratiSistem().getEmailVlasnika(), sistemServis.vratiSistem().getNominatimAdresa());
	        //nominatim = new NominatimReverseGeocodingJAPI(sistemServis.vratiSistem().getNominatimAdresa());
	        obracun = new Obracuni();
	        posta = new Mail(sistemServis.vratiSistem().getEmailServer(), String.valueOf(sistemServis.vratiSistem().getEmailServerPort()), 
	                sistemServis.vratiSistem().getEmailKorisnik(), sistemServis.vratiSistem().getEmailLozinka());
	        
	        logger.info("Спољни сервиси конфигурисани");
	        
	        // ───────────────────────────────────────────────────────
	        // ШАГ 4: НОВА ЛОГИКА - Креирање и покретање TCP сервера
	        // ───────────────────────────────────────────────────────
	        logger.info("Иницијализација TCP сервера...");
	        
	        // Креирамо ServerManager
	        serverManager = new ServerManager();
	        
	        // Креирамо серверске инстанце
	        neonServer = new OpstiServer(9000, 100);      // 76 активних (2021-01-08)
	        nyitechServer = new NyitechServer(9010, 20);  // 7 активних (застарео)
	        genekoServer = new OpstiServer(9030, 20);     // 12 активних
	        ruptelaServer = new OpstiServer(9040, 200);   // 58 активних, растући!
	        
	        // Региструјемо серверe у менаџеру
	        serverManager.registerServer("NEON", neonServer, 9000);
	        serverManager.registerServer("NYITECH", nyitechServer, 9010);
	        serverManager.registerServer("GENEKO", genekoServer, 9030);
	        serverManager.registerServer("RUPTELA", ruptelaServer, 9040);
	        
	        // Покрећемо све серверe одједном
	        serverManager.startAll();
	        
	        logger.info("  TCP сервери покренути");
	        
	        logger.info("═══════════════════════════════════════════════════════════");
	        logger.info("  АПЛИКАЦИЈА УСПЕШНО ПОКРЕНУТА");
	        logger.info("═══════════════════════════════════════════════════════════");
	        
	    } catch (Throwable e) {
	        logger.error("═══════════════════════════════════════════════════════════");
	        logger.error("  КРИТИЧНА ГРЕШКА ПРИ ПОКРЕТАЊУ");
	        logger.error("═══════════════════════════════════════════════════════════");
	        logger.error("Детаљи грешке:", e);
	        
	        // У случају грешке, покушавамо да зауставимо оно што је покренуто
	        if (serverManager != null) {
	            serverManager.stopAll();
	        }
	        
	        throw new RuntimeException("Неуспешно покретање апликације", e);
	    }
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
	    logger.info("═══════════════════════════════════════════════════════════");
	    logger.info("  ЗАУСТАВЉАЊЕ АПЛИКАЦИЈЕ");
	    logger.info("═══════════════════════════════════════════════════════════");
	    
	    try {
	        // Заустављање свих TCP сервера преко ServerManager-а
	        if (serverManager != null && serverManager.isRunning()) {
	            logger.info("→ Заустављање TCP сервера...");
	            serverManager.stopAll();
	        } else {
	            logger.warn("ServerManager није активан");
	        }
	        
	        // Cleanup Spring контекста
	        context = null;
	        
	        logger.info("═══════════════════════════════════════════════════════════");
	        logger.info("  АПЛИКАЦИЈА УСПЕШНО ЗАУСТАВЉЕНА");
	        logger.info("═══════════════════════════════════════════════════════════");
	        
	    } catch (Throwable e) {
	        logger.error("Грешка при заустављању апликације", e);
	    }
	}

    public static synchronized GeoApiContext ensureGContext() {
        if (gContext == null) {
            String key = (apiGoogle != null) ? apiGoogle : 
                         (sistemServis != null && sistemServis.vratiSistem() != null 
                          ? sistemServis.vratiSistem().getApi() : null);
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("GOOGLE API key nije postavljen");
            }
            gContext = new GeoApiContext().setApiKey(key);
        }
        return gContext;
    }

    public static synchronized NominatimClient ensureNClient() {
        if (nClient == null) {
            String email = (sistemServis != null && sistemServis.vratiSistem() != null)
                    ? sistemServis.vratiSistem().getEmailVlasnika() : "prati@atekom.rs";
            String base  = (sistemServis != null && sistemServis.vratiSistem() != null)
                    ? sistemServis.vratiSistem().getNominatimAdresa() : null;
            if (base == null || base.isEmpty()) base = "https://nominatim.openstreetmap.org";
            nClient = new NominatimClient(email, base);
        }
        return nClient;
    }

    public static synchronized NominatimReverseGeocodingJAPI ensureNominatimJson() {
        if (nominatim == null) {
            String base  = (sistemServis != null && sistemServis.vratiSistem() != null)
                    ? sistemServis.vratiSistem().getNominatimAdresa() : null;
            if (base == null || base.isEmpty()) base = "https://nominatim.openstreetmap.org";
            nominatim = new NominatimReverseGeocodingJAPI(base);
        }
        return nominatim;
    }
}
