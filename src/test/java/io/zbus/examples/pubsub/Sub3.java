package io.zbus.examples.pubsub;

import io.zbus.mq.Broker;
import io.zbus.mq.Consumer;
import io.zbus.mq.Message;
import io.zbus.mq.ZbusBroker;

public class Sub3 {
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception{   
		final Broker broker = new ZbusBroker("127.0.0.1:15555"); 
		
		Consumer c = new Consumer(broker, "MyMQ");   
		c.setConsumerGroup("Group3");  
		c.declareTopic();
		
		while(true){ //take one by one, controlled by caller
			Message message = c.take();
			System.out.println(message);
		}
	} 
}
