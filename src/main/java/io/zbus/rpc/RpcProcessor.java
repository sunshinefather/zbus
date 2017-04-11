package io.zbus.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.zbus.mq.Message;
import io.zbus.mq.MessageProcessor;
import io.zbus.util.JsonUtil;
import io.zbus.util.logging.Logger;
import io.zbus.util.logging.LoggerFactory;


public class RpcProcessor implements MessageProcessor{
	private static final Logger log = LoggerFactory.getLogger(RpcProcessor.class); 
	
	private RpcCodec codec = new JsonRpcCodec();
	private Map<String, MethodInstance> methods = new HashMap<String, MethodInstance>();
	
	private Map<String, List<RpcMethod>> object2Methods = new HashMap<String, List<RpcMethod>>();
	
	public RpcProcessor(){
		
	}
	
	public RpcProcessor(Object... services){
		addModule(services);
	}
	
	public void codec(RpcCodec codec){
		this.codec = codec;
	}
	
	static List<Class<?>> getAllInterfaces(Class<?> clazz){
		List<Class<?>> res = new ArrayList<Class<?>>();
		while(clazz != null){ 
			res.addAll(Arrays.asList(clazz.getInterfaces()));
			clazz = clazz.getSuperclass();
		}
		return res;
	}
	
	public void addModule(Object... services){
		for(Object obj : services){
			for(Class<?> intf : getAllInterfaces(obj.getClass())){
				addModule(intf.getSimpleName(), obj);
				addModule(intf.getName(), obj);
			}
			addModule("", obj);
			addModule(obj.getClass().getSimpleName(), obj);
			addModule(obj.getClass().getName(), obj);
		} 
	}
	
	public void addModule(String module, Object... services){
		for(Object service: services){
			this.initCommandTable(module, service);
		}
	}
	
	private void addMoudleInfo(Object service){
		String serviceKey = service.getClass().getCanonicalName();
		if(object2Methods.containsKey(serviceKey)){
			return;
		}
		List<String> modules = new ArrayList<String>(); 
		modules.add(""); 
		for(Class<?> intf : getAllInterfaces(service.getClass())){
			modules.add(intf.getSimpleName());
			modules.add(intf.getCanonicalName()); 
		}
		modules.add(service.getClass().getSimpleName()); 
		modules.add(service.getClass().getCanonicalName());  
		
		Method [] methods = service.getClass().getMethods(); 
		List<RpcMethod> rpcMethods = new ArrayList<RpcMethod>();
		object2Methods.put(serviceKey,rpcMethods);
		for (Method m : methods) { 
			String method = m.getName();
			Remote cmd = m.getAnnotation(Remote.class);
			if(cmd != null){ 
				method = cmd.id();
				if(cmd.exclude()) continue;
				if("".equals(method)){
					method = m.getName();
				}  
			}
			RpcMethod rpcm = new RpcMethod();
			rpcm.setModules(modules);
			rpcm.setName(method);
			rpcm.setReturnType(m.getReturnType().getCanonicalName());
			List<String> paramTypes = new ArrayList<String>();
			for(Class<?> t : m.getParameterTypes()){
				paramTypes.add(t.getCanonicalName());
			}
			rpcm.setParamTypes(paramTypes);
			rpcMethods.add(rpcm);
		} 
	}
	
	private void removeMoudleInfo(Object service){
		String serviceKey = service.getClass().getCanonicalName();
		object2Methods.remove(serviceKey);
	}
	
	public void removeModule(Object... services){
		for(Object obj : services){
			for(Class<?> intf : getAllInterfaces(obj.getClass())){
				removeModule(intf.getSimpleName(), obj);
				removeModule(intf.getCanonicalName(), obj);
			}
			removeModule("", obj);
			removeModule(obj.getClass().getSimpleName(), obj);
			removeModule(obj.getClass().getCanonicalName(), obj);
		}
	}
	
	public void removeModule(String module, Object... services){
		for(Object service: services){
			this.removeCommandTable(module, service);
		}
	}
	
	private void initCommandTable(String module, Object service){
		addMoudleInfo(service);
		
		try {  
			Method [] methods = service.getClass().getMethods(); 
			for (Method m : methods) { 
				if(m.getDeclaringClass() == Object.class) continue;
				
				String method = m.getName();
				Remote cmd = m.getAnnotation(Remote.class);
				if(cmd != null){ 
					method = cmd.id();
					if(cmd.exclude()) continue;
					if("".equals(method)){
						method = m.getName();
					}  
				}
				
				String paramMD5 = ""; 
				Class<?>[] paramTypes = m.getParameterTypes();
				StringBuilder sb = new StringBuilder();
				for(int i=0;i<paramTypes.length;i++){ 
					sb.append(paramTypes[i].getCanonicalName());
				}
				paramMD5 = sb.toString();
				String key = module + ":" + method+":"+paramMD5; 
				String key2 = module + ":" + method;
				if(this.methods.containsKey(key)){
					log.debug(key + " duplicated"); 
				} else {
					log.debug("register "+service.getClass().getSimpleName()+"\t" + key);
					log.debug("register "+service.getClass().getSimpleName()+"\t"  + key2);
				}
				m.setAccessible(true);
				MethodInstance mi = new MethodInstance(m, service);
				this.methods.put(key, mi);  
				this.methods.put(key2, mi);  
			}  
		} catch (SecurityException e) {
			log.error(e.getMessage(), e);
		}   
	}
	
