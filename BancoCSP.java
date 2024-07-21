package cc.banco;

import java.util.HashMap;
import java.util.Map;
 
import org.jcsp.lang.Alternative;
import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.Channel;
import org.jcsp.lang.Guard;
import org.jcsp.lang.One2OneChannel;
import org.jcsp.lang.ProcessManager;

import es.upm.aedlib.Entry;
import es.upm.aedlib.fifo.FIFO;
import es.upm.aedlib.fifo.FIFOList;
import es.upm.aedlib.priorityqueue.HeapPriorityQueue;
import es.upm.aedlib.priorityqueue.PriorityQueue;

public class BancoCSP implements Banco, CSProcess {
	//1 canal por cada accion que podemos realizar
    private Any2OneChannel chIngresar;
    private Any2OneChannel chDisponible;
    private Any2OneChannel chTransferir;
    private Any2OneChannel chAlertar;
	
    // constructor de BancoCSP
    public BancoCSP() {
	this.chIngresar = Channel.any2one();
	this.chAlertar = Channel.any2one();
	this.chDisponible = Channel.any2one();
	this.chTransferir = Channel.any2one();
	new ProcessManager(this).start();
    }
    //clase para la solicitud al servirdor de transfrerir
    public class TransferirReq {
	String origen;
	String destino;
	int dinero;
	One2OneChannel resp;
	
	//constructor de las solicitudes de transferir
	public TransferirReq(String origen, String destino, int dinero, One2OneChannel resp) {
	    this.origen = origen;
	    this.destino = destino; 
	    this.dinero = dinero; 
	    this.resp = Channel.one2one();
		}
    }
    //clase para la solicitud al servirdor de alertar
    public class AlertarReq {
    String cuenta;
    int saldominimo;
	One2OneChannel resp;

	//constructor de las solicitudes de alertar
	public AlertarReq(String cuenta, int saldominimo, One2OneChannel resp) {
		this.cuenta=cuenta;
		this.saldominimo=saldominimo;
	    this.resp = Channel.one2one();
		}
    }
    //clase para la solicitud al servirdor de ingresar
    public class IngresarReq {
	String cuenta;
	int dinero;
	One2OneChannel resp;
	
	//constructor de las solicitudes de ingresar
	public IngresarReq(String cuenta, int dinero, One2OneChannel resp) {
		this.cuenta=cuenta;
		this.dinero=dinero;
	    this.resp = Channel.one2one();
		}
    }
    //clase para la solicitud al servirdor de disponible
    public class DisponibleReq {
	String cuenta;
	One2OneChannel resp;
	
	//constructor de las solicitudes de disponible
	public DisponibleReq(String cuenta, One2OneChannel resp) {
		this.cuenta=cuenta;
	    this.resp = Channel.one2one();
		}
    }

    public void ingresar(String c, int v){
    	
		// Se crea solicitud con informacion relevante
		IngresarReq solicitud = new IngresarReq(c, v, Channel.one2one());
		// Se escribe en canal correspondiente
		chIngresar.out().write(solicitud);
    }

    public void transferir(String o, String d, int v) throws IllegalArgumentException{
    	if(o.equals(d)) {
    		throw new IllegalArgumentException();	
    	}
    	// Se crea solicitud con informacion relevante
    	TransferirReq solicitud = new TransferirReq(o, d, v, Channel.one2one());
		// Se escribe en canal correspondiente
    	chTransferir.out().write(solicitud);
		
    	solicitud.resp.in().read();

    }

    public int disponible(String c)  throws IllegalArgumentException{
    	// Se crea solicitud con informacion relevante
    	DisponibleReq solicitud = new DisponibleReq(c, Channel.one2one());
		// Se escribe en canal correspondiente
    	chDisponible.out().write(solicitud);
    	// Se trata la respuesta del servidor, guardando en res
    	int res = (int) solicitud.resp.in().read();
    	if(res==-1) throw new IllegalArgumentException();
    	return res;
    }

    public void alertar(String c, int v)  throws IllegalArgumentException{
    	//Se crea solicitud con informacion relevante
    	AlertarReq solicitud = new AlertarReq(c, v, Channel.one2one());
		// Se escribe en canal correspondiente
    	chAlertar.out().write(solicitud);
    	//Se trata la respuesta del servidor
    	int res = (int) solicitud.resp.in().read();
    	if(res==-1) throw new IllegalArgumentException();
    }

