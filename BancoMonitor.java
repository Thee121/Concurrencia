package cc.banco;


import java.util.Map;
import java.util.HashMap;

import es.upm.aedlib.priorityqueue.HeapPriorityQueue;
import es.upm.aedlib.priorityqueue.PriorityQueue;
import es.upm.babel.cclib.Monitor;
import es.upm.aedlib.Entry;
import es.upm.aedlib.fifo.*;

public class BancoMonitor implements Banco {
	// Monitor para la organizacion de procesos del programa
	private Monitor mutex;
	// Mapa para organizar cuentas y cantidad de dinero
	private Map<String, Integer> mapacuentas;
	// Mapa que indica si hay una cuenta bloqueada y peticiones asociadas a dicha cuenta.
	private Map<String, FIFO<peticion>> peticionestrans;
	// Cola de prioridad para organizar las alertas en orden de llegada.
	private PriorityQueue<Integer, peticion> peticionesalertar;

	// constructor para la inicializacion de monitor, mapas y cola.
	public BancoMonitor() {
		this.mapacuentas = new HashMap<String, Integer>();
		this.peticionestrans = new HashMap<String, FIFO<peticion>>();
		this.peticionesalertar = new HeapPriorityQueue<Integer, peticion>();
		this.mutex = new Monitor();
	}

	public void ingresar(String c, int v) {
		mutex.enter();
		// Comprueba si la cuenta existe
		if (mapacuentas.containsKey(c)) {
			int valorcuenta = mapacuentas.get(c);
			// Se ingresa el dinero en la cuenta
			mapacuentas.put(c, valorcuenta + v);
		} else {
			// Se crea la cuenta con el valor correspondiente.
			mapacuentas.put(c, v);
		}
		// Se llama a metodo correspondiente para ver si podemos desbloquear alguna transferencia
		desbloqueartransferencia();

		mutex.leave();
	}

	public void transferir(String o, String d, int v) throws IllegalArgumentException {
		mutex.enter();
		// Se comprueba PRE (Si la cuenta de origen es la misma que la de destino)
		if (o.equals(d)) {
			mutex.leave();
			throw new IllegalArgumentException();
		}
		// Variable para asegurar que solo haya un .await() por cada hilo
		boolean signal = true;
		// Se comprueba si no se cumple la 1 CPRE (origen no existe)
		if (signal && !mapacuentas.containsKey(o)) {
			signal = false;
			// Se genera peticion con origen, destino y valor a transferir
			peticion noorigen = new peticion(o, d, v);
			// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
			if(peticionestrans.get(o)==null) {
				// Como no existe, se crea una lista de peticiones para dicha cuenta
				FIFO<peticion> listapet = new FIFOList<peticion>();
				// Se encola la peticion a la lista
				listapet.enqueue(noorigen);
				// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
				peticionestrans.put(o, listapet);
			}
			else {
			// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
			peticionestrans.get(o).enqueue(noorigen);
			}
			// Se bloquea la condicion de la peticion
			noorigen.condicion.await();
		}
		// Se comprueba si no se cumple la 2 CPRE (destino no existe)
		if (signal && !mapacuentas.containsKey(d)) {
			signal = false;
			// Se genera peticion con origen, destino y valor a transferir
			peticion nodestino = new peticion(o, d, v);
			// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
			if(peticionestrans.get(o)==null) {
				// Como no existe, se crea una lista de peticiones para dicha cuenta
				FIFO<peticion> listapet = new FIFOList<peticion>();
				// Se encola la peticion en la lista
				listapet.enqueue(nodestino);
				// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
				peticionestrans.put(o, listapet);
			}
			else {
			// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
			peticionestrans.get(o).enqueue(nodestino);
			}
			// Se bloquea la condicion de la peticion
			nodestino.condicion.await();
		}
		// Se comprueba si no se cumple la 3 CPRE (no hay suficiente dinero en origen)
		if (signal && mapacuentas.get(o) < v) {
			signal = false;
			// Se genera peticion con origen, destino y valor a transferir
			peticion valormenor = new peticion(o, d, v);
			// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
			if(peticionestrans.get(o)==null) {
				// Como no existe, se crea una lista de peticiones para dicha cuenta
				FIFO<peticion> listapet = new FIFOList<peticion>();
				// Se encola la peticion en la lista
				listapet.enqueue(valormenor);
				// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
				peticionestrans.put(o, listapet);
			}
			else {
			// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
			peticionestrans.get(o).enqueue(valormenor);
			}
			// Se bloquea la condicion de la peticion
			valormenor.condicion.await();
		}
		// Se comprueba si ya hay alguna peticion con la cuenta origen (ya que se tienen que tratar las transferencias en orden de llegada)
		if(signal && peticionestrans.get(o)!=null  && peticionestrans.get(o).size()>0) {
			signal = false;
			// Se genera peticion con origen, destino y valor a transferir
			peticion prioridadmenor = new peticion(o,d,v);
			// Comprueba si existe una entrada de la cuenta en el mapa de peticiones de transferencia
			if(peticionestrans.get(o)==null) {
				// Como no existe, se crea una lista de peticiones para dicha cuenta
				FIFO<peticion> listapet = new FIFOList<peticion>();
				// Se encola la peticion en la lista
				listapet.enqueue(prioridadmenor);
				// Se crea una nueva entrada en el mapa con cuenta origen y su lista de peticiones
				peticionestrans.put(o, listapet);
			}
			else {
			// Como ya existe, se introduce la peticion en la lista de la cuenta correspondiente
			peticionestrans.get(o).enqueue(prioridadmenor);
			}
			// Se bloquea la condicion de la peticion
			prioridadmenor.condicion.await();
		}
		//Si el codigo ha llegado aqui, no se ha violado ninguna de las CPREs
		int valor_origen = mapacuentas.get(o);
		int valor_destino = mapacuentas.get(d);
		//Se saca el dinero a transferir de la cuenta de origen
		mapacuentas.put(o, valor_origen - v);
		//Se introduce el dinero a transferir en la cuenta de destino
		mapacuentas.put(d, valor_destino + v);
		
		// Se llama al metodo correspondiente para ver si se pueden desbloquear alguna transferencia
		if (!desbloqueartransferencia()) {
			// Si no se ha desbloqueado ninguna transferencia, se puede comprobar si se puede desbloquear alguna alerta
			desbloquearalertar(o);
		}
		mutex.leave();
	}

