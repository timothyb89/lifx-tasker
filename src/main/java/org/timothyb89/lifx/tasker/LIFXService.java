package org.timothyb89.lifx.tasker;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.eventbus.EventBus;
import org.timothyb89.eventbus.EventBusClient;
import org.timothyb89.eventbus.EventBusProvider;
import org.timothyb89.eventbus.EventHandler;
import org.timothyb89.lifx.bulb.Bulb;
import org.timothyb89.lifx.bulb.LIFXColor;
import org.timothyb89.lifx.bulb.PowerState;
import org.timothyb89.lifx.gateway.Gateway;
import org.timothyb89.lifx.gateway.GatewayBulbDiscoveredEvent;
import org.timothyb89.lifx.gateway.GatewayManager;
import org.timothyb89.lifx.net.BroadcastListener;
import org.timothyb89.lifx.net.GatewayDiscoveredEvent;

/**
 *
 * @author tim
 */
@EService
public class LIFXService extends Service implements EventBusProvider {

	private static Logger log = LoggerFactory.getLogger(LIFXService.class);
	
	public static final int  DISCOVERY_ATTEMPTS   = 5;
	public static final long DISCOVERY_WAIT       = 100; // milliseconds
	public static final long DISCOVERY_WAIT_SMALL = 250;
	
	public static final long DEFAULT_PULSE_DELAY = 1000;
	
	private LIFXBinder binder;
	
	private BroadcastListener listener;
	
	private EventBus bus;
	
	public LIFXService() {
		binder = new LIFXBinder();
		
		bus = new EventBus() {{
			add(BulbListUpdatedEvent.class);
		}};
		
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
		} catch (BindException ex) {
			log.error("Unable to bind to LIFX port.", ex);
			showToast(getString(R.string.service_bind_failed));
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
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
		} catch (BindException ex) {
			log.error("Unable to bind to LIFX port.", ex);
			showToast(getString(R.string.service_bind_failed));
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
			} catch (BindException ex) {
				log.error("Unable to bind to LIFX port.", ex);
				showToast(getString(R.string.service_bind_failed));
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
	
	private List<Bulb> findBulbs(String[] bulbs) {
		List<Bulb> ret = new ArrayList<>();
		
		for (String bulbName : bulbs) {
			Bulb b = findBulb(bulbName);
			if (b != null) {
				ret.add(b);
			}
		}
		
		return ret;
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
	
	public void toggle(String bulbName) {
		Bulb bulb = findBulb(bulbName);
		
		if (bulb != null) {
			try {
				if (bulb.getPowerState() == PowerState.ON) {
					bulb.turnOff();
				} else {
					bulb.turnOn();
				}
			} catch (IOException ex) {
				log.error("Error calling toggle() on " + bulb, ex);
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
	
	public void setColor(String bulbName, int color) {
		Bulb bulb = findBulb(bulbName);
		
		if (bulb != null) {
			try {
				int red = Color.red(color);
				int green = Color.green(color);
				int blue = Color.blue(color);
				
				bulb.setColor(LIFXColor.fromRGB(red, green, blue));
			} catch (IOException ex) {
				log.error("Error calling setColor()", ex);
			}
		}
	}
	
	public void pulse(String[] bulbNames, int color) {
		List<Bulb> bulbs = findBulbs(bulbNames);
		
		int red = Color.red(color);
		int green = Color.green(color);
		int blue = Color.blue(color);
		LIFXColor c = LIFXColor.fromRGB(red, green, blue);
		
		Map<Bulb, LIFXColor> initialColors = new HashMap<>();
		for (Bulb b : bulbs) {
			initialColors.put(b, b.getColor());
			
			try {
				b.setColor(c);
			} catch (IOException ex) {
				log.error("Error in pulse()", ex);
			}
		}
		
		try {
			Thread.sleep(DEFAULT_PULSE_DELAY);
		} catch (InterruptedException ex) {}
		
		for (Bulb b : bulbs) {
			try {
				b.setColor(initialColors.get(b));
			} catch (IOException ex) {
				log.error("Error in pulse()", ex);
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
