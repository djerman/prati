package rs.cybertrade.prati;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import pratiBaza.servis.AlarmiKorisnikServis;
import pratiBaza.servis.GrupeKorisniciServis;
import pratiBaza.servis.GrupeObjektiServis;
import pratiBaza.servis.GrupeServis;
import pratiBaza.servis.JavljanjaPoslednjaServis;
import pratiBaza.servis.JavljanjaServis;
import pratiBaza.servis.KorisniciServis;
import pratiBaza.servis.ObdServis;
import pratiBaza.servis.ObjektiDetaljiServis;
import pratiBaza.servis.ObjektiServis;
import pratiBaza.servis.OrganizacijeServis;
import pratiBaza.servis.SimServis;
import pratiBaza.servis.SistemAlarmiServis;
import pratiBaza.servis.SistemGorivoServis;
import pratiBaza.servis.SistemOperateriServis;
import pratiBaza.servis.SistemPretplatniciServis;
import pratiBaza.servis.SistemServis;
import pratiBaza.servis.SistemSesijeServis;
import pratiBaza.servis.SistemUredjajiModeliServis;
import pratiBaza.servis.SistemUredjajiProizvodjaciServis;
import pratiBaza.servis.UredjajiServis;
import pratiBaza.servis.ZoneObjektiServis;
import pratiBaza.servis.ZoneServis;

@WebListener
public class Servis implements ServletContextListener{
	@SuppressWarnings("unused")
	private ApplicationContext context;
	public static AlarmiKorisnikServis alarmKorisnikServis;
	public static GrupeKorisniciServis grupeKorisnikServis;
	public static GrupeObjektiServis grupeObjekatServis;
	public static GrupeServis grupeServis;
	public static JavljanjaPoslednjaServis javljanjePoslednjeServis;
	public static JavljanjaServis javljanjeServis;
	public static KorisniciServis korisnikServis;
	public static ObdServis obdServis;
	public static ObjektiDetaljiServis objekatDetaljiServis;
	public static ObjektiServis objekatServis;
	public static OrganizacijeServis organizacijaServis;
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
	public static ZoneObjektiServis zonaObjekatServis;
	public static ZoneServis zonaServis;
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		context = new ClassPathXmlApplicationContext("applicationContext.xml");
		alarmKorisnikServis = ApplicationContextProvider.getApplicationContext().getBean("alarmKorisnikServis", AlarmiKorisnikServis.class);
		grupeKorisnikServis = ApplicationContextProvider.getApplicationContext().getBean("grupaKorisnikServis", GrupeKorisniciServis.class);
		grupeObjekatServis = ApplicationContextProvider.getApplicationContext().getBean("grupaObjekatServis", GrupeObjektiServis.class);
		grupeServis = ApplicationContextProvider.getApplicationContext().getBean("grupaServis", GrupeServis.class);
		javljanjePoslednjeServis = ApplicationContextProvider.getApplicationContext().getBean("javljanjePoslednjeServis", JavljanjaPoslednjaServis.class);
		javljanjeServis = ApplicationContextProvider.getApplicationContext().getBean("javljanjeServis", JavljanjaServis.class);
		korisnikServis = ApplicationContextProvider.getApplicationContext().getBean("korisnikServis", KorisniciServis.class);
		obdServis = ApplicationContextProvider.getApplicationContext().getBean("obdServis", ObdServis.class);
		objekatDetaljiServis = ApplicationContextProvider.getApplicationContext().getBean("objekatDetaljServis", ObjektiDetaljiServis.class);
		objekatServis =ApplicationContextProvider.getApplicationContext().getBean("objekatServis", ObjektiServis.class);
		organizacijaServis = ApplicationContextProvider.getApplicationContext().getBean("organizacijaServis", OrganizacijeServis.class);
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
		zonaObjekatServis = ApplicationContextProvider.getApplicationContext().getBean("zonaObjekatServis", ZoneObjektiServis.class);
		zonaServis = ApplicationContextProvider.getApplicationContext().getBean("zonaServis", ZoneServis.class);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		context = null;
	}

}