	public int disponible(String c) throws IllegalArgumentException {
		mutex.enter();
		// Se comprueba PRE (si cuenta no existe)
		if (!mapacuentas.containsKey(c)) {
			mutex.leave();
			throw new IllegalArgumentException();
		}
		// Se salvaguarda el valor pedido
		int respuesta = mapacuentas.get(c);

		mutex.leave();

		// Devolvemos el dinero disponible de la cuenta c.
		return respuesta;
	}

	public void alertar(String c, int m) throws IllegalArgumentException {
		mutex.enter();
		// Se comprueba PRE (si cuenta no existe)
		if (!mapacuentas.containsKey(c)) {
			mutex.leave();
			throw new IllegalArgumentException();
		}
		// Condicion para poder generar la alerta (dinero de cuenta es mayor que el saldo minimo)
		if (mapacuentas.get(c) >= m) {
			// Se genera peticion con origen, destino y valor a transferir
			peticion peticionalerta = new peticion(c, c, m);
			// Se encola la peticion al final de la cola
			peticionesalertar.enqueue(peticionesalertar.size() + 1, peticionalerta);
			// Se bloquea la condicion de la alerta.
			peticionalerta.condicion.await();
		}
		desbloquearalertar(c);
		mutex.leave();
	}

	// clase auxiliar que permite generar las peticiones de bloqueo
	public class peticion {
		// Cuenta de origen
		private String origen;
		// Cuenta de destino
		private String destino;
		// Condicion de bloqueo de la peticion
		private Monitor.Cond condicion;
		//Dinero asociada a la peticion (valor minimo o dinero a transferir)
		private int dinero;

		// Constructor del metodo auxiliar
		public peticion(String origen, String destino, int valor) {
			this.condicion = mutex.newCond();
			this.origen = origen;
			this.destino = destino;
			this.dinero = valor;
		}
		// Metodo auxiliar que devuelve dinero asociado a la peticion
		public int getdinero() {
			return dinero;
		}
		// Metodo auxiliar que devuelve la cuenta de origen asociada a la peticion
		public String getcuentaorigen() {
			return origen;
		}
		// Metodo auxiliar que devuelve la cuenta de destino asociada a la peticion
		public String getcuentadestino() {
			return destino;
		}
		// Metodo auxiliar que devuelve la condicion de origen asociada a la peticion
		public Monitor.Cond getcondicion() {
			return condicion;
		}
	}
	// Metodo auxiliar que permite desbloquear las peticiones de transferencia
	public boolean desbloqueartransferencia() {
		// Variable que indica si se ha realizado el .signal() de la peticion
		boolean desbloqueado = false;
		// Se recorre las entradas del mapa de transferencia
		for (String cuenta : peticionestrans.keySet()) {
			// Comprueba si hay alguna entrada con la cuenta y si tiene alguna peticion asociada
			if(peticionestrans.get(cuenta)!=null && peticionestrans.get(cuenta).size()!=0) {
				// Escoge la primera peticion de la cuenta
				peticion primerapet = peticionestrans.get(cuenta).first();
				// Comprueba si la cuenta de destino y origen existen, si hay suficiente dinero a transferir y si no se ha desbloqueado algo previamente
				if (mapacuentas.get(primerapet.getcuentadestino()) != null && mapacuentas.get(primerapet.getcuentaorigen()) != null && primerapet.getdinero() <= mapacuentas.get(cuenta) && !desbloqueado) {
					// Se han complido los parametros, por lo tanto se desbloquea la condicion
					primerapet.getcondicion().signal();
					// Se quita la peticion de la lista
					peticionestrans.get(cuenta).dequeue();
					// Se indica que ya se ha realizado el desbloqueo
					desbloqueado = true;
				}
			}
		}
		// Se devuelve si se ha podido desbloquear la transferencia
		return desbloqueado;
	}
	// Metodo auxiliar que permite desbloquear las peticiones de alertar
	public void desbloquearalertar(String cuenta) {
		// Variable que indica si se ha realizado el .signal() de la peticion
		boolean desbloqueado = false;
		// Se recorre la cola de prioridad de alertas
		for (int i = 0; i < peticionesalertar.size() && !desbloqueado; i++) {
			// Se guarda el primer objeto desencolado
			Entry<Integer, peticion> pet = peticionesalertar.dequeue();
			//Comprueba si el dinero de la cuenta es menor que el saldo minimo
			if (pet.getValue().getdinero() > mapacuentas.get(pet.getValue().getcuentaorigen())) {
				//Si lo es, se desbloquea
				pet.getValue().getcondicion().signal();
				//Se indica que ya se ha realizado el desbloqueo
				desbloqueado = true;
			} else {
				//Si no es menor, se vuelve a encolar al final de la lista
				peticionesalertar.enqueue(peticionesalertar.size()+1, pet.getValue());
			}
		}
	}
}
