package org.timothyb89.lifx.tasker;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.eventbus.EventBus;
import org.timothyb89.eventbus.EventBusClient;
import org.timothyb89.eventbus.EventBusProvider;
import org.timothyb89.eventbus.EventHandler;
import org.timothyb89.lifx.bulb.Bulb;
import org.timothyb89.lifx.gateway.Gateway;
import org.timothyb89.lifx.gateway.GatewayBulbDiscoveredEvent;
import org.timothyb89.lifx.gateway.GatewayManager;
import org.timothyb89.lifx.net.BroadcastListener;
import org.timothyb89.lifx.net.GatewayDiscoveredEvent;

/**
 *
 * @author tim
 */
public class LIFXService extends Service implements EventBusProvider {

	private static Logger log = LoggerFactory.getLogger(LIFXService.class);
	
	public static final int  DISCOVERY_ATTEMPTS   = 5;
	public static final long DISCOVERY_WAIT       = 100; // milliseconds
	public static final long DISCOVERY_WAIT_SMALL = 250;
	
	private LIFXBinder binder;
	
	private BroadcastListener listener;
	
	private EventBus bus;
	
	private List<Bulb> bulbsDiscovered;
	
	public LIFXService() {
		binder = new LIFXBinder();
		
		bus = new EventBus() {{
			add(BulbListUpdatedEvent.class);
		}};
		
		bulbsDiscovered = new LinkedList<>();
		
		listener = new BroadcastListener(this);
		listener.bus().register(this);
	}
	
	@Override
	public EventBusClient bus() {
		return bus.getClient();
	}
	
	@Override
	public void onCreate() {
		// start bulb discovery
		// todo: listen for network state events and clear the list of gateways
		// on network change
		
		try {
			listener.startListen();
		} catch (IOException ex) {
			log.error("Unable to bind to lifx udp port", ex);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		log.info("LIFX service started");
		
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		log.info("LIFX service stopped");
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@EventHandler
	public void gatewayDiscovered(GatewayDiscoveredEvent event) {
		// stop listening after the first gateway for now
		log.info("Found gateway: {}", event.getGateway());

		// connect to the gateway and try to discover bulbs
		try {
			event.getGateway().bus().register(this);
			event.getGateway().connect();
		} catch (IOException ex) {
			log.error("Unable to connect to gateway", ex);
		}
		
		// also stop listening
		// this means we'll only ever discover one gateway (for now)
		// TODO: be smarter about this
		try {
			listener.stopListen();
		} catch (IOException ex) {
			// ignore
		}
	}
	
	@EventHandler
	public void bulbDiscovered(GatewayBulbDiscoveredEvent event) {
		log.info("Found bulb: {}", event.getBulb());
		
		bus.push(new BulbListUpdatedEvent());
	}
	
	/**
	 * Gets and connects to all known gateways, if possible. Only those that
	 * currently have a connection (or can be connected to) will be returned.
	 * @return a list of available gateways
	 */
	private List<Gateway> getAvailableGateways() {
		List<Gateway> ret = new LinkedList<>();
		
		for (Gateway g : GatewayManager.getInstance().getGateways()) {
			if (g.isConnected()) {
				ret.add(g);
			} else {
				try {
					g.connect();
					ret.add(g);
				} catch (IOException ex) {
					log.error("Unable to connect to gateway", ex);
				}
			}
		}
		
		return ret;
	}
	
	/**
	 * Similar to {@link #getAvailableGateways()}, but will attempt to discover
	 * new gateways if none are available
	 * @return a list of gateways
	 */
	private List<Gateway> waitForGateways() {
		List<Gateway> ret = getAvailableGateways();
		if (!ret.isEmpty()) {
			return ret;
		}
		
		try {
			listener.startListen();

			Thread.sleep(DISCOVERY_WAIT_SMALL);

			listener.stopListen();
		} catch (IOException ex) {
			log.error("Unable to listen for gateways", ex);
		} catch (InterruptedException ex) {
			// ignore
		}

		return getAvailableGateways();
	}
	
	private Bulb bulbSearch(String name) {
		for (Gateway g : GatewayManager.getInstance().getGateways()) {
			for (Bulb b : g.getBulbs()) {
				if (b.getLabel().equalsIgnoreCase(name)) {
					return b;
				}
			}
		}
		
		return null;
	}
	
	private Bulb findBulb(String name) {
		// try a few times to find the bulb
		// mainly we'll be waiting for discovery here
		
		// also start listening for new gateways, in case we missed one
		if (!listener.isListening()) {
			try {
				listener.startListen();
			} catch (IOException ex) {
				log.error("Unable to start listening", ex);
			}
		}
		
		Bulb bulb = null;
		for (int i = 0; i < DISCOVERY_ATTEMPTS; i++) {
			bulb = bulbSearch(name);
			
			if (bulb != null) {
				break;
			}
			
			try {
				Thread.sleep(DISCOVERY_WAIT);
			} catch (InterruptedException ex) {
				// ignore
			}
		}
		
		try {
			listener.stopListen();
		} catch (IOException ex) {
			// ignore
		}
		
		// try one more time
		if (bulb == null) {
			bulb = bulbSearch(name);

			if (bulb == null) {
				log.warn("Bulb could not be found: {}", name);
				return null;
			}
		}
		
		log.debug("Bulb found: {}", bulb);
		
		// connect automatically
		if (!bulb.getGateway().isConnected()) {
			try {
				bulb.getGateway().connect();
			} catch (IOException ex) {
				log.error("Unable to connect to gateway {}", bulb.getGateway(), ex);
				return null;
			}
		}
		
		return bulb;
	}
	
	public void turnOn() {
		for (Gateway g : waitForGateways()) {
			try {
				g.turnOn();
			} catch (IOException ex) {
				log.error("Unable to issue turnOn() command to gateway", ex);
			}
		}
	}
	
	public void turnOn(String bulbName) {
		Bulb bulb = findBulb(bulbName);
		
		if (bulb != null) {
			try {
				bulb.turnOn();
			} catch (IOException ex) {
				log.error("Error calling turnOn()", ex);
			}
		}
	}
	
	public void turnOff() {
		for (Gateway g : waitForGateways()) {
			try {
				g.turnOff();
			} catch (IOException ex) {
				log.error("Unable to issue turnOff() command to gateway", ex);
			}
		}
	}
	
	public void turnOff(String bulbName) {
		Bulb bulb = findBulb(bulbName);
		
		if (bulb != null) {
			try {
				bulb.turnOff();
			} catch (IOException ex) {
				log.error("Error calling turnOff()", ex);
			}
		}
	}
	
	public List<Bulb> getBulbs() {
		List<Bulb> ret = new LinkedList<>();
		for (Gateway g : GatewayManager.getInstance().getGateways()) {
			ret.addAll(g.getBulbs());
		}
		
		return ret;
	}
	
	public class LIFXBinder extends Binder {
		public LIFXService getService() {
			return LIFXService.this;
		}
	}
	
}