	private void removeCommandTable(String module, Object service){
		removeMoudleInfo(service);
		
		try {  
			Method [] methods = service.getClass().getMethods(); 
			for (Method m : methods) { 
				String method = m.getName();
				Remote cmd = m.getAnnotation(Remote.class);
				if(cmd != null){ 
					method = cmd.id();
					if(cmd.exclude()) continue;
					if("".equals(method)){
						method = m.getName();
					}  
				}
				
				String paramMD5 = ""; 
				Class<?>[] paramTypes = m.getParameterTypes();
				StringBuilder sb = new StringBuilder();
				for(int i=0;i<paramTypes.length;i++){ 
					sb.append(paramTypes[i].getCanonicalName());
				}
				paramMD5 = sb.toString();
				String key = module + ":" + method+":"+paramMD5; 
				String key2 = module + ":" + method;
				this.methods.remove(key);
				this.methods.remove(key2);
			}  
		} catch (SecurityException e) {
			log.error(e.getMessage(), e);
		}   
	}
	
	private static boolean isBlank(String value){
		return (value == null || "".equals(value.trim()));
	}
	

	
	private MethodInstance findMethod(Request req){ 
		String paramTypesMD5 = "";
		if(req.getParamTypes() != null){
			for(String type : req.getParamTypes()){
				paramTypesMD5 += type;
			}
		}
		String module = req.getModule(); 
		String method = req.getMethod();
		String key = module+":"+method+":"+paramTypesMD5;//支持语言多态
		String key2 = module+":"+method;
		if(this.methods.containsKey(key)){
			return this.methods.get(key); 
		} else {  
			if(this.methods.containsKey(key2)){
				return this.methods.get(key2); 
			} else {
				String errorMsg = String.format("%s:%s not found, module may not set, or wrong", module, method);
				throw new IllegalArgumentException(errorMsg);
			}
		}
	}
	
	private void checkParamTypes(MethodInstance target, Request req){
		Class<?>[] targetParamTypes = target.method.getParameterTypes();
		
		if(targetParamTypes.length !=  req.getParams().length){
			String requiredParamTypeString = "";
			for(int i=0;i<targetParamTypes.length;i++){
				Class<?> paramType = targetParamTypes[i]; 
				requiredParamTypeString += paramType.getName();
				if(i<targetParamTypes.length-1){
					requiredParamTypeString += ", ";
				}
			}
			Object[] params = req.getParams();
			String gotParamsString = "";
			for(int i=0;i<params.length;i++){ 
				gotParamsString += params[i];
				if(i<params.length-1){
					gotParamsString += ", ";
				}
			}
			String errorMsg = String.format("Method:%s(%s), called with %s(%s)", 
					target.method.getName(), requiredParamTypeString, target.method.getName(), gotParamsString);
			throw new IllegalArgumentException(errorMsg);
		}
	}
	
	public Message process(Message msg){  
		Response resp = new Response();
		int status = 200;
		final String msgId = msg.getId();
		String encoding = msg.getEncoding();
		try {
			Object result = null;
			Request req = codec.decodeRequest(msg); 
			Request.normalize(req); 
			if(isBlank(req.getMethod())){
				result = object2Methods;
			} else {
				MethodInstance target = findMethod(req);
				checkParamTypes(target, req);
				
				Class<?>[] targetParamTypes = target.method.getParameterTypes();
				Object[] invokeParams = new Object[targetParamTypes.length];  
				Object[] reqParams = req.getParams(); 
				for(int i=0; i<targetParamTypes.length; i++){  
					//invokeParams[i] = codec.convert(reqParams[i], targetParamTypes[i]);
					invokeParams[i] = JsonUtil.convert(reqParams[i], targetParamTypes[i]);
				} 
				result = target.method.invoke(target.instance, invokeParams);
			} 
			resp.setResult(result);  
				
		} catch (InvocationTargetException e) { 
			resp.setError(e.getTargetException());
			status = 500;
		} catch (Throwable e) { 
			resp.setError(e);
			status = 500;
		} 
		try{
			Message res = codec.encodeResponse(resp, encoding);
			res.setId(msgId); 
			res.setStatus(status);
			return res;
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
		} 
		return null; //should not here
	}
	
	class MethodInstance{
		public Method method;
		public Object instance;
		
		public MethodInstance(Method method, Object instance){
			this.method = method;
			this.instance = instance;
		} 
	}

	public RpcCodec getCodec() {
		return codec;
	}

	public void setCodec(RpcCodec codec) {
		this.codec = codec;
	} 
}
