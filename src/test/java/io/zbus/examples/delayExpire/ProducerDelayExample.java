package io.zbus.examples.delayExpire;

import java.util.concurrent.TimeUnit;

import io.zbus.mq.Broker;
import io.zbus.mq.Message;
import io.zbus.mq.Producer;
import io.zbus.mq.ZbusBroker;

public class ProducerDelayExample {
	public static void main(String[] args) throws Exception {
		
		Broker broker = new ZbusBroker("127.0.0.1:15555");

		Producer producer = new Producer(broker, "MyMQ");
		producer.declareTopic();

		Message msg = new Message();
		msg.setDelay(10, TimeUnit.SECONDS);
		
		msg.setBody("hello world " + System.currentTimeMillis());
		msg = producer.publish(msg);
		System.out.println(msg);

		broker.close();
	}
}