    // Codigo del servidor
    public void run() {
	// nombres simbolicos para las entradas
	final int INGRESAR   = 0;
	final int DISPONIBLE = 1;
	final int TRANSFERIR = 2;
	final int ALERTAR    = 3;

	// construimos la estructura para recepcion alternativa
	final Guard[] guards = new AltingChannelInput[4];
	guards[INGRESAR]   = chIngresar.in();
	guards[DISPONIBLE] = chDisponible.in();
	guards[TRANSFERIR] = chTransferir.in();
	guards[ALERTAR]    = chAlertar.in();
	Alternative servicios = new Alternative(guards);
	
	//Mapa que contiene las cuentas y sus valores respectivos
	Map<String, Integer> mapacuentas = new HashMap<String, Integer>(); ;
	// Mapa que indica si hay una cuenta bloqueada y peticiones asociadas a dicha cuenta.
	Map<String, FIFO<TransferirReq>> transferirpet = new HashMap<String, FIFO<TransferirReq>>();
	// Cola de prioridad para organizar las alertas en orden de llegada.
	PriorityQueue<Integer, AlertarReq> peticionesalertar=new HeapPriorityQueue<Integer, AlertarReq>();

	// Bucle principal del servicio
	while(true) {
	    int servicio = servicios.fairSelect();

	    switch (servicio) {
	    case INGRESAR: {
	    	// Se recibe la solicitud
	    	IngresarReq solicitud = (IngresarReq) chIngresar.in().read();
			// Comprueba si la cuenta existe
			if (mapacuentas.containsKey(solicitud.cuenta)) {
				int valorcuenta = mapacuentas.get(solicitud.cuenta);
				// Se ingresa el dinero en la cuenta
				mapacuentas.put(solicitud.cuenta, valorcuenta + solicitud.dinero);
			} else {
				// Se crea la cuenta con el valor correspondiente.
				mapacuentas.put(solicitud.cuenta, solicitud.dinero);
			}
		break;
	    }
	    case DISPONIBLE: {
	    	// Se recibe la solicitud
	    	DisponibleReq solicitud = (DisponibleReq) chDisponible.in().read();
	    	//Comprueba si la cuenta existe
	    	if(!mapacuentas.containsKey(solicitud.cuenta)){
	    		//Si no, escribe -1 en el canal correspondiente
	    		solicitud.resp.out().write(-1);
	    		break;
	    	}
	    	int respuesta = mapacuentas.get(solicitud.cuenta);
	    	//Se escribe el dinero disponible en el canal correspondiente
	    	solicitud.resp.out().write(respuesta);
		break;
	    }
	    case TRANSFERIR: {

	    	TransferirReq solicitudtrans = (TransferirReq) chTransferir.in().read();
			boolean signal = true;
			// Se comprueba si no se cumple la 1 CPRE (origen no existe)
			if (signal && !mapacuentas.containsKey(solicitudtrans.origen)) {
				signal = false;
				// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
				if(transferirpet.get(solicitudtrans.origen)==null) {
					// Como no existe, se crea una lista de peticiones para dicha cuenta
					FIFO<TransferirReq> listapet = new FIFOList<TransferirReq>();
					// Se encola la peticion a la lista
					listapet.enqueue(solicitudtrans);
					// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
					transferirpet.put(solicitudtrans.origen, listapet);
					break;
				}
				else {
				// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
				transferirpet.get(solicitudtrans.origen).enqueue(solicitudtrans);
				break;
				}
			}
			// Se comprueba si no se cumple la 2 CPRE (destino no existe)
			if (signal && !mapacuentas.containsKey(solicitudtrans.destino)) {
				signal = false;
				// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
				if(transferirpet.get(solicitudtrans.origen)==null) {
					// Como no existe, se crea una lista de peticiones para dicha cuenta
					FIFO<TransferirReq> listapet = new FIFOList<TransferirReq>();
					// Se encola la peticion en la lista
					listapet.enqueue(solicitudtrans);
					// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
					transferirpet.put(solicitudtrans.origen, listapet);
					break;
				}
				else {
				// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
				transferirpet.get(solicitudtrans.origen).enqueue(solicitudtrans);
				break;
				}
			}
			// Se comprueba si no se cumple la 3 CPRE (no hay suficiente dinero en origen)
			if (signal && mapacuentas.get(solicitudtrans.origen) < solicitudtrans.dinero) {
				signal = false;
				// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
				if(transferirpet.get(solicitudtrans.origen)==null) {
					// Como no existe, se crea una lista de peticiones para dicha cuenta
					FIFO<TransferirReq> listapet = new FIFOList<TransferirReq>();
					// Se encola la peticion en la lista
					listapet.enqueue(solicitudtrans);
					// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
					transferirpet.put(solicitudtrans.origen, listapet);
					break;
				}
				else {
				// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
				transferirpet.get(solicitudtrans.origen).enqueue(solicitudtrans);
				break;
				}
			}
			// Se comprueba si ya hay alguna peticion con la cuenta origen (ya que se tienen que tratar las transferencias en orden de llegada)
			if(signal && transferirpet.get(solicitudtrans.origen)!=null  && transferirpet.get(solicitudtrans.origen).size()>0) {
				signal = false;
				// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
				if(transferirpet.get(solicitudtrans.origen)==null) {
					// Como no existe, se crea una lista de peticiones para dicha cuenta
					FIFO<TransferirReq> listapet = new FIFOList<TransferirReq>();
					// Se encola la peticion en la lista
					listapet.enqueue(solicitudtrans);
					// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
					transferirpet.put(solicitudtrans.origen, listapet);
					break;
				}
				else {
				// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
				transferirpet.get(solicitudtrans.origen).enqueue(solicitudtrans);
				break;
				}
			}
			int valor_origen = mapacuentas.get(solicitudtrans.origen);
			int valor_destino = mapacuentas.get(solicitudtrans.destino);
			 // restamos valor v del dinero de la cuenta de origen
			mapacuentas.put(solicitudtrans.origen, valor_origen - solicitudtrans.dinero);
			 // sumamos valor v al dinero de la cuenta de destino
			mapacuentas.put(solicitudtrans.destino, valor_destino + solicitudtrans.dinero);
			//Se escribe en el canal correspondiente que se ha terminado con exito
			solicitudtrans.resp.out().write("hecho");
		break;
	    }
	    case ALERTAR: {
	    	//Se crea la solicitud correspondiente
	    	AlertarReq solicitudalertar = (AlertarReq) chAlertar.in().read();
	    	//Comprueba si la cuenta existe 
	    	if(!mapacuentas.containsKey(solicitudalertar.cuenta)){
	    		//Si no, escribe -1 en el canal correspondiente
	    		solicitudalertar.resp.out().write(-1);
	    		break;
	    	}
	    	//Comprueba si el dinero de la cuenta es superior al saldo minimo
			if (mapacuentas.get(solicitudalertar.cuenta) >= solicitudalertar.saldominimo) {
				//Si lo es, se encola la peticion al final de la cola
				peticionesalertar.enqueue(peticionesalertar.size() + 1, solicitudalertar);
			}
			//Si no, se escribe 1 en el canal correspondiente
			else solicitudalertar.resp.out().write(1);

		break;
	    }
	    }
	    //Desbloqueamos las solicitudes en el orden correcto
	    desbloqueartransferencia(transferirpet,mapacuentas);
	    desbloquearalertar(peticionesalertar,mapacuentas);
	}
    }
    //metodo auxiliar que nos permite desbloquear las solicitudes de transferencia correspondientes
	public void desbloqueartransferencia(Map<String, FIFO<TransferirReq>> transferirpet,Map<String, Integer> mapacuentas) {
		for(int i=0;i<transferirpet.size();i++) {
		// Se recorre las entradas del mapa de transferencia
		for (String cuenta : transferirpet.keySet()) {
			// Comprueba si hay alguna entrada con la cuenta y si tiene alguna peticion asociada
			if(transferirpet.get(cuenta)!=null && transferirpet.get(cuenta).size()!=0) {
				// Escoge la primera peticion de la cuenta
				TransferirReq primerapet = transferirpet.get(cuenta).first();
				// Comprueba si la cuenta de destino y origen existen, si hay suficiente dinero a transferir
				if (mapacuentas.get(primerapet.destino) != null && mapacuentas.get(primerapet.origen) != null && primerapet.dinero <= mapacuentas.get(cuenta)) {
					primerapet.resp.out().write("hecho");
					
					int valor_origen = mapacuentas.get(primerapet.origen);
					int valor_destino = mapacuentas.get(primerapet.destino);
					// restamos valor v del dinero de la cuenta de origen
					mapacuentas.put(primerapet.origen, valor_origen - primerapet.dinero); 
					 // sumamos valor v al dinero de la cuenta de destino
					mapacuentas.put(primerapet.destino, valor_destino + primerapet.dinero);
					// Se quita la peticion de la lista
					transferirpet.get(cuenta).dequeue();
					i=-1;// se inicia desde cero otra vez el bucle en busca de nuevas transferencias
				}
			}
		}
		}
	}
	public void desbloquearalertar(PriorityQueue<Integer, AlertarReq> peticionesalertar,Map<String, Integer> mapacuentas) {
		// Se recorre la cola de prioridad de alertas
		for (int i = 0; i < peticionesalertar.size(); i++) {
			// Se guarda el primer objeto desencolado
			Entry<Integer, AlertarReq> primerapet = peticionesalertar.dequeue();
			//Comprueba si el dinero de la cuenta es menor que el saldo minimo
			if (primerapet.getValue().saldominimo > mapacuentas.get(primerapet.getValue().cuenta)) {
				primerapet.getValue().resp.out().write(1);
				i=-1; // se inicia desde cero otra vez el bucle en busca de nuevas alertas
			} else {
				//Si no es menor, se vuelve a encolar al final de la lista
				peticionesalertar.enqueue(peticionesalertar.size()+1, primerapet.getValue());
			}
		}
	}
	
}

