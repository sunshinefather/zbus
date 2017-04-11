package io.zbus.mq.broker;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.zbus.mq.Broker;
import io.zbus.mq.Message;
import io.zbus.mq.MessageCallback;
import io.zbus.mq.MessageInvoker;
import io.zbus.mq.net.MessageIdentifier;
import io.zbus.mq.server.MqAdaptor;
import io.zbus.mq.server.MqServer;
import io.zbus.net.ResultCallback;
import io.zbus.net.Session;
import io.zbus.net.Sync;
import io.zbus.net.Sync.Ticket;
import io.zbus.util.logging.Logger;
import io.zbus.util.logging.LoggerFactory;


public class JvmBroker implements Session, Broker, MessageInvoker {
	private static final Logger log = LoggerFactory.getLogger(JvmBroker.class);

	private MqServer mqServer;
	private MqAdaptor adaptor;
	private final Sync<Message, Message> sync = new Sync<Message, Message>(MessageIdentifier.INSTANCE, MessageIdentifier.INSTANCE);
	private int readTimeout = 3000; 
	private final String id;
	private ConcurrentMap<String, Object> attributes = null;
	private boolean isActive = true;
 
	public JvmBroker(MqServer mqServer) { 
		this.id = UUID.randomUUID().toString();
		this.mqServer = mqServer;  
		this.adaptor = this.mqServer.getMqAdaptor();  
		try {
			adaptor.sessionCreated(this);
		} catch (IOException e) {
			//should not run up here
			log.error(e.getMessage(), e); 
		}
	}
	
	@Override
	public String brokerAddress() { 
		return "jvm";
	}

	@Override
	public String getLocalAddress() {
		return "JvmBroker-Local-" + id();
	}

	@Override
	public String getRemoteAddress() {
		return "JvmBroker-Remote-" + id();
	} 
	
	@Override
	public void write(Object obj) {
		if (!(obj instanceof Message)) {
			throw new IllegalArgumentException("Message type required");
		}
		Message msg = (Message) obj;
		Ticket<Message, Message> ticket = sync.removeTicket(msg.getId());
		if (ticket != null) {
			ticket.notifyResponse(msg);
			return;
		}

		log.debug("!!!!!!!!!!!!!!!!!!!!!!!!!!Drop,%s", msg);

	}

	@Override
	public Message invokeSync(Message req, int timeout) throws IOException, InterruptedException {
		Ticket<Message, Message> ticket = null;
		try {
			ticket = sync.createTicket(req, timeout);
			invokeAsync(req, null);

			if (!ticket.await(timeout, TimeUnit.MILLISECONDS)) {
				return null;
			}
			return ticket.response();
		} finally {
			if (ticket != null) {
				sync.removeTicket(ticket.getId());
			}
		}
	}

	@Override
	public void invokeAsync(Message req, final MessageCallback callback) throws IOException {
		Ticket<Message, Message> ticket = null;
		if (callback != null) {
			ticket = sync.createTicket(req, readTimeout, new ResultCallback<Message>() { 
				@Override
				public void onReturn(Message result) {
					if(callback != null){
						callback.onReturn(result);
					}
				}
			});
		} else {
			if (req.getId() == null) {
				sync.setRequestId(req); 
			}
		}
		try {
			adaptor.sessionMessage(req, this);
		} catch (IOException e) {
			if (ticket != null) {
				sync.removeTicket(ticket.getId());
			}
			throw e;
		}
	} 
	
	@Override
	public void close() throws IOException { 
		//adaptor.sessionToDestroy(this); TODO
		isActive = false;
	}

	@Override
	public MessageInvoker selectForProducer(String topic) throws IOException {
		return this;
	}
	
	@Override
	public MessageInvoker selectForConsumer(String topic) throws IOException {
		return this;
	}

	@Override
	public void releaseInvoker(MessageInvoker client) throws IOException {

	} 


	@Override
	public String id() { 
		return id;
	}

	@Override
	public void writeAndFlush(Object obj) {
		write(obj);
	}

	@Override
	public void flush() { 
		
	}

	@Override
	public boolean isActive() {
		return isActive;
	} 
	
	@SuppressWarnings("unchecked")
	public <V> V attr(String key) {
		if (this.attributes == null) {
			return null;
		}

		return (V) this.attributes.get(key);
	}

	public <V> void attr(String key, V value) {
		if (this.attributes == null) {
			synchronized (this) {
				if (this.attributes == null) {
					this.attributes = new ConcurrentHashMap<String, Object>();
				}
			}
		}
		this.attributes.put(key, value);
	}

	@Override
	public List<Broker> availableServerList() { 
		return Arrays.asList((Broker)this);
	}

	@Override
	public void configServerSelector(ServerSelector selector) { 
		
	}

	@Override
	public void addServer(String serverAddress) { 
		
	}

	@Override
	public void removeServer(String serverAddress) { 
		
	}

	@Override
	public void addServerNotifyListener(ServerNotifyListener listener) { 
		
	}

	@Override
	public void removeServerNotifyListener(ServerNotifyListener listener) { 
		
	} 

}
