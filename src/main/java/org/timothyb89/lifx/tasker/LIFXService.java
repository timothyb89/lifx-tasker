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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.UiThread;
import org.apache.commons.lang3.StringUtils;
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
	public static final long DISCOVERY_WAIT_LONG  = 5000;
	
	public static final long DEFAULT_PULSE_DELAY = 1500;
	
	private LIFXBinder binder;
	
	private BroadcastListener listener;
	
	private EventBus bus;
	
	private final List<Gateway> gateways;
	private final List<Bulb> bulbs;
	
	public LIFXService() {
		binder = new LIFXBinder();
		
		bus = new EventBus() {{
			add(BulbListUpdatedEvent.class);
		}};
		
		listener = new BroadcastListener(this);
		listener.bus().register(this);
		
		gateways = Collections.synchronizedList(new LinkedList<Gateway>());
		bulbs = Collections.synchronizedList(new LinkedList<Bulb>());
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
		
		timedListen();
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
	
	private void timedListen() {
		if (listener.isListening()) {
			return;
		}
		
		try {
			log.info("Starting gateway discovery...");
			listener.startListen();

			new Timer().schedule(new TimerTask() {

				@Override
				public void run() {
					try {
						listener.stopListen();
						log.info(
								"Gateway discovery ended, {} gateways found.",
								gateways.size());
					} catch (IOException ex) {
						log.error("Unable to stop listener", ex);
					}
				}
				
			}, DISCOVERY_WAIT_LONG);
		} catch (BindException ex) {
			log.error("Unable to bind to LIFX port.", ex);
			showToast(getString(R.string.service_bind_failed));
		} catch (IOException ex) {
			log.error("Unable to listen for gateways", ex);
		}
	}
	
	@EventHandler
	public void gatewayDiscovered(GatewayDiscoveredEvent event) {
		// stop listening after the first gateway for now
		log.info("Found gateway: {}", event.getGateway());

		gateways.add(event.getGateway());
		
		// connect to the gateway and try to discover bulbs
		try {
			event.getGateway().bus().register(this);
			event.getGateway().connect();
		} catch (IOException ex) {
			log.error("Unable to connect to gateway", ex);
		}
	}
	
	@EventHandler
	public void bulbDiscovered(GatewayBulbDiscoveredEvent event) {
		log.info("Found bulb: {}", event.getBulb());
		log.info("Gateway is now: {}", event.getGateway());
		
		bulbs.add(event.getBulb());
		
		bus.push(new BulbListUpdatedEvent());
	}
	
	/**
	 * Gets and connects to all known gateways, if possible. Only those that
	 * currently have a connection (or can be connected to) will be returned.
	 * @return a list of available gateways
	 */
	private List<Gateway> getAvailableGateways() {
		List<Gateway> ret = new LinkedList<>();
		
		synchronized (gateways) {
			for (Gateway g : gateways) {
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
		
		timedListen();

		for (int i = 0; i < DISCOVERY_ATTEMPTS; i++) {
			try {	
				Thread.sleep(DISCOVERY_WAIT);
			} catch (InterruptedException ex) {
				// ignore
			}

			if (!gateways.isEmpty()) {
				break;
			} 
		}

		return getAvailableGateways();
	}
	
	private Bulb bulbSearch(String name) {
		synchronized (bulbs) {
			log.info(
					"Searching for bulb {} in bulbs: {}",
					Arrays.toString(bulbs.toArray()));

			for (Bulb b : bulbs) {
				// TODO: add support for MAC address
				
				if (b.getLabel().equalsIgnoreCase(name)) {
					return b;
				}
			}
		}
		
		log.info("Bulb not found: {}", name);
		
		return null;
	}
	
	private List<Bulb> bulbSearch(List<String> names) {
		List<Bulb> ret = new ArrayList<>();
		
		synchronized (bulbs) {
			log.info("Searching for bulbs: {} - Known: {}",
					Arrays.toString(names.toArray()),
					Arrays.toString(bulbs.toArray()));
			
			for (Bulb b : bulbs) {
				if (names.contains(b.getLabel())) {
					names.remove(b.getLabel());
					ret.add(b);
				}
			}
		}
		
		if (names.isEmpty()) {
			log.info("All requested bulbs found");
		} else {
			log.info("Bulbs not found: {}", StringUtils.join(names, ", "));
		}
		
		return ret;
	}
	
	private Bulb findBulb(String name) {
		// try a few times to find the bulb
		// mainly we'll be waiting for discovery here
		
		// also start listening for new gateways, in case we missed one
		timedListen();
		
		Bulb bulb = null;
		for (int i = 0; i < DISCOVERY_ATTEMPTS; i++) {
			bulb = bulbSearch(name);
			
			if (bulb != null) {
				break;
			}
			
			try {
				Thread.sleep(DISCOVERY_WAIT_SMALL);
			} catch (InterruptedException ex) {
				// ignore
			}
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
		List<String> remaining = new ArrayList<>();
		remaining.addAll(Arrays.asList(bulbs));
		
		List<Bulb> ret = new ArrayList<>();
		
		timedListen();
		
		for (int i = 0; i < DISCOVERY_ATTEMPTS; i++) {
			ret.addAll(bulbSearch(remaining));
			
			if (remaining.isEmpty()) {
				// we found all the bulbs we're looking for
				break;
			}
			
			try {
				Thread.sleep(DISCOVERY_WAIT);
			} catch (InterruptedException ex) {
				// ignore
			}
		}
		
		// try one more time
		if (!remaining.isEmpty()) {
			ret.addAll(bulbSearch(remaining));

			if (!remaining.isEmpty()) {
				log.warn(
						"Bulbs could not be found: {}",
						StringUtils.join(remaining, ", "));
				return ret;
			}
		}
		
		log.debug("Bulbs found: {}", Arrays.toString(ret.toArray()));
		
		List<Bulb> deadBulbs = new LinkedList<>();
		
		// connect automatically
		for (Bulb bulb : ret) {
			if (!bulb.getGateway().isConnected()) {
				try {
					bulb.getGateway().connect();
				} catch (IOException ex) {
					log.error("Unable to connect to gateway {}", bulb.getGateway(), ex);
					deadBulbs.add(bulb);
				}
			}
		}
		
		if (!deadBulbs.isEmpty()) {
			log.warn("Dead bulbs: {}", Arrays.toString(deadBulbs.toArray()));
			ret.removeAll(deadBulbs);
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
	
	public void turnOn(String[] bulbNames) {
		for (Bulb b : findBulbs(bulbNames)) {
			try {
				b.turnOn();
			} catch (IOException ex) {
				log.error("Error calling turnOn() for bulb: " + b, ex);
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
	
	public void turnOff(String[] bulbNames) {
		for (Bulb b : findBulbs(bulbNames)) {
			try {
				b.turnOff();
			} catch (IOException ex) {
				log.error("Error calling turnOff() for bulb: " + b, ex);
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
	
	public void toggle(String[] bulbNames) {
		log.info("Attempting toggle on: {}", Arrays.toString(bulbNames));
		
		for (Bulb bulb : findBulbs(bulbNames)) {
			log.info("Toggling {}", bulb);
			
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
	
	public void setColor(String[] bulbNames, int color) {
		for (Bulb bulb : findBulbs(bulbNames)) {
			try {
				int red = Color.red(color);
				int green = Color.green(color);
				int blue = Color.blue(color);
				
				bulb.setColor(LIFXColor.fromRGB(red, green, blue));
			} catch (IOException ex) {
				log.error("Error calling setColor() on " + bulb, ex);
			}
		}
	}
	
	public void pulse(String[] bulbNames, int color) {
		List<Bulb> bulbs = findBulbs(bulbNames);
		
		int red   = Color.red(color);
		int green = Color.green(color);
		int blue  = Color.blue(color);
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
		//List<Bulb> ret = new LinkedList<>();
		//for (Gateway g : GatewayManager.getInstance().getGateways()) {
		//	ret.addAll(g.getBulbs());
		//}
		//
		//return ret;
		
		return bulbs;
	}
	
	public class LIFXBinder extends Binder {
		public LIFXService getService() {
			return LIFXService.this;
		}
	}
	
}
