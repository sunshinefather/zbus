package io.zbus.util;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

public class JsonUtil { 
	private static final String DEFAULT_ENCODING = "UTF-8";  
	
	public static Map<String, Object> parseObject(String jsonString) {
		return JSON.parseObject(jsonString);
	} 
	
	public static <T> T parseObject(String jsonString, Class<T> clazz) {
		return JSON.parseObject(jsonString, clazz);
	} 
	
	@SuppressWarnings("unchecked")
	public static <T> T convert(Object json, Class<T> clazz) { 
		if(clazz.isAssignableFrom(json.getClass())){ 
			return (T)json;
		}
		if(json instanceof JSONObject){
			JSONObject jsonObject = (JSONObject)json;
			return jsonObject.toJavaObject(clazz);
		}
		
		String jsonString = JSON.toJSONString(json);
		return parseObject(jsonString, clazz);
	} 
	
	public static String toJSONString(Object value) {
		return toJSONString(value,DEFAULT_ENCODING);
	}
	
	public static String toJSONString(Object value, String encoding) {
		byte[] data = toJSONBytes(value, encoding);
		try {
			return new String(data, encoding);
		} catch (UnsupportedEncodingException e) {
			return new String(data);
		}
	}
	
	public static byte[] toJSONBytes(Object value, String encoding) {
		return toJSONBytes(value, encoding, SerializerFeature.WriteMapNullValue); 
	} 
	
	private static final byte[] toJSONBytes(Object object, String charsetName,
			SerializerFeature... features) {
		
		if(charsetName == null){
			charsetName = DEFAULT_ENCODING;
		}
		
		SerializeWriter out = new SerializeWriter(); 
		try {
			JSONSerializer serializer = new JSONSerializer(out);
			for (SerializerFeature feature : features) {
				serializer.config(feature, true);
			}

			serializer.write(object);

			return out.toBytes(charsetName);
		} finally {
			out.close();
		}
	}
} 