package io.zbus.examples.rpc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.zbus.examples.rpc.appdomain.InterfaceExample;
import io.zbus.examples.rpc.appdomain.MyEnum;
import io.zbus.examples.rpc.appdomain.Order;
import io.zbus.examples.rpc.appdomain.User;
import io.zbus.rpc.RpcInvoker;
import io.zbus.rpc.Request;
import io.zbus.rpc.Response;

public class RpcCases {
	public static User getUser(String name) {
		User user = new User();
		user.setName(name);
		user.setPassword("password" + System.currentTimeMillis());
		user.setAge(new Random().nextInt(100));
		user.setItem("item_1");
		user.setRoles(Arrays.asList("admin", "common"));
		return user;
	}
	
	
	public static void testRpcInvoker(RpcInvoker rpc){
		Object res = rpc.invokeSync(String.class, "echo", "test");
		System.out.println(res);

		//overlapping test
		res = rpc.invokeSync(String.class, "getString", new Class[]{String.class, int.class}, "version2", 2); 
		System.out.println(res);
		
		res = rpc.invokeSync("getOrder"); 
		System.out.println(res);
		
		Request req;
		req = new Request().method("getOrder");
		Response resp = rpc.invokeSync(req);
		System.out.println(resp.getResult());
		
		Order order = rpc.invokeSync(Order.class, req);
		System.out.println(order); 
		
	}
	
	 
	public static void testDynamicProxy(InterfaceExample biz) throws Exception{ 
		
		List<Map<String, Object>> list = biz.listMap();
		System.out.println(list);
		
		Object[] res = biz.objectArray("xzx");
		for (Object obj : res) {
			System.out.println(obj);
		}

		Object[] array = new Object[] { getUser("rushmore"), "hong", true, 1,
				String.class };
		
		
		int saved = biz.saveObjectArray(array);
		System.out.println(saved);
		 
		Class<?> ret = biz.classTest(String.class);
		System.out.println(ret);
		
		User[] users = new User[]{ getUser("rushmore"),  getUser("rushmore2")};
		biz.saveUserArray(users);
		
		biz.saveUserList(Arrays.asList(users));
		
		Object[] objects = biz.getUsers();
		for(Object obj : objects){
			System.out.println(obj);
		} 
		
		MyEnum e = biz.myEnum(MyEnum.Monday);
		System.out.println(e);
		
		Order order = biz.getOrder();
		System.out.println(order);
		
		byte[] bin = biz.getBin();
		System.out.println(bin);
	}
}
