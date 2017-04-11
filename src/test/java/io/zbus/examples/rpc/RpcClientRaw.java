package io.zbus.examples.rpc;

import io.zbus.mq.Broker;
import io.zbus.mq.MessageInvoker;
import io.zbus.mq.ZbusBroker;
import io.zbus.rpc.Request;
import io.zbus.rpc.Response;
import io.zbus.rpc.RpcInvoker;
import io.zbus.rpc.mq.MqInvoker;

public class RpcClientRaw { 
	
	public static void main(String[] args) throws Exception {  
		
		Broker broker = new ZbusBroker("127.0.0.1:15555"); 
		MessageInvoker mqInvoker = new MqInvoker(broker, "MyRpc");  
		 
		RpcInvoker rpc = new RpcInvoker(mqInvoker); 
		  
		Request request = new Request(); 
		request.setMethod("echo");
		request.setParams(new Object[]{"test"});
		
		Response response = rpc.invokeSync(request);
		System.out.println(response.getResult()); 
		
		broker.close();
	}  
}
